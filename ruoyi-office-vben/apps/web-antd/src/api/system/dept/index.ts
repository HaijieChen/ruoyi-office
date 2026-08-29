import { requestClient } from '#/api/request';

export namespace SystemDeptApi {
  /** 部门信息 */
  export interface Dept {
    id?: number;
    name: string;
    parentId?: number;
    status: number;
    sort: number;
    leaderUserId: number;
    phone: string;
    email: string;
    orgType: string;
    /** 记账本位币（公司）：CNY/USD/HKD */
    functionalCurrency?: string;
    createTime: Date;
    children?: Dept[];
  }
}

/** 查询部门（精简)列表 */
export async function getSimpleDeptList(options?: { hideErrorMessage?: boolean }) {
  return requestClient.get<SystemDeptApi.Dept[]>('/system/dept/simple-list', {
    hideErrorMessage: options?.hideErrorMessage,
  });
}

/** 查询部门列表 */
export async function getDeptList() {
  return requestClient.get('/system/dept/list');
}

/** 查询公司列表（需 system:dept:query） */
export async function getCompanyList() {
  return requestClient.get('/system/dept/company-list');
}

/** 启用公司精简列表（下拉，无额外权限） */
export async function getSimpleCompanyList() {
  return requestClient.get<SystemDeptApi.Dept[]>(
    '/system/dept/company-simple-list',
  );
}

/** 查询部门详情 */
export async function getDept(id: number) {
  return requestClient.get<SystemDeptApi.Dept>(`/system/dept/get?id=${id}`);
}

/** 新增部门 */
export async function createDept(data: SystemDeptApi.Dept) {
  return requestClient.post('/system/dept/create', data);
}

/** 修改部门 */
export async function updateDept(data: SystemDeptApi.Dept) {
  return requestClient.put('/system/dept/update', data);
}

/** 删除部门 */
export async function deleteDept(id: number) {
  return requestClient.delete(`/system/dept/delete?id=${id}`);
}

/** 批量删除部门 */
export async function deleteDeptList(ids: number[]) {
  return requestClient.delete(`/system/dept/delete-list?ids=${ids.join(',')}`);
}

/** 组织架构导入 */
export namespace SystemDeptImportApi {
  export interface ImportError {
    rowNumber: number;
    orgPath?: string;
    field?: string;
    code: string;
    message: string;
  }

  export interface ImportResult {
    fileDigest: string;
    totalRows: number;
    createCount: number;
    skipCount: number;
    updateCount?: number;
    canCommit: boolean;
    errors: ImportError[];
  }
}

/** 下载组织架构导入模板 */
export function getDeptImportTemplate() {
  return requestClient.download('/system/dept/get-import-template');
}

/** 校验组织架构导入文件（只读预览） */
export function validateDeptImport(file: File) {
  return requestClient.upload<SystemDeptImportApi.ImportResult>(
    '/system/dept/import/validate',
    { file },
  );
}

/** 提交组织架构导入（整批原子） */
export function importDept(file: File, expectedDigest: string) {
  return requestClient.upload<SystemDeptImportApi.ImportResult>(
    '/system/dept/import',
    { file, expectedDigest },
  );
}
