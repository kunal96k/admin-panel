let currentPage = 0;
let entriesPerPage = 25;
let totalElements = 0;
let totalPages = 0;
let currentSearch = '';
let editingLeadSourceId = null;

// Initialize
document.addEventListener('DOMContentLoaded', function() {
    loadLeadSources();
    setupEventListeners();
});

// CSRF Token Management
function getCsrfToken() {
    const csrfCookie = document.cookie
        .split('; ')
        .find(row => row.startsWith('XSRF-TOKEN='));
    return csrfCookie ? decodeURIComponent(csrfCookie.split('=')[1]) : null;
}

function getCsrfHeaders() {
    const token = getCsrfToken();
    return token ? { 'X-CSRF-TOKEN': token } : {};
}

function setupEventListeners() {
    // Add Lead Source button
    document.getElementById('btnAddLeadSource').addEventListener('click', function() {
        editingLeadSourceId = null;
        document.getElementById('modalTitle').innerHTML = '<i class="bi bi-megaphone me-2"></i>Add New Lead Source';
        document.getElementById('leadSourceTitle').value = '';
        document.getElementById('leadSourceTitle').classList.remove('is-invalid');
        new bootstrap.Modal(document.getElementById('leadSourceModal')).show();
    });

    // Save Lead Source button
    document.getElementById('btnSaveLeadSource').addEventListener('click', saveLeadSource);

    // Search input with debounce
    let searchTimeout;
    document.getElementById('searchInput').addEventListener('input', function(e) {
        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(() => {
            currentSearch = e.target.value.trim();
            currentPage = 0;
            loadLeadSources();
        }, 500);
    });

    // Entries per page
    document.getElementById('entriesPerPage').addEventListener('change', function(e) {
        entriesPerPage = parseInt(e.target.value);
        currentPage = 0;
        loadLeadSources();
    });

    // Export CSV button
    document.getElementById('btnExportCSV').addEventListener('click', exportToCSV);

    // Form validation on input
    document.getElementById('leadSourceTitle').addEventListener('input', function() {
        this.classList.remove('is-invalid');
    });
}

async function loadLeadSources() {
    try {
            const response = await fetch(`/lead-source/list?search=${encodeURIComponent(currentSearch)}&page=${currentPage}&size=${entriesPerPage}`, {
                headers: {
                    'Accept': 'application/json',
                    ...getCsrfHeaders()
                }
            });
            const result = await response.json();

        if (result.success) {
            totalElements = result.totalElements;
            totalPages = result.totalPages;
            renderTable(result.data);
            updatePaginationInfo();
            renderPagination();
        } else {
            showError('Error loading lead sources: ' + result.message);
        }
    } catch (error) {
        showError('Failed to load lead sources. Please try again.');
        console.error('Error:', error);
    }
}

