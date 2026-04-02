const MAX_PREVIEW_ROWS_PER_CASE = 5;

const CURATED_FIELDS = [
    { key: 'summary', label: 'Summary', description: 'Case title and headline text' },
    { key: 'description', label: 'Description', description: 'Main case description' },
    { key: 'precondition', label: 'Precondition', description: 'Required setup text' },
    { key: 'priority', label: 'Priority', description: 'Case priority value' },
    { key: 'folder', label: 'Folder', description: 'Repository folder path' },
    { key: 'labels', label: 'Labels', description: 'Case labels and tags' },
    { key: 'components', label: 'Components', description: 'Component tags' },
    { key: 'sprint', label: 'Sprint', description: 'Sprint assignment' },
    { key: 'fixVersions', label: 'Fix versions', description: 'Fix version text' },
    { key: 'version', label: 'Version', description: 'Case version text' },
    { key: 'testCaseType', label: 'Test case type', description: 'Case type metadata' },
    { key: 'storyLinkages', label: 'Story linkages', description: 'Linked story references' },
    { key: 'stepSummary', label: 'Step action', description: 'Step action text' },
    { key: 'testData', label: 'Step data', description: 'Step test data values' },
    { key: 'expectedResult', label: 'Expected result', description: 'Step expected outcomes' }
];

const FIELD_LABELS = new Map(CURATED_FIELDS.map((field) => [field.key, field.label]));

