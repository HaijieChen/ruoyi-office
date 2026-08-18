package cn.iocoder.yudao.module.bpm.dal.dataobject.oa;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.module.bpm.enums.OaAttendanceSyncStatusEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * OA 出差申请 DO
 */
@TableName("bpm_oa_business_trip")
@KeySequence("bpm_oa_business_trip_seq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BpmOATripDO extends BaseDO {

    @TableId
    private Long id;
    /**
     * 申请人的用户编号
     */
    private Long userId;
    /**
     * 出差类型：1市内 2省内 3省外 4国外
     */
    private Integer type;
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
