package cn.iocoder.yudao.module.finance.dal.mysql.business;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.MPJLambdaWrapperX;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderPageReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

@Mapper
public interface FinanceBusinessOrderMapper extends BaseMapperX<FinanceBusinessOrderDO> {

    default PageResult<FinanceBusinessOrderDO> selectPage(FinanceBusinessOrderPageReqVO reqVO) {
        MPJLambdaWrapperX<FinanceBusinessOrderDO> wrapper = new MPJLambdaWrapperX<FinanceBusinessOrderDO>()
                .likeIfPresent(FinanceBusinessOrderDO::getOrderNo, reqVO.getOrderNo())
                .eqIfPresent(FinanceBusinessOrderDO::getEntityCompanyDeptId, reqVO.getEntityCompanyDeptId())
                .likeIfPresent(FinanceBusinessOrderDO::getContractProcessId, reqVO.getContractProcessId())
                .likeIfPresent(FinanceBusinessOrderDO::getPayerName, reqVO.getPayerName())
                .eqIfPresent(FinanceBusinessOrderDO::getImporterId, reqVO.getImporterId())
                .betweenIfPresent(FinanceBusinessOrderDO::getImportDate, reqVO.getImportDate())
                .betweenIfPresent(FinanceBusinessOrderDO::getOrderDate, reqVO.getOrderDate());
        // EXP-70 P2 #3：blank-aware 双读（空串 snapshot 须回退 product_name）
        if (StrUtil.isNotBlank(reqVO.getProductName())) {
            String pattern = "%" + reqVO.getProductName().trim() + "%";
            wrapper.apply(
                    "COALESCE(NULLIF(TRIM(product_type_snapshot), ''), product_name) LIKE {0}",
                    pattern);
        }
        if (StrUtil.isNotBlank(reqVO.getContractApplicationNo())) {
            String pattern = "%" + reqVO.getContractApplicationNo().trim() + "%";
            wrapper.apply(
                    "contract_application_id IN (SELECT id FROM finance_contract_application "
                            + "WHERE deleted = b'0' AND application_no LIKE {0})",
                    pattern);
        }
        // 开票可选（EXP-70 复审 #7 / invoice-selectable）：可开余额 > 0 + 有合同 + 非空 snapshot
        // 不得用 legacy product_name 冒充可开产品
        if (Boolean.TRUE.equals(reqVO.getOnlyOpenable())) {
            wrapper.apply("settlement_amount > IFNULL(invoiced_occupied_amount, 0)");
            wrapper.apply("contract_application_id IS NOT NULL");
            wrapper.apply("product_type_snapshot IS NOT NULL AND TRIM(product_type_snapshot) <> ''");
        }
        if (reqVO.getCustomerCompanyId() != null) {
            wrapper.apply(
                    "contract_application_id IN (SELECT id FROM finance_contract_application "
                            + "WHERE deleted = b'0' AND counterparty_company_id = {0})",
                    reqVO.getCustomerCompanyId());
        }
        return selectPage(reqVO, wrapper.orderByDesc(FinanceBusinessOrderDO::getId));
    }

    default List<FinanceBusinessOrderDO> selectListByQuery(FinanceBusinessOrderPageReqVO reqVO) {
        MPJLambdaWrapperX<FinanceBusinessOrderDO> wrapper = new MPJLambdaWrapperX<FinanceBusinessOrderDO>()
                .likeIfPresent(FinanceBusinessOrderDO::getOrderNo, reqVO.getOrderNo())
                .eqIfPresent(FinanceBusinessOrderDO::getEntityCompanyDeptId, reqVO.getEntityCompanyDeptId())
                .likeIfPresent(FinanceBusinessOrderDO::getContractProcessId, reqVO.getContractProcessId())
                .likeIfPresent(FinanceBusinessOrderDO::getPayerName, reqVO.getPayerName())
                .eqIfPresent(FinanceBusinessOrderDO::getImporterId, reqVO.getImporterId())
                .betweenIfPresent(FinanceBusinessOrderDO::getImportDate, reqVO.getImportDate())
                .betweenIfPresent(FinanceBusinessOrderDO::getOrderDate, reqVO.getOrderDate());
        if (StrUtil.isNotBlank(reqVO.getProductName())) {
            String pattern = "%" + reqVO.getProductName().trim() + "%";
            wrapper.apply(
                    "COALESCE(NULLIF(TRIM(product_type_snapshot), ''), product_name) LIKE {0}",
                    pattern);
        }
        if (StrUtil.isNotBlank(reqVO.getContractApplicationNo())) {
            String pattern = "%" + reqVO.getContractApplicationNo().trim() + "%";
            wrapper.apply(
                    "contract_application_id IN (SELECT id FROM finance_contract_application "
                            + "WHERE deleted = b'0' AND application_no LIKE {0})",
                    pattern);
        }
        if (Boolean.TRUE.equals(reqVO.getOnlyOpenable())) {
            wrapper.apply("settlement_amount > IFNULL(invoiced_occupied_amount, 0)");
            wrapper.apply("contract_application_id IS NOT NULL");
            wrapper.apply("product_type_snapshot IS NOT NULL AND TRIM(product_type_snapshot) <> ''");
        }
        if (reqVO.getCustomerCompanyId() != null) {
            wrapper.apply(
                    "contract_application_id IN (SELECT id FROM finance_contract_application "
                            + "WHERE deleted = b'0' AND counterparty_company_id = {0})",
                    reqVO.getCustomerCompanyId());
        }
        return selectList(wrapper.orderByDesc(FinanceBusinessOrderDO::getId));
    }

