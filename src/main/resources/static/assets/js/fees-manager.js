// fees-manager.js

let feesData = [];
let allCourses = [];
let filteredCourses = [];
let currentPage = 1;
let entriesPerPage = 25;
let filteredData = [];
let currentStudentId = null;
let importedFeesData = [];
// CSRF Token Configuration
let csrfToken = null;
let csrfHeader = null;

// Get CSRF token from meta tags
function getCsrfToken() {
    if (!csrfToken) {
        csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
        csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');
    }
    return { token: csrfToken, header: csrfHeader };
}

// Get CSRF headers object for fetch requests
function getCsrfHeaders() {
    const csrf = getCsrfToken();
    const headers = {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
    };

    if (csrf.token && csrf.header) {
        headers[csrf.header] = csrf.token;
    }

    return headers;
}

let banks = [];
let paymentModes = [];

const API_BASE = '/api/fees-manager';

function showLoading(message) {
    Swal.fire({
        title: message,
        allowOutsideClick: false,
        didOpen: () => Swal.showLoading()
    });
}

function showSuccess(message) {
    Swal.fire({
        icon: 'success',
        title: 'Success!',
        text: message,
        confirmButtonColor: '#667eea',
        timer: 2000
    });
}

function showError(message) {
    Swal.fire({
        icon: 'error',
        title: 'Error!',
        text: message,
        confirmButtonColor: '#ef4444'
    });
}

// Load banks from API
async function loadBanks() {
    try {
       const response = await fetch('/bank/list?page=0&size=100', {
           headers: getCsrfHeaders()
       });

        if (response.ok) {
            const result = await response.json();
            console.log('Banks response:', result);

            // Handle different response formats
            if (result.success && result.data) {
                banks = result.data;
            } else if (result.content && Array.isArray(result.content)) {
                banks = result.content;
            } else if (Array.isArray(result)) {
                banks = result;
            } else {
                banks = [];
            }

            console.log('Loaded banks:', banks.length);
            populateBankDropdowns();
        }
    } catch (error) {
        console.error('Error loading banks:', error);
        banks = [];
        // Set default banks if API fails
        banks = [
            { id: 1, bankName: 'SBI', isActive: true },
            { id: 2, bankName: 'HDFC', isActive: true },
            { id: 3, bankName: 'ICICI', isActive: true },
            { id: 4, bankName: 'Axis Bank', isActive: true }
        ];
        populateBankDropdowns();
    }
}

// Load payment modes from API
async function loadPaymentModes() {
    try {
        const response = await fetch('/api/payment-modes/active', {
            headers: getCsrfHeaders()
        });

        if (response.ok) {
            const result = await response.json();
            console.log('Payment modes response:', result);

            paymentModes = Array.isArray(result) ? result : [];
            console.log('Loaded payment modes:', paymentModes.length); // Debug log
            populatePaymentModeDropdowns();
        }
    } catch (error) {
        console.error('Error loading payment modes:', error);
        paymentModes = [];
        // Set default payment modes if API fails
        paymentModes = [
            { id: 1, paymentModeTitle: 'UPI', isActive: true },
            { id: 2, paymentModeTitle: 'NEFT', isActive: true },
            { id: 3, paymentModeTitle: 'RTGS', isActive: true },
            { id: 4, paymentModeTitle: 'IMPS', isActive: true }
        ];
        populatePaymentModeDropdowns();
    }
}

// Populate bank dropdowns
function populateBankDropdowns() {
    const bankSelects = ['bankName', 'refundBankName'];

    bankSelects.forEach(selectId => {
        const select = document.getElementById(selectId);
        if (select) {
            select.innerHTML = '<option value="">-- Select Bank --</option>';
            banks.forEach(bank => {
                const option = document.createElement('option');
                option.value = bank.bankName;
                option.textContent = bank.bankName;
                select.appendChild(option);
            });
        }
    });
}

// Populate payment mode dropdowns
function populatePaymentModeDropdowns() {
    const paymentModeSelects = ['onlinePaymentMode', 'refundOnlinePaymentMode'];

    paymentModeSelects.forEach(selectId => {
        const select = document.getElementById(selectId);
        if (select) {
            const currentValue = select.value;
            select.innerHTML = '<option value="">-- Select Mode --</option>';

            paymentModes.forEach(mode => {
                if (mode.isActive !== false) {
                    const option = document.createElement('option');
                    option.value = mode.paymentModeTitle;
                    option.textContent = mode.paymentModeTitle;
                    select.appendChild(option);
                }
            });

            // Restore previous selection if it exists
            if (currentValue) {
                select.value = currentValue;
            }

            console.log(`Populated ${selectId} with ${select.options.length - 1} modes`);
        }
    });
}

// Initialize
document.addEventListener('DOMContentLoaded', function() {
    initializeEventListeners();
    loadFeesFromBackend();
    loadCoursesForFilter();
    setDefaultDates();
});

function debugAPICall(endpoint, method = 'GET') {
    console.log(`🌐 API Call: ${method} ${API_BASE}${endpoint}`);
}

function initializeEventListeners() {

    loadBanks();
    loadPaymentModes();

    // Search
   document.getElementById('searchInput').addEventListener('input', applyFilters);

    // Entries per page
    document.getElementById('entriesPerPage').addEventListener('change', function() {
        entriesPerPage = parseInt(this.value);
        currentPage = 1;
        renderTable();
    });

    // Import Type Selection
    document.querySelectorAll('input[name="feesImportType"]').forEach(radio => {
        radio.addEventListener('change', handleFeesImportTypeChange);
    });

    // Import/Export
    document.getElementById('btnImportCSV').addEventListener('click', () => {
        new bootstrap.Modal(document.getElementById('importModal')).show();
    });
    document.getElementById('btnExportCSV').addEventListener('click', exportToCSV);

    // Import Button
    document.getElementById('importBtn').addEventListener('click', importFeesCSV);

    // Browse File Button
    document.getElementById('btnBrowseFile')?.addEventListener('click', () => {
        document.getElementById('csvFileInput').click();
    });

    // File Input Change
    document.getElementById('csvFileInput')?.addEventListener('change', function(e) {
        handleFeesCSVFile(e.target.files[0]);
    });

    // Drag and drop
    const importArea = document.getElementById('importArea');
    if (importArea) {
        importArea.addEventListener('dragover', handleDragOver);
        importArea.addEventListener('dragleave', handleDragLeave);
        importArea.addEventListener('drop', handleDrop);
    }

    // Fee Receipt Modal
    document.getElementById('enableGst').addEventListener('change', toggleGstFields);
    document.getElementById('paymentMode').addEventListener('change', togglePaymentFields);
    document.getElementById('btnSaveReceipt').addEventListener('click', saveReceipt);

    // Change Status Modal
    document.getElementById('btnSaveStatus').addEventListener('click', saveStatus);

    // Installments Modal
    document.getElementById('btnGenerateInstallments').addEventListener('click', generateInstallments);
    document.getElementById('btnSaveInstallments').addEventListener('click', saveInstallments);

    // Refund Modal
    document.getElementById('refundPaymentMode').addEventListener('change', toggleRefundPaymentFields);
    document.getElementById('btnSaveRefund').addEventListener('click', saveRefund);
    document.getElementById('btnSaveRefundPrint').addEventListener('click', saveAndPrintRefund);

    // Status and Course filters
    document.getElementById('statusFilter')?.addEventListener('change', applyFilters);
    document.getElementById('courseFilter')?.addEventListener('change', applyFilters);
    document.getElementById('courseSearchInput')?.addEventListener('input', filterCourseList);
}

// ==================== FILTERS ====================

async function loadCoursesForFilter() {
    try {
        const response = await fetch('/api/courses/dropdown', {
            headers: getCsrfHeaders()
        });

        if (response.ok) {
            allCourses = await response.json();
            filteredCourses = [...allCourses];
            populateCourseFilter();
        }
    } catch (error) {
        console.error('Error loading courses:', error);
    }
}

function populateCourseFilter() {
    const select = document.getElementById('courseFilter');
    if (!select) return;

    select.innerHTML = '<option value="">-- All Courses --</option>';

    filteredCourses.forEach(course => {
        const option = document.createElement('option');
        option.value = course.courseName;
        option.textContent = course.courseName;
        select.appendChild(option);
    });
}

function filterCourseList() {
    const searchTerm = document.getElementById('courseSearchInput').value.toLowerCase();

    if (!searchTerm) {
        filteredCourses = [...allCourses];
    } else {
        filteredCourses = allCourses.filter(course =>
            course.courseName.toLowerCase().includes(searchTerm)
        );
    }

    populateCourseFilter();
}

function applyFilters() {
    const statusFilter = document.getElementById('statusFilter')?.value || '';
    const courseFilter = document.getElementById('courseFilter')?.value || '';
    const searchTerm = document.getElementById('searchInput').value.toLowerCase();

    filteredData = feesData.filter(item => {
        // Search filter
        const matchesSearch = !searchTerm ||
            item.regNo.toLowerCase().includes(searchTerm) ||
            item.studentName.toLowerCase().includes(searchTerm) ||
            item.mobile.includes(searchTerm) ||
            (item.course && item.course.toLowerCase().includes(searchTerm));

        // Status filter
        const matchesStatus = !statusFilter || item.status === statusFilter;

        // Course filter
        const matchesCourse = !courseFilter ||
            (item.course && item.course.includes(courseFilter));

        return matchesSearch && matchesStatus && matchesCourse;
    });

    currentPage = 1;
    renderTable();
}

// ==================== IMPORT FUNCTIONS ====================

function handleFeesImportTypeChange() {
    const oldFormatInfo = document.getElementById('oldFeesFormatInfo');
    const newFormatInfo = document.getElementById('newFeesFormatInfo');
    const isOld = this.value === 'old';

    oldFormatInfo.style.display = isOld ? 'block' : 'none';
    newFormatInfo.style.display = isOld ? 'none' : 'block';

    resetFeesImport();
}

function resetFeesImport() {
    document.getElementById('importPreview').style.display = 'none';
    document.getElementById('csvFileInput').value = '';
    document.getElementById('importBtn').disabled = true;
    importedFeesData = [];
}

function handleFeesCSVFile(file) {
    if (!file) return;

    if (!file.name.endsWith('.csv')) {
        Swal.fire({
            icon: 'error',
            title: 'Invalid File',
            text: 'Please upload a valid CSV file',
            confirmButtonColor: '#667eea'
        });
        return;
    }

    const reader = new FileReader();
    reader.onload = (e) => parseFeesCSV(e.target.result);
    reader.onerror = () => {
        Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'Failed to read file',
            confirmButtonColor: '#667eea'
        });
    };
    reader.readAsText(file);
}

function parseFeesCSV(text) {
    const lines = text.split('\n').filter(line => line.trim());

    importedFeesData = [];
    const previewData = [];

    // Skip header row
    for (let i = 1; i < lines.length; i++) {
        const values = lines[i].match(/(".*?"|[^,]+)(?=\s*,|\s*$)/g) || [];
        const row = values.map(v => v.trim().replace(/^"|"$/g, ''));

        if (row.length > 0) {
            const record = {
                registrationNumber: row[0] || '',
                studentName: row[1] || '',
                mobile: row[2] || '',
                totalFees: parseFloat(row[3]) || 0,
                feesDue: parseFloat(row[4]) || 0,
                totalPaid: parseFloat(row[5]) || 0,
                dueDate: row[6] || null,
                feesRefund: parseFloat(row[7]) || 0,
                status: row[8] || 'Pending',
                course: row[9] || ''
            };

            // Validate required fields
            if (record.registrationNumber || record.mobile) {
                importedFeesData.push(record);
                if (previewData.length < 5) previewData.push(record);
            } else {
                console.warn(`Row ${i}: Missing required fields, skipping`);
            }
        }
    }

    displayFeesPreview(previewData);
    document.getElementById('recordCount').textContent = importedFeesData.length;
    document.getElementById('importBtn').disabled = importedFeesData.length === 0;
}

function displayFeesPreview(data) {
    const thead = document.getElementById('previewTableHead');
    const tbody = document.getElementById('previewTableBody');

    thead.innerHTML = '<tr><th>Reg No.</th><th>Student Name</th><th>Mobile</th><th>Total Fees</th><th>Status</th></tr>';
    tbody.innerHTML = data.map(row => `
        <tr>
            <td>${row.registrationNumber || '-'}</td>
            <td>${row.studentName || '-'}</td>
            <td>${row.mobile || '-'}</td>
            <td>₹${row.totalFees?.toLocaleString() || '0'}</td>
            <td><span class="badge ${row.status === 'Clear' ? 'bg-success' : 'bg-warning'}">${row.status || 'Pending'}</span></td>
        </tr>
    `).join('');

    document.getElementById('importPreview').style.display = 'block';
}

