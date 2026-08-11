package cn.iocoder.yudao.module.hrm.dal.dataobject.employee;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 入职资料文件 claim（一次性、绑定租户/上传者/用途）
 */
@TableName("hrm_onboarding_file_claim")
@KeySequence("hrm_onboarding_file_claim_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingFileClaimDO extends BaseDO {

    public static final String PURPOSE = "hrm-onboarding";

    @TableId
    private Long id;

    /** 客户端持有的 claim token（UUID） */
    private String claimToken;

    /** 关联 infra_file.id */
    private Long fileId;

    /** 上传者用户 ID */
    private Long uploaderUserId;

    /** 固定 hrm-onboarding */
    private String purpose;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 消费时间（绑定到员工档案后） */
    private LocalDateTime consumedAt;

    /** 消费到的员工 ID */
    private Long consumedEmployeeId;

}
