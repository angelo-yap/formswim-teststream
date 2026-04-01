function isInteractiveTarget(target) {
    return Boolean(target?.closest?.('.ws-interactive, a, button, input, select, textarea, label'));
}

export function bindWorkspaceRowActions(options) {
    const tbody = options.tbody;
    const selection = options.selection;
    const drawer = options.drawer;
    const editBasePath = options.editBasePath || '/workspace/test-cases/';

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
                    drawer.openByWorkKey(workKey, { readOnly: true });
                } else if (action === 'edit' && actionButton.tagName !== 'A') {
                    window.location.href = editBasePath + encodeURIComponent(workKey);
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
