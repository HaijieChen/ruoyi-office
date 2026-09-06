import assert from 'node:assert/strict';
import test from 'node:test';
import {
  encodeLoginRedirectParam,
  hashRouterFullPath,
  isLoginRoute,
  resolveLoginRedirect,
} from '../../ruoyi-office-vben/apps/web-antd/src/router/login-redirect.ts';
const login = '/auth/login';
const resolve = (target, home = '/workspace') => resolveLoginRedirect(target, home, login, login);
test('old workspace cache and self redirects reach home', () => {
  for (const target of ['', '/workspace', '/auth/login', '/auth/login?redirect=%2Fworkspace']) {
    assert.equal(resolve(target), '/home');
  }
});
test('keeps an intended business path including query and hash', () => {
  assert.equal(resolve(encodeURIComponent('/bpm/process-instance?id=1#detail')), '/bpm/process-instance?id=1#detail');
});
test('uses a valid configured homepage', () => assert.equal(resolve('', '/dashboard'), '/dashboard'));
test('malformed encoded redirect falls back without breaking login', () => assert.equal(resolve('%E0%A4%A'), '/home'));
test('bad homepage cannot loop back to login', () => assert.equal(resolve('', '/auth/login'), '/home'));
test('LGN-07 workspace query and hash are loops', () => {
  assert.equal(resolve('/workspace?x=1'), '/home');
  assert.equal(resolve('/workspace#h'), '/home');
});
test('LGN-09 Workspace case is not the stale alias', () => {
  assert.equal(resolve('/Workspace'), '/Workspace');
});
test('one decode of wrapped %252F keeps acc=%252F', () => {
  const intended = '/bpm/manager/model?acc=%252F';
  assert.equal(resolve(encodeURIComponent(intended)), intended);
  assert.equal(resolve(intended), intended);
});
test('already-decoded business path is not decoded again', () => {
  assert.equal(resolve('/bpm/manager/model?acc=%2F#detail'), '/bpm/manager/model?acc=%2F#detail');
});
test('LGN-17 vue-router one-decode of encodeURIComponent fullPath keeps business query', () => {
  const business = '/bpm/manager/model?acc=acc_wd_20260906_marker';
  const afterVueRouter = encodeURIComponent(business);
  assert.equal(resolve(afterVueRouter), business);
  assert.equal(new URLSearchParams(resolve(afterVueRouter).split('?')[1]).get('acc'), 'acc_wd_20260906_marker');
});
test('LGN-17 logout does not nest login as redirect', () => {
  assert.equal(encodeLoginRedirectParam('/auth/login', login), undefined);
  assert.equal(encodeLoginRedirectParam('/auth/login?redirect=%2Fhome', login), undefined);
  assert.equal(
    encodeLoginRedirectParam('/bpm/manager/model?acc=acc_wd_20260906_marker', login),
    encodeURIComponent('/bpm/manager/model?acc=acc_wd_20260906_marker'),
  );
  assert.equal(isLoginRoute('/auth/login?redirect=%2Fhome', login), true);
  assert.equal(hashRouterFullPath('#/bpm/manager/model?acc=1'), '/bpm/manager/model?acc=1');
});
