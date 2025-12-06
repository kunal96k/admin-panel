console.log('⚙️ Initializing TechnoKraft Admin Console...');

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
        console.log('🔑 User Permissions:', userPermissions);

        document.querySelectorAll('[data-menu-id]').forEach(item => {
            const menuId = parseInt(item.getAttribute('data-menu-id'));
            const hasAccess = userPermissions.includes(menuId);

            if (!hasAccess) {
                item.style.display = 'none';
                console.log(`❌ Hiding menu: ${menuId}`);
            } else {
                console.log(`✅ Showing menu: ${menuId}`);
            }
        });
    } else {
        console.warn('⚠️ userPermissions not defined - showing all menus');
    }
    
    function checkMenuAccess(menuId) {
        if (typeof userPermissions === 'undefined') {
            console.warn('⚠️ userPermissions not loaded');
            return true;
        }
        
        const hasAccess = userPermissions.includes(parseInt(menuId));
        
        if (!hasAccess) {
            console.warn(`🚫 Access denied to menu ID: ${menuId}`);
            Swal.fire({
                icon: 'error',
                title: '🚫 Access Denied',
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
            
            // Skip check for Dashboard (menu_id = 1)
            if (menuId && menuId !== '1') {
                if (!checkMenuAccess(menuId)) {
                    e.preventDefault();
                    e.stopPropagation();
                    e.stopImmediatePropagation();
                    return false;
                }
            }
        }, true);
    });
    
    console.log('✅ Permission-based access control initialized');

    // Prevent direct URL manipulation
    window.addEventListener('load', function() {
        const currentPath = window.location.pathname;
        const currentLink = document.querySelector(`[href="${currentPath}"]`);
        
        if (currentLink) {
            const menuId = currentLink.getAttribute('data-menu-id');
            
            if (menuId && menuId !== '1' && !checkMenuAccess(menuId)) {
                console.error('🚫 Unauthorized access attempt detected via URL manipulation');
                window.location.href = '/access-denied';
            }
        }
    });

   // ========================================
       // SIDEBAR COLLAPSE (DESKTOP ONLY)
       // ========================================

       // Add tooltips to menu items
       function addTooltips() {
           document.querySelectorAll('.menu-link').forEach(link => {
               const text = link.querySelector('.menu-text');
               if (text) {
                   link.setAttribute('data-tooltip', text.textContent.trim());
               }
           });

           document.querySelectorAll('.submenu-link').forEach(link => {
               link.setAttribute('data-tooltip', link.textContent.trim());
           });
       }

       addTooltips();

       if (sidebarToggle) {
           sidebarToggle.addEventListener('click', function(e) {
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
             sidebar.classList.toggle('active');
             sidebarOverlay.classList.toggle('active');
             document.body.style.overflow = sidebar.classList.contains('active') ? 'hidden' : '';
         }

         if (hamburger) {
             hamburger.addEventListener('click', toggleSidebar);
         }

         if (sidebarOverlay) {
             sidebarOverlay.addEventListener('click', toggleSidebar);
         }

         // Restore collapsed state on page load (desktop only)
         if (window.innerWidth >= 992) {
             const isCollapsed = localStorage.getItem('sidebarCollapsed') === 'true';

             if (isCollapsed) {
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

         // ========================================
         // INITIALIZATION
         // ========================================

         console.log('%cTechnoKraft Admin Console', 'color: #2563eb; font-size: 24px; font-weight: bold;');
         console.log('%cVersion 1.0.0 - Initialized Successfully', 'color: #10b981; font-size: 14px;');

         setTimeout(() => {
             showNotification('Welcome to TechnoKraft Admin Console!', 'success');
         }, 500);
   });

   // ========================================
   // BOOTSTRAP DROPDOWN
   // ========================================

   (function() {
       'use strict';

       /**
        * Initialize Bootstrap Dropdowns
        * This ensures dropdowns work on all pages including fragments
        */
       function initializeDropdowns() {
           const dropdowns = document.querySelectorAll('[data-bs-toggle="dropdown"]');

           dropdowns.forEach(element => {
               // Dispose existing instance if present
               const existingDropdown = bootstrap.Dropdown.getInstance(element);
               if (existingDropdown) {
                   existingDropdown.dispose();
               }

               // Create new dropdown instance
               new bootstrap.Dropdown(element, {
                   boundary: 'window',
                   popperConfig: {
                       strategy: 'fixed'
                   }
               });
           });

           if (dropdowns.length > 0) {
               console.log('✅ Initialized', dropdowns.length, 'Bootstrap dropdown(s)');
           }
       }

       // Initialize on DOM ready
       initializeDropdowns();

       /**
        * Reinitialize dropdowns when page content changes
        * This handles Thymeleaf fragment loading
        */
       function observeContentChanges() {
           const mainContent = document.getElementById('mainContent');

           if (!mainContent) {
               console.warn('⚠️ Main content container not found');
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
                   console.log('🔄 Content changed, reinitializing dropdowns...');
                   setTimeout(initializeDropdowns, 50);
               }
           });

           observer.observe(mainContent, {
               childList: true,
               subtree: true
           });

           console.log('👁️ Content observer initialized');
       }

       // Start observing content changes
       observeContentChanges();

       /**
        * Reinitialize on navigation
        * Handles menu link clicks
        */
       function handleNavigation() {
           const menuLinks = document.querySelectorAll('.menu-link, .submenu-link');

           menuLinks.forEach(link => {
               link.addEventListener('click', function() {
                   // Wait for content to load, then reinitialize
                   setTimeout(initializeDropdowns, 200);
               });
           });
       }

       handleNavigation();

       /**
        * Global function for manual reinitialization
        * Call from anywhere: window.reinitBootstrapDropdowns()
        */
       window.reinitBootstrapDropdowns = function() {
           console.log('🔄 Manual dropdown reinitialization');
           initializeDropdowns();
       };

       /**
        * Fallback: Close dropdowns on outside click
        * Ensures dropdowns close even if Bootstrap fails
        */
       document.addEventListener('click', function(e) {
           if (!e.target.closest('.dropdown')) {
               document.querySelectorAll('.dropdown-menu.show').forEach(menu => {
                   menu.classList.remove('show');
               });
           }
       });

       console.log('✅ Dropdown fix module loaded successfully');
   })();

