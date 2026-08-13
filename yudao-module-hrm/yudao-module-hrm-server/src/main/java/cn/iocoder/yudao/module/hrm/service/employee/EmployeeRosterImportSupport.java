package cn.iocoder.yudao.module.hrm.service.employee;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.dict.core.DictFrameworkUtils;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.EmployeeContractVO;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.EmployeeEducationVO;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.EmployeeRosterImportExcelVO;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.EmployeeSaveReqVO;
import cn.iocoder.yudao.module.hrm.enums.EmployeeStatusEnum;
import cn.iocoder.yudao.module.system.enums.DictTypeConstants;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文枢花名册 Excel 行 → {@link EmployeeSaveReqVO} 映射（纯函数，便于单测）。
 */
final class EmployeeRosterImportSupport {

    private static final String DICT_NATION = "hrm_nation";
    private static final String DICT_MARITAL = "hrm_marital_status";
    private static final String DICT_FERTILITY = "hrm_fertility_status";
    private static final String DICT_HOUSEHOLD = "hrm_household_type";
    private static final String DICT_EDUCATION = "hrm_education";
    private static final String DICT_EDUCATION_TYPE = "hrm_education_type";
    private static final String DICT_EMPLOYEE_STATUS = "hrm_employee_status";
    private static final String DICT_EMPLOYMENT_FORM = "hrm_employment_form";
    private static final String DICT_CONTRACT_TYPE = "hrm_contract_type";

    /** 带分隔符的起止区间，如 2021/09/03-2024/08/31、2021年9月3日-无固定期限 */
    private static final Pattern DATE_RANGE = Pattern.compile(
            "^\\s*(\\d{4}[-/.年]\\d{1,2}[-/.月]?\\d{0,2}日?)\\s*[-~至到]+\\s*(.+?)\\s*$");
    /** 紧凑 8 位日起止区间，如 20210903-无固定期限、20210903-20240831 */
    private static final Pattern DATE_RANGE_COMPACT = Pattern.compile(
            "^\\s*(\\d{8})\\s*[-~至到]+\\s*(.+?)\\s*$");

    /** 日期字段正确格式说明（用于失败原因，避免用户不知如何改） */
    static final String DATE_FORMAT_HINT =
            "正确示例：2024-01-15、2024/01/15、2024.01.15、20240115、2024年1月15日";
    /** 年月字段正确格式说明 */
    static final String YEAR_MONTH_FORMAT_HINT =
            "正确示例：2024-01、2024/01、202401；Excel 日期格 2024/1/1 或 2024-01-01 亦可（按年月入库）";
    /** 合同区间正确格式说明 */
    static final String CONTRACT_RANGE_FORMAT_HINT =
            "正确示例：2021/09/03-2024/08/31、2021-09-03~2024-08-31、20210903-无固定期限、2021/09/03-无固定期限";

    private EmployeeRosterImportSupport() {
    }

