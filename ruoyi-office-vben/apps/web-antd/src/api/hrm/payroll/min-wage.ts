import { requestClient } from '#/api/request';

export namespace MinWageApi {
  export interface MinWageRow {
    id?: number;
    amount: number;
    effectiveMonth: number;
    createTime?: string;
  }
}

export function getMinWageHistory() {
  return requestClient.get<MinWageApi.MinWageRow[]>('/hrm/min-wage/history');
}

export function createMinWage(data: { amount: number; nextMonth: boolean }) {
  return requestClient.post('/hrm/min-wage/create', data);
}
