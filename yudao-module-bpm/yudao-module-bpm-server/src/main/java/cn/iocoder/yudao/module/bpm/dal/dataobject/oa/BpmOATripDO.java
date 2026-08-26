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
 * OA 出差申请 DO
 */
@TableName(value = "bpm_oa_business_trip", autoResultMap = true)
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
     * 出差类型：1市内 2省内 3省外 4国外（历史，新单不填）
     */
    private Integer type;
    /** 目的地城市（费用报销住宿城市） */
    private String destination;
    /** 出发城市 */
    private String originCity;
    /** 业务类型：1洽谈 2活动 3其他 */
    private Integer bizType;
    private String transport;
    private String hotelBooking;
    private String partyName;
    private String address;
    private String contactInfo;
    private String needOutput;
    private String hasCarriageFee;
    private String remark;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> attachmentUrls;
    private String reason;
    private Long companionUserId;
    /** 同行人员用户编号，逗号分隔 */
    @TableField(typeHandler = cn.iocoder.yudao.framework.mybatis.core.type.LongListTypeHandler.class)
    private java.util.List<Long> companionUserIds;
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
