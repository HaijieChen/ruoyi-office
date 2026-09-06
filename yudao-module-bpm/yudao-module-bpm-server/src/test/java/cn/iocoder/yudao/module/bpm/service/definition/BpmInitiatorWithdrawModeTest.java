package cn.iocoder.yudao.module.bpm.service.definition;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelMetaInfoVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelSaveReqVO;
import cn.iocoder.yudao.module.bpm.convert.definition.BpmModelConvert;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.definition.BpmProcessDefinitionInfoMapper;
import jakarta.validation.Validation;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.impl.persistence.entity.ModelEntityImpl;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BpmInitiatorWithdrawModeTest {
    private static final String FIELD = "initiatorWithdrawMode";

    @Test
    void nullableEnumValidationRejectsUnknownValues() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (Integer value : new Integer[]{null, 0, 1, 2}) {
                assertTrue(validator.validateValue(BpmModelMetaInfoVO.class, FIELD, value).isEmpty());
            }
            for (Integer value : new Integer[]{-1, 3, Integer.MAX_VALUE}) {
                assertFalse(validator.validateValue(BpmModelMetaInfoVO.class, FIELD, value).isEmpty());
            }
        }
    }

    @Test
    void modelSaveReadAndUnrelatedEditPreserveNullableModeAndIndependentSwitches() throws Exception {
        for (Integer mode : new Integer[]{null, 0, 1, 2}) {
            for (Boolean legacy : new Boolean[]{null, false, true}) {
                var request = new BpmModelSaveReqVO();
                request.setAllowWithdrawTask(legacy);
                request.setAllowCancelRunningProcess(false);
                setMode(request, mode);
                var model = new ModelEntityImpl();
                model.setCreateTime(new Date());
                BpmModelConvert.INSTANCE.copyToModel(model, request);
                var read = BpmModelConvert.INSTANCE.parseMetaInfo(model);
                assertEquals(mode, mode(read));
                var edit = BeanUtils.toBean(read, BpmModelSaveReqVO.class);
                edit.setDescription("unrelated edit");
                BpmModelConvert.INSTANCE.copyToModel(model, edit);
                var reread = BpmModelConvert.INSTANCE.parseMetaInfo(model);
                assertEquals(mode, mode(reread));
                assertEquals(legacy, reread.getAllowWithdrawTask());
                assertEquals(false, reread.getAllowCancelRunningProcess());
            }
        }
    }

    @Test
    void actualPublishPathInsertsSeparateSnapshotsWithoutMaterializingLegacyNull() throws Exception {
        var repository = mock(RepositoryService.class, RETURNS_DEEP_STUBS);
        var mapper = mock(BpmProcessDefinitionInfoMapper.class);
        var service = new BpmProcessDefinitionServiceImpl();
        ReflectionTestUtils.setField(service, "repositoryService", repository);
        ReflectionTestUtils.setField(service, "processDefinitionMapper", mapper);
        var definition = mock(ProcessDefinition.class);
        when(definition.getKey()).thenReturn("test");
        when(definition.getName()).thenReturn("Test");
        when(repository.createProcessDefinitionQuery().deploymentId(any()).singleResult()).thenReturn(definition);
        List<BpmProcessDefinitionInfoDO> snapshots = new ArrayList<>();
        doAnswer(invocation -> { snapshots.add(invocation.getArgument(0)); return 1; })
                .when(mapper).insert(any(BpmProcessDefinitionInfoDO.class));
        var model = new ModelEntityImpl();
        model.setId("model");
        model.setKey("test");
        model.setName("Test");
        var meta = new BpmModelMetaInfoVO();
        meta.setAllowWithdrawTask(false);
        meta.setAllowCancelRunningProcess(true);
        Integer[] modes = {null, 0, 1, 2};
        for (int i = 0; i < modes.length; i++) {
            when(definition.getId()).thenReturn("version-" + i);
            setMode(meta, modes[i]);
            service.createProcessDefinition(model, meta, new byte[0], null, null);
        }
        assertEquals(4, snapshots.size());
        for (int i = 0; i < modes.length; i++) {
            assertEquals(modes[i], mode(snapshots.get(i)));
            assertEquals("version-" + i, snapshots.get(i).getProcessDefinitionId());
            assertEquals(false, snapshots.get(i).getAllowWithdrawTask());
            assertEquals(true, snapshots.get(i).getAllowCancelRunningProcess());
            if (i > 0) assertNotSame(snapshots.get(i - 1), snapshots.get(i));
        }
        verify(mapper, times(4)).insert(any(BpmProcessDefinitionInfoDO.class));
        verifyNoMoreInteractions(mapper);
    }

    private static void setMode(BpmModelMetaInfoVO target, Integer value) throws Exception {
        BpmModelMetaInfoVO.class.getMethod("setInitiatorWithdrawMode", Integer.class).invoke(target, value);
    }

    private static Object mode(Object target) throws Exception {
        return target.getClass().getMethod("getInitiatorWithdrawMode").invoke(target);
    }
}
