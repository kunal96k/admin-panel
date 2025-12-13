// admission.js

(function() {
    'use strict';

       let currentTab = 1;
       const totalTabs = 6;
       let admissionData = {};
       let importedAdmissions = [];
       let videoStream = null;

       let selectedCourses = [];

       let currentPage = 0;
       let pageSize = 25;
       let totalPages = 0;
       let totalElements = 0;

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
       document.addEventListener('DOMContentLoaded', function() {
           initializeEventListeners();
           loadAdmissions();
           loadDropdownData();
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
               tab.addEventListener('shown.bs.tab', function() {
                   currentTab = index + 1;
                   updateNavigationButtons();
                   const progress = this.getAttribute('data-progress');
                   updateProgress(progress);
               });
           });

            const pageSizeSelect = document.getElementById('pageSizeSelect');
               if (pageSizeSelect) {
                   pageSizeSelect.addEventListener('change', function(e) {
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
            document.getElementById('searchInput')?.addEventListener('input', debounce(searchAdmissions, 800));
           // Package selection
           document.getElementById('admPackage')?.addEventListener('change', handlePackageChange);

           // Course selection
           document.getElementById('admCourse')?.addEventListener('change', handleCourseAdd);

           // Discount calculations
           document.getElementById('admDiscountPercent')?.addEventListener('input', calculateDiscount);
           document.getElementById('admDiscountAmount')?.addEventListener('input', calculateDiscountFromAmount);
       }

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

       function getCsrfToken() {
           const metaTag = document.querySelector('meta[name="_csrf"]');
           return metaTag ? metaTag.getAttribute('content') : null;
       }

       function getCsrfHeader() {
           const metaTag = document.querySelector('meta[name="_csrf_header"]');
           return metaTag ? metaTag.getAttribute('content') : 'X-CSRF-TOKEN';
       }

async function loadAdmissions(page = 0, size = 25) {
    try {
        showLoading('Loading admissions...');

        // : Sort by createdAt DESC (latest first)
        const response = await fetch(`/api/admissions?page=${page}&size=${size}`, {
            method: 'GET',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            }
        });

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
                    <td>${adm.admissionDate ? new Date(adm.admissionDate).toLocaleDateString('en-GB') : 'N/A'}</td>
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

                    // ✅ Show enquiry details with date
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
                    // ⚠️ NO ENQUIRY FOUND - REDIRECT TO ENQUIRY PAGE
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
         console.log('Pre-filling form with:', data);
     
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
     
         // Tab 3: Properly populate courses with amounts
         if (data.coursesList && data.coursesList.length > 0) {
             const totalPayable = data.totalPayableFees || 0;
             const amountPerCourse = totalPayable / data.coursesList.length;
     
             selectedCourses = data.coursesList.map((courseName, index) => ({
                 id: index + 1,
                 name: courseName,
                 price: amountPerCourse
             }));
     
             renderSelectedCourses();
         }
     
         setValue('admTotalFees', data.totalPayableFees || 0);
         setValue('admReceivableFees', data.totalReceivableFees || 0);
         setValue('admDiscountPercent', data.discountPercent || 0);
         setValue('admDiscountAmount', data.discountAmount || 0);
     
         // Tab 4: Batch & Subject
         if (data.batchesList && data.batchesList.length > 0) {
             const batchSelect = document.getElementById('admBatch');
             Array.from(batchSelect.options).forEach(option => {
                 if (data.batchesList.includes(option.value)) {
                     option.selected = true;
                 }
             });
         }
     
         const subjectSelect = document.getElementById('admSubject');
         if (data.subjectsList && data.subjectsList.length > 0) {
             enableSubjectSelection().then(() => {
                 setTimeout(() => {
                     Array.from(subjectSelect.options).forEach(option => {
                         if (data.subjectsList.some(s => option.textContent.includes(s))) {
                             option.selected = true;
                         }
                     });
                 }, 500);
             });
         } else {
             subjectSelect.disabled = true;
             subjectSelect.innerHTML = '<option value="">No subjects selected</option>';
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

            img.onload = function() {
                ctx.drawImage(img, 0, 0, 300, 300);
            };

            img.onerror = function() {
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

        // Enable subject selection if courses are selected
        enableSubjectSelection();
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
        enableSubjectSelection();

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
                <td>${course.name}</td>
                <td>
                    <input type="number"
                           class="form-control form-control-sm"
                           value="${parseFloat(course.price || 0).toFixed(2)}"
                           data-index="${index}"
                           onchange="updateCoursePrice(this)"
                           min="0"
                           step="0.01">
                </td>
                <td>
                    <button type="button" class="btn btn-sm btn-danger btn-remove-course"
                            data-index="${index}">
                        <i class="bi bi-trash"></i>
                    </button>
                </td>
            </tr>
        `).join('');

        tbody.querySelectorAll('.btn-remove-course').forEach(btn => {
            btn.addEventListener('click', function(e) {
                e.preventDefault();
                const index = parseInt(this.dataset.index);
                selectedCourses.splice(index, 1);
                renderSelectedCourses();
                calculateTotalFees();

                if (selectedCourses.length === 0) {
                    disableSubjectSelection();
                }
            });
        });
    }

    window.updateCoursePrice = function(input) {
        const index = parseInt(input.dataset.index);
        const newPrice = parseFloat(input.value) || 0;

        selectedCourses[index].price = newPrice;
        calculateTotalFees();
    };

   window.removeCourse = function(indexOrButton) {
       const index = typeof indexOrButton === 'number' ? indexOrButton : parseInt(indexOrButton);

       selectedCourses.splice(index, 1);
       renderSelectedCourses();
       calculateTotalFees();

       if (selectedCourses.length === 0) {
           disableSubjectSelection();
       }

       return false;
   };

    function clearCourseSelection() {
        selectedCourses = [];
        renderSelectedCourses();
        calculateTotalFees();
        disableSubjectSelection();
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

    // ==================== SUBJECT HANDLING ====================

    async function enableSubjectSelection() {
        const subjectSelect = document.getElementById('admSubject');
        subjectSelect.disabled = false;
        subjectSelect.innerHTML = '<option value="">Loading subjects...</option>';

        try {
            // Get subjects for all selected courses
            const allSubjects = [];

            for (const course of selectedCourses) {
                const response = await fetch(`/api/subjects/course/${course.id}`);
                if (response.ok) {
                    const subjects = await response.json();
                    allSubjects.push(...subjects.map(s => ({
                        id: s.id,
                        name: s.subjectName,
                        courseId: course.id,
                        courseName: course.name
                    })));
                }
            }

            subjectSelect.innerHTML = '';
            if (allSubjects.length === 0) {
                subjectSelect.innerHTML = '<option value="">No subjects available</option>';
                return;
            }

            allSubjects.forEach(subject => {
                const option = document.createElement('option');
                option.value = subject.id;
                option.textContent = `${subject.name} (${subject.courseName})`;
                subjectSelect.appendChild(option);
            });

        } catch (error) {
            console.error('Error loading subjects:', error);
            subjectSelect.innerHTML = '<option value="">Error loading subjects</option>';
        }
    }

    function disableSubjectSelection() {
        const subjectSelect = document.getElementById('admSubject');
        subjectSelect.disabled = true;
        subjectSelect.innerHTML = '<option value="">Select courses first</option>';
    }
    
    // ==================== LOAD ADMISSION FOR UPDATE ====================
    
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

                //  CHANGE MODAL TITLE
                document.getElementById('admissionModalTitle').textContent = 'Update Admission';

                //  Store admission ID for update
                document.getElementById('btnFinish').dataset.admissionId = id;
                document.getElementById('btnFinish').textContent = 'Update';

                //  Pre-fill all form data
                prefillAdmissionForm(admission, true);

                //  Load installments ONLY IF THEY EXIST
                if (admission.installments && admission.installments.length > 0) {
                    displayExistingInstallments(admission.installments);

                    //  Pre-fill installment config fields IF AVAILABLE
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
                    //  OLD CSV DATA - No installments
                    const tbody = document.getElementById('installmentsBody');
                    tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted">No installments found. You can generate new ones.</td></tr>';
                }

                setValue('instTotalAmount', admission.totalReceivableFees || 0);

                // Open modal
                currentTab = 1;
                updateNavigationButtons();
                updateProgress(16.66);
                const modal = new bootstrap.Modal(document.getElementById('admissionModal'));
                modal.show();

            } catch (error) {
                Swal.close();
                console.error('Error:', error);
                showError('Failed to load admission details: ' + error.message);
            }
        }

    // ==================== DISPLAY EXISTING INSTALLMENTS ====================
    
    function displayExistingInstallments(installments) {
        const tbody = document.getElementById('installmentsBody');

        if (!installments || installments.length === 0) {
            tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted">No installments generated</td></tr>';
            return;
        }

        tbody.innerHTML = installments.map((inst, index) => `
            <tr>
                <td>
                    <input type="date" class="form-control form-control-sm"
                           value="${inst.dueDate}"
                           id="instDate${index + 1}"
                           required>
                </td>
                <td>
                    <input type="number" class="form-control form-control-sm"
                           value="${inst.amount.toFixed(2)}"
                           id="instAmount${index + 1}"
                           step="0.01"
                           min="0"
                           required>
                </td>
                <td>
                    <select class="form-select form-select-sm" id="instStatus${index + 1}">
                        <option value="Pending" ${inst.status === 'Pending' ? 'selected' : ''}>Pending</option>
                        <option value="Paid" ${inst.status === 'Paid' ? 'selected' : ''}>Paid</option>
                        <option value="Overdue" ${inst.status === 'Overdue' ? 'selected' : ''}>Overdue</option>
                        <option value="Waived" ${inst.status === 'Waived' ? 'selected' : ''}>Waived</option>
                    </select>
                </td>
                <td>
                    <button type="button" class="btn btn-sm btn-danger"
                            onclick="window.removeInstallment(this)"
                            data-installment-id="${inst.id || ''}">
                        <i class="bi bi-trash"></i>
                    </button>
                </td>
            </tr>
        `).join('');
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

              showSuccessWithDetails(
                  isUpdate ? 'Admission Updated!' : 'Admission Saved!',
                  `Admission ${isUpdate ? 'updated' : 'created'} successfully`,
                  `Registration No: ${result.registrationNumber || 'Generated'}`
              );

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
           const selectedBatches = Array.from(batchSelect.selectedOptions).map(opt => opt.value);

           const subjectSelect = document.getElementById('admSubject');
           const selectedSubjects = Array.from(subjectSelect.selectedOptions).map(opt => opt.textContent);

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
               packageName: getSelectedPackageName(),
               courses: selectedCourses.map(c => c.name),
               batches: selectedBatches,
               subjects: selectedSubjects,
               totalPayableFees: parseFloat(getValue('admTotalFees')) || 0,
               totalReceivableFees: parseFloat(getValue('admReceivableFees')) || 0,
               discountPercent: parseFloat(getValue('admDiscountPercent')) || 0,
               discountAmount: parseFloat(getValue('admDiscountAmount')) || 0,
               installmentConfig: installmentConfig
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
   async function searchAdmissions(e) {
       const searchTerm = e.target.value.trim();

       try {
           // If search is empty, reload all admissions
           if (!searchTerm) {
               await loadAdmissions(0, pageSize);
               return;
           }

           const csrfToken = getCsrfToken();
           const headers = {
               'Accept': 'application/json',
               'Content-Type': 'application/json',
           };
           if (csrfToken) headers[getCsrfHeader()] = csrfToken;

           const searchDTO = {
               searchTerm: searchTerm,
               page: 0,
               size: pageSize,
               sortBy: 'createdAt',
               sortDirection: 'DESC'
           };

           const response = await fetch('/api/admissions/search', {
               method: 'POST',
               headers: headers,
               credentials: 'include',
               body: JSON.stringify(searchDTO)
           });

           if (!response.ok) {
               const contentType = response.headers.get('content-type');
               if (contentType && contentType.includes('text/html')) {
                   throw new Error('Server returned HTML instead of JSON. Check if you are logged in.');
               }
               throw new Error(`Search failed: ${response.status}`);
           }

           const data = await response.json();

           currentPage = data.number || 0;
           totalPages = data.totalPages || 0;
           totalElements = data.totalElements || 0;

           renderAdmissionsTable(data.content);
           updatePaginationInfo();
           renderPaginationControls();

       } catch (error) {
           console.error('Search error:', error);
           showError('Search failed: ' + error.message);
           // Fallback to showing current data
           renderAdmissionsTable([]);
       }
   }

// ==================== EXPORT FUNCTIONALITY ====================

let exportDataTable = null;

// Fetch export data with fees
async function prepareExportData() {
    try {
        showLoading('Fetching export data...');

        const response = await fetch(`/api/admissions/export`);
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
    let tempTable = document.getElementById('tempExportTable');

    if (!tempTable) {
        tempTable = document.createElement('table');
        tempTable.id = 'tempExportTable';
        tempTable.style.display = 'none';
        document.body.appendChild(tempTable);
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
                customize: function(doc) {
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
window.exportToCSV = async function() {
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

window.exportToExcel = async function() {
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

window.exportToPDF = async function() {
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

window.copyTableData = async function() {
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

window.printTable = async function() {
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
                console.log(' Loaded courses for search:', allCourses.length);

            } catch (error) {
                console.error('❌ Error loading courses:', error);
            }
        }

        // Show dropdown on focus
        searchInput.addEventListener('focus', function() {
            if (allCourses.length > 0) {
                renderCourseDropdown('');
                dropdown.classList.add('show');
            }
        });

        // Filter on input
        searchInput.addEventListener('input', function() {
            const searchTerm = this.value.toLowerCase().trim();
            renderCourseDropdown(searchTerm);
            dropdown.classList.add('show');
        });

        // Hide dropdown when clicking outside
        document.addEventListener('click', function(e) {
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
                option.addEventListener('click', function(e) {
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
                option.addEventListener('click', function() {
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
                tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted">No installments found</td></tr>';
            } else {
                tbody.innerHTML = installments.map(inst => `
                    <tr>
                        <td>${inst.dueDate}</td>
                        <td>₹${parseFloat(inst.amount).toFixed(2)}</td>
                        <td><span class="badge bg-${inst.status === 'Paid' ? 'success' : 'warning'}">${inst.status}</span></td>
                        <td>
                            <button class="btn btn-sm btn-danger" onclick="deleteInstallment(${inst.id})">
                                <i class="bi bi-trash"></i>
                            </button>
                        </td>
                    </tr>
                `).join('');
            }

            document.getElementById('feeInstStudentName').textContent = admission.studentName;

            const modal = new bootstrap.Modal(document.getElementById('feeInstallmentsModal'));
            modal.show();

        } catch (error) {
            Swal.close();
            console.error('Error:', error);
            showError('Failed to load installments');
        }
    }

    window.deleteInstallment = async function(installmentId) {
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
                const modal = bootstrap.Modal.getInstance(document.getElementById('feeInstallmentsModal'));
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
                   <span class="badge bg-${
                       inst.status === 'Paid' ? 'success' :
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
        document.getElementById('btnTransferAdmission')?.addEventListener('click', async function() {

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
                batches: Array.from(document.getElementById('transferBatch').selectedOptions).map(o => o.value),
                subjects: Array.from(document.getElementById('transferSubject').selectedOptions).map(o => o.textContent),
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
       printWindow.onload = function() {
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
   window.printToPDF = function() {
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

                 // ==================== INSTALLMENT GENERATION ====================

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
                                            min="0"
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

                     window.removeInstallment = function(button) {
                         const row = button.closest('tr');
                         row.remove();

                         const tbody = document.getElementById('installmentsBody');
                         const rows = tbody.querySelectorAll('tr');

                         if (rows.length === 0) {
                             tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted">No installments generated</td></tr>';
                             setValue('instTotalInstAmount', '0');
                         }
                     };

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

                  function toIsoDateFromJsDate(dateStr) {
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

                        // If it's a number (Excel sometimes gives timestamps) — try to convert
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

  // Manual dropdown toggle fallback
  document.addEventListener('DOMContentLoaded', function() {
      const exportBtn = document.getElementById('btnExportAdmissions');
      const exportMenu = document.querySelector('#btnExportAdmissions + .dropdown-menu');

      if (exportBtn && exportMenu) {
          exportBtn.addEventListener('click', function(e) {
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
          document.addEventListener('click', function(e) {
              if (!exportBtn.contains(e.target) && !exportMenu.contains(e.target)) {
                  exportMenu.classList.remove('show');
              }
          });

          // Prevent menu from closing when clicking inside
          exportMenu.addEventListener('click', function(e) {
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
      window.addEventListener('unhandledrejection', function(event) {
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