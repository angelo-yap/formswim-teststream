export function createWorkspaceDndController(options) {
    const tbody = options.tbody;
    const folderTree = options.folderTree;
    const selection = options.selection;
    const normalizeFolder = options.normalizeFolder;
    const moveWorkKeys = options.moveWorkKeys;

    let activeDragPayload = null;
    let activeDropTargetEl = null;
    let activeDragImageHolder = null;

    function setDropCursor(isValid) {
        document.body.style.cursor = isValid ? 'copy' : 'grabbing';
    }

    function clearDropCursor() {
        document.body.style.cursor = '';
    }

    function createDragImage(workKeys) {
        const holder = document.createElement('div');
        holder.className = 'fixed -left-[9999px] -top-[9999px] pointer-events-none z-50';

        const chip = document.createElement('div');
        chip.className = 'inline-flex items-center gap-2 border border-[#E7FF02]/40 bg-black/95 px-3 py-2 text-xs text-white shadow-lg';

        if (Array.isArray(workKeys) && workKeys.length > 1) {
            const glyph = document.createElement('span');
            glyph.className = 'text-white/70';
            glyph.textContent = '::';

            const label = document.createElement('span');
            label.className = 'text-white/90';
            label.textContent = 'Move selected';

            const badge = document.createElement('span');
            badge.className = 'inline-flex items-center justify-center min-w-[1.5rem] h-6 px-2 rounded-full bg-[#E7FF02] text-black font-bold text-xs';
            badge.textContent = String(workKeys.length);

            chip.appendChild(glyph);
            chip.appendChild(label);
            chip.appendChild(badge);
        } else {
            const value = Array.isArray(workKeys) && workKeys.length > 0 ? workKeys[0] : 'Move item';
            chip.textContent = String(value);
        }

        holder.appendChild(chip);
        document.body.appendChild(holder);

        return { holder, chip };
    }

    function clearDropHover() {
        if (activeDropTargetEl) {
            activeDropTargetEl.classList.remove('ws-folder-drop-hover');
            activeDropTargetEl = null;
        }
    }

    function setDropHover(targetEl) {
        if (activeDropTargetEl === targetEl) {
            return;
        }

        clearDropHover();
        if (targetEl) {
            targetEl.classList.add('ws-folder-drop-hover');
            activeDropTargetEl = targetEl;
        }
    }

    function getValidDropTarget(event) {
        const candidate = event.target?.closest?.('[data-drop-target="folder"]');
        if (!candidate || !folderTree || !folderTree.contains(candidate)) {
            return null;
        }

        return candidate;
    }

    if (tbody) {
        tbody.addEventListener('dragstart', (event) => {
            const grabHandle = event.target?.closest?.('.ws-row-grab');
            if (!grabHandle) {
                return;
            }

            const workKey = grabHandle.dataset.workKey || grabHandle.closest('[data-work-key]')?.dataset.workKey || '';
            if (!workKey || !event.dataTransfer) {
                return;
            }

            const selectedIds = selection.getSelectedIds();
            const workKeys = selectedIds.includes(workKey) ? selectedIds : [workKey];
            activeDragPayload = {
                workKeys,
                source: 'drag'
            };

            if (activeDragImageHolder) {
                activeDragImageHolder.remove();
                activeDragImageHolder = null;
            }

            const dragImage = createDragImage(workKeys);
            activeDragImageHolder = dragImage.holder;

            event.dataTransfer.effectAllowed = 'move';
            event.dataTransfer.setData('text/plain', JSON.stringify(activeDragPayload));
            event.dataTransfer.setDragImage(dragImage.chip, 16, 16);
            clearDropCursor();
        });

        tbody.addEventListener('dragend', () => {
            activeDragPayload = null;
            if (activeDragImageHolder) {
                activeDragImageHolder.remove();
                activeDragImageHolder = null;
            }
            clearDropHover();
            clearDropCursor();
        });
    }

    if (folderTree) {
        folderTree.addEventListener('dragenter', (event) => {
            if (!activeDragPayload) {
                return;
            }

            const target = getValidDropTarget(event);
            setDropHover(target);
            setDropCursor(Boolean(target));
        });

        folderTree.addEventListener('dragover', (event) => {
            if (!activeDragPayload) {
                return;
            }

            const target = getValidDropTarget(event);
            setDropHover(target);
            setDropCursor(Boolean(target));
            if (!target) {
                return;
            }

            event.preventDefault();
            if (event.dataTransfer) {
                event.dataTransfer.dropEffect = 'move';
            }
        });

        folderTree.addEventListener('dragleave', (event) => {
            if (!activeDragPayload) {
                return;
            }

            const next = event.relatedTarget;
            if (!next || !folderTree.contains(next)) {
                clearDropHover();
                setDropCursor(false);
            }
        });

        folderTree.addEventListener('drop', async (event) => {
            if (!activeDragPayload) {
                return;
            }

            const target = getValidDropTarget(event);
            clearDropHover();
            clearDropCursor();

            if (!target) {
                activeDragPayload = null;
                return;
            }

            event.preventDefault();
            const targetFolder = normalizeFolder(target.dataset.folderPath || '');
            const payload = activeDragPayload;
            activeDragPayload = null;

            await moveWorkKeys({
                workKeys: payload.workKeys,
                targetFolder,
                source: 'drag'
            });
        });
    }

    return {};
}