    default PageResult<FinanceBusinessOrderDO> selectClaimablePage(FinanceBusinessOrderPageReqVO reqVO,
                                                                   Long importerId) {
        // 注意：apply() 返回父类型，不能接在变量声明的链式末尾，否则无法赋给 MPJLambdaWrapperX
        MPJLambdaWrapperX<FinanceBusinessOrderDO> wrapper = new MPJLambdaWrapperX<FinanceBusinessOrderDO>()
                .likeIfPresent(FinanceBusinessOrderDO::getOrderNo, reqVO.getOrderNo())
                .eqIfPresent(FinanceBusinessOrderDO::getEntityCompanyDeptId, reqVO.getEntityCompanyDeptId())
                .likeIfPresent(FinanceBusinessOrderDO::getContractProcessId, reqVO.getContractProcessId())
                .likeIfPresent(FinanceBusinessOrderDO::getPayerName, reqVO.getPayerName())
                .eq(FinanceBusinessOrderDO::getApplicantUserId, importerId)
                .betweenIfPresent(FinanceBusinessOrderDO::getImportDate, reqVO.getImportDate())
                .betweenIfPresent(FinanceBusinessOrderDO::getOrderDate, reqVO.getOrderDate());
        if (StrUtil.isNotBlank(reqVO.getProductName())) {
            String pattern = "%" + reqVO.getProductName().trim() + "%";
            wrapper.apply(
                    "COALESCE(NULLIF(TRIM(product_type_snapshot), ''), product_name) LIKE {0}",
                    pattern);
        }
        wrapper.apply("settlement_amount > confirmed_claimed_amount");
        if (StrUtil.isNotBlank(reqVO.getContractApplicationNo())) {
            String pattern = "%" + reqVO.getContractApplicationNo().trim() + "%";
            wrapper.apply(
                    "contract_application_id IN (SELECT id FROM finance_contract_application "
                            + "WHERE deleted = b'0' AND application_no LIKE {0})",
                    pattern);
        }
        return selectPage(reqVO, wrapper.orderByDesc(FinanceBusinessOrderDO::getId));
    }

    default FinanceBusinessOrderDO selectByOrderNo(String orderNo) {
        return selectOne("order_no", orderNo);
    }

    default FinanceBusinessOrderDO selectBySourceRowHash(String sourceRowHash) {
        return selectOne("source_row_hash", sourceRowHash);
    }

    default List<FinanceBusinessOrderDO> selectListByIds(Collection<Long> ids) {
        return selectList(new MPJLambdaWrapperX<FinanceBusinessOrderDO>().in(FinanceBusinessOrderDO::getId, ids));
    }

    @Update("UPDATE finance_business_order SET confirmed_claimed_amount = confirmed_claimed_amount + #{amount}, " +
            "update_time = NOW() WHERE id = #{id} AND importer_id = #{importerId} " +
            "AND settlement_amount - confirmed_claimed_amount >= #{amount} AND deleted = b'0'")
    int increaseConfirmedClaimedAmount(@Param("id") Long id, @Param("importerId") Long importerId,
                                       @Param("amount") BigDecimal amount);

    @Update("UPDATE finance_business_order SET confirmed_claimed_amount = confirmed_claimed_amount - #{amount}, " +
            "update_time = NOW() WHERE id = #{id} AND importer_id = #{importerId} " +
            "AND confirmed_claimed_amount >= #{amount} AND deleted = b'0'")
    int decreaseConfirmedClaimedAmount(@Param("id") Long id, @Param("importerId") Long importerId,
                                       @Param("amount") BigDecimal amount);

    /**
     * CAS 增加开票占用：可开金额足够，且合同/产品与开票快照期望一致（EXP-70 防 TOCTOU）。
     * <p>仅 createAndStart / resubmit 写路径调用。期望产品与 dual-read
     * {@code COALESCE(NULLIF(TRIM(product_type_snapshot),''), product_name)} 相等。
     */
    /**
     * P1 #3：占用仅当合同非空且 product_type_snapshot 与期望完全一致（不 dual-read name）。
     */
    @Update("UPDATE finance_business_order SET invoiced_occupied_amount = invoiced_occupied_amount + #{amount}, " +
            "update_time = NOW() WHERE id = #{id} AND deleted = b'0' " +
            "AND settlement_amount - invoiced_occupied_amount >= #{amount} " +
            "AND contract_application_id IS NOT NULL " +
            "AND contract_application_id = #{expectedContractId} " +
            "AND product_type_snapshot IS NOT NULL AND TRIM(product_type_snapshot) <> '' " +
            "AND product_type_snapshot = #{expectedProductType}")
    int increaseInvoicedOccupiedAmount(@Param("id") Long id,
                                       @Param("amount") BigDecimal amount,
                                       @Param("expectedContractId") Long expectedContractId,
                                       @Param("expectedProductType") String expectedProductType);

