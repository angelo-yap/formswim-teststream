export function bindWorkspaceHeaderControls() {
    const accountToggle = document.getElementById('workspaceAccountToggle');
    const account = document.getElementById('workspaceAccountDropdown');
    const teamToggle = document.getElementById('workspaceTeamCodeToggle');
    const team = document.getElementById('workspaceTeamCodeDropdown');
    const teamCodeValue = document.getElementById('workspaceTeamCodeValue');
    const copyButtons = Array.from(document.querySelectorAll('[data-workspace-copy-team-code]'));
    const flashDismissButtons = Array.from(document.querySelectorAll('[data-workspace-flash-dismiss]'));

    function toggleWorkspaceHeaderDropdown(which) {
        const isAccountOpen = account && !account.classList.contains('hidden');
        const isTeamOpen = team && !team.classList.contains('hidden');

        const nextAccountOpen = which === 'account' ? !isAccountOpen : false;
        const nextTeamOpen = which === 'team' ? !isTeamOpen : false;

        if (account) {
            account.classList.toggle('hidden', !nextAccountOpen);
            account.setAttribute('aria-hidden', String(!nextAccountOpen));
        }
        if (team) {
            team.classList.toggle('hidden', !nextTeamOpen);
            team.setAttribute('aria-hidden', String(!nextTeamOpen));
        }

        if (accountToggle) {
            accountToggle.setAttribute('aria-expanded', String(nextAccountOpen));
        }
        if (teamToggle) {
            teamToggle.setAttribute('aria-expanded', String(nextTeamOpen));
        }
    }

    function dismissWorkspaceFlash(button) {
        const item = button.closest('[data-flash-item]');
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

    if (accountToggle) {
        accountToggle.addEventListener('click', () => toggleWorkspaceHeaderDropdown('account'));
    }
    if (teamToggle) {
        teamToggle.addEventListener('click', () => toggleWorkspaceHeaderDropdown('team'));
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

    return {};
}
