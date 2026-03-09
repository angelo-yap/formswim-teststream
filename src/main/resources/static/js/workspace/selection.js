export function createSelection(selectAll, bulkBar, bulkCount) {
    const selectedIds = new Set();
    let visibleIds = [];
    let onSelectionChange = null;

    function setSelectionChangeHandler(handler) {
        onSelectionChange = handler;
    }

    function setVisibleIds(ids) {
        visibleIds = Array.isArray(ids) ? ids.slice() : [];
        syncMasterCheckbox();
        updateBulkBar();
    }

    function toggleSelection(workKey, isSelected) {
        if (!workKey) {
            return;
        }

        if (isSelected) {
            selectedIds.add(workKey);
        } else {
            selectedIds.delete(workKey);
        }

        syncMasterCheckbox();
        updateBulkBar();
    }

    function bindRowCheckboxes(root) {
        if (!root) {
            return;
        }

        root.querySelectorAll('.ws-row-check').forEach((checkbox) => {
            const workKey = checkbox.dataset.workKey || '';
            checkbox.checked = selectedIds.has(workKey);
        });
    }

    function getSelectedIds() {
        return Array.from(selectedIds);
    }

    function updateBulkBar() {
        if (bulkCount) {
            bulkCount.textContent = String(selectedIds.size);
        }
        if (bulkBar) {
            bulkBar.classList.toggle('hidden', selectedIds.size === 0);
        }
        if (typeof onSelectionChange === 'function') {
            onSelectionChange(getSelectedIds());
        }
    }

    function syncMasterCheckbox() {
        if (!selectAll) {
            return;
        }

        const visibleSelectedCount = visibleIds.filter((workKey) => selectedIds.has(workKey)).length;
        if (visibleIds.length === 0 || visibleSelectedCount === 0) {
            selectAll.checked = false;
            selectAll.indeterminate = false;
            return;
        }

        if (visibleSelectedCount === visibleIds.length) {
            selectAll.checked = true;
            selectAll.indeterminate = false;
            return;
        }

        selectAll.checked = false;
        selectAll.indeterminate = true;
    }

    if (selectAll) {
        selectAll.addEventListener('change', () => {
            for (const workKey of visibleIds) {
                if (selectAll.checked) {
                    selectedIds.add(workKey);
                } else {
                    selectedIds.delete(workKey);
                }
            }
            bindRowCheckboxes(document);
            syncMasterCheckbox();
            updateBulkBar();
        });
    }

    return {
        bindRowCheckboxes,
        getSelectedIds,
        setSelectionChangeHandler,
        setVisibleIds,
        toggleSelection
    };
}