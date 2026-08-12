-- EXP-82 运维入口（与 module 内脚本同源）
-- 详见：yudao-module-hrm/yudao-module-hrm-server/src/main/resources/sql/hrm_employee_id_card_uk.sql
--
-- active-only 唯一：生成列 active_id_card + UNIQUE(tenant_id, active_id_card)
-- 允许：建 → 软删 → 再建 → 再软删；历史 deleted 墓碑重复不阻断。
--
-- 预检（必须，active 重复）：
--   SELECT tenant_id, id_card, COUNT(*) c FROM hrm_employee
--   WHERE deleted = b'0' AND id_card IS NOT NULL AND id_card <> ''
--   GROUP BY tenant_id, id_card HAVING c > 1;
--
-- 预检（可选，历史墓碑重复，仅观测）：
--   SELECT tenant_id, id_card, COUNT(*) c FROM hrm_employee
--   WHERE deleted = b'1' AND id_card IS NOT NULL AND id_card <> ''
--   GROUP BY tenant_id, id_card HAVING c > 1;
--
-- 回滚：
--   ALTER TABLE `hrm_employee` DROP INDEX `uk_hrm_employee_active_id_card`;
--   ALTER TABLE `hrm_employee` DROP COLUMN `active_id_card`;

-- 0) DROP 旧版 uk_hrm_employee_id_card(tenant_id,id_card,deleted)
SET @old_uk := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'hrm_employee'
      AND index_name = 'uk_hrm_employee_id_card'
);
SET @sql_drop_old := IF(@old_uk > 0,
    'ALTER TABLE `hrm_employee` DROP INDEX `uk_hrm_employee_id_card`',
    'SELECT ''uk_hrm_employee_id_card not present'' AS info');
PREPARE s0 FROM @sql_drop_old;
EXECUTE s0;
DEALLOCATE PREPARE s0;

-- 1) 生成列
SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'hrm_employee'
      AND column_name = 'active_id_card'
);
SET @sql_col := IF(@col_exists = 0,
    'ALTER TABLE `hrm_employee` ADD COLUMN `active_id_card` varchar(18)
        GENERATED ALWAYS AS (IF(`deleted` = 0, `id_card`, NULL)) STORED
        COMMENT ''在职身份证（软删为 NULL，供 active-only 唯一）'' AFTER `tenant_id`',
    'SELECT ''active_id_card already exists'' AS info');
PREPARE s1 FROM @sql_col;
EXECUTE s1;
DEALLOCATE PREPARE s1;

-- 2) active-only UK
SET @uk_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'hrm_employee'
      AND index_name = 'uk_hrm_employee_active_id_card'
);
SET @sql_uk := IF(@uk_exists = 0,
    'ALTER TABLE `hrm_employee` ADD UNIQUE KEY `uk_hrm_employee_active_id_card` (`tenant_id`, `active_id_card`) USING BTREE',
    'SELECT ''uk_hrm_employee_active_id_card already exists'' AS info');
PREPARE s2 FROM @sql_uk;
EXECUTE s2;
DEALLOCATE PREPARE s2;
