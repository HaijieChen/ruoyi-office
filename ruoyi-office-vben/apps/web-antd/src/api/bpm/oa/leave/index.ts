import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace BpmOALeaveApi {
  export interface Leave {
    id: number;
    userId: number;
    status: number;
    type: number;
    reason: string;
    processInstanceId: string;
    startTime: number;
    endTime: number;
    createTime: Date;
    attachmentUrls?: string[];
    startUserSelectAssignees?: Record<string, string[]>;
  }
}

/** 创建请假申请 */
export async function createLeave(data: BpmOALeaveApi.Leave) {
  return requestClient.post('/bpm/oa/leave/create', data);
}

/** 更新请假申请 */
export async function updateLeave(data: BpmOALeaveApi.Leave) {
  return requestClient.post('/bpm/oa/leave/update', data);
}

/** 获得请假申请 */
export async function getLeave(id: number) {
  return requestClient.get<BpmOALeaveApi.Leave>(`/bpm/oa/leave/get?id=${id}`);
}

/** 获得请假申请分页 */
export async function getLeavePage(params: PageParam) {
  return requestClient.get<PageResult<BpmOALeaveApi.Leave>>(
    '/bpm/oa/leave/page',
    { params },
  );
}

/** 按组织数据范围查询请假报表 */
export async function getLeaveReportPage(params: PageParam) {
  return requestClient.get<PageResult<BpmOALeaveApi.Leave>>(
    '/bpm/oa/leave/report-page',
    { params },
  );
}
