# NON-PRODUCTION / ISO-ONLY BPM metadata fixture

**Not a production migration.** Do not run on 33061 or any shared DB.

Sources (current workspace classes, not an upstream dump):

| Column | Java | Handler / JDBC |
|---|---|---|
| id | BpmProcessDefinitionInfoDO.id @TableId | bigint PK AI |
| process_definition_id | processDefinitionId | varchar(64) |
| model_id | modelId | varchar(64) |
| model_type | modelType BpmModelTypeEnum | int |
| category | category | varchar(64) |
| icon | icon | varchar(512) |
| description | description | varchar(1024) |
| form_type | formType BpmModelFormTypeEnum | int |
| form_id | formId | bigint |
| form_conf | formConf | text |
| form_fields | formFields JacksonTypeHandler | json |
| form_custom_create_path | formCustomCreatePath | varchar(255) |
| form_custom_view_path | formCustomViewPath | varchar(255) |
| simple_model | simpleModel | longtext |
| visible | visible | bit |
| sort | sort | bigint |
| start_user_ids | startUserIds LongListTypeHandler | varchar(1024) |
| start_dept_ids | startDeptIds LongListTypeHandler | varchar(1024) |
| manager_user_ids | managerUserIds LongListTypeHandler | varchar(1024) |
| allow_cancel_running_process | allowCancelRunningProcess | bit |
| allow_withdraw_task | allowWithdrawTask | bit |
| process_id_rule | processIdRule JacksonTypeHandler | json |
| auto_approval_type | autoApprovalType | int |
| title_setting | titleSetting Jackson | json |
| summary_setting | summarySetting Jackson | json |
| process_before_trigger_setting | processBeforeTriggerSetting Jackson | json |
| process_after_trigger_setting | processAfterTriggerSetting Jackson | json |
| task_before_trigger_setting | taskBeforeTriggerSetting Jackson | json |
| task_after_trigger_setting | taskAfterTriggerSetting Jackson | json |
| print_template_setting | printTemplateSetting Jackson | json |
| creator/create_time/updater/update_time/deleted | BaseDO | varchar/datetime/bit |
| tenant_id | tenant line (DO extends BaseDO not TenantBaseDO) | bigint |

Indexes from mapper: process_definition_id, model_id.

Rows are written by BpmModelService.createModel/deployModel after this DDL.
