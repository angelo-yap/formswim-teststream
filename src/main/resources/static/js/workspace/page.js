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
const prevPageBtn = document.getElementById('wsPrevPage');
const nextPageBtn = document.getElementById('wsNextPage');
const pageInfo = document.getElementById('wsPageInfo');

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
    drawerTags: document.getElementById('drawerTags'),
    getTestCaseById: grid.getTestCaseById
});

let searchDebounceHandle = null;
let latestRequestId = 0;
let currentPage = 0;
const pageSize = 50;
let totalPages = 1;
let totalCountValue = 0;

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

function renderCases(cases) {
    const rows = Array.isArray(cases) ? cases : [];
    grid.renderRows(rows, new Set(selection.getSelectedIds()));
    selection.setVisibleIds(rows.map((testCase) => testCase.workKey).filter(Boolean));
    selection.bindRowCheckboxes(tbody);
    renderTotalCount();
}

function renderTotalCount() {
    if (totalCount) {
        totalCount.textContent = String(totalCountValue);
    }
}

function renderPaginationState() {
    if (pageInfo) {
        const safeTotalPages = Math.max(totalPages, 1);
        pageInfo.textContent = 'Page ' + (currentPage + 1) + ' of ' + safeTotalPages;
    }
    if (prevPageBtn) {
        prevPageBtn.disabled = currentPage <= 0;
    }
    if (nextPageBtn) {
        nextPageBtn.disabled = currentPage + 1 >= Math.max(totalPages, 1);
    }
}

function populateSelectOptions(selectElement, values) {
    if (!selectElement) {
        return;
    }

    const selectedValue = selectElement.value;
    selectElement.innerHTML = '';

    const allOption = document.createElement('option');
    allOption.value = '';
    allOption.textContent = 'All';
    selectElement.appendChild(allOption);

    (Array.isArray(values) ? values : []).forEach((value) => {
        if (!value) {
            return;
        }
        const option = document.createElement('option');
        option.value = value;
        option.textContent = value;
        selectElement.appendChild(option);
    });

    const canKeepSelection = selectedValue && Array.from(selectElement.options).some((option) => option.value === selectedValue);
    selectElement.value = canKeepSelection ? selectedValue : '';
}

function populateTagOptions(tags) {
    populateSelectOptions(filterTag, tags);
}

function loadTagOptions() {
    fetch(tagsBaseUrl)
        .then((response) => {
            if (!response.ok) {
                throw new Error('Failed to load tags with status ' + response.status);
            }
            return response.json();
        })
        .then((tags) => {
            populateTagOptions(tags);
        })
        .catch((error) => {
            console.error('Failed to load tag options', error);
        });
}

function loadComponentOptions() {
    fetch(componentsBaseUrl)
        .then((response) => {
            if (!response.ok) {
                throw new Error('Failed to load components with status ' + response.status);
            }
            return response.json();
        })
        .then((components) => {
            populateSelectOptions(filterComponent, components);
        })
        .catch((error) => {
            console.error('Failed to load component options', error);
        });
}

function loadStatusOptions() {
    fetch(statusesBaseUrl)
        .then((response) => {
            if (!response.ok) {
                throw new Error('Failed to load statuses with status ' + response.status);
            }
            return response.json();
        })
        .then((statuses) => {
            populateSelectOptions(filterStatus, statuses);
        })
        .catch((error) => {
            console.error('Failed to load status options', error);
        });
}

function buildFilterParams() {
    const params = new URLSearchParams();
    const search = searchInput ? searchInput.value.trim() : '';
    const component = filterComponent ? filterComponent.value.trim() : '';
    const status = filterStatus ? filterStatus.value.trim() : '';
    const tag = filterTag ? filterTag.value.trim() : '';

    if (search) {
        params.set('search', search);
    }
    if (component) {
        params.set('component', component);
    }
    if (status) {
        params.set('status', status);
    }
    if (tag) {
        params.set('tag', tag);
    }
    params.set('page', String(currentPage));
    params.set('size', String(pageSize));

    return params;
}

function loadFilteredTestCases() {
    const requestId = ++latestRequestId;
    const params = buildFilterParams();
    const url = params.toString() ? apiBaseUrl + '?' + params.toString() : apiBaseUrl;

    fetch(url)
        .then((response) => {
            if (!response.ok) {
                throw new Error('Failed to load test cases with status ' + response.status);
            }
            const parsedTotalCount = Number.parseInt(response.headers.get('X-Total-Count') || '0', 10);
            totalCountValue = Number.isFinite(parsedTotalCount) && parsedTotalCount >= 0 ? parsedTotalCount : 0;
            const parsedTotalPages = Number.parseInt(response.headers.get('X-Total-Pages') || '1', 10);
            totalPages = Number.isFinite(parsedTotalPages) && parsedTotalPages > 0 ? parsedTotalPages : 1;
            renderTotalCount();
            renderPaginationState();
            return response.json();
        })
        .then((data) => {
            // Avoid stale responses overwriting newer filter results.
            if (requestId !== latestRequestId) {
                return;
            }
            renderCases(data);
        })
        .catch((error) => {
            console.error('Failed to load test cases', error);
            if (requestId !== latestRequestId) {
                return;
            }
            totalCountValue = 0;
            renderTotalCount();
            renderCases([]);
        });
}

function applyFilters() {
    currentPage = 0;
    totalPages = 1;
    renderPaginationState();
    loadFilteredTestCases();
}

if (searchInput) {
    searchInput.addEventListener('input', () => {
        if (searchDebounceHandle) {
            window.clearTimeout(searchDebounceHandle);
        }
        searchDebounceHandle = window.setTimeout(() => {
            applyFilters();
        }, 250);
    });
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
if (prevPageBtn) {
    prevPageBtn.addEventListener('click', () => {
        if (currentPage <= 0) {
            return;
        }
        currentPage -= 1;
        loadFilteredTestCases();
    });
}
if (nextPageBtn) {
    nextPageBtn.addEventListener('click', () => {
        if (currentPage + 1 >= Math.max(totalPages, 1)) {
            return;
        }
        currentPage += 1;
        loadFilteredTestCases();
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

                loadComponentOptions();
                loadStatusOptions();
                loadTagOptions();
                loadFilteredTestCases();
            })
            .catch((error) => {
                console.error('Upload failed', error);
                showImportNotice('error', 'Import failed. Please try again.');
                importBtn.textContent = 'Import';
                importBtn.disabled = false;
            });
    });
}

loadComponentOptions();
loadStatusOptions();
loadTagOptions();
loadFilteredTestCases();
