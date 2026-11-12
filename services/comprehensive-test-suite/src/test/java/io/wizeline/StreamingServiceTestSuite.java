package io.wizeline.streamingservice;

import io.wizeline.userservice.dto.UserRegistrationRequest;
import io.wizeline.userservice.dto.UserLoginRequest;
import io.wizeline.userservice.dto.UserResponse;
import io.wizeline.userservice.entity.User;
import io.wizeline.userservice.repository.UserRepository;
import io.wizeline.userservice.service.UserService;
import io.wizeline.userservice.service.JwtTokenProvider;
import io.wizeline.userservice.exception.UserAlreadyExistsException;
import io.wizeline.userservice.exception.InvalidCredentialsException;
import io.wizeline.userservice.exception.UserNotFoundException;
import io.wizeline.paymentservice.dto.PaymentRequest;
import io.wizeline.paymentservice.dto.PaymentResponse;
import io.wizeline.paymentservice.entity.Payment;
import io.wizeline.paymentservice.entity.PaymentStatus;
import io.wizeline.paymentservice.repository.PaymentRepository;
import io.wizeline.paymentservice.service.PaymentService;
import io.wizeline.paymentservice.service.StripeService;
import io.wizeline.paymentservice.exception.PaymentProcessingException;
import io.wizeline.paymentservice.exception.InvalidPaymentAmountException;
import io.wizeline.subscriptionservice.dto.SubscriptionRequest;
import io.wizeline.subscriptionservice.dto.SubscriptionResponse;
import io.wizeline.subscriptionservice.entity.Subscription;
import io.wizeline.subscriptionservice.entity.SubscriptionStatus;
import io.wizeline.subscriptionservice.entity.SubscriptionPlan;
import io.wizeline.subscriptionservice.repository.SubscriptionRepository;
import io.wizeline.subscriptionservice.service.SubscriptionService;
import io.wizeline.subscriptionservice.service.PaymentServiceClient;
import io.wizeline.subscriptionservice.service.NotificationServiceClient;
import io.wizeline.subscriptionservice.exception.SubscriptionException;
import io.wizeline.mediaservice.dto.VideoUploadRequest;
import io.wizeline.mediaservice.dto.VideoResponse;
import io.wizeline.mediaservice.dto.UploadProgressResponse;
import io.wizeline.mediaservice.entity.Video;
import io.wizeline.mediaservice.entity.VideoStatus;
import io.wizeline.mediaservice.repository.VideoRepository;
import io.wizeline.mediaservice.service.MediaService;
import io.wizeline.mediaservice.service.MinIOService;
import io.wizeline.mediaservice.service.RedisService;
import io.wizeline.mediaservice.service.KafkaProducerService;
import io.wizeline.mediaservice.exception.VideoUploadException;
import io.wizeline.mediaservice.exception.VideoNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Streaming Service Comprehensive Test Suite")
class StreamingServiceTestSuite {

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("User Service Tests")
    class UserServiceTests {
        @Mock
        private UserRepository userRepository;
        @Mock
        private JwtTokenProvider jwtTokenProvider;
        @Mock
        private PasswordEncoder passwordEncoder;
        @InjectMocks
        private UserService userService;

        private User testUser;
        private UserRegistrationRequest registrationRequest;
        private UserLoginRequest loginRequest;

        @BeforeEach
        void setUp() {
            testUser = User.builder()
                    .id(UUID.randomUUID())
                    .email("test@example.com")
                    .username("testuser")
                    .password("hashedPassword123")
                    .firstName("John")
                    .lastName("Doe")
                    .isActive(true)
                    .build();

            registrationRequest = UserRegistrationRequest.builder()
                    .email("newuser@example.com")
                    .username("newuser")
                    .password("SecurePass123!")
                    .firstName("Jane")
                    .lastName("Smith")
                    .build();

            loginRequest = UserLoginRequest.builder()
                    .email("test@example.com")
                    .password("SecurePass123!")
                    .build();
        }

        @Test
        @DisplayName("Should register user successfully when valid request provided")
        void testRegisterUser_whenValidRequest_expectsSuccess() {
            when(userRepository.existsByEmail(registrationRequest.getEmail())).thenReturn(false);
            when(userRepository.existsByUsername(registrationRequest.getUsername())).thenReturn(false);
            when(passwordEncoder.encode(registrationRequest.getPassword())).thenReturn("hashedPassword");
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            UserResponse response = userService.registerUser(registrationRequest);

            assertNotNull(response);
            assertEquals(testUser.getEmail(), response.getEmail());
            verify(userRepository, times(1)).save(any(User.class));
        }

