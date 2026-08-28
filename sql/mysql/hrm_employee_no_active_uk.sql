-- 员工工号唯一键改为仅在职行唯一（与身份证 active_id_card 同款）
-- 修复：软删后再删同一工号的新档案时 uk_employee_no(employee_no, deleted, tenant_id) 冲突，
-- 批量删除返回 400「数据写入失败」。
SET NAMES utf8mb4;
SET @db := DATABASE();

SET @sql := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@db AND TABLE_NAME='hrm_employee' AND COLUMN_NAME='active_employee_no'),
        'SELECT 1',
        'ALTER TABLE `hrm_employee` ADD COLUMN `active_employee_no` varchar(50)
            CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            GENERATED ALWAYS AS (IF(`deleted` = 0, NULLIF(TRIM(`employee_no`), ''''), NULL)) STORED
            COMMENT ''在职工号 NULLIF(TRIM) active-only 唯一'''
    )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA=@db AND TABLE_NAME='hrm_employee' AND INDEX_NAME='uk_hrm_employee_active_employee_no'),
        'SELECT 1',
        'ALTER TABLE `hrm_employee` ADD UNIQUE KEY `uk_hrm_employee_active_employee_no` (`tenant_id`, `active_employee_no`) USING BTREE'
    )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA=@db AND TABLE_NAME='hrm_employee' AND INDEX_NAME='uk_employee_no'),
        'ALTER TABLE `hrm_employee` DROP INDEX `uk_employee_no`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
