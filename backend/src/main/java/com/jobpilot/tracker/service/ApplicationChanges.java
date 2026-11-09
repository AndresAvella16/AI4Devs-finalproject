package com.jobpilot.tracker.service;

import com.jobpilot.shared.web.FieldUpdate;
import java.time.LocalDate;

/**
 * Cambios de {@code PATCH /applications/{id}}. Solo se modifican los campos enviados: un {@link FieldUpdate}
 * {@code null} significa "no enviado" (no se toca) y {@link FieldUpdate#clear()}, "enviado a null" (se borra).
 * {@code boardPosition} {@code null} = no se reordena.
 */
public record ApplicationChanges(FieldUpdate<String> notes, FieldUpdate<LocalDate> appliedAt,
        FieldUpdate<LocalDate> followUpDate, Integer boardPosition, long version) {
}
