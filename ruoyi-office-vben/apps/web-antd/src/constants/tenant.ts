import { useAccessStore } from '@vben/stores';

/**
 * OA 固定登录租户。
 * 登录页不再提供租户选择，所有认证相关请求统一使用该租户。
 */
export const FIXED_LOGIN_TENANT_ID = 1;

type AccessStoreWithTenant = Pick<
  ReturnType<typeof useAccessStore>,
  'setTenantId' | 'tenantId'
>;

/** 将登录租户绑定为固定值（覆盖本地缓存里可能残留的其它租户） */
export function bindFixedLoginTenant(
  accessStore: AccessStoreWithTenant = useAccessStore(),
): number {
  accessStore.setTenantId(FIXED_LOGIN_TENANT_ID);
  return FIXED_LOGIN_TENANT_ID;
}

/** 解析请求头用的租户编号：优先 store，否则回落固定租户 */
export function resolveRequestTenantId(
  tenantId: null | number | undefined,
): number {
  return tenantId ?? FIXED_LOGIN_TENANT_ID;
}
