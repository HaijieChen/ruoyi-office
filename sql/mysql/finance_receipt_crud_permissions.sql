-- =============================================================================
-- 银行到款手工增删改权限点
-- 幂等：可重复执行
-- =============================================================================

SET NAMES utf8mb4;

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT perm.name, perm.permission, 3, perm.sort, page_menu.id, '', '', '', NULL,
       0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM (
    SELECT '银行到款创建' AS name, 'finance:receipt:create' AS permission, 6 AS sort
    UNION ALL SELECT '银行到款更新', 'finance:receipt:update', 7
    UNION ALL SELECT '银行到款删除', 'finance:receipt:delete', 8
) perm
CROSS JOIN (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'finance/receipt/index' LIMIT 1
) page_menu
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `permission` = perm.permission
);

UPDATE `system_menu` m
JOIN (
    SELECT '银行到款创建' AS name, 'finance:receipt:create' AS permission, 6 AS sort
    UNION ALL SELECT '银行到款更新', 'finance:receipt:update', 7
    UNION ALL SELECT '银行到款删除', 'finance:receipt:delete', 8
) perm ON m.`permission` = perm.permission
SET m.`name` = perm.name,
    m.`sort` = perm.sort,
    m.`parent_id` = (
        SELECT page_menu.id FROM (
            SELECT `id` FROM `system_menu`
            WHERE `deleted` = b'0' AND `component` = 'finance/receipt/index' LIMIT 1
        ) page_menu
    ),
    m.`update_time` = NOW()
WHERE m.`deleted` = b'0';
