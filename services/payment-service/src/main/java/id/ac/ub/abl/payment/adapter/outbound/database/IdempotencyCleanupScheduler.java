package id.ac.ub.abl.payment.adapter.outbound.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "payment.idempotency.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class IdempotencyCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupScheduler.class);

    private final PaymentIdempotencyAdapter idempotencyAdapter;
    private final int batchSize;
    private final int maxBatchesPerRun;

    public IdempotencyCleanupScheduler(
            PaymentIdempotencyAdapter idempotencyAdapter,
            @Value("${payment.idempotency.cleanup.batch-size:500}") int batchSize,
            @Value("${payment.idempotency.cleanup.max-batches-per-run:20}") int maxBatchesPerRun) {
        if (batchSize < 1 || maxBatchesPerRun < 1) {
            throw new IllegalArgumentException("Batch size dan max batches per run harus positif");
        }
        this.idempotencyAdapter = idempotencyAdapter;
        this.batchSize = batchSize;
        this.maxBatchesPerRun = maxBatchesPerRun;
    }

    @Scheduled(
            fixedDelayString = "${payment.idempotency.cleanup.interval-ms:3600000}",
            initialDelayString = "${payment.idempotency.cleanup.initial-delay-ms:60000}"
    )
    public int cleanExpiredIdempotencyRecords() {
        int totalDeleted = 0;
        int batchesRun = 0;
        try {
            while (batchesRun < maxBatchesPerRun) {
                int deleted = idempotencyAdapter.deleteExpiredCompleted(batchSize);
                totalDeleted += deleted;
                batchesRun++;
                if (deleted < batchSize) {
                    break;
                }
            }
            if (totalDeleted > 0) {
                log.info("Idempotency cleanup berhasil menghapus {} rekaman expired dalam {} batch", totalDeleted, batchesRun);
            }
        } catch (Exception e) {
            log.error("Gagal menjalankan idempotency cleanup: {}", e.getMessage());
        }
        return totalDeleted;
    }
}
