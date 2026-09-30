// comment-handler.js - Comment & Discussion System

class CommentHandler {
    constructor() {
        this.movieId = this.getMovieIdFromUrl();
        this.commentInput = document.getElementById('comment-input-field');
        this.submitBtn = document.getElementById('btn-submit-comment');
        this.commentList = document.getElementById('comment-list');
        this.commentCount = document.getElementById('comment-count');
        this.sortTabs = document.querySelectorAll('.sort-tab-btn');
        this.activeSort = 'top'; // 'top' | 'newest'
        
        this.rawComments = [];
        this.userRatingsMap = {};
        this.reactionsMap = {}; // commentId -> { reactions: {}, totalCount: 0, userReaction: null }
        this.localRepliesMap = {}; // commentId -> parentCommentId
        this.expandedReplies = new Set(); // set of parentCommentIds expanded
        this.activeReplyBoxId = null; // commentId currently replying to
        this.activeEmojiTargetInput = null; // input element for emoji picker
        this.activePickerPopover = null;
        
        const userIdEl = document.getElementById('currentUserId');
        this.currentUserId = userIdEl && userIdEl.value ? parseInt(userIdEl.value) : null;
        const userNameEl = document.getElementById('currentUserName');
        this.currentUserName = userNameEl && userNameEl.value ? userNameEl.value : null;
        this.currentUserInitial = this.currentUserName ? this.currentUserName.charAt(0).toUpperCase() : 'U';
        
        this.modalEl = document.getElementById('deleteConfirmModal');
        
        // Tải cache offline / F5 từ localStorage
        try {
            const cachedRx = localStorage.getItem('ffilm_rx_' + this.movieId);
            if (cachedRx) this.reactionsMap = JSON.parse(cachedRx);
            const cachedRpl = localStorage.getItem('ffilm_replies_' + this.movieId);
            if (cachedRpl) this.localRepliesMap = JSON.parse(cachedRpl);
        } catch (e) {}

        window.commentHandler = this;
        this.init();
    }

    getMovieIdFromUrl() {
        const metaMovieId = document.querySelector('meta[name="movie-id"]');
        if (metaMovieId && metaMovieId.content) {
            return parseInt(metaMovieId.content);
        }
        const pathParts = window.location.pathname.split('/');
        return parseInt(pathParts[pathParts.length - 1]);
    }

    init() {
        if (!this.commentList) return;

        // Load data
        this.loadComments();

        // Listen for rating events
        window.addEventListener('ffilm:movie-rated', (e) => {
            if (e.detail && e.detail.userId) {
                this.userRatingsMap[e.detail.userId] = e.detail.rating;
                this.updateAuthorRatingBadges(e.detail.userId, e.detail.rating);
            }
        });

        window.addEventListener('ffilm:movie-rating-removed', (e) => {
            if (e.detail && e.detail.userId) {
                delete this.userRatingsMap[e.detail.userId];
                this.updateAuthorRatingBadges(e.detail.userId, null);
            }
        });

        // Main input event listeners
        if (this.commentInput && this.submitBtn) {
            this.commentInput.addEventListener('input', () => {
                const content = this.commentInput.value.trim();
                this.submitBtn.disabled = content.length === 0;
            });

            this.commentInput.addEventListener('keypress', (e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    if (!this.submitBtn.disabled) {
                        this.submitComment();
                    }
                }
            });

            this.submitBtn.addEventListener('click', () => {
                this.submitComment();
            });

            // Emoji trigger for main input
            const emojiBtn = document.getElementById('btn-open-emoji');
            if (emojiBtn) {
                emojiBtn.addEventListener('click', (e) => {
                    e.stopPropagation();
                    this.toggleEmojiPicker(emojiBtn, this.commentInput);
                });
            }
        }

