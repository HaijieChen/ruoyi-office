package cn.iocoder.yudao.module.bpm.controller.admin.oa;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAOvertimeCreateReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAOvertimePageReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAOvertimeRespVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOAOvertimeDO;
import cn.iocoder.yudao.module.bpm.service.oa.BpmOAOvertimeService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * OA 加班申请 Controller
 */
@Tag(name = "管理后台 - OA 加班申请")
@RestController
@RequestMapping("/bpm/oa/overtime")
@Validated
public class BpmOAOvertimeController {

    @Resource
    private BpmOAOvertimeService overtimeService;

    @Resource
    private AdminUserApi adminUserApi;

    @Resource
    private DeptApi deptApi;

    @PostMapping("/create")
    @Operation(summary = "创建加班申请")
    public CommonResult<Long> createOvertime(@Valid @RequestBody BpmOAOvertimeCreateReqVO createReqVO) {
        return success(overtimeService.createOvertime(getLoginUserId(), createReqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "获得加班申请")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    public CommonResult<BpmOAOvertimeRespVO> getOvertime(@RequestParam("id") Long id) {
        BpmOAOvertimeDO overtime = overtimeService.getOvertime(id, getLoginUserId());
        return success(buildResp(overtime));
    }

    @GetMapping("/page")
    @Operation(summary = "获得加班申请分页")
    public CommonResult<PageResult<BpmOAOvertimeRespVO>> getOvertimePage(@Valid BpmOAOvertimePageReqVO pageVO) {
        PageResult<BpmOAOvertimeDO> pageResult = overtimeService.getOvertimePage(getLoginUserId(), pageVO);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return success(new PageResult<>(pageResult.getTotal()));
        }
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(
                convertSet(pageResult.getList(), BpmOAOvertimeDO::getUserId));
        Set<Long> deptIds = convertSet(userMap.values(), AdminUserRespDTO::getDeptId);
        Map<Long, DeptRespDTO> deptMap = deptApi.getDeptMap(deptIds);
        return success(new PageResult<>(pageResult.getList().stream()
                .map(item -> buildResp(item, userMap, deptMap))
                .toList(), pageResult.getTotal()));
    }

    private BpmOAOvertimeRespVO buildResp(BpmOAOvertimeDO overtime) {
        if (overtime.getUserId() == null) {
            return BeanUtils.toBean(overtime, BpmOAOvertimeRespVO.class);
        }
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(Set.of(overtime.getUserId()));
        Set<Long> deptIds = convertSet(userMap.values(), AdminUserRespDTO::getDeptId);
        return buildResp(overtime, userMap, deptApi.getDeptMap(deptIds));
    }

    private BpmOAOvertimeRespVO buildResp(BpmOAOvertimeDO overtime,
                                          Map<Long, AdminUserRespDTO> userMap,
                                          Map<Long, DeptRespDTO> deptMap) {
        BpmOAOvertimeRespVO respVO = BeanUtils.toBean(overtime, BpmOAOvertimeRespVO.class);
        AdminUserRespDTO user = userMap.get(overtime.getUserId());
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
