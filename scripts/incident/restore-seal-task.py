#!/usr/bin/env python3
"""One-incident recovery; exact preconditions, transactional writes, server-local backup.
Default is dry-run. Never completes a task or changes the business approval status.
"""
import argparse, json, os, subprocess, tempfile
from pathlib import Path

TASK = '4891da27-aa66-11f1-b502-0242ac120004'
INSTANCE = 'e4a2621f-a83b-11f1-a33a-0242ac120004'
OLD_REASON = '仅针对连续审批的节点自动通过'
NEW_REASON = '系统修复：自动审批未完成，已恢复审批中，待人工办理'
p = argparse.ArgumentParser()
p.add_argument('--apply', action='store_true')
p.add_argument('--container', default='mysql')
p.add_argument('--database', default='ruoyi-office', choices=['ruoyi-office','oa_hotfix_recovery_test'])
p.add_argument('--backup-dir', required=True)
a = p.parse_args()
os.umask(0o077)
backup = Path(a.backup_dir)
backup.mkdir(parents=True, exist_ok=True)
if a.apply and (backup / 'before.json').exists():
    raise SystemExit('Refusing to overwrite an existing recovery backup')
err = tempfile.TemporaryFile(mode='w+t')
process = subprocess.Popen(['docker','exec','-i',a.container,'sh','-c',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --batch --raw --skip-column-names --unbuffered '+a.database],
    stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=err, text=True, bufsize=1)
def query(sql):
    process.stdin.write(sql.rstrip(';')+";\nSELECT '__RECOVERY_END__';\n")
    process.stdin.flush()
    rows=[]
    while True:
        line=process.stdout.readline()
        if not line: raise RuntimeError('Database command failed; transaction was not committed')
        line=line.rstrip('\n')
        if line=='__RECOVERY_END__':return rows
        rows.append(line)
def snapshot(table, where):
    cols=query("SELECT COLUMN_NAME FROM information_schema.columns WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='%s' ORDER BY ORDINAL_POSITION"%table)
    assert cols,table
    expr=','.join("'%s',`%s`"%(c,c) for c in cols)
    return [json.loads(row) for row in query('SELECT JSON_OBJECT('+expr+') FROM '+table+' WHERE '+where+' FOR UPDATE')]
def one(rows):
    assert len(rows)==1, 'Expected exactly one record'
    return rows[0]
try:
    query('SET SESSION innodb_lock_wait_timeout=10')
    query('START TRANSACTION')
    query("SELECT ID_ FROM ACT_RU_EXECUTION WHERE PROC_INST_ID_='%s' FOR UPDATE"%INSTANCE)
    rows = {
        'ACT_RU_TASK':snapshot('ACT_RU_TASK',"ID_='%s'"%TASK),
        'ACT_HI_TASKINST':snapshot('ACT_HI_TASKINST',"ID_='%s'"%TASK),
        'finance_contract_application':snapshot('finance_contract_application',"id=177"),
        'ACT_RU_VARIABLE':snapshot('ACT_RU_VARIABLE',"TASK_ID_='%s' AND NAME_ IN ('TASK_STATUS','TASK_REASON')"%TASK),
        'ACT_HI_VARINST':snapshot('ACT_HI_VARINST',"TASK_ID_='%s' AND NAME_ IN ('TASK_STATUS','TASK_REASON')"%TASK),
    }
    task=one(rows['ACT_RU_TASK']); hist=one(rows['ACT_HI_TASKINST']); bill=one(rows['finance_contract_application'])
    assert task['PROC_INST_ID_']==INSTANCE and task['ASSIGNEE_']=='853' and task['TASK_DEF_KEY_']=='taskSeal' and task['SUSPENSION_STATE_']==1, 'Task identity changed'
    assert hist['END_TIME_'] is None and hist['PROC_INST_ID_']==INSTANCE, 'Task already ended'
    assert bill['application_no']=='CT-20260904-1' and bill['process_instance_id']==INSTANCE and bill['approval_status']=='PENDING' and bill['current_node_key']=='seal' and not bill['seal_file_url'], 'Business state changed'
    for table in ('ACT_RU_VARIABLE','ACT_HI_VARINST'):
        variables={r['NAME_']:r for r in rows[table]}
        assert len(variables)==2 and variables['TASK_STATUS']['LONG_']==2 and variables['TASK_STATUS']['TEXT_']=='2', 'Task status changed'
        assert variables['TASK_REASON']['TEXT_']==OLD_REASON, 'Reason changed'
    if not a.apply:
        query('ROLLBACK');print(json.dumps({'mode':'dry-run','preconditions':'passed','businessStatus':'PENDING','taskStatusBefore':2}))
    else:
        (backup/'before.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2))
        # Recovery never invents a successful approval or changes any business field.
        for table in ('ACT_RU_VARIABLE','ACT_HI_VARINST'):
            changed = query("UPDATE %s SET LONG_=1,TEXT_='1',REV_=REV_+1 WHERE TASK_ID_='%s' AND NAME_='TASK_STATUS' AND LONG_=2 AND TEXT_='2'; SELECT ROW_COUNT()"%(table,TASK))
            assert changed==['1'], 'Status update count changed'
            changed = query("UPDATE %s SET TEXT_='%s',REV_=REV_+1 WHERE TASK_ID_='%s' AND NAME_='TASK_REASON' AND TEXT_='%s'; SELECT ROW_COUNT()"%(table,NEW_REASON,TASK,OLD_REASON))
            assert changed==['1'], 'Reason update count changed'
        for table in ('ACT_RU_VARIABLE','ACT_HI_VARINST'):
            assert query("SELECT LONG_ FROM %s WHERE TASK_ID_='%s' AND NAME_='TASK_STATUS'"%(table,TASK))==['1']
        query('COMMIT')
        (backup/'receipt.json').write_text(json.dumps({'committed':True,'task':TASK,'businessStatus':'PENDING','taskStatus':1,'rowsUpdated':4},indent=2))
        print(json.dumps({'committed':True,'businessStatus':'PENDING','taskStatus':1,'rowsUpdated':4}))
except BaseException:
    if process.poll() is None:
        try:query('ROLLBACK')
        except Exception:pass
    raise
finally:
    if process.stdin:process.stdin.close()
    process.wait(timeout=15)