// ========================================
// USER MENU DROPDOWN
// ========================================


(function() {
    'use strict';

    console.log('🔧 Initializing User Menu Dropdown Fix...');

    // Wait for DOM to be fully loaded
    function initUserDropdown() {
        const userMenu = document.querySelector('.user-menu');
        const dropdownMenu = document.querySelector('.dropdown-menu.dropdown-menu-end');
        const dropdown = document.querySelector('.topbar-right .dropdown');

        if (!userMenu || !dropdownMenu) {
            console.warn('⚠️ User menu elements not found, retrying...');
            setTimeout(initUserDropdown, 100);
            return;
        }

        console.log('✅ User menu elements found');

        // Step 1: Remove Bootstrap's data-bs-toggle to prevent auto-initialization
        userMenu.removeAttribute('data-bs-toggle');
        userMenu.removeAttribute('aria-expanded');

        // Step 2: Destroy any existing Bootstrap dropdown instance
        if (dropdown) {
            const bsDropdown = bootstrap.Dropdown.getInstance(userMenu);
            if (bsDropdown) {
                bsDropdown.dispose();
                console.log('🗑️ Removed existing Bootstrap dropdown instance');
            }
        }

        // Step 3: Add our custom click handler
        userMenu.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();

            console.log('👆 User menu clicked');

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
                console.log('📴 Dropdown closed');
            } else {
                dropdownMenu.classList.add('show');
                userMenu.setAttribute('aria-expanded', 'true');
                console.log('📋 Dropdown opened');
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

        console.log('✅ User menu dropdown initialized successfully (click-only)');
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
                        console.log('📋 Opening Profile Modal');
                        openProfileModal();
                    });
                } else if (icon && icon.classList.contains('bi-gear')) {
                    // Settings
                    item.addEventListener('click', function(e) {
                        e.preventDefault();
                        console.log('⚙️ Opening Settings Modal');
                        openSettingsModal();
                    });
                }
            });

            console.log('✅ Profile & Settings handlers attached');
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
            console.error('Error loading profile:', error);
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
            <div class="row">
                <div class="col-md-4 text-center mb-4">
                    <img src="${photoUrl}"
                         alt="Profile Photo"
                         class="img-fluid rounded-circle mb-3"
                         style="width: 150px; height: 150px; object-fit: cover; border: 4px solid #3b82f6;"
                         onerror="this.src='/assets/images/default-user.png'">
                    <h5 class="mb-1">${employee.employeeName}</h5>
                    <p class="text-muted">${employee.roleName || 'N/A'}</p>
                    <span class="badge ${employee.isActive ? 'bg-success' : 'bg-secondary'}">
                        ${employee.isActive ? 'Active' : 'Inactive'}
                    </span>
                </div>
                <div class="col-md-8">
                    <h6 class="text-primary fw-bold mb-3">Personal Information</h6>
                    <table class="table table-borderless">
                        <tbody>
                            <tr>
                                <td class="text-muted" style="width: 40%;"><i class="bi bi-envelope me-2"></i>Email</td>
                                <td class="fw-semibold">${employee.emailId}</td>
                            </tr>
                            <tr>
                                <td class="text-muted"><i class="bi bi-phone me-2"></i>Mobile</td>
                                <td class="fw-semibold">${employee.mobileNumber}</td>
                            </tr>
                            <tr>
                                <td class="text-muted"><i class="bi bi-briefcase me-2"></i>Designation</td>
                                <td class="fw-semibold">${employee.designation || 'N/A'}</td>
                            </tr>
                            <tr>
                                <td class="text-muted"><i class="bi bi-gender-ambiguous me-2"></i>Gender</td>
                                <td class="fw-semibold">${formatGender(employee.gender)}</td>
                            </tr>
                            <tr>
                                <td class="text-muted"><i class="bi bi-calendar me-2"></i>Date of Birth</td>
                                <td class="fw-semibold">${formatDate(employee.dateOfBirth)}</td>
                            </tr>
                            <tr>
                                <td class="text-muted"><i class="bi bi-geo-alt me-2"></i>Address</td>
                                <td class="fw-semibold">${employee.address || 'N/A'}</td>
                            </tr>
                            ${employee.zoomLink ? `
                            <tr>
                                <td class="text-muted"><i class="bi bi-camera-video me-2"></i>Zoom Link</td>
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

    console.log('✅ Profile & Settings module loaded');

})();