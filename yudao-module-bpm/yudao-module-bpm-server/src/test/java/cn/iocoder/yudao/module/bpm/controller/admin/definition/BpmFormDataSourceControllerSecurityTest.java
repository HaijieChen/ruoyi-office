package cn.iocoder.yudao.module.bpm.controller.admin.definition;

import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.datasource.BpmFormDataSourceCreateWithDraftReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.datasource.BpmFormDataSourceVersionSummaryRespVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BpmFormDataSourceControllerSecurityTest {

    @Test
    void publishRequiresDedicatedPermission() throws NoSuchMethodException {
        Method publish = BpmFormDataSourceController.class.getMethod("publish", Long.class, Long.class);

        assertEquals("@ss.hasPermission('bpm:form-data-source:publish')",
                publish.getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void atomicCreateWithDraftRequiresCreatePermission() throws NoSuchMethodException {
        Method create = BpmFormDataSourceController.class.getMethod(
                "createDataSourceWithDraft", BpmFormDataSourceCreateWithDraftReqVO.class);

        assertEquals("@ss.hasPermission('bpm:form-data-source:create')",
                create.getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void versionHistorySummaryCannotExposeExecutableConfiguration() {
        Set<String> fields = Arrays.stream(BpmFormDataSourceVersionSummaryRespVO.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName).collect(Collectors.toSet());

        assertFalse(fields.contains("sourceConfig"));
        assertFalse(fields.contains("parameterSchema"));
        assertFalse(fields.contains("resultSchema"));
    }

}
