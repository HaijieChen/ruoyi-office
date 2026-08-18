# OA 出差 / 外出流程设计

## 1. 目标

在 Web 后台新增两个全员可发起的 OA 流程：出差、外出。实现方式对齐现有请假（自定义业务表 + BPM），不新建 Maven 模块，不进入财务模块，不做手机端。

字段以钉钉截图为准。审批链由管理员在流程模型中预设；发起人不能改审批人、不能删抄送。本期不写考勤，只在业务单上预留同步状态。

报销表单与财务报表会议纪要不在本次范围。

## 2. 范围

### 2.1 做

- 流程 key：oa_business_trip、oa_outing
- 业务表、接口、状态监听器、Web 发起/列表/详情
- 流程模型与 BPMN（默认审批链）
- 字典、菜单、全员发起可见性
- 考勤预留字段 attendance_sync_status = NOT_SYNCED

### 2.2 不做

- 手机端 / uniapp
- 考勤写入、免打卡、钉钉同步
- 行程重叠校验
- 出差事由、目的地、班次（截图没有）
- 费用报销、财务报表
- 财务 embed 发起壳、财务 create 权限门闩

## 3. 架构

放在 yudao-module-bpm 的 OA 包，与请假并列。

| 项 | 出差 | 外出 |
|---|---|---|
| 流程 key | oa_business_trip | oa_outing |
| 表 | bpm_oa_business_trip | bpm_oa_outing |
| 接口前缀 | /bpm/oa/trip | /bpm/oa/outing |
| 权限前缀 | bpm:oa-trip: | bpm:oa-outing: |
| 前端 | bpm/oa/trip/* | bpm/oa/outing/* |

两个流程相互独立，不抽公共「考勤申请」聚合表。

不注册 BpmEmbedProcessStartPermissionRegistry，不注册 CREATE_SHELL_EMBED_REGISTRY。发起走独立填写页；统一发起目录靠流程模型可见性露出，卡片跳到对应 create 页（formCustomCreatePath）。

## 4. 字段

### 4.1 出差

| 字段 | 列 | 类型 | 规则 |
|---|---|---|---|
| 出差类型 | type | 字典 bpm_oa_trip_type | 必填。值：1 市内、2 省内、3 省外、4 国外 |
| 开始时间 | start_time | datetime | 必填 |
| 结束时间 | end_time | datetime | 必填；必须晚于开始时间 |
| 出差时长 | hours | decimal(8,1) | 按起止自动算，小时、一位小数，只读 |

### 4.2 外出

| 字段 | 列 | 类型 | 规则 |
|---|---|---|---|
| 外出事由 | reason | varchar(500) | 必填 |
| 外出地点 | location | varchar(200) | 必填 |
| 开始时间 | start_time | datetime | 必填 |
| 结束时间 | end_time | datetime | 必填；必须晚于开始时间 |
| 外出时长 | hours | decimal(8,1) | 按起止自动算，小时、一位小数，只读 |
| 是否需要内容产出 | need_output | varchar | 选填。复用字典 infra_boolean_string（true/false），空表示未选 |
| 附件 | attachment_urls | json | 选填。字符串 URL 数组。界面说明：外出对接相关内容 |

### 4.3 共用系统字段

- user_id：申请人
- status：复用 BpmTaskStatusEnum / 流程实例状态
- process_instance_id
- attendance_sync_status：NOT_SYNCED / SYNCED / FAILED。创建时固定 NOT_SYNCED，本期无写入器
- 标准 BaseDO 审计字段

申请人、部门由登录态带出，只读，不落冗余部门列（列表用用户服务解析部门名）。

时长公式：hours = round(Duration.between(start, end).toMinutes() / 60.0, 1)。结果必须 > 0，否则拒绝提交。

## 5. 审批链

默认同一条链，两个模型各自一份，管理员可改：

发起 → 连续多级部门负责人 → 审批人 → 人事（或签）→ 抄送 → 结束

| 节点 | 候选人策略 | 说明 |
|---|---|---|
| 连续多级部门负责人 | START_USER_DEPT_LEADER_MULTI | 从发起人部门向上 |
| 审批人 | 模型预配用户或角色 | 不是发起人自选 |
| 人事 | 角色 hr_admin，或签 | 一人通过即可 |
| 抄送 | 模型预配抄送人 | 发起人不能删 |

发起页不渲染自选审批人控件。创建接口忽略 startUserSelectAssignees。

发起人与某级审批人相同：走平台已有「转部门负责人 / 自动通过」规则，不另写。

人事、指定审批人、抄送人名单不写死在 Java 里，用流程模型 / 部署 SQL 预置。本地与测试环境 BPMN 默认：人事 = hr_admin；审批人、抄送先留空由管理员在设计器补齐，空候选人按平台缺审批人策略处理。

## 6. 权限与可见性

- 流程定义 start_user_ids 置空，统一发起目录全员可见。
- 分类归 default，避免模型管理页看不到。
- 菜单挂在请假同级（BPM OA）。按钮：query / create。
- create 接口只要求登录，不绑业务角色，满足全员可申请。query 仅授 hr_admin 与超管。
- 列表：无 query 时只返回本人单据；持 query 者可看全部。
- 详情：本人、当前审批人（任务上下文）或持 query 者可读。
- 财务出纳角色不参与。

## 7. 数据流

1. 前端校验起止与必填，展示自动时长。
2. 后端再次校验；插入业务单：status=RUNNING，attendance_sync_status=NOT_SYNCED，写入算出的 hours。
3. 以单号为 businessKey 启动对应流程，变量至少包含 hours（出差另含 type，外出另含 need_output）。
4. 回写 process_instance_id。
5. BpmProcessInstanceStatusEventListener 按 key 回写 status。
6. 驳回重提：携带原单字段打开 create，提交时走 create 生成新单并启动新流程。原驳回单保留，不实现 update 接口。

考勤同步本期不调度、不监听通过事件写考勤。

## 8. 异常

| 情况 | 行为 |
|---|---|
| 流程模型未发布 | 明确提示配置出差/外出模型；创建与启流同一事务，失败回滚 |
| 结束 ≤ 开始，或时长 ≤ 0 | 400，不落库 |
| 业务单不存在 | 详情/重提 404 业务码 |
| 非本人且无 query、无任务上下文 | 列表不可见，详情 403 |
| 请求带自选审批人 | 忽略，不写入流程 |

## 9. 前端

照 bpm/oa/leave 复制两套：

- create.vue：表单 + 右侧审批时间线（只读预测，无选人）
- index.vue：我的/查询列表
- detail.vue：表单只读 + 流程进度
- 外出附件用现有 FileUpload
- 时长字段禁用，随起止变化重算

流程模型 formCustomCreatePath / formCustomViewPath 分别指向上述 create / detail。

## 10. 测试

最少覆盖：

- 创建出差/外出启动对应 processDefinitionKey，hours 与一位小数一致
- 结束 ≤ 开始被拒，无残留行
- 通过/驳回/取消后监听器回写 status
- start_user_ids 为空时目录可见；key 不在 embed 权限表
- 创建请求中的 startUserSelectAssignees 不影响候选人
- 新建单 attendance_sync_status = NOT_SYNCED
- 无 query 用户看不到他人单据

## 11. 发布

幂等 SQL：

- 两张业务表
- 字典 bpm_oa_trip_type；是否选项复用 infra_boolean_string，不新建字典
- 菜单与角色授权
- 流程模型分类、start_user_ids 清空、自定义表单路径
- BPMN 资源（可先入库再在设计器补审批人/抄送人）

不改远程环境变量。上线后管理员在设计器补齐「审批人」和抄送人即可发起。
