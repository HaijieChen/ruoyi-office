export const MFA_FLOW_STORAGE_KEY = 'oa.mfa.flow.v1';

export type MfaLoginNext = 'home' | 'challenge' | 'enroll' | 'error';

export interface MfaFactorRef {
  id?: string;
  type?: string;
  label?: string;
  maskedTarget?: string;
}

export interface MfaFlowPayload {
  flowToken?: string;
  tokenClass?: string;
  expiresIn?: number;
  allowedActions?: string[];
  factors?: MfaFactorRef[];
}

export interface MfaStoredFlow extends MfaFlowPayload {
  savedAt: number;
}

export interface MfaLoginLike {
  loginStatus?: string;
  accessToken?: string;
  refreshToken?: string;
  userId?: number;
  expiresTime?: number | string;
  flow?: MfaFlowPayload;
}

function hasFlowToken(result: MfaLoginLike): boolean {
  return Boolean(result.flow?.flowToken);
}

export function resolveLoginNext(result: MfaLoginLike | null | undefined): MfaLoginNext {
  if (!result) {
    return 'error';
  }
  const status = result.loginStatus;
  if (status === 'MFA_REQUIRED') {
    return hasFlowToken(result) ? 'challenge' : 'error';
  }
  if (status === 'MFA_ENROLLMENT_REQUIRED') {
    return hasFlowToken(result) ? 'enroll' : 'error';
  }
  if (status === 'AUTHENTICATED' || (!status && result.accessToken)) {
    return result.accessToken ? 'home' : 'error';
  }
  if (result.accessToken && (status == null || status === '')) {
    return 'home';
  }
  return 'error';
}

export function saveMfaFlow(
  flow: MfaFlowPayload,
  storage: Storage = globalThis.sessionStorage,
  now = Date.now(),
): void {
  const stored: MfaStoredFlow = { ...flow, savedAt: now };
  storage.setItem(MFA_FLOW_STORAGE_KEY, JSON.stringify(stored));
}

export function loadMfaFlow(
  storage: Storage = globalThis.sessionStorage,
): MfaStoredFlow | null {
  const raw = storage.getItem(MFA_FLOW_STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as MfaStoredFlow;
    if (!parsed?.flowToken) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function clearMfaFlow(storage: Storage = globalThis.sessionStorage): void {
  storage.removeItem(MFA_FLOW_STORAGE_KEY);
}

export function isMfaFlowExpired(
  flow: MfaStoredFlow | null | undefined,
  now = Date.now(),
): boolean {
  if (!flow) {
    return true;
  }
  if (flow.expiresIn == null) {
    return false;
  }
  return now > flow.savedAt + flow.expiresIn * 1000;
}
