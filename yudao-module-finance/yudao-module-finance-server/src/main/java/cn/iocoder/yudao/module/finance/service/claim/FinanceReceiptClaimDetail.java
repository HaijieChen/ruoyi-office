package cn.iocoder.yudao.module.finance.service.claim;

import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.claim.FinanceReceiptClaimDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.claim.FinanceReceiptClaimItemDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.receipt.FinanceReceiptDO;

import java.util.List;
import java.util.Map;

public record FinanceReceiptClaimDetail(FinanceReceiptClaimDO claim,
                                        List<FinanceReceiptClaimItemDO> items,
                                        Map<Long, FinanceReceiptDO> receipts,
                                        Map<Long, FinanceBusinessOrderDO> businessOrders,
                                        Map<Long, FinanceInvoiceApplicationDO> invoiceApplications) {
}
