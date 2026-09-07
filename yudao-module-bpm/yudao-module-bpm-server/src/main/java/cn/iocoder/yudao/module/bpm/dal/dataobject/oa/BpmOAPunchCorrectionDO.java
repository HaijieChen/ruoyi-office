package cn.iocoder.yudao.module.bpm.dal.dataobject.oa;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.module.bpm.enums.OaAttendanceSyncStatusEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * OA 补卡申请 DO
 */
@TableName(value = "bpm_oa_punch_correction", autoResultMap = true)
@KeySequence("bpm_oa_punch_correction_seq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BpmOAPunchCorrectionDO extends BaseDO {

    @TableId
    private Long id;
    /**
     * 申请人的用户编号
     */
    private Long userId;
    /**
     * 补卡日期（额度按该日所在月计次）
     */
    private LocalDate punchDate;
    /**
     * 补卡时间（日期+时刻，可与补卡日期不同）
     */
    private LocalDateTime punchTime;
    /**
     * 补卡事由
     */
    private String reason;
    /**
     * 说明附件 URL 数组
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> attachmentUrls;
    /**
     * 审批结果
     *
     * 枚举 {@link BpmTaskStatusEnum}
     */
    private Integer status;
    /**
     * 对应的流程编号
     */
    private String processInstanceId;
    /**
     * 考勤同步状态
     *
     * 枚举 {@link OaAttendanceSyncStatusEnum}
     */
    private String attendanceSyncStatus;

}
