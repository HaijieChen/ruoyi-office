/**
 * EXP-70：商务单编辑打开时的只读产品展示解析。
 * 仅使用详情响应字段（快照优先），不得读合同当前产品 map。
 */
export function resolveEditOpenProductType(detail: {
  productName?: null | string;
  productType?: null | string;
}): string | undefined {
  if (detail.productType != null && String(detail.productType).trim() !== '') {
    return String(detail.productType).trim();
  }
  if (detail.productName != null && String(detail.productName).trim() !== '') {
    return String(detail.productName).trim();
  }
  return undefined;
}

/**
 * 用户显式切换合同时的产品展示（复审 #10）。
 * - 选回「已保存合同 ID」→ 恢复已保存快照
 * - 换到其他合同 → 使用该合同当前产品
 * - 清空合同 → undefined
 */
export function resolveProductAfterContractChange(opts: {
  /** 下拉选中的合同 id */
  selectedContractId?: null | number;
  /** 打开编辑时详情上的已保存合同 id */
  savedContractId?: null | number;
  /** 打开编辑时详情上的已保存产品快照 */
  savedProductSnapshot?: null | string;
  /** 选中合同在可选列表中的当前产品（合同权威当前值） */
  contractCurrentProduct?: null | string;
}): string | undefined {
  const selected = opts.selectedContractId;
  if (selected == null) {
    return undefined;
  }
  if (
    opts.savedContractId != null &&
    selected === opts.savedContractId &&
    opts.savedProductSnapshot != null &&
    String(opts.savedProductSnapshot).trim() !== ''
  ) {
    return String(opts.savedProductSnapshot).trim();
  }
  if (
    opts.contractCurrentProduct != null &&
    String(opts.contractCurrentProduct).trim() !== ''
  ) {
    return String(opts.contractCurrentProduct).trim();
  }
  return undefined;
}
