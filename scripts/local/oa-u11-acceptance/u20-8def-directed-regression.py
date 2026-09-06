#!/usr/bin/env python3
"""Directed HTTP+SQL regression for the immutable 8def iso API.

Not a 83/83 rerun. New rows only, unique run id + explicit test month.
Does not mutate historical samples, roles, BPMN, or withdraw mode.
Credentials via env; never printed.
"""
from __future__ import annotations

import hashlib
import json
import os
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

ALLOWED_BASE = "http://127.0.0.1:48081/admin-api"
TENANT = "1"
ADMIN_USER = os.environ.get("U20_ADMIN_USER", "admin")
ADMIN_PASS = os.environ.get("U20_ADMIN_PASS", "admin123")
HR_USER = os.environ.get("U20_HR_USER", "wangpengiso")
HR_PASS = os.environ.get("U20_HR_PASS", "admin123")
EXPECTED_JAR = "8def53f0a335a454228c7f661c033a0d1a497cad58d28db87839bc06d61c4102"
OT_DAY_CODE = 1_009_001_015
PUNCH_MONTH_CODE = 1_009_001_019
OCCUPY_STATUSES = (1, 2)  # running / approved
RESULTS: list[dict] = []


def log(msg: str) -> None:
    print(msg, flush=True)


def resolve_base() -> str:
    override = os.environ.get("U20_BASE")
    if override and override.rstrip("/") != ALLOWED_BASE:
        raise SystemExit(
            f"refusing U20_BASE={override!r}; isolation regression is hard-pinned to {ALLOWED_BASE}"
        )
    return ALLOWED_BASE


BASE = resolve_base()


def mysql(sql: str) -> str:
    cmd = [
        "docker",
        "exec",
        "oa-u11-iso-mysql",
        "sh",
        "-c",
        'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 -N -e '
        + json.dumps(sql),
    ]
    p = subprocess.run(cmd, capture_output=True, text=True)
    if p.returncode != 0:
        err = [l for l in (p.stderr or "").splitlines() if "Using a password" not in l]
        raise RuntimeError("mysql failed: " + "\n".join(err)[-800:])
    return p.stdout


def http(method: str, path: str, token: str | None = None, body: dict | None = None, timeout: int = 30) -> dict:
    data = None if body is None else json.dumps(body).encode()
    headers = {"Content-Type": "application/json", "Tenant-Id": TENANT}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode()
            return json.loads(raw) if raw else {"http": resp.status}
    except HTTPError as e:
        raw = e.read().decode()
        try:
            parsed = json.loads(raw)
        except Exception:
            parsed = {"msg": raw[:500]}
        parsed["_http"] = e.code
        return parsed
    except URLError as e:
        return {"code": -1, "msg": str(e.reason)}


def login(username: str, password: str) -> str:
    r = http("POST", "/system/auth/login", body={"username": username, "password": password})
    if r.get("code") != 0:
        raise RuntimeError(f"login failed user={username} code={r.get('code')} msg={r.get('msg')}")
    token = (r.get("data") or {}).get("accessToken")
    if not token:
        raise RuntimeError(f"login missing token user={username}")
    return token


def rec(name: str, ok: bool, **extra) -> None:
    RESULTS.append({"case": name, "ok": ok, **extra})
    log(("PASS " if ok else "FAIL ") + name + " " + json.dumps(extra, ensure_ascii=False)[:500])


def jar_sha() -> str:
    p = subprocess.run(
        ["docker", "exec", "oa-u11-iso-api", "sha256sum", "/yudao-server/app.jar"],
        capture_output=True,
        text=True,
        check=True,
    )
    return p.stdout.split()[0]


def fingerprint_existing() -> str:
    ot = mysql(
        "SELECT id,user_id,start_time,end_time,hours,holiday,status,process_instance_id,reason,deleted "
        "FROM oa_u11_iso.bpm_oa_overtime ORDER BY id"
    )
    punch = mysql(
        "SELECT id,user_id,punch_date,punch_time,status,process_instance_id,reason,deleted "
        "FROM oa_u11_iso.bpm_oa_punch_correction ORDER BY id"
    )
    raw = "OT\n" + ot + "PUNCH\n" + punch
    return hashlib.sha256(raw.encode()).hexdigest(), raw


