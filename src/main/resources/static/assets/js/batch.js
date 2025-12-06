// Batch Management JavaScript

// Global variables
let batches = [];
let filteredBatches = [];
let coursesCache = [];
let currentPage = 0;
let pageSize = 25;
let totalPages = 0;
let totalElements = 0;
let currentStep = 1;
let editingBatchId = null;
// CSRF Token Management
let csrfToken = null;
let csrfHeader = null;

// Initialize on DOM ready
document.addEventListener('DOMContentLoaded', function() {

     csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
        csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');

    if (!csrfToken || !csrfHeader) {
        console.warn('CSRF token not found in page meta tags');
    }

    initializeEventListeners();
    loadCourses();
    loadBatches();
});

// Initialize all event listeners
function initializeEventListeners() {
    // Button clicks
    document.getElementById('btnAddBatch').addEventListener('click', openAddBatchModal);
    document.getElementById('btnExportCSV').addEventListener('click', exportToCSV);
    document.getElementById('nextBtn').addEventListener('click', nextStep);
    document.getElementById('prevBtn').addEventListener('click', prevStep);
    document.getElementById('saveBatchBtn').addEventListener('click', saveBatch);
    document.getElementById('attachCourseBtn').addEventListener('click', attachCourse);
    document.getElementById('confirmDeleteBtn').addEventListener('click', confirmDelete);

    // Search and pagination
    document.getElementById('searchInput').addEventListener('input', debounce(handleSearch, 500));
    document.getElementById('pageSizeSelect').addEventListener('change', handlePageSizeChange);

    // Attach course checkbox
    document.getElementById('attachCourse').addEventListener('change', function() {
        if (this.checked) {
            openAttachCourseModal();
        }
    });
}

// Load courses dynamically
async function loadCourses() {
    try {
        const response = await fetch('/api/courses?page=0&size=1000');
        if (!response.ok) throw new Error('Failed to load courses');
        const data = await response.json();
        coursesCache = data.courses || [];
        populateCourseSelect();
    } catch (error) {
        console.error('Error loading courses:', error);
        coursesCache = [];
        Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'Failed to load courses',
            confirmButtonColor: '#667eea'
        });
    }
}

// Populate course select dropdown
function populateCourseSelect() {
    const courseSelect = document.getElementById('courseSelect');
    if (!courseSelect) return;

    courseSelect.innerHTML = '';

    if (coursesCache.length === 0) {
        courseSelect.innerHTML = '<option value="">No courses available</option>';
        courseSelect.disabled = true;
        return;
    }

    courseSelect.disabled = false;
    courseSelect.innerHTML = '<option value="">-- Select Course --</option>' +
        coursesCache.map(course => `<option value="${course.id}">${course.courseName}</option>`).join('');
}

// Load and display batches
async function loadBatches() {
    try {
        showLoading();
        const response = await fetch(`/api/batches?page=${currentPage}&size=${pageSize}`);

        if (!response.ok) {
            throw new Error('Failed to fetch batches');
        }

        const data = await response.json();

        if (data.success) {
            batches = data.batches || [];
            filteredBatches = [...batches];
            totalElements = data.totalElements || 0;
            totalPages = data.totalPages || 0;

            displayBatches();
            updatePagination();
        } else {
            throw new Error(data.message || 'Failed to load batches');
        }
    } catch (error) {
        console.error('Error loading batches:', error);
        showError('Failed to load batches. Please try again.');
        displayEmptyState();
    } finally {
        hideLoading();
    }
}

