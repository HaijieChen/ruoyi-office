import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace BpmOAOutingApi {
  export interface Outing {
    id?: number;
    userId?: number;
    userNickname?: string;
    deptName?: string;
    reason: string;
    location: string;
    startTime: number;
    endTime: number;
    hours?: number;
    needOutput?: string;
    attachmentUrls?: string[];
    processInstanceId?: string;
    status?: number;
    attendanceSyncStatus?: string;
    createTime?: Date | number | string;
  }

  export interface OutingCreate {
    reason: string;
    location: string;
    startTime: number;
    endTime: number;
    needOutput?: string;
    attachmentUrls?: string[];
  }
}

/** 创建外出申请 */
export async function createOuting(data: BpmOAOutingApi.OutingCreate) {
  return requestClient.post<number>('/bpm/oa/outing/create', data);
}

/** 获得外出申请 */
export async function getOuting(id: number) {
  return requestClient.get<BpmOAOutingApi.Outing>(
    `/bpm/oa/outing/get?id=${id}`,
  );
}

/** 获得外出申请分页 */
export async function getOutingPage(params: PageParam) {
  return requestClient.get<PageResult<BpmOAOutingApi.Outing>>(
    '/bpm/oa/outing/page',
    { params },
  );
}
