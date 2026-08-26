package cn.iocoder.yudao.module.finance.service.common;

import cn.iocoder.yudao.module.hrm.api.employee.EmployeeApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.BUSINESS_STAFF_INVALID;

@Component
public class FinanceBusinessStaffSupport {

    @Resource
    private EmployeeApi employeeApi;

    public Long resolve(Long loginUserId, Long requestedStaffUserId) {
        Long staffUserId = requestedStaffUserId != null ? requestedStaffUserId : loginUserId;
        if (staffUserId == null) {
            throw exception(BUSINESS_STAFF_INVALID);
        }
        if (staffUserId.equals(loginUserId)) {
            return staffUserId;
        }
        Boolean ok = employeeApi.isColleague(loginUserId, staffUserId).getCheckedData();
        if (!Boolean.TRUE.equals(ok)) {
            throw exception(BUSINESS_STAFF_INVALID);
        }
        return staffUserId;
    }
}
