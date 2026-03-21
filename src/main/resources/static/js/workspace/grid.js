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
    // This helper keeps the future “real tags” implementation isolated.
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

let tagTooltipEl = null;
let tagTooltipAnchor = null;

function ensureTagTooltip() {
    if (tagTooltipEl) {
        return tagTooltipEl;
    }

    const el = document.createElement('div');
    el.className = 'fixed z-50 border border-white/20 bg-black px-3 py-2 text-xs text-white/80';
    el.style.display = 'none';
    el.style.pointerEvents = 'none';
    el.style.maxWidth = '22rem';
    document.body.appendChild(el);

    const hide = () => hideTagTooltip();
    window.addEventListener('scroll', hide, true);
    window.addEventListener('resize', hide);

    tagTooltipEl = el;
    return el;
}

function hideTagTooltip() {
    if (!tagTooltipEl) {
        return;
    }
    tagTooltipEl.style.display = 'none';
    tagTooltipEl.innerHTML = '';
    tagTooltipAnchor = null;
}

function positionTooltip(anchorRect, tooltipRect) {
    const padding = 8;
    const viewportWidth = window.innerWidth || document.documentElement.clientWidth || 0;
    const viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0;

    let left = anchorRect.left;
    let top = anchorRect.bottom + padding;

    if (left + tooltipRect.width + padding > viewportWidth) {
        left = Math.max(padding, viewportWidth - tooltipRect.width - padding);
    }

    if (top + tooltipRect.height + padding > viewportHeight) {
        // If there's no room below, try above.
        const above = anchorRect.top - padding - tooltipRect.height;
        if (above >= padding) {
            top = above;
        } else {
            top = Math.max(padding, viewportHeight - tooltipRect.height - padding);
        }
    }

    return { left, top };
}

function showTagTooltip(anchorEl, tags) {
    if (!anchorEl || !tags || tags.length === 0) {
        return;
    }

    const tooltip = ensureTagTooltip();
    tagTooltipAnchor = anchorEl;

    // Render each tag as a badge so future per-tag colors are visible here.
    let html = '<div class="flex flex-wrap gap-2">';
    for (const tag of tags) {
        html += '<span class="max-w-full truncate px-2 py-1 text-xs border border-white/15 text-white/60">' + escHtml(tag) + '</span>';
    }
    html += '</div>';

    tooltip.innerHTML = html;
    tooltip.style.display = 'block';

    const anchorRect = anchorEl.getBoundingClientRect();
    const tooltipRect = tooltip.getBoundingClientRect();
    const pos = positionTooltip(anchorRect, tooltipRect);
    tooltip.style.left = String(Math.round(pos.left)) + 'px';
    tooltip.style.top = String(Math.round(pos.top)) + 'px';
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

    function renderRows(testCases, selectedIds) {
        if (!tbody) {
            return;
        }

        // If the grid rerenders while hovering/focused, ensure any tag tooltip is dismissed.
        hideTagTooltip();

        tbody.innerHTML = '';
        testCaseMap = {};

        if (!testCases || testCases.length === 0) {
            const emptyRow = document.createElement('tr');
            emptyRow.className = 'border-b border-white/10';
            emptyRow.innerHTML =
                '<td class="px-6 sm:px-8 py-6" colspan="6">' +
                '<p class="text-sm text-white/70">No test cases found.</p>' +
                '<p class="text-xs text-white/45 mt-1">Import a CSV or XLSX file to get started.</p>' +
                '</td>';
            tbody.appendChild(emptyRow);
            return;
        }

        for (const testCase of testCases) {
            const workKey = testCase.workKey || '—';
            const title = testCase.summary || '—';
            const folder = testCase.folder || '';
            const status = testCase.status || '—';
            const tagModel = buildTagBadges(testCase, 3);
            const updated = testCase.updatedOn || '—';
            const idFontSize = workKey.length > 14 ? '10px' : (workKey.length > 10 ? '11px' : '12px');

            testCaseMap[workKey] = testCase;

            const row = document.createElement('tr');
            row.className = 'border-b border-white/10 hover:bg-white/5 transition-colors cursor-pointer';
            row.dataset.id = workKey;
            row.dataset.title = title;
            row.dataset.status = status;
            row.dataset.updated = updated;

            const titleCell =
                '<div class="min-w-0">' +
                    '<div class="font-semibold text-white/85 truncate">' + escHtml(title) + '</div>' +
                    (folder ? '<div class="text-white/45 text-xs mt-0.5 truncate">' + escHtml(folder) + '</div>' : '') +
                '</div>';

            const tagsCell = tagModel.html || '<span class="text-white/45">—</span>';
            const encodedTags = tagModel.tags && tagModel.tags.length > 0
                ? encodeURIComponent(JSON.stringify(tagModel.tags))
                : '';
            const tagsTabIndex = encodedTags ? '0' : '-1';

            row.innerHTML =
                '<td class="px-3 sm:px-8 py-2.5" onclick="event.stopPropagation();">' +
                    '<input type="checkbox" class="ws-row-check h-4 w-4 accent-[#E7FF02]" aria-label="Select row" data-work-key="' + escHtml(workKey) + '"' +
                    (selectedIds.has(workKey) ? ' checked' : '') + ' />' +
                '</td>' +
                '<td class="px-3 sm:px-0 sm:pr-6 py-2.5 text-white/55"><div class="min-w-0 whitespace-nowrap leading-tight" style="font-size:' + escHtml(idFontSize) + ';">' + escHtml(workKey) + '</div></td>' +
                '<td class="px-3 sm:px-6 py-2.5">' + titleCell + '</td>' +
                '<td class="px-3 sm:px-6 py-2.5"><span class="inline-flex items-center px-2 py-1 border border-white/15 text-xs text-white/70">' + escHtml(status) + '</span></td>' +
                '<td class="px-3 sm:px-6 py-2.5"><div class="min-w-0 flex flex-wrap gap-2" data-ws-tags="' + escHtml(encodedTags) + '" tabindex="' + tagsTabIndex + '">' + tagsCell + '</div></td>' +
                '<td class="pl-3 pr-6 sm:pl-6 sm:pr-10 py-2.5 text-white/55"><div class="min-w-0 whitespace-nowrap">' + escHtml(updated) + '</div></td>';

            const tagsEl = row.querySelector('[data-ws-tags]');
            if (tagsEl) {
                const tags = decodeTagsFromDataset(tagsEl.getAttribute('data-ws-tags'));
                if (tags.length > 0) {
                    tagsEl.addEventListener('mouseenter', () => showTagTooltip(tagsEl, tags));
                    tagsEl.addEventListener('mouseleave', () => {
                        if (tagTooltipAnchor === tagsEl) {
                            hideTagTooltip();
                        }
                    });
                    tagsEl.addEventListener('focusin', () => showTagTooltip(tagsEl, tags));
                    tagsEl.addEventListener('focusout', () => {
                        if (tagTooltipAnchor === tagsEl) {
                            hideTagTooltip();
                        }
                    });
                }
            }

            tbody.appendChild(row);
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