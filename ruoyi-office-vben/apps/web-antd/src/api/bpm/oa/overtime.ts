import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace BpmOAOvertimeApi {
  export interface Overtime {
    id?: number;
    userId?: number;
    userNickname?: string;
    deptName?: string;
    reason: string;
    startTime: number;
    endTime: number;
    hours?: number;
    holiday?: string;
    attachmentUrls?: string[];
    processInstanceId?: string;
    status?: number;
    attendanceSyncStatus?: string;
    createTime?: Date | number | string;
  }

  export interface OvertimeCreate {
    reason: string;
    startTime: number;
    endTime: number;
    holiday: string;
    attachmentUrls?: string[];
    startCompanyDeptId?: number;
  }
}

/** 创建加班申请（不提交时长，由服务端重算） */
export async function createOvertime(data: BpmOAOvertimeApi.OvertimeCreate) {
  return requestClient.post<number>('/bpm/oa/overtime/create', data);
}

/** 获得加班申请 */
export async function getOvertime(id: number) {
  return requestClient.get<BpmOAOvertimeApi.Overtime>(
    `/bpm/oa/overtime/get?id=${id}`,
  );
}

/** 获得加班申请分页 */
export async function getOvertimePage(params: PageParam) {
  return requestClient.get<PageResult<BpmOAOvertimeApi.Overtime>>(
    '/bpm/oa/overtime/page',
    { params },
  );
}
