package cn.iocoder.yudao.module.bpm.service.definition;

import java.util.List;

/** Safe, published-only metadata exposed to the form designer. */
public record BpmFormDataSourcePublishedMetadata(
        Long id,
        String name,
        String code,
        Integer type,
        Integer publishedVersion,
        List<Field> parameterFields,
        List<Field> resultFields,
        String labelField,
        String valueField,
        Boolean pageable) {

    public record Field(String name, String label, String type, Boolean required, String mask) {
    }

}
