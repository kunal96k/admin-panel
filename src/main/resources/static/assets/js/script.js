/**
 * Global API Interceptor & Session Management
 * Handles server restarts, network errors, and session expirations gracefully.
 */
(function() {
    const originalFetch = window.fetch;
    let isSessionAlertOpen = false;

    window.fetch = async (...args) => {
        try {
            const response = await originalFetch(...args);

            // 1. Handle Session Expiration / Redirect to Login
            // If the response URL points to the login page but the original request wasn't for login,
            // or if the status is 401 Unauthorized with an HTML response (Spring Security redirect).
            const isLoginPage = response.url && (response.url.includes('/login') || response.url.endsWith('/login'));
            const isApiRequest = args[0] && (typeof args[0] === 'string' ? args[0].includes('/api/') : args[0].url.includes('/api/'));
            
            const contentType = response.headers.get('content-type');
            const isHtml = contentType && contentType.includes('text/html');

            if (isLoginPage && isHtml && isApiRequest && !isSessionAlertOpen) {
                isSessionAlertOpen = true;
                Swal.fire({
                    title: 'Session Expired',
                    text: 'Your session has timed out. Please login again to continue your work.',
                    icon: 'warning',
                    showCancelButton: false,
                    confirmButtonText: 'Login Now',
                    confirmButtonColor: '#3b82f6',
                    allowOutsideClick: false,
                    allowEscapeKey: false
                }).then(() => {
                    window.location.href = '/login?expired=true';
                });
                
                // Return a rejected promise to stop downstream data processing
                return Promise.reject(new Error('Session expired'));
            }

            return response;
        } catch (error) {
            // 2. Handle Server Down / Restart (Network Error)
            // TypeError usually indicates a network failure in fetch
            const isNetworkError = error instanceof TypeError || 
                                  (error.message && error.message.toLowerCase().includes('network'));
            
            if (isNetworkError && !isSessionAlertOpen) {
                isSessionAlertOpen = true;
                Swal.fire({
                    title: 'Server Connection Lost',
                    text: 'Unable to reach the server. It might be restarting or there might be a network issue.',
                    icon: 'error',
                    showCancelButton: true,
                    confirmButtonText: 'Retry / Refresh',
                    cancelButtonText: 'Wait',
                    confirmButtonColor: '#ef4444',
                    allowOutsideClick: false,
                }).then((result) => {
                    isSessionAlertOpen = false;
                    if (result.isConfirmed) {
                        window.location.reload();
                    }
                });
                
                return Promise.reject(new Error('Server unreachable'));
            }
            throw error;
        }
    };
})();

