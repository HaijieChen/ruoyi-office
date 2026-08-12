-- EXP-82 运维入口（与 module 内脚本同源）
-- 详见：yudao-module-hrm/yudao-module-hrm-server/src/main/resources/sql/hrm_employee_id_card_uk.sql

-- 执行前检查重复：
-- SELECT tenant_id, id_card, COUNT(*) c FROM hrm_employee
-- WHERE deleted = b'0' AND id_card IS NOT NULL AND id_card <> ''
-- GROUP BY tenant_id, id_card HAVING c > 1;

SET @uk_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'hrm_employee'
      AND index_name = 'uk_hrm_employee_id_card'
);

SET @sql := IF(@uk_exists = 0,
    'ALTER TABLE `hrm_employee` ADD UNIQUE KEY `uk_hrm_employee_id_card` (`tenant_id`, `id_card`, `deleted`) USING BTREE',
    'SELECT ''uk_hrm_employee_id_card already exists'' AS info');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 回滚：
-- ALTER TABLE `hrm_employee` DROP INDEX `uk_hrm_employee_id_card`;
