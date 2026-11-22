package id.ac.ub.abl.payment.adapter.outbound.database;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class IdempotencyCleanupSchedulerTest {

    @Test
    @DisplayName("cleanExpiredIdempotencyRecords when deleted < batchSize stops looping")
    void cleanExpiredIdempotencyRecords_whenFewerThanBatchSize_stopsLooping() {
        var adapter = Mockito.mock(PaymentIdempotencyAdapter.class);
        when(adapter.deleteExpiredCompleted(500)).thenReturn(120);

        var scheduler = new IdempotencyCleanupScheduler(adapter, 500, 20);
        int totalDeleted = scheduler.cleanExpiredIdempotencyRecords();

        assertThat(totalDeleted).isEqualTo(120);
        verify(adapter, times(1)).deleteExpiredCompleted(500);
    }

    @Test
    @DisplayName("cleanExpiredIdempotencyRecords when multiple full batches loops until last batch < batchSize")
    void cleanExpiredIdempotencyRecords_whenMultipleFullBatches_loopsUntilUnderBatchSize() {
        var adapter = Mockito.mock(PaymentIdempotencyAdapter.class);
        when(adapter.deleteExpiredCompleted(500)).thenReturn(500, 500, 42);

        var scheduler = new IdempotencyCleanupScheduler(adapter, 500, 20);
        int totalDeleted = scheduler.cleanExpiredIdempotencyRecords();

        assertThat(totalDeleted).isEqualTo(1042);
        verify(adapter, times(3)).deleteExpiredCompleted(500);
    }

    @Test
    @DisplayName("cleanExpiredIdempotencyRecords when maxBatchesPerRun hit stops looping")
    void cleanExpiredIdempotencyRecords_whenMaxBatchesHit_stopsAtMaxBatches() {
        var adapter = Mockito.mock(PaymentIdempotencyAdapter.class);
        when(adapter.deleteExpiredCompleted(100)).thenReturn(100);

        var scheduler = new IdempotencyCleanupScheduler(adapter, 100, 3);
        int totalDeleted = scheduler.cleanExpiredIdempotencyRecords();

        assertThat(totalDeleted).isEqualTo(300);
        verify(adapter, times(3)).deleteExpiredCompleted(100);
    }

    @Test
    @DisplayName("cleanExpiredIdempotencyRecords when adapter throws catches error safely without throwing")
    void cleanExpiredIdempotencyRecords_whenAdapterThrows_catchesAndLogsSafely() {
        var adapter = Mockito.mock(PaymentIdempotencyAdapter.class);
        when(adapter.deleteExpiredCompleted(anyInt())).thenThrow(new RuntimeException("Simulated database failure"));

        var scheduler = new IdempotencyCleanupScheduler(adapter, 500, 20);
        int totalDeleted = scheduler.cleanExpiredIdempotencyRecords();

        assertThat(totalDeleted).isZero();
        verify(adapter, times(1)).deleteExpiredCompleted(500);
    }

    @Test
    @DisplayName("constructor rejects invalid batchSize or maxBatchesPerRun")
    void constructor_validatesArguments() {
        var adapter = Mockito.mock(PaymentIdempotencyAdapter.class);

        assertThatThrownBy(() -> new IdempotencyCleanupScheduler(adapter, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyCleanupScheduler(adapter, 500, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Spring context loads scheduler when cleanup is enabled or missing")
    void contextLoadsScheduler_whenEnabledOrMissing() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        id.ac.ub.abl.payment.configuration.IdempotencyCleanupConfiguration.class,
                        IdempotencyCleanupScheduler.class)
                .withBean(PaymentIdempotencyAdapter.class, () -> Mockito.mock(PaymentIdempotencyAdapter.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotencyCleanupScheduler.class);
                });
    }

    @Test
    @DisplayName("Spring context does not load scheduler when cleanup is disabled")
    void contextDoesNotLoadScheduler_whenDisabled() {
        new ApplicationContextRunner()
                .withPropertyValues("payment.idempotency.cleanup.enabled=false")
                .withUserConfiguration(
                        id.ac.ub.abl.payment.configuration.IdempotencyCleanupConfiguration.class,
                        IdempotencyCleanupScheduler.class)
                .withBean(PaymentIdempotencyAdapter.class, () -> Mockito.mock(PaymentIdempotencyAdapter.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(IdempotencyCleanupScheduler.class);
                });
    }
}
