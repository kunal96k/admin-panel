// Configuration
const config = {
    baseUrl: '/api/', // Update with your API base URL
    endpoints: {
        courses: 'reports/sales/courses',
        salesReport: 'reports/sales/course-wise',
        statistics: 'reports/sales/statistics',
        export: 'reports/sales/export/csv'
    },
    pageLength: 25
};

// Global Variables
let dataTable = null;
let selectedCourseId = null;
let coursesData = [];
let csrfToken = null;
let csrfHeader = null;

// Initialize on DOM Ready
$(document).ready(function () {
    initializePage();
});

// Initialize Page
function initializePage() {
    loadCourses();
    setupEventListeners();
    setDefaultDates();
    initializeValidation();
    initializeCsrfToken();
}

// Set Default Dates (Current Month)
function setDefaultDates() {
    const today = new Date();
    const firstDay = new Date(today.getFullYear(), today.getMonth(), 1);

    $('#fromDate').val(formatDate(firstDay));
    $('#toDate').val(formatDate(today));
}

// Format Date to YYYY-MM-DD
function formatDate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

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

// Load Courses
function loadCourses() {
    showLoading();

    // Refresh CSRF token before request
    initializeCsrfToken();

    $.ajax({
        type: "GET",
        url: config.baseUrl + config.endpoints.courses,
        contentType: "application/json",
        dataType: "json",
        beforeSend: function (xhr) {
            if (sessionStorage.token) {
                xhr.setRequestHeader("Authorization", "Bearer " + sessionStorage.token);
            }
            if (sessionStorage.sbi) {
                xhr.setRequestHeader("X-API-Key", sessionStorage.sbi);
            }
            if (csrfToken && csrfHeader) {
                xhr.setRequestHeader(csrfHeader, csrfToken);
            }
        },
        success: function (result) {
            coursesData = result;
            populateCourseDropdown(result);
            hideLoading();
        },
        error: function (xhr) {
            hideLoading();
            handleError(xhr);
        }
    });
}

// Populate Course Dropdown
function populateCourseDropdown(courses) {
    const $courseFilter = $("#courseFilter");
    $courseFilter.empty();

    $courseFilter.append('<option value="" disabled selected>-- Select Course --</option>');

    courses.forEach(function (course) {
        $courseFilter.append(
            $('<option>', {
                value: course.CourseId,
                text: course.CourseName
            })
        );
    });

    updateStatsCard('#totalCourses', courses.length);
}

// Setup Event Listeners
function setupEventListeners() {
    // Form Submit
    $('#salesReportForm').on('submit', function (e) {
        e.preventDefault();
        if (validateForm()) {
            searchReport();
        }
    });

    // Reset Button
    $('#resetBtn').on('click', resetForm);

    // Course Filter Change
    $('#courseFilter').on('change', function () {
        selectedCourseId = $(this).val();
        $(this).removeClass('is-invalid');
    });

    // Course Search
    $('#courseSearchInput').on('input', function () {
        const searchTerm = $(this).val().toLowerCase();
        $('#courseFilter option').each(function () {
            const text = $(this).text().toLowerCase();
            $(this).toggle(text.includes(searchTerm) || $(this).val() === '');
        });
    });

    // Table Search
    $('#tableSearch').on('keyup', function () {
        if (dataTable) {
            dataTable.search($(this).val()).draw();
        }
    });

    // Page Size Change
    $('#pageSizeSelect').on('change', function () {
        if (dataTable) {
            dataTable.page.len($(this).val()).draw();
        }
    });

    // Export Buttons
    setupExportButtons();
}

// Setup Export Buttons
function setupExportButtons() {
    // Buttons are now handled via DataTables and appended to #exportTools
    // Individual listeners are removed to rely on DataTables buttons extension
}

