package cn.iocoder.yudao.module.finance.dal.dataobject.customer;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 财务客商档案（客户 / 供应商角色可叠加）。
 *
 * <p>可清空字段不要用 FieldStrategy.ALWAYS + 局部 updateById（如 updateStatus 只改 status
 * 会把银行/地址刷空）。全量更新见 Service 中 UpdateWrapper 显式 set。
 *
 * <p>角色权威：{@link #isCustomer} / {@link #isSupplier}；{@link #partyType} 仅兼容保留，不再强制写。
 */
@TableName("finance_customer_company")
@KeySequence("finance_customer_company_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceCustomerCompanyDO extends TenantBaseDO {

    /** 启用 */
    public static final int STATUS_ENABLE = 0;
    /** 停用 */
    public static final int STATUS_DISABLE = 1;

    /** @deprecated 读路径以 isCustomer/isSupplier 为准 */
    public static final String PARTY_TYPE_CUSTOMER = "CUSTOMER";
    public static final String PARTY_TYPE_SUPPLIER = "SUPPLIER";
    public static final String PARTY_TYPE_BOTH = "BOTH";

    /** simple-list / 过滤：客户角色 */
    public static final String ROLE_CUSTOMER = "CUSTOMER";
    /** simple-list / 过滤：供应商角色（须银行齐全） */
    public static final String ROLE_SUPPLIER = "SUPPLIER";

    @TableId
    private Long id;
    private String code;
    private String name;
    private String taxNo;
    private String bankName;
    private String bankAccount;
    private String address;
    private String phone;
    private String contactName;
    private String email;
    /** 兼容列；写权威为 isCustomer/isSupplier */
    private String partyType;
    /** 是否客户角色（开票/合同对方） */
    private Boolean isCustomer;
    /** 是否供应商角色（付款收款方） */
    private Boolean isSupplier;
    /** 0 启用 / 1 停用 */
    private Integer status;

}
