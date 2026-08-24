package cn.iocoder.yudao.module.hrm.service.payroll;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.EmployeeDO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.payroll.PayrollBatchDO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.payroll.PayrollLineDO;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.payroll.PayrollBatchMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.payroll.PayrollLineMapper;
import cn.iocoder.yudao.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Validated
public class PayrollBatchServiceImpl implements PayrollBatchService {

    @Resource
    private PayrollBatchMapper payrollBatchMapper;
    @Resource
    private PayrollLineMapper payrollLineMapper;
    @Resource
    private EmployeeMapper employeeMapper;
    @Resource
    private MinWageService minWageService;
    @Resource
    private NotifyMessageSendApi notifyMessageSendApi;

    private final PayrollCalculator calculator = new PayrollCalculator();

    @Override
    public PayrollBatchDO getOrCreate(int yearMonth) {
        PayrollBatchDO existing = findBatch(yearMonth);
        if (existing != null) {
            return existing;
        }
        PayrollBatchDO batch = new PayrollBatchDO();
        batch.setYearMonth(yearMonth);
        batch.setStatus(PayrollBatchRules.DRAFT);
        payrollBatchMapper.insert(batch);
        return batch;
    }

    @Override
    public PayrollBatchDO generate(int yearMonth) {
        PayrollBatchDO batch = getOrCreate(yearMonth);
        if (!PayrollBatchRules.canEdit(batch.getStatus())) {
            throw new IllegalStateException("published batch is locked");
        }
        payrollLineMapper.delete(new LambdaQueryWrapperX<PayrollLineDO>()
                .eq(PayrollLineDO::getBatchId, batch.getId())
                .eq(PayrollLineDO::getSnapshot, false));
        BigDecimal minWage = minWageService.effectiveOn(yearMonth);
        if (minWage == null) {
            minWage = BigDecimal.ZERO;
        }
        List<EmployeeDO> employees = employeeMapper.selectList(new LambdaQueryWrapperX<EmployeeDO>()
                .notIn(EmployeeDO::getEmployeeStatus, 6, 7));
        for (EmployeeDO emp : employees) {
            BigDecimal wage = emp.getRegularSalary() != null ? emp.getRegularSalary() : emp.getProbationSalary();
            if (wage == null) {
                wage = BigDecimal.ZERO;
            }
            int tenure = tenureYears(emp.getEntryDate(), yearMonth);
            PayrollCalculator.Result calc = calculator.calculate(new PayrollCalculator.Input(
                    wage, new BigDecimal("200"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    wage, wage, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, false, tenure, minWage));
            PayrollLineDO line = new PayrollLineDO();
            fillTemplateLine(line, batch, emp, wage, calc, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            line.setSnapshot(false);
            payrollLineMapper.insert(line);
        }
        return batch;
    }

    @Override
    public PunchUploadVO uploadPunch(int yearMonth, InputStream in) throws Exception {
        generate(yearMonth);
        PunchXlsParser.ParseResult parsed = new PunchXlsParser().parse(in);
        List<EmployeeDO> employees = employeeMapper.selectList(new LambdaQueryWrapperX<EmployeeDO>()
                .notIn(EmployeeDO::getEmployeeStatus, 6, 7));
        PayrollPunchApply.Result applied = PayrollPunchApply.apply(
                employees.stream()
                        .map(e -> new PayrollPunchApply.EmployeeName(e.getId(), e.getName(), e.getEmployeeNo()))
                        .toList(),
                parsed);
        Map<Long, EmployeeDO> byId = employees.stream().collect(Collectors.toMap(EmployeeDO::getId, e -> e));
        BigDecimal minWage = minWageService.effectiveOn(yearMonth);
        if (minWage == null) {
            minWage = BigDecimal.ZERO;
        }
        PayrollBatchDO batch = require(yearMonth);
        List<PayrollLineDO> lines = payrollLineMapper.selectList(new LambdaQueryWrapperX<PayrollLineDO>()
                .eq(PayrollLineDO::getBatchId, batch.getId())
                .eq(PayrollLineDO::getSnapshot, false));
        for (PayrollLineDO line : lines) {
            BigDecimal absence = applied.absenceByEmployeeId().get(line.getEmployeeId());
            if (absence == null) {
                continue;
            }
            EmployeeDO emp = byId.get(line.getEmployeeId());
            if (emp == null) {
                continue;
            }
            BigDecimal wage = emp.getRegularSalary() != null ? emp.getRegularSalary() : emp.getProbationSalary();
            if (wage == null) {
                wage = BigDecimal.ZERO;
            }
            int tenure = tenureYears(emp.getEntryDate(), yearMonth);
            PayrollCalculator.Result calc = calculator.calculate(new PayrollCalculator.Input(
                    wage, new BigDecimal("200"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    wage, wage, line.getTax(), BigDecimal.ZERO, BigDecimal.ZERO, absence,
                    BigDecimal.ZERO, false, tenure, minWage));
            fillTemplateLine(line, batch, emp, wage, calc, BigDecimal.ZERO, absence, line.getTax());
            line.setPunchName(emp.getName());
            payrollLineMapper.updateById(line);
        }
        return new PunchUploadVO(applied.matched(), applied.unmatched());
    }

    @Override
    public PayrollBatchDO publish(int yearMonth) {
        PayrollBatchDO batch = require(yearMonth);
        batch.setStatus(PayrollBatchRules.publish(batch.getStatus()));
        payrollBatchMapper.updateById(batch);
        payrollLineMapper.delete(new LambdaQueryWrapperX<PayrollLineDO>()
                .eq(PayrollLineDO::getBatchId, batch.getId())
                .eq(PayrollLineDO::getSnapshot, true));
        List<PayrollLineDO> hrLines = payrollLineMapper.selectList(new LambdaQueryWrapperX<PayrollLineDO>()
                .eq(PayrollLineDO::getBatchId, batch.getId())
                .eq(PayrollLineDO::getSnapshot, false));
        for (PayrollLineDO hr : hrLines) {
            PayrollLineDO snap = copyPayslip(hr);
            payrollLineMapper.insert(snap);
            if (hr.getUserId() != null) {
                notify(hr.getUserId(), yearMonth);
            }
        }
        return batch;
    }

    @Override
    public PayrollBatchDO withdraw(int yearMonth) {
        PayrollBatchDO batch = require(yearMonth);
        batch.setStatus(PayrollBatchRules.withdraw(batch.getStatus()));
        payrollBatchMapper.updateById(batch);
        payrollLineMapper.delete(new LambdaQueryWrapperX<PayrollLineDO>()
                .eq(PayrollLineDO::getBatchId, batch.getId())
                .eq(PayrollLineDO::getSnapshot, true));
        return batch;
    }

    @Override
    public List<PayrollLineDO> listHrLines(int yearMonth) {
        PayrollBatchDO batch = findBatch(yearMonth);
        if (batch == null) {
            return List.of();
        }
        return payrollLineMapper.selectList(new LambdaQueryWrapperX<PayrollLineDO>()
                .eq(PayrollLineDO::getBatchId, batch.getId())
                .eq(PayrollLineDO::getSnapshot, false));
    }

    @Override
    public List<PayrollLineDO> listMyPayslips(Long loginUserId) {
        return payrollLineMapper.selectList(new LambdaQueryWrapperX<PayrollLineDO>()
                .eq(PayrollLineDO::getSnapshot, true)
                .eq(PayrollLineDO::getUserId, loginUserId)
                .orderByDesc(PayrollLineDO::getId));
    }

    @Override
    public void updateAdjust(Long lineId, PayrollLineDO patch) {
        PayrollLineDO line = payrollLineMapper.selectById(lineId);
        if (line == null || Boolean.TRUE.equals(line.getSnapshot())) {
            throw new IllegalStateException("line not editable");
        }
        PayrollBatchDO batch = payrollBatchMapper.selectById(line.getBatchId());
        if (!PayrollBatchRules.canEdit(batch.getStatus())) {
            throw new IllegalStateException("published batch is locked");
        }
        if (patch.getTax() != null) {
            line.setTax(patch.getTax());
        }
        if (patch.getPerformance() != null) {
            line.setPerformance(patch.getPerformance());
        }
        if (patch.getBonus() != null) {
            line.setBonus(patch.getBonus());
        }
        if (patch.getSubsidy() != null) {
            line.setSubsidy(patch.getSubsidy());
        }
        if (patch.getHousingSubsidy() != null) {
            line.setHousingSubsidy(patch.getHousingSubsidy());
        }
        if (patch.getHolidayOvertimeDays() != null) {
            line.setHolidayOvertimeDays(patch.getHolidayOvertimeDays());
        }
        if (patch.getHolidayOvertimePay() != null) {
            line.setHolidayOvertimePay(patch.getHolidayOvertimePay());
        }
        if (patch.getWeekdayOvertimePay() != null) {
            line.setWeekdayOvertimePay(patch.getWeekdayOvertimePay());
        }
        if (patch.getTripSubsidy() != null) {
            line.setTripSubsidy(patch.getTripSubsidy());
        }
        if (patch.getOtherAdjust() != null) {
            line.setOtherAdjust(patch.getOtherAdjust());
        }
        if (patch.getSocialDeduct() != null) {
            line.setSocialDeduct(patch.getSocialDeduct());
        }
        if (patch.getHousingDeduct() != null) {
            line.setHousingDeduct(patch.getHousingDeduct());
        }
        BigDecimal overtime = nz(line.getHolidayOvertimePay()).add(nz(line.getWeekdayOvertimePay()));
        line.setOvertime(overtime);
        BigDecimal payable = nz(line.getWage())
                .add(nz(line.getFullAttendanceBonus()))
                .add(nz(line.getHousingSubsidy()))
                .add(nz(line.getPerformance()))
                .add(nz(line.getBonus()))
                .add(nz(line.getSubsidy()))
                .add(overtime)
                .add(nz(line.getSickPay()))
                .add(nz(line.getTripSubsidy()))
                .add(nz(line.getOtherAdjust()))
                .subtract(nz(line.getPersonalLeavePay()));
        line.setPayable(payable);
        line.setNet(payable.subtract(nz(line.getSocialDeduct())).subtract(nz(line.getHousingDeduct())).subtract(nz(line.getTax())));
        payrollLineMapper.updateById(line);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private void notify(Long userId, int yearMonth) {
        try {
            NotifySendSingleToUserReqDTO req = new NotifySendSingleToUserReqDTO();
            req.setUserId(userId);
            req.setTemplateCode("hrm_payroll_published");
            Map<String, Object> params = new HashMap<>();
            params.put("yearMonth", yearMonth);
            req.setTemplateParams(params);
            notifyMessageSendApi.sendSingleMessageToAdmin(req);
        } catch (Exception ignored) {
            // template may be missing in early env
        }
    }

    private PayrollBatchDO findBatch(int yearMonth) {
        return payrollBatchMapper.selectOne(new LambdaQueryWrapperX<PayrollBatchDO>()
                .eq(PayrollBatchDO::getYearMonth, yearMonth));
    }

    private PayrollBatchDO require(int yearMonth) {
        PayrollBatchDO batch = findBatch(yearMonth);
        if (batch == null) {
            throw new IllegalStateException("batch not found");
        }
        return batch;
    }

    private void fillTemplateLine(PayrollLineDO line, PayrollBatchDO batch, EmployeeDO emp, BigDecimal wage,
                                  PayrollCalculator.Result calc, BigDecimal sickDays, BigDecimal absence,
                                  BigDecimal tax) {
        line.setBatchId(batch.getId());
        line.setEmployeeId(emp.getId());
        line.setYearMonth(batch.getYearMonth());
        line.setJobPost(emp.getJobPost());
        line.setEmployeeName(emp.getName());
        line.setEntryDate(emp.getEntryDate());
        line.setWage(wage);
        line.setSocialBase(wage);
        line.setHousingBase(wage);
        line.setFullAttendanceBonus(calc.bonusPaid());
        line.setHousingSubsidy(BigDecimal.ZERO);
        line.setPerformance(BigDecimal.ZERO);
        line.setBonus(BigDecimal.ZERO);
        line.setSubsidy(BigDecimal.ZERO);
        line.setHolidayOvertimeDays(BigDecimal.ZERO);
        line.setHolidayOvertimePay(BigDecimal.ZERO);
        line.setWeekdayOvertimePay(BigDecimal.ZERO);
        line.setSickDays(sickDays);
        line.setSickRate(calc.sickRate());
        line.setSickPay(calc.sickPay());
        line.setPersonalAbsenceDays(absence);
        line.setPersonalLeavePay(calc.personalLeaveDeduction());
        line.setTripSubsidy(BigDecimal.ZERO);
        line.setOtherAdjust(BigDecimal.ZERO);
        line.setPayable(calc.payable());
        line.setSocialDeduct(calc.socialDeduction());
        line.setHousingDeduct(calc.housingDeduction());
        line.setTax(tax == null ? BigDecimal.ZERO : tax);
        line.setOvertime(BigDecimal.ZERO);
        line.setNet(calc.net());
        line.setBankAccount(emp.getBankAccount());
        line.setBankName(emp.getBankName());
        line.setMobile(emp.getMobile());
        line.setIdCard(emp.getIdCard());
        line.setUserId(emp.getUserId());
    }

    private static PayrollLineDO copyPayslip(PayrollLineDO hr) {
        PayrollLineDO snap = new PayrollLineDO();
        snap.setBatchId(hr.getBatchId());
        snap.setEmployeeId(hr.getEmployeeId());
        snap.setYearMonth(hr.getYearMonth());
        snap.setCompanyName(hr.getCompanyName());
        snap.setDeptName(hr.getDeptName());
        snap.setJobPost(hr.getJobPost());
        snap.setEmployeeName(hr.getEmployeeName());
        snap.setEntryDate(hr.getEntryDate());
        snap.setWage(hr.getWage());
        snap.setSocialBase(hr.getSocialBase());
        snap.setHousingBase(hr.getHousingBase());
        snap.setFullAttendanceBonus(hr.getFullAttendanceBonus());
        snap.setHousingSubsidy(hr.getHousingSubsidy());
        snap.setPerformance(hr.getPerformance());
        snap.setBonus(hr.getBonus());
        snap.setSubsidy(hr.getSubsidy());
        snap.setHolidayOvertimeDays(hr.getHolidayOvertimeDays());
        snap.setHolidayOvertimePay(hr.getHolidayOvertimePay());
        snap.setWeekdayOvertimePay(hr.getWeekdayOvertimePay());
        snap.setSickDays(hr.getSickDays());
        snap.setSickRate(hr.getSickRate());
        snap.setSickPay(hr.getSickPay());
        snap.setPersonalAbsenceDays(hr.getPersonalAbsenceDays());
        snap.setPersonalLeavePay(hr.getPersonalLeavePay());
        snap.setTripSubsidy(hr.getTripSubsidy());
        snap.setOtherAdjust(hr.getOtherAdjust());
        snap.setPayable(hr.getPayable());
        snap.setSocialDeduct(hr.getSocialDeduct());
        snap.setHousingDeduct(hr.getHousingDeduct());
        snap.setTax(hr.getTax());
        snap.setOvertime(hr.getOvertime());
        snap.setNet(hr.getNet());
        snap.setSnapshot(true);
        snap.setUserId(hr.getUserId());
        return snap;
    }

    static int tenureYears(LocalDate entry, int yearMonth) {
        if (entry == null) {
            return 0;
        }
        int year = yearMonth / 100;
        int month = yearMonth % 100;
        LocalDate asOf = LocalDate.of(year, month, 1);
        int years = asOf.getYear() - entry.getYear();
        if (asOf.getMonthValue() < entry.getMonthValue()) {
            years--;
        }
        return Math.max(years, 0);
    }
}
