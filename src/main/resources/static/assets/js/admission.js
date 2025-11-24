
    (function() {
        'use strict';

        let currentTab = 1;
        const totalTabs = 6;
        let admissionData = {};
        let importedAdmissions = [];

        // Initialize on page load
        document.addEventListener('DOMContentLoaded', function() {
            initializeEventListeners();
            loadAdmissions();
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

            document.querySelectorAll('.modal').forEach(modal => {
                modal.addEventListener('hidden.bs.modal', function () {
                    document.body.style.overflow = 'auto';
                });
            });

            // Proper tab switching in admission modal
            document.querySelectorAll('#admissionTabs .nav-link').forEach((tab, index) => {
                tab.addEventListener('shown.bs.tab', function() {
                    currentTab = index + 1;
                    updateNavigationButtons();
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

            // Drag and drop
            const importArea = document.getElementById('admImportArea');
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

            // Camera
            document.getElementById('btnCapturePhoto')?.addEventListener('click', capturePhoto);

            // Photo upload
            document.getElementById('admPhotoUpload')?.addEventListener('change', function(e) {
                handlePhotoUpload(e.target.files[0]);
            });

            // Generate installments
            document.getElementById('btnGenerateInstallments')?.addEventListener('click', generateInstallments);

            // Search
            document.getElementById('searchInput')?.addEventListener('input', debounce(searchAdmissions, 500));
        }

        // Load Admissions
        function loadAdmissions() {
            const tbody = document.querySelector('#admissionsTable tbody');

            // Sample data - replace with actual API call
            const sampleData = [
                {
                    regNo: 'ADM001',
                    name: 'John Doe',
                    mobile: '9876543210',
                    course: 'JAVA CORE',
                    admissionDate: '2025-01-15'
                },
                {
                    regNo: 'ADM002',
                    name: 'Jane Smith',
                    mobile: '9876543211',
                    course: 'PYTHON',
                    admissionDate: '2025-01-16'
                }
            ];

            if (sampleData.length > 0) {
                tbody.innerHTML = sampleData.map(adm => `
                    <tr>
                        <td><strong>${adm.regNo}</strong></td>
                        <td>${adm.name}</td>
                        <td>${adm.mobile}</td>
                        <td><span class="badge bg-primary">${adm.course}</span></td>
                        <td>${adm.admissionDate}</td>
                        <td>
                            <div class="action-dropdown">
                                <button class="btn btn-sm btn-light action-menu-trigger">
                                    <i class="bi bi-three-dots-vertical"></i>
                                </button>
                                <div class="action-menu">
                                    <button class="action-menu-item" onclick="updateAdmission('${adm.regNo}')">
                                        <i class="bi bi-pencil-square"></i><span>Update</span>
                                    </button>
                                    <button class="action-menu-item" onclick="openFeeInstallments('${adm.regNo}')">
                                        <i class="bi bi-cash-stack"></i><span>Fee Installments</span>
                                    </button>
                                    <button class="action-menu-item" onclick="viewAdmission('${adm.regNo}')">
                                        <i class="bi bi-eye"></i><span>View Details</span>
                                    </button>
                                    <button class="action-menu-item" onclick="viewBatchDetails('${adm.regNo}')">
                                        <i class="bi bi-people"></i><span>Transfer Admission</span>
                                    </button>
                                    <button class="action-menu-item" onclick="printAdmission('${adm.regNo}')">
                                        <i class="bi bi-printer"></i><span>Print Form</span>
                                    </button>
                                    <button class="action-menu-item" onclick="deleteAdmission('${adm.regNo}')">
                                        <i class="bi bi-trash"></i><span>Remove</span>
                                    </button>
                                </div>
                            </div>
                        </td>
                    </tr>
                `).join('');

                attachTableEventListeners(tbody);
            }
        }

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

            document.addEventListener('click', () => {
                document.querySelectorAll('.action-menu').forEach(menu => {
                    menu.classList.remove('show');
                });
            });
        }

        // Modal Functions
        function openNewAdmissionModal() {
            currentTab = 1;
            clearForms();
            updateNavigationButtons();
            updateProgress(16.66);
            const modal = new bootstrap.Modal(document.getElementById('admissionModal'));
            modal.show();
            initializeCamera();
        }

        function clearForms() {
            ['personalInfoForm', 'otherDetailsForm', 'courseDetailsForm', 'batchDetailsForm', 'installmentsForm', 'imageUploadForm'].forEach(id => {
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

        // Save Admission
        function saveAdmission() {
            admissionData = collectAdmissionData();

            Swal.fire({
                title: 'Success!',
                text: 'Admission saved successfully!',
                icon: 'success',
                confirmButtonColor: '#667eea'
            }).then(() => {
                closeModal('admissionModal');
                loadAdmissions();

                // Ask for print
                Swal.fire({
                    title: 'Print Admission Form?',
                    text: 'Do you want to print the admission form?',
                    icon: 'question',
                    showCancelButton: true,
                    confirmButtonText: 'Yes, Print',
                    cancelButtonText: 'No',
                    confirmButtonColor: '#667eea'
                }).then((result) => {
                    if (result.isConfirmed) {
                        window.print();
                    }
                });
            });
        }

        function collectAdmissionData() {
            return {
                firstName: getValue('admFirstName'),
                middleName: getValue('admMiddleName'),
                lastName: getValue('admLastName'),
                college: getValue('admCollege'),
                qualification: getValue('admQualification'),
                aadhaar: getValue('admAadhaar'),
                dob: getValue('admDob'),
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
                document: getValue('admDocument'),
                admissionDate: getValue('admAdmissionDate'),
                leadSource: getValue('admLeadSource'),
                rollNo: getValue('admRollNo'),
                notes: getValue('admNotes')
            };
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
                const values = lines[i].match(/(".*?"|[^,]+)(?=\s*,|\s*$)/g) || [];
                const row = values.map(v => v.trim().replace(/^"|"$/g, ''));

                if (row.length > 0) {
                    let record;

                    if (importType === 'old') {
                        const nameParts = (row[1] || '').split(' ');
                        record = {
                            regNo: row[0],
                            firstName: nameParts[0] || '',
                            lastName: nameParts[nameParts.length - 1] || '',
                            mobile: row[2],
                            course: row[3],
                            admissionDate: row[4]
                        };
                    } else {
                        record = {
                            firstName: row[0],
                            middleName: row[1],
                            lastName: row[2],
                            mobilePrimary: row[3],
                            mobileSecondary: row[4],
                            emailPrimary: row[5],
                            currentAddress: row[6],
                            college: row[7],
                            feeAmount: row[8],
                            paidAmount: row[9],
                            dueDate: row[10],
                            course: row[11],
                            leadSource: row[12]
                        };
                    }

                    importedAdmissions.push(record);
                    if (previewData.length < 5) previewData.push(record);
                }
            }

            displayPreview(previewData);
            document.getElementById('admRecordCount').textContent = importedAdmissions.length;
            document.getElementById('btnImportAdmData').disabled = false;
        }

        function displayPreview(data) {
            const thead = document.getElementById('admPreviewTableHead');
            const tbody = document.getElementById('admPreviewTableBody');

            thead.innerHTML = '<tr><th>First Name</th><th>Last Name</th><th>Mobile</th><th>Course</th></tr>';
            tbody.innerHTML = data.map(row => `
                <tr>
                    <td>${row.firstName || '-'}</td>
                    <td>${row.lastName || '-'}</td>
                    <td>${row.mobilePrimary || row.mobile || '-'}</td>
                    <td>${row.course || '-'}</td>
                </tr>
            `).join('');

            document.getElementById('admImportPreview').style.display = 'block';
        }

        function importAdmissions() {
            Swal.fire({
                title: 'Importing...',
                text: 'Please wait while we import the data',
                allowOutsideClick: false,
                didOpen: () => Swal.showLoading()
            });

            setTimeout(() => {
                Swal.close();
                closeModal('importAdmissionsModal');
                showSuccess(`Successfully imported ${importedAdmissions.length} admissions!`);
                loadAdmissions();
                resetImport();
            }, 2000);
        }

        function exportAdmissions() {
            showSuccess('Exporting admissions to CSV...');
        }

        // Camera Functions
        function initializeCamera() {
            const video = document.getElementById('admVideo');
            if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
                navigator.mediaDevices.getUserMedia({ video: true })
                    .then(stream => {
                        video.srcObject = stream;
                    })
                    .catch(err => {
                        console.error('Camera error:', err);
                    });
            }
        }

        function capturePhoto() {
            const video = document.getElementById('admVideo');
            const canvas = document.getElementById('admCanvas');
            const context = canvas.getContext('2d');
            context.drawImage(video, 0, 0, 300, 300);
            showSuccess('Photo captured successfully!');
        }

        function handlePhotoUpload(file) {
            if (!file) return;
            const reader = new FileReader();
            reader.onload = (e) => {
                const canvas = document.getElementById('admCanvas');
                const context = canvas.getContext('2d');
                const img = new Image();
                img.onload = () => {
                    context.drawImage(img, 0, 0, 300, 300);
                    showSuccess('Photo uploaded successfully!');
                };
                img.src = e.target.result;
            };
            reader.readAsDataURL(file);
        }

        // Installments
        function generateInstallments() {
            const noOfInstallments = parseInt(getValue('instNoOfInstallments'));
            const days = parseInt(getValue('instDays'));
            const startDate = new Date(getValue('instStartDate'));
            const totalAmount = parseFloat(getValue('instTotalAmount') || 0);

            if (!noOfInstallments || !days || !startDate || !totalAmount) {
                showError('Please fill all required fields');
                return;
            }

            const amountPerInstallment = (totalAmount / noOfInstallments).toFixed(2);
            const tbody = document.getElementById('installmentsBody');
            let html = '';

            for (let i = 0; i < noOfInstallments; i++) {
                const installmentDate = new Date(startDate);
                installmentDate.setDate(installmentDate.getDate() + (i * days));
                const dateStr = installmentDate.toISOString().split('T')[0];

                html += `
                    <tr>
                        <td>${dateStr}</td>
                        <td>₹${amountPerInstallment}</td>
                        <td><span class="badge bg-warning">Pending</span></td>
                        <td>
                            <button class="btn btn-sm btn-danger" onclick="removeInstallment(this)">
                                <i class="bi bi-trash"></i>
                            </button>
                        </td>
                    </tr>
                `;
            }

            tbody.innerHTML = html;
            showSuccess('Installments generated successfully!');
        }

        // Global functions for action menu
        window.updateAdmission = function(regNo) {
            showSuccess('Update admission: ' + regNo);
            openNewAdmissionModal();
        };

        window.viewAdmission = function(regNo) {
            const modal = new bootstrap.Modal(document.getElementById('viewAdmissionModal'));
            modal.show();
        };

       // Update the viewBatchDetails function to open Transfer Admission modal
       window.viewBatchDetails = function(regNo) {
           // Populate transfer modal with student data
           document.getElementById('transferStudentName').textContent = 'Student ' + regNo;

           // Sample data - replace with actual API call
           document.getElementById('transferFirstName').value = 'NISHANT';
           document.getElementById('transferMiddleName').value = 'SANTOSH';
           document.getElementById('transferLastName').value = 'PAWAR';
           document.getElementById('transferCollege').value = 'ABC College';
           document.getElementById('transferQualification').value = 'B.Tech';
           document.getElementById('transferDob').value = '2000-05-15';
           document.getElementById('transferGender').value = 'Male';
           document.getElementById('transferBloodGroup').value = 'O+';
           document.getElementById('transferMobilePrimary').value = '9876543210';
           document.getElementById('transferMobileSecondary').value = '9876543211';
           document.getElementById('transferEmailPrimary').value = 'student@example.com';
           document.getElementById('transferCurrentAddress').value = 'Nashik, Maharashtra';
           document.getElementById('transferDocument').value = 'Aadhaar Card';
           document.getElementById('transferLeadSource').value = 'Walk-in';
           document.getElementById('transferTotalFees').value = '50000';
           document.getElementById('transferReceivableFees').value = '45000';

           // Set today's date for transfer date
           const today = new Date().toISOString().split('T')[0];
           document.getElementById('transferDate').value = today;

           const modal = new bootstrap.Modal(document.getElementById('transferAdmissionModal'));
           modal.show();
       };

       // Transfer tab progress update
       document.querySelectorAll('#transferTabs .nav-link').forEach(tab => {
           tab.addEventListener('click', function() {
               const progress = this.getAttribute('data-transfer-progress');
               updateTransferProgress(progress);
           });
       });

       function updateTransferProgress(width) {
           const bar = document.getElementById('transferProgressBar');
           if (bar) bar.style.width = width + '%';
       }

       // Transfer Admission submit
       document.getElementById('btnTransferAdmission')?.addEventListener('click', function() {
           const course = document.getElementById('transferCourse').value;
           const batch = document.getElementById('transferBatch').value;
           const academicYear = document.getElementById('transferAcademicYear').value;
           const transferDate = document.getElementById('transferDate').value;

           if (!course || !batch || !academicYear || !transferDate) {
               showError('Please fill all required fields');
               return;
           }

           Swal.fire({
               title: 'Confirm Transfer?',
               text: 'Are you sure you want to transfer this admission?',
               icon: 'question',
               showCancelButton: true,
               confirmButtonText: 'Yes, Transfer',
               cancelButtonText: 'Cancel',
               confirmButtonColor: '#667eea'
           }).then((result) => {
               if (result.isConfirmed) {
                   // Perform transfer API call here
                   showSuccess('Admission transferred successfully!');
                   closeModal('transferAdmissionModal');
                   loadAdmissions();
               }
           });
       });

        window.printAdmission = function(regNo) {
            showSuccess('Printing admission form for: ' + regNo);
            window.print();
        };

        window.deleteAdmission = function(regNo) {
            Swal.fire({
                title: 'Confirmation',
                text: 'Do you want to delete this admission?',
                icon: 'warning',
                showCancelButton: true,
                confirmButtonText: 'Yes, Delete',
                cancelButtonText: 'Cancel',
                confirmButtonColor: '#ef4444'
            }).then((result) => {
                if (result.isConfirmed) {
                    showSuccess('Admission deleted: ' + regNo);
                    loadAdmissions();
                }
            });
        };

        window.removeInstallment = function(btn) {
            btn.closest('tr').remove();
            showSuccess('Installment removed');
        };

        // Search
        function searchAdmissions(e) {
            const searchTerm = e.target.value.toLowerCase();
            console.log('Searching:', searchTerm);
            // Implement search logic
        }

        // Helper Functions
        function getValue(id) {
            const el = document.getElementById(id);
            return el ? el.value : '';
        }

        function setValue(id, value) {
            const el = document.getElementById(id);
            if (el) el.value = value || '';
        }

        function closeModal(modalId) {
            const modalEl = document.getElementById(modalId);
            const modal = bootstrap.Modal.getInstance(modalEl);
            if (modal) modal.hide();
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


// Fee Installments Functions
window.openFeeInstallments = function(regNo) {
    document.getElementById('feeInstStudentName').textContent = 'Student ' + regNo;
    document.getElementById('feeInstTotalAmount').value = '50000';
    document.getElementById('feeInstTotalInstAmount').value = '0';
    const modal = new bootstrap.Modal(document.getElementById('feeInstallmentsModal'));
    modal.show();
};

window.generateFeeInstallments = function() {
    const noOfInstallments = parseInt(document.getElementById('feeInstNoOfInstallments').value);
    const days = parseInt(document.getElementById('feeInstDays').value);
    const startDate = new Date(document.getElementById('feeInstStartDate').value);
    const totalAmount = parseFloat(document.getElementById('feeInstTotalAmount').value || 0);

    if (!noOfInstallments || !days || !startDate || !totalAmount) {
        showError('Please fill all required fields');
        return;
    }

    const amountPerInstallment = (totalAmount / noOfInstallments).toFixed(2);
    const tbody = document.getElementById('feeInstallmentsBody');
    let html = '';
    let totalInstAmount = 0;

    for (let i = 0; i < noOfInstallments; i++) {
        const installmentDate = new Date(startDate);
        installmentDate.setDate(installmentDate.getDate() + (i * days));
        const dateStr = installmentDate.toISOString().split('T')[0];
        totalInstAmount += parseFloat(amountPerInstallment);

        html += `
            <tr>
                <td>${dateStr}</td>
                <td>₹${amountPerInstallment}</td>
                <td><span class="badge bg-warning">Pending</span></td>
                <td>
                    <button class="btn btn-sm btn-danger" onclick="this.closest('tr').remove(); updateInstTotal();">
                        <i class="bi bi-trash"></i>
                    </button>
                </td>
            </tr>
        `;
    }

    tbody.innerHTML = html;
    document.getElementById('feeInstTotalInstAmount').value = totalInstAmount.toFixed(2);
    showSuccess('Installments generated successfully!');
};

    window.updateInstTotal = function() {
        let total = 0;
        document.querySelectorAll('#feeInstallmentsBody tr').forEach(row => {
            const amount = row.cells[1]?.textContent.replace('₹', '');
            if (amount) total += parseFloat(amount);
        });
        document.getElementById('feeInstTotalInstAmount').value = total.toFixed(2);
    };

    window.saveFeeInstallments = function() {
        showSuccess('Fee installments saved successfully!');
        closeModal('feeInstallmentsModal');
    };

    // Print Admission Form
    window.printAdmission = function(regNo) {
        // Populate print modal with data
        document.getElementById('printRegNo').textContent = regNo;
        document.getElementById('printAdmDate').textContent = '22/11/2025';
        document.getElementById('printStudentName').textContent = 'SANIKA PRADIP GAIKWAD';
        document.getElementById('printBirthDate').textContent = '15/05/2000';
        document.getElementById('printGender').textContent = 'Female';
        document.getElementById('printAadhaar').textContent = '1234 5678 9012';
        document.getElementById('printBloodGroup').textContent = 'O+';
        document.getElementById('printCategory').textContent = 'General';
        document.getElementById('printCast').textContent = 'Hindu';
        document.getElementById('printQualification').textContent = 'B.Tech';
        document.getElementById('printCollege').textContent = 'XYZ College';
        document.getElementById('printMobile1').textContent = '9021211851';
        document.getElementById('printMobile2').textContent = '-';
        document.getElementById('printEmail1').textContent = 'gaikwadsanika152007@gmail.com';
        document.getElementById('printEmail2').textContent = '-';
        document.getElementById('printCurrentAddr').textContent = 'Nashik, Maharashtra';
        document.getElementById('printPermanentAddr').textContent = 'Nashik, Maharashtra';
        document.getElementById('printCourses').textContent = 'C & C++ PROGRAMMING';
        document.getElementById('printDocument').textContent = 'Aadhaar Card';
        document.getElementById('printNotes').textContent = 'NA';

        const modal = new bootstrap.Modal(document.getElementById('printAdmissionModal'));
        modal.show();
    };

    window.printToPDF = function() {
        window.print();
    };

    // Add CSS for print
    const printStyles = `
        @media print {
            body * {
                visibility: hidden;
            }
            #printAdmissionContent, #printAdmissionContent * {
                visibility: visible;
            }
            #printAdmissionContent {
                position: absolute;
                left: 0;
                top: 0;
                width: 100%;
            }
            .modal-header, .modal-footer {
                display: none !important;
            }
        }
    `;

    const styleSheet = document.createElement("style");
    styleSheet.textContent = printStyles;
    document.head.appendChild(styleSheet);

    // Pre-fill admission form from enquiry data
    document.addEventListener('DOMContentLoaded', function() {
        // Check if coming from enquiry
        const admissionEnquiry = sessionStorage.getItem('admissionEnquiry');
        const fromEnquiry = sessionStorage.getItem('admissionFromEnquiry');

        if (admissionEnquiry && fromEnquiry === 'true') {
            try {
                const enquiry = JSON.parse(admissionEnquiry);

                // **CRITICAL FIX: Open modal first, then prefill**
                openNewAdmissionModal();

                // Wait for modal to open
                setTimeout(() => {
                    prefillAdmissionForm(enquiry);

                    // Show success message
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

                // Clean up session storage
                sessionStorage.removeItem('admissionEnquiry');
                sessionStorage.removeItem('admissionFromEnquiry');

            } catch (error) {
                console.error('Error pre-filling form:', error);
            }
        }
    });

    // Function to pre-fill admission form
    function prefillAdmissionForm(enquiry) {
        console.log('Pre-filling admission form with enquiry:', enquiry);

        // Tab 1: Personal Information
        setValue('admFirstName', enquiry.firstName);
        setValue('admMiddleName', enquiry.middleName);
        setValue('admLastName', enquiry.lastName);
        setValue('admCollege', enquiry.college);
        setValue('admQualification', enquiry.qualification);
        setValue('admAadhaar', enquiry.aadhaar);
        setValue('admDob', enquiry.birthDate);
        setValue('admGender', enquiry.gender);

        // Tab 2: Other Details (Communication)
        setValue('admMobilePrimary', enquiry.mobile);
        setValue('admMobileSecondary', enquiry.secondaryMobile);
        setValue('admEmailPrimary', enquiry.email);
        setValue('admCurrentAddress', enquiry.currentAddress);
        setValue('admPermanentAddress', enquiry.permanentAddress);
        setValue('admPinCodeCurrent', enquiry.pinCurrent);
        setValue('admPinCodePermanent', enquiry.pinPermanent);
        setValue('admLeadSource', enquiry.source);
        setValue('admNotes', enquiry.note);

        // Set admission date to today
        const today = new Date().toISOString().split('T')[0];
        setValue('admAdmissionDate', today);

        // Tab 3: Course Details - **CRITICAL FIX: Handle multiple courses**
        setValue('admPackage', enquiry.packageName);

        const courseSelect = document.getElementById('admCourse');
        if (courseSelect && enquiry.coursesList && enquiry.coursesList.length > 0) {
            // Select all matching courses
            Array.from(courseSelect.options).forEach(option => {
                if (enquiry.coursesList.includes(option.value)) {
                    option.selected = true;
                }
            });

            // Display selected courses
            displaySelectedCourses(enquiry.coursesList);
        }

        // Highlight pre-filled fields
        highlightPrefilledFields();
    }

    // Display selected courses in UI
    function displaySelectedCourses(courses) {
        const tbody = document.getElementById('selectedCoursesBody');
        if (!tbody || !courses || courses.length === 0) return;

        tbody.innerHTML = courses.map((course, index) => `
            <tr>
                <td>${course}</td>
                <td><input type="number" class="form-control form-control-sm" value="0" id="courseAmount${index}"></td>
                <td>
                    <button class="btn btn-sm btn-danger" onclick="removeCourse(this)">
                        <i class="bi bi-trash"></i>
                    </button>
                </td>
            </tr>
        `).join('');
    }

    // Open new admission modal - **CRITICAL FIX: Check enquiry first**
    async function openNewAdmissionModal() {
        // Check if opened from enquiry (prefill scenario)
        const fromEnquiry = sessionStorage.getItem('admissionFromEnquiry');

        if (!fromEnquiry) {
            // Standalone new admission - prompt for mobile first
            const { value: mobile } = await Swal.fire({
                title: 'Enter Student Mobile Number',
                html: `
                    <input type="tel" id="swalMobile" class="form-control"
                           placeholder="10-digit mobile number" maxlength="10"
                           pattern="[6-9][0-9]{9}">
                    <small class="text-muted d-block mt-2">
                        <i class="bi bi-info-circle me-1"></i>
                        An enquiry must exist for this mobile number
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
                    showLoading('Checking enquiry...');

                    // Check if enquiry exists
                    const canCreateResponse = await fetch(`/api/admissions/can-create/${mobile}`);
                    const canCreateData = await canCreateResponse.json();

                    if (!canCreateData.canCreate) {
                        Swal.close();
                        await Swal.fire({
                            title: 'Cannot Create Admission',
                            html: `
                                <div class="alert alert-danger">
                                    <strong>No enquiry found for mobile: ${mobile}</strong>
                                    <p class="mb-0 mt-2">
                                        Please create an enquiry first before proceeding with admission.
                                    </p>
                                </div>
                            `,
                            icon: 'error',
                            confirmButtonText: 'OK',
                            confirmButtonColor: '#ef4444'
                        });
                        return;
                    }

                    // Fetch enquiry data
                    const enquiryResponse = await fetch(`/api/admissions/enquiry-data/${mobile}`);
                    const enquiry = await enquiryResponse.json();

                    Swal.close();

                    // Store for prefill
                    sessionStorage.setItem('admissionEnquiry', JSON.stringify(enquiry));
                    sessionStorage.setItem('admissionFromEnquiry', 'true');

                    // Reload page to trigger prefill
                    window.location.reload();

                } catch (error) {
                    Swal.close();
                    console.error('Error:', error);
                    await Swal.fire({
                        title: 'Error',
                        text: 'Failed to check enquiry. Please try again.',
                        icon: 'error',
                        confirmButtonColor: '#ef4444'
                    });
                }
            }
            return;
        }

        currentTab = 1;
        clearForms();
        updateNavigationButtons();
        updateProgress(16.66);
        const modal = new bootstrap.Modal(document.getElementById('admissionModal'));
        modal.show();
    }


    // Highlight pre-filled fields temporarily
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

                // Remove highlight after 3 seconds
                setTimeout(() => {
                    field.style.backgroundColor = '';
                }, 3000);
            }
        });
    }

    // Helper function to set field values
    function setValue(id, value) {
        const el = document.getElementById(id);
        if (el && value) {
            el.value = value;
            // Trigger change event for any listeners
            el.dispatchEvent(new Event('change', { bubbles: true }));
        }
    }

    // Helper function to get field values
    function getValue(id) {
        const el = document.getElementById(id);
        return el ? el.value : '';
    }

    // ============ VALIDATION BEFORE ADMISSION SAVE ============

    // Override existing saveAdmission function to add enquiry validation
    const originalSaveAdmission = window.saveAdmission || function() {};

    window.saveAdmission = async function() {
        const admissionData = collectAdmissionData();

        // CRITICAL: Validate enquiry exists before creating admission
        try {
            showLoading('Validating enquiry...');

            const canCreateResponse = await fetch(
                `/api/admissions/can-create/${admissionData.mobilePrimary}`
            );
            const canCreateData = await canCreateResponse.json();

            Swal.close();

            if (!canCreateData.canCreate) {
                await Swal.fire({
                    title: 'Validation Failed',
                    html: `
                        <div class="alert alert-danger">
                            <strong>Cannot create admission!</strong>
                            <p class="mb-0 mt-2">No enquiry found for mobile: <strong>${admissionData.mobilePrimary}</strong></p>
                        </div>
                        <p class="text-muted mt-3">
                            Please create an enquiry first before proceeding with admission.
                        </p>
                    `,
                    icon: 'error',
                    confirmButtonText: 'OK',
                    confirmButtonColor: '#ef4444'
                });
                return; // Stop admission creation
            }

            // Enquiry exists, proceed with normal admission creation
            await originalSaveAdmission();

        } catch (error) {
            Swal.close();
            console.error('Validation error:', error);
            showError('Failed to validate enquiry. Please try again.');
        }
    };

    // Helper functions
    function showLoading(message) {
        Swal.fire({
            title: message,
            allowOutsideClick: false,
            didOpen: () => Swal.showLoading()
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

    // ============ SETUP INSTRUCTIONS ============
    /*

    2. Ensure the admission form HTML has these field IDs:
       - admFirstName, admMiddleName, admLastName
       - admMobilePrimary, admMobileSecondary
       - admEmailPrimary
       - admCollege, admQualification
       - admCurrentAddress, admPermanentAddress
       - admCourse (dropdown)
       - admNotes

    3. In your enquiry.js action menu, update the admission button:
       ```html
       <button class="action-menu-item" data-action="admission"
               data-id="${enq.id}" data-mobile="${enq.mobile}">
           <i class="bi bi-plus"></i><span>New Admission</span>
       </button>
       ```

    4. The flow will be:
       a) User clicks "New Admission" in enquiry table
       b) System validates enquiry exists & no admission exists
       c) Shows confirmation dialog with enquiry details
       d) Stores enquiry data in sessionStorage
       e) Redirects to /admission page
       f) Admission form auto-loads and pre-fills data
       g) Before saving, validates enquiry still exists

    5. Test the flow:
       - Create an enquiry
       - Click "New Admission" from the action menu
       - Verify form is pre-filled
       - Try to create admission without enquiry (should show error)
    */

})();
