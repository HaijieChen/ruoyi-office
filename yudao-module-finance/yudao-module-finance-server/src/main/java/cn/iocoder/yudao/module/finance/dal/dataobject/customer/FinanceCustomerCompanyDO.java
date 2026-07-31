package cn.iocoder.yudao.module.finance.dal.dataobject.customer;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 财务客户公司（购方档案）
 *
 * <p>可清空字段不要用 FieldStrategy.ALWAYS + 局部 updateById（如 updateStatus 只改 status
 * 会把银行/地址刷空）。全量更新见 Service 中 LambdaUpdateWrapper 显式 set。
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
    private String bankName;
    private String bankAccount;
    private String address;
    private String phone;
    private String contactName;
    private String email;
    /** C1 固定 CUSTOMER */
    private String partyType;
    /** 0 启用 / 1 停用 */
    private Integer status;

}
