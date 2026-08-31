import assert from 'node:assert/strict';
import test from 'node:test';

function resolveBusinessFormViewPath(todoTask, processFormCustomViewPath) {
  const nodePath = todoTask?.formCustomViewPath?.trim() ?? '';
  if (nodePath) {
    return { path: nodePath, source: 'node' };
  }
  return { path: processFormCustomViewPath?.trim() || '', source: 'process' };
}

test('prefers a non-empty todo node path', () => {
  assert.deepEqual(
    resolveBusinessFormViewPath(
      { formCustomViewPath: '/finance/salary-payment/detail/cashier' },
      '/finance/salary-payment/detail/index',
    ),
    { path: '/finance/salary-payment/detail/cashier', source: 'node' },
  );
});

test('falls back when todo path is blank or missing', () => {
  assert.deepEqual(
    resolveBusinessFormViewPath(
      { formCustomViewPath: '  ' },
      '/finance/salary-payment/detail/index',
    ),
    { path: '/finance/salary-payment/detail/index', source: 'process' },
  );
  assert.deepEqual(
    resolveBusinessFormViewPath(null, '/finance/payment-application/detail/index'),
    { path: '/finance/payment-application/detail/index', source: 'process' },
  );
});
