import { createDrawer } from './drawer.js';
import { bindSelectedExport } from './export-selected.js';
import { createGrid } from './grid.js';
import { createSelection } from './selection.js';

const importBtn = document.getElementById('importBtn');
const importFile = document.getElementById('importFile');
const importForm = document.getElementById('importForm');
const tbody = document.getElementById('wsTbody');
const totalCount = document.getElementById('wsTotalCount');
const selectAll = document.getElementById('wsSelectAll');
const searchInput = document.getElementById('wsSearch');
const filterComponent = document.getElementById('wsFilterComponent');
const filterStatus = document.getElementById('wsFilterStatus');
const filterTag = document.getElementById('wsFilterTag');
const bulkBar = document.getElementById('bulkBar');
const bulkCount = document.getElementById('bulkCount');
const bulkExportSelected = document.getElementById('bulkExportSelected');
const bulkOrganize = document.getElementById('bulkOrganize');
const importNoticeContainer = document.getElementById('importNoticeContainer');
const importNotice = document.getElementById('importNotice');
const importNoticeBadge = document.getElementById('importNoticeBadge');
const importNoticeMessage = document.getElementById('importNoticeMessage');
const importNoticeClose = document.getElementById('importNoticeClose');
const prevPageBtn = document.getElementById('wsPrevPage');
const nextPageBtn = document.getElementById('wsNextPage');
const pageInfo = document.getElementById('wsPageInfo');

const folderTree = document.getElementById('wsFolderTree');
const folderLoading = document.getElementById('wsFolderLoading');
const folderEmpty = document.getElementById('wsFolderEmpty');
const sidebar = document.getElementById('wsSidebar');
const sidebarToggle = document.getElementById('wsSidebarToggle');
const sidebarContent = document.getElementById('wsSidebarContent');
const sidebarTitle = document.getElementById('wsSidebarTitle');
const sidebarInner = document.getElementById('wsSidebarInner');
const sidebarHeader = document.getElementById('wsSidebarHeader');
const sidebarToggleExpandedHost = document.getElementById('wsSidebarToggleExpandedHost');
const sidebarToggleCollapsedHost = document.getElementById('wsSidebarToggleCollapsedHost');

const organizeModal = document.getElementById('organizeModal');
const organizeBackdrop = document.getElementById('organizeBackdrop');
const organizePanel = document.getElementById('organizePanel');
const organizeClose = document.getElementById('organizeClose');
const organizeCancel = document.getElementById('organizeCancel');
const organizeSave = document.getElementById('organizeSave');
const organizeFolderSelect = document.getElementById('organizeFolderSelect');
const organizeFolderInput = document.getElementById('organizeFolderInput');
const organizeError = document.getElementById('organizeError');

const apiBaseUrl = document.querySelector('meta[name="workspace-api-base"]')?.content || '/api/testcases';
const exportBaseUrl = document.querySelector('meta[name="workspace-export-base"]')?.content || '/workspace/export';
const componentsBaseUrl = document.querySelector('meta[name="workspace-components-base"]')?.content || '/api/components';
const statusesBaseUrl = document.querySelector('meta[name="workspace-statuses-base"]')?.content || '/api/statuses';
const tagsBaseUrl = document.querySelector('meta[name="workspace-tags-base"]')?.content || '/api/tags';
const foldersBaseUrl = document.querySelector('meta[name="workspace-folders-base"]')?.content || '/api/folders';

const grid = createGrid(tbody);
const selection = createSelection(selectAll, bulkBar, bulkCount);
const exportSelected = bindSelectedExport(bulkExportSelected, () => selection.getSelectedIds(), exportBaseUrl);
const drawer = createDrawer({
    drawer: document.getElementById('drawer'),
    drawerPanel: document.getElementById('drawerPanel'),
    drawerBackdrop: document.getElementById('drawerBackdrop'),
    drawerClose: document.getElementById('drawerClose'),
    drawerSave: document.getElementById('drawerSave'),
    drawerId: document.getElementById('drawerId'),
    drawerTitle: document.getElementById('drawerTitle'),
    drawerSummary: document.getElementById('drawerSummary'),
    drawerSteps: document.getElementById('drawerSteps'),
    drawerStatus: document.getElementById('drawerStatus'),
    drawerUpdated: document.getElementById('drawerUpdated'),
    drawerAssignee: document.getElementById('drawerAssignee'),
    drawerReporter: document.getElementById('drawerReporter'),
    drawerCreatedOn: document.getElementById('drawerCreatedOn'),
    drawerTags: document.getElementById('drawerTags'),
    drawerModeLabel: document.getElementById('drawerModeLabel'),
    drawerFooterHint: document.getElementById('drawerFooterHint'),
    getTestCaseById: grid.getTestCaseById
});

