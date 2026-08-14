import type { Component } from 'vue';

/**
 * CUSTOM 流程在「通用发起」壳内嵌的业务表单注册表。
 * key = processDefinition.key（与后端 PROCESS_KEY 一致）。
 *
 * EXP-87：finance_salary_payment_apply / finance_tax_payment_apply **不注册壳内嵌**，
 * 也不走通用 createProcessInstance；统一目录卡片通过 {@link CREATE_SHELL_REDIRECT_REGISTRY}
 * 跳转业务创建页（独立菜单入口同等可用）。
 */
export const CREATE_SHELL_EMBED_REGISTRY: Record<
  string,
  () => Promise<{ default: Component }>
> = {
  finance_payment_apply: () =>
    import('#/views/finance/payment-application/modules/form-body.vue'),
  finance_contract_sign: () =>
    import('#/views/finance/contract-application/modules/form-body.vue'),
  finance_invoice_apply: () =>
    import('#/views/finance/invoice-application/modules/form-body.vue'),
};

/**
 * 统一发起目录：卡片点击后 router 跳转的业务入口（非壳内嵌）。
 * path 与后端 BpmEmbedProcessStartPermissionRegistry.catalogRedirectPath 对齐。
 * openCreate=1 打开创建表单（各业务页须支持）。
 */
export const CREATE_SHELL_REDIRECT_REGISTRY: Record<string, string> = {
  finance_salary_payment_apply: '/finance/salary-payment',
  finance_tax_payment_apply: '/finance/tax-payment',
};

export function resolveCreateShellEmbedLoader(key?: null | string) {
  if (!key) return undefined;
  return CREATE_SHELL_EMBED_REGISTRY[key];
}

export function isCreateShellEmbedRegistered(key?: null | string): boolean {
  return !!key && key in CREATE_SHELL_EMBED_REGISTRY;
}

export function resolveCreateShellRedirectPath(
  key?: null | string,
): string | undefined {
  if (!key) return undefined;
  return CREATE_SHELL_REDIRECT_REGISTRY[key];
}

export function isCreateShellRedirectProcess(key?: null | string): boolean {
  return !!key && key in CREATE_SHELL_REDIRECT_REGISTRY;
}