function escapeHtml(value) {
    return String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

function findMatchIndex(source, needle, caseSensitive) {
    const haystack = String(source || '');
    const search = String(needle || '');
    if (!haystack || !search) {
        return -1;
    }

    if (caseSensitive) {
        return haystack.indexOf(search);
    }

    return haystack.toLowerCase().indexOf(search.toLowerCase());
}

function findMatchSpans(source, needle, caseSensitive) {
    const haystack = String(source || '');
    const search = String(needle || '');
    if (!haystack || !search) {
        return [];
    }

    const spans = [];
    const loweredHaystack = caseSensitive ? '' : haystack.toLowerCase();
    const loweredNeedle = caseSensitive ? '' : search.toLowerCase();
    let searchIndex = 0;

    while (searchIndex < haystack.length) {
        const matchIndex = caseSensitive
            ? haystack.indexOf(search, searchIndex)
            : loweredHaystack.indexOf(loweredNeedle, searchIndex);
        if (matchIndex < 0) {
            break;
        }

        spans.push({ start: matchIndex, end: matchIndex + search.length });
        searchIndex = matchIndex + Math.max(search.length, 1);
    }

    return spans;
}

function buildHighlightedSnippet(text, token, caseSensitive) {
    const source = String(text || '');
    const needle = String(token || '');
    if (!source || !needle) {
        return escapeHtml(source);
    }

    const matchIndex = findMatchIndex(source, needle, caseSensitive);
    if (matchIndex < 0) {
        return escapeHtml(source);
    }

    const contextRadius = 36;
    const start = Math.max(0, matchIndex - contextRadius);
    const end = Math.min(source.length, matchIndex + needle.length + contextRadius);
    const prefix = start > 0 ? '...' : '';
    const suffix = end < source.length ? '...' : '';
    const before = source.slice(start, matchIndex);
    const match = source.slice(matchIndex, matchIndex + needle.length);
    const after = source.slice(matchIndex + needle.length, end);

    return prefix
        + escapeHtml(before)
        + '<mark class="bg-[#E7FF02] text-black px-0.5">' + escapeHtml(match) + '</mark>'
        + escapeHtml(after)
        + suffix;
}

function buildHighlightedHtml(text, token, caseSensitive) {
    const source = String(text || '');
    const needle = String(token || '');
    if (!source) {
        return '';
    }
    if (!needle) {
        return escapeHtml(source);
    }

    const spans = findMatchSpans(source, needle, caseSensitive);
    if (spans.length === 0) {
        return escapeHtml(source);
    }

    let html = '';
    let searchIndex = 0;

    for (const span of spans) {
        html += escapeHtml(source.slice(searchIndex, span.start));
        html += '<mark class="bg-[#E7FF02] text-black px-0.5">' + escapeHtml(source.slice(span.start, span.end)) + '</mark>';
        searchIndex = span.end;
    }

    html += escapeHtml(source.slice(searchIndex));
    return html;
}

function buildFieldValues(testCase, fieldKey) {
    if (!testCase) {
        return [];
    }

    if (fieldKey === 'stepSummary' || fieldKey === 'testData' || fieldKey === 'expectedResult') {
        const steps = Array.isArray(testCase.steps) ? testCase.steps : [];
        const stepLabel = FIELD_LABELS.get(fieldKey) || fieldKey;
        return steps
            .filter((step) => String(step?.[fieldKey] || '').trim() !== '')
            .map((step) => ({
                label: 'Step ' + String(step?.stepNumber ?? '-') + ' - ' + stepLabel,
                value: String(step?.[fieldKey] || '')
            }));
    }

    const rawValue = String(testCase[fieldKey] || '');
    if (!rawValue.trim()) {
        return [];
    }

    return [{
        label: FIELD_LABELS.get(fieldKey) || fieldKey,
        value: rawValue
    }];
}

function buildCasePreviewRows(testCase, fieldKeys, findText, isExpanded, caseSensitive) {
    const allRows = [];
    const matchingRows = [];
    const searchText = String(findText || '');

    for (const fieldKey of fieldKeys) {
        for (const row of buildFieldValues(testCase, fieldKey)) {
            allRows.push(row);
            if (searchText && findMatchIndex(row.value, searchText, caseSensitive) >= 0) {
                matchingRows.push(row);
            }
        }
    }

    const compactRows = searchText && matchingRows.length > 0 ? matchingRows : allRows;
    const sourceRows = isExpanded ? allRows : compactRows.slice(0, MAX_PREVIEW_ROWS_PER_CASE);
    const visibleRows = sourceRows.map((row) => ({
        label: row.label,
        valueHtml: isExpanded
            ? buildHighlightedHtml(row.value, searchText, caseSensitive)
            : (searchText ? buildHighlightedSnippet(row.value, searchText, caseSensitive) : escapeHtml(row.value))
    }));
    const canToggleExpanded = allRows.length > sourceRows.length
        || (searchText && matchingRows.length > 0 && allRows.length > compactRows.length)
        || (Boolean(isExpanded) && allRows.length > 0);

    return {
        hasAnyContent: allRows.length > 0,
        hasMatch: matchingRows.length > 0,
        canToggleExpanded,
        hiddenCount: isExpanded ? 0 : Math.max(0, compactRows.length - visibleRows.length),
        totalCount: allRows.length,
        rows: visibleRows
    };
}

function buildResultMessage(result, payload) {
    const updatedTargets = Number(result?.updatedCaseCount || 0) + Number(result?.updatedStepCount || 0);
    const replacements = Number(result?.totalReplacements || 0);
    const failureCount = Array.isArray(result?.failures) ? result.failures.length : 0;
    const statusValue = String(payload?.statusValue || '').trim();

    if (replacements === 0 && updatedTargets === 0) {
        if (statusValue) {
            let message = 'No selected test cases needed a status change.';
            if (failureCount > 0) {
                message += ' ' + failureCount + ' selected item' + (failureCount === 1 ? ' was' : 's were') + ' skipped.';
            }
            return message;
        }
        if (failureCount > 0) {
            return 'No matching text was replaced. Some selected test cases could not be edited.';
        }
        return 'No matching text was found in the selected fields.';
    }

    let message;
    if (replacements === 0 && statusValue) {
        message = 'Updated status on ' + Number(result?.updatedCaseCount || 0) + ' case'
            + (Number(result?.updatedCaseCount || 0) === 1 ? '' : 's')
            + ' to ' + statusValue + '.';
    } else {
        message = 'Applied ' + replacements + ' replacement' + (replacements === 1 ? '' : 's')
            + ' across ' + Number(result?.updatedCaseCount || 0) + ' case'
            + (Number(result?.updatedCaseCount || 0) === 1 ? '' : 's')
            + ' and ' + Number(result?.updatedStepCount || 0) + ' step'
            + (Number(result?.updatedStepCount || 0) === 1 ? '' : 's') + '.';

        if (statusValue) {
            message += ' Status set to ' + statusValue + '.';
        }
    }

    if (failureCount > 0) {
        message += ' ' + failureCount + ' selected item' + (failureCount === 1 ? ' was' : 's were') + ' skipped.';
    }

    return message;
}

function createNoopApi() {
    return {
        close() {},
        onSelectionChanged() {},
        open() {}
    };
}

export function createBulkEdit(options) {
    const config = options || {};
    const bulkEditButton = config.bulkEditButton || document.getElementById('bulkEditOpen');
    const modal = document.getElementById('bulkEditModal');
    const panel = document.getElementById('bulkEditPanel');
    const backdrop = document.getElementById('bulkEditBackdrop');
    const closeButton = document.getElementById('bulkEditClose');
    const cancelButton = document.getElementById('bulkEditCancel');
    const applyButton = document.getElementById('bulkEditApply');
    const findInput = document.getElementById('bulkEditFindText');
    const replaceInput = document.getElementById('bulkEditReplaceText');
    const caseSensitiveInput = document.getElementById('bulkEditCaseSensitive');
    const statusSelect = document.getElementById('bulkEditStatusValue');
    const fieldsContainer = document.getElementById('bulkEditFields');
    const toggleAllFieldsButton = document.getElementById('bulkEditToggleAllFields');
    const inlineError = document.getElementById('bulkEditInlineError');
    const previewInfo = document.getElementById('bulkEditPreviewInfo');
    const previewList = document.getElementById('bulkEditPreviewList');
    const selectedCount = document.getElementById('bulkEditSelectedCount');

    if (!modal || !panel || !applyButton || !findInput || !replaceInput || !caseSensitiveInput || !statusSelect || !fieldsContainer || !toggleAllFieldsButton || !previewInfo || !previewList || !selectedCount) {
        return createNoopApi();
    }

    const state = {
        expandedCaseIds: new Set(),
        isOpen: false,
        isSubmitting: false,
        scrollLock: null,
        selectedIds: []
    };

    function lockBackgroundScroll() {
        if (state.scrollLock) {
            return;
        }

        state.scrollLock = {
            bodyOverflow: document.body.style.overflow,
            docOverflow: document.documentElement.style.overflow
        };
        document.documentElement.style.overflow = 'hidden';
        document.body.style.overflow = 'hidden';
    }

    function unlockBackgroundScroll() {
        if (!state.scrollLock) {
            return;
        }

        document.documentElement.style.overflow = state.scrollLock.docOverflow || '';
        document.body.style.overflow = state.scrollLock.bodyOverflow || '';
        state.scrollLock = null;
    }

    function renderFieldOptions() {
        fieldsContainer.innerHTML = '';
        for (const field of CURATED_FIELDS) {
            const label = document.createElement('label');
            label.className = 'flex items-start gap-3 rounded-lg border border-white/10 bg-zinc-800/70 px-3 py-3 text-sm text-white/85 hover:border-white/20 hover:bg-zinc-800';
            label.innerHTML =
                '<input type="checkbox" class="bulk-edit-field h-4 w-4 mt-0.5 accent-[#E7FF02]" value="' + escapeHtml(field.key) + '" checked />' +
                '<span class="min-w-0">' +
                    '<span class="block text-white/92">' + escapeHtml(field.label) + '</span>' +
                    '<span class="block text-xs text-white/50 mt-1">' + escapeHtml(field.description) + '</span>' +
                '</span>';
            fieldsContainer.appendChild(label);
        }
        syncFieldToggleLabel();
    }

    function setInlineError(message) {
        if (!inlineError) {
            return;
        }

        const text = String(message || '').trim();
        if (!text) {
            inlineError.textContent = '';
            inlineError.classList.add('hidden');
            return;
        }

        inlineError.textContent = text;
        inlineError.classList.remove('hidden');
    }

    function getSelectedIds() {
        const values = typeof config.getSelectedIds === 'function' ? config.getSelectedIds() : [];
        return Array.from(new Set((Array.isArray(values) ? values : []).filter(Boolean)));
    }

    function getFieldSelection() {
        const checked = Array.from(fieldsContainer.querySelectorAll('.bulk-edit-field:checked'))
            .map((input) => String(input.value || '').trim())
            .filter(Boolean);

        return checked;
    }

    function hasTextOperation() {
        return String(findInput.value || '').trim().length > 0;
    }

    function hasStatusOperation() {
        return String(statusSelect.value || '').trim().length > 0;
    }

    function canSubmitCurrentState() {
        if (state.selectedIds.length === 0 || state.isSubmitting) {
            return false;
        }

        const textRequested = hasTextOperation();
        const statusRequested = hasStatusOperation();
        if (!textRequested && !statusRequested) {
            return false;
        }

        if (textRequested) {
            return getFieldSelection().length > 0;
        }

        return statusRequested;
    }

    function syncFieldToggleLabel() {
        const allChecked = Array.from(fieldsContainer.querySelectorAll('.bulk-edit-field')).every((input) => input.checked);
        toggleAllFieldsButton.textContent = allChecked ? 'Deselect all' : 'Select all';
    }

    function syncActionState() {
        const count = state.selectedIds.length;
        selectedCount.textContent = count + ' selected on this page';
        applyButton.disabled = !canSubmitCurrentState();
        applyButton.classList.toggle('opacity-60', applyButton.disabled);
        applyButton.classList.toggle('cursor-not-allowed', applyButton.disabled);
        syncFieldToggleLabel();
    }

    function setAllFieldsChecked(checked) {
        fieldsContainer.querySelectorAll('.bulk-edit-field').forEach((input) => {
            input.checked = Boolean(checked);
        });
        syncFieldToggleLabel();
    }

    function setSubmitting(isSubmitting) {
        state.isSubmitting = Boolean(isSubmitting);
        const disabled = state.isSubmitting;
        applyButton.disabled = disabled || !canSubmitCurrentState();
        applyButton.textContent = disabled ? 'Applying...' : 'Apply';
        findInput.disabled = disabled;
        replaceInput.disabled = disabled;
        caseSensitiveInput.disabled = disabled;
        statusSelect.disabled = disabled;
        fieldsContainer.querySelectorAll('input').forEach((input) => {
            input.disabled = disabled;
        });
        toggleAllFieldsButton.disabled = disabled;
        if (closeButton) {
            closeButton.disabled = disabled;
        }
        if (cancelButton) {
            cancelButton.disabled = disabled;
        }
    }

    function toggleExpandedCase(workKey) {
        if (!workKey) {
            return;
        }

        if (state.expandedCaseIds.has(workKey)) {
            state.expandedCaseIds.delete(workKey);
        } else {
            state.expandedCaseIds.add(workKey);
        }
    }

    function renderPreview() {
        const pageCases = typeof config.getCurrentPageCases === 'function' ? config.getCurrentPageCases() : [];
        const cases = Array.isArray(pageCases) ? pageCases : [];
        const selectedMap = new Map(cases.map((testCase) => [String(testCase?.workKey || ''), testCase]));
        const selectedCases = state.selectedIds
            .map((workKey) => selectedMap.get(workKey))
            .filter(Boolean);
        const findText = String(findInput.value || '').trim();
        const fieldKeys = getFieldSelection();
        const caseSensitive = Boolean(caseSensitiveInput.checked);
        const statusValue = String(statusSelect.value || '').trim();

        if (selectedCases.length === 0) {
            previewInfo.textContent = 'Select test cases on the current page to preview the edit.';
            previewList.innerHTML = '<p class="text-xs text-white/45">No current-page test cases selected.</p>';
            return;
        }

        if (findText) {
            previewInfo.textContent = 'Showing ' + selectedCases.length + ' selected case' + (selectedCases.length === 1 ? '' : 's')
                + ' on this page. Matching text is highlighted with ' + (caseSensitive ? 'exact-case' : 'case-insensitive') + ' search.';
        } else if (statusValue) {
            previewInfo.textContent = 'Showing ' + selectedCases.length + ' selected case' + (selectedCases.length === 1 ? '' : 's')
                + ' on this page. Previewing a status update to ' + statusValue + '.';
        } else {
            previewInfo.textContent = 'Showing ' + selectedCases.length + ' selected case' + (selectedCases.length === 1 ? '' : 's') + ' on this page. Enter find text or choose a status to preview changes.';
        }

        previewList.innerHTML = selectedCases.map((testCase) => {
            const workKey = String(testCase.workKey || '');
            const isExpanded = state.expandedCaseIds.has(workKey);
            const preview = buildCasePreviewRows(testCase, fieldKeys, findText, isExpanded, caseSensitive);
            let stateHtml = '';

            if (!preview.hasAnyContent) {
                stateHtml = '<p class="mt-3 text-xs text-white/45">No content found in the targeted fields for this case.</p>';
            } else if (findText && !preview.hasMatch) {
                stateHtml = '<p class="mt-3 text-xs text-white/50">No matches in the selected fields for this case.</p>';
            }

            const currentStatus = String(testCase.status || '').trim();
            const statusIntentHtml = statusValue
                ? '<p class="mt-3 text-xs ' + (currentStatus === statusValue ? 'text-amber-200' : 'text-[#E7FF02]') + '">'
                    + (currentStatus === statusValue
                        ? 'Status will remain ' + escapeHtml(statusValue) + '.'
                        : 'Status will change from ' + escapeHtml(currentStatus || 'unset') + ' to ' + escapeHtml(statusValue) + '.')
                + '</p>'
                : '';

            const rowsHtml = preview.rows.map((row) =>
                '<div class="mt-2 rounded-lg border border-zinc-700/80 bg-zinc-800/85 px-3 py-3">'
                    + '<p class="text-[11px] uppercase tracking-[0.12em] text-white/50">' + escapeHtml(row.label) + '</p>'
                    + '<p class="mt-1 text-sm text-white/85 leading-6 whitespace-pre-wrap break-words">' + row.valueHtml + '</p>'
                + '</div>'
            ).join('');

            const hiddenCount = Math.max(preview.totalCount - preview.rows.length, 0);
            const hiddenHtml = !isExpanded && hiddenCount > 0
                ? '<p class="mt-3 text-xs text-white/45">+' + hiddenCount + ' more targeted value' + (hiddenCount === 1 ? '' : 's') + ' available for this case.</p>'
                : '';
            const expandButtonHtml = preview.hasAnyContent && preview.canToggleExpanded
                ? '<button type="button" class="px-3 py-2 border border-white/15 hover:border-[#E7FF02] hover:text-[#E7FF02] transition-colors text-xs text-white/70" data-action="toggle-expand" data-work-key="' + escapeHtml(workKey) + '">'
                    + (isExpanded ? 'Show less' : 'Show more')
                + '</button>'
                : '';
            const previewButtonHtml = workKey
                ? '<button type="button" class="px-3 py-2 border border-white/15 hover:border-[#E7FF02] hover:text-[#E7FF02] transition-colors text-xs text-white/70" data-action="open-case-preview" data-work-key="' + escapeHtml(workKey) + '">Preview in Drawer</button>'
                : '';

            return '<article class="rounded-xl border border-zinc-700/80 bg-zinc-900/85 px-4 py-4">'
                + '<div class="flex items-start justify-between gap-3">'
                    + '<div class="min-w-0">'
                        + '<p class="text-xs text-[#E7FF02] font-bold">' + escapeHtml(workKey || '-') + '</p>'
                        + '<h4 class="mt-1 text-sm text-white/92 leading-6 break-words">' + escapeHtml(String(testCase.summary || 'Untitled test case')) + '</h4>'
                    + '</div>'
                    + '<div class="flex shrink-0 items-center gap-2">'
                        + previewButtonHtml
                        + expandButtonHtml
                    + '</div>'
                + '</div>'
                + stateHtml
                + statusIntentHtml
                + rowsHtml
                + hiddenHtml
            + '</article>';
        }).join('');
    }

    function open() {
        state.selectedIds = getSelectedIds();
        state.expandedCaseIds.clear();
        setAllFieldsChecked(true);
        caseSensitiveInput.checked = true;
        statusSelect.value = '';
        syncActionState();

        if (state.selectedIds.length === 0) {
            if (typeof config.showNotice === 'function') {
                config.showNotice('error', 'Select at least one test case on this page before using bulk edit.');
            }
            return;
        }

        setInlineError('');
        modal.classList.remove('hidden');
        modal.setAttribute('aria-hidden', 'false');
        lockBackgroundScroll();
        state.isOpen = true;
        requestAnimationFrame(() => {
            panel.classList.remove('translate-y-3', 'opacity-0');
        });
        renderPreview();
        findInput.focus();
    }

    function close(force) {
        if (!state.isOpen) {
            return;
        }
        if (state.isSubmitting && !force) {
            return;
        }

        panel.classList.add('translate-y-3', 'opacity-0');
        modal.setAttribute('aria-hidden', 'true');
        state.isOpen = false;
        window.setTimeout(() => {
            modal.classList.add('hidden');
            unlockBackgroundScroll();
        }, 160);
    }

    function buildPayload() {
        const findText = String(findInput.value || '').trim();
        const replaceText = String(replaceInput.value || '');
        const statusValue = String(statusSelect.value || '').trim();
        const fields = getFieldSelection();
        const caseSensitive = Boolean(caseSensitiveInput.checked);

        if (state.selectedIds.length === 0) {
            setInlineError('Select at least one test case on this page before applying a bulk edit.');
            return null;
        }

        if (!findText && !statusValue) {
            setInlineError('Choose a status or enter find text before applying a bulk edit.');
            return null;
        }

        if (findText && fields.length === 0) {
            setInlineError('Select at least one text field when using find and replace.');
            return null;
        }

        setInlineError('');
        return {
            workKeys: state.selectedIds.slice(),
            findText: findText,
            replaceText: findText ? replaceText : '',
            fields: findText ? fields : [],
            caseSensitive,
            statusValue
        };
    }

    async function applyBulkEdit() {
        const payload = buildPayload();
        if (!payload || state.isSubmitting) {
            return;
        }

        setSubmitting(true);
        try {
            const csrf = typeof config.getCsrf === 'function' ? (config.getCsrf() || {}) : {};
            const response = await fetch('/api/testcases/bulk-edit', {
                method: 'PATCH',
                headers: {
                    'Content-Type': 'application/json',
                    [csrf.headerName || 'X-CSRF-TOKEN']: csrf.token || ''
                },
                body: JSON.stringify(payload)
            });

            if (!response.ok) {
                let message = 'Bulk edit failed. Please try again.';
                if (response.status === 400) {
                    message = 'Bulk edit request was invalid. Check the selected fields and search text.';
                } else if (response.status === 401) {
                    message = 'You must be logged in to bulk edit test cases.';
                } else if (response.status === 403) {
                    message = 'You do not have permission to bulk edit these test cases.';
                } else if (response.status === 503) {
                    message = 'Bulk edit is currently disabled by rollout settings.';
                }

                setInlineError(message);
                if (typeof config.showNotice === 'function') {
                    config.showNotice('error', message);
                }
                return;
            }

            const result = await response.json();
            const message = buildResultMessage(result, payload);
            const didChange = Number(result?.totalReplacements || 0) > 0 || Number(result?.updatedCaseCount || 0) > 0 || Number(result?.updatedStepCount || 0) > 0;

            if (typeof config.showNotice === 'function') {
                config.showNotice(didChange ? 'success' : 'error', message);
            }

            if (didChange && typeof config.refreshCurrentPage === 'function') {
                await config.refreshCurrentPage();
            }

            if (didChange) {
                close(true);
            }
        } catch (error) {
            console.error('Bulk edit failed', error);
            const message = 'Bulk edit failed. Please check your connection and try again.';
            setInlineError(message);
            if (typeof config.showNotice === 'function') {
                config.showNotice('error', message);
            }
        } finally {
            setSubmitting(false);
            syncActionState();
            if (state.isOpen) {
                renderPreview();
            }
        }
    }

    function onSelectionChanged(selectedIds) {
        state.selectedIds = Array.from(new Set((Array.isArray(selectedIds) ? selectedIds : []).filter(Boolean)));
        const selectedSet = new Set(state.selectedIds);
        for (const workKey of Array.from(state.expandedCaseIds)) {
            if (!selectedSet.has(workKey)) {
                state.expandedCaseIds.delete(workKey);
            }
        }
        syncActionState();
        if (state.isOpen) {
            renderPreview();
        }
    }

    renderFieldOptions();
    setAllFieldsChecked(true);
    syncActionState();

    if (bulkEditButton) {
        bulkEditButton.addEventListener('click', open);
    }
    if (backdrop) {
        backdrop.addEventListener('click', close);
    }
    if (closeButton) {
        closeButton.addEventListener('click', close);
    }
    if (cancelButton) {
        cancelButton.addEventListener('click', close);
    }
    applyButton.addEventListener('click', applyBulkEdit);
    findInput.addEventListener('input', () => {
        setInlineError('');
        if (state.isOpen) {
            syncActionState();
            renderPreview();
        }
    });
    replaceInput.addEventListener('input', () => {
        if (state.isOpen) {
            syncActionState();
            renderPreview();
        }
    });
    caseSensitiveInput.addEventListener('change', () => {
        if (state.isOpen) {
            syncActionState();
            renderPreview();
        }
    });
    statusSelect.addEventListener('change', () => {
        if (state.isOpen) {
            syncActionState();
            renderPreview();
        }
    });
    fieldsContainer.addEventListener('change', () => {
        syncActionState();
        if (state.isOpen) {
            renderPreview();
        }
    });
    toggleAllFieldsButton.addEventListener('click', () => {
        const allChecked = Array.from(fieldsContainer.querySelectorAll('.bulk-edit-field')).every((input) => input.checked);
        setAllFieldsChecked(!allChecked);
        syncActionState();
        if (state.isOpen) {
            renderPreview();
        }
    });
    previewList.addEventListener('click', (event) => {
        const actionButton = event.target?.closest?.('button[data-action]');
        if (!actionButton || state.isSubmitting) {
            return;
        }

        const action = String(actionButton.dataset.action || '');
        const workKey = String(actionButton.dataset.workKey || '');
        if (action === 'toggle-expand') {
            toggleExpandedCase(workKey);
            renderPreview();
            return;
        }

        if (action === 'open-case-preview' && workKey && typeof config.openCasePreview === 'function') {
            config.openCasePreview(workKey);
        }
    });
    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape' && state.isOpen) {
            close();
        }
    });

    return {
        close,
        onSelectionChanged,
        open
    };
}