        @Test
        @DisplayName("Should throw exception when email already exists")
        void testRegisterUser_whenEmailExists_expectsUserAlreadyExistsException() {
            when(userRepository.existsByEmail(registrationRequest.getEmail())).thenReturn(true);

            assertThrows(UserAlreadyExistsException.class, () -> {
                userService.registerUser(registrationRequest);
            });
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Should authenticate user and return JWT token when credentials valid")
        void testLoginUser_whenValidCredentials_expectsJwtToken() {
            String expectedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...";
            when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches(loginRequest.getPassword(), testUser.getPassword())).thenReturn(true);
            when(jwtTokenProvider.generateToken(testUser.getId())).thenReturn(expectedToken);

            String token = userService.loginUser(loginRequest);

            assertNotNull(token);
            assertEquals(expectedToken, token);
            verify(jwtTokenProvider, times(1)).generateToken(testUser.getId());
        }

        @Test
        @DisplayName("Should throw exception when user not found during login")
        void testLoginUser_whenUserNotFound_expectsUserNotFoundException() {
            when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.empty());

            assertThrows(UserNotFoundException.class, () -> {
                userService.loginUser(loginRequest);
            });
            verify(jwtTokenProvider, never()).generateToken(any());
        }

        @Test
        @DisplayName("Should throw exception when password is incorrect")
        void testLoginUser_whenInvalidPassword_expectsInvalidCredentialsException() {
            when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches(loginRequest.getPassword(), testUser.getPassword())).thenReturn(false);

