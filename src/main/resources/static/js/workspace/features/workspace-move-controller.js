export function createWorkspaceMoveController(options) {
    const api = options.api;
    const selection = options.selection;
    const loadFolders = options.loadFolders;
    const reloadCurrentPage = options.reloadCurrentPage;
    const getCurrentPageCases = options.getCurrentPageCases;
    const getNoticeApi = options.getNoticeApi;
    const getCsrf = options.getCsrf;
    const normalizeFolder = options.normalizeFolder;
    const folderTreeModelRef = options.folderTreeModelRef;

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
        for (const testCase of getCurrentPageCases()) {
            const folder = normalizeFolder(testCase?.folder || '');
            if (folder) {
                folders.add(folder);
            }
        }

        const fromTree = [];
        collectFolderPaths(folderTreeModelRef.get(), fromTree);
        for (const folder of fromTree) {
            const normalized = normalizeFolder(folder);
            if (normalized) {
                folders.add(normalized);
            }
        }

        return Array.from(folders).sort((a, b) => a.localeCompare(b));
    }

    async function moveWorkKeys(input) {
        const moveInput = input || {};
        const uniqueKeys = Array.from(new Set((moveInput.workKeys || []).filter(Boolean)));
        const targetFolder = normalizeFolder(moveInput.targetFolder || '');
        if (!targetFolder || uniqueKeys.length === 0) {
            return false;
        }

        const noticeApi = getNoticeApi();
        let payload;
        try {
            payload = await api.bulkMove({
                workKeys: uniqueKeys,
                targetFolder,
                csrf: getCsrf()
            });
        } catch (error) {
            noticeApi.showNotice('error', 'Move failed. Please check your connection and try again.');
            return false;
        }

        if (!payload.ok) {
            const message = payload.status === 401 ? 'You must be logged in to move test cases.'
                : payload.status === 403 ? 'You do not have permission to move these test cases.'
                : 'Move failed. Please try again.';
            noticeApi.showNotice('error', message);
            return false;
        }

        const result = payload.result || {};
        if (result.movedCount === 0) {
            noticeApi.showNotice('error', 'No test cases were moved.');
            return false;
        }

        const failedKeys = new Set((result.failures || []).map((failure) => failure.workKey));
        const movedKeys = new Set(uniqueKeys.filter((workKey) => !failedKeys.has(workKey)));
        selection.removeSelectedIds(Array.from(movedKeys));

        await Promise.all([
            loadFolders(),
            reloadCurrentPage()
        ]);

        if (result.movedCount < uniqueKeys.length) {
            noticeApi.showNotice('error',
                `${result.movedCount} of ${uniqueKeys.length} test cases moved. Some could not be moved.`);
        }

        return true;
    }

    return {
        listKnownFolders,
        moveWorkKeys
    };
}
