package com.jobpilot.offer.service;

import com.jobpilot.offer.domain.JobOffer;
import com.jobpilot.offer.domain.OfferRequirement;
import java.util.List;

/** Una oferta con sus requisitos en orden (vacíos si la extracción está PENDIENTE o FALLIDA). */
public record OfferDetails(JobOffer offer, List<OfferRequirement> requirements) {

    public OfferDetails {
        requirements = List.copyOf(requirements);
    }
}
