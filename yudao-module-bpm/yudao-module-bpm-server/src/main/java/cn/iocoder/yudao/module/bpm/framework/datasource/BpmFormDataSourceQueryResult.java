package cn.iocoder.yudao.module.bpm.framework.datasource;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Result returned by every form data-source provider. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BpmFormDataSourceQueryResult {

    private List<Map<String, Object>> rows = new ArrayList<>();
    private Integer total;
    private Integer version;

}
