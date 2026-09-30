package com.example.project.model;

import jakarta.persistence.*;
import java.util.Date;

@Entity
@Table(name = "CommentReaction",
        uniqueConstraints = @UniqueConstraint(columnNames = {"commentID", "userID"}))
public class CommentReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "commentID", nullable = false)
    private int commentId;

    @Column(name = "userID", nullable = false)
    private int userId;

    @Column(nullable = false, length = 10)
    private String emoji;

    @Column(name = "created_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt = new Date();

    public CommentReaction() {}

    public CommentReaction(int commentId, int userId, String emoji) {
        this.commentId = commentId;
        this.userId = userId;
        this.emoji = emoji;
        this.createdAt = new Date();
    }

    public int getId() { return id; }
    public int getCommentId() { return commentId; }
    public void setCommentId(int commentId) { this.commentId = commentId; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
