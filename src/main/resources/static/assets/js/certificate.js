// Global variables
let currentPage = 0;
let pageSize = 25;
let totalPages = 0;
let totalElements = 0;
let selectedCertificate = null;
let importedCertData = [];

// Initialize on page load
document.addEventListener('DOMContentLoaded', function() {
    initializeEventListeners();
    loadCertificates();
    updateStats();
});

function initializeEventListeners() {
    // Filter changes
    document.getElementById('courseFilter')?.addEventListener('change', () => {
        currentPage = 0;
        loadCertificates();
    });

    document.getElementById('statusFilter')?.addEventListener('change', () => {
        currentPage = 0;
        loadCertificates();
    });

    document.getElementById('pageSizeSelect')?.addEventListener('change', function() {
        pageSize = parseInt(this.value);
        currentPage = 0;
        loadCertificates();
    });

    // Search with debounce
    let searchTimeout;
    document.getElementById('searchInput')?.addEventListener('input', function() {
        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(() => {
            currentPage = 0;
            loadCertificates();
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

    // Import/Export CSV - Check if elements exist before adding listeners
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

function openImportCertModal() {
    const modalEl = document.getElementById('importCertificateModal');
    if (!modalEl) {
        console.error('Import modal not found in DOM');
        Swal.fire({
            title: 'Error',
            text: 'Import modal not available',
            icon: 'error',
            confirmButtonColor: '#667eea'
        });
        return;
    }

    // Reset the import state
    resetCertImport();

    // Create and show modal
    const modal = new bootstrap.Modal(modalEl);
    modal.show();
}

function handleCertCSVFile(file) {
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (e) => parseCertCSV(e.target.result);
    reader.readAsText(file);
}

function parseCertCSV(text) {
    const lines = text.split('\n').filter(line => line.trim());
    importedCertData = [];
    const previewData = [];

    for (let i = 1; i < lines.length; i++) {
        const values = lines[i].match(/(".*?"|[^,]+)(?=\s*,|\s*$)/g) || [];
        const row = values.map(v => v.trim().replace(/^"|"$/g, ''));

        if (row.length >= 8) {
            const record = {
                registrationNo: row[0],
                certificateNo: row[1],
                studentName: row[2],
                courseName: row[3],
                batch: row[4],
                grade: row[5],
                issueDate: row[6],
                status: row[7]
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

function displayCertPreview(data) {
    const thead = document.getElementById('previewCertTableHead');
    const tbody = document.getElementById('previewCertTableBody');

    if (!thead || !tbody) return;

    thead.innerHTML = '<tr><th>Reg No</th><th>Cert No</th><th>Student</th><th>Course</th><th>Grade</th></tr>';
    tbody.innerHTML = data.map(row => `
        <tr>
            <td>${row.registrationNo}</td>
            <td>${row.certificateNo || '-'}</td>
            <td>${row.studentName}</td>
            <td>${row.courseName}</td>
            <td>${row.grade || '-'}</td>
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

        const response = await fetch('/api/certificates/import', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(importedCertData)
        });

        if (!response.ok) throw new Error('Import failed');

        const result = await response.json();

        Swal.fire({
            title: 'Success!',
            text: `Successfully imported ${result.count || importedCertData.length} certificates`,
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
            text: 'Failed to import certificates',
            icon: 'error',
            confirmButtonColor: '#667eea'
        });
    }
}

async function exportCertificatesCSV() {
    try {
        const response = await fetch('/api/certificates/export/csv');
        if (!response.ok) throw new Error('Export failed');

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
            text: 'Failed to export CSV',
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

// Load certificates from server
function loadCertificates() {
    const tbody = document.querySelector('#certificateTable tbody');
    if (!tbody) return;

    // Sample data for demonstration
    const sampleData = [
        {
            registrationNo: 'REG001',
            certificateNo: 'CERT2024001',
            studentName: 'Rahul Sharma',
            courseName: 'JAVA CORE AND ADVANCE',
            batch: 'Batch-A-2024',
            grade: 'A+',
            issueDate: '2024-01-15',
            status: 'Issued'
        },
        {
            registrationNo: 'REG002',
            certificateNo: '',
            studentName: 'Priya Patel',
            courseName: 'PYTHON',
            batch: 'Batch-B-2024',
            grade: '',
            issueDate: '',
            status: 'Pending'
        },
        {
            registrationNo: 'REG003',
            certificateNo: 'CERT2024002',
            studentName: 'Amit Kumar',
            courseName: 'WEB DEVELOPMENT',
            batch: 'Batch-A-2024',
            grade: 'A',
            issueDate: '2024-01-20',
            status: 'Issued'
        }
    ];

    tbody.innerHTML = sampleData.map((cert, index) => `
        <tr>
            <td><strong>${cert.registrationNo}</strong></td>
            <td>${cert.certificateNo || '<span class="text-muted">Not Issued</span>'}</td>
            <td>${cert.studentName}</td>
            <td><span class="badge bg-primary">${cert.courseName}</span></td>
            <td>${cert.batch}</td>
            <td>${cert.grade || '<span class="text-muted">-</span>'}</td>
            <td>${cert.issueDate || '<span class="text-muted">-</span>'}</td>
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
                        <button class="action-menu-item" onclick="issueCertificate(${index})">
                            <i class="bi bi-pencil-square"></i><span>Issue Certificate</span>
                        </button>
                        <button class="action-menu-item" onclick="printCertificate(${index})">
                            <i class="bi bi-printer"></i><span>Print Certificate</span>
                        </button>
                        <button class="action-menu-item" onclick="viewBatch(${index})">
                            <i class="bi bi-people"></i><span>View Batch</span>
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

    updatePaginationInfo(sampleData.length, sampleData.length);
}

function updateStats() {
    // Sample stats
    const totalEl = document.getElementById('totalCount');
    const issuedEl = document.getElementById('issuedCount');
    const pendingEl = document.getElementById('pendingCount');

    if (totalEl) totalEl.textContent = '150';
    if (issuedEl) issuedEl.textContent = '120';
    if (pendingEl) pendingEl.textContent = '30';
}

function issueCertificate(index) {
    // Close action menu
    document.querySelectorAll('.action-menu').forEach(menu => {
        menu.classList.remove('show');
    });

    // Populate modal with student data
    document.getElementById('studentName').value = 'Student Name ' + (index + 1);
    document.getElementById('courseName').value = 'Course Name';
    document.getElementById('grade').value = '';
    document.getElementById('certificateNo').value = 'AUTO-' + Date.now();
    document.getElementById('issueDate').valueAsDate = new Date();
    document.getElementById('courseFromDate').value = '';
    document.getElementById('courseToDate').value = '';
    document.getElementById('certificateNotes').value = '';

    const modal = new bootstrap.Modal(document.getElementById('certificateModal'));
    modal.show();
}

function saveCertificate() {
    const form = document.getElementById('certificateForm');

    if (!form.checkValidity()) {
        form.reportValidity();
        return;
    }

    const certificateData = {
        grade: document.getElementById('grade').value,
        certificateNo: document.getElementById('certificateNo').value,
        issueDate: document.getElementById('issueDate').value,
        courseFromDate: document.getElementById('courseFromDate').value,
        courseToDate: document.getElementById('courseToDate').value,
        notes: document.getElementById('certificateNotes').value
    };

    Swal.fire({
        title: 'Success!',
        text: 'Certificate issued successfully!',
        icon: 'success',
        confirmButtonColor: '#667eea'
    });

    bootstrap.Modal.getInstance(document.getElementById('certificateModal')).hide();
    loadCertificates();
    updateStats();
}

function printCertificate(index) {
    document.querySelectorAll('.action-menu').forEach(menu => {
        menu.classList.remove('show');
    });

    Swal.fire({
        title: 'Print Certificate',
        text: 'Opening certificate for printing...',
        icon: 'info',
        timer: 1500,
        showConfirmButton: false
    });

    // Simulate opening print window
    setTimeout(() => {
        window.open('certificate-print.html', '_blank');
    }, 1500);
}

function viewBatch(index) {
    document.querySelectorAll('.action-menu').forEach(menu => {
        menu.classList.remove('show');
    });

    document.getElementById('batchDetailsContent').innerHTML = `
        <div class="list-group">
            <div class="list-group-item">
                <strong>Batch Name:</strong> Batch-A-2024
            </div>
            <div class="list-group-item">
                <strong>Start Date:</strong> 2024-01-01
            </div>
            <div class="list-group-item">
                <strong>End Date:</strong> 2024-06-30
            </div>
            <div class="list-group-item">
                <strong>Students:</strong> 25
            </div>
        </div>
    `;

    const modal = new bootstrap.Modal(document.getElementById('batchModal'));
    modal.show();
}

function updatePaginationInfo(showing, total) {
    const startEl = document.getElementById('entriesStart');
    const endEl = document.getElementById('entriesEnd');
    const totalEl = document.getElementById('totalEntries');

    if (startEl) startEl.textContent = showing > 0 ? 1 : 0;
    if (endEl) endEl.textContent = showing;
    if (totalEl) totalEl.textContent = total;
}