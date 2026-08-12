package cn.iocoder.yudao.module.infra.controller.admin.file.vo.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 上传文件详情 Response VO")
@Data
public class FileUploadRespVO {

    @Schema(description = "文件编号（权威 claim）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "访问地址", requiredMode = Schema.RequiredMode.REQUIRED)
    private String url;

    @Schema(description = "文件路径", requiredMode = Schema.RequiredMode.REQUIRED)
    private String path;

    @Schema(description = "原文件名")
    private String name;

    @Schema(description = "MIME 类型")
    private String type;

    @Schema(description = "文件大小（字节）")
    private Long size;

    @Schema(description = "配置编号")
    private Long configId;

}
