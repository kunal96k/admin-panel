// Global variables
let currentPage = 0;
let pageSize = 25;
let totalPages = 0;
let totalElements = 0;
let selectedCertificateId = null;
let importedCertData = [];
let allCourses = [];
let manualLogsPage = 0;
let manualLogsSize = 25;
let manualLogsTotalPages = 0;
// CSRF Token handling
let csrfToken = null;
let csrfHeader = null;

// Initialize on page load
document.addEventListener('DOMContentLoaded', function () {
    initializeEventListeners();
    updateStats();
    loadCourses().then(() => {
        initCourseTypeahead();
        initFilterToggle();
        initStatsToggle();
        initOpsToggle();
        updateFilterBadge();
    });
    loadCertificates();
    updateStatsForCurrentView();
    initializeCsrfToken();
});

// Initialize CSRF token from cookie
function initializeCsrfToken() {
    const token = getCookie('XSRF-TOKEN');
    if (token) {
        csrfToken = token;
        csrfHeader = 'X-CSRF-TOKEN';
    } else {
        console.warn('CSRF token not found in cookies');
    }
}

// Get cookie by name
function getCookie(name) {
    const value = `; ${document.cookie}`;
    const parts = value.split(`; ${name}=`);
    if (parts.length === 2) {
        return parts.pop().split(';').shift();
    }
    return null;
}

async function updateStatsForCurrentView() {
    const course = document.getElementById('courseFilter')?.value || '';
    const status = document.getElementById('statusFilter')?.value || '';
    const search = document.getElementById('searchInput')?.value || '';
    const fromDate = document.getElementById('certFromDate')?.value || '';
    const toDate   = document.getElementById('certToDate')?.value || '';

    try {
        const params = new URLSearchParams({
            ...(course   && { course }),
            ...(status   && { status }),
            ...(search   && { search }),
            ...(fromDate && { fromDate }),
            ...(toDate   && { toDate })
        });

        // Refresh CSRF token before request
        initializeCsrfToken();

        const response = await fetch(`/api/certificates/stats-filtered?${params}`, {
            headers: {
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            }
        });

        if (!response.ok) throw new Error('Failed to load filtered stats');

        const stats = await response.json();

        const totalEl = document.getElementById('totalCount');
        const issuedEl = document.getElementById('issuedCount');
        const pendingEl = document.getElementById('pendingCount');

        if (totalEl) totalEl.textContent = stats.total || 0;
        if (issuedEl) issuedEl.textContent = stats.issued || 0;
        if (pendingEl) pendingEl.textContent = stats.pending || 0;
    } catch (error) {
        console.error('Error loading filtered stats:', error);
    }
}

async function autoGenerateCertificates() {
    openStudentSelectModal();
}


function initializeEventListeners() {

    document.getElementById('btnAutoGenerate')?.addEventListener('click', autoGenerateCertificates);

    // Filter changes
    document.getElementById('courseFilter')?.addEventListener('change', () => {
        currentPage = 0;
        loadCertificates();
        updateStatsForCurrentView();
        updateFilterBadge();
    });

    document.getElementById('statusFilter')?.addEventListener('change', () => {
        currentPage = 0;
        loadCertificates();
        updateStatsForCurrentView();
        updateFilterBadge();
    });

    // Date filter listeners (trigger on change and input with console logs)
    const onDateFilterChange = () => {
        const fromVal = document.getElementById('certFromDate')?.value || '';
        const toVal = document.getElementById('certToDate')?.value || '';
        console.log(`[Date Filter] certFromDate: "${fromVal}", certToDate: "${toVal}"`);
        currentPage = 0;
        loadCertificates();
        updateStatsForCurrentView();
        updateFilterBadge();
    };

    document.getElementById('certFromDate')?.addEventListener('change', onDateFilterChange);
    document.getElementById('certFromDate')?.addEventListener('input', onDateFilterChange);
    document.getElementById('certToDate')?.addEventListener('change', onDateFilterChange);
    document.getElementById('certToDate')?.addEventListener('input', onDateFilterChange);

    document.getElementById('pageSizeSelect')?.addEventListener('change', function () {
        pageSize = parseInt(this.value);
        currentPage = 0;
        loadCertificates();
        updateStatsForCurrentView();
    });

    document.getElementById('btnClearFilters')?.addEventListener('click', () => {
        clearAllFilters();
    });

    // Search with debounce
    let searchTimeout;
    document.getElementById('searchInput')?.addEventListener('input', function () {
        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(() => {
            currentPage = 0;
            loadCertificates();
            updateStatsForCurrentView();
        }, 500);
    });

    // Save certificate
    document.getElementById('btnSaveCertificate')?.addEventListener('click', saveCertificate);

    // Set default issue date to today
    const issueDateEl = document.getElementById('issueDate');
    if (issueDateEl) {
        issueDateEl.valueAsDate = new Date();
    }

    // Action menu toggle
    document.addEventListener('click', (e) => {
        if (!e.target.closest('.action-dropdown')) {
            document.querySelectorAll('.action-menu').forEach(menu => {
                menu.classList.remove('show');
            });
        }
    });

    // Import/Export CSV
    const btnImportCSV = document.getElementById('btnImportCSV');
    const btnExportCSV = document.getElementById('btnExportCSV');
    const btnBrowseCertFile = document.getElementById('btnBrowseCertFile');
    const csvCertFileInput = document.getElementById('csvCertFileInput');
    const importCertBtn = document.getElementById('importCertBtn');

    if (btnImportCSV) {
        btnImportCSV.addEventListener('click', openImportCertModal);
    }

    if (btnExportCSV) {
        btnExportCSV.addEventListener('click', exportCertificatesCSV);
    }

    if (btnBrowseCertFile) {
        btnBrowseCertFile.addEventListener('click', () => {
            document.getElementById('csvCertFileInput')?.click();
        });
    }

    if (csvCertFileInput) {
        csvCertFileInput.addEventListener('change', function (e) {
            handleCertCSVFile(e.target.files[0]);
        });
    }

    if (importCertBtn) {
        importCertBtn.addEventListener('click', importCertificatesCSV);
    }

    // Drag and drop
    const importCertArea = document.getElementById('importCertArea');
    if (importCertArea) {
        importCertArea.addEventListener('dragover', (e) => {
            e.preventDefault();
            importCertArea.classList.add('dragover');
        });

        importCertArea.addEventListener('dragleave', () => {
            importCertArea.classList.remove('dragover');
        });

        importCertArea.addEventListener('drop', (e) => {
            e.preventDefault();
            importCertArea.classList.remove('dragover');
            const file = e.dataTransfer.files[0];
            if (file?.name.endsWith('.csv')) {
                handleCertCSVFile(file);
            } else {
                Swal.fire({
                    title: 'Error',
                    text: 'Please upload a valid CSV file.',
                    icon: 'error',
                    confirmButtonColor: '#667eea'
                });
            }
        });
    }
}

/* ── Course Typeahead Chip Widget ── */
let selectedCourseChips = [];

function syncCourseFilterSelect() {
    const select = document.getElementById('courseFilter');
    if (!select) return;
    const val = selectedCourseChips[0] || '';
    select.value = val;
    select.dispatchEvent(new Event('change', { bubbles: true }));
}

function renderCourseChips() {
    const wrap = document.getElementById('courseChipsWrap');
    const input = document.getElementById('courseSearchInputFilter');
    if (!wrap || !input) return;
    wrap.querySelectorAll('.course-chip').forEach(el => el.remove());
    selectedCourseChips.forEach(name => {
        const chip = document.createElement('span');
        chip.className = 'course-chip';
        chip.innerHTML = `<span class="course-chip-name" title="${name}">${name}</span>
            <button type="button" class="course-chip-close" aria-label="Remove ${name}" data-course="${name}">
                <i class="bi bi-x"></i>
            </button>`;
        chip.querySelector('.course-chip-close').addEventListener('click', (e) => {
            e.stopPropagation();
            selectedCourseChips = selectedCourseChips.filter(n => n !== name);
            renderCourseChips();
            syncCourseFilterSelect();
            updateFilterBadge();
        });
        wrap.insertBefore(chip, input);
    });
}

function buildCourseDropdown(term) {
    const dd = document.getElementById('courseDropdownFilter');
    if (!dd) return;
    const filtered = (allCourses || []).filter(c =>
        String(c.courseName || '').toLowerCase().includes((term || '').toLowerCase().trim())
    );
    dd.innerHTML = '';
    if (filtered.length === 0) {
        dd.innerHTML = '<li class="dd-empty">No courses found</li>';
    } else {
        filtered.forEach(c => {
            const isSelected = selectedCourseChips.includes(c.courseName);
            const li = document.createElement('li');
            li.setAttribute('role', 'option');
            li.setAttribute('data-course', c.courseName);
            if (isSelected) li.classList.add('already-selected');
            li.innerHTML = `<i class="bi bi-mortarboard" style="font-size:0.78rem;color:#94a3b8;"></i>
                <span>${c.courseName}</span>
                ${isSelected ? '<i class="bi bi-check2 course-dd-check"></i>' : ''}`;
            li.addEventListener('mousedown', (e) => {
                e.preventDefault();
                if (!isSelected) {
                    selectedCourseChips = [c.courseName];
                    renderCourseChips();
                    syncCourseFilterSelect();
                    closeCourseDropdown();
                    document.getElementById('courseSearchInputFilter').value = '';
                }
            });
            dd.appendChild(li);
        });
    }
}

function openCourseDropdown(term) {
    buildCourseDropdown(term);
    document.getElementById('courseDropdownFilter')?.classList.add('open');
}

function closeCourseDropdown() {
    document.getElementById('courseDropdownFilter')?.classList.remove('open');
}

function filterCourseList() {
    const term = document.getElementById('courseSearchInputFilter')?.value || '';
    openCourseDropdown(term);
}

