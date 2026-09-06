# 加班跨天 / 节假日日历 最终验收报告（仅 test）

最终 SHA：`1befc2a755f324213b17456ecdf4317eb7341b7a`（短 `1befc2a75`）  
独立查询流水线（`pipeline-runs-225-266.json`）：

| 流水线 | run | status | commit |
|---|---|---|---|
| test-sdm-oa 5161458 | **225** | SUCCESS | `1befc2a755f324213b17456ecdf4317eb7341b7a` |
| test-sdm-oa-web 5161594 | **266** | SUCCESS | `1befc2a755f324213b17456ecdf4317eb7341b7a` |

确认 225/266 构建的是 **1befc2a75**，不是 44e3e6ab9 / 0115a45ce。

## 未验项（明确）

1. **定时入口带租户到通知**：未做本次运行验证。cron 已恢复 `0 0 9 ? * MON` Asia/Shanghai；代码有 `TenantUtils.execute(1L)`。HTTP `fetch?year=2027` 已证明同一 `notifyOnce` 能送到 hr_admin userId 218/418。**HTTP ≠ 定时入口。**
2. **下一年度自动发现**：未通。test 机目录页 `zhengceku/` **HTTP 403**，`fetch?year=2027` 为 FAILED（listing-fetch），**不是** NOT_PUBLISHED。不能宣称次年自动获取已验通。
3. **U05 待核验页再走一遍**：PENDING 已在 0115 启用为 ACTIVE，当前无 PENDING。启用路径已 live；拒绝路径仅单测。
4. **Q04 并发 5+5**：未在 live 打。

## 测试数据清理

- 2027 隔离 FAILED id=4：软删
- 站内信 id=13（user 218）与 id=12（user 418）2027 failed：软删
- 2026 ACTIVE id=3 未改日期集合，未重置有效日历

## 白盒矩阵

| ID | 结果 | 证据 |
|---|---|---|
| H01 | PASS | 单测 overtime hours |
| H02 | PASS | 单测 |
| H02b | PASS | live-api 1009001014 119min |
| H02c | PASS | 单测 / H02 |
| H03 | PASS | 单测 |
| H04 | PASS | 单测 |
| H05 | PASS | 单测 |
| H06 | PASS | 单测 |
| X01 | PASS | live-api 1+1 created id=4 后已取消 |
| X02 | PASS | 单测 |
| X03 | PASS | 单测 |
| X04 | PASS | live-api 1009001021 |
| X05 | PASS | live-api 1009001020 列出 09-07、09-08 |
| Q01 | PASS | live 8h 后再加 1009001015 |
| Q02 | PASS | 单测 |
| Q03 | PASS | 单测 |
| Q04 | 未执行 | live 未打并发 |
| Q05 | PASS | live 取消后可重提（取消路径） |
| D01 | PASS | live 1009001020 09-07 |
| D02 | PASS | live 周末 |
| D03 | PASS | live 05-01 holiday=true |
| D04 | PASS | 单测 |
| D05 | PASS | 单测 |
| D06 | PASS | live X05 含工作日 |
| T01 | PASS | live 1009001022 |
| T02 | PASS | live 周末当法定 |
| T03 | PASS | 单测 |
| C01 | PASS | 种子后 ACTIVE 13 天 |
| C02 | PASS | 1009001021 |
| C03 | PASS | 失败保旧；后解析成功 pending→enable |
| C04 | PASS | fetch same:3 |
| C05 | PASS | 单测 listing-format / 403=FAILED |
| C06 | PASS | pending 期间业务仍读旧 ACTIVE，后 enable |
| C07 | PASS | 申请人 403 query；无 verify |
| C08 | PASS | id=3 ACTIVE，种子 REJECTED |
| C09 | PASS（单测） | Schedule.targetYears；live 定时入口通知见未验项 |
| C10 | PASS | 代码约束 / 单测 |
| C11 | FAIL/未通 | 2027 因目录 403 为 FAILED，未打到 NOT_PUBLISHED |
| F01 | PASS | 前端单测 |
| F02 | PASS | 与 X05 文案一致 |
| F03 | PASS | live 直 POST 工作日拒绝 |
| U01 | PASS | 刘鹤 工作流程→展开菜单→节假日日历 |
| U02 | PASS | 申请人 403 |
| U03 | PASS | 页上 13 法定；节日名齐全 |
| U04 | PASS | 列表「初始化已核验」种子行仍在（已驳回） |
| U05 | 部分 | 启用 live 过；当前无 PENDING |
| U06 | PASS | 列表「采集失败」IllegalStateException 旧行 |
| U07 | 部分 | 2027 无 ACTIVE；隔离 FAILED 已清理后应显示缺年文案 |
| U08 | PASS | 默认突出法定 |
| U09 | PASS | 「打开官方公告」gov.cn 链接 |
| U10 | PASS | 申请人无 fetch/verify |

## 简明结论

跨天拆分、2h/8h、准入、2026 日历业务规则、官方正文采集 PENDING→启用、人事可见日历页与节日名、hr_admin my-page 通知路径（HTTP 隔离 2027）已在 test 验证。  
**不能宣称：** 定时任务带租户投递已运行；下一年度公告自动发现已通。