async function importFeesCSV() {
    if (importedFeesData.length === 0) {
        Swal.fire({
            icon: 'error',
            title: 'No Data',
            text: 'No valid data to import',
            confirmButtonColor: '#667eea'
        });
        return;
    }

    try {
        Swal.fire({
            title: 'Importing...',
            text: `Processing ${importedFeesData.length} records`,
            allowOutsideClick: false,
            didOpen: () => Swal.showLoading()
        });

        // Send to backend
       const response = await fetch('/api/fees-manager/bulk-import-json', {
           method: 'POST',
           headers: getCsrfHeaders(),
           body: JSON.stringify(importedFeesData)
       });

        const result = await response.json();

        Swal.close();
        bootstrap.Modal.getInstance(document.getElementById('importModal')).hide();

        if (result.success) {
            Swal.fire({
                icon: 'success',
                title: 'Import Complete!',
                html: `
                    <div class="text-start">
                        <p><strong>Total Records:</strong> ${result.totalRecords}</p>
                        <p><strong>Successfully Imported:</strong> ${result.successfulImports}</p>
                        <p><strong>Failed:</strong> ${result.failedImports}</p>
                    </div>
                `,
                confirmButtonColor: '#667eea'
            }).then(() => {
                // Reload the fees table
                loadFeesFromBackend();
                resetFeesImport();
            });
        } else {
            Swal.fire({
                icon: 'warning',
                title: 'Import Completed with Errors',
                html: `
                    <div class="text-start">
                        <p><strong>Total Records:</strong> ${result.totalRecords}</p>
                        <p><strong>Successfully Imported:</strong> ${result.successfulImports}</p>
                        <p><strong>Failed:</strong> ${result.failedImports}</p>
                        ${result.errors && result.errors.length > 0 ?
                            `<p class="mt-2"><strong>Errors:</strong></p>
                             <ul class="small">${result.errors.slice(0, 5).map(e =>
                                `<li>Row ${e.rowNumber}: ${e.errorMessage}</li>`
                             ).join('')}</ul>` : ''
                        }
                    </div>
                `,
                confirmButtonColor: '#667eea'
            }).then(() => {
                loadFeesFromBackend();
                resetFeesImport();
            });
        }

    } catch (error) {
        Swal.close();
        console.error('Import error:', error);
        Swal.fire({
            icon: 'error',
            title: 'Import Failed',
            text: error.message || 'Failed to import data',
            confirmButtonColor: '#ef4444'
        });
    }
}

async function loadFeesFromBackend() {
    try {
       const response = await fetch('/api/fees-manager?page=0&size=100000', {
           headers: getCsrfHeaders()
       });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const data = await response.json();

        if (data.content && Array.isArray(data.content)) {
            feesData = data.content.map(item => ({
                admissionId: item.regNo,
                regNo: item.registrationNumber,
                studentName: item.studentName,
                mobile: item.mobile,
                totalFees: item.totalFees || 0,
                feesDue: item.feesDue || 0,
                totalPaid: item.totalPaid || 0,
                dueDate: item.dueDate,
                feesRefund: item.feesRefund || 0,
                status: item.status || 'Pending',
                course: item.course || 'N/A'
            }));
            filteredData = [...feesData];
            renderTable();
        } else {
            console.warn('No fees data available');
            feesData = [];
            filteredData = [];
            renderTable();
        }
    } catch (error) {
        console.error('Error loading fees:', error);
        feesData = [];
        filteredData = [];
        renderTable();

        // Show user-friendly error
        Swal.fire({
            icon: 'error',
            title: 'Failed to Load Data',
            text: 'Unable to load fees data. Please refresh the page.',
            confirmButtonColor: '#667eea'
        });
    }
}

// ==================== DRAG AND DROP ====================

function handleDragOver(e) {
    e.preventDefault();
    e.stopPropagation();
    e.currentTarget.classList.add('dragover');
}

function handleDragLeave(e) {
    e.preventDefault();
    e.stopPropagation();
    e.currentTarget.classList.remove('dragover');
}

function handleDrop(e) {
    e.preventDefault();
    e.stopPropagation();
    e.currentTarget.classList.remove('dragover');

    const file = e.dataTransfer.files[0];
    if (file && file.name.endsWith('.csv')) {
        handleFeesCSVFile(file);
    } else {
        Swal.fire({
            icon: 'error',
            title: 'Invalid File',
            text: 'Please upload a valid CSV file',
            confirmButtonColor: '#667eea'
        });
    }
}

// ==================== REMAINING FUNCTIONS (keep as-is) ====================

function setDefaultDates() {
    const today = new Date().toISOString().split('T')[0];
    document.getElementById('receiptDate').value = today;
    document.getElementById('refundDate').value = today;
}

function handleSearch(e) {
    const searchTerm = e.target.value.toLowerCase();
    filteredData = feesData.filter(item =>
        item.regNo.toLowerCase().includes(searchTerm) ||
        item.studentName.toLowerCase().includes(searchTerm) ||
        item.mobile.includes(searchTerm) ||
        item.course.toLowerCase().includes(searchTerm)
    );
    currentPage = 1;
    renderTable();
}

async function changeFeesStatus(regNo) {
    if (!regNo) {
        showError('Registration number is missing');
        return;
    }

    currentStudentRegNo = regNo;

    //  Close any open modals first
    document.querySelectorAll('.modal.show').forEach(modal => {
        bootstrap.Modal.getInstance(modal)?.hide();
    });

    // Small delay to allow previous modal to close
    setTimeout(() => {
        //  Get current status
        const student = feesData.find(s => s.regNo === regNo);
        const currentStatus = student?.status || 'Pending';

        //  Set dropdown value
        const statusSelect = document.getElementById('paymentStatus');
        if (statusSelect) {
            statusSelect.value = currentStatus;
        }

        new bootstrap.Modal(document.getElementById('changeStatusModal')).show();
    }, 300);
}

async function openFeeInstallments(regNo) {
    if (!regNo) {
        showError('Registration number is missing');
        return;
    }

    currentStudentRegNo = regNo;
    const student = feesData.find(s => s.regNo === regNo);

    if (!student) {
        showError('Student record not found');
        return;
    }

    try {
        showLoading('Loading installments...');

        //  STEP 1: Fetch installment config from backend
        const configResponse = await fetch(`${API_BASE}/installment-config/${regNo}`, {
            headers: getCsrfHeaders()
        });

        let installmentConfig = {
            startDate: null,
            numberOfInstallments: null,
            daysBetween: null,
            totalAmount: student.totalFees || 0
        };

        if (configResponse.ok) {
            const config = await configResponse.json();

            if (config.hasExisting) {
                installmentConfig = {
                    startDate: config.startDate,
                    numberOfInstallments: config.numberOfInstallments,
                    daysBetween: config.daysBetween,
                    totalAmount: config.totalAmount || student.totalFees
                };

                console.log(' Loaded installment config from backend:', installmentConfig);
            }
        }

        //  STEP 2: Fetch existing installments
        const response = await fetch(`${API_BASE}/installments/${regNo}`, {
            headers: getCsrfHeaders()
        });

        if (!response.ok) throw new Error('Failed to load installments');

        const installments = await response.json();
        Swal.close();

        // Populate form
        document.getElementById('feeInstStudentName').textContent = student.studentName;
        document.getElementById('feeInstTotalAmount').value = installmentConfig.totalAmount;
        document.getElementById('feeInstTotalInstAmount').value = installmentConfig.totalAmount;

        //  Pre-fill config fields
        if (installmentConfig.startDate) {
            document.getElementById('feeInstStartDate').value = installmentConfig.startDate;
        } else {
            document.getElementById('feeInstStartDate').value = new Date().toISOString().split('T')[0];
        }

        if (installmentConfig.numberOfInstallments) {
            document.getElementById('feeInstNoOfInstallments').value = installmentConfig.numberOfInstallments;
        }

        if (installmentConfig.daysBetween) {
            document.getElementById('feeInstDays').value = installmentConfig.daysBetween;
        }

        // Display installments table (same as before)
        const tbody = document.getElementById('feeInstallmentsBody');
        if (installments.length === 0) {
            tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted">No installments found. Click Generate to create installments.</td></tr>';
        } else {
            tbody.innerHTML = installments.map(inst => {
                const statusBadge = inst.status === 'Refund'
                    ? '<span class="badge bg-danger">Refund</span>'
                    : `<span class="badge bg-${inst.status === 'Paid' ? 'success' : 'warning'}">${inst.status}</span>`;

                return `
                <tr>
                    <td>${inst.dueDate}</td>
                    <td>₹${parseFloat(inst.amount).toFixed(2)}</td>
                    <td>${statusBadge}</td>
                    <td>
                        ${inst.status !== 'Refund' ? `
                        <button class="btn btn-sm btn-danger" onclick="deleteInstallment(${inst.id})">
                            <i class="bi bi-trash"></i>
                        </button>
                        ` : '-'}
                    </td>
                </tr>
            `}).join('');
        }

        new bootstrap.Modal(document.getElementById('feeInstallmentsModal')).show();

    } catch (error) {
        Swal.close();
        console.error('Error:', error);
        showError('Failed to load installments');
    }
}

// Delete installment function
window.deleteInstallment = async function(installmentId) {
    const result = await Swal.fire({
        title: 'Delete Installment?',
        text: 'This action cannot be undone',
        icon: 'warning',
        showCancelButton: true,
        confirmButtonText: 'Yes, Delete',
        cancelButtonText: 'Cancel',
        confirmButtonColor: '#ef4444'
    });

    if (result.isConfirmed) {
        try {
            showLoading('Deleting installment...');

            const response = await fetch(`/api/fees-manager/installments/${installmentId}`, {
                method: 'DELETE',
                headers: getCsrfHeaders()
            });

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.message || 'Failed to delete installment');
            }

            Swal.close();
            showSuccess('Installment deleted successfully!');

            // Reload the current modal
            const feeInstModal = document.getElementById('feeInstallmentsModal');
            if (feeInstModal && bootstrap.Modal.getInstance(feeInstModal)) {
                // Get current regNo from the modal title
                const studentNameEl = document.getElementById('feeInstStudentName');
                if (studentNameEl) {
                    // Find the admission by student name
                    const student = feesData.find(s => s.studentName === studentNameEl.textContent);
                    if (student) {
                        // Reload installments
                        openFeeInstallments(student.regNo);
                    }
                }
            } else {
                // Fallback: reload page
                loadFeesFromBackend();
            }

        } catch (error) {
            Swal.close();
            console.error('Error:', error);
            showError(error.message || 'Failed to delete installment');
        }
    }
};

async function loadRefundHistory(regNo) {
    try {
        const response = await fetch(`${API_BASE}/refunds/${regNo}`, {
            headers: getCsrfHeaders()
        });

        if (response.ok) {
            const refunds = await response.json();
            const tbody = document.getElementById('refundHistoryBody');

            if (refunds.length === 0) {
                tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted">No refund records found</td></tr>';
            } else {
                tbody.innerHTML = refunds.map(ref => `
                    <tr>
                        <td><strong>${ref.refundNumber}</strong></td>
                        <td>${formatDate(ref.refundDate)}</td>
                        <td class="text-danger"><strong>-₹${ref.refundAmount.toFixed(2)}</strong></td>
                        <td><span class="badge bg-info">${ref.paymentMode}</span></td>
                        <td class="small">${ref.notes || '-'}</td>
                        <td class="small">${ref.issuedBy || ref.createdBy || 'SYSTEM'}</td>
                        <td><span class="badge bg-danger">Refunded</span></td>
                    </tr>
                `).join('');
            }
        }
    } catch (error) {
        console.error('Error loading refund history:', error);
    }
}