// Validate Form
function validateForm() {
    let isValid = true;

    const fromDate = $('#fromDate').val();
    const toDate = $('#toDate').val();
    const courseId = $('#courseFilter').val();

    // Validate From Date
    if (!fromDate) {
        $('#fromDate').addClass('is-invalid');
        isValid = false;
    } else {
        $('#fromDate').removeClass('is-invalid');
    }

    // Validate To Date
    if (!toDate) {
        $('#toDate').addClass('is-invalid');
        isValid = false;
    } else {
        $('#toDate').removeClass('is-invalid');
    }

    // Validate Date Range
    if (fromDate && toDate && new Date(fromDate) > new Date(toDate)) {
        $('#toDate').addClass('is-invalid');
        showNotification('To Date must be greater than From Date', 'error');
        isValid = false;
    }

    // Validate Course
    if (!courseId) {
        $('#courseFilter').addClass('is-invalid');
        isValid = false;
    } else {
        $('#courseFilter').removeClass('is-invalid');
    }

    return isValid;
}

// Search Report
function searchReport() {
    showLoading();

    const fromDate = $('#fromDate').val();
    const toDate = $('#toDate').val();

    if (dataTable) {
        dataTable.destroy();
        dataTable = null;
    }

    initializeDataTable(fromDate, toDate);

    // Fetch and update statistics
    fetchStatistics(selectedCourseId, fromDate, toDate);
}

