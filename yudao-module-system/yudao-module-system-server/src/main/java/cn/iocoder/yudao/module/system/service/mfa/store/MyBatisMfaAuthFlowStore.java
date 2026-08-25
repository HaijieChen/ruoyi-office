package cn.iocoder.yudao.module.system.service.mfa.store;

import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaAuthFlowDO;
import cn.iocoder.yudao.module.system.dal.mysql.mfa.MfaAuthFlowMapper;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaAuthFlowState;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaFlowTokenClass;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaAuthFlowRecord;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Repository
public class MyBatisMfaAuthFlowStore implements MfaAuthFlowStore {

    @Resource
    private MfaAuthFlowMapper mapper;

    @Override
    public void insert(MfaAuthFlowRecord record) {
        mapper.insert(toDo(record));
    }

    @Override
    public MfaAuthFlowRecord getByTokenHash(String flowTokenHash) {
        return toRecord(mapper.selectByTokenHash(flowTokenHash));
    }

    @Override
    public boolean casState(String flowTokenHash, MfaAuthFlowState from, MfaAuthFlowState to) {
        return mapper.casState(flowTokenHash, from.name(), to.name()) == 1;
    }

    @Override
    public void clear() {
        mapper.delete(null);
    }

    private static MfaAuthFlowDO toDo(MfaAuthFlowRecord r) {
        return MfaAuthFlowDO.builder()
                .id(r.getId())
                .flowTokenHash(r.getFlowTokenHash())
                .tokenClass(r.getTokenClass() == null ? null : r.getTokenClass().name())
                .state(r.getState() == null ? MfaAuthFlowState.ACTIVE.name() : r.getState().name())
                .tenantId(r.getTenantId())
                .userId(r.getUserId())
                .clientId(r.getClientId())
                .globalPolicyEpoch(r.getGlobalPolicyEpoch())
                .tenantPolicyEpoch(r.getTenantPolicyEpoch())
                .assuranceEpoch(r.getAssuranceEpoch())
                .allowedActions(join(r.getAllowedActions()))
                .allowedFactorIds(join(r.getAllowedFactorIds()))
                .attempts(r.getAttempts())
                .expiresAt(r.getExpiresAt() == null ? null
                        : LocalDateTime.ofInstant(r.getExpiresAt(), ZoneOffset.UTC))
                .build();
    }

    private static MfaAuthFlowRecord toRecord(MfaAuthFlowDO d) {
        if (d == null) {
            return null;
        }
        Instant expires = d.getExpiresAt() == null ? null : d.getExpiresAt().toInstant(ZoneOffset.UTC);
        Instant created = d.getCreateTime() == null ? Instant.now() : d.getCreateTime().toInstant(ZoneOffset.UTC);
        return MfaAuthFlowRecord.builder()
                .id(d.getId())
                .flowTokenHash(d.getFlowTokenHash())
                .tokenClass(d.getTokenClass() == null ? null : MfaFlowTokenClass.valueOf(d.getTokenClass()))
                .state(d.getState() == null ? null : MfaAuthFlowState.valueOf(d.getState()))
                .tenantId(d.getTenantId())
                .userId(d.getUserId())
                .clientId(d.getClientId())
                .globalPolicyEpoch(nz(d.getGlobalPolicyEpoch()))
                .tenantPolicyEpoch(nz(d.getTenantPolicyEpoch()))
                .assuranceEpoch(nz(d.getAssuranceEpoch()))
                .allowedActions(split(d.getAllowedActions()))
                .allowedFactorIds(split(d.getAllowedFactorIds()))
                .attempts(d.getAttempts() == null ? 0 : d.getAttempts())
                .expiresAt(expires)
                .createdAt(created)
                .build();
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    private static String join(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        return String.join(",", items);
    }

    private static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(csv.split(",")));
    }
}
