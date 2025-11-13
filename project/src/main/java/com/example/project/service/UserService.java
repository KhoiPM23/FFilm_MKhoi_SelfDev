package com.example.project.service;

import com.example.project.dto.UserRegisterDto; // THÊM DÒNG NÀY
import com.example.project.model.User;
import com.example.project.repository.PasswordResetTokenRepository;
import com.example.project.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import com.example.project.model.PasswordResetToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.Optional;
import java.util.UUID;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.example.project.dto.UserProfileUpdateDto; 
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
 
@Service
@RequiredArgsConstructor
public class UserService {

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;
    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private EmailService emailService;

    private final UserRepository userRepository;

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public boolean isPasswordValid(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Page<User> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    public User getUserById(int id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    public List<User> getUserByRole(String role) {
        return userRepository.findByRole(role);
    }

    public Page<User> getUserByRole(String role, Pageable pageable) {
        return userRepository.findByRole(role, pageable);
    }

    public List<User> getUserByStatus(boolean status) {
        return userRepository.findByStatus(status);
    }

    public Page<User> getUserByStatus(boolean status, Pageable pageable) {
        return userRepository.findByStatus(status, pageable);
    }

    public User createUser(UserRegisterDto dto) {
        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already exists");
        }

        User user = new User();
        user.setUserName(dto.getUserName());
        user.setEmail(dto.getEmail());
        user.setPassword(dto.getPassword());
        user.setPhoneNumber(dto.getPhoneNumber());

        return userRepository.save(user);
    }

    public User updateUser(int id, User updateUser) {
        User existing = getUserById(id);
        existing.setUserName(updateUser.getUserName());
        existing.setEmail(updateUser.getEmail());
        if (updateUser.getPassword() != null && !updateUser.getPassword().isBlank()) {
            existing.setPassword(updateUser.getPassword());
        }
        existing.setRole(updateUser.getRole());
        existing.setStatus(updateUser.isStatus());
        existing.setPhoneNumber(updateUser.getPhoneNumber());
        return userRepository.save(existing);
    }

    public void deleteUser(int id) {
        if (!userRepository.existsById(id)) {
            throw new IllegalArgumentException("User not found");
        }
        userRepository.deleteById(id);
    }

    @Transactional
    public boolean processForgotPassword(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            // 1. Xóa token cũ (nếu có)
            // >>>>>> DÒNG CẦN THÊM ĐỂ KHẮC PHỤC LỖI 500 <<<<<<
            tokenRepository.deleteByUser(user);

            // 2. Tạo token mới (sử dụng UUID để đảm bảo tính duy nhất)
            String token = UUID.randomUUID().toString();
            PasswordResetToken resetToken = new PasswordResetToken(token, user);

            // 3. Lưu token
            tokenRepository.save(resetToken);

            // 4. Gửi email
            try {
                emailService.sendResetPasswordEmail(user.getEmail(), token);
            } catch (Exception e) { // <-- Thay vì chỉ bắt MailException
                // Ghi log lỗi vào console để bạn biết (Vui lòng kiểm tra log này!)
                System.err.println("LỖI KHÔNG XÁC ĐỊNH TRONG GỬI EMAIL: " + e.getMessage());
                e.printStackTrace(); // In toàn bộ Stack Trace gốc
                // KHÔNG THROW: để luồng vẫn trả về true, tránh lỗi 500 cho người dùng.
            }

            return true;
        }
        return false;
    }

    /**
     * Xác thực token đặt lại mật khẩu.
     */
    public Optional<PasswordResetToken> validatePasswordResetToken(String token) {
        Optional<PasswordResetToken> tokenOptional = tokenRepository.findByToken(token);

        if (tokenOptional.isPresent()) {
            PasswordResetToken resetToken = tokenOptional.get();
            if (!resetToken.isExpired()) {
                return tokenOptional; // Token hợp lệ và chưa hết hạn
            }
            // Token hết hạn, dọn dẹp (tùy chọn)
            tokenRepository.delete(resetToken);
        }
        return Optional.empty(); // Token không hợp lệ hoặc đã hết hạn
    }

    /**
     * Cập nhật mật khẩu mới và xóa token.
     */
    @Transactional
    public void resetPassword(PasswordResetToken tokenEntity, String newPassword) {
        User user = tokenEntity.getUser();

        // 1. Mã hóa và cập nhật mật khẩu
        String encodedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodedPassword);
        userRepository.save(user);

        // 2. Xóa token đã sử dụng
        tokenRepository.delete(tokenEntity);
    }
    @Transactional
public boolean updateProfile(UserProfileUpdateDto dto) {
    User existingUser = getUserById(dto.getId());
    boolean emailChanged = !existingUser.getEmail().equalsIgnoreCase(dto.getEmail());

    // 1. KIỂM TRA RÀNG BUỘC (EMAIL VÀ SỐ ĐIỆN THOẠI)
    
    // Kiểm tra Email trùng lặp (nếu Email bị thay đổi)
    if (emailChanged) {
        Optional<User> userWithNewEmail = userRepository.findByEmail(dto.getEmail());
        if (userWithNewEmail.isPresent() && userWithNewEmail.get().getUserID() != existingUser.getUserID()) {
            // Ném ra ngoại lệ để Controller bắt và hiển thị lỗi
            throw new IllegalArgumentException("Email đã tồn tại. Vui lòng sử dụng Email khác.");
        }
    }

    // Kiểm tra Số điện thoại trùng lặp (Giả định UserRepository có findByPhoneNumber)
    /* // Nếu bạn có findByPhoneNumber trong UserRepository, hãy bật đoạn này:
    Optional<User> userWithNewPhone = userRepository.findByPhoneNumber(dto.getPhoneNumber());
    if (userWithNewPhone.isPresent() && userWithNewPhone.get().getId() != existingUSser.getId()) {
        throw new IllegalArgumentException("Số điện thoại đã được đăng ký. Vui lòng sử dụng số khác.");
    }
    */
    
    // 2. CẬP NHẬT DỮ LIỆU
    existingUser.setUserName(dto.getUserName());
    existingUser.setEmail(dto.getEmail());
    existingUser.setPhoneNumber(dto.getPhoneNumber());
    
    userRepository.save(existingUser);
    
    return emailChanged;
}
public String getInitials(String userName) {
        if (userName == null || userName.trim().isEmpty()) {
            return "U"; // Chữ cái mặc định
        }
        return userName.trim().substring(0, 1).toUpperCase();
    }
}