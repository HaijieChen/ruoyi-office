package cn.iocoder.yudao.module.hrm.controller.admin.employee.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 文枢花名册导入 VO。
 * <p>
 * 列 title 唯一权威来源：附件「文枢花名册模版.xlsx」Sheet「文枢在职」第 2 行（52 列）。
 * 使用 index 绑定，避免「学位」重复表头与含换行列错位。
 * 勿与 {@link EmployeeRosterExportVO} 的简化导出文案混用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeRosterImportExcelVO {

    /** 附件第 2 行 title 原样列表（含换行与长 title），供单测对照 */
    public static final String[] TEMPLATE_HEADERS = {
            "序号",
            "是否缴纳社保",
            "是否缴纳公积金",
            "单位名称",
            "部门",
            "职位",
            "姓名",
            "入职日期",
            "转正日期",
            "试用期薪资",
            "转正薪资",
            "参保年月",
            "身份证号",
            "手机",
            "性别",
            "民族",
            "婚育情况",
            "户籍性质",
            "籍贯",
            "出生年月",
            "年龄",
            "司龄",
            "最高学历",
            "学历类别",
            "学位",
            "第一学历",
            "学位",
            "最高学历\n毕业学校",
            "最高学历专业",
            "最高学历毕业时间",
            "第一学历\n毕业学校",
            "第一学历专业",
            "第一学历毕业时间",
            "户籍地址",
            "现居住地",
            "员工类型",
            "用工形式",
            "合同类型",
            "合同签订次数",
            "目前合同签订日期（起）",
            "目前合同到期日（止）",
            "银行卡号",
            "开户行",
            "紧急联系人/关系",
            "紧急联系人方式",
            "一次合同起止日期",
            "二次合同起止日期",
            "三次合同起止日期",
            "四次合同起止日期",
            "招聘渠道",
            "面试人",
            "入职资料（系统里可以标注一个上传附件的地方，我们可以扫描上传入职资料）"
    };

    @ExcelProperty(value = "序号", index = 0)
    private Integer sequenceNo;

    @ExcelProperty(value = "是否缴纳社保", index = 1)
    private String socialSecurityEnabled;

    @ExcelProperty(value = "是否缴纳公积金", index = 2)
    private String housingFundEnabled;

    @ExcelProperty(value = "单位名称", index = 3)
    private String companyName;

    @ExcelProperty(value = "部门", index = 4)
    private String deptName;

    @ExcelProperty(value = "职位", index = 5)
    private String jobPost;

    @ExcelProperty(value = "姓名", index = 6)
    private String name;

    @ExcelProperty(value = "入职日期", index = 7)
    private String entryDate;

    @ExcelProperty(value = "转正日期", index = 8)
    private String formalDate;

    @ExcelProperty(value = "试用期薪资", index = 9)
    private BigDecimal probationSalary;

    @ExcelProperty(value = "转正薪资", index = 10)
    private BigDecimal regularSalary;

    @ExcelProperty(value = "参保年月", index = 11)
    private String socialSecurityStartMonth;

    @ExcelProperty(value = "身份证号", index = 12)
    private String idCard;

    @ExcelProperty(value = "手机", index = 13)
    private String mobile;

    @ExcelProperty(value = "性别", index = 14)
    private String sex;

    @ExcelProperty(value = "民族", index = 15)
    private String nation;

    @ExcelProperty(value = "婚育情况", index = 16)
    private String marriageChildbearingSummary;

    @ExcelProperty(value = "户籍性质", index = 17)
    private String householdType;

    @ExcelProperty(value = "籍贯", index = 18)
    private String nativePlace;

    @ExcelProperty(value = "出生年月", index = 19)
    private String birthdayMonth;

    @ExcelProperty(value = "年龄", index = 20)
    private Integer age;

    @ExcelProperty(value = "司龄", index = 21)
    private String companyTenure;

    @ExcelProperty(value = "最高学历", index = 22)
    private String highestEducation;

    @ExcelProperty(value = "学历类别", index = 23)
    private String educationType;

    @ExcelProperty(value = "学位", index = 24)
    private String highestDegree;

    @ExcelProperty(value = "第一学历", index = 25)
    private String firstEducation;

    @ExcelProperty(value = "学位", index = 26)
    private String firstDegree;

    @ExcelProperty(value = "最高学历\n毕业学校", index = 27)
    private String highestSchoolName;

    @ExcelProperty(value = "最高学历专业", index = 28)
    private String highestMajor;

    @ExcelProperty(value = "最高学历毕业时间", index = 29)
    private String highestGraduateDate;

    @ExcelProperty(value = "第一学历\n毕业学校", index = 30)
    private String firstSchoolName;

    @ExcelProperty(value = "第一学历专业", index = 31)
    private String firstMajor;

    @ExcelProperty(value = "第一学历毕业时间", index = 32)
    private String firstGraduateDate;

    @ExcelProperty(value = "户籍地址", index = 33)
    private String householdAddress;

    @ExcelProperty(value = "现居住地", index = 34)
    private String currentAddress;

    @ExcelProperty(value = "员工类型", index = 35)
    private String employeeType;

    @ExcelProperty(value = "用工形式", index = 36)
    private String employmentForm;

    @ExcelProperty(value = "合同类型", index = 37)
    private String currentContractType;

    @ExcelProperty(value = "合同签订次数", index = 38)
    private String contractSignCount;

    @ExcelProperty(value = "目前合同签订日期（起）", index = 39)
    private String currentContractStartDate;

    @ExcelProperty(value = "目前合同到期日（止）", index = 40)
    private String currentContractEndDate;

    @ExcelProperty(value = "银行卡号", index = 41)
    private String bankAccount;

    @ExcelProperty(value = "开户行", index = 42)
    private String bankName;

    @ExcelProperty(value = "紧急联系人/关系", index = 43)
    private String emergencyContactRelation;

    @ExcelProperty(value = "紧急联系人方式", index = 44)
    private String emergencyPhone;

    @ExcelProperty(value = "一次合同起止日期", index = 45)
    private String contract1Range;

    @ExcelProperty(value = "二次合同起止日期", index = 46)
    private String contract2Range;

    @ExcelProperty(value = "三次合同起止日期", index = 47)
    private String contract3Range;

    @ExcelProperty(value = "四次合同起止日期", index = 48)
    private String contract4Range;

    @ExcelProperty(value = "招聘渠道", index = 49)
    private String recruitmentChannel;

    @ExcelProperty(value = "面试人", index = 50)
    private String interviewerName;

    @ExcelProperty(value = "入职资料（系统里可以标注一个上传附件的地方，我们可以扫描上传入职资料）", index = 51)
    private String onboardingAttachmentStatus;

    /** 官方 52 列之后：多家任职用英文/中文逗号分隔，与任职部门按序成对 */
    @ExcelProperty(value = "任职单位", index = 52)
    private String extraCompanyNames;

    @ExcelProperty(value = "任职部门", index = 53)
    private String extraDeptNames;

}
