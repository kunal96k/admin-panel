(function() {
    'use strict';

    let importedData = [];
    let currentEnquiryId = null;
    let currentFollowUpEnquiry = null;

    let currentPage = 0;
    let pageSize = 25;
    let totalPages = 0;
    let totalElements = 0;

    const COURSE_NAMES = [
      "SPRING BOOT",
      "MongoDB",
      "AI CRASH COURSE",
      "ENGLISH AND MARATHI TYPING",
      "CCNA Switching",
      "PYTHON - DATA ANALYTICS",
      "Professional English Communication",
      "SQL - Data Analytics",
      "GRAPHICS DESIGN",
      "MEDICAL CODING",
      "ARTIFICIAL INTELLIGENCE",
      "FULL STACK JavaScript  DEVELOPMENT",
      "FULL STACK PYTHON DEVELOPMENT",
      "FULL STACK JAVA DEVELOPMENT",
      "FULL STACK PHP DEVELOPMENT",
      "MEAN STACK",
      "FULL STACK BACKEND JavaScript",
      "FULL STACK BACKEND PYTHON",
      "FULL STACK BACKEND PHP",
      "FULL STACK BACKEND JAVA CORE & ADVANCE",
      "EXPRESS JS",
      "DATA ENGINEERING",
      "FULL STACK",
      "FULL STACK BACKEND",
      "FULL STACK FRONTEND",
      "SAP - ABAP",
      "TABLEAU",
      "MERN STACK",
      "ASP.NET",
      "AUTO CAD",
      "SAP-MM",
      "ANIMATION",
      "SAP PP",
      "SAP SD",
      "SAP FICO",
      "UGNX",
      "CATIA",
      "SOLID-WORKS",
      "CREO",
      "DATA ANALYTICS",
      "BUSINESS ANALYTICS",
      "C PANEL AND WHM",
      "LARAVEL",
      "AUTOMATION TESTING",
      "MANUAL TESTING",
      "KUBERNETES",
      "DJANGO",
      "WEB DEVELOPMENT",
      "CSS",
      "POWER BI",
      "HARDWARE",
      "Data Structures Using C++",
      "HTML",
      "ANSIBLE",
      "CYBER SECURITY",
      "PALOALTO",
      "REACT JS",
      "HACKING",
      "DATA SCIENCE",
      "PENETRATION TESTING",
      "PYTHON + MACHINE LEARNING",
      "JAVA CORE & ADV + JAVA MVC",
      "MYSQL",
      "SOFTWARE TESTING",
      "WEB DESIGNING AND DEVELOPMENT",
      "C & C++ PROGRAMMING",
      ".NET",
      "JAVA FRAMEWORK",
      "CISCO CERTIFIED NETWORK PROFESSIONAL",
      "JAVASCRIPT",
      "JAVA CORE AND ADVANCE",
      "WEBSITE DESIGNING",
      "MACHINE LEARNING",
      "RDBMS",
      "MCSA",
      "ADVANCE EXCEL",
      "DATA STRUCTURE USING C",
      "DBMS",
      "MCSE",
      "NODE JS",
      "Angular JS",
      ".NET MVC",
      "JAVA MVC",
      "DEVOPS",
      "ORACLE DBA",
      "DIGITAL MARKETING",
      "ANDROID",
      "SHELL SCRIPTING",
      "PHP",
      "REDHAT (RHCVA)",
      "REDHAT (OpenStack)",
      "REDHAT (RHCSA)",
      "REDHAT (RHCE)",
      "REDHAT (RHCSA & RHCE)",
      "PYTHON",
      "VMWARE",
      "MCSA & MCSE",
      "BIG DATA HADOOP",
      "AMAZON WEB SERVICES",
      "Azure",
      "JAVA ADVANCE",
      "JAVA CORE",
      "C++ PROGRAMMING",
      "C PROGRAMMING",
      "CISCO CERTIFIED NETWORK ASSOCIATE",
      "NETWORKING (N+)"
    ];


    document.addEventListener('DOMContentLoaded', function() {
        initializeEventListeners();
        loadEnquiries();
        initializeCourseSelector();
    });

    function initializeCourseSelector() {
        const courseSelect = document.getElementById('course');
        if (courseSelect) {
            courseSelect.addEventListener('change', updateSelectedCoursesDisplay);
        }
    }

    function updateSelectedCoursesDisplay() {
        const courseSelect = document.getElementById('course');
        const selectedCourses = Array.from(courseSelect.selectedOptions).map(opt => opt.value);

        // Optional: Add visual feedback below select
        let displayArea = document.getElementById('selectedCoursesDisplay');
        if (!displayArea) {
            displayArea = document.createElement('div');
            displayArea.id = 'selectedCoursesDisplay';
            displayArea.className = 'mt-2';
            courseSelect.parentElement.appendChild(displayArea);
        }

        if (selectedCourses.length > 0) {
            displayArea.innerHTML = `
                <div class="d-flex flex-wrap gap-1">
                    ${selectedCourses.map(course => `
                        <span class="badge bg-primary" style="font-size: 0.85rem; padding: 0.4rem 0.6rem;">
                            ${course}
                            <i class="bi bi-x-circle ms-1" style="cursor: pointer;" onclick="window.removeCourseTag('${course.replace(/'/g, "\\'")}')"></i>
                        </span>
                    `).join('')}
                </div>
            `;
        } else {
            displayArea.innerHTML = '';
        }
    }

    window.removeCourseTag = function(courseName) {
        const courseSelect = document.getElementById('course');
        Array.from(courseSelect.options).forEach(option => {
            if (option.value === courseName) {
                option.selected = false;
            }
        });
        updateSelectedCoursesDisplay();
    };

   // Match and normalize course names from CSV - IMPROVED VERSION
   function matchCourseName(csvCourseName) {
       if (!csvCourseName) return null;

       const normalized = csvCourseName.trim().toUpperCase();

       // Exact match
       for (let course of COURSE_NAMES) {
           if (course.toUpperCase() === normalized) {
               return course;
           }
       }

       // Partial match
       for (let course of COURSE_NAMES) {
           if (course.toUpperCase().includes(normalized) ||
               normalized.includes(course.toUpperCase())) {
               return course;
           }
       }

       // **NEW: If no match found, return the original course name**
       // This allows importing courses not in the predefined list
       console.warn(`Course not in predefined list, using as-is: ${csvCourseName}`);
       return csvCourseName.trim();
   }

    function initializeEventListeners() {
        // Action menu triggers
        document.querySelectorAll('.action-menu-trigger').forEach(trigger => {
            trigger.addEventListener('click', function(e) {
                e.stopPropagation();
                const menu = this.nextElementSibling;
                document.querySelectorAll('.action-menu').forEach(m => {
                    if (m !== menu) m.classList.remove('show');
                });
                menu.classList.toggle('show');
            });
        });

        // Import type radio buttons
        document.querySelectorAll('input[name="importType"]').forEach(radio => {
            radio.addEventListener('change', handleImportTypeChange);
        });

        document.addEventListener('click', () => {
            document.querySelectorAll('.action-menu').forEach(menu => {
                menu.classList.remove('show');
            });
        });

        // Buttons
        document.getElementById('btnAddEnquiry')?.addEventListener('click', openAddModal);
        document.getElementById('btnImportCSV')?.addEventListener('click', openImportModal);
        document.getElementById('btnExportCSV')?.addEventListener('click', exportCSV);
        document.getElementById('btnSaveEnquiry')?.addEventListener('click', saveEnquiry);

        // Tab progress
        document.querySelectorAll('#enquiryTabs .nav-link').forEach(tab => {
            tab.addEventListener('click', function() {
                updateProgress(this.getAttribute('data-progress'));
            });
        });

        document.querySelectorAll('#viewModal .nav-link').forEach(tab => {
            tab.addEventListener('click', function() {
                updateViewProgress(this.getAttribute('data-view-progress'));
            });
        });

        // CSV Import
        document.getElementById('btnBrowseFile')?.addEventListener('click', () => {
            document.getElementById('csvFileInput').click();
        });

        document.getElementById('csvFileInput')?.addEventListener('change', function(e) {
            handleCSVFile(e.target.files[0]);
        });

        document.getElementById('importBtn')?.addEventListener('click', importCSV);
        document.getElementById('btnSaveFollowUp')?.addEventListener('click', saveFollowUp);

        // Drag and drop
        const importArea = document.getElementById('importArea');
        if (importArea) {
            importArea.addEventListener('dragover', (e) => {
                e.preventDefault();
                importArea.classList.add('dragover');
            });
            importArea.addEventListener('dragleave', () => {
                importArea.classList.remove('dragover');
            });
            importArea.addEventListener('drop', (e) => {
                e.preventDefault();
                importArea.classList.remove('dragover');
                const file = e.dataTransfer.files[0];
                if (file?.name.endsWith('.csv')) {
                    handleCSVFile(file);
                } else {
                    showError('Please upload a valid CSV file.');
                }
            });
        }

        // Search with debounce
       document.getElementById('searchInput')?.addEventListener('input', debounce(searchEnquiries, 500));


       const pageSizeSelect = document.querySelector('.form-select[style*="max-width: 150px"]');
       if (pageSizeSelect) {
           pageSizeSelect.addEventListener('change', function() {
               pageSize = parseInt(this.value);
               loadEnquiries(0, pageSize);
           });
       }
    }

    // Load Enquiries from API
    async function loadEnquiries(page = 0, size = 25) {
        try {
            currentPage = page;
            pageSize = size;

            const response = await fetch(`/api/enquiries?page=${page}&size=${size}`);
            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                throw new Error(errorData.message || 'Failed to load enquiries');
            }

            const data = await response.json();
            totalPages = data.totalPages || 0;
            totalElements = data.totalElements || 0;

            renderEnquiriesTable(data.content || []);
            updatePaginationControls();
            updateEntriesInfo();
        } catch (error) {
            console.error('Error loading enquiries:', error);
            renderEnquiriesTable([]);
            showError(error.message || 'Failed to load enquiries');
        }
    }

    // Save Enquiry (Create or Update)
    async function saveEnquiry() {
        const enquiryData = collectFormData();

        if (!validateFormData(enquiryData)) {
            showError('Please fill all required fields');
            return;
        }

        try {
            const url = currentEnquiryId
                ? `/api/enquiries/${currentEnquiryId}`
                : '/api/enquiries';

            const method = currentEnquiryId ? 'PUT' : 'POST';

            const response = await fetch(url, {
                method: method,
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(enquiryData)
            });

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.message || 'Failed to save enquiry');
            }

            showSuccess(currentEnquiryId ? 'Enquiry updated successfully!' : 'Enquiry created successfully!');
            closeModal('enquiryModal');
            loadEnquiries();
            currentEnquiryId = null;

        } catch (error) {
            console.error('Error saving enquiry:', error);
            showError(error.message);
        }
    }

    // Import CSV with course matching
    async function importCSV() {
        if (importedData.length === 0) {
            showError('No data to import');
            return;
        }

        const importType = document.querySelector('input[name="importType"]:checked').value;
        const typeEnum = importType === 'old' ? 'OLD_FORMAT' : 'NEW_FORMAT';

        const dtoList = importedData.map(record => {
            // CRITICAL FIX: Ensure courses is ALWAYS a valid array
            let coursesArray = [];

            if (Array.isArray(record.courses)) {
                coursesArray = record.courses.filter(c => c && c.trim());
            } else if (typeof record.courses === 'string' && record.courses.trim()) {
                coursesArray = record.courses.split(',')
                    .map(c => c.trim())
                    .filter(Boolean);
            }

            // Ensure at least one course exists
            if (coursesArray.length === 0) {
                console.warn(`Skipping record - no valid courses for mobile: ${record.mobilePrimary}`);
                return null;
            }

            const dto = {
                enquiryNo: record.enquiryNo,
                mobile: record.mobilePrimary,
                courses: coursesArray,
                source: record.leadSource || 'Unknown',
                enquiryDate: record.enquiryDate || null,
                status: record.status || 'New'
            };

            if (importType === 'old') {
                dto.name = `${record.firstName || ''} ${record.middleName || ''} ${record.lastName || ''}`.trim();
            } else {
                dto.firstName = record.firstName;
                dto.middleName = record.middleName;
                dto.lastName = record.lastName;
                dto.secondaryMobile = record.mobileSecondary;
                dto.email = record.emailPrimary;
                dto.currentAddress = record.currentAddress;
                dto.permanentAddress = record.permanentAddress;
                dto.college = record.college;
                dto.followupDate = record.followupDate || null;
                dto.note = record.note;
            }

            if (record.assignTo) dto.assignTo = record.assignTo;

            // Debug log
            console.log('Prepared DTO:', {
                mobile: dto.mobile,
                courses: dto.courses,
                coursesType: Array.isArray(dto.courses) ? 'array' : typeof dto.courses,
                coursesLength: dto.courses.length
            });

            return dto;
        }).filter(dto => dto !== null);

        if (dtoList.length === 0) {
            showError('No valid records to import. All records are missing required fields (courses).');
            return;
        }

        try {
            showLoading(`Importing ${dtoList.length} records...`);

            const response = await fetch(`/api/enquiries/bulk-import-json?importSource=${typeEnum}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                },
                body: JSON.stringify(dtoList)
            });

            const contentType = response.headers.get('content-type');

            if (!response.ok) {
                let errorMessage = 'Import failed';

                if (contentType && contentType.includes('application/json')) {
                    const errorData = await response.json();
                    errorMessage = errorData.message || errorMessage;

                    // Log detailed error for debugging
                    console.error('Import error details:', errorData);
                } else {
                    const errorText = await response.text();
                    console.error('Server error:', errorText);
                    errorMessage = 'Server error occurred. Check console for details.';
                }

                throw new Error(errorMessage);
            }

            const result = await response.json();
            Swal.close();
            closeModal('importModal');

            let message = `Successfully imported ${result.successfulImports} out of ${result.totalRecords} records`;
            if (result.failedImports > 0) {
                message += `\n${result.failedImports} records failed`;

                // Show detailed errors if available
                if (result.errors && result.errors.length > 0) {
                    console.warn('Import errors:', result.errors);
                }
            }

            showSuccess(message);
            loadEnquiries();
            resetImport();

        } catch (error) {
            Swal.close();
            console.error('Import error:', error);
            showError(error.message || 'Failed to import data');
        }
    }

    // Export CSV
    async function exportCSV() {
        try {
            const response = await fetch('/api/enquiries/export/csv');
            if (!response.ok) throw new Error('Export failed');

            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = `enquiries_${Date.now()}.csv`;
            link.click();
            window.URL.revokeObjectURL(url);

            showSuccess('CSV exported successfully!');
        } catch (error) {
            console.error('Export error:', error);
            showError('Failed to export CSV');
        }
    }

    // Search Enquiries
// Search Enquiries
async function searchEnquiries(e) {
    const searchTerm = e.target.value.trim();

    try {
        const searchDTO = {
            searchTerm: searchTerm || null,
            page: 0,  // Reset to first page on new search
            size: pageSize,
            sortBy: 'enquiryDate',
            sortDirection: 'DESC'
        };

        const response = await fetch('/api/enquiries/search', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(searchDTO)
        });

        if (!response.ok) throw new Error('Search failed');

        const data = await response.json();
        currentPage = 0;
        totalPages = data.totalPages || 0;
        totalElements = data.totalElements || 0;

        renderEnquiriesTable(data.content);
        updatePaginationControls();
        updateEntriesInfo();
    } catch (error) {
        console.error('Search error:', error);
    }
}

function updatePaginationControls() {
    const paginationEl = document.getElementById('paginationControls');
    if (!paginationEl) return;

    let html = '';

    // Previous button
    html += `
        <li class="page-item ${currentPage === 0 ? 'disabled' : ''}">
            <a class="page-link" href="#" data-page="${currentPage - 1}">Previous</a>
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
        html += `<li class="page-item"><a class="page-link" href="#" data-page="0">1</a></li>`;
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
        html += `<li class="page-item"><a class="page-link" href="#" data-page="${totalPages - 1}">${totalPages}</a></li>`;
    }

    // Next button
    html += `
        <li class="page-item ${currentPage >= totalPages - 1 ? 'disabled' : ''}">
            <a class="page-link" href="#" data-page="${currentPage + 1}">Next</a>
        </li>
    `;

    paginationEl.innerHTML = html;

    // Attach click handlers
    paginationEl.querySelectorAll('.page-link').forEach(link => {
        link.addEventListener('click', function(e) {
            e.preventDefault();
            if (!this.parentElement.classList.contains('disabled') &&
                !this.parentElement.classList.contains('active')) {
                const page = parseInt(this.getAttribute('data-page'));
                loadEnquiries(page, pageSize);
            }
        });
    });
}

function updateEntriesInfo() {
    const startEl = document.getElementById('entriesStart');
    const endEl = document.getElementById('entriesEnd');
    const totalEl = document.getElementById('totalEntries');

    if (!startEl || !endEl || !totalEl) return;

    const start = totalElements === 0 ? 0 : (currentPage * pageSize) + 1;
    const end = Math.min((currentPage + 1) * pageSize, totalElements);

    startEl.textContent = start;
    endEl.textContent = end;
    totalEl.textContent = totalElements;
}

   function renderEnquiriesTable(enquiries) {
       const tbody = document.querySelector('#enquiryTable tbody');
       if (!tbody) return;

       if (!enquiries || enquiries.length === 0) {
           tbody.innerHTML = `
               <tr>
                   <td colspan="9" class="text-center py-5">
                       <i class="bi bi-inbox" style="font-size: 3rem; color: #94a3b8;"></i>
                       <p class="mt-3 mb-0 text-muted">No enquiries found</p>
                       <button class="btn btn-sm btn-primary mt-2" onclick="document.getElementById('btnAddEnquiry').click()">
                           <i class="bi bi-plus-circle me-1"></i>Add First Enquiry
                       </button>
                   </td>
               </tr>
           `;
           return;
       }

       tbody.innerHTML = enquiries.map(enq => {

           const enquiryNumber = enq.enquiryNo || `ENQ${String(enq.id).padStart(6, '0')}`;

           // Parse courses
           let coursesList = [];

           if (Array.isArray(enq.coursesList) && enq.coursesList.length > 0) {
               coursesList = enq.coursesList;
           } else if (Array.isArray(enq.courses)) {
               coursesList = enq.courses;
           } else if (typeof enq.courses === 'string' && enq.courses) {
               coursesList = enq.courses.split(',').map(c => c.trim()).filter(Boolean);
           }

           const coursesHtml = coursesList.length > 0
               ? coursesList.map(course =>
                   `<span class="badge bg-primary d-block mb-1" style="white-space: normal; text-align: left; padding: 0.4rem 0.6rem;">${course}</span>`
                 ).join('')
               : '<span class="badge bg-secondary">N/A</span>';

           return `
               <tr data-id="${enq.id}">
                   <td><strong>${enquiryNumber}</strong></td>
                   <td>${enq.name || `${enq.firstName || ''} ${enq.lastName || ''}`.trim() || 'N/A'}</td>
                   <td>${enq.mobile || 'N/A'}</td>
                   <td style="max-width: 250px; min-width: 180px;">
                       <div style="display: flex; flex-direction: column; gap: 4px;">
                           ${coursesHtml}
                       </div>
                   </td>
                   <td>${enq.source || 'N/A'}</td>
                   <td>${enq.date || 'N/A'}</td>
                   <td>${enq.assign || 'Unassigned'}</td>
                   <td><span class="badge bg-success">${enq.status || 'New'}</span></td>
                   <td>
                       <!-- Actions menu -->
                       <div class="action-dropdown">
                           <button class="btn btn-light action-menu-trigger" style="padding: 0.25rem 0.5rem;">
                               <i class="bi bi-three-dots-vertical"></i>
                           </button>
                           <div class="action-menu">
                               <button class="action-menu-item" data-action="update" data-id="${enq.id}">
                                   <i class="bi bi-pencil-square"></i><span>Update</span>
                               </button>
                               <button class="action-menu-item" data-action="followup" data-id="${enq.id}">
                                   <i class="bi bi-telephone"></i><span>Follow Up</span>
                               </button>
                               <button class="action-menu-item" data-action="view" data-id="${enq.id}">
                                   <i class="bi bi-eye"></i><span>View Details</span>
                               </button>
                               <button class="action-menu-item" data-action="changestatus" data-id="${enq.id}">
                                   <i class="bi bi-pencil"></i><span>Change Enquiry Status</span>
                               </button>
                               <button class="action-menu-item" data-action="admission"
                                         data-id="${enq.id}" data-mobile="${enq.mobile}">
                                     <i class="bi bi-plus"></i><span>New Admission</span>
                               </button>
                               <button class="action-menu-item" data-action="remove" data-id="${enq.id}">
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

    // Handle Actions
    async function handleAction(action, enquiryId) {
        switch(action) {
            case 'update':
                await loadEnquiryForEdit(enquiryId);
                break;
            case 'view':
                await loadEnquiryForView(enquiryId);
                break;
            case 'followup':
                await openFollowUpModal(enquiryId);
                break;
            case 'changestatus':
                await openChangeStatusModal(enquiryId);
                break;
            case 'admission':
                   const mobile = this.getAttribute('data-mobile');
                   await openAdmissionFromEnquiry(enquiryId, mobile);
                   break;
            case 'remove':
                await deleteEnquiry(enquiryId);
                break;
        }
    }

    // Load Enquiry for Edit - Handle multiple courses
    async function loadEnquiryForEdit(id) {
        try {
            const response = await fetch(`/api/enquiries/${id}`);
            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                throw new Error(errorData.message || 'Failed to load enquiry');
            }

            const enquiry = await response.json();
            currentEnquiryId = id;

            // Personal info
            setValue('firstName', enquiry.firstName);
            setValue('middleName', enquiry.middleName);
            setValue('lastName', enquiry.lastName);
            setValue('mobilePrimary', enquiry.mobile);
            setValue('mobileSecondary', enquiry.secondaryMobile);
            setValue('emailPrimary', enquiry.email || enquiry.emailPrimary);
            setValue('emailSecondary', enquiry.secondaryEmail);
            setValue('currentAddress', enquiry.currentAddress);
            setValue('permanentAddress', enquiry.permanentAddress);
            setValue('pinCodeCurrent', enquiry.pinCurrent);
            setValue('pinCodePermanent', enquiry.pinPermanent);
            setValue('college', enquiry.college);
            setValue('qualification', enquiry.qualification);
            setValue('aadhaar', enquiry.aadhaar);
            setValue('dob', enquiry.birthDate);
            setValue('gender', enquiry.gender);

            // Handle multiple courses
            const courseSelect = document.getElementById('course');
            const courses =
                (Array.isArray(enquiry.coursesList) && enquiry.coursesList.length > 0)
                    ? enquiry.coursesList
                    : Array.isArray(enquiry.courses)
                        ? enquiry.courses
                        : (typeof enquiry.courses === 'string'
                            ? enquiry.courses.split(',').map(c => c.trim()).filter(Boolean)
                            : []);

            if (courseSelect) {
                Array.from(courseSelect.options).forEach(option => {
                    option.selected = courses.includes(option.value);
                });
                updateSelectedCoursesDisplay();
            }

            // Other enquiry details
            setValue('package', enquiry.packageName);
            setValue('demoLecture', enquiry.demoLectureRequired ? 'true' : 'false');
            setValue('interestLevel', enquiry.interestLevel);
            setValue('leadSource', enquiry.source);
            setValue('referenceName', enquiry.referenceName);
            setValue('assignTo', enquiry.assign || enquiry.assignTo);
            setValue('enquiryDate', enquiry.date || enquiry.enquiryDate);
            setValue('followupDate', enquiry.followupDate);
            setValue('note', enquiry.note);

            document.getElementById('modalTitle').innerHTML =
                '<i class="bi bi-pencil-square me-2"></i>Update Enquiry';

            const modal = new bootstrap.Modal(document.getElementById('enquiryModal'));
            modal.show();
            updateProgress(25);

        } catch (error) {
            console.error('Error loading enquiry:', error);
            showError(error.message || 'Failed to load enquiry details');
        }
    }

    // Load Enquiry for View
    async function loadEnquiryForView(id) {
        try {
            const response = await fetch(`/api/enquiries/${id}`);
            if (!response.ok) throw new Error('Failed to load enquiry');

            const enquiry = await response.json();

            setValue('viewStudentName', enquiry.name || `${enquiry.firstName || ''} ${enquiry.lastName || ''}`.trim());
            setValue('viewFirstName', enquiry.firstName);
            setValue('viewMiddleName', enquiry.middleName);
            setValue('viewLastName', enquiry.lastName);
            setValue('viewMobilePrimary', enquiry.mobile);
            setValue('viewMobileSecondary', enquiry.secondaryMobile);
            setValue('viewEmailPrimary', enquiry.email);
            setValue('viewEmailSecondary', enquiry.secondaryEmail);
            setValue('viewCurrentAddress', enquiry.currentAddress);
            setValue('viewPermanentAddress', enquiry.permanentAddress);
            setValue('viewPinCodeCurrent', enquiry.pinCurrent);
            setValue('viewPinCodePermanent', enquiry.pinPermanent);
            setValue('viewCollege', enquiry.college);
            setValue('viewQualification', enquiry.qualification);
            setValue('viewAadhaar', enquiry.aadhaar);
            setValue('viewDob', enquiry.birthDate);
            setValue('viewGender', enquiry.gender);
            setValue('viewCourse', enquiry.courses);
            setValue('viewPackage', enquiry.packageName);
            setValue('viewDemoLecture', enquiry.demoLectureRequired ? 'Yes' : 'No');
            setValue('viewInterestLevel', enquiry.interestLevel);
            setValue('viewLeadSource', enquiry.source);
            setValue('viewReferenceName', enquiry.referenceName);
            setValue('viewAssignTo', enquiry.assign);
            setValue('viewEnquiryDate', enquiry.date);
            setValue('viewNote', enquiry.note);

            const modal = new bootstrap.Modal(document.getElementById('viewModal'));
            modal.show();

        } catch (error) {
            console.error('Error loading enquiry:', error);
            showError('Failed to load enquiry details');
        }
    }

    // Delete Enquiry
    async function deleteEnquiry(id) {
        const result = await Swal.fire({
            title: 'Confirmation',
            text: 'Do you want to delete this enquiry?',
            icon: 'warning',
            showCancelButton: true,
            confirmButtonText: 'Yes, Delete',
            cancelButtonText: 'Cancel',
            confirmButtonColor: '#ef4444'
        });

        if (result.isConfirmed) {
            try {
                const response = await fetch(`/api/enquiries/${id}`, {
                    method: 'DELETE'
                });

                if (!response.ok) throw new Error('Delete failed');

                showSuccess('Enquiry deleted successfully');
                loadEnquiries();
            } catch (error) {
                console.error('Delete error:', error);
                showError('Failed to delete enquiry');
            }
        }
    }

    // Open Change Status Modal
    async function openChangeStatusModal(id) {
        try {
            const response = await fetch(`/api/enquiries/${id}`);
            if (!response.ok) throw new Error('Failed to load enquiry');

            const enquiry = await response.json();

            const { value: status } = await Swal.fire({
                title: 'Change Enquiry Status',
                html: `
                    <select id="statusSelect" class="form-select">
                        <option value="New" ${enquiry.status === 'New' ? 'selected' : ''}>New</option>
                        <option value="In Process" ${enquiry.status === 'In Process' ? 'selected' : ''}>In Process</option>
                        <option value="Closed" ${enquiry.status === 'Closed' ? 'selected' : ''}>Closed</option>
                        <option value="Lost" ${enquiry.status === 'Lost' ? 'selected' : ''}>Lost</option>
                    </select>
                `,
                showCancelButton: true,
                confirmButtonText: 'Ok',
                cancelButtonText: 'Cancel',
                preConfirm: () => {
                    return document.getElementById('statusSelect').value;
                }
            });

            if (status) {
                await updateEnquiryStatus(id, status);
            }

        } catch (error) {
            console.error('Error:', error);
            showError('Failed to change status');
        }
    }

    // Update Enquiry Status
    async function updateEnquiryStatus(id, status) {
        try {
            const response = await fetch(`/api/enquiries/${id}/status`, {
                method: 'PATCH',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ status: status })
            });

            if (!response.ok) throw new Error('Failed to update status');

            showSuccess('Status updated successfully');
            loadEnquiries();
        } catch (error) {
            console.error('Error updating status:', error);
            showError('Failed to update status');
        }
    }

    // Open Admission Page - Check enquiry exists
    async function openAdmissionPage(enquiryId) {
        try {
            // Check if enquiry exists
            const response = await fetch(`/api/enquiries/${enquiryId}`);
            if (!response.ok) {
                throw new Error('Enquiry not found');
            }

            const enquiry = await response.json();

            // Store enquiry data in sessionStorage for admission form
            sessionStorage.setItem('admissionEnquiry', JSON.stringify(enquiry));

            // Redirect to admission page
            window.location.href = `/admission?enquiryId=${enquiryId}`;

        } catch (error) {
            console.error('Admission error:', error);
            showError('Please create an enquiry first before proceeding with admission');
        }
    }

    // Open Follow-Up Modal
    async function openFollowUpModal(id) {
        try {
            const response = await fetch(`/api/enquiries/${id}`);
            if (!response.ok) throw new Error('Failed to load enquiry');

            const enquiry = await response.json();
            currentFollowUpEnquiry = enquiry;

            setValue('followUpStudentName', enquiry.name || `${enquiry.firstName || ''} ${enquiry.lastName || ''}`.trim());
            setValue('followUpMobile', enquiry.mobile);

            const today = new Date().toISOString().split('T')[0];
            document.getElementById('nextFollowUpDate').setAttribute('min', today);

            document.getElementById('followUpForm').reset();
            setValue('followUpStudentName', enquiry.name || `${enquiry.firstName || ''} ${enquiry.lastName || ''}`.trim());
            setValue('followUpMobile', enquiry.mobile);

            await loadFollowUpHistory(id);

            const modal = new bootstrap.Modal(document.getElementById('followUpModal'));
            modal.show();

        } catch (error) {
            console.error('Error opening follow-up modal:', error);
            showError('Failed to load enquiry details');
        }
    }

    // Load Follow-Up History
    async function loadFollowUpHistory(enquiryId) {
        const tbody = document.getElementById('followUpHistoryBody');

        try {
            const response = await fetch(`/api/enquiries/${enquiryId}/followups`);

            if (!response.ok) {
                throw new Error('Failed to load follow-ups');
            }

            const history = await response.json();

            if (history && history.length > 0) {
                tbody.innerHTML = history.map(f => `
                    <tr data-followup-id="${f.id}">
                        <td>${f.followUpDate || '-'}</td>
                        <td>${f.nextFollowUpDate || '-'}</td>
                        <td><span class="badge bg-info">${f.mode || '-'}</span></td>
                        <td>${f.note || '-'}</td>
                        <td>
                            <button class="btn btn-sm btn-danger delete-followup"
                                    data-followup-id="${f.id}"
                                    title="Delete">
                                <i class="bi bi-trash"></i>
                            </button>
                        </td>
                    </tr>
                `).join('');

                tbody.querySelectorAll('.delete-followup').forEach(btn => {
                    btn.addEventListener('click', async function() {
                        const followUpId = this.getAttribute('data-followup-id');
                        await deleteFollowUp(followUpId, enquiryId);
                    });
                });
            } else {
                tbody.innerHTML = `
                    <tr>
                        <td colspan="5" class="text-center text-muted">
                            <small>No follow-up history available</small>
                        </td>
                    </tr>
                `;
            }
        } catch (error) {
            console.error('Error loading follow-up history:', error);
            tbody.innerHTML = `
                <tr>
                    <td colspan="5" class="text-center text-muted">
                        <small>No follow-up history available</small>
                    </td>
                </tr>
            `;
        }
    }

    // Delete Follow-Up
    async function deleteFollowUp(followUpId, enquiryId) {
        const result = await Swal.fire({
            title: 'Delete Follow-up?',
            text: 'Are you sure you want to delete this follow-up record?',
            icon: 'warning',
            showCancelButton: true,
            confirmButtonText: 'Yes, Delete',
            cancelButtonText: 'Cancel',
            confirmButtonColor: '#ef4444'
        });

        if (result.isConfirmed) {
            try {
                const response = await fetch(`/api/enquiries/followups/${followUpId}`, {
                    method: 'DELETE'
                });

                if (!response.ok) throw new Error('Failed to delete follow-up');

                showSuccess('Follow-up deleted successfully');
                await loadFollowUpHistory(enquiryId);
            } catch (error) {
                console.error('Error deleting follow-up:', error);
                showError('Failed to delete follow-up');
            }
        }
    }

    // Save Follow-Up
    async function saveFollowUp() {
        const form = document.getElementById('followUpForm');

        if (!form.checkValidity()) {
            form.reportValidity();
            return;
        }

        const followUpData = {
            enquiryId: currentFollowUpEnquiry.id,
            followUpDate: new Date().toISOString().split('T')[0],
            nextFollowUpDate: getValue('nextFollowUpDate') || null,
            mode: getValue('followUpMode'),
            note: getValue('followUpNote')
        };

        try {
            const response = await fetch(`/api/enquiries/${currentFollowUpEnquiry.id}/followups`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(followUpData)
            });

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                throw new Error(errorData.message || 'Failed to save follow-up');
            }

            showSuccess('Follow-up saved successfully!');

            await loadFollowUpHistory(currentFollowUpEnquiry.id);

            form.reset();
            setValue('followUpStudentName', currentFollowUpEnquiry.name ||
                `${currentFollowUpEnquiry.firstName || ''} ${currentFollowUpEnquiry.lastName || ''}`.trim());
            setValue('followUpMobile', currentFollowUpEnquiry.mobile);

            loadEnquiries();

            const sendSMS = await Swal.fire({
                title: 'Send SMS?',
                text: 'Do you want to send follow-up SMS to the student?',
                icon: 'question',
                showCancelButton: true,
                confirmButtonText: 'Yes, Send SMS',
                cancelButtonText: 'No',
                confirmButtonColor: '#667eea'
            });

            if (sendSMS.isConfirmed) {
                sendFollowUpSMS(currentFollowUpEnquiry.mobile, followUpData);
            }

        } catch (error) {
            console.error('Error saving follow-up:', error);
            showError(error.message || 'Failed to save follow-up');
        }
    }

    // Send Follow-Up SMS
    function sendFollowUpSMS(mobile, followUpData) {
        Swal.fire({
            title: 'SMS Sent!',
            text: `Follow-up reminder sent to ${mobile}`,
            icon: 'success',
            confirmButtonColor: '#667eea'
        });
    }

   function parseCSV(text) {
       const lines = text.split('\n').filter(line => line.trim());
       const importType = document.querySelector('input[name="importType"]:checked').value;
   
       importedData = [];
       const previewData = [];
   
       for (let i = 1; i < lines.length; i++) {
           const values = lines[i].match(/(".*?"|[^,]+)(?=\s*,|\s*$)/g) || [];
           const row = values.map(v => v.trim().replace(/^"|"$/g, ''));
   
           if (row.length > 0) {
               let record;
   
               if (importType === 'old') {
                   const nameParts = (row[1] || '').split(' ').filter(Boolean);
                   const coursesStr = row[3] || '';
                   const rawCourses = coursesStr
                       .split(/[,\n]/)
                       .map(c => c.trim())
                       .filter(Boolean);
   
                   if (rawCourses.length === 0) {
                       console.warn(`Row ${i}: No courses found, skipping`);
                       continue;
                   }
   
                   record = {
                       enquiryNo: row[0] && row[0].trim() ? row[0].trim() : null,
                       firstName: nameParts[0] || '',
                       middleName: nameParts.length > 2 ? nameParts.slice(1, -1).join(' ') : '',
                       lastName: nameParts.length > 1 ? nameParts[nameParts.length - 1] : '',
                       mobilePrimary: row[2],
                       courses: rawCourses,
                       leadSource: row[4] || 'Unknown',
                       enquiryDate: validateDate(row[5]) || getTodayDate(), //  Validate date
                       assignTo: row[6] || null,
                       status: row[7] || 'New'
                   };
               } else {
                   const coursesStr = row[13] || '';
                   const rawCourses = coursesStr
                       .split(/[,\n]/)
                       .map(c => c.trim())
                       .filter(Boolean);
   
                   record = {
                       enquiryNo: row[0] && row[0].trim() ? row[0].trim() : null,
                       firstName: row[1],
                       middleName: row[2],
                       lastName: row[3],
                       mobilePrimary: row[4],
                       mobileSecondary: row[5],
                       emailPrimary: row[6],
                       currentAddress: row[7],
                       permanentAddress: row[8],
                       college: row[9],
                       enquiryDate: validateDate(row[10]) || getTodayDate(), //  Validate date
                       followupDate: validateDate(row[11]) || null,
                       note: row[12],
                       courses: rawCourses,
                       leadSource: row[14] || 'Unknown'
                   };
               }
   
               // Validate required fields
               if (record.mobilePrimary && record.courses.length > 0) {
                   importedData.push(record);
                   if (previewData.length < 5) previewData.push(record);
               } else {
                   console.warn(`Row ${i}: Missing mobile or courses, skipping`);
               }
           }
       }
   
       displayPreview(previewData);
       document.getElementById('recordCount').textContent = importedData.length;
       document.getElementById('importBtn').disabled = false;
   }
   
   //  Add date validation function
   function validateDate(dateStr) {
       if (!dateStr || dateStr.trim() === '') return null;
       
       // Try parsing DD-MM-YYYY format
       const parts = dateStr.trim().split(/[-/]/);
       if (parts.length === 3) {
           const [day, month, year] = parts;
           return `${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}`;
       }
       
       return null;
   }
   
   function getTodayDate() {
       return new Date().toISOString().split('T')[0];
   }

    function displayPreview(data) {
        const thead = document.getElementById('previewTableHead');
        const tbody = document.getElementById('previewTableBody');

        thead.innerHTML = '<tr><th>First Name</th><th>Last Name</th><th>Mobile</th><th>Courses</th></tr>';
        tbody.innerHTML = data.map(row => `
            <tr>
                <td>${row.firstName || '-'}</td>
                <td>${row.lastName || '-'}</td>
                <td>${row.mobilePrimary || '-'}</td>
                <td>${Array.isArray(row.courses) ? row.courses.join(', ') : (row.courses || '-')}</td>
            </tr>
        `).join('');

        document.getElementById('importPreview').style.display = 'block';
    }

    function handleImportTypeChange() {
        const oldFormatInfo = document.getElementById('oldFormatInfo');
        const newFormatInfo = document.getElementById('newFormatInfo');
        const isOld = this.value === 'old';

        oldFormatInfo.style.display = isOld ? 'block' : 'none';
        newFormatInfo.style.display = isOld ? 'none' : 'block';

        resetImport();
    }

    function resetImport() {
        document.getElementById('importPreview').style.display = 'none';
        document.getElementById('csvFileInput').value = '';
        document.getElementById('importBtn').disabled = true;
        importedData = [];
    }

    function handleCSVFile(file) {
        if (!file) return;
        const reader = new FileReader();
        reader.onload = (e) => parseCSV(e.target.result);
        reader.readAsText(file);
    }

    function openImportModal() {
        const modal = new bootstrap.Modal(document.getElementById('importModal'));
        modal.show();
    }

    // ========== NEW ADMISSION FROM ENQUIRY ==========

    async function openAdmissionFromEnquiry(enquiryId, mobileNumber) {
        try {
            showLoading('Checking eligibility...');

            // Step 1: Validate enquiry exists
            const enquiryResponse = await fetch(`/api/enquiries/${enquiryId}`);
            if (!enquiryResponse.ok) {
                throw new Error('Enquiry not found');
            }
            const enquiry = await enquiryResponse.json();

            // Step 2: Check if admission can be created
            const canCreateResponse = await fetch(`/api/admissions/can-create/${mobileNumber}`);
            const canCreateData = await canCreateResponse.json();

            Swal.close();

            if (!canCreateData.canCreate) {
                await Swal.fire({
                    title: 'Cannot Create Admission',
                    html: `
                        <div class="text-start">
                            <p class="mb-3">An admission cannot be created for this student.</p>
                            <div class="alert alert-warning mb-0">
                                <strong>Possible reasons:</strong>
                                <ul class="mb-0 mt-2">
                                    <li>Admission already exists for mobile: <strong>${mobileNumber}</strong></li>
                                    <li>Student has been marked as inactive</li>
                                </ul>
                            </div>
                            <p class="mt-3 mb-0 text-muted">
                                <i class="bi bi-info-circle me-1"></i>
                                Please check existing admissions or contact administrator.
                            </p>
                        </div>
                    `,
                    icon: 'warning',
                    confirmButtonText: 'OK',
                    confirmButtonColor: '#667eea',
                    width: '500px'
                });
                return;
            }

            // Step 3: Display confirmation with enquiry details
            const coursesDisplay = Array.isArray(enquiry.coursesList) && enquiry.coursesList.length > 0
                ? enquiry.coursesList.map(c => `<li>${c}</li>`).join('')
                : enquiry.courses || 'N/A';

            const result = await Swal.fire({
                title: 'Create New Admission',
                html: `
                    <div class="text-start">
                        <table class="table table-sm table-borderless">
                            <tr>
                                <td class="text-muted" style="width: 100px;"><strong>Name:</strong></td>
                                <td>${enquiry.name || `${enquiry.firstName} ${enquiry.lastName}`}</td>
                            </tr>
                            <tr>
                                <td class="text-muted"><strong>Mobile:</strong></td>
                                <td>${enquiry.mobile}</td>
                            </tr>
                            <tr>
                                <td class="text-muted"><strong>Course(s):</strong></td>
                                <td><ul class="mb-0 ps-3">${coursesDisplay}</ul></td>
                            </tr>
                            <tr>
                                <td class="text-muted"><strong>College:</strong></td>
                                <td>${enquiry.college || 'N/A'}</td>
                            </tr>
                        </table>
                        <div class="alert alert-info mb-0 mt-3">
                            <i class="bi bi-info-circle me-2"></i>
                            All enquiry details will be pre-filled in the admission form.
                        </div>
                    </div>
                `,
                icon: 'question',
                showCancelButton: true,
                confirmButtonText: '<i class="bi bi-check-circle me-2"></i>Yes, Create Admission',
                cancelButtonText: '<i class="bi bi-x-circle me-2"></i>Cancel',
                confirmButtonColor: '#667eea',
                cancelButtonColor: '#6c757d',
                width: '600px'
            });

            if (result.isConfirmed) {
                // Step 4: Store enquiry data for pre-fill
                sessionStorage.setItem('admissionEnquiry', JSON.stringify(enquiry));
                sessionStorage.setItem('admissionFromEnquiry', 'true');

                // Step 5: Redirect to admission page
                showLoading('Redirecting to admission form...');
                setTimeout(() => {
                    window.location.href = `/students/admission?enquiryId=${enquiryId}&mobile=${mobileNumber}`;
                }, 500);
            }

        } catch (error) {
            Swal.close();
            console.error('Error opening admission:', error);

            await Swal.fire({
                title: 'Error',
                text: error.message || 'Failed to open admission form. Please try again.',
                icon: 'error',
                confirmButtonColor: '#ef4444'
            });
        }
    }

    // Attach event listeners properly
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

        tbody.querySelectorAll('.action-menu-item[data-action="admission"]').forEach(item => {
            item.addEventListener('click', async function(e) {
                e.preventDefault();
                e.stopPropagation();

                const enquiryId = this.getAttribute('data-id');
                const mobile = this.getAttribute('data-mobile');

                console.log('Opening admission for:', { enquiryId, mobile });

                // Close menu
                document.querySelectorAll('.action-menu').forEach(menu => {
                    menu.classList.remove('show');
                });

                await openAdmissionFromEnquiry(enquiryId, mobile);
            });
        });

        // Attach other action listeners
        tbody.querySelectorAll('.action-menu-item:not([data-action="admission"])').forEach(item => {
            item.addEventListener('click', function() {
                const action = this.getAttribute('data-action');
                const id = this.getAttribute('data-id');
                handleAction(action, id);
            });
        });
    }

        async function prefillAdmissionForm(enquiry) {
            // Store enquiry data in sessionStorage for admission page
            sessionStorage.setItem('admissionEnquiry', JSON.stringify(enquiry));

            // Redirect to admission page
            window.location.href = `/admission?enquiryId=${enquiry.id}`;
        }

        function collectFormData() {
            const courseSelect = document.getElementById('course');
            const selectedCourses = Array.from(courseSelect.selectedOptions).map(option => option.value);

            return {
                firstName: getValue('firstName'),
                middleName: getValue('middleName'),
                lastName: getValue('lastName'),
                mobile: getValue('mobilePrimary'),
                secondaryMobile: getValue('mobileSecondary'),
                email: getValue('emailPrimary'),
                secondaryEmail: getValue('emailSecondary'),
                currentAddress: getValue('currentAddress'),
                permanentAddress: getValue('permanentAddress'),
                pinCurrent: getValue('pinCodeCurrent'),
                pinPermanent: getValue('pinCodePermanent'),
                college: getValue('college'),
                qualification: getValue('qualification'),
                aadhaar: getValue('aadhaar'),
                birthDate: getValue('dob') || null,
                gender: getValue('gender'),
                courses: selectedCourses,
                packageName: getValue('package'),
                demoLectureRequired: getValue('demoLecture') === 'true',
                interestLevel: getValue('interestLevel'),
                source: getValue('leadSource'),
                referenceName: getValue('referenceName'),
                enquiryDate: getValue('enquiryDate') || new Date().toISOString().split('T')[0],
                followupDate: getValue('followupDate') || null,
                assignTo: getValue('assignTo'),
                note: getValue('note')
            };
        }

       function validateFormData(data) {
           // Check required fields
           if (!data.firstName || !data.lastName) {
               console.error('Validation failed: Name required');
               return false;
           }

           if (!data.mobile || !/^[6-9]\d{9}$/.test(data.mobile)) {
               console.error('Validation failed: Invalid mobile number');
               return false;
           }

           if (!data.courses || data.courses.length === 0) {
               console.error('Validation failed: At least one course required');
               return false;
           }

           if (!data.source) {
               console.error('Validation failed: Source required');
               return false;
           }

           console.log('Validation passed:', {
               name: `${data.firstName} ${data.lastName}`,
               mobile: data.mobile,
               courses: data.courses,
               source: data.source
           });

           return true;
       }

        function getValue(id) {
            const el = document.getElementById(id);
            return el ? el.value : '';
        }

        function setValue(id, value) {
            const el = document.getElementById(id);
            if (el) el.value = value || '';
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
                confirmButtonColor: '#667eea'
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

        function openAddModal() {
            currentEnquiryId = null;
            clearForm();
            document.getElementById('modalTitle').innerHTML = '<i class="bi bi-person-plus me-2"></i>Add New Enquiry';
            const modal = new bootstrap.Modal(document.getElementById('enquiryModal'));
            modal.show();
            updateProgress(25);
        }

        function clearForm() {
            ['personalForm', 'communicationForm', 'courseForm', 'sourceForm'].forEach(id => {
                document.getElementById(id)?.reset();
            });

            const displayArea = document.getElementById('selectedCoursesDisplay');
            if (displayArea) {
                displayArea.innerHTML = '';
            }
        }

        function closeModal(modalId) {
            const modalEl = document.getElementById(modalId);
            const modal = bootstrap.Modal.getInstance(modalEl);
            if (modal) modal.hide();
        }

        function updateProgress(width) {
            const bar = document.getElementById('progressBar');
            if (bar) bar.style.width = width + '%';
        }

        function updateViewProgress(width) {
            const bar = document.getElementById('viewProgressBar');
            if (bar) bar.style.width = width + '%';
        }

        function debounce(func, wait) {
            let timeout;
            return function(...args) {
                clearTimeout(timeout);
                timeout = setTimeout(() => func.apply(this, args), wait);
            };
        }

})();