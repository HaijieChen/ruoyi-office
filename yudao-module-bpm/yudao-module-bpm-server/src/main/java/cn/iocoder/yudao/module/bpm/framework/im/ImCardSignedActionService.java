package cn.iocoder.yudao.module.bpm.framework.im;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.system.api.social.SocialUserApi;
import cn.iocoder.yudao.module.system.api.social.dto.SocialUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class ImCardSignedActionService {

    @Resource
    private ImProperties imProperties;
    @Resource
    private SocialUserApi socialUserApi;
    @Resource
    private ImCardActionService imCardActionService;

    public ImCardActionResult handle(Integer socialType, String openid, long timestamp, String signature,
                                     String action, String eventId, ImCardTaskSnapshot snapshot) {
        if (!ImCardSignature.verify(imProperties.getCardSecret(), timestamp, eventId, socialType,
                openid, action, signature, System.currentTimeMillis())) {
            return ImCardActionResult.forbidden("签名无效");
        }
        SocialUserRespDTO social = socialUserApi.getSocialUserByOpenid(
                UserTypeEnum.ADMIN.getValue(), socialType, openid).getData();
        if (social == null || social.getUserId() == null) {
            return ImCardActionResult.forbidden("未绑定 OA 账号");
        }
        return imCardActionService.handle(social.getUserId(), action, snapshot, eventId);
    }
}
