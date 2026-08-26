SET NAMES utf8mb4;

SET @db := DATABASE();

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'hrm_employee_employment' AND COLUMN_NAME = 'dept_id');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `hrm_employee_employment` ADD COLUMN `dept_id` bigint NULL COMMENT ''任职部门 system_dept.id'' AFTER `company_dept_id`',
  'SELECT ''skip dept_id'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `hrm_employee_employment` x
INNER JOIN `hrm_employee` e ON e.id = x.employee_id AND e.deleted = b'0'
SET x.dept_id = e.dept_id
WHERE x.deleted = b'0' AND x.signed = b'1' AND x.dept_id IS NULL AND e.dept_id IS NOT NULL;