    /**
     * 将导入行转为保存 VO；校验失败抛 {@link IllegalArgumentException}（message 即行失败原因）。
     */
    static EmployeeSaveReqVO toSaveReq(EmployeeRosterImportExcelVO row) {
        if (row == null) {
            throw new IllegalArgumentException("空行");
        }
        String idCard = trim(row.getIdCard());
        if (StrUtil.isBlank(idCard)) {
            throw new IllegalArgumentException("身份证号不能为空");
        }
        String name = trim(row.getName());
        if (StrUtil.isBlank(name)) {
            throw new IllegalArgumentException("姓名不能为空");
        }
        String mobile = normalizeMobile(row.getMobile());
        if (StrUtil.isBlank(mobile)) {
            throw new IllegalArgumentException("手机不能为空");
        }
        Integer sex = parseSex(row.getSex());
        if (sex == null) {
            throw new IllegalArgumentException("性别不能为空或无法识别（正确示例：男 / 女）");
        }

        EmployeeSaveReqVO req = new EmployeeSaveReqVO();
        req.setName(name);
        req.setIdCard(idCard);
        req.setMobile(mobile);
        req.setSex(sex);
        req.setCompanyName(trim(row.getCompanyName()));
        req.setDeptName(trim(row.getDeptName()));
        req.setJobPost(resolveDictOrRaw("hrm_job_post", row.getJobPost()));
        req.setEntryDate(parseDate(row.getEntryDate(), "入职日期"));
        req.setFormalDate(parseDate(row.getFormalDate(), "转正日期"));
        if (row.getProbationSalary() != null) {
            req.setProbationSalary(row.getProbationSalary());
        }
        if (row.getRegularSalary() != null) {
            req.setRegularSalary(row.getRegularSalary());
        }
        Boolean ss = parseYesNo(row.getSocialSecurityEnabled());
        if (ss != null) {
            req.setSocialSecurityEnabled(ss);
        }
        Boolean hf = parseYesNo(row.getHousingFundEnabled());
        if (hf != null) {
            req.setHousingFundEnabled(hf);
        }
        // 参保年月：兼容 Excel 日期格读成 2024-01-01 / 2024/1/1 / 序列化字符串
        String ssMonthRaw = trim(row.getSocialSecurityStartMonth());
        if (StrUtil.isNotBlank(ssMonthRaw)) {
            String ssMonth = normalizeYearMonth(ssMonthRaw);
            if (ssMonth == null) {
                throw new IllegalArgumentException(
                        "参保年月格式无法解析：" + ssMonthRaw + "；" + YEAR_MONTH_FORMAT_HINT
                                + "；Excel 日期格亦可（如 2024/1/1 → 按 2024-01 入库）");
            }
            req.setSocialSecurityStartMonth(ssMonth);
        }
        req.setNation(resolveDictOrRaw(DICT_NATION, row.getNation()));
        applyMarriageChildbearing(req, row.getMarriageChildbearingSummary());
        req.setHouseholdType(resolveDictOrRaw(DICT_HOUSEHOLD, row.getHouseholdType()));
        req.setNativePlace(trim(row.getNativePlace()));
        req.setBirthday(parseBirthday(row.getBirthdayMonth()));
        req.setHouseholdAddress(trim(row.getHouseholdAddress()));
        req.setCurrentAddress(trim(row.getCurrentAddress()));
        // 员工类型：有值才写入；空白留给 import 路径区分 create 默认正式 / update 保留旧值（F1）
        Integer employeeStatus = parseEmployeeStatusOrNull(row.getEmployeeType());
        if (employeeStatus != null) {
            req.setEmployeeStatus(employeeStatus);
        }
        req.setEmploymentForm(resolveDictOrRaw(DICT_EMPLOYMENT_FORM, row.getEmploymentForm()));
        req.setBankAccount(normalizeBankAccount(row.getBankAccount()));
        req.setBankName(trim(row.getBankName()));
        applyEmergency(req, row.getEmergencyContactRelation(), row.getEmergencyPhone());
        req.setRecruitmentChannel(trim(row.getRecruitmentChannel()));
        req.setInterviewerName(trim(row.getInterviewerName()));

        List<EmployeeEducationVO> educations = buildEducations(row);
        if (!educations.isEmpty()) {
            req.setEducationList(educations);
        }
        List<EmployeeContractVO> contracts = buildContracts(row);
        if (!contracts.isEmpty()) {
            req.setContractList(contracts);
        }
        // 入职资料列：Excel 不携带附件本体，忽略
        return req;
    }