            assertThrows(InvalidCredentialsException.class, () -> {
                userService.loginUser(loginRequest);
            });
            verify(jwtTokenProvider, never()).generateToken(any());
        }

        @Test
        @DisplayName("Should retrieve user profile by ID")
        void testGetUserProfile_whenUserExists_expectsUserResponse() {
            UUID userId = testUser.getId();
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

            UserResponse response = userService.getUserProfile(userId);

            assertNotNull(response);
            assertEquals(testUser.getEmail(), response.getEmail());
        }

        @Test
        @DisplayName("Should deactivate user account")
        void testDeactivateUser_whenUserExists_expectsSuccess() {
            UUID userId = testUser.getId();
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            userService.deactivateUser(userId);

            verify(userRepository, times(1)).save(any(User.class));
        }

        @ParameterizedTest
        @ValueSource(strings = {"test@example.com", "user@domain.co.uk", "name+tag@example.com"})
        @DisplayName("Should validate various email formats")
        void testValidateEmail_withValidFormats_expectsSuccess(String email) {
            registrationRequest.setEmail(email);
            when(userRepository.existsByEmail(email)).thenReturn(false);
            when(userRepository.existsByUsername(registrationRequest.getUsername())).thenReturn(false);
            when(passwordEncoder.encode(registrationRequest.getPassword())).thenReturn("hashedPassword");
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            assertDoesNotThrow(() -> userService.registerUser(registrationRequest));
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("Payment Service Tests")
    class PaymentServiceTests {
        @Mock
        private PaymentRepository paymentRepository;
        @Mock
        private StripeService stripeService;
        @InjectMocks
        private PaymentService paymentService;

        private PaymentRequest paymentRequest;
        private Payment testPayment;

        @BeforeEach
        void setUp() {
            paymentRequest = PaymentRequest.builder()
                    .userId(UUID.randomUUID())
                    .amount(new BigDecimal("99.99"))
                    .currency("USD")
                    .paymentMethodId("pm_1234567890")
                    .description("Monthly subscription")
                    .build();

            testPayment = Payment.builder()
                    .id(UUID.randomUUID())
                    .userId(paymentRequest.getUserId())
                    .amount(paymentRequest.getAmount())
                    .currency(paymentRequest.getCurrency())
                    .status(PaymentStatus.PENDING)
                    .stripePaymentId("pi_1234567890")
                    .createdAt(LocalDateTime.now())
                    .build();
        }

        @Test
        @DisplayName("Should process payment successfully when valid request provided")
        void testProcessPayment_whenValidRequest_expectsSuccess() {
            String stripePaymentId = "pi_1234567890";
            when(stripeService.createPaymentIntent(paymentRequest)).thenReturn(stripePaymentId);
            when(stripeService.confirmPayment(stripePaymentId, paymentRequest.getPaymentMethodId())).thenReturn(true);
            when(paymentRepository.save(any(Payment.class))).thenReturn(testPayment);

            PaymentResponse response = paymentService.processPayment(paymentRequest);

            assertNotNull(response);
            assertEquals(paymentRequest.getAmount(), response.getAmount());
            assertEquals(PaymentStatus.COMPLETED, response.getStatus());
            verify(paymentRepository, times(1)).save(any(Payment.class));
        }

        @Test
        @DisplayName("Should throw exception when payment amount is zero")
        void testProcessPayment_whenAmountZero_expectsInvalidPaymentAmountException() {
            paymentRequest.setAmount(BigDecimal.ZERO);

            assertThrows(InvalidPaymentAmountException.class, () -> {
                paymentService.processPayment(paymentRequest);
            });
            verify(stripeService, never()).createPaymentIntent(any());
        }

        @Test
        @DisplayName("Should throw exception when payment amount is negative")
        void testProcessPayment_whenAmountNegative_expectsInvalidPaymentAmountException() {
            paymentRequest.setAmount(new BigDecimal("-50.00"));

            assertThrows(InvalidPaymentAmountException.class, () -> {
                paymentService.processPayment(paymentRequest);
            });
        }

        @Test
        @DisplayName("Should handle Stripe payment failure gracefully")
        void testProcessPayment_whenStripeFailure_expectsPaymentProcessingException() {
            when(stripeService.createPaymentIntent(paymentRequest)).thenThrow(new RuntimeException("Stripe API error"));

            assertThrows(PaymentProcessingException.class, () -> {
                paymentService.processPayment(paymentRequest);
            });
            verify(paymentRepository, never()).save(any(Payment.class));
        }

        @Test
        @DisplayName("Should retrieve payment by ID")
        void testGetPayment_whenPaymentExists_expectsPaymentResponse() {
            UUID paymentId = testPayment.getId();
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(testPayment));

            PaymentResponse response = paymentService.getPayment(paymentId);

            assertNotNull(response);
            assertEquals(testPayment.getAmount(), response.getAmount());
        }

        @Test
        @DisplayName("Should process refund successfully")
        void testRefundPayment_whenValidRequest_expectsSuccess() {
            UUID paymentId = testPayment.getId();
            testPayment.setStatus(PaymentStatus.COMPLETED);
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(testPayment));
            when(stripeService.refundPayment(testPayment.getStripePaymentId())).thenReturn(true);
            when(paymentRepository.save(any(Payment.class))).thenReturn(testPayment);

            PaymentResponse response = paymentService.refundPayment(paymentId);

            assertNotNull(response);
            assertEquals(PaymentStatus.REFUNDED, response.getStatus());
            verify(stripeService, times(1)).refundPayment(testPayment.getStripePaymentId());
        }

        @ParameterizedTest
        @ValueSource(strings = {"USD", "EUR", "GBP", "JPY"})
        @DisplayName("Should support multiple currencies")
        void testProcessPayment_withMultipleCurrencies_expectsSuccess(String currency) {
            paymentRequest.setCurrency(currency);
            when(stripeService.createPaymentIntent(paymentRequest)).thenReturn("pi_1234567890");
            when(stripeService.confirmPayment("pi_1234567890", paymentRequest.getPaymentMethodId())).thenReturn(true);
            when(paymentRepository.save(any(Payment.class))).thenReturn(testPayment);

            assertDoesNotThrow(() -> paymentService.processPayment(paymentRequest));
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("Subscription Service Tests")
    class SubscriptionServiceTests {
        @Mock
        private SubscriptionRepository subscriptionRepository;
        @Mock
        private PaymentServiceClient paymentServiceClient;
        @Mock
        private NotificationServiceClient notificationServiceClient;
        @InjectMocks
        private SubscriptionService subscriptionService;

        private SubscriptionRequest subscriptionRequest;
        private Subscription testSubscription;

        @BeforeEach
        void setUp() {
            subscriptionRequest = SubscriptionRequest.builder()
                    .userId(UUID.randomUUID())
                    .plan(SubscriptionPlan.PREMIUM)
                    .paymentMethodId("pm_1234567890")
                    .autoRenew(true)
                    .build();

            testSubscription = Subscription.builder()
                    .id(UUID.randomUUID())
                    .userId(subscriptionRequest.getUserId())
                    .plan(subscriptionRequest.getPlan())
                    .status(SubscriptionStatus.ACTIVE)
                    .startDate(LocalDateTime.now())
                    .endDate(LocalDateTime.now().plusMonths(1))
                    .autoRenew(true)
                    .build();
        }

        @Test
        @DisplayName("Should create subscription successfully when valid request provided")
        void testCreateSubscription_whenValidRequest_expectsSuccess() {
            when(subscriptionRepository.findActiveSubscriptionByUserId(subscriptionRequest.getUserId()))
                    .thenReturn(Optional.empty());
            when(paymentServiceClient.processSubscriptionPayment(any())).thenReturn(true);
            when(subscriptionRepository.save(any(Subscription.class))).thenReturn(testSubscription);
            doNothing().when(notificationServiceClient).sendSubscriptionConfirmation(any());

            SubscriptionResponse response = subscriptionService.createSubscription(subscriptionRequest);

            assertNotNull(response);
            assertEquals(SubscriptionStatus.ACTIVE, response.getStatus());
            verify(subscriptionRepository, times(1)).save(any(Subscription.class));
        }

        @Test
        @DisplayName("Should throw exception when user already has active subscription")
        void testCreateSubscription_whenActiveSubscriptionExists_expectsException() {
            when(subscriptionRepository.findActiveSubscriptionByUserId(subscriptionRequest.getUserId()))
                    .thenReturn(Optional.of(testSubscription));

            assertThrows(SubscriptionException.class, () -> {
                subscriptionService.createSubscription(subscriptionRequest);
            });
            verify(paymentServiceClient, never()).processSubscriptionPayment(any());
        }

        @Test
        @DisplayName("Should cancel subscription successfully")
        void testCancelSubscription_whenSubscriptionExists_expectsSuccess() {
            UUID subscriptionId = testSubscription.getId();
            when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(testSubscription));
            when(subscriptionRepository.save(any(Subscription.class))).thenReturn(testSubscription);
            doNothing().when(notificationServiceClient).sendCancellationNotification(any());

            SubscriptionResponse response = subscriptionService.cancelSubscription(subscriptionId);

            assertNotNull(response);
            assertEquals(SubscriptionStatus.CANCELLED, response.getStatus());
            verify(subscriptionRepository, times(1)).save(any(Subscription.class));
        }

        @Test
        @DisplayName("Should renew subscription successfully")
        void testRenewSubscription_whenSubscriptionExpiring_expectsSuccess() {
            UUID subscriptionId = testSubscription.getId();
            testSubscription.setEndDate(LocalDateTime.now().plusDays(1));
            testSubscription.setAutoRenew(true);
            when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(testSubscription));
            when(paymentServiceClient.processSubscriptionPayment(any())).thenReturn(true);
            when(subscriptionRepository.save(any(Subscription.class))).thenReturn(testSubscription);
            doNothing().when(notificationServiceClient).sendRenewalConfirmation(any());

            SubscriptionResponse response = subscriptionService.renewSubscription(subscriptionId);

            assertNotNull(response);
            assertEquals(SubscriptionStatus.ACTIVE, response.getStatus());
            verify(paymentServiceClient, times(1)).processSubscriptionPayment(any());
        }

        @ParameterizedTest
        @EnumSource(SubscriptionPlan.class)
        @DisplayName("Should support all subscription plans")
        void testCreateSubscription_withAllPlans_expectsSuccess(SubscriptionPlan plan) {
            subscriptionRequest.setPlan(plan);
            when(subscriptionRepository.findActiveSubscriptionByUserId(subscriptionRequest.getUserId()))
                    .thenReturn(Optional.empty());
            when(paymentServiceClient.processSubscriptionPayment(any())).thenReturn(true);
            when(subscriptionRepository.save(any(Subscription.class))).thenReturn(testSubscription);
            doNothing().when(notificationServiceClient).sendSubscriptionConfirmation(any());

            assertDoesNotThrow(() -> subscriptionService.createSubscription(subscriptionRequest));
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("Media Service Tests")
    class MediaServiceTests {
        @Mock
        private VideoRepository videoRepository;
        @Mock
        private MinIOService minIOService;
        @Mock
        private RedisService redisService;
        @Mock
        private KafkaProducerService kafkaProducerService;
        @Mock
        private MultipartFile mockFile;
        @InjectMocks
        private MediaService mediaService;

        private VideoUploadRequest uploadRequest;
        private Video testVideo;

        @BeforeEach
        void setUp() {
            uploadRequest = VideoUploadRequest.builder()
                    .userId(UUID.randomUUID())
                    .title("Test Video")
                    .description("A test video for unit testing")
                    .contentType("video/mp4")
                    .build();

            testVideo = Video.builder()
                    .id(UUID.randomUUID())
                    .userId(uploadRequest.getUserId())
                    .title(uploadRequest.getTitle())
                    .description(uploadRequest.getDescription())
                    .status(VideoStatus.UPLOADED)
                    .fileSize(1024000L)
                    .duration(3600)
                    .createdAt(LocalDateTime.now())
                    .build();
        }

        @Test
        @DisplayName("Should initiate video upload successfully")
        void testInitiateUpload_whenValidRequest_expectsSuccess() {
            when(videoRepository.save(any(Video.class))).thenReturn(testVideo);
            when(redisService.setUploadProgress(testVideo.getId(), 0)).thenReturn(true);

            VideoResponse response = mediaService.initiateUpload(uploadRequest);

            assertNotNull(response);
            assertEquals(uploadRequest.getTitle(), response.getTitle());
            assertEquals(VideoStatus.UPLOADING, response.getStatus());
            verify(videoRepository, times(1)).save(any(Video.class));
        }

        @Test
        @DisplayName("Should throw exception when title is empty")
        void testInitiateUpload_whenTitleEmpty_expectsException() {
            uploadRequest.setTitle("");

            assertThrows(IllegalArgumentException.class, () -> {
                mediaService.initiateUpload(uploadRequest);
            });
        }

        @Test
        @DisplayName("Should upload video chunk successfully")
        void testUploadChunk_whenValidChunk_expectsSuccess() throws IOException {
            UUID videoId = testVideo.getId();
            int chunkNumber = 1;
            int totalChunks = 10;
            byte[] chunkData = "test chunk data".getBytes();
            
            when(videoRepository.findById(videoId)).thenReturn(Optional.of(testVideo));
            when(mockFile.getBytes()).thenReturn(chunkData);
            when(minIOService.uploadChunk(videoId, chunkNumber, chunkData)).thenReturn(true);
            when(redisService.updateUploadProgress(videoId, chunkNumber, totalChunks)).thenReturn(10);

            UploadProgressResponse response = mediaService.uploadChunk(videoId, chunkNumber, totalChunks, mockFile);

            assertNotNull(response);
            assertEquals(10, response.getProgressPercentage());
            verify(minIOService, times(1)).uploadChunk(videoId, chunkNumber, chunkData);
        }

        @Test
        @DisplayName("Should complete video upload and trigger processing")
        void testCompleteUpload_whenAllChunksUploaded_expectsSuccess() {
            UUID videoId = testVideo.getId();
            when(videoRepository.findById(videoId)).thenReturn(Optional.of(testVideo));
            when(minIOService.mergeChunks(videoId, 10)).thenReturn(true);
            when(videoRepository.save(any(Video.class))).thenReturn(testVideo);
            doNothing().when(kafkaProducerService).publishVideoProcessingEvent(videoId);
            doNothing().when(redisService).deleteUploadProgress(videoId);

            VideoResponse response = mediaService.completeUpload(videoId, 10);

            assertNotNull(response);
            assertEquals(VideoStatus.PROCESSING, response.getStatus());
            verify(kafkaProducerService, times(1)).publishVideoProcessingEvent(videoId);
        }

        @Test
        @DisplayName("Should retrieve video metadata")
        void testGetVideo_whenVideoExists_expectsVideoResponse() {
            UUID videoId = testVideo.getId();
            when(videoRepository.findById(videoId)).thenReturn(Optional.of(testVideo));

            VideoResponse response = mediaService.getVideo(videoId);

            assertNotNull(response);
            assertEquals(testVideo.getTitle(), response.getTitle());
        }

        @Test
        @DisplayName("Should stream video successfully")
        void testStreamVideo_whenVideoReady_expectsSuccess() {
            UUID videoId = testVideo.getId();
            testVideo.setStatus(VideoStatus.READY);
            when(videoRepository.findById(videoId)).thenReturn(Optional.of(testVideo));
            when(minIOService.getVideoStream(videoId)).thenReturn(new byte[]{1, 2, 3, 4, 5});

            byte[] stream = mediaService.streamVideo(videoId);

            assertNotNull(stream);
            assertTrue(stream.length > 0);
            verify(minIOService, times(1)).getVideoStream(videoId);
        }

        @ParameterizedTest
        @ValueSource(strings = {"video/mp4", "video/webm", "video/quicktime"})
        @DisplayName("Should support multiple video formats")
        void testInitiateUpload_withMultipleFormats_expectsSuccess(String contentType) {
            uploadRequest.setContentType(contentType);
            when(videoRepository.save(any(Video.class))).thenReturn(testVideo);
            when(redisService.setUploadProgress(testVideo.getId(), 0)).thenReturn(true);

            assertDoesNotThrow(() -> mediaService.initiateUpload(uploadRequest));
        }
    }
}
