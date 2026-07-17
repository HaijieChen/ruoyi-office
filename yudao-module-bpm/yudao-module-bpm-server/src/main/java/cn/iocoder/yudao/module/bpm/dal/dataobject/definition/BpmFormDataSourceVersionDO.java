package cn.iocoder.yudao.module.bpm.dal.dataobject.definition;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * BPM 表单数据源版本 DO
 *
 * source_config 存储exactly one typed configuration:
 * - SQL template
 * - dict type
 * - relative platform API path/method
 *
 * @author 宇擎源码
 */
@TableName("bpm_form_data_source_version")
@KeySequence("bpm_form_data_source_version_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BpmFormDataSourceVersionDO extends TenantBaseDO {

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
     * 版本号
     */
    private Integer version;
    /**
     * 状态
     */
    private Integer status;
    /**
     * 数据源配置（JSON）
     *
     * 存储 SQL 模板、字典类型、平台 API 路径/方法等
     */
    private String sourceConfig;
    /**
     * 参数 schema（JSON）
     */
    private String parameterSchema;
    /**
     * 结果 schema（JSON）
     */
    private String resultSchema;
    /**
     * 标签字段名
     */
    private String labelField;
    /**
     * 值字段名
     */
    private String valueField;
    /**
     * 是否分页
     */
    private Boolean pageable;
    /**
     * 最大行数
     */
    private Integer maxRows;
    /**
     * 超时秒数
     */
    private Integer timeoutSeconds;
    /**
     * 缓存秒数
     */
    private Integer cacheSeconds;

}
