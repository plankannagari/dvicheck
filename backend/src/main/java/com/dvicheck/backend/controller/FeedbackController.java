package com.dvicheck.backend.controller;

import com.dvicheck.backend.dto.ApiResponse;
import com.dvicheck.backend.dto.FeedbackRequest;
import com.dvicheck.backend.dto.FeedbackResponse;
import com.dvicheck.backend.exception.DvicheckException;
import com.dvicheck.backend.model.ItemFeedback;
import com.dvicheck.backend.model.LineItem;
import com.dvicheck.backend.repository.ItemFeedbackRepository;
import com.dvicheck.backend.repository.LineItemRepository;
import com.dvicheck.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private static final Set<String> VALID_FEEDBACK_VALUES = Set.of("HELPFUL", "UNHELPFUL");

    private final ItemFeedbackRepository itemFeedbackRepository;
    private final LineItemRepository lineItemRepository;
    private final UserRepository userRepository;

    private UUID currentUserId() {
        String principal = SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal().toString();
        return UUID.fromString(principal);
    }

    // @Transactional: needed here since this controller talks to repositories directly
    // (no service layer) — lineItem.getBill().getUser() below is a two-hop lazy traversal
    // that requires an open session, same reasoning as BillHistoryController.
    @Transactional
    @PostMapping("/{lineItemId}")
    public ResponseEntity<ApiResponse<FeedbackResponse>> submitFeedback(
            @PathVariable UUID lineItemId, @RequestBody FeedbackRequest request) {
        if (!VALID_FEEDBACK_VALUES.contains(request.feedback())) {
            throw DvicheckException.badRequest("Invalid feedback value");
        }

        UUID userId = currentUserId();

        LineItem lineItem = lineItemRepository.findById(lineItemId)
            .orElseThrow(() -> DvicheckException.notFound("Line item"));

        if (!lineItem.getBill().getUser().getId().equals(userId)) {
            throw DvicheckException.unauthorized();
        }

        ItemFeedback itemFeedback = itemFeedbackRepository
            .findByUserIdAndLineItemId(userId, lineItemId)
            .orElse(null);

        if (itemFeedback != null) {
            itemFeedback.setFeedback(request.feedback());
        } else {
            itemFeedback = ItemFeedback.builder()
                .user(userRepository.getReferenceById(userId))
                .lineItem(lineItem)
                .feedback(request.feedback())
                .build();
        }

        ItemFeedback saved = itemFeedbackRepository.save(itemFeedback);

        return ResponseEntity.ok(ApiResponse.ok(
            new FeedbackResponse(lineItemId, saved.getFeedback(), saved.getCreatedAt())));
    }
}
