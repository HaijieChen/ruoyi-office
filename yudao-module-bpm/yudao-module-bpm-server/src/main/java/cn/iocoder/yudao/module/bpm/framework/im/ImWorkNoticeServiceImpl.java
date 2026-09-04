package cn.iocoder.yudao.module.bpm.framework.im;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenTaskCreatedReqDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.social.SocialUserApi;
import cn.iocoder.yudao.module.system.api.social.dto.SocialUserRespDTO;
import cn.iocoder.yudao.module.system.enums.permission.RoleCodeEnum;
import cn.iocoder.yudao.module.system.enums.social.SocialTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ImWorkNoticeServiceImpl implements ImWorkNoticeService {

    private static final List<SocialTypeEnum> IM_TYPES = List.of(
            SocialTypeEnum.WECHAT_ENTERPRISE,
            SocialTypeEnum.DINGTALK,
            SocialTypeEnum.FEISHU
    );

    @Resource
    private PermissionApi permissionApi;
    @Resource
    private SocialUserApi socialUserApi;
    @Resource
    private ImWorkNoticeClient imWorkNoticeClient;

    @Override
    public int notifyTaskAssigned(BpmMessageSendWhenTaskCreatedReqDTO reqDTO) {
        int sent = 0;
        sent += sendToUser(reqDTO.getAssigneeUserId(),
                reqDTO.getProcessInstanceName() + "：" + reqDTO.getTaskName(),
                buildTodoUrl(reqDTO.getProcessInstanceId(), reqDTO.getTaskId()));
        if (reqDTO.getStartUserId() != null
                && !reqDTO.getStartUserId().equals(reqDTO.getAssigneeUserId())) {
            sent += sendToUser(reqDTO.getStartUserId(),
                    "进度：" + reqDTO.getProcessInstanceName(),
                    buildTodoUrl(reqDTO.getProcessInstanceId(), reqDTO.getTaskId()));
        }
        return sent;
    }

    int sendToUser(Long userId, String title, String url) {
        if (userId == null) {
            return 0;
        }
        try {
            Boolean superAdmin = permissionApi.hasAnyRoles(userId, RoleCodeEnum.SUPER_ADMIN.getCode()).getData();
            if (Boolean.TRUE.equals(superAdmin)) {
                return 0;
            }
        } catch (Exception ex) {
            log.warn("[im-notice] skip user {} role check failed", userId, ex);
            return 0;
        }
        int sent = 0;
        for (SocialTypeEnum type : IM_TYPES) {
            try {
                SocialUserRespDTO social = socialUserApi.getSocialUserByUserId(
                        UserTypeEnum.ADMIN.getValue(), userId, type.getType()).getData();
                if (social == null || social.getOpenid() == null) {
                    continue;
                }
                imWorkNoticeClient.send(type, social.getOpenid(), title, url);
                sent++;
            } catch (Exception ex) {
                log.warn("[im-notice] send {} to user {} failed", type, userId, ex);
            }
        }
        return sent;
    }

    static String buildTodoUrl(String processInstanceId, String taskId) {
        StringBuilder url = new StringBuilder("/im/approval/silent-login?id=").append(processInstanceId);
        if (taskId != null) {
            url.append("&taskId=").append(taskId);
        }
        return url.toString();
    }
}
