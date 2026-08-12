package cn.iocoder.yudao.module.hrm.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * HRM 错误码枚举类
 * <p>
 * HRM 系统，使用 1-050-000-000 段
 */
public interface ErrorCodeConstants {

    // ========== 员工档案 1-050-001-000 ==========
    ErrorCode EMPLOYEE_ARCHIVE_NOT_EXISTS = new ErrorCode(1_050_001_001, "员工档案不存在");
    ErrorCode EMPLOYEE_ROSTER_CONTRACT_LIMIT = new ErrorCode(1_050_001_002, "最多维护四次合同");
    ErrorCode EMPLOYEE_ROSTER_CONTRACT_SEQUENCE = new ErrorCode(1_050_001_003, "合同序号必须从1连续递增且不重复");
    ErrorCode EMPLOYEE_ROSTER_CONTRACT_DATE = new ErrorCode(1_050_001_004, "合同结束日期不能早于开始日期");
    ErrorCode EMPLOYEE_ROSTER_EDUCATION_ROLE = new ErrorCode(1_050_001_005, "第一学历与最高学历各至多一条");
    ErrorCode EMPLOYEE_ROSTER_SOCIAL_SECURITY_MONTH = new ErrorCode(1_050_001_006, "缴纳社保时必须填写参保年月");
    ErrorCode EMPLOYEE_ROSTER_SOCIAL_SECURITY_MONTH_FORMAT = new ErrorCode(1_050_001_007, "参保年月格式必须为yyyy-MM");
    ErrorCode EMPLOYEE_ROSTER_SALARY_NEGATIVE = new ErrorCode(1_050_001_008, "薪资不能为负数");
    ErrorCode EMPLOYEE_ROSTER_CONTRACT_START_REQUIRED = new ErrorCode(1_050_001_009, "合同开始日期不能为空");
    ErrorCode EMPLOYEE_ROSTER_ATTACHMENT_LIMIT = new ErrorCode(1_050_001_010, "入职资料最多 10 份");
    ErrorCode EMPLOYEE_ROSTER_ATTACHMENT_INVALID = new ErrorCode(1_050_001_011, "入职资料附件不合法");
    ErrorCode EMPLOYEE_ARCHIVE_USER_ALREADY_GENERATED = new ErrorCode(1_050_001_012, "该员工已生成用户，无需重复生成");

    // ========== 员工入职申请单 1-050-002-000 ==========
    ErrorCode EMPLOYEE_ENTRY_BILL_NOT_EXISTS = new ErrorCode(1_050_002_001, "员工入职申请单不存在");
    ErrorCode EMPLOYEE_ENTRY_BILL_MOBILE_EXISTS = new ErrorCode(1_050_002_002, "手机号已存在，无法重复录入");
    ErrorCode EMPLOYEE_ENTRY_BILL_ID_CARD_EXISTS = new ErrorCode(1_050_002_003, "身份证号已存在，无法重复录入");

    // ========== 员工转正申请单 1-050-003-000 ==========
    ErrorCode EMPLOYEE_REGULAR_BILL_NOT_EXISTS = new ErrorCode(1_050_003_001, "员工转正申请单不存在");

    // ========== 人事调动申请单 1-050-004-000 ==========
    ErrorCode EMPLOYEE_TRANSFER_BILL_NOT_EXISTS = new ErrorCode(1_050_004_001, "人事调动申请单不存在");

    // ========== 员工离职申请单 1-050-005-000 ==========
    ErrorCode EMPLOYEE_RESIGNATION_BILL_NOT_EXISTS = new ErrorCode(1_050_005_001, "员工离职申请单不存在");

}

