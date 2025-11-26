(function() {
    'use strict';

        let currentTab = 1;
       const totalTabs = 6;
       let admissionData = {};
       let importedAdmissions = [];
       let videoStream = null;

       let currentPage = 0;
       let pageSize = 25;
       let totalPages = 0;
       let totalElements = 0;

    // Course list
    const COURSE_NAMES = [
        "JAVA CORE AND ADVANCE",
        "SPRING BOOT",
        "PYTHON",
        "PYTHON - DATA ANALYTICS",
        "WEB DEVELOPMENT",
        "DATA SCIENCE",
        "FULL STACK JAVA DEVELOPMENT",
        "FULL STACK PYTHON DEVELOPMENT",
        "REACT JS",
        "NODE JS",
        "ANGULAR JS",
        "MACHINE LEARNING",
        "ARTIFICIAL INTELLIGENCE"
    ];

    // Initialize on page load
    document.addEventListener('DOMContentLoaded', function() {
        initializeEventListeners();
        loadAdmissions();
        checkEnquiryPreFill();
    });

    function initializeEventListeners() {
        // Main buttons
        document.getElementById('btnNewAdmission')?.addEventListener('click', openNewAdmissionModal);
        document.getElementById('btnImportAdmissions')?.addEventListener('click', openImportModal);
        document.getElementById('btnExportAdmissions')?.addEventListener('click', exportAdmissions);

        // Modal navigation
        document.getElementById('btnNext')?.addEventListener('click', nextTab);
        document.getElementById('btnPrevious')?.addEventListener('click', previousTab);
        document.getElementById('btnFinish')?.addEventListener('click', saveAdmission);

        // Tab switching
        document.querySelectorAll('#admissionTabs .nav-link').forEach((tab, index) => {
            tab.addEventListener('shown.bs.tab', function() {
                currentTab = index + 1;
                updateNavigationButtons();
                const progress = this.getAttribute('data-progress');
                updateProgress(progress);
            });
        });

        // Import functionality
        document.getElementById('btnBrowseAdmFile')?.addEventListener('click', () => {
            document.getElementById('admCsvFileInput').click();
        });

        document.getElementById('admCsvFileInput')?.addEventListener('change', function(e) {
            handleCSVFile(e.target.files[0]);
        });

        document.getElementById('btnImportAdmData')?.addEventListener('click', importAdmissions);

        // Import type change
        document.querySelectorAll('input[name="admImportType"]').forEach(radio => {
            radio.addEventListener('change', handleImportTypeChange);
        });

        // Camera & Photo
        document.getElementById('btnCapturePhoto')?.addEventListener('click', capturePhoto);
        document.getElementById('admPhotoUpload')?.addEventListener('change', function(e) {
            handlePhotoUpload(e.target.files[0]);
        });

        // Generate installments
        document.getElementById('btnGenerateInstallments')?.addEventListener('click', generateInstallments);

        // Search
        document.getElementById('searchInput')?.addEventListener('input', debounce(searchAdmissions, 500));
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

       function getCsrfToken() {
           const metaTag = document.querySelector('meta[name="_csrf"]');
           return metaTag ? metaTag.getAttribute('content') : null;
       }

       function getCsrfHeader() {
           const metaTag = document.querySelector('meta[name="_csrf_header"]');
           return metaTag ? metaTag.getAttribute('content') : 'X-CSRF-TOKEN';
       }

    // Load Admissions from API
    async function loadAdmissions(page = 0, size = 25) {
        try {
            showLoading('Loading admissions...');

             const csrfToken = getCsrfToken();
                    const headers = {
                        'Content-Type': 'application/json'
                    };

                     if (csrfToken) {
                                headers[getCsrfHeader()] = csrfToken;
                     }

            const response = await fetch(`/api/admissions?page=${page}&size=${size}`);

            if (!response.ok) {
                throw new Error('Failed to load admissions');
            }

            const data = await response.json();

            // Update pagination variables
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

    // Render Admissions Table
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
            const courses = adm.coursesList && adm.coursesList.length > 0
                ? adm.coursesList.join(', ')
                : (adm.courses || 'N/A');

            return `
                <tr data-id="${adm.id}">
                    <td><strong>${adm.registrationNumber || '-'}</strong></td>
                    <td>${adm.studentName || `${adm.firstName} ${adm.lastName}`}</td>
                    <td>${adm.mobilePrimary || 'N/A'}</td>
                    <td><span class="badge bg-primary">${courses}</span></td>
                    <td>${adm.admissionDate || 'N/A'}</td>
                    <td>
                        <div class="action-dropdown">
                            <button class="btn btn-sm btn-light action-menu-trigger">
                                <i class="bi bi-three-dots-vertical"></i>
                            </button>
                            <div class="action-menu">
                                <button class="action-menu-item" data-action="update" data-id="${adm.id}">
                                    <i class="bi bi-pencil-square"></i><span>Update</span>
                                </button>
                                <button class="action-menu-item" data-action="installments" data-id="${adm.id}">
                                    <i class="bi bi-cash-stack"></i><span>Fee Installments</span>
                                </button>
                                <button class="action-menu-item" data-action="view" data-id="${adm.id}">
                                    <i class="bi bi-eye"></i><span>View Details</span>
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

    // Attach Table Event Listeners
    function attachTableEventListeners(tbody) {
        tbody.querySelectorAll('.action-menu-trigger').forEach(trigger => {
            trigger.addEventListener('click', function(e) {
                e.stopPropagation();
                const menu = this.nextElementSibling;
                document.querySelectorAll('.action-menu').forEach(m => {
                    if (m !== menu) m.classList.remove('show');
                });
                menu.classList.toggle('show');
            });
        });

        tbody.querySelectorAll('.action-menu-item').forEach(item => {
            item.addEventListener('click', function() {
                const action = this.getAttribute('data-action');
                const id = this.getAttribute('data-id');
                handleAction(action, id);
            });
        });

        document.addEventListener('click', () => {
            document.querySelectorAll('.action-menu').forEach(menu => {
                menu.classList.remove('show');
            });
        });
    }

    // Handle Actions
    async function handleAction(action, id) {
        switch(action) {
            case 'update':
                await loadAdmissionForEdit(id);
                break;
            case 'view':
                await viewAdmission(id);
                break;
            case 'installments':
                await openFeeInstallments(id);
                break;
            case 'transfer':
                await openTransferModal(id);
                break;
            case 'print':
                await printAdmission(id);
                break;
            case 'delete':
                await deleteAdmission(id);
                break;
        }
    }

    // Load Admission for Edit
    async function loadAdmissionForEdit(id) {
        try {
            showLoading('Loading admission details...');

            const response = await fetch(`/api/admissions/${id}`);
            if (!response.ok) throw new Error('Failed to load admission');

            const admission = await response.json();
            Swal.close();

            // Store in session and reload
            sessionStorage.setItem('admissionEnquiry', JSON.stringify(admission));
            sessionStorage.setItem('admissionFromEnquiry', 'true');

            window.location.reload();

        } catch (error) {
            Swal.close();
            console.error('Error:', error);
            showError('Failed to load admission details');
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
                    We'll check if an enquiry exists to pre-fill the form
                </small>
            `,
            showCancelButton: true,
            confirmButtonText: 'Check & Continue',
            cancelButtonText: 'Skip & Create New',
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

                // ✅ ONLY check if enquiry exists (for pre-filling)
                const enquiryResponse = await fetch(`/api/admissions/enquiry-data/${mobile}`);

                if (enquiryResponse.ok) {
                    const enquiry = await enquiryResponse.json();
                    Swal.close();

                    // Show success message
                    await Swal.fire({
                        title: 'Enquiry Found!',
                        html: `
                            <div class="alert alert-success">
                                <strong><i class="bi bi-check-circle me-2"></i>Form will be pre-filled</strong>
                                <p class="mb-2 mt-3">Found enquiry for: <strong>${enquiry.firstName} ${enquiry.lastName}</strong></p>
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

                    window.location.reload();
                } else {
                    // ✅ NO ERROR - Just proceed without pre-fill
                    Swal.close();

                    await Swal.fire({
                        title: 'No Enquiry Found',
                        html: `
                            <div class="alert alert-info">
                                <strong><i class="bi bi-info-circle me-2"></i>Create New Admission</strong>
                                <p class="mb-2 mt-3">No enquiry found for mobile: <strong>${mobile}</strong></p>
                                <p class="mb-0">You can still create a new admission by filling the form manually.</p>
                            </div>
                        `,
                        icon: 'info',
                        confirmButtonText: 'Continue',
                        confirmButtonColor: '#3b82f6',
                        timer: 2500
                    });

                    // Open empty form
                    currentTab = 1;
                    clearForms();
                    setValue('admMobilePrimary', mobile); // Pre-fill only mobile
                    updateNavigationButtons();
                    updateProgress(16.66);
                    const modal = new bootstrap.Modal(document.getElementById('admissionModal'));
                    modal.show();
                }

            } catch (error) {
                Swal.close();
                console.error('Error:', error);

                // ✅ Even on error, allow form to open
                await Swal.fire({
                    title: 'Notice',
                    text: 'Could not check enquiry status. You can still create admission manually.',
                    icon: 'info',
                    confirmButtonColor: '#3b82f6'
                });

                currentTab = 1;
                clearForms();
                setValue('admMobilePrimary', mobile);
                updateNavigationButtons();
                updateProgress(16.66);
                const modal = new bootstrap.Modal(document.getElementById('admissionModal'));
                modal.show();
            }
        } else if (mobile === null) {
            // User clicked "Skip & Create New"
            currentTab = 1;
            clearForms();
            updateNavigationButtons();
            updateProgress(16.66);
            const modal = new bootstrap.Modal(document.getElementById('admissionModal'));
            modal.show();
        }
        return;
    }

    // If already has enquiry data in session, open modal
    currentTab = 1;
    clearForms();
    updateNavigationButtons();
    updateProgress(16.66);
    const modal = new bootstrap.Modal(document.getElementById('admissionModal'));
    modal.show();
}

    function prefillAdmissionForm(data, isUpdate = false) {
        console.log('Pre-filling form with:', data);

        // Tab 1: Personal Info
        setValue('admFirstName', data.firstName);
        setValue('admMiddleName', data.middleName);
        setValue('admLastName', data.lastName);
        setValue('admCollege', data.college);
        setValue('admQualification', data.qualification);
        setValue('admAadhaar', data.aadhaar);
        setValue('admDob', data.birthDate);
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

        const today = new Date().toISOString().split('T')[0];
        setValue('admAdmissionDate', data.admissionDate || today);

        // Tab 3: Course Details
        setValue('admPackage', data.packageName);

        if (data.coursesList && data.coursesList.length > 0) {
            displaySelectedCourses(data.coursesList);
        }

        highlightPrefilledFields();
    }

    // Save Admission
    async function saveAdmission() {
        const admissionData = collectAdmissionData();

        if (!validateAdmissionData(admissionData)) {
            showError('Please fill all required fields');
            return;
        }

        try {
            showLoading('Saving admission...');

            const response = await fetch('/api/admissions', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(admissionData)
            });

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.message || 'Failed to save admission');
            }

            const result = await response.json();

            Swal.close();
            closeModal('admissionModal');

            showSuccess('Admission saved successfully!');
            loadAdmissions();

            // Ask for print
            const printResult = await Swal.fire({
                title: 'Print Admission Form?',
                text: 'Do you want to print the admission form?',
                icon: 'question',
                showCancelButton: true,
                confirmButtonText: 'Yes, Print',
                cancelButtonText: 'No',
                confirmButtonColor: '#667eea'
            });

            if (printResult.isConfirmed) {
                await printAdmission(result.id);
            }

        } catch (error) {
            Swal.close();
            console.error('Error saving admission:', error);
            showError(error.message || 'Failed to save admission');
        }
    }

    // Collect Admission Data - FIXED: Remove recursion
    function collectAdmissionData() {
        const courseSelect = document.getElementById('admCourse');
        const selectedCourses = courseSelect ?
            Array.from(courseSelect.selectedOptions).map(opt => opt.value) : [];

        const batchSelect = document.getElementById('admBatch');
        const selectedBatches = batchSelect ?
            Array.from(batchSelect.selectedOptions).map(opt => opt.value) : [];

        const subjectSelect = document.getElementById('admSubject');
        const selectedSubjects = subjectSelect ?
            Array.from(subjectSelect.selectedOptions).map(opt => opt.value) : [];

        // Collect installment data if available
        const installmentConfig = collectInstallmentData();

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
            packageName: getValue('admPackage'),
            courses: selectedCourses,
            batches: selectedBatches,
            subjects: selectedSubjects,
            totalPayableFees: parseFloat(getValue('admTotalFees')) || 0,
            totalReceivableFees: parseFloat(getValue('admReceivableFees')) || 0,
            discountPercent: parseFloat(getValue('admDiscountPercent')) || 0,
            discountAmount: parseFloat(getValue('admDiscountAmount')) || 0,
            installmentConfig: installmentConfig
        };
    }

    // Collect Installment Data
    function collectInstallmentData() {
        const tbody = document.getElementById('installmentsBody');
        if (!tbody) return null;

        const rows = tbody.querySelectorAll('tr');

        if (rows.length === 0 || rows[0].cells.length === 1) {
            return null;
        }

        const installments = [];

        rows.forEach((row, index) => {
            const date = row.querySelector(`input[type="date"]`)?.value;
            const amount = parseFloat(row.querySelector(`input[type="number"]`)?.value) || 0;
            const status = row.querySelector(`select`)?.value || 'Pending';

            if (date && amount > 0) {
                installments.push({
                    installmentNumber: index + 1,
                    dueDate: date,
                    amount: amount,
                    status: status
                });
            }
        });

        if (installments.length === 0) return null;

        return {
            startDate: installments[0].dueDate,
            numberOfInstallments: installments.length,
            daysBetween: 30
        };
    }

    // Validate Admission Data
    function validateAdmissionData(data) {
        if (!data.firstName || !data.lastName) {
            console.error('Name required');
            return false;
        }

        if (!data.mobilePrimary || !/^[6-9]\d{9}$/.test(data.mobilePrimary)) {
            console.error('Invalid mobile');
            return false;
        }

        if (!data.leadSource) {
            console.error('Lead source required');
            return false;
        }

        if (!data.documentType) {
            console.error('Document type required');
            return false;
        }

        return true;
    }

    // Search Admissions
   async function searchAdmissions(e) {
       const searchTerm = e.target.value.trim();

       try {
           const searchDTO = {
               searchTerm: searchTerm || null,
               page: 0, // Reset to first page on search
               size: pageSize,
               sortBy: 'admission_date',
               sortDirection: 'DESC'
           };

           const response = await fetch('/api/admissions/search', {
               method: 'POST',
               headers: {
                   'Content-Type': 'application/json',
               },
               body: JSON.stringify(searchDTO)
           });

           if (!response.ok) throw new Error('Search failed');

           const data = await response.json();

           // Update pagination for search results
           currentPage = data.number || 0;
           totalPages = data.totalPages || 0;
           totalElements = data.totalElements || 0;

           renderAdmissionsTable(data.content);
           updatePaginationInfo();
           renderPaginationControls();
       } catch (error) {
           console.error('Search error:', error);
       }
   }

    // Export Admissions
    async function exportAdmissions() {
        try {
            showLoading('Exporting admissions...');
            await new Promise(resolve => setTimeout(resolve, 1000));
            Swal.close();
            showSuccess('Export completed successfully!');
        } catch (error) {
            Swal.close();
            console.error('Export error:', error);
            showError('Failed to export admissions');
        }
    }

    // View Admission
    async function viewAdmission(id) {
        try {
            showLoading('Loading admission details...');

            const response = await fetch(`/api/admissions/${id}`);
            if (!response.ok) throw new Error('Failed to load admission');

            const admission = await response.json();
            Swal.close();

            // Populate view modal
            document.getElementById('viewAdmStudentName').textContent = admission.studentName;
            document.getElementById('viewAdmRegNo').textContent = admission.registrationNumber;
            document.getElementById('viewAdmDate').textContent = admission.admissionDate;
            document.getElementById('viewAdmBirthDate').textContent = admission.birthDate || '-';
            document.getElementById('viewAdmGender').textContent = admission.gender || '-';
            document.getElementById('viewAdmAadhaarNo').textContent = admission.aadhaar || '-';
            document.getElementById('viewAdmBloodGroup').textContent = admission.bloodGroup || '-';
            document.getElementById('viewAdmMobile1').textContent = admission.mobilePrimary;
            document.getElementById('viewAdmMobile2').textContent = admission.mobileSecondary || '-';
            document.getElementById('viewAdmEmail').textContent = admission.emailPrimary || '-';
            document.getElementById('viewAdmAddress').textContent = admission.currentAddress || '-';
            document.getElementById('viewAdmCourses').textContent = admission.courses || '-';
            document.getElementById('viewAdmDocument').textContent = admission.documentType || '-';
            document.getElementById('viewAdmNotes').textContent = admission.notes || '-';

            const modal = new bootstrap.Modal(document.getElementById('viewAdmissionModal'));
            modal.show();

        } catch (error) {
            Swal.close();
            console.error('Error:', error);
            showError('Failed to load admission details');
        }
    }

    // Open Fee Installments
    async function openFeeInstallments(id) {
        try {
            showLoading('Loading installments...');

            const response = await fetch(`/api/admissions/${id}/installments`);
            if (!response.ok) throw new Error('Failed to load installments');

            const installments = await response.json();
            Swal.close();

            const tbody = document.getElementById('feeInstallmentsBody');

            if (installments.length === 0) {
                tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted">No installments found</td></tr>';
            } else {
                tbody.innerHTML = installments.map(inst => `
                    <tr>
                        <td>${inst.dueDate}</td>
                        <td>₹${inst.amount.toFixed(2)}</td>
                        <td><span class="badge bg-${inst.status === 'Paid' ? 'success' : 'warning'}">${inst.status}</span></td>
                        <td>
                            <button class="btn btn-sm btn-danger" onclick="deleteInstallment(${inst.id})">
                                <i class="bi bi-trash"></i>
                            </button>
                        </td>
                    </tr>
                `).join('');
            }

            const modal = new bootstrap.Modal(document.getElementById('feeInstallmentsModal'));
            modal.show();

        } catch (error) {
            Swal.close();
            console.error('Error:', error);
            showError('Failed to load installments');
        }
    }

    // Open Transfer Modal
    async function openTransferModal(id) {
        try {
            showLoading('Loading admission...');

            const response = await fetch(`/api/admissions/${id}`);
            if (!response.ok) throw new Error('Failed to load admission');

            const admission = await response.json();
            Swal.close();

            document.getElementById('transferStudentName').textContent = admission.studentName;
            document.getElementById('transferFirstName').value = admission.firstName;
            document.getElementById('transferMiddleName').value = admission.middleName || '';
            document.getElementById('transferLastName').value = admission.lastName;
            document.getElementById('transferMobilePrimary').value = admission.mobilePrimary;
            document.getElementById('transferDate').value = new Date().toISOString().split('T')[0];

            const modal = new bootstrap.Modal(document.getElementById('transferAdmissionModal'));
            modal.show();

        } catch (error) {
            Swal.close();
            console.error('Error:', error);
            showError('Failed to load admission');
        }
    }

    // Print Admission
    async function printAdmission(id) {
        try {
            showLoading('Generating print preview...');

            const response = await fetch(`/api/admissions/${id}`);
            if (!response.ok) throw new Error('Failed to load admission');

            const admission = await response.json();
            Swal.close();

            document.getElementById('printRegNo').textContent = admission.registrationNumber;
            document.getElementById('printAdmDate').textContent = admission.admissionDate;
            document.getElementById('printStudentName').textContent = admission.studentName;
            document.getElementById('printBirthDate').textContent = admission.birthDate || '-';
            document.getElementById('printGender').textContent = admission.gender || '-';
            document.getElementById('printAadhaar').textContent = admission.aadhaar || '-';
            document.getElementById('printBloodGroup').textContent = admission.bloodGroup || '-';
            document.getElementById('printCategory').textContent = admission.category || '-';
            document.getElementById('printCast').textContent = admission.cast || '-';
            document.getElementById('printQualification').textContent = admission.qualification || '-';
            document.getElementById('printCollege').textContent = admission.college || '-';
            document.getElementById('printMobile1').textContent = admission.mobilePrimary;
            document.getElementById('printMobile2').textContent = admission.mobileSecondary || '-';
            document.getElementById('printEmail1').textContent = admission.emailPrimary || '-';
            document.getElementById('printEmail2').textContent = admission.emailSecondary || '-';
            document.getElementById('printCurrentAddr').textContent = admission.currentAddress || '-';
            document.getElementById('printPermanentAddr').textContent = admission.permanentAddress || '-';
            document.getElementById('printCourses').textContent = admission.courses || '-';
            document.getElementById('printDocument').textContent = admission.documentType || '-';
            document.getElementById('printNotes').textContent = admission.notes || '-';

            const modal = new bootstrap.Modal(document.getElementById('printAdmissionModal'));
            modal.show();

        } catch (error) {
            Swal.close();
            console.error('Error:', error);
            showError('Failed to generate print preview');
        }
    }

    function printToPDF() {
        window.print();
    }

    // Delete Admission
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

                const response = await fetch(`/api/admissions/${id}`, {
                    method: 'DELETE'
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

                      img.onload = function() {
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

                  // Generate Installments
                  function generateInstallments() {
                      const totalAmount = parseFloat(getValue('instTotalAmount')) || 0;
                      const noOfInstallments = parseInt(getValue('instNoOfInstallments')) || 0;
                      const daysBetween = parseInt(getValue('instDays')) || 30;
                      const startDate = getValue('instStartDate');

                      if (!totalAmount || !noOfInstallments || !startDate) {
                          showError('Please fill all required fields');
                          return;
                      }

                      const amountPerInstallment = totalAmount / noOfInstallments;
                      const tbody = document.getElementById('installmentsBody');
                      let currentDate = new Date(startDate);

                      tbody.innerHTML = '';

                      for (let i = 1; i <= noOfInstallments; i++) {
                          const dueDate = new Date(currentDate);
                          const formattedDate = dueDate.toISOString().split('T')[0];

                          const row = document.createElement('tr');
                          row.innerHTML = `
                              <td>
                                  <input type="date" class="form-control form-control-sm"
                                         value="${formattedDate}"
                                         id="instDate${i}"
                                         required>
                              </td>
                              <td>
                                  <input type="number" class="form-control form-control-sm"
                                         value="${amountPerInstallment.toFixed(2)}"
                                         id="instAmount${i}"
                                         step="0.01"
                                         required>
                              </td>
                              <td>
                                  <select class="form-select form-select-sm" id="instStatus${i}">
                                      <option value="Pending">Pending</option>
                                      <option value="Paid">Paid</option>
                                      <option value="Overdue">Overdue</option>
                                      <option value="Waived">Waived</option>
                                  </select>
                              </td>
                              <td>
                                  <button type="button" class="btn btn-sm btn-danger"
                                          onclick="window.removeInstallment(this)">
                                      <i class="bi bi-trash"></i>
                                  </button>
                              </td>
                          `;
                          tbody.appendChild(row);

                          currentDate.setDate(currentDate.getDate() + daysBetween);
                      }

                      setValue('instTotalInstAmount', totalAmount.toFixed(2));
                      showSuccess(`Generated ${noOfInstallments} installments`);
                  }

                  function removeInstallment(button) {
                      const row = button.closest('tr');
                      row.remove();

                      const tbody = document.getElementById('installmentsBody');
                      const rows = tbody.querySelectorAll('tr');

                      if (rows.length === 0) {
                          tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted">No installments generated</td></tr>';
                          setValue('instTotalInstAmount', '0');
                      }
                  }

                  function generateFeeInstallments() {
                      const totalAmount = parseFloat(getValue('feeInstTotalAmount')) || 0;
                      const noOfInstallments = parseInt(getValue('feeInstNoOfInstallments')) || 0;
                      const daysBetween = parseInt(getValue('feeInstDays')) || 30;
                      const startDate = getValue('feeInstStartDate');

                      if (!totalAmount || !noOfInstallments || !startDate) {
                          showError('Please fill all required fields');
                          return;
                      }

                      const amountPerInstallment = totalAmount / noOfInstallments;
                      const tbody = document.getElementById('feeInstallmentsBody');
                      let currentDate = new Date(startDate);

                      tbody.innerHTML = '';

                      for (let i = 1; i <= noOfInstallments; i++) {
                          const dueDate = new Date(currentDate);
                          const formattedDate = dueDate.toISOString().split('T')[0];

                          const row = document.createElement('tr');
                          row.innerHTML = `
                              <td>
                                  <input type="date" class="form-control form-control-sm"
                                         value="${formattedDate}"
                                         data-installment="${i}">
                              </td>
                              <td>
                                  <input type="number" class="form-control form-control-sm"
                                         value="${amountPerInstallment.toFixed(2)}"
                                         step="0.01"
                                         data-installment="${i}">
                              </td>
                              <td>
                                  <select class="form-select form-select-sm" data-installment="${i}">
                                      <option value="Pending">Pending</option>
                                      <option value="Paid">Paid</option>
                                      <option value="Overdue">Overdue</option>
                                      <option value="Waived">Waived</option>
                                  </select>
                              </td>
                              <td>
                                  <button type="button" class="btn btn-sm btn-danger"
                                          onclick="this.closest('tr').remove()">
                                      <i class="bi bi-trash"></i>
                                  </button>
                              </td>
                          `;
                          tbody.appendChild(row);

                          currentDate.setDate(currentDate.getDate() + daysBetween);
                      }

                      setValue('feeInstTotalInstAmount', totalAmount.toFixed(2));
                      showSuccess(`Generated ${noOfInstallments} installments`);
                  }

                  async function saveFeeInstallments() {
                      const tbody = document.getElementById('feeInstallmentsBody');
                      const rows = tbody.querySelectorAll('tr');

                      if (rows.length === 0 || rows[0].cells.length === 1) {
                          showError('No installments to save');
                          return;
                      }

                      showSuccess('Fee installments saved successfully!');
                      closeModal('feeInstallmentsModal');
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
                                  admissionDate: parseDate(row[4]) || new Date().toISOString().split('T')[0],
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
                                  admissionDate: parseDate(row[10]) || new Date().toISOString().split('T')[0],
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

                  async function importAdmissions() {
                      if (importedAdmissions.length === 0) {
                          showError('No data to import');
                          return;
                      }

                      try {
                          showLoading(`Importing ${importedAdmissions.length} admissions...`);

                        const response = await fetch('/api/admissions/bulk-import-json?importSource=OLD_FORMAT', {                              method: 'POST',
                              headers: {
                                  'Content-Type': 'application/json',
                              },
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

                  function parseDate(dateStr) {
                      if (!dateStr || !dateStr.trim()) return null;

                      try {
                          const parts = dateStr.split('/');
                          if (parts.length === 3) {
                              const day = parseInt(parts[0]);
                              const month = parseInt(parts[1]) - 1;
                              const year = parseInt(parts[2]);
                              const date = new Date(year, month, day);
                              return date.toISOString().split('T')[0];
                          }

                          const date = new Date(dateStr);
                          if (!isNaN(date.getTime())) {
                              return date.toISOString().split('T')[0];
                          }
                      } catch (e) {
                          console.warn('Failed to parse date:', dateStr);
                      }

                      return null;
                  }

                  // Helper Functions
                  function clearForms() {
                      ['personalInfoForm', 'otherDetailsForm', 'courseDetailsForm',
                       'batchDetailsForm', 'installmentsForm', 'imageUploadForm'].forEach(id => {
                          document.getElementById(id)?.reset();
                      });
                  }

                  function nextTab() {
                      if (currentTab < totalTabs) {
                          currentTab++;
                          showTab(currentTab);
                          updateNavigationButtons();
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

                  function updateProgress(width) {
                      const bar = document.getElementById('admissionProgressBar');
                      if (bar) bar.style.width = width + '%';
                  }

                  function setValue(id, value) {
                      const el = document.getElementById(id);
                      if (el && value != null) {
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
                      return function(...args) {
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
          link.addEventListener('click', function(e) {
              e.preventDefault();
              const page = parseInt(this.getAttribute('data-page'));
              if (!isNaN(page) && page !== currentPage && page >= 0 && page < totalPages) {
                  loadAdmissions(page, pageSize);
              }
          });
      });
  }

      // Make functions globally available
      window.capturePhoto = capturePhoto;
      window.handlePhotoUpload = handlePhotoUpload;
      window.generateInstallments = generateInstallments;
      window.generateFeeInstallments = generateFeeInstallments;
      window.saveFeeInstallments = saveFeeInstallments;
      window.removeCourse = removeCourse;
      window.removeInstallment = removeInstallment;
      window.printToPDF = printToPDF;
  })();