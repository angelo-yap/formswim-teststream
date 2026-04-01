export function bindWorkspacePreviewControls(options) {
    const uiState = options.uiState;
    const collapseAllButton = options.collapseAllButton;
    const rerenderCurrentPage = typeof options.rerenderCurrentPage === 'function'
        ? options.rerenderCurrentPage
        : () => {};

    function refresh() {
        if (!collapseAllButton) {
            return;
        }

        const expandedCount = uiState.getExpandedPreviewKeys().size;
        collapseAllButton.classList.toggle('hidden', expandedCount === 0);
        collapseAllButton.disabled = expandedCount === 0;
        collapseAllButton.textContent = expandedCount > 0
            ? 'Collapse all previews (' + expandedCount + ')'
            : 'Collapse all previews';
    }

    if (collapseAllButton) {
        collapseAllButton.addEventListener('click', () => {
            uiState.clearExpandedPreviews();
            rerenderCurrentPage();
        });
    }

    refresh();

    return {
        refresh
    };
}