// Initialize DataTable
function initializeDataTable(startDate, toDate) {
    // Refresh CSRF token before request
    initializeCsrfToken();

    function exportAllData(e, dt, button, config) {
        const self = this;
        const oldStart = dt.settings()[0]._iDisplayStart;
        const pageInfo = dt.page.info();
        const totalRecords = pageInfo && typeof pageInfo.recordsFiltered === 'number'
            ? pageInfo.recordsFiltered
            : (pageInfo && typeof pageInfo.recordsTotal === 'number' ? pageInfo.recordsTotal : 0);

        const buttonName = config.exportType || config.extend;
        let originalActionFn = null;

        if (buttonName) {
            const btns = $.fn.dataTable.ext.buttons;
            if (btns[buttonName] && btns[buttonName].action) {
                originalActionFn = btns[buttonName].action;
            } else if (btns[buttonName + 'Html5'] && btns[buttonName + 'Html5'].action) {
                originalActionFn = btns[buttonName + 'Html5'].action;
            }
        }

        if (!totalRecords || totalRecords <= 0) {
            if (originalActionFn) {
                originalActionFn.call(self, e, dt, button, config);
            }
            return;
        }

        showLoading();

        dt.one('preXhr', function (_e, _s, data) {
            data.start = 0;
            data.length = totalRecords;
        });

        dt.one('draw', function () {
            hideLoading();
            if (originalActionFn) {
                try {
                    setTimeout(function () {
                        originalActionFn.call(self, e, dt, button, config);
                        dt.one('preXhr', function (_e, _s, data) {
                            data.start = oldStart;
                            data.length = pageInfo.length;
                        });
                        dt.ajax.reload(null, false);
                    }, 100);
                } catch (err) {
                    console.error('Export failed:', err);
                }
            }
        });

        dt.ajax.reload();
    }

    dataTable = $('#salesReportTable').DataTable({
        processing: true,
        serverSide: true,
        destroy: true,
        pageLength: config.pageLength,
        lengthMenu: [10, 25, 50, 100, 1000],
        searching: true, //  Enable DataTables search
        language: {
            processing: '<div class="spinner-border text-primary" role="status"><span class="visually-hidden">Loading...</span></div>',
            emptyTable: '<div class="empty-state"><i class="bi bi-inbox"></i><p>No records found</p></div>',
            search: "_INPUT_",
            searchPlaceholder: "Search in table...",
            info: 'Showing _START_ to _END_ of _TOTAL_ entries',
            infoEmpty: 'Showing 0 to 0 of 0 entries',
            lengthMenu: 'Show _MENU_ entries',
            paginate: {
                first: 'First',
                last: 'Last',
                next: '<i class="bi bi-chevron-right"></i>',
                previous: '<i class="bi bi-chevron-left"></i>'
            }
        },
        dom: '<"row"<"col-sm-12 col-md-6"l><"col-sm-12 col-md-6"f>>' +
            '<"row"<"col-sm-12"tr>>' +
            '<"row"<"col-sm-12 col-md-5"i><"col-sm-12 col-md-7"p>>B',
        buttons: [
            {
                extend: 'copy',
                exportType: 'copy',
                className: 'btn btn-sm btn-outline-primary',
                text: '<i class="bi bi-clipboard"></i> Copy',
                exportOptions: { columns: [0, 1, 2, 3, 4] },
                action: exportAllData
            },
            {
                extend: 'excel',
                exportType: 'excel',
                className: 'btn btn-sm btn-outline-success',
                text: '<i class="bi bi-file-earmark-excel"></i> Excel',
                title: 'Course Wise Sales Report',
                filename: 'course_sales_report_' + new Date().getTime(),
                action: exportAllData
            },
            {
                extend: 'pdf',
                exportType: 'pdf',
                className: 'btn btn-sm btn-outline-danger',
                text: '<i class="bi bi-file-earmark-pdf"></i> PDF',
                title: 'Course Wise Sales Report',
                filename: 'course_sales_report_' + new Date().getTime(),
                orientation: 'landscape',
                action: exportAllData
            },
            {
                extend: 'csv',
                exportType: 'csv',
                className: 'btn btn-sm btn-outline-info',
                text: '<i class="bi bi-filetype-csv"></i> CSV',
                title: 'Course Wise Sales Report',
                filename: 'course_sales_report_' + new Date().getTime(),
                action: exportAllData
            }
        ],
        ajax: {
            url: config.baseUrl + config.endpoints.salesReport,
            type: 'POST',
            contentType: 'application/json',
            beforeSend: function (xhr) {
                if (sessionStorage.token) {
                    xhr.setRequestHeader("Authorization", "Bearer " + sessionStorage.token);
                }
                if (sessionStorage.sbi) {
                    xhr.setRequestHeader("X-API-Key", sessionStorage.sbi);
                }
                if (csrfToken && csrfHeader) {
                    xhr.setRequestHeader(csrfHeader, csrfToken);
                }
            },
            data: function (d) {
                return JSON.stringify({
                    parameters: d,
                    ddlSelectedCourseId: selectedCourseId,
                    StartDate: startDate,
                    toDate: toDate
                });
            },
            dataSrc: function (json) {
                hideLoading();
                const total = json.recordsTotal || 0;
                $('#exportSection').toggle(total > 0);
                updateStatistics(json);
                return json.data || [];
            },
            error: function (xhr) {
                hideLoading();
                handleError(xhr);
            }
        },
        columns: [
            {
                data: 'RegistrationNo',
                render: function (data) {
                    return '<span class="fw-semibold">' + (data || 'N/A') + '</span>';
                }
            },
            {
                data: 'StudentName',
                render: function (data) {
                    return '<span class="text-primary">' + (data || 'N/A') + '</span>';
                }
            },
            {
                data: 'StudentMobileNo',
                render: function (data) {
                    return '<span class="text-muted">' + (data || 'N/A') + '</span>';
                }
            },
            {
                data: 'CreatedDate',
                render: function (data) {
                    return data || 'N/A';
                }
            },
            {
                data: 'CourseAmount',
                render: function (data) {
                    if (!data || data === '₹0.00' || data === 0) {
                        return '<span class="fw-bold text-danger">₹0.00</span>';
                    }
                    return '<span class="fw-bold text-success">' + data + '</span>';
                }
            }
        ],
        order: [[3, 'desc']],
        responsive: true,
        drawCallback: function (settings) {
            const api = this.api();
            const data = api.rows({ page: 'current' }).data();

            //  Get total from first record
            if (data.length > 0) {
                const totalCourseAmount = data[0].TotalCourseAmount || '₹0.00';
                $('#totalAmount').text(totalCourseAmount);

            } else {
                $('#totalAmount').text('₹0.00');
            }
        }
    });

    // Add export buttons to custom container
    if ($('#exportTools').length) {
        dataTable.buttons().container().appendTo('#exportTools');
    }
}

