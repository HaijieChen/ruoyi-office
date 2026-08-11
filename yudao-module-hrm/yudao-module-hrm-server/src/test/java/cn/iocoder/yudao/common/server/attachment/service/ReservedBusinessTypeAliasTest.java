package cn.iocoder.yudao.common.server.attachment.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1：保留业务类型别名规范化（大小写 / trim / 201）。
 */
class ReservedBusinessTypeAliasTest {

    @Test
    void detectsCaseAndTrimAliases() {
        assertTrue(AttachmentServiceImpl.isReservedBusinessType("hrm_employee_archive_onboarding"));
        assertTrue(AttachmentServiceImpl.isReservedBusinessType("HRM_EMPLOYEE_ARCHIVE_ONBOARDING"));
        assertTrue(AttachmentServiceImpl.isReservedBusinessType("  Hrm_Employee_Archive_Onboarding  "));
        assertTrue(AttachmentServiceImpl.isReservedBusinessType("201"));
        assertTrue(AttachmentServiceImpl.isReservedBusinessType(" 201 "));
        assertFalse(AttachmentServiceImpl.isReservedBusinessType("seal_apply_bill"));
        assertFalse(AttachmentServiceImpl.isReservedBusinessType(null));
        assertFalse(AttachmentServiceImpl.isReservedBusinessType(""));
    }

    @Test
    void normalizeTrimsAndLowercases() {
        assertEquals("hrm_employee_archive_onboarding",
                AttachmentServiceImpl.normalizeBusinessType("  HRM_EMPLOYEE_ARCHIVE_ONBOARDING "));
        assertEquals("201", AttachmentServiceImpl.normalizeBusinessType("201"));
    }

}