document.addEventListener('DOMContentLoaded', function() {

    // ========================================
    // VARIABLES
    // ========================================

    const sidebar = document.getElementById('sidebar');
    const sidebarOverlay = document.getElementById('sidebarOverlay');
    const hamburger = document.getElementById('hamburger');
    const sidebarToggle = document.getElementById('sidebarToggle');
    const mainWrapper = document.getElementById('mainWrapper');
    const pageTitle = document.getElementById('pageTitle');

    // ========================================
    // PERMISSION-BASED ACCESS CONTROL
    // ========================================

    // Hide menu items user doesn't have access to
    if (typeof userPermissions !== 'undefined' && Array.isArray(userPermissions)) {
        document.querySelectorAll('[data-menu-id]').forEach(item => {
            const menuId = parseInt(item.getAttribute('data-menu-id'));
            const hasAccess = userPermissions.includes(menuId);

            if (!hasAccess) {
                item.style.display = 'none';
            }
        });
    }

    function checkMenuAccess(menuId) {
        if (typeof userPermissions === 'undefined') return true;

        const hasAccess = userPermissions.includes(parseInt(menuId));

        if (!hasAccess) {
            Swal.fire({
                icon: 'error',
                title: ' Access Denied',
                html: `
                    <div style="text-align: center;">
                        <p style="font-size: 16px; color: #dc2626; margin-bottom: 10px;">
                            You don't have permission to access this page.
                        </p>
                        <p style="font-size: 14px; color: #64748b;">
                            This security violation has been logged.
                        </p>
                    </div>
                `,
                confirmButtonText: 'Return to Dashboard',
                confirmButtonColor: '#2563eb',
                allowOutsideClick: false,
                allowEscapeKey: false
            }).then(() => {
                window.location.href = '/access-denied';
            });
        }

        return hasAccess;
    }

    // Intercept all menu link clicks for permission checking
    const protectedLinks = document.querySelectorAll('[data-menu-id]');

    protectedLinks.forEach(link => {
        link.addEventListener('click', function(e) {
            const menuId = this.getAttribute('data-menu-id');

            if (menuId) {
                if (!checkMenuAccess(menuId)) {
                    e.preventDefault();
                    e.stopPropagation();
                    e.stopImmediatePropagation();
                    return false;
                }
            }
        }, true);
    });

    // Note: Direct URL-based access control is now handled by the
    // blur overlay in layout.html via currentMenuId + userPermissions check.
    // No redirect needed here — the page blurs itself automatically.

    // ========================================
    // SIDEBAR COLLAPSE (DESKTOP ONLY)
    // ========================================



    if (sidebarToggle) {
        sidebarToggle.addEventListener('click', function (e) {
            e.preventDefault();

            // Only work on desktop
            if (window.innerWidth < 992) {
                return;
            }

            const isCollapsed = sidebar.classList.toggle('collapsed');
            mainWrapper.classList.toggle('collapsed');

            // Save state
            localStorage.setItem('sidebarCollapsed', isCollapsed);

            // Close all submenus when collapsing
            if (isCollapsed) {
                document.querySelectorAll('.submenu').forEach(sub => {
                    sub.classList.remove('open');
                });
                document.querySelectorAll('.menu-link[data-toggle="submenu"]').forEach(link => {
                    link.classList.remove('expanded');
                });
            }
        });
    }

       // ========================================
       // MOBILE SIDEBAR TOGGLE
       //========================================
                 function toggleSidebar() {
              const isActive = sidebar.classList.toggle('active');
              sidebarOverlay.classList.toggle('active');
              document.body.style.overflow = isActive ? 'hidden' : '';

              // Toggle chevron icon direction on mobile
              const hamburgerIcon = hamburger ? hamburger.querySelector('i') : null;
              if (hamburgerIcon) {
                  if (isActive) {
                      hamburgerIcon.className = 'bi bi-chevron-left';
                  } else {
                      hamburgerIcon.className = 'bi bi-chevron-right';
                  }
              }
          }

         if (hamburger) {
             hamburger.addEventListener('click', toggleSidebar);
         }

         if (sidebarOverlay) {
             sidebarOverlay.addEventListener('click', toggleSidebar);
         }

         // Restore collapsed state on page load (desktop only)
         if (window.innerWidth >= 992) {
             const collapsedState = localStorage.getItem('sidebarCollapsed');
             if (collapsedState === 'false') {
                 sidebar.classList.remove('collapsed');
                 mainWrapper.classList.remove('collapsed');
             } else if (collapsedState === null) {
                 localStorage.setItem('sidebarCollapsed', 'true');
                 sidebar.classList.add('collapsed');
                 mainWrapper.classList.add('collapsed');
             } else if (collapsedState === 'true') {
                 sidebar.classList.add('collapsed');
                 mainWrapper.classList.add('collapsed');
             }
         }

         // ========================================
         // WINDOW RESIZE HANDLER
         // ========================================

         window.addEventListener('resize', function() {
             // Close mobile sidebar when resizing to desktop
             if (window.innerWidth >= 992 && sidebar.classList.contains('active')) {
                 sidebar.classList.remove('active');
                 sidebarOverlay.classList.remove('active');
                 document.body.style.overflow = '';
             }

             // Remove collapsed state on mobile/tablet
             if (window.innerWidth < 992) {
                 sidebar.classList.remove('collapsed');
                 mainWrapper.classList.remove('collapsed');
             } else {
                 // Restore collapsed state on desktop
                 const isCollapsed = localStorage.getItem('sidebarCollapsed') === 'true';
                 if (isCollapsed) {
                     sidebar.classList.add('collapsed');
                     mainWrapper.classList.add('collapsed');
                 }
             }
         });

         // ========================================
         // SUBMENU TOGGLE
         // ========================================

         const menuToggles = document.querySelectorAll('[data-toggle="submenu"]');

         menuToggles.forEach(toggle => {
             toggle.addEventListener('click', function(e) {
                 e.preventDefault();
                 e.stopPropagation();

                 // Don't allow submenu toggle when sidebar is collapsed on desktop
                 // unless sidebar is being hovered
                 if (window.innerWidth >= 992 &&
                     sidebar.classList.contains('collapsed') &&
                     !sidebar.matches(':hover')) {
                     return;
                 }

                 const submenu = this.nextElementSibling;
                 const isOpen = submenu.classList.contains('open');

                 // Close all other submenus
                 document.querySelectorAll('.submenu').forEach(sub => {
                     if (sub !== submenu) {
                         sub.classList.remove('open');
                     }
                 });

                 document.querySelectorAll('.menu-link[data-toggle="submenu"]').forEach(link => {
                     if (link !== this) {
                         link.classList.remove('expanded');
                     }
                 });

                 // Toggle current submenu
                 if (!isOpen) {
                     submenu.classList.add('open');
                     this.classList.add('expanded');
                 } else {
                     submenu.classList.remove('open');
                     this.classList.remove('expanded');
                 }
             });
         });

         // Handle submenu expansion when hovering over collapsed sidebar
         let hoverTimeout;

         sidebar.addEventListener('mouseenter', function() {
             if (window.innerWidth >= 992 && this.classList.contains('collapsed')) {
                 clearTimeout(hoverTimeout);
                 hoverTimeout = setTimeout(() => {
                     // Allow interactions when hovering
                 }, 100);
             }
         });

         sidebar.addEventListener('mouseleave', function() {
             if (window.innerWidth >= 992 && this.classList.contains('collapsed')) {
                 clearTimeout(hoverTimeout);
                 // Close submenus when mouse leaves
                 document.querySelectorAll('.submenu').forEach(sub => {
                     sub.classList.remove('open');
                 });
                 document.querySelectorAll('.menu-link[data-toggle="submenu"]').forEach(link => {
                     link.classList.remove('expanded');
                 });
             }
         });

         // ========================================
         // MENU NAVIGATION
         // ========================================

         const allMenuLinks = document.querySelectorAll('.menu-link:not([data-toggle]), .submenu-link');

         const pageTitles = {
             'dashboard': 'Dashboard',
             'enquiry': 'Student Enquiry',
             'admission': 'Student Admission',
             'fees-manager': 'Fees Manager',
             'certificate': 'Certificate Management',
             'course': 'Course Master',
             'batch': 'Batch Master',
             'employee': 'Employee Master',
             'role': 'Role Management',
             'bank': 'Bank Master',
             'lead-source': 'Lead Source',
             'create-package': 'Create Package',
             'online-payment': 'Online Payment Mode',
             'course-wise-sales': 'Course-wise Sales Report',
             'fees-collection': 'Fees Collection Report'
         };

         allMenuLinks.forEach(link => {
             link.addEventListener('click', function(e) {

                 const page = this.getAttribute('data-page');

                 // Remove active from all links
                 document.querySelectorAll('.menu-link').forEach(l => {
                     if (!l.hasAttribute('data-toggle')) {
                         l.classList.remove('active');
                     }
                 });
                 document.querySelectorAll('.submenu-link').forEach(l => {
                     l.classList.remove('active');
                 });

                 // Add active to clicked link
                 this.classList.add('active');

                 // Expand parent submenu if needed
                 if (this.classList.contains('submenu-link')) {
                     const parentSubmenu = this.closest('.submenu');
                     const parentToggle = parentSubmenu.previousElementSibling;

                     if (parentToggle && !parentSubmenu.classList.contains('open')) {
                         parentToggle.click();
                     }
                 }

                 // Update page title
                 if (page && pageTitles[page]) {
                     pageTitle.textContent = pageTitles[page];
                 }

                 // Close mobile sidebar after navigation
                 if (window.innerWidth < 992 && sidebar.classList.contains('active')) {
                     toggleSidebar();
                 }
             });
         });

         // ========================================
         // CHARTS
         // ========================================

         const lineCtx = document.getElementById('lineChart');
         if (lineCtx) {
             new Chart(lineCtx, {
                 type: 'line',
                 data: {
                     labels: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
                     datasets: [{
                         label: 'Revenue (₹ in Lakhs)',
                         data: [3.2, 3.8, 4.1, 3.9, 4.5, 4.8, 5.2, 4.9, 5.1, 5.5, 5.8, 6.2],
                         borderColor: 'rgb(59, 130, 246)',
                         backgroundColor: 'rgba(59, 130, 246, 0.1)',
                         tension: 0.4,
                         fill: true,
                         pointBackgroundColor: 'rgb(59, 130, 246)',
                         pointBorderColor: '#fff',
                         pointBorderWidth: 2,
                         pointRadius: 4,
                         pointHoverRadius: 6
                     }]
                 },
                 options: {
                     responsive: true,
                     maintainAspectRatio: false,
                     plugins: {
                         legend: {
                             display: true,
                             position: 'top',
                         },
                         tooltip: {
                             mode: 'index',
                             intersect: false,
                         }
                     },
                     scales: {
                         y: {
                             beginAtZero: true,
                             ticks: {
                                 callback: function(value) {
                                     return '₹' + value + 'L';
                                 }
                             }
                         }
                     }
                 }
             });
         }

         const pieCtx = document.getElementById('pieChart');
         if (pieCtx) {
             new Chart(pieCtx, {
                 type: 'pie',
                 data: {
                     labels: ['Web Development', 'Data Science', 'Digital Marketing', 'App Development', 'Graphic Design', 'Others'],
                     datasets: [{
                         data: [30, 25, 15, 12, 10, 8],
                         backgroundColor: [
                             'rgba(59, 130, 246, 0.8)',
                             'rgba(16, 185, 129, 0.8)',
                             'rgba(245, 158, 11, 0.8)',
                             'rgba(139, 92, 246, 0.8)',
                             'rgba(236, 72, 153, 0.8)',
                             'rgba(107, 114, 128, 0.8)'
                         ],
                         borderColor: '#fff',
                         borderWidth: 2
                     }]
                 },
                 options: {
                     responsive: true,
                     maintainAspectRatio: false,
                     plugins: {
                         legend: {
                             position: 'bottom',
                             labels: {
                                 padding: 15,
                                 usePointStyle: true
                             }
                         },
                         tooltip: {
                             callbacks: {
                                 label: function(context) {
                                     const label = context.label || '';
                                     const value = context.parsed || 0;
                                     return label + ': ' + value + '%';
                                 }
                             }
                         }
                     }
                 }
             });
         }

         // ========================================
         // LOGOUT FUNCTIONALITY
         // ========================================

         const logoutBtn = document.getElementById('logoutBtn');
         const sidebarLogout = document.getElementById('sidebarLogout');
         const logoutForm = document.getElementById('logoutForm');

         function handleLogout(e) {
             e.preventDefault();

             Swal.fire({
                 title: 'Logout Confirmation',
                 text: 'Are you sure you want to logout?',
                 icon: 'question',
                 showCancelButton: true,
                 confirmButtonColor: '#2563eb',
                 cancelButtonColor: '#64748b',
                 confirmButtonText: 'Yes, logout',
                 cancelButtonText: 'Cancel'
             }).then((result) => {
                 if (result.isConfirmed) {
                     Swal.fire({
                         title: 'Logging out...',
                         allowOutsideClick: false,
                         showConfirmButton: false,
                         didOpen: () => {
                             Swal.showLoading();
                         }
                     });

                     setTimeout(() => {
                         if (logoutForm) {
                             logoutForm.submit();
                         } else {
                             window.location.href = '/logout';
                         }
                     }, 500);
                 }
             });
         }

         if (logoutBtn) {
             logoutBtn.addEventListener('click', handleLogout);
         }

         if (sidebarLogout) {
             sidebarLogout.addEventListener('click', handleLogout);
         }

         // ========================================
         // NOTIFICATION SYSTEM
         // ========================================

         function showNotification(message, type = 'success') {
             const Toast = Swal.mixin({
                 toast: true,
                 position: 'top-end',
                 showConfirmButton: false,
                 timer: 3000,
                 timerProgressBar: true,
                 didOpen: (toast) => {
                     toast.addEventListener('mouseenter', Swal.stopTimer);
                     toast.addEventListener('mouseleave', Swal.resumeTimer);
                 }
             });

             Toast.fire({
                 icon: type,
                 title: message
             });
         }

         const notificationBtn = document.querySelector('.notification-btn');

         if (notificationBtn) {
             notificationBtn.addEventListener('click', function() {
                 Swal.fire({
                     title: 'Notifications',
                     html: `
                         <div class="text-start">
                             <div class="border-bottom pb-3 mb-3">
                                 <h6 class="mb-1">New Enquiry Received</h6>
                                 <small class="text-muted">1 year 9 months ago</small>
                                 <p class="mb-0 mt-2 small">
                                     A new student enquiry for <strong>Python Backend Development</strong>.<br>
                                     <strong>Name:</strong> Kunal Patil<br>
                                     <strong>Contact:</strong> 7020268464<br>
                                     <strong>Course:</strong> Full Stack Frontend + Python Django<br>
                                     <strong>Source:</strong> Google<br>
                                     <strong>Enquiry Date:</strong> 2024-02-07
                                 </p>
                             </div>
                             <div class="border-bottom pb-3 mb-3">
                                 <h6 class="mb-1">Fee Payment Received</h6>
                                 <small class="text-muted">1 year 6 months ago</small>
                                 <p class="mb-0 mt-2 small">
                                     Fee payment received from <strong>Kunal Patil</strong>.<br>
                                     <strong>Date:</strong> 20-05-2024
                                 </p>
                             </div>
                             <div class="pb-2">
                                 <h6 class="mb-1">Certificate Ready</h6>
                                 <small class="text-muted">5 days ago</small>
                                 <p class="mb-0 mt-2 small">1 new certificates are ready for printing.</p>
                             </div>
                         </div>
                     `,
                     confirmButtonText: 'Close',
                     confirmButtonColor: '#2563eb',
                     customClass: {
                         popup: 'rounded-3',
                         confirmButton: 'rounded-2'
                     },
                     width: '500px'
                 });
             });
         }
});

         // ========================================
         // LOGOUT FUNCTIONALITY
         // ========================================

         (function() {
             'use strict';

             function initializeDropdowns() {
                 const dropdowns = document.querySelectorAll('[data-bs-toggle="dropdown"]');

                 dropdowns.forEach(element => {
                     const existingDropdown = bootstrap.Dropdown.getInstance(element);
                     if (existingDropdown) {
                         existingDropdown.dispose();
                     }

                     new bootstrap.Dropdown(element, {
                         boundary: 'window',
                         popperConfig: {
                             strategy: 'fixed'
                         }
                     });
                 });
             }

             initializeDropdowns();

             function observeContentChanges() {
                 const mainContent = document.getElementById('mainContent');

                 if (!mainContent) {
                     return;
                 }

                 const observer = new MutationObserver(function(mutations) {
                     let shouldReinit = false;

                     mutations.forEach(function(mutation) {
                         if (mutation.addedNodes.length > 0) {
                             mutation.addedNodes.forEach(function(node) {
                                 if (node.nodeType === 1) {
                                     const hasDropdown = node.querySelector?.('[data-bs-toggle="dropdown"]') ||
                                         node.matches?.('[data-bs-toggle="dropdown"]');
                                     if (hasDropdown) {
                                         shouldReinit = true;
                                     }
                                 }
                             });
                         }
                     });

                     if (shouldReinit) {
                         setTimeout(initializeDropdowns, 50);
                     }
                 });

                 observer.observe(mainContent, {
                     childList: true,
                     subtree: true
                 });
             }

             observeContentChanges();

             function handleNavigation() {
                 const menuLinks = document.querySelectorAll('.menu-link, .submenu-link');

                 menuLinks.forEach(link => {
                     link.addEventListener('click', function() {
                         setTimeout(initializeDropdowns, 200);
                     });
                 });
             }

             handleNavigation();

             window.reinitBootstrapDropdowns = function() {
                 initializeDropdowns();
             };

             document.addEventListener('click', function(e) {
                 if (!e.target.closest('.dropdown')) {
                     document.querySelectorAll('.dropdown-menu.show').forEach(menu => {
                         menu.classList.remove('show');
                     });
                 }
             });

         })();

