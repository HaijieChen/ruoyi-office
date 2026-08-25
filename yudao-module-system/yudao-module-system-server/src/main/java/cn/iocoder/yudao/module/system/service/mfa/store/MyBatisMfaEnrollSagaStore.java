package cn.iocoder.yudao.module.system.service.mfa.store;

import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaEnrollSagaDO;
import cn.iocoder.yudao.module.system.dal.mysql.mfa.MfaEnrollSagaMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisMfaEnrollSagaStore implements MfaEnrollSagaStore {

    @Resource
    private MfaEnrollSagaMapper mapper;

    @Override
    public void upsert(Record record) {
        MfaEnrollSagaDO row = MfaEnrollSagaDO.builder()
                .flowTokenHash(record.flowTokenHash())
                .tenantId(record.tenantId())
                .userId(record.userId())
                .factorId(record.factorId())
                .totpStep(record.totpStep())
                .expectedEpoch(record.expectedEpoch())
                .accessToken(record.accessToken())
                .refreshToken(record.refreshToken())
                .state(record.state())
                .build();
        MfaEnrollSagaDO existing = mapper.selectById(record.flowTokenHash());
        if (existing == null) {
            mapper.insert(row);
        } else {
            mapper.updateById(row);
        }
    }

    @Override
    public Record get(String flowTokenHash) {
        MfaEnrollSagaDO d = mapper.selectById(flowTokenHash);
        if (d == null) {
            return null;
        }
        return new Record(d.getFlowTokenHash(), d.getTenantId(), d.getUserId(), d.getFactorId(),
                d.getTotpStep(), d.getExpectedEpoch() == null ? 0L : d.getExpectedEpoch(),
                d.getAccessToken(), d.getRefreshToken(), d.getState());
    }

    @Override
    public void clear() {
        mapper.delete(null);
    }
}