function initCourseTypeahead() {
    const input  = document.getElementById('courseSearchInputFilter');
    const dd     = document.getElementById('courseDropdownFilter');
    const widget = document.getElementById('courseChipsInput');
    if (!input || !dd) return;

    widget?.addEventListener('click', () => input.focus());
    input.addEventListener('focus', () => openCourseDropdown(input.value));
    input.addEventListener('input', () => openCourseDropdown(input.value));

    input.addEventListener('keydown', (e) => {
        const items = [...dd.querySelectorAll('li:not(.already-selected):not(.dd-empty)')];
        const highlighted = dd.querySelector('li.highlighted');
        let idx = items.indexOf(highlighted);

        if (e.key === 'ArrowDown') {
            e.preventDefault();
            if (highlighted) highlighted.classList.remove('highlighted');
            idx = (idx + 1) % items.length;
            items[idx]?.classList.add('highlighted');
            items[idx]?.scrollIntoView({ block: 'nearest' });
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            if (highlighted) highlighted.classList.remove('highlighted');
            idx = (idx - 1 + items.length) % items.length;
            items[idx]?.classList.add('highlighted');
            items[idx]?.scrollIntoView({ block: 'nearest' });
        } else if (e.key === 'Enter') {
            e.preventDefault();
            if (highlighted) highlighted.dispatchEvent(new MouseEvent('mousedown'));
        } else if (e.key === 'Escape') {
            closeCourseDropdown();
            input.blur();
        } else if (e.key === 'Backspace' && input.value === '' && selectedCourseChips.length) {
            selectedCourseChips.pop();
            renderCourseChips();
            syncCourseFilterSelect();
            updateFilterBadge();
        }
    });

    document.addEventListener('mousedown', (e) => {
        if (!document.getElementById('courseTypeahead')?.contains(e.target)) {
            closeCourseDropdown();
        }
    });
}

// ── Filter Badge Counter ──
function updateFilterBadge() {
    let count = 0;
    if (document.getElementById('statusFilter')?.value)       count++;
    if (document.getElementById('certFromDate')?.value)       count++;
    if (document.getElementById('certToDate')?.value)         count++;
    if (selectedCourseChips && selectedCourseChips.length > 0) count++;

    const badge = document.getElementById('filterBadge');
    const btn   = document.getElementById('btnToggleFilters');
    if (badge) {
        badge.textContent = count;
        badge.classList.toggle('d-none', count === 0);
    }
    if (btn) {
        btn.classList.toggle('btn-outline-secondary', count === 0);
        btn.classList.toggle('btn-primary',           count > 0);
    }
}

// ── Filter Panel Toggle ──
function initFilterToggle() {
    const btn    = document.getElementById('btnToggleFilters');
    const panel  = document.getElementById('advancedFiltersPanel');
    if (!btn || !panel) return;

    let isAnimating = false;
    const bsCollapse = new bootstrap.Collapse(panel, { toggle: false });

    panel.addEventListener('shown.bs.collapse',  () => { isAnimating = false; btn.setAttribute('aria-expanded','true');  btn.classList.add('filter-btn-open'); });
    panel.addEventListener('hidden.bs.collapse', () => { isAnimating = false; btn.setAttribute('aria-expanded','false'); btn.classList.remove('filter-btn-open'); });
    panel.addEventListener('show.bs.collapse',   () => { isAnimating = true; });
    panel.addEventListener('hide.bs.collapse',   () => { isAnimating = true; });

    btn.addEventListener('click', () => {
        if (isAnimating) return;
        bsCollapse.toggle();
    });
}

// ── Stats Collapse Panel Toggle ──
function initStatsToggle() {
    const btn    = document.getElementById('btnToggleStats');
    const panel  = document.getElementById('statsCardsPanel');
    if (!btn || !panel) return;

    let isAnimating = false;
    const bsCollapse = new bootstrap.Collapse(panel, { toggle: false });
    
    // Add open class initially since it starts shown
    btn.classList.add('filter-btn-open');

    panel.addEventListener('shown.bs.collapse',  () => { isAnimating = false; btn.setAttribute('aria-expanded','true');  btn.classList.add('filter-btn-open'); });
    panel.addEventListener('hidden.bs.collapse', () => { isAnimating = false; btn.setAttribute('aria-expanded','false'); btn.classList.remove('filter-btn-open'); });
    panel.addEventListener('show.bs.collapse',   () => { isAnimating = true; });
    panel.addEventListener('hide.bs.collapse',   () => { isAnimating = true; });

    btn.addEventListener('click', () => {
        if (isAnimating) return;
        bsCollapse.toggle();
    });
}

// ── Operations Collapse Panel Toggle ──
function initOpsToggle() {
    const btn    = document.getElementById('btnToggleOps');
    const panel  = document.getElementById('operationsCardsPanel');
    if (!btn || !panel) return;

    let isAnimating = false;
    const bsCollapse = new bootstrap.Collapse(panel, { toggle: false });
    
    // Add open class initially since it starts shown
    btn.classList.add('filter-btn-open');

    panel.addEventListener('shown.bs.collapse',  () => { isAnimating = false; btn.setAttribute('aria-expanded','true');  btn.classList.add('filter-btn-open'); });
    panel.addEventListener('hidden.bs.collapse', () => { isAnimating = false; btn.setAttribute('aria-expanded','false'); btn.classList.remove('filter-btn-open'); });
    panel.addEventListener('show.bs.collapse',   () => { isAnimating = true; });
    panel.addEventListener('hide.bs.collapse',   () => { isAnimating = true; });

    btn.addEventListener('click', () => {
        if (isAnimating) return;
        bsCollapse.toggle();
    });
}

function clearAllFilters() {
    if (document.getElementById('searchInput'))          document.getElementById('searchInput').value = '';
    if (document.getElementById('courseFilter'))         document.getElementById('courseFilter').value = '';
    if (document.getElementById('statusFilter'))         document.getElementById('statusFilter').value = '';
    if (document.getElementById('courseSearchInputFilter')) document.getElementById('courseSearchInputFilter').value = '';
    if (document.getElementById('certFromDate'))         document.getElementById('certFromDate').value = '';
    if (document.getElementById('certToDate'))           document.getElementById('certToDate').value = '';

    // Reset typeahead chips
    selectedCourseChips = [];
    renderCourseChips();
    closeCourseDropdown();

    currentPage = 0;
    loadCertificates();
    updateStatsForCurrentView();
    updateFilterBadge();
}

// Load courses from backend
async function loadCourses() {
    try {
        // Refresh CSRF token before request
        initializeCsrfToken();

        const response = await fetch('/api/courses?page=0&size=1000', {
            headers: {
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            }
        });

        if (!response.ok) throw new Error('Failed to load courses');

        const data = await response.json();
        allCourses = data.courses || [];

        const courseFilter = document.getElementById('courseFilter');
        if (courseFilter) {
            courseFilter.innerHTML = '<option value=""></option>';
            allCourses.forEach(course => {
                const option = document.createElement('option');
                option.value = course.courseName;
                option.textContent = course.courseName;
                courseFilter.appendChild(option);
            });
        }
    } catch (error) {
        console.error('Error loading courses:', error);
    }
}

