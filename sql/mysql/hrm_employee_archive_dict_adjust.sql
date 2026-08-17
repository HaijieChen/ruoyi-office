-- 员工档案下拉字典对齐：
--   文化程度：高中 / 大专 / 本科 / 研究生 / 博士
--   政治面貌：群众 / 党员
--   婚姻状况：已婚 / 未婚 / 已婚未育 / 已婚已育 / 离异 / 丧偶
--   职务：补「组长」
-- 幂等：可重复执行。旧编码项（1/2/3…）停用，新项 value=中文标签，与花名册导入原文一致。

-- 1. 确保字典类型存在
INSERT INTO system_dict_type(name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time)
SELECT '文化程度', 'hrm_education', 0, '员工文化程度', '1', NOW(), '1', NOW(), false, NULL
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type = 'hrm_education' AND deleted = 0);

INSERT INTO system_dict_type(name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time)
SELECT '政治面貌', 'hrm_political_status', 0, '员工政治面貌', '1', NOW(), '1', NOW(), false, NULL
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type = 'hrm_political_status' AND deleted = 0);

INSERT INTO system_dict_type(name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time)
SELECT '婚姻状况', 'hrm_marital_status', 0, '员工婚姻状况', '1', NOW(), '1', NOW(), false, NULL
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type = 'hrm_marital_status' AND deleted = 0);

INSERT INTO system_dict_type(name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time)
SELECT '职务', 'hrm_job_position', 0, '员工职务', '1', NOW(), '1', NOW(), false, NULL
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type = 'hrm_job_position' AND deleted = 0);

INSERT INTO system_dict_type(name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time)
SELECT '血型', 'hrm_blood_type', 0, '员工血型', '1', NOW(), '1', NOW(), false, NULL
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type = 'hrm_blood_type' AND deleted = 0);

INSERT INTO system_dict_type(name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time)
SELECT '人员状态', 'hrm_employee_status', 0, '员工人员状态', '1', NOW(), '1', NOW(), false, NULL
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type = 'hrm_employee_status' AND deleted = 0);

INSERT INTO system_dict_type(name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time)
SELECT '户籍性质', 'hrm_household_type', 0, '员工户籍性质', '1', NOW(), '1', NOW(), false, NULL
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type = 'hrm_household_type' AND deleted = 0);

INSERT INTO system_dict_type(name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time)
SELECT '用工形式', 'hrm_employment_form', 0, '用工形式', '1', NOW(), '1', NOW(), false, NULL
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type = 'hrm_employment_form' AND deleted = 0);

INSERT INTO system_dict_type(name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time)
SELECT '学历类别', 'hrm_education_type', 0, '学历类别', '1', NOW(), '1', NOW(), false, NULL
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type = 'hrm_education_type' AND deleted = 0);

INSERT INTO system_dict_type(name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time)
SELECT '合同类型', 'hrm_contract_type', 0, '劳动合同类型', '1', NOW(), '1', NOW(), false, NULL
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type = 'hrm_contract_type' AND deleted = 0);

-- 2. 文化程度：只保留指定 5 项
UPDATE system_dict_data
SET status = 1, updater = '1', update_time = NOW()
WHERE dict_type = 'hrm_education' AND deleted = 0
  AND value NOT IN ('高中', '大专', '本科', '研究生', '博士');

INSERT INTO system_dict_data(dict_type, label, value, sort, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted)
SELECT 'hrm_education', d.label, d.value, d.sort, 0, 'default', '', '', '1', NOW(), '1', NOW(), false
FROM (
  SELECT '高中' AS label, '高中' AS value, 1 AS sort
  UNION ALL SELECT '大专', '大专', 2
  UNION ALL SELECT '本科', '本科', 3
  UNION ALL SELECT '研究生', '研究生', 4
  UNION ALL SELECT '博士', '博士', 5
) d
WHERE NOT EXISTS (
  SELECT 1 FROM system_dict_data x
  WHERE x.dict_type = 'hrm_education' AND x.value = d.value AND x.deleted = 0
);

