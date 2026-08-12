package cn.iocoder.yudao.module.hrm.controller.admin.employee.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 文枢在职花名册导出 VO（列顺序严格对应模板 1-52）
 */
@Data
public class EmployeeRosterExportVO {

    @ExcelProperty("序号")
    private Integer sequenceNo;

    @ExcelProperty("是否缴纳社保")
    private String socialSecurityEnabled;

    @ExcelProperty("是否缴纳公积金")
    private String housingFundEnabled;

    @ExcelProperty("单位名称")
    private String companyName;

    @ExcelProperty("部门")
    private String deptName;

    @ExcelProperty("职位")
    private String jobPost;

    @ExcelProperty("姓名")
    private String name;

    @ExcelProperty("入职日期")
    private String entryDate;

    @ExcelProperty("转正日期")
    private String formalDate;

    @ExcelProperty("试用期薪资")
    private BigDecimal probationSalary;

    @ExcelProperty("转正薪资")
    private BigDecimal regularSalary;

    @ExcelProperty("参保年月")
    private String socialSecurityStartMonth;

    @ExcelProperty("身份证号")
    private String idCard;

    @ExcelProperty("手机")
    private String mobile;

    @ExcelProperty("性别")
    private String sex;

    @ExcelProperty("民族")
    private String nation;

    @ExcelProperty("婚育情况")
    private String marriageChildbearingSummary;

    @ExcelProperty("户籍性质")
    private String householdType;

    @ExcelProperty("籍贯")
    private String nativePlace;

    @ExcelProperty("出生年月")
    private String birthdayMonth;

    @ExcelProperty("年龄")
    private Integer age;

    @ExcelProperty("司龄")
    private String companyTenure;

    @ExcelProperty("最高学历")
    private String highestEducation;

    @ExcelProperty("学历类别")
    private String educationType;

    @ExcelProperty("学位")
    private String highestDegree;

    @ExcelProperty("第一学历")
    private String firstEducation;

    @ExcelProperty("学位")
    private String firstDegree;

    @ExcelProperty("最高学历毕业学校")
    private String highestSchoolName;

    @ExcelProperty("最高学历专业")
    private String highestMajor;

    @ExcelProperty("最高学历毕业时间")
    private String highestGraduateDate;

    @ExcelProperty("第一学历毕业学校")
    private String firstSchoolName;

    @ExcelProperty("第一学历专业")
    private String firstMajor;

    @ExcelProperty("第一学历毕业时间")
    private String firstGraduateDate;

    @ExcelProperty("户籍地址")
    private String householdAddress;

    @ExcelProperty("现居住地")
    private String currentAddress;

    @ExcelProperty("员工类型")
    private String employeeType;

    @ExcelProperty("用工形式")
    private String employmentForm;

    @ExcelProperty("合同类型")
    private String currentContractType;

    @ExcelProperty("合同签订次数")
    private Integer contractSignCount;

    @ExcelProperty("目前合同签订日期（起）")
    private String currentContractStartDate;

    @ExcelProperty("目前合同到期日（止）")
    private String currentContractEndDate;

    @ExcelProperty("银行卡号")
    private String bankAccount;

    @ExcelProperty("开户行")
    private String bankName;

    @ExcelProperty("紧急联系人/关系")
    private String emergencyContactRelation;

    @ExcelProperty("紧急联系人方式")
    private String emergencyPhone;

    @ExcelProperty("一次合同起止日期")
    private String contract1Range;

    @ExcelProperty("二次合同起止日期")
    private String contract2Range;

    @ExcelProperty("三次合同起止日期")
    private String contract3Range;

    @ExcelProperty("四次合同起止日期")
    private String contract4Range;

    @ExcelProperty("招聘渠道")
    private String recruitmentChannel;

    @ExcelProperty("面试人")
    private String interviewerName;

    @ExcelProperty("入职资料")
    private String onboardingAttachmentStatus;

}
