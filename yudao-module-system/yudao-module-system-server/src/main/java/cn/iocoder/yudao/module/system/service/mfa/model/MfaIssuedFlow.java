package cn.iocoder.yudao.module.system.service.mfa.model;

import cn.iocoder.yudao.module.system.service.mfa.enums.MfaFlowTokenClass;
import lombok.Builder;
import lombok.Value;

/**
 * 新签发 flow 的一次性返回（含原始 flowToken，仅创建时可见）。
 */
@Value
@Builder
public class MfaIssuedFlow {
    String flowId;
    /** CSPRNG opaque bearer；服务端只存 hash */
    String flowToken;
    MfaFlowTokenClass tokenClass;
    int expiresInSeconds;
}
