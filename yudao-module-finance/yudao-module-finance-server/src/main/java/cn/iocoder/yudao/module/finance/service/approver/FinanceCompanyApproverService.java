package cn.iocoder.yudao.module.finance.service.approver;

import cn.iocoder.yudao.module.finance.controller.admin.approver.vo.FinanceCompanyApproverRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.approver.vo.FinanceCompanyApproverSaveReqVO;

import java.util.List;
import java.util.Set;

public interface FinanceCompanyApproverService {

    List<FinanceCompanyApproverRespVO> list();

    FinanceCompanyApproverRespVO getByCompany(Long entityCompanyDeptId);

    void save(FinanceCompanyApproverSaveReqVO reqVO);

    Set<Long> listUserIdsByCompany(Long entityCompanyDeptId);
}