// Update Statistics
function updateStatistics(json) {
    const data = json.data || [];

    // Get total amount from first record
    let totalAmount = 0;
    if (data.length > 0 && data[0].TotalCourseAmount) {
        // Remove ₹ symbol and parse
        const amountStr = data[0].TotalCourseAmount.replace('₹', '').replace(/,/g, '');
        totalAmount = parseFloat(amountStr) || 0;
    }

    const totalStudents = json.recordsFiltered || 0;
    const avgFees = totalStudents > 0 ? totalAmount / totalStudents : 0;

    updateStatsCard('#totalStudents', totalStudents);
    updateStatsCard('#totalRevenue', '₹' + formatCurrency(totalAmount));
    updateStatsCard('#avgFees', '₹' + formatCurrency(avgFees));

    // Update footer total
    $('#totalAmount').text('₹' + formatCurrency(totalAmount));
}

// Update Stats Card
function updateStatsCard(selector, value) {
    $(selector).fadeOut(200, function () {
        $(this).text(value).fadeIn(200);
    });
}

// Update Pagination Info
function updatePaginationInfo(json) {
    const start = json.recordsFiltered > 0 ? (json.draw - 1) * json.length + 1 : 0;
    const end = Math.min(json.draw * json.length, json.recordsFiltered);

    $('#entriesStart').text(start);
    $('#entriesEnd').text(end);
    $('#totalEntries').text(json.recordsFiltered || 0);
}

// Update Pagination Controls
function updatePaginationControls() {
    if (!dataTable) return;

    const info = dataTable.page.info();
    const $pagination = $('#paginationControls');

    $pagination.empty();

    // Previous Button
    $pagination.append(`
        <li class="page-item ${info.page === 0 ? 'disabled' : ''}">
            <a class="page-link" href="#" data-page="${info.page - 1}">
                <i class="bi bi-chevron-left"></i>
            </a>
        </li>
    `);

    // Page Numbers
    const startPage = Math.max(0, info.page - 2);
    const endPage = Math.min(info.pages - 1, info.page + 2);

    if (startPage > 0) {
        $pagination.append(`
            <li class="page-item">
                <a class="page-link" href="#" data-page="0">1</a>
            </li>
        `);
        if (startPage > 1) {
            $pagination.append('<li class="page-item disabled"><span class="page-link">...</span></li>');
        }
    }

    for (let i = startPage; i <= endPage; i++) {
        $pagination.append(`
            <li class="page-item ${i === info.page ? 'active' : ''}">
                <a class="page-link" href="#" data-page="${i}">${i + 1}</a>
            </li>
        `);
    }

    if (endPage < info.pages - 1) {
        if (endPage < info.pages - 2) {
            $pagination.append('<li class="page-item disabled"><span class="page-link">...</span></li>');
        }
        $pagination.append(`
            <li class="page-item">
                <a class="page-link" href="#" data-page="${info.pages - 1}">${info.pages}</a>
            </li>
        `);
    }

    // Next Button
    $pagination.append(`
        <li class="page-item ${info.page === info.pages - 1 ? 'disabled' : ''}">
            <a class="page-link" href="#" data-page="${info.page + 1}">
                <i class="bi bi-chevron-right"></i>
            </a>
        </li>
    `);

    // Bind Click Events
    $pagination.find('a.page-link').on('click', function (e) {
        e.preventDefault();
        const page = $(this).data('page');
        if (page !== undefined && dataTable) {
            dataTable.page(page).draw('page');
        }
    });
}

