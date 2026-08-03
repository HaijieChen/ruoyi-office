package cn.iocoder.yudao.module.finance.service.common;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.*;

/**
 * 组织架构「公司」解析（与开票公司同源：company-simple-list）。
 */
@Component
public class FinanceEntityCompanyResolver {

    /** 组织类型：公司（与 system OrgTypeEnum.COMPANY 一致） */
    public static final String ORG_TYPE_COMPANY = "1";

    @Resource
    private DeptApi deptApi;

    public record ResolvedCompany(Long deptId, String name) {
    }

    /**
     * 按 deptId 校验为启用公司，返回服务端名称快照。
     */
    public ResolvedCompany requireByDeptId(Long deptId) {
        if (deptId == null) {
            throw exception(ENTITY_COMPANY_REQUIRED);
        }
        DeptRespDTO dept = deptApi.getDept(deptId).getCheckedData();
        if (dept == null
                || !Objects.equals(CommonStatusEnum.ENABLE.getStatus(), dept.getStatus())
                || !isCompany(dept)) {
            throw exception(ENTITY_COMPANY_INVALID);
        }
        return new ResolvedCompany(dept.getId(), dept.getName());
    }

    /**
     * 导入：名称精确匹配启用公司；0 命中 / 多命中返回错误文案（不抛异常，供行级 failureRows）。
     * 大批量导入请先 {@link #loadEnabledCompanies()} 再调用
     * {@link #matchByNameOrError(String, ResolvedCompany[], List)} 避免每行 RPC。
     */
    public String matchByNameOrError(String companyName, ResolvedCompany[] out) {
        return matchByNameOrError(companyName, out, loadEnabledCompanies());
    }

    /** 加载启用公司 simple-list（导入入口缓存一次）。 */
    public List<DeptRespDTO> loadEnabledCompanies() {
        List<DeptRespDTO> companies = deptApi.getCompanySimpleList().getCheckedData();
        return companies == null ? List.of() : companies;
    }

    /**
     * 使用预加载公司列表做名称精确匹配。
     */
    public String matchByNameOrError(String companyName, ResolvedCompany[] out,
                                     List<DeptRespDTO> companies) {
        if (StrUtil.isBlank(companyName)) {
            return "主体公司不能为空";
        }
        String name = companyName.trim();
        List<DeptRespDTO> source = companies == null ? List.of() : companies;
        List<DeptRespDTO> hits = new ArrayList<>();
        for (DeptRespDTO c : source) {
            if (c != null && name.equals(StrUtil.trim(c.getName()))) {
                hits.add(c);
            }
        }
        if (hits.isEmpty()) {
            return "主体公司不存在或未启用";
        }
        if (hits.size() > 1) {
            return "主体公司名称重复，请改用唯一名称";
        }
        DeptRespDTO hit = hits.get(0);
        out[0] = new ResolvedCompany(hit.getId(), hit.getName());
        return null;
    }

    private static boolean isCompany(DeptRespDTO dept) {
        return ORG_TYPE_COMPANY.equals(String.valueOf(dept.getOrgType()));
    }
}
