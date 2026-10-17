CREATE TABLE IF NOT EXISTS `sys_user`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`    VARCHAR(50)  NOT NULL COMMENT '用户名（手机号注册用户默认为手机号）',
    `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `password`    VARCHAR(128) DEFAULT NULL COMMENT '密码（SHA-256 + 盐 + 1024 次迭代），手机号注册用户为空',
    `salt`        VARCHAR(32)  DEFAULT NULL COMMENT '密码盐，手机号注册用户为空',
    `nickname`    VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    `role`        VARCHAR(20)  NOT NULL DEFAULT 'user' COMMENT '角色：admin/user',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用，0 禁用',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_phone` (`phone`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '系统用户表';
