import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace BpmOAOvertimeCalendarApi {
  export interface Version {
    id?: number;
    calendarYear?: number;
    source?: string;
    sourceUrl?: string;
    fetchedAt?: string;
    contentHash?: string;
    status?: string;
    verifiedBy?: number;
    verifiedAt?: string;
    parseNote?: string;
    diffJson?: string;
    legalHolidaysJson?: string;
    makeupWorkdaysJson?: string;
    makeupRestDaysJson?: string;
    weekendsJson?: string;
  }
}

export function getOvertimeCalendarPage(params: PageParam) {
  return requestClient.get<PageResult<BpmOAOvertimeCalendarApi.Version>>(
    '/bpm/oa/overtime-calendar/page',
    { params },
  );
}

export function getOvertimeCalendar(id: number) {
  return requestClient.get<BpmOAOvertimeCalendarApi.Version>(
    `/bpm/oa/overtime-calendar/get?id=${id}`,
  );
}

export function verifyOvertimeCalendar(id: number, enable: boolean) {
  return requestClient.post<boolean>(
    `/bpm/oa/overtime-calendar/verify?id=${id}&enable=${enable}`,
  );
}

export function fetchOvertimeCalendar() {
  return requestClient.post<string>('/bpm/oa/overtime-calendar/fetch');
}

export function getActiveOvertimeCalendar(year: number) {
  return requestClient.get<BpmOAOvertimeCalendarApi.Version>(
    '/bpm/oa/overtime-calendar/active',
    { params: { year } },
  );
}
