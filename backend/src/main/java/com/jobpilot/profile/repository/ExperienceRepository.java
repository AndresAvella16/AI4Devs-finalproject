package com.jobpilot.profile.repository;

import com.jobpilot.profile.domain.Experience;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExperienceRepository extends JpaRepository<Experience, UUID> {

    /** Experiencias en el orden del perfil (índice {@code (profile_id, sort_order)}). */
    @Query("SELECT e FROM Experience e WHERE e.profile.id = :profileId ORDER BY e.sortOrder")
    List<Experience> findByProfileIdOrdered(@Param("profileId") UUID profileId);
}
