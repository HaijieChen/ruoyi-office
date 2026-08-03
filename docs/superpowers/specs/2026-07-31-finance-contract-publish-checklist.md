# 合同签约 — 发布清单（CS-T7）

> 依据：`tech-plan-contract-signing` D-T1–D-T10；字段契约 `2026-07-31-finance-contract-application-field-contract.md`  
> 回归说明：`2026-07-31-finance-contract-regression.md`  
> BPM 约定：`2026-07-31-finance-contract-bpm-delegate.md`

## 1. 数据库

- [ ] 执行 `sql/mysql/finance_contract_application_phase1.sql`  
  - 建表 `finance_contract_application`  
  - BO 加列 `contract_application_id` + 索引；**保留** `contract_process_id`  
- [ ] 执行 `sql/mysql/finance_contract_application_menu_phase1.sql`  
  - 菜单「合同签约」+ 权限 query/create/resubmit/update  
  - 角色：`business_staff` / `finance_admin`  
- [ ] 校验：无「审批流程已完成证明」列（C22）

幂等：两脚本均可重复执行。

## 2. BPM

- [ ] 导入并发布流程 KEY = **`finance_contract_sign`**  
  - 种子：`sql/mysql/bpmn/finance_contract_sign.bpmn20.xml`（**含 BPMNDiagram/DI**）  
- [ ] **DI 验收（D-T9）**  
  - [ ] 流程设计打开 `finance_contract_sign`，画布可见全部节点与连线，可拖拽修改  
  - [ ] 运行 `sql/mysql/finance_contract_bpmn_diagram_di.sql`，`has_di = 1`  
  - [ ] 改模型后 **重新发布**（运行中实例仍走旧 definition）  
- [ ] 结束事件挂载同步 Delegate：`${financeContractApprovalOutcomeDelegate}`  
- [ ] UserTask create 挂载：`${financeContractTaskNodeLabelListener}`（种子已含）  
- [ ] businessKey = 合同申请 id；变量 `needMail`（boolean）  
- [ ] 候选人（本平台契约，CS-F12）：`candidateStrategy=10`（ROLE）+ `candidateParam=<角色 code>`  
  须先执行菜单/角色 SQL 种子角色，再导入 BPMN 发布（validate 会校验角色存在）

| task | candidateParam（role code） |
| --- | --- |
| taskBizLead | contract_biz_lead |
| taskLegal | contract_legal |
| taskFinance | contract_finance |
| taskGm | contract_gm |
| taskSeal / taskArchive | contract_seal_admin |
| taskMail | contract_mail |

- [ ] 辅路径 StatusListener 已随应用部署（不可替代 Delegate 主路径）

## 3. 后端 / 应用

- [ ] 部署含 finance 模块版本（createAndStart / resubmit / cancel / record* / BO 强制关联）  
- [ ] Redis 可用（发号 `CT-yyyyMMdd-*`）  
- [ ] 客商 `finance_customer_company` 已有启用数据（对方必选）

## 4. 前端

- [ ] 组件路径：`finance/contract-application/index`  
- [ ] BPM 自定义查看：`formCustomViewPath` = `/finance/contract-application/info/index`（待办详情只读单号，非列表）  
- [ ] 列表「创建时间」列使用 `formatDateTime`  
- [ ] 菜单可见「合同签约」；BS 可提交/重提/撤回；FA 可查  
- [ ] 发起仅 createAndStart（无草稿）；catalog 可用 `?openCreate=1`  
- [ ] 商务签单新建可选「合同签约申请」；导入列 **合同申请业务单号**

## 5. 角色 SoD（菜单层）

| 角色 | 合同签约 |
| --- | --- |
| business_staff | query / create / resubmit |
| finance_admin | query / update（manageAll 全量查询；**非**用印执行入口） |
| contract_seal_admin | query / **record-seal**（用印+归档，对齐 BPM candidate） |
| contract_mail | query / **record-mail**（邮寄） |

审批/用印候选人由 BPM 候选组配置；执行 API 另需上表 record-* 菜单权限（CS-F9）。

## 6. 关键路径冒烟（环境）

账号建议：`business_staff` / `finance_admin`（tenant_id=1）

| # | 步骤 | 期望 |
| --- | --- | --- |
| S1 | BS 提交合同签约（销售合同，needMail=否） | 列表 PENDING；有 processInstanceId；单号 CT-… |
| S2 | 流程逐节点批过至用印 | 列表节点标签变化（待业务主管→…→待用印） |
| S3 | 用印不传扫描件 recordSeal | 失败 |
| S4 | recordSeal + complete 用印 → 归档 → 结束 | APPROVED；BO 选择器可见该单（本人） |
| S5 | 中途 PENDING 时 BO 选该合同 | 不可选 / 创建失败 |
| S6 | BS 新建商务单挂该合同 | 成功 |
| S7 | 他人 APPROVED 合同 | BS 不可选 |
| S8 | 导入缺业务单号 / 错误单号 | 整行失败 |
| S9 | 历史空 BO 只改备注保存 | 成功；仍可不挂合同 |
| S10 | 已映射 BO 清空合同 | 禁止 |
| S11 | 有开票占用 BO 换合同 | 禁止 |
| S12 | PENDING 用印后撤回 | 禁止；用印前可撤回→CANCELLED |
| S13 | REJECTED resubmit | 新 processInstance；整链重批 |
| S14 | needMail=是 无邮寄单号结束 | 不可完成邮寄节点 |
| S15 | 设计器 DI | 见 §2 |

## 7. 回滚注意

- 进行中流程实例绑定旧 definition；回滚应用前确认无关键在途或接受双版本  
- 菜单脚本无自动删菜单；回滚需手工  
- BO 已写 `contract_application_id` 的数据勿直接 DROP 合同表  

## 8. 交付物索引

| 类型 | 路径 |
| --- | --- |
| DDL | `sql/mysql/finance_contract_application_phase1.sql` |
| 菜单 | `sql/mysql/finance_contract_application_menu_phase1.sql` |
| BPMN+DI | `sql/mysql/bpmn/finance_contract_sign.bpmn20.xml` |
| DI 校验 | `sql/mysql/finance_contract_bpmn_diagram_di.sql` |
| 字段契约 | `docs/superpowers/specs/2026-07-31-finance-contract-application-field-contract.md` |
| Delegate | `docs/superpowers/specs/2026-07-31-finance-contract-bpm-delegate.md` |
| 回归 | `docs/superpowers/specs/2026-07-31-finance-contract-regression.md` |
