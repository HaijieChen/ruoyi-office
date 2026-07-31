package cn.iocoder.yudao.module.finance.dal.dataobject.customer;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 财务客户公司（购方档案）
 *
 * <p>可选税项字段使用 ALWAYS：允许 FA 清空银行/地址等（review H1）。
 */
@TableName("finance_customer_company")
@KeySequence("finance_customer_company_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceCustomerCompanyDO extends BaseDO {

    /** 启用 */
    public static final int STATUS_ENABLE = 0;
    /** 停用 */
    public static final int STATUS_DISABLE = 1;

    public static final String PARTY_TYPE_CUSTOMER = "CUSTOMER";

    @TableId
    private Long id;
    private String code;
    private String name;
    private String taxNo;
    /** 可清空 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String bankName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String bankAccount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String address;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String phone;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String contactName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String email;
    /** C1 固定 CUSTOMER */
    private String partyType;
    /** 0 启用 / 1 停用 */
    private Integer status;

}