let selectedFolder = '';
let currentPage = 0;
let pageSize = 50;
let totalPages = 0;
let hasNextPage = false;
let allTestCases = [];
let folderTreeModel = null;
let isSidebarExpanded = true;
let activeDragPayload = null;
let activeDropTargetEl = null;

function setSidebarExpanded(expanded) {
    isSidebarExpanded = Boolean(expanded);

    if (sidebar) {
        if (isSidebarExpanded) {
            sidebar.style.width = '';
            sidebar.style.minWidth = '';
            sidebar.style.borderRight = '';
            sidebar.style.overflow = '';
        } else {
            sidebar.style.width = '0px';
            sidebar.style.minWidth = '0px';
            sidebar.style.borderRight = '0';
            sidebar.style.overflow = 'hidden';
        }
    }

    if (sidebarContent) {
        sidebarContent.classList.toggle('hidden', !isSidebarExpanded);
    }

    if (sidebarTitle) {
        sidebarTitle.classList.toggle('hidden', !isSidebarExpanded);
    }

    if (sidebarInner) {
        sidebarInner.style.padding = isSidebarExpanded ? '' : '0.25rem';
    }

    if (sidebarHeader) {
        sidebarHeader.style.justifyContent = isSidebarExpanded ? '' : 'center';
    }

    if (sidebarToggle) {
        sidebarToggle.setAttribute('aria-expanded', String(isSidebarExpanded));
        sidebarToggle.setAttribute('aria-label', isSidebarExpanded ? 'Collapse repository sidebar' : 'Expand repository sidebar');
        sidebarToggle.style.width = '';
        sidebarToggle.style.padding = '';
        sidebarToggle.innerHTML = isSidebarExpanded
            ? '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="w-4 h-4"><path d="M15 6l-6 6 6 6" /></svg>'
            : '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="w-4 h-4"><path d="M9 6l6 6-6 6" /></svg>';
    }

    if (sidebarToggleExpandedHost && sidebarToggleCollapsedHost && sidebarToggle) {
        if (isSidebarExpanded) {
            sidebarToggleExpandedHost.appendChild(sidebarToggle);
            sidebarToggleCollapsedHost.classList.add('hidden');
        } else {
            sidebarToggleCollapsedHost.appendChild(sidebarToggle);
            sidebarToggleCollapsedHost.classList.remove('hidden');
        }
    }
}

function normalizeFolder(value) {
    const raw = String(value || '').trim();
    if (!raw) {
        return '';
    }

    const asSlash = raw.replace(/\\/g, '/');
    return asSlash.replace(/^\/+/, '').replace(/\/+$/, '');
}

function createFolderTreeModel(folderNames) {
    const root = {
        name: '',
        path: '',
        children: new Map(),
        expanded: true
    };

    for (const folderName of folderNames || []) {
        const normalized = normalizeFolder(folderName);
        if (!normalized) {
            continue;
        }

        const parts = normalized.split('/').filter(Boolean);
        let current = root;
        let currentPath = '';

        for (const part of parts) {
            currentPath = currentPath ? currentPath + '/' + part : part;
            if (!current.children.has(part)) {
                current.children.set(part, {
                    name: part,
                    path: currentPath,
                    children: new Map(),
                    expanded: true
                });
            }
            current = current.children.get(part);
        }
    }

    return root;
}

function getCaseFolders() {
    const set = new Set();
    for (const testCase of allTestCases) {
        const normalized = normalizeFolder(testCase?.folder || '');
        if (normalized) {
            set.add(normalized);
        }
    }
    return Array.from(set);
}

function collectFolderPaths(node, output) {
    if (!node || !node.children) {
        return;
    }

    for (const child of node.children.values()) {
        if (child.path) {
            output.push(child.path);
        }
        collectFolderPaths(child, output);
    }
}

function listKnownFolders() {
    const folders = new Set(getCaseFolders());
    const fromTree = [];
    collectFolderPaths(folderTreeModel, fromTree);
    for (const folder of fromTree) {
        folders.add(folder);
    }

    return Array.from(folders).sort((a, b) => a.localeCompare(b));
}

