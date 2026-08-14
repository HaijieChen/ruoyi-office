package cn.iocoder.yudao.module.system.service.mfa.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MfaFactorView {
    String id;
    String type;
    String status;
    String label;
    String maskedTarget;
}
