package cn.iocoder.yudao.module.finance.service.allocation;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.finance.controller.admin.allocation.vo.FinanceDeptCostAllocationImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.allocation.vo.FinanceDeptCostAllocationImportRespVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.allocation.FinanceDeptCostAllocationDO;
import cn.iocoder.yudao.module.finance.dal.mysql.allocation.FinanceDeptCostAllocationMapper;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.DEPT_ALLOCATION_IMPORT_EMPTY;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.DEPT_ALLOCATION_IMPORT_INVALID;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.DEPT_ALLOCATION_SOURCE_TYPE_INVALID;

@Service
@Validated
public class FinanceDeptCostAllocationServiceImpl implements FinanceDeptCostAllocationService {

    private final FinanceDeptCostAllocationMapper allocationMapper;
    private final DeptApi deptApi;

    public FinanceDeptCostAllocationServiceImpl(FinanceDeptCostAllocationMapper allocationMapper,
                                                DeptApi deptApi) {
        this.allocationMapper = allocationMapper;
        this.deptApi = deptApi;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FinanceDeptCostAllocationImportRespVO importAllocationList(
            List<FinanceDeptCostAllocationImportExcelVO> rows, String sourceType, Long importerId) {
        if (!FinanceDeptCostAllocationImportSupport.isValidSourceType(sourceType)) {
            throw exception(DEPT_ALLOCATION_SOURCE_TYPE_INVALID);
        }
        String normalizedSourceType = sourceType.trim();
        List<FinanceDeptCostAllocationImportExcelVO> dataRows = skipBlankRows(rows);
        if (dataRows.isEmpty()) {
            throw exception(DEPT_ALLOCATION_IMPORT_EMPTY);
        }

        List<DeptRespDTO> depts = loadEnabledDepts();
        Map<String, List<DeptRespDTO>> deptsByName = FinanceDeptCostAllocationImportSupport.indexByName(depts);
        Map<Long, DeptRespDTO> deptsById = FinanceDeptCostAllocationImportSupport.indexById(depts);

        List<String> errors = new ArrayList<>();
        List<FinanceDeptCostAllocationImportSupport.ParsedRow> parsed = new ArrayList<>(dataRows.size());
        for (int i = 0; i < dataRows.size(); i++) {
            int excelRowNumber = i + 2;
            FinanceDeptCostAllocationImportSupport.ParsedRow[] out =
                    new FinanceDeptCostAllocationImportSupport.ParsedRow[1];
            String err = FinanceDeptCostAllocationImportSupport.validateAndParse(
                    dataRows.get(i), deptsByName, deptsById, out);
            if (err != null) {
                errors.add("第" + excelRowNumber + "行：" + err);
                continue;
            }
            parsed.add(out[0]);
        }
        if (!errors.isEmpty()) {
            throw exception(DEPT_ALLOCATION_IMPORT_INVALID, String.join("；", errors));
        }

        LocalDateTime importTime = LocalDateTime.now();
        Set<String> periods = new LinkedHashSet<>();
        for (FinanceDeptCostAllocationImportSupport.ParsedRow row : parsed) {
            periods.add(row.period());
        }
        for (String period : periods) {
            allocationMapper.deleteByPeriodAndSourceType(period, normalizedSourceType);
        }
        for (FinanceDeptCostAllocationImportSupport.ParsedRow row : parsed) {
            allocationMapper.insert(FinanceDeptCostAllocationDO.builder()
                    .period(row.period())
                    .sourceType(normalizedSourceType)
                    .deptId(row.deptId())
                    .deptName(row.deptName())
                    .amount(row.amount())
                    .remark(row.remark())
                    .importerId(importerId)
                    .importTime(importTime)
                    .build());
        }
        return FinanceDeptCostAllocationImportRespVO.builder()
                .createdCount(parsed.size())
                .build();
    }

    private List<DeptRespDTO> loadEnabledDepts() {
        CommonResult<List<DeptRespDTO>> result = deptApi.getSimpleDeptList();
        List<DeptRespDTO> list = result != null && result.isSuccess() ? result.getData() : null;
        return list == null ? List.of() : list;
    }

    private static List<FinanceDeptCostAllocationImportExcelVO> skipBlankRows(
            List<FinanceDeptCostAllocationImportExcelVO> rows) {
        if (CollUtil.isEmpty(rows)) {
            return List.of();
        }
        List<FinanceDeptCostAllocationImportExcelVO> dataRows = new ArrayList<>();
        for (FinanceDeptCostAllocationImportExcelVO row : rows) {
            if (!FinanceDeptCostAllocationImportSupport.isBlankRow(row)) {
                dataRows.add(row);
            }
        }
        return dataRows;
    }
}
