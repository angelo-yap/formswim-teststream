function escapeHtml(value) {
    return String(value == null ? '' : value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function textOrDash(value) {
    const raw = String(value == null ? '' : value).trim();
    return raw ? escapeHtml(raw) : '-';
}

function parseDelimited(value) {
    if (!value) {
        return [];
    }

    return String(value)
        .split(/[,;|]/)
        .map((item) => item.trim())
        .filter(Boolean);
}

function buildPreviewId(workKey) {
    const raw = String(workKey || '').trim() || 'unknown';
    return 'ws-preview-' + raw.replace(/[^a-zA-Z0-9_-]/g, '-');
}

function renderMetaItem(label, value) {
    return '' +
        '<div class="border border-white/10 bg-black/30 px-3 py-2">' +
            '<p class="text-[11px] uppercase tracking-wider text-white/45">' + escapeHtml(label) + '</p>' +
            '<p class="mt-1 text-xs text-white/80 break-words">' + textOrDash(value) + '</p>' +
        '</div>';
}

function renderTagBadges(testCase) {
    const values = [];
    values.push(...parseDelimited(testCase?.components));
    values.push(...parseDelimited(testCase?.testCaseType));
    values.push(...parseDelimited(testCase?.labels));

    const seen = new Set();
    const tags = [];
    for (const value of values) {
        const key = value.toLowerCase();
        if (!seen.has(key)) {
            seen.add(key);
            tags.push(value);
        }
    }

    if (tags.length === 0) {
        return '<span class="text-xs text-white/50">No tags</span>';
    }

    return tags
        .map((tag) => '<span class="inline-flex items-center px-2 py-1 border border-white/15 text-[11px] text-white/70">' + escapeHtml(tag) + '</span>')
        .join('');
}

function renderSteps(steps) {
    const safeSteps = Array.isArray(steps) ? steps : [];
    if (safeSteps.length === 0) {
        return {
            html: '<p class="text-xs text-white/50">No steps available.</p>',
            hiddenCount: 0
        };
    }

    const initiallyVisibleCount = 3;
    let html = '<ol class="space-y-3">';
    for (let index = 0; index < safeSteps.length; index++) {
        const step = safeSteps[index] || {};
        const isExtra = index >= initiallyVisibleCount;
        html += '' +
            '<li class="border border-white/10 bg-black/25 p-3' + (isExtra ? ' hidden ws-preview-step-extra' : '') + '">' +
                '<div class="flex items-center justify-between gap-3">' +
                    '<p class="text-[11px] uppercase tracking-wider text-white/45">Step ' + escapeHtml(step.stepNumber || String(index + 1)) + '</p>' +
                '</div>' +
                '<div class="mt-2 space-y-2 text-xs">' +
                    '<div>' +
                        '<p class="text-white/50">Action</p>' +
                        '<p class="text-white/85 break-words">' + textOrDash(step.stepSummary) + '</p>' +
                    '</div>' +
                    '<div>' +
                        '<p class="text-white/50">Data</p>' +
                        '<p class="text-white/85 break-words">' + textOrDash(step.testData) + '</p>' +
                    '</div>' +
                    '<div>' +
                        '<p class="text-white/50">Expected</p>' +
                        '<p class="text-white/85 break-words">' + textOrDash(step.expectedResult) + '</p>' +
                    '</div>' +
                '</div>' +
            '</li>';
    }
    html += '</ol>';

    return {
        html,
        hiddenCount: Math.max(safeSteps.length - initiallyVisibleCount, 0)
    };
}

export function renderInlinePreviewRow(testCase, options) {
    const viewOptions = options || {};
    const workKey = String(testCase?.workKey || '').trim();
    const previewId = buildPreviewId(workKey);
    const isSelected = Boolean(viewOptions.isSelected);
    const stepsView = renderSteps(testCase?.steps);
    const hasExpandableSteps = stepsView.hiddenCount > 0;

    const stepToggleButton = hasExpandableSteps
        ? '<button type="button" class="ws-interactive mt-3 px-3 py-2 border border-white/20 hover:border-[#E7FF02] hover:text-[#E7FF02] transition-colors text-xs text-white/70" data-action="toggle-preview-steps" data-expanded="false" data-hidden-count="' + escapeHtml(String(stepsView.hiddenCount)) + '">Show all ' + escapeHtml(String(stepsView.hiddenCount)) + ' more steps</button>'
        : '';

    return '' +
        '<tr class="ws-preview-row border-b border-white/10' + (isSelected ? ' ws-preview-selected' : ' ws-preview-open') + '">' +
            '<td colspan="7" class="px-0 py-0">' +
                '<section id="' + escapeHtml(previewId) + '" class="ws-preview-card bg-white/[0.02]" data-preview-card-for="' + escapeHtml(workKey) + '">' +
                    '<div class="px-4 sm:px-6 py-4 space-y-4">' +
                        '<div>' +
                            '<div class="flex flex-wrap items-start justify-between gap-3">' +
                                '<div class="min-w-0">' +
                                    '<p class="text-[11px] uppercase tracking-wider text-white/45">Title</p>' +
                                    '<p class="mt-1 text-sm text-white/92 break-words whitespace-pre-wrap">' + textOrDash(testCase?.summary) + '</p>' +
                                    '<p class="mt-1 text-xs text-white/60 break-all">' + textOrDash(testCase?.workKey) + '</p>' +
                                '</div>' +
                                '<button type="button" class="ws-row-action ws-interactive px-3 py-2 border border-white/20 hover:border-[#E7FF02] hover:text-[#E7FF02] transition-colors text-xs text-white/75" data-action="preview" data-work-key="' + escapeHtml(workKey) + '" aria-expanded="true" aria-controls="' + escapeHtml(previewId) + '">Collapse</button>' +
                            '</div>' +
                        '</div>' +
                        '<div class="grid grid-cols-1 xl:grid-cols-2 gap-3">' +
                            renderMetaItem('Status', testCase?.status) +
                            renderMetaItem('Priority', testCase?.priority) +
                            renderMetaItem('Folder', testCase?.folder) +
                            renderMetaItem('Updated', testCase?.updatedOn) +
                            renderMetaItem('Assignee', testCase?.assignee) +
                            renderMetaItem('Reporter', testCase?.reporter) +
                        '</div>' +
                        '<div>' +
                            '<p class="text-[11px] uppercase tracking-wider text-white/45">Description</p>' +
                            '<p class="mt-1 text-xs text-white/85 leading-6 break-words">' + textOrDash(testCase?.description) + '</p>' +
                        '</div>' +
                        '<div>' +
                            '<p class="text-[11px] uppercase tracking-wider text-white/45">Precondition</p>' +
                            '<p class="mt-1 text-xs text-white/85 leading-6 break-words">' + textOrDash(testCase?.precondition) + '</p>' +
                        '</div>' +
                        '<div>' +
                            '<p class="text-[11px] uppercase tracking-wider text-white/45">Tags</p>' +
                            '<div class="mt-2 flex flex-wrap gap-2">' + renderTagBadges(testCase) + '</div>' +
                        '</div>' +
                        '<div>' +
                            '<div class="flex items-center justify-between gap-3">' +
                                '<p class="text-[11px] uppercase tracking-wider text-white/45">Steps</p>' +
                                '<span class="text-[11px] text-white/50">' + escapeHtml(String(Array.isArray(testCase?.steps) ? testCase.steps.length : 0)) + ' total</span>' +
                            '</div>' +
                            '<div class="mt-2">' + stepsView.html + stepToggleButton + '</div>' +
                        '</div>' +
                        '<div class="pt-1 border-t border-white/10 flex items-center justify-end">' +
                            '<button type="button" class="ws-row-action ws-interactive mt-3 px-3 py-2 border border-white/20 hover:border-[#E7FF02] hover:text-[#E7FF02] transition-colors text-xs text-white/75" data-action="preview" data-work-key="' + escapeHtml(workKey) + '" aria-expanded="true" aria-controls="' + escapeHtml(previewId) + '">Collapse</button>' +
                        '</div>' +
                    '</div>' +
                '</section>' +
            '</td>' +
        '</tr>';
}
