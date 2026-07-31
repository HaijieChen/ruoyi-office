-- 开票申请 BPMN：为模型编辑器补齐 BPMNDiagram（图形坐标）
-- 根因：种子/手工 XML 仅有 process 语义、无 DI，bpmn-js 修改时画布空白
-- 更新 ACT_RE_MODEL.EDITOR_SOURCE_VALUE_ID_ 对应 ACT_GE_BYTEARRAY
-- 注意：不改已部署 definition 资源；仅影响「流程设计-修改」设计器展示。
-- 若需运行态与图一致，需在设计器保存后重新「发布」。

-- 本脚本在运维侧用应用更新更稳妥；以下为结构说明 + 校验查询。
-- 实际 oa-test 已由运维/脚本写回完整 XML（含 start→财务审批→end + executionListener）。

SELECT m.KEY_, b.ID_, CHAR_LENGTH(b.BYTES_) AS xml_len,
       CASE WHEN CONVERT(b.BYTES_ USING utf8mb4) LIKE '%BPMNDiagram%' THEN 1 ELSE 0 END AS has_di
FROM ACT_RE_MODEL m
JOIN ACT_GE_BYTEARRAY b ON b.ID_ = m.EDITOR_SOURCE_VALUE_ID_
WHERE m.KEY_ = 'finance_invoice_apply';
