// Fees Collection Report JavaScript
(function () {
    'use strict';

    // Global variables
    let dataTable = null;
    const API_BASE_URL = '/api/fee-collections';

    // CSRF Token handling
    let csrfToken = null;
    let csrfHeader = null;

    // Initialize on page load
    $(document).ready(function () {
        initializePage();
        setupEventListeners();
        initializeCsrfToken();
        setDefaultDates();
        $('#exportSection').slideDown();
    });

    // Set default dates (last 30 days)
    function setDefaultDates() {
        const today = new Date();
        const thirtyDaysAgo = new Date(today);
        thirtyDaysAgo.setDate(today.getDate() - 30);

        $('#toDate').val(formatDate(today));
        $('#fromDate').val(formatDate(thirtyDaysAgo));
    }

    // Format date to YYYY-MM-DD
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
                console.log('CSRF token initialized');
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

        function setupAjaxWithCsrf() {
            $.ajaxSetup({
                beforeSend: function(xhr) {
                    if (csrfToken && csrfHeader) {
                        xhr.setRequestHeader(csrfHeader, csrfToken);
                    }
                }
            });
        }

    // Initialize page
    function initializePage() {
        console.log('Fees Collection page initialized');
    }

    // Setup event listeners
    function setupEventListeners() {
        // Form submission
        $('#feesCollectionForm').on('submit', function (e) {
            e.preventDefault();
            if (validateForm()) {
                searchRecords();
            }
        });

        // Reset button
        $('#resetBtn').on('click', resetForm);

        // Import CSV button
        $('#importCsvBtn').on('click', function () {
            $('#csvFileInput').click();
        });

        // CSV file input change
        $('#csvFileInput').on('change', handleCsvImport);
    }

    // Validate form
    function validateForm() {
        const fromDate = $('#fromDate').val();
        const toDate = $('#toDate').val();

        if (!fromDate || !toDate) {
            Swal.fire({
                icon: 'warning',
                title: 'Validation Error',
                text: 'Please select both from and to dates',
                confirmButtonColor: '#667eea'
            });
            return false;
        }

        if (new Date(fromDate) > new Date(toDate)) {
            Swal.fire({
                icon: 'warning',
                title: 'Invalid Date Range',
                text: 'From date cannot be greater than to date',
                confirmButtonColor: '#667eea'
            });
            return false;
        }

        return true;
    }

    // Search records
    function searchRecords() {
        showLoading();

        const fromDate = $('#fromDate').val();
        const toDate = $('#toDate').val();
        const paymentMode = $('#paymentModeFilter').val();
        const dataSource = $('#dataSourceFilter').val();


        $.ajax({
            url: `${API_BASE_URL}`,
            method: 'GET',
            data: {
                fromDate: fromDate,
                toDate: toDate,
                paymentMode: paymentMode || null,
                dataSource: dataSource || null,
                page: 0,
                size: 1000000
            },
            beforeSend: function(xhr) {
                if (csrfToken && csrfHeader) {
                    xhr.setRequestHeader(csrfHeader, csrfToken);
                }
            },
            success: function (response) {
                if (dataTable) {
                    dataTable.destroy();
                }

                // ✅ Check if response.content exists and has data
                console.log('API Response:', response);

                const hasData = response && response.content && Array.isArray(response.content) && response.content.length > 0;

                if (hasData) {
                    console.log('✅ Data found:', response.content.length, 'records');
                    initializeDataTable(response.content);
                    $('#exportSection').slideDown();
                } else {
                    console.log('❌ No data found in response');
                    // Reset table to empty state
                    $('#feesCollectionTable tbody').html(`
                        <tr>
                            <td colspan="6" class="text-center py-5">
                                <i class="bi bi-inbox" style="font-size: 3rem; color: #94a3b8;"></i>
                                <p class="mt-3 mb-0 text-muted">No records found for the selected criteria</p>
                            </td>
                        </tr>
                    `);
                    $('#exportSection').slideUp();
                    $('#feesCollectionTable tfoot').hide();
                }

                fetchStatistics();
                hideLoading();

                if (!hasData) {
                    Swal.fire({
                        icon: 'info',
                        title: 'No Records Found',
                        text: 'No records found for the selected criteria',
                        confirmButtonColor: '#667eea'
                    });
                }
            },
            error: function (xhr, status, error) {
                hideLoading();
                Swal.fire({
                    icon: 'error',
                    title: 'Error',
                    text: 'Failed to fetch records: ' + error,
                    confirmButtonColor: '#667eea'
                });
            }
        });
    }

    // Fetch statistics
    function fetchStatistics() {
        const fromDate = $('#fromDate').val();
        const toDate = $('#toDate').val();
        const paymentMode = $('#paymentModeFilter').val();
        const dataSource = $('#dataSourceFilter').val();

         $.ajax({
            url: `${API_BASE_URL}/statistics`,
            method: 'GET',
            data: {
                fromDate: fromDate,
                toDate: toDate,
                paymentMode: paymentMode || null,
                dataSource: dataSource || null
            },
            beforeSend: function(xhr) {
                if (csrfToken && csrfHeader) {
                    xhr.setRequestHeader(csrfHeader, csrfToken);
                }
            },
            success: function (stats) {
                updateStatistics(stats);
            },
            error: function () {
                console.error('Failed to fetch statistics');
            }
        });
    }

    // Initialize DataTable
    function initializeDataTable(data) {
        dataTable = $('#feesCollectionTable').DataTable({
            data: data,
            columns: [
                { data: 'receiptNo', defaultContent: 'N/A' },
                { data: 'studentName', defaultContent: 'N/A' },
                { data: 'mobileNo', defaultContent: 'N/A' },
                {
                    data: 'receiptDate',
                    render: function (data, type, row) {
                        if (!data) return row.receiptDateOriginal || 'N/A';
                        return new Date(data).toLocaleDateString('en-GB');
                    },
                    defaultContent: 'N/A'
                },
                {
                    data: 'paidFees',
                    render: function (data) {
                        if (!data) return '₹0.00';
                        return '₹' + Number(data).toLocaleString('en-IN', { minimumFractionDigits: 2 });
                    },
                    defaultContent: '₹0.00'
                },
                {
                    data: 'paymentMode',
                    render: function (data) {
                        if (!data) return '<span class="badge badge-cash">Cash</span>';
                        let badgeClass = 'badge-cash';
                        if (data === 'Online') badgeClass = 'badge-online';
                        else if (data === 'Cheque') badgeClass = 'badge-cheque';
                        else if (data === 'Card') badgeClass = 'badge-card';
                        return `<span class="badge ${badgeClass}">${data}</span>`;
                    },
                    defaultContent: '<span class="badge badge-cash">Cash</span>'
                }
            ],
            order: [[0, 'desc']],
            pageLength: 25,
            responsive: true,
            dom: '<"row"<"col-sm-12 col-md-6"l><"col-sm-12 col-md-6"f>>' +
                '<"row"<"col-sm-12"tr>>' +
                '<"row"<"col-sm-12 col-md-5"i><"col-sm-12 col-md-7"p>>',
            language: {
                search: "_INPUT_",
                searchPlaceholder: "Search records...",
                lengthMenu: "Show _MENU_ entries",
                info: "Showing _START_ to _END_ of _TOTAL_ entries",
                infoEmpty: "No entries available",
                infoFiltered: "(filtered from _MAX_ total entries)",
                paginate: {
                    first: '<i class="bi bi-chevron-double-left"></i>',
                    previous: '<i class="bi bi-chevron-left"></i>',
                    next: '<i class="bi bi-chevron-right"></i>',
                    last: '<i class="bi bi-chevron-double-right"></i>'
                }
            },
            buttons: [
                {
                    extend: 'copy',
                    text: '<i class="bi bi-clipboard me-1"></i> Copy',
                    className: 'btn btn-sm btn-export',
                    exportOptions: { columns: [0, 1, 2, 3, 4, 5] }
                },
                {
                    extend: 'csv',
                    text: '<i class="bi bi-filetype-csv me-1"></i> CSV',
                    className: 'btn btn-sm btn-export',
                    exportOptions: { columns: [0, 1, 2, 3, 4, 5] }
                },
                {
                    extend: 'excel',
                    text: '<i class="bi bi-file-earmark-excel me-1"></i> Excel',
                    className: 'btn btn-sm btn-export',
                    exportOptions: { columns: [0, 1, 2, 3, 4, 5] }
                },
                {
                    extend: 'pdf',
                    text: '<i class="bi bi-file-earmark-pdf me-1"></i> PDF',
                    className: 'btn btn-sm btn-export',
                    orientation: 'landscape',
                    exportOptions: { columns: [0, 1, 2, 3, 4, 5] }
                },
                {
                    extend: 'print',
                    text: '<i class="bi bi-printer me-1"></i> Print',
                    className: 'btn btn-sm btn-export',
                    exportOptions: { columns: [0, 1, 2, 3, 4, 5] }
                }
            ],
            footerCallback: function () {
                const api = this.api();
                const total = api.column(4).data().reduce((a, b) => {
                    const val = typeof b === 'string' ? parseFloat(b.replace(/[₹,]/g, '')) : b;
                    return a + (isNaN(val) ? 0 : val);
                }, 0);
                $('#footerTotalAmount').html('₹' + total.toLocaleString('en-IN', { minimumFractionDigits: 2 }));
            },
            drawCallback: function () {
                if (data && data.length > 0) {
                    $('#feesCollectionTable tfoot').show();
                } else {
                    $('#feesCollectionTable tfoot').hide();
                }
            }
        });

        // Add export buttons to custom container
        dataTable.buttons().container().appendTo('#exportTools');
    }

    // Update statistics
    function updateStatistics(stats) {
        $('#totalReceipts').text(stats.totalReceipts || 0);
        $('#totalAmount').text('₹' + (stats.totalAmount || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 }));
    }

    // Reset form
    function resetForm() {
        $('#feesCollectionForm')[0].reset();
        setDefaultDates();
        $('#paymentModeFilter').val('');
        $('#dataSourceFilter').val('');

        if (dataTable) {
            dataTable.destroy();
        }

        $('#feesCollectionTable tbody').html(`
            <tr>
                <td colspan="6" class="text-center py-5">
                    <i class="bi bi-inbox" style="font-size: 3rem; color: #94a3b8;"></i>
                    <p class="mt-3 mb-0 text-muted">No data available. Please select filters and search.</p>
                </td>
            </tr>
        `);

        $('#exportSection').slideUp();
        $('#feesCollectionTable tfoot').hide();

        $('#totalReceipts').text('0');
        $('#totalAmount').text('₹0');
    }

    // Handle CSV import
    function handleCsvImport(e) {
        const file = e.target.files[0];
        if (!file) return;

        if (!file.name.endsWith('.csv')) {
            Swal.fire({
                icon: 'error',
                title: 'Invalid File',
                text: 'Please select a valid CSV file',
                confirmButtonColor: '#667eea'
            });
            return;
        }

        Swal.fire({
            title: 'Import CSV Data',
            html: '<strong>Important:</strong> This will import ALL data from the CSV file AS-IS without any validation.' +
                  '<br><br>The import will accept:' +
                  '<ul class="text-start">' +
                  '<li>Duplicate records</li>' +
                  '<li>NULL or empty values</li>' +
                  '<li>Invalid date formats</li>' +
                  '<li>Any text or numbers</li>' +
                  '</ul>' +
                  '<br>All imported data will be marked as "Old Imported Data".' +
                  '<br><br>Do you want to continue?',
            icon: 'warning',
            showCancelButton: true,
            confirmButtonColor: '#667eea',
            cancelButtonColor: '#6c757d',
            confirmButtonText: 'Yes, Import',
            cancelButtonText: 'Cancel'
        }).then((result) => {
            if (result.isConfirmed) {
                performCSVImport(file);
            } else {
                $('#csvFileInput').val('');
            }
        });
    }

    // Perform CSV import
    function performCSVImport(file) {
        showLoading();

        const formData = new FormData();
        formData.append('file', file);

        $.ajax({
            url: `${API_BASE_URL}/import-csv`,
            method: 'POST',
            data: formData,
            processData: false,
            contentType: false,
            beforeSend: function(xhr) {
               if (csrfToken && csrfHeader) {
                   xhr.setRequestHeader(csrfHeader, csrfToken);
               }
            },
            success: function (response) {
                hideLoading();

                if (response.success) {
                    Swal.fire({
                        icon: 'success',
                        title: 'Import Successful',
                        html: `<strong>${response.message}</strong><br><br>` +
                              `Total Records: ${response.totalRecords}<br>` +
                              `Successful: ${response.successCount}<br>` +
                              `Failed: ${response.errorCount}` +
                              (response.errors && response.errors.length > 0 ?
                                  `<br><br><small>First few errors:<br>${response.errors.slice(0, 5).join('<br>')}</small>` : ''),
                        confirmButtonColor: '#667eea'
                    }).then(() => {
                        if ($('#fromDate').val() && $('#toDate').val()) {
                            searchRecords();
                        }
                    });
                } else {
                    Swal.fire({
                        icon: 'error',
                        title: 'Import Failed',
                        text: response.message,
                        confirmButtonColor: '#667eea'
                    });
                }

                $('#csvFileInput').val('');
            },
            error: function (xhr, status, error) {
                hideLoading();
                Swal.fire({
                    icon: 'error',
                    title: 'Import Failed',
                    text: 'Failed to import CSV: ' + error,
                    confirmButtonColor: '#667eea'
                });
                $('#csvFileInput').val('');
            }
        });
    }

    // Show loading overlay
    function showLoading() {
        if ($('.loading-overlay').length === 0) {
            $('body').append(`
                <div class="loading-overlay">
                    <div class="loading-spinner"></div>
                </div>
            `);
        }
    }

    // Hide loading overlay
    function hideLoading() {
        $('.loading-overlay').remove();
    }

})();