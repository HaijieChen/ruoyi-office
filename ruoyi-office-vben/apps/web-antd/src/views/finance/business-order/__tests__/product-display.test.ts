import { describe, expect, it } from 'vitest';

import {
  resolveEditOpenProductType,
  resolveProductAfterContractChange,
} from '../product-display';

describe('resolveEditOpenProductType (EXP-70 rereview #2)', () => {
  it('prefers detail productType snapshot over productName', () => {
    expect(
      resolveEditOpenProductType({ productType: '软件', productName: '旧自由文本' }),
    ).toBe('软件');
  });

  it('keeps stored snapshot when it differs from contract current value', () => {
    const detailSnapshot = '软件';
    const contractCurrentProduct = '硬件';
    const displayed = resolveEditOpenProductType({
      productType: detailSnapshot,
      productName: detailSnapshot,
    });
    expect(displayed).toBe('软件');
    expect(displayed).not.toBe(contractCurrentProduct);
  });

  it('falls back to productName when productType blank', () => {
    expect(resolveEditOpenProductType({ productType: '  ', productName: 'legacy' })).toBe(
      'legacy',
    );
    expect(resolveEditOpenProductType({ productType: null, productName: 'legacy' })).toBe(
      'legacy',
    );
  });

  it('returns undefined when both empty', () => {
    expect(resolveEditOpenProductType({ productType: null, productName: null })).toBeUndefined();
    expect(resolveEditOpenProductType({})).toBeUndefined();
  });
});

describe('resolveProductAfterContractChange (EXP-70 rereview #10 A→B→A)', () => {
  const savedId = 50;
  const savedSnap = '软件';
  const contractA = '软件'; // 合同当前可能已变，但选回原合同时仍恢复 savedSnap
  const contractB = '硬件';

  it('A → B uses contract B current product', () => {
    expect(
      resolveProductAfterContractChange({
        selectedContractId: 60,
        savedContractId: savedId,
        savedProductSnapshot: savedSnap,
        contractCurrentProduct: contractB,
      }),
    ).toBe('硬件');
  });

  it('A → B → A restores saved snapshot even if contract A current differs', () => {
    // 合同 A 当前已改为「独代」，但已保存快照仍是「软件」
    expect(
      resolveProductAfterContractChange({
        selectedContractId: savedId,
        savedContractId: savedId,
        savedProductSnapshot: savedSnap,
        contractCurrentProduct: '独代',
      }),
    ).toBe('软件');
    expect(
      resolveProductAfterContractChange({
        selectedContractId: savedId,
        savedContractId: savedId,
        savedProductSnapshot: savedSnap,
        contractCurrentProduct: contractA,
      }),
    ).toBe(savedSnap);
  });

  it('clearing contract clears product display', () => {
    expect(
      resolveProductAfterContractChange({
        selectedContractId: null,
        savedContractId: savedId,
        savedProductSnapshot: savedSnap,
        contractCurrentProduct: contractA,
      }),
    ).toBeUndefined();
  });

  it('create flow (no saved) uses selected contract current product', () => {
    expect(
      resolveProductAfterContractChange({
        selectedContractId: 1,
        savedContractId: undefined,
        savedProductSnapshot: undefined,
        contractCurrentProduct: '搜索',
      }),
    ).toBe('搜索');
  });
});
