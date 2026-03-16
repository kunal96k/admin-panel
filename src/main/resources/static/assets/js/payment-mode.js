// payment-mode.js

let allPaymentModes = [];
let filteredPaymentModes = [];
let currentPage = 1;
let entriesPerPage = 25;
let editingPaymentModeId = null;
// CSRF Token Management
let csrfToken = null;
let csrfHeader = null;

// Initialize on page load
document.addEventListener('DOMContentLoaded', function() {
    loadPaymentModes();
    initializeEventListeners();
});

// Initialize all event listeners
function initializeEventListeners() {
    // Get CSRF token from meta tags
    csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
    csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');

    if (!csrfToken || !csrfHeader) {
        console.warn('CSRF token not found in page meta tags');
    }

    // Add Payment Mode button
    document.getElementById('btnAddPaymentMode').addEventListener('click', function() {
        openAddModal();
    });

    // Save Payment Mode button
    document.getElementById('btnSavePaymentMode').addEventListener('click', function() {
        savePaymentMode();
    });

    // Confirm Delete button
    document.getElementById('btnConfirmDelete').addEventListener('click', function() {
        confirmDelete();
    });

    // Export CSV button
    document.getElementById('btnExportCSV').addEventListener('click', function() {
        exportToCSV();
    });

    // Search input
    document.getElementById('searchInput').addEventListener('input', function() {
        handleSearch();
    });

    // Entries per page change
    document.getElementById('entriesPerPage').addEventListener('change', function() {
        entriesPerPage = parseInt(this.value);
        currentPage = 1;
        renderTable();
    });

    // Form validation on input
    document.getElementById('paymentModeTitle').addEventListener('input', function() {
        validatePaymentModeTitle();
    });

    // Modal close event
    document.getElementById('paymentModeModal').addEventListener('hidden.bs.modal', function() {
        resetForm();
    });
}

// Load all payment modes from backend
function loadPaymentModes() {
    showLoadingSpinner();

    fetch('/api/payment-modes')
        .then(response => {
            if (!response.ok) {
                throw new Error('Failed to fetch payment modes');
            }
            return response.json();
        })
        .then(data => {
            allPaymentModes = data;
            filteredPaymentModes = [...allPaymentModes];
            renderTable();
        })
        .catch(error => {
            console.error('Error loading payment modes:', error);
            showError('Failed to load payment modes. Please refresh the page.');
            hideLoadingSpinner();
        });
}

