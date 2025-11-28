 // Sample data
    const courses = [
        { id: 1, name: 'JAVA CORE AND ADVANCE', amount: 15000 },
        { id: 2, name: 'SPRING BOOT', amount: 18000 },
        { id: 3, name: 'PYTHON', amount: 12000 },
        { id: 4, name: 'DATA SCIENCE', amount: 20000 },
        { id: 5, name: 'WEB DEVELOPMENT', amount: 16000 },
        { id: 6, name: 'FULL STACK JAVA', amount: 25000 },
        { id: 7, name: 'FULL STACK PYTHON', amount: 22000 },
        { id: 8, name: 'MONGODB', amount: 10000 }
    ];

    let packages = [
        {
            id: 1,
            name: 'Full Stack Development',
            courses: [
                { id: 1, name: 'JAVA CORE AND ADVANCE', amount: 15000 },
                { id: 2, name: 'SPRING BOOT', amount: 18000 },
                { id: 5, name: 'WEB DEVELOPMENT', amount: 16000 }
            ],
            totalAmount: 49000
        }
    ];

    let selectedCourses = [];
    let currentPage = 1;
    let entriesPerPage = 10;
    let filteredPackages = [...packages];

    // Initialize
    $(document).ready(function() {
        setupEventListeners();
        renderPackagesTable();
    });

    function setupEventListeners() {
        // Course selection
        $('#courseSelect').on('change', function() {
            const selectedOption = $(this).find('option:selected');
            const courseId = parseInt($(this).val());
            const courseName = selectedOption.text().split(' - ')[0];
            const courseAmount = parseInt(selectedOption.data('amount'));

            if (courseId && !selectedCourses.find(c => c.id === courseId)) {
                selectedCourses.push({
                    id: courseId,
                    name: courseName,
                    amount: courseAmount
                });
                renderSelectedCourses();
                updateTotalAmount();
            }

            $(this).val('');
        });

        // Package form submission
        $('#packageForm').on('submit', function(e) {
            e.preventDefault();
            savePackage();
        });

        // Cancel button
        $('#btnCancel').on('click', function() {
            resetForm();
        });

        // Search
        $('#searchInput').on('input', function() {
            const searchTerm = $(this).val().toLowerCase();
            filteredPackages = packages.filter(pkg =>
                pkg.name.toLowerCase().includes(searchTerm)
            );
            currentPage = 1;
            renderPackagesTable();
        });

        // Entries per page
        $('#entriesPerPage').on('change', function() {
            entriesPerPage = parseInt($(this).val());
            currentPage = 1;
            renderPackagesTable();
        });
    }

    function renderSelectedCourses() {
        const tbody = $('#selectedCoursesBody');

        if (selectedCourses.length === 0) {
            tbody.html(`
                <tr>
                    <td colspan="3" class="text-center text-muted py-4">
                        <i class="bi bi-inbox" style="font-size: 2rem; color: #cbd5e1;"></i>
                        <p class="mt-2 mb-0">No courses selected</p>
                    </td>
                </tr>
            `);
        } else {
            tbody.html(selectedCourses.map(course => `
                <tr>
                    <td>${course.name}</td>
                    <td>₹${course.amount.toLocaleString('en-IN')}</td>
                    <td>
                        <button class="btn btn-sm btn-danger action-btn" onclick="removeCourse(${course.id})">
                            <i class="bi bi-trash"></i> Remove
                        </button>
                    </td>
                </tr>
            `).join(''));
        }
    }

    function removeCourse(courseId) {
        selectedCourses = selectedCourses.filter(c => c.id !== courseId);
        renderSelectedCourses();
        updateTotalAmount();
    }

    function updateTotalAmount() {
        const total = selectedCourses.reduce((sum, course) => sum + course.amount, 0);
        $('#totalAmount').text(total.toLocaleString('en-IN'));
    }

    function savePackage() {
        const packageTitle = $('#packageTitle').val().trim();

        if (!packageTitle) {
            Swal.fire({
                icon: 'warning',
                title: 'Validation Error',
                text: 'Please enter package title'
            });
            return;
        }

        if (selectedCourses.length === 0) {
            Swal.fire({
                icon: 'warning',
                title: 'Validation Error',
                text: 'Please select at least one course'
            });
            return;
        }

        const totalAmount = selectedCourses.reduce((sum, course) => sum + course.amount, 0);
        const newPackage = {
            id: Math.max(...packages.map(p => p.id), 0) + 1,
            name: packageTitle,
            courses: [...selectedCourses],
            totalAmount: totalAmount
        };

        packages.push(newPackage);
        filteredPackages = [...packages];

        Swal.fire({
            icon: 'success',
            title: 'Success',
            text: 'Package created successfully!',
            timer: 2000
        });

        resetForm();
        renderPackagesTable();
    }

    function resetForm() {
        $('#packageTitle').val('');
        $('#courseSelect').val('');
        selectedCourses = [];
        renderSelectedCourses();
        updateTotalAmount();
    }

    function renderPackagesTable() {
        const start = (currentPage - 1) * entriesPerPage;
        const end = start + entriesPerPage;
        const paginatedPackages = filteredPackages.slice(start, end);

        const tbody = $('#packagesTableBody');

        if (paginatedPackages.length === 0) {
            tbody.html(`
                <tr>
                    <td colspan="5" class="text-center py-4">
                        <i class="bi bi-inbox" style="font-size: 3rem; color: #cbd5e1;"></i>
                        <p class="mt-2 mb-0 text-muted">No packages found</p>
                    </td>
                </tr>
            `);
        } else {
            tbody.html(paginatedPackages.map((pkg, index) => `
                <tr>
                    <td>${start + index + 1}</td>
                    <td>${pkg.name}</td>
                    <td>
                        ${pkg.courses.map(c => `<span class="course-badge">${c.name}</span>`).join('')}
                    </td>
                    <td>₹${pkg.totalAmount.toLocaleString('en-IN')}</td>
                    <td>
                        <button class="btn btn-sm btn-info action-btn" onclick="viewPackage(${pkg.id})">
                            <i class="bi bi-eye"></i> View
                        </button>
                        <button class="btn btn-sm btn-danger action-btn" onclick="deletePackage(${pkg.id})">
                            <i class="bi bi-trash"></i> Delete
                        </button>
                    </td>
                </tr>
            `).join(''));
        }

        updatePackagesPaginationInfo();
        renderPackagesPagination();
    }

    function updatePackagesPaginationInfo() {
        const start = filteredPackages.length === 0 ? 0 : (currentPage - 1) * entriesPerPage + 1;
        const end = Math.min(currentPage * entriesPerPage, filteredPackages.length);

        $('#entriesStart').text(start);
        $('#entriesEnd').text(end);
        $('#totalEntries').text(filteredPackages.length);
    }

    function renderPackagesPagination() {
        const totalPages = Math.ceil(filteredPackages.length / entriesPerPage);
        const pagination = $('#paginationControls');

        let html = `
            <li class="page-item ${currentPage === 1 ? 'disabled' : ''}">
                <a class="page-link" href="#" onclick="changePackagePage(${currentPage - 1}); return false;">Previous</a>
            </li>
        `;

        for (let i = 1; i <= totalPages; i++) {
            if (i === 1 || i === totalPages || (i >= currentPage - 1 && i <= currentPage + 1)) {
                html += `
                    <li class="page-item ${i === currentPage ? 'active' : ''}">
                        <a class="page-link" href="#" onclick="changePackagePage(${i}); return false;">${i}</a>
                    </li>
                `;
            } else if (i === currentPage - 2 || i === currentPage + 2) {
                html += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
            }
        }

        html += `
            <li class="page-item ${currentPage === totalPages || totalPages === 0 ? 'disabled' : ''}">
                <a class="page-link" href="#" onclick="changePackagePage(${currentPage + 1}); return false;">Next</a>
            </li>
        `;

        pagination.html(html);
    }

    function changePackagePage(page) {
        const totalPages = Math.ceil(filteredPackages.length / entriesPerPage);
        if (page >= 1 && page <= totalPages) {
            currentPage = page;
            renderPackagesTable();
        }
    }

    function viewPackage(id) {
        const pkg = packages.find(p => p.id === id);
        if (pkg) {
            $('#viewPackageName').text(pkg.name);
            $('#viewPackageAmount').text('₹' + pkg.totalAmount.toLocaleString('en-IN'));

            const coursesHtml = pkg.courses.map(c => `
                <div class="d-flex justify-content-between align-items-center mb-2 p-2 bg-light rounded">
                    <span>${c.name}</span>
                    <strong>₹${c.amount.toLocaleString('en-IN')}</strong>
                </div>
            `).join('');

            $('#viewPackageCourses').html(coursesHtml);

            new bootstrap.Modal($('#viewPackageModal')[0]).show();
        }
    }

    function deletePackage(id) {
        new bootstrap.Modal($('#deleteModal')[0]).show();

        $('#btnConfirmDelete').off('click').on('click', function() {
            packages = packages.filter(p => p.id !== id);
            filteredPackages = [...packages];
            renderPackagesTable();

            Swal.fire({
                icon: 'success',
                title: 'Deleted',
                text: 'Package deleted successfully!',
                timer: 2000
            });

            bootstrap.Modal.getInstance($('#deleteModal')[0]).hide();
        });
    }