const MAX_PREVIEW_ROWS_PER_CASE = 5;

const CURATED_FIELDS = [
    { key: 'summary', label: 'Summary', description: 'Case title and headline text' },
    { key: 'description', label: 'Description', description: 'Main case description' },
    { key: 'precondition', label: 'Precondition', description: 'Required setup text' },
    { key: 'status', label: 'Status', description: 'Case workflow status' },
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

function buildHighlightedSnippet(text, token) {
    const source = String(text || '');
    const needle = String(token || '');
    if (!source || !needle) {
        return escapeHtml(source);
    }

    const matchIndex = source.indexOf(needle);
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

function buildCasePreviewRows(testCase, fieldKeys, findText) {
    const allRows = [];
    const matchingRows = [];
    const searchText = String(findText || '');

    for (const fieldKey of fieldKeys) {
        for (const row of buildFieldValues(testCase, fieldKey)) {
            allRows.push(row);
            if (searchText && row.value.indexOf(searchText) >= 0) {
                matchingRows.push(row);
            }
        }
    }

    const preferredRows = searchText && matchingRows.length > 0 ? matchingRows : allRows;
    const visibleRows = preferredRows.slice(0, MAX_PREVIEW_ROWS_PER_CASE).map((row) => ({
        label: row.label,
        valueHtml: searchText ? buildHighlightedSnippet(row.value, searchText) : escapeHtml(row.value)
    }));

    return {
        hasAnyContent: allRows.length > 0,
        hasMatch: matchingRows.length > 0,
        hiddenCount: Math.max(0, preferredRows.length - visibleRows.length),
        rows: visibleRows
    };
}

function buildResultMessage(result) {
    const updatedTargets = Number(result?.updatedCaseCount || 0) + Number(result?.updatedStepCount || 0);
    const replacements = Number(result?.totalReplacements || 0);
    const failureCount = Array.isArray(result?.failures) ? result.failures.length : 0;

    if (replacements === 0 && updatedTargets === 0) {
        if (failureCount > 0) {
            return 'No matching text was replaced. Some selected test cases could not be edited.';
        }
        return 'No matching text was found in the selected fields.';
    }

    let message = 'Applied ' + replacements + ' replacement' + (replacements === 1 ? '' : 's')
        + ' across ' + Number(result?.updatedCaseCount || 0) + ' case'
        + (Number(result?.updatedCaseCount || 0) === 1 ? '' : 's')
        + ' and ' + Number(result?.updatedStepCount || 0) + ' step'
        + (Number(result?.updatedStepCount || 0) === 1 ? '' : 's') + '.';

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
    const fieldsContainer = document.getElementById('bulkEditFields');
    const inlineError = document.getElementById('bulkEditInlineError');
    const previewInfo = document.getElementById('bulkEditPreviewInfo');
    const previewList = document.getElementById('bulkEditPreviewList');
    const selectedCount = document.getElementById('bulkEditSelectedCount');
    const isEnabled = typeof config.isEnabled === 'function' ? config.isEnabled : () => false;

    if (!isEnabled() || !modal || !panel || !applyButton || !findInput || !replaceInput || !fieldsContainer || !previewInfo || !previewList || !selectedCount) {
        return createNoopApi();
    }

    const state = {
        isOpen: false,
        isSubmitting: false,
        selectedIds: []
    };

    function renderFieldOptions() {
        fieldsContainer.innerHTML = '';
        for (const field of CURATED_FIELDS) {
            const label = document.createElement('label');
            label.className = 'flex items-start gap-3 rounded-lg border border-white/10 bg-zinc-800/70 px-3 py-3 text-sm text-white/85 hover:border-white/20 hover:bg-zinc-800';
            label.innerHTML =
                '<input type="checkbox" class="bulk-edit-field h-4 w-4 mt-0.5 accent-[#E7FF02]" value="' + escapeHtml(field.key) + '" />' +
                '<span class="min-w-0">' +
                    '<span class="block text-white/92">' + escapeHtml(field.label) + '</span>' +
                    '<span class="block text-xs text-white/50 mt-1">' + escapeHtml(field.description) + '</span>' +
                '</span>';
            fieldsContainer.appendChild(label);
        }
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

        return checked.length > 0 ? checked : CURATED_FIELDS.map((field) => field.key);
    }

    function syncSelectedCount() {
        const count = state.selectedIds.length;
        selectedCount.textContent = count + ' selected on this page';
        applyButton.disabled = state.isSubmitting || count === 0;
        applyButton.classList.toggle('opacity-60', applyButton.disabled);
        applyButton.classList.toggle('cursor-not-allowed', applyButton.disabled);
    }

    function setSubmitting(isSubmitting) {
        state.isSubmitting = Boolean(isSubmitting);
        const disabled = state.isSubmitting;
        applyButton.disabled = disabled || state.selectedIds.length === 0;
        applyButton.textContent = disabled ? 'Applying...' : 'Apply';
        findInput.disabled = disabled;
        replaceInput.disabled = disabled;
        fieldsContainer.querySelectorAll('input').forEach((input) => {
            input.disabled = disabled;
        });
        if (closeButton) {
            closeButton.disabled = disabled;
        }
        if (cancelButton) {
            cancelButton.disabled = disabled;
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

        if (selectedCases.length === 0) {
            previewInfo.textContent = 'Select test cases on the current page to preview the edit.';
            previewList.innerHTML = '<p class="text-xs text-white/45">No current-page test cases selected.</p>';
            return;
        }

        previewInfo.textContent = findText
            ? 'Showing ' + selectedCases.length + ' selected case' + (selectedCases.length === 1 ? '' : 's') + ' on this page. Matching text is highlighted when found.'
            : 'Showing ' + selectedCases.length + ' selected case' + (selectedCases.length === 1 ? '' : 's') + ' on this page. Enter find text to highlight exact matches.';

        previewList.innerHTML = selectedCases.map((testCase) => {
            const preview = buildCasePreviewRows(testCase, fieldKeys, findText);
            let stateHtml = '';

            if (!preview.hasAnyContent) {
                stateHtml = '<p class="mt-3 text-xs text-white/45">No content found in the targeted fields for this case.</p>';
            } else if (findText && !preview.hasMatch) {
                stateHtml = '<p class="mt-3 text-xs text-white/50">No exact matches in the selected fields for this case.</p>';
            }

            const rowsHtml = preview.rows.map((row) =>
                '<div class="mt-2 rounded-lg border border-white/10 bg-zinc-800/70 px-3 py-2.5">'
                    + '<p class="text-[11px] uppercase tracking-[0.12em] text-white/50">' + escapeHtml(row.label) + '</p>'
                    + '<p class="mt-1 text-sm text-white/85 leading-6">' + row.valueHtml + '</p>'
                + '</div>'
            ).join('');

            const hiddenHtml = preview.hiddenCount > 0
                ? '<p class="mt-3 text-xs text-white/45">+' + preview.hiddenCount + ' more targeted value' + (preview.hiddenCount === 1 ? '' : 's') + ' hidden for brevity.</p>'
                : '';

            return '<article class="rounded-xl border border-white/10 bg-zinc-900/75 px-4 py-3.5">'
                + '<div class="flex items-start justify-between gap-3">'
                    + '<div class="min-w-0">'
                        + '<p class="text-xs text-[#E7FF02] font-bold">' + escapeHtml(String(testCase.workKey || '-')) + '</p>'
                        + '<h4 class="mt-1 text-sm text-white/92 truncate">' + escapeHtml(String(testCase.summary || 'Untitled test case')) + '</h4>'
                    + '</div>'
                + '</div>'
                + stateHtml
                + rowsHtml
                + hiddenHtml
            + '</article>';
        }).join('');
    }

    function open() {
        state.selectedIds = getSelectedIds();
        syncSelectedCount();

        if (state.selectedIds.length === 0) {
            if (typeof config.showNotice === 'function') {
                config.showNotice('error', 'Select at least one test case on this page before using bulk edit.');
            }
            return;
        }

        setInlineError('');
        modal.classList.remove('hidden');
        modal.setAttribute('aria-hidden', 'false');
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
        }, 160);
    }

    function buildPayload() {
        const workKeys = state.selectedIds.slice();
        const findText = String(findInput.value || '').trim();
        const replaceText = String(replaceInput.value || '');
        const fields = getFieldSelection();

        if (workKeys.length === 0) {
            setInlineError('Select at least one test case on this page before applying a bulk edit.');
            return null;
        }

        if (!findText) {
            setInlineError('Find text is required.');
            return null;
        }

        setInlineError('');
        return {
            workKeys,
            findText,
            replaceText,
            fields
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
            const message = buildResultMessage(result);
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
            syncSelectedCount();
            if (state.isOpen) {
                renderPreview();
            }
        }
    }

    function onSelectionChanged(selectedIds) {
        state.selectedIds = Array.from(new Set((Array.isArray(selectedIds) ? selectedIds : []).filter(Boolean)));
        syncSelectedCount();
        if (state.isOpen) {
            renderPreview();
        }
    }

    renderFieldOptions();
    syncSelectedCount();

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
            renderPreview();
        }
    });
    replaceInput.addEventListener('input', () => {
        if (state.isOpen) {
            renderPreview();
        }
    });
    fieldsContainer.addEventListener('change', () => {
        if (state.isOpen) {
            renderPreview();
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
