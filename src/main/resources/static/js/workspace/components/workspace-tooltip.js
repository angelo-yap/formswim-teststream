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
        const above = anchorRect.top - padding - tooltipRect.height;
        if (above >= padding) {
            top = above;
        } else {
            top = Math.max(padding, viewportHeight - tooltipRect.height - padding);
        }
    }

    return { left, top };
}

export function createWorkspaceTooltip(options) {
    const tooltipOptions = options || {};
    const className = tooltipOptions.className || 'fixed z-50 border border-white/20 bg-black px-3 py-2 text-xs text-white/80';
    const maxWidth = tooltipOptions.maxWidth || '22rem';
    const whiteSpace = tooltipOptions.whiteSpace || '';
    const wordBreak = tooltipOptions.wordBreak || '';

    let tooltipEl = null;

    function ensureElement() {
        if (tooltipEl) {
            return tooltipEl;
        }

        const el = document.createElement('div');
        el.className = className;
        el.style.display = 'none';
        el.style.pointerEvents = 'none';
        el.style.maxWidth = maxWidth;
        if (whiteSpace) {
            el.style.whiteSpace = whiteSpace;
        }
        if (wordBreak) {
            el.style.wordBreak = wordBreak;
        }

        document.body.appendChild(el);
        window.addEventListener('scroll', hide, true);
        window.addEventListener('resize', hide);
        tooltipEl = el;
        return el;
    }

    function hide() {
        if (!tooltipEl) {
            return;
        }

        tooltipEl.style.display = 'none';
        tooltipEl.innerHTML = '';
    }

    function show(anchorEl, html) {
        if (!anchorEl) {
            return;
        }

        const tooltip = ensureElement();
        tooltip.innerHTML = String(html || '');
        tooltip.style.display = 'block';

        const anchorRect = anchorEl.getBoundingClientRect();
        const tooltipRect = tooltip.getBoundingClientRect();
        const pos = positionTooltip(anchorRect, tooltipRect);
        tooltip.style.left = String(Math.round(pos.left)) + 'px';
        tooltip.style.top = String(Math.round(pos.top)) + 'px';
    }

    return {
        hide,
        show
    };
}
