import { escHtml, tagColor } from './grid.js';

export function createDrawer(options) {
    const drawer = options.drawer;
    const drawerPanel = options.drawerPanel;
    const drawerBackdrop = options.drawerBackdrop;
    const drawerClose = options.drawerClose;
    const drawerSave = options.drawerSave;
    const drawerId = options.drawerId;
    const drawerTitle = options.drawerTitle;
    const drawerSummary = options.drawerSummary;
    const drawerSteps = options.drawerSteps;
    const drawerStatus = options.drawerStatus;
    const drawerUpdated = options.drawerUpdated;
    const drawerAssignee = options.drawerAssignee;
    const drawerReporter = options.drawerReporter;
    const drawerCreatedOn = options.drawerCreatedOn;
    const drawerTagBadges = options.drawerTagBadges;
    const drawerTagInput = options.drawerTagInput;
    const drawerTagDropdown = options.drawerTagDropdown;
    const drawerManageTagsBtn = options.drawerManageTagsBtn;
    const drawerManageTagsPanel = options.drawerManageTagsPanel;
    const drawerManageTagsList = options.drawerManageTagsList;
    const drawerManageTagsEmpty = options.drawerManageTagsEmpty;
    const drawerModeLabel = options.drawerModeLabel;
    const drawerFooterHint = options.drawerFooterHint;
    const getTestCaseById = options.getTestCaseById;

    // Callbacks provided by page.js for API calls.
    let onTagAdd = options.onTagAdd || null;       // (workKey, tagId) => Promise<Tag[]>
    let onTagRemove = options.onTagRemove || null; // (workKey, tagId) => Promise<Tag[]>
    let onTagCreate = options.onTagCreate || null; // (name) => Promise<Tag>
    let onTagRename = options.onTagRename || null; // (tagId, name) => Promise<Tag>
    let onTagDelete = options.onTagDelete || null; // (tagId) => Promise<void>

    // Live state for the open drawer.
    let currentWorkKey = null;
    let currentTags = []; // [{id, name}] assigned to the open test case
    let teamCatalog = []; // [{id, name}] full team tag list
    let isReadOnly = false;
    let dropdownIndex = -1; // -1 = no item highlighted

    // --- Tag badge rendering ---

    function renderTagBadges() {
        if (!drawerTagBadges) {
            return;
        }

        drawerTagBadges.innerHTML = '';

        if (currentTags.length === 0) {
            const empty = document.createElement('span');
            empty.className = 'text-xs text-white/45';
            empty.textContent = 'No tags.';
            drawerTagBadges.appendChild(empty);
            return;
        }

        for (const tag of currentTags) {
            const badge = document.createElement('span');
            badge.className = 'inline-flex items-center gap-1 px-2 py-0.5 text-xs font-medium rounded-full whitespace-nowrap';

            const c = tagColor(tag.name);
            badge.style.color = c.color;
            badge.style.backgroundColor = c.bg;

            const label = document.createElement('span');
            label.textContent = tag.name;
            badge.appendChild(label);

            if (!isReadOnly) {
                const removeBtn = document.createElement('button');
                removeBtn.type = 'button';
                removeBtn.className = 'ml-0.5 opacity-50 hover:opacity-100 focus:outline-none';
                removeBtn.setAttribute('aria-label', 'Remove tag ' + tag.name);
                removeBtn.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" fill="currentColor" class="w-3 h-3"><path d="M3.72 3.72a.75.75 0 0 1 1.06 0L8 6.94l3.22-3.22a.75.75 0 1 1 1.06 1.06L9.06 8l3.22 3.22a.75.75 0 1 1-1.06 1.06L8 9.06l-3.22 3.22a.75.75 0 0 1-1.06-1.06L6.94 8 3.72 4.78a.75.75 0 0 1 0-1.06z"/></svg>';
                removeBtn.addEventListener('click', () => handleRemoveTag(tag.id));
                badge.appendChild(removeBtn);
            }

            drawerTagBadges.appendChild(badge);
        }
    }

    // --- Dropdown ---

    function setDropdownHighlight(index) {
        if (!drawerTagDropdown) return;
        const btns = drawerTagDropdown.querySelectorAll('button');
        btns.forEach((btn, i) => {
            btn.classList.toggle('bg-white/10', i === index);
        });
        dropdownIndex = index;
        if (index >= 0 && index < btns.length) {
            btns[index].scrollIntoView({ block: 'nearest' });
        }
    }

    function showDropdown(items) {
        if (!drawerTagDropdown) {
            return;
        }

        drawerTagDropdown.innerHTML = '';
        dropdownIndex = -1;

        if (items.length === 0) {
            drawerTagDropdown.classList.add('hidden');
            return;
        }

        for (const item of items) {
            const row = document.createElement('button');
            row.type = 'button';
            row.className = 'w-full text-left px-3 py-2 text-xs text-white/75 hover:bg-white/10 focus:outline-none focus:bg-white/10';

            if (item.isCreate) {
                row.innerHTML = '<span class="text-white/45 mr-1">Create</span>' + escHtml(item.name);
                row.addEventListener('click', () => handleCreateTag(item.name));
            } else {
                row.className += ' flex items-center justify-between gap-2';
                const nameSpan = document.createElement('span');
                nameSpan.textContent = item.name;
                const countSpan = document.createElement('span');
                countSpan.className = 'shrink-0 text-white/30';
                countSpan.textContent = item.count > 0 ? String(item.count) : '';
                row.appendChild(nameSpan);
                row.appendChild(countSpan);
                row.addEventListener('click', () => handleAddTag(item.id));
            }

            drawerTagDropdown.appendChild(row);
        }

        drawerTagDropdown.classList.remove('hidden');

        // Keyboard hint footer.
        const hint = document.createElement('div');
        hint.className = 'px-3 py-1.5 text-white/25 text-xs border-t border-white/10 select-none';
        hint.textContent = '↑↓ navigate · ↵ select · Esc close';
        drawerTagDropdown.appendChild(hint);
    }

    function hideDropdown() {
        if (drawerTagDropdown) {
            drawerTagDropdown.classList.add('hidden');
        }
        dropdownIndex = -1;
    }

    function buildDropdownItems(query) {
        const q = query.trim().toLowerCase();
        const assignedIds = new Set(currentTags.map((t) => t.id));

        // Filter catalog: not already assigned, matches query.
        const matches = teamCatalog.filter((t) => {
            return !assignedIds.has(t.id) && (!q || t.name.toLowerCase().includes(q));
        });

        const items = matches.map((t) => ({ id: t.id, name: t.name, count: t.count || 0 }));

        // Offer "create" at the top if query is non-empty and no exact match exists.
        if (q && !teamCatalog.some((t) => t.name.toLowerCase() === q)) {
            items.unshift({ isCreate: true, name: query.trim() });
        }

        return items;
    }

    // --- Tag operations ---

    function handleAddTag(tagId) {
        if (!currentWorkKey || !onTagAdd) {
            return;
        }

        const tag = teamCatalog.find((t) => t.id === tagId);
        if (!tag || currentTags.some((t) => t.id === tagId)) {
            return;
        }

        if (drawerTagInput) {
            drawerTagInput.value = '';
        }
        hideDropdown();

        // Optimistic update.
        currentTags = [...currentTags, tag].sort((a, b) =>
            a.name.localeCompare(b.name, undefined, { sensitivity: 'base' })
        );
        renderTagBadges();

        onTagAdd(currentWorkKey, tagId).then((updatedTags) => {
            if (updatedTags) {
                currentTags = updatedTags;
                renderTagBadges();
            }
        }).catch(() => {
            // Revert.
            currentTags = currentTags.filter((t) => t.id !== tagId);
            renderTagBadges();
        });
    }

    function handleCreateTag(name) {
        if (!currentWorkKey || !onTagCreate || !onTagAdd) {
            return;
        }

        if (drawerTagInput) {
            drawerTagInput.value = '';
        }
        hideDropdown();

        // Optimistic update with a temporary ID.
        const tempId = 'tmp_' + Date.now();
        const trimmedName = name.trim();
        currentTags = [...currentTags, { id: tempId, name: trimmedName }].sort((a, b) =>
            a.name.localeCompare(b.name, undefined, { sensitivity: 'base' })
        );
        renderTagBadges();

        onTagCreate(trimmedName).then((newTag) => {
            if (!newTag) {
                throw new Error('No tag returned');
            }
            // Replace temp entry and update catalog.
            currentTags = currentTags.filter((t) => t.id !== tempId);
            if (!teamCatalog.some((t) => t.id === newTag.id)) {
                teamCatalog = [...teamCatalog, newTag].sort((a, b) =>
                    a.name.localeCompare(b.name, undefined, { sensitivity: 'base' })
                );
            }
            return onTagAdd(currentWorkKey, newTag.id);
        }).then((updatedTags) => {
            if (updatedTags) {
                currentTags = updatedTags;
                renderTagBadges();
            }
        }).catch(() => {
            // Revert temp entry.
            currentTags = currentTags.filter((t) => t.id !== tempId);
            renderTagBadges();
        });
    }

    function handleRemoveTag(tagId) {
        if (!currentWorkKey || !onTagRemove) {
            return;
        }

        // Optimistic update.
        const previous = currentTags;
        currentTags = currentTags.filter((t) => t.id !== tagId);
        renderTagBadges();

        onTagRemove(currentWorkKey, tagId).then((updatedTags) => {
            if (updatedTags) {
                currentTags = updatedTags;
                renderTagBadges();
            }
        }).catch(() => {
            // Revert.
            currentTags = previous;
            renderTagBadges();
        });
    }

    // --- Input events ---

    if (drawerTagInput) {
        drawerTagInput.addEventListener('input', () => {
            if (isReadOnly) {
                return;
            }
            const query = drawerTagInput.value;
            if (!query.trim()) {
                // Show all unassigned tags when input is empty and focused.
                showDropdown(buildDropdownItems(''));
                return;
            }
            showDropdown(buildDropdownItems(query));
        });

        drawerTagInput.addEventListener('focus', () => {
            if (!isReadOnly) {
                showDropdown(buildDropdownItems(drawerTagInput.value));
            }
        });

        drawerTagInput.addEventListener('keydown', (event) => {
            if (event.key === 'Escape') {
                hideDropdown();
                drawerTagInput.blur();
            } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                event.preventDefault();
                if (!drawerTagDropdown || drawerTagDropdown.classList.contains('hidden')) return;
                const btns = drawerTagDropdown.querySelectorAll('button');
                if (btns.length === 0) return;
                const next = event.key === 'ArrowDown'
                    ? Math.min(dropdownIndex + 1, btns.length - 1)
                    : Math.max(dropdownIndex - 1, 0);
                setDropdownHighlight(next);
            } else if (event.key === 'Enter') {
                event.preventDefault();
                if (!drawerTagDropdown || drawerTagDropdown.classList.contains('hidden')) return;
                const btns = drawerTagDropdown.querySelectorAll('button');
                const target = dropdownIndex >= 0 ? btns[dropdownIndex] : btns[0];
                if (target) target.click();
            }
        });
    }

    document.addEventListener('click', (event) => {
        if (!drawerTagDropdown || drawerTagDropdown.classList.contains('hidden')) {
            return;
        }
        const input = drawerTagInput;
        const dropdown = drawerTagDropdown;
        if (input && !input.contains(event.target) && !dropdown.contains(event.target)) {
            hideDropdown();
        }
    });

    // --- Manage panel ---

    function renderManageTagsList() {
        if (!drawerManageTagsList) {
            return;
        }

        drawerManageTagsList.innerHTML = '';

        const isEmpty = teamCatalog.length === 0;
        if (drawerManageTagsEmpty) {
            drawerManageTagsEmpty.classList.toggle('hidden', !isEmpty);
        }

        for (const tag of teamCatalog) {
            const c = tagColor(tag.name);
            const row = document.createElement('div');
            row.className = 'flex items-center gap-2 py-1';
            row.dataset.tagId = String(tag.id);

            // Colored pill label.
            const pill = document.createElement('span');
            pill.className = 'px-2 py-0.5 text-xs font-medium rounded-full whitespace-nowrap';
            pill.style.color = c.color;
            pill.style.backgroundColor = c.bg;
            pill.textContent = tag.name;

            // Rename button.
            const renameBtn = document.createElement('button');
            renameBtn.type = 'button';
            renameBtn.className = 'ml-auto shrink-0 text-white/35 hover:text-white/70 transition-colors focus:outline-none';
            renameBtn.setAttribute('aria-label', 'Rename ' + tag.name);
            renameBtn.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" fill="currentColor" class="w-3.5 h-3.5"><path d="M13.488 2.513a1.75 1.75 0 0 0-2.475 0L2.68 10.845a.75.75 0 0 0-.207.404l-.5 3a.75.75 0 0 0 .878.878l3-.5a.75.75 0 0 0 .404-.207l8.33-8.33a1.75 1.75 0 0 0 0-2.475l-.097-.098Z"/></svg>';

            // Delete button.
            const deleteBtn = document.createElement('button');
            deleteBtn.type = 'button';
            deleteBtn.className = 'shrink-0 text-white/35 hover:text-red-400 transition-colors focus:outline-none';
            deleteBtn.setAttribute('aria-label', 'Delete ' + tag.name);
            deleteBtn.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" fill="currentColor" class="w-3.5 h-3.5"><path fill-rule="evenodd" d="M5 3.25V4H2.75a.75.75 0 0 0 0 1.5h.3l.815 8.15A1.5 1.5 0 0 0 5.357 15h5.285a1.5 1.5 0 0 0 1.493-1.35l.815-8.15h.3a.75.75 0 0 0 0-1.5H11v-.75A2.25 2.25 0 0 0 8.75 1h-1.5A2.25 2.25 0 0 0 5 3.25Zm2.25-.75a.75.75 0 0 0-.75.75V4h3v-.75a.75.75 0 0 0-.75-.75h-1.5ZM6.05 6a.75.75 0 0 1 .787.713l.275 5.5a.75.75 0 0 1-1.498.075l-.275-5.5A.75.75 0 0 1 6.05 6Zm3.9 0a.75.75 0 0 1 .712.787l-.275 5.5a.75.75 0 0 1-1.498-.075l.275-5.5A.75.75 0 0 1 9.95 6Z" clip-rule="evenodd"/></svg>';

            // Usage count.
            const countSpan = document.createElement('span');
            countSpan.className = 'text-xs text-white/30 shrink-0';
            countSpan.textContent = (tag.count || 0) > 0 ? String(tag.count) : '0';

            row.appendChild(pill);
            row.appendChild(countSpan);
            row.appendChild(renameBtn);
            row.appendChild(deleteBtn);
            drawerManageTagsList.appendChild(row);

            renameBtn.addEventListener('click', () => startRename(row, tag));
            deleteBtn.addEventListener('click', () => handleDeleteTag(tag.id));
        }
    }

    function startRename(row, tag) {
        row.innerHTML = '';

        const input = document.createElement('input');
        input.type = 'text';
        input.value = tag.name;
        input.className = 'min-w-0 flex-1 bg-black border border-white/20 px-2 py-0.5 text-xs text-white/85 focus:outline-none focus:border-[#E7FF02]';

        const confirmBtn = document.createElement('button');
        confirmBtn.type = 'button';
        confirmBtn.className = 'ml-auto shrink-0 text-white/50 hover:text-[#E7FF02] transition-colors focus:outline-none';
        confirmBtn.setAttribute('aria-label', 'Confirm rename');
        confirmBtn.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" fill="currentColor" class="w-3.5 h-3.5"><path fill-rule="evenodd" d="M12.416 3.376a.75.75 0 0 1 .208 1.04l-5 7.5a.75.75 0 0 1-1.154.114l-3-3a.75.75 0 0 1 1.06-1.06l2.353 2.353 4.493-6.74a.75.75 0 0 1 1.04-.207Z" clip-rule="evenodd"/></svg>';

        const cancelBtn = document.createElement('button');
        cancelBtn.type = 'button';
        cancelBtn.className = 'shrink-0 text-white/35 hover:text-white/70 transition-colors focus:outline-none';
        cancelBtn.setAttribute('aria-label', 'Cancel rename');
        cancelBtn.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" fill="currentColor" class="w-3 h-3"><path d="M3.72 3.72a.75.75 0 0 1 1.06 0L8 6.94l3.22-3.22a.75.75 0 1 1 1.06 1.06L9.06 8l3.22 3.22a.75.75 0 1 1-1.06 1.06L8 9.06l-3.22 3.22a.75.75 0 0 1-1.06-1.06L6.94 8 3.72 4.78a.75.75 0 0 1 0-1.06z"/></svg>';

        row.appendChild(input);
        row.appendChild(confirmBtn);
        row.appendChild(cancelBtn);
        input.focus();
        input.select();

        const commit = () => {
            const newName = input.value.trim();
            if (!newName || newName === tag.name || !onTagRename) {
                renderManageTagsList();
                return;
            }
            handleRenameTag(tag.id, newName);
        };

        confirmBtn.addEventListener('click', commit);
        cancelBtn.addEventListener('click', () => renderManageTagsList());
        input.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                commit();
            } else if (e.key === 'Escape') {
                renderManageTagsList();
            }
        });
    }

    function handleRenameTag(tagId, newName) {
        if (!onTagRename) {
            return;
        }

        // Optimistic update in catalog and current tags.
        teamCatalog = teamCatalog.map((t) => t.id === tagId ? { ...t, name: newName } : t);
        currentTags = currentTags.map((t) => t.id === tagId ? { ...t, name: newName } : t);
        renderManageTagsList();
        renderTagBadges();

        onTagRename(tagId, newName).catch(() => {
            // Revert isn't straightforward without the old name — just re-fetch is handled by caller.
        });
    }

    function handleDeleteTag(tagId) {
        if (!onTagDelete) {
            return;
        }

        // Optimistic update.
        const prevCatalog = teamCatalog;
        const prevTags = currentTags;
        teamCatalog = teamCatalog.filter((t) => t.id !== tagId);
        currentTags = currentTags.filter((t) => t.id !== tagId);
        renderManageTagsList();
        renderTagBadges();

        onTagDelete(tagId).catch(() => {
            teamCatalog = prevCatalog;
            currentTags = prevTags;
            renderManageTagsList();
            renderTagBadges();
        });
    }

    let manageOpen = false;

    if (drawerManageTagsBtn) {
        drawerManageTagsBtn.addEventListener('click', () => {
            manageOpen = !manageOpen;
            if (drawerManageTagsPanel) {
                drawerManageTagsPanel.classList.toggle('hidden', !manageOpen);
            }
            drawerManageTagsBtn.textContent = manageOpen ? 'Done' : 'Manage';
            if (manageOpen) {
                renderManageTagsList();
            }
        });
    }

    // --- Steps ---

    function renderSteps(steps) {
        if (!drawerSteps) {
            return;
        }

        drawerSteps.innerHTML = '';
        if (!steps || steps.length === 0) {
            drawerSteps.innerHTML = '<p class="text-xs text-white/45">No steps.</p>';
            return;
        }

        for (const step of steps) {
            const block = document.createElement('div');
            block.className = 'border border-white/10 p-4';
            block.innerHTML =
                '<p class="text-xs text-white/45">Step ' + step.stepNumber + '</p>' +
                '<div class="mt-3 grid grid-cols-1 gap-3">' +
                    '<input type="text" value="' + escHtml(step.stepSummary || '') + '" class="bg-black border border-white/15 px-3 py-2 text-sm text-white/85 focus:outline-none focus:border-[#E7FF02]" placeholder="Action" />' +
                    '<input type="text" value="' + escHtml(step.testData || '') + '" class="bg-black border border-white/15 px-3 py-2 text-sm text-white/85 focus:outline-none focus:border-[#E7FF02]" placeholder="Data" />' +
                    '<input type="text" value="' + escHtml(step.expectedResult || '') + '" class="bg-black border border-white/15 px-3 py-2 text-sm text-white/85 focus:outline-none focus:border-[#E7FF02]" placeholder="Expected" />' +
                '</div>';
            drawerSteps.appendChild(block);
        }
    }

    function setReadOnlyMode(readOnly) {
        if (!drawer) {
            return;
        }

        isReadOnly = Boolean(readOnly);
        const fields = drawer.querySelectorAll('input, textarea, select');
        fields.forEach((field) => {
            field.disabled = isReadOnly;
            field.classList.toggle('opacity-70', isReadOnly);
        });

        if (drawerSave) {
            drawerSave.disabled = isReadOnly;
            drawerSave.classList.toggle('hidden', isReadOnly);
        }

        if (drawerModeLabel) {
            drawerModeLabel.textContent = isReadOnly ? 'Preview test case' : 'Edit test case';
        }

        if (drawerFooterHint) {
            drawerFooterHint.textContent = isReadOnly
                ? 'Preview mode is read-only.'
                : 'Save updates and return to grid.';
        }

        // Show/hide tag input based on mode.
        const tagInputWrap = drawerTagInput ? drawerTagInput.parentElement : null;
        if (tagInputWrap) {
            tagInputWrap.classList.toggle('hidden', isReadOnly);
        }

        renderTagBadges();
    }

    function openByWorkKey(workKey, openOptions) {
        if (!drawer || !drawerPanel) {
            return;
        }

        const resolvedWorkKey = workKey || '—';
        const opts = openOptions || {};
        const testCase = getTestCaseById(resolvedWorkKey) || {};
        const title = testCase.summary || '';
        const status = testCase.status || '—';
        const updated = testCase.updatedOn || '—';

        currentWorkKey = resolvedWorkKey;
        currentTags = Array.isArray(testCase.tags) ? testCase.tags.map((t) => ({ id: t.id, name: t.name })) : [];

        if (drawerId) {
            drawerId.textContent = resolvedWorkKey;
        }
        if (drawerTitle) {
            drawerTitle.value = title;
        }
        if (drawerSummary) {
            drawerSummary.value = testCase.description || '';
        }
        if (drawerStatus) {
            drawerStatus.value = status;
        }
        if (drawerUpdated) {
            drawerUpdated.textContent = updated;
        }
        if (drawerAssignee) {
            drawerAssignee.textContent = testCase.assignee || '—';
        }
        if (drawerReporter) {
            drawerReporter.textContent = testCase.reporter || '—';
        }
        if (drawerCreatedOn) {
            drawerCreatedOn.textContent = testCase.createdOn || '—';
        }
        if (drawerTagInput) {
            drawerTagInput.value = '';
        }
        hideDropdown();
        manageOpen = false;
        if (drawerManageTagsPanel) {
            drawerManageTagsPanel.classList.add('hidden');
        }
        if (drawerManageTagsBtn) {
            drawerManageTagsBtn.textContent = 'Manage';
        }

        renderSteps(testCase.steps || []);
        setReadOnlyMode(Boolean(opts.readOnly));

        drawer.classList.remove('hidden');
        drawer.setAttribute('aria-hidden', 'false');
        requestAnimationFrame(() => {
            drawerPanel.classList.remove('translate-x-full');
        });
    }

    function closeDrawer() {
        if (!drawer || !drawerPanel) {
            return;
        }

        hideDropdown();
        currentWorkKey = null;
        drawerPanel.classList.add('translate-x-full');
        drawer.setAttribute('aria-hidden', 'true');
        window.setTimeout(() => {
            drawer.classList.add('hidden');
        }, 180);
    }

    function setTeamCatalog(catalog) {
        teamCatalog = Array.isArray(catalog) ? catalog : [];
    }

    function setCallbacks(callbacks) {
        if (callbacks.onTagAdd) { onTagAdd = callbacks.onTagAdd; }
        if (callbacks.onTagRemove) { onTagRemove = callbacks.onTagRemove; }
        if (callbacks.onTagCreate) { onTagCreate = callbacks.onTagCreate; }
        if (callbacks.onTagRename) { onTagRename = callbacks.onTagRename; }
        if (callbacks.onTagDelete) { onTagDelete = callbacks.onTagDelete; }
    }

    if (drawerBackdrop) {
        drawerBackdrop.addEventListener('click', closeDrawer);
    }
    if (drawerClose) {
        drawerClose.addEventListener('click', closeDrawer);
    }
    if (drawerSave) {
        drawerSave.addEventListener('click', closeDrawer);
    }

    return {
        close: closeDrawer,
        openByWorkKey,
        setTeamCatalog,
        setCallbacks
    };
}
