    // Sample data
    let leadSources = [
        { id: 1, title: 'Walk-in' },
        { id: 2, title: 'Website' },
        { id: 3, title: 'Social Media' },
        { id: 4, title: 'Referral' },
        { id: 5, title: 'Newspaper Advertisement' },
        { id: 6, title: 'Google Ads' }
    ];

    let currentPage = 1;
    let entriesPerPage = 25;
    let filteredLeadSources = [...leadSources];
    let editingLeadSourceId = null;

    // Initialize
    document.addEventListener('DOMContentLoaded', function() {
        renderTable();
        setupEventListeners();
    });

    function setupEventListeners() {
        // Add Lead Source button
        document.getElementById('btnAddLeadSource').addEventListener('click', function() {
            editingLeadSourceId = null;
            document.getElementById('modalTitle').innerHTML = '<i class="bi bi-megaphone me-2"></i>Add New Lead Source';
            document.getElementById('leadSourceTitle').value = '';
            new bootstrap.Modal(document.getElementById('leadSourceModal')).show();
        });

        // Save Lead Source button
        document.getElementById('btnSaveLeadSource').addEventListener('click', saveLeadSource);

        // Search input
        document.getElementById('searchInput').addEventListener('input', function(e) {
            const searchTerm = e.target.value.toLowerCase();
            filteredLeadSources = leadSources.filter(source =>
                source.title.toLowerCase().includes(searchTerm)
            );
            currentPage = 1;
            renderTable();
        });

        // Entries per page
        document.getElementById('entriesPerPage').addEventListener('change', function(e) {
            entriesPerPage = parseInt(e.target.value);
            currentPage = 1;
            renderTable();
        });
    }

    function renderTable() {
        const start = (currentPage - 1) * entriesPerPage;
        const end = start + entriesPerPage;
        const paginatedLeadSources = filteredLeadSources.slice(start, end);

        const tbody = document.getElementById('leadSourceTableBody');

        if (paginatedLeadSources.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="3" class="text-center py-4">
                        <i class="bi bi-inbox" style="font-size: 3rem; color: #cbd5e1;"></i>
                        <p class="mt-2 mb-0 text-muted">No lead sources found</p>
                    </td>
                </tr>
            `;
        } else {
            tbody.innerHTML = paginatedLeadSources.map((source, index) => `
                <tr>
                    <td>${start + index + 1}</td>
                    <td>${source.title}</td>
                    <td>
                        <button class="btn btn-sm btn-warning action-btn" onclick="editLeadSource(${source.id})">
                            <i class="bi bi-pencil"></i> Edit
                        </button>
                        <button class="btn btn-sm btn-danger action-btn" onclick="deleteLeadSource(${source.id})">
                            <i class="bi bi-trash"></i> Delete
                        </button>
                    </td>
                </tr>
            `).join('');
        }

        updatePaginationInfo();
        renderPagination();
    }

    function updatePaginationInfo() {
        const start = filteredLeadSources.length === 0 ? 0 : (currentPage - 1) * entriesPerPage + 1;
        const end = Math.min(currentPage * entriesPerPage, filteredLeadSources.length);

        document.getElementById('entriesStart').textContent = start;
        document.getElementById('entriesEnd').textContent = end;
        document.getElementById('totalEntries').textContent = filteredLeadSources.length;
    }

    function renderPagination() {
        const totalPages = Math.ceil(filteredLeadSources.length / entriesPerPage);
        const pagination = document.getElementById('paginationControls');

        let html = `
            <li class="page-item ${currentPage === 1 ? 'disabled' : ''}">
                <a class="page-link" href="#" onclick="changePage(${currentPage - 1}); return false;">Previous</a>
            </li>
        `;

        for (let i = 1; i <= totalPages; i++) {
            if (i === 1 || i === totalPages || (i >= currentPage - 1 && i <= currentPage + 1)) {
                html += `
                    <li class="page-item ${i === currentPage ? 'active' : ''}">
                        <a class="page-link" href="#" onclick="changePage(${i}); return false;">${i}</a>
                    </li>
                `;
            } else if (i === currentPage - 2 || i === currentPage + 2) {
                html += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
            }
        }

        html += `
            <li class="page-item ${currentPage === totalPages || totalPages === 0 ? 'disabled' : ''}">
                <a class="page-link" href="#" onclick="changePage(${currentPage + 1}); return false;">Next</a>
            </li>
        `;

        pagination.innerHTML = html;
    }

    function changePage(page) {
        const totalPages = Math.ceil(filteredLeadSources.length / entriesPerPage);
        if (page >= 1 && page <= totalPages) {
            currentPage = page;
            renderTable();
        }
    }

    function editLeadSource(id) {
        const source = leadSources.find(s => s.id === id);
        if (source) {
            editingLeadSourceId = id;
            document.getElementById('modalTitle').innerHTML = '<i class="bi bi-pencil me-2"></i>Edit Lead Source';
            document.getElementById('leadSourceTitle').value = source.title;
            new bootstrap.Modal(document.getElementById('leadSourceModal')).show();
        }
    }

    function saveLeadSource() {
        const sourceTitle = document.getElementById('leadSourceTitle').value.trim();

        if (!sourceTitle) {
            Swal.fire({
                icon: 'warning',
                title: 'Validation Error',
                text: 'Please enter lead source title'
            });
            return;
        }

        if (editingLeadSourceId) {
            // Update existing lead source
            const source = leadSources.find(s => s.id === editingLeadSourceId);
            if (source) {
                source.title = sourceTitle;
                Swal.fire({
                    icon: 'success',
                    title: 'Success',
                    text: 'Lead source updated successfully!',
                    timer: 2000
                });
            }
        } else {
            // Add new lead source
            const newId = Math.max(...leadSources.map(s => s.id), 0) + 1;
            leadSources.push({ id: newId, title: sourceTitle });
            Swal.fire({
                icon: 'success',
                title: 'Success',
                text: 'Lead source added successfully!',
                timer: 2000
            });
        }

        filteredLeadSources = [...leadSources];
        renderTable();
        bootstrap.Modal.getInstance(document.getElementById('leadSourceModal')).hide();
    }

    function deleteLeadSource(id) {
        editingLeadSourceId = id;
        new bootstrap.Modal(document.getElementById('deleteModal')).show();

        document.getElementById('btnConfirmDelete').onclick = function() {
            leadSources = leadSources.filter(s => s.id !== id);
            filteredLeadSources = [...leadSources];
            renderTable();

            Swal.fire({
                icon: 'success',
                title: 'Deleted',
                text: 'Lead source deleted successfully!',
                timer: 2000
            });

            bootstrap.Modal.getInstance(document.getElementById('deleteModal')).hide();
        };
    }