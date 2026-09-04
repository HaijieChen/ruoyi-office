package cn.iocoder.yudao.module.bpm.framework.im;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "yudao.im")
public class ImProperties {

    /**
     * 卡片 HMAC 密钥。空则验签失败。
     */
    private String cardSecret = "";

    private Wecom wecom = new Wecom();
    private Dingtalk dingtalk = new Dingtalk();
    private Feishu feishu = new Feishu();

    @Data
    public static class Wecom {
        private String corpId = "";
        private String secret = "";
        private Integer agentId;
    }

    @Data
    public static class Dingtalk {
        private String appKey = "";
        private String appSecret = "";
        private Long agentId;
    }

    @Data
    public static class Feishu {
        private String appId = "";
        private String appSecret = "";
    }
}
