package cn.iocoder.yudao.module.system.service.mfa.store;

import cn.iocoder.yudao.module.system.service.mfa.enums.MfaAuthFlowState;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaAuthFlowRecord;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 同 JVM 共享 CAS store；跨节点由 {@link MyBatisMfaAuthFlowStore} 承接。
 */
public class InMemoryMfaAuthFlowStore implements MfaAuthFlowStore {

    private static final InMemoryMfaAuthFlowStore SHARED = new InMemoryMfaAuthFlowStore();

    private final ConcurrentHashMap<String, Slot> map = new ConcurrentHashMap<>();

    public static InMemoryMfaAuthFlowStore shared() {
        return SHARED;
    }

    @Override
    public void insert(MfaAuthFlowRecord record) {
        map.put(record.getFlowTokenHash(), new Slot(record, new AtomicReference<>(record.getState())));
    }

    @Override
    public MfaAuthFlowRecord getByTokenHash(String flowTokenHash) {
        Slot slot = map.get(flowTokenHash);
        if (slot == null) {
            return null;
        }
        slot.record.setState(slot.state.get());
        return slot.record;
    }

    @Override
    public boolean casState(String flowTokenHash, MfaAuthFlowState from, MfaAuthFlowState to) {
        Slot slot = map.get(flowTokenHash);
        if (slot == null) {
            return false;
        }
        if (!slot.state.compareAndSet(from, to)) {
            return false;
        }
        slot.record.setState(to);
        return true;
    }

    @Override
    public void clear() {
        map.clear();
    }

    private record Slot(MfaAuthFlowRecord record, AtomicReference<MfaAuthFlowState> state) {
    }
}
