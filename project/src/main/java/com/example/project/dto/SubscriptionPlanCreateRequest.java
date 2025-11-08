package com.example.project.dto;

import java.math.BigDecimal;

public class SubscriptionPlanCreateRequest {
    private String planName;
    private BigDecimal price;
    private String description;

    public SubscriptionPlanCreateRequest(String planName, BigDecimal price, String description) {
        this.planName = planName;
        this.price = price;
        this.description = description;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}
