package cn.iocoder.yudao.module.bpm.dal.dataobject.task;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@TableName(value = "bpm_process_instance_share", autoResultMap = true)
@KeySequence("bpm_process_instance_share_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BpmProcessInstanceShareDO extends BaseDO {

    @TableId
    private Long id;
    private String processInstanceId;
    private Long startUserId;
    private Long recipientUserId;
    private String processInstanceName;
    /** 空表示有效分享 */
    private LocalDateTime revokedAt;
}
