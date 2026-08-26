package cn.iocoder.yudao.module.hrm.api.employee;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.hrm.api.employee.dto.EmployeeColleagueRespDTO;
import cn.iocoder.yudao.module.hrm.api.employee.dto.EmployeeUpdateReqDTO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.EmployeeDO;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeMapper;
import cn.iocoder.yudao.module.hrm.service.employee.EmployeeService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController // 提供 RESTful API 接口，给 Feign 调用
@Validated
public class EmployeeApiImpl implements EmployeeApi {

    @Resource
    private EmployeeMapper employeeMapper;
    @Resource
    private EmployeeService employeeService;

    @Override
    public CommonResult<Boolean> updateEmployeeByUserId(Long userId, EmployeeUpdateReqDTO updateReqDTO) {
        EmployeeDO employee = employeeMapper.selectByUserId(userId);
        if (employee == null) {
            return success(false);
        }
        // 只有已生成用户的员工才需要同步更新
        if (employee.getUserGenerated() == null || !employee.getUserGenerated()) {
            return success(false);
        }

        EmployeeDO updateObj = new EmployeeDO();
        updateObj.setId(employee.getId());
        updateObj.setName(updateReqDTO.getName());
        updateObj.setMobile(updateReqDTO.getMobile());
        updateObj.setEmail(updateReqDTO.getEmail());
        updateObj.setSex(updateReqDTO.getSex());
        updateObj.setAvatar(updateReqDTO.getAvatar());
        updateObj.setDeptId(updateReqDTO.getDeptId());
        updateObj.setRemark(updateReqDTO.getRemark());
        employeeMapper.updateById(updateObj);

        return success(true);
    }

    @Override
    public CommonResult<Boolean> updateUserGeneratedStatus(Long userId, Boolean userGenerated) {
        EmployeeDO employee = employeeMapper.selectByUserId(userId);
        if (employee == null) {
            return success(false);
        }

        EmployeeDO updateObj = new EmployeeDO();
        updateObj.setId(employee.getId());
        updateObj.setUserGenerated(userGenerated);
        updateObj.setUserId(userGenerated ? userId : null);
        employeeMapper.updateById(updateObj);

        return success(true);
    }

    @Override
    public CommonResult<List<EmployeeColleagueRespDTO>> listColleagues(Long userId) {
        return success(employeeService.listColleaguesByUserId(userId));
    }

    @Override
    public CommonResult<Boolean> isColleague(Long userId, Long staffUserId) {
        if (userId == null || staffUserId == null) {
            return success(false);
        }
        if (userId.equals(staffUserId)) {
            return success(true);
        }
        return success(employeeService.listColleaguesByUserId(userId).stream()
                .anyMatch(item -> staffUserId.equals(item.getUserId())));
    }

}

