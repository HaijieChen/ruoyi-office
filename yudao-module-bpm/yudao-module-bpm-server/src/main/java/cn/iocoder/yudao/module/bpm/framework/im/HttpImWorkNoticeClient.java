package cn.iocoder.yudao.module.bpm.framework.im;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.module.system.enums.social.SocialTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 企微 / 钉钉 / 飞书官方 HTTP 投递。凭证为空则跳过。
 */
@Primary
@Component
@Slf4j
public class HttpImWorkNoticeClient implements ImWorkNoticeClient {

    @Resource
    private ImHttpPoster imHttpPoster;
    @Resource
    private ImProperties imProperties;

    private final ConcurrentHashMap<SocialTypeEnum, String> tokenCache = new ConcurrentHashMap<>();

    @Override
    public void send(SocialTypeEnum socialType, String openid, String title, String url) {
        if (socialType == SocialTypeEnum.WECHAT_ENTERPRISE) {
            sendWecom(openid, title, url);
        } else if (socialType == SocialTypeEnum.DINGTALK) {
            sendDingtalk(openid, title, url);
        } else if (socialType == SocialTypeEnum.FEISHU) {
            sendFeishu(openid, title, url);
        } else {
            log.info("[im-notice] skip unsupported type={}", socialType);
        }
    }

    private void sendWecom(String openid, String title, String url) {
        ImProperties.Wecom cfg = imProperties.getWecom();
        if (!StringUtils.hasText(cfg.getCorpId()) || !StringUtils.hasText(cfg.getSecret()) || cfg.getAgentId() == null) {
            log.info("[im-notice] wecom skipped: missing credentials");
            return;
        }
        String token = tokenCache.computeIfAbsent(SocialTypeEnum.WECHAT_ENTERPRISE, t -> {
            String resp = imHttpPoster.get("https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid="
                    + cfg.getCorpId() + "&corpsecret=" + cfg.getSecret());
            return JSONUtil.parseObj(resp).getStr("access_token");
        });
        JSONObject textcard = new JSONObject();
        textcard.set("title", title);
        textcard.set("description", title);
        textcard.set("url", url);
        textcard.set("btntxt", "详情");
        JSONObject body = new JSONObject();
        body.set("touser", openid);
        body.set("msgtype", "textcard");
        body.set("agentid", cfg.getAgentId());
        body.set("textcard", textcard);
        imHttpPoster.postJson("https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=" + token,
                body.toString());
    }

    private void sendDingtalk(String openid, String title, String url) {
        ImProperties.Dingtalk cfg = imProperties.getDingtalk();
        if (!StringUtils.hasText(cfg.getAppKey()) || !StringUtils.hasText(cfg.getAppSecret()) || cfg.getAgentId() == null) {
            log.info("[im-notice] dingtalk skipped: missing credentials");
            return;
        }
        String token = tokenCache.computeIfAbsent(SocialTypeEnum.DINGTALK, t -> {
            String resp = imHttpPoster.get("https://oapi.dingtalk.com/gettoken?appkey="
                    + cfg.getAppKey() + "&appsecret=" + cfg.getAppSecret());
            return JSONUtil.parseObj(resp).getStr("access_token");
        });
        JSONObject card = new JSONObject();
        card.set("title", title);
        card.set("markdown", title);
        card.set("single_title", "详情");
        card.set("single_url", url);
        JSONObject msg = new JSONObject();
        msg.set("msgtype", "action_card");
        msg.set("action_card", card);
        JSONObject body = new JSONObject();
        body.set("agent_id", cfg.getAgentId());
        body.set("userid_list", openid);
        body.set("msg", msg);
        imHttpPoster.postJson("https://oapi.dingtalk.com/topapi/message/corpconversation/asyncsend_v2?access_token=" + token,
                body.toString());
    }

    private void sendFeishu(String openid, String title, String url) {
        ImProperties.Feishu cfg = imProperties.getFeishu();
        if (!StringUtils.hasText(cfg.getAppId()) || !StringUtils.hasText(cfg.getAppSecret())) {
            log.info("[im-notice] feishu skipped: missing credentials");
            return;
        }
        String token = tokenCache.computeIfAbsent(SocialTypeEnum.FEISHU, t -> {
            JSONObject req = new JSONObject();
            req.set("app_id", cfg.getAppId());
            req.set("app_secret", cfg.getAppSecret());
            String resp = imHttpPoster.postJson("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal",
                    req.toString());
            return JSONUtil.parseObj(resp).getStr("tenant_access_token");
        });
        JSONObject content = new JSONObject();
        content.set("text", title + " " + url);
        JSONObject body = new JSONObject();
        body.set("receive_id", openid);
        body.set("msg_type", "text");
        body.set("content", content.toString());
        imHttpPoster.postJson("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=open_id",
                body.toString(), "Bearer " + token);
    }
}
