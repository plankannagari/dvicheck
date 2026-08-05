package com.dvicheck.backend.repository;

import com.dvicheck.backend.model.ShoppingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShoppingItemRepository extends JpaRepository<ShoppingItem, UUID> {

    List<ShoppingItem> findByListIdOrderByCreatedAtAsc(UUID listId);

    Optional<ShoppingItem> findByIdAndListId(UUID id, UUID listId);
}
