// Global variables
let currentPage = 0;
let pageSize = 25;
let totalPages = 0;
let totalElements = 0;
let selectedCertificateId = null;
let importedCertData = [];
let allCourses = [];

// Initialize on page load
document.addEventListener('DOMContentLoaded', function() {
    initializeEventListeners();
    updateStats();
    loadCourses().then(() => {
            initializeCourseSearch();
        });
    loadCertificates();
    updateStatsForCurrentView();
});

async function updateStatsForCurrentView() {
    const course = document.getElementById('courseFilter')?.value || '';
    const status = document.getElementById('statusFilter')?.value || '';
    const search = document.getElementById('searchInput')?.value || '';

    try {
        const params = new URLSearchParams({
            ...(course && { course }),
            ...(status && { status }),
            ...(search && { search })
        });

        const response = await fetch(`/api/certificates/stats-filtered?${params}`);
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

function initializeEventListeners() {
    // Filter changes
    document.getElementById('courseFilter')?.addEventListener('change', () => {
        currentPage = 0;
        loadCertificates();
         updateStatsForCurrentView();
    });

    document.getElementById('statusFilter')?.addEventListener('change', () => {
        currentPage = 0;
        loadCertificates();
         updateStatsForCurrentView();
    });

    document.getElementById('pageSizeSelect')?.addEventListener('change', function() {
        pageSize = parseInt(this.value);
        currentPage = 0;
        loadCertificates();
         updateStatsForCurrentView();
    });

    // Search with debounce
    let searchTimeout;
    document.getElementById('searchInput')?.addEventListener('input', function() {
        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(() => {
            currentPage = 0;
            loadCertificates();
             updateStatsForCurrentView();
        }, 500);
    });

    function makeSelectSearchable() {
        const courseFilter = document.getElementById('courseFilter');
        if (!courseFilter) return;

        // Simple native search implementation
        const wrapper = document.createElement('div');
        wrapper.style.position = 'relative';
        wrapper.style.width = '100%';

        const searchInput = document.createElement('input');
        searchInput.type = 'text';
        searchInput.className = 'form-control mb-2';
        searchInput.placeholder = 'Search courses...';

        courseFilter.parentNode.insertBefore(wrapper, courseFilter);
        wrapper.appendChild(searchInput);
        wrapper.appendChild(courseFilter);

        const allOptions = Array.from(courseFilter.options);

        searchInput.addEventListener('input', function() {
            const searchTerm = this.value.toLowerCase();

            courseFilter.innerHTML = '<option value="">-- All Courses --</option>';

            allOptions.slice(1).forEach(option => {
                if (option.text.toLowerCase().includes(searchTerm)) {
                    courseFilter.appendChild(option.cloneNode(true));
                }
            });
        });
    }

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
        csvCertFileInput.addEventListener('change', function(e) {
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

function initializeCourseSearch() {
    const searchInput = document.getElementById('courseSearchInput');
    const courseFilter = document.getElementById('courseFilter');

    if (!searchInput || !courseFilter) return;

    // Store original options
    const allOptions = Array.from(courseFilter.options);

    searchInput.addEventListener('input', function() {
        const searchTerm = this.value.toLowerCase().trim();

        // Clear current options except "All Courses"
        courseFilter.innerHTML = '<option value="">-- All Courses --</option>';

        // Filter and add matching options
        allOptions.slice(1).forEach(option => {
            if (option.text.toLowerCase().includes(searchTerm)) {
                courseFilter.appendChild(option.cloneNode(true));
            }
        });
    });
}

// Load courses from backend
async function loadCourses() {
    try {
        const response = await fetch('/api/courses?page=0&size=1000');
        if (!response.ok) throw new Error('Failed to load courses');

        const data = await response.json();
        allCourses = data.courses || [];

        const courseFilter = document.getElementById('courseFilter');
        if (courseFilter) {
            courseFilter.innerHTML = '<option value="">-- All Courses --</option>';
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
        const course = document.getElementById('courseFilter')?.value || '';
        const status = document.getElementById('statusFilter')?.value || '';
        const search = document.getElementById('searchInput')?.value || '';

        const params = new URLSearchParams({
            page: currentPage,
            size: pageSize,
            ...(course && { course }),
            ...(status && { status }),
            ...(search && { search })
        });

        const response = await fetch(`/api/certificates?${params}`);
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
            updatePaginationInfo(0, 0);
            return;
        }

        tbody.innerHTML = certificates.map(cert => `
            <tr>
                <td><strong>${cert.registrationNo}</strong></td>
                <td>${cert.certificateNo || '<span class="text-muted">Not Issued</span>'}</td>
                <td>${cert.studentName}</td>
                <td><span class="badge bg-primary">${cert.courseName}</span></td>
                <td>${cert.batch || '-'}</td>
                <td>${cert.grade || '<span class="text-muted">-</span>'}</td>
                <td>${cert.issueDate ? new Date(cert.issueDate).toLocaleDateString() : '<span class="text-muted">-</span>'}</td>
                <td>
                    <span class="badge ${cert.status === 'Issued' ? 'bg-success' : 'bg-warning'}">
                        ${cert.status}
                    </span>
                </td>
                <td>
                    <div class="action-dropdown">
                        <button class="btn btn-light action-menu-trigger" style="padding: 0.25rem 0.5rem;">
                            <i class="bi bi-three-dots-vertical"></i>
                        </button>
                        <div class="action-menu">
                            <button class="action-menu-item" onclick="issueCertificate(${cert.id})">
                                <i class="bi bi-pencil-square"></i><span>${cert.status === 'Issued' ? 'Edit Certificate' : 'Issue Certificate'}</span>
                            </button>
                            ${cert.status === 'Issued' ? `
                            <button class="action-menu-item" onclick="viewCertificate(${cert.id})">
                                <i class="bi bi-eye"></i><span>View Certificate</span>
                            </button>
                            <button class="action-menu-item" onclick="sendCertificateEmail(${cert.id})">
                                <i class="bi bi-envelope"></i><span>Send Email</span>
                            </button>
                            ` : ''}
                            <button class="action-menu-item" onclick="printCertificate(${cert.id})">
                                <i class="bi bi-printer"></i><span>Print Certificate</span>
                            </button>
                            <button class="action-menu-item text-danger" onclick="deleteCertificate(${cert.id})">
                                <i class="bi bi-trash"></i><span>Delete</span>
                            </button>
                        </div>
                    </div>
                </td>
            </tr>
        `).join('');

        // Attach menu triggers
        document.querySelectorAll('.action-menu-trigger').forEach(trigger => {
            trigger.addEventListener('click', function(e) {
                e.stopPropagation();
                const menu = this.nextElementSibling;
                document.querySelectorAll('.action-menu').forEach(m => {
                    if (m !== menu) m.classList.remove('show');
                });
                menu.classList.toggle('show');
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
        const response = await fetch('/api/certificates/stats');
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
        const response = await fetch(`/api/certificates/${id}`);
        if (!response.ok) throw new Error('Failed to load certificate');

        const certificate = await response.json();
        selectedCertificateId = id;

        // Populate modal
        document.getElementById('studentName').value = certificate.studentName;
        document.getElementById('courseName').value = certificate.courseName;
        document.getElementById('grade').value = certificate.grade || '';
        document.getElementById('certificateNo').value = certificate.certificateNo || 'AUTO-GENERATED';

        if (certificate.issueDate) {
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

// Save certificate
async function saveCertificate() {
    const form = document.getElementById('certificateForm');

    if (!form.checkValidity()) {
        form.reportValidity();
        return;
    }

    const certificateData = {
        certificateNo: document.getElementById('certificateNo').value,
        grade: document.getElementById('grade').value || null,
        issueDate: document.getElementById('issueDate').value,
        courseFromDate: document.getElementById('courseFromDate').value,
        courseToDate: document.getElementById('courseToDate').value,
        notes: document.getElementById('certificateNotes').value
    };

    try {
        const response = await fetch(`/api/certificates/${selectedCertificateId}/issue`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(certificateData)
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'Failed to issue certificate');
        }

        Swal.fire({
            title: 'Success!',
            text: 'Certificate issued successfully!',
            icon: 'success',
            confirmButtonColor: '#667eea'
        });

        bootstrap.Modal.getInstance(document.getElementById('certificateModal')).hide();
        loadCertificates();
        updateStats();

    } catch (error) {
        console.error('Error issuing certificate:', error);
        Swal.fire({
            title: 'Error',
            text: error.message,
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
            const response = await fetch(`/api/certificates/${id}`, {
                method: 'DELETE'
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

    // ✅ Open print view in new window
    window.open(`/certificates/print/${id}`, '_blank', 'width=1400,height=900');
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

        // ✅ Check if certificate is issued
        if (certificate.status !== 'Issued') {
            Swal.fire({
                title: 'Certificate Not Issued',
                text: 'This certificate has not been issued yet. Please issue the certificate first.',
                icon: 'warning',
                confirmButtonColor: '#667eea'
            });
            return;
        }

        // ✅ Open certificate in new window
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

// Send certificate email
async function sendCertificateEmail(id) {
    document.querySelectorAll('.action-menu').forEach(menu => {
        menu.classList.remove('show');
    });

    try {
        // ✅ Fetch certificate data first to get email
        const response = await fetch(`/api/certificates/${id}`);
        if (!response.ok) throw new Error('Failed to load certificate');

        const certificate = await response.json();

        const result = await Swal.fire({
            title: 'Send Certificate Email',
            html: `
                <div style="text-align: left;">
                    <label class="form-label">Enter student's email address:</label>
                    <input type="email" id="studentEmail" class="form-control"
                           placeholder="student@example.com"
                           value="${certificate.studentEmail || ''}"
                           required>
                </div>
            `,
            icon: 'question',
            showCancelButton: true,
            confirmButtonColor: '#667eea',
            cancelButtonColor: '#6c757d',
            confirmButtonText: 'Send Email',
            preConfirm: () => {
                const email = document.getElementById('studentEmail').value;
                if (!email || !email.includes('@')) {
                    Swal.showValidationMessage('Please enter a valid email address');
                    return false;
                }
                return email;
            }
        });

        if (result.isConfirmed) {
            Swal.fire({
                title: 'Sending...',
                text: 'Please wait while we send the certificate',
                allowOutsideClick: false,
                didOpen: () => Swal.showLoading()
            });

            const emailResponse = await fetch(`/api/certificates/${id}/send-email`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ email: result.value })
            });

            if (!emailResponse.ok) {
                const error = await emailResponse.json();
                throw new Error(error.message || error.error || 'Failed to send email');
            }

            Swal.fire({
                title: 'Success!',
                text: 'Certificate has been sent successfully!',
                icon: 'success',
                confirmButtonColor: '#667eea'
            });
        }

    } catch (error) {
        console.error('Error sending email:', error);
        Swal.fire({
            title: 'Error',
            text: error.message || 'Failed to send email',
            icon: 'error',
            confirmButtonColor: '#667eea'
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

// FIXED: Parse CSV with correct format
function parseCertCSV(text) {
    const lines = text.split('\n').filter(line => line.trim());
    importedCertData = [];
    const previewData = [];

    for (let i = 1; i < lines.length; i++) {
        const values = lines[i].match(/(".*?"|[^,\t]+)(?=\s*[,\t]|\s*$)/g) || [];
        const row = values.map(v => v.trim().replace(/^"|"$/g, ''));

        if (row.length >= 3) { // Minimum: RegNo, CertNo, StudentName
            const record = {
                registrationNo: row[0] || null,
                certificateNo: row[1] || null,
                studentName: row[2] || null,
                courseName: row[3] || null,
                batch: row[4] || null,
                grade: row[5] || null,
                issueDate: row[6] || null,
                status: row[7] || 'Not Issued' // Default status
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

// FIXED: Display preview with correct columns
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

        const response = await fetch('/api/certificates/import', {
            method: 'POST',
            body: formData
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

// FIXED: Convert to correct CSV format
function convertToCSV(data) {
    const header = 'Reg No,Certificate No,Student Name,Course,Batch,Grade,Issue Date,Status\n';
    const rows = data.map(row =>
        `${row.registrationNo || ''},${row.certificateNo || ''},${row.studentName || ''},${row.courseName || ''},${row.batch || ''},${row.grade || ''},${row.issueDate || ''},${row.status || 'Not Issued'}`
    ).join('\n');
    return header + rows;
}

async function exportCertificatesCSV() {
    try {
        Swal.fire({
            title: 'Exporting...',
            text: 'Preparing CSV file',
            allowOutsideClick: false,
            didOpen: () => Swal.showLoading()
        });

        const response = await fetch('/api/certificates/export/csv');

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'Export failed');
        }

        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `certificates_${Date.now()}.csv`;
        link.click();
        window.URL.revokeObjectURL(url);

        Swal.fire({
            title: 'Success!',
            text: 'CSV exported successfully!',
            icon: 'success',
            timer: 2000,
            showConfirmButton: false
        });
    } catch (error) {
        console.error('Export error:', error);
        Swal.fire({
            title: 'Error',
            text: error.message || 'Failed to export CSV',
            icon: 'error',
            confirmButtonColor: '#667eea'
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

    const start = showing > 0 ? (currentPage * pageSize) + 1 : 0;
    const end = (currentPage * pageSize) + showing;

    if (startEl) startEl.textContent = start;
    if (endEl) endEl.textContent = end;
    if (totalEl) totalEl.textContent = total;
}

function renderPagination() {
    const paginationControls = document.getElementById('paginationControls');
    if (!paginationControls) return;

    let html = '';

    // Previous button
    html += `
        <li class="page-item ${currentPage === 0 ? 'disabled' : ''}">
            <a class="page-link" href="#" onclick="changePage(${currentPage - 1}); return false;">Previous</a>
        </li>
    `;

    // Page numbers
    const maxPages = 5;
    let startPage = Math.max(0, currentPage - 2);
    let endPage = Math.min(totalPages - 1, startPage + maxPages - 1);

    if (endPage - startPage < maxPages - 1) {
        startPage = Math.max(0, endPage - maxPages + 1);
    }

    for (let i = startPage; i <= endPage; i++) {
        html += `
            <li class="page-item ${i === currentPage ? 'active' : ''}">
                <a class="page-link" href="#" onclick="changePage(${i}); return false;">${i + 1}</a>
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