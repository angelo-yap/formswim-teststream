export function bindWorkspaceHeaderControls() {
    const FLASH_AUTO_DISMISS_MS = 5000;
    const settingsToggle = document.getElementById('workspaceSettingsToggle');
    const settingsIcon = document.getElementById('workspaceSettingsIcon');
    const settingsDropdown = document.getElementById('workspaceSettingsDropdown');
    const teamCodeValue = document.getElementById('workspaceTeamCodeValue');
    const copyButtons = Array.from(document.querySelectorAll('[data-workspace-copy-team-code]'));
    const flashDismissButtons = Array.from(document.querySelectorAll('[data-workspace-flash-dismiss]'));

    function setSettingsOpen(isOpen) {
        if (!settingsDropdown || !settingsToggle) {
            return;
        }

        settingsDropdown.classList.toggle('hidden', !isOpen);
        settingsDropdown.setAttribute('aria-hidden', String(!isOpen));
        settingsToggle.setAttribute('aria-expanded', String(isOpen));
        if (settingsIcon) {
            settingsIcon.classList.toggle('rotate-180', isOpen);
        }
    }

    function dismissWorkspaceFlashItem(item) {
        if (item) {
            item.classList.add('hidden');
        }
        const container = document.getElementById('workspaceFlashContainer');
        if (!container) {
            return;
        }

        const visibleItems = container.querySelectorAll('[data-flash-item]:not(.hidden)');
        if (visibleItems.length === 0) {
            container.classList.add('hidden');
        }
    }

    function dismissWorkspaceFlash(button) {
        const item = button.closest('[data-flash-item]');
        dismissWorkspaceFlashItem(item);
    }

    function scheduleWorkspaceFlashAutoDismiss() {
        const items = Array.from(document.querySelectorAll('#workspaceFlashContainer [data-flash-item]:not(.hidden)'));
        items.forEach((item) => {
            if (!(item instanceof HTMLElement)) {
                return;
            }
            if (item.dataset.autoDismissBound === 'true') {
                return;
            }
            item.dataset.autoDismissBound = 'true';
            window.setTimeout(() => {
                dismissWorkspaceFlashItem(item);
            }, FLASH_AUTO_DISMISS_MS);
        });
    }

    async function copyWorkspaceTeamCode() {
        const statusEl = document.getElementById('workspaceCopyTeamCodeStatus');
        const teamCode = teamCodeValue ? teamCodeValue.textContent.trim() : '';

        if (!teamCode) {
            if (statusEl) {
                statusEl.textContent = 'No team code found.';
            }
            return;
        }

        try {
            if (navigator.clipboard && navigator.clipboard.writeText) {
                await navigator.clipboard.writeText(teamCode);
            } else {
                const input = document.createElement('input');
                input.value = teamCode;
                document.body.appendChild(input);
                input.select();
                document.execCommand('copy');
                document.body.removeChild(input);
            }

            if (statusEl) {
                statusEl.textContent = 'Copied team code.';
            }
            setTimeout(() => {
                if (statusEl) {
                    statusEl.textContent = '';
                }
            }, 1200);
        } catch (error) {
            if (statusEl) {
                statusEl.textContent = 'Copy failed. Please copy manually.';
            }
        }
    }

    if (settingsToggle && settingsDropdown) {
        settingsToggle.addEventListener('click', () => {
            const isOpen = !settingsDropdown.classList.contains('hidden');
            setSettingsOpen(!isOpen);
        });

        document.addEventListener('click', (event) => {
            const target = event.target;
            if (!(target instanceof Node)) {
                return;
            }
            if (settingsDropdown.contains(target) || settingsToggle.contains(target)) {
                return;
            }
            setSettingsOpen(false);
        });

        document.addEventListener('keydown', (event) => {
            if (event.key === 'Escape') {
                setSettingsOpen(false);
            }
        });
    }

    if (teamCodeValue) {
        teamCodeValue.addEventListener('click', () => {
            copyWorkspaceTeamCode();
        });
        teamCodeValue.addEventListener('keydown', (event) => {
            if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                copyWorkspaceTeamCode();
            }
        });
    }
    copyButtons.forEach((button) => {
        button.addEventListener('click', () => {
            copyWorkspaceTeamCode();
        });
    });
    flashDismissButtons.forEach((button) => {
        button.addEventListener('click', () => {
            dismissWorkspaceFlash(button);
        });
    });
    scheduleWorkspaceFlashAutoDismiss();

    return {};
}
