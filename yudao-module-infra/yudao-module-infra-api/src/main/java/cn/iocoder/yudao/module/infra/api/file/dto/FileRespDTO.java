package cn.iocoder.yudao.module.infra.api.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "RPC 服务 - 文件 Response DTO")
@Data
public class FileRespDTO {

    @Schema(description = "文件编号", example = "1")
    private Long id;

    @Schema(description = "配置编号", example = "11")
    private Long configId;

    @Schema(description = "原文件名", example = "id.pdf")
    private String name;

    @Schema(description = "文件路径")
    private String path;

    @Schema(description = "访问地址")
    private String url;

    @Schema(description = "MIME 类型")
    private String type;

    @Schema(description = "文件大小（字节）")
    private Long size;

}
