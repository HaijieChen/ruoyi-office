-- 用印申请流程部署后补分类/自定义表单路径（幂等）
SET NAMES utf8mb4;

UPDATE `bpm_process_definition_info`
SET `category` = 'legal',
    `start_user_ids` = NULL,
    `form_type` = 20,
    `form_custom_create_path` = '/oa/seal/seal-apply-info',
    `form_custom_view_path` = '/oa/seal/seal-apply-info',
    `visible` = b'1',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND (
    `process_definition_id` LIKE 'oa_seal_apply_bill%'
    OR `form_custom_create_path` LIKE '%seal-apply%'
    OR `form_custom_view_path` LIKE '%sealapply/info%'
  );

UPDATE `ACT_RE_MODEL`
SET `CATEGORY_` = 'legal',
    `LAST_UPDATE_TIME_` = NOW(3)
WHERE `KEY_` = 'oa_seal_apply_bill';
