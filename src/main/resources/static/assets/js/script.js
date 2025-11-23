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
    // SIDEBAR COLLAPSE (DESKTOP ONLY)
    // ========================================

    if (sidebarToggle) {
        sidebarToggle.addEventListener('click', function(e) {
            e.preventDefault();

            // Don't allow collapse on mobile
            if (window.innerWidth <= 991) {
                return;
            }

            sidebar.classList.toggle('collapsed');
            mainWrapper.classList.toggle('collapsed');

            // Change icon
            const icon = this.querySelector('i');
            if (sidebar.classList.contains('collapsed')) {
                icon.classList.remove('bi-chevron-left');
                icon.classList.add('bi-chevron-right');
            } else {
                icon.classList.remove('bi-chevron-right');
                icon.classList.add('bi-chevron-left');
            }

            // Ensure the button remains visible when sidebar is collapsed
            this.style.display = 'flex';
        });
    }

    // ========================================
    // MOBILE SIDEBAR TOGGLE
    // ========================================

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

    // ========================================
    // WINDOW RESIZE HANDLER
    // ========================================

    window.addEventListener('resize', function() {
        // Close mobile sidebar when resizing to desktop
        if (window.innerWidth > 991 && sidebar.classList.contains('active')) {
            sidebar.classList.remove('active');
            sidebarOverlay.classList.remove('active');
            document.body.style.overflow = '';
        }

        // Reset collapsed state on mobile
        if (window.innerWidth <= 991) {
            sidebar.classList.remove('collapsed');
            mainWrapper.classList.remove('collapsed');
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
            if (sidebar.classList.contains('collapsed') && window.innerWidth > 991) {
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
            e.preventDefault();

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
            if (window.innerWidth <= 991 && sidebar.classList.contains('active')) {
                toggleSidebar();
            }

            showNotification(`Navigated to ${pageTitles[page] || 'Unknown Page'}`, 'info');
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
                    Swal.fire({
                        title: 'Logged Out Successfully!',
                        icon: 'success',
                        timer: 1500,
                        showConfirmButton: false
                    });
                }, 1000);
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
                            <small class="text-muted">2 minutes ago</small>
                            <p class="mb-0 mt-2 small">A new student enquiry for Web Development course.</p>
                        </div>
                        <div class="border-bottom pb-3 mb-3">
                            <h6 class="mb-1">Fee Payment Received</h6>
                            <small class="text-muted">1 hour ago</small>
                            <p class="mb-0 mt-2 small">₹15,000 received from John Doe for semester fees.</p>
                        </div>
                        <div class="pb-2">
                            <h6 class="mb-1">Certificate Ready</h6>
                            <small class="text-muted">3 hours ago</small>
                            <p class="mb-0 mt-2 small">5 new certificates are ready for printing.</p>
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
