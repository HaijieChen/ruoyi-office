-- ----------------------------
-- EXP-75 文枢员工花名册字段补齐（幂等 / 可中断续跑）
-- 每步先判定当前状态；重复执行不会因“列已存在”中断后续步骤
-- 历史数据新增列保持 NULL；不默认社保/公积金为「否」
-- ----------------------------

SET NAMES utf8mb4;

-- 通用：按表/列是否存在执行 DDL
-- 用法：CALL 不可用时用 information_schema + PREPARE

-- 1. 员工主档新增可空列（逐列检查）
SET @db := DATABASE();

-- social_security_enabled
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'hrm_employee' AND COLUMN_NAME = 'social_security_enabled');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `hrm_employee` ADD COLUMN `social_security_enabled` bit(1) NULL DEFAULT NULL COMMENT ''是否缴纳社保'' AFTER `formal_date`',
  'SELECT ''skip social_security_enabled'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'hrm_employee' AND COLUMN_NAME = 'housing_fund_enabled');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `hrm_employee` ADD COLUMN `housing_fund_enabled` bit(1) NULL DEFAULT NULL COMMENT ''是否缴纳公积金'' AFTER `social_security_enabled`',
  'SELECT ''skip housing_fund_enabled'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'hrm_employee' AND COLUMN_NAME = 'social_security_start_month');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `hrm_employee` ADD COLUMN `social_security_start_month` char(7) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT ''参保年月 yyyy-MM'' AFTER `housing_fund_enabled`',
  'SELECT ''skip social_security_start_month'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'hrm_employee' AND COLUMN_NAME = 'probation_salary');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `hrm_employee` ADD COLUMN `probation_salary` decimal(12,2) NULL DEFAULT NULL COMMENT ''试用期薪资'' AFTER `social_security_start_month`',
  'SELECT ''skip probation_salary'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'hrm_employee' AND COLUMN_NAME = 'regular_salary');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `hrm_employee` ADD COLUMN `regular_salary` decimal(12,2) NULL DEFAULT NULL COMMENT ''转正薪资'' AFTER `probation_salary`',
  'SELECT ''skip regular_salary'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'hrm_employee' AND COLUMN_NAME = 'fertility_status');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `hrm_employee` ADD COLUMN `fertility_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT ''生育状况'' AFTER `regular_salary`',
  'SELECT ''skip fertility_status'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'hrm_employee' AND COLUMN_NAME = 'household_type');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `hrm_employee` ADD COLUMN `household_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT ''户籍性质'' AFTER `fertility_status`',
  'SELECT ''skip household_type'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'hrm_employee' AND COLUMN_NAME = 'employment_form');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `hrm_employee` ADD COLUMN `employment_form` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT ''用工形式'' AFTER `household_type`',
  'SELECT ''skip employment_form'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'hrm_employee' AND COLUMN_NAME = 'emergency_relationship');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `hrm_employee` ADD COLUMN `emergency_relationship` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT ''紧急联系人关系'' AFTER `emergency_phone`',
  'SELECT ''skip emergency_relationship'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'hrm_employee' AND COLUMN_NAME = 'recruitment_channel');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `hrm_employee` ADD COLUMN `recruitment_channel` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT ''招聘渠道'' AFTER `emergency_relationship`',
  'SELECT ''skip recruitment_channel'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'hrm_employee' AND COLUMN_NAME = 'interviewer_name');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `hrm_employee` ADD COLUMN `interviewer_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT ''面试人'' AFTER `recruitment_channel`',
  'SELECT ''skip interviewer_name'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 教育明细扩展
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'hrm_employee_education' AND COLUMN_NAME = 'education_level');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `hrm_employee_education` ADD COLUMN `education_level` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT ''学历'' AFTER `end_time`',
  'SELECT ''skip education_level'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'hrm_employee_education' AND COLUMN_NAME = 'education_type');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `hrm_employee_education` ADD COLUMN `education_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT ''学历类别'' AFTER `education_level`',
  'SELECT ''skip education_type'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'hrm_employee_education' AND COLUMN_NAME = 'degree');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `hrm_employee_education` ADD COLUMN `degree` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT ''学位'' AFTER `education_type`',
  'SELECT ''skip degree'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'hrm_employee_education' AND COLUMN_NAME = 'first_education');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `hrm_employee_education` ADD COLUMN `first_education` bit(1) NOT NULL DEFAULT b''0'' COMMENT ''是否第一学历'' AFTER `degree`',
  'SELECT ''skip first_education'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'hrm_employee_education' AND COLUMN_NAME = 'highest_education');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `hrm_employee_education` ADD COLUMN `highest_education` bit(1) NOT NULL DEFAULT b''0'' COMMENT ''是否最高学历'' AFTER `first_education`',
  'SELECT ''skip highest_education'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 合同明细表
