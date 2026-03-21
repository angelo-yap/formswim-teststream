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
    const drawerModeLabel = options.drawerModeLabel;
    const drawerFooterHint = options.drawerFooterHint;
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

    function setReadOnlyMode(readOnly) {
        if (!drawer) {
            return;
        }

        const isReadOnly = Boolean(readOnly);
        const fields = drawer.querySelectorAll('input, textarea, select');
        fields.forEach((field) => {
            field.disabled = isReadOnly;
            field.classList.toggle('opacity-70', isReadOnly);
        });

        if (drawerSave) {
            drawerSave.disabled = isReadOnly;
            drawerSave.classList.toggle('hidden', isReadOnly);
        }

        if (drawerModeLabel) {
            drawerModeLabel.textContent = isReadOnly ? 'Preview test case' : 'Edit test case';
        }

        if (drawerFooterHint) {
            drawerFooterHint.textContent = isReadOnly
                ? 'Preview mode is read-only.'
                : 'Save updates and return to grid.';
        }
    }

    function openByWorkKey(workKey, options) {
        if (!drawer || !drawerPanel) {
            return;
        }

        const resolvedWorkKey = workKey || '—';
        const openOptions = options || {};
        const testCase = getTestCaseById(resolvedWorkKey) || {};
        const title = testCase.summary || '';
        const status = testCase.status || '—';
        const updated = testCase.updatedOn || '—';

        if (drawerId) {
            drawerId.textContent = resolvedWorkKey;
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
        setReadOnlyMode(Boolean(openOptions.readOnly));

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
        close: closeDrawer,
        openByWorkKey
    };
}