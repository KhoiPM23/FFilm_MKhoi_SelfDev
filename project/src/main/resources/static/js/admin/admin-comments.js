// admin-comments.js - Quản lý bình luận cho Admin

class AdminCommentManager {
    constructor() {
        this.allComments = [];
        this.filteredComments = [];
        this.currentPage = 1;
        this.itemsPerPage = 10;
        this.currentAction = null;
        this.currentCommentId = null;

        this.init();
    }

    init() {
        // Load comments khi trang vừa load
        this.loadComments();

        // Setup event listeners
        this.setupEventListeners();
    }

    setupEventListeners() {
        // Search
        const searchInput = document.getElementById('searchInput');
        if (searchInput) {
            searchInput.addEventListener('input', (e) => {
                this.handleSearch(e.target.value);
            });
        }

        // Filters
        const statusFilter = document.getElementById('statusFilter');
        if (statusFilter) {
            statusFilter.addEventListener('change', () => {
                this.applyFilters();
            });
        }

        const movieFilter = document.getElementById('movieFilter');
        if (movieFilter) {
            movieFilter.addEventListener('change', () => {
                this.applyFilters();
            });
        }
    }

    /**
     * Load tất cả comments từ API
     */
    async loadComments() {
        try {
            console.log('[AdminCommentManager] Loading all comments...');
            const response = await fetch('/api/comments/admin/all', {
                credentials: 'include'  // Đảm bảo gửi cookie session
            });

            console.log('[AdminCommentManager] Response status:', response.status);

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                console.error('[AdminCommentManager] Error response:', errorData);
                throw new Error(`HTTP error! status: ${response.status} - ${errorData.message || 'Unknown error'}`);
            }

            const data = await response.json();
            console.log('[AdminCommentManager] API Response:', data);

            if (data.success) {
                this.allComments = data.comments;
                this.filteredComments = [...this.allComments];
                this.updateStats();
                this.populateMovieFilter();
                this.renderTable();
            } else {
                this.showError(data.message || 'Không thể tải danh sách bình luận');
            }
        } catch (error) {
            console.error('[AdminCommentManager] Error loading comments:', error);
            // Không hiển thị alert, chỉ log và hiển thị trong table
            document.getElementById('commentsTableBody').innerHTML = `
                <tr>
                    <td colspan="7" style="text-align: center; padding: 40px; color: #ff6b6b;">
                        <i class="fas fa-exclamation-circle"></i> ${error.message}
                    </td>
                </tr>
            `;
        }
    }

    /**
     * Cập nhật thống kê
     */
    updateStats() {
        const totalComments = this.allComments.length;
        const approvedComments = this.allComments.filter(c => c.status === 'approved').length;
        const deletedComments = this.allComments.filter(c => c.status === 'deleted').length;

        document.getElementById('totalComments').textContent = totalComments;
        document.getElementById('approvedComments').textContent = approvedComments;
        document.getElementById('deletedComments').textContent = deletedComments;
    }

    /**
     * Populate movie filter dropdown
     */
    populateMovieFilter() {
        const movieFilter = document.getElementById('movieFilter');
        if (!movieFilter) return;

        // Get unique movies
        const movies = new Map();
        this.allComments.forEach(comment => {
            if (comment.movie) {
                movies.set(comment.movie.movieID, comment.movie.title);
            }
        });

        // Clear existing options (except first one)
        movieFilter.innerHTML = '<option value="">Tất cả phim</option>';

        // Add movie options
        movies.forEach((title, id) => {
            const option = document.createElement('option');
            option.value = id;
            option.textContent = title;
            movieFilter.appendChild(option);
        });
    }

    /**
     * Apply filters
     */
    applyFilters() {
        const statusFilter = document.getElementById('statusFilter').value;
        const movieFilter = document.getElementById('movieFilter').value;
        const searchInput = document.getElementById('searchInput').value.toLowerCase();

        this.filteredComments = this.allComments.filter(comment => {
            // Status filter
            if (statusFilter && comment.status !== statusFilter) {
                return false;
            }

            // Movie filter
            if (movieFilter && comment.movie?.movieID != movieFilter) {
                return false;
            }

            // Search filter
            if (searchInput) {
                const userName = comment.user?.userName?.toLowerCase() || '';
                const content = comment.content?.toLowerCase() || '';
                const movieTitle = comment.movie?.title?.toLowerCase() || '';

                if (!userName.includes(searchInput) && 
                    !content.includes(searchInput) && 
                    !movieTitle.includes(searchInput)) {
                    return false;
                }
            }

            return true;
        });

        this.currentPage = 1;
        this.renderTable();
    }

    /**
     * Handle search
     */
    handleSearch(searchTerm) {
        this.applyFilters();
    }

    /**
     * Render table
     */
    renderTable() {
        const tbody = document.getElementById('commentsTableBody');
        if (!tbody) return;

        if (this.filteredComments.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="7" style="text-align: center; padding: 40px; color: var(--color-light-gray);">
                        <i class="fas fa-inbox"></i> Không có bình luận nào
                    </td>
                </tr>
            `;
            document.getElementById('pagination').innerHTML = '';
            return;
        }

        // Pagination
        const startIndex = (this.currentPage - 1) * this.itemsPerPage;
        const endIndex = startIndex + this.itemsPerPage;
        const pageComments = this.filteredComments.slice(startIndex, endIndex);

        // Render rows
        tbody.innerHTML = pageComments.map(comment => this.createTableRow(comment)).join('');

        // Render pagination
        this.renderPagination();
    }

    /**
     * Create table row
     */
    createTableRow(comment) {
        const userName = comment.user?.userName || 'Ẩn danh';
        const movieTitle = comment.movie?.title || 'N/A';
        const content = this.escapeHtml(comment.content || '');
        const createAt = this.formatDate(comment.createAt);
        const status = comment.status || 'approved';
        const statusClass = status === 'approved' ? 'status-approved' : 'status-deleted';
        const statusText = status === 'approved' ? 'Hoạt động' : 'Đã xóa';

        // Truncate content if too long
        const maxLength = 100;
        const truncated = content.length > maxLength;
        const displayContent = truncated ? content.substring(0, maxLength) + '...' : content;

        return `
            <tr data-comment-id="${comment.commentID}">
                <td>${comment.commentID}</td>
                <td>
                    <span class="user-name">${userName}</span>
                </td>
                <td>
                    <span class="movie-title">${movieTitle}</span>
                </td>
                <td>
                    <div class="comment-content" id="content-${comment.commentID}">
                        ${displayContent}
                        ${truncated ? `<button class="expand-btn" onclick="adminCommentManager.toggleContent(${comment.commentID})">Xem thêm</button>` : ''}
                    </div>
                    <div style="display:none" id="full-content-${comment.commentID}">${content}</div>
                </td>
                <td class="comment-date">${createAt}</td>
                <td>
                    <span class="status-badge ${statusClass}">${statusText}</span>
                </td>
                <td>
                    ${status === 'approved' 
                        ? `<button class="action-btn btn-delete" onclick="adminCommentManager.confirmDelete(${comment.commentID})">
                               <i class="fas fa-trash"></i> Xóa
                           </button>`
                        : `<span style="color: var(--color-light-gray); font-size: 13px;">Đã xóa</span>`
                    }
                </td>
            </tr>
        `;
    }

    /**
     * Toggle content expansion
     */
    toggleContent(commentId) {
        const contentDiv = document.getElementById(`content-${commentId}`);
        const fullContent = document.getElementById(`full-content-${commentId}`).textContent;
        
        if (contentDiv.classList.contains('expanded')) {
            // Collapse
            const maxLength = 100;
            const truncated = fullContent.substring(0, maxLength) + '...';
            contentDiv.innerHTML = `${truncated}<button class="expand-btn" onclick="adminCommentManager.toggleContent(${commentId})">Xem thêm</button>`;
            contentDiv.classList.remove('expanded');
        } else {
            // Expand
            contentDiv.innerHTML = `${fullContent}<button class="expand-btn" onclick="adminCommentManager.toggleContent(${commentId})">Thu gọn</button>`;
            contentDiv.classList.add('expanded');
        }
    }

    /**
     * Render pagination
     */
    renderPagination() {
        const pagination = document.getElementById('pagination');
        if (!pagination) return;

        const totalPages = Math.ceil(this.filteredComments.length / this.itemsPerPage);

        if (totalPages <= 1) {
            pagination.innerHTML = '';
            return;
        }

        let html = '<div class="pagination-container">';
        
        // Previous button
        html += `<button class="pagination-btn ${this.currentPage === 1 ? 'disabled' : ''}" 
                    onclick="adminCommentManager.goToPage(${this.currentPage - 1})" 
                    ${this.currentPage === 1 ? 'disabled' : ''}>
                    <i class="fas fa-chevron-left"></i>
                </button>`;

        // Page numbers
        for (let i = 1; i <= totalPages; i++) {
            if (i === 1 || i === totalPages || (i >= this.currentPage - 2 && i <= this.currentPage + 2)) {
                html += `<button class="pagination-btn ${i === this.currentPage ? 'active' : ''}" 
                            onclick="adminCommentManager.goToPage(${i})">
                            ${i}
                        </button>`;
            } else if (i === this.currentPage - 3 || i === this.currentPage + 3) {
                html += '<span class="pagination-dots">...</span>';
            }
        }

        // Next button
        html += `<button class="pagination-btn ${this.currentPage === totalPages ? 'disabled' : ''}" 
                    onclick="adminCommentManager.goToPage(${this.currentPage + 1})" 
                    ${this.currentPage === totalPages ? 'disabled' : ''}>
                    <i class="fas fa-chevron-right"></i>
                </button>`;

        html += '</div>';
        pagination.innerHTML = html;
    }

    /**
     * Go to page
     */
    goToPage(page) {
        const totalPages = Math.ceil(this.filteredComments.length / this.itemsPerPage);
        if (page < 1 || page > totalPages) return;

        this.currentPage = page;
        this.renderTable();
    }

    /**
     * Confirm delete
     */
    confirmDelete(commentId) {
        this.currentCommentId = commentId;
        this.currentAction = 'delete';

        const comment = this.allComments.find(c => c.commentID === commentId);
        const movieTitle = comment?.movie?.title || 'N/A';
        const userName = comment?.user?.userName || 'Ẩn danh';

        document.getElementById('modalTitle').textContent = 'Xác nhận xóa bình luận';
        document.getElementById('modalMessage').innerHTML = `
            Bạn có chắc chắn muốn xóa bình luận này?<br><br>
            <strong>Người dùng:</strong> ${userName}<br>
            <strong>Phim:</strong> ${movieTitle}<br>
            <strong>Nội dung:</strong> ${this.escapeHtml(comment?.content?.substring(0, 100) || '')}...
        `;
        
        document.getElementById('confirmModal').style.display = 'flex';
    }

    /**
     * Confirm action
     */
    async confirmAction() {
        if (this.currentAction === 'delete') {
            await this.deleteComment(this.currentCommentId);
        }

        this.closeModal();
    }

    /**
     * Delete comment
     */
    async deleteComment(commentId) {
        try {
            console.log('[AdminCommentManager] Deleting comment:', commentId);

            const response = await fetch(`/api/comments/admin/${commentId}`, {
                method: 'DELETE',
            });

            const data = await response.json();
            console.log('[AdminCommentManager] Delete response:', data);

            if (data.success) {
                // Reload comments
                await this.loadComments();
            } else {
                this.showError(data.message || 'Không thể xóa bình luận');
            }
        } catch (error) {
            console.error('[AdminCommentManager] Error deleting comment:', error);
            this.showError('Lỗi kết nối. Vui lòng thử lại sau.');
        }
    }

    /**
     * Close modal
     */
    closeModal() {
        document.getElementById('confirmModal').style.display = 'none';
        this.currentAction = null;
        this.currentCommentId = null;
    }

    /**
     * Format date
     */
    formatDate(dateString) {
        if (!dateString) return 'N/A';

        try {
            const date = new Date(dateString);
            if (isNaN(date.getTime())) return 'N/A';

            const day = String(date.getDate()).padStart(2, '0');
            const month = String(date.getMonth() + 1).padStart(2, '0');
            const year = date.getFullYear();
            const hours = String(date.getHours()).padStart(2, '0');
            const minutes = String(date.getMinutes()).padStart(2, '0');

            return `${day}/${month}/${year} ${hours}:${minutes}`;
        } catch (error) {
            return 'N/A';
        }
    }

    /**
     * Escape HTML
     */
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /**
     * Show error
     */
    showError(message) {
        alert('Lỗi: ' + message);
    }
}

// Global functions
function refreshComments() {
    if (window.adminCommentManager) {
        window.adminCommentManager.loadComments();
    }
}

function exportComments() {
    alert('Chức năng xuất dữ liệu đang được phát triển.');
}

function closeModal() {
    if (window.adminCommentManager) {
        window.adminCommentManager.closeModal();
    }
}

function confirmAction() {
    if (window.adminCommentManager) {
        window.adminCommentManager.confirmAction();
    }
}

// Initialize khi DOM ready
document.addEventListener('DOMContentLoaded', () => {
    window.adminCommentManager = new AdminCommentManager();
});
