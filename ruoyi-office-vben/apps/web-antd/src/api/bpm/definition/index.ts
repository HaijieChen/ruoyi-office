import type { PageParam, PageResult } from '@vben/request';

import type { BpmModelApi } from '#/api/bpm/model';

import { requestClient } from '#/api/request';

export namespace BpmProcessDefinitionApi {
  /** 流程定义 */
  export interface ProcessDefinition {
    id: string;
    key?: string;
    version: number;
    name: string;
    category: string;
    categoryName?: string;
    description: string;
    deploymentTime: number;
    suspensionState: number;
    modelType: number;
    modelId: string;
    formType?: number;
    formId?: number;
    formName?: string;
    formCustomCreatePath?: string;
    bpmnXml?: string;
    simpleModel?: string;
    formFields?: string[];
    icon?: string;
    startUsers?: BpmModelApi.UserInfo[];
    /** 后端权威：当前用户是否可发起（列表仅返回可发起项；深链/详情用） */
    canStart?: boolean;
    /** 嵌入式流程要求的业务权限；前端勿硬编码权限真相 */
    requiredStartPermission?: string;
    /** 不可发起时的友好原因，直接展示，勿再弹通用 403 */
    cannotStartReason?: string;
  }
}

/** 查询流程定义 */
export async function getProcessDefinition(id?: string, key?: string) {
  return requestClient.get<BpmProcessDefinitionApi.ProcessDefinition>(
    '/bpm/process-definition/get',
    {
      params: { id, key },
    },
  );
}

/** 分页查询流程定义 */
export async function getProcessDefinitionPage(params: PageParam) {
  return requestClient.get<
    PageResult<BpmProcessDefinitionApi.ProcessDefinition>
  >('/bpm/process-definition/page', { params });
}

/** 查询流程定义列表 */
export async function getProcessDefinitionList(params: any) {
  return requestClient.get<BpmProcessDefinitionApi.ProcessDefinition[]>(
    '/bpm/process-definition/list',
    {
      params,
    },
  );
}

export interface AllowedEmployment {
  deptId?: number;
  companyDeptId?: number;
  signed?: boolean;
  companyName?: string;
  deptName?: string;
}

export function getAllowedEmployments(id: string) {
  return requestClient.get<AllowedEmployment[]>(
    '/bpm/process-definition/allowed-employments',
    { params: { id } },
  );
}

/** 查询流程定义列表（简单列表） */
export async function getSimpleProcessDefinitionList(category?: string) {
  return requestClient.get<
    PageResult<BpmProcessDefinitionApi.ProcessDefinition>
  >('/bpm/process-definition/simple-list', {
    params: category ? { category } : {},
  });
}
