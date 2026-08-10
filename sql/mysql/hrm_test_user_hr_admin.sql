-- 人事管理员本地验证账号（幂等、仅用于开发/测试租户）
--
-- 登录账号：hradminuser
-- 初始密码：admin123
-- 依赖：hrm_roles_hr_admin.sql 已创建 hr_admin 角色。
-- 生产环境不要直接使用此测试账号或密码。
-- 有效状态可重复收敛；账号/角色逻辑键若出现重复则 SIGNAL 回滚，不静默选择首条。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `__tmp_hr_admin_account_assert_single`;
DELIMITER $$
CREATE PROCEDURE `__tmp_hr_admin_account_assert_single`(IN p_selector VARCHAR(96), IN p_count BIGINT)
BEGIN
    DECLARE v_message VARCHAR(255);
    IF p_count <> 1 THEN
        ROLLBACK;
        SET v_message = CONCAT('hr_admin account selector must match exactly one active row: ', p_selector,
                               ' (count=', p_count, ')');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
    END IF;
END$$
DELIMITER ;

START TRANSACTION;
SET @tenant_id = 1;

-- 若账号曾被软删除，恢复后再统一收敛字段；不创建重复账号。
UPDATE `system_users`
SET `deleted` = b'0',
    `nickname` = '人事管理员测试',
    `remark` = '人事管理员角色联调测试账号（仅限本地/测试租户）',
    `password` = '$2a$04$KljJDa/LK7QfDm0lF5OhuePhlPfjRH3tB2Wu351Uidz.oQGJXevPi',
    `dept_id` = 100,
    `status` = 0,
    `updater` = 'admin',
    `update_time` = NOW()
WHERE `username` = 'hradminuser' AND `tenant_id` = @tenant_id;

INSERT INTO `system_users`
    (`username`, `password`, `nickname`, `remark`, `dept_id`, `post_ids`, `email`, `mobile`,
     `sex`, `avatar`, `status`, `login_ip`, `login_date`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 'hradminuser',
       '$2a$04$KljJDa/LK7QfDm0lF5OhuePhlPfjRH3tB2Wu351Uidz.oQGJXevPi',
       '人事管理员测试',
       '人事管理员角色联调测试账号（仅限本地/测试租户）',
       100, NULL, '', '', 0, '', 0, '', NULL,
       'admin', NOW(), 'admin', NOW(), b'0', @tenant_id
WHERE NOT EXISTS (
    SELECT 1 FROM `system_users`
    WHERE `username` = 'hradminuser' AND `tenant_id` = @tenant_id
);

UPDATE `system_users`
SET `nickname` = '人事管理员测试',
    `remark` = '人事管理员角色联调测试账号（仅限本地/测试租户）',
    `password` = '$2a$04$KljJDa/LK7QfDm0lF5OhuePhlPfjRH3tB2Wu351Uidz.oQGJXevPi',
    `dept_id` = 100,
    `status` = 0,
    `updater` = 'admin',
    `update_time` = NOW()
WHERE `username` = 'hradminuser' AND `tenant_id` = @tenant_id AND `deleted` = b'0';

SET @hr_admin_user_count = (
    SELECT COUNT(*) FROM `system_users`
    WHERE `username` = 'hradminuser' AND `tenant_id` = @tenant_id AND `deleted` = b'0'
);
CALL `__tmp_hr_admin_account_assert_single`('username hradminuser', @hr_admin_user_count);
SET @hr_admin_user_id = (
    SELECT MIN(`id`) FROM `system_users`
    WHERE `username` = 'hradminuser' AND `tenant_id` = @tenant_id AND `deleted` = b'0'
);

SET @hr_admin_role_count = (
    SELECT COUNT(*) FROM `system_role`
    WHERE `code` = 'hr_admin' AND `tenant_id` = @tenant_id AND `deleted` = b'0'
);
CALL `__tmp_hr_admin_account_assert_single`('role hr_admin', @hr_admin_role_count);
SET @hr_admin_role_id = (
    SELECT MIN(`id`) FROM `system_role`
    WHERE `code` = 'hr_admin' AND `tenant_id` = @tenant_id AND `deleted` = b'0'
);

-- 该验证账号只保留人事管理员角色，避免继承其他高权限角色。
DELETE FROM `system_user_role`
WHERE `tenant_id` = @tenant_id AND `user_id` = @hr_admin_user_id;

INSERT INTO `system_user_role`
    (`user_id`, `role_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES (@hr_admin_user_id, @hr_admin_role_id, 'admin', NOW(), 'admin', NOW(), b'0', @tenant_id);

COMMIT;
DROP PROCEDURE IF EXISTS `__tmp_hr_admin_account_assert_single`;
