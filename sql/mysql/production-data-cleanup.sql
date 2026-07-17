-- RuoYi Office production data cleanup
-- Target: dedicated local database `ruoyi-office`
-- Prerequisite: take and verify a full logical backup before execution.
-- This script preserves platform metadata and workflow definitions, but removes
-- business data, runtime data, demo identities, logs, tokens, files, jobs, and
-- integration credentials. It intentionally preserves the existing admin password;
-- change that password immediately before exposing the system to production traffic.

USE `ruoyi-office`;
SET NAMES utf8mb4;

DELIMITER //

DROP PROCEDURE IF EXISTS assert_cleanup_target//
CREATE PROCEDURE assert_cleanup_target()
BEGIN
    IF DATABASE() <> 'ruoyi-office' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Refusing cleanup: unexpected database';
    END IF;
    IF (SELECT COUNT(*) FROM system_users WHERE id = 1 AND username = 'admin') <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Refusing cleanup: admin baseline is missing';
    END IF;
    IF (SELECT COUNT(*) FROM system_tenant WHERE id = 1) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Refusing cleanup: default tenant is missing';
    END IF;
    IF (SELECT COUNT(*) FROM system_role WHERE id = 1 AND code = 'super_admin') <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Refusing cleanup: super administrator role is missing';
    END IF;
END//

CALL assert_cleanup_target()//
DROP PROCEDURE assert_cleanup_target//

DROP PROCEDURE IF EXISTS truncate_non_baseline_tables//
CREATE PROCEDURE truncate_non_baseline_tables()
BEGIN
    DECLARE finished INTEGER DEFAULT 0;
    DECLARE current_table VARCHAR(128);
    DECLARE tables_to_clear CURSOR FOR
        SELECT table_name
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_type = 'BASE TABLE'
          AND LOWER(table_name) NOT IN (
              'act_ge_bytearray',
              'act_ge_property',
              'act_id_bytearray',
              'act_id_group',
              'act_id_info',
              'act_id_membership',
              'act_id_priv',
              'act_id_priv_mapping',
              'act_id_property',
              'act_id_token',
              'act_id_user',
              'act_procdef_info',
              'act_re_deployment',
              'act_re_model',
              'act_re_procdef',
              'bpm_category',
              'bpm_form',
              'bpm_process_definition_info',
              'bpm_process_expression',
              'bpm_process_listener',
              'infra_config',
              'infra_file_config',
              'system_dept',
              'system_dict_data',
              'system_dict_type',
              'system_home_app_config',
              'system_home_component',
              'system_home_component_category',
              'system_home_page',
              'system_home_page_layout',
              'system_menu',
              'system_notify_template',
              'system_oauth2_client',
              'system_role',
              'system_tenant',
              'system_user_role',
              'system_users'
          );
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = 1;

    OPEN tables_to_clear;
    clear_loop: LOOP
        FETCH tables_to_clear INTO current_table;
        IF finished = 1 THEN
            LEAVE clear_loop;
        END IF;
        SET @truncate_sql = CONCAT('TRUNCATE TABLE `', REPLACE(current_table, '`', '``'), '`');
        PREPARE truncate_statement FROM @truncate_sql;
        EXECUTE truncate_statement;
        DEALLOCATE PREPARE truncate_statement;
    END LOOP;
    CLOSE tables_to_clear;
END//

SET FOREIGN_KEY_CHECKS = 0//
CALL truncate_non_baseline_tables()//
DROP PROCEDURE truncate_non_baseline_tables//

-- Keep only a neutral default organization and the administrator identity.
DELETE FROM system_user_role//
DELETE FROM system_users WHERE id <> 1//
UPDATE system_users
SET nickname = '系统管理员',
    remark = NULL,
    dept_id = 100,
    post_ids = NULL,
    email = '',
    mobile = '',
    sex = 0,
    avatar = '',
    status = 0,
    login_ip = '',
    login_date = NULL,
    creator = 'system',
    updater = 'system',
    deleted = b'0',
    tenant_id = 1
WHERE id = 1//

DELETE FROM system_role WHERE id <> 1//
UPDATE system_role
SET name = '超级管理员',
    sort = 1,
    data_scope = 1,
    data_scope_dept_ids = '',
    status = 0,
    remark = '系统内置超级管理员角色',
    updater = 'system',
    deleted = b'0',
    tenant_id = 1
WHERE id = 1//

