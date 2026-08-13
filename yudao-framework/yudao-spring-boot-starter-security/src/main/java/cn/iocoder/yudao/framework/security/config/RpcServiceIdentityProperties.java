package cn.iocoder.yudao.framework.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * RPC 服务身份共享密钥（Finance → BPM create-by-business 等）。
 * <p>
 * 生产务必通过环境变量覆盖默认值。
 */
@ConfigurationProperties(prefix = "yudao.rpc.service-identity")
@Validated
@Data
public class RpcServiceIdentityProperties {

    /**
     * HMAC 密钥；Finance 签发、BPM 校验共用。
     */
    private String secret = "yudao-rpc-service-identity-dev-only";

    /**
     * 是否启用服务身份校验（create-by-business 等 privileged 路径）。默认 true。
     */
    private Boolean enabled = true;
}
