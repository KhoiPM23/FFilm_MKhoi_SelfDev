USE FFilm3;
GO

-- 1. Table WatchRoom
IF OBJECT_ID(N'dbo.WatchRoom', N'U') IS NULL
BEGIN
    CREATE TABLE WatchRoom (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        name NVARCHAR(255) NOT NULL,
        accessType NVARCHAR(10) NULL,
        password NVARCHAR(255) NULL,
        maxUsers INT NOT NULL,
        owner_id INT NOT NULL,
        isActive BIT NOT NULL,
        createdAt DATETIME2 NULL,
        CONSTRAINT FK_WatchRoom_Users FOREIGN KEY (owner_id) REFERENCES Users(userID)
    );
END
GO

-- 2. Table FriendRequests
IF OBJECT_ID(N'dbo.FriendRequests', N'U') IS NULL
BEGIN
    CREATE TABLE FriendRequests (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        sender_id INT NOT NULL,
        receiver_id INT NOT NULL,
        status NVARCHAR(20) NULL,
        createdAt DATETIME2 NULL,
        CONSTRAINT FK_FriendRequests_Sender FOREIGN KEY (sender_id) REFERENCES Users(userID),
        CONSTRAINT FK_FriendRequests_Receiver FOREIGN KEY (receiver_id) REFERENCES Users(userID),
        CONSTRAINT UQ_FriendRequests_Sender_Receiver UNIQUE (sender_id, receiver_id)
    );
END
GO

-- 3. Table Notification
IF OBJECT_ID(N'dbo.Notification', N'U') IS NULL
BEGIN
    CREATE TABLE Notification (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        content NVARCHAR(255) NULL,
        type NVARCHAR(50) NULL,
        link NVARCHAR(255) NULL,
        isRead BIT NOT NULL DEFAULT 0,
        timestamp DATETIME2 NULL,
        user_id INT NULL,
        CONSTRAINT FK_Notification_Users FOREIGN KEY (user_id) REFERENCES Users(userID)
    );
END
GO

-- 4. Table UserFollow
IF OBJECT_ID(N'dbo.UserFollow', N'U') IS NULL
BEGIN
    CREATE TABLE UserFollow (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        follower_id INT NOT NULL,
        following_id INT NOT NULL,
        createdAt DATETIME2 NULL,
        CONSTRAINT FK_UserFollow_Follower FOREIGN KEY (follower_id) REFERENCES Users(userID),
        CONSTRAINT FK_UserFollow_Following FOREIGN KEY (following_id) REFERENCES Users(userID),
        CONSTRAINT UQ_UserFollow_Follower_Following UNIQUE (follower_id, following_id)
    );
END
GO
