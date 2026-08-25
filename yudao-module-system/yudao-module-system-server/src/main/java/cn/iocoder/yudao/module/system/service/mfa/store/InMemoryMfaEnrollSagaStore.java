package cn.iocoder.yudao.module.system.service.mfa.store;

import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMfaEnrollSagaStore implements MfaEnrollSagaStore {

    private static final InMemoryMfaEnrollSagaStore SHARED = new InMemoryMfaEnrollSagaStore();

    private final ConcurrentHashMap<String, Record> map = new ConcurrentHashMap<>();

    public static InMemoryMfaEnrollSagaStore shared() {
        return SHARED;
    }

    @Override
    public void upsert(Record record) {
        map.put(record.flowTokenHash(), record);
    }

    @Override
    public Record get(String flowTokenHash) {
        return map.get(flowTokenHash);
    }

    @Override
    public void clear() {
        map.clear();
    }
}
