package cn.iocoder.yudao.module.system.service.mfa.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MfaPendingTotp {
    String factorId;
    String secretManual;
    String otpauthUri;
}
