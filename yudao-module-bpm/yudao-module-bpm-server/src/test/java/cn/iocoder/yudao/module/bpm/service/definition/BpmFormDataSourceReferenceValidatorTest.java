package cn.iocoder.yudao.module.bpm.service.definition;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.BPM_DATA_SOURCE_FORM_NOT_REFERENCED;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.FORM_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BpmFormDataSourceReferenceValidatorTest {

    private final AtomicReference<BpmFormDO> storedForm = new AtomicReference<>();
    private BpmFormDataSourceReferenceValidator validator;

    @BeforeEach
    void setUp() {
        BpmFormService formService = (BpmFormService) Proxy.newProxyInstance(
                BpmFormService.class.getClassLoader(), new Class<?>[]{BpmFormService.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getForm")) {
                        return storedForm.get();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        validator = new BpmFormDataSourceReferenceValidator(formService);
    }

    @Test
    void shouldAcceptExactReferenceInNestedFormCreateChildren() {
        storedForm.set(form(List.of("""
                {"type":"Row","children":[
                  {"type":"Col","children":[
                    {"type":"RemoteDataSourceSelect","field":"sealIds",
                     "props":{"dataSourceCode":"oa_available_seals"}}
                  ]}
                ]}
                """)));

        assertDoesNotThrow(() -> validator.validateFormReference(7L, "oa_available_seals"));
    }

    @Test
    void shouldRejectMissingForm() {
        storedForm.set(null);

        ServiceException error = assertThrows(ServiceException.class,
                () -> validator.validateFormReference(7L, "oa_available_seals"));

        assertEquals(FORM_NOT_EXISTS.getCode(), error.getCode());
    }

    @Test
    void shouldRejectMissingReference() {
        storedForm.set(form(List.of("""
                {"type":"RemoteDataSourceSelect","field":"sealIds",
                 "props":{"dataSourceCode":"another_source"}}
                """)));

        assertNotReferenced("oa_available_seals");
    }

    @Test
    void shouldRejectSourceCodeSpoofedOutsideKnownComponentProps() {
        storedForm.set(form(List.of(
                """
                {"type":"Select","field":"sealIds",
                 "props":{"dataSourceCode":"oa_available_seals"}}
                """,
                """
                {"type":"RemoteDataSourceSelect","field":"sealIds",
                 "dataSourceCode":"oa_available_seals","props":{}}
                """,
                """
                {"type":"Input","field":"memo","title":"oa_available_seals"}
                """)));

        assertNotReferenced("oa_available_seals");
    }

    @Test
    void shouldRejectConceptualRemoteSelectAlias() {
        storedForm.set(form(List.of("""
                {"type":"RemoteSelect","field":"sealIds",
                 "props":{"dataSourceCode":"oa_available_seals"}}
                """)));

        assertNotReferenced("oa_available_seals");
    }

    @Test
    void shouldFailClosedForMalformedFieldJson() {
        storedForm.set(form(List.of(
                "{not-json}",
                """
                {"type":"RemoteDataSourceSelect","field":"sealIds",
                 "props":{"dataSourceCode":"oa_available_seals"}}
                """)));

        assertNotReferenced("oa_available_seals");
    }

    @Test
    void shouldFailClosedForTrailingJsonContent() {
        storedForm.set(form(List.of("""
                {"type":"RemoteDataSourceSelect","field":"sealIds",
                 "props":{"dataSourceCode":"oa_available_seals"}}
                {"type":"Input","field":"ignored"}
                """)));

        assertNotReferenced("oa_available_seals");
    }

    @Test
    void shouldRejectBlankInputsAndEmptyFields() {
        storedForm.set(form(List.of()));

        assertNotReferenced(" ");
        ServiceException nullFormIdError = assertThrows(ServiceException.class,
                () -> validator.validateFormReference(null, "oa_available_seals"));
        assertEquals(FORM_NOT_EXISTS.getCode(), nullFormIdError.getCode());
    }

    private void assertNotReferenced(String sourceCode) {
        ServiceException error = assertThrows(ServiceException.class,
                () -> validator.validateFormReference(7L, sourceCode));
        assertEquals(BPM_DATA_SOURCE_FORM_NOT_REFERENCED.getCode(), error.getCode());
    }

    private static BpmFormDO form(List<String> fields) {
        return new BpmFormDO().setId(7L).setFields(fields);
    }

}
