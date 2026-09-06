import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace BpmOAPunchApi {
  export interface Punch {
    id?: number;
    userId?: number;
    userNickname?: string;
    deptName?: string;
    punchDate: string;
    punchTime: number;
    reason: string;
    attachmentUrls?: string[];
    processInstanceId?: string;
    status?: number;
    attendanceSyncStatus?: string;
    createTime?: Date | number | string;
  }

  export interface PunchCreate {
    punchDate: string;
    punchTime: number;
    reason: string;
    attachmentUrls?: string[];
    startCompanyDeptId?: number;
  }
}

/** 创建补卡申请 */
export async function createPunch(data: BpmOAPunchApi.PunchCreate) {
  return requestClient.post<number>('/bpm/oa/punch-correction/create', data);
}

/** 获得补卡申请 */
export async function getPunch(id: number) {
  return requestClient.get<BpmOAPunchApi.Punch>(
    `/bpm/oa/punch-correction/get?id=${id}`,
  );
}

/** 获得补卡申请分页 */
export async function getPunchPage(params: PageParam) {
  return requestClient.get<PageResult<BpmOAPunchApi.Punch>>(
    '/bpm/oa/punch-correction/page',
    { params },
  );
}

/** 指定补卡日期所在月的剩余次数 */
export async function getPunchRemaining(punchDate: string) {
  return requestClient.get<number>('/bpm/oa/punch-correction/remaining', {
    params: { punchDate },
  });
}