// ========================================
// USER MENU DROPDOWN
// ========================================


(function() {
    'use strict';
    // Wait for DOM to be fully loaded
    function initUserDropdown() {
        const userMenu = document.querySelector('.user-menu');
        const dropdownMenu = document.querySelector('.dropdown-menu.dropdown-menu-end');
        const dropdown = document.querySelector('.topbar-right .dropdown');

        if (!userMenu || !dropdownMenu) {
            setTimeout(initUserDropdown, 100);
            return;
        }

        // Step 1: Remove Bootstrap's data-bs-toggle to prevent auto-initialization
        userMenu.removeAttribute('data-bs-toggle');
        userMenu.removeAttribute('aria-expanded');

        // Step 2: Destroy any existing Bootstrap dropdown instance
        if (dropdown) {
            const bsDropdown = bootstrap.Dropdown.getInstance(userMenu);
            if (bsDropdown) {
                bsDropdown.dispose();
            }
        }

        // Step 3: Add our custom click handler
        userMenu.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();

            const isCurrentlyOpen = dropdownMenu.classList.contains('show');

            // Close all other dropdowns first
            document.querySelectorAll('.dropdown-menu.show').forEach(menu => {
                if (menu !== dropdownMenu) {
                    menu.classList.remove('show');
                }
            });

            // Toggle current dropdown
            if (isCurrentlyOpen) {
                dropdownMenu.classList.remove('show');
                userMenu.setAttribute('aria-expanded', 'false');
            } else {
                dropdownMenu.classList.add('show');
                userMenu.setAttribute('aria-expanded', 'true');
            }
        });

        // Step 4: Close dropdown when clicking outside
        document.addEventListener('click', function(e) {
            if (!dropdown.contains(e.target)) {
                dropdownMenu.classList.remove('show');
                userMenu.setAttribute('aria-expanded', 'false');
            }
        });

        // Step 5: Prevent dropdown from closing when clicking inside menu
        dropdownMenu.addEventListener('click', function(e) {
            // Only prevent default if not clicking logout
            if (!e.target.closest('#logoutBtn')) {
                e.stopPropagation();
            }
        });
    }

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initUserDropdown);
    } else {
        initUserDropdown();
    }

})();

