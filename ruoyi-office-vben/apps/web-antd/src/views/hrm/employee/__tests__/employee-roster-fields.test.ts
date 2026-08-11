import { describe, expect, it } from 'vitest';

import { createAttachmentFromUpload } from '../../../../components/attachment-list/data';
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

  it('builds attachment metadata only from server upload result', () => {
    const file = new File(['%PDF'], 'scan.pdf', { type: 'application/pdf' });
    const att = createAttachmentFromUpload(file, 1, {
      url: 'https://cdn.example.com/scan.pdf',
      path: '/hrm/scan.pdf',
    });
    expect(att.fileUrl).toBe('https://cdn.example.com/scan.pdf');
    expect(att.filePath).toBe('/hrm/scan.pdf');
    expect(att.fileUrl.startsWith('blob:')).toBe(false);
    expect(att.fileExtension).toBe('pdf');
  });
});
