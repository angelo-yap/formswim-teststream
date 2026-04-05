export function createWorkspaceFolderTree(options) {
    const uiState = options.uiState;
    const api = options.api;
    const folderTree = options.folderTree;
    const folderLoading = options.folderLoading;
    const folderEmpty = options.folderEmpty;
    const newFolderButton = options.newFolderButton;
    const sidebar = options.sidebar;
    const sidebarResizeHandle = options.sidebarResizeHandle;
    const sidebarContent = options.sidebarContent;
    const sidebarTitle = options.sidebarTitle;
    const sidebarInner = options.sidebarInner;
    const sidebarHeader = options.sidebarHeader;
    const showNotice = options.showNotice;
    const onFolderChanged = options.onFolderChanged;
    const SIDEBAR_DEFAULT_WIDTH = 320;
    const SIDEBAR_MIN_OPEN_WIDTH = 48;
    const SIDEBAR_MAX_WIDTH = 560;
    const SIDEBAR_CLOSE_SNAP_WIDTH = 24;

    let folderTreeModel = createFolderTreeModel([], []);
    let folderNodeByPath = new Map();
    let inlineEditorState = null;
    let contextMenuEl = null;
    let contextMenuCleanupBound = false;
    let activeFolderDrag = null;
    let activeFolderDropTargetEl = null;
    let activeFolderDropMode = null;
    let isFolderLoading = false;
    let pendingDeletePrompt = null;
    let sidebarWidthPx = SIDEBAR_DEFAULT_WIDTH;

    function snapshotExpandedState(model) {
        const expandedByPath = new Map();
        if (!model || !model.pathIndex) {
            return expandedByPath;
        }

        for (const [path, node] of model.pathIndex.entries()) {
            if (!path || !node) {
                continue;
            }
            expandedByPath.set(path, Boolean(node.expanded));
        }
        return expandedByPath;
    }

    function applyExpandedState(model, expandedByPath) {
        if (!model || !model.pathIndex || !expandedByPath) {
            return;
        }

        for (const [path, node] of model.pathIndex.entries()) {
            if (!path || !node) {
                continue;
            }
            if (expandedByPath.has(path)) {
                node.expanded = expandedByPath.get(path);
            }
        }
    }

    function expandPath(path) {
        const normalized = uiState.normalizeFolder(path || '');
        if (!normalized || !folderTreeModel || !folderTreeModel.pathIndex) {
            return;
        }

        const segments = normalized.split('/').filter(Boolean);
        let currentPath = '';
        for (const segment of segments) {
            currentPath = currentPath ? currentPath + '/' + segment : segment;
            const node = folderTreeModel.pathIndex.get(currentPath);
            if (node) {
                node.expanded = true;
            }
        }
    }

    function clampSidebarWidth(widthPx) {
        return Math.min(Math.max(widthPx, SIDEBAR_MIN_OPEN_WIDTH), SIDEBAR_MAX_WIDTH);
    }

    function applySidebarWidth(widthPx) {
        const numericWidth = Math.max(Number(widthPx) || 0, 0);
        const isSidebarExpanded = numericWidth > 0;
        uiState.setSidebarExpanded(isSidebarExpanded);

        if (sidebar) {
            if (isSidebarExpanded) {
                sidebar.style.width = String(numericWidth) + 'px';
                sidebar.style.minWidth = String(numericWidth) + 'px';
                sidebar.style.borderRight = '';
                // Keep directory content clipped so it never overlays into the grid.
                sidebar.style.overflow = 'hidden';
            } else {
                sidebar.style.width = '0px';
                sidebar.style.minWidth = '0px';
                sidebar.style.borderRight = '0';
                sidebar.style.overflow = 'hidden';
            }
        }

        if (sidebarContent) {
            sidebarContent.classList.toggle('hidden', !isSidebarExpanded);
        }

        if (sidebarTitle) {
            sidebarTitle.classList.toggle('hidden', !isSidebarExpanded);
        }

        if (sidebarInner) {
            sidebarInner.style.padding = isSidebarExpanded ? '' : '0.25rem';
            sidebarInner.style.overflow = 'hidden';
        }

        if (sidebarHeader) {
            sidebarHeader.style.justifyContent = isSidebarExpanded ? '' : 'center';
        }

        if (sidebarResizeHandle) {
            sidebarResizeHandle.setAttribute('aria-valuenow', String(Math.round(numericWidth)));
            sidebarResizeHandle.classList.toggle('bg-white/10', isSidebarExpanded);
            sidebarResizeHandle.classList.toggle('bg-white/20', !isSidebarExpanded);
        }

        if (isSidebarExpanded) {
            sidebarWidthPx = numericWidth;
        }
    }

    function setSidebarWidth(widthPx) {
        const numericWidth = Number(widthPx) || 0;
        if (numericWidth <= SIDEBAR_CLOSE_SNAP_WIDTH) {
            applySidebarWidth(0);
            return;
        }

        applySidebarWidth(clampSidebarWidth(numericWidth));
    }

    function setSidebarExpanded(expanded) {
        if (expanded) {
            const nextWidth = sidebarWidthPx > 0 ? sidebarWidthPx : SIDEBAR_DEFAULT_WIDTH;
            setSidebarWidth(nextWidth);
            return;
        }

        applySidebarWidth(0);
    }

    function createFolderTreeModel(folderNames, folderNodes) {
        const root = {
            name: '',
            id: null,
            path: '',
            parentPath: '',
            children: new Map(),
            expanded: true
        };

        const pathIndex = new Map();
        pathIndex.set('', root);

        const normalizedNodes = Array.isArray(folderNodes) ? folderNodes : [];

        if (normalizedNodes.length > 0) {
            for (const rawNode of normalizedNodes) {
                const normalized = uiState.normalizeFolder(rawNode?.path || '');
                const name = String(rawNode?.name || '').trim();
                if (!normalized || !name) {
                    continue;
                }

                const parts = normalized.split('/').filter(Boolean);
                let current = root;
                let currentPath = '';

                for (let index = 0; index < parts.length; index++) {
                    const part = parts[index];
                    const parentPath = currentPath;
                    currentPath = currentPath ? currentPath + '/' + part : part;

                    if (!current.children.has(part)) {
                        current.children.set(part, {
                            id: null,
                            name: part,
                            path: currentPath,
                            parentPath,
                            children: new Map(),
                            expanded: false
                        });
                    }

                    const child = current.children.get(part);
                    if (index === parts.length - 1) {
                        child.id = rawNode?.id ?? null;
                        child.name = name;
                    }

                    pathIndex.set(currentPath, child);
                    current = child;
                }
            }

            return { root, pathIndex };
        }

        for (const folderName of folderNames || []) {
            const normalized = uiState.normalizeFolder(folderName);
            if (!normalized) {
                continue;
            }

            const parts = normalized.split('/').filter(Boolean);
            let current = root;
            let currentPath = '';

            for (const part of parts) {
                const parentPath = currentPath;
                currentPath = currentPath ? currentPath + '/' + part : part;
                if (!current.children.has(part)) {
                    current.children.set(part, {
                        id: null,
                        name: part,
                        path: currentPath,
                        parentPath,
                        children: new Map(),
                        expanded: false
                    });
                }
                current = current.children.get(part);
                pathIndex.set(currentPath, current);
            }
        }

        return { root, pathIndex };
    }

    function getNodeMetaByPath(path) {
        const normalized = uiState.normalizeFolder(path || '');
        if (!normalized) {
            return null;
        }
        return folderNodeByPath.get(normalized) || null;
    }

    function clearInlineEditor() {
        inlineEditorState = null;
    }

    function emitFolderChanged(path) {
        if (typeof onFolderChanged === 'function') {
            onFolderChanged(path || '');
        }
    }

    function notify(type, message) {
        if (typeof showNotice === 'function') {
            showNotice(type, message);
            return;
        }
        if (message) {
            window.alert(String(message));
        }
    }

    function dismissDeletePrompt(confirmed) {
        if (!pendingDeletePrompt) {
            return;
        }

        const { root, resolve, onKeyDown } = pendingDeletePrompt;
        pendingDeletePrompt = null;

        if (onKeyDown) {
            document.removeEventListener('keydown', onKeyDown);
        }
        if (root && root.parentNode) {
            root.parentNode.removeChild(root);
        }
        resolve(Boolean(confirmed));
    }

    function confirmDeleteFolder(path) {
        if (pendingDeletePrompt) {
            dismissDeletePrompt(false);
        }

        return new Promise((resolve) => {
            const root = document.createElement('div');
            root.className = 'fixed right-4 top-4 z-[1400] w-[min(28rem,calc(100vw-2rem))]';

            const notice = document.createElement('div');
            notice.className = 'border border-white/10 bg-black/95 px-4 py-3 text-sm text-white/80 shadow-[0_18px_48px_rgba(0,0,0,0.55)]';
            notice.style.borderColor = 'rgba(255, 255, 255, 0.15)';

            const headingRow = document.createElement('div');
            headingRow.className = 'flex items-start justify-between gap-3';

            const headingWrap = document.createElement('div');
            headingWrap.className = 'min-w-0';

            const badge = document.createElement('span');
            badge.className = 'font-bold text-black px-2 py-1 mr-3 bg-white/70';
            badge.textContent = 'Confirm';

            const heading = document.createElement('span');
            heading.className = 'font-medium text-white';
            heading.textContent = 'Delete folder?';

            const message = document.createElement('p');
            message.className = 'mt-2 break-words text-white/75';
            message.textContent = 'Delete "' + String(path || '') + '"? Only empty folders can be deleted.';

            const actions = document.createElement('div');
            actions.className = 'mt-3 flex items-center justify-end gap-2';

            const cancelButton = document.createElement('button');
            cancelButton.type = 'button';
            cancelButton.className = 'px-3 py-1.5 border border-white/20 hover:border-white/40 text-white/85 hover:text-white transition-colors text-xs';
            cancelButton.textContent = 'Cancel';

            const deleteButton = document.createElement('button');
            deleteButton.type = 'button';
            deleteButton.className = 'px-3 py-1.5 border text-black font-semibold text-xs transition-colors';
            deleteButton.style.borderColor = '#E7FF02';
            deleteButton.style.backgroundColor = '#E7FF02';
            deleteButton.textContent = 'Delete';

            headingWrap.appendChild(badge);
            headingWrap.appendChild(heading);
            headingWrap.appendChild(message);

            actions.appendChild(cancelButton);
            actions.appendChild(deleteButton);

            headingRow.appendChild(headingWrap);
            notice.appendChild(headingRow);
            notice.appendChild(actions);
            root.appendChild(notice);
            document.body.appendChild(root);

            const onKeyDown = (event) => {
                if (event.key === 'Escape') {
                    event.preventDefault();
                    dismissDeletePrompt(false);
                }
            };

            pendingDeletePrompt = {
                root,
                resolve,
                onKeyDown
            };

            document.addEventListener('keydown', onKeyDown);

            cancelButton.addEventListener('click', () => dismissDeletePrompt(false));
            deleteButton.addEventListener('click', () => dismissDeletePrompt(true));

            window.requestAnimationFrame(() => {
                deleteButton.focus();
            });
        });
    }

    function closeContextMenu() {
        if (contextMenuEl && contextMenuEl.parentNode) {
            contextMenuEl.parentNode.removeChild(contextMenuEl);
        }
        contextMenuEl = null;
    }

    function bindContextMenuCleanup() {
        if (contextMenuCleanupBound) {
            return;
        }
        contextMenuCleanupBound = true;
        document.addEventListener('click', () => closeContextMenu());
        document.addEventListener('keydown', (event) => {
            if (event.key === 'Escape') {
                closeContextMenu();
            }
        });
        window.addEventListener('resize', () => closeContextMenu());
    }

    function openContextMenu(node, event) {
        closeContextMenu();
        bindContextMenuCleanup();

        const menu = document.createElement('div');
        menu.className = 'fixed z-[1200] min-w-[10rem] border border-white/20 bg-black text-white shadow-xl';
        menu.style.left = String(event.clientX) + 'px';
        menu.style.top = String(event.clientY) + 'px';

        const actions = [
            {
                label: 'New Folder',
                handler: () => {
                    startInlineCreate(node);
                }
            },
            {
                label: 'Rename',
                disabled: !node.id,
                handler: () => {
                    startInlineRename(node);
                }
            },
            {
                label: 'Delete',
                disabled: !node.id,
                handler: () => {
                    handleDeleteFolder(node);
                }
            }
        ];

        for (const action of actions) {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'block w-full px-3 py-2 text-left text-sm transition-colors';
            if (action.disabled) {
                button.classList.add('text-white/35', 'cursor-not-allowed');
            } else {
                button.classList.add('hover:bg-white/10');
            }
            button.textContent = action.label;
            button.disabled = Boolean(action.disabled);
            button.addEventListener('click', (clickEvent) => {
                clickEvent.stopPropagation();
                closeContextMenu();
                if (!action.disabled) {
                    action.handler();
                }
            });
            menu.appendChild(button);
        }

        document.body.appendChild(menu);
        contextMenuEl = menu;
    }

    function startInlineCreate(parentNode) {
        const parentPath = parentNode?.path || '';
        const parentMeta = parentPath ? getNodeMetaByPath(parentPath) : null;

        if (parentPath && !parentMeta?.id) {
            notify('error', 'Cannot create subfolder here because this folder id is unavailable.');
            return;
        }

        inlineEditorState = {
            mode: 'create',
            parentPath,
            parentId: parentMeta?.id ?? null,
            value: ''
        };
        if (parentPath) {
            expandPath(parentPath);
        }
        renderFolderTree();
    }

    function startInlineRename(node) {
        const meta = getNodeMetaByPath(node.path);
        if (!meta?.id) {
            notify('error', 'Cannot rename this folder because its id is unavailable.');
            return;
        }

        inlineEditorState = {
            mode: 'rename',
            targetId: meta.id,
            targetPath: node.path,
            value: node.name
        };
        renderFolderTree();
    }

    async function submitInlineEditor(value) {
        if (!inlineEditorState) {
            return;
        }

        const name = String(value || '').trim();
        if (!name) {
            clearInlineEditor();
            renderFolderTree();
            return;
        }

        try {
            if (inlineEditorState.mode === 'create') {
                if (typeof api.createFolder !== 'function') {
                    throw new Error('Folder create API is unavailable (frontend assets may be out of sync).');
                }
                const created = await api.createFolder({
                    name,
                    parentId: inlineEditorState.parentId
                });

                clearInlineEditor();
                const currentSelection = uiState.getSelectedFolder();
                await loadFolders();

                if (created?.path) {
                    uiState.setSelectedFolder(uiState.normalizeFolder(created.path));
                    renderFolderTree();
                    emitFolderChanged(uiState.getSelectedFolder());
                    notify('success', 'Folder created.');
                } else if (currentSelection) {
                    emitFolderChanged(currentSelection);
                }
                return;
            }

            if (inlineEditorState.mode === 'rename') {
                if (typeof api.updateFolder !== 'function') {
                    throw new Error('Folder update API is unavailable (frontend assets may be out of sync).');
                }
                const updated = await api.updateFolder(inlineEditorState.targetId, { name });
                const previousSelected = uiState.getSelectedFolder();
                const renamedPath = inlineEditorState.targetPath;

                clearInlineEditor();
                await loadFolders();

                if (previousSelected && renamedPath && updated?.path) {
                    const normalizedRenamedPath = uiState.normalizeFolder(renamedPath);
                    const normalizedUpdatedPath = uiState.normalizeFolder(updated.path);

                    if (previousSelected === normalizedRenamedPath) {
                        uiState.setSelectedFolder(normalizedUpdatedPath);
                    } else if (previousSelected.startsWith(normalizedRenamedPath + '/')) {
                        const suffix = previousSelected.slice(normalizedRenamedPath.length);
                        uiState.setSelectedFolder(uiState.normalizeFolder(normalizedUpdatedPath + suffix));
                    }
                }

                emitFolderChanged(uiState.getSelectedFolder());

                notify('success', 'Folder renamed.');
            }
        } catch (error) {
            notify('error', error?.message || 'Folder operation failed.');
            clearInlineEditor();
            renderFolderTree();
        }
    }

    async function handleDeleteFolder(node) {
        if (node?.children && node.children.size > 0) {
            notify('error', 'Folder cannot be deleted because it contains subfolders.');
            return;
        }

        const meta = getNodeMetaByPath(node.path);
        if (!meta?.id) {
            notify('error', 'Cannot delete this folder because its id is unavailable.');
            return;
        }

        const confirmed = await confirmDeleteFolder(node.path);
        if (!confirmed) {
            return;
        }

        try {
            if (typeof api.deleteFolder !== 'function') {
                throw new Error('Folder delete API is unavailable (frontend assets may be out of sync).');
            }
            await api.deleteFolder(meta.id);
            const selected = uiState.getSelectedFolder();
            if (selected && (selected === node.path || selected.startsWith(node.path + '/'))) {
                uiState.setSelectedFolder('');
                emitFolderChanged('');
            }
            await loadFolders();
            notify('success', 'Folder deleted.');
        } catch (error) {
            notify('error', error?.message || 'Folder delete failed.');
        }
    }

    function isInvalidFolderDrop(sourcePath, candidateParentPath) {
        if (!sourcePath) {
            return true;
        }
        const normalizedParent = uiState.normalizeFolder(candidateParentPath || '');
        if (sourcePath === normalizedParent) {
            return true;
        }
        return Boolean(normalizedParent && normalizedParent.startsWith(sourcePath + '/'));
    }

    function setFolderDropHover(targetEl, mode) {
        if (activeFolderDropTargetEl === targetEl && activeFolderDropMode === mode) {
            return;
        }
        if (activeFolderDropTargetEl) {
            activeFolderDropTargetEl.classList.remove('ws-folder-drop-hover');
            activeFolderDropTargetEl.classList.remove('ws-folder-drop-line-top');
            activeFolderDropTargetEl.classList.remove('ws-folder-drop-line-bottom');
        }

        activeFolderDropTargetEl = targetEl;
        activeFolderDropMode = mode || null;
        if (activeFolderDropTargetEl) {
            if (activeFolderDropMode === 'sibling-before') {
                activeFolderDropTargetEl.classList.add('ws-folder-drop-line-top');
            } else {
                activeFolderDropTargetEl.classList.add('ws-folder-drop-hover');
            }
        }
    }

    function clearFolderDropHover() {
        if (activeFolderDropTargetEl) {
            activeFolderDropTargetEl.classList.remove('ws-folder-drop-hover');
            activeFolderDropTargetEl.classList.remove('ws-folder-drop-line-top');
            activeFolderDropTargetEl.classList.remove('ws-folder-drop-line-bottom');
            activeFolderDropTargetEl = null;
        }
        activeFolderDropMode = null;
    }

    function resolveDropMode(event, rowEl) {
        const rect = rowEl.getBoundingClientRect();
        if (!rect || rect.height <= 0) {
            return 'child';
        }

        const offsetY = event.clientY - rect.top;
        const edgeThreshold = Math.min(8, Math.max(4, rect.height * 0.2));
        if (offsetY <= edgeThreshold) {
            return 'sibling-before';
        }
        return 'child';
    }

    function resolveCandidateParentPath(targetNode, dropMode) {
        if (!targetNode) {
            return '';
        }
        if (dropMode === 'sibling-before') {
            return targetNode.parentPath || '';
        }
        return targetNode.path || '';
    }

    function isSelfTopDrop(sourcePath, targetPath, dropMode) {
        return dropMode === 'sibling-before' && sourcePath && sourcePath === uiState.normalizeFolder(targetPath || '');
    }

    async function handleFolderDrop(dragPayload, targetNode, dropMode) {
        if (!dragPayload || !targetNode) {
            return;
        }

        const sourcePath = uiState.normalizeFolder(dragPayload.path || '');
        const targetPath = uiState.normalizeFolder(targetNode.path || '');
        if (isSelfTopDrop(sourcePath, targetPath, dropMode)) {
            notify('error', 'Cannot drop a folder on its own top edge.');
            return;
        }

        const candidateParentPath = uiState.normalizeFolder(resolveCandidateParentPath(targetNode, dropMode));
        if (isInvalidFolderDrop(sourcePath, candidateParentPath)) {
            notify('error', 'Cannot move a folder into itself or its descendants.');
            return;
        }

        const targetMeta = getNodeMetaByPath(uiState.normalizeFolder(targetNode.path || ''));
        if (!targetMeta) {
            notify('error', 'Destination folder is unavailable.');
            return;
        }

        let nextParentId = null;
        if (dropMode === 'sibling-before') {
            nextParentId = targetMeta.parentId ?? null;
        } else if (targetMeta.id) {
            nextParentId = targetMeta.id;
        } else {
            notify('error', 'Destination folder is unavailable.');
            return;
        }

        try {
            const updated = await api.updateFolder(dragPayload.id, { parentId: nextParentId });
            const selected = uiState.getSelectedFolder();
            if (selected && selected === sourcePath && updated?.path) {
                uiState.setSelectedFolder(uiState.normalizeFolder(updated.path));
                emitFolderChanged(uiState.getSelectedFolder());
            }
            await loadFolders();
            notify('success', 'Folder moved.');
        } catch (error) {
            notify('error', error?.message || 'Folder move failed.');
        }
    }

    function renderFolderTree() {
        if (!folderTree) {
            return;
        }

        folderTree.innerHTML = '';

        const selectedFolder = uiState.getSelectedFolder();
        const selectedRowClasses = 'bg-[#E7FF02]/30 text-white';
        const unselectedRowClasses = 'text-white/80 hover:bg-white/5';
        const rowWrapClasses = 'select-none';
        const rowInnerBaseClasses = 'flex items-center gap-2 py-2 pl-2 pr-2 text-sm rounded-md transition-colors w-full';
        const rowSelectButtonClasses = 'min-w-0 flex-1 flex items-center gap-2 text-left';

        const showAllWrap = document.createElement('div');
        showAllWrap.className = rowWrapClasses;
        showAllWrap.style.paddingLeft = '12px';

        const showAllInner = document.createElement('div');
        showAllInner.className = rowInnerBaseClasses + ' ' + (selectedFolder ? unselectedRowClasses : selectedRowClasses);

        const showAllButton = document.createElement('button');
        showAllButton.type = 'button';
        showAllButton.className = rowSelectButtonClasses;
        showAllButton.setAttribute('aria-label', 'Show all files');
        if (!selectedFolder) {
            showAllButton.setAttribute('aria-current', 'true');
        }
        showAllButton.innerHTML =
            '<span class="shrink-0 text-white/60">' +
            '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" class="w-4 h-4"><path d="M4 6h16" /><path d="M4 12h16" /><path d="M4 18h16" /></svg>' +
            '</span>' +
            '<span class="min-w-0 truncate">Show all files <span id="wsTotalCount" class="text-white/55">(' + String(uiState.getPageState().totalCount) + ')</span></span>';

        if (isFolderLoading) {
            const loadingSpinner = document.createElement('span');
            loadingSpinner.className = 'shrink-0 inline-flex items-center justify-center text-white/55';
            loadingSpinner.setAttribute('aria-label', 'Loading folders');
            loadingSpinner.innerHTML =
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="w-3.5 h-3.5 animate-spin" aria-hidden="true" focusable="false">' +
                '<path d="M12 3a9 9 0 1 0 9 9" />' +
                '</svg>';
            showAllButton.appendChild(loadingSpinner);
        }

        showAllButton.addEventListener('click', () => {
            if (!uiState.getSelectedFolder()) {
                return;
            }
            uiState.setSelectedFolder('');
            renderFolderTree();
            if (typeof onFolderChanged === 'function') {
                onFolderChanged('');
            }
        });

        showAllInner.appendChild(showAllButton);
        showAllWrap.appendChild(showAllInner);
        folderTree.appendChild(showAllWrap);

        if (!folderTreeModel || !folderTreeModel.root || !folderTreeModel.root.children || folderTreeModel.root.children.size === 0) {
            if (inlineEditorState && inlineEditorState.mode === 'create' && !inlineEditorState.parentPath) {
                renderInlineEditorRow(null, 0);
            }
            return;
        }

        const sortedChildren = (node) => Array.from(node.children.values()).sort((a, b) => a.name.localeCompare(b.name));

        function renderInlineEditorRow(parentNode, depth) {
            const rowWrap = document.createElement('div');
            rowWrap.className = rowWrapClasses;
            rowWrap.style.paddingLeft = String(12 + depth * 16) + 'px';

            const rowInner = document.createElement('div');
            rowInner.className = rowInnerBaseClasses + ' text-white/90 bg-white/5';
            rowInner.style.position = 'relative';
            rowInner.style.zIndex = '1';

            const spacer = document.createElement('span');
            spacer.className = 'shrink-0 w-5 h-5 flex items-center justify-center text-white/50';
            spacer.innerHTML =
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" class="w-4 h-4"><path d="M12 5v14" /><path d="M5 12h14" /></svg>';

            const input = document.createElement('input');
            input.type = 'text';
            input.value = inlineEditorState?.value || '';
            input.className = 'min-w-0 flex-1 bg-black border border-white/20 px-2 py-1 text-sm text-white focus:outline-none focus:border-[#E7FF02]';
            input.placeholder = inlineEditorState?.mode === 'rename' ? 'Rename folder' : 'New folder';

            input.addEventListener('keydown', async (event) => {
                if (event.key === 'Escape') {
                    event.preventDefault();
                    clearInlineEditor();
                    renderFolderTree();
                    return;
                }
                if (event.key === 'Enter') {
                    event.preventDefault();
                    await submitInlineEditor(input.value);
                }
            });

            input.addEventListener('blur', async () => {
                if (!inlineEditorState) {
                    return;
                }
                await submitInlineEditor(input.value);
            });

            rowInner.appendChild(spacer);
            rowInner.appendChild(input);
            rowWrap.appendChild(rowInner);
            folderTree.appendChild(rowWrap);

            window.requestAnimationFrame(() => {
                input.focus();
                input.select();
            });
        }

        const renderNode = (node, depth) => {
            const hasChildren = node.children && node.children.size > 0;
            const isOpen = Boolean(hasChildren && node.expanded);
            const rowWrap = document.createElement('div');
            rowWrap.className = rowWrapClasses;
            rowWrap.style.paddingLeft = String(12 + depth * 16) + 'px';
            rowWrap.style.position = 'relative';

            if (depth > 0) {
                for (let guideLevel = 1; guideLevel <= depth; guideLevel++) {
                    const guide = document.createElement('span');
                    guide.className = 'pointer-events-none absolute top-0 bottom-0 w-px bg-white/20';
                    guide.style.left = String(12 + (guideLevel - 1) * 16 + 8) + 'px';
                    rowWrap.appendChild(guide);
                }
            }

            const rowInner = document.createElement('div');
            const isSelected = uiState.getSelectedFolder() === node.path;
            rowInner.className = rowInnerBaseClasses + ' ' + (isSelected ? selectedRowClasses : unselectedRowClasses);
            rowInner.style.position = 'relative';
            rowInner.style.zIndex = '1';
            rowInner.dataset.dropTarget = 'folder';
            rowInner.dataset.folderPath = node.path;

            const nodeMeta = getNodeMetaByPath(node.path);
            const canMutate = Boolean(nodeMeta?.id);
            if (canMutate) {
                rowInner.draggable = true;
                rowInner.dataset.folderId = String(nodeMeta.id);
            }

            let toggle;
            if (hasChildren) {
                toggle = document.createElement('button');
                toggle.type = 'button';
                toggle.className = 'shrink-0 w-5 h-5 flex items-center justify-center text-white/45 hover:text-white/80 transition-colors';
                toggle.setAttribute('aria-label', node.expanded ? 'Collapse folder' : 'Expand folder');
                toggle.setAttribute('aria-expanded', String(Boolean(node.expanded)));
                toggle.innerHTML = node.expanded
                    ? '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="w-4 h-4"><path d="M6 9l6 6 6-6" /></svg>'
                    : '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="w-4 h-4"><path d="M9 6l6 6-6 6" /></svg>';

                toggle.addEventListener('click', (event) => {
                    event.preventDefault();
                    event.stopPropagation();
                    node.expanded = !node.expanded;
                    renderFolderTree();
                });
            } else {
                toggle = document.createElement('span');
                toggle.className = 'shrink-0 w-5 h-5 flex items-center justify-center';
                toggle.innerHTML = '<span class="block w-4 h-4"></span>';
            }

            const iconHtml = isOpen
                ? '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" class="w-4 h-4"><path d="M3 8a2 2 0 0 1 2-2h4l2 2h9a2 2 0 0 1 2 2v1" /><path d="M3 11h18l-1.5 8.5a2 2 0 0 1-2 1.5H6.5a2 2 0 0 1-2-1.5L3 11z" /></svg>'
                : '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" class="w-4 h-4"><path d="M3 7a2 2 0 0 1 2-2h5l2 2h9a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7z" /></svg>';

            const iconSpan = document.createElement('span');
            iconSpan.className = 'shrink-0 text-white/60';
            iconSpan.innerHTML = iconHtml;

            const labelSpan = document.createElement('span');
            labelSpan.className = 'min-w-0 truncate';
            labelSpan.textContent = node.name;

            const isRenameEditing = Boolean(inlineEditorState && inlineEditorState.mode === 'rename' && inlineEditorState.targetPath === node.path);

            if (isRenameEditing) {
                const editContainer = document.createElement('div');
                editContainer.className = rowSelectButtonClasses;
                editContainer.setAttribute('role', 'group');
                editContainer.setAttribute('aria-label', 'Rename folder ' + node.path);
                editContainer.appendChild(iconSpan);

                const editInput = document.createElement('input');
                editInput.type = 'text';
                editInput.value = inlineEditorState.value || node.name;
                editInput.className = 'min-w-0 flex-1 bg-black border border-white/20 px-2 py-1 text-sm text-white focus:outline-none focus:border-[#E7FF02]';
                editInput.addEventListener('keydown', async (event) => {
                    if (event.key === 'Escape') {
                        event.preventDefault();
                        clearInlineEditor();
                        renderFolderTree();
                        return;
                    }
                    if (event.key === 'Enter') {
                        event.preventDefault();
                        await submitInlineEditor(editInput.value);
                    }
                });
                editInput.addEventListener('blur', async () => {
                    if (!inlineEditorState) {
                        return;
                    }
                    await submitInlineEditor(editInput.value);
                });
                editContainer.appendChild(editInput);
                rowInner.appendChild(toggle);
                rowInner.appendChild(editContainer);
                rowWrap.appendChild(rowInner);
                folderTree.appendChild(rowWrap);

                window.requestAnimationFrame(() => {
                    editInput.focus();
                    editInput.select();
                });
            } else {
                const selectButton = document.createElement('button');
                selectButton.type = 'button';
                selectButton.className = rowSelectButtonClasses;
                selectButton.setAttribute('aria-label', 'Filter by folder ' + node.path);
                if (isSelected) {
                    selectButton.setAttribute('aria-current', 'true');
                }

                selectButton.appendChild(iconSpan);
                selectButton.appendChild(labelSpan);
                selectButton.addEventListener('click', () => {
                    const next = uiState.getSelectedFolder() === node.path ? '' : node.path;
                    uiState.setSelectedFolder(next);
                    renderFolderTree();
                    emitFolderChanged(next);
                });

                rowInner.appendChild(toggle);
                rowInner.appendChild(selectButton);
                rowWrap.appendChild(rowInner);
                folderTree.appendChild(rowWrap);
            }

            rowInner.addEventListener('contextmenu', (event) => {
                event.preventDefault();
                event.stopPropagation();
                openContextMenu(node, event);
            });

            rowInner.addEventListener('dragstart', (event) => {
                if (!canMutate || !event.dataTransfer) {
                    return;
                }
                activeFolderDrag = {
                    id: nodeMeta.id,
                    path: node.path
                };
                event.dataTransfer.effectAllowed = 'move';
                event.dataTransfer.setData('text/plain', node.path);
            });

            rowInner.addEventListener('dragend', () => {
                activeFolderDrag = null;
                clearFolderDropHover();
            });

            rowInner.addEventListener('dragover', (event) => {
                if (!activeFolderDrag) {
                    return;
                }
                const sourcePath = uiState.normalizeFolder(activeFolderDrag.path || '');
                const dropMode = resolveDropMode(event, rowInner);
                if (isSelfTopDrop(sourcePath, node.path, dropMode)) {
                    clearFolderDropHover();
                    return;
                }
                const candidateParentPath = resolveCandidateParentPath(node, dropMode);
                if (isInvalidFolderDrop(sourcePath, candidateParentPath)) {
                    clearFolderDropHover();
                    return;
                }
                event.preventDefault();
                if (event.dataTransfer) {
                    event.dataTransfer.dropEffect = 'move';
                }
                setFolderDropHover(rowInner, dropMode);
            });

            rowInner.addEventListener('dragleave', (event) => {
                if (!activeFolderDrag) {
                    return;
                }
                const relatedTarget = event.relatedTarget;
                if (!relatedTarget || !rowInner.contains(relatedTarget)) {
                    clearFolderDropHover();
                }
            });

            rowInner.addEventListener('drop', async (event) => {
                if (!activeFolderDrag) {
                    return;
                }
                event.preventDefault();
                const targetNode = node;
                const dropMode = resolveDropMode(event, rowInner);
                const dragPayload = activeFolderDrag;
                activeFolderDrag = null;
                clearFolderDropHover();
                await handleFolderDrop(dragPayload, targetNode, dropMode);
            });

            if (inlineEditorState && inlineEditorState.mode === 'create' && inlineEditorState.parentPath === node.path) {
                renderInlineEditorRow(node, depth + 1);
            }

            if (hasChildren && node.expanded) {
                for (const child of sortedChildren(node)) {
                    renderNode(child, depth + 1);
                }
            }
        };

        if (inlineEditorState && inlineEditorState.mode === 'create' && !inlineEditorState.parentPath) {
            renderInlineEditorRow(null, 0);
        }

        for (const child of sortedChildren(folderTreeModel.root)) {
            renderNode(child, 0);
        }
    }

    function loadFolders() {
        const expandedByPath = snapshotExpandedState(folderTreeModel);

        isFolderLoading = true;
        if (folderLoading) {
            folderLoading.classList.add('hidden');
        }
        if (folderEmpty) {
            folderEmpty.classList.add('hidden');
            folderEmpty.textContent = 'No folders found.';
        }

        renderFolderTree();

        if (typeof api.fetchFolders !== 'function') {
            notify('error', 'Folder list API is unavailable (frontend assets may be out of sync).');
            isFolderLoading = false;
            renderFolderTree();
            return Promise.resolve();
        }

        const folderNodesPromise = typeof api.fetchFolderNodes === 'function'
            ? api.fetchFolderNodes().catch(() => [])
            : Promise.resolve([]);

        return Promise.all([api.fetchFolders(), folderNodesPromise])
            .then(([folders, nodes]) => {
                isFolderLoading = false;
                folderNodeByPath = new Map();
                for (const node of nodes || []) {
                    const normalizedPath = uiState.normalizeFolder(node?.path || '');
                    if (normalizedPath) {
                        folderNodeByPath.set(normalizedPath, node);
                    }
                }

                folderTreeModel = createFolderTreeModel(folders, nodes);
                applyExpandedState(folderTreeModel, expandedByPath);

                if (folderLoading) {
                    folderLoading.classList.add('hidden');
                }

                const hasAny = folderTreeModel && folderTreeModel.root && folderTreeModel.root.children && folderTreeModel.root.children.size > 0;
                if (folderEmpty) {
                    folderEmpty.classList.toggle('hidden', hasAny);
                }

                renderFolderTree();
                return folderTreeModel;
            })
            .catch((error) => {
                console.error('Failed to load folders', error);
                isFolderLoading = false;
                folderTreeModel = createFolderTreeModel([], []);
                folderNodeByPath = new Map();
                if (folderLoading) {
                    folderLoading.classList.add('hidden');
                }
                if (folderEmpty) {
                    folderEmpty.classList.remove('hidden');
                    folderEmpty.textContent = 'Folders unavailable.';
                }
                renderFolderTree();
                return folderTreeModel;
            });
    }

    function getFolderTreeModel() {
        return folderTreeModel;
    }

    if (sidebarResizeHandle && sidebar) {
        sidebarResizeHandle.setAttribute('aria-valuemin', '0');
        sidebarResizeHandle.setAttribute('aria-valuemax', String(SIDEBAR_MAX_WIDTH));
        sidebarResizeHandle.addEventListener('pointerdown', (event) => {
            if (event.button !== 0) {
                return;
            }

            const startX = Number(event.clientX) || 0;
            const startWidth = sidebar.getBoundingClientRect().width || 0;

            const onPointerMove = (moveEvent) => {
                const nextWidth = startWidth + ((Number(moveEvent.clientX) || 0) - startX);
                setSidebarWidth(nextWidth);
            };

            const onPointerUp = () => {
                document.removeEventListener('pointermove', onPointerMove);
                document.removeEventListener('pointerup', onPointerUp);
            };

            document.addEventListener('pointermove', onPointerMove);
            document.addEventListener('pointerup', onPointerUp);
            event.preventDefault();
        });
    }

    if (newFolderButton) {
        newFolderButton.addEventListener('click', () => {
            const selectedPath = uiState.getSelectedFolder();
            if (!selectedPath) {
                startInlineCreate(null);
                return;
            }

            const selectedNode = folderTreeModel.pathIndex.get(selectedPath);
            if (!selectedNode) {
                startInlineCreate(null);
                return;
            }

            startInlineCreate(selectedNode);
        });
    }

    setSidebarExpanded(uiState.isSidebarExpanded());

    return {
        getFolderTreeModel,
        loadFolders,
        renderFolderTree,
        setSidebarExpanded
    };
}
