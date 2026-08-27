SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `hrm_employee_employment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `employee_id` bigint NOT NULL,
  `company_dept_id` bigint NOT NULL COMMENT '任职公司 system_dept.id',
  `signed` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否签约公司',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_employee` (`employee_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工任职公司';

INSERT INTO `hrm_employee_employment` (
  `employee_id`, `company_dept_id`, `signed`, `creator`, `create_time`, `updater`, `update_time`, `deleted`
)
SELECT e.id, e.company_id, b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM `hrm_employee` e
WHERE e.deleted = b'0' AND e.company_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `hrm_employee_employment` x
    WHERE x.deleted = b'0' AND x.employee_id = e.id AND x.company_dept_id = e.company_id
  );
