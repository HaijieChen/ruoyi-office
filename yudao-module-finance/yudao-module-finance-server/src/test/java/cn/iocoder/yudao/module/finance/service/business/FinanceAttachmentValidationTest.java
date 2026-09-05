package cn.iocoder.yudao.module.finance.service.business;

import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderSaveReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationCreateAndStartReqVO;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinanceAttachmentValidationTest {
    @Test
    void bothRequestsEnforceOptionalTenNonBlankUrls() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var business = new FinanceBusinessOrderSaveReqVO();
            var invoice = new FinanceInvoiceApplicationCreateAndStartReqVO();
            for (List<String> urls : List.of(List.<String>of(), List.of("https://files/a.pdf"),
                    Collections.nCopies(10, "https://files/a.pdf"), Collections.nCopies(11, "https://files/a.pdf"),
                    List.of(" "), List.of("x".repeat(2049)))) {
                boolean valid = urls.size() <= 10 && urls.stream().allMatch(s -> !s.isBlank() && s.length() <= 2048);
                business.setAttachmentFileUrls(urls);
                invoice.setAttachmentFileUrls(urls);
                for (Object request : List.of(business, invoice)) {
                    assertEquals(valid, validator.validate(request).stream()
                            .noneMatch(v -> v.getPropertyPath().toString().startsWith("attachmentFileUrls")));
                }
            }
            business.setAttachmentFileUrls(null);
            invoice.setAttachmentFileUrls(null);
            for (Object request : List.of(business, invoice)) {
                assertEquals(true, validator.validate(request).stream()
                        .noneMatch(v -> v.getPropertyPath().toString().startsWith("attachmentFileUrls")));
            }
        }
    }
}
