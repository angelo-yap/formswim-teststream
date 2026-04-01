export function createWorkspaceOrganizeModal(options) {
    const selection = options.selection;
    const normalizeFolder = options.normalizeFolder;
    const getKnownFolders = options.getKnownFolders;
    const moveWorkKeys = options.moveWorkKeys;
    const bulkOrganize = options.bulkOrganize;
    const organizeModal = options.organizeModal;
    const organizeBackdrop = options.organizeBackdrop;
    const organizePanel = options.organizePanel;
    const organizeClose = options.organizeClose;
    const organizeCancel = options.organizeCancel;
    const organizeSave = options.organizeSave;
    const organizeFolderSelect = options.organizeFolderSelect;
    const organizeFolderInput = options.organizeFolderInput;
    const organizeError = options.organizeError;

    function populateOrganizeFolderOptions(selectedValue) {
        if (!organizeFolderSelect) {
            return;
        }

        const folders = getKnownFolders();
        organizeFolderSelect.innerHTML = '<option value="">Select a folder</option>';
        for (const folder of folders) {
            const option = document.createElement('option');
            option.value = folder;
            option.textContent = folder;
            organizeFolderSelect.appendChild(option);
        }

        const normalizedSelected = normalizeFolder(selectedValue || '');
        if (normalizedSelected && folders.includes(normalizedSelected)) {
            organizeFolderSelect.value = normalizedSelected;
        }
    }

    function closeOrganizeModal() {
        if (!organizeModal || !organizePanel) {
            return;
        }

        organizePanel.classList.add('translate-y-3', 'opacity-0');
        organizeModal.setAttribute('aria-hidden', 'true');
        window.setTimeout(() => {
            organizeModal.classList.add('hidden');
        }, 160);
    }

    function openOrganizeModal() {
        if (!organizeModal || !organizePanel) {
            return;
        }

        const selectedIds = selection.getSelectedIds();
        if (selectedIds.length === 0) {
            return;
        }

        populateOrganizeFolderOptions('');
        if (organizeFolderInput) {
            organizeFolderInput.value = '';
        }
        if (organizeError) {
            organizeError.classList.add('hidden');
            organizeError.textContent = 'A target folder is required.';
        }

        organizeModal.classList.remove('hidden');
        organizeModal.setAttribute('aria-hidden', 'false');
        requestAnimationFrame(() => {
            organizePanel.classList.remove('translate-y-3', 'opacity-0');
        });
    }

    if (bulkOrganize) {
        bulkOrganize.addEventListener('click', openOrganizeModal);
    }
    if (organizeBackdrop) {
        organizeBackdrop.addEventListener('click', closeOrganizeModal);
    }
    if (organizeClose) {
        organizeClose.addEventListener('click', closeOrganizeModal);
    }
    if (organizeCancel) {
        organizeCancel.addEventListener('click', closeOrganizeModal);
    }
    if (organizeFolderSelect && organizeFolderInput) {
        organizeFolderSelect.addEventListener('change', () => {
            const selected = normalizeFolder(organizeFolderSelect.value || '');
            if (selected) {
                organizeFolderInput.value = selected;
            }
        });

        organizeFolderInput.addEventListener('input', () => {
            const normalized = normalizeFolder(organizeFolderInput.value || '');
            if (normalizeFolder(organizeFolderSelect.value || '') === normalized) {
                return;
            }
            organizeFolderSelect.value = '';
        });
    }
    if (organizeSave) {
        organizeSave.addEventListener('click', async () => {
            const workKeys = selection.getSelectedIds();
            const fromSelect = normalizeFolder(organizeFolderSelect?.value || '');
            const fromInput = normalizeFolder(organizeFolderInput?.value || '');
            const targetFolder = normalizeFolder(fromInput || fromSelect);

            if (!targetFolder) {
                if (organizeError) {
                    organizeError.classList.remove('hidden');
                    organizeError.textContent = 'A target folder is required.';
                }
                return;
            }

            if (organizeError) {
                organizeError.classList.add('hidden');
            }

            const moved = await moveWorkKeys({
                workKeys,
                targetFolder,
                source: 'organize-modal'
            });

            if (moved) {
                closeOrganizeModal();
            }
        });
    }

    return {
        close: closeOrganizeModal,
        open: openOrganizeModal
    };
}
