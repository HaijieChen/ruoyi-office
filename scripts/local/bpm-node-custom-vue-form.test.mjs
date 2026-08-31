import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

function readRepositoryFile(relativePath) {
  return readFileSync(path.join(repositoryRoot, relativePath), 'utf8');
}

test('designer UserTask form panel writes formCustomViewPath extension body', () => {
  const form = readRepositoryFile(
    'ruoyi-office-vben/apps/web-antd/src/views/bpm/components/bpmn-process-designer/package/penal/form/ElementForm.vue',
  );
  const descriptor = readRepositoryFile(
    'ruoyi-office-vben/apps/web-antd/src/views/bpm/components/bpmn-process-designer/package/designer/plugins/descriptor/flowableDescriptor.json',
  );

  assert.match(form, /label="自定义查看页"/);
  assert.match(form, /FORM_CUSTOM_VIEW_PATH_TYPE = 'formCustomViewPath'/);
  assert.match(form, /moddle\.create\(\s*`\$\{prefix\}:\$\{FORM_CUSTOM_VIEW_PATH_TYPE\}`/);
  assert.match(form, /allow-clear/);
  assert.match(form, /v-model:value="formKey"/);
  assert.match(
    descriptor,
    /"name":\s*"formCustomViewPath"[\s\S]*?"isBody":\s*true/,
  );
});

test('payment family detail pages split by mode instead of task ids', () => {
  const families = [
    'payment-application',
    'salary-payment',
    'tax-payment',
  ];
  for (const family of families) {
    const index = readRepositoryFile(
      `ruoyi-office-vben/apps/web-antd/src/views/finance/${family}/detail/index.vue`,
    );
    const finance = readRepositoryFile(
      `ruoyi-office-vben/apps/web-antd/src/views/finance/${family}/detail/finance.vue`,
    );
    const cashier = readRepositoryFile(
      `ruoyi-office-vben/apps/web-antd/src/views/finance/${family}/detail/cashier.vue`,
    );
    assert.doesNotMatch(index, /taskFinance|taskCashier/);
    assert.match(index, /mode\?: 'cashier' \| 'finance' \| 'readonly'/);
    assert.match(index, /mode === 'finance'/);
    assert.match(index, /mode === 'cashier'/);
    assert.match(finance, /mode="finance"/);
    assert.match(cashier, /mode="cashier"/);
  }
});

test('approval shell prefers todo node Vue path and does not silent-fallback', () => {
  const constants = readRepositoryFile(
    'ruoyi-office-vben/apps/web-antd/src/views/bpm/processInstance/constants.ts',
  );
  const detail = readRepositoryFile(
    'ruoyi-office-vben/apps/web-antd/src/views/bpm/processInstance/detail/index.vue',
  );

  assert.match(constants, /export function resolveBusinessFormViewPath/);
  assert.match(constants, /source: 'node'/);
  assert.match(constants, /source: 'process'/);
  assert.match(detail, /resolveBusinessFormViewPath\(\s*data\?\.todoTask/);
  assert.match(detail, /resolved\.source === 'node' && resolved\.path && !loaded/);
  assert.match(detail, /无法加载节点自定义查看页/);
  assert.match(
    detail,
    /isFinanceApprovalPShellViewPath\(processDefinition\.value\?\.formCustomViewPath\)/,
  );
  assert.match(detail, /if \(businessFormLoadError\.value\) \{\s*return false;/);
  assert.match(
    detail,
    /v-if="!businessFormLoadError && \(!isPShellCustom \|\| isApproval\)"/,
  );
  assert.match(detail, /v-if="!businessFormLoadError && isApproval"/);
});
