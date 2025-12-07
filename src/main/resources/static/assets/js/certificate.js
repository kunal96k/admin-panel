// Global variables
let currentPage = 0;
let pageSize = 25;
let totalPages = 0;
let totalElements = 0;
let selectedCertificateId = null;
let importedCertData = [];
let allCourses = [];
// CSRF Token handling
let csrfToken = null;
let csrfHeader = null;

// Initialize on page load
document.addEventListener('DOMContentLoaded', function() {
    initializeEventListeners();
    updateStats();
    loadCourses().then(() => {
       initializeCourseSearch();
    });
    loadCertificates();
    updateStatsForCurrentView();
    initializeCsrfToken();
});

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

async function updateStatsForCurrentView() {
    const course = document.getElementById('courseFilter')?.value || '';
    const status = document.getElementById('statusFilter')?.value || '';
    const search = document.getElementById('searchInput')?.value || '';

    try {
        const params = new URLSearchParams({
            ...(course && { course }),
            ...(status && { status }),
            ...(search && { search })
        });

        // Refresh CSRF token before request
        initializeCsrfToken();

        const response = await fetch(`/api/certificates/stats-filtered?${params}`, {
            headers: {
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            }
        });

        if (!response.ok) throw new Error('Failed to load filtered stats');

        const stats = await response.json();

        const totalEl = document.getElementById('totalCount');
        const issuedEl = document.getElementById('issuedCount');
        const pendingEl = document.getElementById('pendingCount');

        if (totalEl) totalEl.textContent = stats.total || 0;
        if (issuedEl) issuedEl.textContent = stats.issued || 0;
        if (pendingEl) pendingEl.textContent = stats.pending || 0;
    } catch (error) {
        console.error('Error loading filtered stats:', error);
    }
}

async function autoGenerateCertificates() {
    const result = await Swal.fire({
        title: 'Auto-Generate Certificates',
        html: `
            <div class="text-start">
                <p>This will automatically create certificate entries for all students who have:</p>
                <ul>
                    <li>Cleared their fees (Status = "Clear")</li>
                    <li>Fees Due = 0</li>
                    <li>Total Fees = Total Paid</li>
                </ul>
                <p class="text-warning"><i class="bi bi-exclamation-triangle me-2"></i>Duplicate entries (same student + course) will be skipped automatically.</p>
                <p class="fw-bold">Continue?</p>
            </div>
        `,
        icon: 'question',
        showCancelButton: true,
        confirmButtonText: '<i class="bi bi-check-circle me-2"></i>Yes, Generate',
        cancelButtonText: 'Cancel',
        confirmButtonColor: '#10b981',
        cancelButtonColor: '#6c757d',
        width: '550px'
    });

    if (!result.isConfirmed) return;

    try {
        Swal.fire({
            title: 'Processing...',
            html: 'Checking fees and generating certificates',
            allowOutsideClick: false,
            didOpen: () => Swal.showLoading()
        });

        // Refresh CSRF token before request
        initializeCsrfToken();

        const response = await fetch('/api/certificates/auto-generate', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            }
        });

        const data = await response.json();

        Swal.close();

        if (data.success) {
            Swal.fire({
                icon: 'success',
                title: 'Success!',
                html: `
                    <p><strong>${data.certificatesCreated}</strong> certificate(s) created successfully!</p>
                    <p class="text-muted mt-2">Students can now have their certificates issued.</p>
                `,
                confirmButtonColor: '#10b981'
            }).then(() => {
                loadCertificates();
                updateStats();
            });
        } else {
            Swal.fire({
                icon: 'warning',
                title: 'No Certificates Created',
                text: data.message || 'No eligible students found with cleared fees',
                confirmButtonColor: '#667eea'
            });
        }

    } catch (error) {
        Swal.close();
        console.error('Auto-generate error:', error);
        Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'Failed to auto-generate certificates',
            confirmButtonColor: '#ef4444'
        });
    }
}

function initializeEventListeners() {

    document.getElementById('btnAutoGenerate')?.addEventListener('click', autoGenerateCertificates);

    // Filter changes
    document.getElementById('courseFilter')?.addEventListener('change', () => {
        currentPage = 0;
        loadCertificates();
        updateStatsForCurrentView();
    });

    document.getElementById('statusFilter')?.addEventListener('change', () => {
        currentPage = 0;
        loadCertificates();
        updateStatsForCurrentView();
    });

    document.getElementById('pageSizeSelect')?.addEventListener('change', function() {
        pageSize = parseInt(this.value);
        currentPage = 0;
        loadCertificates();
        updateStatsForCurrentView();
    });

    // Search with debounce
    let searchTimeout;
    document.getElementById('searchInput')?.addEventListener('input', function() {
        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(() => {
            currentPage = 0;
            loadCertificates();
            updateStatsForCurrentView();
        }, 500);
    });

    // Save certificate
    document.getElementById('btnSaveCertificate')?.addEventListener('click', saveCertificate);

    // Set default issue date to today
    const issueDateEl = document.getElementById('issueDate');
    if (issueDateEl) {
        issueDateEl.valueAsDate = new Date();
    }

    // Action menu toggle
    document.addEventListener('click', (e) => {
        if (!e.target.closest('.action-dropdown')) {
            document.querySelectorAll('.action-menu').forEach(menu => {
                menu.classList.remove('show');
            });
        }
    });

    // Import/Export CSV
    const btnImportCSV = document.getElementById('btnImportCSV');
    const btnExportCSV = document.getElementById('btnExportCSV');
    const btnBrowseCertFile = document.getElementById('btnBrowseCertFile');
    const csvCertFileInput = document.getElementById('csvCertFileInput');
    const importCertBtn = document.getElementById('importCertBtn');

    if (btnImportCSV) {
        btnImportCSV.addEventListener('click', openImportCertModal);
    }

    if (btnExportCSV) {
        btnExportCSV.addEventListener('click', exportCertificatesCSV);
    }

    if (btnBrowseCertFile) {
        btnBrowseCertFile.addEventListener('click', () => {
            document.getElementById('csvCertFileInput')?.click();
        });
    }

    if (csvCertFileInput) {
        csvCertFileInput.addEventListener('change', function(e) {
            handleCertCSVFile(e.target.files[0]);
        });
    }

    if (importCertBtn) {
        importCertBtn.addEventListener('click', importCertificatesCSV);
    }

    // Drag and drop
    const importCertArea = document.getElementById('importCertArea');
    if (importCertArea) {
        importCertArea.addEventListener('dragover', (e) => {
            e.preventDefault();
            importCertArea.classList.add('dragover');
        });

        importCertArea.addEventListener('dragleave', () => {
            importCertArea.classList.remove('dragover');
        });

        importCertArea.addEventListener('drop', (e) => {
            e.preventDefault();
            importCertArea.classList.remove('dragover');
            const file = e.dataTransfer.files[0];
            if (file?.name.endsWith('.csv')) {
                handleCertCSVFile(file);
            } else {
                Swal.fire({
                    title: 'Error',
                    text: 'Please upload a valid CSV file.',
                    icon: 'error',
                    confirmButtonColor: '#667eea'
                });
            }
        });
    }
}

