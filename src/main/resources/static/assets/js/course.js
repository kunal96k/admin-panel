/**
 * Course Management System - Complete JavaScript (Defensive Version)
 * TechnoKraft Training & Solutions
 */
(function() {
    'use strict';

    // Check if we're on the course management page
    const courseTableBody = document.getElementById('courseTableBody');
    if (!courseTableBody) {
        console.log('Course management elements not found. Skipping initialization.');
        return;
    }

    // API Configuration
    const API_BASE_URL = '/api/courses';
    const SUBJECT_API_URL = '/api/subjects';
    const UPLOAD_BASE_URL = '/uploads/courses';

    // State Management
    let courses = [];
    let filteredCourses = [];
    let subjects = [];
    let currentPage = 0;
    let pageSize = 25;
    let totalPages = 0;
    let totalItems = 0;
    let editingCourseId = null;
    let editingSubjectId = null;
    let selectedCourseId = null;
    let currentImageFile = null;

    // Initialize on DOM Load
    document.addEventListener('DOMContentLoaded', function() {
        console.log('Course Management System - Initializing...');
        initializeEventListeners();
        loadCourses();
    });

    // ==================== EVENT LISTENERS ====================

    function initializeEventListeners() {
        // Check if required elements exist
        const btnAddCourse = document.getElementById('btnAddCourse');
        const btnExportCSV = document.getElementById('btnExportCSV');
        const searchInput = document.getElementById('searchInput');
        const pageSizeSelect = document.getElementById('pageSizeSelect');
        const saveCourseBtn = document.getElementById('saveCourseBtn');
        const saveSubjectBtn = document.getElementById('saveSubjectBtn');

        if (!btnAddCourse || !btnExportCSV || !searchInput || !pageSizeSelect || !saveCourseBtn || !saveSubjectBtn) {
            console.error('Required course management elements not found');
            return;
        }

        // Action buttons
        btnAddCourse.addEventListener('click', openAddCourseModal);
        btnExportCSV.addEventListener('click', exportCSV);
        saveCourseBtn.addEventListener('click', saveCourse);
        saveSubjectBtn.addEventListener('click', saveSubject);

        // Search
        searchInput.addEventListener('input', debounce(handleSearch, 300));

        // Page size
        pageSizeSelect.addEventListener('change', function() {
            pageSize = parseInt(this.value);
            currentPage = 0;
            loadCourses();
        });

        // Image dropzone
        initializeImageUpload();

        // Image modal
        const imageModalClose = document.getElementById('imageModalClose');
        const imageModalBackdrop = document.getElementById('imageModalBackdrop');

        if (imageModalClose) {
            imageModalClose.addEventListener('click', closeImageModal);
        }

        if (imageModalBackdrop) {
            imageModalBackdrop.addEventListener('click', function(e) {
                if (e.target === this) closeImageModal();
            });
        }
    }

    function initializeImageUpload() {
        const dropzone = document.getElementById('courseImageDropzone');
        const fileInput = document.getElementById('courseImageInput');
        const removeImageBtn = document.getElementById('removeImageBtn');

        if (!dropzone || !fileInput || !removeImageBtn) {
            console.warn('Image upload elements not found');
            return;
        }

        dropzone.addEventListener('click', () => fileInput.click());

        dropzone.addEventListener('dragover', (e) => {
            e.preventDefault();
            dropzone.classList.add('dragover');
        });

        dropzone.addEventListener('dragleave', () => {
            dropzone.classList.remove('dragover');
        });

        dropzone.addEventListener('drop', (e) => {
            e.preventDefault();
            dropzone.classList.remove('dragover');
            const file = e.dataTransfer.files[0];
            if (file && file.type.startsWith('image/')) {
                handleImageFile(file);
            }
        });

        fileInput.addEventListener('change', (e) => {
            const file = e.target.files[0];
            if (file) handleImageFile(file);
        });

        removeImageBtn.addEventListener('click', removeImage);
    }

    // ==================== DATA LOADING ====================

    async function loadCourses() {
        try {
            showLoading();

           const response = await fetch(`${API_BASE_URL}?page=${currentPage}&size=${pageSize}`, {
               headers: {
                   'Accept': 'application/json'
               }
           });

            if (!response.ok) {
                throw new Error('Failed to load courses');
            }

            const data = await response.json();

            courses = data.courses || [];
            filteredCourses = [...courses];
            totalPages = data.totalPages || 0;
            totalItems = data.totalItems || 0;

            renderTable();

        } catch (error) {
            console.error('Error loading courses:', error);
            showError('Failed to load courses. Please try again.');
            renderEmptyTable();
        }
    }

    async function handleSearch(e) {
        const searchTerm = e.target.value.trim();

        try {
            const url = searchTerm
                ? `${API_BASE_URL}/search?searchTerm=${encodeURIComponent(searchTerm)}&page=${currentPage}&size=${pageSize}`
                : `${API_BASE_URL}?page=${currentPage}&size=${pageSize}`;

            const response = await fetch(url, {
                        headers: {
                            'Accept': 'application/json'
                        }
                    });

            if (!response.ok) {
                throw new Error('Search failed');
            }

            const data = await response.json();

            filteredCourses = data.courses || [];
            totalPages = data.totalPages || 0;
            totalItems = data.totalItems || 0;
            currentPage = 0;

            renderTable();

        } catch (error) {
            console.error('Error searching courses:', error);
            showError('Search failed. Please try again.');
        }
    }

    // ==================== TABLE RENDERING ====================

    function renderTable() {
        const tbody = document.getElementById('courseTableBody');
        if (!tbody) return;

        if (filteredCourses.length === 0) {
            renderEmptyTable();
            return;
        }

        tbody.innerHTML = filteredCourses.map((course, index) => {
            const srNo = (currentPage * pageSize) + index + 1;

            const imageHtml = course.courseImagePath
                ? `<img src="${UPLOAD_BASE_URL}/${course.courseImagePath}"
                        class="course-image"
                        onclick="viewImage('${UPLOAD_BASE_URL}/${course.courseImagePath}')"
                        alt="${course.courseName}"
                        onerror="this.onerror=null; this.parentElement.innerHTML='<div class=\\'no-image-placeholder\\'>No Image</div>';">`
                : `<div class="no-image-placeholder">No Image</div>`;

            return `
                <tr>
                    <td><strong>${srNo}</strong></td>
                    <td>${imageHtml}</td>
                    <td><strong>${course.courseName}</strong></td>
                    <td><strong>₹${Number(course.courseFees).toLocaleString()}</strong></td>
                    <td>
                        <button class="action-btn" onclick="editCourse(${course.id})" title="Edit Course">
                            <i class="bi bi-pencil-square"></i>
                        </button>
                        <button class="action-btn" onclick="deleteCourse(${course.id})" title="Delete Course">
                            <i class="bi bi-trash"></i>
                        </button>
                        <button class="action-btn" onclick="manageSubjects(${course.id})" title="Manage Subjects">
                            <i class="bi bi-list-ul"></i>
                        </button>
                    </td>
                </tr>
            `;
        }).join('');

        updatePaginationInfo();
        renderPagination();
    }

    function renderEmptyTable() {
        const tbody = document.getElementById('courseTableBody');
        if (!tbody) return;

        tbody.innerHTML = `
            <tr>
                <td colspan="5" class="text-center py-5">
                    <i class="bi bi-inbox" style="font-size: 3rem; color: #94a3b8;"></i>
                    <p class="mt-3 mb-0 text-muted">No courses found</p>
                </td>
            </tr>
        `;
        updatePaginationInfo();

        const paginationControls = document.getElementById('paginationControls');
        if (paginationControls) {
            paginationControls.innerHTML = '';
        }
    }

    function showLoading() {
        const tbody = document.getElementById('courseTableBody');
        if (!tbody) return;

        tbody.innerHTML = `
            <tr>
                <td colspan="5" class="text-center py-4">
                    <div class="spinner-border text-primary" role="status">
                        <span class="visually-hidden">Loading...</span>
                    </div>
                    <p class="mt-2 mb-0">Loading courses...</p>
                </td>
            </tr>
        `;
    }

    function updatePaginationInfo() {
        const entriesStart = document.getElementById('entriesStart');
        const entriesEnd = document.getElementById('entriesEnd');
        const totalEntriesElem = document.getElementById('totalEntries');

        if (!entriesStart || !entriesEnd || !totalEntriesElem) return;

        const start = filteredCourses.length === 0 ? 0 : (currentPage * pageSize) + 1;
        const end = Math.min((currentPage + 1) * pageSize, totalItems);

        entriesStart.textContent = start;
        entriesEnd.textContent = end;
        totalEntriesElem.textContent = totalItems;
    }

    function renderPagination() {
        const pagination = document.getElementById('paginationControls');
        if (!pagination) return;

        let html = '';

        // Previous button
        html += `
            <li class="page-item ${currentPage === 0 ? 'disabled' : ''}">
                <a class="page-link" href="#" onclick="changePage(${currentPage - 1}); return false;">Previous</a>
            </li>
        `;

        // Page numbers
        const maxVisible = 5;
        let startPage = Math.max(0, currentPage - Math.floor(maxVisible / 2));
        let endPage = Math.min(totalPages - 1, startPage + maxVisible - 1);

        if (endPage - startPage < maxVisible - 1) {
            startPage = Math.max(0, endPage - maxVisible + 1);
        }

        for (let i = startPage; i <= endPage; i++) {
            html += `
                <li class="page-item ${i === currentPage ? 'active' : ''}">
                    <a class="page-link" href="#" onclick="changePage(${i}); return false;">${i + 1}</a>
                </li>
            `;
        }

        // Next button
        html += `
            <li class="page-item ${currentPage >= totalPages - 1 ? 'disabled' : ''}">
                <a class="page-link" href="#" onclick="changePage(${currentPage + 1}); return false;">Next</a>
            </li>
        `;

        pagination.innerHTML = html;
    }

    window.changePage = function(page) {
        if (page >= 0 && page < totalPages) {
            currentPage = page;
            loadCourses();
        }
    };

    // ==================== COURSE OPERATIONS ====================

    function openAddCourseModal() {
        editingCourseId = null;
        const courseForm = document.getElementById('courseForm');
        if (courseForm) courseForm.reset();

        const courseId = document.getElementById('courseId');
        if (courseId) courseId.value = '';

        const courseModalTitle = document.getElementById('courseModalTitle');
        if (courseModalTitle) {
            courseModalTitle.innerHTML = '<i class="bi bi-plus-circle me-2"></i>Add New Course';
        }

        removeImage();

        const courseModalElem = document.getElementById('courseModal');
        if (courseModalElem && typeof bootstrap !== 'undefined') {
            const modal = new bootstrap.Modal(courseModalElem);
            modal.show();
        }
    }

    window.editCourse = async function(id) {
        try {
           const response = await fetch(`${API_BASE_URL}/${id}`, {
               headers: {
                   'Accept': 'application/json'
               }
           });

            if (!response.ok) {
                throw new Error('Failed to load course details');
            }

            const course = await response.json();

            editingCourseId = id;

            const courseId = document.getElementById('courseId');
            const courseName = document.getElementById('courseName');
            const courseFees = document.getElementById('courseFees');
            const courseModalTitle = document.getElementById('courseModalTitle');

            if (courseId) courseId.value = id;
            if (courseName) courseName.value = course.courseName;
            if (courseFees) courseFees.value = course.courseFees;
            if (courseModalTitle) {
                courseModalTitle.innerHTML = '<i class="bi bi-pencil-square me-2"></i>Update Course';
            }

            if (course.courseImagePath) {
                const previewImg = document.getElementById('previewImg');
                const imagePreview = document.getElementById('imagePreview');
                if (previewImg) previewImg.src = `${UPLOAD_BASE_URL}/${course.courseImagePath}`;
                if (imagePreview) imagePreview.style.display = 'block';
            } else {
                removeImage();
            }

            const courseModalElem = document.getElementById('courseModal');
            if (courseModalElem && typeof bootstrap !== 'undefined') {
                const modal = new bootstrap.Modal(courseModalElem);
                modal.show();
            }

        } catch (error) {
            console.error('Error loading course:', error);
            showError('Failed to load course details');
        }
    };

    async function saveCourse() {
        const form = document.getElementById('courseForm');
        if (!form) return;

        if (!form.checkValidity()) {
            form.reportValidity();
            return;
        }

        const courseName = document.getElementById('courseName');
        const courseFees = document.getElementById('courseFees');

        if (!courseName || !courseFees) return;

        const formData = new FormData();
        formData.append('courseName', courseName.value.trim());
        formData.append('courseFees', courseFees.value);

        if (currentImageFile) {
            formData.append('courseImage', currentImageFile);
        }

        try {
            const url = editingCourseId
                ? `${API_BASE_URL}/${editingCourseId}`
                : API_BASE_URL;

            const method = editingCourseId ? 'PUT' : 'POST';

           const response = await fetch(url, {
               method: method,
               body: formData,
               headers: {
                   'Accept': 'application/json'
               }
           });


            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.error || 'Failed to save course');
            }

            const message = editingCourseId ? 'Course updated successfully!' : 'Course added successfully!';
            showSuccess(message);

            const courseModalElem = document.getElementById('courseModal');
            if (courseModalElem && typeof bootstrap !== 'undefined') {
                const modalInstance = bootstrap.Modal.getInstance(courseModalElem);
                if (modalInstance) modalInstance.hide();
            }

            // Reload courses
            await loadCourses();

        } catch (error) {
            console.error('Error saving course:', error);
            showError(error.message);
        }
    }

    window.deleteCourse = async function(id) {
        if (typeof Swal === 'undefined') {
            if (!confirm('Do you want to remove the selected course?')) return;
        } else {
            const result = await Swal.fire({
                title: 'Confirmation',
                text: 'Do you want to remove the selected course?',
                icon: 'warning',
                showCancelButton: true,
                confirmButtonText: 'Yes, Delete',
                cancelButtonText: 'Cancel',
                confirmButtonColor: '#ef4444'
            });

            if (!result.isConfirmed) return;
        }

        try {
          const response = await fetch(`${API_BASE_URL}/${id}`, {
              method: 'DELETE',
              headers: {
                  'Accept': 'application/json'
              }
          });

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.error || 'Failed to delete course');
            }

            showSuccess('Course deleted successfully!');

            // Reload courses
            await loadCourses();

        } catch (error) {
            console.error('Error deleting course:', error);
            showError(error.message);
        }
    };

    // ==================== IMAGE HANDLING ====================

    function handleImageFile(file) {
        if (file.size > 2 * 1024 * 1024) {
            showError('Image size should be less than 2MB');
            return;
        }

        if (!file.type.startsWith('image/')) {
            showError('Please select a valid image file');
            return;
        }

        currentImageFile = file;
        const reader = new FileReader();
        reader.onload = function(e) {
            const previewImg = document.getElementById('previewImg');
            const imagePreview = document.getElementById('imagePreview');
            if (previewImg) previewImg.src = e.target.result;
            if (imagePreview) imagePreview.style.display = 'block';
        };
        reader.readAsDataURL(file);
    }

    function removeImage() {
        currentImageFile = null;
        const courseImageInput = document.getElementById('courseImageInput');
        const imagePreview = document.getElementById('imagePreview');
        const previewImg = document.getElementById('previewImg');

        if (courseImageInput) courseImageInput.value = '';
        if (imagePreview) imagePreview.style.display = 'none';
        if (previewImg) previewImg.src = '';
    }

    window.viewImage = function(imageSrc) {
        const modalImage = document.getElementById('modalImage');
        const imageModalBackdrop = document.getElementById('imageModalBackdrop');

        if (modalImage) modalImage.src = imageSrc;
        if (imageModalBackdrop) imageModalBackdrop.classList.add('show');
    };

    function closeImageModal() {
        const imageModalBackdrop = document.getElementById('imageModalBackdrop');
        if (imageModalBackdrop) imageModalBackdrop.classList.remove('show');
    }

    // ==================== SUBJECT MANAGEMENT ====================

    window.manageSubjects = async function(courseId) {
        selectedCourseId = courseId;
        const course = courses.find(c => c.id === courseId);

        if (!course) {
            showError('Course not found');
            return;
        }

        const subjectModalTitle = document.getElementById('subjectModalTitle');
        const subjectCourseId = document.getElementById('subjectCourseId');
        const subjectForm = document.getElementById('subjectForm');
        const subjectId = document.getElementById('subjectId');
        const subjectBtnText = document.getElementById('subjectBtnText');

        if (subjectModalTitle) {
            subjectModalTitle.innerHTML = `<i class="bi bi-list-ul me-2"></i>${course.courseName} : Manage Subjects`;
        }
        if (subjectCourseId) subjectCourseId.value = courseId;
        if (subjectForm) subjectForm.reset();
        if (subjectId) subjectId.value = '';
        if (subjectBtnText) subjectBtnText.textContent = 'Add Subject';
        editingSubjectId = null;

        // Load subjects from backend
        await loadSubjects(courseId);

        const subjectModalElem = document.getElementById('subjectModal');
        if (subjectModalElem && typeof bootstrap !== 'undefined') {
            const modal = new bootstrap.Modal(subjectModalElem);
            modal.show();
        }
    };

    async function loadSubjects(courseId) {
        try {
           const response = await fetch(`${SUBJECT_API_URL}/course/${courseId}`, {
               headers: {
                   'Accept': 'application/json'
               }
           });

            if (!response.ok) {
                throw new Error('Failed to load subjects');
            }

            subjects = await response.json();
            renderSubjectTable();

        } catch (error) {
            console.error('Error loading subjects:', error);
            showError('Failed to load subjects');
            subjects = [];
            renderSubjectTable();
        }
    }

    function renderSubjectTable() {
        const tbody = document.getElementById('subjectTableBody');
        if (!tbody) return;

        if (subjects.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="2" class="text-center py-4 text-muted">
                        No subjects added yet
                    </td>
                </tr>
            `;
            return;
        }

        tbody.innerHTML = subjects.map(subject => `
            <tr>
                <td>${subject.subjectName}</td>
                <td>
                    <button class="action-btn" onclick="editSubject(${subject.id})" title="Edit Subject">
                        <i class="bi bi-pencil-square"></i>
                    </button>
                    <button class="action-btn" onclick="deleteSubject(${subject.id})" title="Delete Subject">
                        <i class="bi bi-trash"></i>
                    </button>
                </td>
            </tr>
        `).join('');
    }

    async function saveSubject() {
        const subjectNameInput = document.getElementById('subjectName');
        if (!subjectNameInput) return;

        const subjectName = subjectNameInput.value.trim();

        if (!subjectName) {
            showError('Please enter subject name');
            return;
        }

        try {
            const formData = new FormData();
            formData.append('subjectName', subjectName);
            formData.append('courseId', selectedCourseId);

            const url = editingSubjectId
                ? `${SUBJECT_API_URL}/${editingSubjectId}`
                : SUBJECT_API_URL;

            const method = editingSubjectId ? 'PUT' : 'POST';

           const response = await fetch(url, {
               method: method,
               body: formData,
               headers: {
                   'Accept': 'application/json'
               }
           });

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.error || 'Failed to save subject');
            }

            const message = editingSubjectId ? 'Subject updated successfully!' : 'Subject added successfully!';
            showSuccess(message);

            editingSubjectId = null;

            const subjectBtnText = document.getElementById('subjectBtnText');
            if (subjectBtnText) subjectBtnText.textContent = 'Add Subject';

            const subjectForm = document.getElementById('subjectForm');
            if (subjectForm) subjectForm.reset();

            // Reload subjects
            await loadSubjects(selectedCourseId);

        } catch (error) {
            console.error('Error saving subject:', error);
            showError(error.message);
        }
    }

    window.editSubject = function(id) {
        const subject = subjects.find(s => s.id === id);
        if (!subject) return;

        editingSubjectId = id;

        const subjectId = document.getElementById('subjectId');
        const subjectName = document.getElementById('subjectName');
        const subjectBtnText = document.getElementById('subjectBtnText');

        if (subjectId) subjectId.value = id;
        if (subjectName) subjectName.value = subject.subjectName;
        if (subjectBtnText) subjectBtnText.textContent = 'Update Subject';
    };

    window.deleteSubject = async function(id) {
        if (typeof Swal === 'undefined') {
            if (!confirm('Are you sure you want to remove this subject?')) return;
        } else {
            const result = await Swal.fire({
                title: 'Confirmation',
                text: 'Are you sure you want to remove this subject?',
                icon: 'warning',
                showCancelButton: true,
                confirmButtonText: 'Yes, Delete',
                cancelButtonText: 'Cancel',
                confirmButtonColor: '#ef4444'
            });

            if (!result.isConfirmed) return;
        }

        try {

           const response = await fetch(`${SUBJECT_API_URL}/${id}`, {
               method: 'DELETE',
               headers: {
                   'Accept': 'application/json'
               }
           });

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.error || 'Failed to delete subject');
            }

            showSuccess('Subject deleted successfully!');

            // Reload subjects
            await loadSubjects(selectedCourseId);

        } catch (error) {
            console.error('Error deleting subject:', error);
            showError(error.message);
        }
    };

    // ==================== CSV EXPORT ====================

    async function exportCSV() {
        try {

          const response = await fetch(`${API_BASE_URL}/export/csv`, {
              headers: {
                  'Accept': 'application/json'
              }
          });
            if (!response.ok) {
                throw new Error('Export failed');
            }

            const courses = await response.json();

            // Create CSV content
            const headers = ['Sr. No.', 'Course Name', 'Course Fees'];
            const rows = courses.map(course => [
                course.srNo,
                course.courseName,
                course.courseFees
            ]);

            let csvContent = headers.join(',') + '\n';
            rows.forEach(row => {
                csvContent += row.map(cell => `"${cell}"`).join(',') + '\n';
            });

            // Download CSV
            const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `courses_${Date.now()}.csv`;
            a.click();
            window.URL.revokeObjectURL(url);

            showSuccess('CSV exported successfully!');

        } catch (error) {
            console.error('Error exporting CSV:', error);
            showError('Export failed. Please try again.');
        }
    }

    // ==================== UTILITY FUNCTIONS ====================

    function showSuccess(message) {
        if (typeof Swal !== 'undefined') {
            Swal.fire({
                title: 'Success!',
                text: message,
                icon: 'success',
                confirmButtonColor: '#667eea',
                timer: 2000
            });
        } else {
            alert(message);
        }
    }

    function showError(message) {
        if (typeof Swal !== 'undefined') {
            Swal.fire({
                title: 'Error!',
                text: message,
                icon: 'error',
                confirmButtonColor: '#ef4444'
            });
        } else {
            alert(message);
        }
    }

    function debounce(func, wait) {
        let timeout;
        return function(...args) {
            clearTimeout(timeout);
            timeout = setTimeout(() => func.apply(this, args), wait);
        };
    }

    console.log('✅ Course Management System - Initialized Successfully');

})();