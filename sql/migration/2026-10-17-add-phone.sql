-- 已有数据库升级脚本：支持手机号验证码登录
-- 注意：只需在一个已有库上执行一次
USE `juejin`;

ALTER TABLE `sys_user`
    MODIFY COLUMN `password` VARCHAR(128) DEFAULT NULL COMMENT '密码（SHA-256 + 盐 + 1024 次迭代），手机号注册用户为空',
    MODIFY COLUMN `salt`     VARCHAR(32)  DEFAULT NULL COMMENT '密码盐，手机号注册用户为空',
    ADD COLUMN `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号' AFTER `username`,
    ADD UNIQUE KEY `uk_phone` (`phone`);
