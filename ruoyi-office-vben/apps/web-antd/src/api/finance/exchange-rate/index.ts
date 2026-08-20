import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace FinanceExchangeRateApi {
  export interface Row {
    id?: number;
    periodLabel: string;
    fromCurrency: string;
    toCurrency: string;
    rate: number;
  }
}

export function getExchangeRatePage(params: PageParam & Partial<FinanceExchangeRateApi.Row>) {
  return requestClient.get<PageResult<FinanceExchangeRateApi.Row>>(
    '/finance/exchange-rate/page',
    { params },
  );
}

export function saveExchangeRate(data: FinanceExchangeRateApi.Row) {
  return requestClient.post<number>('/finance/exchange-rate/save', data);
}
