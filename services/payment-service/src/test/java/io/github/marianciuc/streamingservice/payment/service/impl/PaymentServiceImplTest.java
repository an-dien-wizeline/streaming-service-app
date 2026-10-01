/*
 * Copyright (c) 2024  Vladimir Marianciuc. All Rights Reserved.
 *
 * Project: STREAMING SERVICE APP
 * File: PaymentServiceImplTest.java
 *
 */

package io.github.marianciuc.streamingservice.payment.service.impl;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import io.github.marianciuc.streamingservice.payment.dto.common.AddressDto;
import io.github.marianciuc.streamingservice.payment.dto.common.TransactionDto;
import io.github.marianciuc.streamingservice.payment.entity.Address;
import io.github.marianciuc.streamingservice.payment.entity.CardHolder;
import io.github.marianciuc.streamingservice.payment.entity.JWTUserPrincipal;
import io.github.marianciuc.streamingservice.payment.entity.Refund;
import io.github.marianciuc.streamingservice.payment.entity.Transaction;
import io.github.marianciuc.streamingservice.payment.enums.CardStatus;
import io.github.marianciuc.streamingservice.payment.enums.Currency;
import io.github.marianciuc.streamingservice.payment.enums.PaymentStatus;
import io.github.marianciuc.streamingservice.payment.enums.RefundStatus;
import io.github.marianciuc.streamingservice.payment.kafka.PaymentKafkaProducer;
import io.github.marianciuc.streamingservice.payment.kafka.messages.InitializePaymentMessage;
import io.github.marianciuc.streamingservice.payment.repository.AddressRepository;
import io.github.marianciuc.streamingservice.payment.repository.RefundRepository;
import io.github.marianciuc.streamingservice.payment.repository.TransactionRepository;
import io.github.marianciuc.streamingservice.payment.service.CardHolderService;
import io.github.marianciuc.streamingservice.payment.service.UserService;
import io.github.marianciuc.streamingservice.payment.specifications.TransactionSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Payment Service Implementation Tests")
class PaymentServiceImplTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CardHolderService cardHolderService;

    @Mock
    private PaymentKafkaProducer paymentKafkaProducer;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    private UUID testUserId;
    private UUID testTransactionId;
    private UUID testOrderId;
    private CardHolder testCardHolder;
    private Transaction testTransaction;
    private InitializePaymentMessage testPaymentMessage;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testTransactionId = UUID.randomUUID();
        testOrderId = UUID.randomUUID();

        testCardHolder = CardHolder.builder()
                .userId(testUserId)
                .stripeCustomerId("cus_test123")
                .email("test@example.com")
                .cardHolderName("John Doe")
                .phoneNumber("+1234567890")
                .cardStatus(CardStatus.ACTIVE)
                .build();

        testTransaction = Transaction.builder()
                .id(testTransactionId)
                .orderId(testOrderId)
                .cardHolder(testCardHolder)
                .amount(10000L)
                .currency(Currency.USD)
                .status(PaymentStatus.PENDING)
                .build();

        testPaymentMessage = new InitializePaymentMessage(
                testUserId,
                testOrderId,
                100.0,
                Currency.USD
        );
    }

    @Nested
    @DisplayName("TransactionService.initializeTransaction Tests")
    class InitializeTransactionTests {

        @Test
        @DisplayName("Should successfully initialize transaction with valid payment message")
        void testInitializeTransaction_whenValidMessage_expectsSuccess() throws StripeException {
            // ARRANGE
            PaymentIntent mockPaymentIntent = mock(PaymentIntent.class);
            when(mockPaymentIntent.getId()).thenReturn("pi_test123");
            when(cardHolderService.findCardHolderEntity(testUserId)).thenReturn(testCardHolder);

            try (var mockedStatic = mockStatic(com.stripe.model.PaymentIntent.class)) {
                mockedStatic.when(() -> PaymentIntent.create(any())).thenReturn(mockPaymentIntent);

                // ACT
                transactionService.initializeTransaction(testPaymentMessage);

                // ASSERT
                ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
                verify(transactionRepository).save(transactionCaptor.capture());
                Transaction savedTransaction = transactionCaptor.getValue();

                assertEquals(PaymentStatus.SUCCESS, savedTransaction.getStatus());
                assertEquals("pi_test123", savedTransaction.getStripePaymentIntentId());
                assertEquals(testOrderId, savedTransaction.getOrderId());
                assertEquals(Currency.USD, savedTransaction.getCurrency());
                assertEquals(10000L, savedTransaction.getAmount());
            }
        }

        @Test
        @DisplayName("Should handle Stripe exception and mark transaction as failed")
        void testInitializeTransaction_whenStripeException_expectsFailed() throws StripeException {
            // ARRANGE
            StripeException stripeException = new StripeException("Card declined");
            when(cardHolderService.findCardHolderEntity(testUserId)).thenReturn(testCardHolder);

            try (var mockedStatic = mockStatic(com.stripe.model.PaymentIntent.class)) {
                mockedStatic.when(() -> PaymentIntent.create(any())).thenThrow(stripeException);

                // ACT & ASSERT
                assertThrows(RuntimeException.class, () -> transactionService.initializeTransaction(testPaymentMessage));

                ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
                verify(transactionRepository).save(transactionCaptor.capture());
                Transaction savedTransaction = transactionCaptor.getValue();

                assertEquals(PaymentStatus.FAILED, savedTransaction.getStatus());
                assertEquals("Card declined", savedTransaction.getFailureMessage());
            }
        }

        @Test
        @DisplayName("Should set correct currency multiplier for different currencies")
        void testInitializeTransaction_whenDifferentCurrency_expectsCorrectMultiplier() throws StripeException {
            // ARRANGE
            InitializePaymentMessage eurMessage = new InitializePaymentMessage(
                    testUserId,
                    testOrderId,
                    50.0,
                    Currency.EUR
            );
            PaymentIntent mockPaymentIntent = mock(PaymentIntent.class);
            when(mockPaymentIntent.getId()).thenReturn("pi_eur123");
            when(cardHolderService.findCardHolderEntity(testUserId)).thenReturn(testCardHolder);

            try (var mockedStatic = mockStatic(com.stripe.model.PaymentIntent.class)) {
                mockedStatic.when(() -> PaymentIntent.create(any())).thenReturn(mockPaymentIntent);

                // ACT
                transactionService.initializeTransaction(eurMessage);

                // ASSERT
                ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
                verify(transactionRepository).save(transactionCaptor.capture());
                Transaction savedTransaction = transactionCaptor.getValue();

                assertEquals(Currency.EUR, savedTransaction.getCurrency());
                assertEquals(5000L, savedTransaction.getAmount());
            }
        }

        @Test
        @DisplayName("Should handle JPY currency with different multiplier")
        void testInitializeTransaction_whenJPYCurrency_expectsCorrectAmount() throws StripeException {
            // ARRANGE
            InitializePaymentMessage jpyMessage = new InitializePaymentMessage(
                    testUserId,
                    testOrderId,
                    1000.0,
                    Currency.JPY
            );
            PaymentIntent mockPaymentIntent = mock(PaymentIntent.class);
            when(mockPaymentIntent.getId()).thenReturn("pi_jpy123");
            when(cardHolderService.findCardHolderEntity(testUserId)).thenReturn(testCardHolder);

            try (var mockedStatic = mockStatic(com.stripe.model.PaymentIntent.class)) {
                mockedStatic.when(() -> PaymentIntent.create(any())).thenReturn(mockPaymentIntent);

                // ACT
                transactionService.initializeTransaction(jpyMessage);

                // ASSERT
                ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
                verify(transactionRepository).save(transactionCaptor.capture());
                Transaction savedTransaction = transactionCaptor.getValue();

                assertEquals(Currency.JPY, savedTransaction.getCurrency());
                assertEquals(1000L, savedTransaction.getAmount());
            }
        }

        @Test
        @DisplayName("Should save transaction even when Stripe call fails")
        void testInitializeTransaction_whenStripeCallFails_expectsTransactionSaved() throws StripeException {
            // ARRANGE
            when(cardHolderService.findCardHolderEntity(testUserId)).thenReturn(testCardHolder);

            try (var mockedStatic = mockStatic(com.stripe.model.PaymentIntent.class)) {
                mockedStatic.when(() -> PaymentIntent.create(any())).thenThrow(new StripeException("Network error"));

                // ACT & ASSERT
                assertThrows(RuntimeException.class, () -> transactionService.initializeTransaction(testPaymentMessage));
                verify(transactionRepository).save(any(Transaction.class));
            }
        }
    }

    @Nested
    @DisplayName("TransactionService.findTransactionEntity Tests")
    class FindTransactionEntityTests {

        @Test
        @DisplayName("Should return transaction when found by ID")
        void testFindTransactionEntity_whenTransactionExists_expectsTransaction() {
            // ARRANGE
            when(transactionRepository.findById(testTransactionId)).thenReturn(Optional.of(testTransaction));

            // ACT
            Transaction result = transactionService.findTransactionEntity(testTransactionId);

            // ASSERT
            assertNotNull(result);
            assertEquals(testTransactionId, result.getId());
            assertEquals(testOrderId, result.getOrderId());
            verify(transactionRepository).findById(testTransactionId);
        }

        @Test
        @DisplayName("Should throw RuntimeException when transaction not found")
        void testFindTransactionEntity_whenTransactionNotFound_expectsException() {
            // ARRANGE
            when(transactionRepository.findById(testTransactionId)).thenReturn(Optional.empty());

            // ACT & ASSERT
            assertThrows(RuntimeException.class, () -> transactionService.findTransactionEntity(testTransactionId));
            verify(transactionRepository).findById(testTransactionId);
        }

        @Test
        @DisplayName("Should throw RuntimeException with correct message when transaction not found")
        void testFindTransactionEntity_whenNotFound_expectsCorrectMessage() {
            // ARRANGE
            when(transactionRepository.findById(testTransactionId)).thenReturn(Optional.empty());

            // ACT & ASSERT
            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> transactionService.findTransactionEntity(testTransactionId));
            assertEquals("Transaction not found", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("TransactionService.getTransactions Tests")
    class GetTransactionsTests {

        @BeforeEach
        void setUpSecurityContext() {
            SecurityContext securityContext = mock(SecurityContext.class);
            Authentication authentication = mock(Authentication.class);
            SecurityContextHolder.setContext(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
        }

        @Test
        @DisplayName("Should return transactions for authenticated user with USER role")
        void testGetTransactions_whenUserRole_expectsUserTransactions() {
            // ARRANGE
            SecurityContext securityContext = SecurityContextHolder.getContext();
            Authentication authentication = securityContext.getAuthentication();
            JWTUserPrincipal userPrincipal = new JWTUserPrincipal(testUserId, "user@example.com", new ArrayList<>());
            when(authentication.getPrincipal()).thenReturn(userPrincipal);

            List<Transaction> transactions = List.of(testTransaction);
            Page<Transaction> transactionPage = new PageImpl<>(transactions);
            when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(transactionPage);

            // ACT
            List<TransactionDto> result = transactionService.getTransactions(0, 10, "ASC", PaymentStatus.SUCCESS, null);

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.size());
            verify(transactionRepository).findAll(any(Specification.class), any(Pageable.class));
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when principal is not JWTUserPrincipal")
        void testGetTransactions_whenInvalidPrincipal_expectsAccessDenied() {
            // ARRANGE
            SecurityContext securityContext = SecurityContextHolder.getContext();
            Authentication authentication = securityContext.getAuthentication();
            when(authentication.getPrincipal()).thenReturn("invalid_principal");

            // ACT & ASSERT
            assertThrows(AccessDeniedException.class,
                    () -> transactionService.getTransactions(0, 10, "ASC", PaymentStatus.SUCCESS, null));
        }

        @Test
        @DisplayName("Should allow ADMIN to query other user's transactions")
        void testGetTransactions_whenAdminRole_expectsCanQueryOtherUsers() {
            // ARRANGE
            SecurityContext securityContext = SecurityContextHolder.getContext();
            Authentication authentication = securityContext.getAuthentication();
            Collection<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
            JWTUserPrincipal adminPrincipal = new JWTUserPrincipal(testUserId, "admin@example.com", new ArrayList<>(authorities));
            when(authentication.getPrincipal()).thenReturn(adminPrincipal);

            UUID otherUserId = UUID.randomUUID();
            List<Transaction> transactions = List.of(testTransaction);
            Page<Transaction> transactionPage = new PageImpl<>(transactions);
            when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(transactionPage);

            // ACT
            List<TransactionDto> result = transactionService.getTransactions(0, 10, "ASC", PaymentStatus.SUCCESS, otherUserId);

            // ASSERT
            assertNotNull(result);
            verify(transactionRepository).findAll(any(Specification.class), any(Pageable.class));
        }

        @Test
        @DisplayName("Should restrict non-admin users to their own transactions")
        void testGetTransactions_whenNonAdminUser_expectsRestrictedToOwnTransactions() {
            // ARRANGE
            SecurityContext securityContext = SecurityContextHolder.getContext();
            Authentication authentication = securityContext.getAuthentication();
            JWTUserPrincipal userPrincipal = new JWTUserPrincipal(testUserId, "user@example.com", new ArrayList<>());
            when(authentication.getPrincipal()).thenReturn(userPrincipal);

            UUID otherUserId = UUID.randomUUID();
            List<Transaction> transactions = List.of(testTransaction);
            Page<Transaction> transactionPage = new PageImpl<>(transactions);
            when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(transactionPage);

            // ACT
            List<TransactionDto> result = transactionService.getTransactions(0, 10, "ASC", PaymentStatus.SUCCESS, otherUserId);

            // ASSERT
            assertNotNull(result);
            verify(transactionRepository).findAll(any(Specification.class), any(Pageable.class));
        }

        @ParameterizedTest
        @ValueSource(strings = {"ASC", "DESC"})
        @DisplayName("Should handle different sort directions")
        void testGetTransactions_whenDifferentSortDirection_expectsCorrectSort(String sortDirection) {
            // ARRANGE
            SecurityContext securityContext = SecurityContextHolder.getContext();
            Authentication authentication = securityContext.getAuthentication();
            JWTUserPrincipal userPrincipal = new JWTUserPrincipal(testUserId, "user@example.com", new ArrayList<>());
            when(authentication.getPrincipal()).thenReturn(userPrincipal);

            List<Transaction> transactions = List.of(testTransaction);
            Page<Transaction> transactionPage = new PageImpl<>(transactions);
            when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(transactionPage);

            // ACT
            List<TransactionDto> result = transactionService.getTransactions(0, 10, sortDirection, PaymentStatus.SUCCESS, null);

            // ASSERT
            assertNotNull(result);
            verify(transactionRepository).findAll(any(Specification.class), any(Pageable.class));
        }
    }
}

@ExtendWith(MockitoExtension.class)
@DisplayName("Refund Service Implementation Tests")
class RefundServiceImplTest {

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private TransactionServiceImpl transactionService;

    @InjectMocks
    private RefundServiceImpl refundService;

    private UUID testTransactionId;
    private UUID testUserId;
    private CardHolder testCardHolder;
    private Transaction testTransaction;

    @BeforeEach
    void setUp() {
        testTransactionId = UUID.randomUUID();
        testUserId = UUID.randomUUID();

        testCardHolder = CardHolder.builder()
                .userId(testUserId)
                .stripeCustomerId("cus_test123")
                .email("test@example.com")
                .cardHolderName("John Doe")
                .cardStatus(CardStatus.ACTIVE)
                .build();

        testTransaction = Transaction.builder()
                .id(testTransactionId)
                .orderId(UUID.randomUUID())
                .cardHolder(testCardHolder)
                .amount(10000L)
                .currency(Currency.USD)
                .status(PaymentStatus.SUCCESS)
                .stripePaymentIntentId("pi_test123")
                .build();
    }

    @Nested
    @DisplayName("RefundService.processRefund Tests")
    class ProcessRefundTests {

        @Test
        @DisplayName("Should successfully process refund for valid transaction")
        void testProcessRefund_whenValidTransaction_expectsSuccess() throws StripeException {
            // ARRANGE
            when(transactionService.findTransactionEntity(testTransactionId)).thenReturn(testTransaction);
            Refund mockStripeRefund = mock(Refund.class);
            when(mockStripeRefund.getId()).thenReturn("re_test123");
            when(mockStripeRefund.getAmount()).thenReturn(10000L);

            try (var mockedStatic = mockStatic(com.stripe.model.Refund.class)) {
                mockedStatic.when(() -> Refund.create(any())).thenReturn(mockStripeRefund);

                // ACT
                refundService.processRefund(testTransactionId);

                // ASSERT
                ArgumentCaptor<io.github.marianciuc.streamingservice.payment.entity.Refund> refundCaptor =
                        ArgumentCaptor.forClass(io.github.marianciuc.streamingservice.payment.entity.Refund.class);
                verify(refundRepository).save(refundCaptor.capture());
                io.github.marianciuc.streamingservice.payment.entity.Refund savedRefund = refundCaptor.getValue();

                assertEquals(RefundStatus.SUCCESS, savedRefund.getStatus());
                assertEquals("re_test123", savedRefund.getStripeRefundId());
                assertEquals(10000L, savedRefund.getAmount());
                assertEquals(Currency.USD, savedRefund.getCurrency());
            }
        }

        @Test
        @DisplayName("Should handle Stripe exception during refund")
        void testProcessRefund_whenStripeException_expectsFailed() throws StripeException {
            // ARRANGE
            when(transactionService.findTransactionEntity(testTransactionId)).thenReturn(testTransaction);
            StripeException stripeException = new StripeException("Refund failed");

            try (var mockedStatic = mockStatic(com.stripe.model.Refund.class)) {
                mockedStatic.when(() -> Refund.create(any())).thenThrow(stripeException);

                // ACT & ASSERT
                assertThrows(RuntimeException.class, () -> refundService.processRefund(testTransactionId));

                ArgumentCaptor<io.github.marianciuc.streamingservice.payment.entity.Refund> refundCaptor =
                        ArgumentCaptor.forClass(io.github.marianciuc.streamingservice.payment.entity.Refund.class);
                verify(refundRepository).save(refundCaptor.capture());
                io.github.marianciuc.streamingservice.payment.entity.Refund savedRefund = refundCaptor.getValue();

                assertEquals(RefundStatus.FAILED, savedRefund.getStatus());
            }
        }

        @Test
        @DisplayName("Should save refund even when Stripe call fails")
        void testProcessRefund_whenStripeCallFails_expectsRefundSaved() throws StripeException {
            // ARRANGE
            when(transactionService.findTransactionEntity(testTransactionId)).thenReturn(testTransaction);

            try (var mockedStatic = mockStatic(com.stripe.model.Refund.class)) {
                mockedStatic.when(() -> Refund.create(any())).thenThrow(new StripeException("Network error"));

                // ACT & ASSERT
                assertThrows(RuntimeException.class, () -> refundService.processRefund(testTransactionId));
                verify(refundRepository).save(any(io.github.marianciuc.streamingservice.payment.entity.Refund.class));
            }
        }

        @Test
        @DisplayName("Should use correct transaction payment intent ID for refund")
        void testProcessRefund_whenProcessing_expectsCorrectPaymentIntentUsed() throws StripeException {
            // ARRANGE
            when(transactionService.findTransactionEntity(testTransactionId)).thenReturn(testTransaction);
            Refund mockStripeRefund = mock(Refund.class);
            when(mockStripeRefund.getId()).thenReturn("re_test123");
            when(mockStripeRefund.getAmount()).thenReturn(10000L);

            try (var mockedStatic = mockStatic(com.stripe.model.Refund.class)) {
                mockedStatic.when(() -> Refund.create(any())).thenReturn(mockStripeRefund);

                // ACT
                refundService.processRefund(testTransactionId);

                // ASSERT
                verify(transactionService).findTransactionEntity(testTransactionId);
            }
        }
    }
}

@ExtendWith(MockitoExtension.class)
@DisplayName("Address Service Implementation Tests")
class AddressServiceImplTest {

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private UserService userService;

    @Mock
    private CardHolderService cardHolderService;

    @InjectMocks
    private AddressServiceImpl addressService;

    private UUID testUserId;
    private UUID testAddressId;
    private AddressDto testAddressDto;
    private Address testAddress;
    private CardHolder testCardHolder;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testAddressId = UUID.randomUUID();

        testAddressDto = new AddressDto(
                testAddressId,
                "123 Main St",
                "Apt 4B",
                "New York",
                "10001",
                "NY",
                "United States"
        );

        testCardHolder = CardHolder.builder()
                .userId(testUserId)
                .stripeCustomerId("cus_test123")
                .email("test@example.com")
                .cardHolderName("John Doe")
                .cardStatus(CardStatus.ACTIVE)
                .build();

        testAddress = Address.builder()
                .id(testAddressId)
                .line1("123 Main St")
                .line2("Apt 4B")
                .city("New York")
                .postalCode("10001")
                .state("NY")
                .country("United States")
                .cardHolder(testCardHolder)
                .build();
    }

    @Nested
    @DisplayName("AddressService.addAddress Tests")
    class AddAddressTests {

        @Test
        @DisplayName("Should successfully add new address")
        void testAddAddress_whenValidDto_expectsAddressSaved() {
            // ARRANGE
            when(addressRepository.save(any(Address.class))).thenReturn(testAddress);

            // ACT
            Address result = addressService.addAddress(testAddressDto);

            // ASSERT
            assertNotNull(result);
            assertEquals(testAddressId, result.getId());
            assertEquals("123 Main St", result.getLine1());
            assertEquals("New York", result.getCity());
            verify(addressRepository).save(any(Address.class));
        }

        @Test
        @DisplayName("Should map all address fields correctly")
        void testAddAddress_whenValidDto_expectsAllFieldsMapped() {
            // ARRANGE
            when(addressRepository.save(any(Address.class))).thenReturn(testAddress);

            // ACT
            Address result = addressService.addAddress(testAddressDto);

            // ASSERT
            assertEquals("123 Main St", result.getLine1());
            assertEquals("Apt 4B", result.getLine2());
            assertEquals("New York", result.getCity());
            assertEquals("10001", result.getPostalCode());
            assertEquals("NY", result.getState());
            assertEquals("United States", result.getCountry());
        }

        @Test
        @DisplayName("Should handle address with optional line2 field")
        void testAddAddress_whenLine2IsEmpty_expectsAddressSaved() {
            // ARRANGE
            AddressDto addressWithoutLine2 = new AddressDto(
                    testAddressId,
                    "123 Main St",
                    "",
                    "New York",
                    "10001",
                    "NY",
                    "United States"
            );
            Address addressEntity = Address.builder()
                    .id(testAddressId)
                    .line1("123 Main St")
                    .line2("")
                    .city("New York")
                    .postalCode("10001")
                    .state("NY")
                    .country("United States")
                    .build();
            when(addressRepository.save(any(Address.class))).thenReturn(addressEntity);

            // ACT
            Address result = addressService.addAddress(addressWithoutLine2);

            // ASSERT
            assertNotNull(result);
            assertEquals("", result.getLine2());
        }
    }

    @Nested
    @DisplayName("AddressService.updateAddress Tests")
    class UpdateAddressTests {

        @Test
        @DisplayName("Should successfully update address with all fields")
        void testUpdateAddress_whenValidDto_expectsAddressUpdated() {
            // ARRANGE
            when(userService.extractUserIdFromAuth()).thenReturn(testUserId);
            when(cardHolderService.findCardHolderEntity(testUserId)).thenReturn(testCardHolder);
            when(addressRepository.findByCardHolder(testCardHolder)).thenReturn(Optional.of(testAddress));
            when(addressRepository.save(any(Address.class))).thenReturn(testAddress);

            AddressDto updateDto = new AddressDto(
                    testAddressId,
                    "456 Oak Ave",
                    "Suite 100",
                    "Los Angeles",
                    "90001",
                    "CA",
                    "United States"
            );

            // ACT
            addressService.updateAddress(updateDto);

            // ASSERT
            ArgumentCaptor<Address> addressCaptor = ArgumentCaptor.forClass(Address.class);
            verify(addressRepository).save(addressCaptor.capture());
            Address updatedAddress = addressCaptor.getValue();

            assertEquals("456 Oak Ave", updatedAddress.getLine1());
            assertEquals("Suite 100", updatedAddress.getLine2());
            assertEquals("Los Angeles", updatedAddress.getCity());
        }

        @Test
        @DisplayName("Should throw exception when address not found")
        void testUpdateAddress_whenAddressNotFound_expectsException() {
            // ARRANGE
            when(userService.extractUserIdFromAuth()).thenReturn(testUserId);
            when(cardHolderService.findCardHolderEntity(testUserId)).thenReturn(testCardHolder);
            when(addressRepository.findByCardHolder(testCardHolder)).thenReturn(Optional.empty());

            // ACT & ASSERT
            assertThrows(RuntimeException.class, () -> addressService.updateAddress(testAddressDto));
        }

        @Test
        @DisplayName("Should only update non-empty fields")
        void testUpdateAddress_whenPartialUpdate_expectsOnlyNonEmptyFieldsUpdated() {
            // ARRANGE
            when(userService.extractUserIdFromAuth()).thenReturn(testUserId);
            when(cardHolderService.findCardHolderEntity(testUserId)).thenReturn(testCardHolder);
            when(addressRepository.findByCardHolder(testCardHolder)).thenReturn(Optional.of(testAddress));
            when(addressRepository.save(any(Address.class))).thenReturn(testAddress);

            AddressDto partialUpdateDto = new AddressDto(
                    testAddressId,
                    "",
                    "",
                    "Boston",
                    "",
                    "",
                    ""
            );

            // ACT
            addressService.updateAddress(partialUpdateDto);

            // ASSERT
            ArgumentCaptor<Address> addressCaptor = ArgumentCaptor.forClass(Address.class);
            verify(addressRepository).save(addressCaptor.capture());
            Address updatedAddress = addressCaptor.getValue();

            assertEquals("Boston", updatedAddress.getCity());
        }
    }

    @Nested
    @DisplayName("AddressService.deleteAddress Tests")
    class DeleteAddressTests {

        @Test
        @DisplayName("Should successfully delete address by ID")
        void testDeleteAddress_whenValidId_expectsAddressDeleted() {
            // ARRANGE
            // ACT
            addressService.deleteAddress(testAddressId);

            // ASSERT
            verify(addressRepository).deleteById(testAddressId);
        }

        @Test
        @DisplayName("Should handle deletion of non-existent address")
        void testDeleteAddress_whenIdNotFound_expectsNoException() {
            // ARRANGE
            doNothing().when(addressRepository).deleteById(testAddressId);

            // ACT & ASSERT
            assertDoesNotThrow(() -> addressService.deleteAddress(testAddressId));
            verify(addressRepository).deleteById(testAddressId);
        }
    }
}
