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
const importNoticeContainer = document.getElementById('importNoticeContainer');
const importNotice = document.getElementById('importNotice');
const importNoticeBadge = document.getElementById('importNoticeBadge');
const importNoticeMessage = document.getElementById('importNoticeMessage');
const importNoticeClose = document.getElementById('importNoticeClose');

const apiBaseUrl = document.querySelector('meta[name="workspace-api-base"]')?.content || '/api/testcases';
const exportBaseUrl = document.querySelector('meta[name="workspace-export-base"]')?.content || '/workspace/export';

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
    getTestCaseById: grid.getTestCaseById
});

let allTestCases = [];

selection.setSelectionChangeHandler((selectedIds) => {
    exportSelected.updateButtonState(selectedIds);
});

drawer.bind(tbody);

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

    const filtered = allTestCases.filter((testCase) => {
        const workKey = (testCase.workKey || '').toLowerCase();
        const summary = (testCase.summary || '').toLowerCase();
        const components = (testCase.components || '').toLowerCase();
        const testCaseType = (testCase.testCaseType || '').toLowerCase();
        const testCaseStatus = (testCase.status || '').toLowerCase();

        const matchesQuery = !query || workKey.includes(query) || summary.includes(query) || components.includes(query);
        const matchesComponent = !component || components.includes(component);
        const matchesStatus = !status || testCaseStatus === status;
        const matchesTag = !tag || components.includes(tag) || testCaseType.includes(tag);
        return matchesQuery && matchesComponent && matchesStatus && matchesTag;
    });

    grid.renderRows(filtered, new Set(selection.getSelectedIds()));
    selection.setVisibleIds(filtered.map((testCase) => testCase.workKey).filter(Boolean));
    selection.bindRowCheckboxes(tbody);
}

function loadAllTestCases() {
    fetch(apiBaseUrl)
        .then((response) => response.json())
        .then((data) => {
            allTestCases = Array.isArray(data) ? data : [];
            if (totalCount) {
                totalCount.textContent = String(allTestCases.length);
            }
            applyFilters();
        })
        .catch((error) => {
            console.error('Failed to load test cases', error);
            allTestCases = [];
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
            .then((response) => {
                if (!response.ok) {
                    throw new Error('Upload failed with status ' + response.status);
                }
                return response.json();
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

                if (json.message) {
                    showImportNotice('success', json.message);
                } else if (json.errors && json.errors.length > 0) {
                    showImportNotice('error', 'Import warnings\n' + json.errors.join('\n'));
                }

                loadAllTestCases();
            })
            .catch((error) => {
                console.error('Upload failed', error);
                showImportNotice('error', 'Import failed. Please try again.');
                importBtn.textContent = 'Import';
                importBtn.disabled = false;
            });
    });
}

loadAllTestCases();
