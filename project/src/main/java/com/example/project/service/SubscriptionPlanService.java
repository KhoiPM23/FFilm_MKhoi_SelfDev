package com.example.project.service;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal; // <-- THÊM IMPORT NÀY

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.project.dto.AdminPlanRequest; // THÊM DTO MỚI
import com.example.project.dto.SubscriptionPlanCreateRequest; // Giữ DTO cũ (nếu nơi khác còn dùng)
import com.example.project.model.SubscriptionPlan;
import com.example.project.repository.SubscriptionPlanRepository;

@Service
public class SubscriptionPlanService {

    @Autowired
    private SubscriptionPlanRepository planRepository;

    /**
     * Dùng cho user xem (trang /subscriptionPlan)
     */
    public List<SubscriptionPlan> showALlPlans() {
        // Chỉ hiển thị các gói đang 'active' cho user
        return planRepository.findAll().stream()
                .filter(SubscriptionPlan::isStatus)
                .toList();
    }

    /**
     * [MỚI] Dùng cho Admin xem (trang /admin/manage-plans)
     */
    public List<SubscriptionPlan> getAllPlansForAdmin() {
        return planRepository.findAll();
    }

    /**
     * [MỚI] Lấy 1 gói bằng ID (cho modal edit)
     */
    public SubscriptionPlan getPlanById(Integer id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy gói với ID: " + id));
    }

    /**
     * [SỬA LẠI] Dùng DTO mới của Admin
     */
    public SubscriptionPlan createPlan(AdminPlanRequest dto) {
        if (planRepository.existsByPlanName(dto.getPlanName())) {
            throw new RuntimeException("Tên gói đã tồn tại");
        }
        SubscriptionPlan plan = new SubscriptionPlan();
        mapDtoToEntity(dto, plan); // Gọi hàm helper
        return planRepository.save(plan);
    }

    /**
     * [MỚI] Hàm cập nhật
     */
    public SubscriptionPlan updatePlan(Integer id, AdminPlanRequest dto) {
        SubscriptionPlan plan = getPlanById(id); // Lấy plan, nếu không có sẽ throw lỗi

        // Kiểm tra tên trùng (nếu đổi tên)
        Optional<SubscriptionPlan> existing = planRepository.findByPlanName(dto.getPlanName());
        if (existing.isPresent() && existing.get().getPlanID() != id) {
            throw new RuntimeException("Tên gói này đã thuộc về một gói khác");
        }

        mapDtoToEntity(dto, plan); // Cập nhật thông tin
        return planRepository.save(plan);
    }

    /**
     * [MỚI] Hàm Vô hiệu hóa (Soft Delete)
     */
    public void deactivatePlan(Integer id) {
        SubscriptionPlan plan = getPlanById(id);
        plan.setStatus(false); // Chỉ cần đổi trạng thái
        planRepository.save(plan);
    }

    // [MỚI] Hàm helper để map DTO -> Entity
    private void mapDtoToEntity(AdminPlanRequest dto, SubscriptionPlan plan) {
        plan.setPlanName(dto.getPlanName());
        plan.setPrice(dto.getPrice()); // Entity bây giờ dùng BigDecimal
        plan.setDescription(dto.getDescription());
        plan.setFeatured(dto.isFeatured());
        plan.setStatus(dto.isStatus());
    }

    /**
     * [GIỮ LẠI HÀM CŨ]
     * Hàm này có thể đang được dùng ở đâu đó, ta giữ lại
     * nhưng nên chuyển sang dùng createPlan(AdminPlanRequest dto)
     */
    public boolean createSubscriptionPlan(SubscriptionPlanCreateRequest dto) {
        if (planRepository.existsByPlanName(dto.getPlanName())) {
            return false;
        }
        SubscriptionPlan subscriptionPlan = new SubscriptionPlan();
        subscriptionPlan.setPlanName(dto.getPlanName());
        subscriptionPlan.setPrice(dto.getPrice());
        subscriptionPlan.setDescription(dto.getDescription());
        subscriptionPlan.setFeatured(false);
        subscriptionPlan.setStatus(true);
        planRepository.save(subscriptionPlan);
        return true;
    }
}