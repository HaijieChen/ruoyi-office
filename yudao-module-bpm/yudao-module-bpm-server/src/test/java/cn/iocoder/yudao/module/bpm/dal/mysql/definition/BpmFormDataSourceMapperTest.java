package cn.iocoder.yudao.module.bpm.dal.mysql.definition;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceVersionDO;
import org.junit.jupiter.api.Test;

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

    private BpmFormDataSourceVersionDO publishedVersion(Long dataSourceId, Integer version) {
        return new BpmFormDataSourceVersionDO()
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
    }

}
