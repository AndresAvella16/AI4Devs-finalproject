package com.jobpilot.tracker.web;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** {@code CreateApplicationRequest} (sección 4). */
public record CreateApplicationRequest(@NotNull UUID offerId) {
}
