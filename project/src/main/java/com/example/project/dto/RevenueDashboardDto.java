package com.example.project.dto;

public class RevenueDashboardDto {
    private Double totalRevenue;
    private Double todayRevenue;
    private Double monthRevenue;
    private long totalTransactions;

    public RevenueDashboardDto(Double totalRevenue, Double todayRevenue, Double monthRevenue, long totalTransactions) {
        this.totalRevenue = totalRevenue != null ? totalRevenue : 0.0;
        this.todayRevenue = todayRevenue != null ? todayRevenue : 0.0;
        this.monthRevenue = monthRevenue != null ? monthRevenue : 0.0;
        this.totalTransactions = totalTransactions;
    }

    // Getters
    public Double getTotalRevenue() { return totalRevenue; }
    public Double getTodayRevenue() { return todayRevenue; }
    public Double getMonthRevenue() { return monthRevenue; }
    public long getTotalTransactions() { return totalTransactions; }
}