UPDATE system_dict_data d
JOIN (
  SELECT '高中' AS value, 1 AS sort
  UNION ALL SELECT '大专', 2
  UNION ALL SELECT '本科', 3
  UNION ALL SELECT '研究生', 4
  UNION ALL SELECT '博士', 5
) t ON d.value = t.value
SET d.status = 0, d.label = t.value, d.sort = t.sort, d.updater = '1', d.update_time = NOW()
WHERE d.dict_type = 'hrm_education' AND d.deleted = 0;

-- 3. 政治面貌：群众、党员
UPDATE system_dict_data
SET status = 1, updater = '1', update_time = NOW()
WHERE dict_type = 'hrm_political_status' AND deleted = 0
  AND value NOT IN ('群众', '党员');

INSERT INTO system_dict_data(dict_type, label, value, sort, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted)
SELECT 'hrm_political_status', d.label, d.value, d.sort, 0, 'default', '', '', '1', NOW(), '1', NOW(), false
FROM (
  SELECT '群众' AS label, '群众' AS value, 1 AS sort
  UNION ALL SELECT '党员', '党员', 2
) d
WHERE NOT EXISTS (
  SELECT 1 FROM system_dict_data x
  WHERE x.dict_type = 'hrm_political_status' AND x.value = d.value AND x.deleted = 0
);

UPDATE system_dict_data d
JOIN (
  SELECT '群众' AS value, 1 AS sort
  UNION ALL SELECT '党员', 2
) t ON d.value = t.value
SET d.status = 0, d.label = t.value, d.sort = t.sort, d.updater = '1', d.update_time = NOW()
WHERE d.dict_type = 'hrm_political_status' AND d.deleted = 0;

-- 4. 婚姻状况
UPDATE system_dict_data
SET status = 1, updater = '1', update_time = NOW()
WHERE dict_type = 'hrm_marital_status' AND deleted = 0
  AND value NOT IN ('已婚', '未婚', '已婚未育', '已婚已育', '离异', '丧偶');

INSERT INTO system_dict_data(dict_type, label, value, sort, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted)
SELECT 'hrm_marital_status', d.label, d.value, d.sort, 0, 'default', '', '', '1', NOW(), '1', NOW(), false
FROM (
  SELECT '已婚' AS label, '已婚' AS value, 1 AS sort
  UNION ALL SELECT '未婚', '未婚', 2
  UNION ALL SELECT '已婚未育', '已婚未育', 3
  UNION ALL SELECT '已婚已育', '已婚已育', 4
  UNION ALL SELECT '离异', '离异', 5
  UNION ALL SELECT '丧偶', '丧偶', 6
) d
WHERE NOT EXISTS (
  SELECT 1 FROM system_dict_data x
  WHERE x.dict_type = 'hrm_marital_status' AND x.value = d.value AND x.deleted = 0
);

UPDATE system_dict_data d
JOIN (
  SELECT '已婚' AS value, 1 AS sort
  UNION ALL SELECT '未婚', 2
  UNION ALL SELECT '已婚未育', 3
  UNION ALL SELECT '已婚已育', 4
  UNION ALL SELECT '离异', 5
  UNION ALL SELECT '丧偶', 6
) t ON d.value = t.value
SET d.status = 0, d.label = t.value, d.sort = t.sort, d.updater = '1', d.update_time = NOW()
WHERE d.dict_type = 'hrm_marital_status' AND d.deleted = 0;

-- 5. 职务补组长
INSERT INTO system_dict_data(dict_type, label, value, sort, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted)
SELECT 'hrm_job_position', '组长', '组长', 7, 0, 'default', '', '', '1', NOW(), '1', NOW(), false
WHERE NOT EXISTS (
  SELECT 1 FROM system_dict_data x
  WHERE x.dict_type = 'hrm_job_position' AND x.deleted = 0
    AND (x.value = '组长' OR x.label = '组长')
);
