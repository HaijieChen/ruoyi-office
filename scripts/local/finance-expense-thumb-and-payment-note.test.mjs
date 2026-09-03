import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

function read(rel) {
  return readFileSync(new URL(`../../${rel}`, import.meta.url), 'utf8');
}

test('expense reimbursement attachments use compact picture-card thumbs', () => {
  const upload = read(
    'ruoyi-office-vben/apps/web-antd/src/components/upload/file-upload.vue',
  );
  const form = read(
    'ruoyi-office-vben/apps/web-antd/src/views/finance/expense-reimbursement/modules/form-body.vue',
  );
  const detail = read(
    'ruoyi-office-vben/apps/web-antd/src/views/finance/expense-reimbursement/detail.vue',
  );

  const preview = read(
    'ruoyi-office-vben/apps/web-antd/src/components/upload/file-preview-list.vue',
  );
  assert.match(upload, /listType: 'text'/);
  assert.match(upload, /:list-type="listType"/);
  assert.match(upload, /file-upload-thumb/);
  assert.match(upload, /#itemRender/);
  assert.match(preview, /list-type="picture-card"/);
  assert.match(form, /list-type="picture-card"/);
  assert.match(detail, /title="附件"/);
  assert.match(detail, /FilePreviewList :value="record.invoiceFileUrl"/);
});

test('payment application special note is visible on form and detail', () => {
  const form = read(
    'ruoyi-office-vben/apps/web-antd/src/views/finance/payment-application/modules/form-body.vue',
  );
  const bpmDetail = read(
    'ruoyi-office-vben/apps/web-antd/src/views/finance/payment-application/detail/index.vue',
  );
  const modalDetail = read(
    'ruoyi-office-vben/apps/web-antd/src/views/finance/payment-application/modules/detail.vue',
  );

  assert.match(form, /name="specialNote"/);
  assert.match(form, /special-note-textarea/);
  assert.match(form, /-webkit-text-fill-color/);
  assert.match(form, /formData\.specialNote/);
  assert.match(bpmDetail, /特殊说明/);
  assert.match(bpmDetail, /detail\.specialNote/);
  assert.match(modalDetail, /特殊说明/);
  assert.match(modalDetail, /detail\.specialNote/);
});
