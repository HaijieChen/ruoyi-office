package cn.iocoder.yudao.module.hrm.controller.admin.payroll;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.module.hrm.dal.dataobject.payroll.PayrollBatchDO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.payroll.PayrollLineDO;
import cn.iocoder.yudao.module.hrm.service.payroll.PayrollBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import cn.iocoder.yudao.module.hrm.controller.admin.payroll.vo.PayrollExportExcelVO;
import cn.iocoder.yudao.module.hrm.controller.admin.payroll.vo.PunchTemplateExcelVO;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 月度工资核算")
@RestController
@RequestMapping("/hrm/payroll-batch")
@Validated
public class PayrollBatchController {

    @Resource
    private PayrollBatchService payrollBatchService;

    @GetMapping("/get")
    @Operation(summary = "获取或创建批次")
    @PreAuthorize("@ss.hasPermission('hrm:payroll-batch:query')")
    public CommonResult<PayrollBatchDO> get(@RequestParam Integer yearMonth) {
        return success(payrollBatchService.getOrCreate(yearMonth));
    }

    @PostMapping("/generate")
    @Operation(summary = "按档案生成草稿")
    @PreAuthorize("@ss.hasPermission('hrm:payroll-batch:create')")
    public CommonResult<PayrollBatchDO> generate(@RequestParam Integer yearMonth) {
        return success(payrollBatchService.generate(yearMonth));
    }

    @GetMapping("/punch-template")
    @Operation(summary = "下载打卡导入模板（含工号）")
    @PreAuthorize("@ss.hasPermission('hrm:payroll-batch:query')")
    public void punchTemplate(HttpServletResponse response) throws IOException {
        ExcelUtils.write(response, "打卡导入模板.xls", "打卡", PunchTemplateExcelVO.class, List.of());
    }

    @PostMapping("/upload-punch")
    @Operation(summary = "上传打卡并合并旷工")
    @PreAuthorize("@ss.hasPermission('hrm:payroll-batch:update')")
    public CommonResult<PayrollBatchService.PunchUploadVO> uploadPunch(
            @RequestParam Integer yearMonth,
            @RequestParam("file") MultipartFile file) throws Exception {
        return success(payrollBatchService.uploadPunch(yearMonth, file.getInputStream()));
    }

    @PostMapping("/publish")
    @Operation(summary = "确认下发")
    @PreAuthorize("@ss.hasPermission('hrm:payroll-batch:publish')")
    public CommonResult<PayrollBatchDO> publish(@RequestParam Integer yearMonth) {
        return success(payrollBatchService.publish(yearMonth));
    }

    @PostMapping("/withdraw")
    @Operation(summary = "撤回")
    @PreAuthorize("@ss.hasPermission('hrm:payroll-batch:withdraw')")
    public CommonResult<PayrollBatchDO> withdraw(@RequestParam Integer yearMonth) {
        return success(payrollBatchService.withdraw(yearMonth));
    }

    @GetMapping("/lines")
    @Operation(summary = "人事工资行")
    @PreAuthorize("@ss.hasPermission('hrm:payroll-batch:query')")
    public CommonResult<List<PayrollLineDO>> lines(@RequestParam Integer yearMonth) {
        return success(payrollBatchService.listHrLines(yearMonth));
    }

    @PutMapping("/adjust")
    @Operation(summary = "草稿调整单人金额")
    @PreAuthorize("@ss.hasPermission('hrm:payroll-batch:update')")
    public CommonResult<Boolean> adjust(@RequestParam Long lineId,
                                        @RequestBody PayrollLineDO patch) {
        payrollBatchService.updateAdjust(lineId, patch);
        return success(true);
    }

    @GetMapping("/export")
    @Operation(summary = "导出工资表")
    @PreAuthorize("@ss.hasPermission('hrm:payroll-batch:export')")
    public void export(@RequestParam Integer yearMonth, HttpServletResponse response) throws IOException {
        List<PayrollLineDO> lines = payrollBatchService.listHrLines(yearMonth);
        List<PayrollExportExcelVO> rows = new ArrayList<>();
        for (PayrollLineDO line : lines) {
            PayrollExportExcelVO row = new PayrollExportExcelVO();
            row.setYearMonth(line.getYearMonth());
            row.setCompanyName(line.getCompanyName());
            row.setDeptName(line.getDeptName());
            row.setJobPost(line.getJobPost());
            row.setName(line.getEmployeeName());
            row.setEntryDate(line.getEntryDate());
            row.setWage(line.getWage());
            row.setSocialBase(line.getSocialBase());
            row.setHousingBase(line.getHousingBase());
            row.setFullAttendanceBonus(line.getFullAttendanceBonus());
            row.setHousingSubsidy(line.getHousingSubsidy());
            row.setPerformance(line.getPerformance());
            row.setBonus(line.getBonus());
            row.setSubsidy(line.getSubsidy());
            row.setHolidayOvertimeDays(line.getHolidayOvertimeDays());
            row.setHolidayOvertimePay(line.getHolidayOvertimePay());
            row.setWeekdayOvertimePay(line.getWeekdayOvertimePay());
            row.setSickDays(line.getSickDays());
            row.setSickRate(line.getSickRate());
            row.setSickPay(line.getSickPay());
            row.setPersonalAbsenceDays(line.getPersonalAbsenceDays());
            row.setPersonalLeavePay(line.getPersonalLeavePay());
            row.setTripSubsidy(line.getTripSubsidy());
            row.setOtherAdjust(line.getOtherAdjust());
            row.setPayable(line.getPayable());
            row.setSocialDeduct(line.getSocialDeduct());
            row.setHousingDeduct(line.getHousingDeduct());
            row.setTax(line.getTax());
            row.setNet(line.getNet());
            row.setBankAccount(line.getBankAccount());
            row.setBankName(line.getBankName());
            row.setMobile(line.getMobile());
            row.setIdCard(line.getIdCard());
            rows.add(row);
        }
        ExcelUtils.write(response, yearMonth + "工资表.xls", "工资表", PayrollExportExcelVO.class, rows);
    }
}
