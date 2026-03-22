import { escHtml } from './grid.js';

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
    const drawerModeLabel = options.drawerModeLabel;
    const drawerFooterHint = options.drawerFooterHint;
    const getTestCaseById = options.getTestCaseById;

    // Callbacks provided by page.js for API calls.
    let onTagAdd = options.onTagAdd || null;       // (workKey, tagId) => Promise<Tag[]>
    let onTagRemove = options.onTagRemove || null; // (workKey, tagId) => Promise<Tag[]>
    let onTagCreate = options.onTagCreate || null; // (name) => Promise<Tag>

    // Live state for the open drawer.
    let currentWorkKey = null;
    let currentTags = []; // [{id, name}] assigned to the open test case
    let teamCatalog = []; // [{id, name}] full team tag list
    let isReadOnly = false;

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
            badge.className = 'inline-flex items-center gap-1 px-2 py-1 text-xs border border-white/15 text-white/75';

            const label = document.createElement('span');
            label.textContent = tag.name;
            badge.appendChild(label);

            if (!isReadOnly) {
                const removeBtn = document.createElement('button');
                removeBtn.type = 'button';
                removeBtn.className = 'ml-0.5 text-white/40 hover:text-white/80 focus:outline-none';
                removeBtn.setAttribute('aria-label', 'Remove tag ' + tag.name);
                removeBtn.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" fill="currentColor" class="w-3 h-3"><path d="M3.72 3.72a.75.75 0 0 1 1.06 0L8 6.94l3.22-3.22a.75.75 0 1 1 1.06 1.06L9.06 8l3.22 3.22a.75.75 0 1 1-1.06 1.06L8 9.06l-3.22 3.22a.75.75 0 0 1-1.06-1.06L6.94 8 3.72 4.78a.75.75 0 0 1 0-1.06z"/></svg>';
                removeBtn.addEventListener('click', () => handleRemoveTag(tag.id));
                badge.appendChild(removeBtn);
            }

            drawerTagBadges.appendChild(badge);
        }
    }

    // --- Dropdown ---

    function showDropdown(items) {
        if (!drawerTagDropdown) {
            return;
        }

        drawerTagDropdown.innerHTML = '';

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
                row.textContent = item.name;
                row.addEventListener('click', () => handleAddTag(item.id));
            }

            drawerTagDropdown.appendChild(row);
        }

        drawerTagDropdown.classList.remove('hidden');
    }

    function hideDropdown() {
        if (drawerTagDropdown) {
            drawerTagDropdown.classList.add('hidden');
        }
    }

    function buildDropdownItems(query) {
        const q = query.trim().toLowerCase();
        const assignedIds = new Set(currentTags.map((t) => t.id));

        // Filter catalog: not already assigned, matches query.
        const matches = teamCatalog.filter((t) => {
            return !assignedIds.has(t.id) && (!q || t.name.toLowerCase().includes(q));
        });

        const items = matches.map((t) => ({ id: t.id, name: t.name }));

        // Offer "create" if query is non-empty and no exact match exists.
        if (q && !teamCatalog.some((t) => t.name.toLowerCase() === q)) {
            items.push({ isCreate: true, name: query.trim() });
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
        if (callbacks.onTagAdd) {
            onTagAdd = callbacks.onTagAdd;
        }
        if (callbacks.onTagRemove) {
            onTagRemove = callbacks.onTagRemove;
        }
        if (callbacks.onTagCreate) {
            onTagCreate = callbacks.onTagCreate;
        }
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
