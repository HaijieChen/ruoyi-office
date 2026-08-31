import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

function readRepositoryFile(relativePath) {
  return readFileSync(path.join(repositoryRoot, relativePath), 'utf8');
}

test('salary forms sum and submit optional housing fund', () => {
  const form = readRepositoryFile(
    'ruoyi-office-vben/apps/web-antd/src/views/finance/salary-payment/modules/form.vue',
  );
  const body = readRepositoryFile(
    'ruoyi-office-vben/apps/web-antd/src/views/finance/salary-payment/modules/form-body.vue',
  );
  const api = readRepositoryFile(
    'ruoyi-office-vben/apps/web-antd/src/api/finance/salary-payment/index.ts',
  );
  for (const src of [form, body]) {
    assert.match(src, /housingFundAmount/);
    assert.match(src, /placeholder="公积金（选填）"/);
    assert.match(src, /Number\(l\.housingFundAmount \|\| 0\)/);
  }
  assert.match(api, /housingFundAmount\?: number/);
});

test('salary-capable detail tables show housing fund column', () => {
  const files = [
    'ruoyi-office-vben/apps/web-antd/src/views/finance/salary-payment/detail/index.vue',
    'ruoyi-office-vben/apps/web-antd/src/views/finance/payment-application/detail/index.vue',
    'ruoyi-office-vben/apps/web-antd/src/views/finance/tax-payment/detail/index.vue',
  ];
  for (const file of files) {
    const src = readRepositoryFile(file);
    assert.match(src, /title: '公积金'/);
    assert.match(src, /dataIndex: 'housingFundAmount'/);
  }
});
