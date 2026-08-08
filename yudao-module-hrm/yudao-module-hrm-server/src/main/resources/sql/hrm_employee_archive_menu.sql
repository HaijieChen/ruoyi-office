-- ----------------------------
-- 员工档案管理菜单 SQL
-- ----------------------------

-- 菜单 SQL 使用动态父菜单和前端真实组件路径，避免依赖硬编码 ID。
SET @hrm_menu_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `name` = '人力资源管理' AND `parent_id` = 0
    ORDER BY `id` LIMIT 1
);
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`)
SELECT '人力资源管理', '', 1, 30, 0, '/hrm', 'ep:user', NULL, NULL, 0, b'1', b'1', b'1'
WHERE @hrm_menu_id IS NULL;
SET @hrm_menu_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `name` = '人力资源管理' AND `parent_id` = 0
    ORDER BY `id` LIMIT 1
);

SET @parent_menu_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'hrm/employee/list/index'
    ORDER BY `id` LIMIT 1
);
SET @legacy_parent_menu_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `name` = '员工档案管理'
    ORDER BY `id` LIMIT 1
);
SET @parent_menu_id = COALESCE(@parent_menu_id, @legacy_parent_menu_id);
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`)
SELECT '员工档案管理', '', 2, 10, @hrm_menu_id, 'employee', 'ant-design:solution-outlined',
       'hrm/employee/list/index', 'HrmEmployeeArchiveList', 0, b'1', b'1', b'1'
WHERE @parent_menu_id IS NULL;
SET @parent_menu_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'hrm/employee/list/index'
    ORDER BY `id` LIMIT 1
);
SET @parent_menu_id = COALESCE(@parent_menu_id, @legacy_parent_menu_id);
UPDATE `system_menu`
SET `parent_id` = @hrm_menu_id, `path` = 'employee', `component` = 'hrm/employee/list/index',
    `component_name` = 'HrmEmployeeArchiveList', `status` = 0, `visible` = b'1', `deleted` = b'0'
WHERE `id` = @parent_menu_id;

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`)
SELECT '员工档案详情', 'hrm:employee-archive:query', 2, 11, @parent_menu_id,
       '/hrm/employee/employee-archive-info', '', 'hrm/employee/info/index', 'HrmEmployeeArchiveInfo',
       0, b'0', b'1', b'1'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'hrm/employee/info/index'
);
UPDATE `system_menu`
SET `parent_id` = @parent_menu_id, `path` = '/hrm/employee/employee-archive-info',
    `component` = 'hrm/employee/info/index', `component_name` = 'HrmEmployeeArchiveInfo',
    `permission` = 'hrm:employee-archive:query', `status` = 0, `visible` = b'0', `deleted` = b'0'
WHERE `deleted` = b'0' AND `component` = 'hrm/employee/info/index';

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`)
SELECT p.`name`, p.`permission`, 3, p.`sort`, @parent_menu_id, '', '', NULL, NULL,
       0, b'1', b'1', b'1'
