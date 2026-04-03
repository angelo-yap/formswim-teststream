export function createSelection(selectAll, bulkBar, bulkCount) {
    const selectedIds = new Set();
    let visibleIds = [];
    let selectionAnchor = null;
    let onSelectionChange = null;

    function setSelectionChangeHandler(handler) {
        onSelectionChange = handler;
    }

    function setVisibleIds(ids) {
        visibleIds = Array.isArray(ids) ? ids.slice() : [];

        if (selectionAnchor && !visibleIds.includes(selectionAnchor)) {
            selectionAnchor = null;
        }

        syncMasterCheckbox();
        updateBulkBar();
    }

    function getFirstVisibleId() {
        return visibleIds.length > 0 ? visibleIds[0] : null;
    }

    function setSelectionAnchor(workKey) {
        selectionAnchor = workKey ? String(workKey) : null;
    }

    function getSelectionAnchor() {
        return selectionAnchor;
    }

    function updateSelectionState() {
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

        updateSelectionState();
    }

    function toggleSingle(workKey) {
        if (!workKey) {
            return;
        }

        if (selectedIds.has(workKey)) {
            selectedIds.delete(workKey);
        } else {
            selectedIds.add(workKey);
        }

        setSelectionAnchor(workKey);
        updateSelectionState();
    }

    function selectOnly(workKey) {
        if (!workKey) {
            clearSelection();
            return;
        }

        selectedIds.clear();
        selectedIds.add(workKey);
        setSelectionAnchor(workKey);
        updateSelectionState();
    }

    function clearSelection() {
        selectedIds.clear();
        selectionAnchor = null;
        updateSelectionState();
    }

    function isSelected(workKey) {
        if (!workKey) {
            return false;
        }

        return selectedIds.has(workKey);
    }

    function setSelected(workKey, shouldBeSelected) {
        toggleSelection(workKey, Boolean(shouldBeSelected));
    }

    function selectRange(anchorWorkKey, targetWorkKey) {
        const anchor = anchorWorkKey ? String(anchorWorkKey) : '';
        const target = targetWorkKey ? String(targetWorkKey) : '';
        if (!anchor || !target) {
            return;
        }

        const anchorIndex = visibleIds.indexOf(anchor);
        const targetIndex = visibleIds.indexOf(target);
        if (anchorIndex === -1 || targetIndex === -1) {
            selectOnly(target);
            return;
        }

        const start = Math.min(anchorIndex, targetIndex);
        const end = Math.max(anchorIndex, targetIndex);

        selectedIds.clear();
        for (let index = start; index <= end; index += 1) {
            const visibleId = visibleIds[index];
            if (visibleId) {
                selectedIds.add(visibleId);
            }
        }

        setSelectionAnchor(anchor);
        updateSelectionState();
    }

    function removeSelectedIds(workKeys) {
        if (!Array.isArray(workKeys) || workKeys.length === 0) {
            return;
        }

        let changed = false;
        for (const workKey of workKeys) {
            if (!workKey) {
                continue;
            }
            if (selectedIds.delete(workKey)) {
                changed = true;
            }
        }

        if (selectionAnchor && !selectedIds.has(selectionAnchor) && !visibleIds.includes(selectionAnchor)) {
            selectionAnchor = null;
        }

        if (!changed) {
            return;
        }

        updateSelectionState();
    }

    function retainSelectedIds(workKeys) {
        const allowed = new Set((Array.isArray(workKeys) ? workKeys : []).filter(Boolean));
        let changed = false;

        for (const workKey of Array.from(selectedIds)) {
            if (allowed.has(workKey)) {
                continue;
            }
            selectedIds.delete(workKey);
            changed = true;
        }

        if (selectionAnchor && !allowed.has(selectionAnchor)) {
            selectionAnchor = null;
        }

        if (!changed) {
            updateSelectionState();
            return;
        }

        updateSelectionState();
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
            updateSelectionState();
        });
    }

    return {
        bindRowCheckboxes,
        clearSelection,
        getFirstVisibleId,
        getSelectedIds,
        getSelectionAnchor,
        isSelected,
        removeSelectedIds,
        retainSelectedIds,
        selectOnly,
        selectRange,
        setSelected,
        setSelectionAnchor,
        setSelectionChangeHandler,
        setVisibleIds,
        toggleSelection,
        toggleSingle
    };
}