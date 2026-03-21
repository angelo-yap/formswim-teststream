import { escHtml } from './grid.js';

export function createDrawer(options) {
    const drawer = options.drawer;
    const drawerPanel = options.drawerPanel;
    const drawerBackdrop = options.drawerBackdrop;
    const drawerClose = options.drawerClose;
    const drawerSave = options.drawerSave;
    const drawerId = options.drawerId;
    const drawerTitle = options.drawerTitle;
    const drawerSummary = options.drawerSummary;
    const drawerSteps = options.drawerSteps;
    const drawerStatus = options.drawerStatus;
    const drawerUpdated = options.drawerUpdated;
    const drawerAssignee = options.drawerAssignee;
    const drawerReporter = options.drawerReporter;
    const drawerCreatedOn = options.drawerCreatedOn;
    const drawerTags = options.drawerTags;
    const getTestCaseById = options.getTestCaseById;

    function renderSteps(steps) {
        if (!drawerSteps) {
            return;
        }

        drawerSteps.innerHTML = '';
        if (!steps || steps.length === 0) {
            drawerSteps.innerHTML = '<p class="text-xs text-white/45">No steps.</p>';
            return;
        }

        for (const step of steps) {
            const block = document.createElement('div');
            block.className = 'border border-white/10 p-4';
            block.innerHTML =
                '<p class="text-xs text-white/45">Step ' + step.stepNumber + '</p>' +
                '<div class="mt-3 grid grid-cols-1 gap-3">' +
                    '<input type="text" value="' + escHtml(step.stepSummary || '') + '" class="bg-black border border-white/15 px-3 py-2 text-sm text-white/85 focus:outline-none focus:border-[#E7FF02]" placeholder="Action" />' +
                    '<input type="text" value="' + escHtml(step.testData || '') + '" class="bg-black border border-white/15 px-3 py-2 text-sm text-white/85 focus:outline-none focus:border-[#E7FF02]" placeholder="Data" />' +
                    '<input type="text" value="' + escHtml(step.expectedResult || '') + '" class="bg-black border border-white/15 px-3 py-2 text-sm text-white/85 focus:outline-none focus:border-[#E7FF02]" placeholder="Expected" />' +
                '</div>';
            drawerSteps.appendChild(block);
        }
    }

    function openDrawer(row) {
        if (!drawer || !drawerPanel) {
            return;
        }

        const workKey = row.dataset.id || '—';
        const title = row.dataset.title || '';
        const status = row.dataset.status || '—';
        const updated = row.dataset.updated || '—';
        const testCase = getTestCaseById(workKey) || {};

        if (drawerId) {
            drawerId.textContent = workKey;
        }
        if (drawerTitle) {
            drawerTitle.value = title;
        }
        if (drawerSummary) {
            drawerSummary.value = testCase.description || '';
        }
        if (drawerStatus) {
            drawerStatus.value = status;
        }
        if (drawerUpdated) {
            drawerUpdated.textContent = updated;
        }
        if (drawerAssignee) {
            drawerAssignee.textContent = testCase.assignee || '—';
        }
        if (drawerReporter) {
            drawerReporter.textContent = testCase.reporter || '—';
        }
        if (drawerCreatedOn) {
            drawerCreatedOn.textContent = testCase.createdOn || '—';
        }
        if (drawerTags) {
            const tagParts = [];
            if (testCase.components) {
                tagParts.push(testCase.components);
            }
            if (testCase.testCaseType) {
                tagParts.push(testCase.testCaseType);
            }
            drawerTags.textContent = tagParts.length > 0 ? tagParts.join(', ') : '—';
        }

        renderSteps(testCase.steps || []);

        drawer.classList.remove('hidden');
        drawer.setAttribute('aria-hidden', 'false');
        requestAnimationFrame(() => {
            drawerPanel.classList.remove('translate-x-full');
        });
    }

    function closeDrawer() {
        if (!drawer || !drawerPanel) {
            return;
        }

        drawerPanel.classList.add('translate-x-full');
        drawer.setAttribute('aria-hidden', 'true');
        window.setTimeout(() => {
            drawer.classList.add('hidden');
        }, 180);
    }

    if (drawerBackdrop) {
        drawerBackdrop.addEventListener('click', closeDrawer);
    }
    if (drawerClose) {
        drawerClose.addEventListener('click', closeDrawer);
    }
    if (drawerSave) {
        drawerSave.addEventListener('click', closeDrawer);
    }

    return {
        bind(tbody) {
            if (!tbody) {
                return;
            }

            tbody.addEventListener('click', (event) => {
                const target = event.target;
                if (target && target.classList && target.classList.contains('ws-row-check')) {
                    return;
                }

                const row = target && target.closest ? target.closest('tr') : null;
                if (!row || !row.dataset.id) {
                    return;
                }

                openDrawer(row);
            });
        }
    };
}