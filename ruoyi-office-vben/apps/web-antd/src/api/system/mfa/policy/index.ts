import { requestClient } from '#/api/request';

export namespace SystemMfaPolicyApi {
  export interface Policy {
    lifecycleState?: string;
    mode?: string;
    allowedFactors?: string[];
    usable?: boolean;
    unusableReason?: string;
    globalPolicyEpoch?: number;
    globalMinAcceptedEpoch?: number;
  }

  export interface PolicySave {
    mode: string;
    allowedFactors: string[];
  }
}

export function getMfaPolicy() {
  return requestClient.get<SystemMfaPolicyApi.Policy>('/system/mfa-policy/get');
}

export function updateMfaPolicy(data: SystemMfaPolicyApi.PolicySave) {
  return requestClient.put<number>('/system/mfa-policy/update', data);
}