CREATE TABLE IF NOT EXISTS `hrm_employee_contract` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `employee_id` bigint NOT NULL COMMENT '员工ID',
  `sequence_no` tinyint NOT NULL COMMENT '合同序号 1-4',
  `contract_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '合同类型',
  `start_date` date NOT NULL COMMENT '合同开始日期',
  `end_date` date NULL DEFAULT NULL COMMENT '合同结束日期',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_employee_id` (`employee_id`) USING BTREE,
  KEY `idx_employee_sequence` (`employee_id`, `sequence_no`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工合同明细表';

-- 4-8. 字典（类型 + 数据，均幂等）
INSERT INTO system_dict_type(name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time)
SELECT '生育状况', 'hrm_fertility_status', 0, '员工生育状况', '1', NOW(), '1', NOW(), false, NULL
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type = 'hrm_fertility_status' AND deleted = 0);

INSERT INTO system_dict_data(dict_type, label, value, sort, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted)
SELECT 'hrm_fertility_status', d.label, d.value, d.sort, 0, 'default', '', '', '1', NOW(), '1', NOW(), false
FROM (
  SELECT '未育' AS label, '1' AS value, 1 AS sort
  UNION ALL SELECT '已育', '2', 2
  UNION ALL SELECT '不详', '3', 3
) d
WHERE NOT EXISTS (
  SELECT 1 FROM system_dict_data x WHERE x.dict_type = 'hrm_fertility_status' AND x.value = d.value AND x.deleted = 0
);

INSERT INTO system_dict_type(name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time)
SELECT '户籍性质', 'hrm_household_type', 0, '员工户籍性质', '1', NOW(), '1', NOW(), false, NULL
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type = 'hrm_household_type' AND deleted = 0);

INSERT INTO system_dict_data(dict_type, label, value, sort, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted)
SELECT 'hrm_household_type', d.label, d.value, d.sort, 0, 'default', '', '', '1', NOW(), '1', NOW(), false
FROM (
  SELECT '农业户口' AS label, '1' AS value, 1 AS sort
  UNION ALL SELECT '非农业户口', '2', 2
  UNION ALL SELECT '居民户口', '3', 3
  UNION ALL SELECT '其他', '4', 4
) d
WHERE NOT EXISTS (
  SELECT 1 FROM system_dict_data x WHERE x.dict_type = 'hrm_household_type' AND x.value = d.value AND x.deleted = 0
);

INSERT INTO system_dict_type(name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time)
SELECT '学历类别', 'hrm_education_type', 0, '学历类别', '1', NOW(), '1', NOW(), false, NULL
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type = 'hrm_education_type' AND deleted = 0);

INSERT INTO system_dict_data(dict_type, label, value, sort, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted)
SELECT 'hrm_education_type', d.label, d.value, d.sort, 0, 'default', '', '', '1', NOW(), '1', NOW(), false
FROM (
  SELECT '全日制' AS label, '1' AS value, 1 AS sort
  UNION ALL SELECT '非全日制', '2', 2
  UNION ALL SELECT '其他', '3', 3
) d
WHERE NOT EXISTS (
  SELECT 1 FROM system_dict_data x WHERE x.dict_type = 'hrm_education_type' AND x.value = d.value AND x.deleted = 0
);

INSERT INTO system_dict_type(name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time)
SELECT '用工形式', 'hrm_employment_form', 0, '用工形式', '1', NOW(), '1', NOW(), false, NULL
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type = 'hrm_employment_form' AND deleted = 0);

INSERT INTO system_dict_data(dict_type, label, value, sort, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted)
SELECT 'hrm_employment_form', d.label, d.value, d.sort, 0, 'default', '', '', '1', NOW(), '1', NOW(), false
FROM (
  SELECT '全日制' AS label, '1' AS value, 1 AS sort
  UNION ALL SELECT '非全日制', '2', 2
  UNION ALL SELECT '劳务派遣', '3', 3
  UNION ALL SELECT '退休返聘', '4', 4
  UNION ALL SELECT '实习', '5', 5
  UNION ALL SELECT '其他', '6', 6
) d
WHERE NOT EXISTS (
  SELECT 1 FROM system_dict_data x WHERE x.dict_type = 'hrm_employment_form' AND x.value = d.value AND x.deleted = 0
);