def occupying_ot_hours(user_id: int, day: str, run_id: str) -> str:
    return mysql(
        "SELECT COALESCE(SUM(hours),0) FROM oa_u11_iso.bpm_oa_overtime "
        f"WHERE user_id={user_id} AND deleted=0 AND status IN {OCCUPY_STATUSES} "
        f"AND DATE(start_time)='{day}' AND reason LIKE 'U20-8def-{run_id} %'"
    ).strip()


def occupying_punch_count(user_id: int, month_start: str, month_end: str, run_id: str) -> str:
    return mysql(
        "SELECT COUNT(*) FROM oa_u11_iso.bpm_oa_punch_correction "
        f"WHERE user_id={user_id} AND deleted=0 AND status IN {OCCUPY_STATUSES} "
        f"AND punch_date>='{month_start}' AND punch_date<'{month_end}' "
        f"AND reason LIKE 'U20-8def-{run_id} %'"
    ).strip()


def leftover_flowable(run_started: str, run_id: str) -> list[str]:
    sql = (
        "SELECT pi.ID_, pi.BUSINESS_KEY_ FROM oa_u11_iso.ACT_HI_PROCINST pi "
        f"WHERE pi.START_TIME_ >= '{run_started}' "
        "AND pi.PROC_DEF_ID_ LIKE 'oa_%' "
        "AND NOT EXISTS (SELECT 1 FROM oa_u11_iso.bpm_oa_overtime o WHERE o.process_instance_id=pi.ID_) "
        "AND NOT EXISTS (SELECT 1 FROM oa_u11_iso.bpm_oa_punch_correction p WHERE p.process_instance_id=pi.ID_);"
    )
    out = mysql(sql).strip()
    return [l for l in out.splitlines() if l.strip()]


def todo(token: str, process_instance_id: str) -> list[dict]:
    r = http(
        "GET",
        f"/bpm/task/todo-page?pageNo=1&pageSize=20&processInstanceId={process_instance_id}",
        token=token,
    )
    return (r.get("data") or {}).get("list") or []


def approve_chain(admin_token: str, hr_token: str, pi: str, bill: str) -> dict:
    steps = []
    for _ in range(4):
        ru = mysql(
            f"SELECT ID_, NAME_, ASSIGNEE_ FROM oa_u11_iso.ACT_RU_TASK WHERE PROC_INST_ID_='{pi}'"
        ).strip()
        if not ru:
            status = mysql(
                f"SELECT status FROM oa_u11_iso.{bill} WHERE process_instance_id='{pi}'"
            ).strip()
            return {"steps": steps, "status": status, "ru_task": 0}
        line = ru.splitlines()[0].split("\t")
        task_id, name, assignee = (line + ["", "", ""])[:3]
        token = hr_token if assignee == "221" else admin_token
        who = "hr" if assignee == "221" else "admin"
        r = http(
            "PUT",
            "/bpm/task/approve",
            token=token,
            body={"id": task_id, "reason": f"U20-8def approve {name}"},
        )
        steps.append({"name": name, "assignee": assignee, "who": who, "code": r.get("code"), "msg": r.get("msg")})
        if r.get("code") != 0:
            return {"steps": steps, "status": "approve_fail", "ru_task": -1}
        time.sleep(0.4)
    ru_n = mysql(f"SELECT COUNT(*) FROM oa_u11_iso.ACT_RU_TASK WHERE PROC_INST_ID_='{pi}'").strip()
    status = mysql(f"SELECT status FROM oa_u11_iso.{bill} WHERE process_instance_id='{pi}'").strip()
    return {"steps": steps, "status": status, "ru_task": int(ru_n or 0)}


