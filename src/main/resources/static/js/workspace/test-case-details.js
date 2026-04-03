function escapeHtml(value) {
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function safeHref(rawUrl) {
    const value = String(rawUrl || '').trim();
    if (!value) {
        return '#';
    }

    try {
        const parsed = new URL(value, window.location.origin);
        if (parsed.protocol === 'http:' || parsed.protocol === 'https:') {
            return parsed.href;
        }
    } catch (error) {
        return '#';
    }

    return '#';
}

function createLinkHtml(text, url) {
    const href = safeHref(url);
    const label = escapeHtml(text || url || 'link');
    const hrefAttr = escapeHtml(href);
    const isImage = /\.(png|jpe?g|gif|webp|svg)(\?.*)?$/i.test(url || '');
    const imageSuffix = isImage ? ' <span class="text-white/50">(image link)</span>' : '';

    return '<a class="text-[#E7FF02] underline underline-offset-2 break-all hover:text-[#f3ff7a]" href="' + hrefAttr + '" target="_blank" rel="noopener noreferrer">' + label + '</a>' + imageSuffix;
}

function tokenizeLinks(raw) {
    const linkTokens = [];
    let output = String(raw || '');

    output = output.replace(/\[([^\]|]+)\|([^\]]+)\]/g, (_, label, url) => {
        const token = '__LINK_TOKEN_' + linkTokens.length + '__';
        linkTokens.push({ token, html: createLinkHtml(label.trim(), url.trim()) });
        return token;
    });

    output = output.replace(/(https?:\/\/[^\s<]+)/g, (url) => {
        const token = '__LINK_TOKEN_' + linkTokens.length + '__';
        linkTokens.push({ token, html: createLinkHtml(url, url) });
        return token;
    });

    return { output, linkTokens };
}

function applyInlineMarkdown(rawLine) {
    const withLinks = tokenizeLinks(rawLine);
    let safe = escapeHtml(withLinks.output);

    safe = safe
        .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
        .replace(/\*([^*]+)\*/g, '<em>$1</em>');

    for (const token of withLinks.linkTokens) {
        safe = safe.replaceAll(token.token, token.html);
    }

    return safe;
}

function renderMarkdown(rawText, emptyLabel) {
    const raw = normalizeMarkdownInput(rawText);
    if (!raw) {
        return '<p class="text-white/55">' + escapeHtml(emptyLabel || 'Not provided') + '</p>';
    }

    const lines = raw.replace(/\r\n/g, '\n').split('\n');
    const blocks = [];
    let listItems = [];

    function flushList() {
        if (listItems.length === 0) {
            return;
        }

        let listHtml = '<ul class="list-disc pl-5 space-y-1">';
        for (const item of listItems) {
            listHtml += '<li>' + applyInlineMarkdown(item) + '</li>';
        }
        listHtml += '</ul>';
        blocks.push(listHtml);
        listItems = [];
    }

    for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed) {
            flushList();
            continue;
        }

        const headingMatch = trimmed.match(/^(#{1,3})\s+(.+)$/);
        if (headingMatch) {
            flushList();
            const level = headingMatch[1].length;
            const headingText = applyInlineMarkdown(headingMatch[2]);
            const headingClass = level === 1
                ? 'text-sm font-semibold text-white'
                : 'text-sm font-semibold text-white/90';
            blocks.push('<p class="' + headingClass + '">' + headingText + '</p>');
            continue;
        }

        if (/^[-*]\s+/.test(trimmed)) {
            listItems.push(trimmed.replace(/^[-*]\s+/, ''));
            continue;
        }

        flushList();
        blocks.push('<p>' + applyInlineMarkdown(trimmed) + '</p>');
    }

    flushList();

    if (blocks.length === 0) {
        return '<p class="text-white/55">' + escapeHtml(emptyLabel || 'Not provided') + '</p>';
    }

    return '<div class="space-y-3 break-words leading-7">' + blocks.join('') + '</div>';
}

function formatMarkdownBlocks() {
    const blocks = document.querySelectorAll('[data-md="true"]');
    for (const block of blocks) {
        const emptyLabel = block.getAttribute('data-empty-label') || 'Not provided';
        block.innerHTML = renderMarkdown(block.textContent || '', emptyLabel);
    }
}

