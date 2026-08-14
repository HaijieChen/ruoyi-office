package cn.iocoder.yudao.framework.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * RPC 服务身份共享密钥（Finance → BPM create-by-business 等）。
 * <p>
 * EXP-87 F1：源码<strong>不</strong>内置可运行的默认 secret；密钥仅来自环境变量/配置注入。
 * privileged 通道启用（{@code enabled=true}）时必须配置强密钥，否则启动失败（fail-closed）。
 * <pre>
 * yudao.rpc.service-identity.secret=${YUDAO_RPC_SERVICE_IDENTITY_SECRET}
 * yudao.rpc.service-identity.enabled=true
 * </pre>
 */
@ConfigurationProperties(prefix = "yudao.rpc.service-identity")
@Validated
@Data
public class RpcServiceIdentityProperties {

    /**
     * HMAC 密钥；Finance 签发、BPM 校验共用。
     * <p>
     * 无字段默认值；未配置时为 null。
     */
    private String secret;

    /**
     * 是否启用 privileged 服务身份通道（create-by-business）。
     * 默认 true：启用则启动时强制校验 secret；设为 false 则关闭通道（不签发/不放行）。
     */
    private Boolean enabled = true;
}
