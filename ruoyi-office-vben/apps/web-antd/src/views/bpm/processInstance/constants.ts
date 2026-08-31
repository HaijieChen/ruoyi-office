/**
 * Finance CUSTOM formCustomViewPath values that use the same process-detail
 * shell as NORMAL forms (tabs + timeline + diagram + records).
 * Must match deployed BPM formCustomViewPath byte-for-byte.
 */
export const FINANCE_APPROVAL_PSHELL_VIEW_PATHS = [
  '/finance/contract-application/info/index',
  '/finance/invoice-application/info/index',
  '/finance/payment-application/detail/index',
  '/finance/salary-payment/detail/index',
  '/finance/tax-payment/detail/index',
  '/finance/invoice-redflush/info/index',
  '/finance/expense-reimbursement/detail',
  '/oa/seal/sealapply/info/index',
  '/oa/seal/seal-apply-info',
] as const;

export function isFinanceApprovalPShellViewPath(
  path?: null | string,
): boolean {
  if (!path) return false;
  const normalized = path.trim().replace(/\/+$/, '');
  return (FINANCE_APPROVAL_PSHELL_VIEW_PATHS as readonly string[]).some(
    (p) => p === path || p === normalized || p.replace(/\/index$/, '') === normalized,
  );
}
