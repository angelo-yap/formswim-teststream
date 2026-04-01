function isInteractiveTarget(target) {
    return Boolean(target?.closest?.('.ws-interactive, a, button, input, select, textarea, label'));
}

export function bindWorkspaceRowActions(options) {
    const tbody = options.tbody;
    const selection = options.selection;
    const editBasePath = options.editBasePath || '/workspace/test-cases/';
    const uiState = options.uiState;
    const rerenderCurrentPage = typeof options.rerenderCurrentPage === 'function'
        ? options.rerenderCurrentPage
        : () => {};

    function syncRowSelectionUi() {
        if (!tbody) {
            return;
        }

        const rows = tbody.querySelectorAll('tr[data-work-key]');
        rows.forEach((row) => {
            const workKey = row.dataset.workKey || '';
            const isSelected = selection.isSelected(workKey);
            row.classList.toggle('ws-row-selected', isSelected);
        });

        const previewCards = tbody.querySelectorAll('[data-preview-card-for]');
        previewCards.forEach((previewCard) => {
            const workKey = previewCard.getAttribute('data-preview-card-for') || '';
            const isSelected = selection.isSelected(workKey);
            const previewRow = previewCard.closest('tr.ws-preview-row');
            if (!previewRow) {
                return;
            }

            previewRow.classList.toggle('ws-preview-selected', isSelected);
            previewRow.classList.toggle('ws-preview-open', !isSelected);
        });
    }

    if (tbody) {
        tbody.addEventListener('change', (event) => {
            const target = event.target;
            if (!target || !target.classList || !target.classList.contains('ws-row-check')) {
                return;
            }

            selection.toggleSelection(target.dataset.workKey || '', target.checked);
            syncRowSelectionUi();
        });

        tbody.addEventListener('click', (event) => {
            const actionButton = event.target?.closest?.('.ws-row-action');
            if (actionButton) {
                const workKey = actionButton.dataset.workKey || actionButton.closest('[data-work-key]')?.dataset.workKey || '';
                if (!workKey) {
                    return;
                }

                const action = actionButton.dataset.action;
                if (action === 'preview') {
                    if (!uiState || typeof uiState.togglePreviewExpanded !== 'function') {
                        return;
                    }
                    uiState.togglePreviewExpanded(workKey);
                    rerenderCurrentPage();
                } else if (action === 'edit' && actionButton.tagName !== 'A') {
                    window.location.href = editBasePath + encodeURIComponent(workKey);
                }
                return;
            }

            const genericAction = event.target?.closest?.('[data-action]');
            if (genericAction && !genericAction.classList.contains('ws-row-action')) {
                const action = genericAction.dataset.action;
                if (action === 'toggle-preview-steps') {
                    const previewCard = genericAction.closest('[data-preview-card-for]');
                    if (!previewCard) {
                        return;
                    }

                    const isExpanded = String(genericAction.dataset.expanded || '').toLowerCase() === 'true';
                    const hiddenStepRows = previewCard.querySelectorAll('.ws-preview-step-extra');
                    hiddenStepRows.forEach((row) => {
                        row.classList.toggle('hidden', isExpanded);
                    });

                    const hiddenCount = Number.parseInt(genericAction.dataset.hiddenCount || '0', 10) || 0;
                    genericAction.dataset.expanded = isExpanded ? 'false' : 'true';
                    genericAction.textContent = isExpanded
                        ? 'Show all ' + hiddenCount + ' more steps'
                        : 'Show fewer steps';
                }
                return;
            }

            if (isInteractiveTarget(event.target)) {
                return;
            }

            const row = event.target?.closest?.('tr[data-work-key]');
            if (!row) {
                return;
            }

            const workKey = row.dataset.workKey || '';
            if (!workKey) {
                return;
            }

            const nextChecked = !selection.isSelected(workKey);
            selection.setSelected(workKey, nextChecked);

            const check = row.querySelector('.ws-row-check');
            if (check) {
                check.checked = nextChecked;
            }
            syncRowSelectionUi();
        });
    }

    return {
        syncRowSelectionUi
    };
}
