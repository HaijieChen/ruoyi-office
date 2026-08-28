import { describe, expect, it } from 'vitest';

import { mapWageCardToPayee } from '../payee-prefill';

describe('mapWageCardToPayee', () => {
  it('maps archive name and wage card onto payee fields', () => {
    expect(
      mapWageCardToPayee({
        name: '李四',
        bankName: '招商银行',
        bankAccount: '6225',
      }),
    ).toEqual({
      payeeAccountName: '李四',
      payeeBankName: '招商银行',
      payeeAccountNo: '6225',
    });
  });

  it('empty archive leaves blanks for hand-fill', () => {
    expect(mapWageCardToPayee(null)).toEqual({
      payeeAccountName: '',
      payeeBankName: '',
      payeeAccountNo: '',
    });
  });
});
