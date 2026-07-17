package cn.iocoder.yudao.module.bpm.dal.mysql.definition;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceLogDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceVersionDO;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmFormDataSourceVersionStatusEnum;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import jakarta.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link BpmFormDataSourceMapper} 的单元测试类
 *
 * @author 宇擎源码
 */
public class BpmFormDataSourceMapperTest extends BaseDbUnitTest {

    @Resource
    private BpmFormDataSourceMapper mapper;

    @Resource
    private BpmFormDataSourceVersionMapper versionMapper;

    @Resource
    private BpmFormDataSourceLogMapper logMapper;

    @Test
    void insertsDefinitionAndPublishedVersion() {
        BpmFormDataSourceDO source = new BpmFormDataSourceDO()
            .setName("可用印章").setCode("oa_available_seals")
            .setType(1).setStatus(0).setPublishedVersion(1);
        mapper.insert(source);
        versionMapper.insert(publishedVersion(source.getId(), 1));
        assertEquals("oa_available_seals", mapper.selectById(source.getId()).getCode());
        assertEquals(1, versionMapper.selectPublished(source.getId()).getVersion());
    }

    @Test
    void selectByCodeReturnsCorrectRecord() {
        BpmFormDataSourceDO source = new BpmFormDataSourceDO()
            .setName("测试数据源").setCode("test_ds")
            .setType(1).setStatus(0).setPublishedVersion(1);
        mapper.insert(source);
        BpmFormDataSourceDO found = mapper.selectByCode("test_ds");
        assertNotNull(found);
        assertEquals(source.getId(), found.getId());
    }

    @Test
    void selectNextVersionReturnsCorrectValue() {
        BpmFormDataSourceDO source = new BpmFormDataSourceDO()
            .setName("版本测试").setCode("ver_test")
            .setType(1).setStatus(0).setPublishedVersion(1);
        mapper.insert(source);
        // No versions yet — next should be 1
        assertEquals(1, versionMapper.selectNextVersion(source.getId()));
        // Insert version 1
        versionMapper.insert(publishedVersion(source.getId(), 1));
        assertEquals(2, versionMapper.selectNextVersion(source.getId()));
    }

    @Test
    void persistsTenantAwareExecutionLog() {
        BpmFormDataSourceLogDO log = new BpmFormDataSourceLogDO()
                .setDataSourceId(10L)
                .setVersion(2)
                .setFormId(20L)
                .setProcessInstanceId("process-30")
                .setUserId(40L)
                .setParameterDigest("sha256:digest")
                .setRowCount(3)
                .setDurationMs(18L)
                .setSuccess(true);
        log.setTenantId(50L);

        logMapper.insert(log);

        BpmFormDataSourceLogDO saved = logMapper.selectById(log.getId());
        assertNotNull(saved);
        assertEquals(10L, saved.getDataSourceId());
        assertEquals(2, saved.getVersion());
        assertEquals("sha256:digest", saved.getParameterDigest());
        assertEquals(50L, saved.getTenantId());
    }

    @Test
    void enforcesDefinitionUniquenessPerTenant() {
        mapper.insert(dataSource("duplicate_code", 1L));

        assertThrows(DataIntegrityViolationException.class,
                () -> mapper.insert(dataSource("duplicate_code", 1L)));
    }

    @Test
    void enforcesVersionUniqueness() {
        BpmFormDataSourceVersionDO first = publishedVersion(100L, 1);
        versionMapper.insert(first);

        assertThrows(DataIntegrityViolationException.class,
                () -> versionMapper.insert(publishedVersion(100L, 1)));
    }

    @Test
    void selectPublishedReturnsNullWhenMissing() {
        assertNull(versionMapper.selectPublished(999L));
    }

    @Test
    void selectPublishedReturnsHighestHistoricalPublishedVersion() {
        versionMapper.insert(publishedVersion(200L, 1));
        versionMapper.insert(publishedVersion(200L, 2));

        assertEquals(2, versionMapper.selectPublished(200L).getVersion());
    }

    @Test
    void updateDraftClearsNullableConfigurationFields() {
        BpmFormDataSourceVersionDO draft = publishedVersion(300L, 1)
                .setStatus(BpmFormDataSourceVersionStatusEnum.DRAFT.getStatus());
        versionMapper.insert(draft);

        int updated = versionMapper.updateDraft(new BpmFormDataSourceVersionDO()
                .setId(draft.getId()).setDataSourceId(300L)
                .setSourceConfig("{\"sql\":\"SELECT id FROM employee WHERE tenant_id = :tenantId\"}")
                .setParameterSchema("[]").setResultSchema("[]").setPageable(false)
                .setLabelField(null).setValueField(null).setMaxRows(null)
                .setTimeoutSeconds(null).setCacheSeconds(null));

        BpmFormDataSourceVersionDO saved = versionMapper.selectById(draft.getId());
        assertEquals(1, updated);
        assertNull(saved.getLabelField());
        assertNull(saved.getValueField());
        assertNull(saved.getMaxRows());
        assertNull(saved.getTimeoutSeconds());
        assertNull(saved.getCacheSeconds());
    }

    @Test
    void publishDraftIsConditionalAndImmutableAfterTransition() {
        BpmFormDataSourceVersionDO draft = publishedVersion(301L, 1)
                .setStatus(BpmFormDataSourceVersionStatusEnum.DRAFT.getStatus());
        versionMapper.insert(draft);

        assertEquals(1, versionMapper.publishDraft(draft.getId(), 301L));
        assertTrue(BpmFormDataSourceVersionStatusEnum.isPublished(
                versionMapper.selectById(draft.getId()).getStatus()));
        assertEquals(0, versionMapper.publishDraft(draft.getId(), 301L));
    }

    private BpmFormDataSourceDO dataSource(String code, Long tenantId) {
        BpmFormDataSourceDO source = new BpmFormDataSourceDO()
                .setName("测试数据源")
                .setCode(code)
                .setType(1)
                .setStatus(0);
        source.setTenantId(tenantId);
        return source;
    }

    private BpmFormDataSourceVersionDO publishedVersion(Long dataSourceId, Integer version) {
        BpmFormDataSourceVersionDO value = new BpmFormDataSourceVersionDO()
            .setDataSourceId(dataSourceId)
            .setVersion(version)
            .setStatus(0)
            .setSourceConfig("{\"sql\":\"SELECT 1\"}")
            .setParameterSchema("{}")
            .setResultSchema("{}")
            .setLabelField("name")
            .setValueField("id")
            .setPageable(false)
            .setMaxRows(100)
            .setTimeoutSeconds(30)
            .setCacheSeconds(0);
        value.setTenantId(1L);
        return value;
    }

}
