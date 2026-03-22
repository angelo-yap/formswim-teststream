import { createDrawer } from './drawer.js';
import { bindSelectedExport } from './export-selected.js';
import { createGrid } from './grid.js';
import { createSelection } from './selection.js';

const importBtn = document.getElementById('importBtn');
const importFile = document.getElementById('importFile');
const importForm = document.getElementById('importForm');
const tbody = document.getElementById('wsTbody');
const totalCount = document.getElementById('wsTotalCount');
const selectAll = document.getElementById('wsSelectAll');
const searchInput = document.getElementById('wsSearch');
const filterComponent = document.getElementById('wsFilterComponent');
const filterStatus = document.getElementById('wsFilterStatus');
const filterTag = document.getElementById('wsFilterTag');
const bulkBar = document.getElementById('bulkBar');
const bulkCount = document.getElementById('bulkCount');
const bulkExportSelected = document.getElementById('bulkExportSelected');
const bulkOrganize = document.getElementById('bulkOrganize');
const importNoticeContainer = document.getElementById('importNoticeContainer');
const importNotice = document.getElementById('importNotice');
const importNoticeBadge = document.getElementById('importNoticeBadge');
const importNoticeMessage = document.getElementById('importNoticeMessage');
const importNoticeClose = document.getElementById('importNoticeClose');

const folderTree = document.getElementById('wsFolderTree');
const folderLoading = document.getElementById('wsFolderLoading');
const folderEmpty = document.getElementById('wsFolderEmpty');
const sidebar = document.getElementById('wsSidebar');
const sidebarToggle = document.getElementById('wsSidebarToggle');
const sidebarContent = document.getElementById('wsSidebarContent');
const sidebarTitle = document.getElementById('wsSidebarTitle');
const sidebarInner = document.getElementById('wsSidebarInner');
const sidebarHeader = document.getElementById('wsSidebarHeader');
const sidebarToggleExpandedHost = document.getElementById('wsSidebarToggleExpandedHost');
const sidebarToggleCollapsedHost = document.getElementById('wsSidebarToggleCollapsedHost');

const organizeModal = document.getElementById('organizeModal');
const organizeBackdrop = document.getElementById('organizeBackdrop');
const organizePanel = document.getElementById('organizePanel');
const organizeClose = document.getElementById('organizeClose');
const organizeCancel = document.getElementById('organizeCancel');
const organizeSave = document.getElementById('organizeSave');
const organizeFolderSelect = document.getElementById('organizeFolderSelect');
const organizeFolderInput = document.getElementById('organizeFolderInput');
const organizeError = document.getElementById('organizeError');

const apiBaseUrl = document.querySelector('meta[name="workspace-api-base"]')?.content || '/api/testcases';
const exportBaseUrl = document.querySelector('meta[name="workspace-export-base"]')?.content || '/workspace/export';
const componentsBaseUrl = document.querySelector('meta[name="workspace-components-base"]')?.content || '/api/components';
const statusesBaseUrl = document.querySelector('meta[name="workspace-statuses-base"]')?.content || '/api/statuses';
const tagsBaseUrl = document.querySelector('meta[name="workspace-tags-base"]')?.content || '/api/tags';

const grid = createGrid(tbody);
const selection = createSelection(selectAll, bulkBar, bulkCount);
const exportSelected = bindSelectedExport(bulkExportSelected, () => selection.getSelectedIds(), exportBaseUrl);
const drawer = createDrawer({
    drawer: document.getElementById('drawer'),
    drawerPanel: document.getElementById('drawerPanel'),
    drawerBackdrop: document.getElementById('drawerBackdrop'),
    drawerClose: document.getElementById('drawerClose'),
    drawerSave: document.getElementById('drawerSave'),
    drawerId: document.getElementById('drawerId'),
    drawerTitle: document.getElementById('drawerTitle'),
    drawerSummary: document.getElementById('drawerSummary'),
    drawerSteps: document.getElementById('drawerSteps'),
    drawerStatus: document.getElementById('drawerStatus'),
    drawerUpdated: document.getElementById('drawerUpdated'),
    drawerAssignee: document.getElementById('drawerAssignee'),
    drawerReporter: document.getElementById('drawerReporter'),
    drawerCreatedOn: document.getElementById('drawerCreatedOn'),
    drawerTagBadges: document.getElementById('drawerTagBadges'),
    drawerTagInput: document.getElementById('drawerTagInput'),
    drawerTagDropdown: document.getElementById('drawerTagDropdown'),
    drawerModeLabel: document.getElementById('drawerModeLabel'),
    drawerFooterHint: document.getElementById('drawerFooterHint'),
    getTestCaseById: grid.getTestCaseById
});

