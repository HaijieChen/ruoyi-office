import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { test } from 'node:test';
import vm from 'node:vm';

const require = createRequire(new URL('../../ruoyi-office-vben/package.json', import.meta.url));
const { parse, compileScript, compileTemplate } = require('vue/compiler-sfc');
const ts = require('typescript');
const vue = require('vue');
const source = readFileSync(new URL('../../ruoyi-office-vben/apps/web-antd/src/views/bpm/model/form/modules/extra-setting.vue', import.meta.url), 'utf8');

// Execute the actual SFC setup, keeping Vue reactivity; stub only framework/UI imports.
function setup(data) {
  const model = vue.ref(data);
  const { descriptor } = parse(source);
  const script = compileScript(descriptor, { id: 'withdraw-policy-test' });
  const js = ts.transpileModule(script.content, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;
  const module = { exports: {} };
  vm.runInNewContext(js, {
    module, exports: module.exports,
    require(id) {
      if (id === 'vue') return { ...vue, useModel: () => model, provide() {} };
      if (id === '@vben/common-ui') return { useVbenModal: () => [null, {}] };
      if (id === '@vben/constants') return { BpmAutoApproveType: { NONE: 0 }, BpmModelFormType: { NORMAL: 10 } };
      return {};
    },
  });
  const bindings = module.exports.default.setup({}, { expose() {} });
  return { bindings, model };
}

test('legacy inherited display does not materialize on open or unrelated save', () => {
  for (const oldSwitch of [undefined, null, false, true]) {
    for (const mode of [undefined, null]) {
      const { bindings, model } = setup({ initiatorWithdrawMode: mode, allowWithdrawTask: oldSwitch, allowCancelRunningProcess: true });
      bindings.initData();
      assert.ok(bindings.initiatorWithdrawDisplayMode, 'actual selector computed binding exists');
      assert.equal(bindings.initiatorWithdrawDisplayMode.value, oldSwitch === false ? 0 : 2);
      model.value.description = 'unrelated';
      const saved = JSON.parse(JSON.stringify(model.value));
      assert.equal(saved.initiatorWithdrawMode, mode);
      assert.equal(saved.allowWithdrawTask, oldSwitch);
      assert.equal(saved.allowCancelRunningProcess, true);
    }
  }
});

test('intentional selection writes only new policy, including inherited same value', () => {
  for (const value of [0, 1, 2]) {
    const { bindings, model } = setup({ initiatorWithdrawMode: null, allowWithdrawTask: false, allowCancelRunningProcess: true });
    bindings.initiatorWithdrawDisplayMode.value = value;
    assert.equal(model.value.initiatorWithdrawMode, value);
    assert.equal(model.value.allowWithdrawTask, false);
    assert.equal(model.value.allowCancelRunningProcess, true);
    assert.equal(setup(JSON.parse(JSON.stringify(model.value))).bindings.initiatorWithdrawDisplayMode.value, value);
  }
});

test('rendered radio click intentionally materializes even the inherited selected option', () => {
  const { descriptor } = parse(source);
  const result = compileTemplate({ source: descriptor.template.content, filename: 'extra-setting.vue', id: 'withdraw-policy-test' });
  const js = ts.transpileModule(result.code, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
  const module = { exports: {} };
  vm.runInNewContext(js, { module, exports: module.exports, require: () => ({ ...vue, resolveComponent: (name) => name }) });
  for (const value of [0, 1, 2]) {
    const { bindings, model } = setup({ initiatorWithdrawMode: null, allowWithdrawTask: false });
    bindings.initData();
    const vnode = module.exports.render(vue.proxyRefs(bindings), []);
    const radios = [];
    function visit(node) {
      if (!node || typeof node !== 'object') return;
      if (Array.isArray(node)) return node.forEach(visit);
      if (node.type === 'Radio' && node.props?.onClick) radios.push(node);
      visit(node.children);
      if (typeof node.default === 'function') visit(node.default());
    }
    visit(vnode);
    assert.equal(radios.length, 3);
    radios.find((radio) => radio.props.value === value).props.onClick();
    assert.equal(model.value.initiatorWithdrawMode, value);
    assert.equal(model.value.allowWithdrawTask, false);
  }
});

test('actual model form defaults only new models and preserves loaded legacy policy', async () => {
  const formSource = readFileSync(new URL('../../ruoyi-office-vben/apps/web-antd/src/views/bpm/model/form/index.vue', import.meta.url), 'utf8');
  const { descriptor } = parse(formSource);
  const script = compileScript(descriptor, { id: 'model-form-test' });
  const js = ts.transpileModule(script.content, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
  for (const action of ['create', 'update', 'copy', 'definition']) {
    const module = { exports: {} };
    vm.runInNewContext(js, {
      module, exports: module.exports,
      require(id) {
        if (id === 'vue') return { ...vue, provide() {}, onMounted() {}, onBeforeUnmount() {} };
        if (id === 'vue-router') return { useRouter: () => ({}), useRoute: () => ({ params: { type: action, id: 'old' } }) };
        if (id === '@vben/stores') return { useUserStore: () => ({ userInfo: { id: 1 } }) };
        if (id === '@vben/hooks') return { useTabs: () => ({}) };
        if (id === '@vben/constants') return { BpmAutoApproveType: { NONE: 0 }, BpmModelType: { SIMPLE: 10, BPMN: 20 }, BpmModelFormType: { NORMAL: 10 } };
        if (id === '#/api/bpm/model') return { getModel: async () => ({ name: 'Old', key: 'old', initiatorWithdrawMode: null }) };
        if (id === '#/api/bpm/definition') return { getProcessDefinition: async () => ({ modelId: 'old', modelType: 10, initiatorWithdrawMode: null }) };
        return new Proxy({}, { get: () => async () => [] });
      },
    });
    const bindings = module.exports.default.setup({}, { expose() {} });
    await bindings.initData();
    assert.equal(bindings.formData.value.initiatorWithdrawMode, action === 'create' ? 0 : null);
  }
});

test('extra settings template compiles', () => {
  const { descriptor } = parse(source);
  const result = compileTemplate({ source: descriptor.template.content, filename: 'extra-setting.vue', id: 'withdraw-policy-test' });
  assert.deepEqual(result.errors, []);
});
