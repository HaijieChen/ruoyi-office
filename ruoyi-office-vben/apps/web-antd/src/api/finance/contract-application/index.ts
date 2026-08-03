import { requestClient } from '#/api/request';

export namespace FinanceContractApplicationApi {
  export interface ContractApplication {
    id: number;
    applicationNo: string;
    approvalStatus?: string;
    counterpartyName?: string;
    contractAmount?: number;
    productType?: string;
  }
}

/** 商务签单可选合同（已通过且本人申请） */
export function listSelectableContractsForBo() {
  return requestClient.get<FinanceContractApplicationApi.ContractApplication[]>(
    '/finance/contract-application/list-selectable-for-bo',
  );
}
