package com.dvicheck.backend.repository;

import com.dvicheck.backend.model.ShoppingList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShoppingListRepository extends JpaRepository<ShoppingList, UUID> {

    List<ShoppingList> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<ShoppingList> findByIdAndUserId(UUID id, UUID userId);
}
