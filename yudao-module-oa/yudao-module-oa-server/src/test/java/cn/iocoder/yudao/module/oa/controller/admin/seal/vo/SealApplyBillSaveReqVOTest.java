package cn.iocoder.yudao.module.oa.controller.admin.seal.vo;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SealApplyBillSaveReqVOTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void nameOnly_withoutCatalogId_passes() {
        SealApplyBillSaveReqVO vo = requiredBase();
        vo.setSealName("公司公章");
        vo.setSealId(null);
        vo.setSealNo(null);

        Set<ConstraintViolation<SealApplyBillSaveReqVO>> violations = validator.validate(vo);
        assertTrue(violations.isEmpty(), () -> violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", ")));
    }

    @Test
    void blankSealName_fails() {
        SealApplyBillSaveReqVO vo = requiredBase();
        vo.setSealName("  ");
        vo.setSealId(1L);
        vo.setSealNo("YZ001");

        Set<String> messages = validator.validate(vo).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
        assertTrue(messages.contains("印章名称不能为空"));
    }

    @Test
    void oldShapedBody_withSealId_stillPassesWhenNameSet() {
        SealApplyBillSaveReqVO vo = requiredBase();
        vo.setSealName("公司公章");
        vo.setSealId(12L);
        vo.setSealNo("YZ001");

        assertTrue(validator.validate(vo).isEmpty());
        assertFalse(vo.getSealId() == null);
    }

    private static SealApplyBillSaveReqVO requiredBase() {
        SealApplyBillSaveReqVO vo = new SealApplyBillSaveReqVO();
        vo.setCause("合同签署");
        vo.setUseType(1);
        vo.setUseMode(1);
        vo.setCompanyId(1L);
        vo.setCompanyName("宇擎科技");
        vo.setDeptId(1L);
        vo.setDeptName("技术部");
        return vo;
    }
}