// Reset Form
function resetForm() {
    $('#salesReportForm')[0].reset();
    setDefaultDates();
    selectedCourseId = null;
    $('#courseFilter').val('');
    $('#courseSearchInput').val('');
    $('#tableSearch').val('');

    $('.is-invalid').removeClass('is-invalid');

    if (dataTable) {
        dataTable.destroy();
        dataTable = null;
    }

    $('#salesReportTable tbody').html(`
        <tr>
            <td colspan="5" class="text-center py-5">
                <i class="bi bi-inbox" style="font-size: 3rem; color: #94a3b8;"></i>
                <p class="mt-3 mb-0 text-muted">No data available. Please select filters and search.</p>
            </td>
        </tr>
    `);

    updateStatsCard('#totalStudents', 0);
    updateStatsCard('#totalRevenue', '₹0.00');
    $('#avgFees').text('₹0.00');
    $('#totalAmount').text('₹0.00');
    $('#exportSection').hide();

    showNotification('Form reset successfully', 'info');
}

// Format Currency
function formatCurrency(amount) {
    return parseFloat(amount || 0).toLocaleString('en-IN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    });
}

// Format Display Date
function formatDisplayDate(dateString) {
    if (!dateString) return 'N/A';

    // If it's already formatted (contains letters), return as-is
    if (dateString.match(/[a-zA-Z]/)) {
        return dateString;
    }

    // Otherwise, parse and format
    try {
        const date = new Date(dateString);
        const options = { day: '2-digit', month: 'short', year: 'numeric' };
        return date.toLocaleDateString('en-IN', options);
    } catch (e) {
        return dateString;
    }
}

// Initialize Validation
function initializeValidation() {
    $('.form-control, .form-select').on('input change', function () {
        if ($(this).val()) {
            $(this).removeClass('is-invalid');
        }
    });
}

// Show Loading
function showLoading() {
    if ($('.loading-overlay').length === 0) {
        $('body').append(`
            <div class="loading-overlay">
                <div class="loading-spinner"></div>
            </div>
        `);
    }
}

// Hide Loading
function hideLoading() {
    $('.loading-overlay').remove();
}

// Show Notification
function showNotification(message, type = 'info') {
    const icon = {
        success: 'success',
        error: 'error',
        warning: 'warning',
        info: 'info'
    };

    Swal.fire({
        icon: icon[type],
        title: message,
        toast: true,
        position: 'top-end',
        showConfirmButton: false,
        timer: 3000,
        timerProgressBar: true
    });
}

// Handle Error
function handleError(xhr) {
    let message = 'An error occurred. Please try again.';

    if (xhr.status === 401) {
        message = 'Session expired. Please login again.';
        setTimeout(() => {
            window.location.href = '/login';
        }, 2000);
    } else if (xhr.status === 403) {
        message = 'Access denied. You do not have permission.';
    } else if (xhr.status === 404) {
        message = 'Resource not found.';
    } else if (xhr.status === 500) {
        message = 'Server error. Please contact support.';
    }

    showNotification(message, 'error');
}

// Fetch Statistics
function fetchStatistics(courseId, fromDate, toDate) {
    if (!courseId || !fromDate || !toDate) return;

    // Refresh CSRF token before request
    initializeCsrfToken();

    $.ajax({
        type: 'GET',
        url: config.baseUrl + config.endpoints.statistics,
        data: {
            courseId: courseId,
            startDate: fromDate,
            toDate: toDate
        },
        beforeSend: function (xhr) {
            if (sessionStorage.token) {
                xhr.setRequestHeader("Authorization", "Bearer " + sessionStorage.token);
            }
            if (sessionStorage.sbi) {
                xhr.setRequestHeader("X-API-Key", sessionStorage.sbi);
            }
            if (csrfToken && csrfHeader) {
                xhr.setRequestHeader(csrfHeader, csrfToken);
            }
        },
        success: function (stats) {
            updateStatsCard('#totalStudents', stats.totalStudents);
            updateStatsCard('#totalRevenue', '₹' + stats.totalRevenue);
            updateStatsCard('#totalCourses', stats.totalCourses);
            updateStatsCard('#avgFees', '₹' + stats.avgFees);
        },
        error: function (xhr) {
            console.error('Failed to fetch statistics');
        }
    });
}