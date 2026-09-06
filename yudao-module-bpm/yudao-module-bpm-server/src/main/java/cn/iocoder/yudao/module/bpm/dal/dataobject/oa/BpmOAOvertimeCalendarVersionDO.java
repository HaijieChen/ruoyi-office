package cn.iocoder.yudao.module.bpm.dal.dataobject.oa;

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

@TableName("bpm_oa_overtime_calendar_version")
@KeySequence("bpm_oa_overtime_calendar_version_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BpmOAOvertimeCalendarVersionDO extends BaseDO {

    public static final String ACTIVE = "ACTIVE";
    public static final String PENDING = "PENDING";
    public static final String REJECTED = "REJECTED";
    public static final String FAILED = "FAILED";
    public static final String NOT_PUBLISHED = "NOT_PUBLISHED";

    @TableId
    private Long id;
    private Integer calendarYear;
    private String source;
    private String sourceUrl;
    private LocalDateTime fetchedAt;
    private String contentHash;
    private String status;
    private Long verifiedBy;
    private LocalDateTime verifiedAt;
    private String rawExcerpt;
    private String parseNote;
    private String diffJson;
    private String notifyFingerprint;
    private String legalHolidaysJson;
    private String makeupWorkdaysJson;
    private String makeupRestDaysJson;
    private String weekendsJson;
    private String festivalsJson;
}
