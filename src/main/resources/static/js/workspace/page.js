import { createWorkspacePageApi } from './api/workspace-page-api.js';
import { createBulkEdit } from './bulk-edit.js';
import { createWorkspaceFolderTree } from './components/workspace-folder-tree.js';
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
const bulkBar = document.getElementById('bulkBar');
const bulkCount = document.getElementById('bulkCount');
const bulkExportSelected = document.getElementById('bulkExportSelected');
const bulkEditOpen = document.getElementById('bulkEditOpen');
const bulkDelete = document.getElementById('bulkDelete');
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
const newTestcaseButton = document.getElementById('wsNewTestcaseButton');
const newTestcaseForm = document.getElementById('wsNewTestcaseForm');
const newTestcaseIdInput = document.getElementById('wsNewTestcaseId');
const newTestcaseNameInput = document.getElementById('wsNewTestcaseName');

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
    syncRowSelectionUi: () => syncRowSelectionUi(),
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

function clearNewTestcaseForm() {
    if (newTestcaseIdInput) {
        newTestcaseIdInput.value = '';
    }
    if (newTestcaseNameInput) {
        newTestcaseNameInput.value = '';
    }
}

function hideNewTestcaseForm() {
    if (!newTestcaseForm) {
        return;
    }
    newTestcaseForm.classList.add('hidden');
    clearNewTestcaseForm();
}

function showNewTestcaseForm() {
    if (!newTestcaseForm) {
        return;
    }
    newTestcaseForm.classList.remove('hidden');
    window.requestAnimationFrame(() => {
        if (newTestcaseIdInput) {
            newTestcaseIdInput.focus();
            newTestcaseIdInput.select();
        }
    });
}

async function submitNewTestcaseFromForm() {
    if (typeof api.createTestCase !== 'function') {
        showNotice('error', 'Testcase create API is unavailable.');
        return;
    }

    const workKey = String(newTestcaseIdInput?.value || '').trim();
    const name = String(newTestcaseNameInput?.value || '').trim();
    const folder = uiState.getSelectedFolder() || '';

    if (!workKey || !name) {
        showNotice('error', 'Testcase ID and name are required.');
        return;
    }

    try {
        await api.createTestCase({
            workKey,
            name,
            folder
        });

        hideNewTestcaseForm();
        await Promise.all([
            dataController.loadCurrentPage({ page: 0 }),
            dataController.loadFilterOptions()
        ]);
        showNotice('success', 'Testcase sucessfully created');
    } catch (error) {
        showNotice('error', error?.message || 'Failed to create testcase.');
    }
}

if (newTestcaseButton) {
    newTestcaseButton.addEventListener('click', () => {
        if (!newTestcaseForm || newTestcaseForm.classList.contains('hidden')) {
            showNewTestcaseForm();
            return;
        }
        hideNewTestcaseForm();
    });
}

if (newTestcaseIdInput) {
    newTestcaseIdInput.addEventListener('keydown', async (event) => {
        if (event.key === 'Escape') {
            event.preventDefault();
            hideNewTestcaseForm();
            return;
        }
        if (event.key === 'Enter') {
            event.preventDefault();
            await submitNewTestcaseFromForm();
        }
    });
}

if (newTestcaseNameInput) {
    newTestcaseNameInput.addEventListener('keydown', async (event) => {
        if (event.key === 'Escape') {
            event.preventDefault();
            hideNewTestcaseForm();
            return;
        }
        if (event.key === 'Enter') {
            event.preventDefault();
            await submitNewTestcaseFromForm();
        }
    });
}

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