// Display batches in table
function displayBatches() {
    const tbody = document.getElementById('batchTableBody');

    if (filteredBatches.length === 0) {
        displayEmptyState();
        return;
    }

    const start = currentPage * pageSize;

    tbody.innerHTML = filteredBatches.map((batch, index) => `
        <tr>
            <td>${start + index + 1}</td>
            <td><strong>${escapeHtml(batch.batchNo)}</strong></td>
            <td>${escapeHtml(batch.batchName)}</td>
            <td>${formatDate(batch.startDate)}</td>
            <td>${formatDate(batch.endDate)}</td>
            <td>${escapeHtml(batch.timing)}</td>
            <td>
                <span class="badge-status ${batch.status === 'Active' ? 'badge-active' : 'badge-inactive'}">
                    ${escapeHtml(batch.status)}
                </span>
            </td>
            <td>
                <button class="action-btn" onclick="viewBatch(${batch.id})" title="View Details">
                    <i class="bi bi-eye"></i>
                </button>
                <button class="action-btn" onclick="editBatch(${batch.id})" title="Edit">
                    <i class="bi bi-pencil-square"></i>
                </button>
                <button class="action-btn" onclick="deleteBatch(${batch.id})" title="Delete">
                    <i class="bi bi-trash"></i>
                </button>
            </td>
        </tr>
    `).join('');
}

// Display empty state
function displayEmptyState() {
    const tbody = document.getElementById('batchTableBody');
    tbody.innerHTML = `
        <tr>
            <td colspan="8" class="text-center py-5">
                <div class="empty-state">
                    <i class="bi bi-inbox"></i>
                    <h5 class="mt-3">No Batches Found</h5>
                    <p class="text-muted">Create your first batch to get started</p>
                </div>
            </td>
        </tr>
    `;
}

// Update pagination controls
function updatePagination() {
    const start = totalElements === 0 ? 0 : currentPage * pageSize + 1;
    const end = Math.min((currentPage + 1) * pageSize, totalElements);

    document.getElementById('entriesStart').textContent = start;
    document.getElementById('entriesEnd').textContent = end;
    document.getElementById('totalEntries').textContent = totalElements;

    const paginationControls = document.getElementById('paginationControls');
    let paginationHTML = '';

    // Previous button
    paginationHTML += `
        <li class="page-item ${currentPage === 0 ? 'disabled' : ''}">
            <a class="page-link" href="#" onclick="changePage(${currentPage - 1}); return false;">
                <i class="bi bi-chevron-left"></i>
            </a>
        </li>
    `;

    // Page numbers
    for (let i = 0; i < totalPages; i++) {
        if (i === 0 || i === totalPages - 1 || (i >= currentPage - 1 && i <= currentPage + 1)) {
            paginationHTML += `
                <li class="page-item ${i === currentPage ? 'active' : ''}">
                    <a class="page-link" href="#" onclick="changePage(${i}); return false;">${i + 1}</a>
                </li>
            `;
        } else if (i === currentPage - 2 || i === currentPage + 2) {
            paginationHTML += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
        }
    }

    // Next button
    paginationHTML += `
        <li class="page-item ${currentPage === totalPages - 1 || totalPages === 0 ? 'disabled' : ''}">
            <a class="page-link" href="#" onclick="changePage(${currentPage + 1}); return false;">
                <i class="bi bi-chevron-right"></i>
            </a>
        </li>
    `;

    paginationControls.innerHTML = paginationHTML;
}

// Change page
function changePage(page) {
    if (page < 0 || page >= totalPages) return;
    currentPage = page;
    loadBatches();
}

// Handle search
async function handleSearch() {
    const searchTerm = document.getElementById('searchInput').value.trim();

    try {
        showLoading();
        const response = await fetch('/api/batches/search', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                [csrfHeader]: csrfToken
            },
            body: JSON.stringify({
                searchTerm: searchTerm || null,
                page: 0,
                size: pageSize
            })
        });

        if (!response.ok) {
            throw new Error('Search failed');
        }

        const data = await response.json();

        if (data.success) {
            batches = data.batches || [];
            filteredBatches = [...batches];
            totalElements = data.totalElements || 0;
            totalPages = data.totalPages || 0;
            currentPage = 0;

            displayBatches();
            updatePagination();
        }
    } catch (error) {
        console.error('Error searching batches:', error);
        showError('Search failed. Please try again.');
    } finally {
        hideLoading();
    }
}

// Handle page size change
function handlePageSizeChange(e) {
    pageSize = parseInt(e.target.value);
    currentPage = 0;
    loadBatches();
}

