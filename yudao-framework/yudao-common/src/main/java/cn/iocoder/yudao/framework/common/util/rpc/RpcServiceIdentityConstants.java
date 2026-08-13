package cn.iocoder.yudao.framework.common.util.rpc;

/**
 * EXP-87 G1：RPC 服务间调用方身份（不可由调用方随意自报业务信任）。
 */
public final class RpcServiceIdentityConstants {

    /** 调用方服务名 Header（逻辑名，与 spring.application.name 对齐惯例） */
    public static final String HEADER_SERVICE_NAME = "X-Rpc-Service-Name";

    /**
     * 调用方身份令牌 Header：HMAC-SHA256(secret, serviceName + ":" + timeBucket)。
     * 伪造明文服务名而无有效 token 一律拒绝。
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

    private RpcServiceIdentityConstants() {
    }
}