DELETE FROM system_dept WHERE id <> 100//
UPDATE system_dept
SET name = '总公司',
    parent_id = 0,
    sort = 0,
    leader_user_id = 1,
    phone = NULL,
    email = NULL,
    status = 0,
    updater = 'system',
    deleted = b'0',
    tenant_id = 1,
    org_type = '1'
WHERE id = 100//

DELETE FROM system_tenant WHERE id <> 1//
UPDATE system_tenant
SET name = '默认租户',
    contact_user_id = 1,
    contact_name = '系统管理员',
    contact_mobile = NULL,
    status = 0,
    website = '',
    websites = NULL,
    package_id = 0,
    expire_time = '2099-12-31 23:59:59',
    account_count = 1000,
    updater = 'system',
    deleted = b'0'
WHERE id = 1//

-- The default OAuth2 client is required by the platform's own password login.
-- Remove only demo SSO clients; access/refresh tokens remain cleared separately.
DELETE FROM system_oauth2_client WHERE id <> 1//
UPDATE system_oauth2_client
SET name = '平台默认客户端',
    logo = '',
    description = '平台登录必需客户端',
    status = 0,
    updater = 'system',
    deleted = b'0'
WHERE id = 1 AND client_id = 'default'//

INSERT INTO system_user_role
    (id, user_id, role_id, creator, updater, deleted, tenant_id)
VALUES
    (1, 1, 1, 'system', 'system', b'0', 1)//

-- Retain only the database-backed file provider, without retaining uploaded files.
DELETE FROM infra_file_config WHERE id <> 4//
UPDATE infra_file_config
SET name = '数据库存储',
    remark = '生产初始化默认存储；上线前可替换为正式对象存储',
    master = b'1',
    updater = 'system',
    deleted = b'0'
WHERE id = 4//

ALTER TABLE system_users AUTO_INCREMENT = 2//
ALTER TABLE system_role AUTO_INCREMENT = 2//
ALTER TABLE system_dept AUTO_INCREMENT = 101//
ALTER TABLE system_tenant AUTO_INCREMENT = 2//
ALTER TABLE system_user_role AUTO_INCREMENT = 2//

SET FOREIGN_KEY_CHECKS = 1//

DROP PROCEDURE IF EXISTS assert_production_baseline//
CREATE PROCEDURE assert_production_baseline()
BEGIN
    IF (SELECT COUNT(*) FROM system_users) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cleanup validation failed: users';
    END IF;
    IF (SELECT COUNT(*) FROM system_tenant) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cleanup validation failed: tenants';
    END IF;
    IF (SELECT COUNT(*) FROM system_role) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cleanup validation failed: roles';
    END IF;
    IF (SELECT COUNT(*) FROM system_dept) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cleanup validation failed: departments';
    END IF;
    IF (SELECT COUNT(*) FROM system_user_role WHERE user_id = 1 AND role_id = 1 AND tenant_id = 1) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cleanup validation failed: admin role';
    END IF;
    IF (SELECT COUNT(*) FROM system_oauth2_client WHERE id = 1 AND client_id = 'default' AND status = 0) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cleanup validation failed: default OAuth2 client';
    END IF;
    IF (SELECT COUNT(*) FROM system_oauth2_access_token) <> 0
       OR (SELECT COUNT(*) FROM system_oauth2_refresh_token) <> 0
       OR (SELECT COUNT(*) FROM system_login_log) <> 0
       OR (SELECT COUNT(*) FROM system_operate_log) <> 0
       OR (SELECT COUNT(*) FROM system_notify_message) <> 0
       OR (SELECT COUNT(*) FROM infra_file) <> 0
       OR (SELECT COUNT(*) FROM act_ru_execution) <> 0
       OR (SELECT COUNT(*) FROM act_hi_procinst) <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cleanup validation failed: runtime data remains';
    END IF;
END//

CALL assert_production_baseline()//
DROP PROCEDURE assert_production_baseline//

DELIMITER ;

SELECT 'production_cleanup_complete' AS result;
SELECT COUNT(*) AS retained_menus FROM system_menu;
SELECT COUNT(*) AS retained_dict_types FROM system_dict_type;
SELECT COUNT(*) AS retained_dict_items FROM system_dict_data;
SELECT COUNT(*) AS retained_platform_configs FROM infra_config;
SELECT COUNT(*) AS retained_home_pages FROM system_home_page;
SELECT COUNT(*) AS retained_workflow_definitions FROM act_re_procdef;
SELECT id, username, nickname, dept_id, tenant_id FROM system_users;
SELECT id, name, parent_id, org_type, tenant_id FROM system_dept;
SELECT id, name, contact_name, status FROM system_tenant;