function renderTable(leadSources) {
    const tbody = document.getElementById('leadSourceTableBody');

    if (leadSources.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="3" class="text-center py-4">
                    <i class="bi bi-inbox" style="font-size: 3rem; color: #cbd5e1;"></i>
                    <p class="mt-2 mb-0 text-muted">No lead sources found</p>
                </td>
            </tr>
        `;
    } else {
        tbody.innerHTML = leadSources.map((source, index) => `
            <tr>
                <td>${currentPage * entriesPerPage + index + 1}</td>
                <td>${escapeHtml(source.sourceTitle)}</td>
                <td>
                    <button class="action-btn" onclick="editLeadSource(${source.id})" title="Edit">
                        <i class="bi bi-pencil-square"></i>
                    </button>
                    <button class="action-btn" onclick="deleteLeadSource(${source.id})" title="Delete">
                        <i class="bi bi-trash"></i>
                    </button>
                </td>
            </tr>
        `).join('');
    }
}

function updatePaginationInfo() {
    const start = totalElements === 0 ? 0 : currentPage * entriesPerPage + 1;
    const end = Math.min((currentPage + 1) * entriesPerPage, totalElements);

    document.getElementById('entriesStart').textContent = start;
    document.getElementById('entriesEnd').textContent = end;
    document.getElementById('totalEntries').textContent = totalElements;
}

function renderPagination() {
    const pagination = document.getElementById('paginationControls');

    let html = `
        <li class="page-item ${currentPage === 0 ? 'disabled' : ''}">
            <a class="page-link" href="#" onclick="changePage(${currentPage - 1}); return false;">Previous</a>
        </li>
    `;

    for (let i = 0; i < totalPages; i++) {
        if (i === 0 || i === totalPages - 1 || (i >= currentPage - 1 && i <= currentPage + 1)) {
            html += `
                <li class="page-item ${i === currentPage ? 'active' : ''}">
                    <a class="page-link" href="#" onclick="changePage(${i}); return false;">${i + 1}</a>
                </li>
            `;
        } else if (i === currentPage - 2 || i === currentPage + 2) {
            html += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
        }
    }

    html += `
        <li class="page-item ${currentPage === totalPages - 1 || totalPages === 0 ? 'disabled' : ''}">
            <a class="page-link" href="#" onclick="changePage(${currentPage + 1}); return false;">Next</a>
        </li>
    `;

    pagination.innerHTML = html;
}

function changePage(page) {
    if (page >= 0 && page < totalPages) {
        currentPage = page;
        loadLeadSources();
    }
}

async function editLeadSource(id) {
    try {
       const response = await fetch(`/lead-source/${id}`, {
           headers: {
               'Accept': 'application/json',
               ...getCsrfHeaders()
           }
       });
        const result = await response.json();

        if (result.success) {
            editingLeadSourceId = id;
            document.getElementById('modalTitle').innerHTML = '<i class="bi bi-pencil me-2"></i>Edit Lead Source';
            document.getElementById('leadSourceTitle').value = result.data.sourceTitle;
            document.getElementById('leadSourceTitle').classList.remove('is-invalid');
            new bootstrap.Modal(document.getElementById('leadSourceModal')).show();
        } else {
            showError(result.message);
        }
    } catch (error) {
        showError('Failed to load lead source details');
        console.error('Error:', error);
    }
}

async function saveLeadSource() {
    const sourceTitle = document.getElementById('leadSourceTitle').value.trim();
    const sourceTitleInput = document.getElementById('leadSourceTitle');
    const errorDiv = document.getElementById('leadSourceTitleError');

    // Validation
    if (!sourceTitle) {
        sourceTitleInput.classList.add('is-invalid');
        errorDiv.textContent = 'Lead source title is required';
        return;
    }

    if (sourceTitle.length > 50) {
        sourceTitleInput.classList.add('is-invalid');
        errorDiv.textContent = 'Lead source title must not exceed 50 characters';
        return;
    }

    const leadSourceDTO = {
        sourceTitle: sourceTitle
    };

    try {
        const url = editingLeadSourceId ? `/lead-source/update/${editingLeadSourceId}` : '/lead-source/create';
        const method = editingLeadSourceId ? 'PUT' : 'POST';

       const response = await fetch(url, {
           method: method,
           headers: {
               'Content-Type': 'application/json',
               ...getCsrfHeaders()
           },
           body: JSON.stringify(leadSourceDTO)
       });

        const result = await response.json();

        if (result.success) {
            Swal.fire({
                icon: 'success',
                title: 'Success',
                text: result.message,
                timer: 2000,
                showConfirmButton: false
            });

            bootstrap.Modal.getInstance(document.getElementById('leadSourceModal')).hide();
            loadLeadSources();
        } else {
            sourceTitleInput.classList.add('is-invalid');
            errorDiv.textContent = result.message;
        }
    } catch (error) {
        showError('Failed to save lead source. Please try again.');
        console.error('Error:', error);
    }
}

function deleteLeadSource(id) {
    editingLeadSourceId = id;
    new bootstrap.Modal(document.getElementById('deleteModal')).show();

    document.getElementById('btnConfirmDelete').onclick = async function() {
        try {
            const response = await fetch(`/lead-source/delete/${id}`, {
                method: 'DELETE',
                headers: {
                    'Accept': 'application/json',
                    ...getCsrfHeaders()
                }
            });

            const result = await response.json();

            if (result.success) {
                Swal.fire({
                    icon: 'success',
                    title: 'Deleted',
                    text: result.message,
                    timer: 2000,
                    showConfirmButton: false
                });

                bootstrap.Modal.getInstance(document.getElementById('deleteModal')).hide();
                loadLeadSources();
            } else {
                showError(result.message);
            }
        } catch (error) {
            showError('Failed to delete lead source. Please try again.');
            console.error('Error:', error);
        }
    };
}

async function exportToCSV() {
    if (totalElements === 0) {
        Swal.fire({
            icon: 'warning',
            title: 'No Data',
            text: 'Table is empty. Nothing to export.',
            confirmButtonText: 'OK'
        });
        return;
    }

    try {
        Swal.fire({
            title: 'Exporting...',
            text: 'Please wait while we prepare your CSV file',
            allowOutsideClick: false,
            didOpen: () => {
                Swal.showLoading();
            }
        });

       const url = `/lead-source/export?search=${encodeURIComponent(currentSearch)}`;
       const response = await fetch(url, {
           headers: {
               'Accept': 'application/json',
               ...getCsrfHeaders()
           }
       });

        if (response.ok) {
            const blob = await response.blob();
            const downloadUrl = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = downloadUrl;
            a.download = 'lead_sources_export.csv';
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(downloadUrl);
            document.body.removeChild(a);

            Swal.fire({
                icon: 'success',
                title: 'Success',
                text: 'CSV exported successfully!',
                timer: 2000,
                showConfirmButton: false
            });
        } else {
            const errorText = await response.text();
            throw new Error(errorText || 'Export failed');
        }
    } catch (error) {
        Swal.fire({
            icon: 'error',
            title: 'Export Failed',
            text: error.message || 'Failed to export CSV. Please try again.'
        });
        console.error('Error:', error);
    }
}

function showError(message) {
    Swal.fire({
        icon: 'error',
        title: 'Error',
        text: message
    });
}

function escapeHtml(text) {
    const map = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    };
    return text.replace(/[&<>"']/g, m => map[m]);
}