// Export to CSV
function exportToCSV() {
    if (filteredBatches.length === 0) {
        Swal.fire({
            icon: 'warning',
            title: 'No Data to Export',
            text: 'There are no batches to export. Please add batches first.',
            confirmButtonColor: '#667eea'
        });
        return;
    }

    const headers = ['Batch No', 'Batch Name', 'Start Date', 'End Date', 'Batch Timing', 'Status'];
    const csvContent = [
        headers.join(','),
        ...filteredBatches.map(batch => [
            batch.batchNo,
            `"${batch.batchName}"`,
            formatDate(batch.startDate),
            formatDate(batch.endDate),
            `"${batch.timing}"`,
            batch.status
        ].join(','))
    ].join('\n');

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = `batches_${new Date().toISOString().split('T')[0]}.csv`;
    link.click();

    Swal.fire({
        icon: 'success',
        title: 'Export Successful',
        text: 'Batch data has been exported to CSV',
        timer: 2000,
        showConfirmButton: false
    });
}

// Open Add Batch Modal
function openAddBatchModal() {
    editingBatchId = null;
    currentStep = 1;
    document.getElementById('batchModalTitle').innerHTML = '<i class="bi bi-plus-circle me-2"></i>Create New Batch';
    document.getElementById('batchForm').reset();
    document.getElementById('batchSize').value = '50';
    document.getElementById('startTime').value = '00:00';
    document.getElementById('endTime').value = '00:00';
    document.getElementById('attachCourse').checked = false;

    // Uncheck all day checkboxes
    document.querySelectorAll('.day-checkbox').forEach(cb => cb.checked = false);

    updateWizardSteps();
    const modal = new bootstrap.Modal(document.getElementById('batchModal'));
    modal.show();
}

// Edit Batch
async function editBatch(id) {
    try {
        showLoading();
        const response = await fetch(`/api/batches/${id}`);

        if (!response.ok) {
            throw new Error('Failed to fetch batch details');
        }

        const data = await response.json();

        if (data.success && data.batch) {
            const batch = data.batch;
            editingBatchId = id;
            currentStep = 1;

            document.getElementById('batchModalTitle').innerHTML = '<i class="bi bi-pencil-square me-2"></i>Edit Batch';

            // Populate form fields
            document.getElementById('batchName').value = batch.batchName;
            document.getElementById('batchSize').value = batch.batchSize;
            document.getElementById('startDate').value = batch.startDate || '';
            document.getElementById('endDate').value = batch.endDate || '';
            document.getElementById('startTime').value = batch.startTime || '00:00';
            document.getElementById('endTime').value = batch.endTime || '00:00';

            // Set day checkboxes
            document.getElementById('daySunday').checked = batch.isSunday || false;
            document.getElementById('dayMonday').checked = batch.isMonday || false;
            document.getElementById('dayTuesday').checked = batch.isTuesday || false;
            document.getElementById('dayWednesday').checked = batch.isWednesday || false;
            document.getElementById('dayThursday').checked = batch.isThursday || false;
            document.getElementById('dayFriday').checked = batch.isFriday || false;
            document.getElementById('daySaturday').checked = batch.isSaturday || false;

            // Set course if attached
            if (batch.courseId) {
                document.getElementById('attachCourse').checked = true;
                document.getElementById('courseSelect').value = batch.courseId;
            }

            updateWizardSteps();
            const modal = new bootstrap.Modal(document.getElementById('batchModal'));
            modal.show();
        }
    } catch (error) {
        console.error('Error loading batch:', error);
        showError('Failed to load batch details');
    } finally {
        hideLoading();
    }
}

