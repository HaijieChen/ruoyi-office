package cn.iocoder.yudao.module.finance.controller.admin.common.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "财务列表批量删除结果")
@Data
public class FinanceBatchDeleteRespVO {

    @Schema(description = "成功删除条数")
    private int deleted;

    @Schema(description = "跳过原因")
    private List<String> errors = new ArrayList<>();
}
