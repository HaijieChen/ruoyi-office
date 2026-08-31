import { describe, expect, it } from 'vitest';

function appliedAccountId(
  lines: { entityCompanyDeptId?: number; companyBankAccountId?: number }[],
  entityCompanyDeptId?: number,
) {
  return lines.find((l) => l.entityCompanyDeptId === entityCompanyDeptId)
    ?.companyBankAccountId;
}

describe('salary/tax line account default', () => {
  it('prefills cashier account from the matching application line', () => {
    expect(
      appliedAccountId(
        [
          { entityCompanyDeptId: 20, companyBankAccountId: 77 },
          { entityCompanyDeptId: 21, companyBankAccountId: 88 },
        ],
        21,
      ),
    ).toBe(88);
  });

  it('clears account when company changes', () => {
    const line: { entityCompanyDeptId?: number; companyBankAccountId?: number } =
      { entityCompanyDeptId: 20, companyBankAccountId: 77 };
    line.entityCompanyDeptId = 21;
    line.companyBankAccountId = undefined;
    expect(line.companyBankAccountId).toBeUndefined();
  });
});
