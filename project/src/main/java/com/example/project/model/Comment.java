package com.example.project.model;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "Comment")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int commentID;

    @NotBlank(message = "content is required")
    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String content;

    @NotNull(message = "CreateAt is required")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Ho_Chi_Minh")
    @Column(name = "create_at")
    private Date createAt;
    private String status;

    @ManyToOne
    @JsonIgnoreProperties({ "comments", "reviews", "reports", "subscriptions", "payments", "watchHistories",
            "password" })
    @JoinColumn(name = "userID")
    private User user;

    @ManyToOne
    @JsonIgnoreProperties({ "comments", "reviews", "reports", "seasons", "persons", "genres", "categories",
            "watchHistories" })
    @JoinColumn(name = "movieID")
    private Movie movie;

    @ManyToOne
    @JoinColumn(name = "parent_commentID")
    private Comment parentComment;

    public Comment() {
    }

    public Comment(String content, Date createAt, String status, User user, Movie movie, Comment parentComment) {
        this.content = content;
        this.createAt = createAt;
        this.status = status;
        this.user = user;
        this.movie = movie;
        this.parentComment = parentComment;
    }

    public int getCommentID() {
        return commentID;
    }

    public void setCommentID(int commentID) {
        this.commentID = commentID;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Date getCreateAt() {
        return createAt;
    }

    public void setCreateAt(Date createAt) {
        this.createAt = createAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Movie getMovie() {
        return movie;
    }

    public void setMovie(Movie movie) {
        this.movie = movie;
    }

    public Comment getParentComment() {
        return parentComment;
    }

    public void setParentComment(Comment parentComment) {
        this.parentComment = parentComment;
    }

}
