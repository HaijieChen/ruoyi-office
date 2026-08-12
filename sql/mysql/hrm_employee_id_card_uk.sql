-- EXP-82 ops copy (byte-aligned with module script)

-- ============================================================================
-- EXP-82：员工档案「在职身份证」active-only 唯一约束（P1-1/2/3 加固版）
-- ============================================================================
-- 权威定义
--   active_id_card GENERATED ALWAYS AS (
--       IF(`deleted` = 0, NULLIF(TRIM(`id_card`), ''), NULL)
--   ) STORED  -- varchar(18)
--   UNIQUE KEY uk_hrm_employee_active_id_card (`tenant_id`, `active_id_card`)
--
-- 安装顺序（保护窗口 / P1-3）
--   1) fail-closed 预检（与表达式同一规范化）
--   2) 确保生成列定义正确（错误同名普通列：先 DROP 依赖 UK，再 DROP/ADD 列）
--   3) 确保新 UK 定义正确（non_unique=0，列序 tenant_id, active_id_card）并 fail-closed 校验
--   4) 最后才 DROP 旧 uk_hrm_employee_id_card(tenant_id,id_card,deleted)
--
-- 回滚
--   优先只回滚应用代码，保留本约束。
--   schema 回退前必须保留/安装等价 active-only 唯一保护；禁止无保护窗口。
--   勿装回旧 UNIQUE(tenant_id,id_card,deleted)。
-- ============================================================================

DROP PROCEDURE IF EXISTS `__exp82_assert`;
DELIMITER $$
CREATE PROCEDURE `__exp82_assert`(IN p_ok INT, IN p_msg VARCHAR(512))
BEGIN
    IF p_ok = 0 OR p_ok IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = p_msg;
    END IF;
END$$
DELIMITER ;

-- ---------- 0) fail-closed 预检（P1-2：与 NULLIF(TRIM(id_card),'') 对齐）----------
SET @dup_active := (
    SELECT COUNT(1) FROM (
        SELECT 1
        FROM `hrm_employee`
        WHERE `deleted` = 0
          AND NULLIF(TRIM(`id_card`), '') IS NOT NULL
        GROUP BY `tenant_id`, NULLIF(TRIM(`id_card`), '')
        HAVING COUNT(*) > 1
    ) t
);
CALL `__exp82_assert`(@dup_active = 0,
    'EXP-82 precheck failed: duplicate active id_card (after TRIM/NULLIF). Clean active dups before migrate.');

-- ---------- helpers: definition probes ----------
-- col_ok: STORED GENERATED + expression contains deleted/nullif/trim/id_card
SET @col_extra := (
    SELECT `EXTRA` FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'hrm_employee' AND column_name = 'active_id_card' LIMIT 1
);
SET @col_gen := (
    SELECT LOWER(IFNULL(`GENERATION_EXPRESSION`, '')) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'hrm_employee' AND column_name = 'active_id_card' LIMIT 1
);
SET @col_type := (
    SELECT LOWER(IFNULL(`COLUMN_TYPE`, '')) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'hrm_employee' AND column_name = 'active_id_card' LIMIT 1
);
SET @col_ok := IF(
    @col_extra IS NOT NULL
    AND UPPER(@col_extra) LIKE '%STORED%GENERATED%'
    AND @col_gen LIKE '%deleted%'
    AND @col_gen LIKE '%nullif%'
    AND @col_gen LIKE '%trim%'
    AND @col_gen LIKE '%id_card%'
    AND @col_type LIKE 'varchar(18)%',
    1, 0
);

SET @uk_ok := (
    SELECT IF(
        SUM(CASE WHEN seq_in_index = 1 AND column_name = 'tenant_id' AND non_unique = 0 THEN 1 ELSE 0 END) = 1
        AND SUM(CASE WHEN seq_in_index = 2 AND column_name = 'active_id_card' AND non_unique = 0 THEN 1 ELSE 0 END) = 1
        AND COUNT(*) = 2,
        1, 0)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'hrm_employee'
      AND index_name = 'uk_hrm_employee_active_id_card'
);

SET @uk_name_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'hrm_employee'
      AND index_name = 'uk_hrm_employee_active_id_card'
);

-- ---------- 1) 修复生成列（若错误：先卸新 UK 名再 DROP 列，避免依赖阻塞）----------
-- 若 UK 名存在且（列错误 或 UK 定义错误），先 DROP 该索引以便重建
SET @need_rebuild_col := IF(@col_extra IS NOT NULL AND IFNULL(@col_ok, 0) = 0, 1, 0);
SET @need_rebuild_uk := IF(@uk_name_exists > 0 AND IFNULL(@uk_ok, 0) = 0, 1, 0);

SET @sql_drop_uk_for_rebuild := IF(
    (@need_rebuild_col = 1 OR @need_rebuild_uk = 1) AND @uk_name_exists > 0,
    'ALTER TABLE `hrm_employee` DROP INDEX `uk_hrm_employee_active_id_card`',
    'SELECT 1');
PREPARE s_dr_uk FROM @sql_drop_uk_for_rebuild;
EXECUTE s_dr_uk;
DEALLOCATE PREPARE s_dr_uk;

SET @sql_drop_bad_col := IF(@need_rebuild_col = 1,
    'ALTER TABLE `hrm_employee` DROP COLUMN `active_id_card`',
    'SELECT 1');
PREPARE s_dr_col FROM @sql_drop_bad_col;
EXECUTE s_dr_col;
DEALLOCATE PREPARE s_dr_col;