// ========================================
// PROFILE & SETTINGS MODALS
// ========================================

(function() {
    'use strict';

    const API_BASE_URL = '/api/employees';

    // Get CSRF token
    function getCsrfToken() {
        const csrfMeta = document.querySelector('meta[name="_csrf"]');
        if (csrfMeta) {
            return csrfMeta.getAttribute('content');
        }
        const csrfCookie = document.cookie
            .split('; ')
            .find(row => row.startsWith('XSRF-TOKEN='));
        return csrfCookie ? decodeURIComponent(csrfCookie.split('=')[1]) : null;
    }

    function getCsrfHeaders() {
        const token = getCsrfToken();
        const headerName = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN';
        return token ? { [headerName]: token } : {};
    }

    // Initialize modal handlers
    function initializeProfileSettings() {
        // Wait for dropdown menu to be available
        setTimeout(() => {
            // Create modals if they don't exist
            if (!document.getElementById('profileModal')) {
                createProfileModal();
            }
            if (!document.getElementById('settingsModal')) {
                createSettingsModal();
            }

            // Attach event listeners to dropdown items
            const dropdownItems = document.querySelectorAll('.dropdown-item');
            dropdownItems.forEach(item => {
                const icon = item.querySelector('i');

                if (icon && icon.classList.contains('bi-person')) {
                    // My Profile
                    item.addEventListener('click', function(e) {
                        e.preventDefault();
                        openProfileModal();
                    });
                } else if (icon && icon.classList.contains('bi-gear')) {
                    // Settings
                    item.addEventListener('click', function(e) {
                        e.preventDefault();
                        openSettingsModal();
                    });
                }
            });
        }, 500);
    }

    // Create Profile Modal
    function createProfileModal() {
        const modalHTML = `
            <div class="modal fade" id="profileModal" tabindex="-1" data-bs-backdrop="static">
                <div class="modal-dialog modal-lg">
                    <div class="modal-content">
                        <div class="modal-header bg-primary text-white">
                            <h5 class="modal-title">
                                <i class="bi bi-person-circle me-2"></i>My Profile
                            </h5>
                            <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal"></button>
                        </div>
                        <div class="modal-body">
                            <div id="profileContent">
                                <div class="text-center py-4">
                                    <div class="spinner-border text-primary" role="status"></div>
                                    <p class="mt-2">Loading profile...</p>
                                </div>
                            </div>
                        </div>
                        <div class="modal-footer">
                            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
                        </div>
                    </div>
                </div>
            </div>
        `;
        document.body.insertAdjacentHTML('beforeend', modalHTML);
    }

    // Create Settings Modal
    function createSettingsModal() {
        const currentYear = new Date().getFullYear();
        const modalHTML = `
            <div class="modal fade" id="settingsModal" tabindex="-1" data-bs-backdrop="static">
                <div class="modal-dialog modal-lg">
                    <div class="modal-content">
                        <div class="modal-header bg-info text-white">
                            <h5 class="modal-title">
                                <i class="bi bi-gear me-2"></i>Settings
                            </h5>
                            <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal"></button>
                        </div>
                        <div class="modal-body">
                            <div class="settings-content">
                                <div class="mb-4">
                                    <h6 class="text-primary fw-bold">
                                        <i class="bi bi-info-circle me-2"></i>About TechnoKraft CRM
                                    </h6>
                                    <p class="text-muted">
                                        TechnoKraft Student Management System is a comprehensive CRM solution
                                        designed to streamline educational institute operations including student
                                        admissions, enquiries, fee management, and more.
                                    </p>
                                </div>

                                <div class="mb-4">
                                    <h6 class="text-primary fw-bold">
                                        <i class="bi bi-list-check me-2"></i>Current Features
                                    </h6>
                                    <ul class="list-unstyled">
                                        <li class="mb-2">
                                            <i class="bi bi-check-circle-fill text-success me-2"></i>
                                            Student Enquiry Management
                                        </li>
                                        <li class="mb-2">
                                            <i class="bi bi-check-circle-fill text-success me-2"></i>
                                            Admission Processing
                                        </li>
                                        <li class="mb-2">
                                            <i class="bi bi-check-circle-fill text-success me-2"></i>
                                            Fee Collection & Tracking
                                        </li>
                                        <li class="mb-2">
                                            <i class="bi bi-check-circle-fill text-success me-2"></i>
                                            Certificate Generation
                                        </li>
                                        <li class="mb-2">
                                            <i class="bi bi-check-circle-fill text-success me-2"></i>
                                            Employee & Role Management
                                        </li>
                                        <li class="mb-2">
                                            <i class="bi bi-check-circle-fill text-success me-2"></i>
                                            Course & Batch Management
                                        </li>
                                    </ul>
                                </div>

                                <div class="mb-4">
                                    <h6 class="text-warning fw-bold">
                                        <i class="bi bi-clock-history me-2"></i>Upcoming Features
                                    </h6>
                                    <ul class="list-unstyled">
                                        <li class="mb-2">
                                            <i class="bi bi-hourglass-split text-warning me-2"></i>
                                            Advanced Reporting & Analytics - <span class="badge bg-warning text-dark">Coming Soon</span>
                                        </li>
                                        <li class="mb-2">
                                            <i class="bi bi-hourglass-split text-warning me-2"></i>
                                            SMS & Email Automation - <span class="badge bg-warning text-dark">Coming Soon</span>
                                        </li>
                                        <li class="mb-2">
                                            <i class="bi bi-hourglass-split text-warning me-2"></i>
                                            Online Exam Module - <span class="badge bg-warning text-dark">Coming Soon</span>
                                        </li>
                                        <li class="mb-2">
                                            <i class="bi bi-hourglass-split text-warning me-2"></i>
                                            Student Mobile App - <span class="badge bg-warning text-dark">Coming Soon</span>
                                        </li>
                                        <li class="mb-2">
                                            <i class="bi bi-hourglass-split text-warning me-2"></i>
                                            Attendance Management - <span class="badge bg-warning text-dark">Coming Soon</span>
                                        </li>
                                        <li class="mb-2">
                                            <i class="bi bi-hourglass-split text-warning me-2"></i>
                                            Payment Gateway Integration - <span class="badge bg-warning text-dark">Coming Soon</span>
                                        </li>
                                    </ul>
                                </div>

                                <div class="alert alert-info">
                                    <i class="bi bi-info-circle me-2"></i>
                                    <strong>Note:</strong> New features are being actively developed and will be
                                    implemented soon. Stay tuned for updates!
                                </div>

                                <div class="mt-4 text-center">
                                    <p class="text-muted small mb-0">
                                        <i class="bi bi-c-circle me-1"></i>
                                        ${currentYear} TechnoKraft Training & Solution PVT LTD
                                    </p>
                                    <p class="text-muted small">Version 1.0.0</p>
                                </div>
                            </div>
                        </div>
                        <div class="modal-footer">
                            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
                        </div>
                    </div>
                </div>
            </div>
        `;
        document.body.insertAdjacentHTML('beforeend', modalHTML);
    }

    // Open Profile Modal
    async function openProfileModal() {
        const modalElement = document.getElementById('profileModal');
        const modal = new bootstrap.Modal(modalElement);
        modal.show();

        try {
            const response = await fetch(`${API_BASE_URL}/current-user`, {
                headers: {
                    'Accept': 'application/json',
                    ...getCsrfHeaders()
                }
            });

            if (!response.ok) {
                throw new Error('Failed to load profile data');
            }

            const employee = await response.json();
            displayProfile(employee);

        } catch (error) {
            document.getElementById('profileContent').innerHTML = `
                <div class="alert alert-danger">
                    <i class="bi bi-exclamation-triangle me-2"></i>
                    Failed to load profile data. Please try again.
                </div>
            `;
        }
    }

    // Display Profile Data
    function displayProfile(employee) {
        const photoUrl = employee.photoUrl || '/assets/images/default-user.png';

        const profileHTML = `
            <div class="container-fluid">
                <div class="row g-4">
                    <div class="col-md-4 text-center">
                        <img src="${photoUrl}"
                             alt="Profile Photo"
                             class="img-fluid rounded-circle mb-3"
                             style="width: 150px; height: 150px; object-fit: cover; border: 4px solid #3b82f6;"
                             onerror="this.src='/assets/images/default-user.png'">
                        <h5 class="mb-1">${employee.employeeName}</h5>
                        <p class="text-muted mb-2">${employee.roleName || 'N/A'}</p>
                        <span class="badge ${employee.isActive ? 'bg-success' : 'bg-secondary'}">
                            ${employee.isActive ? 'Active' : 'Inactive'}
                        </span>
                    </div>
                    <div class="col-md-8">
                        <h6 class="text-primary fw-bold mb-3">
                            <i class="bi bi-person-lines-fill me-2"></i>Personal Information
                        </h6>
                        <table class="table table-hover table-sm" style="table-layout: fixed; width: 100%;">
                            <tbody>
                                <tr>
                                    <td class="text-muted" style="width: 35%;">
                                        <i class="bi bi-envelope me-2"></i>Email
                                    </td>
                                    <td class="fw-semibold">${employee.emailId}</td>
                                </tr>
                                <tr>
                                    <td class="text-muted">
                                        <i class="bi bi-phone me-2"></i>Mobile
                                    </td>
                                    <td class="fw-semibold">${employee.mobileNumber}</td>
                                </tr>
                                <tr>
                                    <td class="text-muted">
                                        <i class="bi bi-briefcase me-2"></i>Designation
                                    </td>
                                    <td class="fw-semibold">${employee.designation || 'N/A'}</td>
                                </tr>
                                <tr>
                                    <td class="text-muted">
                                        <i class="bi bi-gender-ambiguous me-2"></i>Gender
                                    </td>
                                    <td class="fw-semibold">${formatGender(employee.gender)}</td>
                                </tr>
                                <tr>
                                    <td class="text-muted">
                                        <i class="bi bi-calendar me-2"></i>Date of Birth
                                    </td>
                                    <td class="fw-semibold">${formatDate(employee.dateOfBirth)}</td>
                                </tr>
                                <tr>
                                    <td class="text-muted">
                                        <i class="bi bi-geo-alt me-2"></i>Address
                                    </td>
                                    <td class="fw-semibold">${employee.address || 'N/A'}</td>
                                </tr>
                                ${employee.zoomLink ? `
                                <tr>
                                    <td class="text-muted">
                                        <i class="bi bi-camera-video me-2"></i>Zoom Link
                                    </td>
                                    <td>
                                        <a href="${employee.zoomLink}" target="_blank" class="text-primary">
                                            Join Meeting <i class="bi bi-box-arrow-up-right ms-1"></i>
                                        </a>
                                    </td>
                                </tr>
                                ` : ''}
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        `;

        document.getElementById('profileContent').innerHTML = profileHTML;
    }

    // Helper Functions
    function formatDate(dateString) {
        if (!dateString) return 'N/A';
        try {
            const date = new Date(dateString);
            return date.toLocaleDateString('en-IN', {
                year: 'numeric',
                month: 'long',
                day: 'numeric'
            });
        } catch (e) {
            return dateString;
        }
    }

    function formatGender(gender) {
        const genderMap = {
            'M': 'Male',
            'F': 'Female',
            'O': 'Other'
        };
        return genderMap[gender] || 'N/A';
    }

    // Open Settings Modal
    function openSettingsModal() {
        const modalElement = document.getElementById('settingsModal');
        const modal = new bootstrap.Modal(modalElement);
        modal.show();
    }

    // Initialize on page load
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initializeProfileSettings);
    } else {
        initializeProfileSettings();
    }

    // Export for external use
    window.openProfileModal = openProfileModal;
    window.openSettingsModal = openSettingsModal;
})();

