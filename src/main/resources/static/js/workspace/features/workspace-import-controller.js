export function createWorkspaceImportController(options) {
    const api = options.api;
    const importBtn = options.importBtn;
    const importFile = options.importFile;
    const importForm = options.importForm;
    const importNoticeContainer = options.importNoticeContainer;
    const importNotice = options.importNotice;
    const importNoticeBadge = options.importNoticeBadge;
    const importNoticeMessage = options.importNoticeMessage;
    const importNoticeClose = options.importNoticeClose;
    const onUploadComplete = options.onUploadComplete;

    function clearNotice() {
        if (!importNoticeContainer) {
            return;
        }

        importNoticeContainer.classList.add('hidden');
        if (importNoticeMessage) {
            importNoticeMessage.textContent = '';
        }
    }

    function showNotice(type, message) {
        if (!importNoticeContainer || !importNotice || !importNoticeBadge || !importNoticeMessage || !message) {
            return;
        }

        const isSuccess = type === 'success';
        importNoticeContainer.classList.remove('hidden');
        importNoticeMessage.textContent = message;

        if (isSuccess) {
            importNotice.style.borderColor = 'rgba(231, 255, 2, 0.35)';
            importNoticeBadge.textContent = 'OK';
            importNoticeBadge.classList.remove('bg-white/70');
            importNoticeBadge.style.backgroundColor = '#E7FF02';
        } else {
            importNotice.style.borderColor = 'rgba(255, 255, 255, 0.15)';
            importNoticeBadge.textContent = 'Error';
            importNoticeBadge.classList.add('bg-white/70');
            importNoticeBadge.style.backgroundColor = '';
        }
    }

    if (importNoticeClose) {
        importNoticeClose.addEventListener('click', clearNotice);
    }

    if (importBtn && importFile) {
        importBtn.addEventListener('click', () => {
            importFile.click();
        });

        importFile.addEventListener('change', async () => {
            if (!importFile.files || importFile.files.length === 0) {
                return;
            }

            clearNotice();
            importBtn.textContent = 'Importing...';
            importBtn.disabled = true;

            const csrfInput = importForm ? importForm.querySelector('input[type="hidden"]') : null;
            const csrfField = csrfInput && csrfInput.name && csrfInput.value
                ? { name: csrfInput.name, value: csrfInput.value }
                : null;

            try {
                const json = await api.uploadFile({
                    file: importFile.files[0],
                    csrfField
                });

                importBtn.textContent = 'Import';
                importBtn.disabled = false;
                importFile.value = '';

                if (json.reviewRequired && json.reviewUrl) {
                    window.location.href = json.reviewUrl;
                    return;
                }

                if (json.exactDuplicateFile) {
                    showNotice('error', json.message || 'This exact file was already uploaded.');
                    return;
                }

                if (json.errors && json.errors.length > 0) {
                    const header = json.message ? String(json.message).trim() : 'Import failed.';
                    showNotice('error', header + '\n' + json.errors.join('\n'));
                } else if (json.message) {
                    showNotice('success', json.message);
                }

                if (typeof onUploadComplete === 'function') {
                    await onUploadComplete();
                }
            } catch (error) {
                console.error('Upload failed', error);
                const message = error && error.message ? String(error.message) : 'Import failed. Please try again.';
                showNotice('error', message);
                importBtn.textContent = 'Import';
                importBtn.disabled = false;
            }
        });
    }

    return {
        clearNotice,
        showNotice
    };
}
