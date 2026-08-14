package com.dvicheck.backend.service;

import com.dvicheck.backend.dto.ShoppingItemDto;
import com.dvicheck.backend.dto.ShoppingListDto;
import com.dvicheck.backend.exception.DvicheckException;
import com.dvicheck.backend.model.ListStatus;
import com.dvicheck.backend.model.PantryMemory;
import com.dvicheck.backend.model.ShoppingItem;
import com.dvicheck.backend.model.ShoppingList;
import com.dvicheck.backend.model.User;
import com.dvicheck.backend.repository.PantryMemoryRepository;
import com.dvicheck.backend.repository.ShoppingItemRepository;
import com.dvicheck.backend.repository.ShoppingListRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShoppingListService {

    private static final int DUPLICATE_WINDOW_DAYS = 10;

    private final ShoppingListRepository shoppingListRepository;
    private final ShoppingItemRepository shoppingItemRepository;
    private final PantryMemoryRepository pantryMemoryRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public List<ShoppingListDto> getLists(UUID userId) {
        return shoppingListRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
            .map(list -> toListDto(list, list.getItems().size()))
            .toList();
    }

    @Transactional
    public ShoppingListDto createList(UUID userId, String name) {
        User user = userService.findById(userId);

        ShoppingList list = ShoppingList.builder()
            .user(user)
            .name(name)
            .status(ListStatus.DRAFT)
            .build();

        ShoppingList saved = shoppingListRepository.save(list);
        return toListDto(saved, 0);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getListWithItems(UUID userId, UUID listId) {
        ShoppingList list = shoppingListRepository.findByIdAndUserId(listId, userId)
            .orElseThrow(() -> DvicheckException.notFound("Shopping list"));

        List<ShoppingItemDto> itemDtos = shoppingItemRepository.findByListIdOrderByCreatedAtAsc(listId).stream()
            .map(this::toItemDto)
            .toList();

        return Map.of("list", toListDto(list, itemDtos.size()), "items", itemDtos);
    }

    @Transactional
    public ShoppingItemDto addItem(UUID userId, UUID listId, String name, String quantity) {
        ShoppingList list = shoppingListRepository.findByIdAndUserId(listId, userId)
            .orElseThrow(() -> DvicheckException.notFound("Shopping list"));

        DuplicateCheckResult duplicateCheck = checkForDuplicate(userId, name);

        ShoppingItem item = ShoppingItem.builder()
            .list(list)
            .name(name)
            .quantity(quantity != null ? quantity : "1")
            .isDuplicate(duplicateCheck.isDuplicate())
            .duplicateWarning(duplicateCheck.warning())
            .lastPurchasedDate(duplicateCheck.lastPurchasedDate())
            .build();

        ShoppingItem saved = shoppingItemRepository.save(item);
        return toItemDto(saved);
    }

    @Transactional
    public ShoppingItemDto toggleItem(UUID userId, UUID listId, UUID itemId) {
        shoppingListRepository.findByIdAndUserId(listId, userId)
            .orElseThrow(() -> DvicheckException.notFound("Shopping list"));

        ShoppingItem item = shoppingItemRepository.findByIdAndListId(itemId, listId)
            .orElseThrow(() -> DvicheckException.notFound("Shopping item"));

        item.setChecked(!item.isChecked());

        ShoppingItem saved = shoppingItemRepository.save(item);
        return toItemDto(saved);
    }

    @Transactional
    public void deleteItem(UUID userId, UUID listId, UUID itemId) {
        shoppingListRepository.findByIdAndUserId(listId, userId)
            .orElseThrow(() -> DvicheckException.notFound("Shopping list"));

        ShoppingItem item = shoppingItemRepository.findByIdAndListId(itemId, listId)
            .orElseThrow(() -> DvicheckException.notFound("Shopping item"));

        shoppingItemRepository.delete(item);
    }

    private DuplicateCheckResult checkForDuplicate(UUID userId, String itemName) {
        String normalisedName = PantryMemory.normalise(itemName);

        Optional<PantryMemory> match = pantryMemoryRepository.findByUserIdAndNormalisedName(userId, normalisedName);
        if (match.isEmpty()) {
            match = findSubstringMatch(userId, normalisedName);
        }

        return match
            .map(this::toDuplicateResult)
            .orElseGet(() -> new DuplicateCheckResult(false, null, null));
    }

    private Optional<PantryMemory> findSubstringMatch(UUID userId, String normalisedName) {
        return pantryMemoryRepository.findByUserId(userId).stream()
            .filter(candidate -> candidate.getNormalisedName().contains(normalisedName)
                || normalisedName.contains(candidate.getNormalisedName()))
            .max(Comparator.comparing(PantryMemory::getLastBoughtDate));
    }

    private DuplicateCheckResult toDuplicateResult(PantryMemory pantry) {
        LocalDate lastBoughtDate = pantry.getLastBoughtDate();
        boolean withinWindow = !lastBoughtDate.isBefore(LocalDate.now().minusDays(DUPLICATE_WINDOW_DAYS));

        if (withinWindow) {
            long daysAgo = ChronoUnit.DAYS.between(lastBoughtDate, LocalDate.now());
            String warning = "Bought " + daysAgo + " days ago — check your pantry first";
            return new DuplicateCheckResult(true, warning, lastBoughtDate);
        }

        return new DuplicateCheckResult(false, null, lastBoughtDate);
    }

    private ShoppingListDto toListDto(ShoppingList list, int itemCount) {
        return new ShoppingListDto(
            list.getId(),
            list.getName(),
            list.getStatus().name(),
            list.getCreatedAt(),
            list.getUpdatedAt(),
            itemCount
        );
    }

    private ShoppingItemDto toItemDto(ShoppingItem item) {
        return new ShoppingItemDto(
            item.getId(),
            item.getName(),
            item.getQuantity(),
            item.isChecked(),
            item.isDuplicate(),
            item.getDuplicateWarning(),
            item.getLastPurchasedDate()
        );
    }

    private record DuplicateCheckResult(boolean isDuplicate, String warning, LocalDate lastPurchasedDate) {}
}