INSERT INTO system_dict_type(name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time)
SELECT '合同类型', 'hrm_contract_type', 0, '劳动合同类型', '1', NOW(), '1', NOW(), false, NULL
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type = 'hrm_contract_type' AND deleted = 0);

INSERT INTO system_dict_data(dict_type, label, value, sort, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted)
SELECT 'hrm_contract_type', d.label, d.value, d.sort, 0, 'default', '', '', '1', NOW(), '1', NOW(), false
FROM (
  SELECT '固定期限' AS label, '1' AS value, 1 AS sort
  UNION ALL SELECT '无固定期限', '2', 2
  UNION ALL SELECT '以完成一定工作任务为期限', '3', 3
  UNION ALL SELECT '劳务协议', '4', 4
  UNION ALL SELECT '实习协议', '5', 5
  UNION ALL SELECT '其他', '6', 6
) d
WHERE NOT EXISTS (
  SELECT 1 FROM system_dict_data x WHERE x.dict_type = 'hrm_contract_type' AND x.value = d.value AND x.deleted = 0
);

-- 9. common_attachment.file_id（权威文件 claim）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'common_attachment' AND COLUMN_NAME = 'file_id');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `common_attachment` ADD COLUMN `file_id` bigint NULL DEFAULT NULL COMMENT ''关联 infra_file 编号'' AFTER `business_id`',
  'SELECT ''skip common_attachment.file_id'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 10. 入职资料 claim 表（一次性 token，绑定租户/上传者/用途）
CREATE TABLE IF NOT EXISTS `hrm_onboarding_file_claim` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `claim_token` varchar(64) NOT NULL COMMENT '客户端 claim token',
  `file_id` bigint NOT NULL COMMENT 'infra_file 编号',
  `uploader_user_id` bigint NOT NULL COMMENT '上传者用户编号',
  `purpose` varchar(32) NOT NULL DEFAULT 'hrm-onboarding' COMMENT '用途',
  `expire_time` datetime NOT NULL COMMENT '过期时间',
  `consumed_at` datetime NULL DEFAULT NULL COMMENT '消费时间',
  `consumed_employee_id` bigint NULL DEFAULT NULL COMMENT '绑定员工编号',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_claim_token` (`claim_token`) USING BTREE,
  KEY `idx_file_id` (`file_id`) USING BTREE,
  KEY `idx_uploader` (`uploader_user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='入职资料文件 claim';

-- 11. 历史 file_id 唯一身份（字节精确 BINARY）
-- 权威候选：唯一 BINARY URL 优先，否则唯一 BINARY path；或 file_id 与 path 字节一致
-- 可重跑：先纠错，再回填，再 path 规范化，最后清 URL
--
-- 【发布前快照 / 回滚】
-- 1) before-image: mysqldump 至少 common_attachment(id,business_type,file_id,file_path,file_url,deleted)
--                  与 infra_file(id,path,url,deleted)
-- 2) 回滚: 按 id 恢复 file_id/file_path/file_url；勿 DROP 列
-- 3) 完整脚本必须连跑两次，URL-only 与 path 歧义夹具结果应稳定正确

-- 11a. 清空 missing / deleted 目标（活动行）
UPDATE `common_attachment` a
LEFT JOIN `infra_file` f ON f.id = a.file_id AND f.deleted = b'0'
SET a.file_id = NULL
WHERE LOWER(TRIM(a.business_type)) IN ('hrm_employee_archive_onboarding', '201')
  AND a.deleted = b'0'
  AND a.file_id IS NOT NULL
  AND f.id IS NULL;

-- 11b. 清空非权威非空 file_id（BINARY；file_id+path 一致视为权威）
UPDATE `common_attachment` a
SET a.file_id = NULL
WHERE LOWER(TRIM(a.business_type)) IN ('hrm_employee_archive_onboarding', '201')
  AND a.deleted = b'0'
  AND a.file_id IS NOT NULL
  AND NOT (
    EXISTS (
      SELECT 1 FROM `infra_file` f
      WHERE f.id = a.file_id AND f.deleted = b'0'
        AND BINARY f.path = BINARY a.file_path
    )
    OR EXISTS (
      SELECT 1 FROM (
        SELECT BINARY f.url AS u, MIN(f.id) AS fid
        FROM `infra_file` f
        WHERE f.deleted = b'0' AND f.url IS NOT NULL AND f.url <> ''
        GROUP BY BINARY f.url
        HAVING COUNT(*) = 1
      ) cand WHERE cand.u = BINARY a.file_url AND cand.fid = a.file_id
    )
    OR (
      NOT EXISTS (
        SELECT 1 FROM (
          SELECT BINARY f.url AS u
          FROM `infra_file` f
          WHERE f.deleted = b'0' AND f.url IS NOT NULL AND f.url <> ''
          GROUP BY BINARY f.url
          HAVING COUNT(*) = 1
        ) cand WHERE cand.u = BINARY a.file_url
      )
      AND EXISTS (
        SELECT 1 FROM (
          SELECT BINARY f.path AS p, MIN(f.id) AS fid
          FROM `infra_file` f
          WHERE f.deleted = b'0' AND f.path IS NOT NULL AND f.path <> ''
          GROUP BY BINARY f.path
          HAVING COUNT(*) = 1
        ) cand WHERE cand.p = BINARY a.file_path AND cand.fid = a.file_id
      )
    )
  );

