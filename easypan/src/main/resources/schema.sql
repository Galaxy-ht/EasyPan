CREATE TABLE IF NOT EXISTS user_info (
    user_id VARCHAR(50) PRIMARY KEY,
    nick_name VARCHAR(50),
    email VARCHAR(150),
    qq_open_id INT,
    qq_avatar VARCHAR(255),
    password VARCHAR(100),
    join_time DATETIME,
    last_login_time DATETIME,
    status INT DEFAULT 1,
    use_space BIGINT DEFAULT 0,
    total_space BIGINT DEFAULT 5242880
);

CREATE TABLE IF NOT EXISTS file_info (
    file_id VARCHAR(50) PRIMARY KEY,
    user_id VARCHAR(50),
    file_md5 VARCHAR(100),
    file_pid VARCHAR(50),
    file_size BIGINT,
    file_name VARCHAR(255),
    file_cover VARCHAR(255),
    file_path VARCHAR(500),
    create_time DATETIME,
    last_update_time DATETIME,
    folder_type INT DEFAULT 0,
    file_category INT,
    file_type INT,
    status INT DEFAULT 1,
    recovery_time DATETIME,
    del_flag INT DEFAULT 2
);

CREATE TABLE IF NOT EXISTS file_share (
    share_id VARCHAR(50) PRIMARY KEY,
    file_id VARCHAR(50),
    user_id VARCHAR(50),
    valid_type INT,
    expire_time DATETIME,
    share_time DATETIME,
    code VARCHAR(50),
    show_count INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS email_code (
    email VARCHAR(150) PRIMARY KEY,
    code VARCHAR(20),
    creat_time DATETIME,
    status INT DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_user_info_email ON user_info(email);
CREATE INDEX IF NOT EXISTS idx_file_info_user_id ON file_info(user_id);
CREATE INDEX IF NOT EXISTS idx_file_info_file_pid ON file_info(file_pid);
CREATE INDEX IF NOT EXISTS idx_file_info_del_flag ON file_info(del_flag);
CREATE INDEX IF NOT EXISTS idx_file_share_user_id ON file_share(user_id);