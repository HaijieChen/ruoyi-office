package cn.iocoder.yudao.module.finance.dal.mysql.receipt;

import cn.iocoder.yudao.module.finance.dal.dataobject.receipt.FinanceReceiptLifecycleAuditDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface FinanceReceiptLifecycleAuditMapper {

    @Options(useGeneratedKeys = true, keyProperty = "id")
    @Insert("INSERT INTO finance_receipt_lifecycle_audit " +
            "(receipt_id, action, operator_id, action_time, reason) " +
            "VALUES (#{receiptId}, #{action}, #{operatorId}, #{actionTime}, #{reason})")
    int insert(FinanceReceiptLifecycleAuditDO audit);

    @Select("SELECT id, receipt_id, action, operator_id, action_time, reason " +
            "FROM finance_receipt_lifecycle_audit " +
            "WHERE receipt_id = #{receiptId} AND deleted = b'0' " +
            "ORDER BY action_time DESC, id DESC")
    @Results(id = "receiptLifecycleAuditResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "receiptId", column = "receipt_id"),
            @Result(property = "action", column = "action"),
            @Result(property = "operatorId", column = "operator_id"),
            @Result(property = "actionTime", column = "action_time"),
            @Result(property = "reason", column = "reason")
    })
    List<FinanceReceiptLifecycleAuditDO> selectListByReceiptId(Long receiptId);
}
