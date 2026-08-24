-- 薪酬验收 PX01-PX10。幂等：按工号/用户名跳过。
SET NAMES utf8mb4;

INSERT INTO system_users (username, password, nickname, dept_id, mobile, sex, status, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT v.username, '$2a$04$KljJDa/LK7QfDm0lF5OhuePhlPfjRH3tB2Wu351Uidz.oQGJXevPi', v.nickname, 128, v.mobile, 1, 0,
       'admin', NOW(), 'admin', NOW(), b'0', 1
FROM (
    SELECT 'px01' username, '验收全勤' nickname, '13900000001' mobile
    UNION ALL SELECT 'px02','验收事假','13900000002'
    UNION ALL SELECT 'px03','验收病假短','13900000003'
    UNION ALL SELECT 'px04','验收年假','13900000004'
    UNION ALL SELECT 'px05','验收调休','13900000005'
    UNION ALL SELECT 'px06','验收婚假','13900000006'
    UNION ALL SELECT 'px07','验收出差','13900000007'
    UNION ALL SELECT 'px08','验收外出','13900000008'
    UNION ALL SELECT 'px09','验收旷工','13900000009'
    UNION ALL SELECT 'px10','验收病假满','13900000010'
) v
WHERE NOT EXISTS (SELECT 1 FROM system_users u WHERE u.deleted=b'0' AND u.username=v.username);

INSERT INTO system_user_role (user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT u.id, r.id, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM system_users u
JOIN system_role r ON r.code='common' AND r.deleted=b'0' AND r.tenant_id=1
WHERE u.username LIKE 'px0%' AND u.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_user_role ur WHERE ur.deleted=b'0' AND ur.user_id=u.id AND ur.role_id=r.id);

INSERT INTO hrm_employee (
    employee_no, name, sex, mobile, employee_status, dept_id, dept_name, company_id, company_name,
    entry_date, regular_salary, job_post, user_id, user_generated, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT v.no, v.name, 1, v.mobile, 1, 128, '验收部', 100, '验收公司', v.entry, 8700.00, '验收岗', u.id, b'1',
       'admin', NOW(), 'admin', NOW(), b'0', 1
FROM (
    SELECT 'PX01' no, '验收全勤' name, '13900000001' mobile, '2016-01-01' entry
    UNION ALL SELECT 'PX02','验收事假','13900000002','2020-01-01'
    UNION ALL SELECT 'PX03','验收病假短','13900000003','2025-08-01'
    UNION ALL SELECT 'PX04','验收年假','13900000004','2018-01-01'
    UNION ALL SELECT 'PX05','验收调休','13900000005','2019-01-01'
    UNION ALL SELECT 'PX06','验收婚假','13900000006','2022-01-01'
    UNION ALL SELECT 'PX07','验收出差','13900000007','2021-01-01'
    UNION ALL SELECT 'PX08','验收外出','13900000008','2021-06-01'
    UNION ALL SELECT 'PX09','验收旷工','13900000009','2020-06-01'
    UNION ALL SELECT 'PX10','验收病假满','13900000010','2015-01-01'
) v
JOIN system_users u ON u.username=LOWER(v.no) AND u.deleted=b'0'
WHERE NOT EXISTS (SELECT 1 FROM hrm_employee e WHERE e.deleted=b'0' AND e.employee_no=v.no);

-- 已通过假勤（result/status=2）
INSERT INTO bpm_oa_leave (user_id, type, reason, start_time, end_time, day, result, process_instance_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT u.id, v.type, v.reason, v.start_time, v.end_time, v.day, 2, NULL, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM (
    SELECT 'px02' uname, 2 type, '验收事假1天' reason, '2026-08-03 09:00:00' start_time, '2026-08-03 18:00:00' end_time, 1 day
    UNION ALL SELECT 'px03',1,'验收病假2天','2026-08-04 09:00:00','2026-08-05 18:00:00',2
    UNION ALL SELECT 'px04',4,'验收年假2天','2026-08-11 09:00:00','2026-08-12 18:00:00',2
    UNION ALL SELECT 'px05',5,'验收调休1天','2026-08-13 09:00:00','2026-08-13 18:00:00',1
    UNION ALL SELECT 'px06',3,'验收婚假3天','2026-08-17 09:00:00','2026-08-19 18:00:00',3
    UNION ALL SELECT 'px10',1,'验收全月病假','2026-08-01 09:00:00','2026-08-31 18:00:00',21
) v
JOIN system_users u ON u.username=v.uname AND u.deleted=b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM bpm_oa_leave l WHERE l.deleted=b'0' AND l.user_id=u.id AND l.reason=v.reason
);

INSERT INTO bpm_oa_business_trip (user_id, destination, reason, start_time, end_time, hours, status, attendance_sync_status, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT u.id, '上海', '验收出差覆盖旷工', '2026-08-06 09:00:00', '2026-08-06 18:00:00', 8.0, 2, 'NOT_SYNCED',
       'admin', NOW(), 'admin', NOW(), b'0', 1
FROM system_users u
WHERE u.username='px07' AND u.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM bpm_oa_business_trip t WHERE t.deleted=b'0' AND t.user_id=u.id AND t.reason='验收出差覆盖旷工');

INSERT INTO bpm_oa_outing (user_id, reason, location, start_time, end_time, hours, status, attendance_sync_status, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT u.id, '验收外出覆盖旷工', '上海', '2026-08-07 09:00:00', '2026-08-07 18:00:00', 8.0, 2, 'NOT_SYNCED',
       'admin', NOW(), 'admin', NOW(), b'0', 1
FROM system_users u
WHERE u.username='px08' AND u.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM bpm_oa_outing o WHERE o.deleted=b'0' AND o.user_id=u.id AND o.reason='验收外出覆盖旷工');
