export function escHtml(value) {
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

export function createGrid(tbody) {
    let testCaseMap = {};

    function renderRows(testCases, selectedIds) {
        if (!tbody) {
            return;
        }

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
            const tags = testCase.components || '';
            const testCaseType = testCase.testCaseType || '';
            const updated = testCase.updatedOn || '—';

            testCaseMap[workKey] = testCase;

            const row = document.createElement('tr');
            row.className = 'border-b border-white/10 hover:bg-white/5 transition-colors cursor-pointer';
            row.dataset.id = workKey;
            row.dataset.title = title;
            row.dataset.status = status;
            row.dataset.updated = updated;

            const titleCell = '<span class="font-semibold text-white/85">' + escHtml(title) + '</span>' +
                (folder ? '<span class="text-white/45"> - </span><span class="text-white/45">' + escHtml(folder) + '</span>' : '');

            let tagsCell = '';
            if (tags) {
                tagsCell += '<span class="px-2 py-1 text-xs border border-white/15 text-white/60">' + escHtml(tags) + '</span>';
            }
            if (testCaseType) {
                tagsCell += '<span class="px-2 py-1 text-xs border border-white/15 text-white/60">' + escHtml(testCaseType) + '</span>';
            }
            if (!tagsCell) {
                tagsCell = '<span class="text-white/45">—</span>';
            }

            row.innerHTML =
                '<td class="px-6 sm:px-8 py-2.5" onclick="event.stopPropagation();">' +
                    '<input type="checkbox" class="ws-row-check h-4 w-4 accent-[#E7FF02]" aria-label="Select row" data-work-key="' + escHtml(workKey) + '"' +
                    (selectedIds.has(workKey) ? ' checked' : '') + ' />' +
                '</td>' +
                '<td class="px-6 py-2.5 sm:px-0 sm:pr-8 text-white/55">' + escHtml(workKey) + '</td>' +
                '<td class="px-6 py-2.5"><div class="min-w-0 truncate">' + titleCell + '</div></td>' +
                '<td class="px-6 py-2.5"><span class="inline-flex items-center px-2 py-1 border border-white/15 text-xs text-white/70">' + escHtml(status) + '</span></td>' +
                '<td class="px-6 py-2.5"><div class="flex flex-wrap gap-2">' + tagsCell + '</div></td>' +
                '<td class="px-6 py-2.5 text-white/55">' + escHtml(updated) + '</td>';

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