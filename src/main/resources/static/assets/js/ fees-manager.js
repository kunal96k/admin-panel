// fees-manager.js

// Sample Data (Replace with actual API calls)
let feesData = [
    {
        regNo: 'STU001',
        studentName: 'John Doe',
        mobile: '9876543210',
        totalFees: 50000,
        feesDue: 15000,
        totalPaid: 35000,
        dueDate: '2024-12-31',
        feesRefund: 0,
        status: 'Pending',
        course: 'FULL STACK JAVA DEVELOPMENT'
    },
    {
        regNo: 'STU002',
        studentName: 'Jane Smith',
        mobile: '9876543211',
        totalFees: 45000,
        feesDue: 0,
        totalPaid: 45000,
        dueDate: '2024-11-30',
        feesRefund: 0,
        status: 'Clear',
        course: 'PYTHON - DATA ANALYTICS'
    }
];

let currentPage = 1;
let entriesPerPage = 25;
let filteredData = [...feesData];
let currentStudentId = null;

// Initialize
document.addEventListener('DOMContentLoaded', function() {
    initializeEventListeners();
    renderTable();
    setDefaultDates();
});

function initializeEventListeners() {
    // Search
    document.getElementById('searchInput').addEventListener('input', handleSearch);

    // Entries per page
    document.getElementById('entriesPerPage').addEventListener('change', function() {
        entriesPerPage = parseInt(this.value);
        currentPage = 1;
        renderTable();
    });

    // Import/Export
    document.getElementById('btnImportCSV').addEventListener('click', () => {
        new bootstrap.Modal(document.getElementById('importModal')).show();
    });
    document.getElementById('btnExportCSV').addEventListener('click', exportToCSV);

    // Import Modal
    document.getElementById('btnBrowseFile').addEventListener('click', () => {
        document.getElementById('csvFileInput').click();
    });
    document.getElementById('csvFileInput').addEventListener('change', handleFileSelect);
    document.getElementById('importBtn').addEventListener('click', handleImport);

    // Drag and drop
    const importArea = document.getElementById('importArea');
    importArea.addEventListener('dragover', handleDragOver);
    importArea.addEventListener('dragleave', handleDragLeave);
    importArea.addEventListener('drop', handleDrop);

    // Fee Receipt Modal
    document.getElementById('enableGst').addEventListener('change', toggleGstFields);
    document.getElementById('paymentMode').addEventListener('change', togglePaymentFields);
    document.getElementById('btnSaveReceipt').addEventListener('click', saveReceipt);
    document.getElementById('btnSavePrint').addEventListener('click', saveAndPrintReceipt);

    // Change Status Modal
    document.getElementById('btnSaveStatus').addEventListener('click', saveStatus);

    // Installments Modal
    document.getElementById('btnGenerateInstallments').addEventListener('click', generateInstallments);
    document.getElementById('btnSaveInstallments').addEventListener('click', saveInstallments);

    // Refund Modal
    document.getElementById('refundPaymentMode').addEventListener('change', toggleRefundPaymentFields);
    document.getElementById('btnSaveRefund').addEventListener('click', saveRefund);
    document.getElementById('btnSaveRefundPrint').addEventListener('click', saveAndPrintRefund);
}

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

