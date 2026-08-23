package com.dvicheck.backend.repository;

import com.dvicheck.backend.model.ItemFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ItemFeedbackRepository extends JpaRepository<ItemFeedback, UUID> {

    Optional<ItemFeedback> findByUserIdAndLineItemId(UUID userId, UUID lineItemId);

    List<ItemFeedback> findByUserId(UUID userId);

    long countByFeedbackAndLineItemId(String feedback, UUID lineItemId);
}