let allTestCases = [];
let selectedFolder = '';

let folderTreeModel = null;
let isSidebarExpanded = true;
let activeDragPayload = null;
let activeDropTargetEl = null;
let activeDragImageHolder = null;

function setSidebarExpanded(expanded) {
    isSidebarExpanded = Boolean(expanded);

    if (sidebar) {
        // Use inline sizing so Tailwind CDN doesn't miss dynamically-toggled width classes.
        if (isSidebarExpanded) {
            sidebar.style.width = '';
            sidebar.style.minWidth = '';
            sidebar.style.borderRight = '';
            sidebar.style.overflow = '';
        } else {
            // Fully collapse so the grid can be flush-left.
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
        // Keep button height; only reduce horizontal padding when collapsed.
        sidebarInner.style.padding = isSidebarExpanded ? '' : '0.25rem';
    }

    if (sidebarHeader) {
        sidebarHeader.style.justifyContent = isSidebarExpanded ? '' : 'center';
    }

    if (sidebarToggle) {
        sidebarToggle.setAttribute('aria-expanded', String(isSidebarExpanded));
        sidebarToggle.setAttribute('aria-label', isSidebarExpanded ? 'Collapse repository sidebar' : 'Expand repository sidebar');
        // Keep a consistent square button in both locations.
        sidebarToggle.style.width = '';
        sidebarToggle.style.padding = '';
        sidebarToggle.innerHTML = isSidebarExpanded
            ? '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="w-4 h-4"><path d="M15 6l-6 6 6 6" /></svg>'
            : '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="w-4 h-4"><path d="M9 6l6 6-6 6" /></svg>';
    }

    // Move the toggle button so it's still accessible when the sidebar is fully collapsed.
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

if (sidebarToggle) {
    sidebarToggle.addEventListener('click', () => {
        setSidebarExpanded(!isSidebarExpanded);
    });
}

selection.setSelectionChangeHandler((selectedIds) => {
    exportSelected.updateButtonState(selectedIds);
    syncRowSelectionUi();
});

if (drawer && typeof drawer.bind === 'function') {
    drawer.bind(tbody);
}

if (importNoticeClose) {
    importNoticeClose.addEventListener('click', clearImportNotice);
}

if (tbody) {
    tbody.addEventListener('change', (event) => {
        const target = event.target;
        if (!target || !target.classList || !target.classList.contains('ws-row-check')) {
            return;
        }

        selection.toggleSelection(target.dataset.workKey || '', target.checked);
        syncRowSelectionUi();
    });

    tbody.addEventListener('click', (event) => {
        const actionButton = event.target?.closest?.('.ws-row-action');
        if (actionButton) {
            const workKey = actionButton.dataset.workKey || actionButton.closest('[data-work-key]')?.dataset.workKey || '';
            if (!workKey) {
                return;
            }

            const action = actionButton.dataset.action;
            if (action === 'preview') {
                drawer.openByWorkKey(workKey, { readOnly: true });
            } else if (action === 'edit') {
                drawer.openByWorkKey(workKey, { readOnly: false });
            }
            return;
        }

        if (isInteractiveTarget(event.target)) {
            return;
        }

        const row = event.target?.closest?.('tr[data-work-key]');
        if (!row) {
            return;
        }

        const workKey = row.dataset.workKey || '';
        if (!workKey) {
            return;
        }

        const nextChecked = !selection.isSelected(workKey);
        selection.setSelected(workKey, nextChecked);
        const check = row.querySelector('.ws-row-check');
        if (check) {
            check.checked = nextChecked;
        }
        syncRowSelectionUi();
    });

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

    folderTree.addEventListener('drop', (event) => {
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

        handleBulkMove({
            workKeys: payload.workKeys,
            targetFolder,
            source: 'drag'
        });
    });
}

function clearImportNotice() {
    if (!importNoticeContainer) {
        return;
    }

    importNoticeContainer.classList.add('hidden');
    if (importNoticeMessage) {
        importNoticeMessage.textContent = '';
    }
}

function showImportNotice(type, message) {
    if (!importNoticeContainer || !importNotice || !importNoticeBadge || !importNoticeMessage || !message) {
        return;
    }

    const isSuccess = type === 'success';
    importNoticeContainer.classList.remove('hidden');
    importNoticeMessage.textContent = message;

    if (isSuccess) {
        importNotice.style.borderColor = 'rgba(231, 255, 2, 0.35)';
        importNoticeBadge.textContent = 'OK';
        importNoticeBadge.classList.remove('bg-white/70');
        importNoticeBadge.style.backgroundColor = '#E7FF02';
    } else {
        importNotice.style.borderColor = 'rgba(255, 255, 255, 0.15)';
        importNoticeBadge.textContent = 'Error';
        importNoticeBadge.classList.add('bg-white/70');
        importNoticeBadge.style.backgroundColor = '';
    }
}

function applyFilters() {
    const query = searchInput ? searchInput.value.trim().toLowerCase() : '';
    const component = filterComponent ? filterComponent.value.trim().toLowerCase() : '';
    const status = filterStatus ? filterStatus.value.trim().toLowerCase() : '';
    const tag = filterTag ? filterTag.value.trim().toLowerCase() : '';
    const selectedFolderLower = selectedFolder ? selectedFolder.toLowerCase() : '';

    const filtered = allTestCases.filter((testCase) => {
        const workKey = (testCase.workKey || '').toLowerCase();
        const summary = (testCase.summary || '').toLowerCase();
        const components = (testCase.components || '').toLowerCase();
        const testCaseType = (testCase.testCaseType || '').toLowerCase();
        const testCaseStatus = (testCase.status || '').toLowerCase();
        const folder = normalizeFolder(testCase.folder || '').toLowerCase();

        const matchesQuery = !query || workKey.includes(query) || summary.includes(query) || components.includes(query);
        const matchesComponent = !component || components.includes(component);
        const matchesStatus = !status || testCaseStatus === status;
        const customTags = Array.isArray(testCase.tags) ? testCase.tags : [];
        const matchesTag = !tag || customTags.some((t) => (t.name || '').toLowerCase() === tag);

        const matchesFolder = !selectedFolderLower || folder === selectedFolderLower || folder.startsWith(selectedFolderLower + '/');
        return matchesQuery && matchesComponent && matchesStatus && matchesTag && matchesFolder;
    });

    if (totalCount) {
        totalCount.textContent = String(filtered.length);
    }

    grid.renderRows(filtered, new Set(selection.getSelectedIds()));
    selection.setVisibleIds(filtered.map((testCase) => testCase.workKey).filter(Boolean));
    selection.bindRowCheckboxes(tbody);
    syncRowSelectionUi();
}

function syncRowSelectionUi() {
    if (!tbody) {
        return;
    }

    const rows = tbody.querySelectorAll('tr[data-work-key]');
    rows.forEach((row) => {
        const workKey = row.dataset.workKey || '';
        const isSelected = selection.isSelected(workKey);
        row.classList.toggle('ws-row-selected', isSelected);
    });
}

function isInteractiveTarget(target) {
    return Boolean(target?.closest?.('.ws-interactive, a, button, input, select, textarea, label'));
}

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

function normalizeFolder(value) {
    const raw = String(value || '').trim();
    if (!raw) {
        return '';
    }

    // Accept both Windows and URL separators, then normalize to forward slashes.
    const asSlash = raw.replace(/\\/g, '/');
    return asSlash.replace(/^\/+/, '').replace(/\/+$/, '');
}

function createFolderTreeModel(folderNames) {
    const root = {
        name: '',
        path: '',
        children: new Map(),
        expanded: true
    };

    for (const folderName of folderNames || []) {
        const normalized = normalizeFolder(folderName);
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

    const selectedRowClasses = 'bg-[#E7FF02]/10 border-[#E7FF02] text-white';
    const unselectedRowClasses = 'border-transparent text-white/80 hover:bg-white/5 hover:border-white/10';
    const rowWrapClasses = 'select-none';
    const rowInnerBaseClasses = 'flex items-center gap-2 py-2 pl-2 pr-2 text-sm rounded-md border transition-colors w-full';
    const rowSelectButtonClasses = 'min-w-0 flex-1 flex items-center gap-2 text-left';

    // Always offer a way to clear the folder filter.
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

    const activateShowAll = () => {
        if (!selectedFolder) {
            return;
        }
        selectedFolder = '';
        renderFolderTree();
        applyFilters();
    };

    showAllButton.addEventListener('click', activateShowAll);
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
        const isSelected = selectedFolder === node.path;
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
            // Avoid creating a disabled/unlabeled button for leaf nodes.
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

        const activateFolder = () => {
            const next = selectedFolder === node.path ? '' : node.path;
            selectedFolder = next;
            renderFolderTree();
            applyFilters();
        };

        selectButton.addEventListener('click', activateFolder);

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

    fetch('/api/folders')
        .then((response) => {
            if (!response.ok) {
                throw new Error('Folders failed with status ' + response.status);
            }
            return response.json();
        })
        .then((data) => {
            const folders = Array.isArray(data) ? data : [];
            folderTreeModel = createFolderTreeModel(folders);

            if (folderLoading) {
                folderLoading.classList.add('hidden');
            }

            const hasAny = folderTreeModel && folderTreeModel.children && folderTreeModel.children.size > 0;
            if (folderEmpty) {
                folderEmpty.classList.toggle('hidden', hasAny);
            }

            renderFolderTree();
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
        });
}

function loadFilterOptions() {
    const requests = [
        fetchOptionValues(componentsBaseUrl),
        fetchOptionValues(statusesBaseUrl),
        fetchOptionValues(tagsBaseUrl)
    ];

    Promise.all(requests)
        .then(([components, statuses, tags]) => {
            populateSelect(filterComponent, components);
            populateSelect(filterStatus, statuses);
            populateSelect(filterTag, tags);
        })
        .catch(() => {
            const components = uniqueSorted(allTestCases.map((item) => item?.components));
            const statuses = uniqueSorted(allTestCases.map((item) => item?.status));
            const tags = uniqueSorted(allTestCases.flatMap((item) => [item?.components, item?.testCaseType]));
            populateSelect(filterComponent, components);
            populateSelect(filterStatus, statuses);
            populateSelect(filterTag, tags);
        });
}

function fetchOptionValues(url) {
    return fetch(url)
        .then((response) => {
            if (!response.ok) {
                throw new Error('Options failed with status ' + response.status);
            }
            return response.json();
        })
        .then((data) => uniqueSorted(Array.isArray(data) ? data : []));
}

function uniqueSorted(values) {
    const set = new Set();
    for (const value of values || []) {
        const raw = String(value || '').trim();
        if (raw) {
            set.add(raw);
        }
    }

    return Array.from(set).sort((a, b) => a.localeCompare(b, undefined, { sensitivity: 'base' }));
}

function populateSelect(selectEl, values) {
    if (!selectEl) {
        return;
    }

    const previous = selectEl.value || '';
    selectEl.innerHTML = '<option value="">All</option>';
    for (const value of values) {
        const option = document.createElement('option');
        option.value = String(value || '');
        option.textContent = String(value || '');
        selectEl.appendChild(option);
    }

    if (previous && values.includes(previous)) {
        selectEl.value = previous;
    }
}

function collectFolderPaths(node, output) {
    if (!node || !node.children) {
        return;
    }

    for (const child of node.children.values()) {
        if (child.path) {
            output.push(child.path);
        }
        collectFolderPaths(child, output);
    }
}

function listKnownFolders() {
    const folders = new Set();
    for (const testCase of allTestCases) {
        const folder = normalizeFolder(testCase?.folder || '');
        if (folder) {
            folders.add(folder);
        }
    }

    const fromTree = [];
    collectFolderPaths(folderTreeModel, fromTree);
    for (const folder of fromTree) {
        const normalized = normalizeFolder(folder);
        if (normalized) {
            folders.add(normalized);
        }
    }

    return Array.from(folders).sort((a, b) => a.localeCompare(b));
}

function populateOrganizeFolderOptions(selectedValue) {
    if (!organizeFolderSelect) {
        return;
    }

    const folders = listKnownFolders();
    organizeFolderSelect.innerHTML = '<option value="">Select a folder</option>';
    for (const folder of folders) {
        const option = document.createElement('option');
        option.value = folder;
        option.textContent = folder;
        organizeFolderSelect.appendChild(option);
    }

    const normalizedSelected = normalizeFolder(selectedValue || '');
    if (normalizedSelected && folders.includes(normalizedSelected)) {
        organizeFolderSelect.value = normalizedSelected;
    }
}

function closeOrganizeModal() {
    if (!organizeModal || !organizePanel) {
        return;
    }

    organizePanel.classList.add('translate-y-3', 'opacity-0');
    organizeModal.setAttribute('aria-hidden', 'true');
    window.setTimeout(() => {
        organizeModal.classList.add('hidden');
    }, 160);
}

function openOrganizeModal() {
    if (!organizeModal || !organizePanel) {
        return;
    }

    const selectedIds = selection.getSelectedIds();
    if (selectedIds.length === 0) {
        return;
    }

    populateOrganizeFolderOptions('');
    if (organizeFolderInput) {
        organizeFolderInput.value = '';
    }
    if (organizeError) {
        organizeError.classList.add('hidden');
        organizeError.textContent = 'A target folder is required.';
    }

    organizeModal.classList.remove('hidden');
    organizeModal.setAttribute('aria-hidden', 'false');
    requestAnimationFrame(() => {
        organizePanel.classList.remove('translate-y-3', 'opacity-0');
    });
}

function handleBulkMove(input) {
    const moveInput = input || {};
    const uniqueKeys = Array.from(new Set((moveInput.workKeys || []).filter(Boolean)));
    const targetFolder = normalizeFolder(moveInput.targetFolder || '');
    if (!targetFolder || uniqueKeys.length === 0) {
        return false;
    }

    const moveSet = new Set(uniqueKeys);
    allTestCases = allTestCases.map((testCase) => {
        if (!moveSet.has(testCase.workKey)) {
            return testCase;
        }
        return {
            ...testCase,
            folder: targetFolder
        };
    });

    selection.removeSelectedIds(uniqueKeys);

    const knownFolders = listKnownFolders();
    if (!knownFolders.includes(targetFolder)) {
        knownFolders.push(targetFolder);
    }
    folderTreeModel = createFolderTreeModel(knownFolders);
    renderFolderTree();
    applyFilters();
    return true;
}

function loadAllTestCases() {
    const pageSize = 200;
    const maxPages = 500;

    const fetchPage = (page, collected) => {
        if (page >= maxPages) {
            return Promise.resolve(collected);
        }

        const params = new URLSearchParams();
        params.set('page', String(page));
        params.set('size', String(pageSize));

        const url = apiBaseUrl + '?' + params.toString();
        return fetch(url)
            .then((response) => {
                if (!response.ok) {
                    throw new Error('Test cases failed with status ' + response.status);
                }

                const hasNext = String(response.headers.get('X-Has-Next')).toLowerCase() === 'true';
                const totalPages = Number(response.headers.get('X-Total-Pages'));
                return response.json().then((data) => {
                    const pageItems = Array.isArray(data) ? data : [];
                    const nextCollected = collected.concat(pageItems);
                    const hasKnownMorePages = Number.isFinite(totalPages) && totalPages > 0 && (page + 1) < totalPages;
                    const likelyHasMoreBySize = pageItems.length === pageSize;
                    if (hasNext || hasKnownMorePages || likelyHasMoreBySize) {
                        return fetchPage(page + 1, nextCollected);
                    }
                    return nextCollected;
                });
            });
    };

    fetchPage(0, [])
        .then((allRows) => {
            allTestCases = allRows;
            if (totalCount) {
                totalCount.textContent = String(allTestCases.length);
            }
            loadFilterOptions();
            applyFilters();
        })
        .catch((error) => {
            console.error('Failed to load test cases', error);
            allTestCases = [];
            loadFilterOptions();
            applyFilters();
        });
}

if (searchInput) {
    searchInput.addEventListener('input', applyFilters);
}
if (filterComponent) {
    filterComponent.addEventListener('change', applyFilters);
}
if (filterStatus) {
    filterStatus.addEventListener('change', applyFilters);
}
if (filterTag) {
    filterTag.addEventListener('change', applyFilters);
}

if (bulkOrganize) {
    bulkOrganize.addEventListener('click', openOrganizeModal);
}
if (organizeBackdrop) {
    organizeBackdrop.addEventListener('click', closeOrganizeModal);
}
if (organizeClose) {
    organizeClose.addEventListener('click', closeOrganizeModal);
}
if (organizeCancel) {
    organizeCancel.addEventListener('click', closeOrganizeModal);
}
if (organizeFolderSelect && organizeFolderInput) {
    organizeFolderSelect.addEventListener('change', () => {
        const selected = normalizeFolder(organizeFolderSelect.value || '');
        if (selected) {
            organizeFolderInput.value = selected;
        }
    });

    organizeFolderInput.addEventListener('input', () => {
        const normalized = normalizeFolder(organizeFolderInput.value || '');
        if (normalizeFolder(organizeFolderSelect.value || '') === normalized) {
            return;
        }
        organizeFolderSelect.value = '';
    });
}
if (organizeSave) {
    organizeSave.addEventListener('click', () => {
        const workKeys = selection.getSelectedIds();
        const fromSelect = normalizeFolder(organizeFolderSelect?.value || '');
        const fromInput = normalizeFolder(organizeFolderInput?.value || '');
        const targetFolder = normalizeFolder(fromInput || fromSelect);

        if (!targetFolder) {
            if (organizeError) {
                organizeError.classList.remove('hidden');
                organizeError.textContent = 'A target folder is required.';
            }
            return;
        }

        if (organizeError) {
            organizeError.classList.add('hidden');
        }

        const moved = handleBulkMove({
            workKeys,
            targetFolder,
            source: 'organize-modal'
        });

        if (moved) {
            closeOrganizeModal();
        }
    });
}

if (importBtn && importFile) {
    importBtn.addEventListener('click', () => {
        importFile.click();
    });

    importFile.addEventListener('change', () => {
        if (!importFile.files || importFile.files.length === 0) {
            return;
        }

        clearImportNotice();

        const formData = new FormData();
        formData.append('file', importFile.files[0]);

        const csrfInput = importForm ? importForm.querySelector('input[type="hidden"]') : null;
        if (csrfInput && csrfInput.name && csrfInput.value) {
            formData.append(csrfInput.name, csrfInput.value);
        }

        importBtn.textContent = 'Importing…';
        importBtn.disabled = true;

        fetch('/api/upload', { method: 'POST', body: formData })
            .then(async (response) => {
                if (response.ok) {
                    return response.json();
                }

                // Try to surface the backend's message (Spring may return JSON or plain text).
                let detail = '';
                try {
                    const contentType = response.headers.get('content-type') || '';
                    if (contentType.includes('application/json')) {
                        const json = await response.json();
                        detail = json?.message || (Array.isArray(json?.errors) ? json.errors.join('\n') : '');
                    } else {
                        detail = await response.text();
                    }
                } catch (e) {
                    // ignore parsing failure
                }

                const suffix = detail ? ': ' + String(detail).trim() : '';
                throw new Error('Upload failed with status ' + response.status + suffix);
            })
            .then((json) => {
                importBtn.textContent = 'Import';
                importBtn.disabled = false;
                importFile.value = '';

                if (json.reviewRequired && json.reviewUrl) {
                    window.location.href = json.reviewUrl;
                    return;
                }

                if (json.exactDuplicateFile) {
                    showImportNotice('error', json.message || 'This exact file was already uploaded.');
                    return;
                }

                if (json.errors && json.errors.length > 0) {
                    const header = json.message ? String(json.message).trim() : 'Import failed.';
                    showImportNotice('error', header + '\n' + json.errors.join('\n'));
                } else if (json.message) {
                    showImportNotice('success', json.message);
                }

                loadAllTestCases();
                loadFolders();
            })
            .catch((error) => {
                console.error('Upload failed', error);
                const message = error && error.message ? String(error.message) : 'Import failed. Please try again.';
                showImportNotice('error', message);
                importBtn.textContent = 'Import';
                importBtn.disabled = false;
            });
    });
}

loadAllTestCases();
loadFolders();
