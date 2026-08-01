-- 合同签约 BPMN DI 校验（CS-T3 / D-T9）
-- 种子 XML：sql/mysql/bpmn/finance_contract_sign.bpmn20.xml（含 BPMNDiagram）
-- 部署 KEY：finance_contract_sign
-- 设计器打开后若无图：将种子导入 ACT_RE_MODEL 编辑源，保存后重新发布。

-- 校验模型编辑源是否含 DI
SELECT m.KEY_, b.ID_, CHAR_LENGTH(b.BYTES_) AS xml_len,
       CASE WHEN CONVERT(b.BYTES_ USING utf8mb4) LIKE '%BPMNDiagram%' THEN 1 ELSE 0 END AS has_di
FROM ACT_RE_MODEL m
JOIN ACT_GE_BYTEARRAY b ON b.ID_ = m.EDITOR_SOURCE_VALUE_ID_
WHERE m.KEY_ = 'finance_contract_sign';

-- 校验已部署定义资源（可选）
-- SELECT d.KEY_, d.VERSION_, RES.NAME_,
--        CASE WHEN CONVERT(b.BYTES_ USING utf8mb4) LIKE '%BPMNDiagram%' THEN 1 ELSE 0 END AS has_di
-- FROM ACT_RE_PROCDEF d
-- JOIN ACT_GE_BYTEARRAY b ON b.ID_ = d.DEPLOYMENT_ID_ -- 需按实际资源关联调整
-- WHERE d.KEY_ = 'finance_contract_sign';
