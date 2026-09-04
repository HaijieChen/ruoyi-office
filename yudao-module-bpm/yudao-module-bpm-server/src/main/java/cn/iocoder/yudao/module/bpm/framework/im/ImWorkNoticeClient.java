package cn.iocoder.yudao.module.bpm.framework.im;

import cn.iocoder.yudao.module.system.enums.social.SocialTypeEnum;

/**
 * 向企微 / 钉钉 / 飞书投递工作通知。平台适配器实现本接口。
 */
public interface ImWorkNoticeClient {

    void send(SocialTypeEnum socialType, String openid, String title, String url);
}
