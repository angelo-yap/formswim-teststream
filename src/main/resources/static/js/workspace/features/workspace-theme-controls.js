const THEME_STORAGE_KEY = 'workspace.theme';
const THEMES = {
    soft: 'workspace-theme-soft',
    black: 'workspace-theme-black'
};
const DEFAULT_THEME = 'soft';

function resolveTheme(rawValue) {
    const value = String(rawValue || '').trim().toLowerCase();
    return Object.prototype.hasOwnProperty.call(THEMES, value) ? value : DEFAULT_THEME;
}

function applyTheme(themeKey) {
    const body = document.body;
    if (!body) {
        return;
    }

    body.classList.remove(THEMES.soft, THEMES.black);
    body.classList.add(THEMES[themeKey]);
    body.setAttribute('data-workspace-theme', themeKey);
}

export function bindWorkspaceThemeControls() {
    const select = document.getElementById('wsThemeSelect');
    const storedTheme = (() => {
        try {
            return window.localStorage.getItem(THEME_STORAGE_KEY);
        } catch (e) {
            return null;
        }
    })();

    const initialTheme = resolveTheme(storedTheme);
    applyTheme(initialTheme);

    if (!select) {
        return {};
    }

    select.value = initialTheme;
    select.addEventListener('change', () => {
        const nextTheme = resolveTheme(select.value);
        applyTheme(nextTheme);
        try {
            window.localStorage.setItem(THEME_STORAGE_KEY, nextTheme);
        } catch (e) {
            // Ignore storage failures and still keep the in-memory theme.
        }
    });

    return {
        applyTheme
    };
}
