package com.dvicheck.backend.repository;

import com.dvicheck.backend.model.PantryMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PantryMemoryRepository extends JpaRepository<PantryMemory, UUID> {

    Optional<PantryMemory> findByUserIdAndNormalisedName(UUID userId, String normalisedName);

    List<PantryMemory> findByUserIdOrderByLastBoughtDateDesc(UUID userId);
}