function renderTable() {
    const tbody = document.querySelector('#feesTable tbody');
    const start = (currentPage - 1) * entriesPerPage;
    const end = start + entriesPerPage;
    const paginatedData = filteredData.slice(start, end);

    if (paginatedData.length === 0) {
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

    tbody.innerHTML = paginatedData.map(item => `
        <tr>
            <td><strong>${item.regNo}</strong></td>
            <td>${item.studentName}</td>
            <td>${item.mobile}</td>
            <td>₹${item.totalFees.toLocaleString()}</td>
            <td>₹${item.feesDue.toLocaleString()}</td>
            <td>₹${item.totalPaid.toLocaleString()}</td>
            <td>${formatDate(item.dueDate)}</td>
            <td>₹${item.feesRefund.toLocaleString()}</td>
            <td><span class="badge ${item.status === 'Clear' ? 'bg-success' : 'bg-warning'}">${item.status}</span></td>
            <td><span class="badge bg-primary">${item.course}</span></td>
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
                        <button class="action-menu-item" onclick="changeStatus('${item.regNo}')">
                            <i class="bi bi-arrow-repeat text-warning"></i>
                            <span>Update Fee Status</span>
                        </button>
                        <button class="action-menu-item" onclick="manageInstallments('${item.regNo}')">
                            <i class="bi bi-calendar3 text-success"></i>
                            <span>Fee Installments</span>
                        </button>
                        <button class="action-menu-item" onclick="feesRefund('${item.regNo}')">
                            <i class="bi bi-arrow-counterclockwise text-danger"></i>
                            <span>Fees Refund</span>
                        </button>
                    </div>
                </div>
            </td>
        </tr>
    `).join('');

    updatePagination();
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

// Modal Functions
function openFeeReceipt(regNo) {
    currentStudentId = regNo;
    const student = feesData.find(s => s.regNo === regNo);

    document.getElementById('receiptStudentName').value = student.studentName;
    document.getElementById('receiptTotalFees').value = student.totalFees;
    document.getElementById('receiptPendingFees').value = student.feesDue;
    document.getElementById('receivedFees').value = student.totalPaid;

    new bootstrap.Modal(document.getElementById('feeReceiptModal')).show();
}

function viewReceipts(regNo) {
    currentStudentId = regNo;
    const student = feesData.find(s => s.regNo === regNo);

    document.getElementById('viewReceiptStudentName').textContent = student.studentName;

    // Sample receipts data
    const receiptsBody = document.getElementById('receiptsTableBody');
    receiptsBody.innerHTML = `
        <tr>
            <td>REC001</td>
            <td>INV001</td>
            <td>₹10,000</td>
            <td>${formatDate(new Date())}</td>
            <td>Cash</td>
            <td>First installment</td>
            <td>Regular</td>
            <td>
                <button class="btn btn-sm btn-primary" onclick="printReceipt('REC001')">
                    <i class="bi bi-printer"></i>
                </button>
                <button class="btn btn-sm btn-danger" onclick="deleteReceipt('REC001')">
                    <i class="bi bi-trash"></i>
                </button>
            </td>
        </tr>
    `;

    new bootstrap.Modal(document.getElementById('viewReceiptsModal')).show();
}

function changeStatus(regNo) {
    currentStudentId = regNo;
    new bootstrap.Modal(document.getElementById('changeStatusModal')).show();
}

function manageInstallments(regNo) {
    currentStudentId = regNo;
    const student = feesData.find(s => s.regNo === regNo);

    document.getElementById('installmentTotalAmt').value = student.totalFees;
    document.getElementById('totalInstallmentAmt').value = student.totalFees;

    new bootstrap.Modal(document.getElementById('installmentsModal')).show();
}

function feesRefund(regNo) {
    currentStudentId = regNo;
    const student = feesData.find(s => s.regNo === regNo);

    document.getElementById('refundStudentName').value = student.studentName;
    document.getElementById('refundTotalFees').value = student.totalFees;
    document.getElementById('refundPaidFees').value = student.totalPaid;
    document.getElementById('refundPendingFees').value = student.feesDue;

    new bootstrap.Modal(document.getElementById('feesRefundModal')).show();
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
function saveReceipt() {
    Swal.fire({
        icon: 'success',
        title: 'Success!',
        text: 'Fee receipt saved successfully',
        confirmButtonColor: '#667eea'
    });
    bootstrap.Modal.getInstance(document.getElementById('feeReceiptModal')).hide();
}

function saveAndPrintReceipt() {
    Swal.fire({
        icon: 'success',
        title: 'Success!',
        text: 'Fee receipt saved and printed successfully',
        confirmButtonColor: '#667eea'
    });
    bootstrap.Modal.getInstance(document.getElementById('feeReceiptModal')).hide();
}

function saveStatus() {
    Swal.fire({
        icon: 'success',
        title: 'Success!',
        text: 'Fee status updated successfully',
        confirmButtonColor: '#667eea'
    });
    bootstrap.Modal.getInstance(document.getElementById('changeStatusModal')).hide();
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

    // Update total installment amount
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
    });
    bootstrap.Modal.getInstance(document.getElementById('installmentsModal')).hide();
}

function saveRefund() {
    Swal.fire({
        icon: 'success',
        title: 'Success!',
        text: 'Refund saved successfully',
        confirmButtonColor: '#667eea'
    });
    bootstrap.Modal.getInstance(document.getElementById('feesRefundModal')).hide();
}

function saveAndPrintRefund() {
    Swal.fire({
        icon: 'success',
        title: 'Success!',
        text: 'Refund saved and printed successfully',
        confirmButtonColor: '#667eea'
    });
    bootstrap.Modal.getInstance(document.getElementById('feesRefundModal')).hide();
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
    // Implement print functionality
}

function deleteReceipt(receiptNo) {
    Swal.fire({
        title: 'Are you sure?',
        text: "You won't be able to revert this!",
        icon: 'warning',
        showCancelButton: true,
        confirmButtonColor: '#667eea',
        cancelButtonColor: '#ef4444',
        confirmButtonText: 'Yes, delete it!'
    }).then((result) => {
        if (result.isConfirmed) {
            Swal.fire({
                icon: 'success',
                title: 'Deleted!',
                text: 'Receipt has been deleted.',
                confirmButtonColor: '#667eea'
            });
        }
    });
}