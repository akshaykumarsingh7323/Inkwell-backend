package com.inkwell.payment.service.impl;

import com.inkwell.payment.client.PostClient;
import com.inkwell.payment.client.SubscriptionClient;
import com.inkwell.payment.dto.PaymentOrderRequest;
import com.inkwell.payment.dto.PaymentResponse;
import com.inkwell.payment.dto.PaymentVerifyRequest;
import com.inkwell.payment.entity.AuthorEarning;
import com.inkwell.payment.entity.Payment;
import com.inkwell.payment.repository.AuthorEarningRepository;
import com.inkwell.payment.repository.PaymentRepository;
import com.razorpay.Order;
import com.razorpay.OrderClient;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @org.mockito.Mock
    private PaymentRepository paymentRepository;

    @org.mockito.Mock
    private AuthorEarningRepository authorEarningRepository;

    @org.mockito.Mock
    private PostClient postClient;

    @org.mockito.Mock
    private RabbitTemplate rabbitTemplate;

    @org.mockito.Mock
    private RazorpayClient razorpayClient;

    @org.mockito.Mock
    private SubscriptionClient subscriptionClient;

    @org.mockito.Mock
    private CacheManager cacheManager;

    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl();
        ReflectionTestUtils.setField(paymentService, "paymentRepository", paymentRepository);
        ReflectionTestUtils.setField(paymentService, "authorEarningRepository", authorEarningRepository);
        ReflectionTestUtils.setField(paymentService, "postClient", postClient);
        ReflectionTestUtils.setField(paymentService, "rabbitTemplate", rabbitTemplate);
        ReflectionTestUtils.setField(paymentService, "razorpayClient", razorpayClient);
        ReflectionTestUtils.setField(paymentService, "subscriptionClient", subscriptionClient);
        ReflectionTestUtils.setField(paymentService, "cacheManager", cacheManager);
        ReflectionTestUtils.setField(paymentService, "razorpaySecret", "secret");
        ReflectionTestUtils.setField(paymentService, "commissionRate", 0.2d);
    }

    @Test
    void createOrder_ShouldReturnOrderIdAndSaveAuthorId() throws Exception {
        PaymentOrderRequest request = new PaymentOrderRequest();
        request.setAmount(100.0);
        request.setUserId("user1");
        request.setPostId("1");

        OrderClient orderClient = mock(OrderClient.class);
        ReflectionTestUtils.setField(razorpayClient, "orders", orderClient);
        Order order = mock(Order.class);
        when(order.get("id")).thenReturn("order_123");
        when(orderClient.create(any(JSONObject.class))).thenReturn(order);

        PostClient.PostSummary post = new PostClient.PostSummary();
        post.setAuthorId(99L);
        when(postClient.getPostById(1L)).thenReturn(post);

        String orderId = paymentService.createOrder(request);

        assertEquals("order_123", orderId);
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void createOrder_WhenPostLookupFails_ShouldStillSave() throws Exception {
        PaymentOrderRequest request = new PaymentOrderRequest();
        request.setAmount(100.0);
        request.setUserId("user1");
        request.setPostId("1");

        OrderClient orderClient = mock(OrderClient.class);
        ReflectionTestUtils.setField(razorpayClient, "orders", orderClient);
        Order order = mock(Order.class);
        when(order.get("id")).thenReturn("order_123");
        when(orderClient.create(any(JSONObject.class))).thenReturn(order);
        when(postClient.getPostById(1L)).thenThrow(new RuntimeException("down"));

        assertEquals("order_123", paymentService.createOrder(request));
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void createOrder_ForSubscription_ShouldNotLookupPost() throws Exception {
        PaymentOrderRequest request = new PaymentOrderRequest();
        request.setAmount(100.0);
        request.setUserId("user1");
        request.setPostId("SUBSCRIPTION");
        request.setPaymentType("SUBSCRIPTION");

        OrderClient orderClient = mock(OrderClient.class);
        ReflectionTestUtils.setField(razorpayClient, "orders", orderClient);
        Order order = mock(Order.class);
        when(order.get("id")).thenReturn("order_123");
        when(orderClient.create(any(JSONObject.class))).thenReturn(order);

        paymentService.createOrder(request);

        verify(postClient, never()).getPostById(any());
    }

    @Test
    void createOrder_WithNullPostIdAndType_ShouldDefaultToSubscriptionAndSinglePost() throws Exception {
        PaymentOrderRequest request = new PaymentOrderRequest();
        request.setAmount(100.0);
        request.setUserId("user1");
        request.setPostId(null);
        request.setPaymentType(null);

        OrderClient orderClient = mock(OrderClient.class);
        ReflectionTestUtils.setField(razorpayClient, "orders", orderClient);
        Order order = mock(Order.class);
        when(order.get("id")).thenReturn("order_123");
        when(orderClient.create(any(JSONObject.class))).thenReturn(order);

        paymentService.createOrder(request);

        verify(paymentRepository).save(org.mockito.ArgumentMatchers.argThat(p -> 
            p.getPostId().equals("SUBSCRIPTION") && p.getPaymentType() == Payment.PaymentType.SINGLE_POST
        ));
    }

    @Test
    void verifyPayment_WhenValid_ShouldUpdateCacheEarningsAndPublishEvent() throws Exception {
        Payment payment = Payment.builder()
                .paymentId("pay")
                .userId("user1")
                .postId("1")
                .orderId("order_123")
                .amount(100.0)
                .paymentType(Payment.PaymentType.SINGLE_POST)
                .authorId("99")
                .status(Payment.PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        when(paymentRepository.findByOrderId("order_123")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(authorEarningRepository.findById("99")).thenReturn(Optional.of(
                AuthorEarning.builder().authorId("99").totalEarnings(BigDecimal.TEN).build()
        ));
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("purchase_status")).thenReturn(cache);

        try (MockedStatic<Utils> utilsMock = Mockito.mockStatic(Utils.class)) {
            utilsMock.when(() -> Utils.verifyPaymentSignature(any(JSONObject.class), eq("secret"))).thenReturn(true);

            PaymentResponse response = paymentService.verifyPayment(new PaymentVerifyRequest("order_123", "payment_1", "sig"));

            assertEquals("SUCCESS", response.getStatus());
            verify(cache).evict("user1:1");
            verify(authorEarningRepository).save(any(AuthorEarning.class));
            verify(rabbitTemplate).convertAndSend(
                    eq(com.inkwell.payment.config.RabbitMQConfig.EXCHANGE),
                    eq(com.inkwell.payment.config.RabbitMQConfig.SUCCESS_ROUTING_KEY),
                    org.mockito.ArgumentMatchers.<Object>any()
            );
        }
    }

    @Test
    void verifyPayment_WhenValidAndSubscription_ShouldCreateSubscription() throws Exception {
        Payment payment = Payment.builder()
                .paymentId("pay")
                .userId("user1")
                .postId("SUBSCRIPTION")
                .orderId("order_123")
                .amount(100.0)
                .paymentType(Payment.PaymentType.SUBSCRIPTION)
                .status(Payment.PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        when(paymentRepository.findByOrderId("order_123")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<Utils> utilsMock = Mockito.mockStatic(Utils.class)) {
            utilsMock.when(() -> Utils.verifyPaymentSignature(any(JSONObject.class), eq("secret"))).thenReturn(true);

            paymentService.verifyPayment(new PaymentVerifyRequest("order_123", "payment_1", "sig"));

            verify(subscriptionClient).createSubscription("user1", "MONTHLY");
        }
    }

    @Test
    void verifyPayment_WhenAuthorIdMissing_ShouldFetchPostForEarnings() throws Exception {
        Payment payment = Payment.builder()
                .paymentId("pay")
                .userId("user1")
                .postId("1")
                .orderId("order_123")
                .amount(100.0)
                .paymentType(Payment.PaymentType.SINGLE_POST)
                .authorId(null)
                .status(Payment.PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        when(paymentRepository.findByOrderId("order_123")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(authorEarningRepository.findById("99")).thenReturn(Optional.empty());
        PostClient.PostSummary post = new PostClient.PostSummary();
        post.setAuthorId(99L);
        when(postClient.getPostById(1L)).thenReturn(post);
        when(cacheManager.getCache("purchase_status")).thenReturn(null);

        try (MockedStatic<Utils> utilsMock = Mockito.mockStatic(Utils.class)) {
            utilsMock.when(() -> Utils.verifyPaymentSignature(any(JSONObject.class), eq("secret"))).thenReturn(true);

            paymentService.verifyPayment(new PaymentVerifyRequest("order_123", "payment_1", "sig"));

            verify(authorEarningRepository).save(any(AuthorEarning.class));
        }
    }

    @Test
    void verifyPayment_WhenAuthorLookupFails_ShouldSwallowAndComplete() throws Exception {
        Payment payment = Payment.builder()
                .paymentId("pay")
                .userId("user1")
                .postId("1")
                .orderId("order_123")
                .amount(100.0)
                .paymentType(Payment.PaymentType.SINGLE_POST)
                .authorId(null)
                .status(Payment.PaymentStatus.PENDING)
                .build();
        when(paymentRepository.findByOrderId("order_123")).thenReturn(Optional.of(payment));
        when(postClient.getPostById(1L)).thenThrow(new RuntimeException("client down"));

        try (MockedStatic<Utils> utilsMock = Mockito.mockStatic(Utils.class)) {
            utilsMock.when(() -> Utils.verifyPaymentSignature(any(JSONObject.class), eq("secret"))).thenReturn(true);
            paymentService.verifyPayment(new PaymentVerifyRequest("order_123", "payment_1", "sig"));
            // No exception should propagate
        }
    }



    @Test
    void verifyPayment_WhenInvalid_ShouldMarkFailed() throws Exception {
        Payment payment = Payment.builder()
                .paymentId("pay")
                .userId("user1")
                .postId("1")
                .orderId("order_123")
                .amount(100.0)
                .status(Payment.PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        when(paymentRepository.findByOrderId("order_123")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<Utils> utilsMock = Mockito.mockStatic(Utils.class)) {
            utilsMock.when(() -> Utils.verifyPaymentSignature(any(JSONObject.class), eq("secret"))).thenReturn(false);

            PaymentResponse response = paymentService.verifyPayment(new PaymentVerifyRequest("order_123", "payment_1", "sig"));

            assertEquals("FAILED", response.getStatus());
            verify(rabbitTemplate, never()).convertAndSend(
                    eq(com.inkwell.payment.config.RabbitMQConfig.EXCHANGE),
                    eq(com.inkwell.payment.config.RabbitMQConfig.SUCCESS_ROUTING_KEY),
                    org.mockito.ArgumentMatchers.<Object>any()
            );
        }
    }

    @Test
    void hasAccess_ShouldCheckSubscriptionFirst() {
        when(subscriptionClient.hasActiveSubscription("user1")).thenReturn(true);
        assertTrue(paymentService.hasAccess("user1", "post1"));
    }

    @Test
    void hasAccess_ShouldCheckPostPurchaseIfNoSubscription() {
        when(subscriptionClient.hasActiveSubscription("user1")).thenReturn(false);
        when(paymentRepository.findByUserIdAndPostIdAndStatus("user1", "post1", Payment.PaymentStatus.SUCCESS))
                .thenReturn(Optional.of(new Payment()));
        assertTrue(paymentService.hasAccess("user1", "post1"));
    }

    @Test
    void hasAccess_WhenSubscriptionCheckFails_ShouldFallbackToPurchaseCheck() {
        when(subscriptionClient.hasActiveSubscription("user1")).thenThrow(new RuntimeException("down"));
        when(paymentRepository.findByUserIdAndPostIdAndStatus("user1", "post1", Payment.PaymentStatus.SUCCESS))
                .thenReturn(Optional.empty());
        assertFalse(paymentService.hasAccess("user1", "post1"));
    }

    @Test
    void getUserPayments_ShouldMapRepositoryResults() {
        when(paymentRepository.findByUserId("user1")).thenReturn(List.of(
                Payment.builder().paymentId("pay").userId("user1").postId("1").amount(100.0).status(Payment.PaymentStatus.SUCCESS).createdAt(LocalDateTime.now()).build()
        ));
        assertEquals(1, paymentService.getUserPayments("user1").size());
    }

    @Test
    void verifyPayment_WhenSubscriptionCreationFails_ShouldSwallowException() throws Exception {
        Payment payment = Payment.builder()
                .paymentId("pay")
                .userId("user1")
                .postId("SUBSCRIPTION")
                .orderId("order_123")
                .amount(100.0)
                .paymentType(Payment.PaymentType.SUBSCRIPTION)
                .status(Payment.PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        when(paymentRepository.findByOrderId("order_123")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.doThrow(new RuntimeException("down")).when(subscriptionClient).createSubscription("user1", "MONTHLY");

        try (MockedStatic<Utils> utilsMock = Mockito.mockStatic(Utils.class)) {
            utilsMock.when(() -> Utils.verifyPaymentSignature(any(JSONObject.class), eq("secret"))).thenReturn(true);
            assertDoesNotThrow(() -> paymentService.verifyPayment(new PaymentVerifyRequest("order_123", "payment_1", "sig")));
        }
    }

    @Test
    void createOrder_WithPostIdSubscription_ShouldNotLookupPost() throws Exception {
        PaymentOrderRequest request = new PaymentOrderRequest();
        request.setAmount(100.0);
        request.setUserId("user1");
        request.setPostId("SUBSCRIPTION");

        OrderClient orderClient = mock(OrderClient.class);
        ReflectionTestUtils.setField(razorpayClient, "orders", orderClient);
        Order order = mock(Order.class);
        when(order.get("id")).thenReturn("order_123");
        when(orderClient.create(any(JSONObject.class))).thenReturn(order);

        paymentService.createOrder(request);
        verify(postClient, never()).getPostById(anyLong());
    }

    @Test
    void processSuccessPayment_WithPostIdSubscription_ShouldSkipAuthorLookup() throws Exception {
        Payment payment = Payment.builder()
                .paymentId("pay")
                .userId("user1")
                .postId("SUBSCRIPTION")
                .orderId("order_123")
                .amount(100.0)
                .paymentType(Payment.PaymentType.SINGLE_POST)
                .authorId(null)
                .status(Payment.PaymentStatus.PENDING)
                .build();
        when(paymentRepository.findByOrderId("order_123")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<Utils> utilsMock = Mockito.mockStatic(Utils.class)) {
            utilsMock.when(() -> Utils.verifyPaymentSignature(any(JSONObject.class), eq("secret"))).thenReturn(true);
            paymentService.verifyPayment(new PaymentVerifyRequest("order_123", "payment_1", "sig"));
            verify(postClient, never()).getPostById(anyLong());
        }
    }

    @Test
    void verifyPayment_WhenPaymentNotFound_ShouldThrowException() {
        when(paymentRepository.findByOrderId("none")).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> paymentService.verifyPayment(new PaymentVerifyRequest("none", "pay", "sig")));
    }

    @Test
    void processSuccessPayment_WhenPostNotFound_ShouldNotUpdateAuthorId() {
        Payment payment = Payment.builder()
                .paymentId("pay")
                .userId("user1")
                .postId("1")
                .amount(100.0)
                .paymentType(Payment.PaymentType.SINGLE_POST)
                .authorId(null)
                .build();
        
        when(postClient.getPostById(1L)).thenReturn(null);
        
        ReflectionTestUtils.invokeMethod(paymentService, "processSuccessPayment", payment);
        verify(authorEarningRepository, never()).save(any());
    }

    @Test
    void processSuccessPayment_WhenSubscriptionClientFails_ShouldLogAndContinue() {
        Payment payment = Payment.builder()
                .paymentId("pay")
                .userId("user1")
                .postId("SUBSCRIPTION")
                .amount(100.0)
                .paymentType(Payment.PaymentType.SUBSCRIPTION)
                .build();
        
        when(subscriptionClient.createSubscription(anyString(), anyString())).thenThrow(new RuntimeException("API Down"));
        
        ReflectionTestUtils.invokeMethod(paymentService, "processSuccessPayment", payment);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), anyMap());
    }

    @Test
    void processSuccessPayment_WhenAuthorEarningFails_ShouldLogAndContinue() {
        Payment payment = Payment.builder()
                .paymentId("pay")
                .userId("user1")
                .postId("1")
                .authorId("99")
                .amount(100.0)
                .paymentType(Payment.PaymentType.SINGLE_POST)
                .build();
        
        when(authorEarningRepository.findById("99")).thenThrow(new RuntimeException("DB Down"));
        
        ReflectionTestUtils.invokeMethod(paymentService, "processSuccessPayment", payment);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), anyMap());
    }
}