function initializeCourseSearch() {
    const searchInput = document.getElementById('courseSearchInput');
    const courseFilter = document.getElementById('courseFilter');

    if (!searchInput || !courseFilter) return;

    // Store original options
    const allOptions = Array.from(courseFilter.options);

    searchInput.addEventListener('input', function() {
        const searchTerm = this.value.toLowerCase().trim();

        // Clear current options except "All Courses"
        courseFilter.innerHTML = '<option value="">-- All Courses --</option>';

        // Filter and add matching options
        allOptions.slice(1).forEach(option => {
            if (option.text.toLowerCase().includes(searchTerm)) {
                courseFilter.appendChild(option.cloneNode(true));
            }
        });
    });
}

// Load courses from backend
async function loadCourses() {
    try {
        // Refresh CSRF token before request
        initializeCsrfToken();

        const response = await fetch('/api/courses?page=0&size=1000', {
            headers: {
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            }
        });

        if (!response.ok) throw new Error('Failed to load courses');

        const data = await response.json();
        allCourses = data.courses || [];

        const courseFilter = document.getElementById('courseFilter');
        if (courseFilter) {
            courseFilter.innerHTML = '<option value="">-- All Courses --</option>';
            allCourses.forEach(course => {
                const option = document.createElement('option');
                option.value = course.courseName;
                option.textContent = course.courseName;
                courseFilter.appendChild(option);
            });
        }
    } catch (error) {
        console.error('Error loading courses:', error);
    }
}