    static List<EmployeeEducationVO> buildEducations(EmployeeRosterImportExcelVO row) {
        List<EmployeeEducationVO> list = new ArrayList<>(2);
        boolean hasHighest = hasAny(row.getHighestEducation(), row.getHighestSchoolName(),
                row.getHighestMajor(), row.getHighestGraduateDate(), row.getHighestDegree(),
                row.getEducationType());
        boolean hasFirst = hasAny(row.getFirstEducation(), row.getFirstSchoolName(),
                row.getFirstMajor(), row.getFirstGraduateDate(), row.getFirstDegree());
        if (hasHighest) {
            EmployeeEducationVO e = new EmployeeEducationVO();
            e.setHighestEducation(true);
            e.setFirstEducation(false);
            e.setEducationLevel(resolveDictOrRaw(DICT_EDUCATION, row.getHighestEducation()));
            e.setEducationType(resolveDictOrRaw(DICT_EDUCATION_TYPE, row.getEducationType()));
            e.setDegree(trim(row.getHighestDegree()));
            e.setSchoolName(trim(row.getHighestSchoolName()));
            e.setMajor(trim(row.getHighestMajor()));
            e.setEndTime(parseDate(row.getHighestGraduateDate(), "最高学历毕业时间"));
            // startTime 可空（保存侧未强制）
            list.add(e);
        }
        if (hasFirst) {
            EmployeeEducationVO e = new EmployeeEducationVO();
            e.setFirstEducation(true);
            e.setHighestEducation(false);
            e.setEducationLevel(resolveDictOrRaw(DICT_EDUCATION, row.getFirstEducation()));
            e.setDegree(trim(row.getFirstDegree()));
            e.setSchoolName(trim(row.getFirstSchoolName()));
            e.setMajor(trim(row.getFirstMajor()));
            e.setEndTime(parseDate(row.getFirstGraduateDate(), "第一学历毕业时间"));
            list.add(e);
        }
        // 若最高=第一且内容相同，合并为一条双角色
        if (list.size() == 2) {
            EmployeeEducationVO a = list.get(0);
            EmployeeEducationVO b = list.get(1);
            if (StrUtil.equals(a.getEducationLevel(), b.getEducationLevel())
                    && StrUtil.equals(a.getSchoolName(), b.getSchoolName())
                    && StrUtil.equals(a.getMajor(), b.getMajor())) {
                a.setFirstEducation(true);
                list = List.of(a);
            }
        }
        return list;
    }