function renderTable() {
    const tbody = document.querySelector('#feesTable tbody');

    if (!filteredData || filteredData.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="11" class="text-center py-4">
                    <p class="mb-0 text-muted">No data available</p>
                </td>
            </tr>
        `;
        updatePagination();
        return;
    }

    const start = (currentPage - 1) * entriesPerPage;
    const end = start + entriesPerPage;
    const paginatedData = filteredData.slice(start, end);

    tbody.innerHTML = paginatedData.map(item => {
        const netPaid = item.totalPaid || 0;
        const totalRefund = item.feesRefund || 0;
        const grossPaid = netPaid + totalRefund;
        const actualDue = item.feesDue || 0;

        //  CHANGE: Determine due date display
        let dueDateDisplay = '-'; // Default for clear fees

        if (actualDue > 0.01) {
            // Fees pending - show due date
            if (item.dueDate) {
                dueDateDisplay = formatDate(item.dueDate);
            } else {
                dueDateDisplay = '<span class="text-muted">Not Set</span>';
            }
        }

        // Determine status
        let statusBadge = 'bg-warning';
        let statusText = item.status || 'Pending';

        if (totalRefund > 0 && actualDue > 0.01) {
            statusBadge = 'bg-danger';
            statusText = 'Refund';
        } else if (actualDue <= 0.01) {
            statusBadge = 'bg-success';
            statusText = 'Clear';
        } else if (item.status === 'Overdue') {
            statusBadge = 'bg-danger';
        }

        return `
            <tr>
                <td><strong>${item.regNo || 'N/A'}</strong></td>
                <td>${item.studentName || 'N/A'}</td>
                <td>${item.mobile || 'N/A'}</td>
                <td>₹${(item.totalFees || 0).toLocaleString()}</td>
                <td class="${actualDue > 0 ? 'text-danger' : 'text-success'}">
                    <strong>₹${actualDue.toLocaleString()}</strong>
                    ${totalRefund > 0 ? `<br><small class="text-muted">(Refund: ₹${totalRefund.toLocaleString()})</small>` : ''}
                </td>
                <td>
                    ₹${netPaid.toLocaleString()}
                    ${totalRefund > 0 ? `<br><small class="text-muted">(Gross: ₹${grossPaid.toLocaleString()})</small>` : ''}
                </td>
                <td>${dueDateDisplay}</td>
                <td><span class="badge ${statusBadge}">${statusText}</span></td>
                <td><span class="badge bg-primary">${item.course || 'N/A'}</span></td>
                <td>
                    <div class="action-dropdown">
                        <button class="btn btn-sm btn-outline-secondary dropdown-toggle" onclick="toggleActionMenu(event)">
                            <i class="bi bi-three-dots-vertical"></i>
                        </button>
                        <div class="action-menu">
                            <button class="action-menu-item" onclick="openFeeReceipt('${item.regNo}')">
                                <i class="bi bi-receipt text-primary"></i>
                                <span>New Fee Receipt</span>
                            </button>
                            <button class="action-menu-item" onclick="viewReceipts('${item.regNo}')">
                                <i class="bi bi-receipt-cutoff text-info"></i>
                                <span>View Receipts</span>
                            </button>
                            <button class="action-menu-item" onclick="openFeeInstallments('${item.regNo}')">
                                <i class="bi bi-cash-stack text-success"></i>
                                <span>Fee Installments</span>
                            </button>
                            <button class="action-menu-item" onclick="changeFeesStatus('${item.regNo}')">
                                <i class="bi bi-arrow-repeat text-warning"></i>
                                <span>Change Status</span>
                            </button>
                            <button class="action-menu-item" onclick="feesRefund('${item.regNo}')">
                                <i class="bi bi-arrow-counterclockwise text-danger"></i>
                                <span>Fees Refund</span>
                            </button>
                        </div>
                    </div>
                </td>
            </tr>
        `;
    }).join('');

    updatePagination();
}

async function viewReceipts(regNo) {
    if (!regNo) {
        showError('Registration number is missing');
        return;
    }

    const student = feesData.find(s => s.regNo === regNo);
    if (!student) {
        showError('Student record not found');
        return;
    }

    currentStudentRegNo = regNo;

    try {
        showLoading('Loading receipts...');

        const response = await fetch(`${API_BASE}/receipts/${regNo}`, {
            headers: getCsrfHeaders()
        });

        if (!response.ok) throw new Error('Failed to load receipts');

        const receipts = await response.json();
        Swal.close();

        // Populate modal
        document.getElementById('viewReceiptStudentName').textContent = student.studentName;

        const tbody = document.getElementById('receiptsTableBody');
        if (receipts.length === 0) {
            tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted">No receipts found</td></tr>';
        } else {
            tbody.innerHTML = receipts.map(receipt => {
                //  Check data source to determine if it's old imported data
                const isOldData = receipt.dataSource === 'IMPORTED_OLD_DATA' ||
                                 receipt.receiptType === 'Old Imported';

                const receiptTypeBadge = isOldData
                    ? '<span class="badge bg-secondary">Old Import</span>'
                    : '<span class="badge bg-success">Regular</span>';

                return `
                <tr>
                    <td><strong>${receipt.receiptNumber || 'N/A'}</strong></td>
                    <td>${receipt.invoiceNumber || 'N/A'}</td>
                    <td>₹${(receipt.amountReceived || 0).toLocaleString()}</td>
                    <td>${receipt.receiptDate ? formatDate(receipt.receiptDate) : 'N/A'}</td>
                    <td><span class="badge bg-info">${receipt.paymentMode || 'Cash'}</span></td>
                    <td>${receipt.notes || '-'}</td>
                    <td>${receiptTypeBadge}</td>
                    <td>
                        <button class="btn btn-sm btn-primary me-1"
                            onclick="viewReceiptPreview('${receipt.receiptNumber}', '${regNo}')"
                            title="View">
                            <i class="bi bi-eye"></i>
                        </button>
                       ${!isOldData ? `
                       <button class="btn btn-sm btn-info me-1"
                           onclick="emailReceipt('${receipt.receiptNumber}', '${student.studentName}', '${student.mobile}')"
                           title="Email">
                           <i class="bi bi-envelope"></i>
                       </button>
                        <button class="btn btn-sm btn-warning me-1"
                            onclick="updateFeeReceipt(${receipt.id}, '${regNo}')"
                            title="Edit">
                            <i class="bi bi-pencil"></i>
                        </button>
                        <button class="btn btn-sm btn-danger"
                            onclick="deleteReceipt(${receipt.id})"
                            title="Delete">
                            <i class="bi bi-trash"></i>
                        </button>
                        ` : '<span class="text-muted small">View Only</span>'}
                    </td>
                </tr>
            `}).join('');
        }

        new bootstrap.Modal(document.getElementById('viewReceiptsModal')).show();

    } catch (error) {
        Swal.close();
        console.error('Error:', error);
        showError('Failed to load receipts');
    }
}

function toggleActionMenu(event) {
    event.stopPropagation();
    const menu = event.target.closest('.action-dropdown').querySelector('.action-menu');

    // Close all other menus
    document.querySelectorAll('.action-menu').forEach(m => {
        if (m !== menu) m.classList.remove('show');
    });

    menu.classList.toggle('show');
}

// Close menus when clicking outside
document.addEventListener('click', function() {
    document.querySelectorAll('.action-menu').forEach(menu => {
        menu.classList.remove('show');
    });
});

function updatePagination() {
    const totalPages = Math.ceil(filteredData.length / entriesPerPage);
    const start = (currentPage - 1) * entriesPerPage + 1;
    const end = Math.min(currentPage * entriesPerPage, filteredData.length);

    document.getElementById('entriesStart').textContent = filteredData.length === 0 ? 0 : start;
    document.getElementById('entriesEnd').textContent = end;
    document.getElementById('totalEntries').textContent = filteredData.length;

    const paginationControls = document.getElementById('paginationControls');
    let paginationHTML = '';

    // Previous button
    paginationHTML += `
        <li class="page-item ${currentPage === 1 ? 'disabled' : ''}">
            <a class="page-link" href="#" onclick="changePage(${currentPage - 1}); return false;">Previous</a>
        </li>
    `;

    // Page numbers
    for (let i = 1; i <= totalPages; i++) {
        if (i === 1 || i === totalPages || (i >= currentPage - 1 && i <= currentPage + 1)) {
            paginationHTML += `
                <li class="page-item ${i === currentPage ? 'active' : ''}">
                    <a class="page-link" href="#" onclick="changePage(${i}); return false;">${i}</a>
                </li>
            `;
        } else if (i === currentPage - 2 || i === currentPage + 2) {
            paginationHTML += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
        }
    }

    // Next button
    paginationHTML += `
        <li class="page-item ${currentPage === totalPages ? 'disabled' : ''}">
            <a class="page-link" href="#" onclick="changePage(${currentPage + 1}); return false;">Next</a>
        </li>
    `;

    paginationControls.innerHTML = paginationHTML;
}

function changePage(page) {
    const totalPages = Math.ceil(filteredData.length / entriesPerPage);
    if (page >= 1 && page <= totalPages) {
        currentPage = page;
        renderTable();
    }
}

function formatDate(dateString) {
    const date = new Date(dateString);
    return date.toLocaleDateString('en-GB');
}

// Generate Receipt HTML
function generateReceiptHTML(data) {
    // Convert amount to words
    const amountInWords = numberToWords(data.amountReceived || 0);

    // Format date
    const formatDate = (dateStr) => {
        if (!dateStr) return 'N/A';
        const date = new Date(dateStr);
        return date.toLocaleDateString('en-GB');
    };

    return `
        <div class="receipt-container" id="receiptContent">
            <div class="receipt-header">
                <div class="text-center mb-4">
                    <img src="/assets/images/technokraft-logo.png" alt="Logo" style="height: 60px; margin-bottom: 10px;"
                         onerror="this.style.display='none'">
                    <h3 class="mb-1" style="color: #667eea; font-weight: 700;">TechnoKraft Training Solutions</h3>
                    <p class="mb-0 text-muted">Excellence in Technical Education</p>
                    <p class="mb-0 small text-muted">Kanchwala Avenue, College Road, Nashik, Maharashtra - 422005</p>
                    <p class="mb-0 small text-muted">Phone: +91 86456 28278 | Email: support@tts.net.in</p>
                </div>

                <div style="border-top: 3px solid #667eea; border-bottom: 3px solid #667eea; padding: 10px 0; margin: 20px 0;">
                    <h4 class="text-center mb-0" style="color: #1e293b; font-weight: 600;">FEE RECEIPT</h4>
                </div>
            </div>

            <div class="receipt-body">
                <div class="row mb-3">
                    <div class="col-6">
                        <p class="mb-1"><strong>Receipt No:</strong> ${data.receiptNumber || 'N/A'}</p>
                        <p class="mb-1"><strong>Invoice No:</strong> ${data.invoiceNumber || 'N/A'}</p>
                    </div>
                    <div class="col-6 text-end">
                        <p class="mb-1"><strong>Date:</strong> ${formatDate(data.receiptDate)}</p>
                    </div>
                </div>

                <div class="student-details" style="background: #f8fafc; padding: 15px; border-radius: 8px; margin-bottom: 20px;">
                    <h6 style="color: #667eea; margin-bottom: 10px; font-weight: 600;">Student Details</h6>
                    <div class="row">
                        <div class="col-6">
                            <p class="mb-1"><strong>Name:</strong> ${data.studentName || 'N/A'}</p>
                            <p class="mb-1"><strong>Reg. No:</strong> ${data.registrationNumber || 'N/A'}</p>
                        </div>
                        <div class="col-6">
                            <p class="mb-1"><strong>Mobile:</strong> ${data.mobile || 'N/A'}</p>
                            <p class="mb-1"><strong>Course:</strong> ${data.course || 'N/A'}</p>
                        </div>
                    </div>
                </div>

                <div class="payment-details" style="border: 2px solid #e2e8f0; border-radius: 8px; padding: 15px; margin-bottom: 20px;">
                    <h6 style="color: #667eea; margin-bottom: 15px; font-weight: 600;">Payment Details</h6>
                    <table class="table table-sm mb-0">
                        <tbody>
                            <tr>
                                <td><strong>Amount Paid:</strong></td>
                                <td class="text-end"><strong style="font-size: 1.1rem; color: #10b981;">₹${(data.amountReceived || 0).toLocaleString()}</strong></td>
                            </tr>
                            <tr>
                                <td><strong>Amount in Words:</strong></td>
                                <td class="text-end"><em>${amountInWords}</em></td>
                            </tr>
                            <tr>
                                <td><strong>Payment Mode:</strong></td>
                                <td class="text-end">${data.paymentMode || 'Cash'}</td>
                            </tr>
                            ${data.transactionNumber ? `
                            <tr>
                                <td><strong>Transaction ID:</strong></td>
                                <td class="text-end">${data.transactionNumber}</td>
                            </tr>
                            ` : ''}
                            ${data.chequeNumber ? `
                            <tr>
                                <td><strong>Cheque No:</strong></td>
                                <td class="text-end">${data.chequeNumber}</td>
                            </tr>
                            ` : ''}
                            ${data.bankName ? `
                            <tr>
                                <td><strong>Bank Name:</strong></td>
                                <td class="text-end">${data.bankName}</td>
                            </tr>
                            ` : ''}
                            <tr>
                                <td><strong>Total Fees:</strong></td>
                                <td class="text-end">₹${(data.totalFees || 0).toLocaleString()}</td>
                            </tr>
                            ${data.pendingFees > 0.01 ? `
                            <tr>
                                <td><strong>Current Pending Fees:</strong></td>
                                <td class="text-end"><strong style="color: #16a34a;">₹${(data.pendingFees || 0).toLocaleString()}</strong></td>
                            </tr>
                            ` : `
                            <tr style="background-color: #f0fdf4;">
                                <td><strong>Payment Status:</strong></td>
                                <td class="text-end"><strong style="color: #16a34a;">✓ PAID IN FULL</strong></td>
                            </tr>
                            `}
                            ${data.pendingFees > 0.01 && data.nextDueDate ? `
                            <tr>
                                <td><strong>Next Due Date:</strong></td>
                                <td class="text-end">${formatDate(data.nextDueDate)}</td>
                            </tr>
                            ` : ''}
                        </tbody>
                    </table>
                </div>

                ${data.notes ? `
                <div class="note-section" style="background: #fef3c7; padding: 10px; border-radius: 6px; border-left: 4px solid #f59e0b; margin-bottom: 20px;">
                    <p class="mb-0 small"><strong>Note:</strong> ${data.notes}</p>
                </div>
                ` : ''}

                <div class="receipt-footer mt-4">
                    <div class="row">
                        <div class="col-6">
                            <div style="border-top: 2px solid #334155; padding-top: 5px; margin-top: 50px;">
                                <p class="mb-0 text-center small"><strong>Student Signature</strong></p>
                            </div>
                        </div>
                        <div class="col-6">
                            <div style="border-top: 2px solid #334155; padding-top: 5px; margin-top: 50px;">
                                <p class="mb-0 text-center small"><strong>Authorized Signature</strong></p>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="text-center mt-4 pt-3" style="border-top: 1px dashed #cbd5e1;">
                    <p class="mb-0 small text-muted">This is a computer-generated receipt and does not require a physical signature.</p>
                    <p class="mb-0 small text-muted">For any queries, please contact us at +91 86456 28278</p>
                </div>
            </div>
        </div>
    `;
}

// Helper function to convert number to words
function numberToWords(num) {
    if (num === 0) return 'Zero Rupees Only';

    const ones = ['', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine'];
    const tens = ['', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety'];
    const teens = ['Ten', 'Eleven', 'Twelve', 'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen', 'Seventeen', 'Eighteen', 'Nineteen'];

    function convertLessThanThousand(n) {
        if (n === 0) return '';
        if (n < 10) return ones[n];
        if (n < 20) return teens[n - 10];
        if (n < 100) return tens[Math.floor(n / 10)] + (n % 10 !== 0 ? ' ' + ones[n % 10] : '');
        return ones[Math.floor(n / 100)] + ' Hundred' + (n % 100 !== 0 ? ' ' + convertLessThanThousand(n % 100) : '');
    }

    num = Math.floor(num);

    if (num < 1000) return convertLessThanThousand(num) + ' Rupees Only';
    if (num < 100000) {
        const thousands = Math.floor(num / 1000);
        const remainder = num % 1000;
        return convertLessThanThousand(thousands) + ' Thousand' +
               (remainder !== 0 ? ' ' + convertLessThanThousand(remainder) : '') + ' Rupees Only';
    }

    const lakhs = Math.floor(num / 100000);
    const remainder = num % 100000;
    const thousands = Math.floor(remainder / 1000);
    const hundreds = remainder % 1000;

    let result = convertLessThanThousand(lakhs) + ' Lakh';
    if (thousands > 0) result += ' ' + convertLessThanThousand(thousands) + ' Thousand';
    if (hundreds > 0) result += ' ' + convertLessThanThousand(hundreds);

    return result + ' Rupees Only';
}

// Print Receipt Content
function printReceiptContent(htmlContent) {
    const printWindow = window.open('', '_blank', 'width=800,height=600');

    printWindow.document.write(`
        <!DOCTYPE html>
        <html>
        <head>
            <title>Print Receipt</title>
            <link href="https://cdnjs.cloudflare.com/ajax/libs/bootstrap/5.3.2/css/bootstrap.min.css" rel="stylesheet">
            <style>
                @media print {
                    body {
                        margin: 0;
                        padding: 20px;
                    }
                    .receipt-container {
                        max-width: 800px;
                        margin: 0 auto;
                    }
                    @page {
                        size: A4;
                        margin: 10mm;
                    }
                }
                body {
                    font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                }
                .receipt-container {
                    padding: 30px;
                    max-width: 800px;
                    margin: 0 auto;
                }
            </style>
        </head>
        <body>
            ${htmlContent}
            <script>
                window.onload = function() {
                    setTimeout(function() {
                        window.print();
                        window.onafterprint = function() {
                            window.close();
                        };
                    }, 250);
                };
            </script>
        </body>
        </html>
    `);

    printWindow.document.close();
}

function emailReceiptWithPreview(receiptNo, studentName, mobile) {
    const student = feesData.find(s => s.studentName === studentName);

    viewReceiptPreview(receiptNo, student.regNo);
}

function updateReceipt(receiptNo) {
    Swal.fire({
        title: 'Update Receipt',
        html: `
            <div class="text-start">
                <div class="mb-3">
                    <label class="form-label">Receipt No.</label>
                    <input type="text" class="form-control" id="updateReceiptNo" value="${receiptNo}" readonly>
                </div>
                <div class="mb-3">
                    <label class="form-label">Amount</label>
                    <input type="number" class="form-control" id="updateAmount" value="10000" required>
                </div>
                <div class="mb-3">
                    <label class="form-label">Payment Mode</label>
                    <select class="form-select" id="updatePaymentMode">
                        <option value="Cash">Cash</option>
                        <option value="Online">Online</option>
                        <option value="Cheque">Cheque</option>
                    </select>
                </div>
                <div class="mb-3">
                    <label class="form-label">Note</label>
                    <textarea class="form-control" id="updateNote" rows="2"></textarea>
                </div>
            </div>
        `,
        showCancelButton: true,
        confirmButtonText: '<i class="bi bi-check-circle me-2"></i>Update',
        cancelButtonText: 'Cancel',
        confirmButtonColor: '#667eea',
        width: '500px',
        preConfirm: () => {
            const amount = document.getElementById('updateAmount').value;
            if (!amount || amount <= 0) {
                Swal.showValidationMessage('Please enter a valid amount');
                return false;
            }
            return {
                receiptNo: receiptNo,
                amount: amount,
                paymentMode: document.getElementById('updatePaymentMode').value,
                note: document.getElementById('updateNote').value
            };
        }
    }).then((result) => {
        if (result.isConfirmed) {
            // Here you would make API call to update receipt
            console.log('Updating receipt:', result.value);

            Swal.fire({
                icon: 'success',
                title: 'Updated!',
                text: 'Receipt updated successfully',
                confirmButtonColor: '#667eea'
            }).then(() => {
                // Reload receipts
                viewReceipts(currentStudentId);
            });
        }
    });
}

document.getElementById('importBtn').addEventListener('click', importFeesCSV);


async function importFeesCSV() {
    if (importedFeesData.length === 0) {
        Swal.fire({
            icon: 'error',
            title: 'No Data',
            text: 'No valid data to import',
            confirmButtonColor: '#667eea'
        });
        return;
    }

    try {
        Swal.fire({
            title: 'Importing...',
            text: `Processing ${importedFeesData.length} records`,
            allowOutsideClick: false,
            didOpen: () => Swal.showLoading()
        });

        // Send to backend
        const response = await fetch('/api/fees-manager/bulk-import-json', {
            method: 'POST',
            headers: getCsrfHeaders(),
            body: JSON.stringify(importedFeesData)
        });

        const result = await response.json();

        Swal.close();
        bootstrap.Modal.getInstance(document.getElementById('importModal')).hide();

        if (result.success) {
            Swal.fire({
                icon: 'success',
                title: 'Import Complete!',
                html: `
                    <div class="text-start">
                        <p><strong>Total Records:</strong> ${result.totalRecords}</p>
                        <p><strong>Successfully Imported:</strong> ${result.successfulImports}</p>
                        <p><strong>Failed:</strong> ${result.failedImports}</p>
                    </div>
                `,
                confirmButtonColor: '#667eea'
            }).then(() => {
                // Reload the fees table
                renderTable();
                resetFeesImport();
            });
        } else {
            Swal.fire({
                icon: 'warning',
                title: 'Import Completed with Errors',
                html: `
                    <div class="text-start">
                        <p><strong>Total Records:</strong> ${result.totalRecords}</p>
                        <p><strong>Successfully Imported:</strong> ${result.successfulImports}</p>
                        <p><strong>Failed:</strong> ${result.failedImports}</p>
                        ${result.errors && result.errors.length > 0 ?
                            `<p class="mt-2"><strong>Errors:</strong></p>
                             <ul class="small">${result.errors.slice(0, 5).map(e =>
                                `<li>Row ${e.rowNumber}: ${e.errorMessage}</li>`
                             ).join('')}</ul>` : ''
                        }
                    </div>
                `,
                confirmButtonColor: '#667eea'
            }).then(() => {
                renderTable();
                resetFeesImport();
            });
        }

    } catch (error) {
        Swal.close();
        console.error('Import error:', error);
        Swal.fire({
            icon: 'error',
            title: 'Import Failed',
            text: error.message || 'Failed to import data',
            confirmButtonColor: '#ef4444'
        });
    }
}

// Generate and print receipt
async function printReceiptWithData(receiptNo, regNo) {
    try {
        // Fetch receipt data
        const response = await fetch(`${API_BASE}/receipts/${regNo}`, {
            headers: getCsrfHeaders()
        });

        if (!response.ok) {
            throw new Error('Failed to fetch receipt data');
        }

        const receipts = await response.json();
        const receipt = receipts.find(r => r.receiptNumber === receiptNo);

        if (!receipt) {
            throw new Error('Receipt not found');
        }

        // Generate receipt HTML
        const receiptHTML = generateReceiptHTML(receipt);
        printReceiptContent(receiptHTML);

    } catch (error) {
        console.error('Error printing receipt:', error);
        Swal.fire({
            icon: 'error',
            title: 'Print Error',
            text: 'Failed to load receipt data for printing',
            confirmButtonColor: '#ef4444'
        });
    }
}

// Toggle Functions
function toggleGstFields() {
    const isChecked = document.getElementById('enableGst').checked;
    document.getElementById('sgstField').style.display = isChecked ? 'block' : 'none';
    document.getElementById('cgstField').style.display = isChecked ? 'block' : 'none';
    document.getElementById('invoiceValueField').style.display = isChecked ? 'block' : 'none';
}

function togglePaymentFields() {
    const mode = document.getElementById('paymentMode').value;

    document.getElementById('bankField').style.display = mode !== 'Cash' ? 'block' : 'none';
    document.getElementById('chequeDateField').style.display = mode === 'Cheque' ? 'block' : 'none';
    document.getElementById('chequeNoField').style.display = mode === 'Cheque' ? 'block' : 'none';
    document.getElementById('ifscField').style.display = mode === 'Online' ? 'block' : 'none';
    document.getElementById('transactionField').style.display = mode === 'Online' ? 'block' : 'none';
    document.getElementById('onlinePaymentField').style.display = mode === 'Online' ? 'block' : 'none';
}

function toggleRefundPaymentFields() {
    const mode = document.getElementById('refundPaymentMode').value;

    document.getElementById('refundBankField').style.display = mode !== 'Cash' ? 'block' : 'none';
    document.getElementById('refundChequeDateField').style.display = mode === 'Cheque' ? 'block' : 'none';
    document.getElementById('refundChequeNoField').style.display = mode === 'Cheque' ? 'block' : 'none';
    document.getElementById('refundIfscField').style.display = mode === 'Online' ? 'block' : 'none';
    document.getElementById('refundTransactionField').style.display = mode === 'Online' ? 'block' : 'none';
    document.getElementById('refundOnlinePaymentField').style.display = mode === 'Online' ? 'block' : 'none';
}

// Save Functions
async function saveReceipt() {
    const regNo = currentStudentRegNo;
    const receiptId = document.getElementById('btnSaveReceipt').dataset.receiptId;
    const isUpdate = !!receiptId;

    if (!regNo) {
        showError('Student information is missing');
        return;
    }

    const nowReceiving = parseFloat(document.getElementById('nowReceiving').value);
    const installmentId = document.getElementById('installment').value;
    const nextDueDate = document.getElementById('nextDueDate').value;

    if (!nowReceiving || nowReceiving <= 0) {
        showError('Please enter a valid amount');
        return;
    }

    const receiptData = {
        regNo: regNo,
        installmentId: installmentId ? parseInt(installmentId) : null, //   Include installment ID
        receiptDate: document.getElementById('receiptDate').value,
        amountReceived: nowReceiving,
        previousPaid: parseFloat(document.getElementById('receivedFees').value) || 0,
        totalFees: parseFloat(document.getElementById('receiptTotalFees').value) || 0,
        pendingFees: parseFloat(document.getElementById('receiptPendingFees').value) || 0,
        gstEnabled: document.getElementById('enableGst').checked,
        sgstPercent: document.getElementById('enableGst').checked ? parseFloat(document.getElementById('sgstPercent').value) : null,
        cgstPercent: document.getElementById('enableGst').checked ? parseFloat(document.getElementById('cgstPercent').value) : null,
        invoiceValue: document.getElementById('enableGst').checked ? parseFloat(document.getElementById('invoiceValue').value) : null,
        paymentMode: document.getElementById('paymentMode').value,
        bankName: document.getElementById('bankName').value || null,
        chequeNumber: document.getElementById('chequeNo').value || null,
        chequeDate: document.getElementById('chequeDate').value || null,
        transactionNumber: document.getElementById('transactionNo').value || null,
        ifscCode: document.getElementById('ifscCode').value || null,
        onlinePaymentMode: document.getElementById('onlinePaymentMode').value || null,
        nextDueDate: nextDueDate,
        receiptType: 'Regular',
        notes: document.getElementById('receiptNotes').value || null
    };

    console.log('💾 Saving receipt with installmentId:', installmentId); //  Debug log

    try {
        showLoading(isUpdate ? 'Updating receipt...' : 'Saving receipt...');

        const url = isUpdate ? `/api/fees-manager/receipts/${receiptId}` : '/api/fees-manager/receipts';
        const method = isUpdate ? 'PUT' : 'POST';

        const response = await fetch(url, {
            method: method,
            headers: getCsrfHeaders(),
            body: JSON.stringify(receiptData)
        });

        const result = await response.json();
        Swal.close();

        if (response.ok) {
            await updateFeesTotalPaid(regNo);

            showSuccess(`Fee receipt ${isUpdate ? 'updated' : 'saved'} successfully`);

            // Reset button and title
            delete document.getElementById('btnSaveReceipt').dataset.receiptId;
            document.getElementById('btnSaveReceipt').innerHTML = '<i class="bi bi-check-circle me-2"></i>Save Changes';
            document.querySelector('#feeReceiptModal .modal-title').innerHTML = '<i class="bi bi-receipt me-2"></i>New Fee Receipt';

            bootstrap.Modal.getInstance(document.getElementById('feeReceiptModal')).hide();

            // Reload data
            await loadFeesFromBackend();
        } else {
            throw new Error(result.message || `Failed to ${isUpdate ? 'update' : 'save'} receipt`);
        }

    } catch (error) {
        Swal.close();
        console.error('Error:', error);
        showError(error.message || `Failed to ${isUpdate ? 'update' : 'save'} receipt`);
    }
}

function generateFeeInstallments() {
    const totalAmount = parseFloat(document.getElementById('feeInstTotalAmount').value);
    const noOfInstallments = parseInt(document.getElementById('feeInstNoOfInstallments').value);
    const daysBetween = parseInt(document.getElementById('feeInstDays').value);
    const startDate = document.getElementById('feeInstStartDate').value;

    if (!totalAmount || !noOfInstallments || !startDate) {
        showError('Please fill all required fields');
        return;
    }

    const amountPerInstallment = totalAmount / noOfInstallments;
    const tbody = document.getElementById('feeInstallmentsBody');
    let currentDate = new Date(startDate);

    tbody.innerHTML = '';

    for (let i = 1; i <= noOfInstallments; i++) {
        const dueDate = new Date(currentDate);
        const formattedDate = dueDate.toISOString().split('T')[0];

        const row = document.createElement('tr');
        row.innerHTML = `
            <td>
                <input type="date" class="form-control form-control-sm"
                       value="${formattedDate}"
                       data-installment="${i}">
            </td>
            <td>
                <input type="number" class="form-control form-control-sm"
                       value="${amountPerInstallment.toFixed(2)}"
                       step="0.01"
                       data-installment="${i}">
            </td>
            <td>
                <select class="form-select form-select-sm" data-installment="${i}">
                    <option value="Pending">Pending</option>
                    <option value="Paid">Paid</option>
                    <option value="Overdue">Overdue</option>
                    <option value="Waived">Waived</option>
                </select>
            </td>
            <td>
                <button type="button" class="btn btn-sm btn-danger"
                        onclick="this.closest('tr').remove()">
                    <i class="bi bi-trash"></i>
                </button>
            </td>
        `;
        tbody.appendChild(row);

        currentDate.setDate(currentDate.getDate() + daysBetween);
    }

    document.getElementById('feeInstTotalInstAmount').value = totalAmount.toFixed(2);
    showSuccess(`Generated ${noOfInstallments} installments`);
}

async function saveFeeInstallments() {
    const tbody = document.getElementById('feeInstallmentsBody');
    const rows = tbody.querySelectorAll('tr');

    if (rows.length === 0 || rows[0].cells.length === 1) {
        showError('No installments to save');
        return;
    }

    const installments = [];
    rows.forEach((row, index) => {
        const dateInput = row.querySelector('input[type="date"]');
        const amountInput = row.querySelector('input[type="number"]');
        const statusSelect = row.querySelector('select');

        installments.push({
            installmentNumber: index + 1,
            dueDate: dateInput.value,
            amount: parseFloat(amountInput.value),
            status: statusSelect.value
        });
    });

    try {
        showLoading('Saving installments...');

        const response = await fetch(`${API_BASE}/installments/${currentStudentRegNo}`, {
            method: 'POST',
            headers: getCsrfHeaders(),
            body: JSON.stringify({
                registrationNumber: currentStudentRegNo,
                installments: installments
            })
        });

        if (!response.ok) throw new Error('Failed to save installments');

        Swal.close();
        showSuccess('Fee installments saved successfully!');
        bootstrap.Modal.getInstance(document.getElementById('feeInstallmentsModal')).hide();

    } catch (error) {
        Swal.close();
        console.error('Error:', error);
        showError('Failed to save installments');
    }
}

// Update Fees Total Paid
async function updateFeesTotalPaid(regNo) {
    try {
        // Fetch all receipts for this student
        const response = await fetch(`${API_BASE}/receipts/${regNo}`, {
            headers: getCsrfHeaders()
        });

        if (response.ok) {
            const receipts = await response.json();

            // Calculate total paid from all receipts
            const totalPaid = receipts.reduce((sum, receipt) => {
                return sum + (receipt.amountReceived || 0);
            }, 0);

            // Update fees table
           await fetch(`${API_BASE}/update-total-paid`, {
               method: 'PUT',
               headers: getCsrfHeaders(),
               body: JSON.stringify({
                   regNo: regNo,
                   totalPaid: totalPaid
               })
           });
        }
    } catch (error) {
        console.error('Error updating total paid:', error);
    }
}

function saveAndPrintReceipt() {
    Swal.fire({
        icon: 'success',
        title: 'Success!',
        text: 'Fee receipt saved successfully',
        confirmButtonColor: '#667eea'
    }).then(() => {
        bootstrap.Modal.getInstance(document.getElementById('feeReceiptModal')).hide();
        loadFeesFromBackend();
    });
}

async function saveStatus() {
    const regNo = currentStudentRegNo;

    if (!regNo) {
        showError('Student information is missing');
        return;
    }

    const paymentStatus = document.getElementById('paymentStatus').value;

    if (!paymentStatus) {
        showError('Please select a payment status');
        return;
    }

    try {
        showLoading('Updating status...');

        const response = await fetch(`${API_BASE}/status`, {
            method: 'PUT',
            headers: getCsrfHeaders(),
            body: JSON.stringify({
                registrationNumber: regNo,
                paymentStatus: paymentStatus
            })
        });

        if (!response.ok) {
            throw new Error('Failed to update status');
        }

        Swal.close();
        showSuccess('Fee status updated successfully');

        bootstrap.Modal.getInstance(document.getElementById('changeStatusModal')).hide();

        // Reload fees data
        await loadFeesFromBackend();

    } catch (error) {
        Swal.close();
        console.error('Error updating status:', error);
        showError(error.message || 'Failed to update status');
    }
}

function generateInstallments() {
    const noOfInstallments = parseInt(document.getElementById('noOfInstallments').value);
    const daysGap = parseInt(document.getElementById('daysGap').value);
    const startDate = new Date(document.getElementById('installmentStartDate').value);
    const totalAmt = parseFloat(document.getElementById('installmentTotalAmt').value);

    if (!noOfInstallments || !daysGap || !startDate) {
        Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'Please fill all required fields',
            confirmButtonColor: '#667eea'
        });
        return;
    }

    const amountPerInstallment = (totalAmt / noOfInstallments).toFixed(2);
    const tbody = document.getElementById('installmentsTableBody');
    let html = '';

    for (let i = 0; i < noOfInstallments; i++) {
        const installmentDate = new Date(startDate);
        installmentDate.setDate(installmentDate.getDate() + (i * daysGap));

        html += `
            <tr>
                <td>${installmentDate.toISOString().split('T')[0]}</td>
                <td>₹${parseFloat(amountPerInstallment).toLocaleString()}</td>
                <td><span class="badge bg-warning">Pending</span></td>
                <td>
                    <button class="btn btn-sm btn-danger" onclick="removeInstallment(this)">
                        <i class="bi bi-trash"></i>
                    </button>
                </td>
            </tr>
        `;
    }

    tbody.innerHTML = html;
    document.getElementById('totalInstallmentAmt').value = totalAmt;
}

function removeInstallment(btn) {
    btn.closest('tr').remove();
}

function saveInstallments() {
    Swal.fire({
        icon: 'success',
        title: 'Success!',
        text: 'Installments saved successfully',
        confirmButtonColor: '#667eea'
    }).then(() => {
        bootstrap.Modal.getInstance(document.getElementById('installmentsModal')).hide();
    });
}

function saveAndPrintRefund() {
    Swal.fire({
        icon: 'success',
        title: 'Success!',
        text: 'Refund saved and printed successfully',
        confirmButtonColor: '#667eea'
    }).then(() => {
        bootstrap.Modal.getInstance(document.getElementById('feesRefundModal')).hide();
        loadFeesFromBackend();
    });
}


// CSV Import/Export Functions
function handleFileSelect(e) {
    const file = e.target.files[0];
    if (file) {
        processCSVFile(file);
    }
}

function handleDragOver(e) {
    e.preventDefault();
    e.stopPropagation();
    e.currentTarget.classList.add('dragover');
}

function handleDragLeave(e) {
    e.preventDefault();
    e.stopPropagation();
    e.currentTarget.classList.remove('dragover');
}

function handleDrop(e) {
    e.preventDefault();
    e.stopPropagation();
    e.currentTarget.classList.remove('dragover');

    const file = e.dataTransfer.files[0];
    if (file && file.type === 'text/csv') {
        processCSVFile(file);
    } else {
        Swal.fire({
            icon: 'error',
            title: 'Invalid File',
            text: 'Please upload a valid CSV file',
            confirmButtonColor: '#667eea'
        });
    }
}

function processCSVFile(file) {
    const reader = new FileReader();
    reader.onload = function(e) {
        const text = e.target.result;
        const rows = text.split('\n');
        const headers = rows[0].split(',');

        // Display preview
        const previewHead = document.getElementById('previewTableHead');
        const previewBody = document.getElementById('previewTableBody');

        previewHead.innerHTML = '<tr>' + headers.map(h => `<th>${h.trim()}</th>`).join('') + '</tr>';

        let previewHTML = '';
        for (let i = 1; i < Math.min(6, rows.length); i++) {
            if (rows[i].trim()) {
                const cells = rows[i].split(',');
                previewHTML += '<tr>' + cells.map(c => `<td>${c.trim()}</td>`).join('') + '</tr>';
            }
        }
        previewBody.innerHTML = previewHTML;

        document.getElementById('recordCount').textContent = rows.length - 1;
        document.getElementById('importPreview').style.display = 'block';
        document.getElementById('importBtn').disabled = false;
    };
    reader.readAsText(file);
}

function handleImport() {
    Swal.fire({
        icon: 'success',
        title: 'Success!',
        text: 'Data imported successfully',
        confirmButtonColor: '#667eea'
    });
    bootstrap.Modal.getInstance(document.getElementById('importModal')).hide();
}

function exportToCSV() {
    const headers = ['Reg No.', 'Student Name', 'Mobile No.', 'Total Fees', 'Fees Due', 'Total Paid', 'Due Date', 'Fees Refund', 'Status', 'Course'];

    let csv = headers.join(',') + '\n';

    feesData.forEach(row => {
        csv += [
            row.regNo,
            row.studentName,
            row.mobile,
            row.totalFees,
            row.feesDue,
            row.totalPaid,
            row.dueDate,
            row.feesRefund,
            row.status,
            row.course
        ].join(',') + '\n';
    });

    const blob = new Blob([csv], { type: 'text/csv' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'fees_data_' + new Date().toISOString().split('T')[0] + '.csv';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);

    Swal.fire({
        icon: 'success',
        title: 'Success!',
        text: 'Data exported successfully',
        confirmButtonColor: '#667eea'
    });
}

function printReceipt(receiptNo) {
    console.log('Printing receipt:', receiptNo);

    Swal.fire({
        icon: 'info',
        title: 'Print Receipt',
        text: `Printing receipt: ${receiptNo}`,
        confirmButtonColor: '#667eea'
    });
}

async function deleteReceipt(receiptId) {
    const result = await Swal.fire({
        title: 'Are you sure?',
        text: "You won't be able to revert this!",
        icon: 'warning',
        showCancelButton: true,
        confirmButtonColor: '#667eea',
        cancelButtonColor: '#ef4444',
        confirmButtonText: 'Yes, delete it!'
    });

    if (result.isConfirmed) {
        try {
             const response = await fetch(
                 `/api/fees-manager/receipts/${receiptId}`,
                 {
                     method: 'DELETE',
                     headers: getCsrfHeaders()
                 }
             );

            if (response.ok) {
                Swal.fire({
                    icon: 'success',
                    title: 'Deleted!',
                    text: 'Receipt has been deleted.',
                    confirmButtonColor: '#667eea'
                }).then(() => {
                    // Reload receipts for current student
                    if (currentStudentId) {
                        viewReceipts(currentStudentId);
                    }
                });
            } else {
                throw new Error('Delete failed');
            }

        } catch (error) {
            Swal.fire({
                icon: 'error',
                title: 'Error',
                text: 'Failed to delete receipt',
                confirmButtonColor: '#ef4444'
            });
        }
    }
}

// ==================== Modal Functions - Search by admissionId ====================

async function openFeeReceipt(regNo) {
    if (!regNo) {
        showError('Student registration number is missing');
        return;
    }

    //  Check if this is an old student
    const isOldStudent = !regNo.startsWith('REG');

    //  RESET MODAL TITLE FOR NEW RECEIPT
    document.querySelector('#feeReceiptModal .modal-title').innerHTML =
        '<i class="bi bi-receipt me-2"></i>New Fee Receipt';

    //  RESET BUTTON TEXT
    delete document.getElementById('btnSaveReceipt').dataset.receiptId;
    document.getElementById('btnSaveReceipt').innerHTML =
        '<i class="bi bi-check-circle me-2"></i>Save Changes';

    currentStudentRegNo = regNo;
    const student = feesData.find(s => s.regNo === regNo);

    if (!student) {
        showError('Student record not found');
        return;
    }

    // Reset form
    document.getElementById('feeReceiptForm').reset();

    // Populate form
    document.getElementById('receiptStudentName').value = student.studentName;
    document.getElementById('receiptTotalFees').value = student.totalFees;
    document.getElementById('receiptPendingFees').value = student.feesDue;
    document.getElementById('receivedFees').value = student.totalPaid;

    // Set today's date
    const today = new Date().toISOString().split('T')[0];
    document.getElementById('receiptDate').value = today;

    //  Handle installments based on student type
    if (isOldStudent) {
        // For old students: Make installment optional
        const installmentLabel = document.querySelector('label[for="installment"]');
        if (installmentLabel) {
            // Remove required indicator
            const requiredSpan = installmentLabel.querySelector('.required');
            if (requiredSpan) {
                requiredSpan.remove();
            }
        }

        // Set default "Not Applicable" option
        const installmentSelect = document.getElementById('installment');
        installmentSelect.innerHTML = '<option value="" selected>Not Applicable (Old Student)</option>';
        installmentSelect.disabled = false; // Keep enabled but with NA option

        console.log('ℹ️ Old student - Installment field set to optional');
    } else {
        // For new students (REG*): Load installments normally
        await loadInstallmentsForReceipt(regNo);
    }

    // Set next due date automatically
    if (!isOldStudent) {
        setNextInstallmentDueDate(regNo);
    } else {
        // For old students, set next due date to 30 days from now
        const nextDate = new Date();
        nextDate.setDate(nextDate.getDate() + 30);
        document.getElementById('nextDueDate').value = nextDate.toISOString().split('T')[0];
    }

    new bootstrap.Modal(document.getElementById('feeReceiptModal')).show();
}

// Set next installment due date based on existing installments
async function setNextInstallmentDueDate(regNo) {
    try {
        const response = await fetch(`${API_BASE}/installments/${regNo}`, {
            headers: getCsrfHeaders()
        });

        if (response.ok) {
            const installments = await response.json();

            if (installments && installments.length > 0) {
                // Find the last pending or last installment
                const pendingInstallments = installments.filter(i => i.status === 'Pending');

                if (pendingInstallments.length > 0) {
                    // Get the next pending installment date
                    const nextInstallment = pendingInstallments[0];
                    document.getElementById('nextDueDate').value = nextInstallment.dueDate;
                } else {
                    // All paid, calculate next date from last installment
                    const lastInstallment = installments[installments.length - 1];
                    const lastDate = new Date(lastInstallment.dueDate);

                    // Get days gap from fees data
                    const student = feesData.find(s => s.regNo === regNo);
                    const daysGap = student?.daysBetweenInstallments || 30;

                    lastDate.setDate(lastDate.getDate() + daysGap);
                    document.getElementById('nextDueDate').value = lastDate.toISOString().split('T')[0];
                }
            } else {
                // No installments, set 30 days from today
                const nextDate = new Date();
                nextDate.setDate(nextDate.getDate() + 30);
                document.getElementById('nextDueDate').value = nextDate.toISOString().split('T')[0];
            }
        }
    } catch (error) {
        console.error('Error setting next due date:', error);
        // Default to 30 days from today
        const nextDate = new Date();
        nextDate.setDate(nextDate.getDate() + 30);
        document.getElementById('nextDueDate').value = nextDate.toISOString().split('T')[0];
    }
}

/**
 *  Helper: Check if student is old (imported)
 */
function isOldStudent(regNo) {
    return regNo && !regNo.startsWith('REG');
}

/**
 *  Helper: Show/hide installment hint
 */
function toggleInstallmentHint(isOld) {
    const hint = document.getElementById('installmentHint');
    const required = document.getElementById('installmentRequired');

    if (hint) hint.style.display = isOld ? 'block' : 'none';
    if (required) required.style.display = isOld ? 'none' : 'inline';
}

async function emailReceipt(receiptNo, studentName, mobile) {
    // Get regNo from the receipt data
    let studentEmail = '';
    let actualRegNo = null;

    try {
        // First, find the student in feesData by name
        const student = feesData.find(s => s.studentName === studentName);

        if (student) {
            actualRegNo = student.regNo;
            console.log(' Found regNo from feesData:', actualRegNo);
        } else {
            console.error(' Student not found in feesData:', studentName);
            showError('Could not find student registration number');
            return;
        }

        // Now fetch admission data using the CORRECT registration number
        const admResponse = await fetch(`/api/admissions/by-regno/${actualRegNo}`, {
            headers: getCsrfHeaders()
        });

        if (admResponse.ok) {
            const admission = await admResponse.json();
            studentEmail = admission.emailPrimary || '';
        }
    } catch (error) {
        console.error('Error fetching email:', error);
    }

    // Close view receipts modal if open
    const viewReceiptsModal = document.getElementById('viewReceiptsModal');
    if (viewReceiptsModal) {
        const modalInstance = bootstrap.Modal.getInstance(viewReceiptsModal);
        if (modalInstance) {
            modalInstance.hide();
        }
    }

    document.querySelectorAll('.modal-backdrop').forEach(backdrop => backdrop.remove());
    document.body.classList.remove('modal-open');
    document.body.style.removeProperty('padding-right');

    await new Promise(resolve => setTimeout(resolve, 400));

    const { value: formValues, isConfirmed } = await Swal.fire({
        title: 'Send Receipt via Email',
        html: `
            <div class="text-start">
                <div class="mb-3">
                    <label class="form-label fw-bold">Receipt No.</label>
                    <input type="text" class="form-control" value="${receiptNo}" readonly
                           style="background-color: #f8f9fa;">
                </div>
                <div class="mb-3">
                    <label class="form-label fw-bold">Student Name <span class="text-danger">*</span></label>
                    <input type="text" class="form-control" id="emailStudentName"
                           value="${studentName}" required>
                </div>
                <div class="mb-3">
                    <label class="form-label fw-bold">Email Address <span class="text-danger">*</span></label>
                    <input type="email" class="form-control" id="emailAddress"
                           placeholder="student@example.com"
                           value="${studentEmail}" required>
                    <small class="text-muted">Receipt will be sent as PDF attachment</small>
                </div>
                <div class="mb-3">
                    <label class="form-label fw-bold">Additional Message (Optional)</label>
                    <textarea class="form-control" id="emailMessage" rows="3"
                              placeholder="Add any additional message..."></textarea>
                </div>
            </div>
        `,
        showCancelButton: true,
        confirmButtonText: '<i class="bi bi-envelope me-2"></i>Send Email',
        cancelButtonText: 'Cancel',
        confirmButtonColor: '#667eea',
        width: '600px',
        showLoaderOnConfirm: true,
        preConfirm: async () => {
            const email = document.getElementById('emailAddress').value.trim();
            const name = document.getElementById('emailStudentName').value.trim();

            // Validation
            if (!email) {
                Swal.showValidationMessage('Please enter an email address');
                return false;
            }
            if (!name) {
                Swal.showValidationMessage('Please enter student name');
                return false;
            }
            const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
            if (!emailRegex.test(email)) {
                Swal.showValidationMessage('Please enter a valid email address');
                return false;
            }

            if (!actualRegNo) {
                Swal.showValidationMessage('Could not find registration number');
                return false;
            }

            try {
                console.log('🔍 Fetching receipt for regNo:', actualRegNo);

                // Fetch receipt data
                const receiptResponse = await fetch(`${API_BASE}/receipts/${actualRegNo}`, {
                    headers: getCsrfHeaders()
                });

                if (!receiptResponse.ok) {
                    const errorText = await receiptResponse.text();
                    console.error(' Receipt fetch failed:', errorText);
                    Swal.showValidationMessage('Failed to load receipt data');
                    return false;
                }

                const receipts = await receiptResponse.json();
                const receipt = receipts.find(r => r.receiptNumber === receiptNo);

                if (!receipt) {
                    console.error(' Receipt not found in response:', receiptNo);
                    Swal.showValidationMessage('Receipt not found');
                    return false;
                }

                // Get CURRENT fees data from feesData array
                const student = feesData.find(s => s.regNo === actualRegNo);

                // Use CURRENT data instead of receipt's historical data
                const receiptData = {
                    ...receipt,
                    mobile: student?.mobile || mobile || 'N/A',
                    course: student?.course || 'N/A',
                    email: email,
                    // Override with CURRENT pending fees and due date
                    pendingFees: student?.feesDue || 0,
                    nextDueDate: student?.dueDate || null
                };

                console.log('📄 Generating PDF with CURRENT data:', {
                    pendingFees: receiptData.pendingFees,
                    nextDueDate: receiptData.nextDueDate
                });

                // Generate PDF
                const pdfBase64 = await generateInvoicePDF(receiptData);

                if (!pdfBase64) {
                    Swal.showValidationMessage('Failed to generate PDF');
                    return false;
                }

                // Validate PDF size
                const pdfSizeKB = Math.round((pdfBase64.length * 3 / 4) / 1024);
                console.log(`📊 PDF size: ${pdfSizeKB} KB (${pdfBase64.length} chars base64)`);

                if (pdfSizeKB > 8192) { // 8MB limit for safety
                    Swal.showValidationMessage(`PDF too large (${pdfSizeKB}KB). Maximum 8MB allowed.`);
                    return false;
                }

                // Show progress for large PDFs
                if (pdfSizeKB > 2048) { // > 2MB
                    console.log('⏳ Large PDF detected, sending may take longer...');
                }

                // Increase timeout for large PDFs
                const controller = new AbortController();
                const timeoutMs = 60000; // 60 seconds
                const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

                try {
                    console.log('📧 Sending email to:', email);
                    console.log('📤 Request payload size:', JSON.stringify({
                        email: email,
                        studentName: name,
                        message: document.getElementById('emailMessage').value,
                        pdfData: pdfBase64.substring(0, 100) + '...' // Log first 100 chars only
                    }).length, 'bytes');

                    const response = await fetch(`${API_BASE}/receipts/${receiptNo}/send-email`, {
                        method: 'POST',
                        headers: getCsrfHeaders(),
                        body: JSON.stringify({
                            email: email,
                            studentName: name,
                            message: document.getElementById('emailMessage').value,
                            pdfData: pdfBase64
                        }),
                        signal: controller.signal
                    });

                    clearTimeout(timeoutId);

                    // Better error handling
                    if (!response.ok) {
                        const contentType = response.headers.get('content-type');
                        let errorMessage = 'Failed to send email';

                        console.error(' Server response status:', response.status);
                        console.error(' Server response headers:',
                            Object.fromEntries(response.headers.entries()));

                        if (contentType && contentType.includes('application/json')) {
                            try {
                                const errorData = await response.json();
                                errorMessage = errorData.message || errorMessage;
                                console.error(' Server error JSON:', errorData);
                            } catch (jsonError) {
                                console.error(' Failed to parse error JSON:', jsonError);
                            }
                        } else {
                            const errorText = await response.text();
                            console.error(' Server error text:', errorText.substring(0, 500));

                            // Check for specific error patterns
                            if (errorText.includes('JSON parse error')) {
                                errorMessage = 'Failed to process PDF. Please try again or contact support.';
                            } else if (errorText.includes('Unexpected end-of-input')) {
                                errorMessage = 'PDF data was truncated. Please try again.';
                            }
                        }

                        Swal.showValidationMessage(errorMessage);
                        return false;
                    }

                    const result = await response.json();
                    console.log(' Email API response:', result);

                    return { email, result };

                } catch (error) {
                    clearTimeout(timeoutId);

                    if (error.name === 'AbortError') {
                        console.log('⏱ Email request timed out after', timeoutMs/1000, 'seconds');
                        console.log('ℹ️ Email is likely being processed in background');
                        // Don't fail - return success as email is processing
                        return { email, timeout: true };
                    }

                    console.error(' Network error:', error);
                    console.error(' Error name:', error.name);
                    console.error(' Error message:', error.message);
                    console.error(' Error stack:', error.stack);

                    Swal.showValidationMessage('Network error: ' + error.message);
                    return false;
                }
            } catch (error) {
                console.error(' Error in email process:', error);
                console.error(' Error type:', error.constructor.name);
                console.error(' Error details:', error);
                Swal.showValidationMessage('Unexpected error: ' + error.message);
                return false;
            }
        }
    });

    if (isConfirmed && formValues) {
        let message = `Receipt is being sent to: <p class="fw-bold text-primary">${formValues.email}</p>`;

        if (formValues.timeout) {
            message += '<p class="small text-muted">⏳ Request timed out but email is being processed in background.</p>';
        } else {
            message += '<p class="small text-muted">Please check your inbox in a few moments</p>';
        }

        Swal.fire({
            icon: 'success',
            title: 'Email Sent!',
            html: message,
            confirmButtonColor: '#667eea'
        });
    }
}

function changeStatus(admissionId) {
    if (!admissionId) {
        Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'Student admission ID is missing',
            confirmButtonColor: '#667eea'
        });
        return;
    }

    currentStudentId = admissionId;
    new bootstrap.Modal(document.getElementById('changeStatusModal')).show();
}

function changeStatus(admissionId) {
    if (!admissionId) {
        Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'Student admission ID is missing',
            confirmButtonColor: '#667eea'
        });
        return;
    }

    currentStudentId = admissionId;
    new bootstrap.Modal(document.getElementById('changeStatusModal')).show();
}

//  manageInstallments function
function manageInstallments(admissionId) {
    if (!admissionId) {
        Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'Student admission ID is missing',
            confirmButtonColor: '#667eea'
        });
        return;
    }

    currentStudentId = admissionId;
    const student = feesData.find(s => s.admissionId === admissionId);

    if (!student) {
        Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'Student record not found',
            confirmButtonColor: '#667eea'
        });
        return;
    }

    document.getElementById('installmentTotalAmt').value = student.totalFees;
    document.getElementById('totalInstallmentAmt').value = student.totalFees;

    new bootstrap.Modal(document.getElementById('installmentsModal')).show();
}

//  feesRefund function
function feesRefund(regNo) {

    if (!regNo) {
        Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'Student registration number is missing',
            confirmButtonColor: '#667eea'
        });
        return;
    }

    currentStudentRegNo = regNo;
    const student = feesData.find(s => s.regNo === regNo);

    if (!student) {
        Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'Student record not found',
            confirmButtonColor: '#667eea'
        });
        return;
    }

    document.getElementById('refundStudentName').value = student.studentName;
    document.getElementById('refundTotalFees').value = student.totalFees;
    document.getElementById('refundPaidFees').value = student.totalPaid;
    document.getElementById('refundPendingFees').value = student.feesDue;

    loadRefundHistory(regNo);

    new bootstrap.Modal(document.getElementById('feesRefundModal')).show();
}

async function updateFeeReceipt(receiptId, regNo) {
    try {
        showLoading('Loading receipt...');

        const response = await fetch(`${API_BASE}/receipts/${regNo}`, {
            headers: getCsrfHeaders()
        });

        if (!response.ok) throw new Error('Failed to load receipt');

        const receipts = await response.json();
        const receipt = receipts.find(r => r.id === receiptId);

        if (!receipt) throw new Error('Receipt not found');

        Swal.close();

        // Close view receipts modal
        bootstrap.Modal.getInstance(document.getElementById('viewReceiptsModal')).hide();

        //  CHANGE MODAL TITLE
        document.querySelector('#feeReceiptModal .modal-title').innerHTML =
            '<i class="bi bi-pencil-square me-2"></i>Update Fee Receipt';

        // Open fee receipt modal with data
        currentStudentRegNo = regNo;
        const student = feesData.find(s => s.regNo === regNo);

        // Populate form
        document.getElementById('receiptStudentName').value = student.studentName;
        document.getElementById('receiptTotalFees').value = student.totalFees;
        document.getElementById('receiptPendingFees').value = student.feesDue;
        document.getElementById('receivedFees').value = receipt.previousPaid || 0;
        document.getElementById('nowReceiving').value = receipt.amountReceived;
        document.getElementById('receiptDate').value = receipt.receiptDate;
        document.getElementById('paymentMode').value = receipt.paymentMode;
        document.getElementById('receiptNotes').value = receipt.notes || '';

        if (receipt.nextDueDate) {
            document.getElementById('nextDueDate').value = receipt.nextDueDate;
        }

        togglePaymentFields();

        if (receipt.bankName) document.getElementById('bankName').value = receipt.bankName;
        if (receipt.chequeNumber) document.getElementById('chequeNo').value = receipt.chequeNumber;
        if (receipt.chequeDate) document.getElementById('chequeDate').value = receipt.chequeDate;
        if (receipt.transactionNumber) document.getElementById('transactionNo').value = receipt.transactionNumber;
        if (receipt.ifscCode) document.getElementById('ifscCode').value = receipt.ifscCode;
        if (receipt.onlinePaymentMode) document.getElementById('onlinePaymentMode').value = receipt.onlinePaymentMode;

        // Load installments
        await loadInstallmentsForReceipt(regNo);

        // Store receipt ID for update
        document.getElementById('btnSaveReceipt').dataset.receiptId = receiptId;
        document.getElementById('btnSaveReceipt').innerHTML = '<i class="bi bi-arrow-repeat me-2"></i>Update Receipt';

        new bootstrap.Modal(document.getElementById('feeReceiptModal')).show();

    } catch (error) {
        Swal.close();
        console.error('Error:', error);
        showError('Failed to load receipt for update');
    }
}

async function loadInstallmentsForReceipt(regNo) {
    try {
        const response = await fetch(`${API_BASE}/installments/${regNo}`, {
            headers: getCsrfHeaders()
        });

        if (response.ok) {
            const installments = await response.json();
            const select = document.getElementById('installment');

            select.innerHTML = '<option value="">-- Select Installment --</option>';

            // Only show pending installments
            const pendingInstallments = installments.filter(i => i.status === 'Pending');

            //  ADD: Store all installments for next due date calculation
            select.dataset.allInstallments = JSON.stringify(installments);

            pendingInstallments.forEach(inst => {
                const option = document.createElement('option');
                option.value = inst.id;
                option.textContent = `Installment ${inst.installmentNumber} - ₹${inst.amount.toFixed(2)} (Due: ${inst.dueDate})`;
                //  ADD: Store installment number in option
                option.dataset.installmentNumber = inst.installmentNumber;
                option.dataset.dueDate = inst.dueDate;
                select.appendChild(option);
            });

            if (pendingInstallments.length === 0) {
                select.innerHTML = '<option value="">All installments paid</option>';
            }

            //  ADD: Auto-update next due date when installment is selected
            select.addEventListener('change', updateNextDueDateFromInstallment);
        }
    } catch (error) {
        console.error('Error loading installments:', error);
    }
}

// New function to auto-update next due date
function updateNextDueDateFromInstallment() {
    const select = document.getElementById('installment');
    const nextDueDateInput = document.getElementById('nextDueDate');

    const selectedOption = select.options[select.selectedIndex];

    if (!selectedOption || !selectedOption.value) {
        return; // No installment selected
    }

    const currentInstallmentNumber = parseInt(selectedOption.dataset.installmentNumber);
    const allInstallments = JSON.parse(select.dataset.allInstallments || '[]');

    //  Find the NEXT installment (current + 1)
    const nextInstallment = allInstallments.find(
        inst => inst.installmentNumber === currentInstallmentNumber + 1
    );

    if (nextInstallment) {
        //  Set next installment's due date
        nextDueDateInput.value = nextInstallment.dueDate;
        console.log(` Auto-set next due date to Installment ${nextInstallment.installmentNumber}: ${nextInstallment.dueDate}`);
    } else {
        //  This is the last installment - clear next due date
        nextDueDateInput.value = '';
        console.log('ℹ️ Last installment - no next due date');
    }
}

// ==================== Save Refund ====================

async function saveRefund() {
    const regNo = currentStudentRegNo;

    if (!regNo) {
        Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'Student information is missing',
            confirmButtonColor: '#667eea'
        });
        return;
    }

    const refundAmount = parseFloat(document.getElementById('refundAmount').value);

    if (!refundAmount || refundAmount <= 0) {
        Swal.fire({
            icon: 'error',
            title: 'Validation Error',
            text: 'Please enter a valid refund amount',
            confirmButtonColor: '#667eea'
        });
        return;
    }

    // VALIDATE NOTE FIELD
    const notes = document.getElementById('refundNotes').value?.trim();
    if (!notes || notes.length === 0) {
        Swal.fire({
            icon: 'error',
            title: 'Validation Error',
            text: 'Please enter a note/reason for the refund',
            confirmButtonColor: '#667eea'
        });
        return;
    }


    const refundData = {
        regNo: regNo,
        refundDate: document.getElementById('refundDate').value,
        refundAmount: refundAmount,
        totalFees: parseFloat(document.getElementById('refundTotalFees').value) || 0,
        paidFees: parseFloat(document.getElementById('refundPaidFees').value) || 0,
        pendingFees: parseFloat(document.getElementById('refundPendingFees').value) || 0,
        paymentMode: document.getElementById('refundPaymentMode').value,
        bankName: document.getElementById('refundBankName').value || null,
        chequeNumber: document.getElementById('refundChequeNo').value || null,
        chequeDate: document.getElementById('refundChequeDate').value || null,
        transactionNumber: document.getElementById('refundTransactionNo').value || null,
        ifscCode: document.getElementById('refundIfscCode').value || null,
        onlinePaymentMode: document.getElementById('refundOnlinePaymentMode').value || null,
        paymentClear: document.getElementById('refundPaymentClear').value === 'true',
        notes: document.getElementById('refundNotes').value || null
    };

    try {
        Swal.fire({
            title: 'Saving...',
            text: 'Please wait while we process the refund',
            allowOutsideClick: false,
            didOpen: () => Swal.showLoading()
        });

      const response = await fetch('/api/fees-manager/refunds', {
          method: 'POST',
          headers: getCsrfHeaders(),
          body: JSON.stringify(refundData)
      });

        const result = await response.json();

        Swal.close();

        if (response.ok) {
            Swal.fire({
                icon: 'success',
                title: 'Success!',
                text: 'Refund saved successfully',
                confirmButtonColor: '#667eea'
            }).then(() => {
                bootstrap.Modal.getInstance(document.getElementById('feesRefundModal')).hide();
                loadFeesFromBackend();
            });
        } else {
            throw new Error(result.message || 'Failed to save refund');
        }

    } catch (error) {
        Swal.close();
        console.error('Error saving refund:', error);
        Swal.fire({
            icon: 'error',
            title: 'Error',
            text: error.message || 'Failed to save refund',
            confirmButtonColor: '#ef4444'
        });
    }
}

// ==================== View Receipt Preview ====================

async function viewReceiptPreview(receiptNo, regNo) {
    try {
        showLoading('Loading receipt...');

        //  FIX: Use regNo parameter, not currentStudentRegNo
        console.log('🔍 viewReceiptPreview called with:', { receiptNo, regNo });

        const response = await fetch(`${API_BASE}/receipts/${regNo}`, {
            headers: getCsrfHeaders()
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: Failed to load receipt`);
        }

        const receipts = await response.json();
        const receipt = receipts.find(r => r.receiptNumber === receiptNo);

        if (!receipt) {
            throw new Error(`Receipt ${receiptNo} not found in response`);
        }

        const student = feesData.find(s => s.regNo === regNo);

        let currentPendingFees = 0;
        let currentNextDueDate = null;

        if (student) {
            currentPendingFees = student.feesDue || 0;
            currentNextDueDate = student.dueDate || null;
        }

        const pendingAtReceiptTime = receipt.pendingFees || 0;

        Swal.close();

        const receiptHTML = generateReceiptHTML({
            ...receipt,
            mobile: student?.mobile || 'N/A',
            course: student?.course || 'N/A',
            pendingFees: currentPendingFees,
            nextDueDate: currentNextDueDate,
            pendingFeesAtTime: pendingAtReceiptTime
        });

        Swal.fire({
            title: 'Fee Receipt Preview',
            html: receiptHTML,
            width: '800px',
            showCloseButton: true,
            showCancelButton: true,
            showConfirmButton: true,
            confirmButtonText: '<i class="bi bi-printer me-2"></i>Print Receipt',
            cancelButtonText: '<i class="bi bi-envelope me-2"></i>Send via Email',
            confirmButtonColor: '#667eea',
            cancelButtonColor: '#10b981',
            reverseButtons: true,
            customClass: {
                htmlContainer: 'receipt-preview-container'
            }
        }).then((result) => {
            if (result.isConfirmed) {
                printReceiptContent(receiptHTML);
            } else if (result.dismiss === Swal.DismissReason.cancel) {
                emailReceipt(receiptNo, receipt.studentName, student?.mobile);
            }
        });

    } catch (error) {
        Swal.close();
        console.error(' Error in viewReceiptPreview:', error);
        Swal.fire({
            icon: 'error',
            title: 'Error',
            text: error.message || 'Failed to load receipt',
            confirmButtonColor: '#ef4444'
        });
    }
}

