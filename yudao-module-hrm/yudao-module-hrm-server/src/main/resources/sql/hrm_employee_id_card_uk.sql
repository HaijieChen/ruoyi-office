-- ----------------------------
-- EXP-82：员工档案「在职身份证」唯一约束（active-only upsert）
-- ----------------------------
-- 目标：并发导入不双建同一在职身份证；允许 建→软删→再建→再软删 全生命周期。
--
-- 方案：生成列 active_id_card
--   deleted=0 且 id_card 非空 → active_id_card = id_card
--   deleted=1 或 id_card 为空 → active_id_card = NULL
-- UNIQUE(tenant_id, active_id_card)：MySQL 允许多个 NULL，故历史墓碑不占唯一位。
--
-- 若曾安装旧版 uk_hrm_employee_id_card(tenant_id,id_card,deleted)，本脚本会先 DROP。
--
-- 预检（active 重复必须为 0；历史 deleted 重复可保留）：
--   SELECT tenant_id, id_card, COUNT(*) c FROM hrm_employee
--   WHERE deleted = b'0' AND id_card IS NOT NULL AND id_card <> ''
--   GROUP BY tenant_id, id_card HAVING c > 1;
--
-- 预检（可选，仅观测历史墓碑重复，不阻断 active-only UK）：
--   SELECT tenant_id, id_card, COUNT(*) c FROM hrm_employee
--   WHERE deleted = b'1' AND id_card IS NOT NULL AND id_card <> ''
--   GROUP BY tenant_id, id_card HAVING c > 1;
--
-- 回滚：
--   ALTER TABLE `hrm_employee` DROP INDEX `uk_hrm_employee_active_id_card`;
--   ALTER TABLE `hrm_employee` DROP COLUMN `active_id_card`;
--   -- 勿再装回旧 uk_hrm_employee_id_card(tenant_id,id_card,deleted)

-- 0) 丢弃旧版「含 deleted 布尔」唯一键（阻断重复软删生命周期）
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

-- 1) 生成列 active_id_card（幂等：列已存在则跳过）
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

-- 2) active-only 唯一键（幂等）
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
