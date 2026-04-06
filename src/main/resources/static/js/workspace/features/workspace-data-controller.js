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

    const previous = String(selectEl.value || selectEl.dataset.pendingValue || '').trim();
    const nextValues = Array.isArray(values) ? values.slice() : [];
    if (previous && !nextValues.includes(previous)) {
        nextValues.push(previous);
    }

    selectEl.innerHTML = '<option value="">All</option>';
    for (const value of uniqueSorted(nextValues)) {
        const option = document.createElement('option');
        option.value = String(value || '');
        option.textContent = String(value || '');
        selectEl.appendChild(option);
    }

    if (previous) {
        selectEl.value = previous;
    }
    selectEl.dataset.pendingValue = '';
}

export function createWorkspaceDataController(options) {
    const api = options.api;
    const uiState = options.uiState;
    const grid = options.grid;
    const selection = options.selection;
    const tbody = options.tbody;
    const totalCount = options.totalCount;
    const getTotalCountElement = typeof options.getTotalCountElement === 'function'
        ? options.getTotalCountElement
        : null;
    const pageInfo = options.pageInfo;
    const prevPageButton = options.prevPageButton;
    const nextPageButton = options.nextPageButton;
    const searchInput = options.searchInput;
    const filterComponent = options.filterComponent;
    const filterStatus = options.filterStatus;
    const filterTag = options.filterTag;
    const filterCustomTag = options.filterCustomTag;
    const syncRowSelectionUi = options.syncRowSelectionUi;
    const onTagClick = typeof options.onTagClick === 'function' ? options.onTagClick : null;
    const onAfterRender = typeof options.onAfterRender === 'function' ? options.onAfterRender : null;

    let currentPageCases = [];
    let activePageRequestId = 0;

    function getFilterElements() {
        return {
            searchInput,
            filterComponent,
            filterStatus,
            filterTag,
            filterCustomTag
        };
    }

    function updatePageControls() {
        const pageState = uiState.getPageState();
        const resolvedTotalPages = Math.max(pageState.totalPages, 1);
        const resolvedCurrentPage = Math.min(pageState.page + 1, resolvedTotalPages);

        if (pageInfo) {
            pageInfo.textContent = 'Page ' + resolvedCurrentPage + ' of ' + resolvedTotalPages;
        }
        if (prevPageButton) {
            prevPageButton.disabled = pageState.isLoading || pageState.page <= 0;
        }
        if (nextPageButton) {
            nextPageButton.disabled = pageState.isLoading || !pageState.hasNext;
        }
    }

    function showWorkspaceLoading(message) {
        if (!tbody) {
            return;
        }

        const detail = message || 'Loading test cases...';
        tbody.innerHTML =
            '<tr class="border-b border-white/10">' +
                '<td class="px-6 sm:px-8 py-6" colspan="7">' +
                    '<p class="text-sm text-white/45">' + detail + '</p>' +
                '</td>' +
            '</tr>';
    }

    function renderCurrentPage() {
        const pageState = uiState.getPageState();

        const totalCountElement = getTotalCountElement ? getTotalCountElement() : totalCount;
        if (totalCountElement) {
            totalCountElement.textContent = '(' + String(pageState.totalCount) + ')';
        }

        const visibleIds = currentPageCases.map((testCase) => testCase.workKey).filter(Boolean);
        uiState.retainExpandedPreviewKeys(visibleIds);
        selection.retainSelectedIds(visibleIds);
        const expandedPreviewKeys = uiState.getExpandedPreviewKeys();
        grid.renderRows(currentPageCases, new Set(selection.getSelectedIds()), {
            expandedPreviewKeys,
            onTagClick
        });
        selection.setVisibleIds(visibleIds);
        selection.bindRowCheckboxes(tbody);
        if (typeof syncRowSelectionUi === 'function') {
            syncRowSelectionUi();
        }
        updatePageControls();
        uiState.syncWorkspaceUrl(getFilterElements());
        if (onAfterRender) {
            onAfterRender({
                currentPageCases: currentPageCases.slice(),
                expandedPreviewKeys,
                visibleIds
            });
        }
    }

    function loadCurrentPage(options) {
        const loadOptions = options || {};
        uiState.applyPageLoadOptions(loadOptions);

        const requestId = ++activePageRequestId;
        uiState.setLoading(true);
        updatePageControls();
        showWorkspaceLoading(loadOptions.loadingMessage || 'Loading test cases...');

        return api.fetchPage(uiState.buildPageRequestParams(getFilterElements()))
            .then((payload) => {
                if (requestId !== activePageRequestId) {
                    return;
                }

                if (payload.totalPages > 0 && payload.page >= payload.totalPages) {
                    uiState.setPage(Math.max(payload.totalPages - 1, 0));
                    return loadCurrentPage({ loadingMessage: 'Loading test cases...' });
                }

                currentPageCases = payload.cases;
                uiState.updatePageData({
                    page: payload.page != null ? payload.page : uiState.getPageState().page,
                    totalCount: payload.totalCount,
                    totalPages: payload.totalPages,
                    hasNext: payload.hasNext
                });
                renderCurrentPage();
            })
            .catch((error) => {
                if (requestId !== activePageRequestId) {
                    return;
                }

                console.error('Failed to load test cases', error);
                currentPageCases = [];
                uiState.updatePageData({
                    totalCount: 0,
                    totalPages: 1,
                    hasNext: false
                });
                renderCurrentPage();
            })
            .finally(() => {
                if (requestId !== activePageRequestId) {
                    return;
                }

                uiState.setLoading(false);
                updatePageControls();
            });
    }

    function applyFilters(options) {
        return loadCurrentPage(options);
    }

    function loadFilterOptions() {
        return api.fetchFilterOptions()
            .then((values) => {
                populateSelect(filterComponent, values.components);
                populateSelect(filterStatus, values.statuses);
                populateSelect(filterTag, values.tags);
            })
            .catch(() => {
                const components = uniqueSorted(currentPageCases.map((item) => item?.components));
                const statuses = uniqueSorted(currentPageCases.map((item) => item?.status));
                const tags = uniqueSorted(currentPageCases.flatMap((item) => [item?.components, item?.testCaseType]));
                populateSelect(filterComponent, components);
                populateSelect(filterStatus, statuses);
                populateSelect(filterTag, tags);
            });
    }

    function getCurrentPageCases() {
        return currentPageCases.slice();
    }

    function rerenderCurrentPage() {
        renderCurrentPage();
    }

    return {
        applyFilters,
        getCurrentPageCases,
        loadCurrentPage,
        loadFilterOptions,
        rerenderCurrentPage,
        updatePageControls
    };
}
