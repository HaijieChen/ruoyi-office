import assert from 'node:assert/strict';
import test from 'node:test';
import { resolveLoginRedirect } from '../../ruoyi-office-vben/apps/web-antd/src/router/login-redirect.ts';

const login = '/auth/login';
const resolve = (target, home = '/workspace', current = login) =>
  resolveLoginRedirect(target, home, login, current);

test('LGN-07 workspace query and hash are loops', () => {
  assert.equal(resolve('/workspace?x=1'), '/home');
  assert.equal(resolve('/workspace#h'), '/home');
});

test('LGN-09 Workspace case is not the stale alias', () => {
  assert.equal(resolve('/Workspace'), '/Workspace');
});

test('keeps registered manager model path including encoded percent and hash', () => {
  const intended = '/bpm/manager/model?acc=%2F#detail';
  assert.equal(resolve(encodeURIComponent(intended)), intended);
});

test('one decode of %252F still leaves %2F in acc', () => {
  const raw = encodeURIComponent('/bpm/manager/model?acc=%252F');
  assert.equal(resolve(raw), '/bpm/manager/model?acc=%252F');
});

test('external URL string is returned and is not treated as navigation', () => {
  assert.equal(resolve('https://evil.example/'), 'https://evil.example/');
  assert.equal(resolve('//evil.example/'), '//evil.example/');
});
