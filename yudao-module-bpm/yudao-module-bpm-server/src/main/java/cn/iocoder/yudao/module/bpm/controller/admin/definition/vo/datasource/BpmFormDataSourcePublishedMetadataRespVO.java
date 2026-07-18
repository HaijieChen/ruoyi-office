package cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.datasource;

import cn.iocoder.yudao.module.bpm.service.definition.BpmFormDataSourcePublishedMetadata;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;

@Schema(description = "管理后台 - BPM 表单数据源已发布元数据 Response VO")
@Data
public class BpmFormDataSourcePublishedMetadataRespVO {

    @Schema(description = "数据源编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "数据源名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "可用印章")
    private String name;

    @Schema(description = "数据源标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "oa_available_seals")
    private String code;

    @Schema(description = "数据源类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer type;

    @Schema(description = "已发布版本号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Integer publishedVersion;

    @Schema(description = "请求参数字段", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Field> parameterFields;

    @Schema(description = "结果字段", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Field> resultFields;

    @Schema(description = "默认显示字段", example = "name")
    private String labelField;

    @Schema(description = "默认值字段", example = "id")
    private String valueField;

    @Schema(description = "是否分页", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    private Boolean pageable;

    public static BpmFormDataSourcePublishedMetadataRespVO from(BpmFormDataSourcePublishedMetadata metadata) {
        return new BpmFormDataSourcePublishedMetadataRespVO()
                .setId(metadata.id()).setName(metadata.name()).setCode(metadata.code()).setType(metadata.type())
                .setPublishedVersion(metadata.publishedVersion())
                .setParameterFields(convertList(metadata.parameterFields(), Field::from))
                .setResultFields(convertList(metadata.resultFields(), Field::from))
                .setLabelField(metadata.labelField()).setValueField(metadata.valueField())
                .setPageable(metadata.pageable());
    }

    @Schema(description = "管理后台 - BPM 表单数据源字段元数据")
    @Data
    public static class Field {

        @Schema(description = "字段名", requiredMode = Schema.RequiredMode.REQUIRED, example = "customerId")
        private String name;

        @Schema(description = "中文说明", requiredMode = Schema.RequiredMode.REQUIRED, example = "客商编号")
        private String label;

        @Schema(description = "字段类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "LONG")
        private String type;

        @Schema(description = "是否必填", example = "true")
        private Boolean required;

        @Schema(description = "脱敏策略", example = "PHONE")
        private String mask;

        private static Field from(BpmFormDataSourcePublishedMetadata.Field field) {
            return new Field().setName(field.name()).setLabel(field.label()).setType(field.type())
                    .setRequired(field.required()).setMask(field.mask());
        }
    }

}