function normalizeMarkdownInput(value) {
    if (value == null) {
        return '';
    }

    // Break long inline heading markers into separate lines for readability.
    return String(value)
        .replace(/\r\n/g, '\n')
        .replace(/\s+(#{1,3})\s+/g, '\n$1 ')
        .trim();
}

function initBackToWorkspaceLink() {
    const backLink = document.querySelector('[data-back-to-workspace="true"]');
    if (!backLink) {
        return;
    }

    let hasSameOriginReferrer = false;
    if (document.referrer) {
        try {
            hasSameOriginReferrer = new URL(document.referrer).origin === window.location.origin;
        } catch (error) {
            hasSameOriginReferrer = false;
        }
    }

    const canUseHistoryBack = window.history.length > 1 && hasSameOriginReferrer;

    backLink.addEventListener('click', (event) => {
        if (!canUseHistoryBack) {
            return;
        }

        // Keep modifier clicks/new-tab behavior unchanged.
        if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
            return;
        }

        event.preventDefault();
        window.history.back();
    });
}

initBackToWorkspaceLink();
formatMarkdownBlocks();

// ---- Single-field edit support ----

let noticeTimeout = null;

function getCsrf() {
    return {
        token: document.querySelector('meta[name="_csrf"]')?.content || '',
        headerName: document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN'
    };
}

function getWorkKey() {
    return document.querySelector('[data-work-key]')?.dataset?.workKey || '';
}

function getApiFieldKey(fieldKey) {
    // Composite step field keys like "step-1-stepSummary" map to just "stepSummary" for the API.
    const stepMatch = fieldKey.match(/^step-\d+-(.+)$/);
    return stepMatch ? stepMatch[1] : fieldKey;
}

function showNotice(type, message) {
    const notice = document.getElementById('detailsNotice');
    const msg = document.getElementById('detailsNoticeMsg');
    if (!notice || !msg) {
        return;
    }
    msg.textContent = message;
    notice.classList.toggle('border-[#E7FF02]/50', type === 'success');
    notice.classList.toggle('border-red-500/40', type !== 'success');
    notice.classList.remove('hidden');
    clearTimeout(noticeTimeout);
    noticeTimeout = setTimeout(() => notice.classList.add('hidden'), 6000);
}

async function saveFieldEdit(workKey, fieldKey, oldValue, newValue) {
    const csrf = getCsrf();
    const apiField = getApiFieldKey(fieldKey);
    const response = await fetch('/api/testcases/bulk-edit', {
        method: 'PATCH',
        headers: {
            'Content-Type': 'application/json',
            [csrf.headerName]: csrf.token
        },
        body: JSON.stringify({
            workKeys: [workKey],
            findText: oldValue,
            replaceText: newValue,
            fields: [apiField]
        })
    });
    if (!response.ok) {
        const err = new Error('Edit failed with status ' + response.status);
        err.status = response.status;
        throw err;
    }
    return response.json();
}

function updateFolderSegments(newFolderPath) {
    const container = document.getElementById('folderSegmentsContainer');
    if (!container) {
        return;
    }
    const segments = String(newFolderPath || '').split('/').map((s) => s.trim()).filter(Boolean);
    if (segments.length === 0) {
        container.innerHTML = '';
        return;
    }
    container.innerHTML = segments.map((segment, index) => {
        const sep = index < segments.length - 1
            ? '<span class="text-white/40">/</span>'
            : '';
        return '<span class="px-2 py-1 border border-white/15 bg-black/20">' + escapeHtml(segment) + '</span>' + sep;
    }).join('');
}

function applyDisplayUpdate(fieldKey, newValue) {
    const displayEl = document.querySelector('[data-field-display="' + fieldKey + '"]');
    if (!displayEl) {
        return;
    }
    displayEl.dataset.rawValue = newValue;
    if (displayEl.dataset.md === 'true') {
        const emptyLabel = displayEl.dataset.emptyLabel || 'Not provided';
        displayEl.innerHTML = renderMarkdown(newValue, emptyLabel);
    } else {
        displayEl.textContent = newValue;
    }
    if (fieldKey === 'folder') {
        updateFolderSegments(newValue);
    }
}

function activateEditMode(fieldKey) {
    const displayEl = document.querySelector('[data-field-display="' + fieldKey + '"]');
    const editContainer = document.querySelector('[data-field-edit="' + fieldKey + '"]');
    const editBtn = document.querySelector('[data-edit-field="' + fieldKey + '"]');
    const textarea = document.querySelector('[data-field-textarea="' + fieldKey + '"]');
    const input = document.querySelector('[data-field-input="' + fieldKey + '"]');
    if (!displayEl || !editContainer) {
        return;
    }
    const currentValue = displayEl.dataset.rawValue || '';
    if (textarea) {
        textarea.value = currentValue;
    }
    if (input) {
        input.value = currentValue;
    }
    displayEl.classList.add('hidden');
    if (editBtn) {
        editBtn.classList.add('hidden');
    }
    editContainer.classList.remove('hidden');
    const focusTarget = textarea || input;
    if (focusTarget) {
        focusTarget.focus();
    }
}

function deactivateEditMode(fieldKey) {
    const displayEl = document.querySelector('[data-field-display="' + fieldKey + '"]');
    const editContainer = document.querySelector('[data-field-edit="' + fieldKey + '"]');
    const editBtn = document.querySelector('[data-edit-field="' + fieldKey + '"]');
    if (editContainer) {
        editContainer.classList.add('hidden');
    }
    if (displayEl) {
        displayEl.classList.remove('hidden');
    }
    if (editBtn) {
        editBtn.classList.remove('hidden');
    }
}

function getEditErrorMessage(error) {
    const status = error?.status;
    if (status === 401) {
        return 'You must be logged in to edit.';
    }
    if (status === 403) {
        return 'You do not have permission to edit this test case.';
    }
    if (status === 400) {
        return 'The current value may have changed. Refresh and try again.';
    }
    return 'Edit failed. Please try again.';
}

function initFieldEditing() {
    const workKey = getWorkKey();
    if (!workKey) {
        return;
    }

    document.querySelectorAll('[data-edit-field]').forEach((editBtn) => {
        const fieldKey = editBtn.dataset.editField;
        editBtn.addEventListener('click', () => activateEditMode(fieldKey));
    });

    document.querySelectorAll('[data-field-cancel]').forEach((cancelBtn) => {
        const fieldKey = cancelBtn.dataset.fieldCancel;
        cancelBtn.addEventListener('click', () => deactivateEditMode(fieldKey));
    });

    document.querySelectorAll('[data-field-save]').forEach((saveBtn) => {
        const fieldKey = saveBtn.dataset.fieldSave;
        const errorEl = document.querySelector('[data-field-error="' + fieldKey + '"]');

        saveBtn.addEventListener('click', async () => {
            if (saveBtn.disabled) {
                return;
            }

            const displayEl = document.querySelector('[data-field-display="' + fieldKey + '"]');
            const textarea = document.querySelector('[data-field-textarea="' + fieldKey + '"]');
            const input = document.querySelector('[data-field-input="' + fieldKey + '"]');

            const oldValue = displayEl?.dataset?.rawValue || '';
            const newValue = (textarea || input)?.value || '';

            if (newValue === oldValue) {
                deactivateEditMode(fieldKey);
                return;
            }

            if (!oldValue.trim()) {
                if (errorEl) {
                    errorEl.textContent = 'Empty fields cannot be edited here. Use bulk edit to add content.';
                    errorEl.classList.remove('hidden');
                }
                return;
            }

            if (errorEl) {
                errorEl.classList.add('hidden');
            }

            const originalLabel = saveBtn.textContent;
            saveBtn.textContent = 'Saving...';
            saveBtn.disabled = true;

            try {
                const result = await saveFieldEdit(workKey, fieldKey, oldValue, newValue);
                const didChange = Number(result?.totalReplacements || 0) > 0;

                if (didChange) {
                    applyDisplayUpdate(fieldKey, newValue);
                    deactivateEditMode(fieldKey);
                    showNotice('success', 'Field updated.');
                } else {
                    if (errorEl) {
                        errorEl.textContent = 'No match found. The value may have changed — refresh and try again.';
                        errorEl.classList.remove('hidden');
                    }
                }
            } catch (error) {
                const message = getEditErrorMessage(error);
                if (errorEl) {
                    errorEl.textContent = message;
                    errorEl.classList.remove('hidden');
                }
                showNotice('error', message);
            } finally {
                saveBtn.textContent = originalLabel;
                saveBtn.disabled = false;
            }
        });
    });

    const noticeClose = document.getElementById('detailsNoticeClose');
    if (noticeClose) {
        noticeClose.addEventListener('click', () => {
            const notice = document.getElementById('detailsNotice');
            if (notice) {
                notice.classList.add('hidden');
                clearTimeout(noticeTimeout);
            }
        });
    }
}

initFieldEditing();