-- 列不存在则添加权威定义
SET @col_extra := (
    SELECT `EXTRA` FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'hrm_employee' AND column_name = 'active_id_card' LIMIT 1
);
SET @sql_add_col := IF(@col_extra IS NULL,
    'ALTER TABLE `hrm_employee` ADD COLUMN `active_id_card` varchar(18)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
        GENERATED ALWAYS AS (IF(`deleted` = 0, NULLIF(TRIM(`id_card`), \'\'), NULL)) STORED
        COMMENT ''在职身份证 NULLIF(TRIM) active-only'' AFTER `tenant_id`',
    'SELECT ''active_id_card present'' AS info');
PREPARE s_add_col FROM @sql_add_col;
EXECUTE s_add_col;
DEALLOCATE PREPARE s_add_col;

-- fail-closed 校验生成列
SET @col_extra2 := (
    SELECT `EXTRA` FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'hrm_employee' AND column_name = 'active_id_card' LIMIT 1
);
SET @col_gen2 := (
    SELECT LOWER(IFNULL(`GENERATION_EXPRESSION`, '')) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'hrm_employee' AND column_name = 'active_id_card' LIMIT 1
);
CALL `__exp82_assert`(
    @col_extra2 IS NOT NULL AND UPPER(@col_extra2) LIKE '%STORED%GENERATED%'
        AND @col_gen2 LIKE '%nullif%' AND @col_gen2 LIKE '%trim%'
        AND @col_gen2 LIKE '%deleted%' AND @col_gen2 LIKE '%id_card%',
    'EXP-82 fail-closed: active_id_card missing or not STORED GENERATED with NULLIF(TRIM(id_card)).'
);

-- ---------- 2) 安装/重建新 UK 并 fail-closed 校验 ----------
SET @uk_ok := (
    SELECT IF(
        SUM(CASE WHEN seq_in_index = 1 AND column_name = 'tenant_id' AND non_unique = 0 THEN 1 ELSE 0 END) = 1
        AND SUM(CASE WHEN seq_in_index = 2 AND column_name = 'active_id_card' AND non_unique = 0 THEN 1 ELSE 0 END) = 1
        AND COUNT(*) = 2,
        1, 0)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'hrm_employee'
      AND index_name = 'uk_hrm_employee_active_id_card'
);
SET @uk_name_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'hrm_employee'
      AND index_name = 'uk_hrm_employee_active_id_card'
);

SET @sql_drop_bad_uk2 := IF(@uk_name_exists > 0 AND IFNULL(@uk_ok, 0) = 0,
    'ALTER TABLE `hrm_employee` DROP INDEX `uk_hrm_employee_active_id_card`',
    'SELECT 1');
PREPARE s_dr_uk2 FROM @sql_drop_bad_uk2;
EXECUTE s_dr_uk2;
DEALLOCATE PREPARE s_dr_uk2;

SET @uk_name_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'hrm_employee'
      AND index_name = 'uk_hrm_employee_active_id_card'
);
SET @sql_add_uk := IF(@uk_name_exists = 0,
    'ALTER TABLE `hrm_employee` ADD UNIQUE KEY `uk_hrm_employee_active_id_card` (`tenant_id`, `active_id_card`) USING BTREE',
    'SELECT ''uk already correct'' AS info');
PREPARE s_add_uk FROM @sql_add_uk;
EXECUTE s_add_uk;
DEALLOCATE PREPARE s_add_uk;

SET @uk_ok2 := (
    SELECT IF(
        SUM(CASE WHEN seq_in_index = 1 AND column_name = 'tenant_id' AND non_unique = 0 THEN 1 ELSE 0 END) = 1
        AND SUM(CASE WHEN seq_in_index = 2 AND column_name = 'active_id_card' AND non_unique = 0 THEN 1 ELSE 0 END) = 1
        AND COUNT(*) = 2,
        1, 0)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'hrm_employee'
      AND index_name = 'uk_hrm_employee_active_id_card'
);
CALL `__exp82_assert`(IFNULL(@uk_ok2, 0) = 1,
    'EXP-82 fail-closed: uk_hrm_employee_active_id_card not UNIQUE(tenant_id, active_id_card).');

-- ---------- 3) 最后 DROP 旧 UK（新保护已就位）----------
SET @old_uk := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'hrm_employee'
      AND index_name = 'uk_hrm_employee_id_card'
);
SET @sql_drop_old := IF(@old_uk > 0,
    'ALTER TABLE `hrm_employee` DROP INDEX `uk_hrm_employee_id_card`',
    'SELECT ''old uk not present'' AS info');
PREPARE s_drop_old FROM @sql_drop_old;
EXECUTE s_drop_old;
DEALLOCATE PREPARE s_drop_old;

-- ---------- 4) 最终断言 ----------
SET @final_ok := (
    SELECT IF(
        SUM(CASE WHEN seq_in_index = 1 AND column_name = 'tenant_id' AND non_unique = 0 THEN 1 ELSE 0 END) = 1
        AND SUM(CASE WHEN seq_in_index = 2 AND column_name = 'active_id_card' AND non_unique = 0 THEN 1 ELSE 0 END) = 1
        AND COUNT(*) = 2,
        1, 0)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'hrm_employee'
      AND index_name = 'uk_hrm_employee_active_id_card'
);
CALL `__exp82_assert`(IFNULL(@final_ok, 0) = 1,
    'EXP-82 fail-closed: final check lost active-only UK.');

DROP PROCEDURE IF EXISTS `__exp82_assert`;
SELECT 'EXP-82 active-only id_card UK migrate OK' AS result;
