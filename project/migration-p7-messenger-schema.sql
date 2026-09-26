USE FFilm3;
GO

-- 1. Table messenger_messages
IF OBJECT_ID(N'dbo.messenger_messages', N'U') IS NULL
BEGIN
    CREATE TABLE messenger_messages (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        sender_id INT NOT NULL,
        receiver_id INT NOT NULL,
        content NVARCHAR(MAX) NULL,
        mediaUrl VARCHAR(2048) NULL,
        type VARCHAR(50) NOT NULL DEFAULT 'TEXT',
        status VARCHAR(50) NOT NULL DEFAULT 'SENT',
        timestamp DATETIME2 NULL,
        reply_to_id BIGINT NULL,
        isDeleted BIT NOT NULL DEFAULT 0,
        is_pinned BIT NOT NULL DEFAULT 0,
        forwarded_from NVARCHAR(255) NULL,
        original_message_id BIGINT NULL,
        call_duration INT NULL,
        call_status VARCHAR(50) NULL,
        CONSTRAINT FK_messenger_messages_sender FOREIGN KEY (sender_id) REFERENCES Users(userID),
        CONSTRAINT FK_messenger_messages_receiver FOREIGN KEY (receiver_id) REFERENCES Users(userID),
        CONSTRAINT FK_messenger_messages_replyTo FOREIGN KEY (reply_to_id) REFERENCES messenger_messages(id)
    );

    CREATE INDEX IX_messenger_messages_sender_receiver ON messenger_messages(sender_id, receiver_id, timestamp);
    CREATE INDEX IX_messenger_messages_receiver_status ON messenger_messages(receiver_id, status);
END
GO

-- 2. Table conversation_settings
IF OBJECT_ID(N'dbo.conversation_settings', N'U') IS NULL
BEGIN
    CREATE TABLE conversation_settings (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        user_id INT NOT NULL,
        partner_id INT NOT NULL,
        theme_color VARCHAR(20) DEFAULT '#0084ff',
        nickname NVARCHAR(100) NULL,
        notification_enabled BIT NOT NULL DEFAULT 1,
        custom_background_url NVARCHAR(1024) NULL,
        muted_until DATETIME2 NULL,
        version BIGINT NULL DEFAULT 0,
        CONSTRAINT FK_conversation_settings_user FOREIGN KEY (user_id) REFERENCES Users(userID),
        CONSTRAINT FK_conversation_settings_partner FOREIGN KEY (partner_id) REFERENCES Users(userID),
        CONSTRAINT UQ_conversation_settings_user_partner UNIQUE (user_id, partner_id)
    );
END
GO

-- 3. Table call_logs
IF OBJECT_ID(N'dbo.call_logs', N'U') IS NULL
BEGIN
    CREATE TABLE call_logs (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        user_id INT NOT NULL,
        partner_id INT NOT NULL,
        partner_name NVARCHAR(100) NOT NULL,
        call_type VARCHAR(50) NOT NULL,
        duration INT NOT NULL DEFAULT 0,
        timestamp DATETIME2 NOT NULL,
        call_status VARCHAR(50) NOT NULL,
        is_video BIT NOT NULL DEFAULT 0,
        peer_id VARCHAR(100) NULL,
        initiator_id INT NULL,
        CONSTRAINT FK_call_logs_user FOREIGN KEY (user_id) REFERENCES Users(userID),
        CONSTRAINT FK_call_logs_partner FOREIGN KEY (partner_id) REFERENCES Users(userID)
    );

    CREATE INDEX IX_call_logs_user_timestamp ON call_logs(user_id, timestamp DESC);
END
GO
