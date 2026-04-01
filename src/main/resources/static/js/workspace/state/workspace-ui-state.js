export function createWorkspaceUiState(options) {
    const initialSearchParams = options.initialSearchParams || new URLSearchParams();
    const defaultPageSize = Number(options.defaultPageSize) || 50;

    let selectedFolder = normalizeFolder(initialSearchParams.get('folder') || '');
    const pageState = {
        page: Math.max(Number.parseInt(initialSearchParams.get('page') || '0', 10) || 0, 0),
        size: Math.min(Math.max(Number.parseInt(initialSearchParams.get('size') || String(defaultPageSize), 10) || defaultPageSize, 1), 200),
        totalCount: 0,
        totalPages: 1,
        hasNext: false,
        isLoading: false
    };
    let sidebarExpanded = true;

    function normalizeFolder(value) {
        const raw = String(value || '').trim();
        if (!raw) {
            return '';
        }

        const asSlash = raw.replace(/\\/g, '/');
        return asSlash.replace(/^\/+/, '').replace(/\/+$/, '');
    }

    function hydrateInputs(input) {
        const elements = input || {};

        if (elements.searchInput) {
            elements.searchInput.value = initialSearchParams.get('search') || '';
        }
        if (elements.filterComponent) {
            elements.filterComponent.dataset.pendingValue = initialSearchParams.get('component') || '';
        }
        if (elements.filterStatus) {
            elements.filterStatus.dataset.pendingValue = initialSearchParams.get('status') || '';
        }
        if (elements.filterTag) {
            elements.filterTag.dataset.pendingValue = initialSearchParams.get('tag') || '';
        }
    }

    function getActiveFilterValue(selectEl) {
        if (!selectEl) {
            return '';
        }

        return String(selectEl.value || selectEl.dataset.pendingValue || '').trim();
    }

    function buildQuerySnapshot(elements) {
        const input = elements || {};
        return {
            search: input.searchInput ? input.searchInput.value.trim() : '',
            component: getActiveFilterValue(input.filterComponent),
            status: getActiveFilterValue(input.filterStatus),
            tag: getActiveFilterValue(input.filterTag),
            folder: selectedFolder
        };
    }

    function buildPageRequestParams(elements) {
        const params = new URLSearchParams();
        const query = buildQuerySnapshot(elements);
        params.set('page', String(pageState.page));
        params.set('size', String(pageState.size));

        if (query.search) {
            params.set('search', query.search);
        }
        if (query.status) {
            params.set('status', query.status);
        }
        if (query.component) {
            params.set('component', query.component);
        }
        if (query.tag) {
            params.set('tag', query.tag);
        }
        if (query.folder) {
            params.set('folder', query.folder);
        }

        return params;
    }

    function syncWorkspaceUrl(elements) {
        const params = new URLSearchParams();
        const query = buildQuerySnapshot(elements);

        if (query.search) {
            params.set('search', query.search);
        }
        if (query.status) {
            params.set('status', query.status);
        }
        if (query.component) {
            params.set('component', query.component);
        }
        if (query.tag) {
            params.set('tag', query.tag);
        }
        if (query.folder) {
            params.set('folder', query.folder);
        }
        if (pageState.page > 0) {
            params.set('page', String(pageState.page));
        }
        if (pageState.size !== defaultPageSize) {
            params.set('size', String(pageState.size));
        }

        const nextQuery = params.toString();
        const nextUrl = nextQuery ? '/workspace?' + nextQuery : '/workspace';
        window.history.replaceState({}, '', nextUrl);
    }

    function applyPageLoadOptions(options) {
        const loadOptions = options || {};
        if (loadOptions.resetPage) {
            pageState.page = 0;
        }
        if (loadOptions.page != null) {
            pageState.page = Math.max(Number(loadOptions.page) || 0, 0);
        }
    }

    function updatePageData(payload) {
        const nextPayload = payload || {};
        if (nextPayload.page != null) {
            pageState.page = Math.max(Number(nextPayload.page) || 0, 0);
        }
        if (nextPayload.totalCount != null) {
            pageState.totalCount = Math.max(Number(nextPayload.totalCount) || 0, 0);
        }
        if (nextPayload.totalPages != null) {
            pageState.totalPages = Math.max(Number(nextPayload.totalPages) || 1, 1);
        }
        if (nextPayload.hasNext != null) {
            pageState.hasNext = Boolean(nextPayload.hasNext);
        }
    }

    function setLoading(isLoading) {
        pageState.isLoading = Boolean(isLoading);
    }

    function setSelectedFolder(value) {
        selectedFolder = normalizeFolder(value || '');
    }

    function getSelectedFolder() {
        return selectedFolder;
    }

    function getPageState() {
        return {
            page: pageState.page,
            size: pageState.size,
            totalCount: pageState.totalCount,
            totalPages: pageState.totalPages,
            hasNext: pageState.hasNext,
            isLoading: pageState.isLoading
        };
    }

    function setPage(page) {
        pageState.page = Math.max(Number(page) || 0, 0);
    }

    function setSidebarExpanded(expanded) {
        sidebarExpanded = Boolean(expanded);
    }

    function isSidebarExpanded() {
        return sidebarExpanded;
    }

    return {
        applyPageLoadOptions,
        buildPageRequestParams,
        getActiveFilterValue,
        getPageState,
        getSelectedFolder,
        hydrateInputs,
        isSidebarExpanded,
        normalizeFolder,
        setLoading,
        setPage,
        setSelectedFolder,
        setSidebarExpanded,
        syncWorkspaceUrl,
        updatePageData
    };
}
