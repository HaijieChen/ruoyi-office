import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

function readRepositoryFile(relativePath) {
  return readFileSync(path.join(repositoryRoot, relativePath), 'utf8');
}

function processTaskAssignedBody() {
  const src = readRepositoryFile(
    'yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java',
  );
  const start = src.indexOf('public void processTaskAssigned(Task task)');
  assert.ok(start >= 0, 'processTaskAssigned missing');
  const next = src.indexOf('\n    @Override', start + 1);
  return next >= 0 ? src.slice(start, next) : src.slice(start);
}

test('return-target node skips APPROVE_ALL auto-approve', () => {
  const body = processTaskAssignedBody();
  const returnFlagIdx = body.indexOf('PROCESS_INSTANCE_VARIABLE_RETURN_FLAG');
  const approveAllIdx = body.indexOf('BpmAutoApproveTypeEnum.APPROVE_ALL');
  assert.ok(returnFlagIdx >= 0, 'RETURN_FLAG must be read in processTaskAssigned');
  assert.ok(approveAllIdx >= 0, 'APPROVE_ALL must still exist');
  assert.ok(
    returnFlagIdx < approveAllIdx,
    'RETURN_FLAG must be read before APPROVE_ALL so a returned node is not auto-passed',
  );
  assert.match(
    body,
    /if \(processDefinitionInfo\.getAutoApprovalType\(\) != null\s*&&\s*ObjUtil\.notEqual\(returnTaskFlag, Boolean\.TRUE\)\) \{[\s\S]*BpmAutoApproveTypeEnum\.APPROVE_ALL[\s\S]*BpmAutoApproveTypeEnum\.APPROVE_SEQUENT/,
  );
});

test('return-target node also skips APPROVE_SEQUENT auto-approve', () => {
  const body = processTaskAssignedBody();
  assert.match(
    body,
    /if \(processDefinitionInfo\.getAutoApprovalType\(\) != null\s*&&\s*ObjUtil\.notEqual\(returnTaskFlag, Boolean\.TRUE\)\) \{[\s\S]*BpmAutoApproveTypeEnum\.APPROVE_SEQUENT/,
  );
});