function setDropCursor(isValid) {
    document.body.style.cursor = isValid ? 'copy' : 'not-allowed';
}

function clearDropCursor() {
    document.body.style.cursor = '';
}

function clearDropHover() {
    if (activeDropTargetEl) {
        activeDropTargetEl.classList.remove('ws-folder-drop-hover');
        activeDropTargetEl = null;
    }
}

function setDropHover(targetEl) {
    if (activeDropTargetEl === targetEl) {
        return;
    }

    clearDropHover();
    if (targetEl) {
        targetEl.classList.add('ws-folder-drop-hover');
        activeDropTargetEl = targetEl;
    }
}

function getValidDropTarget(event) {
    const candidate = event.target?.closest?.('[data-drop-target="folder"]');
    if (!candidate || !folderTree || !folderTree.contains(candidate)) {
        return null;
    }

    return candidate;
}

function syncRowSelectionUI() {
    if (!tbody) {
        return;
    }

    selection.bindRowCheckboxes(tbody);
    const rows = tbody.querySelectorAll('tr[data-work-key]');
    rows.forEach((row) => {
        const workKey = row.dataset.workKey || '';
        const isSelected = selection.isSelected(workKey);
        row.classList.toggle('ws-row-selected', isSelected);
        row.classList.toggle('bg-[#E7FF02]/10', isSelected);
    });
}

function isInteractiveTarget(target) {
    return Boolean(target?.closest?.('.ws-interactive, a, button, input, select, textarea, label'));
}

function createDragImage(workKeys) {
    const holder = document.createElement('div');
    holder.className = 'fixed -left-[9999px] -top-[9999px] pointer-events-none';

    const chip = document.createElement('div');
    chip.style.background = 'rgba(0, 0, 0, 0.95)';
    chip.style.border = '1px solid rgba(231, 255, 2, 0.5)';
    chip.style.color = '#ffffff';
    chip.style.padding = '8px 10px';
    chip.style.fontSize = '12px';
    chip.style.display = 'inline-flex';
    chip.style.alignItems = 'center';
    chip.style.gap = '8px';

    if (workKeys.length === 1) {
        chip.textContent = workKeys[0];
    } else {
        const dots = document.createElement('span');
        dots.textContent = '::';
        dots.style.opacity = '0.8';

        const label = document.createElement('span');
        label.textContent = 'Move selected';

        const badge = document.createElement('span');
        badge.textContent = String(workKeys.length);
        badge.style.background = '#E7FF02';
        badge.style.color = '#000000';
        badge.style.fontWeight = '700';
        badge.style.padding = '1px 6px';
        badge.style.borderRadius = '10px';

        chip.appendChild(dots);
        chip.appendChild(label);
        chip.appendChild(badge);
    }

    holder.appendChild(chip);
    document.body.appendChild(holder);
    return { holder, chip };
}

function updatePaginationUi() {
    if (pageInfo) {
        const safeTotalPages = Math.max(totalPages, 1);
        pageInfo.textContent = 'Page ' + (currentPage + 1) + ' of ' + safeTotalPages;
    }
    if (prevPageBtn) prevPageBtn.disabled = currentPage <= 0;
    if (nextPageBtn) nextPageBtn.disabled = !hasNextPage;
}

function debounce(fn, waitMs) {
    let timeoutId = null;
    return (...args) => {
        if (timeoutId !== null) window.clearTimeout(timeoutId);
        timeoutId = window.setTimeout(() => {
            timeoutId = null;
            fn(...args);
        }, waitMs);
    };
}

function buildFilterParams() {
    const params = new URLSearchParams();
    if (searchInput && searchInput.value.trim()) params.set('search', searchInput.value.trim());
    if (filterComponent && filterComponent.value.trim()) params.set('component', filterComponent.value.trim());
    if (filterStatus && filterStatus.value.trim()) params.set('status', filterStatus.value.trim());
    if (filterTag && filterTag.value.trim()) params.set('tag', filterTag.value.trim());
    if (selectedFolder) params.set('folder', selectedFolder);
    params.set('page', String(currentPage));
    params.set('size', String(pageSize));
    return params;
}

