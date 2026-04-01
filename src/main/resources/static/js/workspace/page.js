import { createWorkspacePageApi } from './api/workspace-page-api.js';
import { createBulkEdit } from './bulk-edit.js';
import { createDrawer } from './drawer.js';
import { bindSelectedExport } from './export-selected.js';
import { createWorkspaceDataController } from './features/workspace-data-controller.js';
import { createWorkspaceImportController } from './features/workspace-import-controller.js';
import { bindWorkspaceRowActions } from './features/workspace-row-actions.js';
import { createGrid } from './grid.js';
import { createSelection } from './selection.js';
import { createWorkspaceUiState } from './state/workspace-ui-state.js';

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
const bulkEditOpen = document.getElementById('bulkEditOpen');
const bulkOrganize = document.getElementById('bulkOrganize');
const pageInfo = document.getElementById('wsPageInfo');
const prevPageButton = document.getElementById('wsPrevPage');
const nextPageButton = document.getElementById('wsNextPage');
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
const bulkEditEnabled = String(document.querySelector('meta[name="workspace-bulk-edit-enabled"]')?.content || '').toLowerCase() === 'true';

const uiState = createWorkspaceUiState({
    initialSearchParams: new URLSearchParams(window.location.search),
    defaultPageSize: 50
});
uiState.hydrateInputs({
    searchInput,
    filterComponent,
    filterStatus,
    filterTag
});

const api = createWorkspacePageApi({
    apiBaseUrl,
    componentsBaseUrl,
    statusesBaseUrl,
    tagsBaseUrl
});

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
    drawerTags: document.getElementById('drawerTags'),
    drawerModeLabel: document.getElementById('drawerModeLabel'),
    drawerFooterHint: document.getElementById('drawerFooterHint'),
    getTestCaseById: grid.getTestCaseById
});

let syncRowSelectionUi = () => {};
let importController = {
    clearNotice() {},
    showNotice() {}
};

const dataController = createWorkspaceDataController({
    api,
    uiState,
    grid,
    selection,
    tbody,
    totalCount,
    pageInfo,
    prevPageButton,
    nextPageButton,
    searchInput,
    filterComponent,
    filterStatus,
    filterTag,
    syncRowSelectionUi: () => syncRowSelectionUi()
});

const bulkEdit = createBulkEdit({
    bulkEditButton: bulkEditOpen,
    openCasePreview: (workKey) => {
        drawer.openByWorkKey(workKey, { readOnly: true });
    },
    getCurrentPageCases: () => dataController.getCurrentPageCases(),
    getCsrf: () => ({
        headerName: document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN',
        token: document.querySelector('meta[name="_csrf"]')?.content || ''
    }),
    getSelectedIds: () => selection.getSelectedIds(),
    isEnabled: () => bulkEditEnabled,
    refreshCurrentPage: async () => {
        await dataController.loadCurrentPage();
    },
    showNotice: (type, message) => {
        importController.showNotice(type, message);
    }
});

const rowActions = bindWorkspaceRowActions({
    tbody,
    selection,
    drawer,
    editBasePath: '/workspace/test-cases/'
});
syncRowSelectionUi = rowActions.syncRowSelectionUi;

importController = createWorkspaceImportController({
    api,
    importBtn,
    importFile,
    importForm,
    importNoticeContainer,
    importNotice,
    importNoticeBadge,
    importNoticeMessage,
    importNoticeClose,
    onUploadComplete: async () => {
        await Promise.all([
            dataController.loadCurrentPage({ page: 0 }),
            dataController.loadFilterOptions(),
            loadFolders()
        ]);
    }
});

let folderTreeModel = null;
let isSidebarExpanded = true;
let activeDragPayload = null;
let activeDropTargetEl = null;
let activeDragImageHolder = null;
let searchDebounceHandle = 0;

const normalizeFolder = uiState.normalizeFolder;
const loadCurrentPage = (options) => dataController.loadCurrentPage(options);
const loadFilterOptions = () => dataController.loadFilterOptions();
const applyFilters = (options) => dataController.applyFilters(options);
const showImportNotice = (type, message) => importController.showNotice(type, message);