FROM (
    SELECT '员工档案查询' AS `name`, 'hrm:employee-archive:query' AS `permission`, 1 AS `sort`
    UNION ALL SELECT '员工档案创建', 'hrm:employee-archive:create', 2
    UNION ALL SELECT '员工档案更新', 'hrm:employee-archive:update', 3
    UNION ALL SELECT '员工档案删除', 'hrm:employee-archive:delete', 4
    UNION ALL SELECT '员工档案导出', 'hrm:employee-archive:export', 5
) p
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu` m
    WHERE m.`deleted` = b'0' AND m.`parent_id` = @parent_menu_id
      AND m.`permission` = p.`permission`
);

-- ----------------------------
-- 字典数据 SQL
-- ----------------------------

-- 血型字典
INSERT INTO system_dict_type(name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time)
VALUES ('血型', 'hrm_blood_type', 0, '员工血型', '1', NOW(), '1', NOW(), false, NULL);

SET @dict_type_id = 'hrm_blood_type';

INSERT INTO system_dict_data(dict_type, label, value, sort, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES
(@dict_type_id, 'A型', '1', 1, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, 'B型', '2', 2, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, 'AB型', '3', 3, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, 'O型', '4', 4, 0, 'default', '', '', '1', NOW(), '1', NOW(), false);

-- 人员状态字典
INSERT INTO system_dict_type(name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time)
VALUES ('人员状态', 'hrm_employee_status', 0, '员工人员状态', '1', NOW(), '1', NOW(), false, NULL);

SET @dict_type_id = 'hrm_employee_status';

INSERT INTO system_dict_data(dict_type, label, value, sort, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES
(@dict_type_id, '正式', '1', 1, 0, 'success', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '试用期', '2', 2, 0, 'warning', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '实习生', '3', 3, 0, 'info', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '临时工', '5', 5, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '离职', '6', 6, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '退休', '7', 6, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
;

-- 职务字典
INSERT INTO system_dict_type(name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time)
VALUES ('职务', 'hrm_job_position', 0, '员工职务', '1', NOW(), '1', NOW(), false, NULL);

SET @dict_type_id = 'hrm_job_position';

INSERT INTO system_dict_data(dict_type, label, value, sort, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES
(@dict_type_id, '总经理', '1', 1, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '副总经理', '2', 2, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '部门经理', '3', 3, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '副部门经理', '4', 4, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '主管', '5', 5, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '副主管', '6', 6, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '专员', '7', 7, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '助理', '8', 8, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '其他', '9', 9, 0, 'default', '', '', '1', NOW(), '1', NOW(), false);

-- 文化程度字典
INSERT INTO system_dict_type(name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time)
VALUES ('文化程度', 'hrm_education', 0, '员工文化程度', '1', NOW(), '1', NOW(), false, NULL);

SET @dict_type_id = 'hrm_education';

INSERT INTO system_dict_data(dict_type, label, value, sort, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES
(@dict_type_id, '小学', '1', 1, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '初中', '2', 2, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '高中', '3', 3, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '中专', '4', 4, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '大专', '5', 5, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '本科', '6', 6, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '硕士', '7', 7, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '博士', '8', 8, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '其他', '9', 9, 0, 'default', '', '', '1', NOW(), '1', NOW(), false);

-- 民族字典
INSERT INTO system_dict_type(name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time)
VALUES ('民族', 'hrm_nation', 0, '员工民族', '1', NOW(), '1', NOW(), false, NULL);

SET @dict_type_id = 'hrm_nation';

INSERT INTO system_dict_data(dict_type, label, value, sort, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES
(@dict_type_id, '汉族', '1', 1, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '蒙古族', '2', 2, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '回族', '3', 3, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '藏族', '4', 4, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '维吾尔族', '5', 5, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '苗族', '6', 6, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '彝族', '7', 7, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '壮族', '8', 8, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '布依族', '9', 9, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '朝鲜族', '10', 10, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '满族', '11', 11, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '侗族', '12', 12, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '瑶族', '13', 13, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '白族', '14', 14, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '土家族', '15', 15, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '哈尼族', '16', 16, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '哈萨克族', '17', 17, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '傣族', '18', 18, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '黎族', '19', 19, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '傈僳族', '20', 20, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '佤族', '21', 21, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '畲族', '22', 22, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '高山族', '23', 23, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '拉祜族', '24', 24, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '水族', '25', 25, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '东乡族', '26', 26, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '纳西族', '27', 27, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '景颇族', '28', 28, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '柯尔克孜族', '29', 29, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '土族', '30', 30, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '达斡尔族', '31', 31, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '仫佬族', '32', 32, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '羌族', '33', 33, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '布朗族', '34', 34, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '撒拉族', '35', 35, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '毛南族', '36', 36, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '仡佬族', '37', 37, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '锡伯族', '38', 38, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '阿昌族', '39', 39, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '普米族', '40', 40, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '塔吉克族', '41', 41, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '怒族', '42', 42, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '乌孜别克族', '43', 43, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '俄罗斯族', '44', 44, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '鄂温克族', '45', 45, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '德昂族', '46', 46, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '保安族', '47', 47, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '裕固族', '48', 48, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '京族', '49', 49, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '塔塔尔族', '50', 50, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '独龙族', '51', 51, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '鄂伦春族', '52', 52, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '赫哲族', '53', 53, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '门巴族', '54', 54, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '珞巴族', '55', 55, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '基诺族', '56', 56, 0, 'default', '', '', '1', NOW(), '1', NOW(), false);

-- 政治面貌字典
INSERT INTO system_dict_type(name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time)
VALUES ('政治面貌', 'hrm_political_status', 0, '员工政治面貌', '1', NOW(), '1', NOW(), false, NULL);

SET @dict_type_id = 'hrm_political_status';

INSERT INTO system_dict_data(dict_type, label, value, sort, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES
(@dict_type_id, '中共党员', '1', 1, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '中共预备党员', '2', 2, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '共青团员', '3', 3, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '民主党派', '4', 4, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '群众', '5', 5, 0, 'default', '', '', '1', NOW(), '1', NOW(), false);

-- 婚姻状况字典
INSERT INTO system_dict_type(name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time)
VALUES ('婚姻状况', 'hrm_marital_status', 0, '员工婚姻状况', '1', NOW(), '1', NOW(), false, NULL);

SET @dict_type_id = 'hrm_marital_status';

INSERT INTO system_dict_data(dict_type, label, value, sort, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES
(@dict_type_id, '未婚', '1', 1, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '已婚', '2', 2, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '离异', '3', 3, 0, 'default', '', '', '1', NOW(), '1', NOW(), false),
(@dict_type_id, '丧偶', '4', 4, 0, 'default', '', '', '1', NOW(), '1', NOW(), false);