function confirmBulkDeleteTestcases(count) {
    return new Promise((resolve) => {
        const isSoftTheme = document.body?.dataset?.workspaceTheme !== 'black';
        const previousActiveElement = document.activeElement;

        const root = document.createElement('div');
        root.className = 'fixed inset-0 z-[1400] flex items-center justify-center p-4';
        root.style.backgroundColor = isSoftTheme ? 'rgba(20, 20, 20, 0.46)' : 'rgba(0, 0, 0, 0.62)';
        root.style.backdropFilter = 'blur(2px)';

        const notice = document.createElement('div');
        notice.className = 'w-[min(32rem,calc(100vw-2rem))] border px-5 py-5 text-sm text-white/85 rounded-md shadow-[0_24px_80px_rgba(0,0,0,0.55)]';
        notice.style.backgroundColor = isSoftTheme ? '#2b2b2b' : 'rgba(0, 0, 0, 0.95)';
        notice.style.borderColor = 'rgba(255, 255, 255, 0.24)';
        notice.setAttribute('role', 'dialog');
        notice.setAttribute('aria-modal', 'true');
        notice.setAttribute('aria-label', 'Confirm testcase delete');

        const headingWrap = document.createElement('div');
        headingWrap.className = 'min-w-0';

        const badge = document.createElement('span');
        badge.className = 'font-bold text-black px-2 py-1 mr-3';
        badge.style.backgroundColor = '#E7FF02';
        badge.textContent = 'Confirm';

        const heading = document.createElement('span');
        heading.className = 'font-medium text-white';
        heading.textContent = 'Delete testcase(s)?';

        const message = document.createElement('p');
        message.className = 'mt-2 break-words text-white/75';
        message.textContent = 'Delete ' + count + ' selected testcase(s)? This action cannot be undone.';

        const actions = document.createElement('div');
        actions.className = 'mt-5 flex items-center justify-end gap-2';

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

        actions.appendChild(cancelButton);
        actions.appendChild(deleteButton);

        headingWrap.appendChild(badge);
        headingWrap.appendChild(heading);
        headingWrap.appendChild(message);
        notice.appendChild(headingWrap);
        notice.appendChild(actions);
        root.appendChild(notice);
        document.body.appendChild(root);

        const cleanup = (confirmed) => {
            document.removeEventListener('keydown', onKeyDown);
            if (root.parentNode) {
                root.parentNode.removeChild(root);
            }
            if (previousActiveElement && typeof previousActiveElement.focus === 'function') {
                previousActiveElement.focus();
            }
            resolve(Boolean(confirmed));
        };

        const onKeyDown = (event) => {
            if (event.key === 'Escape') {
                event.preventDefault();
                cleanup(false);
                return;
            }

            if (event.key === 'Tab') {
                const first = cancelButton;
                const last = deleteButton;
                if (event.shiftKey && document.activeElement === first) {
                    event.preventDefault();
                    last.focus();
                    return;
                }
                if (!event.shiftKey && document.activeElement === last) {
                    event.preventDefault();
                    first.focus();
                }
            }
        };

        document.addEventListener('keydown', onKeyDown);
        cancelButton.addEventListener('click', () => cleanup(false));
        deleteButton.addEventListener('click', () => cleanup(true));
        root.addEventListener('click', (event) => {
            if (event.target === root) {
                cleanup(false);
            }
        });

        window.requestAnimationFrame(() => {
            cancelButton.focus();
        });
    });
}

if (bulkDelete) {
    bulkDelete.addEventListener('click', async () => {
        if (typeof api.deleteTestCase !== 'function') {
            showNotice('error', 'Testcase delete API is unavailable.');
            return;
        }

        const selectedWorkKeys = selection.getSelectedIds();
        if (!Array.isArray(selectedWorkKeys) || selectedWorkKeys.length === 0) {
            showNotice('error', 'Select at least one testcase to delete.');
            return;
        }

        const confirmed = await confirmBulkDeleteTestcases(selectedWorkKeys.length);
        if (!confirmed) {
            return;
        }

        const results = await Promise.allSettled(
            selectedWorkKeys.map((workKey) => api.deleteTestCase(workKey))
        );

        const failed = [];
        for (let i = 0; i < results.length; i += 1) {
            if (results[i].status === 'rejected') {
                failed.push(selectedWorkKeys[i]);
            }
        }

        await Promise.all([
            dataController.loadCurrentPage(),
            dataController.loadFilterOptions()
        ]);
        selection.clearSelection();

        if (failed.length === 0) {
            showNotice('success', 'Testcase deleted.');
            return;
        }

        if (failed.length === selectedWorkKeys.length) {
            showNotice('error', 'Testcase delete failed.');
            return;
        }

        showNotice('error', 'Testcase delete failed.');
    });
}
