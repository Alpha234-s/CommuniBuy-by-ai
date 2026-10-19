-- 1. 用户表
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(32) PRIMARY KEY,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(64),
    description VARCHAR(255),
    role INT DEFAULT 0,
    address VARCHAR(255)
);

-- 2. 团购商品表
CREATE TABLE IF NOT EXISTS items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    price INT UNSIGNED NOT NULL,
    stock INT UNSIGNED NOT NULL,
    status INT NOT NULL,
    owner_id VARCHAR(32),
    start_time BIGINT,
    end_time BIGINT
);

-- 3. 订单表
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    item_id BIGINT UNSIGNED NOT NULL,
    user_id VARCHAR(32) NOT NULL,
    owner_id VARCHAR(32) NOT NULL,
    quantity INT UNSIGNED NOT NULL,
    price INT UNSIGNED NOT NULL,
    address VARCHAR(255),
    status INT NOT NULL,
    create_time BIGINT,
    pay_time BIGINT,
    ship_time BIGINT,
    receipt_time BIGINT,
    close_time BIGINT
);

-- 4. JWT 吊销表
CREATE TABLE IF NOT EXISTS blacklist (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    token_hash CHAR(64) NOT NULL,
    expire_at BIGINT NOT NULL
);