package cn.iocoder.yudao.framework.common.util.rpc;

/**
 * EXP-87 G1/F2/F3：RPC 服务间调用方身份（不可由调用方随意自报业务信任）。
 */
public final class RpcServiceIdentityConstants {

    /** 调用方服务名 Header（逻辑名，与 spring.application.name 对齐惯例） */
    public static final String HEADER_SERVICE_NAME = "X-Rpc-Service-Name";

    /**
     * 调用方身份令牌 Header：HMAC-SHA256(secret, serviceName + ":" + audience + ":" + timeBucket)。
     * audience 绑定 method + path + BPM 目标服务，防止跨路由/跨服务重放。
     */
    public static final String HEADER_SERVICE_TOKEN = "X-Rpc-Service-Token";

    /** Finance 模块逻辑服务名 */
    public static final String FINANCE_SERVER = "finance-server";

    /** BPM 模块逻辑服务名（与 Feign {@code name} / spring.application.name 对齐） */
    public static final String BPM_SERVER = "bpm-server";

    /** Spring Security 权限：已通过 HMAC 校验的 Finance 服务身份 */
    public static final String AUTHORITY_FINANCE_SERVER = "RPC_SERVICE_FINANCE";

    /** 时间桶秒数（5 分钟），校验时允许 ±1 桶漂移 */
    public static final long TOKEN_BUCKET_SECONDS = 300L;

    /** privileged 路径（规范化后精确匹配） */
    public static final String PATH_BPM_CREATE_BY_BUSINESS =
            "/rpc-api/bpm/process-instance/create-by-business";

    public static final String METHOD_BPM_CREATE_BY_BUSINESS = "POST";

    /**
     * privileged 启动 audience：METHOD + path + @ + BPM 目标服务。
     * 示例：{@code POST /rpc-api/bpm/process-instance/create-by-business@bpm-server}
     */
    public static final String AUDIENCE_BPM_CREATE_BY_BUSINESS =
            METHOD_BPM_CREATE_BY_BUSINESS + " " + PATH_BPM_CREATE_BY_BUSINESS + "@" + BPM_SERVER;

    private RpcServiceIdentityConstants() {
    }

    /**
     * 规范化 path：去 query、去尾部 /（根路径除外）、保证前导 /。
     */
    public static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String p = path.trim();
        int q = p.indexOf('?');
        if (q >= 0) {
            p = p.substring(0, q);
        }
        // 去掉 scheme://host:port 前缀（若 url() 被误用）
        int scheme = p.indexOf("://");
        if (scheme >= 0) {
            int slash = p.indexOf('/', scheme + 3);
            p = slash >= 0 ? p.substring(slash) : "/";
        }
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    /**
     * 构造 audience：METHOD + space + normalizedPath + @ + targetService。
     */
    public static String audience(String httpMethod, String path, String targetService) {
        String m = httpMethod == null ? "" : httpMethod.trim().toUpperCase();
        String p = normalizePath(path);
        String t = targetService == null ? "" : targetService.trim();
        return m + " " + p + "@" + t;
    }

    /**
     * 是否 privileged create-by-business（method + path 精确相等）。
     */
    public static boolean isExactPrivilegedCreateByBusiness(String httpMethod, String path) {
        if (httpMethod == null || path == null) {
            return false;
        }
        return METHOD_BPM_CREATE_BY_BUSINESS.equalsIgnoreCase(httpMethod.trim())
                && PATH_BPM_CREATE_BY_BUSINESS.equals(normalizePath(path));
    }
}
