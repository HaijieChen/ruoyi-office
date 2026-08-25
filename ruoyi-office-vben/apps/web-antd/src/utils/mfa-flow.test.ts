import { beforeEach, describe, expect, it } from 'vitest';

import {
  MFA_FLOW_STORAGE_KEY,
  clearMfaFlow,
  isMfaFlowExpired,
  loadMfaFlow,
  resolveLoginNext,
  saveMfaFlow,
} from './mfa-flow';

import type { AuthApi } from '#/api';

function memoryStorage(): Storage {
  const map = new Map<string, string>();
  return {
    get length() {
      return map.size;
    },
    clear() {
      map.clear();
    },
    getItem(key: string) {
      return map.has(key) ? map.get(key)! : null;
    },
    key(index: number) {
      return [...map.keys()][index] ?? null;
    },
    removeItem(key: string) {
      map.delete(key);
    },
    setItem(key: string, value: string) {
      map.set(key, value);
    },
  } as Storage;
}

describe('resolveLoginNext', () => {
  it('goes home when authenticated with access token', () => {
    expect(
      resolveLoginNext({
        loginStatus: 'AUTHENTICATED',
        accessToken: 'tok',
        refreshToken: 'ref',
        userId: 1,
      }),
    ).toBe('home');
  });

  it('treats legacy token-only payload as home', () => {
    expect(
      resolveLoginNext({
        accessToken: 'tok',
        refreshToken: 'ref',
        userId: 1,
      } as AuthApi.LoginResult),
    ).toBe('home');
  });

  it('routes challenge when MFA_REQUIRED has a flow token', () => {
    expect(
      resolveLoginNext({
        loginStatus: 'MFA_REQUIRED',
        userId: 1,
        flow: {
          flowToken: 'ft',
          tokenClass: 'PRE_AUTH',
          expiresIn: 300,
          allowedActions: ['verify', 'send', 'logout'],
          factors: [{ id: 'f1', type: 'TOTP' }],
        },
      }),
    ).toBe('challenge');
  });

  it('routes enroll when MFA_ENROLLMENT_REQUIRED has a flow token', () => {
    expect(
      resolveLoginNext({
        loginStatus: 'MFA_ENROLLMENT_REQUIRED',
        userId: 1,
        flow: {
          flowToken: 'ft',
          tokenClass: 'ENROLLMENT',
          expiresIn: 600,
          allowedActions: ['enroll', 'logout'],
          factors: [],
        },
      }),
    ).toBe('enroll');
  });

  it('rejects MFA_REQUIRED without flow token', () => {
    expect(
      resolveLoginNext({
        loginStatus: 'MFA_REQUIRED',
        userId: 1,
      }),
    ).toBe('error');
  });
});

describe('mfa flow session', () => {
  let storage: Storage;

  beforeEach(() => {
    storage = memoryStorage();
  });

  it('round-trips flow payload', () => {
    saveMfaFlow(
      {
        flowToken: 'abc',
        tokenClass: 'PRE_AUTH',
        expiresIn: 120,
        factors: [{ id: '1', type: 'TOTP' }],
      },
      storage,
      1000,
    );
    const loaded = loadMfaFlow(storage);
    expect(loaded?.flowToken).toBe('abc');
    expect(loaded?.savedAt).toBe(1000);
    expect(storage.getItem(MFA_FLOW_STORAGE_KEY)).toContain('abc');
  });

  it('expires using savedAt + expiresIn', () => {
    saveMfaFlow(
      { flowToken: 'abc', tokenClass: 'PRE_AUTH', expiresIn: 30 },
      storage,
      1000,
    );
    const loaded = loadMfaFlow(storage);
    expect(isMfaFlowExpired(loaded, 31000)).toBe(false);
    expect(isMfaFlowExpired(loaded, 32000)).toBe(true);
  });

  it('clears storage', () => {
    saveMfaFlow({ flowToken: 'abc', tokenClass: 'PRE_AUTH' }, storage, 1);
    clearMfaFlow(storage);
    expect(loadMfaFlow(storage)).toBeNull();
  });
});
