package cn.iocoder.yudao.module.system.service.mfa.store;

import cn.iocoder.yudao.module.system.service.mfa.enums.MfaAuthFlowState;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaAuthFlowRecord;

/**
 * Auth flow 可切换存储（切片 4）：进程内或 MyBatis CAS。
 */
public interface MfaAuthFlowStore {

    void insert(MfaAuthFlowRecord record);

    MfaAuthFlowRecord getByTokenHash(String flowTokenHash);

    /**
     * 条件更新 state；影响行数 1 为成功。
     */
    boolean casState(String flowTokenHash, MfaAuthFlowState from, MfaAuthFlowState to);

    void clear();
}
