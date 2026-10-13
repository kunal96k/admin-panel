/**
 * WhatsApp API Audit Logs & Billing Reconciliation Management JS
 * Accessible strictly to SUPER_ADMIN.
 */

(function () {
    'use strict';

    // State
    let currentPage = 0;
    let pageSize = 25;
    let totalPages = 1;
    let totalElements = 0;

    // Elements
    const logsTableBody = document.getElementById('logsTableBody');
    const badgeTotalRecords = document.getElementById('badgeTotalRecords');
    const paginationInfo = document.getElementById('paginationInfo');
    const paginationUl = document.getElementById('paginationUl');
    const selectPageSize = document.getElementById('selectPageSize');

    const filterStartDate = document.getElementById('filterStartDate');
    const filterEndDate = document.getElementById('filterEndDate');
    const filterModule = document.getElementById('filterModule');
    const filterStatus = document.getElementById('filterStatus');
    const filterTrigger = document.getElementById('filterTrigger');
    const filterSearch = document.getElementById('filterSearch');
    const btnClearSearch = document.getElementById('btnClearSearch');

    const btnApplyFilters = document.getElementById('btnApplyFilters');
    const btnResetFilters = document.getElementById('btnResetFilters');
    const btnRefreshLogs = document.getElementById('btnRefreshLogs');
    const btnExportCsv = document.getElementById('btnExportCsv');

    // KPI Elements
    const kpiTotalCalls = document.getElementById('kpiTotalCalls');
    const kpiSuccessCount = document.getElementById('kpiSuccessCount');
    const kpiSuccessRate = document.getElementById('kpiSuccessRate');
    const kpiFailedCount = document.getElementById('kpiFailedCount');
    const kpiBlockedCount = document.getElementById('kpiBlockedCount');
    const kpiUtilityCount = document.getElementById('kpiUtilityCount');
    const kpiMarketingCount = document.getElementById('kpiMarketingCount');

    // Modal Details Elements
    const modalElement = document.getElementById('modalLogDetails');
    let logDetailsModal = null;
    if (modalElement && typeof bootstrap !== 'undefined') {
        logDetailsModal = new bootstrap.Modal(modalElement);
    }

    // Initialize
    document.addEventListener('DOMContentLoaded', function () {
        initDefaultDate();
        loadKpiStats();
        loadLogs(0);
        bindEvents();
    });

    function initDefaultDate() {
        // By default, set filter to This Month
        setQuickDateRange('this-month');
    }

    function bindEvents() {
        if (btnApplyFilters) {
            btnApplyFilters.addEventListener('click', function () {
                currentPage = 0;
                loadKpiStats();
                loadLogs(0);
            });
        }

        if (btnResetFilters) {
            btnResetFilters.addEventListener('click', function () {
                filterStartDate.value = '';
                filterEndDate.value = '';
                filterModule.value = '';
                filterStatus.value = '';
                filterTrigger.value = '';
                filterSearch.value = '';
                currentPage = 0;
                loadKpiStats();
                loadLogs(0);
            });
        }

        if (btnRefreshLogs) {
            btnRefreshLogs.addEventListener('click', function () {
                loadKpiStats();
                loadLogs(currentPage);
            });
        }

        if (selectPageSize) {
            selectPageSize.addEventListener('change', function () {
                pageSize = parseInt(this.value, 10) || 25;
                currentPage = 0;
                loadLogs(0);
            });
        }

        if (filterSearch) {
            filterSearch.addEventListener('keydown', function (e) {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    currentPage = 0;
                    loadKpiStats();
                    loadLogs(0);
                }
            });
        }

        if (btnClearSearch) {
            btnClearSearch.addEventListener('click', function () {
                filterSearch.value = '';
                currentPage = 0;
                loadKpiStats();
                loadLogs(0);
            });
        }

        // Quick date buttons
        document.querySelectorAll('.btn-quick-date').forEach(btn => {
            btn.addEventListener('click', function () {
                const range = this.getAttribute('data-range');
                setQuickDateRange(range);
                document.querySelectorAll('.btn-quick-date').forEach(b => b.classList.remove('active', 'btn-secondary'));
                this.classList.add('active');
                currentPage = 0;
                loadKpiStats();
                loadLogs(0);
            });
        });

        // Export CSV button
        if (btnExportCsv) {
            btnExportCsv.addEventListener('click', function () {
                exportCsv();
            });
        }

        // Copy JSON buttons in modal
        document.querySelectorAll('.copy-btn').forEach(btn => {
            btn.addEventListener('click', function () {
                const targetId = this.getAttribute('data-target');
                const targetElem = document.getElementById(targetId);
                if (targetElem && targetElem.innerText) {
                    navigator.clipboard.writeText(targetElem.innerText).then(() => {
                        const originalHtml = this.innerHTML;
                        this.innerHTML = '<i class="bi bi-check-lg text-success me-1"></i> Copied!';
                        setTimeout(() => {
                            this.innerHTML = originalHtml;
                        }, 2000);
                    }).catch(err => {
                        console.error('Failed to copy JSON:', err);
                    });
                }
            });
        });
    }

    function setQuickDateRange(range) {
        const now = new Date();
        const formatDate = (d) => {
            const year = d.getFullYear();
            const month = String(d.getMonth() + 1).padStart(2, '0');
            const day = String(d.getDate()).padStart(2, '0');
            return `${year}-${month}-${day}`;
        };

        if (range === 'today') {
            const todayStr = formatDate(now);
            filterStartDate.value = todayStr;
            filterEndDate.value = todayStr;
        } else if (range === 'yesterday') {
            const yesterday = new Date(now);
            yesterday.setDate(now.getDate() - 1);
            const str = formatDate(yesterday);
            filterStartDate.value = str;
            filterEndDate.value = str;
        } else if (range === 'this-month') {
            const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1);
            filterStartDate.value = formatDate(startOfMonth);
            filterEndDate.value = formatDate(now);
        } else if (range === 'last-month') {
            const startOfLastMonth = new Date(now.getFullYear(), now.getMonth() - 1, 1);
            const endOfLastMonth = new Date(now.getFullYear(), now.getMonth(), 0);
            filterStartDate.value = formatDate(startOfLastMonth);
            filterEndDate.value = formatDate(endOfLastMonth);
        } else if (range === 'all') {
            filterStartDate.value = '';
            filterEndDate.value = '';
        }
    }

    function buildFilterParams() {
        const params = new URLSearchParams();
        if (filterStartDate && filterStartDate.value) {
            params.append('startDate', filterStartDate.value);
        }
        if (filterEndDate && filterEndDate.value) {
            params.append('endDate', filterEndDate.value);
        }
        if (filterModule && filterModule.value) {
            params.append('moduleType', filterModule.value);
        }
        if (filterStatus && filterStatus.value) {
            params.append('status', filterStatus.value);
        }
        if (filterTrigger && filterTrigger.value) {
            params.append('triggerType', filterTrigger.value);
        }
        if (filterSearch && filterSearch.value.trim()) {
            params.append('search', filterSearch.value.trim());
        }
        return params;
    }

    /**
     * Load top KPI aggregate metrics
     */
    function loadKpiStats() {
        const params = new URLSearchParams();
        if (filterStartDate && filterStartDate.value) {
            params.append('startDate', filterStartDate.value);
        }
        if (filterEndDate && filterEndDate.value) {
            params.append('endDate', filterEndDate.value);
        }
        if (filterModule && filterModule.value) {
            params.append('moduleType', filterModule.value);
        }

        fetch('/api/whatsapp-logs/stats?' + params.toString())
            .then(res => {
                if (!res.ok) throw new Error('HTTP ' + res.status);
                return res.json();
            })
            .then(stats => {
                if (kpiTotalCalls) kpiTotalCalls.innerText = (stats.totalCalls || 0).toLocaleString();
                if (kpiSuccessCount) kpiSuccessCount.innerText = (stats.successCount || 0).toLocaleString();
                if (kpiFailedCount) kpiFailedCount.innerText = (stats.failedCount || 0).toLocaleString();
                if (kpiUtilityCount) kpiUtilityCount.innerText = (stats.utilityCount || 0).toLocaleString();
                if (kpiMarketingCount) kpiMarketingCount.innerText = (stats.marketingCount || 0).toLocaleString();

                if (kpiSuccessRate) {
                    kpiSuccessRate.innerText = `${(stats.successRate || 0).toFixed(1)}% success rate`;
                }
                if (kpiBlockedCount) {
                    kpiBlockedCount.innerText = `${stats.blockedCount || 0} blocked by config`;
                }
            })
            .catch(err => {
                console.warn('[KPI] Failed to load WhatsApp stats:', err);
            });
    }

    /**
     * Load paginated logs from API
     */
    function loadLogs(page) {
        currentPage = page;
        const params = buildFilterParams();
        params.append('page', page);
        params.append('size', pageSize);

        logsTableBody.innerHTML = `
            <tr>
                <td colspan="10" class="text-center py-5 text-muted">
                    <div class="spinner-border spinner-border-sm text-primary me-2" role="status"></div>
                    Loading WhatsApp API audit logs...
                </td>
            </tr>
        `;

        fetch('/api/whatsapp-logs?' + params.toString())
            .then(res => {
                if (res.status === 403) {
                    throw new Error('Access denied. Super Admin privileges required.');
                }
                if (!res.ok) {
                    throw new Error('Server returned status ' + res.status);
                }
                return res.json();
            })
            .then(data => {
                totalElements = data.totalElements || 0;
                totalPages = data.totalPages || 1;
                renderLogsTable(data.content || []);
                renderPagination(data);
            })
            .catch(err => {
                console.error('[LOGS] Error fetching audit logs:', err);
                logsTableBody.innerHTML = `
                    <tr>
                        <td colspan="10" class="text-center py-5 text-danger">
                            <i class="bi bi-exclamation-triangle-fill fs-4 d-block mb-2"></i>
                            ${escapeHtml(err.message || 'Failed to load logs')}
                        </td>
                    </tr>
                `;
            });
    }

    /**
     * Render data rows in table
     */
    function renderLogsTable(logs) {
        if (!logs || logs.length === 0) {
            logsTableBody.innerHTML = `
                <tr>
                    <td colspan="10" class="text-center py-5 text-muted">
                        <i class="bi bi-inbox fs-3 d-block mb-2 text-secondary"></i>
                        No WhatsApp API logs found matching the selected filters.
                    </td>
                </tr>
            `;
            if (badgeTotalRecords) badgeTotalRecords.innerText = '0 records';
            return;
        }

        if (badgeTotalRecords) badgeTotalRecords.innerText = `${totalElements.toLocaleString()} records`;

        let html = '';
        logs.forEach(log => {
            const statusBadge = getStatusBadge(log.status, log.failureReason);
            const categoryBadge = getCategoryBadge(log.billingCategory);
            const wamidSnippet = log.metaMessageId 
                ? `<span class="wamid-code" title="${escapeHtml(log.metaMessageId)}">${escapeHtml(log.metaMessageId)}</span>`
                : `<span class="text-muted small">-</span>`;

            const triggerSourceIcon = log.triggerType === 'AUTOMATED_SCHEDULER'
                ? '<i class="bi bi-robot text-primary me-1" title="Automated Scheduler"></i>'
                : '<i class="bi bi-person text-secondary me-1" title="Manual User Action"></i>';

            const userDisplay = log.triggeredByEmployeeName || log.triggeredByUsername || 'System';

            html += `
                <tr>
                    <td class="font-monospace text-muted small">#${log.id}</td>
                    <td class="small text-nowrap">${escapeHtml(log.createdAt || '-')}</td>
                    <td>
                        <div class="fw-semibold small">${escapeHtml(formatModuleName(log.moduleType))}</div>
                        <div class="text-muted font-monospace fs-xs" style="font-size: 0.76rem;">${escapeHtml(log.templateName || '-')}</div>
                    </td>
                    <td>${categoryBadge}</td>
                    <td class="small">
                        <div class="d-flex align-items-center">
                            ${triggerSourceIcon}
                            <span class="text-truncate" style="max-width: 120px;" title="${escapeHtml(userDisplay)}">
                                ${escapeHtml(userDisplay)}
                            </span>
                        </div>
                    </td>
                    <td>
                        <div class="fw-semibold small">${escapeHtml(log.studentName || '-')}</div>
                        <div class="text-muted small font-monospace">${escapeHtml(log.recipientMobile || '-')}</div>
                    </td>
                    <td class="small font-monospace">${escapeHtml(log.referenceNumber || '-')}</td>
                    <td>${wamidSnippet}</td>
                    <td>${statusBadge}</td>
                    <td class="text-center">
                        <button type="button" class="btn btn-sm btn-outline-primary py-0 px-2 btn-view-log" 
                                data-id="${log.id}" title="Inspect API Call & Raw Payloads">
                            <i class="bi bi-eye"></i>
                        </button>
                    </td>
                </tr>
            `;
        });

        logsTableBody.innerHTML = html;

        // Bind inspect buttons
        document.querySelectorAll('.btn-view-log').forEach(btn => {
            btn.addEventListener('click', function () {
                const id = this.getAttribute('data-id');
                openDetailsModal(id);
            });
        });
    }

    function formatModuleName(moduleType) {
        if (!moduleType) return 'Unknown';
        if (moduleType === 'COURSE_INFORMATION') return 'Course Information';
        if (moduleType === 'FEE_REMINDER') return 'Fee Due Reminder';
        if (moduleType === 'TEST') return 'API Test';
        return moduleType;
    }

    function getCategoryBadge(cat) {
        if (!cat) return '<span class="badge bg-secondary">-</span>';
        if (cat.toUpperCase() === 'UTILITY') {
            return `<span class="badge badge-utility"><i class="bi bi-wallet2 me-1"></i>UTILITY</span>`;
        }
        if (cat.toUpperCase() === 'MARKETING') {
            return `<span class="badge badge-marketing"><i class="bi bi-megaphone me-1"></i>MARKETING</span>`;
        }
        return `<span class="badge bg-light text-dark border">${escapeHtml(cat)}</span>`;
    }

    function getStatusBadge(status, reason) {
        if (!status) return '<span class="badge bg-secondary">-</span>';
        if (status.toUpperCase() === 'SUCCESS') {
            return `<span class="badge badge-status-success"><i class="bi bi-check2 me-1"></i>SUCCESS</span>`;
        }
        if (status.toUpperCase() === 'FAILED') {
            const title = reason ? `title="${escapeHtml(reason)}"` : '';
            return `<span class="badge badge-status-failed" ${title}><i class="bi bi-x-circle me-1"></i>FAILED</span>`;
        }
        if (status.toUpperCase() === 'BLOCKED') {
            const title = reason ? `title="${escapeHtml(reason)}"` : '';
            return `<span class="badge badge-status-blocked" ${title}><i class="bi bi-slash-circle me-1"></i>BLOCKED</span>`;
        }
        return `<span class="badge bg-secondary">${escapeHtml(status)}</span>`;
    }

    /**
     * Render pagination controls
     */
    function renderPagination(data) {
        const total = data.totalElements || 0;
        const page = data.number || 0;
        const size = data.size || 25;
        const start = total === 0 ? 0 : (page * size) + 1;
        const end = Math.min((page + 1) * size, total);

        if (paginationInfo) {
            paginationInfo.innerText = `Showing ${start} to ${end} of ${total.toLocaleString()} entries`;
        }

        if (!paginationUl) return;
        paginationUl.innerHTML = '';

        if (totalPages <= 1) return;

        // Previous button
        const prevLi = document.createElement('li');
        prevLi.className = `page-item ${page === 0 ? 'disabled' : ''}`;
        prevLi.innerHTML = `<a class="page-link" href="#" aria-label="Previous">&laquo;</a>`;
        prevLi.addEventListener('click', function (e) {
            e.preventDefault();
            if (page > 0) loadLogs(page - 1);
        });
        paginationUl.appendChild(prevLi);

        // Page number windows
        const maxPagesToShow = 5;
        let startPage = Math.max(0, page - 2);
        let endPage = Math.min(totalPages - 1, startPage + maxPagesToShow - 1);
        if (endPage - startPage < maxPagesToShow - 1) {
            startPage = Math.max(0, endPage - maxPagesToShow + 1);
        }

        for (let i = startPage; i <= endPage; i++) {
            const li = document.createElement('li');
            li.className = `page-item ${i === page ? 'active' : ''}`;
            li.innerHTML = `<a class="page-link" href="#">${i + 1}</a>`;
            const targetPage = i;
            li.addEventListener('click', function (e) {
                e.preventDefault();
                if (targetPage !== page) loadLogs(targetPage);
            });
            paginationUl.appendChild(li);
        }

        // Next button
        const nextLi = document.createElement('li');
        nextLi.className = `page-item ${page >= totalPages - 1 ? 'disabled' : ''}`;
        nextLi.innerHTML = `<a class="page-link" href="#" aria-label="Next">&raquo;</a>`;
        nextLi.addEventListener('click', function (e) {
            e.preventDefault();
            if (page < totalPages - 1) loadLogs(page + 1);
        });
        paginationUl.appendChild(nextLi);
    }

    /**
     * Open details inspection modal
     */
    function openDetailsModal(id) {
        if (!id) return;

        // Reset fields
        document.getElementById('detailLogId').innerText = id;
        document.getElementById('detailCreatedAt').innerText = 'Loading...';
        document.getElementById('detailModuleType').innerText = '-';
        document.getElementById('detailTemplateName').innerText = '-';
        document.getElementById('detailBillingCategory').innerText = '-';
        document.getElementById('detailTriggerType').innerText = '-';
        document.getElementById('detailTriggeredBy').innerText = '-';
        document.getElementById('detailStudentName').innerText = '-';
        document.getElementById('detailRecipientMobile').innerText = '-';
        document.getElementById('detailReferenceNumber').innerText = '-';
        document.getElementById('detailCourseName').innerText = '-';
        document.getElementById('detailAttachmentName').innerText = '-';
        document.getElementById('detailRequestPayload').innerText = '// Loading...';
        document.getElementById('detailResponsePayload').innerText = '// Loading...';

        const statusBanner = document.getElementById('detailStatusBanner');
        statusBanner.className = 'p-3 rounded-3 mb-4 border bg-light';

        if (logDetailsModal) {
            logDetailsModal.show();
        }

        fetch('/api/whatsapp-logs/' + id)
            .then(res => {
                if (!res.ok) throw new Error('HTTP ' + res.status);
                return res.json();
            })
            .then(log => {
                document.getElementById('detailCreatedAt').innerText = log.createdAt ? log.createdAt.replace('T', ' ') : '-';
                document.getElementById('detailModuleType').innerText = formatModuleName(log.moduleType);
                document.getElementById('detailTemplateName').innerText = log.templateName || '-';
                document.getElementById('detailBillingCategory').innerText = log.billingCategory || '-';
                document.getElementById('detailTriggerType').innerText = log.triggerType || '-';
                document.getElementById('detailTriggeredBy').innerText = `${log.triggeredByEmployeeName || ''} (${log.triggeredByUsername || 'System'})`;

                document.getElementById('detailStudentName').innerText = log.studentName || '-';
                document.getElementById('detailRecipientMobile').innerText = log.recipientMobile || '-';
                document.getElementById('detailReferenceNumber').innerText = log.referenceNumber || (log.referenceId ? '#' + log.referenceId : '-');
                document.getElementById('detailCourseName').innerText = log.courseName || '-';
                document.getElementById('detailAttachmentName').innerText = log.attachmentName || 'None';

                // Status banner
                const statusText = document.getElementById('detailStatusText');
                const httpCode = document.getElementById('detailHttpCode');
                const metaMessageId = document.getElementById('detailMetaMessageId');
                const failureReason = document.getElementById('detailFailureReason');

                statusText.innerText = log.status || 'UNKNOWN';
                httpCode.innerText = 'HTTP ' + (log.httpStatusCode || '-');
                metaMessageId.innerText = 'Meta Message ID: ' + (log.metaMessageId || 'N/A');

                if (log.status === 'SUCCESS') {
                    statusBanner.className = 'p-3 rounded-3 mb-4 border bg-success-subtle text-success';
                    failureReason.style.display = 'none';
                } else if (log.status === 'BLOCKED') {
                    statusBanner.className = 'p-3 rounded-3 mb-4 border bg-warning-subtle text-warning-emphasis';
                    failureReason.style.display = 'block';
                    failureReason.innerText = 'Blocked Reason: ' + (log.failureReason || 'Disabled by Super Admin configuration');
                } else {
                    statusBanner.className = 'p-3 rounded-3 mb-4 border bg-danger-subtle text-danger';
                    failureReason.style.display = 'block';
                    failureReason.innerText = 'Failure Reason: ' + (log.failureReason || 'Unknown error');
                }

                // Payloads formatting
                document.getElementById('detailRequestPayload').innerText = formatJsonString(log.requestPayload);
                document.getElementById('detailResponsePayload').innerText = formatJsonString(log.responsePayload);
            })
            .catch(err => {
                console.error('[DETAILS] Failed to load log details:', err);
                document.getElementById('detailCreatedAt').innerText = 'Error loading log details';
            });
    }

    function formatJsonString(str) {
        if (!str || !str.trim()) return '// No payload recorded';
        try {
            const obj = JSON.parse(str);
            return JSON.stringify(obj, null, 2);
        } catch (e) {
            return str;
        }
    }

    /**
     * Export filtered logs to CSV
     */
    function exportCsv() {
        const params = buildFilterParams();
        const exportUrl = '/api/whatsapp-logs/export/csv?' + params.toString();

        if (typeof Swal !== 'undefined') {
            Swal.fire({
                title: 'Export WhatsApp Audit Logs?',
                text: 'A reconciliation CSV containing all matching records will be downloaded.',
                icon: 'question',
                showCancelButton: true,
                confirmButtonText: '<i class="bi bi-download me-1"></i> Download CSV',
                cancelButtonText: 'Cancel',
                confirmButtonColor: '#128C7E'
            }).then((res) => {
                if (res.isConfirmed) {
                    window.location.href = exportUrl;
                }
            });
        } else {
            window.location.href = exportUrl;
        }
    }

    function escapeHtml(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

})();
