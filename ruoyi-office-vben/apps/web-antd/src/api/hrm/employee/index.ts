import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace EmployeeArchiveApi {
  /** 员工档案 */
  export interface EmployeeArchive {
    id?: number; // 编号
    employeeNo?: string; // 员工编号
    name?: string; // 姓名
    sex?: number; // 性别（1:男 2:女）
    birthday?: string; // 出生日期 (YYYY-MM-DD)
    bloodType?: number; // 血型（1:A 2:B 3:AB 4:O）
    education?: string; // 文化程度
    nation?: string; // 民族
    jobTitle?: string; // 职称
    nativePlace?: string; // 籍贯
    height?: number; // 身高(cm)
    weight?: number; // 体重(kg)
    idCard?: string; // 身份证号码
    mobile?: string; // 手机号
    email?: string; // 邮箱
    householdAddress?: string; // 户籍所在地
    currentAddress?: string; // 现居住地址
    emergencyContact?: string; // 紧急联系人
    emergencyPhone?: string; // 联系电话
    emergencyRelationship?: string; // 紧急联系人关系
    socialSecurityEnabled?: boolean | null; // 是否缴纳社保
    housingFundEnabled?: boolean | null; // 是否缴纳公积金
    socialSecurityStartMonth?: string; // 参保年月 yyyy-MM
    probationSalary?: number; // 试用期薪资
    regularSalary?: number; // 转正薪资
    fertilityStatus?: string; // 生育状况
    householdType?: string; // 户籍性质
    employmentForm?: string; // 用工形式
    recruitmentChannel?: string; // 招聘渠道
    interviewerName?: string; // 面试人
    age?: number; // 年龄
    companyTenureMonths?: number; // 司龄月数
    marriageChildbearingSummary?: string;
    contractSignCount?: number;
    currentContractType?: string;
    currentContractStartDate?: string;
    currentContractEndDate?: string;
    avatar?: string; // 照片
    bankName?: string; // 工资开户行
    bankAccount?: string; // 工资卡账户
    jobPost?: string; // 职位
    jobPosition?: string; // 职务
    employeeStatus?: number; // 人员状态（员工类型）
    deptId?: number; // 所属部门
    deptName?: string; // 所属部门名称
    companyId?: number; // 所属公司ID
    companyName?: string; // 所属单位
    entryDate?: string; // 入职日期 (YYYY-MM-DD)
    formalDate?: string; // 转正日期 (YYYY-MM-DD)
    remark?: string; // 备注
    userId?: number; // 关联用户ID
    userGenerated?: boolean; // 是否已生成用户
    createTime?: Date | string; // 创建时间
    employmentList?: EmployeeEmployment[];
    workExperienceList?: EmployeeWorkExperience[]; // 工作经历列表
    educationList?: EmployeeEducation[]; // 教育经历列表
    familyList?: EmployeeFamily[]; // 家属信息列表
    contractList?: EmployeeContract[]; // 合同明细
    /** 入职资料：新附件只传 claimToken；已有附件传 id */
    onboardingAttachments?: OnboardingAttachment[];
  }

  export interface EmployeeEmployment {
    companyDeptId: number;
    companyName?: string;
    deptId?: number;
    deptName?: string;
    signed?: boolean;
  }

  export interface EmployeeContract {
    id?: number;
    sequenceNo: 1 | 2 | 3 | 4;
    contractType?: string;
    startDate: string;
    endDate?: string;
  }

  /** 入职资料附件（专用 VO，不自报 businessType/size/url） */
  export interface OnboardingAttachment {
    id?: number;
    /** 一次性 claim（新上传） */
    claimToken?: string;
    fileName?: string;
    fileSize?: number;
    fileExtension?: string;
    fileType?: string;
    sortOrder?: number;
    remark?: string;
    uploadTime?: string | Date;
    /** 鉴权下载相对路径 */
    downloadPath?: string;
  }

  /** 入职资料上传 claim 响应（无公开 URL/path/configId） */
  export interface OnboardingFileClaim {
    claimToken: string;
    fileName?: string;
    fileSize?: number;
    fileExtension?: string;
    expireTime?: number;
  }

  /** 员工工作经历 */
  export interface EmployeeWorkExperience {
    id?: number; // 编号
    startTime?: string; // 开始时间 (YYYY-MM-DD)
    endTime?: string; // 截止时间 (YYYY-MM-DD)
    jobPosition?: string; // 职务
    companyName?: string; // 单位名称
  }

  /** 员工教育经历 */
  export interface EmployeeEducation {
    id?: number; // 编号
    startTime?: string; // 开始时间 (YYYY-MM-DD)
    endTime?: string; // 截止时间 (YYYY-MM-DD)
    educationLevel?: string; // 学历
    educationType?: string; // 学历类别
    degree?: string; // 学位
    firstEducation?: boolean; // 第一学历
    highestEducation?: boolean; // 最高学历
    major?: string; // 专业
    schoolName?: string; // 学校名称
  }

  /** 员工家属信息 */
  export interface EmployeeFamily {
    id?: number; // 编号
    name?: string; // 姓名
    relationship?: string; // 关系
    mobile?: string; // 联系电话
    workUnit?: string; // 工作单位
  }

  /** 员工档案分页请求 */
  export interface EmployeeArchivePageReqVO extends PageParam {
    employeeNo?: string; // 员工编号
    name?: string; // 姓名
    deptId?: number; // 所属部门
    employeeStatus?: number; // 人员状态
    entryDate?: Date[]; // 入职日期
    createTime?: Date[]; // 创建时间
  }

  /** 员工选择分页请求（过滤正式员工） */
  export interface EmployeeArchiveSelectReqVO extends PageParam {
    employeeNo?: string;
    name?: string;
    deptId?: number;
    jobPost?: string;
    jobPosition?: string;
    employeeStatus?: number; // 额外筛选其他状态（后端会强制过滤正式）
    entryDate?: Date[];
    createTime?: Date[];
    /** 需要包含的人员状态集合（优先级高于excludeEmployeeStatusList），例如 [2, 3, 5] */
    includeEmployeeStatusList?: number[];
    /** 需要排除的人员状态集合，例如 [1, 3] */
    excludeEmployeeStatusList?: number[];
  }
}

