package com.inkwell.payment.repository;

import com.inkwell.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, String> {
    Optional<Payment> findByOrderId(String orderId);
    List<Payment> findByUserId(String userId);
    List<Payment> findByAuthorId(String authorId);
    Optional<Payment> findByUserIdAndPostIdAndStatus(String userId, String postId, Payment.PaymentStatus status);
}
