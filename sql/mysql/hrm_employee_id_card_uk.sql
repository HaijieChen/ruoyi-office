-- EXP-82 ops copy (aligned with module script)

-- ============================================================================
-- EXP-82：员工档案「在职身份证」active-only 唯一约束（精确定义探针版）
-- ============================================================================
-- 权威定义
--   active_id_card varchar(18) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
--     GENERATED ALWAYS AS (
--       IF(`deleted` = 0, NULLIF(TRIM(`id_card`), ''), NULL)
--     ) STORED
--   UNIQUE KEY uk_hrm_employee_active_id_card (`tenant_id`, `active_id_card`)
--     全列索引：information_schema.statistics.SUB_PART IS NULL
--
-- 探针（禁止仅关键词子串 / 禁止 NULLIF 任意第二参数）
--   列：STORED GENERATED + charset/collation 权威匹配 + 表达式 ARG 精确匹配
--       CHARACTER_SET_NAME=utf8mb4 AND COLLATION_NAME=utf8mb4_unicode_ci
--         （或与 id_card 列 charset/collation 完全一致）
--       if((`deleted`=0),nullif(trim(`id_card`),[_charset]''),null)
--       第二参数仅精确空串 ''（可选 _latin1/_utf8mb4 等 introducer）
--       解析时仅去掉 ASCII 空格，不得删除 Tab/CR/LF（避免把 '\t' 归一成 ''）
--       拒绝 'ID-Q'、Tab/CR/LF 等非空 sentinel；拒绝 if((`deleted`=1),...)
--   索引：non_unique=0 且 (seq1=tenant_id, seq2=active_id_card) 且两列 SUB_PART IS NULL
--
-- 顺序：预检 → 修复列/UK 并 fail-closed 校验 → 最后 DROP 旧 uk_hrm_employee_id_card
-- 回滚：优先保 schema 只回滚代码；禁止无保护卸 UK；勿装回旧 (tenant_id,id_card,deleted)
-- ============================================================================

DROP PROCEDURE IF EXISTS `__exp82_assert`;
DROP PROCEDURE IF EXISTS `__exp82_probe_col_ok`;
DROP PROCEDURE IF EXISTS `__exp82_probe_uk_ok`;

DELIMITER $$
CREATE PROCEDURE `__exp82_assert`(IN p_ok INT, IN p_msg VARCHAR(512))
BEGIN
    IF p_ok = 0 OR p_ok IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = p_msg;
    END IF;
END$$

