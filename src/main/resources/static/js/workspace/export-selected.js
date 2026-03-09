export function bindSelectedExport(button, getSelectedIds, exportBaseUrl) {
    if (!button) {
        return {
            updateButtonState() {}
        };
    }

    function updateButtonState(selectedIds) {
        const totalSelected = Array.isArray(selectedIds) ? selectedIds.length : 0;
        button.disabled = totalSelected === 0;
        button.classList.toggle('opacity-40', totalSelected === 0);
        button.classList.toggle('cursor-not-allowed', totalSelected === 0);
    }

    button.addEventListener('click', () => {
        const selectedIds = getSelectedIds();
        if (selectedIds.length === 0) {
            return;
        }

        const params = new URLSearchParams();
        for (const workKey of selectedIds) {
            params.append('workKeys', workKey);
        }
        window.location.href = exportBaseUrl + '?' + params.toString();
    });

    updateButtonState([]);
    return {
        updateButtonState
    };
}