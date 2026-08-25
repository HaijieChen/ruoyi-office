import { getCompanyBankAccountSimpleList } from '#/api/finance/company-bank-account';

const ALLOWED = new Set(['CNY', 'USD', 'HKD']);

/** 启用账户币种：仅一种用该种；多种优先 CNY；没有账户则 CNY。 */
export async function defaultCurrencyFromCompanyAccounts(
  entityCompanyDeptId?: number | null,
): Promise<string> {
  if (entityCompanyDeptId == null) {
    return 'CNY';
  }
  try {
    const rows = (await getCompanyBankAccountSimpleList(entityCompanyDeptId)) ?? [];
    const found = new Set<string>();
    for (const row of rows) {
      const c = String(row.currency || '')
        .trim()
        .toUpperCase();
      if (ALLOWED.has(c)) {
        found.add(c);
      }
    }
    if (found.size === 1) {
      return [...found][0]!;
    }
    if (found.has('CNY')) {
      return 'CNY';
    }
    if (found.size > 0) {
      return [...found][0]!;
    }
  } catch {
    // 无账户权限时退回 CNY
  }
  return 'CNY';
}
