package cn.iocoder.yudao.module.bpm.framework.datasource;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.BPM_DATA_SOURCE_CONFIG_INVALID;

/** Dictionary provider using the platform's existing tenant-aware dictionary API. */
@Component
public class BpmDictDataSourceProvider implements BpmFormDataSourceProvider {

    private final DictDataApi dictDataApi;

    public BpmDictDataSourceProvider(DictDataApi dictDataApi) {
        this.dictDataApi = dictDataApi;
    }

    @Override
    public int getType() {
        return TYPE_DICT;
    }

    @Override
    public BpmFormDataSourceQueryResult execute(BpmFormDataSourceExecutionContext context) {
        Map<String, Object> config = parseConfig(context.getVersion().getSourceConfig());
        String dictType = config.get("dictType") instanceof String value ? value : null;
        if (!StringUtils.hasText(dictType)) {
            throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
        List<DictDataRespDTO> dictionary = dictDataApi.getDictDataList(dictType).getCheckedData();
        String labelField = StringUtils.hasText(context.getVersion().getLabelField())
                ? context.getVersion().getLabelField() : "label";
        String valueField = StringUtils.hasText(context.getVersion().getValueField())
                ? context.getVersion().getValueField() : "value";
        List<Map<String, Object>> rows = new ArrayList<>();
        if (dictionary != null) {
            dictionary.stream().filter(item -> CommonStatusEnum.isEnable(item.getStatus())).forEach(item -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put(labelField, item.getLabel());
                row.put(valueField, item.getValue());
                row.put("dictType", item.getDictType());
                rows.add(row);
            });
        }
        return new BpmFormDataSourceQueryResult(rows, rows.size(), context.getVersion().getVersion());
    }

    private static Map<String, Object> parseConfig(String json) {
        Map<String, Object> result = JsonUtils.parseObjectQuietly(json, new TypeReference<>() {});
        if (result == null) {
            throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
        return result;
    }

}
