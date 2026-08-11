-- EXP-75：历史 file_id 修复清单 / 歧义对账（只读，可重复执行）
-- 用途：迁移后输出仍无 file_id 或 path/url 冲突的活动附件，供人工修复。

-- 1) 重复 path 的 infra_file（非唯一）
SELECT f.path, COUNT(*) AS cnt, GROUP_CONCAT(f.id ORDER BY f.id) AS file_ids
FROM infra_file f
WHERE f.deleted = b'0' AND f.path IS NOT NULL AND f.path <> ''
GROUP BY f.path
HAVING COUNT(*) > 1;

-- 2) 重复 URL 的 infra_file（非唯一）
SELECT f.url, COUNT(*) AS cnt, GROUP_CONCAT(f.id ORDER BY f.id) AS file_ids
FROM infra_file f
WHERE f.deleted = b'0' AND f.url IS NOT NULL AND f.url <> ''
GROUP BY f.url
HAVING COUNT(*) > 1;

-- 3) 活动 onboarding/入职单附件仍无 file_id（待修复）
SELECT a.id, a.business_type, a.business_id, a.file_path, a.file_url, a.file_id, a.tenant_id,
  (SELECT COUNT(*) FROM infra_file f WHERE f.deleted = b'0' AND f.path = a.file_path) AS path_cnt,
  (SELECT COUNT(*) FROM infra_file f WHERE f.deleted = b'0' AND f.url = a.file_url) AS url_cnt
FROM common_attachment a
WHERE a.business_type IN ('hrm_employee_archive_onboarding', '201')
  AND a.deleted = b'0'
  AND a.file_id IS NULL;

-- 4) 软删 infra_file 不应作为候选（抽样：附件 path 仅匹配软删行）
SELECT a.id, a.file_path, a.file_url
FROM common_attachment a
WHERE a.business_type IN ('hrm_employee_archive_onboarding', '201')
  AND a.deleted = b'0'
  AND a.file_id IS NULL
  AND EXISTS (
    SELECT 1 FROM infra_file f WHERE f.path = a.file_path AND f.deleted = b'1'
  )
  AND NOT EXISTS (
    SELECT 1 FROM infra_file f WHERE f.path = a.file_path AND f.deleted = b'0'
  );
