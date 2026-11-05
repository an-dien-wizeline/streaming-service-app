package io.wizeline.payment.service;

import io.wizeline.payment.dto.PaymentRequest;
import io.wizeline.payment.dto.PaymentResponse;
import io.wizeline.payment.entity.Payment;
import io.wizeline.payment.entity.PaymentStatus;
import io.wizeline.payment.exception.PaymentProcessingException;
import io.wizeline.payment.exception.InvalidPaymentException;
import io.wizeline.payment.repository.PaymentRepository;
import io.wizeline.payment.client.StripeClient;
import io.wizeline.payment.event.PaymentEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName(\"Payment Service Unit Tests\")
class PaymentServiceTest {

    private PaymentService paymentService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private StripeClient stripeClient;

    @Mock
    private PaymentEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, stripeClient, eventPublisher);
    }

    // ============ HAPPY PATH TESTS ============

    @Test
    @DisplayName(\"should process payment successfully when valid request provided\")
    void testProcessPayment_whenValidRequest_expectsSuccess() {
        // ARRANGE
        PaymentRequest request = new PaymentRequest();
        request.setUserId(\"user123\");
        request.setAmount(new BigDecimal(\"99.99\"));
        request.setCurrency(\"USD\");
        request.setStripeToken(\"tok_visa\");
        request.setSubscriptionId(\"sub_456\");

        Payment savedPayment = new Payment();
        savedPayment.setId(\"pay_789\");
        savedPayment.setUserId(\"user123\");
        savedPayment.setAmount(new BigDecimal(\"99.99\"));
        savedPayment.setStatus(PaymentStatus.SUCCESS);
        savedPayment.setStripeChargeId(\"ch_1234567890\");
        savedPayment.setCreatedAt(LocalDateTime.now());

        when(stripeClient.charge(request.getAmount(), request.getCurrency(), request.getStripeToken()))
            .thenReturn(\"ch_1234567890\");
        when(paymentRepository.save(any(Payment.class)))
            .thenReturn(savedPayment);

        // ACT
        PaymentResponse response = paymentService.processPayment(request);

        // ASSERT
        assertNotNull(response);
        assertEquals(\"pay_789\", response.getPaymentId());
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        assertEquals(new BigDecimal(\"99.99\"), response.getAmount());
        verify(stripeClient, times(1)).charge(request.getAmount(), request.getCurrency(), request.getStripeToken());
        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(eventPublisher, times(1)).publishPaymentSuccess(any(Payment.class));
    }

    @Test
    @DisplayName(\"should handle subscription renewal payment successfully\")
    void testProcessSubscriptionRenewal_whenValidRequest_expectsSuccess() {
        // ARRANGE
        PaymentRequest request = new PaymentRequest();
        request.setUserId(\"user123\");
        request.setAmount(new BigDecimal(\"9.99\"));
        request.setCurrency(\"USD\");
        request.setStripeToken(\"tok_visa\");
        request.setSubscriptionId(\"sub_456\");
        request.setIsRenewal(true);

        Payment savedPayment = new Payment();
        savedPayment.setId(\"pay_renewal_001\");
        savedPayment.setSubscriptionId(\"sub_456\");
        savedPayment.setStatus(PaymentStatus.SUCCESS);
        savedPayment.setIsRenewal(true);

        when(stripeClient.charge(any(), any(), any())).thenReturn(\"ch_renewal_001\");
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        // ACT
        PaymentResponse response = paymentService.processPayment(request);

        // ASSERT
        assertNotNull(response);
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        assertTrue(response.isRenewal());
        verify(eventPublisher, times(1)).publishPaymentSuccess(any(Payment.class));
    }

    @Test
    @DisplayName(\"should retrieve payment by ID successfully\")
    void testGetPaymentById_whenPaymentExists_expectsPaymentReturned() {
        // ARRANGE
        String paymentId = \"pay_789\";
        Payment payment = new Payment();
        payment.setId(paymentId);
        payment.setUserId(\"user123\");
        payment.setAmount(new BigDecimal(\"99.99\"));
        payment.setStatus(PaymentStatus.SUCCESS);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        // ACT
        PaymentResponse response = paymentService.getPaymentById(paymentId);

        // ASSERT
        assertNotNull(response);
        assertEquals(paymentId, response.getPaymentId());
        assertEquals(\"user123\", response.getUserId());
        verify(paymentRepository, times(1)).findById(paymentId);
    }

    // ============ BOUNDARY VALUE TESTS ============

    @ParameterizedTest
    @DisplayName(\"should handle various valid amounts\")
    @CsvSource({
        \"0.01, USD\",
        \"1.00, USD\",
        \"99.99, USD\",
        \"999.99, USD\",
        \"9999.99, USD\"
    })
    void testProcessPayment_whenVariousValidAmounts_expectsSuccess(String amount, String currency) {
        // ARRANGE
        PaymentRequest request = new PaymentRequest();
        request.setUserId(\"user123\");
        request.setAmount(new BigDecimal(amount));
        request.setCurrency(currency);
        request.setStripeToken(\"tok_visa\");

        Payment savedPayment = new Payment();
        savedPayment.setId(\"pay_\" + System.nanoTime());
        savedPayment.setAmount(new BigDecimal(amount));
        savedPayment.setStatus(PaymentStatus.SUCCESS);

        when(stripeClient.charge(any(), any(), any())).thenReturn(\"ch_test\");
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        // ACT
        PaymentResponse response = paymentService.processPayment(request);

        // ASSERT
        assertNotNull(response);
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        assertEquals(new BigDecimal(amount), response.getAmount());
    }

    @Test
    @DisplayName(\"should handle zero amount payment\")
    void testProcessPayment_whenZeroAmount_expectsInvalidPaymentException() {
        // ARRANGE
        PaymentRequest request = new PaymentRequest();
        request.setUserId(\"user123\");
        request.setAmount(BigDecimal.ZERO);
        request.setCurrency(\"USD\");
        request.setStripeToken(\"tok_visa\");

        // ACT & ASSERT
        assertThrows(InvalidPaymentException.class, () -> paymentService.processPayment(request));
        verify(stripeClient, never()).charge(any(), any(), any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName(\"should handle negative amount payment\")
    void testProcessPayment_whenNegativeAmount_expectsInvalidPaymentException() {
        // ARRANGE
        PaymentRequest request = new PaymentRequest();
        request.setUserId(\"user123\");
        request.setAmount(new BigDecimal(\"-50.00\"));
        request.setCurrency(\"USD\");
        request.setStripeToken(\"tok_visa\");

        // ACT & ASSERT
        assertThrows(InvalidPaymentException.class, () -> paymentService.processPayment(request));
        verify(stripeClient, never()).charge(any(), any(), any());
    }

    @Test
    @DisplayName(\"should handle null amount\")
    void testProcessPayment_whenNullAmount_expectsInvalidPaymentException() {
        // ARRANGE
        PaymentRequest request = new PaymentRequest();
        request.setUserId(\"user123\");
        request.setAmount(null);
        request.setCurrency(\"USD\");
        request.setStripeToken(\"tok_visa\");

        // ACT & ASSERT
        assertThrows(InvalidPaymentException.class, () -> paymentService.processPayment(request));
    }

    @Test
    @DisplayName(\"should handle null user ID\")
    void testProcessPayment_whenNullUserId_expectsInvalidPaymentException() {
        // ARRANGE
        PaymentRequest request = new PaymentRequest();
        request.setUserId(null);
        request.setAmount(new BigDecimal(\"99.99\"));
        request.setCurrency(\"USD\");
        request.setStripeToken(\"tok_visa\");

        // ACT & ASSERT
        assertThrows(InvalidPaymentException.class, () -> paymentService.processPayment(request));
    }

    @Test
    @DisplayName(\"should handle empty user ID\")
    void testProcessPayment_whenEmptyUserId_expectsInvalidPaymentException() {
        // ARRANGE
        PaymentRequest request = new PaymentRequest();
        request.setUserId(\"\");
        request.setAmount(new BigDecimal(\"99.99\"));
        request.setCurrency(\"USD\");
        request.setStripeToken(\"tok_visa\");

        // ACT & ASSERT
        assertThrows(InvalidPaymentException.class, () -> paymentService.processPayment(request));
    }

    @Test
    @DisplayName(\"should handle null stripe token\")
    void testProcessPayment_whenNullStripeToken_expectsInvalidPaymentException() {
        // ARRANGE
        PaymentRequest request = new PaymentRequest();
        request.setUserId(\"user123\");
        request.setAmount(new BigDecimal(\"99.99\"));
        request.setCurrency(\"USD\");
        request.setStripeToken(null);

        // ACT & ASSERT
        assertThrows(InvalidPaymentException.class, () -> paymentService.processPayment(request));
    }

    // ============ ERROR/EXCEPTION TESTS ============

    @Test
    @DisplayName(\"should handle Stripe API failure gracefully\")
    void testProcessPayment_whenStripeApiFails_expectsPaymentProcessingException() {
        // ARRANGE
        PaymentRequest request = new PaymentRequest();
        request.setUserId(\"user123\");
        request.setAmount(new BigDecimal(\"99.99\"));
        request.setCurrency(\"USD\");
        request.setStripeToken(\"tok_chargeDeclined\");

        when(stripeClient.charge(any(), any(), any()))
            .thenThrow(new RuntimeException(\"Stripe API error: Card declined\"));

        // ACT & ASSERT
        assertThrows(PaymentProcessingException.class, () -> paymentService.processPayment(request));
        verify(paymentRepository, never()).save(any());
        verify(eventPublisher, never()).publishPaymentSuccess(any());
    }

    @Test
    @DisplayName(\"should handle database save failure\")
    void testProcessPayment_whenDatabaseSaveFails_expectsPaymentProcessingException() {
        // ARRANGE
        PaymentRequest request = new PaymentRequest();
        request.setUserId(\"user123\");
        request.setAmount(new BigDecimal(\"99.99\"));
        request.setCurrency(\"USD\");
        request.setStripeToken(\"tok_visa\");

        when(stripeClient.charge(any(), any(), any())).thenReturn(\"ch_1234567890\");
        when(paymentRepository.save(any(Payment.class)))
            .thenThrow(new RuntimeException(\"Database connection failed\"));

        // ACT & ASSERT
        assertThrows(PaymentProcessingException.class, () -> paymentService.processPayment(request));
        verify(eventPublisher, never()).publishPaymentSuccess(any());
    }

    @Test
    @DisplayName(\"should handle payment not found\")
    void testGetPaymentById_whenPaymentNotFound_expectsEmptyOptional() {
        // ARRANGE
        String paymentId = \"pay_nonexistent\";
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        // ACT & ASSERT
        assertThrows(PaymentProcessingException.class, () -> paymentService.getPaymentById(paymentId));
        verify(paymentRepository, times(1)).findById(paymentId);
    }

    @Test
    @DisplayName(\"should handle refund request successfully\")
    void testRefundPayment_whenValidPaymentId_expectsRefundSuccess() {
        // ARRANGE
        String paymentId = \"pay_789\";
        Payment payment = new Payment();
        payment.setId(paymentId);
        payment.setUserId(\"user123\");
        payment.setAmount(new BigDecimal(\"99.99\"));
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setStripeChargeId(\"ch_1234567890\");

        Payment refundedPayment = new Payment();
        refundedPayment.setId(paymentId);
        refundedPayment.setStatus(PaymentStatus.REFUNDED);
        refundedPayment.setRefundedAt(LocalDateTime.now());

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(stripeClient.refund(\"ch_1234567890\")).thenReturn(true);
        when(paymentRepository.save(any(Payment.class))).thenReturn(refundedPayment);

        // ACT
        PaymentResponse response = paymentService.refundPayment(paymentId);

        // ASSERT
        assertNotNull(response);
        assertEquals(PaymentStatus.REFUNDED, response.getStatus());
        verify(stripeClient, times(1)).refund(\"ch_1234567890\");
        verify(eventPublisher, times(1)).publishPaymentRefunded(any(Payment.class));
    }

    @Test
    @DisplayName(\"should handle refund failure when Stripe refund fails\")
    void testRefundPayment_whenStripeRefundFails_expectsPaymentProcessingException() {
        // ARRANGE
        String paymentId = \"pay_789\";
        Payment payment = new Payment();
        payment.setId(paymentId);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setStripeChargeId(\"ch_1234567890\");

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(stripeClient.refund(\"ch_1234567890\")).thenReturn(false);

        // ACT & ASSERT
        assertThrows(PaymentProcessingException.class, () -> paymentService.refundPayment(paymentId));
        verify(paymentRepository, never()).save(any());
    }

    // ============ SIDE EFFECTS TESTS ============

    @Test
    @DisplayName(\"should publish payment success event after successful payment\")
    void testProcessPayment_whenSuccessful_expectsEventPublished() {
        // ARRANGE
        PaymentRequest request = new PaymentRequest();
        request.setUserId(\"user123\");
        request.setAmount(new BigDecimal(\"99.99\"));
        request.setCurrency(\"USD\");
        request.setStripeToken(\"tok_visa\");
        request.setSubscriptionId(\"sub_456\");

        Payment savedPayment = new Payment();
        savedPayment.setId(\"pay_789\");
        savedPayment.setStatus(PaymentStatus.SUCCESS);

        when(stripeClient.charge(any(), any(), any())).thenReturn(\"ch_1234567890\");
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        // ACT
        paymentService.processPayment(request);

        // ASSERT
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(eventPublisher, times(1)).publishPaymentSuccess(paymentCaptor.capture());
        Payment publishedPayment = paymentCaptor.getValue();
        assertEquals(\"pay_789\", publishedPayment.getId());
        assertEquals(PaymentStatus.SUCCESS, publishedPayment.getStatus());
    }

    @Test
    @DisplayName(\"should not publish event when payment processing fails\")
    void testProcessPayment_whenFails_expectsNoEventPublished() {
        // ARRANGE
        PaymentRequest request = new PaymentRequest();
        request.setUserId(\"user123\");
        request.setAmount(new BigDecimal(\"99.99\"));
        request.setCurrency(\"USD\");
        request.setStripeToken(\"tok_chargeDeclined\");

        when(stripeClient.charge(any(), any(), any()))
            .thenThrow(new RuntimeException(\"Card declined\"));

        // ACT & ASSERT
        assertThrows(PaymentProcessingException.class, () -> paymentService.processPayment(request));
        verify(eventPublisher, never()).publishPaymentSuccess(any());
    }

    // ============ IDEMPOTENCY TESTS ============

    @Test
    @DisplayName(\"should handle duplicate payment requests idempotently\")
    void testProcessPayment_whenDuplicateRequest_expectsIdempotent() {
        // ARRANGE
        PaymentRequest request = new PaymentRequest();
        request.setUserId(\"user123\");
        request.setAmount(new BigDecimal(\"99.99\"));
        request.setCurrency(\"USD\");
        request.setStripeToken(\"tok_visa\");
        request.setIdempotencyKey(\"idempotency_key_123\");

        Payment existingPayment = new Payment();
        existingPayment.setId(\"pay_existing\");
        existingPayment.setStatus(PaymentStatus.SUCCESS);
        existingPayment.setIdempotencyKey(\"idempotency_key_123\");

        when(paymentRepository.findByIdempotencyKey(\"idempotency_key_123\"))
            .thenReturn(Optional.of(existingPayment));

        // ACT
        PaymentResponse response = paymentService.processPayment(request);

        // ASSERT
        assertNotNull(response);
        assertEquals(\"pay_existing\", response.getPaymentId());
        verify(stripeClient, never()).charge(any(), any(), any());
        verify(paymentRepository, never()).save(any());
    }

    // ============ CONCURRENCY TESTS ============

    @Test
    @DisplayName(\"should handle concurrent payment requests safely\")
    void testProcessPayment_whenConcurrentRequests_expectsSafeHandling() throws InterruptedException {
        // ARRANGE
        PaymentRequest request1 = new PaymentRequest();
        request1.setUserId(\"user123\");
        request1.setAmount(new BigDecimal(\"99.99\"));
        request1.setCurrency(\"USD\");
        request1.setStripeToken(\"tok_visa\");

        Payment payment1 = new Payment();
        payment1.setId(\"pay_1\");
        payment1.setStatus(PaymentStatus.SUCCESS);

        when(stripeClient.charge(any(), any(), any())).thenReturn(\"ch_1\");
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment1);

        // ACT
        Thread thread1 = new Thread(() -> paymentService.processPayment(request1));
        Thread thread2 = new Thread(() -> paymentService.processPayment(request1));

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        // ASSERT
        verify(stripeClient, atLeast(1)).charge(any(), any(), any());
        verify(paymentRepository, atLeast(1)).save(any(Payment.class));
    }
}