function renderPage(testCases) {
    const list = Array.isArray(testCases) ? testCases : [];
    allTestCases = list; // Update our local cache for the drag/drop mock submit
    
    if (totalCount) {
        totalCount.textContent = String(totalCount.dataset.serverTotal || list.length);
    }
    grid.renderRows(list, new Set(selection.getSelectedIds()));
    selection.setVisibleIds(list.map((testCase) => testCase.workKey).filter(Boolean));
    syncRowSelectionUI(); // Kept from your drag/drop branch!
}

function loadTestCases(resetPage = false) {
    if (resetPage) currentPage = 0;
    const queryString = buildFilterParams().toString();
    const url = queryString ? apiBaseUrl + '?' + queryString : apiBaseUrl;

    fetch(url)
        .then((response) => {
            if (!response.ok) throw new Error('Test cases failed with status ' + response.status);
            
            const totalCountHeader = Number(response.headers.get('X-Total-Count'));
            const totalPagesHeader = Number(response.headers.get('X-Total-Pages'));
            const pageHeader = Number(response.headers.get('X-Page'));
            const pageSizeHeader = Number(response.headers.get('X-Page-Size'));
            const hasNextHeader = String(response.headers.get('X-Has-Next')).toLowerCase() === 'true';

            if (!Number.isNaN(totalCountHeader) && totalCount) totalCount.dataset.serverTotal = String(totalCountHeader);
            if (!Number.isNaN(totalPagesHeader)) totalPages = totalPagesHeader;
            if (!Number.isNaN(pageHeader)) currentPage = pageHeader;
            if (!Number.isNaN(pageSizeHeader) && pageSizeHeader > 0) pageSize = pageSizeHeader;
            hasNextPage = hasNextHeader;
            updatePaginationUi();

            return response.json();
        })
        .then((data) => renderPage(data))
        .catch((error) => {
            console.error('Failed to load test cases', error);
            if (totalCount) totalCount.dataset.serverTotal = '0';
            totalPages = 0;
            hasNextPage = false;
            updatePaginationUi();
            renderPage([]);
        });
}

function applyFilters() {
    loadTestCases(true);
}

const debouncedApplyFilters = debounce(applyFilters, 300);

function fetchOptionValues(url) {
    return fetch(url).then(r => r.ok ? r.json() : []).then(data => Array.isArray(data) ? data.map(v => String(v || '').trim()).filter(Boolean).sort((a,b) => a.localeCompare(b, undefined, {sensitivity: 'base'})) : []).catch(() => []);
}

function populateSelect(selectEl, values) {
    if (!selectEl) return;
    const previous = selectEl.value || '';
    selectEl.innerHTML = '<option value="">All</option>';
    for (const value of values) {
        const option = document.createElement('option');
        option.value = value;
        option.textContent = value;
        selectEl.appendChild(option);
    }
    if (previous && values.includes(previous)) selectEl.value = previous;
}

function loadFilterOptions() {
    Promise.all([
        fetchOptionValues(componentsBaseUrl),
        fetchOptionValues(statusesBaseUrl),
        fetchOptionValues(tagsBaseUrl)
    ]).then(([components, statuses, tags]) => {
        populateSelect(filterComponent, components);
        populateSelect(filterStatus, statuses);
        populateSelect(filterTag, tags);
    });
}