CREATE PROCEDURE `__exp82_probe_col_ok`(OUT p_ok INT)
BEGIN
    DECLARE v_extra VARCHAR(256) DEFAULT NULL;
    DECLARE v_gen   VARCHAR(1024) DEFAULT '';
    DECLARE v_type  VARCHAR(64) DEFAULT '';
    DECLARE v_cs    VARCHAR(64) DEFAULT NULL;
    DECLARE v_coll  VARCHAR(64) DEFAULT NULL;
    DECLARE v_id_cs VARCHAR(64) DEFAULT NULL;
    DECLARE v_id_coll VARCHAR(64) DEFAULT NULL;
    DECLARE v_norm  VARCHAR(1024) DEFAULT '';
    DECLARE v_arg   VARCHAR(256) DEFAULT NULL;
    DECLARE v_empty VARCHAR(8) DEFAULT NULL;
    DECLARE v_prefix VARCHAR(64) DEFAULT NULL;
    DECLARE v_cs_ok INT DEFAULT 0;

    SELECT `EXTRA`,
           LOWER(IFNULL(`GENERATION_EXPRESSION`, '')),
           LOWER(IFNULL(`COLUMN_TYPE`, '')),
           LOWER(IFNULL(`CHARACTER_SET_NAME`, '')),
           LOWER(IFNULL(`COLLATION_NAME`, ''))
      INTO v_extra, v_gen, v_type, v_cs, v_coll
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'hrm_employee'
      AND column_name = 'active_id_card'
    LIMIT 1;

    SELECT LOWER(IFNULL(`CHARACTER_SET_NAME`, '')),
           LOWER(IFNULL(`COLLATION_NAME`, ''))
      INTO v_id_cs, v_id_coll
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'hrm_employee'
      AND column_name = 'id_card'
    LIMIT 1;

    IF v_extra IS NULL THEN
        SET p_ok = 0;
    ELSE
        -- 仅去掉 ASCII 空格做结构匹配；保留 Tab/CR/LF，避免把非空 sentinel 归一成空串
        SET v_norm = REPLACE(v_gen, ' ', '');
        -- MySQL 将空串字面量存成 \'\'（常带 _latin1 等 introducer）；非空如 ID-Q / Tab 不得通过
        SET v_empty = CONCAT(CHAR(92), CHAR(39), CHAR(92), CHAR(39));
        -- 提取 nullif(trim(`id_card`), <ARG> ),null 中的 <ARG>（不改写字面量内容）
        IF v_norm LIKE 'if((`deleted`=0),nullif(trim(`id_card`),%),null)'
           OR v_norm LIKE 'if((`deleted`=0),nullif(trim(id_card),%),null)' THEN
            SET v_arg = SUBSTRING_INDEX(
                SUBSTRING_INDEX(v_norm, 'nullif(trim(`id_card`),', -1),
                '),null)', 1);
            IF v_arg = v_norm OR v_arg = '' THEN
                SET v_arg = SUBSTRING_INDEX(
                    SUBSTRING_INDEX(v_norm, 'nullif(trim(id_card),', -1),
                    '),null)', 1);
            END IF;
        ELSE
            SET v_arg = NULL;
        END IF;

        -- charset/collation：权威 utf8mb4 + utf8mb4_unicode_ci，或与 id_card 完全一致
        SET v_cs_ok = IF(
            (v_cs = 'utf8mb4' AND v_coll = 'utf8mb4_unicode_ci')
            OR (v_cs IS NOT NULL AND v_cs <> '' AND v_cs = v_id_cs AND v_coll = v_id_coll),
            1, 0
        );

        -- 仅允许 ARG = \'\' 或 _charset\'\' （精确空串；禁止 Tab/CR/LF 等控制字符）
        IF v_arg IS NULL THEN
            SET p_ok = 0;
        ELSEIF RIGHT(v_arg, 4) = v_empty
               AND LOCATE(CHAR(9), v_arg) = 0
               AND LOCATE(CHAR(10), v_arg) = 0
               AND LOCATE(CHAR(13), v_arg) = 0 THEN
            SET v_prefix = LEFT(v_arg, CHAR_LENGTH(v_arg) - 4);
            SET p_ok = IF(
                UPPER(v_extra) LIKE '%STORED%GENERATED%'
                AND v_type LIKE 'varchar(18)%'
                AND v_cs_ok = 1
                AND (v_prefix = '' OR v_prefix REGEXP '^_[a-z0-9]+$')
                AND v_norm NOT LIKE 'if((`deleted`=1)%'
                AND v_norm NOT LIKE 'if(`deleted`=1%',
                1, 0
            );
        ELSE
            SET p_ok = 0;
        END IF;
    END IF;
END$$

CREATE PROCEDURE `__exp82_probe_uk_ok`(OUT p_ok INT)
BEGIN
    -- 全列 UNIQUE(tenant_id, active_id_card)：SUB_PART 必须为 NULL
    SELECT IF(
        SUM(CASE WHEN s.seq_in_index = 1 AND s.column_name = 'tenant_id'
                  AND s.non_unique = 0 AND s.sub_part IS NULL THEN 1 ELSE 0 END) = 1
        AND SUM(CASE WHEN s.seq_in_index = 2 AND s.column_name = 'active_id_card'
                  AND s.non_unique = 0 AND s.sub_part IS NULL THEN 1 ELSE 0 END) = 1
        AND COUNT(*) = 2,
        1, 0)
    INTO p_ok
    FROM information_schema.statistics s
    WHERE s.table_schema = DATABASE()
      AND s.table_name = 'hrm_employee'
      AND s.index_name = 'uk_hrm_employee_active_id_card';
    SET p_ok = IFNULL(p_ok, 0);
END$$
DELIMITER ;

-- ---------- 0) fail-closed 预检 ----------
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

