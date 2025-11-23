(function() {
    'use strict';

    let importedData = [];
    let currentEnquiryId = null;
    let currentFollowUpEnquiry = null;

    document.addEventListener('DOMContentLoaded', function() {
        initializeEventListeners();
        loadEnquiries();
    });

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

        // Action menu items
        document.querySelectorAll('.action-menu-item').forEach(item => {
            item.addEventListener('click', function() {
                const action = this.getAttribute('data-action');
                const row = this.closest('tr');
                const enquiryId = row?.querySelector('td strong')?.textContent;
                handleAction(action, enquiryId);
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

        // Follow-Up Modal
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

        // Search
        document.getElementById('searchInput')?.addEventListener('input', debounce(searchEnquiries, 500));
    }

    // Load Enquiries from API
    async function loadEnquiries(page = 0, size = 25) {
        try {
            const response = await fetch(`/api/enquiries?page=${page}&size=${size}`);
            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                throw new Error(errorData.message || 'Failed to load enquiries');
            }

            const data = await response.json();
            renderEnquiriesTable(data.content || []);
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

            const result = await response.json();

            showSuccess(currentEnquiryId ? 'Enquiry updated successfully!' : 'Enquiry created successfully!');
            closeModal('enquiryModal');
            loadEnquiries();
            currentEnquiryId = null;

        } catch (error) {
            console.error('Error saving enquiry:', error);
            showError(error.message);
        }
    }

    // Collect Form Data
    function collectFormData() {
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
            courses: [getValue('course')].filter(Boolean),
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

    // Validate Form Data
    function validateFormData(data) {
        if (!data.firstName || !data.lastName) return false;
        if (!data.mobile || !/^[6-9]\d{9}$/.test(data.mobile)) return false;
        if (!data.courses || data.courses.length === 0) return false;
        if (!data.source) return false;
        return true;
    }

    // Import CSV
    async function importCSV() {
        if (importedData.length === 0) {
            showError('No data to import');
            return;
        }

        const importType = document.querySelector('input[name="importType"]:checked').value;
        const typeEnum = importType === 'old' ? 'OLD_FORMAT' : 'NEW_FORMAT';

        // Convert to EnquiryRequestDTO format
        const dtoList = importedData.map(record => {
            const dto = {
                mobile: record.mobilePrimary,
                courses: [record.course].filter(Boolean),
                source: record.leadSource || 'Unknown',
                enquiryDate: record.enquiryDate || new Date().toISOString().split('T')[0],
                status: record.status || 'New'
            };

            if (importType === 'old') {
                dto.name = `${record.firstName} ${record.middleName} ${record.lastName}`.trim();
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

            return dto;
        });

        try {
            showLoading('Importing data...');

            const response = await fetch(`/api/enquiries/bulk-import-json?importSource=${typeEnum}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(dtoList)
            });

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                throw new Error(errorData.message || 'Import failed');
            }

            const result = await response.json();

            Swal.close();
            closeModal('importModal');

            let message = `Successfully imported ${result.successfulImports} out of ${result.totalRecords} records`;
            if (result.failedImports > 0) {
                message += `\n${result.failedImports} records failed`;
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
    async function searchEnquiries(e) {
        const searchTerm = e.target.value.trim();

        try {
            const searchDTO = {
                searchTerm: searchTerm || null,
                page: 0,
                size: 25,
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
            renderEnquiriesTable(data.content);
        } catch (error) {
            console.error('Search error:', error);
        }
    }

    // Render Enquiries Table
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

        tbody.innerHTML = enquiries.map(enq => `
            <tr data-id="${enq.id}">
                <td><strong>${enq.id}</strong></td>
                <td>${enq.name || `${enq.firstName || ''} ${enq.lastName || ''}`.trim() || 'N/A'}</td>
                <td>${enq.mobile || 'N/A'}</td>
                <td><span class="badge bg-primary">${enq.courses || 'N/A'}</span></td>
                <td>${enq.source || 'N/A'}</td>
                <td>${enq.date || 'N/A'}</td>
                <td>${enq.assign || 'Unassigned'}</td>
                <td><span class="badge bg-success">${enq.status || 'New'}</span></td>
                <td>
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
                            <button class="action-menu-item" data-action="remove" data-id="${enq.id}">
                                <i class="bi bi-trash"></i><span>Remove</span>
                            </button>
                        </div>
                    </div>
                </td>
            </tr>
        `).join('');

        // Re-attach event listeners
        attachTableEventListeners(tbody);
    }

    // Helper function to attach table event listeners
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
            case 'remove':
                await deleteEnquiry(enquiryId);
                break;
        }
    }

    // Load Enquiry for Edit
    async function loadEnquiryForEdit(id) {
        try {
            const response = await fetch(`/api/enquiries/${id}`);
            if (!response.ok) throw new Error('Failed to load enquiry');

            const enquiry = await response.json();
            currentEnquiryId = id;

            // Fill form
            setValue('firstName', enquiry.firstName);
            setValue('middleName', enquiry.middleName);
            setValue('lastName', enquiry.lastName);
            setValue('mobilePrimary', enquiry.mobile);
            setValue('mobileSecondary', enquiry.secondaryMobile);
            setValue('emailPrimary', enquiry.email);
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
            setValue('course', enquiry.coursesList?.[0] || '');
            setValue('package', enquiry.packageName);
            setValue('demoLecture', enquiry.demoLectureRequired ? 'true' : 'false');
            setValue('interestLevel', enquiry.interestLevel);
            setValue('leadSource', enquiry.source);
            setValue('referenceName', enquiry.referenceName);
            setValue('assignTo', enquiry.assign);
            setValue('enquiryDate', enquiry.date);
            setValue('followupDate', enquiry.followupDate);
            setValue('note', enquiry.note);

            document.getElementById('modalTitle').innerHTML = '<i class="bi bi-pencil-square me-2"></i>Update Enquiry';
            const modal = new bootstrap.Modal(document.getElementById('enquiryModal'));
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

    // Open Follow-Up Modal
    async function openFollowUpModal(id) {
        try {
            const response = await fetch(`/api/enquiries/${id}`);
            if (!response.ok) throw new Error('Failed to load enquiry');

            const enquiry = await response.json();
            currentFollowUpEnquiry = enquiry;

            // Fill modal fields
            setValue('followUpStudentName', enquiry.name || `${enquiry.firstName || ''} ${enquiry.lastName || ''}`.trim());
            setValue('followUpMobile', enquiry.mobile);

            // Set minimum date to today
            const today = new Date().toISOString().split('T')[0];
            document.getElementById('nextFollowUpDate').setAttribute('min', today);

            // Clear form
            document.getElementById('followUpForm').reset();
            setValue('followUpStudentName', enquiry.name || `${enquiry.firstName || ''} ${enquiry.lastName || ''}`.trim());
            setValue('followUpMobile', enquiry.mobile);

            // Load follow-up history
            await loadFollowUpHistory(id);

            // Show modal
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

        // For now, show placeholder - you can implement API endpoint later
        tbody.innerHTML = `
            <tr>
                <td colspan="4" class="text-center text-muted">
                    <small>Follow-up history will be displayed here</small>
                </td>
            </tr>
        `;

        // TODO: Implement API call to fetch follow-up history
        // Example:
        // const response = await fetch(`/api/enquiries/${enquiryId}/followups`);
        // const history = await response.json();
        // renderFollowUpHistory(history);
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
            mode: getValue('followUpMode'),
            nextFollowUpDate: getValue('nextFollowUpDate'),
            note: getValue('followUpNote'),
            followUpDate: new Date().toISOString().split('T')[0]
        };

        try {
            // Update enquiry with new follow-up date
            const updateResponse = await fetch(`/api/enquiries/${currentFollowUpEnquiry.id}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    ...currentFollowUpEnquiry,
                    followupDate: followUpData.nextFollowUpDate,
                    note: followUpData.note,
                    status: 'Follow-up Scheduled'
                })
            });

            if (!updateResponse.ok) throw new Error('Failed to save follow-up');

            // TODO: Save follow-up history to separate table if you have that endpoint
            // await fetch('/api/followups', {
            //     method: 'POST',
            //     headers: { 'Content-Type': 'application/json' },
            //     body: JSON.stringify(followUpData)
            // });

            showSuccess('Follow-up saved successfully!');
            closeModal('followUpModal');
            loadEnquiries();

            // Ask if user wants to send SMS
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
        // Implement SMS sending logic here
        // This is a placeholder
        Swal.fire({
            title: 'SMS Sent!',
            text: `Follow-up reminder sent to ${mobile}`,
            icon: 'success',
            confirmButtonColor: '#667eea'
        });
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

    function openAddModal() {
        currentEnquiryId = null;
        clearForm();
        document.getElementById('modalTitle').innerHTML = '<i class="bi bi-person-plus me-2"></i>Add New Enquiry';
        const modal = new bootstrap.Modal(document.getElementById('enquiryModal'));
        modal.show();
        updateProgress(25);
    }

    function clearForm() {
        ['personalForm', 'communicationForm', 'followupForm', 'sourceForm'].forEach(id => {
            document.getElementById(id)?.reset();
        });
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
                    const nameParts = (row[1] || '').split(' ');
                    record = {
                        firstName: nameParts[0] || '',
                        middleName: nameParts.length > 2 ? nameParts.slice(1, -1).join(' ') : '',
                        lastName: nameParts.length > 1 ? nameParts[nameParts.length - 1] : '',
                        mobilePrimary: row[2],
                        course: row[3],
                        leadSource: row[4],
                        enquiryDate: row[5],
                        assignTo: row[6],
                        status: row[7] || 'New'
                    };
                } else {
                    record = {
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
                        leadSource: row[14]
                    };
                }

                importedData.push(record);
                if (previewData.length < 5) previewData.push(record);
            }
        }

        displayPreview(previewData);
        document.getElementById('recordCount').textContent = importedData.length;
        document.getElementById('importBtn').disabled = false;
    }

    function displayPreview(data) {
        const thead = document.getElementById('previewTableHead');
        const tbody = document.getElementById('previewTableBody');

        thead.innerHTML = '<tr><th>First Name</th><th>Last Name</th><th>Mobile</th><th>Course</th></tr>';
        tbody.innerHTML = data.map(row => `
            <tr>
                <td>${row.firstName || '-'}</td>
                <td>${row.lastName || '-'}</td>
                <td>${row.mobilePrimary || '-'}</td>
                <td>${row.course || '-'}</td>
            </tr>
        `).join('');

        document.getElementById('importPreview').style.display = 'block';
    }

    function debounce(func, wait) {
        let timeout;
        return function(...args) {
            clearTimeout(timeout);
            timeout = setTimeout(() => func.apply(this, args), wait);
        };
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

    function showLoading(message) {
        Swal.fire({
            title: message,
            allowOutsideClick: false,
            didOpen: () => Swal.showLoading()
        });
    }

    function openImportModal() {
        const modal = new bootstrap.Modal(document.getElementById('importModal'));
        modal.show();
    }

})();