// ========================================
// RESET PASSWORD MODULE
// ========================================

(function() {
    'use strict';

    // Wait for DOM to be fully loaded
    function initResetPassword() {
        const resetPasswordBtn = document.getElementById('resetPasswordBtn');
        const submitBtn = document.getElementById('submitResetPassword');
        const newPasswordInput = document.getElementById('newPassword');
        const confirmPasswordInput = document.getElementById('confirmPassword');

        if (!resetPasswordBtn) {
            setTimeout(initResetPassword, 100);
            return;
        }

        // Open modal
        resetPasswordBtn.addEventListener('click', function(e) {
            e.preventDefault();

            const modal = new bootstrap.Modal(document.getElementById('resetPasswordModal'));
            modal.show();

            // Reset form when opening
            resetForm();
        });

        // Real-time password strength check
        if (newPasswordInput) {
            newPasswordInput.addEventListener('input', function() {
                checkPasswordStrength(this.value);
                validatePasswordMatch();
            });
        }

        // Real-time confirm password check
        if (confirmPasswordInput) {
            confirmPasswordInput.addEventListener('input', validatePasswordMatch);
        }

        // Submit form
        if (submitBtn) {
            submitBtn.addEventListener('click', handlePasswordReset);
        }
    }

    // Toggle password visibility
    window.togglePassword = function(fieldId) {
        const field = document.getElementById(fieldId);
        const icon = document.getElementById(fieldId + '-icon');

        if (field.type === 'password') {
            field.type = 'text';
            icon.classList.remove('bi-eye');
            icon.classList.add('bi-eye-slash');
        } else {
            field.type = 'password';
            icon.classList.remove('bi-eye-slash');
            icon.classList.add('bi-eye');
        }
    };

    // Check password strength
    function checkPasswordStrength(password) {
        const strengthBadge = document.getElementById('passwordStrength');

        if (!password) {
            strengthBadge.textContent = 'Not Set';
            strengthBadge.className = 'badge bg-secondary';
            resetRequirements();
            return;
        }

        let strength = 0;
        const requirements = {
            length: password.length >= 8,
            uppercase: /[A-Z]/.test(password),
            lowercase: /[a-z]/.test(password),
            number: /\d/.test(password),
            special: /[!@#$%^&*(),.?":{}|<>]/.test(password)
        };

        // Update requirement indicators
        updateRequirement('req-length', requirements.length);
        updateRequirement('req-uppercase', requirements.uppercase);
        updateRequirement('req-lowercase', requirements.lowercase);
        updateRequirement('req-number', requirements.number);
        updateRequirement('req-special', requirements.special);

        // Calculate strength
        Object.values(requirements).forEach(met => {
            if (met) strength++;
        });

        // Update badge
        if (strength === 5) {
            strengthBadge.textContent = 'Strong';
            strengthBadge.className = 'badge bg-success';
        } else if (strength >= 3) {
            strengthBadge.textContent = 'Medium';
            strengthBadge.className = 'badge bg-warning text-dark';
        } else {
            strengthBadge.textContent = 'Weak';
            strengthBadge.className = 'badge bg-danger';
        }
    }

    // Update requirement indicator
    function updateRequirement(reqId, met) {
        const element = document.getElementById(reqId);
        const icon = element.querySelector('i');

        if (met) {
            icon.className = 'bi bi-check-circle-fill text-success';
        } else {
            icon.className = 'bi bi-circle text-muted';
        }
    }

    // Reset requirement indicators
    function resetRequirements() {
        ['req-length', 'req-uppercase', 'req-lowercase', 'req-number', 'req-special'].forEach(reqId => {
            const element = document.getElementById(reqId);
            const icon = element.querySelector('i');
            icon.className = 'bi bi-circle text-muted';
        });
    }

    // Validate password match
    function validatePasswordMatch() {
        const newPassword = document.getElementById('newPassword').value;
        const confirmPassword = document.getElementById('confirmPassword').value;
        const errorDiv = document.getElementById('confirmPassword-error');
        const confirmField = document.getElementById('confirmPassword');

        if (confirmPassword && newPassword !== confirmPassword) {
            confirmField.classList.add('is-invalid');
            errorDiv.textContent = 'Passwords do not match';
        } else {
            confirmField.classList.remove('is-invalid');
            errorDiv.textContent = '';
        }
    }

    // Handle password reset
    async function handlePasswordReset() {
        const currentPassword = document.getElementById('currentPassword').value;
        const newPassword = document.getElementById('newPassword').value;
        const confirmPassword = document.getElementById('confirmPassword').value;

        // Clear previous errors
        clearErrors();

        // Validate fields
        if (!currentPassword || !newPassword || !confirmPassword) {
            showError('All fields are required');
            return;
        }

        if (newPassword !== confirmPassword) {
            showFieldError('confirmPassword', 'Passwords do not match');
            return;
        }

        // Check password strength
        const isStrong = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[!@#$%^&*(),.?":{}|<>]).{8,}$/.test(newPassword);
        if (!isStrong) {
            showFieldError('newPassword', 'Password does not meet security requirements');
            return;
        }

        // Get CSRF token
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');

        const submitBtn = document.getElementById('submitResetPassword');
        submitBtn.disabled = true;
        submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Resetting...';

        try {
            const response = await fetch('/api/auth/reset-password', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    [csrfHeader]: csrfToken
                },
                body: JSON.stringify({
                    currentPassword,
                    newPassword,
                    confirmPassword
                })
            });

            const data = await response.json();

            if (response.ok) {
                // Success
                const modal = bootstrap.Modal.getInstance(document.getElementById('resetPasswordModal'));
                modal.hide();

                Swal.fire({
                    icon: 'success',
                    title: 'Password Reset Successful',
                    html: `
                        <p>${data.message}</p>
                        <p class="text-muted small">You will be redirected to login page in 3 seconds...</p>
                    `,
                    timer: 3000,
                    showConfirmButton: false,
                    allowOutsideClick: false
                }).then(() => {
                    // Logout and redirect
                    const logoutForm = document.getElementById('logoutForm');
                    if (logoutForm) {
                        logoutForm.submit();
                    } else {
                        window.location.href = '/logout';
                    }
                });

            } else {
                // Error handling
                if (response.status === 401) {
                    // Incorrect current password
                    showFieldError('currentPassword', 'Current password is incorrect');
                } else if (data.field === 'currentPassword') {
                    // Field-specific error
                    showFieldError('currentPassword', data.message);
                } else if (data.message.includes('Current password')) {
                    showFieldError('currentPassword', data.message);
                } else if (data.message.includes('do not match')) {
                    showFieldError('confirmPassword', data.message);
                } else if (data.message.includes('security requirements')) {
                    showFieldError('newPassword', data.message);
                } else {
                    // General error
                    showError(data.message);
                }
            }

        } catch (error) {
            showError('Failed to reset password. Please try again.');
        } finally {
            submitBtn.disabled = false;
            submitBtn.innerHTML = '<i class="bi bi-shield-check me-1"></i> Reset Password';
        }
    }

    // Show field error
    function showFieldError(fieldId, message) {
        const field = document.getElementById(fieldId);
        const errorDiv = document.getElementById(fieldId + '-error');

        field.classList.add('is-invalid');
        errorDiv.textContent = message;
    }

    // Show general error
    function showError(message) {
        Swal.fire({
            icon: 'error',
            title: 'Reset Failed',
            text: message,
            confirmButtonColor: '#dc3545'
        });
    }

    // Clear all errors
    function clearErrors() {
        ['currentPassword', 'newPassword', 'confirmPassword'].forEach(fieldId => {
            const field = document.getElementById(fieldId);
            const errorDiv = document.getElementById(fieldId + '-error');

            field.classList.remove('is-invalid');
            if (errorDiv) errorDiv.textContent = '';
        });
    }

    // Reset form
    function resetForm() {
        const form = document.getElementById('resetPasswordForm');
        if (form) {
            form.reset();
            clearErrors();
            resetRequirements();

            const strengthBadge = document.getElementById('passwordStrength');
            if (strengthBadge) {
                strengthBadge.textContent = 'Not Set';
                strengthBadge.className = 'badge bg-secondary';
            }

            // Reset password field types to password
            ['currentPassword', 'newPassword', 'confirmPassword'].forEach(fieldId => {
                const field = document.getElementById(fieldId);
                const icon = document.getElementById(fieldId + '-icon');

                if (field) field.type = 'password';
                if (icon) {
                    icon.classList.remove('bi-eye-slash');
                    icon.classList.add('bi-eye');
                }
            });
        }
    }

    // Initialize
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initResetPassword);
    } else {
        initResetPassword();
    }

})();

