/**
 * Role Management System
 */
(function() {
    'use strict';

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

    const API_BASE_URL = '/api/roles';
    const MENU_API_URL = '/api/menus';

    let roles = [];
    let menus = [];
    let currentRoleId = null;

    document.addEventListener('DOMContentLoaded', function() {
        initializeEventListeners();
        loadRoles();
    });

    function initializeEventListeners() {
        document.getElementById('roleForm').addEventListener('submit', saveRole);
        document.getElementById('cancelBtn').addEventListener('click', cancelEdit);
        document.getElementById('selectAll').addEventListener('change', toggleSelectAll);
    }

    async function loadRoles() {
        try {
          const response = await fetch(API_BASE_URL, {
              headers: {
                  'Accept': 'application/json',
                  ...getCsrfHeaders()
              }
          });

            roles = await response.json();
            renderRoleTable();
        } catch (error) {
            console.error('Error loading roles:', error);
            showError('Failed to load roles');
        }
    }

    function renderRoleTable() {
        const tbody = document.getElementById('roleTableBody');

        if (roles.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="3" class="text-center py-5">
                        <i class="bi bi-inbox" style="font-size: 3rem; color: #94a3b8;"></i>
                        <p class="mt-3 mb-0 text-muted">No roles found</p>
                    </td>
                </tr>
            `;
            return;
        }

        tbody.innerHTML = roles
            .map(
                (role, index) => `
            <tr>
                <td><strong>${index + 1}</strong></td>
                <td>${role.roleTitle}</td>
                <td>
                    <button class="action-btn" onclick="editRole(${role.id})" title="Edit Role">
                        <i class="bi bi-pencil-square"></i>
                    </button>
                    <button class="action-btn" onclick="deleteRole(${role.id})" title="Delete Role">
                        <i class="bi bi-trash"></i>
                    </button>
                </td>
            </tr>
        `
            )
            .join('');
    }

    async function saveRole(e) {
        e.preventDefault();

        const roleTitle = document.getElementById('roleTitle').value.trim();
        if (!roleTitle) {
            showError('Please enter role title');
            return;
        }

        try {
            const url = currentRoleId ? `${API_BASE_URL}/${currentRoleId}` : API_BASE_URL;
            const method = currentRoleId ? 'PUT' : 'POST';

           const response = await fetch(url, {
               method: method,
               headers: {
                   'Content-Type': 'application/json',
                   ...getCsrfHeaders()
               },
               body: JSON.stringify({ roleTitle })
           });

            if (!response.ok) throw new Error('Failed to save role');

            const savedRole = await response.json();
            currentRoleId = savedRole.id;

            showSuccess(currentRoleId ? 'Role updated successfully!' : 'Role created successfully!');

            await loadRoles();
            await loadMenuPermissions(currentRoleId);

            document.getElementById('permissionsSection').style.display = 'block';
        } catch (error) {
            console.error('Error saving role:', error);
            showError(error.message);
        }
    }

    async function loadMenuPermissions(roleId) {
        try {
            const response = await fetch(`${MENU_API_URL}?roleId=${roleId}`, {
                headers: {
                    'Accept': 'application/json',
                    ...getCsrfHeaders()
                }
            });
            menus = await response.json();
            renderPermissionsTable();
        } catch (error) {
            console.error('Error loading menus:', error);
            showError('Failed to load menu permissions');
        }
    }

    function renderPermissionsTable() {
        const tbody = document.getElementById('permissionsTableBody');

        let html = '';
        let currentMainMenu = '';

        menus.forEach(menu => {
            if (menu.mainMenu !== currentMainMenu) {
                currentMainMenu = menu.mainMenu;
                html += `
                    <tr class="menu-group">
                        <td colspan="3"><strong>${currentMainMenu}</strong></td>
                    </tr>
                `;
            }

            html += `
                <tr class="submenu-row">
                    <td></td>
                    <td>${menu.submenu}</td>
                    <td>
                        <div class="form-check">
                            <input class="form-check-input menu-checkbox"
                                   type="checkbox"
                                   data-menu-id="${menu.id}"
                                   ${menu.hasAccess ? 'checked' : ''}
                                   onchange="updateMenuPermission(${menu.id}, this.checked)">
                        </div>
                    </td>
                </tr>
            `;
        });

        tbody.innerHTML = html;
    }

    window.updateMenuPermission = async function(menuId, hasAccess) {
        try {
           const response = await fetch(`${API_BASE_URL}/${currentRoleId}/permissions`, {
               method: 'POST',
               headers: {
                   'Content-Type': 'application/json',
                   ...getCsrfHeaders()
               },
               body: JSON.stringify({ menuId, hasAccess })
           });

            if (!response.ok) throw new Error('Failed to update permission');
        } catch (error) {
            console.error('Error updating permission:', error);
            showError('Failed to update permission');
        }
    };

    function toggleSelectAll(e) {
        const checkboxes = document.querySelectorAll('.menu-checkbox');
        checkboxes.forEach(cb => {
            cb.checked = e.target.checked;
            updateMenuPermission(parseInt(cb.dataset.menuId), cb.checked);
        });
    }

    window.editRole = async function(id) {
        try {
            const response = await fetch(`${API_BASE_URL}/${id}`, {
                headers: {
                    'Accept': 'application/json',
                    ...getCsrfHeaders()
                }
            });
            const role = await response.json();

            currentRoleId = id;
            document.getElementById('roleTitle').value = role.roleTitle;
            document.getElementById('saveRoleBtn').innerHTML =
                '<i class="bi bi-check-circle me-2"></i>Update Role';

            await loadMenuPermissions(id);
            document.getElementById('permissionsSection').style.display = 'block';
        } catch (error) {
            console.error('Error loading role:', error);
            showError('Failed to load role details');
        }
    };

    window.deleteRole = async function(id) {
        const result = await Swal.fire({
            title: 'Confirmation',
            text: 'Do you really want to remove selected employee role?',
            icon: 'warning',
            showCancelButton: true,
            confirmButtonText: 'Yes, Delete',
            cancelButtonText: 'Cancel',
            confirmButtonColor: '#ef4444'
        });

        if (!result.isConfirmed) return;

        try {
            const response = await fetch(`${API_BASE_URL}/${id}`, {
                method: 'DELETE',
                headers: {
                    'Accept': 'application/json',
                    ...getCsrfHeaders()
                }
            });
            if (!response.ok) throw new Error('Failed to delete role');

            showSuccess('Role deleted successfully!');
            await loadRoles();
            cancelEdit();
        } catch (error) {
            console.error('Error deleting role:', error);
            showError(error.message);
        }
    };

    function cancelEdit() {
        currentRoleId = null;
        document.getElementById('roleForm').reset();
        document.getElementById('saveRoleBtn').innerHTML =
            '<i class="bi bi-check-circle me-2"></i>Save';
        document.getElementById('permissionsSection').style.display = 'none';
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
})();