// View Batch
async function viewBatch(id) {
    try {
        showLoading();
        const response = await fetch(`/api/batches/${id}`);

        if (!response.ok) {
            throw new Error('Failed to fetch batch details');
        }

        const data = await response.json();

        if (data.success && data.batch) {
            const batch = data.batch;
            const days = [];
            if (batch.isSunday) days.push('Sunday');
            if (batch.isMonday) days.push('Monday');
            if (batch.isTuesday) days.push('Tuesday');
            if (batch.isWednesday) days.push('Wednesday');
            if (batch.isThursday) days.push('Thursday');
            if (batch.isFriday) days.push('Friday');
            if (batch.isSaturday) days.push('Saturday');

            Swal.fire({
                title: `Batch Details - ${batch.batchNo}`,
                html: `
                    <div class="text-start">
                        <p><strong>Batch Name:</strong> ${escapeHtml(batch.batchName)}</p>
                        <p><strong>Batch Size:</strong> ${batch.batchSize}</p>
                        <p><strong>Start Date:</strong> ${formatDate(batch.startDate)}</p>
                        <p><strong>End Date:</strong> ${formatDate(batch.endDate)}</p>
                        <p><strong>Timing:</strong> ${escapeHtml(batch.timing)}</p>
                        <p><strong>Days:</strong> ${days.length > 0 ? days.join(', ') : 'Not specified'}</p>
                        <p><strong>Course:</strong> ${batch.courseName || 'Not attached'}</p>
                        <p><strong>Status:</strong> <span class="badge-status ${batch.status === 'Active' ? 'badge-active' : 'badge-inactive'}">${escapeHtml(batch.status)}</span></p>
                    </div>
                `,
                confirmButtonColor: '#667eea',
                width: '600px'
            });
        }
    } catch (error) {
        console.error('Error viewing batch:', error);
        showError('Failed to load batch details');
    } finally {
        hideLoading();
    }
}

// Delete Batch
function deleteBatch(id) {
    editingBatchId = id;
    const modal = new bootstrap.Modal(document.getElementById('deleteModal'));
    modal.show();
}

// Confirm Delete - Hard Delete
async function confirmDelete() {
    try {
        showLoading();
        const response = await fetch(`/api/batches/${editingBatchId}`, {
            method: 'DELETE',
            headers: {
                [csrfHeader]: csrfToken
            }
        });

        const data = await response.json();

        const modal = bootstrap.Modal.getInstance(document.getElementById('deleteModal'));
        modal.hide();

        if (data.success) {
            await Swal.fire({
                icon: 'success',
                title: 'Deleted!',
                text: 'Batch has been deleted successfully',
                timer: 2000,
                showConfirmButton: false
            });

            loadBatches();
        } else {
            throw new Error(data.message || 'Failed to delete batch');
        }
    } catch (error) {
        console.error('Error deleting batch:', error);
        showError(error.message || 'Failed to delete batch');
    } finally {
        hideLoading();
    }
}

// Wizard Navigation
function nextStep() {
    if (!validateCurrentStep()) return;

    if (currentStep < 3) {
        currentStep++;
        updateWizardSteps();
    }
}

function prevStep() {
    if (currentStep > 1) {
        currentStep--;
        updateWizardSteps();
    }
}

function updateWizardSteps() {
    // Update step indicators
    document.querySelectorAll('.wizard-step').forEach((step, index) => {
        step.classList.remove('active', 'completed');
        if (index + 1 < currentStep) {
            step.classList.add('completed');
        } else if (index + 1 === currentStep) {
            step.classList.add('active');
        }
    });

    // Update content visibility
    document.querySelectorAll('.wizard-content').forEach((content, index) => {
        content.classList.remove('active');
        if (index + 1 === currentStep) {
            content.classList.add('active');
        }
    });

    // Update button visibility
    document.getElementById('prevBtn').style.display = currentStep > 1 ? 'inline-block' : 'none';
    document.getElementById('nextBtn').style.display = currentStep < 3 ? 'inline-block' : 'none';
    document.getElementById('saveBatchBtn').style.display = currentStep === 3 ? 'inline-block' : 'none';
}

function validateCurrentStep() {
    const step = currentStep;

    if (step === 1) {
        const batchName = document.getElementById('batchName').value.trim();
        const batchSize = document.getElementById('batchSize').value;

        if (!batchName) {
            showWarning('Please enter batch name');
            return false;
        }

        if (!batchSize || batchSize < 1 || batchSize > 200) {
            showWarning('Please enter a valid batch size (1-200)');
            return false;
        }
    }

    if (step === 2) {
        const startTime = document.getElementById('startTime').value;
        const endTime = document.getElementById('endTime').value;
        const startDate = document.getElementById('startDate').value;
        const endDate = document.getElementById('endDate').value;

        if (!startTime || !endTime) {
            showWarning('Please enter start time and end time');
            return false;
        }

        if (startTime >= endTime) {
            showWarning('End time must be after start time');
            return false;
        }

        if (startDate && endDate && new Date(endDate) < new Date(startDate)) {
            showWarning('End date must be after start date');
            return false;
        }
    }

    return true;
}

