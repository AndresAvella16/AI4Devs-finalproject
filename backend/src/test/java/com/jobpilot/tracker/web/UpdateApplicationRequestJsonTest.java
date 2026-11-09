package com.jobpilot.tracker.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobpilot.shared.web.FieldUpdate;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code PATCH /applications/{id}} solo modifica los campos que se envían: un campo ausente llega como {@code null}
 * (no se toca), un campo enviado a {@code null} llega como {@link FieldUpdate#clear()} (se borra) y un valor llega
 * como {@code FieldUpdate.of(valor)}. Las restricciones del tipo contenido se validan a través del
 * {@code FieldUpdateValueExtractor}.
 */
class UpdateApplicationRequestJsonTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void validator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void close() {
        validatorFactory.close();
    }

    private static UpdateApplicationRequest read(String json) {
        return JSON.readValue(json, UpdateApplicationRequest.class);
    }

    @Test
    void absentFieldsAreNull() {
        UpdateApplicationRequest request = read("{\"version\": 3}");

        assertThat(request.notes()).isNull();
        assertThat(request.appliedAt()).isNull();
        assertThat(request.followUpDate()).isNull();
        assertThat(request.boardPosition()).isNull();
        assertThat(request.version()).isEqualTo(3L);
    }

    @Test
    void explicitNullsClearTheField() {
        UpdateApplicationRequest request = read(
                "{\"notes\": null, \"appliedAt\": null, \"followUpDate\": null, \"version\": 0}");

        assertThat(request.notes()).isNotNull().satisfies(update -> assertThat(update.isClear()).isTrue());
        assertThat(request.appliedAt()).isNotNull().satisfies(update -> assertThat(update.isClear()).isTrue());
        assertThat(request.followUpDate()).isNotNull().satisfies(update -> assertThat(update.isClear()).isTrue());
    }

    @Test
    void valuesAreReadWithTheDeserializerOfTheContainedType() {
        UpdateApplicationRequest request = read("""
                {"notes": "Llamar el lunes", "appliedAt": "2026-11-02", "followUpDate": "2026-11-16",
                 "boardPosition": 2, "version": 1}""");

        assertThat(request.notes()).isEqualTo(FieldUpdate.of("Llamar el lunes"));
        assertThat(request.appliedAt()).isEqualTo(FieldUpdate.of(LocalDate.of(2026, 11, 2)));
        assertThat(request.followUpDate()).isEqualTo(FieldUpdate.of(LocalDate.of(2026, 11, 16)));
        assertThat(request.boardPosition()).isEqualTo(2);
    }

    @Test
    void anInvalidDateIsAJsonError() {
        assertThatThrownBy(() -> read("{\"appliedAt\": \"no-es-una-fecha\", \"version\": 0}"))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void notesLongerThan5000CharactersAreRejected() {
        UpdateApplicationRequest request = read("{\"notes\": \"" + "a".repeat(5001) + "\", \"version\": 0}");

        Set<ConstraintViolation<UpdateApplicationRequest>> violations = validator.validate(request);

        assertThat(violations).singleElement()
                .satisfies(violation -> assertThat(violation.getPropertyPath().toString()).startsWith("notes"));
    }

    @Test
    void notesOf5000CharactersAndClearedNotesAreValid() {
        assertThat(validator.validate(read("{\"notes\": \"" + "a".repeat(5000) + "\", \"version\": 0}"))).isEmpty();
        assertThat(validator.validate(read("{\"notes\": null, \"version\": 0}"))).isEmpty();
        assertThat(validator.validate(read("{\"version\": 0}"))).isEmpty();
    }
}