/**
 * Generate professional invoice PDF from receipt data
 *  Now uses CURRENT pending fees and due date instead of historical data
 */
async function generateInvoicePDF(receiptData) {
    return new Promise((resolve, reject) => {
        try {
            const { jsPDF } = window.jspdf;
            const doc = new jsPDF('p', 'mm', 'a4');

            const pageWidth = doc.internal.pageSize.getWidth();
            const margin = 15;
            let y = 15;

            // ===== ADD LOGO =====
            const logoUrl = '/assets/images/tts-logo-ev.png';
            try {
                doc.addImage(logoUrl, 'PNG', margin, y, 20, 25);
            } catch (e) {
                console.warn('Logo not found:', e);
            }

            // ===== HEADER - Company Info =====
            doc.setFontSize(16);
            doc.setFont('helvetica', 'bold');
            doc.setTextColor(0, 0, 0);
            doc.text('TechnoKraft Training Solutions', margin + 30, y + 6);

            y += 10;
            doc.setFontSize(9);
            doc.setFont('helvetica', 'normal');
            doc.text('1st Floor, Kanchwala Avenue, Above Viju\'s Dabeli', margin + 30, y);

            y += 4;
            doc.text('College Road, Nashik, Maharashtra - 422005', margin + 30, y);

            y += 4;
            doc.text('Phone: +91 02532312447 | Email: info@tts.net.in', margin + 30, y);

            y += 12;

            // ===== TITLE BAR =====
            doc.setDrawColor(0, 0, 0);
            doc.setLineWidth(0.5);
            doc.line(margin, y, pageWidth - margin, y);

            y += 10;
            doc.setFontSize(14);
            doc.setFont('helvetica', 'bold');
            doc.text('FEES RECEIPT', pageWidth / 2, y, { align: 'center' });

            y += 6;
            doc.line(margin, y, pageWidth - margin, y);
            y += 10;

            // ===== TABLE SETUP =====
            const rowHeight = 9;
            const fullWidth = pageWidth - 2 * margin;
            const halfWidth = fullWidth / 2;
            const labelWidth = 45;
            const valueWidth = halfWidth - labelWidth;

            doc.setFontSize(10);
            doc.setDrawColor(0, 0, 0);
            doc.setLineWidth(0.3);

            // ===== ROW 1: Receipt Date & Receipt No =====
            doc.rect(margin, y, labelWidth, rowHeight);
            doc.setFont('helvetica', 'bold');
            doc.text('Receipt Date:', margin + 2, y + 6);

            doc.rect(margin + labelWidth, y, valueWidth, rowHeight);
            doc.setFont('helvetica', 'normal');
            doc.text(formatDate(receiptData.receiptDate), margin + labelWidth + 2, y + 6);

            doc.rect(margin + halfWidth, y, labelWidth, rowHeight);
            doc.setFont('helvetica', 'bold');
            doc.text('Receipt No:', margin + halfWidth + 2, y + 6);

            doc.rect(margin + halfWidth + labelWidth, y, valueWidth, rowHeight);
            doc.setFont('helvetica', 'normal');
            doc.text(receiptData.receiptNumber || 'N/A', margin + halfWidth + labelWidth + 2, y + 6);

            y += rowHeight;

            // ===== ROW 2: Invoice No & Reg. No =====
            doc.rect(margin, y, labelWidth, rowHeight);
            doc.setFont('helvetica', 'bold');
            doc.text('Invoice No:', margin + 2, y + 6);

            doc.rect(margin + labelWidth, y, valueWidth, rowHeight);
            doc.setFont('helvetica', 'normal');
            doc.text(receiptData.invoiceNumber || 'N/A', margin + labelWidth + 2, y + 6);

            doc.rect(margin + halfWidth, y, labelWidth, rowHeight);
            doc.setFont('helvetica', 'bold');
            doc.text('Reg. No:', margin + halfWidth + 2, y + 6);

            doc.rect(margin + halfWidth + labelWidth, y, valueWidth, rowHeight);
            doc.setFont('helvetica', 'normal');
            doc.text(receiptData.registrationNumber || 'N/A', margin + halfWidth + labelWidth + 2, y + 6);

            y += rowHeight;

            // ===== ROW 3: Student Name (full width) =====
            doc.rect(margin, y, labelWidth, rowHeight);
            doc.setFont('helvetica', 'bold');
            doc.text('Student Name:', margin + 2, y + 6);

            doc.rect(margin + labelWidth, y, fullWidth - labelWidth, rowHeight);
            doc.setFont('helvetica', 'normal');
            doc.text(receiptData.studentName || 'N/A', margin + labelWidth + 2, y + 6);

            y += rowHeight;

            // ===== ROW 4: Contact No & Email =====
            doc.rect(margin, y, labelWidth, rowHeight);
            doc.setFont('helvetica', 'bold');
            doc.text('Contact No:', margin + 2, y + 6);

            doc.rect(margin + labelWidth, y, valueWidth, rowHeight);
            doc.setFont('helvetica', 'normal');
            doc.text(receiptData.mobile || 'N/A', margin + labelWidth + 2, y + 6);

            doc.rect(margin + halfWidth, y, labelWidth, rowHeight);
            doc.setFont('helvetica', 'bold');
            doc.text('Email:', margin + halfWidth + 2, y + 6);

            doc.rect(margin + halfWidth + labelWidth, y, valueWidth, rowHeight);
            doc.setFont('helvetica', 'normal');
            doc.setFontSize(8);
            const emailText = receiptData.email || 'N/A';
            doc.text(emailText, margin + halfWidth + labelWidth + 2, y + 6);
            doc.setFontSize(10);

            y += rowHeight;

            // ===== ROW 5: Courses (full width, dynamic height) =====
            doc.rect(margin, y, labelWidth, rowHeight);
            doc.setFont('helvetica', 'bold');
            doc.text('Courses:', margin + 2, y + 6);

            const courseText = receiptData.course || 'N/A';
            const maxCourseWidth = fullWidth - labelWidth - 4;
            const splitCourse = doc.splitTextToSize(courseText, maxCourseWidth);
            const courseHeight = Math.max(rowHeight, splitCourse.length * 5 + 4);

            doc.rect(margin + labelWidth, y, fullWidth - labelWidth, courseHeight);
            doc.setFont('helvetica', 'normal');
            doc.text(splitCourse, margin + labelWidth + 2, y + 6);

            y += courseHeight;

            // ===== ROW 6: Amount Received =====
            doc.rect(margin, y, labelWidth, rowHeight);
            doc.setFont('helvetica', 'bold');
            doc.text('Amount Received:', margin + 2, y + 6);

            doc.rect(margin + labelWidth, y, fullWidth - labelWidth, rowHeight);
            doc.setFont('helvetica', 'normal');
            doc.text('Rs. ' + formatCurrency(receiptData.amountReceived), margin + labelWidth + 2, y + 6);

            y += rowHeight;

            // ===== ROW 7: Amount in Words =====
            doc.rect(margin, y, labelWidth, rowHeight);
            doc.setFont('helvetica', 'bold');
            doc.text('Amount (in words):', margin + 2, y + 6);

            doc.rect(margin + labelWidth, y, fullWidth - labelWidth, rowHeight);
            doc.setFont('helvetica', 'italic');
            doc.setFontSize(9);
            doc.text(numberToWords(receiptData.amountReceived), margin + labelWidth + 2, y + 6);

            y += rowHeight;
            doc.setFontSize(10);

            // ===== PAYMENT DETAILS =====
            const paymentRows = [
                ['Payment Mode:', receiptData.paymentMode || 'Cash'],
                ['Cheque No:', receiptData.chequeNumber || 'NA'],
                ['Cheque Dated:', receiptData.chequeDate ? formatDate(receiptData.chequeDate) : 'NA'],
                ['Bank Name:', receiptData.bankName || 'NA'],
                ['IFSC Code:', receiptData.ifscCode || 'NA'],
                ['Online Tranx. No:', receiptData.transactionNumber || 'NA']
            ];

            paymentRows.forEach(([label, value]) => {
                doc.rect(margin, y, labelWidth, rowHeight);
                doc.setFont('helvetica', 'bold');
                doc.text(label, margin + 2, y + 6);

                doc.rect(margin + labelWidth, y, fullWidth - labelWidth, rowHeight);
                doc.setFont('helvetica', 'normal');
                doc.text(value, margin + labelWidth + 2, y + 6);

                y += rowHeight;
            });

            // =====  Due Date Row - Show "Paid in Full" when fees are zero =====
            const dueDateValue = (receiptData.nextDueDate && receiptData.pendingFees > 0.01)
                ? formatDate(receiptData.nextDueDate)
                : 'Paid in Full';

            doc.rect(margin, y, labelWidth, rowHeight);
            doc.setFont('helvetica', 'bold');
            doc.text('Due Date:', margin + 2, y + 6);

            doc.rect(margin + labelWidth, y, fullWidth - labelWidth, rowHeight);
            doc.setFont('helvetica', 'normal');
            doc.text(dueDateValue, margin + labelWidth + 2, y + 6);

            y += rowHeight;

            // =====  Due Fees Row - Show "Rs. 0.00 (Paid in Full)" when fees are zero =====
            const dueFeesValue = receiptData.pendingFees > 0.01
                ? 'Rs. ' + formatCurrency(receiptData.pendingFees || 0)
                : 'Rs. 0.00 (Paid in Full)';

            doc.rect(margin, y, labelWidth, rowHeight);
            doc.setFont('helvetica', 'bold');
            doc.text('Due Fees:', margin + 2, y + 6);

            doc.rect(margin + labelWidth, y, fullWidth - labelWidth, rowHeight);
            doc.setFont('helvetica', 'normal');
            doc.text(dueFeesValue, margin + labelWidth + 2, y + 6);

            y += rowHeight;

            // ===== Total Fees Row =====
            doc.rect(margin, y, labelWidth, rowHeight);
            doc.setFont('helvetica', 'bold');
            doc.text('Total Fees:', margin + 2, y + 6);

            doc.rect(margin + labelWidth, y, fullWidth - labelWidth, rowHeight);
            doc.setFont('helvetica', 'normal');
            doc.text('Rs. ' + formatCurrency(receiptData.totalFees || 0), margin + labelWidth + 2, y + 6);

            y += rowHeight;
            y += 3; // Extra spacing before terms

            // ===== TERMS & CONDITIONS =====
            const termsHeight = 32;
            doc.rect(margin, y, fullWidth, termsHeight);

            y += 6;
            doc.setFontSize(9);
            doc.setFont('helvetica', 'bold');
            doc.text('Terms and Conditions:', margin + 3, y);

            y += 5;
            doc.setFont('helvetica', 'normal');
            doc.setFontSize(8);
            const terms = [
                'This receipt of fees is an acknowledgement of the payment made to TechnoKraft Training Solutions.',
                'Present this receipt of fees whenever demanded.',
                'Fees once paid is neither refundable nor transferable under any circumstances.',
                'This is a computer generated voucher, signature is not required.'
            ];

            terms.forEach(term => {
                const wrappedTerm = doc.splitTextToSize(term, fullWidth - 6);
                doc.text(wrappedTerm, margin + 3, y);
                y += wrappedTerm.length * 4.5;
            });

            y += 10;

            // ===== FOOTER =====
            doc.setFontSize(7);
            doc.setFont('helvetica', 'normal');
            doc.setTextColor(100, 100, 100);
            doc.text('Generated on ' + formatDate(new Date()), pageWidth / 2, y, { align: 'center' });

            const pdfBase64 = doc.output('dataurlstring').split(',')[1];
            resolve(pdfBase64);

        } catch (error) {
            console.error(' Error generating PDF:', error);
            reject(error);
        }
    });
}

