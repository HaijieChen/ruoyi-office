package cn.iocoder.yudao.module.system.service.mfa.store;

/**
 * enroll 可重启补偿日志（切片 4 fix1）。
 */
public interface MfaEnrollSagaStore {

    String VERIFIED = "VERIFIED";
    String TOKEN_ISSUED = "TOKEN_ISSUED";
    String COMMITTED = "COMMITTED";
    String COMPENSATE_TOKEN = "COMPENSATE_TOKEN";

    void upsert(Record record);

    Record get(String flowTokenHash);

    void clear();

    record Record(String flowTokenHash, Long tenantId, Long userId, String factorId,
                  Long totpStep, long expectedEpoch, String accessToken, String state) {
        public Record withState(String newState, String token) {
            return new Record(flowTokenHash, tenantId, userId, factorId, totpStep, expectedEpoch,
                    token == null ? accessToken : token, newState);
        }
    }
}