// Load certificates from server
async function loadCertificates() {
    const tbody = document.querySelector('#certificateTable tbody');
    if (!tbody) return;

    // Show loading
    tbody.innerHTML = '<tr><td colspan="9" class="text-center py-4">Loading...</td></tr>';

    try {
        const course   = document.getElementById('courseFilter')?.value || '';
        const status   = document.getElementById('statusFilter')?.value || '';
        const search   = document.getElementById('searchInput')?.value || '';
        const fromDate = document.getElementById('certFromDate')?.value || '';
        const toDate   = document.getElementById('certToDate')?.value || '';

        const params = new URLSearchParams({
            page: currentPage,
            size: pageSize,
            ...(course   && { course }),
            ...(status   && { status }),
            ...(search   && { search }),
            ...(fromDate && { fromDate }),
            ...(toDate   && { toDate })
        });

        // Refresh CSRF token before request
        initializeCsrfToken();

        const response = await fetch(`/api/certificates?${params}`, {
            headers: {
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            }
        });

        if (!response.ok) throw new Error('Failed to load certificates');

        const data = await response.json();
        const certificates = data.certificates || [];

        if (certificates.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="9" class="text-center py-5">
                        <i class="bi bi-inbox" style="font-size: 3rem; color: #94a3b8;"></i>
                        <p class="mt-3 mb-0 text-muted">No certificates found</p>
                    </td>
                </tr>
            `;
            // FIX: reset totals so renderPagination clears stale page numbers
            totalPages = 0;
            totalElements = 0;
            updatePaginationInfo(0, 0);
            renderPagination();
            return;
        }

        tbody.innerHTML = certificates.map(cert => `
            <tr>
                <td data-label="REG NO."><strong>${cert.registrationNo}</strong></td>
                <td data-label="CERTIFICATE NO.">${cert.certificateNo || '<span class="text-muted">Not Issued</span>'}</td>
                <td data-label="STUDENT NAME">${cert.studentName}</td>
                <td data-label="COURSE"><span class="badge bg-primary">${cert.courseName}</span></td>
                <td data-label="BATCH">${cert.batch || '-'}</td>
                <td data-label="GRADE">${cert.grade || '<span class="text-muted">-</span>'}</td>
                <td data-label="ISSUE DATE">${cert.issueDate ? new Date(cert.issueDate).toLocaleDateString() : '<span class="text-muted">-</span>'}</td>
                <td data-label="STATUS">
                    <span class="badge ${cert.status === 'Issued' ? 'bg-success' : 'bg-warning'}">
                        ${cert.status}
                    </span>
                </td>
                <td data-label="ACTIONS">
                        <div class="action-dropdown">
                            <button class="action-btn action-menu-trigger">
                                <i class="bi bi-three-dots-vertical"></i>
                            </button>
                            <div class="action-menu">
                                <button class="action-menu-item" data-action="update" onclick="issueCertificate(${cert.id})">
                                    <i class="bi bi-pencil-square"></i><span>${cert.status === 'Issued' ? 'Edit Certificate' : 'Issue Certificate'}</span>
                                </button>
                                ${cert.status === 'Issued' ? `
                                <button class="action-menu-item" data-action="view" onclick="viewCertificate(${cert.id})">
                                    <i class="bi bi-eye"></i><span>View Certificate</span>
                                </button>
                                <button class="action-menu-item" data-action="changestatus" onclick="promptAndSendEmail(${cert.id}, '${cert.studentEmail || ''}')">
                                    <i class="bi bi-envelope"></i><span>Send Email</span>
                                </button>
                                <button class="action-menu-item" data-action="print" onclick="printCertificate(${cert.id})">
                                    <i class="bi bi-printer"></i><span>Print Certificate</span>
                                </button>
                                ` : ''}
                                <button class="action-menu-item" data-action="delete" onclick="deleteCertificate(${cert.id})">
                                    <i class="bi bi-trash"></i><span>Delete</span>
                                </button>
                            </div>
                        </div>
                </td>
            </tr>
        `).join('');

        // Attach menu triggers
        document.querySelectorAll('.action-menu-trigger').forEach(trigger => {
            trigger.addEventListener('click', function (e) {
                e.stopPropagation();
                if (typeof openActionMenuFixed === 'function') {
                    openActionMenuFixed(this);
                }
            });
        });

        // Update pagination
        totalPages = data.totalPages;
        totalElements = data.totalItems;
        updatePaginationInfo(certificates.length, totalElements);
        renderPagination();

    } catch (error) {
        console.error('Error loading certificates:', error);
        tbody.innerHTML = `
            <tr>
                <td colspan="9" class="text-center py-4 text-danger">
                    <i class="bi bi-exclamation-triangle"></i> Error loading certificates
                </td>
            </tr>
        `;
    }
}

// Update statistics
async function updateStats() {
    try {
        // Refresh CSRF token before request
        initializeCsrfToken();

        const response = await fetch('/api/certificates/stats', {
            headers: {
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            }
        });

        if (!response.ok) throw new Error('Failed to load stats');

        const stats = await response.json();
        const totalEl = document.getElementById('totalCount');
        const issuedEl = document.getElementById('issuedCount');
        const pendingEl = document.getElementById('pendingCount');

        if (totalEl) totalEl.textContent = stats.total || 0;
        if (issuedEl) issuedEl.textContent = stats.issued || 0;
        if (pendingEl) pendingEl.textContent = stats.pending || 0;
    } catch (error) {
        console.error('Error loading stats:', error);
    }
}

// Issue certificate
async function issueCertificate(id) {
    document.querySelectorAll('.action-menu').forEach(menu => {
        menu.classList.remove('show');
    });

    try {
        // Refresh CSRF token before request
        initializeCsrfToken();

        const response = await fetch(`/api/certificates/${id}`, {
            headers: {
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            }
        });

        if (!response.ok) throw new Error('Failed to load certificate');

        const certificate = await response.json();
        selectedCertificateId = id;

        document.getElementById('studentName').value = certificate.studentName || '';

        // Set course in searchable dropdown
        document.getElementById('courseName').value = certificate.courseName || '';
        const courseSearchEl = document.getElementById('courseSearchInput_cert');
        if (courseSearchEl) courseSearchEl.value = certificate.courseName || '';

        // Show course preview with logo
        showCoursePreview(certificate.courseName, certificate.courseImagePath);

        document.getElementById('batchName').value = certificate.batch || '';
        document.getElementById('grade').value = certificate.grade || '';
        document.getElementById('certificateNo').value = certificate.certificateNo || 'AUTO-GENERATED';

        // Set date to today if not issued, otherwise use saved date
        if (certificate.status === 'Issued' && certificate.issueDate) {
            document.getElementById('issueDate').value = certificate.issueDate;
        } else {
            document.getElementById('issueDate').valueAsDate = new Date();
        }

        document.getElementById('courseFromDate').value = certificate.courseFromDate || '';
        document.getElementById('courseToDate').value = certificate.courseToDate || '';
        document.getElementById('certificateNotes').value = certificate.notes || '';

        const modal = new bootstrap.Modal(document.getElementById('certificateModal'));
        modal.show();

    } catch (error) {
        console.error('Error loading certificate:', error);
        Swal.fire({
            title: 'Error',
            text: 'Failed to load certificate details',
            icon: 'error',
            confirmButtonColor: '#667eea'
        });
    }
}

// Delete certificate
async function deleteCertificate(id) {
    document.querySelectorAll('.action-menu').forEach(menu => {
        menu.classList.remove('show');
    });

    const result = await Swal.fire({
        title: 'Are you sure?',
        text: "You won't be able to revert this!",
        icon: 'warning',
        showCancelButton: true,
        confirmButtonColor: '#dc3545',
        cancelButtonColor: '#6c757d',
        confirmButtonText: 'Yes, delete it!'
    });

    if (result.isConfirmed) {
        try {
            // Refresh CSRF token before request
            initializeCsrfToken();

            const response = await fetch(`/api/certificates/${id}`, {
                method: 'DELETE',
                headers: {
                    ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
                }
            });

            if (!response.ok) throw new Error('Failed to delete certificate');

            Swal.fire({
                title: 'Deleted!',
                text: 'Certificate has been deleted.',
                icon: 'success',
                confirmButtonColor: '#667eea'
            });

            loadCertificates();
            updateStats();

        } catch (error) {
            console.error('Error deleting certificate:', error);
            Swal.fire({
                title: 'Error',
                text: 'Failed to delete certificate',
                icon: 'error',
                confirmButtonColor: '#667eea'
            });
        }
    }
}

// Print certificate
function printCertificate(id) {
    document.querySelectorAll('.action-menu').forEach(menu => {
        menu.classList.remove('show');
    });

    // Check if certificate is issued before allowing print
    fetch(`/api/certificates/${id}`)
        .then(response => response.json())
        .then(certificate => {
            if (certificate.status !== 'Issued') {
                Swal.fire({
                    title: 'Certificate Not Issued',
                    text: 'This certificate has not been issued yet. Please issue the certificate first.',
                    icon: 'warning',
                    confirmButtonColor: '#667eea'
                });
                return;
            }

            //  Open in new window with print-optimized settings
            const printWindow = window.open(
                `/certificates/print/${id}`,
                'CertificatePrint',
                'width=1400,height=900,menubar=no,toolbar=no,location=no,status=no'
            );

            //  Auto-trigger print when loaded
            if (printWindow) {
                printWindow.onload = function () {
                    setTimeout(() => {
                        printWindow.print();
                    }, 500);
                };
            }
        })
        .catch(error => {
            console.error('Error checking certificate status:', error);
            Swal.fire({
                title: 'Error',
                text: 'Failed to check certificate status',
                icon: 'error',
                confirmButtonColor: '#667eea'
            });
        });
}

// View certificate
async function viewCertificate(id) {
    document.querySelectorAll('.action-menu').forEach(menu => {
        menu.classList.remove('show');
    });

    try {
        const response = await fetch(`/api/certificates/${id}`);
        if (!response.ok) throw new Error('Failed to load certificate');

        const certificate = await response.json();

        //  Check if certificate is issued
        if (certificate.status !== 'Issued') {
            Swal.fire({
                title: 'Certificate Not Issued',
                text: 'This certificate has not been issued yet. Please issue the certificate first.',
                icon: 'warning',
                confirmButtonColor: '#667eea'
            });
            return;
        }

        window.open(`/certificates/view/${id}`, '_blank', 'width=1400,height=900');

    } catch (error) {
        console.error('Error viewing certificate:', error);
        Swal.fire({
            title: 'Error',
            text: 'Failed to load certificate',
            icon: 'error',
            confirmButtonColor: '#667eea'
        });
    }
}

async function promptAndSendEmail(certificateId, studentEmail) {
    document.querySelectorAll('.action-menu').forEach(menu => {
        menu.classList.remove('show');
    });

    try {
        // Refresh CSRF token before request
        initializeCsrfToken();

        const certResponse = await fetch(`/api/certificates/${certificateId}`, {
            headers: {
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            }
        });

        if (!certResponse.ok) throw new Error('Failed to load certificate');

        const certificate = await certResponse.json();

        let finalEmail = studentEmail || '';
        let studentName = certificate.studentName || '';
        const actualRegNo = certificate.registrationNo;

        if (!finalEmail && actualRegNo) {
            try {
                console.log('Fetching admission data for:', actualRegNo);

                const admResponse = await fetch(`/api/admissions/by-regno/${actualRegNo}`, {
                    method: 'GET',
                    headers: {
                        'Accept': 'application/json',
                        'Content-Type': 'application/json',
                        ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
                    }
                });

                if (admResponse.ok) {
                    const admission = await admResponse.json();
                    console.log('Admission data received:', admission);

                    // Use emailPrimary first, fallback to emailSecondary
                    finalEmail = admission.emailPrimary || admission.emailSecondary || '';

                    // Update student name if available in admission
                    if (admission.studentName) {
                        studentName = admission.studentName;
                    }

                    console.log('Final email:', finalEmail);
                } else {
                    console.warn('Admission not found for registration number:', actualRegNo);
                }
            } catch (error) {
                console.error('Error fetching admission email:', error);
            }
        }

        const { value: formValues } = await Swal.fire({
            title: '<i class="bi bi-envelope-paper me-2"></i>Send Certificate via Email',
            html: `
                <div class="text-start">
                    <div class="alert alert-info mb-3">
                        <i class="bi bi-info-circle me-2"></i>
                        <strong>Certificate will be sent as a high-quality image attachment</strong>
                    </div>

                    <div class="mb-3">
                        <label class="form-label fw-bold">
                            Certificate Number
                            <i class="bi bi-lock-fill text-muted ms-1" style="font-size: 0.8rem;"></i>
                        </label>
                        <input
                            type="text"
                            id="emailCertNo"
                            class="form-control bg-light"
                            value="${certificate.certificateNo || 'Not Issued'}"
                            disabled
                            style="cursor: not-allowed;"
                        >
                    </div>

                    <div class="mb-3">
                        <label class="form-label fw-bold">
                            Student Name
                            <i class="bi bi-lock-fill text-muted ms-1" style="font-size: 0.8rem;"></i>
                        </label>
                        <input
                            type="text"
                            id="emailStudentName"
                            class="form-control bg-light"
                            value="${studentName}"
                        >
                    </div>

                    <div class="mb-3">
                        <label class="form-label fw-bold">
                            Email Address <span class="text-danger">*</span>
                        </label>
                        <input
                            type="email"
                            id="emailRecipient"
                            class="form-control"
                            value="${finalEmail}"
                            placeholder="Enter recipient email address"
                            required
                        >
                        <small class="text-muted">
                            <i class="bi bi-info-circle me-1"></i>
                            ${finalEmail ? 'Email auto-filled from admission records' : 'Please enter email address manually'}
                        </small>
                    </div>

                    <div class="mb-3">
                        <label class="form-label fw-bold">Additional Message (Optional)</label>
                        <textarea
                            id="emailMessage"
                            class="form-control"
                            rows="3"
                            placeholder="Add a personal message to include in the email..."
                        ></textarea>
                    </div>

                    <div class="alert alert-warning mb-0">
                        <i class="bi bi-exclamation-triangle me-2"></i>
                        <small><strong>Note:</strong> Make sure the email address is correct. The certificate will be sent immediately.</small>
                    </div>
                </div>
            `,
            width: '600px',
            showCancelButton: true,
            confirmButtonText: '<i class="bi bi-send-fill me-2"></i>Send Email',
            cancelButtonText: '<i class="bi bi-x-circle me-2"></i>Cancel',
            confirmButtonColor: '#10b981',
            cancelButtonColor: '#6c757d',
            focusConfirm: false,
            preConfirm: () => {
                const email = document.getElementById('emailRecipient').value.trim();
                const message = document.getElementById('emailMessage').value.trim();

                if (!email) {
                    Swal.showValidationMessage('Please enter an email address');
                    return false;
                }

                const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
                if (!emailRegex.test(email)) {
                    Swal.showValidationMessage('Please enter a valid email address');
                    return false;
                }

                return { email, message };
            }
        });

        if (formValues) {
            await sendCertificateEmail(certificateId, formValues.email, formValues.message);
        }

    } catch (error) {
        console.error('Error preparing email modal:', error);
        Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'Failed to load certificate details',
            confirmButtonColor: '#ef4444'
        });
    }
}

/**
 * Send certificate email with canvas image
 */
async function sendCertificateEmail(certificateId, email, additionalMessage) {
    try {
        // Show loading with better UI
        Swal.fire({
            title: 'Sending Email...',
            html: `
                <div class="text-center">
                    <div class="spinner-border text-primary mb-3" role="status" style="width: 3rem; height: 3rem;">
                        <span class="visually-hidden">Loading...</span>
                    </div>
                    <p class="mb-2">Generating certificate image...</p>
                    <p class="text-muted small">This may take a few moments</p>
                </div>
            `,
            allowOutsideClick: false,
            showConfirmButton: false
        });

        // Fetch certificate view to get canvas data
        const viewUrl = `/certificates/view/${certificateId}`;
        const response = await fetch(viewUrl);
        const html = await response.text();

        // Create temporary iframe to render certificate
        const iframe = document.createElement('iframe');
        iframe.style.position = 'absolute';
        iframe.style.left = '-9999px';
        iframe.style.width = '1754px';
        iframe.style.height = '1240px';
        document.body.appendChild(iframe);

        // Load HTML into iframe
        iframe.contentDocument.open();
        iframe.contentDocument.write(html);
        iframe.contentDocument.close();

        // Wait for canvas to render
        await new Promise(resolve => setTimeout(resolve, 3000));

        // Get canvas data
        const canvas = iframe.contentDocument.getElementById('canvas');
        if (!canvas) {
            throw new Error('Certificate canvas not found');
        }

        const imageData = canvas.toDataURL('image/jpeg', 1.0);

        // Remove iframe
        document.body.removeChild(iframe);

        // Update loading message
        Swal.update({
            html: `
                <div class="text-center">
                    <div class="spinner-border text-success mb-3" role="status" style="width: 3rem; height: 3rem;">
                        <span class="visually-hidden">Loading...</span>
                    </div>
                    <p class="mb-2">Sending email to ${email}...</p>
                    <p class="text-muted small">Almost done!</p>
                </div>
            `
        });

        // Refresh CSRF token before request
        initializeCsrfToken();

        // Send to backend
        const sendResponse = await fetch(`/api/certificates/${certificateId}/send-email-with-image`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            },
            body: JSON.stringify({
                email: email,
                imageData: imageData,
                additionalMessage: additionalMessage || null
            })
        });

        const result = await sendResponse.json();

        if (!sendResponse.ok) {
            throw new Error(result.error || 'Failed to send email');
        }

        Swal.fire({
            icon: 'success',
            title: 'Email Sent Successfully!',
            html: `
                <div class="text-center">
                    <i class="bi bi-check-circle-fill text-success" style="font-size: 4rem;"></i>
                    <p class="mt-3 mb-2">Certificate has been sent to:</p>
                    <p class="fw-bold text-primary">${email}</p>
                    <small class="text-muted">The recipient should receive it within a few minutes</small>
                </div>
            `,
            confirmButtonColor: '#10b981',
            timer: 5000
        });

    } catch (error) {
        console.error('Error sending email:', error);
        Swal.fire({
            icon: 'error',
            title: 'Failed to Send Email',
            html: `
                <div class="alert alert-danger text-start">
                    <i class="bi bi-exclamation-triangle me-2"></i>
                    <strong>Error:</strong> ${error.message || 'Could not send certificate email'}
                </div>
                <p class="text-muted mt-3">
                    <small>Please check the email address and try again. If the problem persists, contact support.</small>
                </p>
            `,
            confirmButtonColor: '#ef4444'
        });
    }
}

// CSV Import/Export Functions
function openImportCertModal() {
    const modalEl = document.getElementById('importCertificateModal');
    if (!modalEl) {
        console.error('Import modal not found');
        return;
    }

    resetCertImport();
    const modal = new bootstrap.Modal(modalEl);
    modal.show();
}

function handleCertCSVFile(file) {
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (e) => parseCertCSV(e.target.result);
    reader.readAsText(file);
}

// : Parse CSV with correct format
//  Parse CSV with correct 7-column format
function parseCertCSV(text) {
    const lines = text.split('\n').filter(line => line.trim());
    importedCertData = [];
    const previewData = [];

    for (let i = 1; i < lines.length; i++) {
        const values = lines[i].match(/(".*?"|[^,\t]+)(?=\s*[,\t]|\s*$)/g) || [];
        const row = values.map(v => v.trim().replace(/^"|"$/g, ''));

        if (row.length >= 3) { // Minimum: RegNo, CertNo, StudentName
            //  CSV has 7 columns: Reg No, Cert No, Student Name, Batch(Course), Grade, Issue Date, Status
            const record = {
                registrationNo: row[0] || null,        // Column 0: Reg No
                certificateNo: row[1] || null,         // Column 1: Certificate No
                studentName: row[2] || null,           // Column 2: Student Name
                courseName: row[3] || null,            // Column 3: Batch (actually course name)
                batch: 'NA',                            //  Always NA - not in CSV
                grade: row[4] || null,                 // Column 4: Grade
                issueDate: row[5] || null,             // Column 5: Issue Date
                status: row[6] || 'Not Issued'         // Column 6: Status
            };

            importedCertData.push(record);
            if (previewData.length < 5) previewData.push(record);
        }
    }

    displayCertPreview(previewData);

    const countEl = document.getElementById('certRecordCount');
    if (countEl) {
        countEl.textContent = importedCertData.length;
    }

    const importBtn = document.getElementById('importCertBtn');
    if (importBtn) {
        importBtn.disabled = importedCertData.length === 0;
    }
}

// : Display preview with correct columns
function displayCertPreview(data) {
    const thead = document.getElementById('previewCertTableHead');
    const tbody = document.getElementById('previewCertTableBody');

    if (!thead || !tbody) return;

    thead.innerHTML = '<tr><th>Reg No</th><th>Cert No</th><th>Student</th><th>Course</th><th>Status</th></tr>';
    tbody.innerHTML = data.map(row => `
        <tr>
            <td>${row.registrationNo || '-'}</td>
            <td>${row.certificateNo || 'Not Issued'}</td>
            <td>${row.studentName || '-'}</td>
            <td>${row.courseName || '-'}</td>
            <td><span class="badge ${row.status === 'Issued' ? 'bg-success' : 'bg-warning'}">${row.status || 'Not Issued'}</span></td>
        </tr>
    `).join('');

    const previewEl = document.getElementById('importCertPreview');
    if (previewEl) {
        previewEl.style.display = 'block';
    }
}

async function importCertificatesCSV() {
    if (importedCertData.length === 0) {
        Swal.fire({
            title: 'Error',
            text: 'No data to import',
            icon: 'error',
            confirmButtonColor: '#667eea'
        });
        return;
    }

    try {
        Swal.fire({
            title: 'Importing...',
            text: `Importing ${importedCertData.length} certificates`,
            allowOutsideClick: false,
            didOpen: () => Swal.showLoading()
        });

        const formData = new FormData();
        const csvContent = convertToCSV(importedCertData);
        const blob = new Blob([csvContent], { type: 'text/csv' });
        formData.append('file', blob, 'certificates.csv');

        // Refresh CSRF token before request
        initializeCsrfToken();

        const response = await fetch('/api/certificates/import', {
            method: 'POST',
            body: formData,
            headers: {
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            }
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'Import failed');
        }

        const result = await response.json();

        Swal.fire({
            title: 'Success!',
            html: `
               <p>Successfully imported: <strong>${result.success ?? 0}</strong></p>
               <p>Skipped (duplicates): <strong>${result.skipped ?? 0}</strong></p>
           `,
            icon: 'success',
            confirmButtonColor: '#667eea'
        });

        const modalEl = document.getElementById('importCertificateModal');
        const modalInstance = bootstrap.Modal.getInstance(modalEl);
        if (modalInstance) {
            modalInstance.hide();
        }

        loadCertificates();
        updateStats();
        resetCertImport();

    } catch (error) {
        console.error('Import error:', error);
        Swal.fire({
            title: 'Error',
            text: error.message || 'Failed to import certificates',
            icon: 'error',
            confirmButtonColor: '#667eea'
        });
    }
}

// : Convert to correct CSV format
function convertToCSV(data) {
    const header = 'Reg No,Certificate No,Student Name,Course,Batch,Grade,Issue Date,Status\n';
    const rows = data.map(row =>
        `${row.registrationNo || ''},${row.certificateNo || ''},${row.studentName || ''},${row.courseName || ''},${row.batch || ''},${row.grade || ''},${row.issueDate || ''},${row.status || 'Not Issued'}`
    ).join('\n');
    return header + rows;
}

async function exportCertificatesCSV() {
    try {
        // Refresh CSRF token before request
        initializeCsrfToken();

        const checkResponse = await fetch('/api/certificates/stats', {
            headers: {
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            }
        });

        const stats = await checkResponse.json();

        if (!stats || stats.total === 0) {
            Swal.fire({
                icon: 'info',
                title: 'No Data Found',
                text: 'The certificate table is empty. There is no data to export.',
                confirmButtonColor: '#667eea'
            });
            return;
        }

        Swal.fire({
            title: 'Exporting...',
            text: 'Preparing CSV file',
            allowOutsideClick: false,
            didOpen: () => Swal.showLoading()
        });

        const response = await fetch('/api/certificates/export/csv', {
            headers: {
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            }
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'Export failed');
        }

        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `certificates_${new Date().toISOString().split('T')[0]}.csv`;
        link.click();
        window.URL.revokeObjectURL(url);

        Swal.fire({
            icon: 'success',
            title: 'Export Successful!',
            text: `${stats.total} certificate(s) exported successfully!`,
            timer: 2000,
            showConfirmButton: false
        });
    } catch (error) {
        console.error('Export error:', error);
        Swal.fire({
            icon: 'error',
            title: 'Export Failed',
            text: error.message || 'Failed to export CSV',
            confirmButtonColor: '#ef4444'
        });
    }
}

function resetCertImport() {
    const previewEl = document.getElementById('importCertPreview');
    const fileInput = document.getElementById('csvCertFileInput');
    const importBtn = document.getElementById('importCertBtn');

    if (previewEl) previewEl.style.display = 'none';
    if (fileInput) fileInput.value = '';
    if (importBtn) importBtn.disabled = true;

    importedCertData = [];
}

// Pagination functions
function updatePaginationInfo(showing, total) {
    const startEl = document.getElementById('entriesStart');
    const endEl = document.getElementById('entriesEnd');
    const totalEl = document.getElementById('totalEntries');

    //  Handle empty data case
    if (total === 0 || showing === 0) {
        if (startEl) startEl.textContent = '0';
        if (endEl) endEl.textContent = '0';
        if (totalEl) totalEl.textContent = '0';
        return;
    }

    const start = (currentPage * pageSize) + 1;
    const end = (currentPage * pageSize) + showing;

    if (startEl) startEl.textContent = start;
    if (endEl) endEl.textContent = end;
    if (totalEl) totalEl.textContent = total;
}

function renderPagination() {
    const paginationControls = document.getElementById('paginationControls');
    if (!paginationControls) return;

    //  FIX: Hide pagination if no data
    if (totalPages === 0 || totalElements === 0) {
        paginationControls.innerHTML = '';
        return;
    }

    let html = '';

    // Previous button
    html += `
        <li class="page-item ${currentPage === 0 ? 'disabled' : ''}">
            <a class="page-link" href="#" onclick="changePage(${currentPage - 1}); return false;">Previous</a>
        </li>
    `;

    //  Smart pagination with ellipsis
    const maxVisiblePages = 7;
    let startPage = 0;
    let endPage = totalPages - 1;

    if (totalPages > maxVisiblePages) {
        const halfVisible = Math.floor(maxVisiblePages / 2);

        if (currentPage <= halfVisible) {
            // Near start
            endPage = maxVisiblePages - 2;
        } else if (currentPage >= totalPages - halfVisible - 1) {
            // Near end
            startPage = totalPages - maxVisiblePages + 1;
        } else {
            // Middle
            startPage = currentPage - halfVisible + 1;
            endPage = currentPage + halfVisible - 1;
        }
    }

    // First page button (always show if not in range)
    if (startPage > 0) {
        html += `
            <li class="page-item ${currentPage === 0 ? 'active' : ''}">
                <a class="page-link" href="#" onclick="changePage(0); return false;">1</a>
            </li>
        `;
        if (startPage > 1) {
            html += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
        }
    }

    // Middle page numbers
    for (let i = startPage; i <= endPage; i++) {
        html += `
            <li class="page-item ${i === currentPage ? 'active' : ''}">
                <a class="page-link" href="#" onclick="changePage(${i}); return false;">${i + 1}</a>
            </li>
        `;
    }

    // Last page button (always show if not in range)
    if (endPage < totalPages - 1) {
        if (endPage < totalPages - 2) {
            html += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
        }
        html += `
            <li class="page-item ${currentPage === totalPages - 1 ? 'active' : ''}">
                <a class="page-link" href="#" onclick="changePage(${totalPages - 1}); return false;">${totalPages}</a>
            </li>
        `;
    }

    // Next button
    html += `
        <li class="page-item ${currentPage >= totalPages - 1 ? 'disabled' : ''}">
            <a class="page-link" href="#" onclick="changePage(${currentPage + 1}); return false;">Next</a>
        </li>
    `;

    paginationControls.innerHTML = html;
}

function changePage(page) {
    if (page < 0 || page >= totalPages) return;
    currentPage = page;
    loadCertificates();
}




// ==================== MANUAL CERTIFICATE GENERATION ====================

async function openManualCertificateModal() {
    // Fetch courses
    let courseOptions = '<option value="">-- Select Course --</option>';
    try {
        const response = await fetch('/api/courses?page=0&size=1000');
        const data = await response.json();
        data.courses.forEach(course => {
            courseOptions += `<option value="${course.courseName}">${course.courseName}</option>`;
        });
    } catch (error) {
        console.error('Error loading courses:', error);
    }

    const { value: formValues } = await Swal.fire({
        title: 'Manual Certificate Generation',
        html: `
            <div class="text-start">
                <div class="alert alert-warning mb-3">
                    <i class="bi bi-exclamation-triangle me-2"></i>
                    <strong>Backup Feature:</strong> Use only when auto-generation misses a record
                </div>

                <div class="mb-3">
                    <label class="form-label">Registration Number <span class="text-danger">*</span></label>
                    <input type="text" id="manualRegNo" class="form-control" placeholder="Enter registration number">
                </div>

                <div class="mb-3">
                    <label class="form-label">Student Name <span class="text-danger">*</span></label>
                    <input type="text" id="manualStudentName" class="form-control" placeholder="Enter student name">
                </div>

                <div class="mb-3">
                    <label class="form-label">Course <span class="text-danger">*</span></label>
                    <select id="manualCourse" class="form-select">
                        ${courseOptions}
                    </select>
                </div>

                <div class="mb-3">
                    <label class="form-label">Reason for Manual Generation <span class="text-danger">*</span></label>
                    <textarea id="manualReason" class="form-control" rows="3" placeholder="E.g., Missed by auto-generation, Special case, etc."></textarea>
                </div>
            </div>
        `,
        width: '550px',
        showCancelButton: true,
        confirmButtonText: '<i class="bi bi-plus-circle me-2"></i>Create Certificate',
        cancelButtonText: 'Cancel',
        confirmButtonColor: '#10b981',
        cancelButtonColor: '#6c757d',
        preConfirm: () => {
            const regNo = document.getElementById('manualRegNo').value.trim();
            const studentName = document.getElementById('manualStudentName').value.trim();
            const courseName = document.getElementById('manualCourse').value;
            const reason = document.getElementById('manualReason').value.trim();

            if (!regNo || !studentName || !courseName || !reason) {
                Swal.showValidationMessage('Please fill in all required fields');
                return false;
            }

            return { regNo, studentName, courseName, reason };
        }
    });

    if (formValues) {
        await createManualCertificate(formValues);
    }
}

async function createManualCertificate(data) {
    try {
        Swal.fire({
            title: 'Creating Certificate...',
            allowOutsideClick: false,
            didOpen: () => Swal.showLoading()
        });

        // Refresh CSRF token before request
        initializeCsrfToken();

        const response = await fetch('/api/certificates/manual-generate', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            },
            body: JSON.stringify({
                registrationNo: data.regNo,
                studentName: data.studentName,
                courseName: data.courseName,
                reason: data.reason
            })
        });

        const result = await response.json();

        if (result.success) {
            Swal.fire({
                icon: 'success',
                title: 'Certificate Created!',
                html: `
                    <p>Certificate has been created manually for:</p>
                    <ul class="text-start">
                        <li><strong>Student:</strong> ${data.studentName}</li>
                        <li><strong>Registration:</strong> ${data.regNo}</li>
                        <li><strong>Course:</strong> ${data.courseName}</li>
                    </ul>
                    <p class="text-muted mt-2">
                        <small>This action has been logged for audit purposes</small>
                    </p>
                `,
                confirmButtonColor: '#10b981'
            }).then(() => {
                loadCertificates();
                updateStats();
            });
        } else {
            throw new Error(result.message || 'Failed to create certificate');
        }

    } catch (error) {
        Swal.fire({
            icon: 'error',
            title: 'Error',
            text: error.message || 'Failed to create manual certificate',
            confirmButtonColor: '#ef4444'
        });
    }
}

// ==================== INITIALIZE EVENT LISTENERS ====================

function initializeCertificateButtons() {
    // Auto-generate button
    document.getElementById('btnAutoGenerate')?.addEventListener('click', autoGenerateCertificates);

    // Manual generate button
    document.getElementById('btnManualGenerate')?.addEventListener('click', openManualCertificateModal);

    // View logs button
    document.getElementById('btnViewManualLogs')?.addEventListener('click', viewManualCertificateLogs);
}

// Call on page load
document.addEventListener('DOMContentLoaded', initializeCertificateButtons);

async function loadManualLogsPage(page) {
    Swal.fire({
        title: 'Loading Logs...',
        allowOutsideClick: false,
        didOpen: () => Swal.showLoading(),
        customClass: {
            popup: 'manual-logs-popup'
        }
    });

    initializeCsrfToken();

    const params = new URLSearchParams({
        page,
        size: manualLogsSize
    });

    const response = await fetch(`/api/certificates/manual-logs/paged?${params}`, {
        headers: {
            ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
        }
    });

    if (!response.ok) {
        throw new Error('Failed to load manual certificate logs');
    }

    const pageData = await response.json();
    const logs = pageData.content || [];
    manualLogsTotalPages = pageData.totalPages || 0;
    manualLogsPage = pageData.number || 0;

    if (manualLogsTotalPages === 0) {
        Swal.fire({
            icon: 'info',
            title: 'No Manual Records',
            text: 'No manual certificate generation records found',
            confirmButtonColor: '#667eea'
        });
        return;
    }

    const tableRows = logs.map(log => {

        let formattedDate = 'N/A';

        if (log.createdAt) {
            try {
                const dateStr = log.createdAt.replace('T', ' ').split('.')[0];
                const createdDate = new Date(dateStr);

                if (!isNaN(createdDate.getTime())) {
                    formattedDate = createdDate.toLocaleString('en-IN', {
                        year: 'numeric',
                        month: 'short',
                        day: '2-digit',
                        hour: '2-digit',
                        minute: '2-digit',
                        hour12: true,
                        timeZone: 'Asia/Kolkata'
                    });
                }
            } catch (e) {
                console.error('Date parsing error:', e, log.createdAt);
            }
        }

        return `
            <tr>
                <td><strong>${log.registrationNo || '-'}</strong></td>
                <td>${log.studentName || '-'}</td>
                <td><span class="badge bg-primary">${log.courseName || '-'}</span></td>
                <td>
                    <div>
                        <i class="bi bi-person-fill text-primary me-1"></i>
                        <strong>${log.createdByEmployeeName || 'Unknown'}</strong>
                    </div>
                </td>
                <td><small class="text-muted">${log.reason || '-'}</small></td>
                <td>
                    <small class="text-success">
                        <i class="bi bi-calendar-check me-1"></i>${formattedDate}
                    </small>
                </td>
            </tr>
        `;
    }).join('');

    const startEntry = (pageData.number * pageData.size) + (logs.length > 0 ? 1 : 0);
    const endEntry = (pageData.number * pageData.size) + logs.length;

    const modalHtml = `
        <div class="manual-logs-table-wrap">
            <div class="table-responsive d-none d-md-block" style="max-height:480px; overflow-y:auto;">
                <table class="table table-sm table-hover mb-0">
                    <thead class="table-light sticky-top">
                        <tr>
                            <th>Reg No</th>
                            <th>Student Name</th>
                            <th>Course</th>
                            <th>Created By</th>
                            <th>Reason</th>
                            <th>Created At</th>
                        </tr>
                    </thead>
                    <tbody>${tableRows}</tbody>
                </table>
            </div>
            <div class="d-md-none manual-logs-cards" style="max-height:70vh; overflow-y:auto;">
                ${logs.map(log => {
                    let fd = 'N/A';
                    if (log.createdAt) {
                        try {
                            const d = new Date(log.createdAt.replace('T',' ').split('.')[0]);
                            if (!isNaN(d)) fd = d.toLocaleString('en-IN',{year:'numeric',month:'short',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:true,timeZone:'Asia/Kolkata'});
                        } catch(e){}
                    }
                    return `
                    <div class="manual-log-card">
                        <div class="mlc-header">
                            <span class="mlc-regno">${log.registrationNo || '-'}</span>
                            <span class="badge bg-primary ms-2">${log.courseName || '-'}</span>
                        </div>
                        <div class="mlc-name">${log.studentName || '-'}</div>
                        <div class="mlc-row"><i class="bi bi-person-fill text-primary me-1"></i><strong>${log.createdByEmployeeName || 'Unknown'}</strong></div>
                        <div class="mlc-row text-muted small">${log.reason || '-'}</div>
                        <div class="mlc-date"><i class="bi bi-calendar-check me-1"></i>${fd}</div>
                    </div>`;
                }).join('')}
            </div>
        </div>
        <div class="manual-logs-pagination">
            <div class="mlp-info text-muted small">
                Showing <strong>${startEntry}</strong>&ndash;<strong>${endEntry}</strong> of <strong>${pageData.totalElements}</strong>
            </div>
            <div class="mlp-controls">
                <button type="button" class="btn btn-outline-secondary btn-sm" id="manualLogsPrev" ${manualLogsPage <= 0 ? 'disabled' : ''}>
                    <i class="bi bi-chevron-left"></i> Prev
                </button>
                <span class="mlp-page">Page <strong>${manualLogsPage + 1}</strong> / <strong>${manualLogsTotalPages}</strong></span>
                <button type="button" class="btn btn-outline-secondary btn-sm" id="manualLogsNext" ${manualLogsPage >= manualLogsTotalPages - 1 ? 'disabled' : ''}>
                    Next <i class="bi bi-chevron-right"></i>
                </button>
            </div>
        </div>
    `;

    Swal.fire({
        title: '<i class="bi bi-clipboard-data me-2"></i>Manual Certificate Logs',
        html: modalHtml,
        width: 'min(95vw, 950px)',
        confirmButtonText: 'Close',
        confirmButtonColor: '#667eea',
        customClass: {
            popup: 'manual-logs-popup'
        },
        didOpen: () => {
            const prevBtn = document.getElementById('manualLogsPrev');
            const nextBtn = document.getElementById('manualLogsNext');

            if (prevBtn) {
                prevBtn.addEventListener('click', async () => {
                    if (manualLogsPage > 0) {
                        await loadManualLogsPage(manualLogsPage - 1);
                    }
                });
            }

            if (nextBtn) {
                nextBtn.addEventListener('click', async () => {
                    if (manualLogsPage < manualLogsTotalPages - 1) {
                        await loadManualLogsPage(manualLogsPage + 1);
                    }
                });
            }
        }
    });
}

async function viewManualCertificateLogs() {
    try {
        manualLogsPage = 0;
        await loadManualLogsPage(manualLogsPage);

    } catch (error) {
        console.error('Error loading manual logs:', error);
        Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'Failed to load manual certificate logs',
            confirmButtonColor: '#ef4444'
        });
    }
}

// ==================== SAVE CERTIFICATE WITH BETTER ERROR HANDLING ====================

async function saveCertificate() {
    const form = document.getElementById('certificateForm');

    if (!form.checkValidity()) {
        form.reportValidity();
        return;
    }

    const certificateData = {
        studentName: document.getElementById('studentName').value,
        courseName: document.getElementById('courseName').value,
        batch: document.getElementById('batchName').value,
        certificateNo: document.getElementById('certificateNo').value,
        grade: document.getElementById('grade').value || null,
        issueDate: document.getElementById('issueDate').value,
        courseFromDate: document.getElementById('courseFromDate').value || null,
        courseToDate: document.getElementById('courseToDate').value || null,
        notes: document.getElementById('certificateNotes').value
    };

    try {
        Swal.fire({
            title: 'Issuing Certificate...',
            html: 'Please wait while we process your request',
            allowOutsideClick: false,
            didOpen: () => Swal.showLoading()
        });

        // Refresh CSRF token before request
        initializeCsrfToken();

        const response = await fetch(`/api/certificates/${selectedCertificateId}/issue`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            },
            body: JSON.stringify(certificateData)
        });

        const contentType = response.headers.get('content-type');
        let errorData = null;

        if (!response.ok) {
            if (contentType && contentType.includes('application/json')) {
                errorData = await response.json();
            } else {
                const textError = await response.text();
                errorData = { error: textError || 'Unknown error occurred' };
            }

            if (errorData.error && errorData.error.includes('Duplicate entry')) {
                throw new Error('This certificate number already exists. Please try again - a unique number will be generated automatically.');
            } else if (errorData.error && errorData.error.includes('already exists')) {
                throw new Error(errorData.error);
            } else {
                throw new Error(errorData.error || 'Failed to issue certificate');
            }
        }

        Swal.fire({
            title: 'Success!',
            html: '<i class="bi bi-check-circle-fill text-success" style="font-size: 3rem;"></i><br><br>Certificate issued successfully!',
            icon: 'success',
            confirmButtonColor: '#667eea',
            timer: 2000
        });

        bootstrap.Modal.getInstance(document.getElementById('certificateModal')).hide();
        loadCertificates();
        updateStats();

    } catch (error) {
        console.error('Error issuing certificate:', error);

        Swal.fire({
            title: 'Error',
            html: `
                <div class="alert alert-danger text-start">
                    <i class="bi bi-exclamation-triangle me-2"></i>
                    <strong>Failed to issue certificate</strong>
                    <p class="mb-0 mt-2">${error.message}</p>
                </div>
                <p class="text-muted mt-3">
                    <small>If the problem persists, please contact support.</small>
                </p>
            `,
            icon: 'error',
            confirmButtonColor: '#667eea'
        });
    }
}

// ==================== ADD CUSTOM CSS FOR MANUAL LOGS ====================

const customStyles = `
<style>
/* ===== Manual Logs Popup ===== */
.manual-logs-popup {
    border-radius: 14px !important;
    padding: 0 !important;
}
.manual-logs-popup .swal2-title {
    font-size: clamp(1rem, 3vw, 1.25rem);
    padding-top: 1rem;
}
.manual-logs-popup .swal2-html-container {
    padding: 0.5rem 1rem 0 !important;
    margin: 0 !important;
    overflow: visible !important;
}

/* Desktop table */
.manual-logs-popup .table thead th {
    background-color: #f8f9fa;
    font-weight: 600;
    font-size: 0.82rem;
    text-transform: uppercase;
    color: #495057;
    border-bottom: 2px solid #dee2e6;
    white-space: nowrap;
}
.manual-logs-popup .table tbody tr:hover { background-color: #f0f4ff; }
.manual-logs-popup .table td {
    vertical-align: middle;
    font-size: 0.88rem;
}
.manual-logs-popup .badge {
    font-size: 0.82rem;
    padding: 0.3em 0.6em;
}
.table thead.sticky-top {
    position: sticky;
    top: 0;
    z-index: 10;
    box-shadow: 0 2px 4px rgba(0,0,0,0.08);
}

/* Mobile cards */
.manual-logs-cards { display: flex; flex-direction: column; gap: 0.6rem; padding: 0.25rem 0; }
.manual-log-card {
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 10px;
    padding: 0.75rem 1rem;
    text-align: left;
}
.mlc-header { display: flex; align-items: center; flex-wrap: wrap; gap: 0.35rem; margin-bottom: 0.3rem; }
.mlc-regno { font-weight: 700; font-size: 0.92rem; color: #1e293b; }
.mlc-name { font-size: 0.95rem; font-weight: 600; color: #334155; margin-bottom: 0.25rem; }
.mlc-row { font-size: 0.85rem; margin-bottom: 0.15rem; color: #475569; }
.mlc-date { font-size: 0.82rem; color: #16a34a; margin-top: 0.3rem; }

/* Pagination */
.manual-logs-pagination {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 0.5rem;
    margin-top: 0.75rem;
    padding: 0.5rem 0 0.25rem;
    border-top: 1px solid #e2e8f0;
}
.mlp-controls { display: flex; align-items: center; gap: 0.4rem; flex-wrap: wrap; }
.mlp-page { font-size: 0.88rem; color: #64748b; white-space: nowrap; }

/* Shared */
#modalStudentTableBody tr { cursor: pointer; }
</style>
`;

// Inject styles on page load
document.addEventListener('DOMContentLoaded', () => {
    const styleElement = document.createElement('div');
    styleElement.innerHTML = customStyles;
    document.head.appendChild(styleElement);
});

// =========================================================================
// STUDENT SELECTION & BULK CERTIFICATE GENERATION
// =========================================================================

let modalCurrentPage = 0;
let modalPageSize = 25;
let selectedStudents = new Set(); // Stores registration numbers
let modalSearchTimeout = null;

function openStudentSelectModal() {
    modalCurrentPage = 0;
    selectedStudents.clear();
    updateSelectedCount();

    const searchInput = document.getElementById('modalStudentSearch');
    if (searchInput) searchInput.value = '';

    const checkAll = document.getElementById('checkAllInModal');
    if (checkAll) checkAll.checked = false;

    // Reset table to initial state
    const tbody = document.getElementById('modalStudentTableBody');
    if (tbody) {
        tbody.innerHTML = `
            <tr>
                <td colspan="6" class="text-center py-5 text-muted">
                    <i class="bi bi-search mb-2" style="font-size: 2rem;"></i>
                    <p>Start typing to search students...</p>
                </td>
            </tr>
        `;
    }
    updateModalPagination(0, 0, false);

    const modal = new bootstrap.Modal(document.getElementById('studentSelectModal'));
    modal.show();

    // Focus search input after modal is shown
    document.getElementById('studentSelectModal')?.addEventListener('shown.bs.modal', function handler() {
        document.getElementById('modalStudentSearch')?.focus();
        this.removeEventListener('shown.bs.modal', handler);
    });
}

// Dynamic search with debounce (400ms)
document.getElementById('modalStudentSearch')?.addEventListener('input', function () {
    clearTimeout(modalSearchTimeout);
    modalSearchTimeout = setTimeout(() => {
        modalCurrentPage = 0;
        loadStudentsInModal();
    }, 400);
});

// Pagination listeners
document.getElementById('modalPrev')?.addEventListener('click', function () {
    if (!this.classList.contains('disabled')) {
        modalCurrentPage--;
        loadStudentsInModal();
    }
});

document.getElementById('modalNext')?.addEventListener('click', function () {
    if (!this.classList.contains('disabled')) {
        modalCurrentPage++;
        loadStudentsInModal();
    }
});

// Select all on current page
document.getElementById('checkAllInModal')?.addEventListener('change', function () {
    const checkboxes = document.querySelectorAll('.student-checkbox');
    checkboxes.forEach(cb => {
        cb.checked = this.checked;
        const regNo = cb.getAttribute('data-regno');
        if (this.checked) {
            selectedStudents.add(regNo);
        } else {
            selectedStudents.delete(regNo);
        }
    });
    updateSelectedCount();
});

// Clear selection
document.getElementById('btnClearSelection')?.addEventListener('click', () => {
    selectedStudents.clear();
    const checkAll = document.getElementById('checkAllInModal');
    if (checkAll) checkAll.checked = false;
    document.querySelectorAll('.student-checkbox').forEach(cb => cb.checked = false);
    updateSelectedCount();
});

// Generate button
document.getElementById('btnGenerateForSelected')?.addEventListener('click', generateCertificatesForSelected);

// Row selection click handler
document.getElementById('modalStudentTableBody')?.addEventListener('click', function (e) {
    const tr = e.target.closest('tr');
    if (!tr) return;

    // Skip if clicking directly on checkbox to avoid double-toggling
    if (e.target.classList.contains('student-checkbox') || e.target.closest('.student-checkbox')) {
        return;
    }

    const checkbox = tr.querySelector('.student-checkbox');
    if (checkbox) {
        checkbox.checked = !checkbox.checked;
        const regNo = checkbox.getAttribute('data-regno');
        window.toggleStudentSelection(regNo, checkbox);
    }
});

/**
 * Load students from server using POST /api/admissions/search
 * Uses offset pagination (page, size) — server-side
 */
async function loadStudentsInModal() {
    const tbody = document.getElementById('modalStudentTableBody');
    const searchTerm = document.getElementById('modalStudentSearch')?.value?.trim() || '';

    if (!tbody) return;

    // Show spinner
    tbody.innerHTML = `
        <tr>
            <td colspan="6" class="text-center py-4">
                <div class="spinner-border spinner-border-sm text-success" role="status"></div>
                <span class="ms-2">Searching...</span>
            </td>
        </tr>
    `;

    try {
        const searchDTO = {
            searchTerm: searchTerm,
            page: modalCurrentPage,
            size: modalPageSize,
            sortBy: "admissionDate",
            sortDirection: "DESC"
        };

        initializeCsrfToken();
        const response = await fetch('/api/admissions/search', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            },
            body: JSON.stringify(searchDTO)
        });

        if (!response.ok) throw new Error('Search failed');

        const data = await response.json();
        const students = data.content || [];

        if (students.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="6" class="text-center py-5 text-muted">
                        <i class="bi bi-inbox mb-2" style="font-size: 2rem;"></i>
                        <p class="mb-0">${searchTerm ? 'No students found for "' + searchTerm + '"' : 'No students available'}</p>
                    </td>
                </tr>
            `;
            updateModalPagination(0, 0, false);
            return;
        }

        // Render table rows using AdmissionResponseDTO fields
        tbody.innerHTML = students.map((student) => {
            const isSelected = selectedStudents.has(student.registrationNumber);
            const name = student.studentName || [student.firstName, student.middleName, student.lastName].filter(Boolean).join(' ') || '-';
            const mobile = student.mobilePrimary || '-';
            const courses = student.coursesList || (student.courses ? student.courses.split(',') : []);
            const feesStatus = student.feesStatus || 'Pending';

            let statusBadgeClass = 'bg-warning';
            if (feesStatus === 'Clear') statusBadgeClass = 'bg-success';
            else if (feesStatus === 'Partial') statusBadgeClass = 'bg-info';
            else if (feesStatus === 'Overdue') statusBadgeClass = 'bg-danger';
            else if (feesStatus === 'Cancelled') statusBadgeClass = 'bg-secondary';

            return `
                <tr class="${isSelected ? 'table-success' : ''}">
                    <td>
                        <input type="checkbox" class="form-check-input student-checkbox" 
                               data-regno="${student.registrationNumber}"
                               ${isSelected ? 'checked' : ''}
                               onchange="window.toggleStudentSelection('${student.registrationNumber}', this)">
                    </td>
                    <td><strong>${student.registrationNumber}</strong></td>
                    <td>${name}</td>
                    <td>${mobile}</td>
                    <td>
                        <div class="d-flex flex-wrap gap-1">
                            ${courses.map(c => `<span class="badge bg-light text-dark border" style="font-size: 0.65rem;">${c.trim()}</span>`).join('')}
                        </div>
                    </td>
                    <td>
                        <span class="badge ${statusBadgeClass}" style="font-size: 0.7rem;">
                            ${feesStatus}
                        </span>
                    </td>
                </tr>
            `;
        }).join('');

        updateModalPagination(students.length, data.totalElements, !data.last);

        // Sync checkAll checkbox
        const allChecked = students.length > 0 && students.every(s => selectedStudents.has(s.registrationNumber));
        const checkAll = document.getElementById('checkAllInModal');
        if (checkAll) checkAll.checked = allChecked;

    } catch (error) {
        console.error('Error loading students in modal:', error);
        tbody.innerHTML = `
            <tr>
                <td colspan="6" class="text-center py-5 text-danger">
                    <i class="bi bi-exclamation-triangle-fill mb-2" style="font-size: 2rem;"></i>
                    <p>Failed to load students. Please check your connection.</p>
                </td>
            </tr>
        `;
    }
}

// Called from inline onchange handler
window.toggleStudentSelection = function (regNo, checkbox) {
    if (checkbox.checked) {
        selectedStudents.add(regNo);
        checkbox.closest('tr')?.classList.add('table-success');
    } else {
        selectedStudents.delete(regNo);
        checkbox.closest('tr')?.classList.remove('table-success');
        const checkAll = document.getElementById('checkAllInModal');
        if (checkAll) checkAll.checked = false;
    }
    updateSelectedCount();
};

function updateSelectedCount() {
    const count = selectedStudents.size;
    const el = document.getElementById('selectedStudentCount');
    if (el) el.textContent = count;

    const clearBtn = document.getElementById('btnClearSelection');
    if (clearBtn) clearBtn.style.display = count > 0 ? 'inline-block' : 'none';

    // Enable/disable generate button
    const genBtn = document.getElementById('btnGenerateForSelected');
    if (genBtn) genBtn.disabled = count === 0;
}

function updateModalPagination(currentPageCount, totalCount, hasNext) {
    const start = totalCount > 0 ? (modalCurrentPage * modalPageSize + 1) : 0;
    const end = Math.min(start + currentPageCount - 1, totalCount);

    const infoEl = document.getElementById('modalPageInfo');
    if (infoEl) {
        infoEl.textContent = totalCount > 0
            ? `Showing ${start} to ${end} of ${totalCount} students`
            : 'No students to show';
    }

    const prevBtn = document.getElementById('modalPrev');
    const nextBtn = document.getElementById('modalNext');

    if (prevBtn) prevBtn.classList.toggle('disabled', modalCurrentPage === 0);
    if (nextBtn) nextBtn.classList.toggle('disabled', !hasNext);
}

async function generateCertificatesForSelected() {
    if (selectedStudents.size === 0) {
        Swal.fire({
            icon: 'warning',
            title: 'No Selection',
            text: 'Please select at least one student to generate certificates.',
            confirmButtonColor: '#667eea'
        });
        return;
    }

    const result = await Swal.fire({
        title: 'Generate Certificates?',
        html: `
            <div class="text-start">
                <p>You have selected <strong>${selectedStudents.size}</strong> student(s).</p>
                <p>Certificates will be created for each of their enrolled courses.</p>
                <p class="text-warning mb-0"><i class="bi bi-info-circle me-1"></i>Duplicate student + course certificates will be skipped.</p>
            </div>
        `,
        icon: 'question',
        showCancelButton: true,
        confirmButtonText: '<i class="bi bi-magic me-2"></i>Yes, Generate',
        cancelButtonText: 'Cancel',
        confirmButtonColor: '#198754',
        cancelButtonColor: '#6c757d',
        width: '500px'
    });

    if (!result.isConfirmed) return;

    try {
        Swal.fire({
            title: 'Processing...',
            html: `Generating certificates for ${selectedStudents.size} student(s)`,
            allowOutsideClick: false,
            didOpen: () => Swal.showLoading()
        });

        initializeCsrfToken();
        const response = await fetch('/api/certificates/bulk-generate', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            },
            body: JSON.stringify({ registrationNumbers: Array.from(selectedStudents) })
        });

        const data = await response.json();
        Swal.close();

        if (data.success) {
            Swal.fire({
                icon: 'success',
                title: 'Certificates Created!',
                html: `<p><strong>${data.certificatesCreated}</strong> certificate(s) generated successfully!</p>
                       ${data.skipped > 0 ? `<p class="text-muted">${data.skipped} duplicate(s) skipped.</p>` : ''}`,
                confirmButtonColor: '#198754'
            }).then(() => {
                const modalEl = document.getElementById('studentSelectModal');
                const modal = bootstrap.Modal.getInstance(modalEl);
                if (modal) modal.hide();

                loadCertificates();
                updateStats();
                updateStatsForCurrentView();
            });
        } else {
            Swal.fire({
                icon: 'warning',
                title: 'No New Certificates',
                text: data.message || 'All selected students already have certificates for their courses.',
                confirmButtonColor: '#667eea'
            });
        }

    } catch (error) {
        Swal.close();
        console.error('Bulk generation error:', error);
        Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'Failed to generate certificates. Please try again.',
            confirmButtonColor: '#ef4444'
        });
    }
}

// =========================================================================
// COURSE SEARCH DROPDOWN FOR CERTIFICATE ISSUE MODAL
// =========================================================================

let allCoursesCache = null;
let courseSearchDebounce = null;

/**
 * Fetch all courses from server and cache them
 */
async function fetchAllCourses() {
    if (allCoursesCache) return allCoursesCache;

    try {
        const response = await fetch('/api/courses?page=0&size=1000');
        const data = await response.json();
        allCoursesCache = (data.courses || []).map(c => ({
            name: c.courseName,
            imagePath: c.courseImagePath || null
        }));
        return allCoursesCache;
    } catch (error) {
        console.error('Error fetching courses:', error);
        return [];
    }
}

/**
 * Show course preview with logo when a course is selected
 */
function showCoursePreview(courseName, imagePath) {
    const previewDiv = document.getElementById('selectedCoursePreview');
    const imgEl = document.getElementById('selectedCourseImage');
    const badgeEl = document.getElementById('selectedCourseBadge');

    if (!previewDiv || !badgeEl) return;

    if (!courseName) {
        previewDiv.style.cssText = 'display: none !important';
        return;
    }

    badgeEl.textContent = courseName;
    previewDiv.style.cssText = 'display: flex !important';

    if (imgEl) {
        if (imagePath) {
            imgEl.src = '/uploads/courses/' + imagePath;
            imgEl.classList.remove('d-none');
            imgEl.onerror = () => { imgEl.classList.add('d-none'); };
        } else {
            imgEl.classList.add('d-none');
        }
    }
}

/**
 * Filter and render course dropdown
 */
function renderCourseDropdown(courses, filterText) {
    const dropdownList = document.getElementById('courseDropdownList');
    if (!dropdownList) return;

    const filtered = filterText
        ? courses.filter(c => c.name.toLowerCase().includes(filterText.toLowerCase()))
        : courses;

    if (filtered.length === 0) {
        dropdownList.innerHTML = '<div class="list-group-item text-muted small py-2">No courses found</div>';
        dropdownList.style.display = 'block';
        return;
    }

    dropdownList.innerHTML = filtered.map(c => `
        <button type="button" class="list-group-item list-group-item-action d-flex align-items-center gap-2 py-2"
                data-course-name="${c.name}" data-course-image="${c.imagePath || ''}">
            ${c.imagePath
            ? `<img src="/uploads/courses/${c.imagePath}" alt="" style="width: 24px; height: 24px; object-fit: contain; border-radius: 3px;" onerror="this.style.display='none'">`
            : '<i class="bi bi-book text-muted" style="width: 24px; text-align: center;"></i>'
        }
            <span style="font-size: 0.88rem;">${c.name}</span>
        </button>
    `).join('');

    dropdownList.style.display = 'block';

    // Bind click events
    dropdownList.querySelectorAll('.list-group-item-action').forEach(item => {
        item.addEventListener('click', () => {
            const name = item.getAttribute('data-course-name');
            const image = item.getAttribute('data-course-image');

            document.getElementById('courseName').value = name;
            document.getElementById('courseSearchInput_cert').value = name;
            showCoursePreview(name, image || null);
            dropdownList.style.display = 'none';
        });
    });
}

// Setup course search input event listeners
document.addEventListener('DOMContentLoaded', () => {
    const courseSearchInput = document.getElementById('courseSearchInput_cert');
    const dropdownList = document.getElementById('courseDropdownList');

    if (!courseSearchInput) return;

    // On focus — show dropdown
    courseSearchInput.addEventListener('focus', async () => {
        const courses = await fetchAllCourses();
        renderCourseDropdown(courses, courseSearchInput.value);
    });

    // On input — filter dropdown with debounce
    courseSearchInput.addEventListener('input', () => {
        clearTimeout(courseSearchDebounce);
        courseSearchDebounce = setTimeout(async () => {
            const courses = await fetchAllCourses();
            renderCourseDropdown(courses, courseSearchInput.value);

            // Update hidden value as user types (for manual entry if needed)
            document.getElementById('courseName').value = courseSearchInput.value;
        }, 200);
    });

    // Close dropdown when clicking outside
    document.addEventListener('click', (e) => {
        if (dropdownList && !e.target.closest('#courseDropdownContainer')) {
            dropdownList.style.display = 'none';
        }
    });
});

