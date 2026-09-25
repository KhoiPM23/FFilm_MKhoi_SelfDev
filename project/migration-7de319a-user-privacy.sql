-- Migration script to sync User privacy fields from commit 7de319a
-- Safe, additive changes to the existing Users table.

USE FFilm3;
GO

IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'Users' AND COLUMN_NAME = 'isPublicFavorites')
BEGIN
    ALTER TABLE Users ADD isPublicFavorites BIT NULL;
END
GO

IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'Users' AND COLUMN_NAME = 'isPublicFriendList')
BEGIN
    ALTER TABLE Users ADD isPublicFriendList BIT NULL;
END
GO

IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'Users' AND COLUMN_NAME = 'isPublicWatchHistory')
BEGIN
    ALTER TABLE Users ADD isPublicWatchHistory BIT NULL;
END
GO
