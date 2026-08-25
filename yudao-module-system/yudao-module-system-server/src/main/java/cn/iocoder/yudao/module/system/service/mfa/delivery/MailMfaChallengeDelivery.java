package cn.iocoder.yudao.module.system.service.mfa.delivery;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.system.service.mail.MailSendService;

import java.util.List;
import java.util.Map;

/**
 * EMAIL 走 MailSendService；其它类型交给 fallback（默认桩）。
 */
public class MailMfaChallengeDelivery implements MfaChallengeDelivery {

    public static final String TEMPLATE_CODE = "mfa-email-code";

    private final MailSendService mailSendService;
    private final MfaChallengeDelivery fallback;

    public MailMfaChallengeDelivery(MailSendService mailSendService, MfaChallengeDelivery fallback) {
        this.mailSendService = mailSendService;
        this.fallback = fallback == null ? new StubMfaChallengeDelivery() : fallback;
    }

    @Override
    public void deliver(String factorType, String destination, String code) {
        if (factorType != null && "EMAIL".equalsIgnoreCase(factorType.trim())) {
            if (destination == null || destination.isBlank()) {
                throw new IllegalArgumentException("email destination blank");
            }
            mailSendService.sendSingleMail(List.of(destination.trim()), null, null,
                    0L, UserTypeEnum.ADMIN.getValue(),
                    TEMPLATE_CODE, Map.of("code", code));
            return;
        }
        fallback.deliver(factorType, destination, code);
    }
}
