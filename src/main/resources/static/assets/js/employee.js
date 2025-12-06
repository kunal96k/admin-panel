    (function() {
        'use strict';

        const MENU_API_URL = '/api/menus';
        const API_BASE_URL = '/api/employees';
        const ROLE_API_URL = '/api/roles';

        let employees = [];
        let roles = [];
        let currentPage = 0;
        let pageSize = 25;
        let totalPages = 0;
        let totalItems = 0;
        let currentStep = 1;
        let editingEmployeeId = null;
        let webcamStream = null;
        let capturedPhoto = null;
        let employeeModal = null;

        // ========================================
        // PERMISSION CHECK UTILITY
        // ========================================

        // CSRF Token Management
        function getCsrfToken() {
            const csrfCookie = document.cookie
                .split('; ')
                .find(row => row.startsWith('XSRF-TOKEN='));
            return csrfCookie ? decodeURIComponent(csrfCookie.split('=')[1]) : null;
        }

        function getCsrfHeaders() {
            const token = getCsrfToken();
            return token ? { 'X-CSRF-TOKEN': token } : {};
        }

        function checkEmployeeModifyPermission(employeeId, operation) {

            return fetch(`/api/employees/${employeeId}/can-modify`, {
                headers: {
                    'Accept': 'application/json',
                    ...getCsrfHeaders()
                }
            })
                .then(response => response.json())
                .then(data => {
                    if (!data.canModify) {
                        Swal.fire({
                            icon: 'error',
                            title: '🚫 Access Denied',
                            html: `<div style="text-align: left; padding: 10px;">
                                ${data.message}
                            </div>`,
                            confirmButtonColor: '#ef4444',
                            confirmButtonText: 'Understood'
                        });
                        return false;
                    }
                    return true;
                })
                .catch(error => {
                    console.error('Permission check failed:', error);
                    return false;
                });
        }

        document.addEventListener('DOMContentLoaded', function() {
            initializeEventListeners();
            loadRoles();
            loadEmployees();

            // Initialize modal instance
            const modalElement = document.getElementById('employeeModal');
            if (modalElement) {
                employeeModal = new bootstrap.Modal(modalElement);
            }
        });

        async function loadRoles() {
            try {
                const response = await fetch(`${ROLE_API_URL}/active`, {
                    headers: {
                        'Accept': 'application/json',
                        ...getCsrfHeaders()
                    }
                });
                if (!response.ok) throw new Error('Failed to load roles');

                roles = await response.json();
                const roleSelect = document.getElementById('role');
                roleSelect.innerHTML = '<option value="">-- Select Role --</option>' +
                    roles.map(role => `<option value="${role.id}">${role.roleTitle}</option>`).join('');
            } catch (error) {
                console.error('Error loading roles:', error);
            }
        }

        function initializeEventListeners() {
            document.getElementById('btnAddEmployee').addEventListener('click', openAddEmployeeModal);
            document.getElementById('btnExportCSV').addEventListener('click', exportCSV);
            document.getElementById('saveEmployeeBtn').addEventListener('click', saveEmployee);
            document.getElementById('nextBtn').addEventListener('click', nextStep);
            document.getElementById('prevBtn').addEventListener('click', prevStep);
            document.getElementById('searchInput').addEventListener('input', debounce(handleSearch, 300));
            document.getElementById('pageSizeSelect').addEventListener('change', function() {
                pageSize = parseInt(this.value);
                currentPage = 0;
                loadEmployees();
            });
            document.getElementById('captureBtn').addEventListener('click', capturePhoto);
            document.getElementById('photoUpload').addEventListener('change', handlePhotoUpload);

            // Modal cleanup
            const employeeModalElement = document.getElementById('employeeModal');
            employeeModalElement.addEventListener('hidden.bs.modal', function() {
                if (webcamStream) {
                    webcamStream.getTracks().forEach(track => track.stop());
                    webcamStream = null;
                }
                resetForm();
            });

            setupPasswordValidation();
            setupUserCredentialsToggle();
        }

        function setupUserCredentialsToggle() {
            const checkbox = document.getElementById('addUserCheckbox');
            const section = document.getElementById('userCredentialsSection');

            if (checkbox && section) {
                checkbox.addEventListener('change', function() {
                    section.style.display = this.checked ? 'flex' : 'none';
                    if (!this.checked) {
                        clearPasswordFields();
                    }
                });
            }
        }

        function resetForm() {
            document.getElementById('employeeForm').reset();
            document.getElementById('addUserCheckbox').checked = false;
            document.getElementById('userCredentialsSection').style.display = 'none';
            clearPasswordFields();
            currentStep = 1;
            showStep(1);
            editingEmployeeId = null;

            // Clear canvas
            const canvas = document.getElementById('canvas');
            const ctx = canvas.getContext('2d');
            ctx.clearRect(0, 0, canvas.width, canvas.height);

            // Uncheck all menu permissions
            document.querySelectorAll('.menu-permission-checkbox').forEach(cb => cb.checked = false);
            updateMenuCount();
        }

        async function saveEmployee() {
            if (!validateCurrentStep()) return;

            const addUserChecked = document.getElementById('addUserCheckbox').checked;
            const username = document.getElementById('username').value.trim();
            const password = document.getElementById('password').value;
            const confirmPassword = document.getElementById('confirmPassword').value;

            // Only validate and send credentials if checkbox is checked
            if (addUserChecked) {
                if (!username || !password || !confirmPassword) {
                    showError('Username, Password and Confirm Password are all required when "Add User Login Credentials" is enabled');
                    return;
                }

                if (password !== confirmPassword) {
                    showError('Password and Confirm Password do not match');
                    return;
                }

                if (!validatePasswordRequirements(password)) {
                    showError('Password does not meet security requirements');
                    return;
                }
            }

            const menuPermissions = [];
            document.querySelectorAll('.menu-permission-checkbox').forEach(checkbox => {
                menuPermissions.push({
                    menuId: parseInt(checkbox.dataset.menuId),
                    hasAccess: checkbox.checked
                });
            });

            const employeeData = {
                employeeName: document.getElementById('employeeName').value,
                mobileNumber: document.getElementById('mobileNumber').value,
                emailId: document.getElementById('emailId').value,
                designation: document.getElementById('designation').value,
                gender: document.getElementById('gender').value,
                dateOfBirth: document.getElementById('dateOfBirth').value,
                address: document.getElementById('address').value,
                roleId: document.getElementById('role').value,
                zoomLink: document.getElementById('zoomLink').value,
                menuPermissions: menuPermissions,
                // Only send credentials if checkbox is CHECKED
                username: addUserChecked ? username : null,
                password: addUserChecked ? password : null,
                confirmPassword: addUserChecked ? confirmPassword : null,
                viewAdmission: document.getElementById('viewAdmission')?.checked || false,
                newAdmission: document.getElementById('newAdmission')?.checked || false,
                viewEnquiry: document.getElementById('viewEnquiry')?.checked || false,
                newEnquiry: document.getElementById('newEnquiry')?.checked || false,
                accDashboard: document.getElementById('accDashboard')?.checked || false,
                counsellorDash: document.getElementById('counsellorDash')?.checked || false,
                todaysFollowup: document.getElementById('todaysFollowup')?.checked || false,
                overdueFollowup: document.getElementById('overdueFollowup')?.checked || false,
                feeManager: document.getElementById('feeManager')?.checked || false,
                batchWiseFee: document.getElementById('batchWiseFee')?.checked || false,
                paymentLink: document.getElementById('paymentLink')?.checked || false,
                manageCourse: document.getElementById('manageCourse')?.checked || false,
                manageBatch: document.getElementById('manageBatch')?.checked || false,
                timeTable: document.getElementById('timeTable')?.checked || false,
                timeTableAttendance: document.getElementById('timeTableAttendance')?.checked || false,
                studyNote: document.getElementById('studyNote')?.checked || false,
                sendAppMsg: document.getElementById('sendAppMsg')?.checked || false,
                shareVideo: document.getElementById('shareVideo')?.checked || false,
                liveLecture: document.getElementById('liveLecture')?.checked || false,
                offlineExam: document.getElementById('offlineExam')?.checked || false
            };

            // Show loading with proper message
            Swal.fire({
                title: editingEmployeeId ? 'Updating Employee...' : 'Creating Employee...',
                html: '<div class="text-center"><div class="spinner-border text-primary" role="status"></div><p class="mt-3">Please wait while we save your changes...</p></div>',
                allowOutsideClick: false,
                showConfirmButton: false,
                didOpen: () => {
                    Swal.showLoading();
                }
            });

            try {
                const formData = new FormData();
                formData.append('data', JSON.stringify(employeeData));

                const photoFile = document.getElementById('photoUpload').files[0];
                if (photoFile) {
                    formData.append('photo', photoFile);
                }

                const url = editingEmployeeId ? `${API_BASE_URL}/${editingEmployeeId}` : API_BASE_URL;
                const method = editingEmployeeId ? 'PUT' : 'POST';

                const response = await fetch(url, {
                    method: method,
                    body: formData,
                    headers: {
                        'Accept': 'application/json',
                        ...getCsrfHeaders()
                    }
                });

                if (!response.ok) {
                    const error = await response.json();
                    throw new Error(error.message || 'Failed to save employee');
                }

                Swal.close();

                // Show success message with icon
                Swal.fire({
                    icon: 'success',
                    title: 'Success!',
                    text: editingEmployeeId ? 'Employee updated successfully!' : 'Employee added successfully!',
                    confirmButtonColor: '#667eea',
                    timer: 2000
                });

                const modalElement = document.getElementById('employeeModal');
                const modalInstance = bootstrap.Modal.getInstance(modalElement);
                if (modalInstance) {
                    modalInstance.hide();
                }

                await loadEmployees();
            } catch (error) {
                console.error('Error saving employee:', error);
                Swal.close();

                // Show detailed error message
                Swal.fire({
                    icon: 'error',
                    title: 'Error!',
                    text: error.message || 'Failed to save employee. Please try again.',
                    confirmButtonColor: '#ef4444'
                });
            }
        }

        function validatePasswordRequirements(password) {
            if (!password) return false;

            const hasMinLength = password.length >= 8;
            const hasUpperCase = /[A-Z]/.test(password);
            const hasLowerCase = /[a-z]/.test(password);
            const hasNumber = /[0-9]/.test(password);
            const hasSpecial = /[!@#$%^&*(),.?":{}|<>]/.test(password);

            return hasMinLength && hasUpperCase && hasLowerCase && hasNumber && hasSpecial;
        }

        async function loadEmployees() {
            try {
                showLoading();
                const response = await fetch(`${API_BASE_URL}?page=${currentPage}&size=${pageSize}`, {
                    headers: {
                        'Accept': 'application/json',
                        ...getCsrfHeaders()
                    }
                });
                if (!response.ok) throw new Error('Failed to load employees');

                const data = await response.json();
                employees = data.employees || [];
                totalPages = data.totalPages || 0;
                totalItems = data.totalItems || 0;

                renderTable();
            } catch (error) {
                console.error('Error loading employees:', error);
                showError('Failed to load employees');
                renderEmptyTable();
            }
        }

        function renderTable() {
            const tbody = document.getElementById('employeeTableBody');
            if (employees.length === 0) {
                renderEmptyTable();
                return;
            }

            tbody.innerHTML = employees.map((emp, index) => {
                const srNo = (currentPage * pageSize) + index + 1;
                const accessBadge = emp.isActive
                    ? '<span class="badge badge-access badge-active">Active</span>'
                    : '<span class="badge badge-access badge-inactive">Inactive</span>';

                const dobDisplay = emp.dateOfBirth ? formatDate(emp.dateOfBirth) : 'N/A';

                return `
                    <tr>
                        <td style="text-align:center;"><strong>${srNo}</strong></td>
                        <td>${emp.employeeName}</td>
                        <td>${emp.roleName || 'N/A'}</td>
                        <td>${emp.mobileNumber}</td>
                        <td>${emp.emailId}</td>
                        <td>${accessBadge}</td>
                        <td>${dobDisplay}</td>
                        <td>
                            <button class="action-btn btn-edit" data-employee-id="${emp.id}" title="Edit">
                                <i class="bi bi-pencil-square"></i>
                            </button>
                            <button class="action-btn btn-delete" data-employee-id="${emp.id}" title="Delete">
                                <i class="bi bi-trash"></i>
                            </button>
                            <button class="action-btn btn-view" data-employee-id="${emp.id}" title="View Details">
                                <i class="bi bi-eye"></i>
                            </button>
                        </td>
                    </tr>
                `;
            }).join('');

            updatePaginationInfo();
            renderPagination();

            // Attach event listeners using event delegation
            attachTableEventListeners();
        }

        function attachTableEventListeners() {
            const tbody = document.getElementById('employeeTableBody');

            // Remove old listeners if any
            tbody.removeEventListener('click', handleTableClick);

            // Add single event listener for all buttons
            tbody.addEventListener('click', handleTableClick);
        }

        function handleTableClick(event) {
            const editBtn = event.target.closest('.btn-edit');
            const deleteBtn = event.target.closest('.btn-delete');
            const viewBtn = event.target.closest('.btn-view');

            if (editBtn) {
                const employeeId = parseInt(editBtn.dataset.employeeId);
                editEmployee(employeeId);
            } else if (deleteBtn) {
                const employeeId = parseInt(deleteBtn.dataset.employeeId);
                deleteEmployee(employeeId);
            } else if (viewBtn) {
                const employeeId = parseInt(viewBtn.dataset.employeeId);
                viewEmployee(employeeId);
            }
        }

        let allMenus = [];

        async function loadMenus() {
            try {
                const response = await fetch(`${MENU_API_URL}/all`, {
                    headers: {
                        'Accept': 'application/json',
                        ...getCsrfHeaders()
                    }
                });
                if (!response.ok) throw new Error('Failed to load menus');

                allMenus = await response.json();
                renderMenuPermissions();
            } catch (error) {
                console.error('Error loading menus:', error);
                document.getElementById('menuPermissionsContainer').innerHTML =
                    '<div class="col-12 text-center text-danger">Failed to load menus</div>';
            }
        }

        function renderMenuPermissions() {
            const container = document.getElementById('menuPermissionsContainer');

            if (allMenus.length === 0) {
                container.innerHTML = '<div class="col-12 text-center text-muted">No menus available</div>';
                return;
            }

            const grouped = allMenus.reduce((acc, menu) => {
                if (!acc[menu.mainMenu]) acc[menu.mainMenu] = [];
                acc[menu.mainMenu].push(menu);
                return acc;
            }, {});

            container.innerHTML = Object.entries(grouped).map(([mainMenu, menus]) => `
                <div class="col-md-6 mb-3">
                    <div class="card">
                        <div class="card-header bg-light">
                            <strong>${mainMenu}</strong>
                        </div>
                        <div class="card-body">
                            ${menus.map(menu => `
                                <div class="form-check mb-2">
                                    <input class="form-check-input menu-permission-checkbox"
                                           type="checkbox"
                                           id="menu_${menu.id}"
                                           data-menu-id="${menu.id}"
                                           onchange="updateMenuCount()">
                                    <label class="form-check-label" for="menu_${menu.id}">
                                        ${menu.submenu}
                                    </label>
                                </div>
                            `).join('')}
                        </div>
                    </div>
                </div>
            `).join('');

            updateMenuCount();
        }

        function updateMenuCount() {
            const checked = document.querySelectorAll('.menu-permission-checkbox:checked').length;
            document.getElementById('menuPermissionCount').textContent = `${checked} selected`;
        }

        function nextStep() {
            if (validateCurrentStep()) {
                if (currentStep < 4) {
                    currentStep++;
                    showStep(currentStep);
                }
            }
        }

        function prevStep() {
            if (currentStep > 1) {
                currentStep--;
                showStep(currentStep);
            }
        }

        function showStep(step) {
            document.querySelectorAll('.wizard-content').forEach(content => {
                content.classList.remove('active');
            });
            document.querySelectorAll('.wizard-step').forEach(stepEl => {
                stepEl.classList.remove('active');
            });

            document.getElementById('step' + step).classList.add('active');
            document.querySelector(`[data-step="${step}"]`).classList.add('active');

            document.getElementById('prevBtn').style.display = step === 1 ? 'none' : 'inline-block';
            document.getElementById('nextBtn').style.display = step === 4 ? 'none' : 'inline-block';
            document.getElementById('saveEmployeeBtn').style.display = step === 4 ? 'inline-block' : 'none';
        }

        function validateCurrentStep() {
            const step = currentStep;

            if (step === 1) {
                const name = document.getElementById('employeeName').value.trim();
                const mobile = document.getElementById('mobileNumber').value.trim();
                const email = document.getElementById('emailId').value.trim();
                const gender = document.getElementById('gender').value;
                const dob = document.getElementById('dateOfBirth').value;

                if (!name || !mobile || !email || !gender || !dob) {
                    showError('Please fill all required fields');
                    return false;
                }

                if (mobile.length !== 10 || !/^\d+$/.test(mobile)) {
                    showError('Mobile number must be 10 digits');
                    return false;
                }
            }

            if (step === 2) {
                const roleId = document.getElementById('role').value;
                if (!roleId) {
                    showError('Please select a role');
                    return false;
                }
            }

            return true;
        }

        function handleSearch(event) {
            const searchTerm = event.target.value.trim();
            currentPage = 0;

            if (searchTerm) {
                searchEmployees(searchTerm);
            } else {
                loadEmployees();
            }
        }

        async function searchEmployees(searchTerm) {
            try {
                showLoading();
                const response = await fetch(`${API_BASE_URL}/search?searchTerm=${encodeURIComponent(searchTerm)}&page=${currentPage}&size=${pageSize}`, {
                    headers: {
                        'Accept': 'application/json',
                        ...getCsrfHeaders()
                    }
                });
                if (!response.ok) throw new Error('Failed to search employees');

                const data = await response.json();
                employees = data.employees || [];
                totalPages = data.totalPages || 0;
                totalItems = data.totalItems || 0;

                renderTable();
            } catch (error) {
                console.error('Error searching employees:', error);
                showError('Failed to search employees');
                renderEmptyTable();
            }
        }

        function setupPasswordValidation() {
            const password = document.getElementById('password');
            const confirmPassword = document.getElementById('confirmPassword');

            if (password) {
                password.addEventListener('input', function() {
                    validatePasswordStrength(this.value);
                    checkPasswordMatch();
                });
                addPasswordToggle(password);
            }

            if (confirmPassword) {
                confirmPassword.addEventListener('input', checkPasswordMatch);
                addPasswordToggle(confirmPassword);
            }
        }

        function setupMenuAccordion() {
            const accordionButton = document.querySelector('[data-bs-toggle="collapse"]');
            if (accordionButton) {
                accordionButton.addEventListener('click', function(e) {
                    e.preventDefault();
                    const target = document.querySelector(this.getAttribute('data-bs-target'));
                    const bsCollapse = new bootstrap.Collapse(target, {
                        toggle: true
                    });
                });
            }
        }

        function addPasswordToggle(input) {
            const wrapper = input.parentElement;
            if (wrapper.querySelector('.password-toggle')) return;

            const toggleBtn = document.createElement('button');
            toggleBtn.type = 'button';
            toggleBtn.className = 'password-toggle';
            toggleBtn.innerHTML = '<i class="bi bi-eye"></i>';
            toggleBtn.style.cssText = `
                position: absolute;
                right: 10px;
                top: 50%;
                transform: translateY(-50%);
                border: none;
                background: transparent;
                cursor: pointer;
                padding: 5px;
                color: #64748b;
                z-index: 10;
            `;

            toggleBtn.addEventListener('click', function() {
                const type = input.getAttribute('type') === 'password' ? 'text' : 'password';
                input.setAttribute('type', type);
                this.querySelector('i').className = type === 'password' ? 'bi bi-eye' : 'bi bi-eye-slash';
            });

            wrapper.appendChild(toggleBtn);
        }

        function validatePasswordStrength(password) {
            const strengthIndicator = getOrCreateElement('passwordStrengthIndicator',
                document.getElementById('password').parentElement);
            const guideText = getOrCreateElement('passwordGuide',
                document.getElementById('password').parentElement);

            if (!password) {
                strengthIndicator.style.display = 'none';
                guideText.innerHTML = getPasswordGuidelines();
                return;
            }

            strengthIndicator.style.display = 'block';

            const hasMinLength = password.length >= 8;
            const hasUpperCase = /[A-Z]/.test(password);
            const hasLowerCase = /[a-z]/.test(password);
            const hasNumber = /[0-9]/.test(password);
            const hasSpecial = /[!@#$%^&*(),.?":{}|<>]/.test(password);

            const strength = [hasMinLength, hasUpperCase, hasLowerCase, hasNumber, hasSpecial]
                .filter(Boolean).length;

            let strengthText, strengthColor, strengthWidth;

            if (strength <= 2) {
                strengthText = 'Weak';
                strengthColor = '#ef4444';
                strengthWidth = '33%';
            } else if (strength <= 4) {
                strengthText = 'Medium';
                strengthColor = '#f59e0b';
                strengthWidth = '66%';
            } else {
                strengthText = 'Strong';
                strengthColor = '#10b981';
                strengthWidth = '100%';
            }

            strengthIndicator.innerHTML = `
                <div style="margin-top: 5px;">
                    <div style="display: flex; justify-content: space-between; margin-bottom: 3px;">
                        <small style="color: #64748b;">Password Strength:</small>
                        <small style="color: ${strengthColor}; font-weight: 600;">${strengthText}</small>
                    </div>
                    <div style="height: 4px; background: #e2e8f0; border-radius: 2px; overflow: hidden;">
                        <div style="height: 100%; width: ${strengthWidth}; background: ${strengthColor};
                             transition: all 0.3s;"></div>
                    </div>
                </div>
            `;

            guideText.innerHTML = `
                <div style="margin-top: 8px; padding: 8px; background: #f8fafc; border-radius: 4px;
                     font-size: 0.85rem;">
                    <div style="color: ${hasMinLength ? '#10b981' : '#94a3b8'};">
                        <i class="bi ${hasMinLength ? 'bi-check-circle-fill' : 'bi-circle'}"></i>
                        At least 8 characters
                    </div>
                    <div style="color: ${hasUpperCase ? '#10b981' : '#94a3b8'};">
                        <i class="bi ${hasUpperCase ? 'bi-check-circle-fill' : 'bi-circle'}"></i>
                        One uppercase letter
                    </div>
                    <div style="color: ${hasLowerCase ? '#10b981' : '#94a3b8'};">
                        <i class="bi ${hasLowerCase ? 'bi-check-circle-fill' : 'bi-circle'}"></i>
                        One lowercase letter
                    </div>
                    <div style="color: ${hasNumber ? '#10b981' : '#94a3b8'};">
                        <i class="bi ${hasNumber ? 'bi-check-circle-fill' : 'bi-circle'}"></i>
                        One number
                    </div>
                    <div style="color: ${hasSpecial ? '#10b981' : '#94a3b8'};">
                        <i class="bi ${hasSpecial ? 'bi-check-circle-fill' : 'bi-circle'}"></i>
                        One special character (!@#$%^&*)
                    </div>
                </div>
            `;
        }

        function checkPasswordMatch() {
            const password = document.getElementById('password').value;
            const confirmPassword = document.getElementById('confirmPassword');
            const confirmValue = confirmPassword.value;
            const feedbackDiv = document.getElementById('confirmPasswordFeedback');

            if (!confirmValue) {
                confirmPassword.style.borderColor = '';
                confirmPassword.style.boxShadow = '';
                feedbackDiv.innerHTML = '';
                return;
            }

            if (password === confirmValue) {
                confirmPassword.style.borderColor = '#10b981';
                confirmPassword.style.boxShadow = '0 0 0 0.2rem rgba(16, 185, 129, 0.25)';
                feedbackDiv.innerHTML = `
                    <span style="color: #10b981;">
                        <i class="bi bi-check-circle-fill"></i> Passwords match
                    </span>
                `;
            } else {
                confirmPassword.style.borderColor = '#ef4444';
                confirmPassword.style.boxShadow = '0 0 0 0.2rem rgba(239, 68, 68, 0.25)';
                feedbackDiv.innerHTML = `
                    <span style="color: #ef4444;">
                        <i class="bi bi-x-circle-fill"></i> Passwords do not match
                    </span>
                `;
            }
        }

        function getPasswordGuidelines() {
            return `
                <div style="margin-top: 8px; padding: 8px; background: #f1f5f9; border-radius: 4px;
                     font-size: 0.85rem; color: #475569;">
                    <strong>Password must contain:</strong>
                    <ul style="margin: 5px 0 0 20px; padding: 0;">
                        <li>At least 8 characters</li>
                        <li>One uppercase letter (A-Z)</li>
                        <li>One lowercase letter (a-z)</li>
                        <li>One number (0-9)</li>
                        <li>One special character (!@#$%^&*)</li>
                    </ul>
                </div>
            `;
        }

        function getOrCreateElement(id, parent) {
            let element = document.getElementById(id);
            if (!element) {
                element = document.createElement('div');
                element.id = id;
                parent.appendChild(element);
            }
            return element;
        }

        function clearPasswordFields() {
            document.getElementById('username').value = '';
            document.getElementById('password').value = '';
            document.getElementById('confirmPassword').value = '';
            document.getElementById('confirmPassword').style.borderColor = '';
            document.getElementById('confirmPassword').style.boxShadow = '';

            const indicator = document.getElementById('passwordStrengthIndicator');
            if (indicator) indicator.innerHTML = '';

            const guide = document.getElementById('passwordGuide');
            if (guide) guide.innerHTML = '';

            const feedback = document.getElementById('confirmPasswordFeedback');
            if (feedback) feedback.innerHTML = '';
        }

        function openAddEmployeeModal() {
            resetForm();
            document.getElementById('employeeModalTitle').innerHTML =
                '<i class="bi bi-plus-circle me-2"></i>Add New Employee';
            loadMenus();
            if (employeeModal) employeeModal.show();
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

        function formatDate(dateString) {
            if (!dateString) return 'N/A';

            try {
                const date = new Date(dateString);
                const options = { year: 'numeric', month: 'short', day: 'numeric' };
                return date.toLocaleDateString('en-IN', options);
            } catch (e) {
                return dateString;
            }
        }

        function renderEmptyTable() {
            document.getElementById('employeeTableBody').innerHTML = `
                <tr>
                    <td colspan="8" class="text-center py-5">
                        <i class="bi bi-inbox" style="font-size: 3rem; color: #94a3b8;"></i>
                        <p class="mt-3 mb-0 text-muted">No employees found</p>
                    </td>
                </tr>
            `;
        }

        function showLoading() {
            document.getElementById('employeeTableBody').innerHTML = `
                <tr>
                    <td colspan="8" class="text-center py-4">
                        <div class="spinner-border text-primary" role="status"></div>
                        <p class="mt-2 mb-0">Loading employees...</p>
                    </td>
                </tr>
            `;
        }

        function updatePaginationInfo() {
            const start = totalItems === 0 ? 0 : (currentPage * pageSize) + 1;
            const end = Math.min((currentPage + 1) * pageSize, totalItems);

            document.getElementById('entriesStart').textContent = start;
            document.getElementById('entriesEnd').textContent = end;
            document.getElementById('totalEntries').textContent = totalItems;
        }

        function renderPagination() {
            const pagination = document.getElementById('paginationControls');
            if (totalPages <= 1) {
                pagination.innerHTML = '';
                return;
            }

            let html = '';

            html += `
                <li class="page-item ${currentPage === 0 ? 'disabled' : ''}">
                    <a class="page-link" href="#" onclick="changePage(${currentPage - 1}); return false;">Previous</a>
                </li>
            `;

            for (let i = 0; i < totalPages; i++) {
                if (i === 0 || i === totalPages - 1 || (i >= currentPage - 1 && i <= currentPage + 1)) {
                    html += `
                        <li class="page-item ${i === currentPage ? 'active' : ''}">
                            <a class="page-link" href="#" onclick="changePage(${i}); return false;">${i + 1}</a>
                        </li>
                    `;
                } else if (i === currentPage - 2 || i === currentPage + 2) {
                    html += '<li class="page-item disabled"><span class="page-link">...</span></li>';
                }
            }

            html += `
                <li class="page-item ${currentPage === totalPages - 1 ? 'disabled' : ''}">
                    <a class="page-link" href="#" onclick="changePage(${currentPage + 1}); return false;">Next</a>
                </li>
            `;

            pagination.innerHTML = html;
        }

        function capturePhoto() {
            const video = document.getElementById('video');
            const canvas = document.getElementById('canvas');
            const context = canvas.getContext('2d');

            canvas.width = video.videoWidth;
            canvas.height = video.videoHeight;
            context.drawImage(video, 0, 0);

            capturedPhoto = canvas.toDataURL('image/png');
        }

        function handlePhotoUpload(event) {
            const file = event.target.files[0];
            if (file) {
                const reader = new FileReader();
                reader.onload = function(e) {
                    const canvas = document.getElementById('canvas');
                    const context = canvas.getContext('2d');
                    const img = new Image();
                    img.onload = function() {
                        canvas.width = img.width;
                        canvas.height = img.height;
                        context.drawImage(img, 0, 0);
                    };
                    img.src = e.target.result;
                };
                reader.readAsDataURL(file);
            }
        }

        async function editEmployee(id) {
            try {
                //  STEP 1: Check permission BEFORE loading any data
                const hasPermission = await checkEmployeeModifyPermission(id, 'update');
                if (!hasPermission) {
                    return; // Stop execution if no permission
                }

                // STEP 2: Show loading
                Swal.fire({
                    title: 'Loading...',
                    html: '<div class="text-center"><div class="spinner-border text-primary" role="status"></div><p class="mt-3">Please wait while we load employee data...</p></div>',
                    allowOutsideClick: false,
                    showConfirmButton: false,
                    didOpen: () => {
                        Swal.showLoading();
                    }
                });

                // STEP 3: Load employee data
                const response = await fetch(`${API_BASE_URL}/${id}`, {
                    headers: {
                        'Accept': 'application/json',
                        ...getCsrfHeaders()
                    }
                });

                if (!response.ok) throw new Error('Failed to load employee');

                const employee = await response.json();

                // STEP 4: Load menus and permissions
                await loadMenus();
                await loadEmployeePermissions(id);

                editingEmployeeId = id;

                document.getElementById('employeeModalTitle').innerHTML =
                    '<i class="bi bi-pencil-square me-2"></i>Edit Employee';

                // STEP 5: Fill form fields with existing data
                document.getElementById('employeeName').value = employee.employeeName || '';
                document.getElementById('mobileNumber').value = employee.mobileNumber || '';
                document.getElementById('emailId').value = employee.emailId || '';
                document.getElementById('designation').value = employee.designation || '';
                document.getElementById('gender').value = employee.gender || '';
                document.getElementById('dateOfBirth').value = employee.dateOfBirth || '';
                document.getElementById('address').value = employee.address || '';
                document.getElementById('role').value = employee.roleId || '';
                document.getElementById('zoomLink').value = employee.zoomLink || '';

                // STEP 6: Fill mobile permissions checkboxes
                document.getElementById('newAdmission').checked = employee.newAdmission || false;
                document.getElementById('viewAdmission').checked = employee.viewAdmission || false;
                document.getElementById('newEnquiry').checked = employee.newEnquiry || false;
                document.getElementById('viewEnquiry').checked = employee.viewEnquiry || false;
                document.getElementById('accDashboard').checked = employee.accDashboard || false;
                document.getElementById('counsellorDash').checked = employee.counsellorDash || false;
                document.getElementById('todaysFollowup').checked = employee.todaysFollowup || false;
                document.getElementById('overdueFollowup').checked = employee.overdueFollowup || false;
                document.getElementById('feeManager').checked = employee.feeManager || false;
                document.getElementById('batchWiseFee').checked = employee.batchWiseFee || false;
                document.getElementById('paymentLink').checked = employee.paymentLink || false;
                document.getElementById('manageCourse').checked = employee.manageCourse || false;
                document.getElementById('manageBatch').checked = employee.manageBatch || false;
                document.getElementById('timeTable').checked = employee.timeTable || false;
                document.getElementById('timeTableAttendance').checked = employee.timeTableAttendance || false;
                document.getElementById('studyNote').checked = employee.studyNote || false;
                document.getElementById('sendAppMsg').checked = employee.sendAppMsg || false;
                document.getElementById('shareVideo').checked = employee.shareVideo || false;
                document.getElementById('liveLecture').checked = employee.liveLecture || false;
                document.getElementById('offlineExam').checked = employee.offlineExam || false;

                // STEP 7: Hide user credentials section on edit (only show if they want to update)
                document.getElementById('addUserCheckbox').checked = false;
                document.getElementById('userCredentialsSection').style.display = 'none';
                clearPasswordFields();

                currentStep = 1;
                showStep(1);

                // STEP 8: Close loading and show modal
                Swal.close();

                const modal = new bootstrap.Modal(document.getElementById('employeeModal'));
                modal.show();
            } catch (error) {
                console.error('Error loading employee:', error);
                Swal.close();
                Swal.fire({
                    icon: 'error',
                    title: 'Error!',
                    text: error.message || 'Failed to load employee details',
                    confirmButtonColor: '#ef4444'
                });
            }
        }

        // Security check before delete
        async function deleteEmployee(id) {
            try {
                //  STEP 1: Check permission BEFORE showing confirmation
                const hasPermission = await checkEmployeeModifyPermission(id, 'delete');
                if (!hasPermission) {
                    return; // Stop execution if no permission
                }

                // STEP 2: Show confirmation dialog
                const result = await Swal.fire({
                    title: 'Are you sure?',
                    html: `
                        <div class="text-center">
                            <p style="font-size: 16px; margin-bottom: 10px;">You won't be able to revert this action!</p>
                            <p style="font-size: 14px; color: #64748b;">This will permanently delete the employee record.</p>
                        </div>
                    `,
                    icon: 'warning',
                    showCancelButton: true,
                    confirmButtonColor: '#ef4444',
                    cancelButtonColor: '#64748b',
                    confirmButtonText: 'Yes, delete it!',
                    cancelButtonText: 'Cancel'
                });

                if (result.isConfirmed) {
                    // STEP 3: Show deleting loader
                    Swal.fire({
                        title: 'Deleting...',
                        html: '<div class="text-center"><div class="spinner-border text-danger" role="status"></div><p class="mt-3">Please wait...</p></div>',
                        allowOutsideClick: false,
                        showConfirmButton: false
                    });

                    const response = await fetch(`${API_BASE_URL}/${id}`, {
                        method: 'DELETE',
                        headers: {
                            'Accept': 'application/json',
                            ...getCsrfHeaders()
                        }
                    });

                    if (!response.ok) {
                        const error = await response.json();
                        throw new Error(error.message || 'Failed to delete employee');
                    }

                    Swal.close();
                    Swal.fire({
                        icon: 'success',
                        title: 'Deleted!',
                        text: 'Employee deleted successfully!',
                        confirmButtonColor: '#667eea',
                        timer: 2000
                    });

                    await loadEmployees();
                }
            } catch (error) {
                console.error('Error deleting employee:', error);
                Swal.close();
                Swal.fire({
                    icon: 'error',
                    title: 'Error!',
                    text: error.message || 'Failed to delete employee',
                    confirmButtonColor: '#ef4444'
                });
            }
        }

        async function loadEmployeePermissions(employeeId) {
            try {
                const response = await fetch(`${API_BASE_URL}/${employeeId}/permissions`, {
                    headers: {
                        'Accept': 'application/json',
                        ...getCsrfHeaders()
                    }
                });
                if (!response.ok) return; // Skip if endpoint doesn't exist yet

                const permissions = await response.json();

                // Set menu permission checkboxes
                permissions.forEach(perm => {
                    const checkbox = document.querySelector(`[data-menu-id="${perm.menuId}"]`);
                    if (checkbox) {
                        checkbox.checked = perm.hasAccess;
                    }
                });

                updateMenuCount();
            } catch (error) {
                console.warn('Could not load employee permissions:', error);
            }
        }

        async function exportCSV() {
            try {
                const response = await fetch(`${API_BASE_URL}?page=0&size=10000`, {
                    headers: {
                        'Accept': 'application/json',
                        ...getCsrfHeaders()
                    }
                });
                if (!response.ok) throw new Error('Failed to fetch data');

                const data = await response.json();
                const employees = data.employees || [];

                // Check if table is empty
                if (employees.length === 0) {
                    Swal.fire({
                        icon: 'info',
                        title: 'No Data Available',
                        text: 'The employee table is empty. There is no data to export.',
                        confirmButtonColor: '#667eea'
                    });
                    return;
                }

                let csv = 'Employee Name,Mobile,Email,Role,Designation,Gender,DOB,Address,Status\n';

                employees.forEach(emp => {
                    csv += `"${emp.employeeName}","${emp.mobileNumber}","${emp.emailId}",`;
                    csv += `"${emp.roleName || 'N/A'}","${emp.designation || ''}","${emp.gender}",`;
                    csv += `"${formatDate(emp.dateOfBirth)}","${emp.address || ''}","${emp.isActive ? 'Active' : 'Inactive'}"\n`;
                });

                const blob = new Blob([csv], { type: 'text/csv' });
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `employees_${new Date().getTime()}.csv`;
                a.click();
                window.URL.revokeObjectURL(url);

                showSuccess(`CSV exported successfully! (${employees.length} records)`);
            } catch (error) {
                console.error('Error exporting CSV:', error);
                showError('Failed to export CSV');
            }
        }


           async function viewEmployee(id) {
               try {
                   const response = await fetch(`${API_BASE_URL}/${id}`, {
                       headers: {
                           'Accept': 'application/json',
                           ...getCsrfHeaders()
                       }
                   });
                   if (!response.ok) throw new Error('Failed to load employee');

                   const emp = await response.json();

                   Swal.fire({
                       title: emp.employeeName,
                       html: `
                           <div style="text-align: left;">
                               <p><strong>Mobile:</strong> ${emp.mobileNumber}</p>
                               <p><strong>Email:</strong> ${emp.emailId}</p>
                               <p><strong>Role:</strong> ${emp.roleName || 'N/A'}</p>
                               <p><strong>Designation:</strong> ${emp.designation || 'N/A'}</p>
                               <p><strong>Gender:</strong> ${emp.gender}</p>
                               <p><strong>DOB:</strong> ${formatDate(emp.dateOfBirth)}</p>
                               <p><strong>Address:</strong> ${emp.address || 'N/A'}</p>
                               <p><strong>Status:</strong> ${emp.isActive ? 'Active' : 'Inactive'}</p>
                           </div>
                       `,
                       confirmButtonColor: '#667eea',
                       width: 600
                   });
               } catch (error) {
                   console.error('Error viewing employee:', error);
                   showError('Failed to load employee details');
               }
           }

            window.changePage = function(page) {
                if (page >= 0 && page < totalPages) {
                    currentPage = page;
                    loadEmployees();
                }
            };

            window.updateMenuCount = updateMenuCount;
            window.editEmployee = editEmployee;
            window.deleteEmployee = deleteEmployee;
    })();