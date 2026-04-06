import { renderInlinePreviewRow } from './components/workspace-inline-preview-row.js';
import { createWorkspaceTooltip } from './components/workspace-tooltip.js';

export function escHtml(value) {
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

function parseTagList(value) {
    if (!value) {
        return [];
    }

    if (Array.isArray(value)) {
        return value
            .map((item) => String(item || '').trim())
            .filter(Boolean);
    }

    const raw = String(value || '').trim();
    if (!raw) {
        return [];
    }

    // Support comma/semicolon/pipe separated tag strings.
    return raw
        .split(/[,;|]/)
        .map((item) => item.trim())
        .filter(Boolean);
}

function buildTagBadges(testCase, maxVisible = 3) {
    // Today, tags live in `components` (often a string). `testCaseType` is shown as an additional badge.
    // This helper keeps the future "real tags" implementation isolated.
    const list = [];
    list.push(...parseTagList(testCase?.components));
    list.push(...parseTagList(testCase?.testCaseType));

    // De-dupe (case-insensitive) while preserving the first-seen casing.
    const byLower = new Map();
    for (const tag of list) {
        const key = tag.toLowerCase();
        if (!byLower.has(key)) {
            byLower.set(key, tag);
        }
    }

    const tags = Array.from(byLower.values());
    const visible = tags.slice(0, Math.max(0, maxVisible));
    const hiddenCount = Math.max(0, tags.length - visible.length);

    let html = '';
    for (const tag of visible) {
        html += '<span class="max-w-full truncate px-2 py-1 text-xs border border-white/15 text-white/60">' + escHtml(tag) + '</span>';
    }
    if (hiddenCount > 0) {
        html += '<span class="max-w-full truncate px-2 py-1 text-xs border border-white/15 text-white/60">+' + escHtml(hiddenCount) + '</span>';
    }

    return {
        tags,
        html
    };
}

function buildCustomTagBadges(customTags) {
    if (!customTags || customTags.length === 0) {
        return '<span class="text-white/25 text-xs italic">Add tag...</span>';
    }
    let html = '';
    for (const tag of customTags) {
        const color = escHtml(tag.color || '#6b7280');
        const name = escHtml(tag.name || '');
        html += '<span class="inline-flex items-center gap-1 px-2 py-0.5 text-xs border border-white/15 text-white/80" style="border-left: 3px solid ' + color + '">' + name + '</span>';
    }
    return html;
}

function buildPreviewElementId(workKey) {
    return 'ws-preview-' + String(workKey || '').replace(/[^a-zA-Z0-9_-]/g, '-');
}

const tagTooltip = createWorkspaceTooltip({
    className: 'fixed z-50 border border-white/20 bg-black px-3 py-2 text-xs text-white/80',
    maxWidth: '22rem'
});
const titleTooltip = createWorkspaceTooltip({
    className: 'fixed z-50 border border-white/20 bg-black px-3.5 py-2.5 text-sm text-white/90',
    maxWidth: '42rem',
    whiteSpace: 'normal',
    wordBreak: 'break-word'
});

function isHorizontallyTruncated(element) {
    if (!element) {
        return false;
    }

    return element.scrollWidth > element.clientWidth + 1;
}

function showTagTooltip(anchorEl, tags) {
    if (!anchorEl || !tags || tags.length === 0) {
        return;
    }

    // Render each tag as a badge so future per-tag colors are visible here.
    let html = '<div class="flex flex-wrap gap-2">';
    for (const tag of tags) {
        html += '<span class="max-w-full truncate px-2 py-1 text-xs border border-white/15 text-white/60">' + escHtml(tag) + '</span>';
    }
    html += '</div>';

    tagTooltip.show(anchorEl, html);
}

function showTitleTooltip(anchorEl, fullTitle) {
    if (!anchorEl || !isHorizontallyTruncated(anchorEl)) {
        return;
    }

    const normalizedTitle = String(fullTitle || '').trim();
    if (!normalizedTitle) {
        return;
    }

    titleTooltip.show(anchorEl, escHtml(normalizedTitle));
}

function decodeTagsFromDataset(value) {
    if (!value) {
        return [];
    }

    try {
        const parsed = JSON.parse(decodeURIComponent(String(value)));
        return Array.isArray(parsed) ? parsed.map((item) => String(item)) : [];
    } catch (e) {
        return [];
    }
}

export function createGrid(tbody) {
    let testCaseMap = {};

    function renderRows(testCases, selectedIds, options) {
        if (!tbody) {
            return;
        }

        const viewOptions = options || {};
        const expandedPreviewKeys = viewOptions.expandedPreviewKeys instanceof Set
            ? viewOptions.expandedPreviewKeys
            : new Set();

        // If the grid rerenders while hovering/focused, ensure active tooltips are dismissed.
        tagTooltip.hide();
        titleTooltip.hide();

        tbody.innerHTML = '';
        testCaseMap = {};

        if (!testCases || testCases.length === 0) {
            const emptyRow = document.createElement('tr');
            emptyRow.className = 'border-b border-white/10';
            emptyRow.innerHTML =
                '<td class="px-6 sm:px-8 py-6" colspan="7">' +
                '<p class="text-sm text-white/70">No test cases found.</p>' +
                '<p class="text-xs text-white/45 mt-1">Import a CSV or XLSX file to get started.</p>' +
                '</td>';
            tbody.appendChild(emptyRow);
            return;
        }

        for (const testCase of testCases) {
            const workKey = testCase.workKey || '-';
            const title = testCase.summary || '-';
            const status = testCase.status || '-';
            const tagModel = buildTagBadges(testCase, 3);
            const customTags = Array.isArray(testCase.customTags) ? testCase.customTags : [];
            const updated = testCase.updatedOn || '-';
            const previewUrl = '/workspace/test-cases/' + encodeURIComponent(workKey);
            const isSelected = selectedIds.has(workKey);
            const isExpanded = expandedPreviewKeys.has(workKey);
            const previewElementId = buildPreviewElementId(workKey);

            testCaseMap[workKey] = testCase;

            const row = document.createElement('tr');
            row.className = 'ws-row border-b border-white/10 hover:bg-white/5 transition-colors cursor-pointer'
                + (isSelected ? ' ws-row-selected' : '')
                + (isExpanded ? ' ws-row-expanded' : '');
            row.dataset.id = workKey;
            row.dataset.workKey = workKey;
            row.dataset.title = title;
            row.dataset.status = status;
            row.dataset.updated = updated;

            const customTagBadgesHtml = buildCustomTagBadges(customTags);
            const titleCell =
                '<div class="min-w-0">' +
                    '<div class="font-semibold text-white/85 truncate ws-title-text" data-ws-title tabindex="0">' + escHtml(title) + '</div>' +
                    '<div class="mt-1 flex flex-wrap gap-1 ws-custom-tags" data-work-key="' + escHtml(workKey) + '" role="button" tabindex="0" aria-label="Edit tags">' +
                        customTagBadgesHtml +
                    '</div>' +
                '</div>';

            const tagsCell = tagModel.html || '<span class="text-white/45">-</span>';
            const encodedTags = tagModel.tags && tagModel.tags.length > 0
                ? encodeURIComponent(JSON.stringify(tagModel.tags))
                : '';
            const tagsTabIndex = encodedTags ? '0' : '-1';

            row.innerHTML =
                '<td class="px-2 sm:px-4 py-2.5 text-white/45">' +
                    '<button type="button" class="ws-row-grab ws-interactive h-7 w-7 inline-flex items-center justify-center border border-transparent hover:border-white/20 rounded-sm" aria-label="Drag to move" draggable="true" data-work-key="' + escHtml(workKey) + '">' +
                        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-3.5 h-3.5" aria-hidden="true">' +
                            '<rect x="5" y="3.5" width="2" height="2" rx="0.4"></rect>' +
                            '<rect x="5" y="9" width="2" height="2" rx="0.4"></rect>' +
                            '<rect x="5" y="14.5" width="2" height="2" rx="0.4"></rect>' +
                            '<rect x="13" y="3.5" width="2" height="2" rx="0.4"></rect>' +
                            '<rect x="13" y="9" width="2" height="2" rx="0.4"></rect>' +
                            '<rect x="13" y="14.5" width="2" height="2" rx="0.4"></rect>' +
                        '</svg>' +
                    '</button>' +
                '</td>' +
                '<td class="px-2 sm:px-3 py-2.5">' +
                    '<input type="checkbox" class="ws-row-check ws-interactive h-4 w-4 accent-[#E7FF02]" aria-label="Select row" data-work-key="' + escHtml(workKey) + '"' +
                    (isSelected ? ' checked' : '') + ' />' +
                '</td>' +
                '<td class="px-3 sm:px-6 py-2.5"><div class="min-w-0 truncate">' + titleCell + '</div></td>' +
                '<td class="px-3 sm:px-6 py-2.5"><span class="inline-flex items-center px-2 py-1 border border-white/15 text-xs text-white/70">' + escHtml(status) + '</span></td>' +
                '<td class="px-3 sm:px-6 py-2.5"><div class="min-w-0 flex flex-wrap gap-2" data-ws-tags="' + escHtml(encodedTags) + '" tabindex="' + tagsTabIndex + '">' + tagsCell + '</div></td>' +
                '<td class="pl-3 pr-6 sm:pl-6 sm:pr-4 py-2.5 text-white/55"><div class="min-w-0 whitespace-nowrap">' + escHtml(updated) + '</div></td>' +
                '<td class="px-2 sm:px-4 py-2.5 text-right">' +
                    '<div class="inline-flex items-center gap-2 whitespace-nowrap" data-work-key="' + escHtml(workKey) + '">' +
                        '<button type="button" class="ws-row-action ws-row-preview ws-interactive whitespace-nowrap text-center px-2.5 py-1.5 border border-white/20 hover:border-[#E7FF02] hover:text-[#E7FF02] text-xs" data-action="preview" data-work-key="' + escHtml(workKey) + '" aria-expanded="' + (isExpanded ? 'true' : 'false') + '" aria-controls="' + escHtml(previewElementId) + '">' + (isExpanded ? 'Hide Preview' : 'Preview') + '</button>' +
                        '<a class="ws-row-action ws-row-edit ws-interactive whitespace-nowrap px-2.5 py-1.5 border border-white/20 hover:border-[#E7FF02] hover:text-[#E7FF02] text-xs" data-action="edit" data-work-key="' + escHtml(workKey) + '" href="' + escHtml(previewUrl) + '">Edit</a>' +
                    '</div>' +
                '</td>';

            const tagsEl = row.querySelector('[data-ws-tags]');
            if (tagsEl) {
                const tags = decodeTagsFromDataset(tagsEl.getAttribute('data-ws-tags'));
                if (tags.length > 0) {
                    tagsEl.addEventListener('mouseenter', () => showTagTooltip(tagsEl, tags));
                    tagsEl.addEventListener('mouseleave', () => {
                        tagTooltip.hide();
                    });
                    tagsEl.addEventListener('focusin', () => showTagTooltip(tagsEl, tags));
                    tagsEl.addEventListener('focusout', () => {
                        tagTooltip.hide();
                    });
                }
            }

            const titleEl = row.querySelector('[data-ws-title]');
            if (titleEl) {
                titleEl.addEventListener('mouseenter', () => showTitleTooltip(titleEl, title));
                titleEl.addEventListener('mouseleave', () => {
                    titleTooltip.hide();
                });
                titleEl.addEventListener('focusin', () => showTitleTooltip(titleEl, title));
                titleEl.addEventListener('focusout', () => {
                    titleTooltip.hide();
                });
            }

            tbody.appendChild(row);

            if (isExpanded) {
                tbody.insertAdjacentHTML('beforeend', renderInlinePreviewRow(testCase, { isSelected }));
            }
        }
    }

    function getTestCaseById(workKey) {
        return testCaseMap[workKey] || null;
    }

    return {
        renderRows,
        getTestCaseById
    };
}
