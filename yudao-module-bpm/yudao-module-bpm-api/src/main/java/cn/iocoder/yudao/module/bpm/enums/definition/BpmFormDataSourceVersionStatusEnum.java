package cn.iocoder.yudao.module.bpm.enums.definition;

import cn.hutool.core.util.ObjUtil;
import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * Dynamic-form data-source version status.
 *
 * <p>The persistence schema historically uses {@code 0} for published and {@code 1} for draft. Keep these values
 * centralized so lifecycle and execution code never infer them from the general enabled/disabled status enum.</p>
 */
@Getter
@AllArgsConstructor
public enum BpmFormDataSourceVersionStatusEnum implements ArrayValuable<Integer> {

    PUBLISHED(0, "已发布"),
    DRAFT(1, "草稿");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(BpmFormDataSourceVersionStatusEnum::getStatus).toArray(Integer[]::new);

    private final Integer status;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static boolean isPublished(Integer status) {
        return ObjUtil.equal(PUBLISHED.status, status);
    }

    public static boolean isDraft(Integer status) {
        return ObjUtil.equal(DRAFT.status, status);
    }

}
