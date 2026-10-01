/*
 * Copyright (c) 2024  Vladimir Marianciuc. All Rights Reserved.
 *
 * Project: STREAMING SERVICE APP
 * File: CustomerServiceImplTest.java
 *
 */

package io.github.marianciuc.streamingservice.customer.services;

import io.github.marianciuc.streamingservice.customer.dto.CustomerDto;
import io.github.marianciuc.streamingservice.customer.dto.PaginationResponse;
import io.github.marianciuc.streamingservice.customer.exceptions.EntityNotFoundException;
import io.github.marianciuc.streamingservice.customer.exceptions.VerificationCodeException;
import io.github.marianciuc.streamingservice.customer.kafka.messages.CreateUserMessage;
import io.github.marianciuc.streamingservice.customer.model.Customer;
import io.github.marianciuc.streamingservice.customer.repository.CustomerRepository;
import io.github.marianciuc.streamingservice.customer.services.impl.CustomerServiceImpl;
import io.github.marianciuc.streamingservice.customer.security.services.UserService;
import io.github.marianciuc.streamingservice.customer.specifications.CustomerSpecification;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CustomerServiceImpl.
 * Tests cover happy paths, error cases, boundary values, and side effects.
 * Framework: JUnit 5 (Jupiter) with Mockito
 * All external dependencies are mocked to ensure unit test isolation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerServiceImpl Tests")
