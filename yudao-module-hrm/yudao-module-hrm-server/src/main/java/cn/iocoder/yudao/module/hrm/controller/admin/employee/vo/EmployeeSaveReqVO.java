package cn.iocoder.yudao.module.hrm.controller.admin.employee.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import jakarta.validation.Valid;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;

@Schema(description = "管理后台 - 员工档案保存 Request VO")
@Data
public class EmployeeSaveReqVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "员工编号", example = "10000000")
    private String employeeNo;

    /** 花名册导入：空白工号保持空，不走页面新建的自动生成 */
    @JsonIgnore
    private boolean skipAutoEmployeeNo;

    @Schema(description = "姓名", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三")
    @NotBlank(message = "姓名不能为空")
    private String name;

    @Schema(description = "性别（1:男 2:女）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "性别不能为空")
    private Integer sex;

    @Schema(description = "出生日期", example = "2000-01-01")
    @JsonFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate birthday;

    @Schema(description = "血型（1:A 2:B 3:AB 4:O）", example = "1")
    private Integer bloodType;

    @Schema(description = "文化程度", example = "本科")
    private String education;

    @Schema(description = "民族", example = "汉族")
    private String nation;

    @Schema(description = "职称", example = "高级工程师")
    private String jobTitle;

    @Schema(description = "籍贯", example = "北京市海淀区")
    private String nativePlace;

    @Schema(description = "身高(cm)", example = "175")
    private BigDecimal height;

    @Schema(description = "体重(kg)", example = "65")
    private BigDecimal weight;

    @Schema(description = "身份证号码", example = "110101199001011234")
    private String idCard;

    @Schema(description = "手机号", requiredMode = Schema.RequiredMode.REQUIRED, example = "13800138000")
    @NotBlank(message = "手机号不能为空")
    private String mobile;

    @Schema(description = "邮箱", example = "zhangsan@example.com")
    private String email;

    @Schema(description = "政治面貌", example = "中共党员")
    private String politicalStatus;

    @Schema(description = "婚姻状况", example = "已婚")
    private String maritalStatus;

    @Schema(description = "户籍所在地", example = "北京市海淀区中关村大街1号")
    private String householdAddress;

    @Schema(description = "现居住地址", example = "北京市朝阳区建国路1号")
    private String currentAddress;

    @Schema(description = "紧急联系人", example = "李四")
    private String emergencyContact;

    @Schema(description = "联系电话", example = "13900139000")
    private String emergencyPhone;

    /**
     * 花名册可空字段：JSON 省略=保留；显式 null=清空；非空=写入。
     * 通过自定义 setter 记录字段是否出现在请求中。
     */
    @Schema(description = "紧急联系人关系", example = "配偶")
    @Getter
    @Setter(AccessLevel.NONE)
    private String emergencyRelationship;
    @JsonIgnore
    private boolean emergencyRelationshipPresent;

    @Schema(description = "是否缴纳社保", example = "true")
    @Getter
    @Setter(AccessLevel.NONE)
    private Boolean socialSecurityEnabled;
    @JsonIgnore
    private boolean socialSecurityEnabledPresent;

    @Schema(description = "是否缴纳公积金", example = "true")
    @Getter
    @Setter(AccessLevel.NONE)
    private Boolean housingFundEnabled;
    @JsonIgnore
    private boolean housingFundEnabledPresent;

    @Schema(description = "参保年月 yyyy-MM", example = "2024-01")
    @Getter
    @Setter(AccessLevel.NONE)
    private String socialSecurityStartMonth;
    @JsonIgnore
    private boolean socialSecurityStartMonthPresent;

    @Schema(description = "试用期薪资", example = "8000.00")
    @Getter
    @Setter(AccessLevel.NONE)
    private BigDecimal probationSalary;
    @JsonIgnore
    private boolean probationSalaryPresent;

    @Schema(description = "转正薪资", example = "10000.00")
    @Getter
    @Setter(AccessLevel.NONE)
    private BigDecimal regularSalary;
    @JsonIgnore
    private boolean regularSalaryPresent;

    @Schema(description = "生育状况", example = "1")
    @Getter
    @Setter(AccessLevel.NONE)
    private String fertilityStatus;
    @JsonIgnore
    private boolean fertilityStatusPresent;

    @Schema(description = "户籍性质", example = "1")
    @Getter
    @Setter(AccessLevel.NONE)
    private String householdType;
    @JsonIgnore
    private boolean householdTypePresent;

    @Schema(description = "用工形式", example = "1")
    @Getter
    @Setter(AccessLevel.NONE)
    private String employmentForm;
    @JsonIgnore
    private boolean employmentFormPresent;

    @Schema(description = "招聘渠道", example = "内推")
    @Getter
    @Setter(AccessLevel.NONE)
    private String recruitmentChannel;
    @JsonIgnore
    private boolean recruitmentChannelPresent;

    @Schema(description = "面试人", example = "王五")
    @Getter
    @Setter(AccessLevel.NONE)
    private String interviewerName;
    @JsonIgnore
    private boolean interviewerNamePresent;

    @JsonProperty("emergencyRelationship")
    public void setEmergencyRelationship(String emergencyRelationship) {
        this.emergencyRelationship = emergencyRelationship;
        this.emergencyRelationshipPresent = true;
    }

    @JsonProperty("socialSecurityEnabled")
    public void setSocialSecurityEnabled(Boolean socialSecurityEnabled) {
        this.socialSecurityEnabled = socialSecurityEnabled;
        this.socialSecurityEnabledPresent = true;
    }

    @JsonProperty("housingFundEnabled")
    public void setHousingFundEnabled(Boolean housingFundEnabled) {
        this.housingFundEnabled = housingFundEnabled;
        this.housingFundEnabledPresent = true;
    }

    @JsonProperty("socialSecurityStartMonth")
    public void setSocialSecurityStartMonth(String socialSecurityStartMonth) {
        this.socialSecurityStartMonth = socialSecurityStartMonth;
        this.socialSecurityStartMonthPresent = true;
    }

    @JsonProperty("probationSalary")
    public void setProbationSalary(BigDecimal probationSalary) {
        this.probationSalary = probationSalary;
        this.probationSalaryPresent = true;
    }

    @JsonProperty("regularSalary")
    public void setRegularSalary(BigDecimal regularSalary) {
        this.regularSalary = regularSalary;
        this.regularSalaryPresent = true;
    }

    @JsonProperty("fertilityStatus")
    public void setFertilityStatus(String fertilityStatus) {
        this.fertilityStatus = fertilityStatus;
        this.fertilityStatusPresent = true;
    }

    @JsonProperty("householdType")
    public void setHouseholdType(String householdType) {
        this.householdType = householdType;
        this.householdTypePresent = true;
    }

    @JsonProperty("employmentForm")
    public void setEmploymentForm(String employmentForm) {
        this.employmentForm = employmentForm;
        this.employmentFormPresent = true;
    }

    @JsonProperty("recruitmentChannel")
    public void setRecruitmentChannel(String recruitmentChannel) {
        this.recruitmentChannel = recruitmentChannel;
        this.recruitmentChannelPresent = true;
    }

    @JsonProperty("interviewerName")
    public void setInterviewerName(String interviewerName) {
        this.interviewerName = interviewerName;
        this.interviewerNamePresent = true;
    }

    @Schema(description = "照片", example = "http://127.0.0.1:48080/admin-api/infra/file/4/get/xxx.jpg")
    private String avatar;

    @Schema(description = "工资开户行", example = "中国工商银行北京分行")
    private String bankName;

    @Schema(description = "工资卡账户", example = "6222021234567890123")
    private String bankAccount;

    @Schema(description = "职位", example = "产品经理")
    private String jobPost;

    @Schema(description = "职务", example = "部门经理")
    private String jobPosition;

    @Schema(description = "人员状态（1:正式 2:试用期 3:实习生 4:兼职 5:零时工）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "人员状态不能为空")
    private Integer employeeStatus;

    @Schema(description = "所属部门", example = "1")
    private Long deptId;

    @Schema(description = "所属部门名称", example = "研发部")
    private String deptName;

    @Schema(description = "所属公司ID", example = "1")
    private Long companyId;

    @Schema(description = "所属单位", example = "北京创星科技发展有限公司")
    private String companyName;

    @Schema(description = "入职日期", example = "2023-01-01")
    @JsonFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate entryDate;

    @Schema(description = "转正日期", example = "2023-04-01")
    @JsonFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate formalDate;

    @Schema(description = "备注", example = "优秀员工")
    private String remark;

    /**
     * 工作经历列表
     */
    @Schema(description = "任职公司列表")
    private List<EmployeeEmploymentVO> employmentList;

    @Schema(description = "工作经历列表")
    private List<EmployeeWorkExperienceVO> workExperienceList;

    /**
     * 教育经历列表
     */
    @Schema(description = "教育经历列表")
    private List<EmployeeEducationVO> educationList;

    /**
     * 家属信息列表
     */
    @Schema(description = "家属信息列表")
    private List<EmployeeFamilyVO> familyList;

    /**
     * 合同明细列表（最多 4 条）。null=不修改；[]=清空。
     */
    @Schema(description = "合同明细列表；null 表示不修改，空数组表示清空")
    @Valid
    private List<EmployeeContractVO> contractList;

    /**
     * 入职资料附件。null=不修改；[]=清空。
     * 专用 VO：新附件只传 fileId（权威 claim），不传 businessType/businessId/伪造 size。
     */
    @Schema(description = "入职资料附件；null 表示不修改，空数组表示清空")
    @Valid
    private List<OnboardingAttachmentSaveReqVO> onboardingAttachments;

}

