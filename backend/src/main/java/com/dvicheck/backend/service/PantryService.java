package com.dvicheck.backend.service;

import com.dvicheck.backend.dto.PantryItemDto;
import com.dvicheck.backend.model.Bill;
import com.dvicheck.backend.model.LineItem;
import com.dvicheck.backend.model.PantryMemory;
import com.dvicheck.backend.repository.PantryMemoryRepository;
import com.dvicheck.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PantryService {

    private static final int DEFAULT_ESTIMATED_REMAINING_DAYS = 7;

    private final PantryMemoryRepository pantryMemoryRepository;
    private final UserRepository userRepository;

    @Transactional
    public void updateFromBill(UUID userId, Bill bill) {
        try {
            for (LineItem lineItem : bill.getLineItems()) {
                String normalisedName = PantryMemory.normalise(lineItem.getName());

                PantryMemory pantry = pantryMemoryRepository
                    .findByUserIdAndNormalisedName(userId, normalisedName)
                    .orElse(null);

                if (pantry != null) {
                    pantry.setLastBoughtDate(bill.getPurchaseDate());
                    pantry.setPurchaseCount(pantry.getPurchaseCount() + 1);
                    pantry.setTypicalQuantity(lineItem.getQuantity().toString());
                    pantry.setEstimatedRemainingDays(DEFAULT_ESTIMATED_REMAINING_DAYS);
                } else {
                    pantry = PantryMemory.builder()
                        .user(userRepository.getReferenceById(userId))
                        .itemName(lineItem.getName())
                        .normalisedName(normalisedName)
                        .lastBoughtDate(bill.getPurchaseDate())
                        .typicalQuantity(lineItem.getQuantity().toString())
                        .estimatedRemainingDays(DEFAULT_ESTIMATED_REMAINING_DAYS)
                        .purchaseCount(1)
                        .build();
                }

                pantryMemoryRepository.save(pantry);
                log.info("Updated pantry: item={}, lastBought={}", pantry.getItemName(), pantry.getLastBoughtDate());
            }
        } catch (Exception e) {
            log.warn("Pantry update failed for user={}, bill={}", userId, bill.getId(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<PantryItemDto> getPantryItems(UUID userId) {
        return pantryMemoryRepository.findByUserIdOrderByLastBoughtDateDesc(userId).stream()
            .map(this::toDto)
            .toList();
    }

    private PantryItemDto toDto(PantryMemory pantry) {
        return new PantryItemDto(
            pantry.getId(),
            pantry.getItemName(),
            pantry.getNormalisedName(),
            pantry.getLastBoughtDate(),
            pantry.getPurchaseCount(),
            pantry.getTypicalQuantity(),
            pantry.getEstimatedRemainingDays(),
            depletionStatus(pantry.getEstimatedRemainingDays())
        );
    }

    private String depletionStatus(int estimatedRemainingDays) {
        if (estimatedRemainingDays <= 2) {
            return "LOW";
        }
        if (estimatedRemainingDays <= 5) {
            return "MEDIUM";
        }
        return "OK";
    }
}