public class CustomerServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private UserService userService;

    @InjectMocks
    private CustomerServiceImpl customerService;

    private UUID testCustomerId;
    private Customer testCustomer;
    private CustomerDto testCustomerDto;
    private CreateUserMessage testCreateUserMessage;

    @BeforeEach
    void setUp() {
        testCustomerId = UUID.randomUUID();
        testCustomer = Customer.builder()
                .id(testCustomerId)
                .email("test@example.com")
                .username("testuser")
                .theme("light")
                .preferredLanguage("en")
                .country("US")
                .birthDate(LocalDate.of(1990, 1, 1))
                .profilePicture("")
                .isEmailVerified(false)
                .profileIsCompleted(false)
                .receiveNewsletter(false)
                .enableNotifications(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testCustomerDto = new CustomerDto(
                testCustomerId,
                "light",
                "test@example.com",
                LocalDate.of(1990, 1, 1),
                "US",
                "testuser",
                "",
                "en",
                false,
                false,
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        testCreateUserMessage = new CreateUserMessage(
                testCustomerId,
                "test@example.com",
                "testuser"
        );
    }

    @Nested
    @DisplayName("createCustomer Tests")
    class CreateCustomerTests {

        @Test
        @DisplayName("When valid CreateUserMessage provided, expects customer saved with default values")
        void test_createCustomer_when_valid_message_expects_customer_saved() {
            // ARRANGE
            when(customerRepository.save(any(Customer.class))).thenReturn(testCustomer);

            // ACT
            customerService.createCustomer(testCreateUserMessage);

            // ASSERT
            ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
            verify(customerRepository, times(1)).save(customerCaptor.capture());
            Customer savedCustomer = customerCaptor.getValue();
            assertEquals(testCreateUserMessage.id(), savedCustomer.getId());
            assertEquals(testCreateUserMessage.email(), savedCustomer.getEmail());
            assertEquals(testCreateUserMessage.username(), savedCustomer.getUsername());
            assertEquals("light", savedCustomer.getTheme());
            assertEquals("en", savedCustomer.getPreferredLanguage());
            assertFalse(savedCustomer.isEmailVerified());
            assertFalse(savedCustomer.isProfileIsCompleted());
            assertFalse(savedCustomer.isReceiveNewsletter());
            assertTrue(savedCustomer.isEnableNotifications());
        }

        @Test
        @DisplayName("When null message provided, expects NullPointerException")
        void test_createCustomer_when_null_message_expects_null_pointer_exception() {
            // ARRANGE & ACT & ASSERT
            assertThrows(NullPointerException.class, () -> customerService.createCustomer(null));
        }

        @Test
        @DisplayName("When repository save fails, expects exception propagated")
        void test_createCustomer_when_repository_fails_expects_exception() {
            // ARRANGE
            when(customerRepository.save(any(Customer.class)))
                    .thenThrow(new RuntimeException("Database error"));

            // ACT & ASSERT
            assertThrows(RuntimeException.class, () -> customerService.createCustomer(testCreateUserMessage));
        }
    }

    @Nested
    @DisplayName("startEmailVerification Tests")
    class StartEmailVerificationTests {

        @Test
        @DisplayName("When email not verified, expects verification email sent")
        void test_startEmailVerification_when_email_not_verified_expects_email_sent() {
            // ARRANGE
            when(userService.extractUserIdFromAuth()).thenReturn(testCustomerId);
            when(customerRepository.findById(testCustomerId)).thenReturn(Optional.of(testCustomer));

            // ACT
            customerService.startEmailVerification();

            // ASSERT
            verify(emailVerificationService, times(1)).sendVerificationEmail(testCustomer.getEmail());
        }

        @Test
        @DisplayName("When email already verified, expects VerificationCodeException")
        void test_startEmailVerification_when_email_verified_expects_exception() {
            // ARRANGE
            testCustomer.setEmailVerified(true);
            when(userService.extractUserIdFromAuth()).thenReturn(testCustomerId);
            when(customerRepository.findById(testCustomerId)).thenReturn(Optional.of(testCustomer));

            // ACT & ASSERT
            VerificationCodeException exception = assertThrows(
                    VerificationCodeException.class,
                    () -> customerService.startEmailVerification()
            );
            assertEquals("Email is already verified", exception.getMessage());
            verify(emailVerificationService, never()).sendVerificationEmail(any());
        }

        @Test
        @DisplayName("When customer not found, expects EntityNotFoundException")
        void test_startEmailVerification_when_customer_not_found_expects_exception() {
            // ARRANGE
            when(userService.extractUserIdFromAuth()).thenReturn(testCustomerId);
            when(customerRepository.findById(testCustomerId)).thenReturn(Optional.empty());

            // ACT & ASSERT
            assertThrows(EntityNotFoundException.class, () -> customerService.startEmailVerification());
        }
    }

    @Nested
    @DisplayName("verifyCode Tests")
    class VerifyCodeTests {

        @Test
        @DisplayName("When valid verification code provided, expects email marked verified")
        void test_verifyCode_when_valid_code_expects_email_verified() {
            // ARRANGE
            String verificationCode = "123456";
            when(userService.extractUserIdFromAuth()).thenReturn(testCustomerId);
            when(customerRepository.findById(testCustomerId)).thenReturn(Optional.of(testCustomer));
            when(emailVerificationService.verifyEmail(verificationCode)).thenReturn(testCustomer.getEmail());
            when(customerRepository.save(any(Customer.class))).thenReturn(testCustomer);

            // ACT
            customerService.verifyCode(verificationCode);

            // ASSERT
            ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
            verify(customerRepository, times(1)).save(customerCaptor.capture());
            assertTrue(customerCaptor.getValue().isEmailVerified());
        }

        @Test
        @DisplayName("When invalid verification code provided, expects VerificationCodeException")
        void test_verifyCode_when_invalid_code_expects_exception() {
            // ARRANGE
            String invalidCode = "999999";
            when(userService.extractUserIdFromAuth()).thenReturn(testCustomerId);
            when(customerRepository.findById(testCustomerId)).thenReturn(Optional.of(testCustomer));
            when(emailVerificationService.verifyEmail(invalidCode))
                    .thenThrow(new VerificationCodeException("Invalid or expired verification code"));

            // ACT & ASSERT
            assertThrows(VerificationCodeException.class, () -> customerService.verifyCode(invalidCode));
            verify(customerRepository, never()).save(any());
        }

        @Test
        @DisplayName("When email mismatch between code and customer, expects VerificationCodeException")
        void test_verifyCode_when_email_mismatch_expects_exception() {
            // ARRANGE
            String verificationCode = "123456";
            String differentEmail = "different@example.com";
            when(userService.extractUserIdFromAuth()).thenReturn(testCustomerId);
            when(customerRepository.findById(testCustomerId)).thenReturn(Optional.of(testCustomer));
            when(emailVerificationService.verifyEmail(verificationCode)).thenReturn(differentEmail);

            // ACT & ASSERT
            VerificationCodeException exception = assertThrows(
                    VerificationCodeException.class,
                    () -> customerService.verifyCode(verificationCode)
            );
            assertEquals("Invalid verification code", exception.getMessage());
            verify(customerRepository, never()).save(any());
        }

        @Test
        @DisplayName("When customer not found, expects EntityNotFoundException")
        void test_verifyCode_when_customer_not_found_expects_exception() {
            // ARRANGE
            when(userService.extractUserIdFromAuth()).thenReturn(testCustomerId);
            when(customerRepository.findById(testCustomerId)).thenReturn(Optional.empty());

            // ACT & ASSERT
            assertThrows(EntityNotFoundException.class, () -> customerService.verifyCode("123456"));
        }
    }

    @Nested
    @DisplayName("findAllByFilter Tests")
    class FindAllByFilterTests {

        @Test
        @DisplayName("When no filters provided, expects all customers paginated")
        void test_findAllByFilter_when_no_filters_expects_all_customers() {
            // ARRANGE
            List<Customer> customers = List.of(testCustomer);
            Page<Customer> page = new PageImpl<>(customers, PageRequest.of(0, 10), 1);
            when(customerRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

            // ACT
            PaginationResponse<List<CustomerDto>> result = customerService.findAllByFilter(
                    0, 10, null, null, null, false, null
            );

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.totalPages());
            assertEquals(0, result.currentPage());
            assertEquals(10, result.pageSize());
            assertEquals(1, result.data().size());
        }

        @Test
        @DisplayName("When country filter provided, expects filtered results")
        void test_findAllByFilter_when_country_filter_expects_filtered_results() {
            // ARRANGE
            List<Customer> customers = List.of(testCustomer);
            Page<Customer> page = new PageImpl<>(customers, PageRequest.of(0, 10), 1);
            when(customerRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

            // ACT
            PaginationResponse<List<CustomerDto>> result = customerService.findAllByFilter(
                    0, 10, "US", null, null, false, null
            );

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.data().size());
            verify(customerRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
        }

        @Test
        @DisplayName("When email filter provided, expects filtered results")
        void test_findAllByFilter_when_email_filter_expects_filtered_results() {
            // ARRANGE
            List<Customer> customers = List.of(testCustomer);
            Page<Customer> page = new PageImpl<>(customers, PageRequest.of(0, 10), 1);
            when(customerRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

            // ACT
            PaginationResponse<List<CustomerDto>> result = customerService.findAllByFilter(
                    0, 10, null, "test@", null, false, null
            );

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.data().size());
        }

        @Test
        @DisplayName("When no results match filters, expects empty list")
        void test_findAllByFilter_when_no_results_expects_empty_list() {
            // ARRANGE
            Page<Customer> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
            when(customerRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

            // ACT
            PaginationResponse<List<CustomerDto>> result = customerService.findAllByFilter(
                    0, 10, "NonExistent", null, null, false, null
            );

            // ASSERT
            assertNotNull(result);
            assertEquals(0, result.data().size());
            assertEquals(0, result.totalPages());
        }

        @Test
        @DisplayName("When email verified filter is true, expects only verified customers")
        void test_findAllByFilter_when_email_verified_filter_expects_verified_only() {
            // ARRANGE
            testCustomer.setEmailVerified(true);
            List<Customer> customers = List.of(testCustomer);
            Page<Customer> page = new PageImpl<>(customers, PageRequest.of(0, 10), 1);
            when(customerRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

            // ACT
            PaginationResponse<List<CustomerDto>> result = customerService.findAllByFilter(
                    0, 10, null, null, null, true, null
            );

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.data().size());
        }
    }

    @Nested
    @DisplayName("findById Tests")
    class FindByIdTests {

        @Test
        @DisplayName("When customer exists, expects CustomerDto returned")
        void test_findById_when_customer_exists_expects_dto_returned() {
            // ARRANGE
            when(customerRepository.findById(testCustomerId)).thenReturn(Optional.of(testCustomer));

            // ACT
            CustomerDto result = customerService.findById(testCustomerId);

            // ASSERT
            assertNotNull(result);
            assertEquals(testCustomerId, result.id());
            assertEquals(testCustomer.getEmail(), result.email());
            assertEquals(testCustomer.getUsername(), result.username());
        }

        @Test
        @DisplayName("When customer does not exist, expects EntityNotFoundException")
        void test_findById_when_customer_not_found_expects_exception() {
            // ARRANGE
            when(customerRepository.findById(testCustomerId)).thenReturn(Optional.empty());

            // ACT & ASSERT
            EntityNotFoundException exception = assertThrows(
                    EntityNotFoundException.class,
                    () -> customerService.findById(testCustomerId)
            );
            assertEquals("Customer not found", exception.getMessage());
        }

        @Test
        @DisplayName("When null UUID provided, expects exception")
        void test_findById_when_null_uuid_expects_exception() {
            // ARRANGE
            when(customerRepository.findById(null)).thenReturn(Optional.empty());

            // ACT & ASSERT
            assertThrows(EntityNotFoundException.class, () -> customerService.findById(null));
        }
    }

    @Nested
    @DisplayName("updateCustomerDetails Tests")
    class UpdateCustomerDetailsTests {

        @Test
        @DisplayName("When email updated, expects email changed and verification reset")
        void test_updateCustomerDetails_when_email_updated_expects_verification_reset() {
            // ARRANGE
            testCustomer.setEmailVerified(true);
            CustomerDto updateDto = new CustomerDto(
                    testCustomerId,
                    null,
                    "newemail@example.com",
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    false,
                    false,
                    null,
                    null
            );
            when(userService.extractUserIdFromAuth()).thenReturn(testCustomerId);
            when(customerRepository.findById(testCustomerId)).thenReturn(Optional.of(testCustomer));
            when(customerRepository.save(any(Customer.class))).thenReturn(testCustomer);

            // ACT
            customerService.updateCustomerDetails(updateDto);

            // ASSERT
            ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
            verify(customerRepository, times(1)).save(customerCaptor.capture());
            Customer updatedCustomer = customerCaptor.getValue();
            assertEquals("newemail@example.com", updatedCustomer.getEmail());
            assertFalse(updatedCustomer.isEmailVerified());
        }

        @Test
        @DisplayName("When theme updated, expects theme changed")
        void test_updateCustomerDetails_when_theme_updated_expects_theme_changed() {
            // ARRANGE
            CustomerDto updateDto = new CustomerDto(
                    testCustomerId,
                    "dark",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    false,
                    false,
                    null,
                    null
            );
            when(userService.extractUserIdFromAuth()).thenReturn(testCustomerId);
            when(customerRepository.findById(testCustomerId)).thenReturn(Optional.of(testCustomer));
            when(customerRepository.save(any(Customer.class))).thenReturn(testCustomer);

            // ACT
            customerService.updateCustomerDetails(updateDto);

            // ASSERT
            ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
            verify(customerRepository, times(1)).save(customerCaptor.capture());
            assertEquals("dark", customerCaptor.getValue().getTheme());
        }

        @Test
        @DisplayName("When all fields updated, expects all fields changed")
        void test_updateCustomerDetails_when_all_fields_updated_expects_all_changed() {
            // ARRANGE
            LocalDate newBirthDate = LocalDate.of(1995, 5, 15);
            CustomerDto updateDto = new CustomerDto(
                    testCustomerId,
                    "dark",
                    "newemail@example.com",
                    newBirthDate,
                    "CA",
                    null,
                    "newpic.jpg",
                    "fr",
                    false,
                    true,
                    false,
                    null,
                    null
            );
            when(userService.extractUserIdFromAuth()).thenReturn(testCustomerId);
            when(customerRepository.findById(testCustomerId)).thenReturn(Optional.of(testCustomer));
            when(customerRepository.save(any(Customer.class))).thenReturn(testCustomer);

            // ACT
            customerService.updateCustomerDetails(updateDto);

            // ASSERT
            ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
            verify(customerRepository, times(1)).save(customerCaptor.capture());
            Customer updatedCustomer = customerCaptor.getValue();
            assertEquals("dark", updatedCustomer.getTheme());
            assertEquals("newemail@example.com", updatedCustomer.getEmail());
            assertEquals(newBirthDate, updatedCustomer.getBirthDate());
            assertEquals("CA", updatedCustomer.getCountry());
            assertEquals("newpic.jpg", updatedCustomer.getProfilePicture());
            assertEquals("fr", updatedCustomer.getPreferredLanguage());
            assertTrue(updatedCustomer.isReceiveNewsletter());
            assertFalse(updatedCustomer.isEnableNotifications());
        }

        @Test
        @DisplayName("When null fields provided, expects no update for those fields")
        void test_updateCustomerDetails_when_null_fields_expects_no_update() {
            // ARRANGE
            String originalEmail = testCustomer.getEmail();
            String originalTheme = testCustomer.getTheme();
            CustomerDto updateDto = new CustomerDto(
                    testCustomerId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    false,
                    false,
                    null,
                    null
            );
            when(userService.extractUserIdFromAuth()).thenReturn(testCustomerId);
            when(customerRepository.findById(testCustomerId)).thenReturn(Optional.of(testCustomer));
            when(customerRepository.save(any(Customer.class))).thenReturn(testCustomer);

            // ACT
            customerService.updateCustomerDetails(updateDto);

            // ASSERT
            ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
            verify(customerRepository, times(1)).save(customerCaptor.capture());
            Customer updatedCustomer = customerCaptor.getValue();
            assertEquals(originalEmail, updatedCustomer.getEmail());
            assertEquals(originalTheme, updatedCustomer.getTheme());
        }

        @Test
        @DisplayName("When customer not found, expects EntityNotFoundException")
        void test_updateCustomerDetails_when_customer_not_found_expects_exception() {
            // ARRANGE
            when(userService.extractUserIdFromAuth()).thenReturn(testCustomerId);
            when(customerRepository.findById(testCustomerId)).thenReturn(Optional.empty());

            // ACT & ASSERT
            assertThrows(EntityNotFoundException.class, () -> customerService.updateCustomerDetails(testCustomerDto));
        }
    }
}
