package cn.iocoder.yudao.module.bpm.dal.dataobject.definition;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * BPM 表单数据源定义 DO
 *
 * @author 宇擎源码
 */
@TableName("bpm_form_data_source")
@KeySequence("bpm_form_data_source_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BpmFormDataSourceDO extends BaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 数据源名称
     */
    private String name;
    /**
     * 数据源标识
     */
    private String code;
    /**
     * 数据源类型
     *
     * 1 - SQL
     * 2 - DICT
     * 3 - PLATFORM_API
     */
    private Integer type;
    /**
     * 状态
     *
     * 0 - 开启
     * 1 - 关闭
     */
    private Integer status;
    /**
     * 已发布版本号
     */
    private Integer publishedVersion;

}
