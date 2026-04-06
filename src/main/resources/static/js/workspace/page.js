import { createWorkspacePageApi } from './api/workspace-page-api.js';
import { createBulkEdit } from './bulk-edit.js';
import { createWorkspaceFolderTree } from './components/workspace-folder-tree.js';
import { createWorkspaceTags } from './components/workspace-tags.js';
import { createDrawer } from './drawer.js';
import { bindSelectedExport } from './export-selected.js';
import { createWorkspaceDataController } from './features/workspace-data-controller.js';
import { createWorkspaceDndController } from './features/workspace-dnd-controller.js';
import { bindWorkspaceHeaderControls } from './features/workspace-header-controls.js';
import { createWorkspaceImportController } from './features/workspace-import-controller.js';
import { createWorkspaceMoveController } from './features/workspace-move-controller.js';
import { createWorkspaceOrganizeModal } from './features/workspace-organize-modal.js';
import { bindWorkspacePreviewControls } from './features/workspace-preview-controls.js';
import { bindWorkspaceRowActions } from './features/workspace-row-actions.js';
import { bindWorkspaceThemeControls } from './features/workspace-theme-controls.js';
import { createGrid } from './grid.js';
import { createSelection } from './selection.js';
import { createWorkspaceUiState } from './state/workspace-ui-state.js';

const importBtn = document.getElementById('importBtn');
const importFile = document.getElementById('importFile');
const importForm = document.getElementById('importForm');
const tbody = document.getElementById('wsTbody');
const selectAll = document.getElementById('wsSelectAll');
const searchInput = document.getElementById('wsSearch');
const filterComponent = document.getElementById('wsFilterComponent');
const filterStatus = document.getElementById('wsFilterStatus');
const filterTag = document.getElementById('wsFilterTag');
const filterCustomTag = document.getElementById('wsFilterCustomTag');
const bulkBar = document.getElementById('bulkBar');
const bulkCount = document.getElementById('bulkCount');
const bulkExportSelected = document.getElementById('bulkExportSelected');
const bulkEditOpen = document.getElementById('bulkEditOpen');
const bulkOrganize = document.getElementById('bulkOrganize');
const pageInfo = document.getElementById('wsPageInfo');
const prevPageButton = document.getElementById('wsPrevPage');
const nextPageButton = document.getElementById('wsNextPage');
const collapseAllPreviews = document.getElementById('wsCollapsePreviews');
const importNoticeContainer = document.getElementById('importNoticeContainer');
const importNotice = document.getElementById('importNotice');
const importNoticeBadge = document.getElementById('importNoticeBadge');
const importNoticeOk = document.getElementById('importNoticeOk');
const importNoticeMessage = document.getElementById('importNoticeMessage');
const importNoticeClose = document.getElementById('importNoticeClose');