// ==================== HELPER FUNCTIONS ====================

function formatDate(date) {
    if (!date) return 'N/A';
    const d = new Date(date);
    const options = { year: 'numeric', month: 'long', day: 'numeric' };
    return d.toLocaleDateString('en-US', options);
}

function formatCurrency(amount) {
    if (!amount) return '0.00';
    return parseFloat(amount).toLocaleString('en-IN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    });
}

function numberToWords(num) {
    if (!num || num === 0) return 'Zero Rupees Only';

    const ones = ['', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine'];
    const tens = ['', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety'];
    const teens = ['Ten', 'Eleven', 'Twelve', 'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen', 'Seventeen', 'Eighteen', 'Nineteen'];

    function convertLessThanThousand(n) {
        if (n === 0) return '';
        if (n < 10) return ones[n];
        if (n < 20) return teens[n - 10];
        if (n < 100) return tens[Math.floor(n / 10)] + (n % 10 !== 0 ? ' ' + ones[n % 10] : '');
        return ones[Math.floor(n / 100)] + ' Hundred' + (n % 100 !== 0 ? ' ' + convertLessThanThousand(n % 100) : '');
    }

    num = Math.floor(num);

    if (num < 1000) return convertLessThanThousand(num) + ' Rupees Only';
    if (num < 100000) {
        const thousands = Math.floor(num / 1000);
        const remainder = num % 1000;
        return convertLessThanThousand(thousands) + ' Thousand' +
               (remainder !== 0 ? ' ' + convertLessThanThousand(remainder) : '') + ' Rupees Only';
    }

    const lakhs = Math.floor(num / 100000);
    const remainder = num % 100000;
    const thousands = Math.floor(remainder / 1000);
    const hundreds = remainder % 1000;

    let result = convertLessThanThousand(lakhs) + ' Lakh';
    if (thousands > 0) result += ' ' + convertLessThanThousand(thousands) + ' Thousand';
    if (hundreds > 0) result += ' ' + convertLessThanThousand(hundreds);

    return result + ' Rupees Only';
}