// ========================================
// SUPER ADMIN: CUTOFF DATE CONFIGURATION
// ========================================

(function() {
    'use strict';

    const btnConfigureCutoffDate = document.getElementById('btnConfigureCutoffDate');
    const cutoffDateModal = document.getElementById('cutoffDateModal');
    const cutoffDateInput = document.getElementById('cutoffDateInput');
    const btnSaveCutoffDate = document.getElementById('btnSaveCutoffDate');

    if (!btnConfigureCutoffDate) {
        return;
    }

    // Open modal and load current cutoff date
    btnConfigureCutoffDate.addEventListener('click', async function(e) {
        e.preventDefault();

        try {
            const response = await fetch('/api/system-config/cutoff-date', {
                headers: {
                    'Accept': 'application/json'
                }
            });

            if (response.ok) {
                const data = await response.json();
                cutoffDateInput.value = data.cutoffDate;
            } else {
                cutoffDateInput.value = '2025-08-01';
            }
        } catch (error) {
            cutoffDateInput.value = '2025-08-01';
        }

        //  Create modal with NO backdrop blocking
        const modal = new bootstrap.Modal(cutoffDateModal, {
            backdrop: 'static',
            keyboard: false,
            focus: true
        });

        modal.show();

        //  Remove backdrop and enable input
        setTimeout(() => {
            const backdrop = document.querySelector('.modal-backdrop');
            if (backdrop) {
                backdrop.style.display = 'none';
            }

            cutoffDateInput.disabled = false;
            cutoffDateInput.readOnly = false;
        }, 300);
    });

    //  Direct click handler on input
    if (cutoffDateInput) {
        cutoffDateInput.addEventListener('click', function(e) {
            e.stopPropagation();

            try {
                this.showPicker();
            } catch (err) {
            }
        });

        cutoffDateInput.addEventListener('focus', function() {
            this.style.backgroundColor = '#fff';
        });

        cutoffDateInput.addEventListener('change', function() {
        });
    }

    // Save cutoff date
    btnSaveCutoffDate.addEventListener('click', async function() {
        const cutoffDate = cutoffDateInput.value;

        if (!cutoffDate) {
            Swal.fire({
                icon: 'error',
                title: 'Validation Error',
                text: 'Please select a cutoff date',
                confirmButtonColor: '#ef4444'
            });
            return;
        }

        const confirmed = await Swal.fire({
            title: 'Confirm Recategorization',
            html: `
                <div class="text-start">
                    <p><strong>Cutoff Date:</strong> ${new Date(cutoffDate).toLocaleDateString('en-GB')}</p>
                    <p class="text-warning">
                        <i class="bi bi-exclamation-triangle me-2"></i>
                        This will automatically recategorize ALL students based on their admission date.
                    </p>
                    <ul class="mt-2 text-muted small">
                        <li>Before cutoff: <strong>OLD_STUDENT</strong></li>
                        <li>After cutoff: <strong>PURSUING</strong></li>
                        <li>REG* numbers: <strong>NEW_STUDENT</strong></li>
                        <li>All certificates issued: <strong>COMPLETED</strong></li>
                        <li>Has refund: <strong>CANCELLED</strong></li>
                    </ul>
                    <p>Are you sure you want to proceed?</p>
                </div>
            `,
            icon: 'warning',
            showCancelButton: true,
            confirmButtonText: 'Yes, Recategorize All',
            cancelButtonText: 'Cancel',
            confirmButtonColor: '#3b82f6'
        });

        if (!confirmed.isConfirmed) return;

        Swal.fire({
            title: 'Processing...',
            html: 'Recategorizing all students based on admission dates. This may take a few moments.',
            allowOutsideClick: false,
            didOpen: () => Swal.showLoading()
        });

        try {
            const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
            const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');

            const response = await fetch('/api/system-config/cutoff-date', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    [csrfHeader]: csrfToken
                },
                body: JSON.stringify({ cutoffDate })
            });

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.message || 'Failed to update cutoff date');
            }

            const result = await response.json();

            Swal.close();

            // Close modal manually
            const modalInstance = bootstrap.Modal.getInstance(cutoffDateModal);
            if (modalInstance) {
                modalInstance.hide();
            }
            cutoffDateModal.classList.remove('show');
            cutoffDateModal.style.display = 'none';
            document.body.classList.remove('modal-open');
            document.body.style.removeProperty('overflow');
            document.body.style.removeProperty('padding-right');

            Swal.fire({
                icon: 'success',
                title: 'Success!',
                html: `
                    <div class="text-start">
                        <p>${result.message}</p>
                        <p class="text-muted"><small>Cutoff Date: ${new Date(result.cutoffDate).toLocaleDateString('en-GB')}</small></p>
                        <p class="text-info"><small>Students are categorized based on their admission date (not created/updated date)</small></p>
                    </div>
                `,
                confirmButtonColor: '#10b981',
                timer: 4000
            }).then(() => {
                // Reload admission page if currently on it
                if (window.location.pathname.includes('/admission')) {
                    location.reload();
                }
            });

        } catch (error) {
            Swal.close();

            Swal.fire({
                icon: 'error',
                title: 'Update Failed',
                text: error.message || 'Failed to update cutoff date',
                confirmButtonColor: '#ef4444'
            });
        }
    });
})();