        // Sort tabs
        document.querySelectorAll('.sort-tab-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                document.querySelectorAll('.sort-tab-btn').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                this.activeSort = btn.dataset.sort || 'top';
                this.renderComments();
            });
        });

        // Global click to close emoji picker
        document.addEventListener('click', (e) => {
            if (!e.target.closest('.emoji-picker-popover') && !e.target.closest('.btn-open-emoji-picker')) {
                this.closeEmojiPicker();
            }
        });
    }

    /**
     * Load danh sách comments, ratings map, và reactions map từ API
     */
    async loadComments() {
        if (!this.commentList) return;
        try {
            const [commentsRes, ratingsRes, reactionsRes] = await Promise.allSettled([
                fetch(`/api/comments/movie/${this.movieId}`),
                fetch(`/api/reviews/movie/${this.movieId}/user-map`),
                fetch(`/api/comments/movie/${this.movieId}/reactions`)
            ]);

            if (ratingsRes.status === 'fulfilled' && ratingsRes.value.ok) {
                this.userRatingsMap = await ratingsRes.value.json();
            }

            if (reactionsRes.status === 'fulfilled' && reactionsRes.value.ok) {
                const rxData = await reactionsRes.value.json();
                if (rxData && rxData.reactions) {
                    // Hợp nhất server reactions với local reactions
                    this.reactionsMap = Object.assign({}, this.reactionsMap, rxData.reactions);
                    try {
                        localStorage.setItem('ffilm_rx_' + this.movieId, JSON.stringify(this.reactionsMap));
                    } catch (e) {}
                }
            }

            if (commentsRes.status === 'fulfilled' && commentsRes.value.ok) {
                const data = await commentsRes.value.json();
                if (data.success) {
                    this.rawComments = data.comments || [];
                    this.updateCommentCount(data.count || this.rawComments.length);
                    this.renderComments();
                } else {
                    this.showError('Không thể tải bình luận');
                }
            } else {
                throw new Error('Lỗi kết nối khi tải bình luận');
            }
        } catch (error) {
            console.error('[CommentHandler] Error loading comments:', error);
            if (this.commentList) {
                this.commentList.innerHTML = `
                    <div style="text-align: center; color: #ff6b6b; padding: 20px">
                        <i class="fas fa-exclamation-circle"></i> ${error.message}
                    </div>
                `;
            }
        }
    }

    updateAuthorRatingBadges(userId, rating) {
        const commentItems = document.querySelectorAll(`.comment-item-thread[data-user-id="${userId}"]`);
        commentItems.forEach(item => {
            const metaEl = item.querySelector('.comment-card-meta');
            if (!metaEl) return;
            let badge = metaEl.querySelector('.comment-user-rating-badge');
            if (rating) {
                if (!badge) {
                    badge = document.createElement('span');
                    badge.className = 'comment-user-rating-badge';
                    metaEl.appendChild(badge);
                }
                badge.innerHTML = `<i class="fas fa-star"></i> ${rating}/5`;
                badge.title = `Đã đánh giá ${rating}/5 sao`;
            } else if (badge) {
                badge.remove();
            }
        });
    }

    getConsistentUserColorClass(name) {
        if (!name) return 'c-0';
        let hash = 0;
        for (let i = 0; i < name.length; i++) {
            hash = name.charCodeAt(i) + ((hash << 5) - hash);
        }
        return 'c-' + (Math.abs(hash) % 6);
    }

    /**
     * Render toàn bộ danh sách comments theo cấu trúc thread (flat threading)
     * Reply-of-reply được flatten vào thread của root comment
     */
    renderComments() {
        if (!this.commentList) return;
        if (!this.rawComments || this.rawComments.length === 0) {
            this.commentList.innerHTML = `
                <div class="comment-empty-state">
                    <i class="far fa-comment-dots" style="font-size: 2.2rem; color: #555; margin-bottom: 12px; display: block;"></i>
                    <p style="margin: 0; color: #888; font-size: 0.95rem;">Chưa có bình luận nào cho phim này.</p>
                    <span style="color: #555; font-size: 0.85rem; margin-top: 4px; display: block;">Hãy là người đầu tiên chia sẻ cảm nghĩ của bạn!</span>
                </div>
            `;
            return;
        }

        // Build lookup: commentID -> comment
        const commentMap = {};
        this.rawComments.forEach(c => { commentMap[c.commentID] = c; });

        // Get direct parentId from comment object
        const getParentId = (c) => {
            let pid = c.parentCommentId ?? (c.parentComment ? (c.parentComment.commentID ?? c.parentComment.id) : null);
            return pid || null;
        };

        // Walk up to find the root comment ID
        const getRootId = (c) => {
            let pid = getParentId(c);
            if (!pid) return null;
            let steps = 0;
            while (pid && commentMap[pid] && getParentId(commentMap[pid]) && steps < 20) {
                pid = getParentId(commentMap[pid]);
                steps++;
            }
            return pid;
        };

        const rootComments = [];
        const repliesByRoot = {}; // rootId -> flat list of all replies in thread

        this.rawComments.forEach(c => {
            const parentId = getParentId(c);
            if (!parentId) {
                rootComments.push(c);
            } else {
                const rootId = getRootId(c) || parentId;
                // Annotate reply with @mention name
                const directParent = commentMap[parentId];
                c._replyToName = directParent?.user?.userName || null;
                c._replyToId = parentId;
                if (!repliesByRoot[rootId]) repliesByRoot[rootId] = [];
                repliesByRoot[rootId].push(c);
            }
        });

        // Sort root comments
        if (this.activeSort === 'top') {
            rootComments.sort((a, b) => {
                const rxA = (this.reactionsMap[a.commentID]?.totalCount || 0) * 2 + (repliesByRoot[a.commentID]?.length || 0) * 3;
                const rxB = (this.reactionsMap[b.commentID]?.totalCount || 0) * 2 + (repliesByRoot[b.commentID]?.length || 0) * 3;
                return rxB - rxA;
            });
        } else {
            rootComments.sort((a, b) => new Date(b.createAt) - new Date(a.createAt));
        }

        // Sort replies oldest-first within each thread
        Object.values(repliesByRoot).forEach(arr =>
            arr.sort((a, b) => new Date(a.createAt) - new Date(b.createAt))
        );

        // Render HTML
        this.commentList.innerHTML = rootComments.map((comment, idx) => {
            const replies = repliesByRoot[comment.commentID] || [];
            return this.createRootCommentHTML(comment, replies, idx);
        }).join('');

        this.attachCommentEventListeners();
    }

    /**
     * Tạo HTML cho 1 Root Comment (kèm nhánh phản hồi)
     */
    createRootCommentHTML(comment, replies, idx) {
        const userName = comment.user?.userName || 'Ẩn danh';
        const userInitial = userName.charAt(0).toUpperCase();
        const createAt = this.formatTimeAgo(comment.createAt);
        const content = this.escapeHtml(comment.content);
        const commentUserId = comment.user?.userID || comment.user?.id;

        const isOwner = this.currentUserId && (this.currentUserId === commentUserId);

        // Avatar: nếu là chính user, luôn hiển thị red gradient rực rỡ đặc trưng của FFilm
        let avatarHTML = '';
        if (comment.user?.avatar) {
            avatarHTML = `<img src="${comment.user.avatar}" class="comment-avatar-circle ${isOwner ? 'current-user-avatar' : ''}" style="object-fit: cover;" alt="${userName}" />`;
        } else {
            avatarHTML = `<div class="comment-avatar-circle ${isOwner ? 'current-user-avatar' : this.getConsistentUserColorClass(userName)}">${userInitial}</div>`;
        }

        const userRating = this.userRatingsMap ? this.userRatingsMap[commentUserId] : null;
        const ratingBadgeHTML = userRating ? `
            <span class="comment-user-rating-badge" title="Đã đánh giá ${userRating}/5 sao">
                <i class="fas fa-star"></i> ${userRating}/5
            </span>
        ` : '';

        // Reaction info
        const rxData = this.reactionsMap[comment.commentID] || { reactions: {}, totalCount: 0, userReaction: null };
        const userReaction = rxData.userReaction;
        const likeBtnText = userReaction ? `${userReaction} ${this.getReactionName(userReaction)}` : 'Thích';
        const likeBtnClass = userReaction ? `reacted ${this.getReactionClass(userReaction)}` : '';

        const reactionsBadgeHTML = rxData.totalCount > 0 ? `
            <div class="reactions-summary-badge" title="${rxData.totalCount} lượt bày tỏ cảm xúc">
                <span class="badge-icons">${this.getTopReactionIcons(rxData.reactions)}</span>
                <span class="badge-count">${rxData.totalCount}</span>
            </div>
        ` : '';

        let actionMenu = '';
        if (isOwner) {
            actionMenu = `
                <div class="comment-actions-menu">
                    <button class="btn-action-icon" title="Chỉnh sửa" onclick="window.commentHandler.toggleEdit(${comment.commentID})">
                        <i class="fas fa-edit"></i>
                    </button>
                    <button class="btn-action-icon delete" title="Xóa" onclick="window.commentHandler.requestDelete(${comment.commentID})">
                        <i class="fas fa-trash"></i>
                    </button>
                </div>
            `;
        }

        // Replies section
        const hasReplies = replies && replies.length > 0;
        const isExpanded = this.expandedReplies.has(comment.commentID);
        let repliesSectionHTML = '';

        if (hasReplies) {
            const repliesHTML = replies.map((reply, rIdx) => this.createReplyHTML(reply, rIdx)).join('');
            repliesSectionHTML = `
                <div class="replies-branch-wrapper" id="replies-wrapper-${comment.commentID}">
                    <button type="button" class="btn-toggle-replies" onclick="window.commentHandler.toggleReplies(${comment.commentID})">
                        <i class="fas ${isExpanded ? 'fa-chevron-up' : 'fa-chevron-down'}"></i>
                        <span>${isExpanded ? 'Thu gọn phản hồi' : `Xem thêm ${replies.length} phản hồi`}</span>
                    </button>
                    <div class="replies-thread-container" id="replies-container-${comment.commentID}" style="display: ${isExpanded ? 'flex' : 'none'};">
                        ${repliesHTML}
                    </div>
                </div>
            `;
        }

        return `
            <div class="comment-item-thread" id="comment-${comment.commentID}" data-comment-id="${comment.commentID}" data-user-id="${commentUserId || ''}">
                ${avatarHTML}
                <div class="comment-card-content">
                    <div class="comment-card-header">
                        <div class="comment-card-meta">
                            <span class="comment-author-name">${userName}</span>
                            ${ratingBadgeHTML}
                            <span class="comment-dot-divider">·</span>
                            <span class="comment-time-ago">${createAt}</span>
                        </div>
                        ${actionMenu}
                    </div>

                    <div class="comment-body-content" id="body-${comment.commentID}">
                        <div class="comment-body-text">${content}</div>
                    </div>

                    <div class="edit-box" id="edit-box-${comment.commentID}" style="display: none; margin: 8px 0;">
                        <textarea class="comment-input-line" style="border: 1px solid #444; border-radius: 8px; padding: 8px;">${content}</textarea>
                        <div class="reply-actions-bar">
                            <button class="btn-cancel-reply" onclick="window.commentHandler.cancelEdit(${comment.commentID})">Hủy</button>
                            <button class="btn-submit-reply" onclick="window.commentHandler.saveEdit(${comment.commentID})">Lưu</button>
                        </div>
                    </div>

                    <div class="comment-action-row">
                        <div class="reaction-btn-wrapper">
                            <button type="button" class="btn-like-trigger ${likeBtnClass}" onclick="window.commentHandler.quickLike(${comment.commentID})">
                                ${likeBtnText}
                            </button>
                            ${this.renderFloatingReactionPalette(comment.commentID)}
                        </div>

                        <button type="button" class="btn-reply-trigger" onclick="window.commentHandler.toggleReplyBox(${comment.commentID}, '${userName}')">
                            Trả lời
                        </button>

                        ${reactionsBadgeHTML}
                    </div>

                    <!-- Inline Reply Input Box (Hidden by default) -->
                    <div id="reply-box-container-${comment.commentID}"></div>

                    ${repliesSectionHTML}
                </div>
            </div>
        `;
    }

    /**
     * Tạo HTML cho 1 Reply Comment (phản hồi lồng có đường nối cong)
     */
    createReplyHTML(reply, idx) {
        const userName = reply.user?.userName || 'Ẩn danh';
        const userInitial = userName.charAt(0).toUpperCase();
        const createAt = this.formatTimeAgo(reply.createAt);
        const content = this.escapeHtml(reply.content);
        const commentUserId = reply.user?.userID || reply.user?.id;

        const isOwner = this.currentUserId && (this.currentUserId === commentUserId);

        let avatarHTML = '';
        if (reply.user?.avatar) {
            avatarHTML = `<img src="${reply.user.avatar}" class="comment-avatar-circle ${isOwner ? 'current-user-avatar' : ''}" style="width: 32px; height: 32px; object-fit: cover;" alt="${userName}" />`;
        } else {
            avatarHTML = `<div class="comment-avatar-circle ${isOwner ? 'current-user-avatar' : this.getConsistentUserColorClass(userName)}" style="width: 32px; height: 32px; font-size: 0.85rem;">${userInitial}</div>`;
        }

        const userRating = this.userRatingsMap ? this.userRatingsMap[commentUserId] : null;
        const ratingBadgeHTML = userRating ? `
            <span class="comment-user-rating-badge" title="Đã đánh giá ${userRating}/5 sao">
                <i class="fas fa-star"></i> ${userRating}/5
            </span>
        ` : '';

        const rxData = this.reactionsMap[reply.commentID] || { reactions: {}, totalCount: 0, userReaction: null };
        const userReaction = rxData.userReaction;
        const likeBtnText = userReaction ? `${userReaction} ${this.getReactionName(userReaction)}` : 'Thích';
        const likeBtnClass = userReaction ? `reacted ${this.getReactionClass(userReaction)}` : '';

        const reactionsBadgeHTML = rxData.totalCount > 0 ? `
            <div class="reactions-summary-badge" title="${rxData.totalCount} lượt bày tỏ cảm xúc">
                <span class="badge-icons">${this.getTopReactionIcons(rxData.reactions)}</span>
                <span class="badge-count">${rxData.totalCount}</span>
            </div>
        ` : '';

        let actionMenu = '';
        if (isOwner) {
            actionMenu = `
                <div class="comment-actions-menu">
                    <button class="btn-action-icon delete" title="Xóa" onclick="window.commentHandler.requestDelete(${reply.commentID})">
                        <i class="fas fa-trash"></i>
                    </button>
                </div>
            `;
        }

        // Root comment ID for flat threading (reply button always opens on root)
        const rootIdForReply = reply._rootId || (reply.parentCommentId ?? reply.commentID);
        // @mention for nested replies
        const mentionHTML = reply._replyToName
            ? `<span class="reply-mention">@${this.escapeHtml(reply._replyToName)}</span> `
            : '';

        return `
            <div class="reply-item-thread" id="comment-${reply.commentID}" data-comment-id="${reply.commentID}">
                ${avatarHTML}
                <div class="comment-card-content">
                    <div class="comment-card-header">
                        <div class="comment-card-meta">
                            <span class="comment-author-name" style="font-size: 0.9rem;">${userName}</span>
                            ${ratingBadgeHTML}
                            <span class="comment-dot-divider">·</span>
                            <span class="comment-time-ago">${createAt}</span>
                        </div>
                        ${actionMenu}
                    </div>

                    <div class="comment-body-content">
                        <div class="comment-body-text" style="font-size: 0.92rem;">${mentionHTML}${content}</div>
                    </div>

                    <div class="comment-action-row" style="font-size: 0.82rem;">
                        <div class="reaction-btn-wrapper">
                            <button type="button" class="btn-like-trigger ${likeBtnClass}" onclick="window.commentHandler.quickLike(${reply.commentID})">
                                ${likeBtnText}
                            </button>
                            ${this.renderFloatingReactionPalette(reply.commentID)}
                        </div>

                        <button type="button" class="btn-reply-trigger" onclick="window.commentHandler.toggleReplyBox(${rootIdForReply}, '${userName}')">
                            Trả lời
                        </button>

                        ${reactionsBadgeHTML}
                    </div>
                </div>
            </div>
        `;
    }

    /**
     * Bảng cảm xúc nổi (Floating Reaction Bar)
     */
    renderFloatingReactionPalette(commentId) {
        return `
            <div class="floating-reaction-bar" id="reaction-palette-${commentId}">
                <button type="button" class="reaction-emoji-btn" data-tooltip="Thích" onclick="window.commentHandler.sendReaction(${commentId}, '👍')">👍</button>
                <button type="button" class="reaction-emoji-btn" data-tooltip="Yêu thích" onclick="window.commentHandler.sendReaction(${commentId}, '❤️')">❤️</button>
                <button type="button" class="reaction-emoji-btn" data-tooltip="Thương thương" onclick="window.commentHandler.sendReaction(${commentId}, '😍')">😍</button>
                <button type="button" class="reaction-emoji-btn" data-tooltip="Haha" onclick="window.commentHandler.sendReaction(${commentId}, '😆')">😆</button>
                <button type="button" class="reaction-emoji-btn" data-tooltip="Buồn" onclick="window.commentHandler.sendReaction(${commentId}, '😢')">😢</button>
                <button type="button" class="reaction-emoji-btn" data-tooltip="Ngạc nhiên" onclick="window.commentHandler.sendReaction(${commentId}, '😮')">😮</button>
                <button type="button" class="reaction-emoji-btn" data-tooltip="Phẫn nộ" onclick="window.commentHandler.sendReaction(${commentId}, '😡')">😡</button>
            </div>
        `;
    }

    getReactionName(emoji) {
        switch (emoji) {
            case '👍': return 'Thích';
            case '❤️': return 'Yêu thích';
            case '😍': return 'Thương thương';
            case '😆': return 'Haha';
            case '😢': return 'Buồn';
            case '😮': return 'Ngạc nhiên';
            case '😡': return 'Phẫn nộ';
            default: return 'Thích';
        }
    }

    getReactionClass(emoji) {
        switch (emoji) {
            case '❤️': return 'love';
            case '😍': return 'care';
            case '😆': return 'haha';
            case '😢': return 'sad';
            case '😮': return 'wow';
            case '😡': return 'angry';
            default: return '';
        }
    }

    getTopReactionIcons(reactions) {
        if (!reactions) return '👍';
        const sorted = Object.entries(reactions)
            .filter(([_, count]) => count > 0)
            .sort((a, b) => b[1] - a[1])
            .slice(0, 3)
            .map(e => e[0]);
        return sorted.length > 0 ? sorted.join('') : '👍';
    }

    /**
     * Gửi cảm xúc (Reaction) kèm lưu trữ localStorage
     */
    async sendReaction(commentId, emoji) {
        if (!this.currentUserId) {
            alert('Bạn cần đăng nhập để bày tỏ cảm xúc');
            return;
        }

        try {
            // Optimistic update
            const curData = this.reactionsMap[commentId] || { reactions: {}, totalCount: 0, userReaction: null };
            const prevReaction = curData.userReaction;
            const newReaction = (prevReaction === emoji) ? null : emoji;

            const newReactions = { ...curData.reactions };
            if (prevReaction && newReactions[prevReaction]) {
                newReactions[prevReaction] = Math.max(0, newReactions[prevReaction] - 1);
            }
            if (newReaction) {
                newReactions[newReaction] = (newReactions[newReaction] || 0) + 1;
            }
            const newTotal = Object.values(newReactions).reduce((a, b) => a + b, 0);

            this.reactionsMap[commentId] = {
                reactions: newReactions,
                totalCount: newTotal,
                userReaction: newReaction
            };

            // Lưu ngay vào localStorage để F5 không bao giờ mất
            try {
                localStorage.setItem('ffilm_rx_' + this.movieId, JSON.stringify(this.reactionsMap));
            } catch (e) {}

            this.renderComments();

            // API Call
            const res = await fetch(`/api/comments/${commentId}/react`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ emoji })
            });

            if (res.ok) {
                const data = await res.json();
                if (data.success) {
                    this.reactionsMap[commentId] = {
                        reactions: data.reactions,
                        totalCount: data.totalCount,
                        userReaction: data.userReaction
                    };
                    try {
                        localStorage.setItem('ffilm_rx_' + this.movieId, JSON.stringify(this.reactionsMap));
                    } catch (e) {}
                    this.renderComments();
                }
            }
        } catch (err) {
            console.error('Reaction error:', err);
        }
    }

    quickLike(commentId) {
        const curData = this.reactionsMap[commentId];
        const userReaction = curData?.userReaction;
        this.sendReaction(commentId, userReaction ? userReaction : '👍');
    }

    /**
     * Mở / Đóng inline reply box
     */
    toggleReplyBox(parentCommentId, targetUserName) {
        if (!this.currentUserId) {
            alert('Bạn cần đăng nhập để phản hồi bình luận');
            return;
        }

        const container = document.getElementById(`reply-box-container-${parentCommentId}`);
        if (!container) return;

        if (this.activeReplyBoxId === parentCommentId && container.innerHTML.trim() !== '') {
            container.innerHTML = '';
            this.activeReplyBoxId = null;
            return;
        }

        this.activeReplyBoxId = parentCommentId;

        // Render reply box
        container.innerHTML = `
            <div class="inline-reply-box">
                <div class="comment-avatar-circle current-user-avatar" style="width: 32px; height: 32px; font-size: 0.82rem;">${this.currentUserInitial}</div>
                <div class="comment-input-wrapper" style="flex: 1;">
                    <div class="comment-input-field-relative">
                        <input type="text" id="reply-input-${parentCommentId}" class="comment-input-line" 
                               placeholder="Phản hồi cho ${targetUserName}..." autocomplete="off" />
                        <button type="button" class="btn-open-emoji-picker" id="btn-emoji-reply-${parentCommentId}">
                            <i class="far fa-smile"></i>
                        </button>
                    </div>
                    <div class="reply-actions-bar">
                        <button type="button" class="btn-cancel-reply" onclick="window.commentHandler.closeReplyBox(${parentCommentId})">Hủy</button>
                        <button type="button" class="btn-submit-reply" id="btn-send-reply-${parentCommentId}">Trả lời</button>
                    </div>
                </div>
            </div>
        `;

        const replyInput = document.getElementById(`reply-input-${parentCommentId}`);
        const replySubmit = document.getElementById(`btn-send-reply-${parentCommentId}`);
        const replyEmoji = document.getElementById(`btn-emoji-reply-${parentCommentId}`);

        if (replyInput) {
            replyInput.focus();
            replyInput.addEventListener('keypress', (e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    this.submitReply(parentCommentId, replyInput.value);
                }
            });
        }

        if (replySubmit && replyInput) {
            replySubmit.addEventListener('click', () => {
                this.submitReply(parentCommentId, replyInput.value);
            });
        }

        if (replyEmoji && replyInput) {
            replyEmoji.addEventListener('click', (e) => {
                e.stopPropagation();
                this.toggleEmojiPicker(replyEmoji, replyInput);
            });
        }
    }

    closeReplyBox(parentCommentId) {
        const container = document.getElementById(`reply-box-container-${parentCommentId}`);
        if (container) container.innerHTML = '';
        this.activeReplyBoxId = null;
    }

    async submitReply(parentCommentId, content) {
        if (!content || !content.trim()) return;

        try {
            const res = await fetch('/api/comments', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    movieId: this.movieId,
                    content: content.trim(),
                    parentCommentId: parentCommentId
                })
            });

            const data = await res.json();
            if (data.success && data.comment) {
                // Annotate with parentCommentId for immediate local rendering
                data.comment.parentCommentId = parentCommentId;
                this.rawComments.push(data.comment);
                this.expandedReplies.add(parentCommentId);
                this.closeReplyBox(parentCommentId);
                this.updateCommentCount(this.rawComments.length);
                this.renderComments();
            } else {
                alert(data.message || 'Không thể gửi phản hồi');
            }
        } catch (err) {
            console.error('Error submitting reply:', err);
            alert('Lỗi gửi phản hồi');
        }
    }

    toggleReplies(parentCommentId) {
        if (this.expandedReplies.has(parentCommentId)) {
            this.expandedReplies.delete(parentCommentId);
        } else {
            this.expandedReplies.add(parentCommentId);
        }
        this.renderComments();
    }

    /**
     * Submit Root Comment
     */
    async submitComment() {
        if (!this.commentInput || !this.submitBtn) return;
        const content = this.commentInput.value.trim();
        if (!content) return;

        this.submitBtn.disabled = true;

        try {
            const res = await fetch('/api/comments', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    movieId: this.movieId,
                    content: content
                })
            });

            const data = await res.json();
            if (data.success && data.comment) {
                this.commentInput.value = '';
                this.rawComments.unshift(data.comment);
                this.updateCommentCount(this.rawComments.length);
                this.renderComments();
            } else {
                alert(data.message || 'Không thể đăng bình luận');
            }
        } catch (err) {
            console.error('Error submitting comment:', err);
            alert('Lỗi gửi bình luận');
        } finally {
            this.submitBtn.disabled = false;
        }
    }

    /**
     * Emoji Picker Popover (Tận dụng EMOJI_CATEGORIES và EMOJI_DATA từ emoji-data.js)
     */
    toggleEmojiPicker(anchorBtn, targetInput) {
        if (this.activePickerPopover) {
            this.closeEmojiPicker();
            return;
        }

        this.activeEmojiTargetInput = targetInput;

        const popover = document.createElement('div');
        popover.className = 'emoji-picker-popover';

        // Header
        const header = document.createElement('div');
        header.className = 'emoji-picker-header';
        header.innerHTML = `
            <span class="emoji-picker-title">Biểu cảm</span>
            <button type="button" class="btn-close-emoji-picker">&times;</button>
        `;
        header.querySelector('.btn-close-emoji-picker').onclick = () => this.closeEmojiPicker();
        popover.appendChild(header);

        // Categories tabs
        const categories = window.EMOJI_CATEGORIES || [
            { id: 'smileys', name: 'Mặt cười', icon: '😀' },
            { id: 'people', name: 'Người', icon: '👋' },
            { id: 'food', name: 'Đồ ăn', icon: '🍎' },
            { id: 'activities', name: 'Hoạt động', icon: '⚽' },
            { id: 'symbols', name: 'Biểu tượng', icon: '❤️' }
        ];

        const catTabs = document.createElement('div');
        catTabs.className = 'emoji-category-tabs';

        categories.forEach((cat, cIdx) => {
            const catBtn = document.createElement('button');
            catBtn.type = 'button';
            catBtn.className = `emoji-cat-btn ${cIdx === 0 ? 'active' : ''}`;
            catBtn.title = cat.name;
            catBtn.textContent = cat.icon;
            catBtn.onclick = () => {
                catTabs.querySelectorAll('.emoji-cat-btn').forEach(b => b.classList.remove('active'));
                catBtn.classList.add('active');
                this.renderEmojiGrid(cat.id, popover);
            };
            catTabs.appendChild(catBtn);
        });
        popover.appendChild(catTabs);

        // Grid viewport
        const grid = document.createElement('div');
        grid.className = 'emoji-grid-viewport';
        grid.id = 'emojiGridViewport';
        popover.appendChild(grid);

        // Append popover next to anchor
        anchorBtn.parentElement.appendChild(popover);
        this.activePickerPopover = popover;

        // Render first category
        this.renderEmojiGrid(categories[0].id, popover);
    }

    renderEmojiGrid(categoryId, popover) {
        const grid = popover.querySelector('#emojiGridViewport');
        if (!grid) return;
        grid.innerHTML = '';

        const allEmojis = window.EMOJI_DATA || [
            { emoji: '😀', category: 'smileys' },
            { emoji: '😃', category: 'smileys' },
            { emoji: '😄', category: 'smileys' },
            { emoji: '😁', category: 'smileys' },
            { emoji: '😆', category: 'smileys' },
            { emoji: '😅', category: 'smileys' },
            { emoji: '🤣', category: 'smileys' },
            { emoji: '😂', category: 'smileys' },
            { emoji: '🙂', category: 'smileys' },
            { emoji: '😊', category: 'smileys' },
            { emoji: '😍', category: 'smileys' },
            { emoji: '🥰', category: 'smileys' },
            { emoji: '😘', category: 'smileys' },
            { emoji: '👍', category: 'people' },
            { emoji: '❤️', category: 'symbols' }
        ];

        const filtered = allEmojis.filter(e => e.category === categoryId);
        filtered.forEach(item => {
            const cell = document.createElement('button');
            cell.type = 'button';
            cell.className = 'emoji-cell-btn';
            cell.textContent = item.emoji;
            cell.title = item.name || '';
            cell.onclick = (e) => {
                e.stopPropagation();
                this.insertEmoji(item.emoji);
            };
            grid.appendChild(cell);
        });
    }

    insertEmoji(emoji) {
        if (!this.activeEmojiTargetInput) return;
        const input = this.activeEmojiTargetInput;
        const start = input.selectionStart || input.value.length;
        const end = input.selectionEnd || input.value.length;
        const text = input.value;
        input.value = text.substring(0, start) + emoji + text.substring(end);
        input.selectionStart = input.selectionEnd = start + emoji.length;
        input.focus();
        input.dispatchEvent(new Event('input'));
    }

    closeEmojiPicker() {
        if (this.activePickerPopover) {
            this.activePickerPopover.remove();
            this.activePickerPopover = null;
            this.activeEmojiTargetInput = null;
        }
    }

    /**
     * Edit / Delete handling
     */
    toggleEdit(commentId) {
        const bodyEl = document.getElementById(`body-${commentId}`);
        const editBox = document.getElementById(`edit-box-${commentId}`);
        if (bodyEl && editBox) {
            bodyEl.style.display = 'none';
            editBox.style.display = 'block';
        }
    }

    cancelEdit(commentId) {
        const bodyEl = document.getElementById(`body-${commentId}`);
        const editBox = document.getElementById(`edit-box-${commentId}`);
        if (bodyEl && editBox) {
            bodyEl.style.display = 'block';
            editBox.style.display = 'none';
        }
    }

    async saveEdit(commentId) {
        const editBox = document.getElementById(`edit-box-${commentId}`);
        const textarea = editBox?.querySelector('textarea');
        if (!textarea) return;

        const newContent = textarea.value.trim();
        if (!newContent) return;

        try {
            const res = await fetch(`/api/comments/${commentId}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ content: newContent })
            });

            if (res.ok) {
                const target = this.rawComments.find(c => c.commentID === commentId);
                if (target) target.content = newContent;
                this.cancelEdit(commentId);
                this.renderComments();
            } else {
                alert('Không thể cập nhật bình luận');
            }
        } catch (err) {
            console.error('Error updating comment:', err);
        }
    }

    requestDelete(commentId) {
        this.pendingDeleteId = commentId;
        if (this.modalEl) {
            this.modalEl.style.display = 'flex';
        } else if (confirm('Bạn có chắc muốn xóa bình luận này?')) {
            this.confirmDelete();
        }
    }

    closeConfirmModal() {
        this.pendingDeleteId = null;
        if (this.modalEl) this.modalEl.style.display = 'none';
    }

    async confirmDelete() {
        if (!this.pendingDeleteId) return;
        const idToDelete = this.pendingDeleteId;
        this.closeConfirmModal();

        try {
            const res = await fetch(`/api/comments/${idToDelete}`, { method: 'DELETE' });
            if (res.ok) {
                this.rawComments = this.rawComments.filter(c => c.commentID !== idToDelete);
                this.updateCommentCount(this.rawComments.length);
                this.renderComments();
            } else {
                alert('Không thể xóa bình luận');
            }
        } catch (err) {
            console.error('Error deleting comment:', err);
        }
    }

    updateCommentCount(count) {
        if (this.commentCount) this.commentCount.textContent = count;
    }

    attachCommentEventListeners() {
        // Extra post-render actions
    }

    formatTimeAgo(dateString) {
        if (!dateString) return 'Vừa xong';
        const date = new Date(dateString);
        const now = new Date();
        const diffSecs = Math.floor((now - date) / 1000);

        if (diffSecs < 60) return 'Vừa xong';
        const diffMins = Math.floor(diffSecs / 60);
        if (diffMins < 60) return `${diffMins} phút trước`;
        const diffHours = Math.floor(diffMins / 60);
        if (diffHours < 24) return `${diffHours} giờ trước`;
        const diffDays = Math.floor(diffHours / 24);
        if (diffDays < 7) return `${diffDays} ngày trước`;
        const diffWeeks = Math.floor(diffDays / 7);
        if (diffWeeks < 52) return `${diffWeeks} tuần trước`;
        const diffYears = Math.floor(diffWeeks / 52);
        return `${diffYears} năm trước`;
    }

    escapeHtml(text) {
        if (!text) return '';
        return text
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }
}

// Auto init when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    new CommentHandler();
});
