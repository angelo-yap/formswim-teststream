// Color palette for custom tags
const TAG_COLORS = [
    { label: 'Red',    value: '#ef4444' },
    { label: 'Orange', value: '#f97316' },
    { label: 'Yellow', value: '#eab308' },
    { label: 'Green',  value: '#22c55e' },
    { label: 'Teal',   value: '#14b8a6' },
    { label: 'Blue',   value: '#3b82f6' },
    { label: 'Violet', value: '#8b5cf6' },
    { label: 'Pink',   value: '#ec4899' },
    { label: 'Rose',   value: '#f43f5e' },
    { label: 'Gray',   value: '#6b7280' },
];

function getCsrfHeaders() {
    const headerName = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';
    const token = document.querySelector('meta[name="_csrf"]')?.content || '';
    return { [headerName]: token };
}

function escHtml(value) {
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

export function createWorkspaceTags({ onTagsChanged }) {
    let popover = null;
    let currentAnchor = null;
    let currentWorkKey = null;
    let allTags = [];
    let assignedTagIds = new Set();
    let newTagColor = TAG_COLORS[0].value;

    function loadAllTags() {
        return fetch('/api/custom-tags')
            .then((r) => r.ok ? r.json() : [])
            .then((data) => { allTags = Array.isArray(data) ? data : []; });
    }

    function saveTags(workKey, tagIds) {
        return fetch('/api/testcases/' + encodeURIComponent(workKey) + '/custom-tags', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json', ...getCsrfHeaders() },
            body: JSON.stringify(tagIds)
        }).then((r) => r.ok ? r.json() : Promise.reject(new Error('Failed to save tags')));
    }

    function createTag(name, color) {
        return fetch('/api/custom-tags', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', ...getCsrfHeaders() },
            body: JSON.stringify({ name, color })
        }).then((r) => r.json().then((body) => {
            if (!r.ok) throw new Error(body?.message || 'Failed to create tag');
            return body;
        }));
    }

    function deleteTag(tagId) {
        return fetch('/api/custom-tags/' + encodeURIComponent(tagId), {
            method: 'DELETE',
            headers: getCsrfHeaders()
        }).then((r) => { if (!r.ok && r.status !== 204) throw new Error('Failed to delete tag'); });
    }

    function renderPopover() {
        if (!popover) return;

        const list = allTags.map((tag) => {
            const checked = assignedTagIds.has(tag.id) ? 'checked' : '';
            return `<label class="flex items-center gap-2 px-3 py-1.5 hover:bg-white/5 cursor-pointer group">
                <input type="checkbox" class="ws-tag-check h-3.5 w-3.5 accent-[#E7FF02]" data-tag-id="${tag.id}" ${checked} />
                <span class="inline-block w-2.5 h-2.5 rounded-full shrink-0" style="background:${escHtml(tag.color)}"></span>
                <span class="text-xs text-white/80 flex-1 truncate">${escHtml(tag.name)}</span>
                <button type="button" class="ws-tag-delete hidden group-hover:inline-flex text-white/30 hover:text-red-400 text-xs px-1" data-tag-id="${tag.id}" aria-label="Delete tag">✕</button>
            </label>`;
        }).join('');

        const colorSwatches = TAG_COLORS.map((c) => {
            const selected = c.value === newTagColor ? 'ring-2 ring-white/60' : '';
            return `<button type="button" class="ws-color-swatch w-5 h-5 rounded-full ${selected}" style="background:${c.value}" data-color="${escHtml(c.value)}" aria-label="${escHtml(c.label)}"></button>`;
        }).join('');

        popover.innerHTML = `
            <div class="text-[10px] uppercase tracking-wider text-white/40 px-3 pt-3 pb-1">Assign tags</div>
            <div class="ws-tag-list max-h-48 overflow-y-auto">${list || '<p class="px-3 py-2 text-xs text-white/40">No tags yet.</p>'}</div>
            <div class="border-t border-white/10 px-3 pt-2 pb-3">
                <div class="text-[10px] uppercase tracking-wider text-white/40 mb-1.5">New tag</div>
                <div class="flex items-center gap-2 flex-wrap mb-1.5">${colorSwatches}</div>
                <div class="flex items-center gap-2">
                    <input id="wsNewTagName" type="text" maxlength="50" placeholder="Tag name"
                        class="flex-1 min-w-0 bg-black border border-white/20 px-2 py-1 text-xs text-white/85 focus:outline-none focus:border-[#E7FF02]" />
                    <button type="button" id="wsNewTagCreate" class="px-2 py-1 border border-white/20 hover:border-[#E7FF02] hover:text-[#E7FF02] text-xs whitespace-nowrap">Add</button>
                </div>
                <p id="wsNewTagError" class="text-xs text-red-400 mt-1 hidden"></p>
            </div>`;

        // Checkbox toggles
        popover.querySelectorAll('.ws-tag-check').forEach((checkbox) => {
            checkbox.addEventListener('change', () => {
                const tagId = Number(checkbox.dataset.tagId);
                if (checkbox.checked) {
                    assignedTagIds.add(tagId);
                } else {
                    assignedTagIds.delete(tagId);
                }
                saveTags(currentWorkKey, Array.from(assignedTagIds))
                    .then((updatedTags) => {
                        if (typeof onTagsChanged === 'function') {
                            onTagsChanged({ workKey: currentWorkKey, tags: updatedTags });
                        }
                    })
                    .catch(console.error);
            });
        });

        // Delete buttons
        popover.querySelectorAll('.ws-tag-delete').forEach((btn) => {
            btn.addEventListener('click', (e) => {
                e.preventDefault();
                const tagId = Number(btn.dataset.tagId);
                deleteTag(tagId)
                    .then(() => {
                        allTags = allTags.filter((t) => t.id !== tagId);
                        assignedTagIds.delete(tagId);
                        return saveTags(currentWorkKey, Array.from(assignedTagIds));
                    })
                    .then((updatedTags) => {
                        renderPopover();
                        if (typeof onTagsChanged === 'function') {
                            onTagsChanged({ workKey: currentWorkKey, tags: updatedTags });
                        }
                    })
                    .catch(console.error);
            });
        });

        // Color swatch selection
        popover.querySelectorAll('.ws-color-swatch').forEach((btn) => {
            btn.addEventListener('click', () => {
                const savedName = popover.querySelector('#wsNewTagName')?.value || '';
                newTagColor = btn.dataset.color;
                renderPopover();
                const nameInput = popover.querySelector('#wsNewTagName');
                if (nameInput) {
                    nameInput.value = savedName;
                    nameInput.focus();
                }
            });
        });

        // Create new tag
        const createBtn = popover.querySelector('#wsNewTagCreate');
        const nameInput = popover.querySelector('#wsNewTagName');
        const errorEl = popover.querySelector('#wsNewTagError');

        function handleCreate() {
            const name = (nameInput?.value || '').trim();
            if (!name) {
                showError('Tag name is required.');
                return;
            }
            createTag(name, newTagColor)
                .then((tag) => {
                    allTags.push(tag);
                    allTags.sort((a, b) => a.name.localeCompare(b.name));
                    assignedTagIds.add(tag.id);
                    return saveTags(currentWorkKey, Array.from(assignedTagIds));
                })
                .then((updatedTags) => {
                    renderPopover();
                    if (typeof onTagsChanged === 'function') {
                        onTagsChanged({ workKey: currentWorkKey, tags: updatedTags });
                    }
                })
                .catch((err) => showError(err.message || 'Could not create tag.'));
        }

        function showError(msg) {
            if (errorEl) {
                errorEl.textContent = msg;
                errorEl.classList.remove('hidden');
            }
        }

        if (createBtn) createBtn.addEventListener('click', handleCreate);
        if (nameInput) {
            nameInput.addEventListener('keydown', (e) => {
                if (e.key === 'Enter') { e.preventDefault(); handleCreate(); }
                if (e.key === 'Escape') close();
            });
        }
    }

    function positionPopover(anchor) {
        if (!popover || !anchor) return;
        const rect = anchor.getBoundingClientRect();
        const spaceBelow = window.innerHeight - rect.bottom;
        const spaceAbove = rect.top;
        const popoverHeight = popover.offsetHeight || 300;

        popover.style.left = Math.min(rect.left, window.innerWidth - 280) + 'px';
        if (spaceBelow >= popoverHeight || spaceBelow >= spaceAbove) {
            popover.style.top = (rect.bottom + 4) + 'px';
        } else {
            popover.style.top = (rect.top - popoverHeight - 4) + 'px';
        }
    }

    function open({ workKey, anchor, testCase }) {
        if (currentAnchor === anchor && popover && !popover.classList.contains('hidden')) {
            close();
            return;
        }
        close();

        currentWorkKey = workKey;
        currentAnchor = anchor;
        assignedTagIds = new Set(
            (Array.isArray(testCase?.customTags) ? testCase.customTags : []).map((t) => t.id)
        );

        popover = document.createElement('div');
        popover.id = 'wsTagPopover';
        popover.className = 'fixed z-50 w-64 border border-white/20 bg-black shadow-xl';
        document.body.appendChild(popover);

        loadAllTags().then(() => {
            renderPopover();
            positionPopover(anchor);
        });

        // Close on outside click
        setTimeout(() => {
            document.addEventListener('mousedown', onOutsideClick);
        }, 0);
    }

    function onOutsideClick(e) {
        if (popover && !popover.contains(e.target) && e.target !== currentAnchor && !currentAnchor?.contains(e.target)) {
            close();
        }
    }

    function close() {
        if (popover) {
            popover.remove();
            popover = null;
        }
        currentAnchor = null;
        currentWorkKey = null;
        document.removeEventListener('mousedown', onOutsideClick);
    }

    return { open, close, loadAllTags };
}
