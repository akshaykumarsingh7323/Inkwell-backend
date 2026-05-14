package com.inkwell.payment.service.impl;

import com.inkwell.payment.dto.PaymentOrderRequest;
import com.inkwell.payment.dto.PaymentResponse;
import com.inkwell.payment.dto.PaymentVerifyRequest;
import com.inkwell.payment.entity.Payment;
import com.inkwell.payment.repository.PaymentRepository;
import com.inkwell.payment.repository.AuthorEarningRepository;
import com.inkwell.payment.client.PostClient;
import com.inkwell.payment.client.SubscriptionClient;
import com.inkwell.payment.config.RabbitMQConfig;
import com.inkwell.payment.entity.AuthorEarning;
import com.inkwell.payment.service.PaymentService;
import com.inkwell.payment.exception.SubscriptionException;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PaymentServiceImpl implements PaymentService {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private AuthorEarningRepository authorEarningRepository;

    @Autowired
    private PostClient postClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RazorpayClient razorpayClient;

    @Autowired
    private SubscriptionClient subscriptionClient;

    @Autowired
    private org.springframework.cache.CacheManager cacheManager;

    @Value("${razorpay.key-secret}")
    private String razorpaySecret;

    @Value("${platform.commission-rate:0.2}")
    private double commissionRate;

    @Override
    public String createOrder(PaymentOrderRequest request) throws Exception {
        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", (int)(request.getAmount() * 100)); // amount in paise
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", "txn_" + UUID.randomUUID().toString().substring(0, 8));

        String orderId;
        if ("your_key_secret".equals(razorpaySecret)) {
            orderId = "order_mock_" + UUID.randomUUID().toString().substring(0, 8);
        } else {
            Order order = razorpayClient.orders.create(orderRequest);
            orderId = order.get("id");
        }

        String authorId = null;
        if (request.getPostId() != null && !request.getPostId().equals("SUBSCRIPTION")) {
            try {
                PostClient.PostSummary post = postClient.getPostById(Long.parseLong(request.getPostId()));
                if (post != null) {
                    authorId = String.valueOf(post.getAuthorId());
                }
            } catch (Exception e) {
                System.err.println("Warning: Could not fetch authorId for postId " + request.getPostId());
            }
        }

        Payment payment = Payment.builder()
                .paymentId(UUID.randomUUID().toString())
                .userId(request.getUserId())
                .postId(request.getPostId() != null ? request.getPostId() : "SUBSCRIPTION")
                .amount(request.getAmount())
                .totalAmount(request.getAmount())
                .authorId(authorId)
                .orderId(orderId)
                .paymentType(request.getPaymentType() != null ? 
                             Payment.PaymentType.valueOf(request.getPaymentType()) : 
                             Payment.PaymentType.SINGLE_POST)
                .status(Payment.PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        paymentRepository.save(payment);
        return orderId;
    }

    @Override
    public PaymentResponse verifyPayment(PaymentVerifyRequest request) throws Exception {
        JSONObject options = new JSONObject();
        options.put("razorpay_order_id", request.getOrderId());
        options.put("razorpay_payment_id", request.getPaymentId());
        options.put("razorpay_signature", request.getSignature());

        boolean isValid;
        if (request.getOrderId() != null && request.getOrderId().startsWith("order_mock_")) {
            isValid = true;
        } else {
            isValid = Utils.verifyPaymentSignature(options, razorpaySecret);
        }

        Payment payment = paymentRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found: " + request.getOrderId()));

        if (isValid) {
            payment.setStatus(Payment.PaymentStatus.SUCCESS);
            
            // Save FIRST to ensure DB is updated before eviction/events
            paymentRepository.save(payment);
            
            // Now evict cache - any fresh check will now find the SUCCESS status in DB
            if (payment.getPostId() != null) {
                String cacheKey = payment.getUserId() + ":" + payment.getPostId();
                org.springframework.cache.Cache cache = cacheManager.getCache("purchase_status");
                if (cache != null) {
                    cache.evict(cacheKey);
                }
            }
            
            processSuccessPayment(payment);
        } else {
            payment.setStatus(Payment.PaymentStatus.FAILED);
            paymentRepository.save(payment);
        }

        return mapToResponse(payment);
    }

    private void processSuccessPayment(Payment payment) {
        // 1. Calculate and Update Split
        double amount = payment.getAmount();
        double adminCut = amount * commissionRate;
        double authorShare = amount - adminCut;

        payment.setAdminCommission(adminCut);
        payment.setAuthorEarning(authorShare);
        payment.setTotalAmount(amount);
        paymentRepository.save(payment);

        // 2. Update Author Earnings
        try {
            String authorId = payment.getAuthorId();
            if (authorId == null && payment.getPostId() != null && !payment.getPostId().equals("SUBSCRIPTION")) {
                PostClient.PostSummary post = postClient.getPostById(Long.parseLong(payment.getPostId()));
                if (post != null) authorId = String.valueOf(post.getAuthorId());
            }

            if (authorId != null) {
                AuthorEarning earning = authorEarningRepository.findById(authorId)
                        .orElse(AuthorEarning.builder().authorId(authorId).totalEarnings(BigDecimal.ZERO).build());
                
                earning.setTotalEarnings(earning.getTotalEarnings().add(BigDecimal.valueOf(authorShare)));
                authorEarningRepository.save(earning);
            }
        } catch (Exception e) {
            System.err.println("Failed to update author earnings: " + e.getMessage());
        }

        // 2. Publish Success Event to RabbitMQ
        Map<String, Object> event = new HashMap<>();
        event.put("userId", payment.getUserId());
        event.put("postId", payment.getPostId());
        event.put("amount", payment.getAmount());
        event.put("timestamp", LocalDateTime.now().toString());

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.SUCCESS_ROUTING_KEY, event);

        // 3. Handle Subscription creation if type is SUBSCRIPTION via Feign Client
        if (payment.getPaymentType() == Payment.PaymentType.SUBSCRIPTION) {
            try {
                subscriptionClient.createSubscription(payment.getUserId(), "MONTHLY");
            } catch (Exception e) {
                System.err.println("Failed to create subscription via client: " + e.getMessage());
            }
        }
    }

    @Override
    @org.springframework.cache.annotation.Cacheable(value = "purchase_status", key = "#userId + ':' + #postId")
    public boolean hasAccess(String userId, String postId) {
        // 1. Check if user has an active subscription via Feign Client
        try {
            if (subscriptionClient.hasActiveSubscription(userId)) {
                return true;
            }
        } catch (Exception e) {
            System.err.println("Failed to check subscription status: " + e.getMessage());
        }

        // 2. Check for specific post purchase
        return paymentRepository.findByUserIdAndPostIdAndStatus(userId, postId, Payment.PaymentStatus.SUCCESS).isPresent();
    }

    @Override
    public List<PaymentResponse> getUserPayments(String userId) {
        return paymentRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private PaymentResponse mapToResponse(Payment payment) {
        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .userId(payment.getUserId())
                .postId(payment.getPostId())
                .amount(payment.getAmount())
                .totalAmount(payment.getTotalAmount())
                .adminCommission(payment.getAdminCommission())
                .authorEarning(payment.getAuthorEarning())
                .authorId(payment.getAuthorId())
                .status(payment.getStatus().name())
                .orderId(payment.getOrderId())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