function renderFolderTree() {
    if (!folderTree) {
        return;
    }

    clearDropHover();
    folderTree.innerHTML = '';

    const selectedRowClasses = 'bg-[#E7FF02]/10 border-[#E7FF02] text-white';
    const unselectedRowClasses = 'border-transparent text-white/80 hover:bg-white/5 hover:border-white/10';
    const rowWrapClasses = 'select-none';
    const rowInnerBaseClasses = 'flex items-center gap-2 py-2 pl-2 pr-2 text-sm rounded-md border transition-colors w-full';
    const rowSelectButtonClasses = 'min-w-0 flex-1 flex items-center gap-2 text-left';

    const showAllWrap = document.createElement('div');
    showAllWrap.className = rowWrapClasses;
    showAllWrap.style.paddingLeft = '12px';

    const showAllInner = document.createElement('div');
    showAllInner.className = rowInnerBaseClasses + ' ' + (selectedFolder ? unselectedRowClasses : selectedRowClasses);

    const showAllButton = document.createElement('button');
    showAllButton.type = 'button';
    showAllButton.className = rowSelectButtonClasses;
    showAllButton.setAttribute('aria-label', 'Show all files');
    showAllInner.dataset.dropTarget = 'invalid';
    if (!selectedFolder) {
        showAllButton.setAttribute('aria-current', 'true');
    }
    showAllButton.innerHTML =
        '<span class="shrink-0 text-white/60">' +
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" class="w-4 h-4"><path d="M4 6h16" /><path d="M4 12h16" /><path d="M4 18h16" /></svg>' +
        '</span>' +
        '<span class="min-w-0 truncate">Show all files</span>';

    showAllButton.addEventListener('click', () => {
        if (!selectedFolder) {
            return;
        }
        selectedFolder = '';
        renderFolderTree();
        applyFilters();
    });

    showAllInner.appendChild(showAllButton);
    showAllWrap.appendChild(showAllInner);
    folderTree.appendChild(showAllWrap);

    if (!folderTreeModel || !folderTreeModel.children || folderTreeModel.children.size === 0) {
        return;
    }

    const sortedChildren = (node) => Array.from(node.children.values()).sort((a, b) => a.name.localeCompare(b.name));

    const renderNode = (node, depth) => {
        const hasChildren = node.children && node.children.size > 0;
        const isOpen = Boolean(hasChildren && node.expanded);
        const rowWrap = document.createElement('div');
        rowWrap.className = rowWrapClasses;
        rowWrap.style.paddingLeft = String(12 + depth * 16) + 'px';

        const rowInner = document.createElement('div');
        const isSelected = selectedFolder === node.path;
        rowInner.className = rowInnerBaseClasses + ' ' + (isSelected ? selectedRowClasses : unselectedRowClasses);
        rowInner.dataset.dropTarget = 'folder';
        rowInner.dataset.folderPath = node.path;

        let toggle;
        if (hasChildren) {
            toggle = document.createElement('button');
            toggle.type = 'button';
            toggle.className = 'shrink-0 w-5 h-5 flex items-center justify-center text-white/45 hover:text-white/80 transition-colors';
            toggle.setAttribute('aria-label', node.expanded ? 'Collapse folder' : 'Expand folder');
            toggle.setAttribute('aria-expanded', String(Boolean(node.expanded)));
            toggle.innerHTML = node.expanded
                ? '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="w-4 h-4"><path d="M6 9l6 6 6-6" /></svg>'
                : '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="w-4 h-4"><path d="M9 6l6 6-6 6" /></svg>';

            toggle.addEventListener('click', (event) => {
                event.preventDefault();
                event.stopPropagation();
                node.expanded = !node.expanded;
                renderFolderTree();
            });
        } else {
            toggle = document.createElement('span');
            toggle.className = 'shrink-0 w-5 h-5 flex items-center justify-center';
            toggle.innerHTML = '<span class="block w-4 h-4"></span>';
        }

        const selectButton = document.createElement('button');
        selectButton.type = 'button';
        selectButton.className = rowSelectButtonClasses;
        selectButton.setAttribute('aria-label', 'Filter by folder ' + node.path);
        if (isSelected) {
            selectButton.setAttribute('aria-current', 'true');
        }

        const iconHtml = isOpen
            ? '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" class="w-4 h-4"><path d="M3 8a2 2 0 0 1 2-2h4l2 2h9a2 2 0 0 1 2 2v1" /><path d="M3 11h18l-1.5 8.5a2 2 0 0 1-2 1.5H6.5a2 2 0 0 1-2-1.5L3 11z" /></svg>'
            : '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" class="w-4 h-4"><path d="M3 7a2 2 0 0 1 2-2h5l2 2h9a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7z" /></svg>';

        const iconSpan = document.createElement('span');
        iconSpan.className = 'shrink-0 text-white/60';
        iconSpan.innerHTML = iconHtml;

        const labelSpan = document.createElement('span');
        labelSpan.className = 'min-w-0 truncate';
        labelSpan.textContent = node.name;

        selectButton.appendChild(iconSpan);
        selectButton.appendChild(labelSpan);
        selectButton.addEventListener('click', () => {
            const next = selectedFolder === node.path ? '' : node.path;
            selectedFolder = next;
            renderFolderTree();
            applyFilters();
        });

        rowInner.appendChild(toggle);
        rowInner.appendChild(selectButton);
        rowWrap.appendChild(rowInner);
        folderTree.appendChild(rowWrap);

        if (hasChildren && node.expanded) {
            for (const child of sortedChildren(node)) {
                renderNode(child, depth + 1);
            }
        }
    };

    for (const child of sortedChildren(folderTreeModel)) {
        renderNode(child, 0);
    }
}

