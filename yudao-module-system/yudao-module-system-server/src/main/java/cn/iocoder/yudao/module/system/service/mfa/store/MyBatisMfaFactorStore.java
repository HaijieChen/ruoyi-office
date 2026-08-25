package cn.iocoder.yudao.module.system.service.mfa.store;

import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaFactorDO;
import cn.iocoder.yudao.module.system.dal.mysql.mfa.MfaFactorMapper;
import cn.iocoder.yudao.module.system.service.mfa.crypto.MfaSecretCipher;
import cn.iocoder.yudao.module.system.service.mfa.crypto.MfaSecretCipherImpl;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaFactorBinding;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class MyBatisMfaFactorStore implements MfaFactorStore {

    @Resource
    private MfaFactorMapper mapper;
    @Resource
    private MfaSecretCipher secretCipher;

    @Override
    public void save(Long tenantId, Long userId, MfaFactorBinding binding) {
        requireFactorKey(binding.getFactorId());
        MfaFactorDO row = toDo(tenantId, userId, binding);
        try {
            MfaFactorDO existing = mapper.selectByUserAndKey(tenantId, userId, binding.getFactorId());
            if (existing == null) {
                mapper.insert(row);
            } else {
                row.setId(existing.getId());
                mapper.updateById(row);
            }
        } catch (DuplicateKeyException ex) {
            MfaFactorDO existing = mapper.selectByUserAndKey(tenantId, userId, binding.getFactorId());
            if (existing == null) {
                throw ex;
            }
            row.setId(existing.getId());
            mapper.updateById(row);
        }
    }

    @Override
    public MfaFactorBinding get(Long tenantId, Long userId, String factorId) {
        MfaFactorDO row = mapper.selectByUserAndKey(tenantId, userId, factorId);
        MfaFactorBinding binding = toBinding(row);
        if (row != null && binding != null && !secretCipher.isActiveKey(row.getKeyId())) {
            row.setSecretCiphertext(secretCipher.encrypt(binding.getSecretOrDestination()));
            row.setKeyId(secretCipher.activeKeyId());
            mapper.updateById(row);
        }
        return binding;
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
        requireFactorKey(factorId);
        return mapper.casStatus(tenantId, userId, factorId, fromStatus, toStatus, lastUsedStep) == 1;
    }

    @Override
    public boolean claimTotpStep(Long tenantId, Long userId, String factorId, long step) {
        requireFactorKey(factorId);
        return mapper.claimTotpStep(tenantId, userId, factorId, step) == 1;
    }

    @Override
    public void clear() {
        mapper.delete(null);
    }

    private MfaFactorDO toDo(Long tenantId, Long userId, MfaFactorBinding b) {
        return MfaFactorDO.builder()
                .tenantId(tenantId)
                .userId(userId)
                .factorKey(b.getFactorId())
                .type(b.getType())
                .status(b.getStatus())
                .label(b.getLabel())
                .destinationMasked(b.getMasked())
                .secretCiphertext(secretCipher.encrypt(b.getSecretOrDestination()))
                .keyId(secretCipher.activeKeyId())
                .lastUsedStep(b.getLastUsedStep())
                .build();
    }

    private MfaFactorBinding toBinding(MfaFactorDO d) {
        if (d == null) {
            return null;
        }
        if (d.getKeyId() != null && MfaSecretCipherImpl.FORBIDDEN_KEY_ID.equalsIgnoreCase(d.getKeyId())) {
            throw new IllegalStateException("MFA secret keyId plain-dev is forbidden");
        }
        String secret = secretCipher.decrypt(d.getKeyId(), d.getSecretCiphertext());
        return MfaFactorBinding.builder()
                .factorId(d.getFactorKey())
                .type(d.getType())
                .status(d.getStatus())
                .secretOrDestination(secret)
                .label(d.getLabel())
                .masked(d.getDestinationMasked())
                .lastUsedStep(d.getLastUsedStep() == null ? -1L : d.getLastUsedStep())
                .build();
    }

    static void requireFactorKey(String factorKey) {
        if (factorKey == null || factorKey.isBlank()) {
            throw new IllegalArgumentException("factor_key is required");
        }
    }
}
