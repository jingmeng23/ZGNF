CREATE TABLE wechat_mini_binding (
    app_id VARCHAR(64) NOT NULL,
    open_id VARCHAR(128) NOT NULL,
    user_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (app_id, open_id),
    UNIQUE KEY uk_wechat_mini_user (app_id, user_id)
);
