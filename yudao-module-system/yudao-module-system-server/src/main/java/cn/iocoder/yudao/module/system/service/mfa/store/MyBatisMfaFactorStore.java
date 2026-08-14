package cn.iocoder.yudao.module.system.service.mfa.store;

import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaFactorDO;
import cn.iocoder.yudao.module.system.dal.mysql.mfa.MfaFactorMapper;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaFactorBinding;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class MyBatisMfaFactorStore implements MfaFactorStore {

    static final String KEY_ID_PLAIN = "plain-dev";

    @Resource
    private MfaFactorMapper mapper;

    @Override
    public void save(Long tenantId, Long userId, MfaFactorBinding binding) {
        MfaFactorDO existing = mapper.selectByUserAndKey(tenantId, userId, binding.getFactorId());
        MfaFactorDO row = toDo(tenantId, userId, binding);
        if (existing == null) {
            mapper.insert(row);
        } else {
            row.setId(existing.getId());
            mapper.updateById(row);
        }
    }

    @Override
    public MfaFactorBinding get(Long tenantId, Long userId, String factorId) {
        return toBinding(mapper.selectByUserAndKey(tenantId, userId, factorId));
    }

    @Override
    public List<MfaFactorBinding> listByUser(Long tenantId, Long userId) {
        List<MfaFactorBinding> out = new ArrayList<>();
        for (MfaFactorDO row : mapper.selectByUser(tenantId, userId)) {
            MfaFactorBinding b = toBinding(row);
            if (b != null) {
                out.add(b);
            }
        }
        return out;
    }

    @Override
    public boolean casStatus(Long tenantId, Long userId, String factorId,
                             String fromStatus, String toStatus, Long lastUsedStep) {
        return mapper.casStatus(tenantId, userId, factorId, fromStatus, toStatus, lastUsedStep) == 1;
    }

    @Override
    public boolean claimTotpStep(Long tenantId, Long userId, String factorId, long step) {
        return mapper.claimTotpStep(tenantId, userId, factorId, step) == 1;
    }

    @Override
    public void clear() {
        mapper.delete(null);
    }

    private static MfaFactorDO toDo(Long tenantId, Long userId, MfaFactorBinding b) {
        return MfaFactorDO.builder()
                .tenantId(tenantId)
                .userId(userId)
                .factorKey(b.getFactorId())
                .type(b.getType())
                .status(b.getStatus())
                .label(b.getLabel())
                .destinationMasked(b.getMasked())
                .secretCiphertext(b.getSecretOrDestination())
                .keyId(KEY_ID_PLAIN)
                .lastUsedStep(b.getLastUsedStep())
                .build();
    }

    private static MfaFactorBinding toBinding(MfaFactorDO d) {
        if (d == null) {
            return null;
        }
        return MfaFactorBinding.builder()
                .factorId(d.getFactorKey())
                .type(d.getType())
                .status(d.getStatus())
                .secretOrDestination(d.getSecretCiphertext())
                .label(d.getLabel())
                .masked(d.getDestinationMasked())
                .lastUsedStep(d.getLastUsedStep() == null ? -1L : d.getLastUsedStep())
                .build();
    }
}
