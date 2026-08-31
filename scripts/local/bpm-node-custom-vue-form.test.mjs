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
