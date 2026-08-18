package cn.iocoder.yudao.module.bpm.controller.admin.oa;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAOutingCreateReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAOutingPageReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAOutingRespVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOAOutingDO;
import cn.iocoder.yudao.module.bpm.service.oa.BpmOAOutingService;
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
 * OA 外出申请 Controller
 */
@Tag(name = "管理后台 - OA 外出申请")
@RestController
@RequestMapping("/bpm/oa/outing")
@Validated
public class BpmOAOutingController {

    @Resource
    private BpmOAOutingService outingService;

    @Resource
    private AdminUserApi adminUserApi;

    @Resource
    private DeptApi deptApi;

    @PostMapping("/create")
    @Operation(summary = "创建外出申请")
    public CommonResult<Long> createOuting(@Valid @RequestBody BpmOAOutingCreateReqVO createReqVO) {
        return success(outingService.createOuting(getLoginUserId(), createReqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "获得外出申请")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    public CommonResult<BpmOAOutingRespVO> getOuting(@RequestParam("id") Long id) {
        BpmOAOutingDO outing = outingService.getOuting(id, getLoginUserId());
        return success(buildResp(outing));
    }

    @GetMapping("/page")
    @Operation(summary = "获得外出申请分页")
    public CommonResult<PageResult<BpmOAOutingRespVO>> getOutingPage(@Valid BpmOAOutingPageReqVO pageVO) {
        PageResult<BpmOAOutingDO> pageResult = outingService.getOutingPage(getLoginUserId(), pageVO);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return success(new PageResult<>(pageResult.getTotal()));
        }
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(
                convertSet(pageResult.getList(), BpmOAOutingDO::getUserId));
        Set<Long> deptIds = convertSet(userMap.values(), AdminUserRespDTO::getDeptId);
        Map<Long, DeptRespDTO> deptMap = deptApi.getDeptMap(deptIds);
        return success(new PageResult<>(pageResult.getList().stream()
                .map(item -> buildResp(item, userMap, deptMap))
                .toList(), pageResult.getTotal()));
    }

    private BpmOAOutingRespVO buildResp(BpmOAOutingDO outing) {
        if (outing.getUserId() == null) {
            return BeanUtils.toBean(outing, BpmOAOutingRespVO.class);
        }
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(Set.of(outing.getUserId()));
        Set<Long> deptIds = convertSet(userMap.values(), AdminUserRespDTO::getDeptId);
        return buildResp(outing, userMap, deptApi.getDeptMap(deptIds));
    }

    private BpmOAOutingRespVO buildResp(BpmOAOutingDO outing,
                                        Map<Long, AdminUserRespDTO> userMap,
                                        Map<Long, DeptRespDTO> deptMap) {
        BpmOAOutingRespVO respVO = BeanUtils.toBean(outing, BpmOAOutingRespVO.class);
        AdminUserRespDTO user = userMap.get(outing.getUserId());
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
