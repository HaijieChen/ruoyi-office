package cn.iocoder.yudao.module.bpm.framework.datasource;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * BPM 动态表单数据源 — 服务端上下文解析器
 *
 * 注入服务端保留参数（tenantId / userId / deptId / companyId），
 * 拒绝浏览器端对保留参数的覆盖。绝不记录原始参数值。
 *
 * <p><b>类型说明：</b>
 * <ul>
 *   <li>tenantId — {@code Long}，来自 {@code LoginUser#getTenantId()}</li>
 *   <li>userId — {@code Long}，来自 {@code LoginUser#getId()}</li>
 *   <li>deptId — {@code String}，来自 {@code LoginUser#getInfo()} 返回的
 *       {@code Map<String, String>}，因此注入类型为 String 而非 Long。
 *       下游 SQL 参数绑定时需注意这一类型差异。</li>
 *   <li>companyId — {@code Long}，来自 {@code LoginUser#getContext("companyId", Long.class)}</li>
 * </ul>
 */
public class BpmFormDataSourceContextResolver {

    /**
     * 服务端保留参数名，浏览器端不允许覆盖
     */
    private static final Set<String> RESERVED_PARAMS = Set.of(
            "tenantId", "userId", "deptId", "companyId", "sharedInstanceIds"
    );

    /**
     * 解析请求参数 + 服务端上下文
     *
     * @param request 浏览器端传入的参数（可为 null）
     * @param user    当前登录用户
     * @return 合并后的参数（服务端保留参数 + 客户端自定义参数）
     * @throws ServiceException 当客户端尝试覆盖保留参数时
     */
    public Map<String, Object> resolve(Map<String, Object> request, LoginUser user) {
        Map<String, Object> result = new HashMap<>();

        // 1. 检查浏览器端是否试图覆盖保留参数
        if (request != null) {
            for (String key : request.keySet()) {
                if (RESERVED_PARAMS.contains(key)) {
                    // 不记录原始参数值，只记录参数名
                    throw ServiceExceptionUtil.exception(ErrorCodeConstants.BPM_DATA_SOURCE_PARAM_RESERVED, key);
                }
            }
            // 2. 合并客户端参数
            result.putAll(request);
        }

        // 3. 注入服务端上下文（保留参数，强制覆盖）
        result.put("tenantId", user.getTenantId());
        result.put("userId", user.getId());
        result.put("deptId", user.getInfo() != null ? user.getInfo().get("deptId") : null);
        result.put("companyId", user.getContext("companyId", Long.class));

        return result;
    }
}
