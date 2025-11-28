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
                const response = await fetch(`${ROLE_API_URL}/active`);
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

            const username = document.getElementById('username').value.trim();
            const password = document.getElementById('password').value;
            const confirmPassword = document.getElementById('confirmPassword').value;

            if (document.getElementById('addUserCheckbox').checked) {
                if (!username || !password || !confirmPassword) {
                    showError('Username, Password and Confirm Password are all required');
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
                username: username || null,
                password: password || null,
                confirmPassword: confirmPassword || null,
                viewAdmission: document.getElementById('viewAdmission')?.checked || false,
                newAdmission: document.getElementById('newAdmission')?.checked || false,
                viewEnquiry: document.getElementById('viewEnquiry')?.checked || false,
                newEnquiry: document.getElementById('newEnquiry')?.checked || false,
                feeManager: document.getElementById('feeManager')?.checked || false,
                manageCourse: document.getElementById('manageCourse')?.checked || false,
                manageBatch: document.getElementById('manageBatch')?.checked || false
            };

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
                    body: formData
                });

                if (!response.ok) {
                    const error = await response.json();
                    throw new Error(error.message || 'Failed to save employee');
                }

                showSuccess(editingEmployeeId ? 'Employee updated successfully!' : 'Employee added successfully!');

              const modalElement = document.getElementById('employeeModal');
              const modalInstance = bootstrap.Modal.getInstance(modalElement);
              if (modalInstance) {
                  modalInstance.hide();
              }

                await loadEmployees();
            } catch (error) {
                console.error('Error saving employee:', error);
                showError(error.message);
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
                const response = await fetch(`${API_BASE_URL}?page=${currentPage}&size=${pageSize}`);

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

                // Format date of birth for display
                const dobDisplay = emp.dateOfBirth ? formatDate(emp.dateOfBirth) : 'N/A';

                return `
                    <tr>
                        <td><strong>${srNo}</strong></td>
                        <td>${emp.employeeName}</td>
                        <td>${emp.roleName || 'N/A'}</td>
                        <td>${emp.mobileNumber}</td>
                        <td>${emp.emailId}</td>
                        <td>${accessBadge}</td>
                        <td>${dobDisplay}</td>
                        <td>
                            <button class="action-btn" onclick="editEmployee(${emp.id})" title="Edit">
                                <i class="bi bi-pencil-square"></i>
                            </button>
                            <button class="action-btn" onclick="deleteEmployee(${emp.id})" title="Delete">
                                <i class="bi bi-trash"></i>
                            </button>
                            <button class="action-btn" onclick="viewEmployee(${emp.id})" title="View Details">
                                <i class="bi bi-eye"></i>
                            </button>
                        </td>
                    </tr>
                `;
            }).join('');

            updatePaginationInfo();
            renderPagination();
        }

        let allMenus = [];

        async function loadMenus() {
            try {
                const response = await fetch(`${MENU_API_URL}/all`);
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
                const response = await fetch(`${API_BASE_URL}/search?searchTerm=${encodeURIComponent(searchTerm)}&page=${currentPage}&size=${pageSize}`);

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


        window.editEmployee = async function (id) {
            try {
                // Check if user can modify this employee
                const securityCheck = await fetch(`${API_BASE_URL}/${id}/can-modify`);
                const permission = await securityCheck.json();

                if (!permission.canModify) {
                    Swal.fire({
                        icon: 'error',
                        title: '🚫 Access Denied',
                        html: permission.message,
                        confirmButtonColor: '#ef4444',
                        confirmButtonText: 'Understood'
                    });
                    return;
                }

                // Proceed with loading employee data
                const response = await fetch(`${API_BASE_URL}/${id}`);
                if (!response.ok) throw new Error('Failed to load employee');

                const employee = await response.json();
                editingEmployeeId = id;

                document.getElementById('employeeModalTitle').innerHTML =
                    '<i class="bi bi-pencil-square me-2"></i>Edit Employee';

                // Fill form fields
                document.getElementById('employeeName').value = employee.employeeName || '';
                document.getElementById('mobileNumber').value = employee.mobileNumber || '';
                document.getElementById('emailId').value = employee.emailId || '';
                document.getElementById('designation').value = employee.designation || '';
                document.getElementById('gender').value = employee.gender || '';
                document.getElementById('dateOfBirth').value = employee.dateOfBirth || '';
                document.getElementById('address').value = employee.address || '';
                document.getElementById('role').value = employee.roleId || '';
                document.getElementById('zoomLink').value = employee.zoomLink || '';

                loadMenus();

                currentStep = 1;
                showStep(1);

                const modal = new bootstrap.Modal(document.getElementById('employeeModal'));
                modal.show();
            } catch (error) {
                console.error('Error loading employee:', error);
                showError(error.message || 'Failed to load employee details');
            }
        };

        // Security check before delete
        window.deleteEmployee = async function (id) {
            try {
                // Check if user can delete this employee
                const securityCheck = await fetch(`${API_BASE_URL}/${id}/can-modify`);
                const permission = await securityCheck.json();

                if (!permission.canDelete) {
                    Swal.fire({
                        icon: 'error',
                        title: '🚫 Access Denied',
                        html: permission.message,
                        confirmButtonColor: '#ef4444',
                        confirmButtonText: 'Understood'
                    });
                    return;
                }

                // Proceed with deletion confirmation
                const result = await Swal.fire({
                    title: 'Are you sure?',
                    text: "You won't be able to revert this!",
                    icon: 'warning',
                    showCancelButton: true,
                    confirmButtonColor: '#ef4444',
                    cancelButtonColor: '#64748b',
                    confirmButtonText: 'Yes, deleteDContinueit!'
                });

                if (result.isConfirmed) {
                    const response = await fetch(`${API_BASE_URL}/${id}`, {
                        method: 'DELETE'
                    });

                    if (!response.ok) {
                        const error = await response.json();
                        throw new Error(error.message || 'Failed to delete employee');
                    }

                    showSuccess('Employee deleted successfully!');
                    await loadEmployees();
                }
            } catch (error) {
                console.error('Error deleting employee:', error);
                showError(error.message || 'Failed to delete employee');
            }
        };

        async function exportCSV() {
            try {
                const response = await fetch(`${API_BASE_URL}?page=0&size=10000`);
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


            window.viewEmployee = async function(id) {
                try {
                    const response = await fetch(`${API_BASE_URL}/${id}`);
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
            };

            window.changePage = function(page) {
                if (page >= 0 && page < totalPages) {
                    currentPage = page;
                    loadEmployees();
                }
            };

            window.updateMenuCount = updateMenuCount;

    })();