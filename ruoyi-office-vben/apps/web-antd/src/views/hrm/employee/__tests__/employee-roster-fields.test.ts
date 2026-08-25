import { describe, expect, it } from 'vitest';

import { createAttachmentFromOnboardingClaim } from '../../../../components/attachment-list/onboarding-claim';
import {
  calcAgeYears,
  calcTenureMonths,
  parseBirthdayFromIdCard,
} from '../info/derived-fields';
import {
  normalizeContractList,
  validateContractList,
  validateEducationRoles,
  validateSocialSecurity,
} from '../info/roster-rules';

describe('employee roster field rules', () => {
  it('keeps contract sequence contiguous and capped at four', () => {
    expect(
      normalizeContractList([{ startDate: '2026-01-01' }]),
    ).toMatchObject([{ sequenceNo: 1 }]);
    expect(() =>
      normalizeContractList(
        Array.from({ length: 5 }, () => ({ startDate: '2026-01-01' })),
      ),
    ).toThrow('最多维护四次合同');
  });

  it('requires one social-security month when social security is enabled', () => {
    expect(validateSocialSecurity({ socialSecurityEnabled: true })).toBe(
      '缴纳社保时必须填写参保年月',
    );
    expect(
      validateSocialSecurity({
        socialSecurityEnabled: true,
        socialSecurityStartMonth: '2024-01',
      }),
    ).toBeUndefined();
    expect(
      validateSocialSecurity({ socialSecurityEnabled: null }),
    ).toBeUndefined();
  });

  it('rejects duplicate education roles', () => {
    expect(
      validateEducationRoles([
        { firstEducation: true },
        { firstEducation: true },
      ]),
    ).toBe('第一学历与最高学历各至多一条');
    expect(
      validateEducationRoles([
        { firstEducation: true, highestEducation: true },
      ]),
    ).toBeUndefined();
  });

  it('rejects contract end before start', () => {
    expect(
      validateContractList([
        { sequenceNo: 1, startDate: '2024-01-01', endDate: '2023-01-01' },
      ]),
    ).toBe('合同结束日期不能早于开始日期');
  });

  it('builds attachment metadata only from HRM scoped claimToken (no public URL)', () => {
    const file = new File(['%PDF'], 'scan.pdf', { type: 'application/pdf' });
    const att = createAttachmentFromOnboardingClaim(file, 1, {
      claimToken: 'claim-token-abc',
      fileName: 'scan.pdf',
      fileSize: 4,
      fileExtension: 'pdf',
    });
    expect((att as any).claimToken).toBe('claim-token-abc');
    expect((att as any).fileId).toBeUndefined();
    expect(att.fileUrl).toBe('');
    expect(att.filePath).toBe('');
    expect(att.fileExtension).toBe('pdf');
  });
});

describe('employee archive derived age and tenure', () => {
  const today = new Date(2026, 7, 25); // 2026-08-25

  it('parses birthday from an 18-digit id card', () => {
    expect(parseBirthdayFromIdCard('11010119900307123X')).toBe('1990-03-07');
    expect(parseBirthdayFromIdCard('bad')).toBeUndefined();
  });

  it('computes full years of age and does not increment before birthday', () => {
    expect(calcAgeYears('1990-08-25', today)).toBe(36);
    expect(calcAgeYears('1990-08-26', today)).toBe(35);
    expect(calcAgeYears(undefined, today)).toBeUndefined();
  });

  it('computes tenure in complete months like backend Period.between', () => {
    expect(calcTenureMonths('2020-08-25', today)).toBe(72);
    expect(calcTenureMonths('2020-08-26', today)).toBe(71);
    expect(calcTenureMonths('2026-08-31', today)).toBe(0);
    expect(calcTenureMonths(undefined, today)).toBeUndefined();
  });
});