/* Global Smart Position Listener with Body Teleportation for Action Dropdown Menus */
window.closeAllActionMenus = function() {
    document.querySelectorAll('.action-menu').forEach(m => {
        m.classList.remove('show');
        m.style.display = '';
        m.style.position = '';
        m.style.top = '';
        m.style.left = '';
        m.style.right = '';
        m.style.bottom = '';
        m.style.transform = '';
        m.style.zIndex = '';
        m.style.opacity = '';
        m.style.visibility = '';
        if (m._originalParent && m.parentElement !== m._originalParent) {
            m._originalParent.appendChild(m);
        }
    });
};

window.openActionMenuFixed = function(triggerBtn) {
    const dropdown = triggerBtn.closest('.action-dropdown');
    let menu = triggerBtn._teleportedMenu || (dropdown ? dropdown.querySelector('.action-menu') : null);
    if (!menu) return;

    const isAlreadyOpen = menu.classList.contains('show');

    // Close all open action menus and return teleported menus to original parent
    closeAllActionMenus();

    if (isAlreadyOpen) return;

    // Store original parent
    if (!menu._originalParent && dropdown) {
        menu._originalParent = dropdown;
    }
    triggerBtn._teleportedMenu = menu;

    // Teleport to document.body to escape parent z-index / stacking context traps
    document.body.appendChild(menu);

    // Measure dimensions
    menu.style.display = 'block';
    menu.style.visibility = 'hidden';
    menu.style.position = 'fixed';
    const menuWidth = menu.offsetWidth || 210;
    const menuHeight = menu.offsetHeight || 260;

    const rect = triggerBtn.getBoundingClientRect();
    const spaceBelow = window.innerHeight - rect.bottom;
    const spaceAbove = rect.top;

    let top, left;

    // Vertical positioning: open above if space below is limited
    if (spaceBelow < menuHeight && spaceAbove > spaceBelow) {
        top = Math.max(10, rect.top - menuHeight - 6);
    } else {
        top = Math.min(window.innerHeight - menuHeight - 10, rect.bottom + 6);
    }

    // Horizontal positioning: align right edge of menu with right edge of button
    if (rect.right - menuWidth >= 10) {
        left = rect.right - menuWidth;
    } else {
        left = Math.max(10, rect.left);
    }

    menu.style.position = 'fixed';
    menu.style.top = `${top}px`;
    menu.style.left = `${left}px`;
    menu.style.right = 'auto';
    menu.style.bottom = 'auto';
    menu.style.zIndex = '999999999';
    menu.style.opacity = '1';
    menu.style.visibility = 'visible';
    menu.style.transform = 'none';

    menu.classList.add('show');
};