// Load certificates from server
async function loadCertificates() {
    const tbody = document.querySelector('#certificateTable tbody');
    if (!tbody) return;

    // Show loading
    tbody.innerHTML = '<tr><td colspan="9" class="text-center py-4">Loading...</td></tr>';

    try {
        const course = document.getElementById('courseFilter')?.value || '';
        const status = document.getElementById('statusFilter')?.value || '';
        const search = document.getElementById('searchInput')?.value || '';

        const params = new URLSearchParams({
            page: currentPage,
            size: pageSize,
            ...(course && { course }),
            ...(status && { status }),
            ...(search && { search })
        });

         // Refresh CSRF token before request
        initializeCsrfToken();

        const response = await fetch(`/api/certificates?${params}`, {
            headers: {
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            }
        });

        if (!response.ok) throw new Error('Failed to load certificates');

        const data = await response.json();
        const certificates = data.certificates || [];

        if (certificates.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="9" class="text-center py-5">
                        <i class="bi bi-inbox" style="font-size: 3rem; color: #94a3b8;"></i>
                        <p class="mt-3 mb-0 text-muted">No certificates found</p>
                    </td>
                </tr>
            `;
            updatePaginationInfo(0, 0);
            return;
        }

        tbody.innerHTML = certificates.map(cert => `
            <tr>
                <td><strong>${cert.registrationNo}</strong></td>
                <td>${cert.certificateNo || '<span class="text-muted">Not Issued</span>'}</td>
                <td>${cert.studentName}</td>
                <td><span class="badge bg-primary">${cert.courseName}</span></td>
                <td>${cert.batch || '-'}</td>
                <td>${cert.grade || '<span class="text-muted">-</span>'}</td>
                <td>${cert.issueDate ? new Date(cert.issueDate).toLocaleDateString() : '<span class="text-muted">-</span>'}</td>
                <td>
                    <span class="badge ${cert.status === 'Issued' ? 'bg-success' : 'bg-warning'}">
                        ${cert.status}
                    </span>
                </td>
                <td>
                    <div class="action-dropdown">
                        <button class="btn btn-light action-menu-trigger" style="padding: 0.25rem 0.5rem;">
                            <i class="bi bi-three-dots-vertical"></i>
                        </button>
                        <div class="action-menu">
                            <button class="action-menu-item" onclick="issueCertificate(${cert.id})">
                                <i class="bi bi-pencil-square"></i><span>${cert.status === 'Issued' ? 'Edit Certificate' : 'Issue Certificate'}</span>
                            </button>
                            ${cert.status === 'Issued' ? `
                            <button class="action-menu-item" onclick="viewCertificate(${cert.id})">
                                <i class="bi bi-eye"></i><span>View Certificate</span>
                            </button>
                           <button class="action-menu-item" onclick="promptAndSendEmail(${cert.id}, '${cert.studentEmail || ''}')">
                               <i class="bi bi-envelope"></i><span>Send Email</span>
                           </button>
                            ` : ''}
                            <button class="action-menu-item" onclick="printCertificate(${cert.id})">
                                <i class="bi bi-printer"></i><span>Print Certificate</span>
                            </button>
                            <button class="action-menu-item text-danger" onclick="deleteCertificate(${cert.id})">
                                <i class="bi bi-trash"></i><span>Delete</span>
                            </button>
                        </div>
                    </div>
                </td>
            </tr>
        `).join('');

        // Attach menu triggers
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

        // Update pagination
        totalPages = data.totalPages;
        totalElements = data.totalItems;
        updatePaginationInfo(certificates.length, totalElements);
        renderPagination();

    } catch (error) {
        console.error('Error loading certificates:', error);
        tbody.innerHTML = `
            <tr>
                <td colspan="9" class="text-center py-4 text-danger">
                    <i class="bi bi-exclamation-triangle"></i> Error loading certificates
                </td>
            </tr>
        `;
    }
}

// Update statistics
async function updateStats() {
    try {
        // Refresh CSRF token before request
        initializeCsrfToken();

        const response = await fetch('/api/certificates/stats', {
            headers: {
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            }
        });

        if (!response.ok) throw new Error('Failed to load stats');

        const stats = await response.json();
        const totalEl = document.getElementById('totalCount');
        const issuedEl = document.getElementById('issuedCount');
        const pendingEl = document.getElementById('pendingCount');

        if (totalEl) totalEl.textContent = stats.total || 0;
        if (issuedEl) issuedEl.textContent = stats.issued || 0;
        if (pendingEl) pendingEl.textContent = stats.pending || 0;
    } catch (error) {
        console.error('Error loading stats:', error);
    }
}

// Issue certificate
async function issueCertificate(id) {
    document.querySelectorAll('.action-menu').forEach(menu => {
        menu.classList.remove('show');
    });

    try {
        // Refresh CSRF token before request
        initializeCsrfToken();

        const response = await fetch(`/api/certificates/${id}`, {
            headers: {
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            }
        });

        if (!response.ok) throw new Error('Failed to load certificate');

        const certificate = await response.json();
        selectedCertificateId = id;

        document.getElementById('studentName').value = certificate.studentName;
        document.getElementById('courseName').value = certificate.courseName;
        document.getElementById('grade').value = certificate.grade || '';
        document.getElementById('certificateNo').value = certificate.certificateNo || 'AUTO-GENERATED';

        if (certificate.issueDate) {
            document.getElementById('issueDate').value = certificate.issueDate;
        } else {
            document.getElementById('issueDate').valueAsDate = new Date();
        }

        document.getElementById('courseFromDate').value = certificate.courseFromDate || '';
        document.getElementById('courseToDate').value = certificate.courseToDate || '';
        document.getElementById('certificateNotes').value = certificate.notes || '';

        const modal = new bootstrap.Modal(document.getElementById('certificateModal'));
        modal.show();

    } catch (error) {
        console.error('Error loading certificate:', error);
        Swal.fire({
            title: 'Error',
            text: 'Failed to load certificate details',
            icon: 'error',
            confirmButtonColor: '#667eea'
        });
    }
}

// Delete certificate
async function deleteCertificate(id) {
    document.querySelectorAll('.action-menu').forEach(menu => {
        menu.classList.remove('show');
    });

    const result = await Swal.fire({
        title: 'Are you sure?',
        text: "You won't be able to revert this!",
        icon: 'warning',
        showCancelButton: true,
        confirmButtonColor: '#dc3545',
        cancelButtonColor: '#6c757d',
        confirmButtonText: 'Yes, delete it!'
    });

    if (result.isConfirmed) {
        try {
            // Refresh CSRF token before request
            initializeCsrfToken();

            const response = await fetch(`/api/certificates/${id}`, {
                method: 'DELETE',
                headers: {
                    ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
                }
            });

            if (!response.ok) throw new Error('Failed to delete certificate');

            Swal.fire({
                title: 'Deleted!',
                text: 'Certificate has been deleted.',
                icon: 'success',
                confirmButtonColor: '#667eea'
            });

            loadCertificates();
            updateStats();

        } catch (error) {
            console.error('Error deleting certificate:', error);
            Swal.fire({
                title: 'Error',
                text: 'Failed to delete certificate',
                icon: 'error',
                confirmButtonColor: '#667eea'
            });
        }
    }
}

// Print certificate
function printCertificate(id) {
    document.querySelectorAll('.action-menu').forEach(menu => {
        menu.classList.remove('show');
    });

    //  Open print view in new window
    window.open(`/certificates/print/${id}`, '_blank', 'width=1400,height=900');
}

// View certificate
async function viewCertificate(id) {
    document.querySelectorAll('.action-menu').forEach(menu => {
        menu.classList.remove('show');
    });

    try {
        const response = await fetch(`/api/certificates/${id}`);
        if (!response.ok) throw new Error('Failed to load certificate');

        const certificate = await response.json();

        //  Check if certificate is issued
        if (certificate.status !== 'Issued') {
            Swal.fire({
                title: 'Certificate Not Issued',
                text: 'This certificate has not been issued yet. Please issue the certificate first.',
                icon: 'warning',
                confirmButtonColor: '#667eea'
            });
            return;
        }

        window.open(`/certificates/view/${id}`, '_blank', 'width=1400,height=900');

    } catch (error) {
        console.error('Error viewing certificate:', error);
        Swal.fire({
            title: 'Error',
            text: 'Failed to load certificate',
            icon: 'error',
            confirmButtonColor: '#667eea'
        });
    }
}

async function promptAndSendEmail(certificateId, studentEmail) {
    document.querySelectorAll('.action-menu').forEach(menu => {
        menu.classList.remove('show');
    });

    try {
        // Refresh CSRF token before request
        initializeCsrfToken();

        const certResponse = await fetch(`/api/certificates/${certificateId}`, {
            headers: {
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            }
        });

        if (!certResponse.ok) throw new Error('Failed to load certificate');

        const certificate = await certResponse.json();

        let finalEmail = studentEmail || '';
        let studentName = certificate.studentName || '';
        const actualRegNo = certificate.registrationNo;

        if (!finalEmail && actualRegNo) {
            try {
                console.log('Fetching admission data for:', actualRegNo);

                const admResponse = await fetch(`/api/admissions/by-regno/${actualRegNo}`, {
                    method: 'GET',
                    headers: {
                        'Accept': 'application/json',
                        'Content-Type': 'application/json',
                        ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
                    }
                });

                if (admResponse.ok) {
                    const admission = await admResponse.json();
                    console.log('Admission data received:', admission);

                    // Use emailPrimary first, fallback to emailSecondary
                    finalEmail = admission.emailPrimary || admission.emailSecondary || '';

                    // Update student name if available in admission
                    if (admission.studentName) {
                        studentName = admission.studentName;
                    }

                    console.log('Final email:', finalEmail);
                } else {
                    console.warn('Admission not found for registration number:', actualRegNo);
                }
            } catch (error) {
                console.error('Error fetching admission email:', error);
            }
        }

        const { value: formValues } = await Swal.fire({
            title: '<i class="bi bi-envelope-paper me-2"></i>Send Certificate via Email',
            html: `
                <div class="text-start">
                    <div class="alert alert-info mb-3">
                        <i class="bi bi-info-circle me-2"></i>
                        <strong>Certificate will be sent as a high-quality image attachment</strong>
                    </div>

                    <div class="mb-3">
                        <label class="form-label fw-bold">
                            Certificate Number
                            <i class="bi bi-lock-fill text-muted ms-1" style="font-size: 0.8rem;"></i>
                        </label>
                        <input
                            type="text"
                            id="emailCertNo"
                            class="form-control bg-light"
                            value="${certificate.certificateNo || 'Not Issued'}"
                            disabled
                            style="cursor: not-allowed;"
                        >
                    </div>

                    <div class="mb-3">
                        <label class="form-label fw-bold">
                            Student Name
                            <i class="bi bi-lock-fill text-muted ms-1" style="font-size: 0.8rem;"></i>
                        </label>
                        <input
                            type="text"
                            id="emailStudentName"
                            class="form-control bg-light"
                            value="${studentName}"
                        >
                    </div>

                    <div class="mb-3">
                        <label class="form-label fw-bold">
                            Email Address <span class="text-danger">*</span>
                        </label>
                        <input
                            type="email"
                            id="emailRecipient"
                            class="form-control"
                            value="${finalEmail}"
                            placeholder="Enter recipient email address"
                            required
                        >
                        <small class="text-muted">
                            <i class="bi bi-info-circle me-1"></i>
                            ${finalEmail ? 'Email auto-filled from admission records' : 'Please enter email address manually'}
                        </small>
                    </div>

                    <div class="mb-3">
                        <label class="form-label fw-bold">Additional Message (Optional)</label>
                        <textarea
                            id="emailMessage"
                            class="form-control"
                            rows="3"
                            placeholder="Add a personal message to include in the email..."
                        ></textarea>
                    </div>

                    <div class="alert alert-warning mb-0">
                        <i class="bi bi-exclamation-triangle me-2"></i>
                        <small><strong>Note:</strong> Make sure the email address is correct. The certificate will be sent immediately.</small>
                    </div>
                </div>
            `,
            width: '600px',
            showCancelButton: true,
            confirmButtonText: '<i class="bi bi-send-fill me-2"></i>Send Email',
            cancelButtonText: '<i class="bi bi-x-circle me-2"></i>Cancel',
            confirmButtonColor: '#10b981',
            cancelButtonColor: '#6c757d',
            focusConfirm: false,
            preConfirm: () => {
                const email = document.getElementById('emailRecipient').value.trim();
                const message = document.getElementById('emailMessage').value.trim();

                if (!email) {
                    Swal.showValidationMessage('Please enter an email address');
                    return false;
                }

                const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
                if (!emailRegex.test(email)) {
                    Swal.showValidationMessage('Please enter a valid email address');
                    return false;
                }

                return { email, message };
            }
        });

        if (formValues) {
            await sendCertificateEmail(certificateId, formValues.email, formValues.message);
        }

    } catch (error) {
        console.error('Error preparing email modal:', error);
        Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'Failed to load certificate details',
            confirmButtonColor: '#ef4444'
        });
    }
}

/**
 * Send certificate email with canvas image
 */
async function sendCertificateEmail(certificateId, email, additionalMessage) {
    try {
        // Show loading with better UI
        Swal.fire({
            title: 'Sending Email...',
            html: `
                <div class="text-center">
                    <div class="spinner-border text-primary mb-3" role="status" style="width: 3rem; height: 3rem;">
                        <span class="visually-hidden">Loading...</span>
                    </div>
                    <p class="mb-2">Generating certificate image...</p>
                    <p class="text-muted small">This may take a few moments</p>
                </div>
            `,
            allowOutsideClick: false,
            showConfirmButton: false
        });

        // Fetch certificate view to get canvas data
        const viewUrl = `/certificates/view/${certificateId}`;
        const response = await fetch(viewUrl);
        const html = await response.text();

        // Create temporary iframe to render certificate
        const iframe = document.createElement('iframe');
        iframe.style.position = 'absolute';
        iframe.style.left = '-9999px';
        iframe.style.width = '1754px';
        iframe.style.height = '1240px';
        document.body.appendChild(iframe);

        // Load HTML into iframe
        iframe.contentDocument.open();
        iframe.contentDocument.write(html);
        iframe.contentDocument.close();

        // Wait for canvas to render
        await new Promise(resolve => setTimeout(resolve, 3000));

        // Get canvas data
        const canvas = iframe.contentDocument.getElementById('canvas');
        if (!canvas) {
            throw new Error('Certificate canvas not found');
        }

        const imageData = canvas.toDataURL('image/jpeg', 1.0);

        // Remove iframe
        document.body.removeChild(iframe);

        // Update loading message
        Swal.update({
            html: `
                <div class="text-center">
                    <div class="spinner-border text-success mb-3" role="status" style="width: 3rem; height: 3rem;">
                        <span class="visually-hidden">Loading...</span>
                    </div>
                    <p class="mb-2">Sending email to ${email}...</p>
                    <p class="text-muted small">Almost done!</p>
                </div>
            `
        });

         // Refresh CSRF token before request
        initializeCsrfToken();

        // Send to backend
        const sendResponse = await fetch(`/api/certificates/${certificateId}/send-email-with-image`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            },
            body: JSON.stringify({
                email: email,
                imageData: imageData,
                additionalMessage: additionalMessage || null
            })
        });

        const result = await sendResponse.json();

        if (!sendResponse.ok) {
            throw new Error(result.error || 'Failed to send email');
        }

        Swal.fire({
            icon: 'success',
            title: 'Email Sent Successfully!',
            html: `
                <div class="text-center">
                    <i class="bi bi-check-circle-fill text-success" style="font-size: 4rem;"></i>
                    <p class="mt-3 mb-2">Certificate has been sent to:</p>
                    <p class="fw-bold text-primary">${email}</p>
                    <small class="text-muted">The recipient should receive it within a few minutes</small>
                </div>
            `,
            confirmButtonColor: '#10b981',
            timer: 5000
        });

    } catch (error) {
        console.error('Error sending email:', error);
        Swal.fire({
            icon: 'error',
            title: 'Failed to Send Email',
            html: `
                <div class="alert alert-danger text-start">
                    <i class="bi bi-exclamation-triangle me-2"></i>
                    <strong>Error:</strong> ${error.message || 'Could not send certificate email'}
                </div>
                <p class="text-muted mt-3">
                    <small>Please check the email address and try again. If the problem persists, contact support.</small>
                </p>
            `,
            confirmButtonColor: '#ef4444'
        });
    }
}

// CSV Import/Export Functions
function openImportCertModal() {
    const modalEl = document.getElementById('importCertificateModal');
    if (!modalEl) {
        console.error('Import modal not found');
        return;
    }

    resetCertImport();
    const modal = new bootstrap.Modal(modalEl);
    modal.show();
}

function handleCertCSVFile(file) {
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (e) => parseCertCSV(e.target.result);
    reader.readAsText(file);
}

// : Parse CSV with correct format
//  Parse CSV with correct 7-column format
function parseCertCSV(text) {
    const lines = text.split('\n').filter(line => line.trim());
    importedCertData = [];
    const previewData = [];

    for (let i = 1; i < lines.length; i++) {
        const values = lines[i].match(/(".*?"|[^,\t]+)(?=\s*[,\t]|\s*$)/g) || [];
        const row = values.map(v => v.trim().replace(/^"|"$/g, ''));

        if (row.length >= 3) { // Minimum: RegNo, CertNo, StudentName
            //  CSV has 7 columns: Reg No, Cert No, Student Name, Batch(Course), Grade, Issue Date, Status
            const record = {
                registrationNo: row[0] || null,        // Column 0: Reg No
                certificateNo: row[1] || null,         // Column 1: Certificate No
                studentName: row[2] || null,           // Column 2: Student Name
                courseName: row[3] || null,            // Column 3: Batch (actually course name)
                batch: 'NA',                            //  Always NA - not in CSV
                grade: row[4] || null,                 // Column 4: Grade
                issueDate: row[5] || null,             // Column 5: Issue Date
                status: row[6] || 'Not Issued'         // Column 6: Status
            };

            importedCertData.push(record);
            if (previewData.length < 5) previewData.push(record);
        }
    }

    displayCertPreview(previewData);

    const countEl = document.getElementById('certRecordCount');
    if (countEl) {
        countEl.textContent = importedCertData.length;
    }

    const importBtn = document.getElementById('importCertBtn');
    if (importBtn) {
        importBtn.disabled = importedCertData.length === 0;
    }
}

// : Display preview with correct columns
function displayCertPreview(data) {
    const thead = document.getElementById('previewCertTableHead');
    const tbody = document.getElementById('previewCertTableBody');

    if (!thead || !tbody) return;

    thead.innerHTML = '<tr><th>Reg No</th><th>Cert No</th><th>Student</th><th>Course</th><th>Status</th></tr>';
    tbody.innerHTML = data.map(row => `
        <tr>
            <td>${row.registrationNo || '-'}</td>
            <td>${row.certificateNo || 'Not Issued'}</td>
            <td>${row.studentName || '-'}</td>
            <td>${row.courseName || '-'}</td>
            <td><span class="badge ${row.status === 'Issued' ? 'bg-success' : 'bg-warning'}">${row.status || 'Not Issued'}</span></td>
        </tr>
    `).join('');

    const previewEl = document.getElementById('importCertPreview');
    if (previewEl) {
        previewEl.style.display = 'block';
    }
}

async function importCertificatesCSV() {
    if (importedCertData.length === 0) {
        Swal.fire({
            title: 'Error',
            text: 'No data to import',
            icon: 'error',
            confirmButtonColor: '#667eea'
        });
        return;
    }

    try {
        Swal.fire({
            title: 'Importing...',
            text: `Importing ${importedCertData.length} certificates`,
            allowOutsideClick: false,
            didOpen: () => Swal.showLoading()
        });

        const formData = new FormData();
        const csvContent = convertToCSV(importedCertData);
        const blob = new Blob([csvContent], { type: 'text/csv' });
        formData.append('file', blob, 'certificates.csv');

        // Refresh CSRF token before request
        initializeCsrfToken();

        const response = await fetch('/api/certificates/import', {
            method: 'POST',
            body: formData,
            headers: {
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            }
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'Import failed');
        }

        const result = await response.json();

       Swal.fire({
           title: 'Success!',
           html: `
               <p>Successfully imported: <strong>${result.success ?? 0}</strong></p>
               <p>Skipped (duplicates): <strong>${result.skipped ?? 0}</strong></p>
           `,
           icon: 'success',
           confirmButtonColor: '#667eea'
       });

        const modalEl = document.getElementById('importCertificateModal');
        const modalInstance = bootstrap.Modal.getInstance(modalEl);
        if (modalInstance) {
            modalInstance.hide();
        }

        loadCertificates();
        updateStats();
        resetCertImport();

    } catch (error) {
        console.error('Import error:', error);
        Swal.fire({
            title: 'Error',
            text: error.message || 'Failed to import certificates',
            icon: 'error',
            confirmButtonColor: '#667eea'
        });
    }
}

// : Convert to correct CSV format
function convertToCSV(data) {
    const header = 'Reg No,Certificate No,Student Name,Course,Batch,Grade,Issue Date,Status\n';
    const rows = data.map(row =>
        `${row.registrationNo || ''},${row.certificateNo || ''},${row.studentName || ''},${row.courseName || ''},${row.batch || ''},${row.grade || ''},${row.issueDate || ''},${row.status || 'Not Issued'}`
    ).join('\n');
    return header + rows;
}

async function exportCertificatesCSV() {
    try {
        // Refresh CSRF token before request
        initializeCsrfToken();

        const checkResponse = await fetch('/api/certificates/stats', {
            headers: {
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            }
        });

        const stats = await checkResponse.json();

        if (!stats || stats.total === 0) {
            Swal.fire({
                icon: 'info',
                title: 'No Data Found',
                text: 'The certificate table is empty. There is no data to export.',
                confirmButtonColor: '#667eea'
            });
            return;
        }

        Swal.fire({
            title: 'Exporting...',
            text: 'Preparing CSV file',
            allowOutsideClick: false,
            didOpen: () => Swal.showLoading()
        });

        const response = await fetch('/api/certificates/export/csv', {
            headers: {
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            }
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'Export failed');
        }

        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `certificates_${new Date().toISOString().split('T')[0]}.csv`;
        link.click();
        window.URL.revokeObjectURL(url);

        Swal.fire({
            icon: 'success',
            title: 'Export Successful!',
            text: `${stats.total} certificate(s) exported successfully!`,
            timer: 2000,
            showConfirmButton: false
        });
    } catch (error) {
        console.error('Export error:', error);
        Swal.fire({
            icon: 'error',
            title: 'Export Failed',
            text: error.message || 'Failed to export CSV',
            confirmButtonColor: '#ef4444'
        });
    }
}

function resetCertImport() {
    const previewEl = document.getElementById('importCertPreview');
    const fileInput = document.getElementById('csvCertFileInput');
    const importBtn = document.getElementById('importCertBtn');

    if (previewEl) previewEl.style.display = 'none';
    if (fileInput) fileInput.value = '';
    if (importBtn) importBtn.disabled = true;

    importedCertData = [];
}

// Pagination functions
function updatePaginationInfo(showing, total) {
    const startEl = document.getElementById('entriesStart');
    const endEl = document.getElementById('entriesEnd');
    const totalEl = document.getElementById('totalEntries');

    //  Handle empty data case
    if (total === 0 || showing === 0) {
        if (startEl) startEl.textContent = '0';
        if (endEl) endEl.textContent = '0';
        if (totalEl) totalEl.textContent = '0';
        return;
    }

    const start = (currentPage * pageSize) + 1;
    const end = (currentPage * pageSize) + showing;

    if (startEl) startEl.textContent = start;
    if (endEl) endEl.textContent = end;
    if (totalEl) totalEl.textContent = total;
}

function renderPagination() {
    const paginationControls = document.getElementById('paginationControls');
    if (!paginationControls) return;

    //  FIX: Hide pagination if no data
    if (totalPages === 0 || totalElements === 0) {
        paginationControls.innerHTML = '';
        return;
    }

    let html = '';

    // Previous button
    html += `
        <li class="page-item ${currentPage === 0 ? 'disabled' : ''}">
            <a class="page-link" href="#" onclick="changePage(${currentPage - 1}); return false;">Previous</a>
        </li>
    `;

    //  Smart pagination with ellipsis
    const maxVisiblePages = 7;
    let startPage = 0;
    let endPage = totalPages - 1;

    if (totalPages > maxVisiblePages) {
        const halfVisible = Math.floor(maxVisiblePages / 2);

        if (currentPage <= halfVisible) {
            // Near start
            endPage = maxVisiblePages - 2;
        } else if (currentPage >= totalPages - halfVisible - 1) {
            // Near end
            startPage = totalPages - maxVisiblePages + 1;
        } else {
            // Middle
            startPage = currentPage - halfVisible + 1;
            endPage = currentPage + halfVisible - 1;
        }
    }

    // First page button (always show if not in range)
    if (startPage > 0) {
        html += `
            <li class="page-item ${currentPage === 0 ? 'active' : ''}">
                <a class="page-link" href="#" onclick="changePage(0); return false;">1</a>
            </li>
        `;
        if (startPage > 1) {
            html += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
        }
    }

    // Middle page numbers
    for (let i = startPage; i <= endPage; i++) {
        html += `
            <li class="page-item ${i === currentPage ? 'active' : ''}">
                <a class="page-link" href="#" onclick="changePage(${i}); return false;">${i + 1}</a>
            </li>
        `;
    }

    // Last page button (always show if not in range)
    if (endPage < totalPages - 1) {
        if (endPage < totalPages - 2) {
            html += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
        }
        html += `
            <li class="page-item ${currentPage === totalPages - 1 ? 'active' : ''}">
                <a class="page-link" href="#" onclick="changePage(${totalPages - 1}); return false;">${totalPages}</a>
            </li>
        `;
    }

    // Next button
    html += `
        <li class="page-item ${currentPage >= totalPages - 1 ? 'disabled' : ''}">
            <a class="page-link" href="#" onclick="changePage(${currentPage + 1}); return false;">Next</a>
        </li>
    `;

    paginationControls.innerHTML = html;
}

function changePage(page) {
    if (page < 0 || page >= totalPages) return;
    currentPage = page;
    loadCertificates();
}


// ==================== AUTO-GENERATE CERTIFICATES ====================

async function autoGenerateCertificates() {
    const result = await Swal.fire({
        title: '🎓 Auto-Generate Certificates',
        html: `
            <div class="text-start">
                <h6 class="text-primary mb-3"> For NEW Admissions Only</h6>
                <p class="mb-2">This will automatically create certificate entries for students with:</p>
                <ul class="mb-3">
                    <li><strong>Registration numbers starting with "REG"</strong> (e.g., REG8000, REG8001)</li>
                    <li>Cleared fees (Status = "Clear" OR Fees Due = 0 OR Total Fees = Total Paid)</li>
                    <li><strong>Separate certificate for each enrolled course</strong></li>
                </ul>

                <div class="alert alert-info mb-3">
                    <i class="bi bi-info-circle me-2"></i>
                    <strong>Note:</strong> Old admission records (7168, 7167, etc.) are managed via CSV import
                </div>

                <p class="text-warning mb-0">
                    <i class="bi bi-exclamation-triangle me-2"></i>
                    Duplicate entries (same student + course) will be automatically skipped
                </p>
            </div>
        `,
        icon: 'question',
        showCancelButton: true,
        confirmButtonText: '<i class="bi bi-magic me-2"></i>Generate Certificates',
        cancelButtonText: '<i class="bi bi-x-circle me-2"></i>Cancel',
        confirmButtonColor: '#10b981',
        cancelButtonColor: '#6c757d',
        width: '600px',
        customClass: {
            popup: 'custom-swal-popup'
        }
    });

    if (!result.isConfirmed) return;

    try {
        Swal.fire({
            title: 'Processing...',
            html: '<div class="text-center"><div class="spinner-border text-primary mb-3" role="status"></div><p>Checking cleared fees and generating certificates...</p></div>',
            allowOutsideClick: false,
            showConfirmButton: false
        });

        // Refresh CSRF token
        initializeCsrfToken();

        const response = await fetch('/api/certificates/auto-generate', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json',
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            }
        });

        //  Check content type before parsing
        const contentType = response.headers.get('content-type');

        if (!contentType || !contentType.includes('application/json')) {
            console.error('❌ Non-JSON response received:', contentType);
            const text = await response.text();
            console.error('Response body:', text.substring(0, 500));
            throw new Error('Server returned non-JSON response. Please check if you are logged in.');
        }

        const data = await response.json();

        console.log(' Auto-generate response:', data);

        Swal.close();

        if (data.success) {
            Swal.fire({
                icon: 'success',
                title: 'Success!',
                html: `
                    <div class="text-center">
                        <div class="display-4 text-success mb-3">
                            <i class="bi bi-check-circle-fill"></i>
                        </div>
                        <h5 class="mb-3">
                            <strong>${data.certificatesCreated}</strong> certificate(s) created successfully!
                        </h5>
                        <p class="text-muted">
                            Students can now have their certificates issued with certificate numbers
                        </p>
                    </div>
                `,
                confirmButtonColor: '#10b981',
                confirmButtonText: 'View Certificates'
            }).then(() => {
                loadCertificates();
                updateStats();
            });
        } else {
            Swal.fire({
                icon: 'info',
                title: 'No Certificates Created',
                html: `
                    <p class="mb-2">${data.message || 'No eligible students found with cleared fees'}</p>
                    <hr>
                    <small class="text-muted">
                        <strong>Possible reasons:</strong><br>
                        • All eligible students already have certificates<br>
                        • No students with cleared fees<br>
                        • Only old admissions found (handled via CSV import)
                    </small>
                `,
                confirmButtonColor: '#667eea'
            });
        }

    } catch (error) {
        Swal.close();
        console.error('❌ Auto-generate error:', error);
        Swal.fire({
            icon: 'error',
            title: 'Error',
            html: `
                <div class="text-start">
                    <p><strong>Failed to auto-generate certificates</strong></p>
                    <p class="text-muted small">${error.message}</p>
                    <hr>
                    <small class="text-danger">
                        If this error persists, please contact support or check browser console for details.
                    </small>
                </div>
            `,
            confirmButtonColor: '#ef4444'
        });
    }
}

// ==================== MANUAL CERTIFICATE GENERATION ====================

async function openManualCertificateModal() {
    // Fetch courses
    let courseOptions = '<option value="">-- Select Course --</option>';
    try {
        const response = await fetch('/api/courses?page=0&size=1000');
        const data = await response.json();
        data.courses.forEach(course => {
            courseOptions += `<option value="${course.courseName}">${course.courseName}</option>`;
        });
    } catch (error) {
        console.error('Error loading courses:', error);
    }

    const { value: formValues } = await Swal.fire({
        title: '🔧 Manual Certificate Generation',
        html: `
            <div class="text-start">
                <div class="alert alert-warning mb-3">
                    <i class="bi bi-exclamation-triangle me-2"></i>
                    <strong>Backup Feature:</strong> Use only when auto-generation misses a record
                </div>

                <div class="mb-3">
                    <label class="form-label">Registration Number <span class="text-danger">*</span></label>
                    <input type="text" id="manualRegNo" class="form-control" placeholder="Enter registration number">
                </div>

                <div class="mb-3">
                    <label class="form-label">Student Name <span class="text-danger">*</span></label>
                    <input type="text" id="manualStudentName" class="form-control" placeholder="Enter student name">
                </div>

                <div class="mb-3">
                    <label class="form-label">Course <span class="text-danger">*</span></label>
                    <select id="manualCourse" class="form-select">
                        ${courseOptions}
                    </select>
                </div>

                <div class="mb-3">
                    <label class="form-label">Reason for Manual Generation <span class="text-danger">*</span></label>
                    <textarea id="manualReason" class="form-control" rows="3" placeholder="E.g., Missed by auto-generation, Special case, etc."></textarea>
                </div>
            </div>
        `,
        width: '550px',
        showCancelButton: true,
        confirmButtonText: '<i class="bi bi-plus-circle me-2"></i>Create Certificate',
        cancelButtonText: 'Cancel',
        confirmButtonColor: '#10b981',
        cancelButtonColor: '#6c757d',
        preConfirm: () => {
            const regNo = document.getElementById('manualRegNo').value.trim();
            const studentName = document.getElementById('manualStudentName').value.trim();
            const courseName = document.getElementById('manualCourse').value;
            const reason = document.getElementById('manualReason').value.trim();

            if (!regNo || !studentName || !courseName || !reason) {
                Swal.showValidationMessage('Please fill in all required fields');
                return false;
            }

            return { regNo, studentName, courseName, reason };
        }
    });

    if (formValues) {
        await createManualCertificate(formValues);
    }
}

async function createManualCertificate(data) {
    try {
        Swal.fire({
            title: 'Creating Certificate...',
            allowOutsideClick: false,
            didOpen: () => Swal.showLoading()
        });

        // Refresh CSRF token before request
        initializeCsrfToken();

        const response = await fetch('/api/certificates/manual-generate', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            },
            body: JSON.stringify({
                registrationNo: data.regNo,
                studentName: data.studentName,
                courseName: data.courseName,
                reason: data.reason
            })
        });

        const result = await response.json();

        if (result.success) {
            Swal.fire({
                icon: 'success',
                title: 'Certificate Created!',
                html: `
                    <p>Certificate has been created manually for:</p>
                    <ul class="text-start">
                        <li><strong>Student:</strong> ${data.studentName}</li>
                        <li><strong>Registration:</strong> ${data.regNo}</li>
                        <li><strong>Course:</strong> ${data.courseName}</li>
                    </ul>
                    <p class="text-muted mt-2">
                        <small>This action has been logged for audit purposes</small>
                    </p>
                `,
                confirmButtonColor: '#10b981'
            }).then(() => {
                loadCertificates();
                updateStats();
            });
        } else {
            throw new Error(result.message || 'Failed to create certificate');
        }

    } catch (error) {
        Swal.fire({
            icon: 'error',
            title: 'Error',
            text: error.message || 'Failed to create manual certificate',
            confirmButtonColor: '#ef4444'
        });
    }
}

// ==================== INITIALIZE EVENT LISTENERS ====================

function initializeCertificateButtons() {
    // Auto-generate button
    document.getElementById('btnAutoGenerate')?.addEventListener('click', autoGenerateCertificates);

    // Manual generate button
    document.getElementById('btnManualGenerate')?.addEventListener('click', openManualCertificateModal);

    // View logs button
    document.getElementById('btnViewManualLogs')?.addEventListener('click', viewManualCertificateLogs);
}

// Call on page load
document.addEventListener('DOMContentLoaded', initializeCertificateButtons);


async function viewManualCertificateLogs() {
    try {
        Swal.fire({
            title: 'Loading Logs...',
            allowOutsideClick: false,
            didOpen: () => Swal.showLoading()
        });

        // Refresh CSRF token before request
        initializeCsrfToken();

        const response = await fetch('/api/certificates/manual-logs', {
            headers: {
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            }
        });

        const logs = await response.json();

        if (logs.length === 0) {
            Swal.fire({
                icon: 'info',
                title: 'No Manual Records',
                text: 'No manual certificate generation records found',
                confirmButtonColor: '#667eea'
            });
            return;
        }
        const tableRows = logs.map(log => {

                let formattedDate = 'N/A';

                if (log.createdAt) {
                    try {
                        const dateStr = log.createdAt.replace('T', ' ').split('.')[0];
                        const createdDate = new Date(dateStr);

                        if (!isNaN(createdDate.getTime())) {
                            formattedDate = createdDate.toLocaleString('en-IN', {
                                year: 'numeric',
                                month: 'short',
                                day: '2-digit',
                                hour: '2-digit',
                                minute: '2-digit',
                                hour12: true,
                                timeZone: 'Asia/Kolkata'
                            });
                        }
                    } catch (e) {
                        console.error('Date parsing error:', e, log.createdAt);
                    }
                }

            return `
                <tr>
                    <td><strong>${log.registrationNo || '-'}</strong></td>
                    <td>${log.studentName || '-'}</td>
                    <td><span class="badge bg-primary">${log.courseName || '-'}</span></td>
                    <td>
                        <div>
                            <i class="bi bi-person-fill text-primary me-1"></i>
                            <strong>${log.createdByEmployeeName || 'Unknown'}</strong>
                        </div>
                    </td>
                    <td><small class="text-muted">${log.reason || '-'}</small></td>
                    <td>
                        <small class="text-success">
                            <i class="bi bi-calendar-check me-1"></i>${formattedDate}
                        </small>
                    </td>
                </tr>
            `;
        }).join('');

        Swal.fire({
            title: '<i class="bi bi-clipboard-data me-2"></i>Manual Certificate Generation Logs',
            html: `
                <div class="table-responsive" style="max-height: 500px; overflow-y: auto;">
                    <table class="table table-sm table-hover">
                        <thead class="table-light sticky-top">
                            <tr>
                                <th>Reg No</th>
                                <th>Student Name</th>
                                <th>Course</th>
                                <th>Created By</th>
                                <th>Reason</th>
                                <th>Created At</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${tableRows}
                        </tbody>
                    </table>
                </div>
                <div class="alert alert-info mt-3 mb-0">
                    <i class="bi bi-info-circle me-2"></i>
                    <strong>Total Manual Records:</strong> ${logs.length}
                </div>
            `,
            width: '950px',
            confirmButtonText: 'Close',
            confirmButtonColor: '#667eea',
            customClass: {
                popup: 'manual-logs-popup'
            }
        });

    } catch (error) {
        console.error('Error loading manual logs:', error);
        Swal.fire({
            icon: 'error',
            title: 'Error',
            text: 'Failed to load manual certificate logs',
            confirmButtonColor: '#ef4444'
        });
    }
}

// ==================== SAVE CERTIFICATE WITH BETTER ERROR HANDLING ====================

async function saveCertificate() {
    const form = document.getElementById('certificateForm');

    if (!form.checkValidity()) {
        form.reportValidity();
        return;
    }

    const certificateData = {
        certificateNo: document.getElementById('certificateNo').value,
        grade: document.getElementById('grade').value || null,
        issueDate: document.getElementById('issueDate').value,
        courseFromDate: document.getElementById('courseFromDate').value,
        courseToDate: document.getElementById('courseToDate').value,
        notes: document.getElementById('certificateNotes').value
    };

    try {
        Swal.fire({
            title: 'Issuing Certificate...',
            html: 'Please wait while we process your request',
            allowOutsideClick: false,
            didOpen: () => Swal.showLoading()
        });

        // Refresh CSRF token before request
        initializeCsrfToken();

        const response = await fetch(`/api/certificates/${selectedCertificateId}/issue`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                ...(csrfToken && csrfHeader && { [csrfHeader]: csrfToken })
            },
            body: JSON.stringify(certificateData)
        });

        const contentType = response.headers.get('content-type');
        let errorData = null;

        if (!response.ok) {
            if (contentType && contentType.includes('application/json')) {
                errorData = await response.json();
            } else {
                const textError = await response.text();
                errorData = { error: textError || 'Unknown error occurred' };
            }

            if (errorData.error && errorData.error.includes('Duplicate entry')) {
                throw new Error('This certificate number already exists. Please try again - a unique number will be generated automatically.');
            } else if (errorData.error && errorData.error.includes('already exists')) {
                throw new Error(errorData.error);
            } else {
                throw new Error(errorData.error || 'Failed to issue certificate');
            }
        }

        Swal.fire({
            title: 'Success!',
            html: '<i class="bi bi-check-circle-fill text-success" style="font-size: 3rem;"></i><br><br>Certificate issued successfully!',
            icon: 'success',
            confirmButtonColor: '#667eea',
            timer: 2000
        });

        bootstrap.Modal.getInstance(document.getElementById('certificateModal')).hide();
        loadCertificates();
        updateStats();

    } catch (error) {
        console.error('Error issuing certificate:', error);

        Swal.fire({
            title: 'Error',
            html: `
                <div class="alert alert-danger text-start">
                    <i class="bi bi-exclamation-triangle me-2"></i>
                    <strong>Failed to issue certificate</strong>
                    <p class="mb-0 mt-2">${error.message}</p>
                </div>
                <p class="text-muted mt-3">
                    <small>If the problem persists, please contact support.</small>
                </p>
            `,
            icon: 'error',
            confirmButtonColor: '#667eea'
        });
    }
}

// ==================== ADD CUSTOM CSS FOR MANUAL LOGS ====================

const customStyles = `
<style>
.manual-logs-popup .table thead th {
    background-color: #f8f9fa;
    font-weight: 600;
    font-size: 0.85rem;
    text-transform: uppercase;
    color: #495057;
    border-bottom: 2px solid #dee2e6;
}

.manual-logs-popup .table tbody tr:hover {
    background-color: #f8f9fa;
}

.manual-logs-popup .table td {
    vertical-align: middle;
    font-size: 0.9rem;
}

.manual-logs-popup .badge {
    font-size: 0.85rem;
    padding: 0.35em 0.65em;
}

.table thead.sticky-top {
    position: sticky;
    top: 0;
    z-index: 10;
    box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}
</style>
`;

// Inject styles on page load
document.addEventListener('DOMContentLoaded', () => {
    const styleElement = document.createElement('div');
    styleElement.innerHTML = customStyles;
    document.head.appendChild(styleElement);
});