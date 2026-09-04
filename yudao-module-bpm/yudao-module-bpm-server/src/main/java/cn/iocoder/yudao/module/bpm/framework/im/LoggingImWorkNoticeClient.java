package cn.iocoder.yudao.module.bpm.framework.im;

import cn.iocoder.yudao.module.system.enums.social.SocialTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 默认投递：记录日志。真实企微/钉钉/飞书适配器后续替换。
 */
@Component
@Slf4j
public class LoggingImWorkNoticeClient implements ImWorkNoticeClient {

    @Override
    public void send(SocialTypeEnum socialType, String openid, String title, String url) {
        log.info("[im-notice] type={} openid={} title={} url={}", socialType, openid, title, url);
    }
}
