package cn.iocoder.yudao.module.system.service.mfa.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class MfaFactorBinding {
    String factorId;
    String type;
    String status;
    String secretOrDestination;
    String label;
    String masked;
    long lastUsedStep;
}
