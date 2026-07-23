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
            beforeSend: function (xhr) {
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

        if (dataTable) {
            dataTable.destroy();
            dataTable = null;
        }

        initializeDataTable({
            fromDate,
            toDate,
            paymentMode: paymentMode || null,
            dataSource: dataSource || null
        });

        fetchStatistics();
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
            beforeSend: function (xhr) {
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
    function initializeDataTable(filters) {
        // Refresh CSRF token before request
        initializeCsrfToken();

        function exportAllData(e, dt, button, config) {
            const self = this;
            const oldStart = dt.settings()[0]._iDisplayStart;
            const pageInfo = dt.page.info();
            const totalRecords = pageInfo && typeof pageInfo.recordsTotal === 'number'
                ? pageInfo.recordsTotal
                : 0;

            // Try to find the original action function
            const buttonName = config.exportType || config.extend;
            let originalActionFn = null;

            if (buttonName) {
                const btns = $.fn.dataTable.ext.buttons;
                if (btns[buttonName] && btns[buttonName].action) {
                    originalActionFn = btns[buttonName].action;
                } else if (btns[buttonName + 'Html5'] && btns[buttonName + 'Html5'].action) {
                    originalActionFn = btns[buttonName + 'Html5'].action;
                } else if (btns[buttonName + 'Flash'] && btns[buttonName + 'Flash'].action) {
                    originalActionFn = btns[buttonName + 'Flash'].action;
                }
            }

            if (!originalActionFn) {
                console.warn('⚠️ Could not find original action for:', buttonName || 'unknown');
            }

            if (!totalRecords || totalRecords <= 0) {
                if (originalActionFn) {
                    originalActionFn.call(self, e, dt, button, config);
                }
                return;
            }

            showLoading(); // Show loading while fetching all data

            dt.one('preXhr', function (_e, _s, data) {
                data.start = 0;
                data.length = totalRecords;
            });

            dt.one('draw', function () {
                hideLoading();

                if (originalActionFn) {
                    try {
                        // Small extra delay to ensure any other draw handlers finished
                        setTimeout(function () {
                            originalActionFn.call(self, e, dt, button, config);
                            // Revert to original settings
                            dt.one('preXhr', function (_e, _s, data) {
                                data.start = oldStart;
                                data.length = pageInfo.length;
                            });
                            dt.ajax.reload(null, false);
                        }, 100);
                    } catch (err) {
                        console.error('❌ Export action failed:', err);
                        hideLoading();
                    }
                } else {
                    console.error('❌ Cannot export: Original action not found');
                    hideLoading();
                }
            });

            dt.ajax.reload();
        }

        dataTable = $('#feesCollectionTable').DataTable({
            processing: true,
            serverSide: true,
            destroy: true,
            pageLength: 25,
            lengthMenu: [10, 25, 50, 100, 1000],
            searching: false,
            createdRow: function (row, data, dataIndex) {
                $(row).find('td:eq(0)').attr('data-label', 'RECEIPT NO.');
                $(row).find('td:eq(1)').attr('data-label', 'STUDENT NAME');
                $(row).find('td:eq(2)').attr('data-label', 'MOBILE NO.');
                $(row).find('td:eq(3)').attr('data-label', 'RECEIPT DATE');
                $(row).find('td:eq(4)').attr('data-label', 'PAID FEES');
                $(row).find('td:eq(5)').attr('data-label', 'PAYMENT MODE');
            },

            ajax: function (dtParams, callback) {
                const page = Math.floor((dtParams.start || 0) / (dtParams.length || 25));
                const size = dtParams.length || 25;

                $.ajax({
                    url: `${API_BASE_URL}`,
                    method: 'GET',
                    data: {
                        fromDate: filters.fromDate,
                        toDate: filters.toDate,
                        paymentMode: filters.paymentMode,
                        dataSource: filters.dataSource,
                        page: page,
                        size: size
                    },
                    beforeSend: function (xhr) {
                        if (csrfToken && csrfHeader) {
                            xhr.setRequestHeader(csrfHeader, csrfToken);
                        }
                    },
                    success: function (response) {
                        const content = response && response.content && Array.isArray(response.content)
                            ? response.content
                            : [];

                        const total = response && typeof response.totalElements === 'number'
                            ? response.totalElements
                            : 0;

                        // Show desktop or mobile export trigger based on screen
                        if (total > 0) {
                            if (window.innerWidth >= 992) {
                                $('#exportSection').show();
                            } else {
                                $('#mobileExportTrigger').show();
                            }
                        } else {
                            $('#exportSection').hide();
                            $('#mobileExportTrigger').hide();
                        }

                        callback({
                            draw: dtParams.draw,
                            recordsTotal: total,
                            recordsFiltered: total,
                            data: content
                        });

                        hideLoading();

                        if (total === 0) {
                            $('#feesCollectionTable tfoot').hide();
                        }
                    },
                    error: function (xhr, status, error) {
                        hideLoading();
                        callback({
                            draw: dtParams.draw,
                            recordsTotal: 0,
                            recordsFiltered: 0,
                            data: []
                        });

                        Swal.fire({
                            icon: 'error',
                            title: 'Error',
                            text: 'Failed to fetch records: ' + error,
                            confirmButtonColor: '#667eea'
                        });
                    }
                });
            },
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
                    defaultContent: 'N/A'
                }
            ],
            responsive: true,
            dom: 'Bfrtip',
            language: {
                processing: '<div class="spinner-border text-primary" role="status"><span class="visually-hidden">Loading...</span></div>',
                emptyTable: 'No data available',
                info: 'Showing _START_ to _END_ of _TOTAL_ entries',
                infoEmpty: 'No entries available',
                infoFiltered: '(filtered from _MAX_ total entries)',
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
                    exportType: 'copy',
                    text: '<i class="bi bi-clipboard me-1"></i> Copy',
                    className: 'btn btn-sm btn-outline-primary',
                    exportOptions: { columns: [0, 1, 2, 3, 4, 5] },
                    footer: true,
                    customize: function (win) {
                        const totalAmt = $('#footerTotalAmount').text() || '₹0.00';
                        const txt = win.document.body.innerText || '';
                        win.document.body.innerText = txt + '\n\nTOTAL AMOUNT:\t' + totalAmt;
                    },
                    action: exportAllData
                },
                {
                    extend: 'csv',
                    exportType: 'csv',
                    text: '<i class="bi bi-filetype-csv me-1"></i> CSV',
                    className: 'btn btn-sm btn-outline-info',
                    title: 'Fees Collection Report',
                    filename: 'fees_collection_report_' + new Date().getTime(),
                    exportOptions: { columns: [0, 1, 2, 3, 4, 5] },
                    footer: true,
                    customize: function (csv) {
                        const totalAmt = $('#footerTotalAmount').text() || '₹0.00';
                        return csv + '\n,,,,TOTAL AMOUNT:,' + totalAmt;
                    },
                    action: exportAllData
                },
                {
                    extend: 'excel',
                    exportType: 'excel',
                    text: '<i class="bi bi-file-earmark-excel me-1"></i> Excel',
                    className: 'btn btn-sm btn-outline-success',
                    title: 'Fees Collection Report',
                    filename: 'fees_collection_report_' + new Date().getTime(),
                    exportOptions: { columns: [0, 1, 2, 3, 4, 5] },
                    footer: true,
                    customize: function (xlsx) {
                        const sheet = xlsx.xl.worksheets['sheet1.xml'];
                        const totalAmt = $('#footerTotalAmount').text() || '₹0.00';
                        const $sheetData = $('sheetData', sheet);
                        const lastRowNum = $('row', $sheetData).length + 1;
                        const footerRow = '<row r="' + lastRowNum + '">' +
                            '<c r="A' + lastRowNum + '" t="inlineStr"><is><t></t></is></c>' +
                            '<c r="B' + lastRowNum + '" t="inlineStr"><is><t></t></is></c>' +
                            '<c r="C' + lastRowNum + '" t="inlineStr"><is><t></t></is></c>' +
                            '<c r="D' + lastRowNum + '" t="inlineStr"><is><t></t></is></c>' +
                            '<c r="E' + lastRowNum + '" t="inlineStr"><is><t>TOTAL AMOUNT:</t></is></c>' +
                            '<c r="F' + lastRowNum + '" t="inlineStr"><is><t>' + totalAmt + '</t></is></c>' +
                            '</row>';
                        $sheetData.append(footerRow);
                    },
                    action: exportAllData
                },
                {
                    extend: 'pdf',
                    exportType: 'pdf',
                    text: '<i class="bi bi-file-earmark-pdf me-1"></i> PDF',
                    className: 'btn btn-sm btn-outline-danger',
                    title: 'Fees Collection Report',
                    filename: 'fees_collection_report_' + new Date().getTime(),
                    orientation: 'landscape',
                    exportOptions: { columns: [0, 1, 2, 3, 4, 5] },
                    footer: true,
                    customize: function (doc) {
                        const totalAmt = $('#footerTotalAmount').text() || '₹0.00';
                        doc.content.push({
                            table: {
                                widths: ['*', '*', '*', '*', '*', '*'],
                                body: [[
                                    { text: '', border: [false, false, false, false] },
                                    { text: '', border: [false, false, false, false] },
                                    { text: '', border: [false, false, false, false] },
                                    { text: '', border: [false, false, false, false] },
                                    { text: 'TOTAL AMOUNT:', bold: true, alignment: 'right', border: [false, true, false, false] },
                                    { text: totalAmt, bold: true, alignment: 'right', color: '#1d4ed8', border: [false, true, false, false] }
                                ]]
                            },
                            margin: [0, 8, 0, 0]
                        });
                    },
                    action: exportAllData
                },
                {
                    extend: 'print',
                    exportType: 'print',
                    text: '<i class="bi bi-printer me-1"></i> Print',
                    className: 'btn btn-sm btn-outline-secondary',
                    exportOptions: { columns: [0, 1, 2, 3, 4, 5] },
                    action: exportAllData
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
                const api = this.api();
                const rowCount = api.rows({ page: 'current' }).data().length;
                if (rowCount > 0) {
                    $('#feesCollectionTable tfoot').show();
                } else {
                    $('#feesCollectionTable tfoot').hide();
                }
            }
        });

        // Add export buttons to desktop container
        dataTable.buttons().container().appendTo('#exportTools');

        // Show desktop or mobile export section
        const isDesktop = window.innerWidth >= 992;
        if (isDesktop) {
            // export section shown via #exportSection toggle in ajax callback
        } else {
            // Mobile: show trigger button (visibility controlled by ajax callback)
            // Clone DT buttons into modal after a brief delay
            setTimeout(function () {
                const $modalTools = $('#exportModalTools');
                $modalTools.empty();
                $('#exportTools .dt-buttons .dt-button, #exportTools .btn').each(function () {
                    const $clone = $(this).clone(true, true);
                    $clone.addClass('w-100').css({ 'margin': '0' });
                    $clone.on('click', function () {
                        const idx = $(this).index();
                        $('#exportTools .dt-buttons .dt-button, #exportTools .btn').eq(idx).trigger('click');
                        setTimeout(() => $('#exportModal').modal('hide'), 200);
                    });
                    $modalTools.append($clone);
                });
            }, 300);
        }
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
        $('#mobileExportTrigger').hide();
        $('#exportModalTools').empty();
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
            beforeSend: function (xhr) {
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