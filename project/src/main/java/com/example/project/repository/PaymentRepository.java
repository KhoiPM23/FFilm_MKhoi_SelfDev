package com.example.project.repository;

import com.example.project.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {

       List<Payment> findByUser_UserIDOrderByPaymentDateDesc(Integer userId);
    // ✅ FIXED: Dùng chuẩn JPA Method Name (Spring tự generate SQL chuẩn)
    // Không cần @Query Native cho cái này, tránh lỗi mapping cột
    List<Payment> findTop10ByOrderByPaymentDateDesc();

    // [MỚI] Lấy TẤT CẢ giao dịch sắp xếp theo ngày mới nhất để xuất báo cáo
    List<Payment> findAllByOrderByPaymentDateDesc();
    // ✅ FIXED: Thống kê tổng doanh thu
    @Query(value = "SELECT COALESCE(CAST(SUM(amount) AS FLOAT), 0.0) FROM Payment WHERE status IN ('SUCCESS', '00')", 
           nativeQuery = true)
    Double calculateTotalRevenue();

    // ✅ FIXED: Thống kê doanh thu theo tháng
    @Query(value = "SELECT COALESCE(CAST(SUM(amount) AS FLOAT), 0.0) FROM Payment " +
           "WHERE status IN ('SUCCESS', '00') " +
           "AND MONTH(payment_date) = :month " +
           "AND YEAR(payment_date) = :year", 
           nativeQuery = true)
    Double calculateRevenueByMonth(@Param("month") int month, @Param("year") int year);

    // ✅ FIXED: Thống kê doanh thu hôm nay
    @Query(value = "SELECT COALESCE(CAST(SUM(amount) AS FLOAT), 0.0) FROM Payment " +
                   "WHERE status IN ('SUCCESS', '00') " +
                   "AND CAST(payment_date AS DATE) = CAST(GETDATE() AS DATE)",
           nativeQuery = true)
    Double calculateRevenueToday();

    // ✅ FIXED: Query GROUP BY cho biểu đồ
    // Sửa ORDER BY để dùng function thay vì alias (tránh lỗi SQL Server strict mode)
    @Query(value = "SELECT MONTH(payment_date) as month, CAST(COALESCE(SUM(amount), 0.0) AS FLOAT) as total " +
                   "FROM Payment " +
                   "WHERE status IN ('SUCCESS', '00') " +
                   "AND YEAR(payment_date) = :year " +
                   "GROUP BY MONTH(payment_date) " +
                   "ORDER BY MONTH(payment_date) ASC", 
           nativeQuery = true)
    List<Object[]> getMonthlyRevenueStats(@Param("year") int year);

    // Đếm tổng giao dịch
    @Query(value = "SELECT COUNT(*) FROM Payment WHERE status IN ('SUCCESS', '00', 'FAILED')", 
           nativeQuery = true)
    long countAllTransactions();
}