// Render table with current data
function renderTable() {
    const tbody = document.getElementById('paymentModeTableBody');

    if (filteredPaymentModes.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="3" class="text-center py-4">
                    <i class="bi bi-inbox" style="font-size: 3rem; color: #6c757d;"></i>
                    <p class="mt-2 text-muted">No payment modes found</p>
                </td>
            </tr>
        `;
        updatePaginationInfo(0, 0, 0);
        return;
    }

    const startIndex = (currentPage - 1) * entriesPerPage;
    const endIndex = Math.min(startIndex + entriesPerPage, filteredPaymentModes.length);
    const paginatedData = filteredPaymentModes.slice(startIndex, endIndex);

    tbody.innerHTML = paginatedData.map((mode, index) => `
        <tr>
            <td>${startIndex + index + 1}</td>
            <td>${escapeHtml(mode.paymentModeTitle)}</td>
            <td>
                <button class="action-btn" onclick="editPaymentMode(${mode.id})" title="Edit">
                    <i class="bi bi-pencil-square"></i>
                </button>
                <button class="action-btn" onclick="deletePaymentMode(${mode.id})" title="Delete">
                    <i class="bi bi-trash"></i>
                </button>
            </td>
        </tr>
    `).join('');

    updatePaginationInfo(startIndex + 1, endIndex, filteredPaymentModes.length);
    renderPagination();
}

// Open modal for adding new payment mode
function openAddModal() {
    editingPaymentModeId = null;
    document.getElementById('modalTitle').innerHTML = '<i class="bi bi-credit-card me-2"></i>Add New Payment Mode';
    document.getElementById('btnSavePaymentMode').innerHTML = '<i class="bi bi-check-circle me-2"></i>Save Payment Mode';
    resetForm();
    const modal = new bootstrap.Modal(document.getElementById('paymentModeModal'));
    modal.show();
}

// Edit payment mode
function editPaymentMode(id) {
    editingPaymentModeId = id;
    const paymentMode = allPaymentModes.find(mode => mode.id === id);

    if (!paymentMode) {
        showError('Payment mode not found');
        return;
    }

    document.getElementById('paymentModeId').value = paymentMode.id;
    document.getElementById('paymentModeTitle').value = paymentMode.paymentModeTitle;
    document.getElementById('modalTitle').innerHTML = '<i class="bi bi-credit-card me-2"></i>Edit Payment Mode';
    document.getElementById('btnSavePaymentMode').innerHTML = '<i class="bi bi-check-circle me-2"></i>Update Payment Mode';

    const modal = new bootstrap.Modal(document.getElementById('paymentModeModal'));
    modal.show();
}

// Save or update payment mode
function savePaymentMode() {
    if (!validateForm()) {
        return;
    }

    const paymentModeTitle = document.getElementById('paymentModeTitle').value.trim();
    const saveBtn = document.getElementById('btnSavePaymentMode');

    // Check for duplicate
    const isDuplicate = allPaymentModes.some(mode =>
        mode.paymentModeTitle.toLowerCase() === paymentModeTitle.toLowerCase() &&
        mode.id !== editingPaymentModeId
    );

    if (isDuplicate) {
        showValidationError('paymentModeTitle', 'This payment mode already exists');
        return;
    }

    saveBtn.classList.add('btn-loading');
    saveBtn.disabled = true;

    const url = editingPaymentModeId ? `/api/payment-modes/${editingPaymentModeId}` : '/api/payment-modes';
    const method = editingPaymentModeId ? 'PUT' : 'POST';

    const requestBody = {
        paymentModeTitle: paymentModeTitle
    };

    fetch(url, {
        method: method,
        headers: {
            'Content-Type': 'application/json',
            [csrfHeader]: csrfToken
        },
        body: JSON.stringify(requestBody)
    })
    .then(response => {
        if (!response.ok) {
            return response.json().then(err => {
                throw new Error(err.message || 'Failed to save payment mode');
            });
        }
        return response.json();
    })
    .then(data => {
        const modal = bootstrap.Modal.getInstance(document.getElementById('paymentModeModal'));
        modal.hide();

        showSuccess(editingPaymentModeId ? 'Payment mode updated successfully' : 'Payment mode added successfully');
        loadPaymentModes();
    })
    .catch(error => {
        console.error('Error saving payment mode:', error);
        showError(error.message || 'Failed to save payment mode');
    })
    .finally(() => {
        saveBtn.classList.remove('btn-loading');
        saveBtn.disabled = false;
    });
}

// Delete payment mode
function deletePaymentMode(id) {
    editingPaymentModeId = id;
    const modal = new bootstrap.Modal(document.getElementById('deleteModal'));
    modal.show();
}

// Confirm delete
function confirmDelete() {
    if (!editingPaymentModeId) {
        return;
    }

    const deleteBtn = document.getElementById('btnConfirmDelete');
    deleteBtn.classList.add('btn-loading');
    deleteBtn.disabled = true;

    fetch(`/api/payment-modes/${editingPaymentModeId}`, {
        method: 'DELETE',
        headers: {
            [csrfHeader]: csrfToken
        }
    })
    .then(response => {
        if (!response.ok) {
            throw new Error('Failed to delete payment mode');
        }

        const modal = bootstrap.Modal.getInstance(document.getElementById('deleteModal'));
        modal.hide();

        showSuccess('Payment mode deleted successfully');
        loadPaymentModes();
    })
    .catch(error => {
        console.error('Error deleting payment mode:', error);
        showError('Failed to delete payment mode');
    })
    .finally(() => {
        deleteBtn.classList.remove('btn-loading');
        deleteBtn.disabled = false;
        editingPaymentModeId = null;
    });
}

// Handle search
function handleSearch() {
    const searchTerm = document.getElementById('searchInput').value.toLowerCase().trim();

    if (searchTerm === '') {
        filteredPaymentModes = [...allPaymentModes];
    } else {
        filteredPaymentModes = allPaymentModes.filter(mode =>
            mode.paymentModeTitle.toLowerCase().includes(searchTerm)
        );
    }

    currentPage = 1;
    renderTable();
}

// Validate form
function validateForm() {
    return validatePaymentModeTitle();
}

// Validate payment mode title
function validatePaymentModeTitle() {
    const input = document.getElementById('paymentModeTitle');
    const value = input.value.trim();

    if (value === '') {
        showValidationError('paymentModeTitle', 'Please enter payment mode title');
        return false;
    }

    if (value.length < 2) {
        showValidationError('paymentModeTitle', 'Payment mode title must be at least 2 characters');
        return false;
    }

    if (value.length > 500) {
        showValidationError('paymentModeTitle', 'Payment mode title must not exceed 500 characters');
        return false;
    }

    clearValidationError('paymentModeTitle');
    return true;
}

// Show validation error
function showValidationError(fieldId, message) {
    const input = document.getElementById(fieldId);
    const errorDiv = document.getElementById(fieldId + 'Error');

    input.classList.add('is-invalid');
    errorDiv.textContent = message;
}

// Clear validation error
function clearValidationError(fieldId) {
    const input = document.getElementById(fieldId);
    const errorDiv = document.getElementById(fieldId + 'Error');

    input.classList.remove('is-invalid');
    errorDiv.textContent = '';
}

// Reset form
function resetForm() {
    document.getElementById('paymentModeForm').reset();
    document.getElementById('paymentModeId').value = '';
    clearValidationError('paymentModeTitle');
    editingPaymentModeId = null;
}

// Update pagination info
function updatePaginationInfo(start, end, total) {
    document.getElementById('entriesStart').textContent = start;
    document.getElementById('entriesEnd').textContent = end;
    document.getElementById('totalEntries').textContent = total;
}

// Render pagination controls
function renderPagination() {
    const totalPages = Math.ceil(filteredPaymentModes.length / entriesPerPage);
    const paginationControls = document.getElementById('paginationControls');

    if (totalPages <= 1) {
        paginationControls.innerHTML = `
            <li class="page-item disabled">
                <a class="page-link" href="#" tabindex="-1">Previous</a>
            </li>
            <li class="page-item active">
                <a class="page-link" href="#">1</a>
            </li>
            <li class="page-item disabled">
                <a class="page-link" href="#">Next</a>
            </li>
        `;
        return;
    }

    let html = `
        <li class="page-item ${currentPage === 1 ? 'disabled' : ''}">
            <a class="page-link" href="#" onclick="changePage(${currentPage - 1}); return false;">Previous</a>
        </li>
    `;

    const maxVisiblePages = 5;
    let startPage = Math.max(1, currentPage - Math.floor(maxVisiblePages / 2));
    let endPage = Math.min(totalPages, startPage + maxVisiblePages - 1);

    if (endPage - startPage < maxVisiblePages - 1) {
        startPage = Math.max(1, endPage - maxVisiblePages + 1);
    }

    if (startPage > 1) {
        html += `<li class="page-item"><a class="page-link" href="#" onclick="changePage(1); return false;">1</a></li>`;
        if (startPage > 2) {
            html += `<li class="page-item disabled"><a class="page-link" href="#">...</a></li>`;
        }
    }

    for (let i = startPage; i <= endPage; i++) {
        html += `
            <li class="page-item ${i === currentPage ? 'active' : ''}">
                <a class="page-link" href="#" onclick="changePage(${i}); return false;">${i}</a>
            </li>
        `;
    }

    if (endPage < totalPages) {
        if (endPage < totalPages - 1) {
            html += `<li class="page-item disabled"><a class="page-link" href="#">...</a></li>`;
        }
        html += `<li class="page-item"><a class="page-link" href="#" onclick="changePage(${totalPages}); return false;">${totalPages}</a></li>`;
    }

    html += `
        <li class="page-item ${currentPage === totalPages ? 'disabled' : ''}">
            <a class="page-link" href="#" onclick="changePage(${currentPage + 1}); return false;">Next</a>
        </li>
    `;

    paginationControls.innerHTML = html;
}

// Change page
function changePage(page) {
    const totalPages = Math.ceil(filteredPaymentModes.length / entriesPerPage);
    if (page < 1 || page > totalPages) {
        return;
    }
    currentPage = page;
    renderTable();
}

// Export to CSV
function exportToCSV() {
    if (filteredPaymentModes.length === 0) {
        showError('No data to export');
        return;
    }

    const headers = ['SR. NO.', 'PAYMENT MODE'];
    const csvContent = [
        headers.join(','),
        ...filteredPaymentModes.map((mode, index) =>
            [index + 1, `"${mode.paymentModeTitle.replace(/"/g, '""')}"`].join(',')
        )
    ].join('\n');

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    const url = URL.createObjectURL(blob);

    link.setAttribute('href', url);
    link.setAttribute('download', `payment_modes_${new Date().toISOString().split('T')[0]}.csv`);
    link.style.visibility = 'hidden';

    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    showSuccess('CSV exported successfully');
}

// Show loading spinner
function showLoadingSpinner() {
    const tbody = document.getElementById('paymentModeTableBody');
    tbody.innerHTML = `
        <tr>
            <td colspan="3" class="text-center py-4">
                <div class="spinner-border text-primary" role="status">
                    <span class="visually-hidden">Loading...</span>
                </div>
            </td>
        </tr>
    `;
}

// Hide loading spinner
function hideLoadingSpinner() {
    renderTable();
}

// Show success message
function showSuccess(message) {
    Swal.fire({
        icon: 'success',
        title: 'Success!',
        text: message,
        timer: 2000,
        showConfirmButton: false
    });
}

// Show error message
function showError(message) {
    Swal.fire({
        icon: 'error',
        title: 'Error!',
        text: message,
        confirmButtonColor: '#667eea'
    });
}

// Escape HTML to prevent XSS
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}