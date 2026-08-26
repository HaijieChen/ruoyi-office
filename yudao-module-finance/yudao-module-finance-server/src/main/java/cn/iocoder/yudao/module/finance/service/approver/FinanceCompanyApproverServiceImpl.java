package cn.iocoder.yudao.module.finance.service.approver;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.finance.controller.admin.approver.vo.FinanceCompanyApproverRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.approver.vo.FinanceCompanyApproverSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.approver.FinanceCompanyApproverDO;
import cn.iocoder.yudao.module.finance.dal.mysql.approver.FinanceCompanyApproverMapper;
import cn.iocoder.yudao.module.finance.service.common.FinanceEntityCompanyResolver;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.COMPANY_APPROVER_USER_INVALID;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.COMPANY_APPROVER_USER_REQUIRED;

@Service
@Validated
public class FinanceCompanyApproverServiceImpl implements FinanceCompanyApproverService {

    private final FinanceCompanyApproverMapper mapper;
    private final FinanceEntityCompanyResolver entityCompanyResolver;
    private final AdminUserApi adminUserApi;

    public FinanceCompanyApproverServiceImpl(FinanceCompanyApproverMapper mapper,
                                             FinanceEntityCompanyResolver entityCompanyResolver,
                                             AdminUserApi adminUserApi) {
        this.mapper = mapper;
        this.entityCompanyResolver = entityCompanyResolver;
        this.adminUserApi = adminUserApi;
    }

    @Override
    public List<FinanceCompanyApproverRespVO> list() {
        List<DeptRespDTO> companies = entityCompanyResolver.loadEnabledCompanies();
        List<FinanceCompanyApproverDO> rows = mapper.selectList();
        Map<Long, List<FinanceCompanyApproverDO>> byCompany = rows.stream()
                .collect(Collectors.groupingBy(FinanceCompanyApproverDO::getEntityCompanyDeptId));
        Set<Long> userIds = rows.stream().map(FinanceCompanyApproverDO::getUserId).collect(Collectors.toSet());
        Map<Long, AdminUserRespDTO> userMap = CollUtil.isEmpty(userIds)
                ? Map.of()
                : adminUserApi.getUserMap(userIds);
        List<FinanceCompanyApproverRespVO> out = new ArrayList<>();
        for (DeptRespDTO company : companies) {
            if (company == null || company.getId() == null) {
                continue;
            }
            out.add(toResp(company.getId(), company.getName(),
                    byCompany.getOrDefault(company.getId(), List.of()), userMap));
        }
        return out;
    }

    @Override
    public FinanceCompanyApproverRespVO getByCompany(Long entityCompanyDeptId) {
        FinanceEntityCompanyResolver.ResolvedCompany company =
                entityCompanyResolver.requireByDeptId(entityCompanyDeptId);
        List<FinanceCompanyApproverDO> rows = mapper.selectListByCompany(entityCompanyDeptId);
        Set<Long> userIds = rows.stream().map(FinanceCompanyApproverDO::getUserId).collect(Collectors.toSet());
        Map<Long, AdminUserRespDTO> userMap = CollUtil.isEmpty(userIds)
                ? Map.of()
                : adminUserApi.getUserMap(userIds);
        return toResp(company.deptId(), company.name(), rows, userMap);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FinanceCompanyApproverSaveReqVO reqVO) {
        entityCompanyResolver.requireByDeptId(reqVO.getEntityCompanyDeptId());
        List<Long> userIds = reqVO.getUserIds() == null ? List.of() : reqVO.getUserIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            throw exception(COMPANY_APPROVER_USER_REQUIRED);
        }
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(userIds);
        for (Long userId : userIds) {
            AdminUserRespDTO user = userMap.get(userId);
            if (user == null || !Integer.valueOf(CommonStatusEnum.ENABLE.getStatus()).equals(user.getStatus())) {
                throw exception(COMPANY_APPROVER_USER_INVALID);
            }
        }
        mapper.deleteByCompany(reqVO.getEntityCompanyDeptId());
        for (Long userId : userIds) {
            mapper.insert(FinanceCompanyApproverDO.builder()
                    .entityCompanyDeptId(reqVO.getEntityCompanyDeptId())
                    .userId(userId)
                    .build());
        }
    }

    @Override
    public Set<Long> listUserIdsByCompany(Long entityCompanyDeptId) {
        if (entityCompanyDeptId == null) {
            return Set.of();
        }
        return mapper.selectListByCompany(entityCompanyDeptId).stream()
                .map(FinanceCompanyApproverDO::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static FinanceCompanyApproverRespVO toResp(Long companyId, String companyName,
                                                       List<FinanceCompanyApproverDO> rows,
                                                       Map<Long, AdminUserRespDTO> userMap) {
        FinanceCompanyApproverRespVO vo = new FinanceCompanyApproverRespVO();
        vo.setEntityCompanyDeptId(companyId);
        vo.setEntityCompanyName(companyName);
        List<Long> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (FinanceCompanyApproverDO row : rows) {
            ids.add(row.getUserId());
            AdminUserRespDTO user = userMap.get(row.getUserId());
            names.add(user != null ? user.getNickname() : String.valueOf(row.getUserId()));
        }
        vo.setUserIds(ids);
        vo.setUserNames(names);
        return vo;
    }
}
