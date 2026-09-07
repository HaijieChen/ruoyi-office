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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * OA 加班申请 DO
 */
@TableName(value = "bpm_oa_overtime", autoResultMap = true)
@KeySequence("bpm_oa_overtime_seq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BpmOAOvertimeDO extends BaseDO {

    @TableId
    private Long id;
    /**
     * 申请人的用户编号
     */
    private Long userId;
    /**
     * 加班事由
     */
    private String reason;
    /**
     * 开始时间
     */
    private LocalDateTime startTime;
    /**
     * 结束时间
     */
    private LocalDateTime endTime;
    /**
     * 时长（小时）
     */
    private BigDecimal hours;
    /**
     * 是否法定节假日（bpm_oa_overtime_holiday：true/false）
     */
    private String holiday;
    /**
     * 附件 URL 数组
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
