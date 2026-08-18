package cn.iocoder.yudao.module.bpm.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * OA 出差 / 外出考勤同步状态。创建时写入 {@link #NOT_SYNCED}，本单元不实现同步写入。
 */
@Getter
@AllArgsConstructor
public enum OaAttendanceSyncStatusEnum {

    NOT_SYNCED("NOT_SYNCED", "未同步"),
    SYNCED("SYNCED", "已同步"),
    FAILED("FAILED", "同步失败");

    /**
     * 持久化值
     */
    private final String status;
    /**
     * 描述
     */
    private final String name;

}