    /**
     * CAS 减少开票占用：invoiced_occupied_amount &gt;= amount。
     * <p>仅 onApprovalOutcome(REJECTED|CANCELLED) / resubmit 释占路径调用。
     */
    @Update("UPDATE finance_business_order SET invoiced_occupied_amount = invoiced_occupied_amount - #{amount}, " +
            "update_time = NOW() WHERE id = #{id} " +
            "AND invoiced_occupied_amount >= #{amount} AND deleted = b'0'")
    int decreaseInvoicedOccupiedAmount(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * 换合同 CAS：仅当开票占用仍为 0 且当前合同 id 未变时，原子写入新合同 + 产品快照。
     */
    @Update("UPDATE finance_business_order SET contract_application_id = #{newContractId}, " +
            "product_type_snapshot = #{productTypeSnapshot}, product_name = #{productTypeSnapshot}, " +
            "update_time = NOW() " +
            "WHERE id = #{id} AND deleted = b'0' " +
            "AND contract_application_id = #{oldContractId} " +
            "AND IFNULL(invoiced_occupied_amount, 0) = 0")
    int casChangeContractApplicationId(@Param("id") Long id,
                                       @Param("oldContractId") Long oldContractId,
                                       @Param("newContractId") Long newContractId,
                                       @Param("productTypeSnapshot") String productTypeSnapshot);

    /**
     * 历史空首次映射：仅当合同仍为空且占用为 0 时，原子写入合同 + 产品快照。
     */
    @Update("UPDATE finance_business_order SET contract_application_id = #{newContractId}, " +
            "product_type_snapshot = #{productTypeSnapshot}, product_name = #{productTypeSnapshot}, " +
            "update_time = NOW() " +
            "WHERE id = #{id} AND deleted = b'0' " +
            "AND contract_application_id IS NULL " +
            "AND IFNULL(invoiced_occupied_amount, 0) = 0")
    int casSetContractApplicationIdWhenEmpty(@Param("id") Long id,
                                             @Param("newContractId") Long newContractId,
                                             @Param("productTypeSnapshot") String productTypeSnapshot);

    /**
     * 统计商务单上 PENDING/APPROVED 且未作废的开票明细行数（换合同阻断）。
     */
    @Select("SELECT COUNT(1) FROM finance_invoice_application_line l " +
            "INNER JOIN finance_invoice_application a ON a.id = l.application_id AND a.deleted = b'0' " +
            "WHERE l.deleted = b'0' AND l.business_order_id = #{businessOrderId} " +
            "AND IFNULL(a.voided, b'0') = b'0' " +
            "AND a.approval_status IN ('PENDING', 'APPROVED')")
    Long countActiveInvoiceLinesByBusinessOrderId(@Param("businessOrderId") Long businessOrderId);

    /**
     * 同合同补齐空产品快照：合同未变、占用为 0、快照仍空，且合同权威条件仍成立时原子写入
     * （EXP-70 复审 #6：校验到写入不漂移 — APPROVED / 未作废 / importer=applicant / 产品一致）。
     */
    @Update("UPDATE finance_business_order SET product_type_snapshot = #{productTypeSnapshot}, " +
            "product_name = #{productTypeSnapshot}, update_time = NOW() " +
            "WHERE id = #{id} AND deleted = b'0' " +
            "AND contract_application_id = #{contractId} " +
            "AND IFNULL(invoiced_occupied_amount, 0) = 0 " +
            "AND (product_type_snapshot IS NULL OR TRIM(product_type_snapshot) = '') " +
            "AND EXISTS (" +
            "  SELECT 1 FROM finance_contract_application ca " +
            "  WHERE ca.id = #{contractId} AND ca.deleted = b'0' " +
            "    AND ca.approval_status = 'APPROVED' " +
            "    AND IFNULL(ca.voided, b'0') = b'0' " +
            "    AND ca.applicant_user_id = IFNULL(finance_business_order.applicant_user_id, finance_business_order.importer_id) " +
            "    AND ca.product_type IS NOT NULL AND TRIM(ca.product_type) <> '' " +
            "    AND TRIM(ca.product_type) = #{productTypeSnapshot}" +
            ")")
    int casFillProductSnapshotWhenEmpty(@Param("id") Long id,
                                        @Param("contractId") Long contractId,
                                        @Param("productTypeSnapshot") String productTypeSnapshot);

}