// Global click event delegation for action menu triggers and items
document.addEventListener('click', function(e) {
    const trigger = e.target.closest('.action-menu-trigger');
    if (trigger) {
        e.stopPropagation();
        e.preventDefault();
        openActionMenuFixed(trigger);
        return;
    }

    const menuItem = e.target.closest('.action-menu-item');
    if (menuItem) {
        // Close immediately — do not wait, so modal can open cleanly on top
        closeAllActionMenus();
        return;
    }

    // If clicking outside an open action menu, close all action menus
    if (!e.target.closest('.action-menu')) {
        closeAllActionMenus();
    }
});

// Close open action menus when scrolling
window.addEventListener('scroll', function() {
    closeAllActionMenus();
}, { passive: true });

// Close action menu immediately whenever any Bootstrap modal starts to open
document.addEventListener('show.bs.modal', function() {
    closeAllActionMenus();
});

// Also handle SweetAlert2 — close action menu when Swal fires
(function patchSwal() {
    if (typeof Swal !== 'undefined' && Swal.mixin) {
        const origFire = Swal.fire.bind(Swal);
        Swal.fire = function() {
            closeAllActionMenus();
            return origFire.apply(this, arguments);
        };
    } else {
        // Swal not loaded yet — retry after DOM ready
        document.addEventListener('DOMContentLoaded', patchSwal);
    }
})();