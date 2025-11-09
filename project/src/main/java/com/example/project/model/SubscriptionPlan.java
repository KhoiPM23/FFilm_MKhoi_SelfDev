package com.example.project.model;

import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "SubscriptionPlan")
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int planID;

    @Column(columnDefinition = "NVARCHAR(255)")
    @NotBlank(message = "planName is not null")
    private String planName;

    @NotNull(message = "price is required")
    private float price;

    @Column(columnDefinition = "NVARCHAR(255)")
    @NotBlank(message = "description  is not null")
    private String description;

    private boolean isFeatured;
    private boolean status;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL)
    private List<Subscription> subscriptions;

    public SubscriptionPlan() {
    }

    public SubscriptionPlan(int planID, String planName, float price, String description, boolean isFeatured,
            boolean status, List<Subscription> subscriptions) {
        this.planID = planID;
        this.planName = planName;
        this.price = price;
        this.description = description;
        this.isFeatured = isFeatured;
        this.status = status;
        this.subscriptions = subscriptions;
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

    public float getPrice() {
        return price;
    }

    public void setPrice(float price) {
        this.price = price;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isFeatured() {
        return isFeatured;
    }

    public void setFeatured(boolean isFeatured) {
        this.isFeatured = isFeatured;
    }

    public boolean isStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public List<Subscription> getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(List<Subscription> subscriptions) {
        this.subscriptions = subscriptions;
    }

}