const folderTree = document.getElementById('wsFolderTree');
const folderLoading = document.getElementById('wsFolderLoading');
const folderEmpty = document.getElementById('wsFolderEmpty');
const sidebar = document.getElementById('wsSidebar');
const sidebarResizeHandle = document.getElementById('wsSidebarResizeHandle');
const sidebarContent = document.getElementById('wsSidebarContent');
const sidebarTitle = document.getElementById('wsSidebarTitle');
const sidebarInner = document.getElementById('wsSidebarInner');
const sidebarHeader = document.getElementById('wsSidebarHeader');
const newFolderButton = document.getElementById('wsNewFolderButton');

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
const uiState = createWorkspaceUiState({
    initialSearchParams: new URLSearchParams(window.location.search),
    defaultPageSize: 50
});
uiState.hydrateInputs({
    searchInput,
    filterComponent,
    filterStatus,
    filterTag,
    filterCustomTag
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

const bulkDeselectAll = document.getElementById('bulkDeselectAll');

let syncRowSelectionUi = () => {};
let importController = {
    clearNotice() {},
    showNotice() {}
};
let showNotice = () => {};
let previewControls = {
    refresh() {}
};

const tagsController = createWorkspaceTags({
    onTagsChanged: ({ workKey, tags }) => {
        dataController.updateTestCaseTags(workKey, tags);
    }
});

const dataController = createWorkspaceDataController({
    api,
    uiState,
    grid,
    selection,
    tbody,
    getTotalCountElement: () => document.getElementById('wsTotalCount'),
    pageInfo,
    prevPageButton,
    nextPageButton,
    searchInput,
    filterComponent,
    filterStatus,
    filterTag,
    filterCustomTag,
    syncRowSelectionUi: () => syncRowSelectionUi(),
    onTagClick: (args) => tagsController.open(args),
    onAfterRender: () => {
        previewControls.refresh();
    }
});

const folderTreeController = createWorkspaceFolderTree({
    uiState,
    api,
    folderTree,
    folderLoading,
    folderEmpty,
    newFolderButton,
    sidebar,
    sidebarResizeHandle,
    sidebarContent,
    sidebarTitle,
    sidebarInner,
    sidebarHeader,
    showNotice: (type, message) => showNotice(type, message),
    onFolderChanged: () => {
        dataController.applyFilters({ resetPage: true });
    }
});

const rowActions = bindWorkspaceRowActions({
    tbody,
    selection,
    uiState,
    rerenderCurrentPage: () => dataController.rerenderCurrentPage(),
    editBasePath: '/workspace/test-cases/'
});
syncRowSelectionUi = rowActions.syncRowSelectionUi;

previewControls = bindWorkspacePreviewControls({
    uiState,
    collapseAllButton: collapseAllPreviews,
    rerenderCurrentPage: () => dataController.rerenderCurrentPage()
});

importController = createWorkspaceImportController({
    api,
    importBtn,
    importFile,
    importForm,
    importNoticeContainer,
    importNotice,
    importNoticeBadge: importNoticeBadge || importNoticeOk,
    importNoticeOk,
    importNoticeMessage,
    importNoticeClose,
    onUploadComplete: async () => {
        await Promise.all([
            dataController.loadCurrentPage({ page: 0 }),
            dataController.loadFilterOptions(),
            folderTreeController.loadFolders()
        ]);
    }
});
showNotice = (type, message) => importController.showNotice(type, message);

const moveController = createWorkspaceMoveController({
    api,
    selection,
    loadFolders: () => folderTreeController.loadFolders(),
    reloadCurrentPage: () => dataController.loadCurrentPage(),
    getCurrentPageCases: () => dataController.getCurrentPageCases(),
    getNoticeApi: () => importController,
    getCsrf: () => ({
        headerName: document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN',
        token: document.querySelector('meta[name="_csrf"]')?.content || ''
    }),
    normalizeFolder: uiState.normalizeFolder,
    folderTreeModelRef: {
        get: () => folderTreeController.getFolderTreeModel()
    }
});

createWorkspaceDndController({
    tbody,
    folderTree,
    selection,
    normalizeFolder: uiState.normalizeFolder,
    moveWorkKeys: (input) => moveController.moveWorkKeys(input)
});

createWorkspaceOrganizeModal({
    selection,
    normalizeFolder: uiState.normalizeFolder,
    getKnownFolders: () => moveController.listKnownFolders(),
    moveWorkKeys: (input) => moveController.moveWorkKeys(input),
    bulkOrganize,
    organizeModal,
    organizeBackdrop,
    organizePanel,
    organizeClose,
    organizeCancel,
    organizeSave,
    organizeFolderSelect,
    organizeFolderInput,
    organizeError
});

bindWorkspaceHeaderControls();
bindWorkspaceThemeControls();

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
    refreshCurrentPage: async () => {
        await dataController.loadCurrentPage();
    },
    refreshFolders: async () => {
        await folderTreeController.loadFolders();
    },
    showNotice: (type, message) => {
        importController.showNotice(type, message);
    }
});

selection.setSelectionChangeHandler((selectedIds) => {
    exportSelected.updateButtonState(selectedIds);
    syncRowSelectionUi();
    bulkEdit.onSelectionChanged(selectedIds);
});

if (drawer && typeof drawer.bind === 'function') {
    drawer.bind(tbody);
}

let searchDebounceHandle = 0;
if (searchInput) {
    searchInput.addEventListener('input', () => {
        window.clearTimeout(searchDebounceHandle);
        searchDebounceHandle = window.setTimeout(() => {
            dataController.applyFilters({ resetPage: true });
        }, 180);
    });
}
if (filterComponent) {
    filterComponent.addEventListener('change', () => dataController.applyFilters({ resetPage: true }));
}
if (filterStatus) {
    filterStatus.addEventListener('change', () => dataController.applyFilters({ resetPage: true }));
}
if (filterTag) {
    filterTag.addEventListener('change', () => dataController.applyFilters({ resetPage: true }));
}
if (prevPageButton) {
    prevPageButton.addEventListener('click', () => {
        const pageState = uiState.getPageState();
        if (pageState.page <= 0 || pageState.isLoading) {
            return;
        }
        dataController.loadCurrentPage({ page: pageState.page - 1 });
    });
}
if (nextPageButton) {
    nextPageButton.addEventListener('click', () => {
        const pageState = uiState.getPageState();
        if (!pageState.hasNext || pageState.isLoading) {
            return;
        }
        dataController.loadCurrentPage({ page: pageState.page + 1 });
    });
}

dataController.loadCurrentPage();
dataController.loadFilterOptions();
folderTreeController.loadFolders();


if (bulkDeselectAll) {
    bulkDeselectAll.addEventListener('click', () => {
        selection.clearSelection();
    });
}