-- 11c. 唯一 BINARY URL 回填
UPDATE `common_attachment` a
INNER JOIN (
  SELECT BINARY f.url AS u, MIN(f.id) AS fid
  FROM `infra_file` f
  WHERE f.deleted = b'0' AND f.url IS NOT NULL AND f.url <> ''
  GROUP BY BINARY f.url
  HAVING COUNT(*) = 1
) cand ON cand.u = BINARY a.file_url
SET a.file_id = cand.fid
WHERE LOWER(TRIM(a.business_type)) IN ('hrm_employee_archive_onboarding', '201')
  AND a.deleted = b'0'
  AND a.file_id IS NULL
  AND a.file_url IS NOT NULL
  AND a.file_url <> '';

-- 11d. 唯一 BINARY path 回填
UPDATE `common_attachment` a
INNER JOIN (
  SELECT BINARY f.path AS p, MIN(f.id) AS fid
  FROM `infra_file` f
  WHERE f.deleted = b'0' AND f.path IS NOT NULL AND f.path <> ''
  GROUP BY BINARY f.path
  HAVING COUNT(*) = 1
) cand ON cand.p = BINARY a.file_path
SET a.file_id = cand.fid
WHERE LOWER(TRIM(a.business_type)) IN ('hrm_employee_archive_onboarding', '201')
  AND a.deleted = b'0'
  AND a.file_id IS NULL
  AND a.file_path IS NOT NULL
  AND a.file_path <> ''
  AND NOT EXISTS (
    SELECT 1 FROM (
      SELECT BINARY f.url AS u
      FROM `infra_file` f
      WHERE f.deleted = b'0' AND f.url IS NOT NULL AND f.url <> ''
      GROUP BY BINARY f.url
      HAVING COUNT(*) = 1
    ) uc WHERE uc.u = BINARY a.file_url
  );

-- 11e. 有 file_id 时 path 规范为权威 infra_file.path（URL-only 绑定后可续跑验证）
UPDATE `common_attachment` a
INNER JOIN `infra_file` f ON f.id = a.file_id AND f.deleted = b'0'
SET a.file_path = f.path
WHERE LOWER(TRIM(a.business_type)) IN ('hrm_employee_archive_onboarding', '201')
  AND a.deleted = b'0'
  AND a.file_id IS NOT NULL;

-- 12. 历史入职单附件 → 员工档案入职资料（含空白别名 ' 201 '）
-- path 优先用权威 infra_file.path；file_url 置空
INSERT INTO `common_attachment` (
  `business_type`, `business_id`, `file_id`, `file_name`, `file_path`, `file_url`,
  `file_size`, `file_type`, `file_extension`, `upload_time`, `sort_order`,
  `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`
)
SELECT
  'hrm_employee_archive_onboarding',
  e.employee_id,
  a.file_id,
  a.file_name,
  COALESCE(f.path, a.file_path),
  '',
  a.file_size,
  a.file_type,
  a.file_extension,
  a.upload_time,
  a.sort_order,
  a.remark,
  a.creator,
  NOW(),
  a.updater,
  NOW(),
  b'0',
  a.tenant_id
FROM `common_attachment` a
INNER JOIN `hrm_employee_entry_bill` e ON e.id = a.business_id AND e.deleted = b'0'
LEFT JOIN `infra_file` f ON f.id = a.file_id AND f.deleted = b'0'
WHERE TRIM(a.business_type) = '201'
  AND a.deleted = b'0'
  AND e.employee_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `common_attachment` t
    WHERE t.business_type = 'hrm_employee_archive_onboarding'
      AND t.business_id = e.employee_id
      AND BINARY t.file_path = BINARY COALESCE(f.path, a.file_path)
      AND t.tenant_id = a.tenant_id
  );

