const TAG_COLORS = [
    { color: '#60a5fa', bg: 'rgba(96,165,250,0.18)' },   // blue
    { color: '#a78bfa', bg: 'rgba(167,139,250,0.18)' },  // violet
    { color: '#4ade80', bg: 'rgba(74,222,128,0.18)' },   // green
    { color: '#fb923c', bg: 'rgba(251,146,60,0.18)' },   // orange
    { color: '#f472b6', bg: 'rgba(244,114,182,0.18)' },  // pink
    { color: '#22d3ee', bg: 'rgba(34,211,238,0.18)' },   // cyan
    { color: '#f87171', bg: 'rgba(248,113,113,0.18)' },  // red
    { color: '#a3e635', bg: 'rgba(163,230,53,0.18)' },   // lime
    { color: '#fbbf24', bg: 'rgba(251,191,36,0.18)' },   // amber
    { color: '#818cf8', bg: 'rgba(129,140,248,0.18)' },  // indigo
    { color: '#34d399', bg: 'rgba(52,211,153,0.18)' },   // emerald
    { color: '#f43f5e', bg: 'rgba(244,63,94,0.18)' },    // rose
    { color: '#38bdf8', bg: 'rgba(56,189,248,0.18)' },   // sky
    { color: '#c084fc', bg: 'rgba(192,132,252,0.18)' },  // purple
    { color: '#fdba74', bg: 'rgba(253,186,116,0.18)' },  // peach
    { color: '#86efac', bg: 'rgba(134,239,172,0.18)' },  // sage
    { color: '#67e8f9', bg: 'rgba(103,232,249,0.18)' },  // light-cyan
    { color: '#fda4af', bg: 'rgba(253,164,175,0.18)' },  // light-rose
    { color: '#d9f99d', bg: 'rgba(217,249,157,0.18)' },  // yellow-green
    { color: '#93c5fd', bg: 'rgba(147,197,253,0.18)' },  // light-blue
];

export function dismissTagTooltip() { hideTagTooltip(); }

export function tagColor(tagName) {
    const s = String(tagName || '');
    const n = Math.abs(s.split('').reduce((h, c) => ((h << 5) - h + c.charCodeAt(0)) | 0, 0));
    return TAG_COLORS[n % TAG_COLORS.length];
}

export function escHtml(value) {
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}


function buildTagBadges(testCase, maxVisible = 3, workKey = '') {
    const rawTags = Array.isArray(testCase?.tags) ? testCase.tags : [];
    const validTags = rawTags.filter((t) => t?.name && String(t.name).trim());
    const tagObjects = validTags.map((t) => ({ id: t.id, name: String(t.name).trim() }));
    const visible = tagObjects.slice(0, Math.max(0, maxVisible));
    const hiddenCount = Math.max(0, tagObjects.length - visible.length);

    let html = '';
    for (const t of visible) {
        const c = tagColor(t.name);
        const removeBtn = workKey && t.id != null
            ? '<button type="button" class="ws-row-action ws-interactive shrink-0 leading-none opacity-60 hover:opacity-100" data-action="remove-tag" data-work-key="' + escHtml(workKey) + '" data-tag-id="' + escHtml(String(t.id)) + '" aria-label="Remove tag">&#x2715;</button>'
            : '';
        html += '<span class="inline-flex items-center gap-1 max-w-full px-2 py-0.5 text-xs border" style="color:' + c.color + ';border-color:' + c.color + '40;background:' + c.bg + '"><span class="truncate">' + escHtml(t.name) + '</span>' + removeBtn + '</span>';
    }

    return { tags: tagObjects, html, hiddenCount };
}

let tagTooltipEl = null;
let tagTooltipAnchor = null;
let tagTooltipHideTimer = null;

function scheduleHideTagTooltip() {
    tagTooltipHideTimer = setTimeout(hideTagTooltip, 120);
}

function cancelHideTagTooltip() {
    if (tagTooltipHideTimer) {
        clearTimeout(tagTooltipHideTimer);
        tagTooltipHideTimer = null;
    }
}

function ensureTagTooltip() {
    if (tagTooltipEl) {
        return tagTooltipEl;
    }

    const el = document.createElement('div');
    el.className = 'fixed z-50 border border-white/20 bg-black px-3 py-2 text-xs text-white/80';
    el.style.display = 'none';
    el.style.maxWidth = '22rem';
    document.body.appendChild(el);

    el.addEventListener('mouseenter', cancelHideTagTooltip);
    el.addEventListener('mouseleave', scheduleHideTagTooltip);
    el.addEventListener('click', (e) => {
        const btn = e.target.closest('.ws-tooltip-remove');
        if (!btn) return;
        const wk = btn.dataset.workKey;
        const tagId = parseInt(btn.dataset.tagId, 10);
        if (wk && tagId) {
            document.dispatchEvent(new CustomEvent('ws-remove-tag', { detail: { workKey: wk, tagId } }));
            hideTagTooltip();
        }
    });

    const hide = () => hideTagTooltip();
    window.addEventListener('scroll', hide, true);
    window.addEventListener('resize', hide);

    tagTooltipEl = el;
    return el;
}

