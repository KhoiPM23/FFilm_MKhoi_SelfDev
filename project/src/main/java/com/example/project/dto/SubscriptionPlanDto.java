package com.example.project.dto;

import java.math.BigDecimal;

public class SubscriptionPlanDto {
    private int planID;
    private String planName;
    private BigDecimal price;
    private String description;
    private boolean featured;
    private boolean status;

    public SubscriptionPlanDto(int planID, String planName, BigDecimal price, String description, boolean featured,
            boolean status) {
        this.planID = planID;
        this.planName = planName;
        this.price = price;
        this.description = description;
        this.featured = featured;
        this.status = status;
    }

    public int getPlanID() {
        return planID;
    }

    public void setPlanID(int planID) {
        this.planID = planID;
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

    public boolean featured() {
        return featured;
    }

    public void setFeatured(boolean featured) {
        this.featured = featured;
    }

    public boolean isStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

}
