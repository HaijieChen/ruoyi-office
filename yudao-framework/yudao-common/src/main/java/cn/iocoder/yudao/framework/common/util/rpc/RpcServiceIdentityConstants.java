package cn.iocoder.yudao.framework.common.util.rpc;

/**
 * EXP-87 G1/F2：RPC 服务间调用方身份（不可由调用方随意自报业务信任）。
 */
public final class RpcServiceIdentityConstants {

    /** 调用方服务名 Header（逻辑名，与 spring.application.name 对齐惯例） */
    public static final String HEADER_SERVICE_NAME = "X-Rpc-Service-Name";

    /**
     * 调用方身份令牌 Header：HMAC-SHA256(secret, serviceName + ":" + audience + ":" + timeBucket)。
     * audience 绑定 HTTP 方法与路径，防止跨路由重放。
     */
    public static final String HEADER_SERVICE_TOKEN = "X-Rpc-Service-Token";

    /** Finance 模块逻辑服务名（与 create-by-business 白名单一致） */
    public static final String FINANCE_SERVER = "finance-server";

    /** BPM 模块逻辑服务名 */
    public static final String BPM_SERVER = "bpm-server";

    /** Spring Security 权限：已通过 HMAC 校验的 Finance 服务身份 */
    public static final String AUTHORITY_FINANCE_SERVER = "RPC_SERVICE_FINANCE";

    /** 时间桶秒数（5 分钟），校验时允许 ±1 桶漂移 */
    public static final long TOKEN_BUCKET_SECONDS = 300L;

    /**
     * privileged 启动 audience：绑定方法 + 路径，不可用其他路由令牌重放。
     * 格式：METHOD + space + absolute path（与 Feign/Servlet path 对齐）
     */
    public static final String AUDIENCE_BPM_CREATE_BY_BUSINESS =
            "POST /rpc-api/bpm/process-instance/create-by-business";

    /** 路径片段：仅该 privileged 路径附带身份头 */
    public static final String PATH_BPM_CREATE_BY_BUSINESS =
            "/rpc-api/bpm/process-instance/create-by-business";

    private RpcServiceIdentityConstants() {
    }

    /**
     * 构造 audience 字符串。
     */
    public static String audience(String httpMethod, String path) {
        String m = httpMethod == null ? "" : httpMethod.trim().toUpperCase();
        String p = path == null ? "" : path.trim();
        // 去掉 query
        int q = p.indexOf('?');
        if (q >= 0) {
            p = p.substring(0, q);
        }
        return m + " " + p;
    }
}
