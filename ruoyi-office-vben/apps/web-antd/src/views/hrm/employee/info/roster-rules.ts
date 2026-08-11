export interface ContractLike {
  sequenceNo?: number;
  startDate?: string;
  endDate?: string;
  contractType?: string;
  id?: number;
}

export interface EducationLike {
  firstEducation?: boolean;
  highestEducation?: boolean;
}

export interface SocialSecurityLike {
  socialSecurityEnabled?: boolean | null;
  socialSecurityStartMonth?: string;
}

/**
 * 规范化合同列表：序号从 1 连续编号，最多 4 条
 */
export function normalizeContractList<T extends ContractLike>(
  list: T[] | undefined | null,
): T[] {
  const items = [...(list || [])];
  if (items.length > 4) {
    throw new Error('最多维护四次合同');
  }
  return items.map((item, index) => ({
    ...item,
    sequenceNo: (index + 1) as 1 | 2 | 3 | 4,
  }));
}

/**
 * 校验合同日期与数量
 */
export function validateContractList(
  list: ContractLike[] | undefined | null,
): string | undefined {
  if (!list || list.length === 0) {
    return undefined;
  }
  if (list.length > 4) {
    return '最多维护四次合同';
  }
  for (let i = 0; i < list.length; i++) {
    const item = list[i]!;
    if (item.sequenceNo !== i + 1) {
      return '合同序号必须从1连续递增';
    }
    if (item.startDate && item.endDate && item.endDate < item.startDate) {
      return '合同结束日期不能早于开始日期';
    }
  }
  return undefined;
}

/**
 * 校验第一/最高学历唯一性
 */
export function validateEducationRoles(
  list: EducationLike[] | undefined | null,
): string | undefined {
  if (!list || list.length === 0) {
    return undefined;
  }
  const firstCount = list.filter((e) => e.firstEducation).length;
  const highestCount = list.filter((e) => e.highestEducation).length;
  if (firstCount > 1 || highestCount > 1) {
    return '第一学历与最高学历各至多一条';
  }
  return undefined;
}

/**
 * 缴纳社保时必须填写参保年月
 */
export function validateSocialSecurity(
  value: SocialSecurityLike,
): string | undefined {
  if (value.socialSecurityEnabled === true) {
    if (!value.socialSecurityStartMonth) {
      return '缴纳社保时必须填写参保年月';
    }
    if (!/^\d{4}-\d{2}$/.test(value.socialSecurityStartMonth)) {
      return '参保年月格式必须为yyyy-MM';
    }
  }
  return undefined;
}
