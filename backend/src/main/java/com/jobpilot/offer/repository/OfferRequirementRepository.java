package com.jobpilot.offer.repository;

import com.jobpilot.offer.domain.OfferRequirement;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfferRequirementRepository extends JpaRepository<OfferRequirement, UUID> {

    /** Requisitos en el orden en que aparecen en la oferta. */
    @Query("SELECT r FROM OfferRequirement r WHERE r.offer.id = :offerId ORDER BY r.sortOrder")
    List<OfferRequirement> findByOfferIdOrdered(@Param("offerId") UUID offerId);

    @Modifying
    @Query("DELETE FROM OfferRequirement r WHERE r.offer.id = :offerId")
    int deleteByOfferId(@Param("offerId") UUID offerId);
}
