package cn.iocoder.yudao.module.bpm.framework.datasource;

import lombok.Data;

/**
 * BPM 动态表单数据源 — 参数元数据
 */
@Data
public class BpmFormDataSourceParameter {

    /**
     * 参数名称（对应 SQL 中的 :paramName）
     */
    private String name;

    /**
     * 是否为服务端保留参数（tenantId / userId / deptId / companyId）
     */
    private boolean reserved;

    public BpmFormDataSourceParameter(String name, boolean reserved) {
        this.name = name;
        this.reserved = reserved;
    }
}
