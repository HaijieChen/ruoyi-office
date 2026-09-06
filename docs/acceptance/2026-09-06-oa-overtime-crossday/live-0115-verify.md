# 0115a45ce live 证据（流水线 223/264 SUCCESS，commit 对齐）

## 调度
应用 21:51 启动。Spring `[scheduling-1]`：
- 21:52:00 `2026=pending:3;`
- 随后每分钟 `2026=same:3;`
目录 `zhengceku/` 403 只记 listing-fetch:http 403，正文解析成功。临时每分钟 cron 已取证，代码改回 `0 0 9 ? * MON`。

## 采集 / 核验
- PENDING id=3，13 法定日，diff 当时只有 hash（下一 SHA 改为日期增减）。
- hr_admin `10000080`（userId 218）`verify id=3 enable=true` code=0。
- 之后：id=3 ACTIVE，id=1 REJECTED，业务 `active?year=2026` 仍 13 天且不含 2026-05-03。

## 通知
- 21:52 调度创建 PENDING 时 `resolve hr_admin userIds failed:`（无租户上下文）。
- DB 仍只有 user_id=1 的旧 id=11。my-page total 对 218 无新日历信。
- 已改为 `TenantUtils.execute(1L)` 后再解析 hr_admin 内部 userId。

## 页面
申请人 10000078 日历 page 403（缺 query）。hr_admin 可 page/fetch/verify。
节日名不再按 2026 月日或月份猜测；用公告解析出的 festivals 元数据，无证据则空。
