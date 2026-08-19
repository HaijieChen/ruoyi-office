import type { Component } from 'vue';

/**
 * CUSTOM 流程在「通用发起」壳内嵌的业务表单注册表。
 * key = processDefinition.key（与后端 PROCESS_KEY 一致）。
 *
 * 合同 / 付款 / 开票 / 薪税 / 出差 / 外出：壳内嵌业务 form-body。
 * submit 走各自业务 API，不走通用 createProcessInstance。
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
  finance_salary_payment_apply: () =>
    import('#/views/finance/salary-payment/modules/form-body.vue'),
  finance_tax_payment_apply: () =>
    import('#/views/finance/tax-payment/modules/form-body.vue'),
  oa_business_trip: () => import('#/views/bpm/oa/trip/modules/form-body.vue'),
  oa_outing: () => import('#/views/bpm/oa/outing/modules/form-body.vue'),
  oa_expense_reimbursement: () =>
    import('#/views/finance/expense-reimbursement/modules/form-body.vue'),
  oa_expense_no_invoice: () =>
    import('#/views/finance/expense-reimbursement/modules/no-invoice-form-body.vue'),
};

/**
 * 统一发起目录：卡片点击后 router 跳转的业务入口（非壳内嵌）。
 * path 与后端 BpmEmbedProcessStartPermissionRegistry.catalogRedirectPath 对齐。
 * openCreate=1 打开创建表单（各业务页须支持）。
 */
export const CREATE_SHELL_REDIRECT_REGISTRY: Record<string, string> = {};

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
