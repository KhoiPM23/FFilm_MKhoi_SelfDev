/**
 * Lấy URL gốc của trang web (ví dụ: https://yourdomain.com)
 * @returns {string} Base URL của ứng dụng
 */
function getBaseUrl() {
    // Trong môi trường development/local: http://localhost:8080
    // Trong môi trường production: https://yourdomain.com
    const protocol = window.location.protocol;
    const host = window.location.host;
    return `${protocol}//${host}`;
}

/**
 * Hiển thị modal chia sẻ và cập nhật các liên kết chia sẻ.
 * Được gọi từ nút "Chia sẻ" trong hover-card.html
 * @param {HTMLElement} buttonElement - Nút "Chia sẻ" đã được click.
 */
function showShareModal(buttonElement) {
    const movieId = buttonElement.getAttribute('data-movie-id');
    const movieTitle = buttonElement.getAttribute('data-movie-title');
    
    // Tạo đường dẫn chi tiết của bộ phim
    const detailUrl = `${getBaseUrl()}/movie/detail/${movieId}`; 
    
    // Cập nhật các liên kết chia sẻ và input URL
    updateShareLinks(detailUrl, movieTitle);

    // Hiển thị modal
    const modalOverlay = document.getElementById('shareModalOverlay');
    if (modalOverlay) {
        modalOverlay.classList.add('active');
        // Tùy chọn: Ngăn chặn cuộn body khi modal mở
        document.body.style.overflow = 'hidden'; 
    }
}

/**
 * Cập nhật các URL chia sẻ cho các mạng xã hội.
 * @param {string} url - URL của trang chi tiết phim.
 * @param {string} title - Tiêu đề của bộ phim.
 */
function updateShareLinks(url, title) {
    const encodedUrl = encodeURIComponent(url);
    const encodedTitle = encodeURIComponent(`Xem ngay: ${title} trên FFilm!`);
    const encodedEmailBody = encodeURIComponent(`Chào bạn, tôi muốn chia sẻ bộ phim thú vị này: ${title}. Bạn có thể xem tại đây: ${url}`);

    // 1. Facebook
    const facebookLink = document.getElementById('shareFacebook');
    if (facebookLink) {
        facebookLink.href = `https://www.facebook.com/sharer/sharer.php?u=${encodedUrl}`;
    }

    // 2. X (Twitter)
    const xLink = document.getElementById('shareX');
    if (xLink) {
        xLink.href = `https://twitter.com/intent/tweet?text=${encodedTitle}&url=${encodedUrl}`;
    }

    // 3. Email
    const emailLink = document.getElementById('shareEmail');
    if (emailLink) {
        emailLink.href = `mailto:?subject=${encodedTitle}&body=${encodedEmailBody}`;
    }
    
    // 4. Input URL
    const urlInput = document.getElementById('shareUrlInput');
    if (urlInput) {
        urlInput.value = url;
    }
}

/**
 * Ẩn modal chia sẻ.
 */
function closeShareModal() {
    const modalOverlay = document.getElementById('shareModalOverlay');
    if (modalOverlay) {
        modalOverlay.classList.remove('active');
        // Cho phép body cuộn lại
        document.body.style.overflow = ''; 
    }
}

/**
 * Sao chép URL chia sẻ vào clipboard.
 */
function copyShareUrl() {
    const urlInput = document.getElementById('shareUrlInput');
    const copyButton = document.getElementById('copyButton');
    
    if (urlInput && copyButton) {
        // Chọn nội dung trong ô input
        urlInput.select();
        urlInput.setSelectionRange(0, 99999); // Dành cho thiết bị di động
        
        // Sao chép
        try {
            navigator.clipboard.writeText(urlInput.value);
            
            // Đổi trạng thái nút thành "Đã sao chép" trong thời gian ngắn
            const originalText = copyButton.textContent;
            copyButton.textContent = 'Đã sao chép!';
            copyButton.style.backgroundColor = '#4CAF50'; // Màu xanh lá
            
            setTimeout(() => {
                copyButton.textContent = originalText;
                copyButton.style.backgroundColor = '#e50914'; // Trở lại màu đỏ
            }, 2000);
            
        } catch (err) {
            console.error('Không thể sao chép văn bản:', err);
            alert('Không thể sao chép tự động. Vui lòng sao chép thủ công.');
        }
    }
}