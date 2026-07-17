package cn.iocoder.yudao.module.bpm.dal.dataobject.definition;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * BPM 表单数据源调用日志 DO
 *
 * @author 宇擎源码
 */
@TableName("bpm_form_data_source_log")
@KeySequence("bpm_form_data_source_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BpmFormDataSourceLogDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 数据源编号
     *
     * 关联 {@link BpmFormDataSourceDO#getId()}
     */
    private Long dataSourceId;
    /**
     * 数据源版本号
     */
    private Integer version;
    /**
     * 表单编号
     */
    private Long formId;
    /**
     * 流程实例编号
     */
    private String processInstanceId;
    /**
     * 用户编号
     */
    private Long userId;
    /**
     * 参数摘要
     */
    private String parameterDigest;
    /**
     * 返回行数
     */
    private Integer rowCount;
    /**
     * 执行耗时（毫秒）
     */
    private Long durationMs;
    /**
     * 是否成功
     */
    private Boolean success;
    /**
     * 错误码
     */
    private String errorCode;

}
