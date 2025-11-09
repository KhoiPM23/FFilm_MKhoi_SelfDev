package com.example.project.dto;

import jakarta.validation.constraints.NotNull;

public class SubscriptionPlanRegisterRequest {
    @NotNull(message = "userID is required")
    private Integer userID;

    @NotNull(message = "planID is required")
    private Integer planID;

    public SubscriptionPlanRegisterRequest(@NotNull(message = "userID is required") Integer userID,
            @NotNull(message = "planID is required") Integer planID) {
        this.userID = userID;
        this.planID = planID;
    }

    public SubscriptionPlanRegisterRequest() {
    }

    public Integer getUserID() {
        return userID;
    }

    public void setUserID(Integer userID) {
        this.userID = userID;
    }

    public Integer getPlanID() {
        return planID;
    }

    public void setPlanID(Integer planID) {
        this.planID = planID;
    }

}
