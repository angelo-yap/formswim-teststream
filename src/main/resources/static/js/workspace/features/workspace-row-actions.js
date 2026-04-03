function isInteractiveTarget(target) {
    return Boolean(target?.closest?.('.ws-interactive, a, button, input, select, textarea, label'));
}

function clearBrowserTextSelection() {
    if (typeof window === 'undefined' || typeof window.getSelection !== 'function') {
        return;
    }

    const selection = window.getSelection();
    if (selection && typeof selection.removeAllRanges === 'function') {
        selection.removeAllRanges();
    }
}

function isModifierToggle(event) {
    return Boolean(event?.ctrlKey || event?.metaKey);
}

function syncCheckboxElement(selection, checkbox) {
    if (!checkbox) {
        return;
    }

    const workKey = checkbox.dataset.workKey || '';
    checkbox.checked = selection.isSelected(workKey);
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

            const checkbox = row.querySelector('.ws-row-check');
            if (checkbox) {
                checkbox.checked = isSelected;
            }
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

    function applyRowSelection(workKey, event) {
        if (!workKey) {
            return;
        }

        const hasShift = Boolean(event?.shiftKey);
        const hasModifier = isModifierToggle(event);
        const anchor = selection.getSelectionAnchor();
        const isAlreadySelected = selection.isSelected(workKey);
        const selectedCount = selection.getSelectedIds().length;

        if (hasShift && anchor) {
            selection.selectRange(anchor, workKey);
            syncRowSelectionUi();
            return;
        }

        if (hasShift) {
            selection.selectOnly(workKey);
            syncRowSelectionUi();
            return;
        }

        if (hasModifier) {
            selection.toggleSingle(workKey);
            syncRowSelectionUi();
            return;
        }

        if (isAlreadySelected && selectedCount === 1) {
            selection.clearSelection();
            syncRowSelectionUi();
            return;
        }

        selection.selectOnly(workKey);
        syncRowSelectionUi();
    }

    if (tbody) {
        tbody.addEventListener('mousedown', (event) => {
            if (event.button !== 0) {
                return;
            }

            const row = event.target?.closest?.('tr[data-work-key]');
            if (!row) {
                return;
            }

            const isCheckbox = Boolean(event.target?.closest?.('.ws-row-check'));
            if (isCheckbox) {
                return;
            }

            if (isInteractiveTarget(event.target)) {
                return;
            }

            event.preventDefault();
            clearBrowserTextSelection();
        });

        tbody.addEventListener('change', (event) => {
            const checkbox = event.target?.closest?.('.ws-row-check');
            if (!checkbox) {
                return;
            }

            event.preventDefault();
            event.stopPropagation();
            syncCheckboxElement(selection, checkbox);
            window.requestAnimationFrame(() => {
                syncCheckboxElement(selection, checkbox);
                syncRowSelectionUi();
            });
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

            const checkbox = event.target?.closest?.('.ws-row-check');
            if (checkbox) {
                event.preventDefault();
                event.stopPropagation();
                clearBrowserTextSelection();
                applyRowSelection(checkbox.dataset.workKey || '', event);
                syncCheckboxElement(selection, checkbox);
                window.requestAnimationFrame(() => {
                    syncCheckboxElement(selection, checkbox);
                    syncRowSelectionUi();
                });
                return;
            }

            if (isInteractiveTarget(event.target)) {
                return;
            }

            const row = event.target?.closest?.('tr[data-work-key]');
            if (!row) {
                return;
            }

            clearBrowserTextSelection();
            applyRowSelection(row.dataset.workKey || '', event);
        });
    }

    return {
        syncRowSelectionUi
    };
}