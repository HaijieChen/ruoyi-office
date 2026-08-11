package cn.iocoder.yudao.module.finance.controller.admin.payment.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 付款申请分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class FinancePaymentApplicationPageReqVO extends PageParam {

    private String applicationNo;
    private String status;
    private String paymentReason;
    private Long payeeCompanyId;
    private String payeeName;
    /** 主体公司筛选 */
    private Long entityCompanyDeptId;

}
