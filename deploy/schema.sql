CREATE DATABASE IF NOT EXISTS pve_logs CHARACTER SET utf8mb4;
USE pve_logs;

-- Moi tran dau la 1 dong, luu duong dan file CSV goc de doi chieu/backup
CREATE TABLE IF NOT EXISTS matches (
    match_id      VARCHAR(64) PRIMARY KEY,
    csv_file_path VARCHAR(255),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Moi dong la 1 dong log trong tran (dung dung dinh dang Core dang xuat ra:
-- Frame,PlayerPosX,PlayerPosY,PlayerEvent,BotPosX,BotPosY,BotEvent)
CREATE TABLE IF NOT EXISTS match_events (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_id       VARCHAR(64) NOT NULL,
    frame          INT NOT NULL,
    player_pos_x   DOUBLE,
    player_pos_y   DOUBLE,
    player_event   VARCHAR(32),
    bot_pos_x      DOUBLE,
    bot_pos_y      DOUBLE,
    bot_event      VARCHAR(32),
    INDEX idx_match_id (match_id),
    FOREIGN KEY (match_id) REFERENCES matches(match_id)
);