    static List<EmployeeContractVO> buildContracts(EmployeeRosterImportExcelVO row) {
        List<EmployeeContractVO> list = new ArrayList<>(4);
        addRangeContract(list, 1, row.getContract1Range(), row.getCurrentContractType());
        addRangeContract(list, 2, row.getContract2Range(), null);
        addRangeContract(list, 3, row.getContract3Range(), null);
        addRangeContract(list, 4, row.getContract4Range(), null);
        if (list.isEmpty()) {
            LocalDate start = parseDate(row.getCurrentContractStartDate(), "目前合同签订日期（起）");
            if (start != null) {
                EmployeeContractVO c = new EmployeeContractVO();
                c.setSequenceNo(1);
                c.setStartDate(start);
                c.setEndDate(parseOpenEndDate(row.getCurrentContractEndDate()));
                c.setContractType(resolveDictOrRaw(DICT_CONTRACT_TYPE, row.getCurrentContractType()));
                list.add(c);
            }
        } else if (list.get(list.size() - 1) != null) {
            // 当前合同类型写到最后一段合同
            String type = resolveDictOrRaw(DICT_CONTRACT_TYPE, row.getCurrentContractType());
            if (StrUtil.isNotBlank(type)) {
                list.get(list.size() - 1).setContractType(type);
            }
        }
        // 重新编号保证 1..n 连续
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setSequenceNo(i + 1);
        }
        return list;
    }

    private static void addRangeContract(List<EmployeeContractVO> list, int seq, String range, String typeHint) {
        if (StrUtil.isBlank(range)) {
            return;
        }
        try {
            LocalDate[] se = parseDateRange(range.trim());
            if (se == null || se[0] == null) {
                throw new IllegalArgumentException(
                        "合同起止日期无法解析：" + range + "；" + CONTRACT_RANGE_FORMAT_HINT);
            }
            EmployeeContractVO c = new EmployeeContractVO();
            c.setSequenceNo(seq);
            c.setStartDate(se[0]);
            c.setEndDate(se[1]);
            if (StrUtil.isNotBlank(typeHint)) {
                c.setContractType(resolveDictOrRaw(DICT_CONTRACT_TYPE, typeHint));
            }
            list.add(c);
        } catch (IllegalArgumentException ex) {
            // 补齐合同区间示例（子解析可能只带了单日示例）
            String msg = ex.getMessage();
            if (msg != null && !msg.contains("无固定期限") && !msg.contains("合同起止")) {
                throw new IllegalArgumentException(msg + "；" + CONTRACT_RANGE_FORMAT_HINT);
            }
            if (msg != null && msg.contains("合同起止") && !msg.contains("正确示例")) {
                throw new IllegalArgumentException(msg + "；" + CONTRACT_RANGE_FORMAT_HINT);
            }
            throw ex;
        }
    }

    static LocalDate[] parseDateRange(String range) {
        if (StrUtil.isBlank(range)) {
            return null;
        }
        String s = range.trim().replace('～', '-').replace('—', '-');
        // 1) 紧凑 8 位日：20210903-无固定期限 / 20210903-20240831
        Matcher compact = DATE_RANGE_COMPACT.matcher(s);
        if (compact.matches()) {
            LocalDate start = parseDate(compact.group(1), "合同起");
            LocalDate end = parseOpenEndDate(compact.group(2));
            return new LocalDate[]{start, end};
        }
        // 2) 带分隔符区间
        Matcher m = DATE_RANGE.matcher(s);
        if (m.matches()) {
            LocalDate start = parseDate(m.group(1), "合同起");
            LocalDate end = parseOpenEndDate(m.group(2));
            return new LocalDate[]{start, end};
        }
        // 3) 仅起始日
        LocalDate only = parseDate(s, "合同起");
        return only == null ? null : new LocalDate[]{only, null};
    }

    static LocalDate parseOpenEndDate(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        String t = raw.trim();
        if (t.contains("无固定") || t.contains("不定期") || "长期".equals(t) || "-".equals(t)) {
            return null;
        }
        return parseDate(t, "合同止");
    }

    static Integer parseSex(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        String t = raw.trim();
        if ("男".equals(t) || "1".equals(t) || "M".equalsIgnoreCase(t)) {
            return 1;
        }
        if ("女".equals(t) || "2".equals(t) || "F".equalsIgnoreCase(t)) {
            return 2;
        }
        try {
            String v = DictFrameworkUtils.parseDictDataValue(DictTypeConstants.USER_SEX, t);
            if (StrUtil.isNotBlank(v)) {
                return Integer.valueOf(v);
            }
        } catch (Exception ignored) {
            // dict 未就绪
        }
        return null;
    }

    static Boolean parseYesNo(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        String t = raw.trim();
        if ("是".equals(t) || "Y".equalsIgnoreCase(t) || "YES".equalsIgnoreCase(t)
                || "true".equalsIgnoreCase(t) || "1".equals(t)) {
            return true;
        }
        if ("否".equals(t) || "N".equalsIgnoreCase(t) || "NO".equalsIgnoreCase(t)
                || "false".equalsIgnoreCase(t) || "0".equals(t)) {
            return false;
        }
        return null;
    }

    /**
     * 解析员工类型标签；空白返回 null（由调用方决定 create 默认 / update 保留）。
     */
    static Integer parseEmployeeStatusOrNull(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        String t = raw.trim();
        for (EmployeeStatusEnum e : EmployeeStatusEnum.values()) {
            if (e.getName().equals(t)) {
                return e.getStatus();
            }
        }
        try {
            String v = DictFrameworkUtils.parseDictDataValue(DICT_EMPLOYEE_STATUS, t);
            if (StrUtil.isNotBlank(v)) {
                return Integer.valueOf(v);
            }
        } catch (Exception ignored) {
            // dict 未就绪
        }
        if (t.contains("试用")) {
            return EmployeeStatusEnum.PROBATIONARY.getStatus();
        }
        if (t.contains("实习")) {
            return EmployeeStatusEnum.INTERN.getStatus();
        }
        if (t.contains("离职")) {
            return EmployeeStatusEnum.RESIGNED.getStatus();
        }
        // 无法识别的非空标签：仍回落正式，避免脏文案导致整行失败
        return EmployeeStatusEnum.FORMAL.getStatus();
    }

    /** 创建路径：空白员工类型默认正式 */
    static Integer defaultEmployeeStatusForCreate(Integer parsedOrNull) {
        return parsedOrNull != null ? parsedOrNull : EmployeeStatusEnum.FORMAL.getStatus();
    }

    /**
     * 身份证脱敏：保留后 4 位，前部用 *（日志与失败明细禁止完整证号，F2）。
     */
    static String maskIdCard(String idCard) {
        if (StrUtil.isBlank(idCard)) {
            return "(empty)";
        }
        String t = idCard.trim();
        if (t.length() <= 4) {
            return "****";
        }
        return "*".repeat(t.length() - 4) + t.substring(t.length() - 4);
    }

    /** 将可能含完整证号的文案脱敏后写入日志 */
    static String sanitizeReasonForLog(String reason, String idCard) {
        if (StrUtil.isBlank(reason)) {
            return reason;
        }
        String r = reason;
        if (StrUtil.isNotBlank(idCard) && r.contains(idCard)) {
            r = r.replace(idCard, maskIdCard(idCard));
        }
        return r;
    }

    /**
     * 导入行失败原因：字段校验类保留原文；SQL/表结构类改写为可读说明，禁止把 MyBatis 堆栈直接回前端。
     */
    static String humanizeImportFailure(Throwable ex, String idCard) {
        if (ex == null) {
            return "未知错误";
        }
        String raw = firstNonBlankMessage(ex);
        if (isSchemaOrSqlGrammarFailure(ex, raw)) {
            return "系统数据表结构异常（非 Excel 字段填写错误），请联系管理员检查库表是否已执行花名册字段迁移";
        }
        if (StrUtil.isBlank(raw)) {
            raw = ex.getClass().getSimpleName();
        }
        // 截断过长 JDBC/MyBatis 文案，避免整段 SQL 刷屏
        if (raw.length() > 240) {
            raw = raw.substring(0, 240) + "…";
        }
        return sanitizeReasonForLog(raw, idCard);
    }

    private static String firstNonBlankMessage(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            if (StrUtil.isNotBlank(cur.getMessage())) {
                return cur.getMessage().trim();
            }
            cur = cur.getCause();
        }
        return null;
    }

    private static boolean isSchemaOrSqlGrammarFailure(Throwable ex, String raw) {
        String hay = (raw == null ? "" : raw) + " " + walkExceptionNames(ex);
        String lower = hay.toLowerCase();
        return lower.contains("unknown column")
                || lower.contains("bad sql grammar")
                || lower.contains("sqlsyntaxerrorexception")
                || lower.contains("error querying database")
                || lower.contains("doesn't exist")
                || lower.contains("does not exist");
    }

    private static String walkExceptionNames(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = ex;
        int depth = 0;
        while (cur != null && depth < 8) {
            sb.append(cur.getClass().getName()).append(' ');
            cur = cur.getCause();
            depth++;
        }
        return sb.toString();
    }

    static void applyMarriageChildbearing(EmployeeSaveReqVO req, String summary) {
        if (StrUtil.isBlank(summary)) {
            return;
        }
        String t = summary.trim();
        if (t.contains("/")) {
            String[] parts = t.split("/", 2);
            req.setMaritalStatus(resolveDictOrRaw(DICT_MARITAL, parts[0]));
            req.setFertilityStatus(resolveDictOrRaw(DICT_FERTILITY, parts[1]));
            return;
        }
        // 常见粘连：已婚已育
        if (t.contains("已婚") && t.contains("已育")) {
            req.setMaritalStatus(resolveDictOrRaw(DICT_MARITAL, "已婚"));
            req.setFertilityStatus(resolveDictOrRaw(DICT_FERTILITY, "已育"));
            return;
        }
        if (t.contains("未婚")) {
            req.setMaritalStatus(resolveDictOrRaw(DICT_MARITAL, "未婚"));
            return;
        }
        req.setMaritalStatus(resolveDictOrRaw(DICT_MARITAL, t));
    }

    static void applyEmergency(EmployeeSaveReqVO req, String contactRelation, String phone) {
        req.setEmergencyPhone(trim(phone));
        if (StrUtil.isBlank(contactRelation)) {
            return;
        }
        String t = contactRelation.trim();
        int idx = t.lastIndexOf('/');
        if (idx > 0 && idx < t.length() - 1) {
            req.setEmergencyContact(t.substring(0, idx).trim());
            req.setEmergencyRelationship(t.substring(idx + 1).trim());
        } else {
            req.setEmergencyContact(t);
        }
    }

    static String resolveDictOrRaw(String dictType, String labelOrValue) {
        if (StrUtil.isBlank(labelOrValue)) {
            return null;
        }
        String t = labelOrValue.trim();
        try {
            String byLabel = DictFrameworkUtils.parseDictDataValue(dictType, t);
            if (StrUtil.isNotBlank(byLabel)) {
                return byLabel;
            }
        } catch (Exception ignored) {
            // dict 未初始化时回退原文
        }
        return t;
    }

    static LocalDate parseDate(String raw, String field) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String && StrUtil.isBlank((String) raw)) {
            return null;
        }
        String t = String.valueOf(raw).trim();
        if (t.isEmpty() || "null".equalsIgnoreCase(t)) {
            return null;
        }
        // Excel 数字日期已由框架转 string 时多为 yyyy-MM-dd
        String[] patterns = {
                "yyyy-MM-dd", "yyyy/M/d", "yyyy/MM/dd", "yyyy.M.d", "yyyy.MM.dd",
                "yyyy年M月d日", "yyyy-M-d", "yyyyMMdd"
        };
        // 截掉时间部分
        if (t.contains("T")) {
            t = t.substring(0, t.indexOf('T'));
        }
        if (t.contains(" ")) {
            t = t.substring(0, t.indexOf(' '));
        }
        for (String p : patterns) {
            try {
                return LocalDate.parse(normalizeDateText(t), DateTimeFormatter.ofPattern(p));
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        // 出生年月 yyyy-MM
        if (t.matches("\\d{4}[-/.年]\\d{1,2}")) {
            try {
                String ym = normalizeYearMonth(t);
                if (ym != null) {
                    return LocalDate.parse(ym + "-01");
                }
            } catch (Exception ignored) {
                // fallthrough
            }
        }
        throw new IllegalArgumentException(field + "格式无法解析：" + raw + "；" + DATE_FORMAT_HINT);
    }

    static LocalDate parseBirthday(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        try {
            return parseDate(raw, "出生年月");
        } catch (IllegalArgumentException ex) {
            String ym = normalizeYearMonth(raw);
            if (ym != null) {
                return LocalDate.parse(ym + "-01");
            }
            throw new IllegalArgumentException(
                    "出生年月格式无法解析：" + raw + "；" + YEAR_MONTH_FORMAT_HINT + "；也可填完整日期如 1990-01-15");
        }
    }

    /**
     * 归一化参保/出生年月为 yyyy-MM。
     * <p>
     * 兼容：
     * <ul>
     *   <li>yyyy-MM / yyyy/M / yyyy.MM / yyyy年M月</li>
     *   <li>yyyyMM（6 位）</li>
     *   <li>完整日期 yyyy-MM-dd、yyyy/M/d、yyyy年M月d日（取年月）</li>
     *   <li>带时间后缀 2024-01-01 00:00:00 / 2024-01-01T00:00</li>
     *   <li>Excel 序列日（纯数字天数，约 1～60000）→ 转本地日期再取年月</li>
     * </ul>
     */
    static String normalizeYearMonth(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        String original = raw.trim();
        // 截掉时间部分
        String t = original;
        if (t.contains("T")) {
            t = t.substring(0, t.indexOf('T'));
        }
        if (t.contains(" ")) {
            t = t.substring(0, t.indexOf(' '));
        }
        t = t.replace('.', '-').replace('/', '-')
                .replace("年", "-").replace("月", "-").replace("日", "");
        // 折叠多余连字符
        while (t.contains("--")) {
            t = t.replace("--", "-");
        }
        if (t.endsWith("-")) {
            t = t.substring(0, t.length() - 1);
        }
        t = t.trim();

        // yyyy-M / yyyy-MM
        if (t.matches("\\d{4}-\\d{1,2}")) {
            String[] p = t.split("-");
            return formatYearMonth(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
        }
        // yyyy-M-d / yyyy-MM-dd（Excel 日期格常读成完整日）
        if (t.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
            String[] p = t.split("-");
            return formatYearMonth(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
        }
        // yyyyMM
        if (t.matches("\\d{6}")) {
            return formatYearMonth(Integer.parseInt(t.substring(0, 4)), Integer.parseInt(t.substring(4, 6)));
        }
        // yyyyMMdd
        if (t.matches("\\d{8}")) {
            return formatYearMonth(Integer.parseInt(t.substring(0, 4)), Integer.parseInt(t.substring(4, 6)));
        }
        // Excel 序列日：如 45292 → 2024-01-01
        if (t.matches("\\d+(\\.\\d+)?")) {
            try {
                double serial = Double.parseDouble(t);
                // Excel 日序列合理范围（1900-01-01 起约 1～60000+）
                if (serial >= 1 && serial < 100000) {
                    // EasyExcel/POI 常用 1899-12-30 纪元
                    LocalDate d = LocalDate.of(1899, 12, 30).plusDays((long) serial);
                    return formatYearMonth(d.getYear(), d.getMonthValue());
                }
            } catch (Exception ignored) {
                // fallthrough
            }
        }
        // 最后尝试 parseDate 再取年月
        try {
            LocalDate d = parseDate(original, "年月");
            if (d != null) {
                return formatYearMonth(d.getYear(), d.getMonthValue());
            }
        } catch (IllegalArgumentException ignored) {
            // fallthrough
        }
        return null;
    }

    private static String formatYearMonth(int year, int month) {
        if (year < 1900 || year > 2100 || month < 1 || month > 12) {
            return null;
        }
        return String.format(Locale.ROOT, "%04d-%02d", year, month);
    }

    private static String normalizeDateText(String t) {
        return t.replace('.', '-').replace('/', '-');
    }

    private static String normalizeMobile(Object mobile) {
        if (mobile == null) {
            return null;
        }
        String t = String.valueOf(mobile).trim();
        // Excel 可能读成 1.59E10
        if (t.matches("\\d+\\.0+")) {
            t = t.substring(0, t.indexOf('.'));
        }
        t = t.replace(" ", "").replace("-", "");
        return t;
    }

    private static String normalizeBankAccount(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        return raw.replace(" ", "").trim();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    private static boolean hasAny(String... vals) {
        for (String v : vals) {
            if (StrUtil.isNotBlank(v)) {
                return true;
            }
        }
        return false;
    }
}
