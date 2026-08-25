import { requestClient } from '#/api/request';

export namespace SystemMfaFactorApi {
  export interface Factor {
    id: string;
    type?: string;
    status?: string;
    label?: string;
    maskedTarget?: string;
  }
  export interface TotpStart {
    factorId?: string;
    otpauthUri?: string;
    secretManual?: string;
  }
  export interface EmailStart {
    factorId?: string;
    maskedEmail?: string;
  }
}

export function getMyMfaFactors() {
  return requestClient.get<SystemMfaFactorApi.Factor[]>('/system/mfa/factor/list');
}

export function startMyTotp() {
  return requestClient.post<SystemMfaFactorApi.TotpStart>('/system/mfa/factor/totp/start');
}

export function confirmMyTotp(data: { factorId: string; code: string }) {
  return requestClient.post<boolean>('/system/mfa/factor/totp/confirm', data);
}

export function startMyEmail() {
  return requestClient.post<SystemMfaFactorApi.EmailStart>('/system/mfa/factor/email/start');
}

export function confirmMyEmail(data: { factorId: string; code: string }) {
  return requestClient.post<boolean>('/system/mfa/factor/email/confirm', data);
}

export function deleteMyMfaFactor(factorId: string) {
  return requestClient.delete<boolean>(`/system/mfa/factor/delete?factorId=${factorId}`);
}
