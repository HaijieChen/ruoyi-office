-- ----------------------------
-- EXP-82：员工档案身份证唯一约束（花名册导入原子 upsert）
-- ----------------------------
-- 目的：防止并发 select-then-create 双建同一身份证档案。
-- 维度：tenant_id + id_card + deleted（与 uk_employee_no 一致，软删后可再建）
-- 前置：同一 tenant 下 active(deleted=0) 不得存在重复非空 id_card；执行前请检查：
--   SELECT tenant_id, id_card, COUNT(*) c FROM hrm_employee
--   WHERE deleted = b'0' AND id_card IS NOT NULL AND id_card <> ''
--   GROUP BY tenant_id, id_card HAVING c > 1;
-- 回滚：
--   ALTER TABLE `hrm_employee` DROP INDEX `uk_hrm_employee_id_card`;

-- 幂等：已存在则跳过
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
