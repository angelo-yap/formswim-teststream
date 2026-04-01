export function createWorkspaceFolderTree(options) {
    const uiState = options.uiState;
    const api = options.api;
    const folderTree = options.folderTree;
    const folderLoading = options.folderLoading;
    const folderEmpty = options.folderEmpty;
    const sidebar = options.sidebar;
    const sidebarToggle = options.sidebarToggle;
    const sidebarContent = options.sidebarContent;
    const sidebarTitle = options.sidebarTitle;
    const sidebarInner = options.sidebarInner;
    const sidebarHeader = options.sidebarHeader;
    const sidebarToggleExpandedHost = options.sidebarToggleExpandedHost;
    const sidebarToggleCollapsedHost = options.sidebarToggleCollapsedHost;
    const onFolderChanged = options.onFolderChanged;

    let folderTreeModel = createFolderTreeModel([]);

    function setSidebarExpanded(expanded) {
        uiState.setSidebarExpanded(expanded);
        const isSidebarExpanded = uiState.isSidebarExpanded();

        if (sidebar) {
            if (isSidebarExpanded) {
                sidebar.style.width = '';
                sidebar.style.minWidth = '';
                sidebar.style.borderRight = '';
                sidebar.style.overflow = '';
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
        }

        if (sidebarHeader) {
            sidebarHeader.style.justifyContent = isSidebarExpanded ? '' : 'center';
        }

        if (sidebarToggle) {
            sidebarToggle.setAttribute('aria-expanded', String(isSidebarExpanded));
            sidebarToggle.setAttribute('aria-label', isSidebarExpanded ? 'Collapse repository sidebar' : 'Expand repository sidebar');
            sidebarToggle.style.width = '';
            sidebarToggle.style.padding = '';
            sidebarToggle.innerHTML = isSidebarExpanded
                ? '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="w-4 h-4"><path d="M15 6l-6 6 6 6" /></svg>'
                : '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="w-4 h-4"><path d="M9 6l6 6-6 6" /></svg>';
        }

        if (sidebarToggleExpandedHost && sidebarToggleCollapsedHost && sidebarToggle) {
            if (isSidebarExpanded) {
                sidebarToggleExpandedHost.appendChild(sidebarToggle);
                sidebarToggleCollapsedHost.classList.add('hidden');
            } else {
                sidebarToggleCollapsedHost.appendChild(sidebarToggle);
                sidebarToggleCollapsedHost.classList.remove('hidden');
            }
        }
    }

    function createFolderTreeModel(folderNames) {
        const root = {
            name: '',
            path: '',
            children: new Map(),
            expanded: true
        };

        for (const folderName of folderNames || []) {
            const normalized = uiState.normalizeFolder(folderName);
            if (!normalized) {
                continue;
            }

            const parts = normalized.split('/').filter(Boolean);
            let current = root;
            let currentPath = '';

            for (const part of parts) {
                currentPath = currentPath ? currentPath + '/' + part : part;
                if (!current.children.has(part)) {
                    current.children.set(part, {
                        name: part,
                        path: currentPath,
                        children: new Map(),
                        expanded: true
                    });
                }
                current = current.children.get(part);
            }
        }

        return root;
    }

    function renderFolderTree() {
        if (!folderTree) {
            return;
        }

        folderTree.innerHTML = '';

        const selectedFolder = uiState.getSelectedFolder();
        const selectedRowClasses = 'bg-[#E7FF02]/10 border-[#E7FF02] text-white';
        const unselectedRowClasses = 'border-transparent text-white/80 hover:bg-white/5 hover:border-white/10';
        const rowWrapClasses = 'select-none';
        const rowInnerBaseClasses = 'flex items-center gap-2 py-2 pl-2 pr-2 text-sm rounded-md border transition-colors w-full';
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
            '<span class="min-w-0 truncate">Show all files</span>';

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

        if (!folderTreeModel || !folderTreeModel.children || folderTreeModel.children.size === 0) {
            return;
        }

        const sortedChildren = (node) => Array.from(node.children.values()).sort((a, b) => a.name.localeCompare(b.name));

        const renderNode = (node, depth) => {
            const hasChildren = node.children && node.children.size > 0;
            const isOpen = Boolean(hasChildren && node.expanded);
            const rowWrap = document.createElement('div');
            rowWrap.className = rowWrapClasses;
            rowWrap.style.paddingLeft = String(12 + depth * 16) + 'px';

            const rowInner = document.createElement('div');
            const isSelected = uiState.getSelectedFolder() === node.path;
            rowInner.className = rowInnerBaseClasses + ' ' + (isSelected ? selectedRowClasses : unselectedRowClasses);
            rowInner.dataset.dropTarget = 'folder';
            rowInner.dataset.folderPath = node.path;

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

            const selectButton = document.createElement('button');
            selectButton.type = 'button';
            selectButton.className = rowSelectButtonClasses;
            selectButton.setAttribute('aria-label', 'Filter by folder ' + node.path);
            if (isSelected) {
                selectButton.setAttribute('aria-current', 'true');
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

            selectButton.appendChild(iconSpan);
            selectButton.appendChild(labelSpan);
            selectButton.addEventListener('click', () => {
                const next = uiState.getSelectedFolder() === node.path ? '' : node.path;
                uiState.setSelectedFolder(next);
                renderFolderTree();
                if (typeof onFolderChanged === 'function') {
                    onFolderChanged(next);
                }
            });

            rowInner.appendChild(toggle);
            rowInner.appendChild(selectButton);
            rowWrap.appendChild(rowInner);
            folderTree.appendChild(rowWrap);

            if (hasChildren && node.expanded) {
                for (const child of sortedChildren(node)) {
                    renderNode(child, depth + 1);
                }
            }
        };

        for (const child of sortedChildren(folderTreeModel)) {
            renderNode(child, 0);
        }
    }

    function loadFolders() {
        if (folderLoading) {
            folderLoading.classList.remove('hidden');
        }
        if (folderEmpty) {
            folderEmpty.classList.add('hidden');
            folderEmpty.textContent = 'No folders found.';
        }

        return api.fetchFolders()
            .then((folders) => {
                folderTreeModel = createFolderTreeModel(folders);

                if (folderLoading) {
                    folderLoading.classList.add('hidden');
                }

                const hasAny = folderTreeModel && folderTreeModel.children && folderTreeModel.children.size > 0;
                if (folderEmpty) {
                    folderEmpty.classList.toggle('hidden', hasAny);
                }

                renderFolderTree();
                return folderTreeModel;
            })
            .catch((error) => {
                console.error('Failed to load folders', error);
                folderTreeModel = createFolderTreeModel([]);
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

    if (sidebarToggle) {
        sidebarToggle.addEventListener('click', () => {
            setSidebarExpanded(!uiState.isSidebarExpanded());
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
