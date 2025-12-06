// JavaScript code for managing course packages

let selectedCourses = [];
let currentPage = 1;
let entriesPerPage = 10;
let allPackages = [];
let filteredPackages = [];
let allCourses = [];
let editingPackageId = null;

// CSRF Token Management
let csrfToken = null;
let csrfHeader = null;

$(document).ready(function() {
    initializeSelect2();
    loadActiveCourses();
    loadPackages();
    setupEventListeners();
});

function initializeSelect2() {
    $('#courseSelect').select2({
        placeholder: '-- Select Course --',
        allowClear: true,
        width: '100%'
    });
}

function setupEventListeners() {
    // Get CSRF token from meta tags
    csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
    csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');

    if (!csrfToken || !csrfHeader) {
        console.warn('CSRF token not found in page meta tags');
    }

    $('#courseSelect').on('select2:select', function(e) {
        const courseId = parseInt(e.params.data.id);
        const course = allCourses.find(c => c.id === courseId);

        if (course && !selectedCourses.find(c => c.id === courseId)) {
            selectedCourses.push(course);
            renderSelectedCourses();
            updateTotalAmount();
        }

        $(this).val(null).trigger('change');
    });

    $('#packageForm').on('submit', function(e) {
        e.preventDefault();
        savePackage();
    });

    $('#btnCancel').on('click', function() {
        resetForm();
    });

    $('#searchInput').on('input', debounce(function() {
        const searchTerm = $(this).val().trim();
        if (searchTerm) {
            searchPackages(searchTerm);
        } else {
            loadPackages();
        }
    }, 500));

    $('#entriesPerPage').on('change', function() {
        entriesPerPage = parseInt($(this).val());
        currentPage = 1;
        renderPackagesTable();
    });

    $('#btnExportCSV').on('click', function() {
        exportToCSV();
    });
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

function loadActiveCourses() {
    $.ajax({
        url: '/api/packages/courses/active',
        method: 'GET',
        success: function(courses) {
            allCourses = courses;
            populateCourseDropdown(courses);
        },
        error: function(xhr) {
            console.error('Error loading courses:', xhr);
            showError('Failed to load courses');
        }
    });
}

function populateCourseDropdown(courses) {
    const select = $('#courseSelect');
    select.empty().append('<option value="">-- Select Course --</option>');

    courses.forEach(course => {
        const option = new Option(
            `${course.courseName} - ₹${course.courseFees.toLocaleString('en-IN')}`,
            course.id
        );
        select.append(option);
    });
}

function loadPackages() {
    $.ajax({
        url: '/api/packages',
        method: 'GET',
        data: {
            page: currentPage - 1,
            size: entriesPerPage
        },
        success: function(response) {
            allPackages = response.packages;
            filteredPackages = response.packages;
            renderPackagesTable();
            updatePaginationInfo(response);
        },
        error: function(xhr) {
            console.error('Error loading packages:', xhr);
            showError('Failed to load packages');
        }
    });
}

function searchPackages(searchTerm) {
    $.ajax({
        url: '/api/packages/search',
        method: 'GET',
        data: {
            searchTerm: searchTerm,
            page: 0,
            size: entriesPerPage
        },
        success: function(response) {
            filteredPackages = response.packages;
            currentPage = 1;
            renderPackagesTable();
            updatePaginationInfo(response);
        },
        error: function(xhr) {
            console.error('Error searching packages:', xhr);
            showError('Failed to search packages');
        }
    });
}

function renderSelectedCourses() {
    const tbody = $('#selectedCoursesBody');

    if (selectedCourses.length === 0) {
        tbody.html(`
            <tr>
                <td colspan="3" class="text-center text-muted py-4">
                    <i class="bi bi-inbox" style="font-size: 2rem; color: #cbd5e1;"></i>
                    <p class="mt-2 mb-0">No courses selected</p>
                </td>
            </tr>
        `);
    } else {
        tbody.html(selectedCourses.map((course, index) => `
            <tr data-course-id="${course.id}">
                <td>
                    <input type="text"
                           class="form-control form-control-sm course-name-input"
                           value="${course.courseName}"
                           data-index="${index}"
                           onchange="updateCourseName(${index}, this.value)">
                </td>
                <td>
                    <input type="number"
                           class="form-control form-control-sm course-fees-input"
                           value="${course.courseFees}"
                           min="0"
                           step="0.01"
                           data-index="${index}"
                           onchange="updateCourseFees(${index}, this.value)">
                </td>
                <td>
                    <button class="btn btn-sm btn-outline-danger" onclick="removeCourse(${course.id})" title="Remove">
                        <i class="bi bi-trash"></i>
                    </button>
                </td>
            </tr>
        `).join(''));
    }
}

function removeCourse(courseId) {
    selectedCourses = selectedCourses.filter(c => c.id !== courseId);
    renderSelectedCourses();
    updateTotalAmount();
}

function updateCourseName(index, newName) {
    if (newName && newName.trim()) {
        selectedCourses[index].courseName = newName.trim();
    }
}

function updateCourseFees(index, newFees) {
    const fees = parseFloat(newFees);
    if (!isNaN(fees) && fees >= 0) {
        selectedCourses[index].courseFees = fees;
        updateTotalAmount();
    }
}

function updateTotalAmount() {
    const total = selectedCourses.reduce((sum, course) => sum + parseFloat(course.courseFees || 0), 0);
    $('#totalAmount').text(total.toLocaleString('en-IN'));
    $('#totalAmountInput').val(total.toFixed(2));
}

window.updateTotalAmountManually = function() {
    const manualTotal = parseFloat($('#totalAmountInput').val());
    if (!isNaN(manualTotal) && manualTotal >= 0) {
        $('#totalAmount').text(manualTotal.toLocaleString('en-IN'));
    } else {
        updateTotalAmount();
    }
}

function savePackage() {
    const packageName = $('#packageTitle').val().trim();

    if (!packageName) {
        showWarning('Please enter package title');
        return;
    }

    if (selectedCourses.length === 0) {
        showWarning('Please select at least one course');
        return;
    }

    // Get the current total amount from input
    const totalAmount = parseFloat($('#totalAmountInput').val());

    if (isNaN(totalAmount) || totalAmount < 0) {
        showWarning('Please enter a valid total amount');
        return;
    }

    const requestData = {
        packageName: packageName,
        courseIds: selectedCourses.map(c => c.id),
        totalAmount: totalAmount,
        courses: selectedCourses.map(c => ({
            id: c.id,
            courseName: c.courseName,
            courseFees: parseFloat(c.courseFees)
        }))
    };

    const url = editingPackageId
        ? `/api/packages/${editingPackageId}`
        : '/api/packages';
    const method = editingPackageId ? 'PUT' : 'POST';

    $.ajax({
        url: url,
        method: method,
        contentType: 'application/json',
        headers: {
            [csrfHeader]: csrfToken
        },
        data: JSON.stringify(requestData),
        success: function(response) {
            showSuccess(editingPackageId ? 'Package updated successfully!' : 'Package created successfully!');
            resetForm();
            loadPackages();
        },
        error: function(xhr) {
            const errorMsg = xhr.responseJSON?.error || 'Failed to save package';
            showError(errorMsg);
        }
    });
}

function resetForm() {
    $('#packageTitle').val('');
    $('#courseSelect').val(null).trigger('change');
    selectedCourses = [];
    editingPackageId = null;
    renderSelectedCourses();
    $('#totalAmount').text('0');
    $('#totalAmountInput').val('0');
    $('#btnSave').html('<i class="bi bi-check-circle me-2"></i>Save Package');
}

function renderPackagesTable() {
    const tbody = $('#packagesTableBody');

    if (filteredPackages.length === 0) {
        tbody.html(`
            <tr>
                <td colspan="5" class="text-center py-4">
                    <i class="bi bi-inbox" style="font-size: 3rem; color: #cbd5e1;"></i>
                    <p class="mt-2 mb-0 text-muted">No packages found</p>
                </td>
            </tr>
        `);
    } else {
        tbody.html(filteredPackages.map((pkg, index) => `
            <tr>
                <td>${(currentPage - 1) * entriesPerPage + index + 1}</td>
                <td>${pkg.packageName}</td>
                <td>
                    ${pkg.courses.map(c => `<span class="course-badge">${c.courseName}</span>`).join('')}
                </td>
                <td>₹${pkg.totalAmount.toLocaleString('en-IN')}</td>
                <td>
                    <button class="btn btn-sm btn-outline-info" onclick="viewPackage(${pkg.id})" title="View">
                        <i class="bi bi-eye"></i>
                    </button>
                    <button class="btn btn-sm btn-outline-warning" onclick="editPackage(${pkg.id})" title="Edit">
                        <i class="bi bi-pencil"></i>
                    </button>
                    <button class="btn btn-sm btn-outline-danger" onclick="deletePackage(${pkg.id})" title="Delete">
                        <i class="bi bi-trash"></i>
                    </button>
                </td>
            </tr>
        `).join(''));
    }
}

function updatePaginationInfo(response) {
    const start = response.totalItems === 0 ? 0 : (response.currentPage * entriesPerPage) + 1;
    const end = Math.min((response.currentPage + 1) * entriesPerPage, response.totalItems);

    $('#entriesStart').text(start);
    $('#entriesEnd').text(end);
    $('#totalEntries').text(response.totalItems);

    renderPagination(response.currentPage + 1, response.totalPages);
}

function renderPagination(current, total) {
    const pagination = $('#paginationControls');
    let html = `
        <li class="page-item ${current === 1 ? 'disabled' : ''}">
            <a class="page-link" href="#" onclick="changePackagePage(${current - 1}); return false;">Previous</a>
        </li>
    `;

    for (let i = 1; i <= total; i++) {
        if (i === 1 || i === total || (i >= current - 1 && i <= current + 1)) {
            html += `
                <li class="page-item ${i === current ? 'active' : ''}">
                    <a class="page-link" href="#" onclick="changePackagePage(${i}); return false;">${i}</a>
                </li>
            `;
        } else if (i === current - 2 || i === current + 2) {
            html += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
        }
    }

    html += `
        <li class="page-item ${current === total || total === 0 ? 'disabled' : ''}">
            <a class="page-link" href="#" onclick="changePackagePage(${current + 1}); return false;">Next</a>
        </li>
    `;

    pagination.html(html);
}

function changePackagePage(page) {
    currentPage = page;
    loadPackages();
}

function viewPackage(id) {
    $.ajax({
        url: `/api/packages/${id}`,
        method: 'GET',
        success: function(pkg) {
            $('#viewPackageName').text(pkg.packageName);
            $('#viewPackageAmount').text('₹' + pkg.totalAmount.toLocaleString('en-IN'));

            const coursesHtml = pkg.courses.map(c => `
                <div class="d-flex justify-content-between align-items-center mb-2 p-2 bg-light rounded">
                    <span>${c.courseName}</span>
                    <strong>₹${c.courseFees.toLocaleString('en-IN')}</strong>
                </div>
            `).join('');

            $('#viewPackageCourses').html(coursesHtml);
            new bootstrap.Modal($('#viewPackageModal')[0]).show();
        },
        error: function(xhr) {
            showError('Failed to load package details');
        }
    });
}

function editPackage(id) {
    $.ajax({
        url: `/api/packages/${id}`,
        method: 'GET',
        success: function(pkg) {
            editingPackageId = id;
            $('#packageTitle').val(pkg.packageName);
            selectedCourses = pkg.courses.map(c => ({
                id: c.id,
                courseName: c.courseName,
                courseFees: c.courseFees
            }));
            renderSelectedCourses();
            $('#totalAmount').text(pkg.totalAmount.toLocaleString('en-IN'));
            $('#totalAmountInput').val(pkg.totalAmount);
            $('#btnSave').html('<i class="bi bi-check-circle me-2"></i>Update Package');

            $('html, body').animate({
                scrollTop: $('#packageForm').offset().top - 100
            }, 500);
        },
        error: function(xhr) {
            showError('Failed to load package for editing');
        }
    });
}

function deletePackage(id) {
    new bootstrap.Modal($('#deleteModal')[0]).show();

    $('#btnConfirmDelete').off('click').on('click', function() {
        $.ajax({
            url: `/api/packages/${id}`,
            method: 'DELETE',
            headers: {
                [csrfHeader]: csrfToken
            },
            success: function() {
                showSuccess('Package deleted successfully!');
                bootstrap.Modal.getInstance($('#deleteModal')[0]).hide();
                loadPackages();
            },
            error: function(xhr) {
                const errorMsg = xhr.responseJSON?.error || 'Failed to delete package';
                showError(errorMsg);
                bootstrap.Modal.getInstance($('#deleteModal')[0]).hide();
            }
        });
    });
}

function exportToCSV() {
    $.ajax({
        url: '/api/packages/export/csv',
        method: 'GET',
        success: function(response) {
            if (response.message) {
                showWarning(response.message);
                return;
            }

            const csvContent = generateCSVContent(response);
            downloadCSV(csvContent, 'packages_export.csv');
            showSuccess('Packages exported successfully!');
        },
        error: function(xhr) {
            showError('Failed to export packages');
        }
    });
}

function generateCSVContent(data) {
    const headers = ['S.No', 'Package Name', 'Courses', 'Total Amount'];
    const rows = data.map(pkg => [
        pkg.serialNo,
        pkg.packageName,
        pkg.courseNames,
        pkg.totalAmount
    ]);

    let csv = headers.join(',') + '\n';
    rows.forEach(row => {
        csv += row.map(cell => `"${cell}"`).join(',') + '\n';
    });

    return csv;
}

function downloadCSV(content, filename) {
    const blob = new Blob([content], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    const url = URL.createObjectURL(blob);
    link.setAttribute('href', url);
    link.setAttribute('download', filename);
    link.style.visibility = 'hidden';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
}

function showSuccess(message) {
    Swal.fire({
        icon: 'success',
        title: 'Success',
        text: message,
        timer: 2000,
        showConfirmButton: false
    });
}

function showError(message) {
    Swal.fire({
        icon: 'error',
        title: 'Error',
        text: message
    });
}

function showWarning(message) {
    Swal.fire({
        icon: 'warning',
        title: 'Warning',
        text: message
    });
}