package cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "管理后台 - 组织导入校验/提交结果")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeptImportRespVO {

    @Schema(description = "文件内容 SHA-256（十六进制小写）", example = "a3f5...")
    private String fileDigest;

    @Schema(description = "有效数据行数", example = "12")
    private Integer totalRows;

    @Schema(description = "将新增/已新增数量", example = "8")
    private Integer createCount;

    @Schema(description = "跳过数量（同路径且字段一致）", example = "4")
    private Integer skipCount;

    @Schema(description = "是否可提交（无错误时为 true）", example = "true")
    private Boolean canCommit;

    @Schema(description = "按行错误明细")
    @Builder.Default
    private List<DeptImportErrorRespVO> errors = new ArrayList<>();

}
