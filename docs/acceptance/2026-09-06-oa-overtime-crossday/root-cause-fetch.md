# Fetch IllegalStateException 根因（test 应用日志 + 同机探测）

时间：2026-09-06 19:36:51.690–19:36:52.136（CST）
主机：10.20.32.1 `java -jar /data/apps/oa/yudao-server.jar --spring.profiles.active=test` pid 99759
日志：`/home/sdm/logs/yudao-server.log`

## 不是网络策略

- `POST /bpm/oa/overtime-calendar/fetch` 由 user 218（10000080）执行，**446ms** 完成，无堆栈。
- `recordFailure` 只存 `last.getClass().getSimpleName()`，所以 DB `parseNote=IllegalStateException`，丢失 `http N` / `empty body` / `parse-incomplete`。
- Connect/timeout 不会记成该简单名；446ms 也不像 10s×3 超时。
- 同机 urllib：公告正文 `https://www.gov.cn/zhengce/zhengceku/202511/content_7047091.htm` **HTTP 200**，27692 bytes，含「2026年部分节假日安排」（Java-http-client 与 Mozilla 均 200）。
- 目录页 `https://www.gov.cn/zhengce/zhengceku/` **HTTP 403**。目录失败不等于正文不可达；ACTIVE `sourceUrl` 已是正文 URL。

## 解析失败

官方正文节日句为：

`劳动节：</strong>5月1日（周五）至5日（周二）放假调休`

解析器 `RANGE` 要求 `节日：\s*N月N日`，被 `</strong>` 打断。本地对同一 HTML：`RANGE=[]`，`SPRING` 能匹配农历初七，但 `festivalStart("春节")` 依赖 RANGE 失败 → `legal.size()!=13` → `parse-incomplete` → `IllegalStateException`。

## 调度

- 处理器只有 `@XxlJob("oaOvertimeCalendarFetchJob")`。
- test：`xxl.job.enabled: false`，admin `http://127.0.0.1:9090/xxl-job-admin`。
- `infra_job` id=41 有行 ≠ 已执行；`infra_job_log` 空。
- `/admin-api/infra/job/get|trigger` 与 `/infra/job-log/page` 在 **yudao-server 本体** 记 `noResourceFoundExceptionHandler`（无路由），不是 403 权限。

## 通知

- 硬编码 `notifyUserId=1`；库 id=11 已写入 user 1。
- hr_admin 218 调 `/system/notify-message/page` 403（缺 `system:notify-message:query`）。
- 本人站内信应为 `/system/notify-message/my-page`。应收件人为持有 `bpm:oa-overtime-calendar:verify` 的人事（刘鹤 10000080 / 李霞 10000184），不是 user 1。