function getCsrf() {
    return {
        headerName: document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN',
        token: document.querySelector('meta[name="_csrf"]')?.content || ''
    };
}

function setSidebarExpanded(expanded) {
    isSidebarExpanded = Boolean(expanded);

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

if (sidebarToggle) {
    sidebarToggle.addEventListener('click', () => {
        setSidebarExpanded(!isSidebarExpanded);
    });
}

selection.setSelectionChangeHandler((selectedIds) => {
    exportSelected.updateButtonState(selectedIds);
    syncRowSelectionUi();
    bulkEdit.onSelectionChanged(selectedIds);
});

if (drawer && typeof drawer.bind === 'function') {
    drawer.bind(tbody);
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
        applyFilters({ resetPage: true });
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
            applyFilters({ resetPage: true });
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
    for (const testCase of dataController.getCurrentPageCases()) {
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

async function handleBulkMove(input) {
    const moveInput = input || {};
    const uniqueKeys = Array.from(new Set((moveInput.workKeys || []).filter(Boolean)));
    const targetFolder = normalizeFolder(moveInput.targetFolder || '');
    if (!targetFolder || uniqueKeys.length === 0) {
        return false;
    }

    let payload;
    try {
        payload = await api.bulkMove({
            workKeys: uniqueKeys,
            targetFolder,
            csrf: getCsrf()
        });
    } catch (error) {
        showImportNotice('error', 'Move failed. Please check your connection and try again.');
        return false;
    }

    if (!payload.ok) {
        const msg = payload.status === 401 ? 'You must be logged in to move test cases.'
            : payload.status === 403 ? 'You do not have permission to move these test cases.'
            : 'Move failed. Please try again.';
        showImportNotice('error', msg);
        return false;
    }

    const result = payload.result || {};
    if (result.movedCount === 0) {
        showImportNotice('error', 'No test cases were moved.');
        return false;
    }

    const failedKeys = new Set((result.failures || []).map((failure) => failure.workKey));
    const movedKeys = new Set(uniqueKeys.filter((workKey) => !failedKeys.has(workKey)));
    selection.removeSelectedIds(Array.from(movedKeys));
    await Promise.all([
        loadFolders(),
        loadCurrentPage()
    ]);

    if (result.movedCount < uniqueKeys.length) {
        showImportNotice('error',
            `${result.movedCount} of ${uniqueKeys.length} test cases moved. Some could not be moved.`);
    }

    return true;
}

if (searchInput) {
    searchInput.addEventListener('input', () => {
        window.clearTimeout(searchDebounceHandle);
        searchDebounceHandle = window.setTimeout(() => {
            applyFilters({ resetPage: true });
        }, 180);
    });
}
if (filterComponent) {
    filterComponent.addEventListener('change', () => applyFilters({ resetPage: true }));
}
if (filterStatus) {
    filterStatus.addEventListener('change', () => applyFilters({ resetPage: true }));
}
if (filterTag) {
    filterTag.addEventListener('change', () => applyFilters({ resetPage: true }));
}
if (prevPageButton) {
    prevPageButton.addEventListener('click', () => {
        const pageState = uiState.getPageState();
        if (pageState.page <= 0 || pageState.isLoading) {
            return;
        }
        loadCurrentPage({ page: pageState.page - 1 });
    });
}
if (nextPageButton) {
    nextPageButton.addEventListener('click', () => {
        const pageState = uiState.getPageState();
        if (!pageState.hasNext || pageState.isLoading) {
            return;
        }
        loadCurrentPage({ page: pageState.page + 1 });
    });
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
    organizeSave.addEventListener('click', async () => {
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

        const moved = await handleBulkMove({
            workKeys,
            targetFolder,
            source: 'organize-modal'
        });

        if (moved) {
            closeOrganizeModal();
        }
    });
}

loadCurrentPage();
loadFilterOptions();
loadFolders();
