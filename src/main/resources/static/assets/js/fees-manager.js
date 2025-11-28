// fees-manager.js

let feesData = []; // Initialize as empty array
let currentPage = 1;
let entriesPerPage = 25;
let filteredData = [];
let currentStudentId = null;
let importedFeesData = [];

// Initialize
document.addEventListener('DOMContentLoaded', function() {
    initializeEventListeners();
    loadFeesFromBackend();
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
            headers: {
                 'Accept': 'application/json',
                'Content-Type': 'application/json',
            },
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
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            }
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

    tbody.innerHTML = paginatedData.map(item => `
        <tr>
            <td><strong>${item.regNo || 'N/A'}</strong></td>
            <td>${item.studentName || 'N/A'}</td>
            <td>${item.mobile || 'N/A'}</td>
            <td>₹${(item.totalFees || 0).toLocaleString()}</td>
            <td>₹${(item.feesDue || 0).toLocaleString()}</td>
            <td>₹${(item.totalPaid || 0).toLocaleString()}</td>
            <td>${item.dueDate ? formatDate(item.dueDate) : '-'}</td>
            <td><span class="badge ${item.status === 'Clear' ? 'bg-success' : 'bg-warning'}">${item.status || 'Pending'}</span></td>
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

// Generate Receipt HTML
function generateReceiptHTML(data) {
    return `
        <div class="receipt-container" id="receiptContent">
            <div class="receipt-header">
                <div class="text-center mb-4">
                    <h3 class="mb-1" style="color: #667eea; font-weight: 700;">TechnoKraft Training Solutions</h3>
                    <p class="mb-0 text-muted">Excellence in Technical Education</p>
                    <p class="mb-0 small text-muted">123 Main Street, Nashik, Maharashtra - 422001</p>
                    <p class="mb-0 small text-muted">Phone: +91 98765 43210 | Email: info@technokraft.com</p>
                </div>

                <div style="border-top: 3px solid #667eea; border-bottom: 3px solid #667eea; padding: 10px 0; margin: 20px 0;">
                    <h4 class="text-center mb-0" style="color: #1e293b; font-weight: 600;">FEE RECEIPT</h4>
                </div>
            </div>

            <div class="receipt-body">
                <div class="row mb-3">
                    <div class="col-6">
                        <p class="mb-1"><strong>Receipt No:</strong> ${data.receiptNo}</p>
                        <p class="mb-1"><strong>Invoice No:</strong> ${data.invoiceNo}</p>
                    </div>
                    <div class="col-6 text-end">
                        <p class="mb-1"><strong>Date:</strong> ${data.date}</p>
                    </div>
                </div>

                <div class="student-details" style="background: #f8fafc; padding: 15px; border-radius: 8px; margin-bottom: 20px;">
                    <h6 style="color: #667eea; margin-bottom: 10px; font-weight: 600;">Student Details</h6>
                    <div class="row">
                        <div class="col-6">
                            <p class="mb-1"><strong>Name:</strong> ${data.studentName}</p>
                            <p class="mb-1"><strong>Reg. No:</strong> ${data.regNo}</p>
                        </div>
                        <div class="col-6">
                            <p class="mb-1"><strong>Mobile:</strong> ${data.mobile}</p>
                            <p class="mb-1"><strong>Course:</strong> ${data.course}</p>
                        </div>
                    </div>
                </div>

                <div class="payment-details" style="border: 2px solid #e2e8f0; border-radius: 8px; padding: 15px; margin-bottom: 20px;">
                    <h6 style="color: #667eea; margin-bottom: 15px; font-weight: 600;">Payment Details</h6>
                    <table class="table table-sm mb-0">
                        <tbody>
                            <tr>
                                <td><strong>Amount Paid:</strong></td>
                                <td class="text-end"><strong style="font-size: 1.1rem; color: #10b981;">₹${data.amount.toLocaleString()}</strong></td>
                            </tr>
                            <tr>
                                <td><strong>Amount in Words:</strong></td>
                                <td class="text-end"><em>${data.amountInWords}</em></td>
                            </tr>
                            <tr>
                                <td><strong>Payment Mode:</strong></td>
                                <td class="text-end">${data.paymentMode}</td>
                            </tr>
                            ${data.transactionId !== '-' ? `
                            <tr>
                                <td><strong>Transaction ID:</strong></td>
                                <td class="text-end">${data.transactionId}</td>
                            </tr>
                            ` : ''}
                            <tr>
                                <td><strong>Received By:</strong></td>
                                <td class="text-end">${data.receivedBy}</td>
                            </tr>
                        </tbody>
                    </table>
                </div>

                ${data.note ? `
                <div class="note-section" style="background: #fef3c7; padding: 10px; border-radius: 6px; border-left: 4px solid #f59e0b; margin-bottom: 20px;">
                    <p class="mb-0 small"><strong>Note:</strong> ${data.note}</p>
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
                    <p class="mb-0 small text-muted">For any queries, please contact us at +91 98765 43210</p>
                </div>
            </div>
        </div>
    `;
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
            headers: {
                 'Accept': 'application/json',
                'Content-Type': 'application/json',
            },
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

function emailReceipt(receiptNo, studentName, mobile) {
    Swal.fire({
        title: 'Send Receipt via Email',
        html: `
            <div class="text-start">
                <div class="mb-3">
                    <label class="form-label">Receipt No.</label>
                    <input type="text" class="form-control" value="${receiptNo}" readonly>
                </div>
                <div class="mb-3">
                    <label class="form-label">Student Name</label>
                    <input type="text" class="form-control" value="${studentName}" readonly>
                </div>
                <div class="mb-3">
                    <label class="form-label">Email Address <span class="text-danger">*</span></label>
                    <input type="email" class="form-control" id="emailAddress" placeholder="student@example.com" required>
                    <small class="text-muted">Enter the email address to send receipt</small>
                </div>
                <div class="mb-3">
                    <label class="form-label">Additional Message (Optional)</label>
                    <textarea class="form-control" id="emailMessage" rows="3" placeholder="Add any additional message..."></textarea>
                </div>
                <div class="form-check">
                    <input class="form-check-input" type="checkbox" id="sendSMS" checked>
//                    <label class="form-check-label" for="sendSMS">
//                        Also send SMS notification to ${mobile}
//                    </label>
                </div>
            </div>
        `,
        showCancelButton: true,
        confirmButtonText: '<i class="bi bi-envelope me-2"></i>Send Email',
        cancelButtonText: 'Cancel',
        confirmButtonColor: '#667eea',
        width: '550px',
        preConfirm: () => {
            const email = document.getElementById('emailAddress').value;
            if (!email) {
                Swal.showValidationMessage('Please enter an email address');
                return false;
            }
            // Basic email validation
            const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
            if (!emailRegex.test(email)) {
                Swal.showValidationMessage('Please enter a valid email address');
                return false;
            }
            return {
                receiptNo: receiptNo,
                email: email,
                message: document.getElementById('emailMessage').value,
                sendSMS: document.getElementById('sendSMS').checked
            };
        }
    }).then((result) => {
        if (result.isConfirmed) {
            // Show sending progress
            Swal.fire({
                title: 'Sending...',
                html: 'Please wait while we send the receipt',
                allowOutsideClick: false,
                didOpen: () => {
                    Swal.showLoading();
                }
            });

            // Simulate API call
            setTimeout(() => {
                Swal.fire({
                    icon: 'success',
                    title: 'Email Sent!',
                    html: `
                        <p>Receipt has been sent successfully to:</p>
                        <p class="fw-bold text-primary">${result.value.email}</p>
                        ${result.value.sendSMS ? '<p class="text-muted mt-2"><i class="bi bi-check-circle text-success"></i> SMS notification also sent</p>' : ''}
                    `,
                    confirmButtonColor: '#667eea'
                });
            }, 2000);

            // Here you would make actual API call
            console.log('Sending email:', result.value);
        }
    });
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

    if (!regNo) {
        Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'Student information is missing',
            confirmButtonColor: '#667eea'
        });
        return;
    }

    // Validate required fields
    const nowReceiving = parseFloat(document.getElementById('nowReceiving').value);
    const installmentId = document.getElementById('installment').value;
    const nextDueDate = document.getElementById('nextDueDate').value;

    if (!nowReceiving || nowReceiving <= 0) {
        Swal.fire({
            icon: 'error',
            title: 'Validation Error',
            text: 'Please enter a valid amount',
            confirmButtonColor: '#667eea'
        });
        return;
    }

    // Prepare receipt data with regNo
    const receiptData = {
        regNo: regNo, // CHANGED FROM admissionId
        installmentId: installmentId ? parseInt(installmentId) : null,
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

    try {
        Swal.fire({
            title: 'Saving...',
            text: 'Please wait while we save the receipt',
            allowOutsideClick: false,
            didOpen: () => Swal.showLoading()
        });

        const response = await fetch('/api/fees-manager/receipts', {
            method: 'POST',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(receiptData)
        });

        const result = await response.json();

        Swal.close();

        if (response.ok) {
            Swal.fire({
                icon: 'success',
                title: 'Success!',
                text: 'Fee receipt saved successfully',
                confirmButtonColor: '#667eea'
            }).then(() => {
                bootstrap.Modal.getInstance(document.getElementById('feeReceiptModal')).hide();
                loadFeesFromBackend();
            });
        } else {
            throw new Error(result.message || 'Failed to save receipt');
        }

    } catch (error) {
        Swal.close();
        console.error('Error saving receipt:', error);
        Swal.fire({
            icon: 'error',
            title: 'Error',
            text: error.message || 'Failed to save receipt',
            confirmButtonColor: '#ef4444'
        });
    }
}


function saveAndPrintReceipt() {
    Swal.fire({
        icon: 'success',
        title: 'Success!',
        text: 'Fee receipt saved and printed successfully',
        confirmButtonColor: '#667eea'
    }).then(() => {
        bootstrap.Modal.getInstance(document.getElementById('feeReceiptModal')).hide();
        loadFeesFromBackend();
    });
}

async function saveStatus() {
    const admissionId = currentStudentId;

    if (!admissionId) {
        Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'Student information is missing',
            confirmButtonColor: '#667eea'
        });
        return;
    }

    const paymentStatus = document.getElementById('paymentStatus').value;

    if (!paymentStatus) {
        Swal.fire({
            icon: 'error',
            title: 'Validation Error',
            text: 'Please select a payment status',
            confirmButtonColor: '#667eea'
        });
        return;
    }

    try {
        Swal.fire({
            title: 'Updating...',
            text: 'Please wait while we update the status',
            allowOutsideClick: false,
            didOpen: () => Swal.showLoading()
        });

        const response = await fetch('/api/fees-manager/status', {
            method: 'PUT',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                admissionId: admissionId,
                paymentStatus: paymentStatus
            })
        });

        const result = await response.json();

        Swal.close();

        if (response.ok) {
            Swal.fire({
                icon: 'success',
                title: 'Success!',
                text: 'Fee status updated successfully',
                confirmButtonColor: '#667eea'
            }).then(() => {
                bootstrap.Modal.getInstance(document.getElementById('changeStatusModal')).hide();
                loadFeesFromBackend();
            });
        } else {
            throw new Error(result.message || 'Failed to update status');
        }

    } catch (error) {
        Swal.close();
        console.error('Error updating status:', error);
        Swal.fire({
            icon: 'error',
            title: 'Error',
            text: error.message || 'Failed to update status',
            confirmButtonColor: '#ef4444'
        });
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
                            headers: {
                                'Accept': 'application/json',
                                'Content-Type': 'application/json'
                            }
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
        Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'Student registration number is missing',
            confirmButtonColor: '#667eea'
        });
        return;
    }

    currentStudentRegNo = regNo; // Store regNo instead of admissionId
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

    document.getElementById('receiptStudentName').value = student.studentName;
    document.getElementById('receiptTotalFees').value = student.totalFees;
    document.getElementById('receiptPendingFees').value = student.feesDue;
    document.getElementById('receivedFees').value = student.totalPaid;

    await loadInstallmentsForReceipt(regNo);

    new bootstrap.Modal(document.getElementById('feeReceiptModal')).show();
}
async function loadInstallmentsForReceipt(regNo) {
    try {
        const response = await fetch(`/api/fees-manager/installments/${regNo}`, {
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            }
        });

        if (response.ok) {
            const installments = await response.json();
            const select = document.getElementById('installment');

            select.innerHTML = '<option value="">-- Select Installment --</option>';

            installments.forEach(inst => {
                const option = document.createElement('option');
                option.value = inst.id;
                option.textContent = `Installment ${inst.installmentNumber} - ₹${inst.amount.toLocaleString()} (${inst.status})`;
                select.appendChild(option);
            });
        }
    } catch (error) {
        console.error('Error loading installments:', error);
    }
}
async function viewReceipts(regNo) {
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

    document.getElementById('viewReceiptStudentName').textContent = student.studentName;

    // Fetch receipts using regNo
    try {
        const response = await fetch(`/api/fees-manager/receipts/${regNo}`, {
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json',
            }
        });

        if (!response.ok) {
            throw new Error('Failed to fetch receipts');
        }

        const receipts = await response.json();

        const receiptsBody = document.getElementById('receiptsTableBody');

        if (receipts.length === 0) {
            receiptsBody.innerHTML = `
                <tr>
                    <td colspan="8" class="text-center text-muted">
                        <small>No receipts found for this student</small>
                    </td>
                </tr>
            `;
        } else {
            receiptsBody.innerHTML = receipts.map(receipt => `
                <tr>
                    <td>${receipt.receiptNumber || 'N/A'}</td>
                    <td>${receipt.invoiceNumber || 'N/A'}</td>
                    <td>₹${(receipt.amountReceived || 0).toLocaleString()}</td>
                    <td>${receipt.receiptDate ? formatDate(receipt.receiptDate) : '-'}</td>
                    <td>${receipt.paymentMode || 'Cash'}</td>
                    <td>${receipt.notes || '-'}</td>
                    <td>${receipt.receiptType || 'Regular'}</td>
                    <td>
                        <div class="btn-group" role="group">
                            <button class="btn btn-sm btn-secondary" onclick="viewReceiptPreview('${receipt.receiptNumber}', '${regNo}')" title="View Receipt">
                                <i class="bi bi-eye"></i>
                            </button>
                            <button class="btn btn-sm btn-info" onclick="printReceipt('${receipt.receiptNumber}')" title="Print">
                                <i class="bi bi-printer"></i>
                            </button>
                            <button class="btn btn-sm btn-success" onclick="emailReceipt('${receipt.receiptNumber}', '${student.studentName}', '${student.mobile}')" title="Email">
                                <i class="bi bi-envelope"></i>
                            </button>
                            <button class="btn btn-sm btn-danger" onclick="deleteReceipt(${receipt.id})" title="Delete">
                                <i class="bi bi-trash"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `).join('');
        }

        new bootstrap.Modal(document.getElementById('viewReceiptsModal')).show();

    } catch (error) {
        console.error('Error fetching receipts:', error);
        Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'Failed to load receipts',
            confirmButtonColor: '#ef4444'
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

    new bootstrap.Modal(document.getElementById('feesRefundModal')).show();
}

// Update saveRefund to use regNo
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
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            },
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

function viewReceiptPreview(receiptNo, admissionId) {
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

    // Mock receipt data - replace with actual API call
    const receiptData = {
        receiptNo: receiptNo,
        invoiceNo: receiptNo.replace('REC', 'INV'),
        date: formatDate(new Date()),
        studentName: student.studentName,
        regNo: student.regNo,
        mobile: student.mobile,
        course: student.course,
        amount: 10000,
        amountInWords: 'Ten Thousand Only',
        paymentMode: 'Cash',
        transactionId: receiptNo === 'REC001' ? '-' : 'TXN' + Math.random().toString(36).substr(2, 9).toUpperCase(),
        receivedBy: 'Admin',
        note: 'First installment payment'
    };

    const receiptHTML = generateReceiptHTML(receiptData);

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
            emailReceipt(receiptNo, student.studentName, student.mobile);
        }
    });
}