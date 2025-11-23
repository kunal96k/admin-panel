        (function() {
            'use strict';

            // Sample data
            const sampleEnquiry = {
                id: 16261,
                firstName: 'MRUNAL',
                middleName: '',
                lastName: 'GUNJAL',
                mobile: '9209462181',
                mobileSecondary: '',
                email: 'mrunal@example.com',
                emailSecondary: '',
                course: 'JAVA CORE AND ADVANCE',
                source: 'Walk-in',
                date: '2025-11-19',
                assignTo: 'Charushila Wankhede',
                status: 'New',
                college: 'ABC College',
                qualification: 'B.Tech',
                aadhaar: '123456789012',
                dob: '2000-01-15',
                gender: 'Male',
                currentAddress: '123 Main Street, Nashik',
                permanentAddress: '123 Main Street, Nashik',
                pinCodeCurrent: '422001',
                pinCodePermanent: '422001',
                package: 'Premium',
                demoLecture: 'No',
                interestLevel: 'High',
                referenceName: 'John Doe',
                note: 'Interested in Java development'
            };

            let importedData = [];

            // Initialize when DOM is ready
            document.addEventListener('DOMContentLoaded', function() {
                initializeEventListeners();
            });

            function initializeEventListeners() {
                // Action menu triggers
                document.querySelectorAll('.action-menu-trigger').forEach(trigger => {
                    trigger.addEventListener('click', function(e) {
                        e.stopPropagation();
                        const menu = this.nextElementSibling;

                        // Close all other menus
                        document.querySelectorAll('.action-menu').forEach(m => {
                            if (m !== menu) m.classList.remove('show');
                        });

                        menu.classList.toggle('show');
                    });
                });

                // Import type radio buttons
                const importTypeRadios = document.querySelectorAll('input[name="importType"]');
                importTypeRadios.forEach(radio => {
                    radio.addEventListener('change', function() {
                        const oldFormatInfo = document.getElementById('oldFormatInfo');
                        const newFormatInfo = document.getElementById('newFormatInfo');

                        if (this.value === 'old') {
                            oldFormatInfo.style.display = 'block';
                            newFormatInfo.style.display = 'none';
                        } else {
                            oldFormatInfo.style.display = 'none';
                            newFormatInfo.style.display = 'block';
                        }

                        // Reset import preview
                        const importPreview = document.getElementById('importPreview');
                        if (importPreview) {
                            importPreview.style.display = 'none';
                        }
                        const csvFileInput = document.getElementById('csvFileInput');
                        if (csvFileInput) {
                            csvFileInput.value = '';
                        }
                    });
                });

                // Close menus when clicking outside
                document.addEventListener('click', function() {
                    document.querySelectorAll('.action-menu').forEach(menu => {
                        menu.classList.remove('show');
                    });
                });

                // Action menu items
                document.querySelectorAll('.action-menu-item').forEach(item => {
                    item.addEventListener('click', function() {
                        const action = this.getAttribute('data-action');
                        handleAction(action);
                    });
                });

                // Top buttons
                const btnAddEnquiry = document.getElementById('btnAddEnquiry');
                if (btnAddEnquiry) {
                    btnAddEnquiry.addEventListener('click', openAddModal);
                }

                const btnImportCSV = document.getElementById('btnImportCSV');
                if (btnImportCSV) {
                    btnImportCSV.addEventListener('click', openImportModal);
                }

                const btnExportCSV = document.getElementById('btnExportCSV');
                if (btnExportCSV) {
                    btnExportCSV.addEventListener('click', exportCSV);
                }

                const btnSaveEnquiry = document.getElementById('btnSaveEnquiry');
                if (btnSaveEnquiry) {
                    btnSaveEnquiry.addEventListener('click', saveEnquiry);
                }

                // Tab progress bars
                document.querySelectorAll('#enquiryTabs .nav-link').forEach(tab => {
                    tab.addEventListener('click', function() {
                        const progress = this.getAttribute('data-progress');
                        updateProgress(progress);
                    });
                });

                document.querySelectorAll('#viewModal .nav-link').forEach(tab => {
                    tab.addEventListener('click', function() {
                        const progress = this.getAttribute('data-view-progress');
                        updateViewProgress(progress);
                    });
                });

                // CSV Import
                const btnBrowseFile = document.getElementById('btnBrowseFile');
                if (btnBrowseFile) {
                    btnBrowseFile.addEventListener('click', function() {
                        document.getElementById('csvFileInput').click();
                    });
                }

                const csvFileInput = document.getElementById('csvFileInput');
                if (csvFileInput) {
                    csvFileInput.addEventListener('change', function(e) {
                        handleCSVFile(e.target.files[0]);
                    });
                }

                const importBtn = document.getElementById('importBtn');
                if (importBtn) {
                    importBtn.addEventListener('click', importCSV);
                }

                // Drag and drop
                const importArea = document.getElementById('importArea');
                if (importArea) {
                    importArea.addEventListener('dragover', function(e) {
                        e.preventDefault();
                        this.classList.add('dragover');
                    });

                    importArea.addEventListener('dragleave', function() {
                        this.classList.remove('dragover');
                    });

                    importArea.addEventListener('drop', function(e) {
                        e.preventDefault();
                        this.classList.remove('dragover');
                        const file = e.dataTransfer.files[0];
                        if (file && file.name.endsWith('.csv')) {
                            handleCSVFile(file);
                        } else {
                            Swal.fire('Error!', 'Please upload a valid CSV file.', 'error');
                        }
                    });
                }

                // Search functionality
                const searchInput = document.getElementById('searchInput');
                if (searchInput) {
                    searchInput.addEventListener('input', function(e) {
                        const searchTerm = e.target.value.toLowerCase();
                        console.log('Searching for:', searchTerm);
                    });
                }
            }

            function handleAction(action) {
                switch(action) {
                    case 'update':
                        openUpdateModal();
                        break;
                    case 'followup':
                        followUp();
                        break;
                    case 'remove':
                        removeEnquiry();
                        break;
                    case 'admission':
                        newAdmission();
                        break;
                    case 'status':
                        changeStatus();
                        break;
                    case 'whatsapp':
                        whatsapp();
                        break;
                    case 'view':
                        openViewModal();
                        break;
                }
            }

            function updateProgress(width) {
                const progressBar = document.getElementById('progressBar');
                if (progressBar) {
                    progressBar.style.width = width + '%';
                }
            }

            function updateViewProgress(width) {
                const viewProgressBar = document.getElementById('viewProgressBar');
                if (viewProgressBar) {
                    viewProgressBar.style.width = width + '%';
                }
            }

            function openAddModal() {

                hideAllActionMenus();

                const modalTitle = document.getElementById('modalTitle');
                if (modalTitle) {
                    modalTitle.innerHTML = '<i class="bi bi-person-plus me-2"></i>Add New Enquiry';
                }
                clearForm();
                const modal = new bootstrap.Modal(document.getElementById('enquiryModal'));
                modal.show();
                updateProgress(25);
            }

            function openUpdateModal() {
                const modalTitle = document.getElementById('modalTitle');
                if (modalTitle) {
                    modalTitle.innerHTML = '<i class="bi bi-pencil-square me-2"></i>Update Enquiry';
                }

                // Fill form with sample data
                fillFormField('firstName', sampleEnquiry.firstName);
                fillFormField('middleName', sampleEnquiry.middleName);
                fillFormField('lastName', sampleEnquiry.lastName);
                fillFormField('college', sampleEnquiry.college);
                fillFormField('qualification', sampleEnquiry.qualification);
                fillFormField('aadhaar', sampleEnquiry.aadhaar);
                fillFormField('dob', sampleEnquiry.dob);
                fillFormField('gender', sampleEnquiry.gender === 'Male' ? 'M' : 'F');
                fillFormField('mobilePrimary', sampleEnquiry.mobile);
                fillFormField('mobileSecondary', sampleEnquiry.mobileSecondary);
                fillFormField('emailPrimary', sampleEnquiry.email);
                fillFormField('emailSecondary', sampleEnquiry.emailSecondary);
                fillFormField('currentAddress', sampleEnquiry.currentAddress);
                fillFormField('permanentAddress', sampleEnquiry.permanentAddress);
                fillFormField('pinCodeCurrent', sampleEnquiry.pinCodeCurrent);
                fillFormField('pinCodePermanent', sampleEnquiry.pinCodePermanent);
                fillFormField('course', sampleEnquiry.course);
                fillFormField('package', sampleEnquiry.package);
                fillFormField('demoLecture', sampleEnquiry.demoLecture === 'No' ? 'false' : 'true');
                fillFormField('interestLevel', sampleEnquiry.interestLevel);
                fillFormField('leadSource', sampleEnquiry.source);
                fillFormField('referenceName', sampleEnquiry.referenceName);
                fillFormField('assignTo', sampleEnquiry.assignTo);
                fillFormField('enquiryDate', sampleEnquiry.date);
                fillFormField('note', sampleEnquiry.note);

                const modal = new bootstrap.Modal(document.getElementById('enquiryModal'));
                modal.show();
                updateProgress(25);
            }

            function openViewModal() {
                const viewStudentName = document.getElementById('viewStudentName');
                if (viewStudentName) {
                    viewStudentName.textContent = sampleEnquiry.firstName + ' ' + sampleEnquiry.lastName;
                }

                // Fill view form
                fillFormField('viewFirstName', sampleEnquiry.firstName);
                fillFormField('viewMiddleName', sampleEnquiry.middleName);
                fillFormField('viewLastName', sampleEnquiry.lastName);
                fillFormField('viewCollege', sampleEnquiry.college);
                fillFormField('viewQualification', sampleEnquiry.qualification);
                fillFormField('viewAadhaar', sampleEnquiry.aadhaar);
                fillFormField('viewDob', sampleEnquiry.dob);
                fillFormField('viewGender', sampleEnquiry.gender);
                fillFormField('viewMobilePrimary', sampleEnquiry.mobile);
                fillFormField('viewMobileSecondary', sampleEnquiry.mobileSecondary);
                fillFormField('viewEmailPrimary', sampleEnquiry.email);
                fillFormField('viewEmailSecondary', sampleEnquiry.emailSecondary);
                fillFormField('viewCurrentAddress', sampleEnquiry.currentAddress);
                fillFormField('viewPermanentAddress', sampleEnquiry.permanentAddress);
                fillFormField('viewPinCodeCurrent', sampleEnquiry.pinCodeCurrent);
                fillFormField('viewPinCodePermanent', sampleEnquiry.pinCodePermanent);
                fillFormField('viewCourse', sampleEnquiry.course);
                fillFormField('viewPackage', sampleEnquiry.package);
                fillFormField('viewDemoLecture', sampleEnquiry.demoLecture);
                fillFormField('viewInterestLevel', sampleEnquiry.interestLevel);
                fillFormField('viewLeadSource', sampleEnquiry.source);
                fillFormField('viewReferenceName', sampleEnquiry.referenceName);
                fillFormField('viewAssignTo', sampleEnquiry.assignTo);
                fillFormField('viewEnquiryDate', sampleEnquiry.date);
                fillFormField('viewNote', sampleEnquiry.note);

                const modal = new bootstrap.Modal(document.getElementById('viewModal'));
                modal.show();
                updateViewProgress(25);
            }

            function fillFormField(id, value) {
                const field = document.getElementById(id);
                if (field) {
                    field.value = value || '';
                }
            }

            function clearForm() {
                const forms = ['personalForm', 'communicationForm', 'followupForm', 'sourceForm'];
                forms.forEach(formId => {
                    const form = document.getElementById(formId);
                    if (form) {
                        form.reset();
                    }
                });
            }

            function saveEnquiry() {
                Swal.fire({
                    title: 'Success!',
                    text: 'Enquiry saved successfully!',
                    icon: 'success',
                    confirmButtonText: 'OK',
                    confirmButtonColor: '#667eea'
                }).then(() => {
                    const modalEl = document.getElementById('enquiryModal');
                    const modal = bootstrap.Modal.getInstance(modalEl);
                    if (modal) {
                        modal.hide();
                    }
                });
            }

            function followUp() {
                Swal.fire({
                    title: 'Follow Up',
                    html: `
                        <div class="text-start">
                            <label class="form-label">Follow Up Date</label>
                            <input type="date" id="followUpDate" class="form-control mb-3">
                            <label class="form-label">Note</label>
                            <textarea id="followUpNote" class="form-control" rows="3"></textarea>
                        </div>
                    `,
                    showCancelButton: true,
                    confirmButtonText: 'Save',
                    confirmButtonColor: '#667eea',
                    cancelButtonText: 'Cancel'
                }).then((result) => {
                    if (result.isConfirmed) {
                        Swal.fire('Success!', 'Follow up scheduled!', 'success');
                    }
                });
            }

            function removeEnquiry() {
                Swal.fire({
                    title: 'Confirmation',
                    text: 'Do you want to delete the selected enquiry?',
                    icon: 'warning',
                    showCancelButton: true,
                    confirmButtonText: 'OK',
                    cancelButtonText: 'Cancel',
                    confirmButtonColor: '#ef4444',
                    cancelButtonColor: '#64748b'
                }).then((result) => {
                    if (result.isConfirmed) {
                        Swal.fire('Deleted!', 'Enquiry has been deleted.', 'success');
                    }
                });
            }

            function newAdmission() {
                Swal.fire({
                    title: 'New Admission',
                    text: 'Convert this enquiry to admission?',
                    icon: 'question',
                    showCancelButton: true,
                    confirmButtonText: 'Yes, Convert',
                    cancelButtonText: 'Cancel',
                    confirmButtonColor: '#667eea'
                }).then((result) => {
                    if (result.isConfirmed) {
                        Swal.fire('Success!', 'Converted to admission successfully!', 'success');
                    }
                });
            }

            function changeStatus() {
                Swal.fire({
                    title: 'Change Enquiry Status',
                    html: `
                        <select id="statusSelect" class="form-select">
                            <option value="">-- Select Status --</option>
                            <option value="New">New</option>
                            <option value="In Progress">In Progress</option>
                            <option value="Contacted">Contacted</option>
                            <option value="Converted">Converted</option>
                            <option value="Lost">Lost</option>
                        </select>
                    `,
                    showCancelButton: true,
                    confirmButtonText: 'Update',
                    confirmButtonColor: '#667eea',
                    cancelButtonText: 'Cancel'
                }).then((result) => {
                    if (result.isConfirmed) {
                        Swal.fire('Updated!', 'Status updated successfully!', 'success');
                    }
                });
            }

            function whatsapp() {
                const phone = sampleEnquiry.mobile;
                const message = `Hello ${sampleEnquiry.firstName}, Thank you for your interest in our courses!`;
                window.open(`https://wa.me/91${phone}?text=${encodeURIComponent(message)}`, '_blank');
            }

            function exportCSV() {
                const headers = ['Enquiry No.', 'Student Name', 'Mobile No.', 'Course', 'Enquiry Source', 'Enquiry Date', 'Assign To', 'Enquiry Status'];

                const csvContent = [
                    headers.join(','),
                    [
                        sampleEnquiry.id,
                        `${sampleEnquiry.firstName} ${sampleEnquiry.middleName} ${sampleEnquiry.lastName}`.replace(/\s+/g, ' ').trim(),
                        sampleEnquiry.mobile,
                        sampleEnquiry.course,
                        sampleEnquiry.source,
                        sampleEnquiry.date,
                        sampleEnquiry.assignTo,
                        sampleEnquiry.status
                    ].join(',')
                ].join('\n');

                const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
                const link = document.createElement('a');
                link.href = URL.createObjectURL(blob);
                link.download = 'enquiries_export.csv';
                link.click();

                Swal.fire({
                    title: 'Success!',
                    text: 'CSV file exported successfully!',
                    icon: 'success',
                    confirmButtonColor: '#667eea'
                });
            }

            function openImportModal() {
                const modal = new bootstrap.Modal(document.getElementById('importModal'));
                modal.show();
            }

            function handleCSVFile(file) {
                if (!file) return;

                const reader = new FileReader();
                reader.onload = function(e) {
                    const text = e.target.result;
                    parseCSV(text);
                };
                reader.readAsText(file);
            }

            function parseCSV(text) {
                const lines = text.split('\n').filter(line => line.trim());
                const headers = lines[0].split(',').map(h => h.trim().replace(/"/g, ''));

                const importType = document.querySelector('input[name="importType"]:checked').value;

                importedData = [];
                const previewData = [];

                for (let i = 1; i < lines.length; i++) {
                    const values = lines[i].match(/(".*?"|[^,]+)(?=\s*,|\s*$)/g) || [];
                    const row = values.map(v => v.trim().replace(/^"|"$/g, ''));

                    if (row.length > 0) {
                        let record;

                        if (importType === 'old') {
                            // Old Format: Enquiry No., Student Name, Mobile No., Course, Enquiry Source, Enquiry Date, Assign To, Enquiry Status
                            const nameParts = (row[1] || '').split(' ');
                            const firstName = nameParts[0] || '';
                            const lastName = nameParts.length > 1 ? nameParts[nameParts.length - 1] : '';
                            const middleName = nameParts.length > 2 ? nameParts.slice(1, -1).join(' ') : '';

                            record = {
                                enquiryNo: row[0],
                                firstName: firstName,
                                middleName: middleName,
                                lastName: lastName,
                                mobilePrimary: row[2],
                                mobileSecondary: '',
                                emailPrimary: '',
                                currentAddress: '',
                                permanentAddress: '',
                                college: '',
                                enquiryDate: row[5],
                                followupDate: '',
                                note: '',
                                course: row[3],
                                leadSource: row[4],
                                assignTo: row[6],
                                status: row[7]
                            };
                        } else {
                            // New Format: Enquiry No., First Name, Middle Name, Last Name, Mobile Primary, Mobile Secondary, Email Primary, Current Address, Permanent Address, College, Enquiry Date, Followup Date, Note, Course, Lead Source
                            record = {
                                enquiryNo: row[0],
                                firstName: row[1],
                                middleName: row[2],
                                lastName: row[3],
                                mobilePrimary: row[4],
                                mobileSecondary: row[5],
                                emailPrimary: row[6],
                                currentAddress: row[7],
                                permanentAddress: row[8],
                                college: row[9],
                                enquiryDate: row[10],
                                followupDate: row[11],
                                note: row[12],
                                course: row[13],
                                leadSource: row[14],
                                assignTo: '',
                                status: 'New'
                            };
                        }

                        importedData.push(record);
                        if (previewData.length < 5) {
                            previewData.push(record);
                        }
                    }
                }

                displayPreview(headers, previewData);
                const recordCount = document.getElementById('recordCount');
                if (recordCount) {
                    recordCount.textContent = importedData.length;
                }
                const importBtn = document.getElementById('importBtn');
                if (importBtn) {
                    importBtn.disabled = false;
                }
            }

            document.addEventListener('show.bs.modal', function () {
                document.querySelectorAll('.action-menu').forEach(m => m.classList.remove('show'));
            });

            /* extra safety: close menus when opening modals via your functions */
            function hideAllActionMenus() {
                document.querySelectorAll('.action-menu').forEach(m => m.classList.remove('show'));
            }

            function displayPreview(headers, data) {
                const thead = document.getElementById('previewTableHead');
                const tbody = document.getElementById('previewTableBody');

                if (!thead || !tbody) return;

                thead.innerHTML = '';
                tbody.innerHTML = '';

                const headerRow = document.createElement('tr');
                const displayHeaders = ['Enquiry No.', 'First Name', 'Middle Name', 'Last Name', 'Mobile', 'Course'];
                displayHeaders.forEach(h => {
                    const th = document.createElement('th');
                    th.textContent = h;
                    headerRow.appendChild(th);
                });
                thead.appendChild(headerRow);

                data.forEach(row => {
                    const tr = document.createElement('tr');
                    [
                        row.enquiryNo,
                        row.firstName,
                        row.middleName,
                        row.lastName,
                        row.mobilePrimary,
                        row.course
                    ].forEach(value => {
                        const td = document.createElement('td');
                        td.textContent = value || '-';
                        tr.appendChild(td);
                    });
                    tbody.appendChild(tr);
                });

                const importPreview = document.getElementById('importPreview');
                if (importPreview) {
                    importPreview.style.display = 'block';
                }
            }

            function importCSV() {
                if (importedData.length === 0) {
                    Swal.fire('Error!', 'No data to import.', 'error');
                    return;
                }

                Swal.fire({
                    title: 'Importing...',
                    text: 'Please wait while we import your data.',
                    allowOutsideClick: false,
                    didOpen: () => {
                        Swal.showLoading();

                        setTimeout(() => {
                            Swal.close();
                            const modalEl = document.getElementById('importModal');
                            const modal = bootstrap.Modal.getInstance(modalEl);
                            if (modal) {
                                modal.hide();
                            }

                            Swal.fire({
                                title: 'Success!',
                                text: `${importedData.length} records imported successfully!`,
                                icon: 'success',
                                confirmButtonColor: '#667eea'
                            });

                            // Reset
                            const importPreview = document.getElementById('importPreview');
                            if (importPreview) {
                                importPreview.style.display = 'none';
                            }
                            const csvFileInput = document.getElementById('csvFileInput');
                            if (csvFileInput) {
                                csvFileInput.value = '';
                            }
                            const importBtn = document.getElementById('importBtn');
                            if (importBtn) {
                                importBtn.disabled = true;
                            }
                            importedData = [];
                        }, 2000);
                    }
                });
            }

        })();
