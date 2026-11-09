package com.jobpilot.ai;

import com.jobpilot.offer.domain.RequirementCategory;
import com.jobpilot.offer.domain.RequirementType;
import java.util.UUID;

/** Un requisito de la oferta tal como se envía al LLM; el modelo debe devolver su {@code id} en la evaluación. */
public record RequirementSnapshot(UUID id, String description, RequirementType type, RequirementCategory category) {
}
