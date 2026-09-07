package cn.iocoder.yudao.module.bpm.controller.admin.oa;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAPunchCorrectionCreateReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAPunchCorrectionPageReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAPunchCorrectionRespVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOAPunchCorrectionDO;
import cn.iocoder.yudao.module.bpm.service.oa.BpmOAPunchCorrectionService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * OA 补卡申请 Controller
 */
@Tag(name = "管理后台 - OA 补卡申请")
@RestController
@RequestMapping("/bpm/oa/punch-correction")
@Validated
public class BpmOAPunchCorrectionController {

    @Resource
    private BpmOAPunchCorrectionService punchCorrectionService;

    @Resource
    private AdminUserApi adminUserApi;

    @Resource
    private DeptApi deptApi;

    @PostMapping("/create")
    @Operation(summary = "创建补卡申请")
    public CommonResult<Long> createPunchCorrection(@Valid @RequestBody BpmOAPunchCorrectionCreateReqVO createReqVO) {
        return success(punchCorrectionService.createPunchCorrection(getLoginUserId(), createReqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "获得补卡申请")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    public CommonResult<BpmOAPunchCorrectionRespVO> getPunchCorrection(@RequestParam("id") Long id) {
        BpmOAPunchCorrectionDO punch = punchCorrectionService.getPunchCorrection(id, getLoginUserId());
        return success(buildResp(punch));
    }

    @GetMapping("/page")
    @Operation(summary = "获得补卡申请分页")
    public CommonResult<PageResult<BpmOAPunchCorrectionRespVO>> getPunchCorrectionPage(
            @Valid BpmOAPunchCorrectionPageReqVO pageVO) {
        PageResult<BpmOAPunchCorrectionDO> pageResult =
                punchCorrectionService.getPunchCorrectionPage(getLoginUserId(), pageVO);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return success(new PageResult<>(pageResult.getTotal()));
        }
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(
                convertSet(pageResult.getList(), BpmOAPunchCorrectionDO::getUserId));
        Set<Long> deptIds = convertSet(userMap.values(), AdminUserRespDTO::getDeptId);
        Map<Long, DeptRespDTO> deptMap = deptApi.getDeptMap(deptIds);
        return success(new PageResult<>(pageResult.getList().stream()
                .map(item -> buildResp(item, userMap, deptMap))
                .toList(), pageResult.getTotal()));
    }

    @GetMapping("/remaining")
    @Operation(summary = "获得指定补卡日期所在月的剩余次数")
    @Parameter(name = "punchDate", description = "补卡日期", required = true, example = "2026-08-15")
    public CommonResult<Integer> getRemainingCount(
            @RequestParam("punchDate") @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY) LocalDate punchDate) {
        return success(punchCorrectionService.getRemainingCount(getLoginUserId(), punchDate));
    }

    private BpmOAPunchCorrectionRespVO buildResp(BpmOAPunchCorrectionDO punch) {
        if (punch.getUserId() == null) {
            return BeanUtils.toBean(punch, BpmOAPunchCorrectionRespVO.class);
        }
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(Set.of(punch.getUserId()));
        Set<Long> deptIds = convertSet(userMap.values(), AdminUserRespDTO::getDeptId);
        return buildResp(punch, userMap, deptApi.getDeptMap(deptIds));
    }

    private BpmOAPunchCorrectionRespVO buildResp(BpmOAPunchCorrectionDO punch,
                                                 Map<Long, AdminUserRespDTO> userMap,
                                                 Map<Long, DeptRespDTO> deptMap) {
        BpmOAPunchCorrectionRespVO respVO = BeanUtils.toBean(punch, BpmOAPunchCorrectionRespVO.class);
        AdminUserRespDTO user = userMap.get(punch.getUserId());
        if (user != null) {
            respVO.setUserNickname(user.getNickname());
            DeptRespDTO dept = user.getDeptId() == null ? null : deptMap.get(user.getDeptId());
            if (dept != null) {
                respVO.setDeptName(dept.getName());
            }
        }
        return respVO;
    }

}
