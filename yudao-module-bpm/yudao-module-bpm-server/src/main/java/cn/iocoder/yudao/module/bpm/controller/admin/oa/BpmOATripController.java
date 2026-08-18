package cn.iocoder.yudao.module.bpm.controller.admin.oa;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOATripCreateReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOATripPageReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOATripRespVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOATripDO;
import cn.iocoder.yudao.module.bpm.service.oa.BpmOATripService;
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
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * OA 出差申请 Controller。create/page/get 仅需登录；query 只做数据范围，不是阻塞注解。
 */
@Tag(name = "管理后台 - OA 出差申请")
@RestController
@RequestMapping("/bpm/oa/trip")
@Validated
public class BpmOATripController {

    @Resource
    private BpmOATripService tripService;

    @Resource
    private AdminUserApi adminUserApi;

    @Resource
    private DeptApi deptApi;

    @PostMapping("/create")
    @Operation(summary = "创建出差申请")
    public CommonResult<Long> createTrip(@Valid @RequestBody BpmOATripCreateReqVO createReqVO) {
        return success(tripService.createTrip(getLoginUserId(), createReqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "获得出差申请")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    public CommonResult<BpmOATripRespVO> getTrip(@RequestParam("id") Long id) {
        BpmOATripDO trip = tripService.getTrip(id, getLoginUserId());
        BpmOATripRespVO respVO = BeanUtils.toBean(trip, BpmOATripRespVO.class);
        fillApplicant(Collections.singletonList(respVO));
        return success(respVO);
    }

    @GetMapping("/page")
    @Operation(summary = "获得出差申请分页")
    public CommonResult<PageResult<BpmOATripRespVO>> getTripPage(@Valid BpmOATripPageReqVO pageVO) {
        PageResult<BpmOATripDO> pageResult = tripService.getTripPage(getLoginUserId(), pageVO);
        PageResult<BpmOATripRespVO> respPage = BeanUtils.toBean(pageResult, BpmOATripRespVO.class);
        fillApplicant(respPage.getList());
        return success(respPage);
    }

    private void fillApplicant(List<BpmOATripRespVO> list) {
        if (CollUtil.isEmpty(list)) {
            return;
        }
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(convertSet(list, BpmOATripRespVO::getUserId));
        Map<Long, DeptRespDTO> deptMap = deptApi.getDeptMap(convertSet(userMap.values(), AdminUserRespDTO::getDeptId));
        for (BpmOATripRespVO vo : list) {
            AdminUserRespDTO user = userMap.get(vo.getUserId());
            if (user == null) {
                continue;
            }
            vo.setUserNickname(user.getNickname());
            DeptRespDTO dept = deptMap.get(user.getDeptId());
            if (dept != null) {
                vo.setDeptName(dept.getName());
            }
        }
    }

}
