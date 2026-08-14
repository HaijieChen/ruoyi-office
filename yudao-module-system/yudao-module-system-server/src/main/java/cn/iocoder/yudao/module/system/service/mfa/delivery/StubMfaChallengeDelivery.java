package cn.iocoder.yudao.module.system.service.mfa.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 默认可测桩：只记录投递，不调用真实 SMS/邮件通道。
 */
public class StubMfaChallengeDelivery implements MfaChallengeDelivery {

    private static final Logger log = LoggerFactory.getLogger(StubMfaChallengeDelivery.class);

    private final List<Record> records = new CopyOnWriteArrayList<>();

    @Override
    public void deliver(String factorType, String destination, String code) {
        records.add(new Record(factorType, destination, code));
        log.debug("MFA challenge stub deliver type={} dest={}", factorType, destination);
    }

    public List<Record> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(records));
    }

    public void clear() {
        records.clear();
    }

    public record Record(String factorType, String destination, String code) {
    }
}
