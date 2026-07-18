-- Dynamic forms with data-source linkage can exceed the historical VARCHAR(5000) limit.
-- This migration is safe to execute repeatedly and also widens the deployed-definition snapshot.

SET @bpm_form_fields_type = (
    SELECT DATA_TYPE
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'bpm_form'
      AND COLUMN_NAME = 'fields'
    LIMIT 1
);
SET @bpm_form_fields_sql = IF(
    @bpm_form_fields_type IS NULL,
    'SELECT ''bpm_form.fields does not exist'' AS migration_warning',
    IF(
        @bpm_form_fields_type = 'longtext',
        'SELECT ''bpm_form.fields is already LONGTEXT'' AS migration_status',
        'ALTER TABLE `bpm_form` MODIFY COLUMN `fields` LONGTEXT NOT NULL COMMENT ''表单项的数组'''
    )
);
PREPARE bpm_form_fields_stmt FROM @bpm_form_fields_sql;
EXECUTE bpm_form_fields_stmt;
DEALLOCATE PREPARE bpm_form_fields_stmt;

SET @bpm_definition_fields_type = (
    SELECT DATA_TYPE
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'bpm_process_definition_info'
      AND COLUMN_NAME = 'form_fields'
    LIMIT 1
);
SET @bpm_definition_fields_sql = IF(
    @bpm_definition_fields_type IS NULL,
    'SELECT ''bpm_process_definition_info.form_fields does not exist'' AS migration_warning',
    IF(
        @bpm_definition_fields_type = 'longtext',
        'SELECT ''bpm_process_definition_info.form_fields is already LONGTEXT'' AS migration_status',
        'ALTER TABLE `bpm_process_definition_info` MODIFY COLUMN `form_fields` LONGTEXT NULL COMMENT ''表单项的数组'''
    )
);
PREPARE bpm_definition_fields_stmt FROM @bpm_definition_fields_sql;
EXECUTE bpm_definition_fields_stmt;
DEALLOCATE PREPARE bpm_definition_fields_stmt;
