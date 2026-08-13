import type { Component } from 'vue';

/**
 * CUSTOM 流程在「通用发起」壳内嵌的业务表单注册表。
 * key = processDefinition.key（与后端 PROCESS_KEY 一致）。
 * catalog 不再 router.push(formCustomCreatePath)。
 *
 * EXP-87：finance_salary_payment_apply / finance_tax_payment_apply **不注册**，
 * 与后端 BpmEmbedProcessStartPermissionRegistry 一致——仅走独立菜单发起，
 * 避免通用壳「未配置发起表单」。
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

export function resolveCreateShellEmbedLoader(key?: null | string) {
  if (!key) return undefined;
  return CREATE_SHELL_EMBED_REGISTRY[key];
}

export function isCreateShellEmbedRegistered(key?: null | string): boolean {
  return !!key && key in CREATE_SHELL_EMBED_REGISTRY;
}
