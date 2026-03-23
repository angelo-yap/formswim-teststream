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
