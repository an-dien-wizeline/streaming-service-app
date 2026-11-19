/*
 * Copyright (c) 2024  Vladimir Marianciuc. All Rights Reserved.
 *
 * Project: STREAMING SERVICE APP
 * File: StreamingServiceUnitTests.java
 * Description: Comprehensive unit test suite for Customer, Order, and Media services
 *
 */

package io.github.marianciuc.streamingservice.customer;

import io.github.marianciuc.streamingservice.customer.dto.CustomerDto;
import io.github.marianciuc.streamingservice.customer.dto.PaginationResponse;
import io.github.marianciuc.streamingservice.customer.exceptions.EntityNotFoundException;
import io.github.marianciuc.streamingservice.customer.exceptions.VerificationCodeException;
import io.github.marianciuc.streamingservice.customer.kafka.messages.CreateUserMessage;
import io.github.marianciuc.streamingservice.customer.model.Customer;
import io.github.marianciuc.streamingservice.customer.repository.CustomerRepository;
import io.github.marianciuc.streamingservice.customer.services.EmailVerificationService;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test suite for CustomerServiceImpl.
 * Tests cover: happy paths, boundary values, error conditions, and side effects.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Customer Service Unit Tests")
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
                .profilePicture("")
                .country("US")
                .birthDate(LocalDate.of(1990, 1, 1))
                .profileIsCompleted(false)
                .isEmailVerified(false)
                .receiveNewsletter(false)
                .enableNotifications(true)
                .build();

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
        @DisplayName("should create customer with valid message")
        void test_createCustomer_when_valid_message_expects_customer_saved() {
            // ARRANGE
            when(customerRepository.save(any(Customer.class))).thenReturn(testCustomer);

            // ACT
            customerService.createCustomer(testCreateUserMessage);

            // ASSERT
            ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
            verify(customerRepository, times(1)).save(customerCaptor.capture());
            Customer savedCustomer = customerCaptor.getValue();
            assertEquals(testCreateUserMessage.email(), savedCustomer.getEmail());
            assertEquals(testCreateUserMessage.username(), savedCustomer.getUsername());
            assertEquals("light", savedCustomer.getTheme());
            assertEquals("en", savedCustomer.getPreferredLanguage());
            assertFalse(savedCustomer.isEmailVerified());
            assertFalse(savedCustomer.isProfileIsCompleted());
        }

        @Test
        @DisplayName("should set default values when creating customer")
        void test_createCustomer_when_defaults_expects_correct_values() {
            // ARRANGE
            when(customerRepository.save(any(Customer.class))).thenReturn(testCustomer);

            // ACT
            customerService.createCustomer(testCreateUserMessage);

            // ASSERT
            ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
            verify(customerRepository).save(customerCaptor.capture());
            Customer savedCustomer = customerCaptor.getValue();
            assertEquals("light", savedCustomer.getTheme());
            assertEquals("en", savedCustomer.getPreferredLanguage());
            assertFalse(savedCustomer.isReceiveNewsletter());
            assertTrue(savedCustomer.isEnableNotifications());
        }

        @Test
        @DisplayName("should handle repository save failure")
        void test_createCustomer_when_repository_fails_expects_exception() {
            // ARRANGE
            when(customerRepository.save(any(Customer.class)))
                    .thenThrow(new RuntimeException("Database error"));

            // ACT & ASSERT
            assertThrows(RuntimeException.class, () -> customerService.createCustomer(testCreateUserMessage));
            verify(customerRepository, times(1)).save(any(Customer.class));
        }
    }

    @Nested
    @DisplayName("startEmailVerification Tests")
    class StartEmailVerificationTests {

        @Test
        @DisplayName("should start email verification for unverified customer")
        void test_startEmailVerification_when_unverified_expects_email_sent() {
            // ARRANGE
            when(userService.extractUserIdFromAuth()).thenReturn(testCustomerId);
            when(customerRepository.findById(testCustomerId)).thenReturn(Optional.of(testCustomer));

            // ACT
            customerService.startEmailVerification();

            // ASSERT
            verify(emailVerificationService, times(1)).sendVerificationEmail(testCustomer.getEmail());
        }

        @Test
        @DisplayName("should throw exception when email already verified")
        void test_startEmailVerification_when_already_verified_expects_exception() {
            // ARRANGE
            testCustomer.setEmailVerified(true);
            when(userService.extractUserIdFromAuth()).thenReturn(testCustomerId);
            when(customerRepository.findById(testCustomerId)).thenReturn(Optional.of(testCustomer));

            // ACT & ASSERT
            assertThrows(VerificationCodeException.class, () -> customerService.startEmailVerification());
            verify(emailVerificationService, never()).sendVerificationEmail(anyString());
        }

        @Test
        @DisplayName("should throw exception when customer not found")
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
        @DisplayName("should verify email with valid code")
        void test_verifyCode_when_valid_code_expects_email_verified() {
            // ARRANGE
            String verificationCode = "ABC123";
            when(userService.extractUserIdFromAuth()).thenReturn(testCustomerId);
            when(customerRepository.findById(testCustomerId)).thenReturn(Optional.of(testCustomer));
            when(emailVerificationService.verifyEmail(verificationCode)).thenReturn(testCustomer.getEmail());

            // ACT
            customerService.verifyCode(verificationCode);

            // ASSERT
            ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
            verify(customerRepository).save(customerCaptor.capture());
            assertTrue(customerCaptor.getValue().isEmailVerified());
        }

        @Test
        @DisplayName("should throw exception when code is invalid")
        void test_verifyCode_when_invalid_code_expects_exception() {
            // ARRANGE
            String invalidCode = "INVALID";
            when(userService.extractUserIdFromAuth()).thenReturn(testCustomerId);
            when(customerRepository.findById(testCustomerId)).thenReturn(Optional.of(testCustomer));
            when(emailVerificationService.verifyEmail(invalidCode))
                    .thenThrow(new VerificationCodeException("Invalid code"));

            // ACT & ASSERT
            assertThrows(VerificationCodeException.class, () -> customerService.verifyCode(invalidCode));
            verify(customerRepository, never()).save(any(Customer.class));
        }

        @Test
        @DisplayName("should throw exception when verified email does not match customer email")
        void test_verifyCode_when_email_mismatch_expects_exception() {
            // ARRANGE
            String verificationCode = "ABC123";
            String differentEmail = "different@example.com";
            when(userService.extractUserIdFromAuth()).thenReturn(testCustomerId);
            when(customerRepository.findById(testCustomerId)).thenReturn(Optional.of(testCustomer));
            when(emailVerificationService.verifyEmail(verificationCode)).thenReturn(differentEmail);

            // ACT & ASSERT
            assertThrows(VerificationCodeException.class, () -> customerService.verifyCode(verificationCode));
            verify(customerRepository, never()).save(any(Customer.class));
        }
    }

    @Nested
    @DisplayName("findAllByFilter Tests")
    class FindAllByFilterTests {

        @Test
        @DisplayName("should return paginated customers with no filters")
        void test_findAllByFilter_when_no_filters_expects_all_customers() {
            // ARRANGE
            List<Customer> customers = List.of(testCustomer);
            Page<Customer> customerPage = new PageImpl<>(customers, PageRequest.of(0, 10), 1);
            when(customerRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(customerPage);

            // ACT
            PaginationResponse<List<CustomerDto>> result = customerService.findAllByFilter(
                    1, 10, null, null, null, false, null
            );

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.getTotalPages());
            assertEquals(0, result.getCurrentPage());
            assertEquals(10, result.getPageSize());
            assertEquals(1, result.getContent().size());
        }

        @Test
        @DisplayName("should filter customers by country")
        void test_findAllByFilter_when_country_filter_expects_filtered_results() {
            // ARRANGE
            List<Customer> customers = List.of(testCustomer);
            Page<Customer> customerPage = new PageImpl<>(customers, PageRequest.of(0, 10), 1);
            when(customerRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(customerPage);

            // ACT
            PaginationResponse<List<CustomerDto>> result = customerService.findAllByFilter(
                    1, 10, "US", null, null, false, null
            );

            // ASSERT
            assertNotNull(result);
            verify(customerRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
        }

        @Test
        @DisplayName("should return empty page when no customers match filter")
        void test_findAllByFilter_when_no_matches_expects_empty_page() {
            // ARRANGE
            Page<Customer> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
            when(customerRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(emptyPage);

            // ACT
            PaginationResponse<List<CustomerDto>> result = customerService.findAllByFilter(
                    1, 10, "NonExistent", null, null, false, null
            );

            // ASSERT
            assertNotNull(result);
            assertEquals(0, result.getTotalPages());
            assertTrue(result.getContent().isEmpty());
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2, 5, 10})
        @DisplayName("should handle various page sizes")
        void test_findAllByFilter_when_various_page_sizes_expects_correct_pagination(int pageSize) {
            // ARRANGE
            Page<Customer> customerPage = new PageImpl<>(List.of(testCustomer), PageRequest.of(0, pageSize), 1);
            when(customerRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(customerPage);

            // ACT
            PaginationResponse<List<CustomerDto>> result = customerService.findAllByFilter(
                    1, pageSize, null, null, null, false, null
            );

            // ASSERT
            assertEquals(pageSize, result.getPageSize());
        }
    }

    @Nested
    @DisplayName("findById Tests")
    class FindByIdTests {

        @Test
        @DisplayName("should return customer when found")
        void test_findById_when_customer_exists_expects_customer_dto() {
            // ARRANGE
            when(customerRepository.findById(testCustomerId)).thenReturn(Optional.of(testCustomer));

            // ACT
            CustomerDto result = customerService.findById(testCustomerId);

            // ASSERT
            assertNotNull(result);
            assertEquals(testCustomer.getEmail(), result.email());
            assertEquals(testCustomer.getUsername(), result.username());
        }

        @Test
        @DisplayName("should throw exception when customer not found")
        void test_findById_when_customer_not_found_expects_exception() {
            // ARRANGE
            when(customerRepository.findById(testCustomerId)).thenReturn(Optional.empty());

            // ACT & ASSERT
            assertThrows(EntityNotFoundException.class, () -> customerService.findById(testCustomerId));
        }

        @Test
        @DisplayName("should handle null customer ID")
        void test_findById_when_null_id_expects_exception() {
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
        @DisplayName("should update customer email and reset verification")
        void test_updateCustomerDetails_when_email_changed_expects_verification_reset() {
            // ARRANGE
            testCustomer.setEmailVerified(true);
            CustomerDto updateDto = new CustomerDto(
                    testCustomerId,
                    "newemail@example.com",
                    "testuser",
                    "light",
                    "en",
                    "",
                    "US",
                    LocalDate.of(1990, 1, 1),
                    false,
                    false,
                    false,
                    true
            );
            when(userService.extractUserIdFromAuth()).thenReturn(testCustomerId);
            when(customerRepository.findById(testCustomerId)).thenReturn(Optional.of(testCustomer));

            // ACT
            customerService.updateCustomerDetails(updateDto);

            // ASSERT
            ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
            verify(customerRepository).save(customerCaptor.capture());
            Customer updatedCustomer = customerCaptor.getValue();
            assertEquals("newemail@example.com", updatedCustomer.getEmail());
            assertFalse(updatedCustomer.isEmailVerified());
        }

        @Test
        @DisplayName("should update theme preference")
        void test_updateCustomerDetails_when_theme_changed_expects_theme_updated() {
            // ARRANGE
            CustomerDto updateDto = new CustomerDto(
                    testCustomerId,
                    null,
                    "testuser",
                    "dark",
                    null,
                    null,
                    null,
                    null,
                    false,
                    false,
                    false,
                    true
            );
            when(userService.extractUserIdFromAuth()).thenReturn(testCustomerId);
            when(customerRepository.findById(testCustomerId)).thenReturn(Optional.of(testCustomer));

            // ACT
            customerService.updateCustomerDetails(updateDto);

            // ASSERT
            ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
            verify(customerRepository).save(customerCaptor.capture());
            assertEquals("dark", customerCaptor.getValue().getTheme());
        }

        @Test
        @DisplayName("should update multiple fields simultaneously")
        void test_updateCustomerDetails_when_multiple_fields_expects_all_updated() {
            // ARRANGE
            CustomerDto updateDto = new CustomerDto(
                    testCustomerId,
                    "newemail@example.com",
                    "testuser",
                    "dark",
                    "fr",
                    "profile.jpg",
                    "FR",
                    LocalDate.of(1995, 5, 15),
                    false,
                    false,
                    true,
                    false
            );
            when(userService.extractUserIdFromAuth()).thenReturn(testCustomerId);
            when(customerRepository.findById(testCustomerId)).thenReturn(Optional.of(testCustomer));

            // ACT
            customerService.updateCustomerDetails(updateDto);

            // ASSERT
            ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
            verify(customerRepository).save(customerCaptor.capture());
            Customer updatedCustomer = customerCaptor.getValue();
            assertEquals("newemail@example.com", updatedCustomer.getEmail());
            assertEquals("dark", updatedCustomer.getTheme());
            assertEquals("fr", updatedCustomer.getPreferredLanguage());
            assertEquals("profile.jpg", updatedCustomer.getProfilePicture());
            assertEquals("FR", updatedCustomer.getCountry());
            assertEquals(LocalDate.of(1995, 5, 15), updatedCustomer.getBirthDate());
        }

        @Test
        @DisplayName("should not update fields when DTO values are null")
        void test_updateCustomerDetails_when_null_values_expects_no_update() {
            // ARRANGE
            String originalEmail = testCustomer.getEmail();
            CustomerDto updateDto = new CustomerDto(
                    testCustomerId,
                    null,
                    "testuser",
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    false,
                    false,
                    true
            );
            when(userService.extractUserIdFromAuth()).thenReturn(testCustomerId);
            when(customerRepository.findById(testCustomerId)).thenReturn(Optional.of(testCustomer));

            // ACT
            customerService.updateCustomerDetails(updateDto);

            // ASSERT
            ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
            verify(customerRepository).save(customerCaptor.capture());
            assertEquals(originalEmail, customerCaptor.getValue().getEmail());
        }

        @Test
        @DisplayName("should throw exception when customer not found during update")
        void test_updateCustomerDetails_when_customer_not_found_expects_exception() {
            // ARRANGE
            CustomerDto updateDto = new CustomerDto(
                    testCustomerId,
                    "newemail@example.com",
                    "testuser",
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    false,
                    false,
                    true
            );
            when(userService.extractUserIdFromAuth()).thenReturn(testCustomerId);
            when(customerRepository.findById(testCustomerId)).thenReturn(Optional.empty());

            // ACT & ASSERT
            assertThrows(EntityNotFoundException.class, () -> customerService.updateCustomerDetails(updateDto));
            verify(customerRepository, never()).save(any(Customer.class));
        }
    }
}
