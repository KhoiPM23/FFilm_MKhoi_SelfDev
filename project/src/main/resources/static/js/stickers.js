
// Categories cho Tenor (tương tự Zalo)
window.TENOR_CATEGORIES = {
    "popular": { name: "Phổ biến", search: "trending" },
    "emotions": { name: "Cảm xúc", search: "emotions" },
    "animals": { name: "Động vật", search: "cute animals" },
    "food": { name: "Đồ ăn", search: "food" },
    "reactions": { name: "Phản ứng", search: "reactions" },
    "celebration": { name: "Chúc mừng", search: "celebration" },
    "love": { name: "Tình yêu", search: "love" },
    "funny": { name: "Hài hước", search: "funny" },
    "greetings": { name: "Chào hỏi", search: "greetings" }
};

// Cache cho stickers đã tải
window.STICKER_CACHE = {
    popular: [],
    searchResults: {},
    recent: JSON.parse(localStorage.getItem('recentStickers') || '[]')
};

// Hàm chính để tải stickers từ Tenor
window.loadTenorStickers = async function(category = 'popular', searchQuery = '', limit = 24) {
    const cacheKey = searchQuery || category;
    
    // Kiểm tra cache trước
    if (!searchQuery && window.STICKER_CACHE[category]?.length > 0) {
        return window.STICKER_CACHE[category];
    }
    
    try {
        // Gọi API proxy server của chúng ta (để bảo vệ API key)
        const endpoint = searchQuery 
            ? `/api/tenor/search?q=${encodeURIComponent(searchQuery)}&limit=${limit}`
            : `/api/tenor/trending?category=${category}&limit=${limit}`;
        
        const response = await fetch(endpoint);
        const data = await response.json();
        
        // Parse dữ liệu từ Tenor
        const stickers = data.results?.map(item => ({
            id: item.id,
            url: item.media_formats?.gif?.url || item.media_formats?.tinygif?.url,
            preview: item.media_formats?.tinygif_preview?.url,
            width: item.media_formats?.gif?.dims?.[0] || 200,
            height: item.media_formats?.gif?.dims?.[1] || 200,
            tags: item.tags || [],
            source: 'tenor'
        })).filter(s => s.url) || [];
        
        // Lưu vào cache
        if (!searchQuery) {
            window.STICKER_CACHE[category] = stickers;
        } else {
            window.STICKER_CACHE.searchResults[searchQuery] = stickers;
        }
        
        return stickers;
    } catch (error) {
        console.error('Lỗi tải stickers từ Tenor:', error);
        return [];
    }
};

// Hàm tìm kiếm stickers real-time
window.searchTenorStickers = async function(query, limit = 12) {
    if (!query || query.length < 2) return [];
    
    // Kiểm tra cache
    if (window.STICKER_CACHE.searchResults[query]) {
        return window.STICKER_CACHE.searchResults[query];
    }
    
    return await window.loadTenorStickers('search', query, limit);
};

// Hàm lấy stickers gợi ý dựa trên tin nhắn
window.getStickerSuggestions = async function(message) {
    if (!message || message.length < 2) return [];
    
    const keywords = extractKeywords(message);
    if (keywords.length === 0) return [];
    
    // Lấy suggestions từ keyword đầu tiên
    const keyword = keywords[0];
    const searchTerm = window.STICKER_SUGGESTIONS_MAP[keyword] || keyword;
    
    return await window.searchTenorStickers(searchTerm, 8);
};

// Map từ khóa -> search terms cho Tenor
window.STICKER_SUGGESTIONS_MAP = {
    "cười": "laughing funny",
    "vui": "happy excited",
    "buồn": "sad crying",
    "khóc": "crying tears",
    "yêu": "love heart",
    "tim": "heart love",
    "ok": "ok thumbs up",
    "like": "like thumbs up",
    "cảm ơn": "thank you",
    "hoan hô": "cheering celebration",
    "wink": "wink flirting",
    "dễ thương": "cute adorable",
    "ngon": "yummy delicious",
    "ngầu": "cool awesome",
    "giận": "angry mad",
    "tức": "angry rage",
    "sợ": "scared afraid",
    "hoảng": "panic shocked",
    "ngượng": "embarrassed shy",
    "chó": "dog puppy",
    "mèo": "cat kitten",
    "cún": "puppy dog",
    "thỏ": "bunny rabbit",
    "cáo": "fox",
    "gấu": "bear teddy",
    "heo": "pig cute",
    "hổ": "tiger",
    "ngựa": "horse",
    "hamburger": "burger food",
    "bánh": "cake dessert",
    "kem": "ice cream",
    "kẹo": "candy sweet",
    "party": "party celebration",
    "tiệc": "party",
    "quà": "gift present",
    "pháo hoa": "fireworks",
    "noel": "christmas",
    "halloween": "halloween",
    "ý tưởng": "idea lightbulb",
    "bom": "bomb explosion",
    "ngủ": "sleep tired",
    "mồ hôi": "sweat nervous",
    "cơ bắp": "muscle strong",
    "khỏe": "strong muscle",
    "chóng mặt": "dizzy",
    "nói": "talking",
    "suy nghĩ": "thinking",
    "hôn": "kiss love",
    "kim cương": "diamond shiny",
    "hoa": "flower beautiful",
    "chạy": "running fast",
    "bóng đá": "soccer football",
    "bóng rổ": "basketball",
    "tennis": "tennis",
    "bơi": "swimming",
    "golf": "golf"
};

// Hàm trích xuất từ khóa từ tin nhắn
function extractKeywords(message) {
    const words = message.toLowerCase().split(/\s+/);
    const keywords = [];
    
    words.forEach(word => {
        // Tìm từ khóa trong map
        for (const [key, value] of Object.entries(window.STICKER_SUGGESTIONS_MAP)) {
            if (key.includes(word) || word.includes(key)) {
                keywords.push(key);
                break;
            }
        }
    });
    
    return [...new Set(keywords)]; // Remove duplicates
}

// Lưu sticker vào recent
window.addToRecentStickers = function(sticker) {
    // Kiểm tra trùng
    const exists = window.STICKER_CACHE.recent.some(s => s.id === sticker.id);
    if (!exists) {
        window.STICKER_CACHE.recent.unshift(sticker);
        // Giới hạn 20 stickers gần đây
        window.STICKER_CACHE.recent = window.STICKER_CACHE.recent.slice(0, 20);
        localStorage.setItem('recentStickers', JSON.stringify(window.STICKER_CACHE.recent));
    }
};

// Lấy stickers gần đây
window.getRecentStickers = function() {
    return window.STICKER_CACHE.recent;
};