function hideTagTooltip() {
    cancelHideTagTooltip();
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

function showTagTooltip(anchorEl, tags, workKey) {
    if (!anchorEl || !tags || tags.length === 0) {
        return;
    }

    const tooltip = ensureTagTooltip();
    tagTooltipAnchor = anchorEl;

    let html = '<div class="flex flex-wrap gap-2">';
    for (const tag of tags) {
        const name = typeof tag === 'string' ? tag : tag.name;
        const tagId = typeof tag === 'string' ? null : tag.id;
        const c = tagColor(name);
        const removeBtn = workKey && tagId != null
            ? '<button type="button" class="ws-tooltip-remove shrink-0 opacity-60 hover:opacity-100 leading-none ml-1" data-work-key="' + escHtml(workKey) + '" data-tag-id="' + escHtml(String(tagId)) + '" aria-label="Remove">&#x2715;</button>'
            : '';
        html += '<span class="inline-flex items-center whitespace-nowrap px-2 py-1 text-xs border" style="color:' + c.color + ';border-color:' + c.color + '40;background:' + c.bg + '">' + escHtml(name) + removeBtn + '</span>';
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
        return Array.isArray(parsed) ? parsed.filter(Boolean) : [];
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
                '<td class="px-6 sm:px-8 py-6" colspan="7">' +
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
            const tagModel = buildTagBadges(testCase, 2, workKey);
            const updated = testCase.updatedOn || '—';
const previewUrl = '/workspace/test-cases/' + encodeURIComponent(workKey);

            testCaseMap[workKey] = testCase;

            const row = document.createElement('tr');
            const isSelected = selectedIds.has(workKey);
            row.className = 'ws-row border-b border-white/10 hover:bg-white/5 transition-colors cursor-pointer' + (isSelected ? ' ws-row-selected' : '');
            row.dataset.id = workKey;
            row.dataset.workKey = workKey;
            row.dataset.title = title;
            row.dataset.status = status;
            row.dataset.updated = updated;

            const titleCell =
                '<div class="min-w-0">' +
                    '<div class="font-semibold text-white/85 truncate">' + escHtml(title) + '</div>' +
                    (folder ? '<div class="text-white/45 text-xs mt-0.5 truncate">' + escHtml(folder) + '</div>' : '') +
                '</div>';

            const encodedTags = tagModel.tags && tagModel.tags.length > 0
                ? encodeURIComponent(JSON.stringify(tagModel.tags))
                : '';
            const tagsTabIndex = encodedTags ? '0' : '-1';
            const overflowBadge = tagModel.hiddenCount > 0
                ? '<span class="shrink-0 px-1.5 py-0.5 text-xs text-white/40 border border-white/15 rounded-full">+' + tagModel.hiddenCount + '</span>'
                : '';
            const tagsCell = tagModel.html
                ? '<div class="min-w-0 flex flex-nowrap gap-1.5 overflow-hidden">' + tagModel.html + '</div>' + overflowBadge
                : '<span class="text-white/45">—</span>';

            row.innerHTML =
                '<td class="px-2 sm:px-4 py-2.5 text-white/45">' +
                    '<button type="button" class="ws-row-grab ws-interactive h-7 w-7 inline-flex items-center justify-center border border-transparent hover:border-white/20 rounded-sm" aria-label="Drag to move" draggable="true" data-work-key="' + escHtml(workKey) + '">' +
                        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" fill="currentColor" class="w-3.5 h-3.5"><circle cx="4" cy="3" r="1.1" /><circle cx="4" cy="8" r="1.1" /><circle cx="4" cy="13" r="1.1" /><circle cx="12" cy="3" r="1.1" /><circle cx="12" cy="8" r="1.1" /><circle cx="12" cy="13" r="1.1" /></svg>' +
                    '</button>' +
                '</td>' +
                '<td class="px-2 sm:px-3 py-2.5">' +
                    '<input type="checkbox" class="ws-row-check ws-interactive h-4 w-4 accent-[#E7FF02]" aria-label="Select row" data-work-key="' + escHtml(workKey) + '"' +
                    (selectedIds.has(workKey) ? ' checked' : '') + ' />' +
                '</td>' +
                '<td class="px-3 sm:px-6 py-2.5"><div class="min-w-0 truncate">' + titleCell + '</div></td>' +
                '<td class="px-3 sm:px-6 py-2.5"><span class="inline-flex items-center px-2 py-1 border border-white/15 text-xs text-white/70">' + escHtml(status) + '</span></td>' +
                '<td class="px-3 sm:px-6 py-2.5"><div class="flex items-center gap-1.5"><div class="min-w-0 flex flex-nowrap items-center gap-1.5" data-ws-tags="' + escHtml(encodedTags) + '" tabindex="' + tagsTabIndex + '">' + tagsCell + '</div><button type="button" class="ws-row-action ws-interactive shrink-0 h-5 w-5 inline-flex items-center justify-center rounded-full border border-white/15 text-white/40 hover:border-white/40 hover:text-white/70 text-xs leading-none" data-action="add-tag" data-work-key="' + escHtml(workKey) + '" aria-label="Add tag">+</button></div></td>' +
                '<td class="pl-3 pr-6 sm:pl-6 sm:pr-4 py-2.5 text-white/55"><div class="min-w-0 whitespace-nowrap">' + escHtml(updated) + '</div></td>' +
                '<td class="px-2 sm:px-4 py-2.5 text-right">' +
                    '<div class="inline-flex items-center gap-2" data-work-key="' + escHtml(workKey) + '">' +
                        '<button type="button" class="ws-row-action ws-row-preview ws-interactive px-2.5 py-1.5 border border-white/20 hover:border-[#E7FF02] hover:text-[#E7FF02] text-xs" data-action="preview" data-work-key="' + escHtml(workKey) + '">Preview</button>' +
                        '<a class="ws-row-action ws-row-edit ws-interactive px-2.5 py-1.5 border border-white/20 hover:border-[#E7FF02] hover:text-[#E7FF02] text-xs" data-action="edit" data-work-key="' + escHtml(workKey) + '" href="' + escHtml(previewUrl) + '">Edit</a>' +
                    '</div>' +
                '</td>';

            const tagsEl = row.querySelector('[data-ws-tags]');
            if (tagsEl) {
                const tags = decodeTagsFromDataset(tagsEl.getAttribute('data-ws-tags'));
                if (tags.length > 0) {
                    tagsEl.addEventListener('mouseenter', () => { cancelHideTagTooltip(); showTagTooltip(tagsEl, tags, workKey); });
                    tagsEl.addEventListener('mouseleave', () => {
                        if (tagTooltipAnchor === tagsEl) {
                            scheduleHideTagTooltip();
                        }
                    });
                    tagsEl.addEventListener('focusin', (e) => { if (!e.target.closest('[data-action="remove-tag"]')) { showTagTooltip(tagsEl, tags, workKey); } });
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

    function updateRowTags(workKey, updatedTags) {
        const tc = testCaseMap[workKey];
        if (!tc) return;
        tc.tags = updatedTags;
        const row = tbody.querySelector('tr[data-work-key="' + CSS.escape(workKey) + '"]');
        if (!row) return;
        const tagModel = buildTagBadges(tc, 2, workKey);
        const encodedTags = tagModel.tags.length > 0 ? encodeURIComponent(JSON.stringify(tagModel.tags)) : '';
        const overflowBadge = tagModel.hiddenCount > 0
            ? '<span class="shrink-0 px-1.5 py-0.5 text-xs text-white/40 border border-white/15 rounded-full">+' + tagModel.hiddenCount + '</span>'
            : '';
        const tagsCell = tagModel.html
            ? '<div class="min-w-0 flex flex-nowrap gap-1.5 overflow-hidden">' + tagModel.html + '</div>' + overflowBadge
            : '<span class="text-white/45">—</span>';
        const tagsEl = row.querySelector('[data-ws-tags]');
        if (!tagsEl) return;
        // Clone to drop all old event listeners, then replace.
        const fresh = tagsEl.cloneNode(false);
        fresh.setAttribute('data-ws-tags', escHtml(encodedTags));
        fresh.setAttribute('tabindex', encodedTags ? '0' : '-1');
        fresh.innerHTML = tagsCell;
        tagsEl.parentNode.replaceChild(fresh, tagsEl);
        if (tagModel.tags.length > 0) {
            fresh.addEventListener('mouseenter', () => { cancelHideTagTooltip(); showTagTooltip(fresh, tagModel.tags, workKey); });
            fresh.addEventListener('mouseleave', () => { if (tagTooltipAnchor === fresh) { scheduleHideTagTooltip(); } });
            fresh.addEventListener('focusin', (e) => { if (!e.target.closest('[data-action="remove-tag"]')) { showTagTooltip(fresh, tagModel.tags, workKey); } });
            fresh.addEventListener('focusout', () => { if (tagTooltipAnchor === fresh) { hideTagTooltip(); } });
        }
    }

    return {
        renderRows,
        getTestCaseById,
        updateRowTags
    };
}