-- ---------- 1) 探测并按需重建生成列 / UK ----------
CALL `__exp82_probe_col_ok`(@col_ok);
CALL `__exp82_probe_uk_ok`(@uk_ok);

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'hrm_employee' AND column_name = 'active_id_card'
);
SET @uk_name_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'hrm_employee'
      AND index_name = 'uk_hrm_employee_active_id_card'
);

SET @need_rebuild_col := IF(@col_exists = 0 OR IFNULL(@col_ok, 0) = 0, 1, 0);
SET @need_rebuild_uk  := IF(@uk_name_exists = 0 OR IFNULL(@uk_ok, 0) = 0, 1, 0);

-- 运维须在维护窗口执行本脚本（ALTER 期间短暂无保护；先装新 UK 再卸旧 UK）
-- 卸依赖 UK（重建列或错误 UK 时必须）
SET @sql := IF((@need_rebuild_col = 1 OR @need_rebuild_uk = 1) AND @uk_name_exists > 0,
    'ALTER TABLE `hrm_employee` DROP INDEX `uk_hrm_employee_active_id_card`',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(@need_rebuild_col = 1 AND @col_exists > 0,
    'ALTER TABLE `hrm_employee` DROP COLUMN `active_id_card`',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'hrm_employee' AND column_name = 'active_id_card'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `hrm_employee` ADD COLUMN `active_id_card` varchar(18)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
        GENERATED ALWAYS AS (IF(`deleted` = 0, NULLIF(TRIM(`id_card`), \'\'), NULL)) STORED
        COMMENT ''在职身份证 NULLIF(TRIM) active-only'' AFTER `tenant_id`',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 列探针 fail-closed（错误表达式如 deleted=1 不得通过）
CALL `__exp82_probe_col_ok`(@col_ok);
CALL `__exp82_assert`(@col_ok = 1,
    'EXP-82 fail-closed: active_id_card must match IF(deleted=0, NULLIF(TRIM(id_card),''''), NULL) STORED.');

-- 再装全列 UK（前缀索引 sub_part 非空 → need_rebuild_uk 已触发 DROP）
SET @uk_name_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'hrm_employee'
      AND index_name = 'uk_hrm_employee_active_id_card'
);
CALL `__exp82_probe_uk_ok`(@uk_ok);
SET @sql := IF(@uk_name_exists > 0 AND IFNULL(@uk_ok, 0) = 0,
    'ALTER TABLE `hrm_employee` DROP INDEX `uk_hrm_employee_active_id_card`',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @uk_name_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'hrm_employee'
      AND index_name = 'uk_hrm_employee_active_id_card'
);
SET @sql := IF(@uk_name_exists = 0,
    'ALTER TABLE `hrm_employee` ADD UNIQUE KEY `uk_hrm_employee_active_id_card` (`tenant_id`, `active_id_card`) USING BTREE',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

CALL `__exp82_probe_uk_ok`(@uk_ok);
CALL `__exp82_assert`(@uk_ok = 1,
    'EXP-82 fail-closed: UK must be UNIQUE full-column (tenant_id, active_id_card) with SUB_PART NULL.');

-- ---------- 2) 仅在新保护通过后 DROP 旧 UK ----------
SET @old_uk := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'hrm_employee'
      AND index_name = 'uk_hrm_employee_id_card'
);
SET @sql := IF(@old_uk > 0,
    'ALTER TABLE `hrm_employee` DROP INDEX `uk_hrm_employee_id_card`',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 3) 最终双探针 ----------
CALL `__exp82_probe_col_ok`(@col_ok);
CALL `__exp82_probe_uk_ok`(@uk_ok);
CALL `__exp82_assert`(@col_ok = 1 AND @uk_ok = 1,
    'EXP-82 fail-closed: final probe failed for column or full-column UK.');

DROP PROCEDURE IF EXISTS `__exp82_assert`;
DROP PROCEDURE IF EXISTS `__exp82_probe_col_ok`;
DROP PROCEDURE IF EXISTS `__exp82_probe_uk_ok`;

SELECT 'EXP-82 active-only id_card UK migrate OK' AS result;
