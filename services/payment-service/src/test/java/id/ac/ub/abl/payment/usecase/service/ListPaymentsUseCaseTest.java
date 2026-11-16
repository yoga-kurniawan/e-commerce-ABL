package id.ac.ub.abl.payment.usecase.service;

import java.math.BigDecimal;
import java.util.List;
import id.ac.ub.abl.payment.domain.Payment;
import id.ac.ub.abl.payment.domain.PaymentMethod;
import id.ac.ub.abl.payment.domain.PaymentStatus;
import id.ac.ub.abl.payment.usecase.exception.InvalidPaymentCommandException;
import id.ac.ub.abl.payment.usecase.exception.PaymentOperationException;
import id.ac.ub.abl.payment.usecase.port.out.PaymentStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListPaymentsUseCaseTest {
    @Mock PaymentStore store;
    @InjectMocks ListPaymentsUseCase useCase;

    @Test
    void execute_whenOrderIdMissing_shouldRejectWithoutCallingStore() {
        assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(InvalidPaymentCommandException.class);

        verifyNoInteractions(store);
    }

    @Test
    void execute_whenOrderHasNoPayments_shouldReturnEmptyList() {
        List<Payment> history = List.of();
        when(store.findByOrderId(501L)).thenReturn(history);

        assertThat(useCase.execute(501L)).isSameAs(history).isEmpty();

        verify(store).findByOrderId(501L);
        verifyNoMoreInteractions(store);
    }

    @Test
    void execute_whenHistoryExists_shouldReturnAllPaymentsInPortOrderWithoutMutation() {
        var failed = Payment.restore(1L, 501L, PaymentMethod.COD, new BigDecimal("350000.12500"),
                PaymentStatus.FAILED, "failed-ref");
        var pending = Payment.restore(2L, 501L, PaymentMethod.TRANSFER, new BigDecimal("350000.12500"),
                PaymentStatus.PENDING, null);
        var history = List.of(failed, pending);
        when(store.findByOrderId(501L)).thenReturn(history);

        assertThat(useCase.execute(501L)).isSameAs(history).containsExactly(failed, pending);

        assertThat(failed.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(failed.getTransactionReference()).isEqualTo("failed-ref");
        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(pending.getTransactionReference()).isNull();
        verify(store).findByOrderId(501L);
        verifyNoMoreInteractions(store);
    }

    @Test
    void execute_whenStoreFails_shouldPropagateOriginalApplicationError() {
        var failure = new PaymentOperationException("DEPENDENCY_UNAVAILABLE", "Database tidak tersedia",
                new IllegalStateException("test database failure"));
        when(store.findByOrderId(501L)).thenThrow(failure);

        assertThatThrownBy(() -> useCase.execute(501L)).isSameAs(failure);

        verify(store).findByOrderId(501L);
        verifyNoMoreInteractions(store);
    }

    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, -1L, 0L, Long.MAX_VALUE})
    void execute_whenOrderIdIsPresent_shouldNotAddUnspecifiedRangeRestrictions(long orderId) {
        when(store.findByOrderId(orderId)).thenReturn(List.of());

        assertThat(useCase.execute(orderId)).isEmpty();

        verify(store).findByOrderId(orderId);
        verifyNoMoreInteractions(store);
    }
}
