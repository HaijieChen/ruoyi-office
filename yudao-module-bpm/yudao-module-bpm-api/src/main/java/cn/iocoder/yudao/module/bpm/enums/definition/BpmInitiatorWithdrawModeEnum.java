package cn.iocoder.yudao.module.bpm.enums.definition;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/** 发起人撤回至发起节点的策略；不控制撤销终止或审批人撤回。 */
@Getter
@AllArgsConstructor
public enum BpmInitiatorWithdrawModeEnum implements ArrayValuable<Integer> {

    DISABLED(0, "不允许"),
    NO_HUMAN_RESULTS(1, "仅无人审批时允许"),
    RUNNING_ALLOWED(2, "审批中允许");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(BpmInitiatorWithdrawModeEnum::getMode).toArray(Integer[]::new);

    private final Integer mode;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
