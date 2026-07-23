// admission.js

(function () {
    'use strict';

    let currentTab = 1;
    const totalTabs = 6;
    let admissionData = {};
    let importedAdmissions = [];
    let videoStream = null;

    let selectedCourses = [];

    let allCoursesForFilter = [];

    let currentSearchTerm = '';
    let currentSearchField = 'ALL';
    let currentStudentCategory = '';
    let currentCourseFilter = '';
    let currentFromDate = '';
    let currentToDate = '';

    let currentPage = 0;
    let pageSize = 25;
    let totalPages = 0;
    let totalElements = 0;
    let exportDataTable = null;

    // ── Filter Badge Counter ──
    function updateFilterBadge() {
        let count = 0;
        if (document.getElementById('courseFilter')?.value) count++;
        if (document.getElementById('categoryFilter')?.value) count++;
        if (document.getElementById('fromDateFilter')?.value) count++;
        if (document.getElementById('toDateFilter')?.value) count++;

        const badge = document.getElementById('filterBadge');
        const btn = document.getElementById('btnToggleFilters');
        if (badge) {
            badge.textContent = count;
            badge.classList.toggle('d-none', count === 0);
        }
        if (btn) {
            btn.classList.toggle('btn-outline-secondary', count === 0);
            btn.classList.toggle('btn-primary', count > 0);
        }
    }

    function getCsrfToken() {
        // Try cookie first (for XSRF-TOKEN)
        const cookieValue = document.cookie
            .split('; ')
            .find(row => row.startsWith('XSRF-TOKEN='))
            ?.split('=')[1];

        if (cookieValue) return cookieValue;

        // Fallback to meta tag
        const metaTag = document.querySelector('meta[name="_csrf"]');
        return metaTag ? metaTag.getAttribute('content') : null;
    }

    function getCsrfHeader() {
        return 'X-CSRF-TOKEN';
    }


    // Initialize on page load
    document.addEventListener('DOMContentLoaded', async function () {
        populateAcademicYears();
        initializeEventListeners();
        loadAdmissions();
        await loadDropdownData();
        await loadCoursesForFilter();
        initCourseTypeahead();    // init typeahead chip widget after courses are loaded
        checkEnquiryPreFill();
        initializeCourseSearch();
    });

    function initializeEventListeners() {
        // Main buttons
        document.getElementById('btnNewAdmission')?.addEventListener('click', openNewAdmissionModal);
        document.getElementById('btnImportAdmissions')?.addEventListener('click', openImportModal);

        // Modal navigation -  : Use proper function names
        document.getElementById('btnNext')?.addEventListener('click', navigateNext);
        document.getElementById('btnPrevious')?.addEventListener('click', navigatePrevious);
        document.getElementById('btnFinish')?.addEventListener('click', saveAdmission);

        // Tab switching
        document.querySelectorAll('#admissionTabs .nav-link').forEach((tab, index) => {
            tab.addEventListener('shown.bs.tab', function () {
                currentTab = index + 1;
                updateNavigationButtons();
                const progress = this.getAttribute('data-progress');
                updateProgress(progress);
            });
        });

        const pageSizeSelect = document.getElementById('pageSizeSelect');
        if (pageSizeSelect) {
            pageSizeSelect.addEventListener('change', function (e) {
                const newSize = parseInt(e.target.value);
                if (!isNaN(newSize) && newSize > 0) {
                    pageSize = newSize;
                    currentPage = 0; // Reset to first page
                    loadAdmissions(0, pageSize);
                }
            });
        }

        // Import functionality
        document.getElementById('btnBrowseAdmFile')?.addEventListener('click', () => {
            document.getElementById('admCsvFileInput').click();
        });

        document.getElementById('admCsvFileInput')?.addEventListener('change', function (e) {
            handleCSVFile(e.target.files[0]);
        });

        document.getElementById('btnImportAdmData')?.addEventListener('click', importAdmissions);

        // Import type change
        document.querySelectorAll('input[name="admImportType"]').forEach(radio => {
            radio.addEventListener('change', handleImportTypeChange);
        });

        // Camera & Photo
        document.getElementById('btnCapturePhoto')?.addEventListener('click', capturePhoto);
        document.getElementById('admPhotoUpload')?.addEventListener('change', function (e) {
            handlePhotoUpload(e.target.files[0]);
        });

        // Generate installments
        document.getElementById('btnGenerateInstallments')?.addEventListener('click', generateInstallments);

        //  TAB 5: Date/Days auto-calculator 
        document.getElementById('instStartDate')?.addEventListener('change', calcInstDaysFromDates);
        document.getElementById('instSecondDate')?.addEventListener('change', calcInstDaysFromDates);
        document.getElementById('instDays')?.addEventListener('input', calcInstSecondDateFromDays);

        //  Fee Modal: Date/Days auto-calculator 
        document.getElementById('feeInstStartDate')?.addEventListener('change', calcFeeInstDaysFromDates);
        document.getElementById('feeInstSecondDate')?.addEventListener('change', calcFeeInstDaysFromDates);
        document.getElementById('feeInstDays')?.addEventListener('input', calcFeeInstSecondDateFromDays);

        //  Add Installment Row buttons 
        document.getElementById('btnAddInstallment')?.addEventListener('click', addInstallmentRow);
        document.getElementById('btnFeeAddInstallment')?.addEventListener('click', addFeeInstallmentRow);

        // Search
        document.getElementById('searchInput')?.addEventListener('input', debounce(searchAdmissions, 800));
        document.getElementById('searchFieldSelect')?.addEventListener('change', searchAdmissions);

        // Student Status Filter
        document.getElementById('categoryFilter')?.addEventListener('change', () => { searchAdmissions(); updateFilterBadge(); });

        // Course Filter
        document.getElementById('courseFilter')?.addEventListener('change', () => { searchAdmissions(); updateFilterBadge(); });
        document.getElementById('courseSearchInput')?.addEventListener('input', filterCourseList);

        // Date Filters
        document.getElementById('fromDateFilter')?.addEventListener('change', () => { searchAdmissions(); updateFilterBadge(); });
        document.getElementById('toDateFilter')?.addEventListener('change', () => { searchAdmissions(); updateFilterBadge(); });

        // Clear Filters
        document.getElementById('btnClearFilters')?.addEventListener('click', () => { clearAllFilters(); updateFilterBadge(); });

        // ── Filter Panel Toggle (JS-controlled, no data-bs-toggle to avoid stuck state) ──
        (function initFilterToggle() {
            const btn = document.getElementById('btnToggleFilters');
            const panel = document.getElementById('advancedFiltersPanel');
            if (!btn || !panel) return;

            let isAnimating = false;
            const bsCollapse = new bootstrap.Collapse(panel, { toggle: false });

            // Sync button visual state after animation ends
            panel.addEventListener('shown.bs.collapse', () => { isAnimating = false; btn.setAttribute('aria-expanded', 'true'); btn.classList.add('filter-btn-open'); });
            panel.addEventListener('hidden.bs.collapse', () => { isAnimating = false; btn.setAttribute('aria-expanded', 'false'); btn.classList.remove('filter-btn-open'); });
            panel.addEventListener('show.bs.collapse', () => { isAnimating = true; });
            panel.addEventListener('hide.bs.collapse', () => { isAnimating = true; });

            btn.addEventListener('click', () => {
                if (isAnimating) return;   // ignore rapid double-clicks while animating
                bsCollapse.toggle();
            });
        })();

        // Package selection
        document.getElementById('admPackage')?.addEventListener('change', handlePackageChange);

        // Course selection
        document.getElementById('admCourse')?.addEventListener('change', handleCourseAdd);

        // Discount calculations
        document.getElementById('admDiscountPercent')?.addEventListener('input', calculateDiscount);
        document.getElementById('admDiscountAmount')?.addEventListener('input', calculateDiscountFromAmount);
    }

    //  Date/Days calculators for TAB 5 
    function calcInstDaysFromDates() {
        const start = document.getElementById('instStartDate')?.value;
        const second = document.getElementById('instSecondDate')?.value;
        if (start && second) {
            const d1 = new Date(start);
            const d2 = new Date(second);
            const diff = Math.round((d2 - d1) / (1000 * 60 * 60 * 24));
            if (diff > 0) {
                const daysEl = document.getElementById('instDays');
                if (daysEl) daysEl.value = diff;
            }
        }
    }
    function calcInstSecondDateFromDays() {
        const start = document.getElementById('instStartDate')?.value;
        const days = parseInt(document.getElementById('instDays')?.value);
        if (start && days > 0) {
            const d = new Date(start);
            d.setDate(d.getDate() + days);
            const secondEl = document.getElementById('instSecondDate');
            if (secondEl) secondEl.value = d.toISOString().split('T')[0];
        }
    }

    //  Date/Days calculators for Fee Modal 
    function calcFeeInstDaysFromDates() {
        const start = document.getElementById('feeInstStartDate')?.value;
        const second = document.getElementById('feeInstSecondDate')?.value;
        if (start && second) {
            const d1 = new Date(start);
            const d2 = new Date(second);
            const diff = Math.round((d2 - d1) / (1000 * 60 * 60 * 24));
            if (diff > 0) {
                const daysEl = document.getElementById('feeInstDays');
                if (daysEl) daysEl.value = diff;
            }
        }
    }
    function calcFeeInstSecondDateFromDays() {
        const start = document.getElementById('feeInstStartDate')?.value;
        const days = parseInt(document.getElementById('feeInstDays')?.value);
        if (start && days > 0) {
            const d = new Date(start);
            d.setDate(d.getDate() + days);
            const secondEl = document.getElementById('feeInstSecondDate');
            if (secondEl) secondEl.value = d.toISOString().split('T')[0];
        }
    }

    //  Recalculate installment row total (Tab 5) 
    function recalculateInstallmentTotal() {
        const tbody = document.getElementById('installmentsBody');
        if (!tbody) return;
        let sum = 0;
        tbody.querySelectorAll('input[type="number"]').forEach(inp => {
            sum += parseFloat(inp.value) || 0;
        });
        const totalEl = document.getElementById('instRowTotal');
        const instTotalEl = document.getElementById('instTotalInstAmount');
        if (totalEl) totalEl.textContent = '₹' + sum.toFixed(2);
        if (instTotalEl) instTotalEl.value = sum.toFixed(2);
    }

    //  Recalculate fee installment row total (Fee Modal) 
    function recalculateFeeInstallmentTotal() {
        const tbody = document.getElementById('feeInstallmentsBody');
        if (!tbody) return;
        let sum = 0;
        tbody.querySelectorAll('input[type="number"]').forEach(inp => {
            sum += parseFloat(inp.value) || 0;
        });
        const totalEl = document.getElementById('feeInstRowTotal');
        const instTotalEl = document.getElementById('feeInstTotalInstAmount');
        if (totalEl) totalEl.textContent = '₹' + sum.toFixed(2);
        if (instTotalEl) instTotalEl.value = sum.toFixed(2);
    }

    //  Build a new editable installment row for Tab 5 
    function buildInstallmentRow(date, amount, status, rowIndex) {
        const tr = document.createElement('tr');
        tr.innerHTML = `
           <td data-label="Date"><input type="date" class="form-control form-control-sm inst-date" value="${date || ''}"></td>
           <td data-label="Amount (₹)"><input type="number" class="form-control form-control-sm inst-amount" value="${amount || ''}" step="0.01" min="0" placeholder="0.00"></td>
           <td data-label="Status">
               <select class="form-select form-select-sm inst-status">
                   <option value="Pending"${status === 'Pending' ? ' selected' : ''}>Pending</option>
                   <option value="Paid"${status === 'Paid' ? ' selected' : ''}>Paid</option>
                   <option value="Overdue"${status === 'Overdue' ? ' selected' : ''}>Overdue</option>
                   <option value="Waived"${status === 'Waived' ? ' selected' : ''}>Waived</option>
               </select>
           </td>
           <td data-label="Remove" class="text-center">
               <button type="button" class="btn btn-sm btn-outline-danger" onclick="window.removeInstallmentRow(this)">
                   <i class="bi bi-trash"></i>
               </button>
           </td>
       `;
        tr.querySelector('.inst-amount').addEventListener('input', recalculateInstallmentTotal);
        return tr;
    }

    //  Add Installment Row (Tab 5) 
    function addInstallmentRow() {
        const tbody = document.getElementById('installmentsBody');
        if (!tbody) return;
        // Remove the "no installments" placeholder row if present
        const placeholder = tbody.querySelector('tr td[colspan]');
        if (placeholder) placeholder.closest('tr').remove();
        tbody.appendChild(buildInstallmentRow('', '', 'Pending'));
        recalculateInstallmentTotal();
    }

    window.removeInstallmentRow = function (btn) {
        btn.closest('tr').remove();
        recalculateInstallmentTotal();
        const tbody = document.getElementById('installmentsBody');
        if (tbody && tbody.querySelectorAll('tr').length === 0) {
            tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted">No installments generated</td></tr>';
            const totalEl = document.getElementById('instRowTotal');
            const instTotalEl = document.getElementById('instTotalInstAmount');
            if (totalEl) totalEl.textContent = '₹0.00';
            if (instTotalEl) instTotalEl.value = '0';
        }
    };

    // ── Build a new editable fee installment row for Fee Modal ──
    function buildFeeInstallmentRow(date, amount, status, notes, instId, createdBy, createdAt, updatedBy, updatedAt) {
        const tr = document.createElement('tr');
        tr.dataset.installmentId = instId || '';

        const createdTimeStr = createdAt ? formatDisplayDateTime(createdAt) : 'N/A';
        const updatedTimeStr = updatedAt ? formatDisplayDateTime(updatedAt) : 'N/A';

        tr.dataset.createdBy = createdBy || 'SYSTEM';
        tr.dataset.createdTime = createdTimeStr;
        tr.dataset.updatedBy = updatedBy || '-';
        tr.dataset.updatedTime = updatedTimeStr;
        tr.style.cursor = 'pointer';

        tr.innerHTML = `
            <td data-label="DATE"><input type="date" class="form-control form-control-sm fee-inst-date" value="${date || ''}"></td>
            <td data-label="AMOUNT (₹)"><input type="number" class="form-control form-control-sm fee-inst-amount" value="${amount || ''}" step="0.01" min="0" placeholder="0.00"></td>
            <td data-label="STATUS">
                <select class="form-select form-select-sm fee-inst-status">
                    <option value="Pending"${status === 'Pending' ? ' selected' : ''}>Pending</option>
                    <option value="Paid"${status === 'Paid' ? ' selected' : ''}>Paid</option>
                    <option value="Partial"${status === 'Partial' ? ' selected' : ''}>Partial</option>
                    <option value="Overdue"${status === 'Overdue' ? ' selected' : ''}>Overdue</option>
                    <option value="Waived"${status === 'Waived' ? ' selected' : ''}>Waived</option>
                </select>
            </td>
            <td data-label="NOTES"><input type="text" class="form-control form-control-sm fee-inst-notes" value="${(notes || '').replace(/"/g, '&quot;')}" placeholder="Notes..."></td>
            <td data-label="ACTION" class="text-center">
                <button type="button" class="btn btn-sm btn-outline-danger" onclick="window.removeFeeInstallmentRow(this)">
                    <i class="bi bi-trash"></i>
                </button>
            </td>
        `;
        tr.querySelector('.fee-inst-amount').addEventListener('input', recalculateFeeInstallmentTotal);
        return tr;
    }

    // ── Setup Fee Installments Audit Logic ──
    function setupFeeInstallmentsAuditLogic() {
        const userRole = document.getElementById('currentUserRole')?.value;
        const isSuperAdmin = userRole && (userRole.toUpperCase().replace(/\s+|_/g, '') === 'SUPERADMIN');
        const auditSection = document.getElementById('feeInstAuditSection');

        if (auditSection) {
            auditSection.style.display = isSuperAdmin ? 'block' : 'none';
        }

        // Reset details
        if (document.getElementById('feeInstCreatedBy')) document.getElementById('feeInstCreatedBy').textContent = '-';
        if (document.getElementById('feeInstCreatedTime')) document.getElementById('feeInstCreatedTime').textContent = '-';
        if (document.getElementById('feeInstUpdatedBy')) document.getElementById('feeInstUpdatedBy').textContent = '-';
        if (document.getElementById('feeInstUpdatedTime')) document.getElementById('feeInstUpdatedTime').textContent = '-';

        if (!isSuperAdmin) return;

        const tbody = document.getElementById('feeInstallmentsBody');
        if (!tbody) return;

        const rows = tbody.querySelectorAll('tr');
        if (rows.length === 0 || (rows.length === 1 && rows[0].cells.length === 1)) {
            return;
        }

        rows.forEach(row => {
            row.addEventListener('click', function (e) {
                if (e.target.closest('button') || e.target.closest('input') || e.target.closest('select')) {
                    return;
                }
                rows.forEach(r => r.classList.remove('table-active'));
                this.classList.add('table-active');

                document.getElementById('feeInstCreatedBy').textContent = this.dataset.createdBy || '-';
                document.getElementById('feeInstCreatedTime').textContent = this.dataset.createdTime || '-';
                document.getElementById('feeInstUpdatedBy').textContent = this.dataset.updatedBy || '-';
                document.getElementById('feeInstUpdatedTime').textContent = this.dataset.updatedTime || '-';
            });
        });

        // Auto click/select the first row
        if (rows.length > 0) {
            const firstRow = rows[0];
            firstRow.classList.add('table-active');
            document.getElementById('feeInstCreatedBy').textContent = firstRow.dataset.createdBy || '-';
            document.getElementById('feeInstCreatedTime').textContent = firstRow.dataset.createdTime || '-';
            document.getElementById('feeInstUpdatedBy').textContent = firstRow.dataset.updatedBy || '-';
            document.getElementById('feeInstUpdatedTime').textContent = firstRow.dataset.updatedTime || '-';
        }
    }

    // ── Add Fee Installment Row (Fee Modal) ──
    function addFeeInstallmentRow() {
        const tbody = document.getElementById('feeInstallmentsBody');
        if (!tbody) return;
        const placeholder = tbody.querySelector('tr td[colspan]');
        if (placeholder) placeholder.closest('tr').remove();
        tbody.appendChild(buildFeeInstallmentRow('', '', 'Pending', ''));
        recalculateFeeInstallmentTotal();
        setupFeeInstallmentsAuditLogic();
    }

    window.removeFeeInstallmentRow = function (btn) {
        btn.closest('tr').remove();
        recalculateFeeInstallmentTotal();
        const tbody = document.getElementById('feeInstallmentsBody');
        if (tbody && tbody.querySelectorAll('tr').length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted">No installments found</td></tr>';
            const totalEl = document.getElementById('feeInstRowTotal');
            const instTotalEl = document.getElementById('feeInstTotalInstAmount');
            if (totalEl) totalEl.textContent = '₹0.00';
            if (instTotalEl) instTotalEl.value = '0';
        }
        setupFeeInstallmentsAuditLogic();
    };

    function navigateNext() {
        if (currentTab < totalTabs) {
            currentTab++;
            showTab(currentTab);
            updateNavigationButtons();
        }
    }

    function navigatePrevious() {
        if (currentTab > 1) {
            currentTab--;
            showTab(currentTab);
            updateNavigationButtons();
        }
    }

    function updateProgress(width) {
        const bar = document.getElementById('admissionProgressBar');
        if (bar) bar.style.width = width + '%';
    }

    // Check for enquiry pre-fill
    function checkEnquiryPreFill() {
        const admissionEnquiry = sessionStorage.getItem('admissionEnquiry');
        const fromEnquiry = sessionStorage.getItem('admissionFromEnquiry');

        if (admissionEnquiry && fromEnquiry === 'true') {
            try {
                const enquiry = JSON.parse(admissionEnquiry);

                // Clear session storage first
                sessionStorage.removeItem('admissionEnquiry');
                sessionStorage.removeItem('admissionFromEnquiry');

                // Open modal
                const modal = new bootstrap.Modal(document.getElementById('admissionModal'));
                modal.show();

                setTimeout(() => {
                    prefillAdmissionForm(enquiry);

                    Swal.fire({
                        title: 'Form Pre-filled',
                        text: 'Student details loaded from enquiry. Please review and complete remaining fields.',
                        icon: 'success',
                        timer: 3000,
                        showConfirmButton: false,
                        toast: true,
                        position: 'top-end'
                    });
                }, 500);

            } catch (error) {
                console.error('Error pre-filling form:', error);
            }
        }
    }

    async function loadAdmissions(page = 0, size = 25) {
        try {
            showLoading('Loading admissions...');

            const hasSearch = !!(currentSearchTerm && currentSearchTerm.trim());
            const hasStudentCategory = !!(currentStudentCategory && currentStudentCategory.trim());
            const hasCourse = !!(currentCourseFilter && currentCourseFilter.trim());
            const hasFromDate = !!(currentFromDate && currentFromDate.trim());
            const hasToDate = !!(currentToDate && currentToDate.trim());

            let response;

            if (hasSearch || hasStudentCategory || hasCourse || hasFromDate || hasToDate) {
                const csrfToken = getCsrfToken();
                const headers = {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                };
                if (csrfToken) headers[getCsrfHeader()] = csrfToken;

                const searchDTO = {
                    searchTerm: (currentSearchTerm || '').trim() || null,
                    searchField: (currentSearchField || 'ALL').trim(),
                    studentCategory: (currentStudentCategory || '').trim() || null,
                    course: (currentCourseFilter || '').trim() || null,
                    admissionDateFrom: (currentFromDate || '').trim() || null,
                    admissionDateTo: (currentToDate || '').trim() || null,
                    page: page,
                    size: size,
                    sortBy: 'admissionDate',
                    sortDirection: 'DESC'
                };

                response = await fetch('/api/admissions/search', {
                    method: 'POST',
                    headers: headers,
                    credentials: 'include',
                    body: JSON.stringify(searchDTO)
                });
            } else {
                // Sort by admission_date DESC (latest first)
                response = await fetch(`/api/admissions?page=${page}&size=${size}&sort=admission_date,desc`, {
                    method: 'GET',
                    headers: {
                        'Accept': 'application/json',
                        'Content-Type': 'application/json'
                    }
                });
            }

            if (!response.ok) {
                throw new Error('Failed to load admissions');
            }

            const data = await response.json();

            currentPage = data.number || 0;
            pageSize = data.size || 25;
            totalPages = data.totalPages || 0;
            totalElements = data.totalElements || 0;

            Swal.close();
            renderAdmissionsTable(data.content || []);
            updatePaginationInfo();
            renderPaginationControls();

        } catch (error) {
            Swal.close();
            console.error('Error loading admissions:', error);
            renderAdmissionsTable([]);
            showError('Failed to load admissions');
        }
    }

    // ==================== COURSE FILTER (SERVER-SIDE SEARCH INPUTS) ====================

    async function loadCoursesForFilter() {
        try {
            const response = await fetch('/api/courses/dropdown');
            if (response.ok) {
                allCoursesForFilter = await response.json();
                populateCourseFilter(allCoursesForFilter);
            }
        } catch (error) {
        }
    }

    /* ── Course Typeahead Chip Widget ── */
    let selectedCourseChips = [];   // array of course name strings currently selected

    function populateCourseFilter(courses) {
        // Keep hidden select in sync (used by searchAdmissions as single value — take first chip)
        const select = document.getElementById('courseFilter');
        if (!select) return;
        select.innerHTML = '<option value=""></option>';
        (courses || []).forEach(c => {
            const opt = document.createElement('option');
            opt.value = c.courseName;
            opt.textContent = c.courseName;
            select.appendChild(opt);
        });
    }

    function syncCourseFilterSelect() {
        // Set hidden select to first selected chip (backend currently accepts single value)
        const select = document.getElementById('courseFilter');
        if (!select) return;
        const val = selectedCourseChips[0] || '';
        select.value = val;
        // fire change so searchAdmissions picks it up
        select.dispatchEvent(new Event('change', { bubbles: true }));
    }

    function renderCourseChips() {
        const wrap = document.getElementById('courseChipsWrap');
        const input = document.getElementById('courseSearchInput');
        if (!wrap || !input) return;
        // Remove existing chips (not the input)
        wrap.querySelectorAll('.course-chip').forEach(el => el.remove());
        selectedCourseChips.forEach(name => {
            const chip = document.createElement('span');
            chip.className = 'course-chip';
            chip.innerHTML = `<span class="course-chip-name" title="${name}">${name}</span>
            <button type="button" class="course-chip-close" aria-label="Remove ${name}" data-course="${name}">
                <i class="bi bi-x"></i>
            </button>`;
            chip.querySelector('.course-chip-close').addEventListener('click', (e) => {
                e.stopPropagation();
                selectedCourseChips = selectedCourseChips.filter(n => n !== name);
                renderCourseChips();
                syncCourseFilterSelect();
                updateFilterBadge();
            });
            wrap.insertBefore(chip, input);
        });
    }

    function buildCourseDropdown(term) {
        const dd = document.getElementById('courseDropdown');
        if (!dd) return;
        const filtered = (allCoursesForFilter || []).filter(c =>
            String(c.courseName || '').toLowerCase().includes((term || '').toLowerCase().trim())
        );
        dd.innerHTML = '';
        if (filtered.length === 0) {
            dd.innerHTML = '<li class="dd-empty">No courses found</li>';
        } else {
            filtered.forEach(c => {
                const isSelected = selectedCourseChips.includes(c.courseName);
                const li = document.createElement('li');
                li.setAttribute('role', 'option');
                li.setAttribute('data-course', c.courseName);
                if (isSelected) li.classList.add('already-selected');
                li.innerHTML = `<i class="bi bi-mortarboard" style="font-size:0.78rem;color:#94a3b8;"></i>
                <span>${c.courseName}</span>
                ${isSelected ? '<i class="bi bi-check2 course-dd-check"></i>' : ''}`;
                li.addEventListener('mousedown', (e) => {
                    e.preventDefault();
                    if (!isSelected) {
                        selectedCourseChips = [c.courseName];   // single-select; change to push() for multi
                        renderCourseChips();
                        syncCourseFilterSelect();
                        updateFilterBadge();
                        closeCourseDropdown();
                        document.getElementById('courseSearchInput').value = '';
                    }
                });
                dd.appendChild(li);
            });
        }
    }

    function openCourseDropdown(term) {
        buildCourseDropdown(term);
        document.getElementById('courseDropdown')?.classList.add('open');
    }
    function closeCourseDropdown() {
        document.getElementById('courseDropdown')?.classList.remove('open');
    }

    function filterCourseList() {
        // legacy alias — called from event listener
        const term = document.getElementById('courseSearchInput')?.value || '';
        openCourseDropdown(term);
    }

    function initCourseTypeahead() {
        const input = document.getElementById('courseSearchInput');
        const dd = document.getElementById('courseDropdown');
        const widget = document.getElementById('courseChipsInput');
        if (!input || !dd) return;

        // Click on container focuses input
        widget?.addEventListener('click', () => input.focus());

        input.addEventListener('focus', () => openCourseDropdown(input.value));
        input.addEventListener('input', () => openCourseDropdown(input.value));

        // Keyboard navigation
        input.addEventListener('keydown', (e) => {
            const items = [...dd.querySelectorAll('li:not(.already-selected):not(.dd-empty)')];
            const highlighted = dd.querySelector('li.highlighted');
            let idx = items.indexOf(highlighted);

            if (e.key === 'ArrowDown') {
                e.preventDefault();
                if (highlighted) highlighted.classList.remove('highlighted');
                idx = (idx + 1) % items.length;
                items[idx]?.classList.add('highlighted');
                items[idx]?.scrollIntoView({ block: 'nearest' });
            } else if (e.key === 'ArrowUp') {
                e.preventDefault();
                if (highlighted) highlighted.classList.remove('highlighted');
                idx = (idx - 1 + items.length) % items.length;
                items[idx]?.classList.add('highlighted');
                items[idx]?.scrollIntoView({ block: 'nearest' });
            } else if (e.key === 'Enter') {
                e.preventDefault();
                if (highlighted) highlighted.dispatchEvent(new MouseEvent('mousedown'));
            } else if (e.key === 'Escape') {
                closeCourseDropdown();
                input.blur();
            } else if (e.key === 'Backspace' && input.value === '' && selectedCourseChips.length) {
                selectedCourseChips.pop();
                renderCourseChips();
                syncCourseFilterSelect();
                updateFilterBadge();
            }
        });

        // Close on outside click
        document.addEventListener('mousedown', (e) => {
            if (!document.getElementById('courseTypeahead')?.contains(e.target)) {
                closeCourseDropdown();
            }
        });
    }


    // ==================== GET CATEGORY BADGE ====================

    /**
     * Get category badge HTML with color coding (NO ICONS)
     */
    function getCategoryBadge(category) {
        // Default to PURSUING if category is null or undefined
        if (!category) {
            category = 'PURSUING';
        }

        const badges = {
            'OLD_STUDENT': '<span class="badge bg-secondary" title="Student admitted before cutoff date">Old Student</span>',
            'NEW_STUDENT': '<span class="badge bg-primary" title="New CRM entry with REG number">New Student</span>',
            'PURSUING': '<span class="badge bg-info text-dark" title="Currently enrolled and pursuing course">Pursuing</span>',
            'COMPLETED': '<span class="badge bg-success" title="All course certificates issued">Completed</span>',
            'CANCELLED': '<span class="badge bg-danger" title="Admission cancelled - has fee refund">Cancelled</span>'
        };

        return badges[category] || '<span class="badge bg-secondary">Unknown</span>';
    }

    // ==================== RENDER ADMISSIONS TABLE ====================

    function renderAdmissionsTable(admissions) {
        const tbody = document.querySelector('#admissionsTable tbody');

        if (!admissions || admissions.length === 0) {
            tbody.innerHTML = `
            <tr>
                <td colspan="6" class="text-center py-5">
                    <i class="bi bi-inbox" style="font-size: 3rem; color: #94a3b8;"></i>
                    <p class="mt-3 mb-0 text-muted">No admissions found</p>
                    <button class="btn btn-sm btn-primary mt-2" onclick="document.getElementById('btnNewAdmission').click()">
                        <i class="bi bi-plus-circle me-1"></i>Add First Admission
                    </button>
                </td>
            </tr>
        `;
            return;
        }

        tbody.innerHTML = admissions.map(adm => {
            // : Log the category data

            // Format courses
            const courses = adm.coursesList && adm.coursesList.length > 0
                ? adm.coursesList.join(', ')
                : (adm.courses || 'N/A');

            //  Get category badge - USE ACTUAL FIELD VALUE
            const category = adm.studentCategory || 'PURSUING'; // Default to PURSUING if null
            const categoryBadge = getCategoryBadge(category);

            // Format student name with category badge
            const studentNameWithBadge = `
            <div>
                <strong>${adm.studentName || `${adm.firstName || ''} ${adm.lastName || ''}`.trim()}</strong>
                <div class="mt-1">${categoryBadge}</div>
            </div>
        `;

            return `
            <tr data-id="${adm.id}">
                <td data-label="REG NO."><strong>${adm.registrationNumber || '-'}</strong></td>
                <td data-label="STUDENT NAME">${studentNameWithBadge}</td>
                <td data-label="MOBILE NO.">${adm.mobilePrimary || 'N/A'}</td>
                <td data-label="COURSE"><span class="badge bg-primary">${courses}</span></td>
                <td data-label="ADMISSION DATE">${adm.admissionDate ? new Date(adm.admissionDate).toLocaleDateString('en-GB') : 'N/A'}</td>
                <td data-label="ACTIONS">
                    <div class="action-dropdown">
                        <button class="action-btn action-menu-trigger">
                            <i class="bi bi-three-dots-vertical"></i>
                        </button>
                        <div class="action-menu">
                            <button class="action-menu-item" data-action="update" data-id="${adm.id}">
                                <i class="bi bi-pencil-square"></i><span>Update</span>
                            </button>
                            <button class="action-menu-item" data-action="change-status" data-id="${adm.id}">
                                <i class="bi bi-arrow-repeat"></i><span>Change Status</span>
                            </button>
                            <button class="action-menu-item" data-action="view" data-id="${adm.id}">
                                <i class="bi bi-eye"></i><span>View Details</span>
                            </button>
                            <button class="action-menu-item" data-action="installments" data-id="${adm.id}">
                                <i class="bi bi-cash-stack"></i><span>Fee Installments</span>
                            </button>
                            <button class="action-menu-item" data-action="transfer" data-id="${adm.id}">
                                <i class="bi bi-arrow-left-right"></i><span>Transfer Admission</span>
                            </button>
                            <button class="action-menu-item" data-action="print" data-id="${adm.id}">
                                <i class="bi bi-printer"></i><span>Print Form</span>
                            </button>
                            <button class="action-menu-item" data-action="delete" data-id="${adm.id}">
                                <i class="bi bi-trash"></i><span>Remove</span>
                            </button>
                        </div>
                    </div>
                </td>
            </tr>
        `;
        }).join('');

        attachTableEventListeners(tbody);
    }

    /**
     * âœ¨ CHANGE STUDENT CATEGORY STATUS
     */
    async function changeStudentStatus(admissionId) {
        try {
            showLoading('Loading current status...');

            // Fetch current admission data
            const response = await fetch(`/api/admissions/${admissionId}`);
            if (!response.ok) throw new Error('Failed to load admission');

            const admission = await response.json();
            Swal.close();

            // Show status change modal
            const { value: newCategory } = await Swal.fire({
                title: 'Change Student Status',
                html: `
                <div class="text-start">
                    <p><strong>Student:</strong> ${admission.studentName}</p>
                    <p><strong>Reg No:</strong> ${admission.registrationNumber}</p>
                    <p><strong>Current Status:</strong> ${getCategoryBadge(admission.studentCategory)}</p>
                    <hr>
                    <label class="form-label fw-bold">Select New Status:</label>
                    <select id="newCategorySelect" class="form-select">
                        <option value="OLD_STUDENT" ${admission.studentCategory === 'OLD_STUDENT' ? 'selected' : ''}>Old Student</option>
                        <option value="NEW_STUDENT" ${admission.studentCategory === 'NEW_STUDENT' ? 'selected' : ''}>New Student</option>
                        <option value="PURSUING" ${admission.studentCategory === 'PURSUING' ? 'selected' : ''}>Pursuing</option>
                        <option value="COMPLETED" ${admission.studentCategory === 'COMPLETED' ? 'selected' : ''}>Completed</option>
                        <option value="CANCELLED" ${admission.studentCategory === 'CANCELLED' ? 'selected' : ''}>Cancelled</option>
                    </select>
                    <small class="text-muted d-block mt-2">
                        <i class="bi bi-info-circle me-1"></i>
                        This will override the automatic categorization
                    </small>
                </div>
            `,
                showCancelButton: true,
                confirmButtonText: 'Update Status',
                cancelButtonText: 'Cancel',
                confirmButtonColor: '#3b82f6',
                preConfirm: () => {
                    return document.getElementById('newCategorySelect').value;
                }
            });

            if (!newCategory) return;

            // Confirm change
            const confirmed = await Swal.fire({
                title: 'Confirm Status Change',
                html: `
                <p>Change status from:</p>
                <p>${getCategoryBadge(admission.studentCategory)}</p>
                <p><i class="bi bi-arrow-down"></i></p>
                <p>${getCategoryBadge(newCategory)}</p>
            `,
                icon: 'warning',
                showCancelButton: true,
                confirmButtonText: 'Yes, Change',
                cancelButtonText: 'Cancel'
            });

            if (!confirmed.isConfirmed) return;

            // Update status
            showLoading('Updating status...');

            const csrfToken = getCsrfToken();
            const headers = {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            };
            if (csrfToken) headers[getCsrfHeader()] = csrfToken;

            const updateResponse = await fetch(`/api/admissions/${admissionId}/category`, {
                method: 'PUT',
                headers: headers,
                credentials: 'include',
                body: JSON.stringify({ category: newCategory })
            });

            if (!updateResponse.ok) {
                throw new Error('Failed to update status');
            }

            Swal.close();

            await Swal.fire({
                icon: 'success',
                title: 'Status Updated!',
                html: `
                <p>Student status changed successfully</p>
                ${getCategoryBadge(newCategory)}
            `,
                confirmButtonColor: '#10b981',
                timer: 2000
            });

            // Reload table
            loadAdmissions(currentPage, pageSize);

        } catch (error) {
            Swal.close();
            console.error('Error changing status:', error);
            showError('Failed to change status: ' + error.message);
        }
    }

    // Make globally available
    window.changeStudentStatus = changeStudentStatus;

    /**
     * Attach Table Event Listeners
     */
    function attachTableEventListeners(tbody) {
        // Action menu toggle
        tbody.querySelectorAll('.action-menu-trigger').forEach(trigger => {
            trigger.addEventListener('click', function (e) {
                e.stopPropagation();
                if (typeof openActionMenuFixed === 'function') {
                    openActionMenuFixed(this);
                }
            });
        });

        // Action menu items
        tbody.querySelectorAll('.action-menu-item').forEach(item => {
            item.addEventListener('click', function (e) {
                e.stopPropagation();
                const action = this.getAttribute('data-action');
                const id = this.getAttribute('data-id');

                // Close menu
                this.closest('.action-menu').classList.remove('show');

                // Handle action
                handleAction(action, id);
            });
        });

        // Close menus when clicking outside
        document.addEventListener('click', () => {
            document.querySelectorAll('.action-menu').forEach(menu => {
                menu.classList.remove('show');
            });
        });
    }

    function handleAction(action, id) {
        switch (action) {
            case 'update':
                loadAdmissionForEdit(id);
                break;
            case 'change-status':
                changeStudentStatus(id);
                break;
            case 'view':
                viewAdmission(id);
                break;
            case 'installments':
                openFeeInstallments(id);
                break;
            case 'transfer':
                openTransferModal(id);
                break;
            case 'print':
                printAdmission(id);
                break;
            case 'delete':
                deleteAdmission(id);
                break;
            default:
                console.warn('Unknown action:', action);
        }
    }

    // Make getCategoryBadge globally available
    window.getCategoryBadge = getCategoryBadge;

    async function loadAdmissionForEdit(id) {
        try {
            showLoading('Loading admission details...');

            const csrfToken = getCsrfToken();
            const headers = {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            };
            if (csrfToken) headers[getCsrfHeader()] = csrfToken;

            const response = await fetch(`/api/admissions/${id}`, {
                method: 'GET',
                headers: headers,
                credentials: 'include'
            });

            if (!response.ok) throw new Error('Failed to load admission');

            const admission = await response.json();
            Swal.close();

            const modalTitle = document.getElementById('admissionModalTitle');
            if (modalTitle) modalTitle.textContent = 'Update Admission';

            const updateAlert = document.getElementById('admissionUpdateAlert');
            if (updateAlert) updateAlert.style.display = 'block';

            const finishBtn = document.getElementById('btnFinish');
            if (finishBtn) {
                finishBtn.dataset.admissionId = id;
                finishBtn.textContent = 'Update';
            }

            prefillAdmissionForm(admission, true);

            if (admission.installments && admission.installments.length > 0) {
                displayExistingInstallments(admission.installments);

                if (admission.installmentStartDate) {
                    setValue('instStartDate', admission.installmentStartDate);
                }
                if (admission.numberOfInstallments) {
                    setValue('instNoOfInstallments', admission.numberOfInstallments);
                }
                if (admission.daysBetweenInstallments) {
                    setValue('instDays', admission.daysBetweenInstallments);
                }
                if (admission.totalInstallmentAmount) {
                    setValue('instTotalInstAmount', admission.totalInstallmentAmount);
                }
            } else {
                const tbody = document.getElementById('installmentsBody');
                if (tbody) {
                    tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted">No installments found. You can generate new ones.</td></tr>';
                }
            }

            setValue('instTotalAmount', admission.totalReceivableFees || 0);

            currentTab = 1;
            updateNavigationButtons();
            updateProgress(16.66);

            const modalEl = document.getElementById('admissionModal');
            if (!modalEl) return;
            const modal = new bootstrap.Modal(modalEl);
            modal.show();

        } catch (error) {
            Swal.close();
            showError('Failed to load admission details: ' + (error?.message || error));
        }
    }

    async function openNewAdmissionModal() {
        const fromEnquiry = sessionStorage.getItem('admissionFromEnquiry');

        if (!fromEnquiry) {
            const { value: mobile } = await Swal.fire({
                title: 'Enter Student Mobile Number',
                html: `
                <input type="tel" id="swalMobile" class="form-control"
                       placeholder="10-digit mobile number" maxlength="10"
                       pattern="[6-9][0-9]{9}">
                <small class="text-muted d-block mt-2">
                    <i class="bi bi-info-circle me-1"></i>
                    We'll check if an enquiry exists for this student
                </small>
            `,
                showCancelButton: true,
                confirmButtonText: 'Check & Continue',
                cancelButtonText: 'Cancel',
                confirmButtonColor: '#667eea',
                preConfirm: () => {
                    const mobileInput = document.getElementById('swalMobile').value;
                    if (!/^[6-9]\d{9}$/.test(mobileInput)) {
                        Swal.showValidationMessage('Please enter a valid 10-digit mobile number');
                        return false;
                    }
                    return mobileInput;
                }
            });

            if (mobile) {
                try {
                    showLoading('Checking for existing enquiry...');
                    const enquiryResponse = await fetch(`/api/admissions/enquiry-data/${mobile}`);

                    if (enquiryResponse.ok) {
                        const enquiry = await enquiryResponse.json();
                        Swal.close();

                        //  Show enquiry details with date
                        const enquiryDate = enquiry.enquiryDate
                            ? new Date(enquiry.enquiryDate).toLocaleDateString('en-IN')
                            : 'Unknown';

                        await Swal.fire({
                            title: 'Enquiry Found!',
                            html: `
                            <div class="alert alert-success">
                                <strong><i class="bi bi-check-circle me-2"></i>Form will be pre-filled</strong>
                                <p class="mb-2 mt-3">Found enquiry for: <strong>${enquiry.firstName} ${enquiry.lastName}</strong></p>
                                <p class="mb-1 text-muted"><small>Enquiry Date: ${enquiryDate}</small></p>
                                <p class="mb-0">The admission form will be pre-filled with enquiry details.</p>
                            </div>
                        `,
                            icon: 'success',
                            confirmButtonText: 'Continue',
                            confirmButtonColor: '#10b981',
                            timer: 2000
                        });

                        sessionStorage.setItem('admissionEnquiry', JSON.stringify(enquiry));
                        sessionStorage.setItem('admissionFromEnquiry', 'true');

                        const today = getTodayDate();
                        setValue('admAdmissionDate', today);
                        setValue('instStartDate', today);
                        window.location.reload();
                    } else {
                        // âš ï¸  NO ENQUIRY FOUND - REDIRECT TO ENQUIRY PAGE
                        Swal.close();

                        const result = await Swal.fire({
                            title: 'No Enquiry Found',
                            html: `
                            <div class="alert alert-warning">
                                <strong><i class="bi bi-exclamation-triangle me-2"></i>Create Enquiry First</strong>
                                <p class="mb-2 mt-3">No enquiry found for mobile: <strong>${mobile}</strong></p>
                                <p class="mb-0">You must create an enquiry before admission.</p>
                            </div>
                        `,
                            icon: 'warning',
                            showCancelButton: true,
                            confirmButtonText: 'Go to Enquiry',
                            cancelButtonText: 'Cancel',
                            confirmButtonColor: '#f59e0b',
                            cancelButtonColor: '#64748b'
                        });

                        if (result.isConfirmed) {
                            sessionStorage.setItem('enquiryMobile', mobile);
                            window.location.href = '/students/enquiry';
                        }
                    }
                } catch (error) {
                    Swal.close();
                    console.error('Error:', error);

                    const result = await Swal.fire({
                        title: 'Error Checking Enquiry',
                        html: `
                        <div class="alert alert-danger">
                            <strong><i class="bi bi-x-circle me-2"></i>System Error</strong>
                            <p class="mb-2 mt-3">Could not verify enquiry status.</p>
                            <p class="mb-0">Please try again or contact support.</p>
                        </div>
                    `,
                        icon: 'error',
                        showCancelButton: true,
                        confirmButtonText: 'Go to Enquiry',
                        cancelButtonText: 'Cancel',
                        confirmButtonColor: '#ef4444'
                    });

                    if (result.isConfirmed) {
                        sessionStorage.setItem('enquiryMobile', mobile);
                        window.location.href = '/students/enquiry';
                    }
                }
            }
            return;
        }

        // If coming from enquiry, open modal directly
        currentTab = 1;
        clearForms();

        const today = new Date().toISOString().split('T')[0];
        setValue('admAdmissionDate', today);
        setValue('instStartDate', today);

        // Hide update alert
        const updateAlert = document.getElementById('admissionUpdateAlert');
        if (updateAlert) updateAlert.style.display = 'none';

        updateNavigationButtons();
        updateProgress(16.66);
        const modal = new bootstrap.Modal(document.getElementById('admissionModal'));
        modal.show();
    }

    // Enhanced success message with details
    function showSuccessWithDetails(title, message, details = null) {
        let html = `<p>${message}</p>`;

        if (details) {
            html += `
            <div class="alert alert-info mt-3 text-start">
                <small>${details}</small>
            </div>
        `;
        }

        Swal.fire({
            icon: 'success',
            title: title,
            html: html,
            confirmButtonColor: '#10b981',
            timer: 3000,
            timerProgressBar: true
        });
    }

    // Enhanced error message with details
    function showErrorWithDetails(title, message, technicalError = null) {
        let html = `
        <div class="alert alert-danger text-start">
            <i class="bi bi-exclamation-triangle me-2"></i>
            <strong>${message}</strong>
        </div>
    `;

        if (technicalError) {
            html += `
            <details class="mt-3">
                <summary class="text-muted" style="cursor: pointer;">
                    <small>Technical Details</small>
                </summary>
                <pre class="text-start mt-2 p-2 bg-light rounded" style="font-size: 0.85rem;">${technicalError}</pre>
            </details>
        `;
        }

        Swal.fire({
            icon: 'error',
            title: title,
            html: html,
            confirmButtonColor: '#ef4444'
        });
    }

    function prefillAdmissionForm(data, isUpdate = false) {

        //  Helper function to format date
        function formatDate(dateValue) {
            if (!dateValue) return '';

            if (!data.admissionDate) {
                const today = getTodayDate();
                setValue('admAdmissionDate', today);
            }

            if (!data.installmentStartDate) {
                const today = getTodayDate();
                setValue('instStartDate', today);
            }

            highlightPrefilledFields();

            // If it's an array [YYYY, MM, DD], convert to string
            if (Array.isArray(dateValue)) {
                const [year, month, day] = dateValue;
                return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
            }

            // If it's already a string in YYYY-MM-DD format, return as is
            if (typeof dateValue === 'string' && dateValue.match(/^\d{4}-\d{2}-\d{2}$/)) {
                return dateValue;
            }

            // Try to parse as date
            try {
                const date = new Date(dateValue);
                if (!isNaN(date.getTime())) {
                    return date.toISOString().split('T')[0];
                }
            } catch (e) {
                console.warn('Failed to parse date:', dateValue);
            }

            return '';
        }

        // Tab 1: Personal Info
        setValue('admFirstName', data.firstName);
        setValue('admMiddleName', data.middleName);
        setValue('admLastName', data.lastName);
        setValue('admCollege', data.college);
        setValue('admQualification', data.qualification);
        setValue('admAadhaar', data.aadhaar);
        setValue('admDob', formatDate(data.birthDate)); //  Format date
        setValue('admGender', data.gender);
        setValue('admCast', data.cast);
        setValue('admCategory', data.category);
        setValue('admPhysicallyHandicapped', data.physicallyHandicapped);
        setValue('admBloodGroup', data.bloodGroup);

        // Tab 2: Other Details
        setValue('admMobilePrimary', data.mobile || data.mobilePrimary);
        setValue('admMobileSecondary', data.secondaryMobile || data.mobileSecondary);
        setValue('admEmailPrimary', data.email || data.emailPrimary);
        setValue('admEmailSecondary', data.emailSecondary);
        setValue('admCurrentAddress', data.currentAddress);
        setValue('admPermanentAddress', data.permanentAddress);
        setValue('admPinCodeCurrent', data.pinCurrent || data.pinCodeCurrent);
        setValue('admPinCodePermanent', data.pinPermanent || data.pinCodePermanent);
        setValue('admDocument', data.documentType);
        setValue('admLeadSource', data.source || data.leadSource);
        setValue('admNotes', data.note || data.notes);
        setValue('admRollNo', data.rollNumber);
        setValue('admAdmissionDate', formatDate(data.admissionDate)); //  Format date

        // Tab 3: Courses & fees
        if (data.coursesList && data.coursesList.length > 0) {
            const coursesMaster = Array.isArray(allCoursesForFilter) ? allCoursesForFilter : [];
            const totalPayableFromData = Number(data.totalPayableFees) || 0;
            const amountPerCourse = data.coursesList.length > 0 ? (totalPayableFromData / data.coursesList.length) : 0;

            const matchedCourses = [];
            const seenCourseIds = new Set();

            let savedDetails = [];
            if (data.courseFeesDetails) {
                try {
                    savedDetails = JSON.parse(data.courseFeesDetails);
                } catch (e) {
                    console.error('Error parsing courseFeesDetails:', e);
                }
            }

            if (savedDetails && savedDetails.length > 0) {
                savedDetails.forEach((detail, index) => {
                    const exact = coursesMaster.find(c => String(c.courseName || '').trim().toLowerCase() === String(detail.name || '').trim().toLowerCase());
                    const courseId = exact ? exact.id : (index + 1);
                    if (!seenCourseIds.has(courseId)) {
                        matchedCourses.push({
                            id: courseId,
                            name: detail.name,
                            price: parseFloat(detail.price) || 0
                        });
                        seenCourseIds.add(courseId);
                    }
                });
            } else {
                data.coursesList.forEach((courseName, index) => {
                    const raw = String(courseName || '').trim();
                    if (!raw) return;

                    const normalizedRaw = raw.toLowerCase();
                    const exact = coursesMaster.find(c => String(c.courseName || '').trim().toLowerCase() === normalizedRaw);

                    if (exact) {
                        if (!seenCourseIds.has(exact.id)) {
                            matchedCourses.push({
                                id: exact.id,
                                name: String(exact.courseName || raw).trim(),
                                price: parseFloat(exact.courseFees) || 0
                            });
                            seenCourseIds.add(exact.id);
                        }
                        return;
                    }

                    // Try splitting by comma/pipe
                    const parts = raw
                        .split(/\s*,\s*|\s*\|\s*/g)
                        .map(p => p.trim())
                        .filter(Boolean);

                    if (parts.length > 1) {
                        let added = false;
                        parts.forEach(part => {
                            const partNorm = part.toLowerCase();
                            const partMatch = coursesMaster.find(c => String(c.courseName || '').trim().toLowerCase() === partNorm);
                            if (partMatch && !seenCourseIds.has(partMatch.id)) {
                                matchedCourses.push({
                                    id: partMatch.id,
                                    name: String(partMatch.courseName || part).trim(),
                                    price: parseFloat(partMatch.courseFees) || 0
                                });
                                seenCourseIds.add(partMatch.id);
                                added = true;
                            }
                        });

                        if (added) return;
                    }

                    // Fallback: substring matches (handles strings like "FULL STACK FRONTEND PYTHON DJANGO")
                    const substringMatches = coursesMaster
                        .filter(c => {
                            const name = String(c.courseName || '').trim();
                            if (!name) return false;
                            return normalizedRaw.includes(name.toLowerCase());
                        })
                        .sort((a, b) => String(b.courseName || '').length - String(a.courseName || '').length);

                    if (substringMatches.length > 0) {
                        substringMatches.forEach(m => {
                            if (!seenCourseIds.has(m.id)) {
                                matchedCourses.push({
                                    id: m.id,
                                    name: String(m.courseName || '').trim(),
                                    price: parseFloat(m.courseFees) || 0
                                });
                                seenCourseIds.add(m.id);
                            }
                        });
                        return;
                    }

                    // Nothing matched
                    matchedCourses.push({
                        id: index + 1,
                        name: raw,
                        price: amountPerCourse || 0
                    });
                });
            }

            selectedCourses = matchedCourses;
            renderSelectedCourses();
            calculateTotalFees();
        }

        const totalFromSelectedCourses = (selectedCourses || []).reduce((sum, c) => sum + (parseFloat(c.price) || 0), 0);
        const totalPayable = (Number(data.totalPayableFees) || 0) > 0 ? Number(data.totalPayableFees) : totalFromSelectedCourses;
        const receivable = (Number(data.totalReceivableFees) || 0) > 0 ? Number(data.totalReceivableFees) : totalPayable;

        setValue('admTotalFees', totalPayable || 0);
        setValue('admReceivableFees', receivable || 0);
        setValue('admDiscountPercent', data.discountPercent || 0);
        setValue('admDiscountAmount', data.discountAmount || 0);
        setValue('admAcademicYear', data.academicYear || getCurrentAcademicYear());

        // Tab 4: Batch & Subject (Subjects removed per user request)
        if (data.batchesList && data.batchesList.length > 0) {
            const batchSelect = document.getElementById('admBatch');
            if (batchSelect) {
                Array.from(batchSelect.options).forEach(option => {
                    if (data.batchesList.includes(option.textContent)) {
                        option.selected = true;
                    }
                });
            }
        }

        //  Tab 5: Installment dates
        if (data.installmentStartDate) {
            setValue('instStartDate', formatDate(data.installmentStartDate));
        }
        if (data.numberOfInstallments) {
            setValue('instNoOfInstallments', data.numberOfInstallments);
        }
        if (data.daysBetweenInstallments) {
            setValue('instDays', data.daysBetweenInstallments);
        }
        if (data.totalInstallmentAmount) {
            setValue('instTotalInstAmount', data.totalInstallmentAmount);
        }

        // Tab 6: Load photo if available
        if (data.photoPath && data.photoPath.trim() !== '') {
            loadStudentPhoto(data.photoPath);
        }

        highlightPrefilledFields();
    }

    function loadStudentPhoto(photoPath) {
        try {
            const canvas = document.getElementById('admCanvas');
            const ctx = canvas.getContext('2d');
            const img = new Image();

            img.onload = function () {
                ctx.drawImage(img, 0, 0, 300, 300);
            };

            img.onerror = function () {
                console.warn('Failed to load student photo:', photoPath);
            };

            // Construct full photo URL
            img.src = `/uploads/admissions/${photoPath}`;

        } catch (error) {
            console.error('Error loading photo:', error);
        }
    }

    // ==================== LOAD DROPDOWN DATA ====================

    async function loadDropdownData() {
        await Promise.all([
            loadPackages(),
            loadBatches(),
            loadLeadSources()
        ]);
    }

    async function loadPackages() {
        try {
            const response = await fetch('/api/packages/dropdown');
            if (!response.ok) throw new Error('Failed to load packages');

            const packages = await response.json();
            const select = document.getElementById('admPackage');

            select.innerHTML = '<option value="">-- Select Package --</option>';
            packages.forEach(pkg => {
                const option = document.createElement('option');
                option.value = pkg.id;
                option.textContent = `${pkg.packageName} (₹${pkg.totalAmount})`;
                option.dataset.courses = JSON.stringify(pkg.courses);
                option.dataset.amount = pkg.totalAmount;
                select.appendChild(option);
            });
        } catch (error) {
            console.error('Error loading packages:', error);
        }
    }

    async function loadBatches() {
        try {
            const response = await fetch('/api/batches?page=0&size=100');
            if (!response.ok) throw new Error('Failed to load batches');

            const data = await response.json();
            const select = document.getElementById('admBatch');

            select.innerHTML = '';
            data.batches.forEach(batch => {
                const option = document.createElement('option');
                option.value = batch.batchNo;
                option.textContent = `${batch.batchName} (${batch.startTime} - ${batch.endTime})`;
                select.appendChild(option);
            });
        } catch (error) {
            console.error('Error loading batches:', error);
        }
    }

    async function loadLeadSources() {
        try {
            const response = await fetch('/lead-source/list?page=0&size=100');
            if (!response.ok) throw new Error('Failed to load lead sources');

            const result = await response.json();
            const select = document.getElementById('admLeadSource');

            select.innerHTML = '<option value="">-- Select Source --</option>';
            result.data.forEach(source => {
                const option = document.createElement('option');
                option.value = source.sourceTitle;
                option.textContent = source.sourceTitle;
                select.appendChild(option);
            });
        } catch (error) {
            console.error('Error loading lead sources:', error);
        }
    }

    // ==================== PACKAGE & COURSE HANDLING ====================

    function handlePackageChange(e) {
        const select = e.target;
        const selectedOption = select.options[select.selectedIndex];

        if (!selectedOption.value) {
            clearCourseSelection();
            return;
        }

        const courses = JSON.parse(selectedOption.dataset.courses || '[]');
        const packageAmount = parseFloat(selectedOption.dataset.amount || 0);

        // Auto-add all package courses
        selectedCourses = courses.map(course => ({
            id: course.id,
            name: course.courseName,
            price: parseFloat(course.courseFees)
        }));

        renderSelectedCourses();
        calculateTotalFees();
    }

    function handleCourseAdd(e) {
        const select = e.target;
        const selectedOption = select.options[select.selectedIndex];

        if (!selectedOption.value) return;

        const courseId = parseInt(selectedOption.value);
        const courseName = selectedOption.dataset.name;
        const coursePrice = parseFloat(selectedOption.dataset.price);

        // Check if already added
        if (selectedCourses.some(c => c.id === courseId)) {
            showError('Course already added');
            select.value = '';
            return;
        }

        // Add course
        selectedCourses.push({
            id: courseId,
            name: courseName,
            price: coursePrice
        });

        renderSelectedCourses();
        calculateTotalFees();

        // Reset selection
        select.value = '';
    }

    function renderSelectedCourses() {
        const tbody = document.getElementById('selectedCoursesBody');

        if (selectedCourses.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="3" class="text-center text-muted">No courses added</td>
                </tr>
            `;
            return;
        }

        tbody.innerHTML = selectedCourses.map((course, index) => `
            <tr>
                <td data-label="Course">${course.name}</td>
                <td data-label="Amount">
                    <input type="number"
                           class="form-control form-control-sm"
                           value="${parseFloat(course.price || 0).toFixed(2)}"
                           data-index="${index}"
                           onchange="updateCoursePrice(this)"
                           min="0"
                           step="0.01">
                </td>
                <td data-label="Remove">
                    <button type="button" class="btn btn-sm btn-danger btn-remove-course"
                            data-index="${index}">
                        <i class="bi bi-trash"></i>
                    </button>
                </td>
            </tr>
        `).join('');

        tbody.querySelectorAll('.btn-remove-course').forEach(btn => {
            btn.addEventListener('click', function (e) {
                e.preventDefault();
                const index = parseInt(this.dataset.index);
                selectedCourses.splice(index, 1);
                renderSelectedCourses();
                calculateTotalFees();

                if (selectedCourses.length === 0) {
                }
            });
        });
    }

    window.updateCoursePrice = function (input) {
        const index = parseInt(input.dataset.index);
        const newPrice = parseFloat(input.value) || 0;

        selectedCourses[index].price = newPrice;
        calculateTotalFees();
    };

    window.removeCourse = function (indexOrButton) {
        const index = typeof indexOrButton === 'number' ? indexOrButton : parseInt(indexOrButton);

        selectedCourses.splice(index, 1);
        renderSelectedCourses();
        calculateTotalFees();

        if (selectedCourses.length === 0) {
        }

        return false;
    };

    function clearCourseSelection() {
        selectedCourses = [];
        renderSelectedCourses();
        calculateTotalFees();
    }

    // ==================== FEE CALCULATIONS ====================

    function calculateDiscount() {
        const totalPayable = parseFloat(document.getElementById('admTotalFees').value) || 0;
        const discountPercent = parseFloat(document.getElementById('admDiscountPercent').value) || 0;

        const discountAmount = (totalPayable * discountPercent) / 100;
        const receivable = totalPayable - discountAmount;

        document.getElementById('admDiscountAmount').value = discountAmount.toFixed(2);
        document.getElementById('admReceivableFees').value = receivable.toFixed(2);

        // : Auto-update installment totals
        setValue('instTotalAmount', receivable.toFixed(2));
        setValue('feeInstTotalAmount', receivable.toFixed(2));
    }

    function calculateDiscountFromAmount() {
        const totalPayable = parseFloat(document.getElementById('admTotalFees').value) || 0;
        const discountAmount = parseFloat(document.getElementById('admDiscountAmount').value) || 0;

        const discountPercent = totalPayable > 0 ? (discountAmount / totalPayable) * 100 : 0;
        const receivable = totalPayable - discountAmount;

        document.getElementById('admDiscountPercent').value = discountPercent.toFixed(2);
        document.getElementById('admReceivableFees').value = receivable.toFixed(2);

        // : Auto-update installment totals
        setValue('instTotalAmount', receivable.toFixed(2));
        setValue('feeInstTotalAmount', receivable.toFixed(2));
    }

    function updateInstallmentTotals(amount) {
        document.getElementById('instTotalAmount').value = amount.toFixed(2);
        document.getElementById('feeInstTotalAmount').value = amount.toFixed(2);
    }


    // ==================== DISPLAY EXISTING INSTALLMENTS ====================

    function displayExistingInstallments(installments) {
        const tbody = document.getElementById('installmentsBody');

        if (!installments || installments.length === 0) {
            tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted">No installments generated</td></tr>';
            return;
        }

        tbody.innerHTML = '';
        installments.forEach((inst, index) => {
            const date = inst.dueDate || '';
            const amount = inst.amount != null ? parseFloat(inst.amount).toFixed(2) : '';
            const status = inst.status || 'Pending';
            const row = buildInstallmentRow(date, amount, status);
            tbody.appendChild(row);
        });
        recalculateInstallmentTotal();
    }

    // ==================== FEE CALCULATIONS ====================

    function calculateTotalFees() {
        const totalPayable = selectedCourses.reduce((sum, course) => sum + course.price, 0);

        document.getElementById('admTotalFees').value = totalPayable.toFixed(2);

        // Recalculate receivable with current discount
        const discountPercent = parseFloat(document.getElementById('admDiscountPercent').value) || 0;
        const discountAmount = (totalPayable * discountPercent) / 100;
        const receivable = totalPayable - discountAmount;

        document.getElementById('admDiscountAmount').value = discountAmount.toFixed(2);
        document.getElementById('admReceivableFees').value = receivable.toFixed(2);

        setValue('instTotalAmount', receivable.toFixed(2));
        setValue('feeInstTotalAmount', receivable.toFixed(2));
    }

    function calculateDiscount() {
        const totalPayable = parseFloat(document.getElementById('admTotalFees').value) || 0;
        const discountPercent = parseFloat(document.getElementById('admDiscountPercent').value) || 0;

        const discountAmount = (totalPayable * discountPercent) / 100;
        const receivable = totalPayable - discountAmount;

        document.getElementById('admDiscountAmount').value = discountAmount.toFixed(2);
        document.getElementById('admReceivableFees').value = receivable.toFixed(2);

        //  Auto-update installment totals
        setValue('instTotalAmount', receivable.toFixed(2));
        setValue('feeInstTotalAmount', receivable.toFixed(2));
    }

    function calculateDiscountFromAmount() {
        const totalPayable = parseFloat(document.getElementById('admTotalFees').value) || 0;
        const discountAmount = parseFloat(document.getElementById('admDiscountAmount').value) || 0;

        const discountPercent = totalPayable > 0 ? (discountAmount / totalPayable) * 100 : 0;
        const receivable = totalPayable - discountAmount;

        document.getElementById('admDiscountPercent').value = discountPercent.toFixed(2);
        document.getElementById('admReceivableFees').value = receivable.toFixed(2);

        //  Auto-update installment totals
        setValue('instTotalAmount', receivable.toFixed(2));
        setValue('feeInstTotalAmount', receivable.toFixed(2));
    }

    // ==================== SAVE/UPDATE ADMISSION ====================

    async function saveAdmission() {
        const admissionData = collectAdmissionData();
        const admissionId = document.getElementById('btnFinish').dataset.admissionId;
        const isUpdate = !!admissionId;

        if (!validateAdmissionData(admissionData)) {
            showErrorWithDetails(
                'Validation Failed',
                'Please fill all required fields',
                'Required: First Name, Last Name, Mobile, Lead Source, At least one course'
            );
            return;
        }

        // Prompt confirmation if this is an update
        if (isUpdate) {
            let parsedCourses = [];
            try {
                parsedCourses = JSON.parse(admissionData.courseFeesDetails);
            } catch (e) {
                console.error('Error parsing courses for confirmation', e);
            }

            const coursesListHtml = parsedCourses.map(c => `
                <li class="list-group-item d-flex justify-content-between align-items-center py-2 px-3">
                    <span class="text-dark"><i class="bi bi-journal-check text-primary me-2"></i>${c.name}</span>
                    <span class="badge bg-primary-subtle text-primary border border-primary-subtle rounded-pill px-3 py-1">₹${parseFloat(c.price || 0).toFixed(2)}</span>
                </li>
            `).join('');

            // Gather installments from DOM table rows
            const installmentsList = [];
            const instRows = document.querySelectorAll('#installmentsBody tr');
            instRows.forEach((row) => {
                const dateInput = row.querySelector('input[type="date"]');
                const amountInput = row.querySelector('input[type="number"]');
                const statusSelect = row.querySelector('select');
                if (dateInput && amountInput) {
                    installmentsList.push({
                        date: dateInput.value,
                        amount: parseFloat(amountInput.value) || 0,
                        status: statusSelect ? statusSelect.value : 'Pending'
                    });
                }
            });

            let installmentsListHtml = '';
            if (installmentsList.length > 0) {
                installmentsListHtml = `
                    <h6 class="fw-bold text-secondary mb-2 mt-3" style="font-size: 0.85rem;">Installment Schedule (2-Column):</h6>
                    <div class="row g-2 mb-3 text-start" style="font-size: 0.8rem;">
                        ${installmentsList.map((inst, idx) => `
                            <div class="col-6">
                                <div class="p-2 border rounded-3 bg-light d-flex justify-content-between align-items-center h-100">
                                    <div>
                                        <span class="text-muted d-block" style="font-size: 0.7rem; font-weight: 600;">Inst. ${idx + 1} (${inst.date ? new Date(inst.date).toLocaleDateString('en-GB') : '-'})</span>
                                        <span class="badge bg-${inst.status === 'Paid' ? 'success' : inst.status === 'Overdue' ? 'danger' : 'warning'} px-1.5 py-0.5" style="font-size: 0.65rem;">${inst.status}</span>
                                    </div>
                                    <span class="fw-bold text-dark ms-2">₹${inst.amount.toFixed(2)}</span>
                                </div>
                            </div>
                        `).join('')}
                    </div>
                `;
            }

            const confirmHtml = `
                <div class="text-start">
                    <p class="mb-3 text-secondary" style="font-size: 0.9rem;">
                        You are about to save changes to the admission record. Please review the updated details below before continuing:
                    </p>
                    <div class="card border border-light-subtle rounded-3 shadow-sm mb-3">
                        <div class="card-header bg-light py-2 px-3 fw-bold text-secondary" style="font-size: 0.85rem;">
                            Admission Summary
                        </div>
                        <div class="card-body p-3" style="font-size: 0.85rem;">
                            <div class="row g-2">
                                <div class="col-5 text-muted">Student Name:</div>
                                <div class="col-7 fw-semibold text-dark">${admissionData.firstName} ${admissionData.lastName}</div>
                                
                                <div class="col-5 text-muted">Primary Mobile:</div>
                                <div class="col-7 fw-semibold text-dark">${admissionData.mobilePrimary || '-'}</div>

                                <div class="col-5 text-muted">Email:</div>
                                <div class="col-7 fw-semibold text-dark text-break">${admissionData.emailPrimary || '-'}</div>

                                <div class="col-5 text-muted">Admission Date:</div>
                                <div class="col-7 fw-semibold text-dark">${admissionData.admissionDate || '-'}</div>

                                <div class="col-5 text-muted">Package:</div>
                                <div class="col-7 fw-semibold text-dark">${admissionData.packageName || '-'}</div>
                            </div>
                        </div>
                    </div>
                    
                    <h6 class="fw-bold text-secondary mb-2" style="font-size: 0.85rem;">Selected Courses & Fees:</h6>
                    <ul class="list-group mb-3" style="font-size: 0.85rem;">
                        ${coursesListHtml || '<li class="list-group-item text-center text-muted">No courses selected</li>'}
                    </ul>

                    ${installmentsListHtml}

                    <div class="row g-2 mt-2 mb-2 text-center" style="font-size: 0.82rem;">
                        <div class="col-4">
                            <div class="p-2 bg-primary-subtle rounded-3">
                                <div class="text-muted fw-semibold" style="font-size: 0.72rem;">COURSE FEES</div>
                                <div class="fw-bold text-primary">₹${(admissionData.totalPayableFees || admissionData.totalReceivableFees || 0).toFixed(2)}</div>
                            </div>
                        </div>
                        <div class="col-4">
                            <div class="p-2 bg-warning-subtle rounded-3">
                                <div class="text-muted fw-semibold" style="font-size: 0.72rem;">DISCOUNT ${admissionData.discountPercent > 0 ? '(' + admissionData.discountPercent + '%)' : ''}</div>
                                <div class="fw-bold text-warning">- ₹${(admissionData.discountAmount || 0).toFixed(2)}</div>
                            </div>
                        </div>
                        <div class="col-4">
                            <div class="p-2 bg-success-subtle rounded-3">
                                <div class="text-muted fw-semibold" style="font-size: 0.72rem;">NET RECEIVABLE</div>
                                <div class="fw-bold text-success">₹${(admissionData.totalReceivableFees || 0).toFixed(2)}</div>
                            </div>
                        </div>
                    </div>
                    <small class="text-danger d-block text-center mt-2">
                        <i class="bi bi-info-circle me-1"></i>
                        Note: Installments will NOT auto-regenerate unless you regenerated them in the Installments tab.
                    </small>
                </div>
            `;

            const confirmResult = await Swal.fire({
                title: 'Confirm Admission Update',
                html: confirmHtml,
                icon: 'question',
                showCancelButton: true,
                confirmButtonColor: '#2563eb',
                cancelButtonColor: '#dc2626',
                confirmButtonText: 'Yes, Save Changes',
                cancelButtonText: 'Cancel',
                width: '520px'
            });

            if (!confirmResult.isConfirmed) {
                return; // Cancel saving
            }

            //  Custom-installment amount-match validation 
            if (admissionData.customInstallments && admissionData.customInstallments.length > 0) {
                const admTotal = admissionData.totalReceivableFees || 0;
                const admSum = admissionData.customInstallments.reduce((s, i) => s + i.amount, 0);
                const admDiff = Math.round((admSum - admTotal) * 100) / 100;

                if (admDiff > 0) {
                    await Swal.fire({
                        icon: 'error',
                        title: 'Installment Amount Mismatch',
                        html: `
                            <div class="text-start">
                                <p class="mb-2">The total of all installment amounts <strong class="text-danger">cannot be greater than</strong> the Net Receivable Amount.</p>
                                <div class="row g-2" style="font-size:0.88rem;">
                                    <div class="col-6 text-muted">Net Receivable:</div>
                                    <div class="col-6 fw-bold text-primary">&#8377;${admTotal.toFixed(2)}</div>
                                    <div class="col-6 text-muted">Installments Sum:</div>
                                    <div class="col-6 fw-bold text-danger">&#8377;${admSum.toFixed(2)}</div>
                                    <div class="col-6 text-muted">Excess:</div>
                                    <div class="col-6 fw-bold text-danger">+ &#8377;${admDiff.toFixed(2)}</div>
                                </div>
                                <p class="mt-2 mb-0 text-muted" style="font-size:0.82rem;">Go back to the Installments tab and reduce amounts by &#8377;${admDiff.toFixed(2)}.</p>
                            </div>`,
                        confirmButtonColor: '#dc2626',
                        confirmButtonText: 'Go Fix'
                    });
                    return;
                }

                if (admDiff < 0) {
                    const short = Math.abs(admDiff);
                    await Swal.fire({
                        icon: 'warning',
                        title: 'Installment Amount Mismatch',
                        html: `
                            <div class="text-start">
                                <p class="mb-2">The total of all installment amounts <strong class="text-warning">cannot be less than</strong> the Net Receivable Amount.</p>
                                <div class="row g-2" style="font-size:0.88rem;">
                                    <div class="col-6 text-muted">Net Receivable:</div>
                                    <div class="col-6 fw-bold text-primary">&#8377;${admTotal.toFixed(2)}</div>
                                    <div class="col-6 text-muted">Installments Sum:</div>
                                    <div class="col-6 fw-bold text-warning">&#8377;${admSum.toFixed(2)}</div>
                                    <div class="col-6 text-muted">Shortfall:</div>
                                    <div class="col-6 fw-bold text-warning">- &#8377;${short.toFixed(2)}</div>
                                </div>
                                <p class="mt-2 mb-0 text-muted" style="font-size:0.82rem;">Go back to the Installments tab and add &#8377;${short.toFixed(2)} more.</p>
                            </div>`,
                        confirmButtonColor: '#d97706',
                        confirmButtonText: 'Go Fix'
                    });
                    return;
                }
            }
        }

        try {
            showLoading(isUpdate ? 'Updating admission...' : 'Saving admission...');

            const url = isUpdate ? `/api/admissions/${admissionId}` : '/api/admissions';
            const method = isUpdate ? 'PUT' : 'POST';

            // Get CSRF token
            const csrfToken = getCsrfToken();
            const csrfHeader = getCsrfHeader();

            const headers = {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            };

            // Add CSRF token to headers
            if (csrfToken) {
                headers[csrfHeader] = csrfToken;
            }

            const response = await fetch(url, {
                method: method,
                headers: headers,
                credentials: 'include', // Important for cookies
                body: JSON.stringify(admissionData)
            });

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.message || `Failed to ${isUpdate ? 'update' : 'save'} admission`);
            }

            const result = await response.json();

            Swal.close();
            closeModal('admissionModal');

            if (isUpdate) {
                let updatedInstHtml = '';
                if (result.installments && result.installments.length > 0) {
                    updatedInstHtml = `
                        <h6 class="fw-bold text-secondary mb-2 mt-3" style="font-size: 0.85rem;">Updated Installments (2-Column):</h6>
                        <div class="row g-2 mb-1" style="font-size: 0.8rem;">
                            ${result.installments.map((inst, idx) => `
                                <div class="col-6">
                                    <div class="p-2 border rounded-3 bg-light d-flex justify-content-between align-items-center h-100">
                                        <div>
                                            <span class="text-muted d-block" style="font-size: 0.7rem; font-weight: 600;">Inst. ${inst.installmentNumber || (idx + 1)} (${inst.dueDate ? new Date(inst.dueDate).toLocaleDateString('en-GB') : '-'})</span>
                                            <span class="badge bg-${inst.status === 'Paid' ? 'success' : inst.status === 'Overdue' ? 'danger' : 'warning'} px-1.5 py-0.5" style="font-size: 0.65rem;">${inst.status}</span>
                                        </div>
                                        <span class="fw-bold text-dark ms-2">₹${(inst.amount || 0).toFixed(2)}</span>
                                    </div>
                                </div>
                            `).join('')}
                        </div>
                    `;
                }

                const previewHtml = `
                    <div class="text-start">
                        <div class="alert alert-success border-0 mb-3" style="background-color: #ecfdf5; color: #065f46; border-radius: 12px; font-size: 0.9rem;">
                            <div class="d-flex align-items-center gap-2">
                                <i class="bi bi-check-circle-fill fs-5"></i>
                                <div>
                                    <strong>Admission updated successfully!</strong>
                                </div>
                            </div>
                        </div>
                        <div class="card border border-light-subtle rounded-3 shadow-sm mb-3">
                            <div class="card-header bg-light py-2 px-3 fw-bold text-secondary" style="font-size: 0.85rem;">
                                Updated Data Preview
                            </div>
                            <div class="card-body p-3" style="font-size: 0.85rem;">
                                <div class="row g-2">
                                    <div class="col-6 text-muted">Student Name:</div>
                                    <div class="col-6 fw-semibold text-dark">${result.studentName || (result.firstName + ' ' + result.lastName)}</div>
                                    
                                    <div class="col-6 text-muted">Reg No:</div>
                                    <div class="col-6 fw-semibold text-primary">${result.registrationNumber}</div>
                                    
                                    <div class="col-6 text-muted">Primary Mobile:</div>
                                    <div class="col-6 fw-semibold text-dark">${result.mobilePrimary || '-'}</div>

                                    <div class="col-6 text-muted">Email:</div>
                                    <div class="col-6 fw-semibold text-dark text-break">${result.emailPrimary || '-'}</div>

                                    <div class="col-6 text-muted">Admission Date:</div>
                                    <div class="col-6 fw-semibold text-dark">${result.admissionDate ? new Date(result.admissionDate).toLocaleDateString('en-GB') : '-'}</div>

                                    <div class="col-6 text-muted">Enrolled Package:</div>
                                    <div class="col-6 fw-semibold text-dark">${result.packageName || '-'}</div>

                                    <div class="col-12"><hr class="my-2"></div>

                                    <div class="col-6 text-muted">Receivable Fees:</div>
                                    <div class="col-6 fw-bold text-success">₹${(result.totalReceivableFees || 0).toFixed(2)}</div>
                                </div>
                            </div>
                        </div>
                        ${updatedInstHtml}
                    </div>
                `;

                Swal.fire({
                    icon: 'success',
                    title: 'Admission Updated!',
                    html: previewHtml,
                    confirmButtonColor: '#3085d6',
                    confirmButtonText: 'Done'
                });
            } else {
                showSuccessWithDetails(
                    'Admission Saved!',
                    `Admission created successfully`,
                    `Registration No: ${result.registrationNumber || 'Generated'}`
                );
            }

            loadAdmissions();
            clearForms();
            clearCourseSelection();

            document.getElementById('admissionModalTitle').textContent = 'New Admission';
            document.getElementById('btnFinish').textContent = 'Finish';
            delete document.getElementById('btnFinish').dataset.admissionId;

        } catch (error) {
            Swal.close();
            console.error('Error saving admission:', error);

            showErrorWithDetails(
                `Failed to ${isUpdate ? 'Update' : 'Save'} Admission`,
                error.message || `An error occurred while ${isUpdate ? 'updating' : 'saving'} the admission`,
                error.stack || error.toString()
            );
        }
    }

    function collectAdmissionData() {
        const batchSelect = document.getElementById('admBatch');
        const selectedBatches = batchSelect
            ? Array.from(batchSelect.selectedOptions || []).map(opt => opt.textContent)
            : [];

        const subjectSelect = document.getElementById('admSubject');
        const selectedSubjects = subjectSelect
            ? Array.from(subjectSelect.selectedOptions || []).map(opt => opt.textContent)
            : [];

        const installmentConfig = collectInstallmentData();

        // Collect custom installment rows from the table
        const customInstallments = [];
        const instBody = document.getElementById('installmentsBody');
        if (instBody) {
            instBody.querySelectorAll('tr').forEach((row, idx) => {
                const dateInput = row.querySelector('.inst-date, input[type="date"]');
                const amountInput = row.querySelector('.inst-amount, input[type="number"]');
                const statusSelect = row.querySelector('.inst-status, select');
                if (dateInput && amountInput && dateInput.value) {
                    const amount = parseFloat(amountInput.value);
                    if (!isNaN(amount) && amount >= 0) {
                        customInstallments.push({
                            installmentNumber: idx + 1,
                            dueDate: dateInput.value,
                            amount: amount,
                            status: statusSelect ? statusSelect.value : 'Pending'
                        });
                    }
                }
            });
        }

        return {
            firstName: getValue('admFirstName'),
            middleName: getValue('admMiddleName'),
            lastName: getValue('admLastName'),
            college: getValue('admCollege'),
            qualification: getValue('admQualification'),
            aadhaar: getValue('admAadhaar'),
            birthDate: getValue('admDob') || null,
            gender: getValue('admGender'),
            cast: getValue('admCast'),
            category: getValue('admCategory'),
            physicallyHandicapped: getValue('admPhysicallyHandicapped'),
            bloodGroup: getValue('admBloodGroup'),
            mobilePrimary: getValue('admMobilePrimary'),
            mobileSecondary: getValue('admMobileSecondary'),
            emailPrimary: getValue('admEmailPrimary'),
            emailSecondary: getValue('admEmailSecondary'),
            currentAddress: getValue('admCurrentAddress'),
            permanentAddress: getValue('admPermanentAddress'),
            pinCodeCurrent: getValue('admPinCodeCurrent'),
            pinCodePermanent: getValue('admPinCodePermanent'),
            documentType: getValue('admDocument'),
            leadSource: getValue('admLeadSource'),
            admissionDate: getValue('admAdmissionDate') || new Date().toISOString().split('T')[0],
            rollNumber: getValue('admRollNo'),
            notes: getValue('admNotes'),
            packageName: getSelectedPackageName(),
            academicYear: getValue('admAcademicYear'),
            courses: selectedCourses.map(c => c.name),
            courseFeesDetails: JSON.stringify(selectedCourses.map(c => ({ name: c.name, price: c.price }))),
            batches: selectedBatches,
            totalPayableFees: parseFloat(getValue('admTotalFees')) || 0,
            totalReceivableFees: parseFloat(getValue('admReceivableFees')) || 0,
            discountPercent: parseFloat(getValue('admDiscountPercent')) || 0,
            discountAmount: parseFloat(getValue('admDiscountAmount')) || 0,
            installmentConfig: installmentConfig,
            customInstallments: customInstallments.length > 0 ? customInstallments : null,
            studentPhoto: document.getElementById('admCanvas').toDataURL('image/jpeg', 0.8)
        };
    }

    function getSelectedPackageName() {
        const select = document.getElementById('admPackage');
        const selectedOption = select.options[select.selectedIndex];
        return selectedOption.value ? selectedOption.textContent.split('(')[0].trim() : null;
    }

    function collectInstallmentData() {
        const tbody = document.getElementById('installmentsBody');
        if (!tbody) return null;

        const rows = tbody.querySelectorAll('tr');
        if (rows.length === 0 || rows[0].cells.length === 1) {
            return null;
        }

        const startDate = getValue('instStartDate');
        const numberOfInstallments = parseInt(getValue('instNoOfInstallments')) || 0;
        const daysBetween = parseInt(getValue('instDays')) || 30;

        if (!startDate || numberOfInstallments === 0) {
            return null;
        }

        return {
            startDate: startDate,
            numberOfInstallments: numberOfInstallments,
            daysBetween: daysBetween
        };
    }

    // Search Admissions
    async function searchAdmissions() {
        const searchTerm = document.getElementById('searchInput')?.value.trim() || '';
        const searchField = document.getElementById('searchFieldSelect')?.value || 'ALL';
        const studentCategory = document.getElementById('categoryFilter')?.value || '';
        const course = document.getElementById('courseFilter')?.value || '';
        const fromDate = document.getElementById('fromDateFilter')?.value || '';
        const toDate = document.getElementById('toDateFilter')?.value || '';

        currentSearchTerm = searchTerm;
        currentSearchField = searchField;
        currentStudentCategory = studentCategory;
        currentCourseFilter = course;
        currentFromDate = fromDate;
        currentToDate = toDate;

        currentPage = 0;
        await loadAdmissions(0, pageSize);
    }

    function clearAllFilters() {
        if (document.getElementById('searchInput')) document.getElementById('searchInput').value = '';
        if (document.getElementById('searchFieldSelect')) document.getElementById('searchFieldSelect').value = 'ALL';
        if (document.getElementById('categoryFilter')) document.getElementById('categoryFilter').value = '';
        if (document.getElementById('courseFilter')) document.getElementById('courseFilter').value = '';
        if (document.getElementById('courseSearchInput')) document.getElementById('courseSearchInput').value = '';
        if (document.getElementById('fromDateFilter')) document.getElementById('fromDateFilter').value = '';
        if (document.getElementById('toDateFilter')) document.getElementById('toDateFilter').value = '';

        // Reset typeahead chips
        selectedCourseChips = [];
        renderCourseChips();
        closeCourseDropdown();

        currentSearchTerm = '';
        currentSearchField = 'ALL';
        currentStudentCategory = '';
        currentCourseFilter = '';
        currentFromDate = '';
        currentToDate = '';

        if (typeof filterCourseList === 'function') {
            filterCourseList();
        }

        currentPage = 0;
        loadAdmissions(0, pageSize);
    }

    // Fetch export data with fees
    async function prepareExportData() {
        try {
            showLoading('Fetching export data...');

            const params = new URLSearchParams();
            if (currentSearchTerm) params.append('searchTerm', currentSearchTerm.trim());
            if (currentStudentCategory) params.append('studentCategory', currentStudentCategory.trim());
            if (currentCourseFilter) params.append('course', currentCourseFilter.trim());
            if (currentFromDate) params.append('admissionDateFrom', currentFromDate.trim());
            if (currentToDate) params.append('admissionDateTo', currentToDate.trim());

            const queryString = params.toString();
            const url = `/api/admissions/export${queryString ? '?' + queryString : ''}`;

            const response = await fetch(url);
            if (!response.ok) throw new Error('Failed to fetch export data');

            const admissions = await response.json();

            Swal.close();

            if (admissions.length === 0) {
                showError('No data available to export');
                return null;
            }

            return admissions;

        } catch (error) {
            Swal.close();
            console.error('Export error:', error);
            showError('Failed to fetch data: ' + error.message);
            return null;
        }
    }

    // Build export table
    function buildExportTable(admissions) {
        let tempContainer = document.getElementById('tempExportTableContainer');
        if (!tempContainer) {
            tempContainer = document.createElement('div');
            tempContainer.id = 'tempExportTableContainer';
            tempContainer.style.display = 'none';
            document.body.appendChild(tempContainer);
        }

        let tempTable = document.getElementById('tempExportTable');

        if (!tempTable) {
            tempTable = document.createElement('table');
            tempTable.id = 'tempExportTable';
            tempContainer.appendChild(tempTable);
        }

        const tableHTML = `
        <thead>
            <tr>
                <th>Reg No</th>
                <th>Student Name</th>
                <th>Mobile</th>
                <th>Email</th>
                <th>Course</th>
                <th>College</th>
                <th>Total Fees</th>
                <th>Receivable</th>
                <th>Admission Date</th>
            </tr>
        </thead>
        <tbody>
            ${admissions.map(adm => `
                <tr>
                    <td>${adm.registrationNumber}</td>
                    <td>${adm.studentName}</td>
                    <td>${adm.mobile}</td>
                    <td>${adm.email}</td>
                    <td>${adm.courses}</td>
                    <td>${adm.college}</td>
                    <td>${adm.totalFees}</td>
                    <td>${adm.receivableFees}</td>
                    <td>${adm.admissionDate}</td>
                </tr>
            `).join('')}
        </tbody>
    `;

        tempTable.innerHTML = tableHTML;
        return tempTable;
    }

    // Initialize DataTable
    function initDataTable(table) {
        if (exportDataTable) {
            try {
                exportDataTable.destroy();
            } catch (e) {
                console.log('Cleaning up old table');
            }
        }

        exportDataTable = $(table).DataTable({
            dom: 'Bfrtip',
            buttons: [
                {
                    extend: 'csvHtml5',
                    text: 'CSV',
                    title: 'Admissions_Export',
                    filename: `Admissions_${new Date().toISOString().split('T')[0]}`
                },
                {
                    extend: 'excelHtml5',
                    text: 'Excel',
                    title: 'Admissions Export',
                    filename: `Admissions_${new Date().toISOString().split('T')[0]}`
                },
                {
                    extend: 'pdfHtml5',
                    text: 'PDF',
                    title: 'TechnoKraft Training & Solutions - Admissions Report',
                    filename: `Admissions_${new Date().toISOString().split('T')[0]}`,
                    orientation: 'landscape',
                    pageSize: 'A3',
                    customize: function (doc) {
                        doc.content.splice(0, 0, {
                            text: 'TechnoKraft Training & Solutions',
                            style: 'header',
                            alignment: 'center',
                            fontSize: 18,
                            bold: true,
                            margin: [0, 0, 0, 10]
                        });

                        doc.content.splice(1, 0, {
                            text: 'Admissions Report',
                            style: 'subheader',
                            alignment: 'center',
                            fontSize: 14,
                            margin: [0, 0, 0, 5]
                        });

                        doc.content.splice(2, 0, {
                            text: `Generated on: ${new Date().toLocaleString()}`,
                            alignment: 'right',
                            fontSize: 10,
                            margin: [0, 0, 0, 15]
                        });

                        doc.styles.tableHeader = {
                            bold: true,
                            fontSize: 11,
                            color: 'white',
                            fillColor: '#4f46e5',
                            alignment: 'center'
                        };

                        doc.defaultStyle.fontSize = 9;
                    }
                },
                'copy',
                'print'
            ],
            paging: false,
            searching: false,
            ordering: false,
            info: false,
            autoWidth: false
        });

        return exportDataTable;
    }

    // Export functions
    window.exportToCSV = async function () {
        try {
            const admissions = await prepareExportData();
            if (!admissions) return;

            const table = buildExportTable(admissions);
            const dt = initDataTable(table);

            dt.button('.buttons-csv').trigger();
            showSuccess('CSV exported successfully!');

        } catch (error) {
            console.error('CSV export error:', error);
            showError('Failed to export CSV');
        }
    };

    window.exportToExcel = async function () {
        try {
            const admissions = await prepareExportData();
            if (!admissions) return;

            const table = buildExportTable(admissions);
            const dt = initDataTable(table);

            dt.button('.buttons-excel').trigger();
            showSuccess('Excel exported successfully!');

        } catch (error) {
            console.error('Excel export error:', error);
            showError('Failed to export Excel');
        }
    };

    window.exportToPDF = async function () {
        try {
            const admissions = await prepareExportData();
            if (!admissions) return;

            const table = buildExportTable(admissions);
            const dt = initDataTable(table);

            dt.button('.buttons-pdf').trigger();
            showSuccess('PDF exported successfully!');

        } catch (error) {
            console.error('PDF export error:', error);
            showError('Failed to export PDF');
        }
    };

    window.copyTableData = async function () {
        try {
            const admissions = await prepareExportData();
            if (!admissions) return;

            const table = buildExportTable(admissions);
            const dt = initDataTable(table);

            dt.button('.buttons-copy').trigger();
            showSuccess('Data copied to clipboard!');

        } catch (error) {
            console.error('Copy error:', error);
            showError('Failed to copy data');
        }
    };

    window.printTable = async function () {
        try {
            const admissions = await prepareExportData();
            if (!admissions) return;

            const table = buildExportTable(admissions);
            const dt = initDataTable(table);

            dt.button('.buttons-print').trigger();

        } catch (error) {
            console.error('Print error:', error);
            showError('Failed to print');
        }
    };
    // Copy table data to clipboard
    async function copyTableData() {
        try {
            showLoading('Copying data to clipboard...');

            const response = await fetch(`/api/admissions?page=0&size=${totalElements}`, {
                method: 'GET',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) throw new Error('Failed to fetch data');

            const data = await response.json();
            const admissions = data.content || [];

            // Create tab-separated text
            const headers = ['Reg No', 'Student Name', 'Mobile', 'Course', 'Admission Date'];
            const rows = admissions.map(adm => [
                adm.registrationNumber || '-',
                adm.studentName || `${adm.firstName} ${adm.lastName}`,
                adm.mobilePrimary || '-',
                adm.courses || '-',
                adm.admissionDate || '-'
            ]);

            const textData = [
                headers.join('\t'),
                ...rows.map(row => row.join('\t'))
            ].join('\n');

            await navigator.clipboard.writeText(textData);

            Swal.close();

            showSuccessWithDetails(
                'Copied to Clipboard!',
                `${admissions.length} records copied`,
                'You can now paste into Excel, Google Sheets, or any text editor'
            );

        } catch (error) {
            Swal.close();
            console.error('Copy error:', error);

            showErrorWithDetails(
                'Copy Failed',
                'Failed to copy data to clipboard',
                error.message
            );
        }
    }

    // View Admission
    async function viewAdmission(id) {
        try {
            showLoading('Loading admission details...');

            const response = await fetch(`/api/admissions/${id}`, {
                method: 'GET',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) throw new Error('Failed to load admission');

            const admission = await response.json();
            Swal.close();

            // Populate view modal
            document.getElementById('viewAdmStudentName').textContent = admission.studentName || `${admission.firstName} ${admission.lastName}`;
            document.getElementById('viewAdmRegNo').textContent = admission.registrationNumber;
            document.getElementById('viewAdmDate').textContent = admission.admissionDate ? new Date(admission.admissionDate).toLocaleDateString('en-GB') : '-';
            document.getElementById('viewAdmBirthDate').textContent = admission.birthDate ? new Date(admission.birthDate).toLocaleDateString('en-GB') : '-';
            document.getElementById('viewAdmGender').textContent = admission.gender || '-';
            document.getElementById('viewAdmAadhaarNo').textContent = admission.aadhaar || '-';
            document.getElementById('viewAdmBloodGroup').textContent = admission.bloodGroup || '-';
            document.getElementById('viewAdmMobile1').textContent = admission.mobilePrimary;
            document.getElementById('viewAdmMobile2').textContent = admission.mobileSecondary || '-';
            document.getElementById('viewAdmEmail').textContent = admission.emailPrimary || '-';
            document.getElementById('viewAdmAddress').textContent = admission.currentAddress || '-';

            // Course & Batch Details
            document.getElementById('viewAdmPackage').textContent = admission.packageName || '-';

            const coursesContainer = document.getElementById('viewAdmCourses');
            let hasDetails = false;
            let savedDetails = [];
            if (admission.courseFeesDetails) {
                try {
                    savedDetails = typeof admission.courseFeesDetails === 'string'
                        ? JSON.parse(admission.courseFeesDetails)
                        : admission.courseFeesDetails;
                    if (Array.isArray(savedDetails) && savedDetails.length > 0) {
                        hasDetails = true;
                    }
                } catch (e) {
                    console.error('Error parsing courseFeesDetails for view:', e);
                }
            }

            if (!hasDetails && admission.courses && admission.courses !== '-') {
                const courseNames = admission.courses.split(',').map(c => c.trim()).filter(Boolean);
                savedDetails = courseNames.map(name => {
                    const matchedCourse = (allCoursesForFilter || []).find(c => c.courseName.trim().toLowerCase() === name.toLowerCase());
                    return {
                        name: name,
                        price: matchedCourse ? matchedCourse.courseFees : 0
                    };
                });
                if (savedDetails.length > 0) {
                    hasDetails = true;
                }
            }

            if (hasDetails) {
                const totalPayable = savedDetails.reduce((s, c) => s + (parseFloat(c.price) || 0), 0);
                const discountAmt = admission.discountAmount || 0;
                const discountPct = admission.discountPercent || 0;
                const receivable = admission.totalReceivableFees || (totalPayable - discountAmt);

                coursesContainer.innerHTML = `
                    <div class="table-responsive mt-1">
                        <table class="table table-sm table-bordered mb-0" style="font-size: 0.85rem;">
                            <thead class="table-light">
                                <tr>
                                    <th>Course Name</th>
                                    <th style="width: 120px;">Fees</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${savedDetails.map(c => `
                                    <tr>
                                        <td data-label="Course Name"><strong>${c.name}</strong></td>
                                        <td data-label="Fees">₹${parseFloat(c.price || 0).toFixed(2)}</td>
                                    </tr>
                                `).join('')}
                            </tbody>
                            <tfoot class="table-secondary">
                                <tr>
                                    <td class="text-muted" style="font-size:0.8rem;">Total Payable</td>
                                    <td class="fw-semibold">₹${totalPayable.toFixed(2)}</td>
                                </tr>
                                ${discountAmt > 0 ? `<tr>
                                    <td class="text-warning" style="font-size:0.8rem;">Discount (${discountPct}%)</td>
                                    <td class="text-warning fw-semibold">- ₹${discountAmt.toFixed(2)}</td>
                                </tr>` : ''}
                                <tr>
                                    <td class="fw-bold text-success" style="font-size:0.8rem;">Net Receivable</td>
                                    <td class="fw-bold text-success">₹${receivable.toFixed(2)}</td>
                                </tr>
                            </tfoot>
                        </table>
                    </div>
                `;
            } else {
                coursesContainer.textContent = admission.courses || '-';
            }

            // Render Detailed Batches
            const batchesContainer = document.getElementById('viewAdmBatches');
            if (admission.batchDetails && admission.batchDetails.length > 0) {
                batchesContainer.innerHTML = `
                    <div class="table-responsive mt-1">
                        <table class="table table-sm table-bordered mb-0" style="font-size: 0.85rem;">
                            <thead class="table-light">
                                <tr>
                                    <th>Batch Name</th>
                                    <th>Start Date</th>
                                    <th>End Date</th>
                                    <th>Timing</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${admission.batchDetails.map(b => `
                                    <tr>
                                        <td data-label="Batch Name"><strong>${b.batchName}</strong></td>
                                        <td data-label="Start Date">${formatDisplayDate(b.startDate)}</td>
                                        <td data-label="End Date">${formatDisplayDate(b.endDate)}</td>
                                        <td data-label="Timing">${formatDisplayTime(b.startTime)} - ${formatDisplayTime(b.endTime)}</td>
                                    </tr>
                                `).join('')}
                            </tbody>
                        </table>
                    </div>
                `;
            } else {
                batchesContainer.textContent = admission.batches || '-';
            }

            document.getElementById('viewAdmAcademicYear').textContent = admission.academicYear || '-';
            document.getElementById('viewAdmDocument').textContent = admission.documentType || '-';
            document.getElementById('viewAdmNotes').textContent = admission.notes || '-';

            // Status Badge
            const statusBadge = document.getElementById('viewAdmStatusBadge');
            if (statusBadge) {
                const status = admission.status || 'Active';
                statusBadge.textContent = status;
                statusBadge.className = `badge shadow-sm rounded-pill px-3 py-1.5 ${status.toLowerCase() === 'active' ? 'bg-success' :
                    status.toLowerCase() === 'pending' ? 'bg-warning text-dark' :
                        'bg-danger'
                    }`;
            }

            // Photo
            const photoImg = document.getElementById('viewStudentPhoto');
            if (admission.photoPath) {
                photoImg.src = `/uploads/admissions/${admission.photoPath}`;
            } else {
                photoImg.src = '/assets/images/user-logo.png';
            }

            // Payment Totals
            const totalFees = admission.totalPayableFees || admission.totalReceivableFees || 0;
            const discountAmt = admission.discountAmount || 0;
            const discountPct = admission.discountPercent || 0;
            const receivable = admission.totalReceivableFees || (totalFees - discountAmt);
            const paidAmount = admission.totalPaidAmount || 0;
            const dueAmount = receivable - paidAmount;

            document.getElementById('viewAdmTotalFees').textContent = `₹${totalFees.toFixed(2)}`;

            const discountEl = document.getElementById('viewAdmDiscount');
            const discountPctEl = document.getElementById('viewAdmDiscountPct');
            if (discountEl) discountEl.textContent = `₹${discountAmt.toFixed(2)}`;
            if (discountPctEl) discountPctEl.textContent = discountPct > 0 ? `${discountPct}% off` : '';

            document.getElementById('viewAdmPaidAmount').textContent = `₹${paidAmount.toFixed(2)}`;
            document.getElementById('viewAdmDueAmount').textContent = `₹${dueAmount.toFixed(2)}`;

            // Populate Audit details for SUPER_ADMIN
            const auditSection = document.getElementById('superadminAuditSection');
            if (auditSection) {
                const userRole = document.getElementById('currentUserRole')?.value;
                if (userRole && (userRole.toUpperCase().replace(/\s+|_/g, '') === 'SUPERADMIN')) {
                    auditSection.style.display = 'block';
                    document.getElementById('viewAdmCreatedBy').textContent = admission.createdBy || '-';
                    document.getElementById('viewAdmCreatedTime').textContent = formatDisplayDateTime(admission.createdAt);
                    document.getElementById('viewAdmUpdatedBy').textContent = admission.updatedBy || '-';
                    document.getElementById('viewAdmUpdatedTime').textContent = formatDisplayDateTime(admission.updatedAt);
                } else {
                    auditSection.style.display = 'none';
                }
            }

            // Installments
            const instBody = document.getElementById('viewAdmInstallmentsBody');
            const thAudit = document.getElementById('viewAdmInstallmentsThAudit');
            const userRole = document.getElementById('currentUserRole')?.value;
            const isSuperAdmin = userRole && (userRole.toUpperCase().replace(/\s+|_/g, '') === 'SUPERADMIN');

            if (thAudit) {
                thAudit.style.display = isSuperAdmin ? 'table-cell' : 'none';
            }

            if (admission.installments && admission.installments.length > 0) {
                instBody.innerHTML = admission.installments.map(inst => {
                    const auditTd = isSuperAdmin ? `
                        <td data-label="Audit" style="font-size: 0.8rem; line-height: 1.2;">
                            <div><strong>Created By:</strong> ${inst.createdBy || 'SYSTEM'}</div>
                            <div class="text-muted">${inst.createdAt ? formatDisplayDateTime(inst.createdAt) : 'N/A'}</div>
                            ${inst.updatedBy ? `
                                <div class="mt-1"><strong>Updated By:</strong> ${inst.updatedBy}</div>
                                <div class="text-muted">${inst.updatedAt ? formatDisplayDateTime(inst.updatedAt) : 'N/A'}</div>
                            ` : ''}
                        </td>
                    ` : '';

                    return `
                        <tr>
                            <td data-label="Date">${new Date(inst.dueDate).toLocaleDateString('en-GB')}</td>
                            <td data-label="Amount">₹${parseFloat(inst.amount).toFixed(2)}</td>
                            <td data-label="Status">
                                <span class="badge bg-${inst.status === 'Paid' ? 'success' : inst.status === 'Overdue' ? 'danger' : 'warning'}">
                                    ${inst.status}
                                </span>
                            </td>
                            <td data-label="Notes" class="small text-muted">${inst.notes || '-'}</td>
                            ${auditTd}
                        </tr>
                    `;
                }).join('');
            } else {
                const colspanVal = isSuperAdmin ? 5 : 4;
                instBody.innerHTML = `<tr><td colspan="${colspanVal}" class="text-center text-muted">No installments found</td></tr>`;
            }

            // Fetch Receipts
            try {
                const receiptResponse = await fetch(`/api/fees-manager/receipts/${admission.registrationNumber}`);
                if (receiptResponse.ok) {
                    const receipts = await receiptResponse.json();
                    const receiptBody = document.getElementById('viewAdmReceiptsBody');
                    if (receipts && receipts.length > 0) {
                        receiptBody.innerHTML = receipts.map(r => `
                            <tr>
                                <td data-label="Receipt No.">${r.receiptNumber}</td>
                                <td data-label="Date">${new Date(r.receiptDate).toLocaleDateString('en-GB')}</td>
                                <td data-label="Amount">₹${parseFloat(r.amountReceived || 0).toFixed(2)}</td>
                                <td data-label="Mode">${r.paymentMode}</td>
                            </tr>
                        `).join('');
                    } else {
                        receiptBody.innerHTML = '<tr><td colspan="4" class="text-center text-muted">No receipts found</td></tr>';
                    }
                }
            } catch (receiptError) {
                console.error('Error fetching receipts:', receiptError);
            }

            // Reset to first tab (Profile) when showing modal
            const profileTabBtn = document.querySelector('#viewAdmissionModal #profile-tab');
            if (profileTabBtn) {
                const tabInstance = bootstrap.Tab.getOrCreateInstance(profileTabBtn);
                tabInstance.show();
            }

            const modal = new bootstrap.Modal(document.getElementById('viewAdmissionModal'));
            modal.show();

        } catch (error) {
            Swal.close();
            console.error('Error:', error);
            showError('Failed to load admission details');
        }
    }

    function formatDisplayDate(dateValue) {
        if (!dateValue) return '-';
        if (Array.isArray(dateValue) && dateValue.length >= 3) {
            const [year, month, day] = dateValue;
            return `${String(day).padStart(2, '0')}/${String(month).padStart(2, '0')}/${year}`;
        }
        if (typeof dateValue === 'string' && dateValue.includes('-')) {
            const parts = dateValue.split('-');
            if (parts.length === 3) return `${parts[2]}/${parts[1]}/${parts[0]}`;
        }
        return dateValue;
    }

    function formatDisplayTime(timeValue) {
        if (!timeValue) return '-';
        let hours, minutes;
        if (Array.isArray(timeValue) && timeValue.length >= 2) {
            [hours, minutes] = timeValue;
        } else if (typeof timeValue === 'string' && timeValue.includes(':')) {
            const parts = timeValue.split(':');
            hours = parseInt(parts[0]);
            minutes = parseInt(parts[1]);
        } else return timeValue;
        if (isNaN(hours) || isNaN(minutes)) return timeValue;
        const ampm = hours >= 12 ? 'PM' : 'AM';
        hours = hours % 12;
        hours = hours ? hours : 12;
        return `${hours}:${String(minutes).padStart(2, '0')} ${ampm}`;
    }

    function formatDisplayDateTime(dateTimeValue) {
        if (!dateTimeValue) return '-';
        try {
            let date;
            if (Array.isArray(dateTimeValue)) {
                const [year, month, day, hours = 0, minutes = 0, seconds = 0] = dateTimeValue;
                date = new Date(year, month - 1, day, hours, minutes, seconds);
            } else {
                const str = String(dateTimeValue).trim();
                if (str.includes(',')) {
                    const parts = str.split(',').map(Number);
                    if (parts.length >= 3 && parts.every(p => !isNaN(p))) {
                        const [year, month, day, hours = 0, minutes = 0, seconds = 0] = parts;
                        date = new Date(year, month - 1, day, hours, minutes, seconds);
                    }
                }
                if (!date || isNaN(date.getTime())) {
                    date = new Date(str);
                }
            }

            if (isNaN(date.getTime())) return String(dateTimeValue);

            const day = String(date.getDate()).padStart(2, '0');
            const month = String(date.getMonth() + 1).padStart(2, '0');
            const year = date.getFullYear();
            const hours = String(date.getHours()).padStart(2, '0');
            const minutes = String(date.getMinutes()).padStart(2, '0');
            const seconds = String(date.getSeconds()).padStart(2, '0');
            return `${day}/${month}/${year} ${hours}:${minutes}:${seconds}`;
        } catch (e) {
            return String(dateTimeValue);
        }
    }

    // Initialize course search functionality
    function initializeCourseSearch() {
        const searchInput = document.getElementById('admCourseSearch');
        const dropdown = document.getElementById('admCourseDropdown');

        if (!searchInput || !dropdown) {
            console.warn('Course search elements not found');
            return;
        }

        let allCourses = [];

        // Load courses on initialization
        loadCoursesForSearch();

        async function loadCoursesForSearch() {
            try {
                const response = await fetch('/api/courses/dropdown');
                if (!response.ok) throw new Error('Failed to load courses');

                allCourses = await response.json();
            } catch (error) {
                console.error('â Œ Error loading courses:', error);
            }
        }

        // Show dropdown on focus
        searchInput.addEventListener('focus', function () {
            if (allCourses.length > 0) {
                renderCourseDropdown('');
                dropdown.classList.add('show');
            }
        });

        // Filter on input
        searchInput.addEventListener('input', function () {
            const searchTerm = this.value.toLowerCase().trim();
            renderCourseDropdown(searchTerm);
            dropdown.classList.add('show');
        });

        // Hide dropdown when clicking outside
        document.addEventListener('click', function (e) {
            if (!searchInput.contains(e.target) && !dropdown.contains(e.target)) {
                dropdown.classList.remove('show');
            }
        });

        function renderCourseDropdown(searchTerm) {
            const filteredCourses = searchTerm
                ? allCourses.filter(course =>
                    course.courseName.toLowerCase().includes(searchTerm)
                )
                : allCourses;

            if (filteredCourses.length === 0) {
                dropdown.innerHTML = '<div class="dropdown-item text-muted">No courses found</div>';
                return;
            }

            dropdown.innerHTML = filteredCourses.map(course => `
                <button type="button"
                        class="dropdown-item course-option"
                        data-id="${course.id}"
                        data-name="${course.courseName}"
                        data-price="${course.courseFees}">
                    <strong>${course.courseName}</strong>
                    <span class="text-muted float-end">₹${parseFloat(course.courseFees).toFixed(2)}</span>
                </button>
            `).join('');

            // Attach click handlers
            dropdown.querySelectorAll('.course-option').forEach(option => {
                option.addEventListener('click', function (e) {
                    e.preventDefault();
                    e.stopPropagation();

                    selectCourse(
                        parseInt(this.dataset.id),
                        this.dataset.name,
                        parseFloat(this.dataset.price)
                    );
                });
            });
        }

        function selectCourse(id, name, price) {
            // Check if already added
            if (selectedCourses.some(c => c.id === id)) {
                showError('Course already added');
                return;
            }

            // Add course
            selectedCourses.push({ id, name, price });

            // Clear search and hide dropdown
            searchInput.value = '';
            dropdown.classList.remove('show');

            // Update UI
            renderSelectedCourses();
            calculateTotalFees();

            Swal.fire({
                icon: 'success',
                title: 'Course Added!',
                text: `${name} has been added`,
                timer: 1500,
                showConfirmButton: false,
                toast: true,
                position: 'top-end'
            });
        }
    }

    async function renderCourseOptions(searchTerm) {
        const dropdown = document.getElementById('admCourseDropdown');

        try {
            const response = await fetch('/api/courses/dropdown');
            if (!response.ok) throw new Error('Failed to load courses');

            const courses = await response.json();

            const filteredCourses = courses.filter(course =>
                course.courseName.toLowerCase().includes(searchTerm)
            );

            if (filteredCourses.length === 0) {
                dropdown.innerHTML = '<div class="dropdown-item text-muted">No courses found</div>';
                return;
            }

            dropdown.innerHTML = filteredCourses.map(course => `
                <button type="button"
                        class="dropdown-item course-option"
                        data-id="${course.id}"
                        data-name="${course.courseName}"
                        data-price="${course.courseFees}">
                    ${course.courseName} <span class="text-muted">(₹${course.courseFees})</span>
                </button>
            `).join('');

            // Attach click handlers
            dropdown.querySelectorAll('.course-option').forEach(option => {
                option.addEventListener('click', function () {
                    selectCourse(
                        parseInt(this.dataset.id),
                        this.dataset.name,
                        parseFloat(this.dataset.price)
                    );
                });
            });

        } catch (error) {
            console.error('Error loading courses:', error);
            dropdown.innerHTML = '<div class="dropdown-item text-danger">Error loading courses</div>';
        }
    }

    // ==================== COURSE SEARCH FUNCTIONALITY ====================

    // Open Fee Installments
    async function openFeeInstallments(id) {
        try {
            showLoading('Loading installments...');

            const response = await fetch(`/api/admissions/${id}`, {
                method: 'GET',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) throw new Error('Failed to load admission');

            const admission = await response.json();
            Swal.close();

            setValue('feeInstTotalAmount', admission.totalReceivableFees || 0);
            setValue('feeInstTotalInstAmount', admission.totalInstallmentAmount || admission.totalReceivableFees || 0);

            if (admission.installmentStartDate) {
                setValue('feeInstStartDate', admission.installmentStartDate);
            }

            if (admission.numberOfInstallments) {
                setValue('feeInstNoOfInstallments', admission.numberOfInstallments);
            }

            if (admission.daysBetweenInstallments) {
                setValue('feeInstDays', admission.daysBetweenInstallments);
            }

            // Store regNo for save
            const nameEl = document.getElementById('feeInstStudentName');
            if (nameEl) {
                nameEl.textContent = admission.studentName;
                nameEl.dataset.regNo = admission.registrationNumber;
            }

            const instResponse = await fetch(`/api/admissions/${id}/installments`, {
                method: 'GET',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                }
            });

            if (!instResponse.ok) throw new Error('Failed to load installments');

            const installments = await instResponse.json();
            const tbody = document.getElementById('feeInstallmentsBody');

            if (installments.length === 0) {
                tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted">No installments found. Generate or add rows below.</td></tr>';
                setupFeeInstallmentsAuditLogic();
            } else {
                tbody.innerHTML = '';
                installments.forEach(inst => {
                    tbody.appendChild(buildFeeInstallmentRow(
                        inst.dueDate || '',
                        inst.amount != null ? parseFloat(inst.amount).toFixed(2) : '',
                        inst.status || 'Pending',
                        inst.notes || '',
                        inst.id,
                        inst.createdBy,
                        inst.createdAt,
                        inst.updatedBy,
                        inst.updatedAt
                    ));
                });
                recalculateFeeInstallmentTotal();
                setupFeeInstallmentsAuditLogic();
            }

            const modal = bootstrap.Modal.getOrCreateInstance(document.getElementById('feeInstallmentsModal'));
            modal.show();

        } catch (error) {
            Swal.close();
            console.error('Error:', error);
            showError('Failed to load installments');
        }
    }

    window.deleteInstallment = async function (installmentId) {
        const result = await Swal.fire({
            title: 'Delete Installment?',
            text: 'This action cannot be undone',
            icon: 'warning',
            showCancelButton: true,
            confirmButtonText: 'Yes, Delete',
            cancelButtonText: 'Cancel',
            confirmButtonColor: '#ef4444'
        });

        if (result.isConfirmed) {
            try {
                showLoading('Deleting installment...');

                const csrfToken = getCsrfToken();
                const headers = {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                };
                if (csrfToken) headers[getCsrfHeader()] = csrfToken;

                const response = await fetch(`/api/fees-manager/installments/${installmentId}`, {
                    method: 'DELETE',
                    headers: headers,
                    credentials: 'include'
                });

                if (!response.ok) {
                    const error = await response.json();
                    throw new Error(error.message || 'Failed to delete installment');
                }

                Swal.close();
                showSuccess('Installment deleted successfully!');

                // Reload installments modal
                const modal = bootstrap.Modal.getOrCreateInstance(document.getElementById('feeInstallmentsModal'));
                if (modal) {
                    const studentName = document.getElementById('feeInstStudentName').textContent;
                    // Reload current view
                    location.reload();
                }

            } catch (error) {
                Swal.close();
                console.error('Error:', error);
                showError(error.message || 'Failed to delete installment');
            }
        }
    };

    window.editInstallment = async function (id, dueDate, amount, status, notes) {
        // Hide the parent Fee Installments modal so SweetAlert inputs are fully accessible
        const feeInstModalEl = document.getElementById('feeInstallmentsModal');
        let feeInstModalInstance = feeInstModalEl ? bootstrap.Modal.getOrCreateInstance(feeInstModalEl) : null;
        if (feeInstModalInstance) {
            feeInstModalInstance.hide();
        }

        // Wait a brief moment for Bootstrap hide transitions and backdrop cleanup to complete
        await new Promise(resolve => setTimeout(resolve, 150));
        document.querySelectorAll('.modal-backdrop').forEach(el => el.remove());
        document.body.classList.remove('modal-open');
        document.body.style.overflow = '';
        document.body.style.paddingRight = '';

        const { value: formValues } = await Swal.fire({
            title: '<i class="bi bi-pencil-square me-2"></i>Edit Installment',
            html: `
                 <div class="text-start">
                     <div class="mb-3">
                         <label class="form-label fw-semibold">Due Date</label>
                         <input type="date" class="form-control" id="editInstDueDate" value="${dueDate}">
                     </div>
                     <div class="mb-3">
                         <label class="form-label fw-semibold">Amount (&#8377;)</label>
                         <input type="number" class="form-control" id="editInstAmount" value="${amount}" step="0.01" min="0">
                     </div>
                     <div class="mb-3">
                         <label class="form-label fw-semibold">Status</label>
                         <select class="form-select" id="editInstStatus">
                             <option value="Pending" ${status === 'Pending' ? 'selected' : ''}>Pending</option>
                             <option value="Paid" ${status === 'Paid' ? 'selected' : ''}>Paid</option>
                             <option value="Partial" ${status === 'Partial' ? 'selected' : ''}>Partial</option>
                             <option value="Overdue" ${status === 'Overdue' ? 'selected' : ''}>Overdue</option>
                             <option value="Refund" ${status === 'Refund' ? 'selected' : ''}>Refund</option>
                         </select>
                     </div>
                     <div class="mb-3">
                         <label class="form-label fw-semibold">Notes</label>
                         <textarea class="form-control" id="editInstNotes" rows="2">${notes || ''}</textarea>
                     </div>
                 </div>
             `,
            focusConfirm: false,
            showCancelButton: true,
            confirmButtonText: '<i class="bi bi-check-circle me-1"></i> Update',
            cancelButtonText: 'Cancel',
            confirmButtonColor: '#667eea',
            didOpen: () => {
                setTimeout(() => {
                    const el = document.getElementById('editInstDueDate');
                    if (el) el.focus();
                }, 80);
            },
            preConfirm: () => {
                const d = document.getElementById('editInstDueDate').value;
                const a = parseFloat(document.getElementById('editInstAmount').value);
                if (!d) { Swal.showValidationMessage('Due Date is required'); return false; }
                if (isNaN(a) || a < 0) { Swal.showValidationMessage('Enter a valid amount'); return false; }
                return {
                    dueDate: d,
                    amount: a,
                    status: document.getElementById('editInstStatus').value,
                    notes: document.getElementById('editInstNotes').value
                };
            }
        });

        // Helper: refresh rows in the parent modal and re-show it
        const reopenFeeModal = async () => {
            if (!feeInstModalEl) return;
            const nameEl = document.getElementById('feeInstStudentName');
            const regNo = nameEl?.dataset?.regNo;
            if (regNo) {
                try {
                    const res = await fetch(`/api/fees-manager/installments/reg/${regNo}`);
                    if (res.ok) {
                        const updatedList = await res.json();
                        const tbody = document.getElementById('feeInstallmentsBody');
                        if (tbody) {
                            tbody.innerHTML = '';
                            updatedList.forEach(inst => {
                                tbody.appendChild(buildFeeInstallmentRow(
                                    inst.dueDate || '',
                                    inst.amount != null ? parseFloat(inst.amount).toFixed(2) : '',
                                    inst.status || 'Pending',
                                    inst.notes || '',
                                    inst.id,
                                    inst.createdBy,
                                    inst.createdAt,
                                    inst.updatedBy,
                                    inst.updatedAt
                                ));
                            });
                            recalculateFeeInstallmentTotal();
                            setupFeeInstallmentsAuditLogic();
                        }
                    }
                } catch (e) {
                    console.warn('Could not refresh installment rows:', e);
                }
            }
            bootstrap.Modal.getOrCreateInstance(feeInstModalEl).show();
        };

        if (formValues) {
            try {
                showLoading('Updating installment...');
                const csrfToken = getCsrfToken();
                const headers = { 'Accept': 'application/json', 'Content-Type': 'application/json' };
                if (csrfToken) headers[getCsrfHeader()] = csrfToken;

                const response = await fetch(`/api/fees-manager/installments/${id}`, {
                    method: 'PUT',
                    headers: headers,
                    body: JSON.stringify(formValues),
                    credentials: 'include'
                });

                if (!response.ok) throw new Error('Failed to update installment');

                Swal.close();
                showSuccess('Installment updated successfully!');
                loadAdmissions(currentPage, pageSize);
                await reopenFeeModal();

            } catch (error) {
                Swal.close();
                showError(error.message || 'Failed to update installment');
                await reopenFeeModal();
            }
        } else {
            // User cancelled — re-open the parent modal
            await reopenFeeModal();
        }
    };
    // ==================== OPEN TRANSFER MODAL WITH INSTALLMENTS ====================

    async function openTransferModal(id) {
        try {
            showLoading('Loading admission...');

            const response = await fetch(`/api/admissions/${id}`, {
                method: 'GET',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) throw new Error('Failed to load admission');

            const admission = await response.json();
            Swal.close();

            // Populate transfer fields
            document.getElementById('transferStudentName').textContent = admission.studentName;
            document.getElementById('transferFirstName').value = admission.firstName;
            document.getElementById('transferMiddleName').value = admission.middleName || '';
            document.getElementById('transferLastName').value = admission.lastName;
            document.getElementById('transferCollege').value = admission.college || '';
            document.getElementById('transferQualification').value = admission.qualification || '';
            document.getElementById('transferDob').value = admission.birthDate || '';
            document.getElementById('transferGender').value = admission.gender || '';
            document.getElementById('transferBloodGroup').value = admission.bloodGroup || '';

            document.getElementById('transferMobilePrimary').value = admission.mobilePrimary;
            document.getElementById('transferMobileSecondary').value = admission.mobileSecondary || '';
            document.getElementById('transferEmailPrimary').value = admission.emailPrimary || '';
            document.getElementById('transferCurrentAddress').value = admission.currentAddress || '';
            document.getElementById('transferDocument').value = admission.documentType || '';
            document.getElementById('transferLeadSource').value = admission.leadSource || '';

            await loadTransferPackages();
            await loadTransferCourses();
            await loadTransferBatches();

            document.getElementById('transferPackage').value = admission.packageName || '';
            document.getElementById('transferTotalFees').value = admission.totalPayableFees || 0;
            document.getElementById('transferReceivableFees').value = admission.totalReceivableFees || 0;
            document.getElementById('transferDiscountPercent').value = admission.discountPercent || 0;
            document.getElementById('transferDiscountAmount').value = admission.discountAmount || 0;

            if (admission.batchesList) {
                const batchSelect = document.getElementById('transferBatch');
                Array.from(batchSelect.options).forEach(option => {
                    option.selected = admission.batchesList.includes(option.value);
                });
            }

            //  DISPLAY EXISTING INSTALLMENTS IN TRANSFER MODAL
            if (admission.installments && admission.installments.length > 0) {
                displayTransferInstallments(admission.installments);
            }

            document.getElementById('transferDate').value = new Date().toISOString().split('T')[0];
            document.getElementById('btnTransferAdmission').dataset.admissionId = id;

            const modal = new bootstrap.Modal(document.getElementById('transferAdmissionModal'));
            modal.show();

        } catch (error) {
            Swal.close();
            console.error('Error:', error);
            showError('Failed to load admission');
        }
    }

    // ==================== DISPLAY INSTALLMENTS IN TRANSFER MODAL ====================

    function displayTransferInstallments(installments) {
        let installmentSection = document.getElementById('transferInstallmentsSection');

        if (!installmentSection) {
            const tab5Content = document.getElementById('transferTab5');
            installmentSection = document.createElement('div');
            installmentSection.id = 'transferInstallmentsSection';
            installmentSection.className = 'col-12 mt-4';
            installmentSection.innerHTML = `
               <h6 class="text-primary mb-3"><u>Existing Installments</u></h6>
               <div class="table-responsive">
                   <table class="table table-sm table-bordered">
                       <thead class="table-light">
                           <tr>
                               <th>Installment</th>
                               <th>Due Date</th>
                               <th>Amount</th>
                               <th>Status</th>
                           </tr>
                       </thead>
                       <tbody id="transferInstallmentsBody"></tbody>
                   </table>
               </div>
           `;
            tab5Content.querySelector('.row').appendChild(installmentSection);
        }

        const tbody = document.getElementById('transferInstallmentsBody');
        tbody.innerHTML = installments.map((inst, index) => `
           <tr>
               <td><strong>#${index + 1}</strong></td>
               <td>${inst.dueDate}</td>
               <td>₹${parseFloat(inst.amount).toFixed(2)}</td>
               <td>
                   <span class="badge bg-${inst.status === 'Paid' ? 'success' :
                inst.status === 'Overdue' ? 'danger' : 'warning'
            }">
                       ${inst.status}
                   </span>
               </td>
           </tr>
       `).join('');
    }

    async function loadTransferPackages() {
        const response = await fetch('/api/packages/dropdown');
        const packages = await response.json();
        const select = document.getElementById('transferPackage');
        select.innerHTML = '<option value="">-- Select Package --</option>';
        packages.forEach(pkg => {
            const option = document.createElement('option');
            option.value = pkg.id;
            option.textContent = `${pkg.packageName} (₹${pkg.totalAmount})`;
            select.appendChild(option);
        });
    }

    async function loadTransferCourses() {
        const response = await fetch('/api/courses/dropdown');
        const courses = await response.json();
        const select = document.getElementById('transferCourse');
        select.innerHTML = '<option value="">-- Select Course --</option>';
        courses.forEach(course => {
            const option = document.createElement('option');
            option.value = course.id;
            option.textContent = `${course.courseName} (₹${course.courseFees})`;
            select.appendChild(option);
        });
    }

    async function loadTransferBatches() {
        const response = await fetch('/api/batches?page=0&size=100');
        const data = await response.json();
        const select = document.getElementById('transferBatch');
        select.innerHTML = '';
        data.batches.forEach(batch => {
            const option = document.createElement('option');
            option.value = batch.batchNo;
            option.textContent = `${batch.batchName} (${batch.startTime} - ${batch.endTime})`;
            select.appendChild(option);
        });
    }

    // Handle transfer submission
    document.getElementById('btnTransferAdmission')?.addEventListener('click', async function () {

        // Show disabled message
        Swal.fire({
            icon: 'warning',
            title: 'Feature Temporarily Disabled',
            html: `
                        <div class="alert alert-warning">
                            <i class="bi bi-exclamation-triangle me-2"></i>
                            <strong>Transfer Admission is temporarily disabled</strong>
                        </div>
                        <p class="mt-3">This feature is currently under maintenance.</p>
                        <p class="text-muted">Please contact your Super Admin for assistance.</p>
                    `,
            confirmButtonText: 'OK',
            confirmButtonColor: '#f59e0b'
        });
        return;
        /*

        const admissionId = this.dataset.admissionId;

        const transferData = {
            admissionId: parseInt(admissionId),
            academicYear: getValue('transferAcademicYear'),
            transferDate: getValue('transferDate'),
            transferReason: getValue('transferReason'),
            courses: [getValue('transferCourse')],
            batches: (() => {
                const el = document.getElementById('transferBatch');
                return el ? Array.from(el.selectedOptions || []).map(o => o.value) : [];
            })(),
            subjects: (() => {
                const el = document.getElementById('transferSubject');
                return el ? Array.from(el.selectedOptions || []).map(o => o.textContent) : [];
            })(),
            packageName: document.getElementById('transferPackage').options[document.getElementById('transferPackage').selectedIndex]?.text,
            totalPayableFees: parseFloat(getValue('transferTotalFees')),
            totalReceivableFees: parseFloat(getValue('transferReceivableFees')),
            discountPercent: parseFloat(getValue('transferDiscountPercent')),
            discountAmount: parseFloat(getValue('transferDiscountAmount'))
        };

        try {
            showLoading('Transferring admission...');

            const response = await fetch('/api/admissions/transfer', {
                method: 'POST',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(transferData)
            });

            if (!response.ok) throw new Error('Transfer failed');

            Swal.close();
            closeModal('transferAdmissionModal');
            showSuccess('Admission transferred successfully!');
            loadAdmissions();

        } catch (error) {
            Swal.close();
            console.error('Error:', error);
            showError('Failed to transfer admission');
        }
        */
    });

    // ==================== PRINT ADMISSION FORM - NEW WINDOW APPROACH ====================

    async function printAdmission(id) {
        try {
            showLoading('Loading admission form...');

            const response = await fetch(`/api/admissions/${id}`, {
                method: 'GET',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) throw new Error('Failed to load admission');

            const admission = await response.json();
            Swal.close();

            // Open print form in new window
            openPrintWindow(admission);

        } catch (error) {
            Swal.close();
            console.error('Error:', error);
            showError('Failed to generate print form');
        }
    }

    function openPrintWindow(admission) {
        // Create new window
        const printWindow = window.open('', '_blank', 'width=900,height=800,scrollbars=yes');

        if (!printWindow) {
            showError('Please allow pop-ups to print the admission form');
            return;
        }

        // Build full HTML document
        const htmlContent = buildPrintHTML(admission);

        // Write to new window
        printWindow.document.open();
        printWindow.document.write(htmlContent);
        printWindow.document.close();

        // Wait for images to load, then show print dialog
        printWindow.onload = function () {
            setTimeout(() => {
                printWindow.focus();
                printWindow.print();
            }, 500);
        };
    }

    function buildPrintHTML(admission) {
        // Prepare photo URL
        let photoUrl = '/assets/images/user-logo.png'; // Default
        if (admission.photoPath && admission.photoPath.trim() !== '') {
            photoUrl = `/uploads/admissions/${admission.photoPath}`;
        }

        // Prepare courses
        const courses = admission.coursesList && admission.coursesList.length > 0
            ? admission.coursesList.join(', ')
            : (admission.courses || '-');

        return `
   <!DOCTYPE html>
   <html lang="en">
   <head>
       <meta charset="UTF-8">
       <meta name="viewport" content="width=device-width, initial-scale=1.0">
       <title>Admission Form - ${admission.registrationNumber || 'Print'}</title>
       <style>
           /* Reset */
           * {
               margin: 0;
               padding: 0;
               box-sizing: border-box;
           }

           /* Page Setup */
           @page {
               size: A4 portrait;
               margin: 12mm;
           }

           body {
               font-family: 'Arial', 'Helvetica', sans-serif;
               font-size: 10pt;
               line-height: 1.4;
               color: #000;
               background: #fff;
               padding: 0;
               margin: 0;
           }

           /* Single Professional Border */
           .form-container {
               border: 2px solid #2c3e50;
               padding: 15mm;
               width: 100%;
               max-width: 210mm;
               margin: 0 auto;
               background: #fff;
               page-break-inside: avoid;
           }

           /* Header Section */
           .header {
               display: flex;
               align-items: center;
               justify-content: space-between;
               padding-bottom: 12px;
               border-bottom: 2px solid #34495e;
               margin-bottom: 12px;
           }

           .header-left {
               display: flex;
               align-items: center;
               flex: 1;
           }

           .logo {
               width: 70px;
               height: 70px;
               margin-right: 15px;
               flex-shrink: 0;
           }

           .logo img {
               width: 100%;
               height: 100%;
               object-fit: contain;
           }

           .company-info {
               flex: 1;
           }

           .company-name {
               font-size: 16pt;
               font-weight: bold;
               margin-bottom: 3px;
               color: #2c3e50;
               letter-spacing: 0.5px;
           }

           .company-details {
               font-size: 9pt;
               line-height: 1.4;
               margin: 2px 0;
               color: #34495e;
           }

           /* Title */
           .form-title {
               text-align: center;
               font-size: 18pt;
               font-weight: bold;
               text-transform: uppercase;
               margin: 12px 0 10px 0;
               padding: 8px 0;
               background: #ecf0f1;
               border-left: 4px solid #3498db;
               letter-spacing: 2px;
               color: #2c3e50;
           }

           /* Registration & Photo Section */
           .reg-photo-section {
               display: flex;
               justify-content: space-between;
               align-items: flex-start;
               margin-bottom: 12px;
               padding-bottom: 10px;
               border-bottom: 1px solid #bdc3c7;
           }

           .reg-info {
               flex: 1;
           }

           .reg-info p {
               font-size: 10pt;
               margin: 5px 0;
               color: #2c3e50;
           }

           .reg-info strong {
               font-weight: 600;
               min-width: 120px;
               display: inline-block;
               color: #34495e;
           }

           .student-photo {
               width: 90px;
               height: 120px;
               border: 2px solid #34495e;
               display: flex;
               align-items: center;
               justify-content: center;
               background: #ecf0f1;
               flex-shrink: 0;
               margin-left: 15px;
           }

           .student-photo img {
               width: 100%;
               height: 100%;
               object-fit: cover;
           }

           /* Section Headers */
           .section-title {
               font-size: 11pt;
               font-weight: bold;
               margin: 10px 0 6px 0;
               padding: 4px 8px;
               background: #34495e;
               color: #fff;
               border-radius: 2px;
           }

           /* Form Fields - Compact */
           .form-row {
               margin: 4px 0;
               font-size: 9.5pt;
               line-height: 1.5;
               display: flex;
           }

           .form-row strong {
               font-weight: 600;
               color: #2c3e50;
               min-width: 150px;
               flex-shrink: 0;
           }

           .form-row span {
               flex: 1;
               color: #34495e;
           }

           /* Two Column Layout - Compact */
           .two-column {
               display: grid;
               grid-template-columns: 1fr 1fr;
               gap: 8px 15px;
               margin: 6px 0;
           }

           .two-column .form-row {
               margin: 0;
           }

           .two-column .form-row strong {
               min-width: 100px;
           }

           /* Declaration - Compact */
           .declaration {
               margin-top: 10px;
               padding: 8px;
               border: 1px solid #34495e;
               background: #f8f9fa;
               page-break-inside: avoid;
           }

           .declaration-content {
               font-size: 9pt;
               font-weight: 600;
               margin-bottom: 8px;
               color: #2c3e50;
           }

           .declaration-fields {
               display: flex;
               justify-content: space-between;
               align-items: center;
               margin-top: 8px;
           }

           .declaration-fields p {
               font-size: 9pt;
               margin: 0;
               color: #34495e;
           }

           .declaration-fields strong {
               font-weight: 600;
           }

           .underline {
               display: inline-block;
               min-width: 120px;
               border-bottom: 1px solid #000;
               margin-left: 5px;
           }

           /* Signature */
           .signature-section {
               margin-top: 15px;
               text-align: right;
           }

           .signature-section p {
               font-size: 10pt;
               font-weight: 600;
               margin: 0;
               color: #2c3e50;
           }

           .signature-line {
               display: inline-block;
               min-width: 200px;
               border-top: 1px solid #000;
               margin-top: 40px;
               padding-top: 5px;
           }

           /* Print Styles */
           @media print {
               body {
                   margin: 0;
                   padding: 0;
               }

               .form-container {
                   border: 2px solid #000;
                   page-break-inside: avoid;
               }

               @page {
                   margin: 10mm;
               }

               /* Ensure colors print */
               * {
                   -webkit-print-color-adjust: exact !important;
                   print-color-adjust: exact !important;
               }
           }

           /* No Photo Placeholder */
           .no-photo {
               color: #7f8c8d;
               font-size: 9pt;
               text-align: center;
               font-style: italic;
           }

           /* Address Fields - Allow wrap */
           .form-row.address {
               display: block;
           }

           .form-row.address strong {
               display: block;
               margin-bottom: 2px;
           }

           .form-row.address span {
               display: block;
               padding-left: 10px;
           }
       </style>
   </head>
   <body>
       <div class="form-container">
           <!-- Header -->
       <!--    <div class="header">
               <div class="header-left">
                   <div class="logo">
                       <img src="/assets/images/tts-logo-ev.png" alt="TTS Logo" onerror="this.style.display='none'">
                   </div>
                   <div class="company-info">
                       <div class="company-name">TechnoKraft Training & Solutions</div>
                       <div class="company-details">1st Floor, Kanchwala Avenue, Above Viju's Dabeli, College Road, Nashik</div>
                       <div class="company-details">
                           <strong>E-mail:</strong> info@tts.net.in &nbsp;|&nbsp;
                           <strong>Mobile:</strong> 02332312447
                       </div>
                   </div>
               </div>
           </div> -->

           <!-- Title -->
           <div class="form-title">Admission Form</div>

           <!-- Registration & Photo -->
           <div class="reg-photo-section">
               <div class="reg-info">
                   <p><strong>Reg. No:</strong> ${admission.registrationNumber || '-'}</p>
                   <p><strong>Admission Date:</strong> ${admission.admissionDate || '-'}</p>
               </div>
               <div class="student-photo">
                   <img src="${photoUrl}" alt="Student Photo" onerror="this.outerHTML='<div class=\\'no-photo\\'>No Photo Available</div>'">
               </div>
           </div>

           <!-- Personal Details -->
           <div class="section-title">Personal Details</div>
           <div class="form-row">
               <strong>1. Student Name:</strong>
               <span>${admission.studentName || '-'}</span>
           </div>
           <div class="form-row">
               <strong>2. Birth Date:</strong>
               <span>${admission.birthDate || '-'}</span>
           </div>
           <div class="form-row">
               <strong>3. Gender:</strong>
               <span>${admission.gender || '-'}</span>
           </div>

           <div class="two-column">
               <div class="form-row">
                   <strong>4. Aadhaar No:</strong>
                   <span>${admission.aadhaar || '-'}</span>
               </div>
               <div class="form-row">
                   <strong>5. Blood Group:</strong>
                   <span>${admission.bloodGroup || '-'}</span>
               </div>
               <div class="form-row">
                   <strong>6. Category:</strong>
                   <span>${admission.category || '-'}</span>
               </div>
               <div class="form-row">
                   <strong>7. Cast:</strong>
                   <span>${admission.cast || '-'}</span>
               </div>
               <div class="form-row">
                   <strong>8. Qualification:</strong>
                   <span>${admission.qualification || '-'}</span>
               </div>
               <div class="form-row">
                   <strong>9. College:</strong>
                   <span>${admission.college || '-'}</span>
               </div>
           </div>

           <!-- Communication Details -->
           <div class="section-title">Communication Details</div>
           <div class="two-column">
               <div class="form-row">
                   <strong>10. Primary Mobile:</strong>
                   <span>${admission.mobilePrimary || '-'}</span>
               </div>
               <div class="form-row">
                   <strong>11. Secondary Mobile:</strong>
                   <span>${admission.mobileSecondary || '-'}</span>
               </div>
               <div class="form-row">
                   <strong>12. Primary Email:</strong>
                   <span>${admission.emailPrimary || '-'}</span>
               </div>
               <div class="form-row">
                   <strong>13. Secondary Email:</strong>
                   <span>${admission.emailSecondary || '-'}</span>
               </div>
           </div>

           <div class="form-row address">
               <strong>14. Current Address:</strong>
               <span>${admission.currentAddress || '-'}</span>
           </div>
           <div class="form-row address">
               <strong>15. Permanent Address:</strong>
               <span>${admission.permanentAddress || '-'}</span>
           </div>

           <!-- Course Details -->
           <div class="section-title">Course Details</div>
           <div class="form-row">
               <strong>16. Courses:</strong>
               <span>${courses}</span>
           </div>
           <div class="form-row">
               <strong>17. Document Submitted:</strong>
               <span>${admission.documentType || '-'}</span>
           </div>
           <div class="form-row">
               <strong>18. Notes:</strong>
               <span>${admission.notes || '-'}</span>
           </div>

           <!-- Declaration -->
           <div class="section-title">Declaration</div>
           <div class="declaration">
               <div class="declaration-content">
                   I hereby declare that above furnished information are true to the best of my knowledge and belief.
               </div>
               <div class="declaration-fields">
                   <p><strong>Place:</strong><span class="underline"></span></p>
                   <p><strong>Date:</strong><span class="underline"></span></p>
               </div>
           </div>

           <!-- Signature -->
           <div class="signature-section">
               <div class="signature-line">Student's Signature</div>
           </div>
       </div>

       <script>
           // Close window after printing
           window.onafterprint = function() {
               window.close();
           };
       </script>
   </body>
   </html>
       `;
    }

    // Make function globally available
    window.printAdmission = printAdmission;
    window.printToPDF = function () {
        window.print();
    };

    // ==================== DELETE ADMISSION ====================
    async function deleteAdmission(id) {
        const result = await Swal.fire({
            title: 'Delete Admission?',
            text: 'This action cannot be undone',
            icon: 'warning',
            showCancelButton: true,
            confirmButtonText: 'Yes, Delete',
            cancelButtonText: 'Cancel',
            confirmButtonColor: '#ef4444'
        });

        if (result.isConfirmed) {
            try {
                showLoading('Deleting admission...');

                const csrfToken = getCsrfToken();
                const headers = {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                };
                if (csrfToken) headers[getCsrfHeader()] = csrfToken;

                const response = await fetch(`/api/admissions/${id}`, {
                    method: 'DELETE',
                    headers: headers,
                    credentials: 'include'
                });

                if (!response.ok) throw new Error('Failed to delete admission');

                Swal.close();
                showSuccess('Admissiondeleted successfully!');
                loadAdmissions();
            } catch (error) {
                Swal.close();
                console.error('Error:', error);
                showError('Failed to delete admission');
            }
        }
    }

    // Camera Functions
    async function capturePhoto() {
        try {
            const video = document.getElementById('admVideo');
            const canvas = document.getElementById('admCanvas');

            if (!videoStream) {
                videoStream = await navigator.mediaDevices.getUserMedia({
                    video: { width: 300, height: 300 }
                });
                video.srcObject = videoStream;

                Swal.fire({
                    title: 'Camera Ready',
                    text: 'Click "Capture Photo" again to take a picture',
                    icon: 'info',
                    timer: 2000,
                    showConfirmButton: false
                });
            } else {
                const context = canvas.getContext('2d');
                context.drawImage(video, 0, 0, 300, 300);

                videoStream.getTracks().forEach(track => track.stop());
                videoStream = null;
                video.srcObject = null;

                Swal.fire({
                    title: 'Photo Captured!',
                    text: 'Photo has been captured successfully',
                    icon: 'success',
                    timer: 1500,
                    showConfirmButton: false
                });
            }
        } catch (error) {
            console.error('Camera error:', error);
            showError('Failed to access camera. Please check permissions.');
        }
    }

    function handlePhotoUpload(file) {
        if (!file) return;

        const canvas = document.getElementById('admCanvas');
        const ctx = canvas.getContext('2d');
        const img = new Image();

        img.onload = function () {
            ctx.drawImage(img, 0, 0, 300, 300);

            Swal.fire({
                title: 'Photo Uploaded!',
                text: 'Photo has been uploaded successfully',
                icon: 'success',
                timer: 1500,
                showConfirmButton: false
            });
        };

        const reader = new FileReader();
        reader.onload = (e) => {
            img.src = e.target.result;
        };
        reader.readAsDataURL(file);
    }

    // ==================== INSTALLMENT GENERATION ====================

    function generateInstallments() {
        const totalAmount = parseFloat(getValue('instTotalAmount')) || 0;
        const noOfInstallments = parseInt(getValue('instNoOfInstallments')) || 0;
        const daysBetween = parseInt(getValue('instDays')) || 30;
        const startDate = getValue('instStartDate');

        if (!totalAmount || !noOfInstallments || !startDate) {
            showError('Please fill Total Amount, No. of Installments and Start Date');
            return;
        }

        const amountPerInstallment = totalAmount / noOfInstallments;
        const tbody = document.getElementById('installmentsBody');
        let currentDate = new Date(startDate);

        tbody.innerHTML = '';

        for (let i = 1; i <= noOfInstallments; i++) {
            const formattedDate = currentDate.toISOString().split('T')[0];
            tbody.appendChild(buildInstallmentRow(formattedDate, amountPerInstallment.toFixed(2), 'Pending'));
            currentDate.setDate(currentDate.getDate() + daysBetween);
        }

        recalculateInstallmentTotal();
        showSuccess(`Generated ${noOfInstallments} installments`);
    }

    window.removeInstallment = function (button) {
        button.closest('tr').remove();
        recalculateInstallmentTotal();
        const tbody = document.getElementById('installmentsBody');
        if (tbody && tbody.querySelectorAll('tr').length === 0) {
            tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted">No installments generated</td></tr>';
            setValue('instTotalInstAmount', '0');
            const rowTotalEl = document.getElementById('instRowTotal');
            if (rowTotalEl) rowTotalEl.textContent = '\u20b90.00';
        }
    };

    function generateFeeInstallments() {
        const totalAmount = parseFloat(getValue('feeInstTotalAmount')) || 0;
        const noOfInstallments = parseInt(getValue('feeInstNoOfInstallments')) || 0;
        const daysBetween = parseInt(getValue('feeInstDays')) || 30;
        const startDate = getValue('feeInstStartDate');

        if (!totalAmount || !noOfInstallments || !startDate) {
            showError('Please fill Total Amount, No. of Installments and Start Date');
            return;
        }

        const amountPerInstallment = totalAmount / noOfInstallments;
        const tbody = document.getElementById('feeInstallmentsBody');
        let currentDate = new Date(startDate);

        tbody.innerHTML = '';

        for (let i = 1; i <= noOfInstallments; i++) {
            const formattedDate = currentDate.toISOString().split('T')[0];
            tbody.appendChild(buildFeeInstallmentRow(formattedDate, amountPerInstallment.toFixed(2), 'Pending', ''));
            currentDate.setDate(currentDate.getDate() + daysBetween);
        }

        recalculateFeeInstallmentTotal();
        setupFeeInstallmentsAuditLogic();
        showSuccess(`Generated ${noOfInstallments} installments`);
    }


    async function saveFeeInstallments() {
        const tbody = document.getElementById('feeInstallmentsBody');
        const regNo = document.getElementById('feeInstStudentName')?.dataset?.regNo;

        if (!regNo) {
            showError('Student registration number not found');
            return;
        }

        const rows = tbody.querySelectorAll('tr');
        if (rows.length === 0 || (rows.length === 1 && rows[0].cells.length === 1)) {
            showError('No installments to save. Please generate or add installments first.');
            return;
        }

        // Collect inline-editable row data
        const installments = [];
        let idx = 1;
        let valid = true;
        rows.forEach(row => {
            const dateInput = row.querySelector('.fee-inst-date');
            const amountInput = row.querySelector('.fee-inst-amount');
            const statusSelect = row.querySelector('.fee-inst-status');
            const notesInput = row.querySelector('.fee-inst-notes');
            if (!dateInput || !amountInput) return;
            const dueDate = dateInput.value;
            const amount = parseFloat(amountInput.value);
            if (!dueDate || isNaN(amount) || amount < 0) {
                valid = false;
                return;
            }
            installments.push({
                installmentNumber: idx++,
                dueDate: dueDate,
                amount: amount,
                status: statusSelect ? statusSelect.value : 'Pending',
                notes: notesInput ? notesInput.value : ''
            });
        });

        if (!valid) {
            showError('Please fill valid Date and Amount for all installment rows.');
            return;
        }

        if (installments.length === 0) {
            showError('No valid installments to save.');
            return;
        }

        //  Amount-match validation 
        const feeInstTotal = parseFloat(document.getElementById('feeInstTotalAmount')?.value) || 0;
        const feeInstSum = installments.reduce((s, i) => s + i.amount, 0);
        const feeInstDiff = Math.round((feeInstSum - feeInstTotal) * 100) / 100; // round to 2dp

        if (feeInstDiff > 0) {
            Swal.fire({
                icon: 'error',
                title: 'Amount Mismatch',
                html: `
                                  <div class="text-start">
                                      <p class="mb-2">The total of all installment amounts <strong class="text-danger">cannot be greater than</strong> the Total Receivable Amount.</p>
                                      <div class="row g-2" style="font-size:0.88rem;">
                                          <div class="col-6 text-muted">Total Receivable:</div>
                                          <div class="col-6 fw-bold text-primary">&#8377;${feeInstTotal.toFixed(2)}</div>
                                          <div class="col-6 text-muted">Installments Sum:</div>
                                          <div class="col-6 fw-bold text-danger">&#8377;${feeInstSum.toFixed(2)}</div>
                                          <div class="col-6 text-muted">Excess:</div>
                                          <div class="col-6 fw-bold text-danger">+ &#8377;${feeInstDiff.toFixed(2)}</div>
                                      </div>
                                      <p class="mt-2 mb-0 text-muted" style="font-size:0.82rem;">Please reduce the installment amounts by &#8377;${feeInstDiff.toFixed(2)} before saving.</p>
                                  </div>`,
                confirmButtonColor: '#dc2626',
                confirmButtonText: 'Fix Amounts'
            });
            return;
        }

        if (feeInstDiff < 0) {
            const short = Math.abs(feeInstDiff);
            Swal.fire({
                icon: 'warning',
                title: 'Amount Mismatch',
                html: `
                                  <div class="text-start">
                                      <p class="mb-2">The total of all installment amounts <strong class="text-warning">cannot be less than</strong> the Total Receivable Amount.</p>
                                      <div class="row g-2" style="font-size:0.88rem;">
                                          <div class="col-6 text-muted">Total Receivable:</div>
                                          <div class="col-6 fw-bold text-primary">&#8377;${feeInstTotal.toFixed(2)}</div>
                                          <div class="col-6 text-muted">Installments Sum:</div>
                                          <div class="col-6 fw-bold text-warning">&#8377;${feeInstSum.toFixed(2)}</div>
                                          <div class="col-6 text-muted">Shortfall:</div>
                                          <div class="col-6 fw-bold text-warning">- &#8377;${short.toFixed(2)}</div>
                                      </div>
                                      <p class="mt-2 mb-0 text-muted" style="font-size:0.82rem;">Please add &#8377;${short.toFixed(2)} more across the installment rows before saving.</p>
                                  </div>`,
                confirmButtonColor: '#d97706',
                confirmButtonText: 'Fix Amounts'
            });
            return;
        }

        try {
            showLoading('Saving installments...');

            const csrfToken = getCsrfToken();
            const headers = { 'Accept': 'application/json', 'Content-Type': 'application/json' };
            if (csrfToken) headers[getCsrfHeader()] = csrfToken;

            const payload = {
                registrationNumber: regNo,
                installments: installments
            };

            const response = await fetch(`/api/fees-manager/installments/${regNo}`, {
                method: 'POST',
                headers: headers,
                credentials: 'include',
                body: JSON.stringify(payload)
            });

            if (!response.ok) {
                const err = await response.json();
                throw new Error(err.message || 'Failed to save installments');
            }

            Swal.close();
            showSuccess('Fee installments saved and synced successfully!');
            closeModal('feeInstallmentsModal');
            loadAdmissions(currentPage, pageSize);

        } catch (error) {
            Swal.close();
            showError(error.message || 'Failed to save installments');
        }
    }

    function removeCourse(button) {
        const row = button.closest('tr');
        row.remove();

        const tbody = document.getElementById('selectedCoursesBody');
        if (tbody.querySelectorAll('tr').length === 0) {
            tbody.innerHTML = '<tr><td colspan="3" class="text-center text-muted">No courses added</td></tr>';
        }
    }

    // Import Functions
    function openImportModal() {
        const modal = new bootstrap.Modal(document.getElementById('importAdmissionsModal'));
        modal.show();
    }

    function handleImportTypeChange() {
        const oldFormatInfo = document.getElementById('admOldFormatInfo');
        const newFormatInfo = document.getElementById('admNewFormatInfo');
        const isOld = this.value === 'old';

        oldFormatInfo.style.display = isOld ? 'block' : 'none';
        newFormatInfo.style.display = isOld ? 'none' : 'block';

        resetImport();
    }

    function resetImport() {
        document.getElementById('admImportPreview').style.display = 'none';
        document.getElementById('admCsvFileInput').value = '';
        document.getElementById('btnImportAdmData').disabled = true;
        importedAdmissions = [];
    }

    function handleCSVFile(file) {
        if (!file) return;
        const reader = new FileReader();
        reader.onload = (e) => parseCSV(e.target.result);
        reader.readAsText(file);
    }

    function parseCSV(text) {
        const lines = text.split('\n').filter(line => line.trim());
        const importType = document.querySelector('input[name="admImportType"]:checked').value;

        importedAdmissions = [];
        const previewData = [];

        for (let i = 1; i < lines.length; i++) {
            const values = lines[i].match(/(".*?"|[^,\t]+)(?=\s*[,\t]|\s*$)/g) || [];
            const row = values.map(v => v.trim().replace(/^"|"$/g, ''));

            if (row.length === 0) continue;

            let record;

            if (importType === 'old') {
                const nameParts = (row[1] || '').split(' ').filter(Boolean);
                const coursesStr = row[3] || '';
                const coursesList = coursesStr
                    .split(/[,\n]/)
                    .map(c => c.trim())
                    .filter(Boolean);

                record = {
                    registrationNumber: row[0] || null,
                    firstName: nameParts[0] || 'Unknown',
                    middleName: nameParts.length > 2 ? nameParts.slice(1, -1).join(' ') : '',
                    lastName: nameParts.length > 1 ? nameParts[nameParts.length - 1] : 'Student',
                    mobilePrimary: cleanMobile(row[2]) || `temp${i}`,
                    courses: coursesList.length > 0 ? coursesList : ['Not Specified'],
                    admissionDate: parseDate_ddmmyyyy_orDate(row[4]) || new Date().toISOString().split('T')[0],
                    leadSource: 'CSV_IMPORT',
                    documentType: 'Aadhaar Card'
                };
            } else {
                const coursesList = (row[11] || '').split(/[,\n]/).map(c => c.trim()).filter(Boolean);

                record = {
                    registrationNumber: row[0] || null,
                    firstName: row[0] || 'Unknown',
                    middleName: row[1] || '',
                    lastName: row[2] || 'Student',
                    mobilePrimary: cleanMobile(row[3]) || `temp${i}`,
                    mobileSecondary: cleanMobile(row[4]),
                    emailPrimary: row[5],
                    currentAddress: row[6],
                    college: row[7],
                    totalPayableFees: parseFloat(row[8]) || 0,
                    totalReceivableFees: parseFloat(row[9]) || 0,
                    admissionDate: parseDate_ddmmyyyy_orDate(row[10]) || new Date().toISOString().split('T')[0],
                    courses: coursesList.length > 0 ? coursesList : ['Not Specified'],
                    leadSource: row[12] || 'CSV_IMPORT',
                    documentType: 'Aadhaar Card'
                };
            }

            // LENIENT: Import ALL records - NO validation filter
            importedAdmissions.push(record);
            if (previewData.length < 5) previewData.push(record);
        }

        displayPreview(previewData);
        document.getElementById('admRecordCount').textContent = importedAdmissions.length;
        document.getElementById('btnImportAdmData').disabled = importedAdmissions.length === 0;
    }

    function displayPreview(data) {
        const thead = document.getElementById('admPreviewTableHead');
        const tbody = document.getElementById('admPreviewTableBody');

        thead.innerHTML = '<tr><th>First Name</th><th>Last Name</th><th>Mobile</th><th>Course</th></tr>';
        tbody.innerHTML = data.map(row => `
                          <tr>
                              <td>${row.firstName || '-'}</td>
                              <td>${row.lastName || '-'}</td>
                              <td>${row.mobilePrimary || '-'}</td>
                              <td>${Array.isArray(row.courses) ? row.courses.join(', ') : (row.courses || '-')}</td>
                          </tr>
                      `).join('');

        document.getElementById('admImportPreview').style.display = 'block';
    }

    // In admission.js - Update importAdmissions function
    async function importAdmissions() {
        if (importedAdmissions.length === 0) {
            showError('No data to import');
            return;
        }

        try {
            showLoading(`Importing ${importedAdmissions.length} admissions...`);

            const csrfToken = getCsrfToken();
            const headers = {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            };
            if (csrfToken) headers[getCsrfHeader()] = csrfToken;

            const response = await fetch('/api/admissions/bulk-import-json?importSource=OLD_FORMAT', {
                method: 'POST',
                headers: headers,
                credentials: 'include',
                body: JSON.stringify(importedAdmissions)
            });

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.message || 'Import failed');
            }

            const result = await response.json();
            Swal.close();
            closeModal('importAdmissionsModal');

            let message = `Successfully imported ${result.successfulImports} out of ${result.totalRecords} admissions`;
            if (result.failedImports > 0) {
                message += `\n${result.failedImports} records failed`;

                if (result.errors && result.errors.length > 0) {
                    console.warn('Import errors:', result.errors);
                }
            }

            showSuccess(message);
            loadAdmissions();
            resetImport();

        } catch (error) {
            Swal.close();
            console.error('Import error:', error);
            showError(error.message || 'Failed to import admissions');
        }
    }

    function cleanMobile(mobile) {
        if (!mobile) return null;

        const cleaned = mobile.replace(/\D/g, '');

        // Remove country code if present
        if (cleaned.startsWith('91') && cleaned.length === 12) {
            return cleaned.substring(2);
        }

        // Accept 10-digit numbers
        if (cleaned.length === 10) {
            return cleaned;
        }

        return cleaned || null;
    }

    function toIsoDateFromJsDate(d) {
        // Use local date parts (not UTC) because we want calendar date as shown in Excel
        const yyyy = d.getFullYear();
        const mm = String(d.getMonth() + 1).padStart(2, '0');
        const dd = String(d.getDate()).padStart(2, '0');
        return `${yyyy}-${mm}-${dd}`;
    }

    function parseDate_ddmmyyyy_orDate(cell) {
        if (!cell && cell !== 0) return null;

        // If it's already a Date object (e.g. from excel parser), convert to yyyy-mm-dd
        if (cell instanceof Date && !isNaN(cell)) {
            return toIsoDateFromJsDate(cell);
        }

        // If it's a number (Excel sometimes gives timestamps) â€” try to convert
        if (typeof cell === 'number') {
            const d = new Date(cell);
            if (!isNaN(d)) return toIsoDateFromJsDate(d);
        }

        // If it's a string, try to parse DD-MM-YYYY or D/M/YYYY etc.
        const s = String(cell).trim();
        if (!s) return null;

        // If it already looks like ISO yyyy-mm-dd, return it
        if (/^\d{4}-\d{2}-\d{2}$/.test(s)) return s;

        // Try split by - or /
        const parts = s.split(/[-\/]/);
        if (parts.length === 3) {
            // Determine if format is DD-MM-YYYY (common) or MM-DD-YYYY (ambiguous).
            // We'll assume the CSV/display uses DD-MM-YYYY as you showed.
            const [d, m, y] = parts;
            const dd = d.padStart(2, '0');
            const mm = m.padStart(2, '0');
            const yyyy = y.length === 2 ? '20' + y : y;
            // Basic validation
            if (/^\d{2}$/.test(dd) && /^\d{2}$/.test(mm) && /^\d{4}$/.test(yyyy)) {
                return `${yyyy}-${mm}-${dd}`;
            }
        }

        // Last resort: try Date parsing but convert to ISO date (risky, avoid if possible)
        const maybe = new Date(s);
        if (!isNaN(maybe)) return toIsoDateFromJsDate(maybe);

        return null;
    }

    function getTodayDate() {
        return new Date().toISOString().split('T')[0];
    }

    function populateAcademicYears() {
        const select = document.getElementById('admAcademicYear');
        const transferSelect = document.getElementById('transferAcademicYear');
        if (!select) return;

        const now = new Date();
        const currentYear = now.getFullYear();

        // Start 3 years ago and generate 15 options (covers current and 10+ future years)
        const startYear = currentYear - 3;
        const currentValue = select.value;
        select.innerHTML = '';

        if (transferSelect) {
            transferSelect.innerHTML = '<option value="">-- Select Academic Year --</option>';
        }

        for (let i = 0; i < 15; i++) {
            const year = startYear + i;
            const shortEndYear = (year + 1) % 100;
            const shortEndYearStr = String(shortEndYear).padStart(2, '0');
            const optionValue = `${year}-${shortEndYearStr}`; // e.g. "2026-27"

            // Populate admAcademicYear
            const option = document.createElement('option');
            option.value = optionValue;
            option.textContent = optionValue;
            select.appendChild(option);

            // Populate transferAcademicYear (format YYYY-YYYY, e.g. "2026-2027")
            if (transferSelect) {
                const longOptionValue = `${year}-${year + 1}`;
                const transOption = document.createElement('option');
                transOption.value = longOptionValue;
                transOption.textContent = longOptionValue;
                transferSelect.appendChild(transOption);
            }
        }

        // Restore selection
        if (currentValue) {
            select.value = currentValue;
        }
    }

    function getCurrentAcademicYear() {
        const now = new Date();
        const currentYear = now.getFullYear();
        const currentMonth = now.getMonth();
        let startYear = currentYear;
        if (currentMonth < 3) {
            startYear = currentYear - 1;
        }
        const endYear = (startYear + 1) % 100;
        const endYearStr = String(endYear).padStart(2, '0');
        return `${startYear}-${endYearStr}`;
    }

    function selectDefaultAcademicYear() {
        const select = document.getElementById('admAcademicYear');
        if (!select) return;
        const currentAcadYear = getCurrentAcademicYear();
        let exists = false;
        for (let i = 0; i < select.options.length; i++) {
            if (select.options[i].value === currentAcadYear) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            const option = document.createElement('option');
            option.value = currentAcadYear;
            option.textContent = currentAcadYear;
            select.add(option, 0);
        }
        select.value = currentAcadYear;
    }

    // ==================== CLEAR FORMS (RESET FOR NEW ADMISSION) ====================

    function clearForms() {
        ['personalInfoForm', 'otherDetailsForm', 'courseDetailsForm',
            'batchDetailsForm', 'installmentsForm', 'imageUploadForm'].forEach(id => {
                document.getElementById(id)?.reset();
            });

        document.getElementById('admTotalFees').value = '0';
        document.getElementById('admReceivableFees').value = '0';
        document.getElementById('admDiscountPercent').value = '0';
        document.getElementById('admDiscountAmount').value = '0';

        setValue('instTotalAmount', '0');
        setValue('instTotalInstAmount', '0');
        setValue('instStartDate', '');
        setValue('instNoOfInstallments', '');
        setValue('instDays', '');

        // Set default dates to today
        const today = getTodayDate();
        setValue('admAdmissionDate', today);
        setValue('instStartDate', today);

        // Set default academic year dynamically
        selectDefaultAcademicYear();

        const tbody = document.getElementById('selectedCoursesBody');
        if (tbody) {
            tbody.innerHTML = '<tr><td colspan="3" class="text-center text-muted">No courses added</td></tr>';
        }

        const instBody = document.getElementById('installmentsBody');
        if (instBody) {
            instBody.innerHTML = '<tr><td colspan="4" class="text-center text-muted">No installments generated</td></tr>';
        }
    }

    function previousTab() {
        if (currentTab > 1) {
            currentTab--;
            showTab(currentTab);
            updateNavigationButtons();
        }
    }

    function showTab(tabNumber) {
        const tabs = document.querySelectorAll('#admissionTabs .nav-link');
        tabs[tabNumber - 1]?.click();
    }

    function updateNavigationButtons() {
        const btnPrevious = document.getElementById('btnPrevious');
        const btnNext = document.getElementById('btnNext');
        const btnFinish = document.getElementById('btnFinish');

        btnPrevious.style.display = currentTab === 1 ? 'none' : 'inline-block';
        btnNext.style.display = currentTab === totalTabs ? 'none' : 'inline-block';
        btnFinish.style.display = currentTab === totalTabs ? 'inline-block' : 'none';
    }

    function setValue(id, value) {
        const el = document.getElementById(id);
        if (el && value != null && value !== '') {
            el.value = value;
            el.dispatchEvent(new Event('change', { bubbles: true }));
        }
    }

    function getValue(id) {
        const el = document.getElementById(id);
        return el ? el.value : '';
    }

    function closeModal(modalId) {
        const modalEl = document.getElementById(modalId);
        const modal = bootstrap.Modal.getInstance(modalEl);
        if (modal) modal.hide();
    }

    function showLoading(message) {
        Swal.fire({
            title: message,
            allowOutsideClick: false,
            didOpen: () => Swal.showLoading()
        });
    }

    function showSuccess(message) {
        Swal.fire({
            title: 'Success!',
            text: message,
            icon: 'success',
            confirmButtonColor: '#667eea',
            timer: 2000
        });
    }

    function showError(message) {
        Swal.fire({
            title: 'Error!',
            text: message,
            icon: 'error',
            confirmButtonColor: '#ef4444'
        });
    }

    function debounce(func, wait) {
        let timeout;
        return function (...args) {
            clearTimeout(timeout);
            timeout = setTimeout(() => func.apply(this, args), wait);
        };
    }

    function highlightPrefilledFields() {
        const fieldsToHighlight = [
            'admFirstName', 'admMiddleName', 'admLastName',
            'admMobilePrimary', 'admEmailPrimary',
            'admCollege', 'admCurrentAddress'
        ];

        fieldsToHighlight.forEach(fieldId => {
            const field = document.getElementById(fieldId);
            if (field && field.value) {
                field.style.backgroundColor = '#e0f2fe';
                field.style.transition = 'background-color 2s';

                setTimeout(() => {
                    field.style.backgroundColor = '';
                }, 3000);
            }
        });
    }

    function displaySelectedCourses(courses) {
        const tbody = document.getElementById('selectedCoursesBody');
        if (!tbody || !courses || courses.length === 0) return;

        tbody.innerHTML = courses.map((course, index) => `
                    <tr>
                        <td>${course}</td>
                        <td><input type="number" class="form-control form-control-sm" value="0" id="courseAmount${index}"></td>
                        <td>
                            <button class="btn btn-sm btn-danger" onclick="window.removeCourse(this)">
                                <i class="bi bi-trash"></i>
                            </button>
                        </td>
                    </tr>
                `).join('');
    }

    // Update pagination info text
    function updatePaginationInfo() {
        const start = totalElements === 0 ? 0 : (currentPage * pageSize) + 1;
        const end = Math.min((currentPage + 1) * pageSize, totalElements);

        document.getElementById('entriesStart').textContent = start;
        document.getElementById('entriesEnd').textContent = end;
        document.getElementById('totalEntries').textContent = totalElements;
    }

    // Render pagination controls
    function renderPaginationControls() {
        const paginationControls = document.getElementById('paginationControls');

        if (totalPages <= 1) {
            paginationControls.innerHTML = '';
            return;
        }

        let html = '';

        // Previous button
        html += `
          <li class="page-item ${currentPage === 0 ? 'disabled' : ''}">
              <a class="page-link" href="#" data-page="${currentPage - 1}">
                  <i class="bi bi-chevron-left"></i>
              </a>
          </li>
      `;

        // Page numbers
        const maxVisiblePages = 5;
        let startPage = Math.max(0, currentPage - Math.floor(maxVisiblePages / 2));
        let endPage = Math.min(totalPages - 1, startPage + maxVisiblePages - 1);

        if (endPage - startPage < maxVisiblePages - 1) {
            startPage = Math.max(0, endPage - maxVisiblePages + 1);
        }

        // First page
        if (startPage > 0) {
            html += `
              <li class="page-item">
                  <a class="page-link" href="#" data-page="0">1</a>
              </li>
          `;
            if (startPage > 1) {
                html += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
            }
        }

        // Page numbers
        for (let i = startPage; i <= endPage; i++) {
            html += `
              <li class="page-item ${i === currentPage ? 'active' : ''}">
                  <a class="page-link" href="#" data-page="${i}">${i + 1}</a>
              </li>
          `;
        }

        // Last page
        if (endPage < totalPages - 1) {
            if (endPage < totalPages - 2) {
                html += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
            }
            html += `
              <li class="page-item">
                  <a class="page-link" href="#" data-page="${totalPages - 1}">${totalPages}</a>
              </li>
          `;
        }

        // Next button
        html += `
          <li class="page-item ${currentPage === totalPages - 1 ? 'disabled' : ''}">
              <a class="page-link" href="#" data-page="${currentPage + 1}">
                  <i class="bi bi-chevron-right"></i>
              </a>
          </li>
      `;

        paginationControls.innerHTML = html;

        // Attach click handlers
        paginationControls.querySelectorAll('a.page-link').forEach(link => {
            link.addEventListener('click', function (e) {
                e.preventDefault();
                const page = parseInt(this.getAttribute('data-page'));
                if (!isNaN(page) && page !== currentPage && page >= 0 && page < totalPages) {
                    loadAdmissions(page, pageSize);
                }
            });
        });
    }

    // Manual dropdown toggle fallback
    document.addEventListener('DOMContentLoaded', function () {
        const exportBtn = document.getElementById('btnExportAdmissions');
        const exportMenu = document.querySelector('#btnExportAdmissions + .dropdown-menu');

        if (exportBtn && exportMenu) {
            exportBtn.addEventListener('click', function (e) {
                e.preventDefault();
                e.stopPropagation();

                // Close other dropdowns
                document.querySelectorAll('.dropdown-menu.show').forEach(menu => {
                    if (menu !== exportMenu) {
                        menu.classList.remove('show');
                    }
                });

                // Toggle this dropdown
                exportMenu.classList.toggle('show');
            });

            // Close when clicking outside
            document.addEventListener('click', function (e) {
                if (!exportBtn.contains(e.target) && !exportMenu.contains(e.target)) {
                    exportMenu.classList.remove('show');
                }
            });

            // Prevent menu from closing when clicking inside
            exportMenu.addEventListener('click', function (e) {
                if (e.target.tagName === 'A') {
                    exportMenu.classList.remove('show');
                }
            });
        }
    });

    function validateAdmissionData(data) {
        if (!data.firstName || !data.lastName) {
            console.error('Name required');
            showError('Please enter student first name and last name');
            return false;
        }

        if (!data.mobilePrimary || !/^[6-9]\d{9}$/.test(data.mobilePrimary)) {
            console.error('Invalid mobile');
            showError('Please enter a valid 10-digit mobile number');
            return false;
        }

        if (!data.leadSource) {
            console.warn('Lead source missing, setting default');
            data.leadSource = 'Direct'; // Set default value
        }

        if (selectedCourses.length === 0) {
            console.error('At least one course required');
            showError('Please select at least one course');
            return false;
        }

        return true;
    }


    // Make functions globally available
    window.navigateNext = navigateNext;
    window.navigatePrevious = navigatePrevious;
    window.showTab = showTab;
    window.capturePhoto = capturePhoto;
    window.handlePhotoUpload = handlePhotoUpload;
    window.generateInstallments = generateInstallments;
    window.generateFeeInstallments = generateFeeInstallments;
    window.saveFeeInstallments = saveFeeInstallments;
    window.removeCourse = removeCourse;
    window.removeInstallment = removeInstallment;
    window.printToPDF = printToPDF;

    // Global fetch error handler
    window.addEventListener('unhandledrejection', function (event) {
        if (event.reason && event.reason.message && event.reason.message.includes('<!DOCTYPE')) {
            console.error('Session expired or authentication required');
            event.preventDefault();

            Swal.fire({
                icon: 'warning',
                title: 'Session Expired',
                text: 'Please refresh the page and login again',
                confirmButtonText: 'Refresh Page',
                allowOutsideClick: false
            }).then(() => {
                window.location.reload();
            });
        }
    });

})();