def precheck_blank(month: str, days: list[str], month_start: str, month_end: str) -> None:
    ot_n = mysql(
        "SELECT COUNT(*) FROM oa_u11_iso.bpm_oa_overtime "
        "WHERE user_id=1 AND deleted=0 AND status IN (1,2) AND ("
        + " OR ".join(f"DATE(start_time)='{d}'" for d in days)
        + ")"
    ).strip()
    punch_n = mysql(
        "SELECT COUNT(*) FROM oa_u11_iso.bpm_oa_punch_correction "
        f"WHERE user_id=1 AND deleted=0 AND status IN (1,2) "
        f"AND punch_date>='{month_start}' AND punch_date<'{month_end}'"
    ).strip()
    any_ot = mysql(
        "SELECT COUNT(*) FROM oa_u11_iso.bpm_oa_overtime "
        f"WHERE user_id=1 AND deleted=0 AND start_time>='{month_start}' AND start_time<'{month_end}'"
    ).strip()
    any_punch = mysql(
        "SELECT COUNT(*) FROM oa_u11_iso.bpm_oa_punch_correction "
        f"WHERE user_id=1 AND deleted=0 AND punch_date>='{month_start}' AND punch_date<'{month_end}'"
    ).strip()
    if int(ot_n) or int(punch_n) or int(any_ot) or int(any_punch):
        raise SystemExit(
            f"fail-closed: month {month} is not blank for user 1 "
            f"(occupying_ot_days={ot_n} occupying_punch={punch_n} any_ot={any_ot} any_punch={any_punch}); "
            "pick another U20_MONTH, do not clean historical samples"
        )


