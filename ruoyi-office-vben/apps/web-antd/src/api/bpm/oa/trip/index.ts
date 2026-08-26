import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace BpmOATripApi {
  export interface Trip {
    id?: number;
    userId?: number;
    userNickname?: string;
    deptName?: string;
    status?: number;
    type?: number;
    bizType?: number;
    destination?: string;
    originCity?: string;
    transport?: string;
    hotelBooking?: string;
    partyName?: string;
    address?: string;
    contactInfo?: string;
    needOutput?: string;
    hasCarriageFee?: string;
    remark?: string;
    attachmentUrls?: string[];
    reason?: string;
    companionUserId?: number;
    companionNickname?: string;
    companionUserIds?: number[];
    companionNicknames?: string[];
    startTime: number;
    endTime: number;
    hours?: number;
    processInstanceId?: string;
    createTime?: Date | number;
    attendanceSyncStatus?: string;
  }

  export interface TripCreate {
    bizType: number;
    originCity: string;
    destination: string;
    reason: string;
    transport: string;
    hotelBooking?: string;
    partyName?: string;
    address?: string;
    contactInfo?: string;
    needOutput?: string;
    hasCarriageFee?: string;
    remark?: string;
    attachmentUrls: string[];
    companionUserId?: number;
    companionUserIds: number[];
    startTime: number;
    endTime: number;
    startCompanyDeptId?: number;
  }
}

/** 创建出差申请 */
export async function createTrip(data: BpmOATripApi.TripCreate) {
  return requestClient.post('/bpm/oa/trip/create', data);
}

/** 获得出差申请 */
export async function getTrip(id: number) {
  return requestClient.get<BpmOATripApi.Trip>(`/bpm/oa/trip/get?id=${id}`);
}

/** 获得出差申请分页 */
export async function getTripPage(params: PageParam) {
  return requestClient.get<PageResult<BpmOATripApi.Trip>>(
    '/bpm/oa/trip/page',
    { params },
  );
}