-- 13. 转档后再纠错+回填 onboarding（含 file_id+path 一致权威）
UPDATE `common_attachment` a
LEFT JOIN `infra_file` f ON f.id = a.file_id AND f.deleted = b'0'
SET a.file_id = NULL
WHERE a.business_type = 'hrm_employee_archive_onboarding'
  AND a.deleted = b'0'
  AND a.file_id IS NOT NULL
  AND f.id IS NULL;

UPDATE `common_attachment` a
SET a.file_id = NULL
WHERE a.business_type = 'hrm_employee_archive_onboarding'
  AND a.deleted = b'0'
  AND a.file_id IS NOT NULL
  AND NOT (
    EXISTS (
      SELECT 1 FROM `infra_file` f
      WHERE f.id = a.file_id AND f.deleted = b'0'
        AND BINARY f.path = BINARY a.file_path
    )
    OR EXISTS (
      SELECT 1 FROM (
        SELECT BINARY f.url AS u, MIN(f.id) AS fid
        FROM `infra_file` f
        WHERE f.deleted = b'0' AND f.url IS NOT NULL AND f.url <> ''
        GROUP BY BINARY f.url HAVING COUNT(*) = 1
      ) cand WHERE cand.u = BINARY a.file_url AND cand.fid = a.file_id
    )
    OR (
      NOT EXISTS (
        SELECT 1 FROM (
          SELECT BINARY f.url AS u FROM `infra_file` f
          WHERE f.deleted = b'0' AND f.url IS NOT NULL AND f.url <> ''
          GROUP BY BINARY f.url HAVING COUNT(*) = 1
        ) cand WHERE cand.u = BINARY a.file_url
      )
      AND EXISTS (
        SELECT 1 FROM (
          SELECT BINARY f.path AS p, MIN(f.id) AS fid
          FROM `infra_file` f
          WHERE f.deleted = b'0' AND f.path IS NOT NULL AND f.path <> ''
          GROUP BY BINARY f.path HAVING COUNT(*) = 1
        ) cand WHERE cand.p = BINARY a.file_path AND cand.fid = a.file_id
      )
    )
  );

UPDATE `common_attachment` a
INNER JOIN (
  SELECT BINARY f.url AS u, MIN(f.id) AS fid
  FROM `infra_file` f
  WHERE f.deleted = b'0' AND f.url IS NOT NULL AND f.url <> ''
  GROUP BY BINARY f.url HAVING COUNT(*) = 1
) cand ON cand.u = BINARY a.file_url
SET a.file_id = cand.fid
WHERE a.business_type = 'hrm_employee_archive_onboarding'
  AND a.deleted = b'0'
  AND a.file_id IS NULL
  AND a.file_url IS NOT NULL AND a.file_url <> '';

UPDATE `common_attachment` a
INNER JOIN (
  SELECT BINARY f.path AS p, MIN(f.id) AS fid
  FROM `infra_file` f
  WHERE f.deleted = b'0' AND f.path IS NOT NULL AND f.path <> ''
  GROUP BY BINARY f.path HAVING COUNT(*) = 1
) cand ON cand.p = BINARY a.file_path
SET a.file_id = cand.fid
WHERE a.business_type = 'hrm_employee_archive_onboarding'
  AND a.deleted = b'0'
  AND a.file_id IS NULL
  AND a.file_path IS NOT NULL AND a.file_path <> ''
  AND NOT EXISTS (
    SELECT 1 FROM (
      SELECT BINARY f.url AS u FROM `infra_file` f
      WHERE f.deleted = b'0' AND f.url IS NOT NULL AND f.url <> ''
      GROUP BY BINARY f.url HAVING COUNT(*) = 1
    ) uc WHERE uc.u = BINARY a.file_url
  );

-- 13e. 再次 path 规范化（保证二次执行权威）
UPDATE `common_attachment` a
INNER JOIN `infra_file` f ON f.id = a.file_id AND f.deleted = b'0'
SET a.file_path = f.path
WHERE a.business_type = 'hrm_employee_archive_onboarding'
  AND a.deleted = b'0'
  AND a.file_id IS NOT NULL;

-- 15. 清空入职资料/入职单公开 URL（含已软删行，防复活后仍可拼链）
UPDATE `common_attachment`
SET `file_url` = ''
WHERE LOWER(TRIM(`business_type`)) IN ('hrm_employee_archive_onboarding', '201')
  AND `file_url` IS NOT NULL
  AND `file_url` <> '';

-- 14. 修复清单见 hrm_employee_roster_exp75_fileid_repair_check.sql