// Save Batch
async function saveBatch() {
    if (!validateCurrentStep()) return;

    const batchData = {
        batchName: document.getElementById('batchName').value.trim(),
        batchSize: parseInt(document.getElementById('batchSize').value),
        startDate: document.getElementById('startDate').value || null,
        endDate: document.getElementById('endDate').value || null,
        startTime: document.getElementById('startTime').value,
        endTime: document.getElementById('endTime').value,
        isSunday: document.getElementById('daySunday').checked,
        isMonday: document.getElementById('dayMonday').checked,
        isTuesday: document.getElementById('dayTuesday').checked,
        isWednesday: document.getElementById('dayWednesday').checked,
        isThursday: document.getElementById('dayThursday').checked,
        isFriday: document.getElementById('dayFriday').checked,
        isSaturday: document.getElementById('daySaturday').checked,
        courseId: document.getElementById('attachCourse').checked ?
                  (document.getElementById('courseSelect').value || null) : null,
        status: 'Active'
    };

    try {
        showLoading();
        const url = editingBatchId ? `/api/batches/${editingBatchId}` : '/api/batches';
        const method = editingBatchId ? 'PUT' : 'POST';

        const response = await fetch(url, {
            method: method,
            headers: {
                'Content-Type': 'application/json',
                [csrfHeader]: csrfToken
            },
            body: JSON.stringify(batchData)
        });

        const data = await response.json();

        if (data.success) {
            const modal = bootstrap.Modal.getInstance(document.getElementById('batchModal'));
            modal.hide();

            await Swal.fire({
                icon: 'success',
                title: editingBatchId ? 'Updated!' : 'Created!',
                text: data.message || `Batch has been ${editingBatchId ? 'updated' : 'created'} successfully`,
                timer: 2000,
                showConfirmButton: false
            });

            loadBatches();
        } else {
            throw new Error(data.message || 'Failed to save batch');
        }
    } catch (error) {
        console.error('Error saving batch:', error);
        showError(error.message || 'Failed to save batch');
    } finally {
        hideLoading();
    }
}

// Open Attach Course Modal
function openAttachCourseModal() {
    const modal = new bootstrap.Modal(document.getElementById('attachCourseModal'));
    modal.show();
}

// Attach Course
async function attachCourse() {
    const courseId = document.getElementById('courseSelect').value;

    if (!courseId) {
        showWarning('Please select a course to attach');
        return;
    }

    const modal = bootstrap.Modal.getInstance(document.getElementById('attachCourseModal'));
    modal.hide();

    // Just close the modal, course will be attached when batch is saved
    await Swal.fire({
        icon: 'success',
        title: 'Course Selected!',
        text: 'Course will be attached when you save the batch',
        timer: 2000,
        showConfirmButton: false
    });
}

// Utility Functions
function formatDate(dateString) {
    if (!dateString || dateString === '-') return '-';
    const date = new Date(dateString);
    return date.toLocaleDateString('en-GB', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

function showLoading() {
    const tbody = document.getElementById('batchTableBody');
    tbody.innerHTML = `
        <tr>
            <td colspan="8" class="text-center py-4">
                <div class="spinner-border text-primary" role="status">
                    <span class="visually-hidden">Loading...</span>
                </div>
                <p class="mt-2 mb-0">Loading...</p>
            </td>
        </tr>
    `;
}

function hideLoading() {
    // Loading will be hidden when data is displayed
}

function showError(message) {
    Swal.fire({
        icon: 'error',
        title: 'Error',
        text: message,
        confirmButtonColor: '#667eea'
    });
}

function showWarning(message) {
    Swal.fire({
        icon: 'warning',
        title: 'Validation Error',
        text: message,
        confirmButtonColor: '#667eea'
    });
}