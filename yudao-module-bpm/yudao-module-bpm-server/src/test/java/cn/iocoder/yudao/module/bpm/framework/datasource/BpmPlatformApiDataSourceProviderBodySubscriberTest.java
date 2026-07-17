package cn.iocoder.yudao.module.bpm.framework.datasource;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.*;

class BpmPlatformApiDataSourceProviderBodySubscriberTest {

    @Test
    void boundedSubscriberCollectsBodyWithinLimit() {
        BpmPlatformApiDataSourceProvider.BoundedByteArraySubscriber subscriber =
                new BpmPlatformApiDataSourceProvider.BoundedByteArraySubscriber(5);
        TestSubscription subscription = new TestSubscription();

        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{1, 2}), ByteBuffer.wrap(new byte[]{3, 4, 5})));
        subscriber.onComplete();

        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, subscriber.getBody().toCompletableFuture().join());
        assertFalse(subscription.cancelled);
        assertEquals(2L, subscription.requested);
    }

    @Test
    void boundedSubscriberCancelsBeforeBufferingOversizedBody() {
        BpmPlatformApiDataSourceProvider.BoundedByteArraySubscriber subscriber =
                new BpmPlatformApiDataSourceProvider.BoundedByteArraySubscriber(4);
        TestSubscription subscription = new TestSubscription();

        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{1, 2, 3}), ByteBuffer.wrap(new byte[]{4, 5})));

        assertTrue(subscription.cancelled);
        CompletionException error = assertThrows(CompletionException.class,
                () -> subscriber.getBody().toCompletableFuture().join());
        assertNotNull(error.getCause());
    }

    private static final class TestSubscription implements Flow.Subscription {

        private long requested;
        private boolean cancelled;

        @Override
        public void request(long n) {
            requested += n;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

}