function populateOrganizeFolderOptions(selectedValue) {
    if (!organizeFolderSelect) {
        return;
    }

    const folders = listKnownFolders();
    organizeFolderSelect.innerHTML = '';

    const placeholder = document.createElement('option');
    placeholder.value = '';
    placeholder.textContent = 'Select a folder';
    organizeFolderSelect.appendChild(placeholder);

    for (const folder of folders) {
        const option = document.createElement('option');
        option.value = folder;
        option.textContent = folder;
        organizeFolderSelect.appendChild(option);
    }

    const normalizedSelected = normalizeFolder(selectedValue || '');
    if (normalizedSelected && folders.includes(normalizedSelected)) {
        organizeFolderSelect.value = normalizedSelected;
    } else {
        organizeFolderSelect.value = '';
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

function handleBulkMove(input) {
    const moveInput = input || {};
    const workKeys = Array.isArray(moveInput.workKeys) ? moveInput.workKeys : [];
    const source = moveInput.source || 'unknown';
    const targetFolder = normalizeFolder(moveInput.targetFolder || '');

    if (!targetFolder) {
        return false;
    }

    const uniqueKeys = Array.from(new Set(workKeys.filter(Boolean)));
    if (uniqueKeys.length === 0) {
        return false;
    }

    console.log({
        workKeys: uniqueKeys,
        targetFolder,
        source
    });

    const moveSet = new Set(uniqueKeys);
    allTestCases = allTestCases.map((testCase) => {
        if (!moveSet.has(testCase.workKey)) {
            return testCase;
        }

        return {
            ...testCase,
            folder: targetFolder
        };
    });

    selection.removeSelectedIds(uniqueKeys);
    folderTreeModel = createFolderTreeModel(getCaseFolders());
    renderFolderTree();
    applyFilters();
    return true;
}

function loadFolders() {
    if (folderLoading) {
        folderLoading.classList.remove('hidden');
    }
    if (folderEmpty) {
        folderEmpty.classList.add('hidden');
        folderEmpty.textContent = 'No folders found.';
    }

    fetch(foldersBaseUrl)
        .then((response) => {
            if (!response.ok) {
                throw new Error('Folders failed with status ' + response.status);
            }
            return response.json();
        })
        .then((data) => {
            const folders = Array.isArray(data) ? data : [];
            const merged = Array.from(new Set([...
                folders.map((item) => normalizeFolder(item)).filter(Boolean),
                ...getCaseFolders()
            ]));
            folderTreeModel = createFolderTreeModel(merged);

            if (folderLoading) {
                folderLoading.classList.add('hidden');
            }

            const hasAny = folderTreeModel && folderTreeModel.children && folderTreeModel.children.size > 0;
            if (folderEmpty) {
                folderEmpty.classList.toggle('hidden', hasAny);
            }

            renderFolderTree();
        })
        .catch((error) => {
            console.error('Failed to load folders', error);
            folderTreeModel = createFolderTreeModel(getCaseFolders());
            if (folderLoading) {
                folderLoading.classList.add('hidden');
            }
            if (folderEmpty) {
                const hasAny = folderTreeModel && folderTreeModel.children && folderTreeModel.children.size > 0;
                folderEmpty.classList.toggle('hidden', hasAny);
                folderEmpty.textContent = hasAny ? 'No folders found.' : 'Folders unavailable.';
            }
            renderFolderTree();
        });
}

function clearImportNotice() {
    if (!importNoticeContainer) {
        return;
    }

    importNoticeContainer.classList.add('hidden');
    if (importNoticeMessage) {
        importNoticeMessage.textContent = '';
    }
}

function showImportNotice(type, message) {
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

if (sidebarToggle) {
    sidebarToggle.addEventListener('click', () => {
        setSidebarExpanded(!isSidebarExpanded);
    });
}

selection.setSelectionChangeHandler((selectedIds) => {
    exportSelected.updateButtonState(selectedIds);
    syncRowSelectionUI();
});

if (importNoticeClose) {
    importNoticeClose.addEventListener('click', clearImportNotice);
}

if (tbody) {
    tbody.addEventListener('change', (event) => {
        const target = event.target;
        if (!target?.classList?.contains('ws-row-check')) {
            return;
        }

        const workKey = target.dataset.workKey || '';
        selection.setSelected(workKey, target.checked);
        syncRowSelectionUI();
    });

    tbody.addEventListener('click', (event) => {
        const actionButton = event.target?.closest?.('.ws-row-action');
        if (actionButton) {
            const workKey = actionButton.dataset.workKey || actionButton.closest('[data-work-key]')?.dataset.workKey || '';
            if (!workKey) {
                return;
            }

            const action = actionButton.dataset.action;
            if (action === 'preview') {
                drawer.openByWorkKey(workKey, { readOnly: true });
            } else {
                drawer.openByWorkKey(workKey, { readOnly: false });
            }
            return;
        }

        if (isInteractiveTarget(event.target)) {
            return;
        }

        const row = event.target?.closest?.('tr[data-work-key]');
        if (!row) {
            return;
        }

        const workKey = row.dataset.workKey || '';
        if (!workKey) {
            return;
        }

        selection.setSelected(workKey, !selection.isSelected(workKey));
        syncRowSelectionUI();
    });

    tbody.addEventListener('dragstart', (event) => {
        const grabHandle = event.target?.closest?.('.ws-row-grab');
        if (!grabHandle) {
            event.preventDefault();
            return;
        }

        const workKey = grabHandle.dataset.workKey || grabHandle.closest('tr[data-work-key]')?.dataset.workKey || '';
        if (!workKey || !event.dataTransfer) {
            event.preventDefault();
            return;
        }

        const selectedIds = selection.getSelectedIds();
        const workKeys = selection.isSelected(workKey)
            ? selectedIds.filter(Boolean)
            : [workKey];

        activeDragPayload = { workKeys: Array.from(new Set(workKeys)) };
        event.dataTransfer.effectAllowed = 'copyMove';
        event.dataTransfer.setData('application/x-work-keys', JSON.stringify(activeDragPayload.workKeys));
        event.dataTransfer.setData('text/plain', activeDragPayload.workKeys.join(','));

        const dragImage = createDragImage(activeDragPayload.workKeys);
        event.dataTransfer.setDragImage(dragImage.chip, 12, 12);
        window.setTimeout(() => {
            dragImage.holder.remove();
        }, 0);
    });

    tbody.addEventListener('dragend', () => {
        activeDragPayload = null;
        clearDropHover();
        clearDropCursor();
    });
}

if (folderTree) {
    folderTree.addEventListener('dragenter', (event) => {
        if (!activeDragPayload) {
            return;
        }

        const targetEl = getValidDropTarget(event);
        if (!targetEl) {
            if (event.dataTransfer) {
                event.dataTransfer.dropEffect = 'none';
            }
            setDropCursor(false);
            clearDropHover();
            return;
        }

        event.preventDefault();
        if (event.dataTransfer) {
            event.dataTransfer.dropEffect = 'copy';
        }
        setDropCursor(true);
        setDropHover(targetEl);
    });

    folderTree.addEventListener('dragover', (event) => {
        if (!activeDragPayload) {
            return;
        }

        const targetEl = getValidDropTarget(event);
        if (!targetEl) {
            if (event.dataTransfer) {
                event.dataTransfer.dropEffect = 'none';
            }
            setDropCursor(false);
            clearDropHover();
            return;
        }

        event.preventDefault();
        if (event.dataTransfer) {
            event.dataTransfer.dropEffect = 'copy';
        }
        setDropCursor(true);
        setDropHover(targetEl);
    });

    folderTree.addEventListener('dragleave', (event) => {
        if (!activeDragPayload) {
            return;
        }

        const next = event.relatedTarget;
        if (!next || !folderTree.contains(next)) {
            clearDropHover();
            clearDropCursor();
        }
    });

    folderTree.addEventListener('drop', (event) => {
        if (!activeDragPayload) {
            return;
        }

        event.preventDefault();
        const targetEl = getValidDropTarget(event);
        if (targetEl) {
            const targetFolder = targetEl.dataset.folderPath || '';
            handleBulkMove({
                workKeys: activeDragPayload.workKeys,
                targetFolder,
                source: 'drag-drop'
            });
        }

        activeDragPayload = null;
        clearDropHover();
        clearDropCursor();
    });
}

if (searchInput) {
    searchInput.addEventListener('input', debouncedApplyFilters);
}
if (filterComponent) {
    filterComponent.addEventListener('change', applyFilters);
}
if (filterStatus) {
    filterStatus.addEventListener('change', applyFilters);
}
if (filterTag) {
    filterTag.addEventListener('change', applyFilters);
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
        organizeFolderInput.value = organizeFolderSelect.value;
        if (organizeError) {
            organizeError.classList.add('hidden');
        }
    });

    organizeFolderInput.addEventListener('input', () => {
        const normalized = normalizeFolder(organizeFolderInput.value || '');
        if (normalizeFolder(organizeFolderSelect.value || '') === normalized) {
            return;
        }

        const options = Array.from(organizeFolderSelect.options).map((option) => option.value);
        organizeFolderSelect.value = options.includes(normalized) ? normalized : '';
        if (organizeError) {
            organizeError.classList.add('hidden');
        }
    });
}

if (organizeSave) {
    organizeSave.addEventListener('click', () => {
        const selectedIds = selection.getSelectedIds();
        const fromInput = organizeFolderInput ? organizeFolderInput.value : '';
        const fromSelect = organizeFolderSelect ? organizeFolderSelect.value : '';
        const targetFolder = normalizeFolder(fromInput || fromSelect);

        if (!targetFolder) {
            if (organizeError) {
                organizeError.classList.remove('hidden');
                organizeError.textContent = 'A target folder is required.';
            }
            return;
        }

        const moved = handleBulkMove({
            workKeys: selectedIds,
            targetFolder,
            source: 'organize-modal'
        });

        if (moved) {
            closeOrganizeModal();
        }
    });
}

if (prevPageBtn) {
    prevPageBtn.addEventListener('click', () => {
        if (currentPage <= 0) {
            return;
        }
        currentPage -= 1;
        loadTestCases(false);
    });
}

if (nextPageBtn) {
    nextPageBtn.addEventListener('click', () => {
        if (!hasNextPage) {
            return;
        }
        currentPage += 1;
        loadTestCases(false);
    });
}

if (importBtn && importFile) {
    importBtn.addEventListener('click', () => {
        importFile.click();
    });

    importFile.addEventListener('change', () => {
        if (!importFile.files || importFile.files.length === 0) {
            return;
        }

        clearImportNotice();

        const formData = new FormData();
        formData.append('file', importFile.files[0]);

        const csrfInput = importForm ? importForm.querySelector('input[type="hidden"]') : null;
        if (csrfInput && csrfInput.name && csrfInput.value) {
            formData.append(csrfInput.name, csrfInput.value);
        }

        importBtn.textContent = 'Importing...';
        importBtn.disabled = true;

        fetch('/api/upload', { method: 'POST', body: formData })
            .then(async (response) => {
                if (response.ok) {
                    return response.json();
                }

                let detail = '';
                try {
                    const contentType = response.headers.get('content-type') || '';
                    if (contentType.includes('application/json')) {
                        const json = await response.json();
                        detail = json?.message || (Array.isArray(json?.errors) ? json.errors.join('\n') : '');
                    } else {
                        detail = await response.text();
                    }
                } catch (e) {
                    // Ignore parse failures and use status message.
                }

                const suffix = detail ? ': ' + String(detail).trim() : '';
                throw new Error('Upload failed with status ' + response.status + suffix);
            })
            .then((json) => {
                importBtn.textContent = 'Import';
                importBtn.disabled = false;
                importFile.value = '';

                if (json.reviewRequired && json.reviewUrl) {
                    window.location.href = json.reviewUrl;
                    return;
                }

                if (json.exactDuplicateFile) {
                    showImportNotice('error', json.message || 'This exact file was already uploaded.');
                    return;
                }

                if (json.errors && json.errors.length > 0) {
                    const header = json.message ? String(json.message).trim() : 'Import failed.';
                    showImportNotice('error', header + '\n' + json.errors.join('\n'));
                } else if (json.message) {
                    showImportNotice('success', json.message);
                }

                loadTestCases(true);
                loadFilterOptions();
                loadFolders();
            })
            .catch((error) => {
                console.error('Upload failed', error);
                const message = error && error.message ? String(error.message) : 'Import failed. Please try again.';
                showImportNotice('error', message);
                importBtn.textContent = 'Import';
                importBtn.disabled = false;
            });
    });
}

updatePaginationUi();
loadFilterOptions();
loadTestCases(true);
loadFolders();