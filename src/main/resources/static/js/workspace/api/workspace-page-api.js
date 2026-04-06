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

function getCsrfHeaders() {
    const headerName = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';
    const token = document.querySelector('meta[name="_csrf"]')?.content || '';
    return {
        [headerName]: token
    };
}

async function parseJsonOrEmpty(response) {
    const contentType = response.headers.get('content-type') || '';
    if (!contentType.includes('application/json')) {
        return null;
    }

    try {
        return await response.json();
    } catch (error) {
        return null;
    }
}

async function toApiError(response, fallbackMessage) {
    const payload = await parseJsonOrEmpty(response);
    const message = payload?.message || fallbackMessage || ('Request failed with status ' + response.status);
    const error = new Error(message);
    error.status = response.status;
    error.payload = payload;
    throw error;
}

export function createWorkspacePageApi(options) {
    const apiBaseUrl = options.apiBaseUrl;
    const componentsBaseUrl = options.componentsBaseUrl;
    const statusesBaseUrl = options.statusesBaseUrl;
    const tagsBaseUrl = options.tagsBaseUrl;

    function fetchPage(params) {
        return fetch(apiBaseUrl + '?' + params.toString())
            .then((response) => {
                if (!response.ok) {
                    throw new Error('Test cases failed with status ' + response.status);
                }

                const nextTotalCount = Number(response.headers.get('X-Total-Count'));
                const nextTotalPages = Number(response.headers.get('X-Total-Pages'));
                const nextPage = Number(response.headers.get('X-Page'));
                const nextHasNext = String(response.headers.get('X-Has-Next')).toLowerCase() === 'true';

                return response.json().then((data) => ({
                    cases: Array.isArray(data) ? data : [],
                    hasNext: nextHasNext,
                    page: Number.isFinite(nextPage) ? nextPage : null,
                    totalCount: Number.isFinite(nextTotalCount) ? nextTotalCount : 0,
                    totalPages: Number.isFinite(nextTotalPages) && nextTotalPages > 0 ? nextTotalPages : 1
                }));
            });
    }

    function fetchFolders() {
        return fetch('/api/folders')
            .then((response) => {
                if (!response.ok) {
                    throw new Error('Folders failed with status ' + response.status);
                }
                return response.json();
            })
            .then((data) => (Array.isArray(data) ? data : []));
    }

    function fetchFolderNodes() {
        return fetch('/api/folders/nodes')
            .then((response) => {
                if (!response.ok) {
                    throw new Error('Folder nodes failed with status ' + response.status);
                }
                return response.json();
            })
            .then((data) => (Array.isArray(data) ? data : []));
    }

    async function createFolder(input) {
        const response = await fetch('/api/folders', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...getCsrfHeaders()
            },
            body: JSON.stringify({
                name: input?.name || '',
                parentId: Object.prototype.hasOwnProperty.call(input || {}, 'parentId') ? input.parentId : undefined
            })
        });

        if (!response.ok) {
            return toApiError(response, 'Failed to create folder.');
        }

        return parseJsonOrEmpty(response);
    }

    async function createTestCase(input) {
        const response = await fetch('/api/testcases', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...getCsrfHeaders()
            },
            body: JSON.stringify({
                workKey: input?.workKey || '',
                name: input?.name || '',
                folder: input?.folder || ''
            })
        });

        if (!response.ok) {
            return toApiError(response, 'Failed to create testcase.');
        }

        return parseJsonOrEmpty(response);
    }

    async function updateFolder(folderId, input) {
        const payload = {};
        if (Object.prototype.hasOwnProperty.call(input || {}, 'name')) {
            payload.name = input.name;
        }
        if (Object.prototype.hasOwnProperty.call(input || {}, 'parentId')) {
            payload.parentId = input.parentId;
        }

        const response = await fetch('/api/folders/' + encodeURIComponent(String(folderId)), {
            method: 'PATCH',
            headers: {
                'Content-Type': 'application/json',
                ...getCsrfHeaders()
            },
            body: JSON.stringify(payload)
        });

        if (!response.ok) {
            return toApiError(response, 'Failed to update folder.');
        }

        return parseJsonOrEmpty(response);
    }

    async function deleteFolder(folderId) {
        const response = await fetch('/api/folders/' + encodeURIComponent(String(folderId)), {
            method: 'DELETE',
            headers: {
                ...getCsrfHeaders()
            }
        });

        if (!response.ok) {
            return toApiError(response, 'Failed to delete folder.');
        }
    }

    async function deleteTestCase(workKey) {
        const response = await fetch('/api/testcases/' + encodeURIComponent(String(workKey || '')), {
            method: 'DELETE',
            headers: {
                ...getCsrfHeaders()
            }
        });

        if (!response.ok) {
            return toApiError(response, 'Failed to delete testcase.');
        }
    }

    function fetchFilterOptions() {
        return Promise.all([
            fetchOptionValues(componentsBaseUrl),
            fetchOptionValues(statusesBaseUrl),
            fetchOptionValues(tagsBaseUrl)
        ]).then(([components, statuses, tags]) => ({
            components,
            statuses,
            tags
        }));
    }

    function bulkMove(input) {
        const moveInput = input || {};
        return fetch('/api/testcases/bulk-move', {
            method: 'PATCH',
            headers: {
                'Content-Type': 'application/json',
                [moveInput.csrf?.headerName || 'X-CSRF-TOKEN']: moveInput.csrf?.token || ''
            },
            body: JSON.stringify({
                workKeys: Array.isArray(moveInput.workKeys) ? moveInput.workKeys : [],
                targetFolder: moveInput.targetFolder || ''
            })
        }).then(async (response) => ({
            ok: response.ok,
            status: response.status,
            result: response.ok ? await response.json() : null
        }));
    }

    function uploadFile(input) {
        const uploadInput = input || {};
        const formData = new FormData();
        formData.append('file', uploadInput.file);

        if (uploadInput.csrfField?.name && uploadInput.csrfField?.value) {
            formData.append(uploadInput.csrfField.name, uploadInput.csrfField.value);
        }

        return fetch('/api/upload', {
            method: 'POST',
            body: formData,
            signal: uploadInput.signal
        }).then(async (response) => {
            if (response.ok) {
                return response.json();
            }

            let detail = '';
            try {
                const contentType = response.headers.get('content-type') || '';
                if (contentType.includes('application/json')) {
                    const json = await response.json();
                    detail = json?.message || (Array.isArray(json?.errors) ? json.errors.join('\n') : '');
                } else {
                    detail = await response.text();
                }
            } catch (error) {
                // ignore parsing failure
            }

            const suffix = detail ? ': ' + String(detail).trim() : '';
            throw new Error('Upload failed with status ' + response.status + suffix);
        });
    }

    return {
        bulkMove,
        createFolder,
        createTestCase,
        deleteFolder,
        deleteTestCase,
        fetchFilterOptions,
        fetchFolders,
        fetchFolderNodes,
        fetchPage,
        updateFolder,
        uploadFile
    };
}
