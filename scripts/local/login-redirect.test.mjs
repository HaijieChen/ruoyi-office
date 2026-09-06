import assert from 'node:assert/strict';
import test from 'node:test';
import { resolveLoginRedirect } from '../../ruoyi-office-vben/apps/web-antd/src/router/login-redirect.ts';
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
