package com.example.project.controller;



import com.example.project.dto.AdminPlanRequest;

import com.example.project.model.SubscriptionPlan;

import com.example.project.service.SubscriptionPlanService;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.HttpStatus;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;



import java.util.List;



@RestController

@RequestMapping("/api/admin/plans") // API bảo mật cho Admin

@CrossOrigin(origins = "*")

public class AdminSubscriptionPlanController {



    @Autowired

    private SubscriptionPlanService planService;



    /**

     * Lấy tất cả các gói (cho Admin)

     */

    @GetMapping

    public ResponseEntity<List<SubscriptionPlan>> getAllPlans() {

        return ResponseEntity.ok(planService.getAllPlansForAdmin());

    }



    /**

     * Lấy chi tiết 1 gói (để fill form edit)

     */

    @GetMapping("/{id}")

    public ResponseEntity<SubscriptionPlan> getPlanById(@PathVariable Integer id) {

        try {

            return ResponseEntity.ok(planService.getPlanById(id));

        } catch (Exception e) {

            return ResponseEntity.notFound().build();

        }

    }



    /**

     * Tạo gói mới

     */

    @PostMapping

    public ResponseEntity<SubscriptionPlan> createPlan(@Valid @RequestBody AdminPlanRequest request) {

        SubscriptionPlan createdPlan = planService.createPlan(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(createdPlan);

    }



    /**

     * Cập nhật gói (bao gồm cả Tên, Giá, Mô tả, Nổi bật, Trạng thái)

     */

    @PutMapping("/{id}")

    public ResponseEntity<SubscriptionPlan> updatePlan(@PathVariable Integer id, @Valid @RequestBody AdminPlanRequest request) {

        SubscriptionPlan updatedPlan = planService.updatePlan(id, request);

        return ResponseEntity.ok(updatedPlan);

    }



    /**

     * [MỚI] Endpoint để vô hiệu hóa (Soft Delete)

     * Được gọi bởi nút "Xóa"

     */

    @PutMapping("/{id}/deactivate")

    public ResponseEntity<Void> deactivatePlan(@PathVariable Integer id) {

        planService.deactivatePlan(id);

        return ResponseEntity.ok().build();

    }

}
