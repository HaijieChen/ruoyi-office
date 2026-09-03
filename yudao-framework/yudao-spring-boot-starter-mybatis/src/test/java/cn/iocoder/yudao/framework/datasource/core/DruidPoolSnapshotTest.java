package cn.iocoder.yudao.framework.datasource.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DruidPoolSnapshotTest {

    @Test
    void unhealthyWhenWaitThreadsExist() {
        DruidPoolSnapshot snap = new DruidPoolSnapshot("master", 3, 7, 200, 2);
        assertTrue(snap.isUnhealthy());
        assertFalse(snap.isNearlyFull());
    }

    @Test
    void unhealthyWhenActiveReachesMax() {
        DruidPoolSnapshot snap = new DruidPoolSnapshot("master", 200, 0, 200, 0);
        assertTrue(snap.isUnhealthy());
        assertTrue(snap.isNearlyFull());
    }

    @Test
    void healthyWhenIdleHeadroom() {
        DruidPoolSnapshot snap = new DruidPoolSnapshot("master", 10, 10, 200, 0);
        assertFalse(snap.isUnhealthy());
        assertFalse(snap.isNearlyFull());
    }

    @Test
    void nearlyFullAtEightyPercent() {
        DruidPoolSnapshot snap = new DruidPoolSnapshot("master", 160, 0, 200, 0);
        assertFalse(snap.isUnhealthy());
        assertTrue(snap.isNearlyFull());
    }

}
