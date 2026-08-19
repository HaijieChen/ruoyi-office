package cn.iocoder.yudao.module.finance.dal.mysql.report;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.MPJLambdaWrapperX;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceArDetailReportPageReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 应收明细查询。分页与导出共用同一套主体公司 / 产品 / 导入人过滤。
 */
@Mapper
public interface FinanceArDetailReportMapper extends BaseMapperX<FinanceBusinessOrderDO> {

    default List<FinanceBusinessOrderDO> selectReportList(FinanceArDetailReportPageReqVO reqVO,
                                                          Long importerIdOrNull) {
        MPJLambdaWrapperX<FinanceBusinessOrderDO> wrapper = new MPJLambdaWrapperX<FinanceBusinessOrderDO>()
                .eqIfPresent(FinanceBusinessOrderDO::getEntityCompanyDeptId, reqVO.getEntityCompanyDeptId());
        if (importerIdOrNull != null) {
            wrapper.eq(FinanceBusinessOrderDO::getImporterId, importerIdOrNull);
        }
        // 与商务单列表 blank-aware 口径一致
        if (reqVO != null && StrUtil.isNotBlank(reqVO.getProductType())) {
            wrapper.apply(
                    "COALESCE(NULLIF(TRIM(product_type_snapshot), ''), product_name) = {0}",
                    reqVO.getProductType().trim());
        }
        return selectList(wrapper.orderByDesc(FinanceBusinessOrderDO::getId));
    }
}