def main() -> int:
    month = os.environ.get("U20_MONTH", "2026-12")
    run_id = os.environ.get("U20_RUN_ID", f"r{datetime.now(timezone.utc).strftime('%Y%m%d%H%M%S')}")
    if len(month) != 7 or month[4] != "-":
        raise SystemExit("U20_MONTH must be YYYY-MM")
    y, m = month.split("-")
    month_start = f"{month}-01"
    month_end = f"{int(y)+1:04d}-01-01" if m == "12" else f"{y}-{int(m)+1:02d}-01"
    ot_day = f"{month}-10"
    punch_days = [f"{month}-02", f"{month}-03", f"{month}-04"]
    approve_day = f"{month}-12"
    cancel_day = f"{month}-13"
    prefix = f"U20-8def-{run_id}"
    json_out = Path(f"/tmp/royi-oa-u11-artifacts/u20-8def-directed-regression-{month}-{run_id}.json")

    sha = jar_sha()
    log(f"jar_sha={sha} month={month} run_id={run_id} base={BASE}")
    if sha != EXPECTED_JAR:
        rec("jar-pin", False, sha=sha, expected=EXPECTED_JAR)
        return 2
    rec("jar-pin", True, sha=sha, month=month, run_id=run_id, base=BASE)

    try:
        precheck_blank(month, [ot_day, approve_day, cancel_day], month_start, month_end)
        rec("precheck-blank-month", True, month=month)
    except SystemExit as e:
        rec("precheck-blank-month", False, err=str(e))
        json_out.write_text(json.dumps({"jar": sha, "results": RESULTS}, ensure_ascii=False, indent=2) + "\n")
        raise

    fp_before, raw_before = fingerprint_existing()
    rec("fingerprint-before", True, sha256=fp_before)

    run_started = mysql("SELECT DATE_FORMAT(NOW(3), '%Y-%m-%d %H:%i:%s.%f')").strip()[:23]
    admin = login(ADMIN_USER, ADMIN_PASS)
    rec("login-admin", True, user=ADMIN_USER)
    try:
        hr = login(HR_USER, HR_PASS)
        rec("login-hr", True, user=HR_USER)
    except Exception as e:
        hr = None
        rec("login-hr", False, user=HR_USER, err=str(e))

    ot_windows = {
        "a": (f"{ot_day} 00:00:00", f"{ot_day} 05:00:00"),
        "b": (f"{ot_day} 05:00:00", f"{ot_day} 10:00:00"),
    }

    def create_ot(tag: str) -> dict:
        start, end = ot_windows[tag]
        r = http(
            "POST",
            "/bpm/oa/overtime/create",
            token=admin,
            body={
                "reason": f"{prefix} ot-concurrent-{tag}",
                "startTime": start,
                "endTime": end,
                "holiday": "false",
            },
        )
        r["_tag"] = tag
        return r

    with ThreadPoolExecutor(max_workers=2) as ex:
        ot_res = [f.result() for f in as_completed([ex.submit(create_ot, t) for t in ("a", "b")])]
    ot_ok = [r for r in ot_res if r.get("code") == 0]
    ot_fail = [r for r in ot_res if r.get("code") != 0]
    hours = occupying_ot_hours(1, ot_day, run_id)
    fail_codes = [r.get("code") for r in ot_fail]
    rec(
        "ot-concurrent-5plus5",
        ok=(len(ot_ok) == 1 and len(ot_fail) == 1 and float(hours) <= 8.0 and OT_DAY_CODE in fail_codes),
        success=len(ot_ok),
        fail=len(ot_fail),
        hours=hours,
        day=ot_day,
        user_id=1,
        occupy_statuses=list(OCCUPY_STATUSES),
        fail_codes=fail_codes,
        fail_msgs=[r.get("msg") for r in ot_fail],
        ids=[r.get("data") for r in ot_ok],
    )
    rec("ot-no-orphan-flowable", ok=(len(leftover_flowable(run_started, run_id)) == 0),
        orphans=leftover_flowable(run_started, run_id))

    def create_punch(day: str) -> dict:
        r = http(
            "POST",
            "/bpm/oa/punch-correction/create",
            token=admin,
            body={
                "punchDate": day,
                "punchTime": f"{day} 09:00:00",
                "reason": f"{prefix} punch-concurrent-{day}",
            },
        )
        r["_day"] = day
        return r

    with ThreadPoolExecutor(max_workers=3) as ex:
        punch_res = [f.result() for f in as_completed([ex.submit(create_punch, d) for d in punch_days])]
    p_ok = [r for r in punch_res if r.get("code") == 0]
    p_fail = [r for r in punch_res if r.get("code") != 0]
    p_cnt = occupying_punch_count(1, month_start, month_end, run_id)
    p_codes = [r.get("code") for r in p_fail]
    rec(
        "punch-concurrent-3-max2",
        ok=(len(p_ok) == 2 and len(p_fail) == 1 and int(p_cnt) == 2 and PUNCH_MONTH_CODE in p_codes),
        success=len(p_ok),
        fail=len(p_fail),
        count=p_cnt,
        month=month,
        user_id=1,
        fail_codes=p_codes,
        fail_msgs=[r.get("msg") for r in p_fail],
        ids=[r.get("data") for r in p_ok],
    )
    rec("punch-no-orphan-flowable", ok=(len(leftover_flowable(run_started, run_id)) == 0),
        orphans=leftover_flowable(run_started, run_id))
    rem = http("GET", f"/bpm/oa/punch-correction/remaining?punchDate={punch_days[0]}", token=admin)
    rec("punch-remaining-month", ok=(rem.get("code") == 0 and rem.get("data") == 0), remaining=rem.get("data"))

    ap = http(
        "POST",
        "/bpm/oa/overtime/create",
        token=admin,
        body={
            "reason": f"{prefix} approve-holiday",
            "startTime": f"{approve_day} 00:00:00",
            "endTime": f"{approve_day} 02:00:00",
            "holiday": "true",
        },
    )
    if ap.get("code") != 0:
        rec("approve-create", False, code=ap.get("code"), msg=ap.get("msg"))
        rec("approve-chain-dept-hr", False, err="create failed")
    else:
        oid = ap.get("data")
        pi = mysql(f"SELECT process_instance_id FROM oa_u11_iso.bpm_oa_overtime WHERE id={oid}").strip()
        rec("approve-create", True, id=oid, pi=pi)
        if not hr:
            rec("approve-chain-dept-hr", False, err="hr login missing")
        else:
            chain = approve_chain(admin, hr, pi, "bpm_oa_overtime")
            rec(
                "approve-chain-dept-hr",
                ok=(str(chain.get("status")) == "2" and chain.get("ru_task") == 0),
                **chain,
            )

    c1 = http(
        "POST",
        "/bpm/oa/overtime/create",
        token=admin,
        body={
            "reason": f"{prefix} cancel-then-reoccupy-1",
            "startTime": f"{cancel_day} 00:00:00",
            "endTime": f"{cancel_day} 05:00:00",
            "holiday": "false",
        },
    )
    if c1.get("code") != 0:
        rec("cancel-create", False, code=c1.get("code"), msg=c1.get("msg"))
        rec("cancel-by-start-user", False, err="create failed")
        rec("reoccupy-after-cancel", False, err="create failed")
    else:
        cid = c1.get("data")
        cpi = mysql(f"SELECT process_instance_id FROM oa_u11_iso.bpm_oa_overtime WHERE id={cid}").strip()
        rec("cancel-create", True, id=cid, pi=cpi)
        cancel = http(
            "DELETE",
            "/bpm/process-instance/cancel-by-start-user",
            token=admin,
            body={"id": cpi, "reason": f"{prefix} cancel release"},
        )
        rec("cancel-by-start-user", cancel.get("code") == 0, code=cancel.get("code"), msg=cancel.get("msg"), pi=cpi)
        if cancel.get("code") == 0:
            c2 = http(
                "POST",
                "/bpm/oa/overtime/create",
                token=admin,
                body={
                    "reason": f"{prefix} cancel-then-reoccupy-2",
                    "startTime": f"{cancel_day} 00:00:00",
                    "endTime": f"{cancel_day} 05:00:00",
                    "holiday": "false",
                },
            )
            rec(
                "reoccupy-after-cancel",
                c2.get("code") == 0,
                code=c2.get("code"),
                msg=c2.get("msg"),
                id=c2.get("data"),
            )
        else:
            rec("reoccupy-after-cancel", False, skipped=False, reason="cancel failed")

    fp_after, _ = fingerprint_existing()
    before_ids_ot = [l.split("\t")[0] for l in raw_before.split("PUNCH\n")[0].splitlines() if l and l[0].isdigit()]
    before_ids_punch = [l.split("\t")[0] for l in raw_before.split("PUNCH\n")[1].splitlines() if l and l[0].isdigit()]
    ot_now = mysql(
        "SELECT id,user_id,start_time,end_time,hours,holiday,status,process_instance_id,reason,deleted "
        "FROM oa_u11_iso.bpm_oa_overtime ORDER BY id"
    )
    punch_now = mysql(
        "SELECT id,user_id,punch_date,punch_time,status,process_instance_id,reason,deleted "
        "FROM oa_u11_iso.bpm_oa_punch_correction ORDER BY id"
    )
    ot_kept = "\n".join(l for l in ot_now.splitlines() if l.split("\t")[0] in set(before_ids_ot))
    punch_kept = "\n".join(l for l in punch_now.splitlines() if l.split("\t")[0] in set(before_ids_punch))
    ot_before_body = "\n".join(l for l in raw_before.split("PUNCH\n")[0].splitlines() if l and l[0].isdigit())
    punch_before_body = "\n".join(l for l in raw_before.split("PUNCH\n")[1].splitlines() if l and l[0].isdigit())
    rec(
        "protected-row-fingerprints-unchanged",
        ok=(ot_kept == ot_before_body and punch_kept == punch_before_body),
        before_ot_ids=before_ids_ot,
        before_punch_ids=before_ids_punch,
        fingerprint_before=fp_before,
        fingerprint_after_all_rows=fp_after,
    )
    rec("jar-pin-end", jar_sha() == EXPECTED_JAR, sha=jar_sha())

    failed = [r for r in RESULTS if not r.get("ok")]
    payload = {
        "jar": sha,
        "month": month,
        "run_id": run_id,
        "base": BASE,
        "note": "new JSON; does not overwrite Nov U20 initial/overlap artifacts",
        "results": RESULTS,
    }
    json_out.parent.mkdir(parents=True, exist_ok=True)
    json_out.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n")
    log(f"wrote {json_out} fail={len(failed)}/{len(RESULTS)}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