/** 查询员工档案分页 */
export function getEmployeeArchivePage(
  params: EmployeeArchiveApi.EmployeeArchivePageReqVO,
) {
  return requestClient.get<PageResult<EmployeeArchiveApi.EmployeeArchive>>(
    '/hrm/employee-archive/page',
    { params },
  );
}

/** 员工档案选择分页（过滤正式员工） */
export function getEmployeeArchiveSelectPage(
  params: EmployeeArchiveApi.EmployeeArchiveSelectReqVO,
) {
  return requestClient.get<PageResult<EmployeeArchiveApi.EmployeeArchive>>(
    '/hrm/employee-archive/select-page',
    { params },
  );
}

export function getMyEmployments() {
  return requestClient.get<EmployeeArchiveApi.EmployeeEmployment[]>(
    '/hrm/employee-archive/my-employments',
  );
}

/** 查询员工档案详情 */
export function getEmployeeArchive(id: number) {
  return requestClient.get<EmployeeArchiveApi.EmployeeArchive>(
    `/hrm/employee-archive/get?id=${id}`,
  );
}

/** 新增员工档案 */
export function createEmployeeArchive(
  data: EmployeeArchiveApi.EmployeeArchive,
) {
  return requestClient.post<number>('/hrm/employee-archive/create', data);
}

/** 修改员工档案 */
export function updateEmployeeArchive(
  data: EmployeeArchiveApi.EmployeeArchive,
) {
  return requestClient.put<boolean>('/hrm/employee-archive/update', data);
}

/** 删除员工档案 */
export function deleteEmployeeArchive(id: number) {
  return requestClient.delete<boolean>(`/hrm/employee-archive/delete?id=${id}`);
}

/** 批量删除员工档案 */
export function deleteEmployeeArchiveList(ids: number[]) {
  return requestClient.delete<boolean>('/hrm/employee-archive/delete-list', {
    params: { ids: ids.join(',') },
  });
}

/** 导出员工档案 Excel */
export function exportEmployeeArchiveExcel(
  params: EmployeeArchiveApi.EmployeeArchivePageReqVO,
) {
  return requestClient.download('/hrm/employee-archive/export-excel', {
    params,
  });
}

/** 文枢花名册导入结果 */
export namespace EmployeeRosterImportApi {
  export interface ImportResult {
    createNames: string[];
    updateNames: string[];
    failureRows: Record<number, string>;
  }
}

/** 下载文枢花名册导入模板（官方附件表头） */
export function importEmployeeRosterTemplate() {
  return requestClient.download('/hrm/employee-archive/get-import-template');
}

/** 导入文枢花名册 Excel */
export function importEmployeeRoster(file: File) {
  return requestClient.upload<EmployeeRosterImportApi.ImportResult>(
    '/hrm/employee-archive/import',
    { file },
  );
}

/** 为员工生成系统用户 */
export function generateUserForEmployee(id: number) {
  return requestClient.post<number>(
    `/hrm/employee-archive/generate-user?id=${id}`,
  );
}

/** 批量为员工生成系统用户 */
export function batchGenerateUserForEmployee(ids: number[]) {
  return requestClient.post<boolean>(
    `/hrm/employee-archive/batch-generate-user?ids=${ids.join(',')}`,
  );
}

/** 上传入职资料：返回一次性 claimToken（不返回公开直链） */
export function uploadOnboardingFile(file: File) {
  return requestClient.upload<EmployeeArchiveApi.OnboardingFileClaim>(
    '/hrm/employee-archive/onboarding-file/upload',
    { file },
  );
}
