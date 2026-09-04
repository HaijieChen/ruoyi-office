package cn.iocoder.yudao.module.bpm.framework.im;

import cn.iocoder.yudao.module.system.enums.social.SocialTypeEnum;
import lombok.extern.slf4j.Slf4j;

/**
 * 无 HTTP 凭证时的降级日志。正式投递见 {@link HttpImWorkNoticeClient}。
 */
@Slf4j
public class LoggingImWorkNoticeClient implements ImWorkNoticeClient {

    @Override
    public void send(SocialTypeEnum socialType, String openid, String title, String url) {
        log.info("[im-notice] type={} openid={} title={} url={}", socialType, openid, title, url);
    }
}
