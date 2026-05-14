package com.inkwell.newsletter.repository;

import com.inkwell.newsletter.entity.Subscriber;
import com.inkwell.newsletter.entity.SubscriberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriberRepository extends JpaRepository<Subscriber, Long>, JpaSpecificationExecutor<Subscriber> {
    Optional<Subscriber> findByEmailAndFollowedAuthorId(String email, Long followedAuthorId);
    Optional<Subscriber> findByEmail(String email);
    Optional<Subscriber> findByUserId(Long userId);
    List<Subscriber> findByStatus(SubscriberStatus status);
    List<Subscriber> findByFollowedAuthorIdAndStatus(Long followedAuthorId, SubscriberStatus status);
    Optional<Subscriber> findByToken(String token);
    boolean existsByEmailAndFollowedAuthorId(String email, Long followedAuthorId);
    boolean existsByEmail(String email);
    long countByFollowedAuthorIdAndStatus(Long followedAuthorId, SubscriberStatus status);
    long countByFollowedAuthorId(Long followedAuthorId);
    long countByStatus(SubscriberStatus status);
}
