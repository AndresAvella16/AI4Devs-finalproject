package com.jobpilot.shared.web;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;

/**
 * Deserializa {@link FieldUpdate}: el valor lo lee el deserializador del tipo contenido (String, LocalDate...); un
 * {@code null} explícito es {@link FieldUpdate#clear()} y un campo ausente se queda en {@code null}.
 */
class FieldUpdateDeserializer extends ValueDeserializer<FieldUpdate<?>> {

    private final ValueDeserializer<?> content;

    FieldUpdateDeserializer() {
        this(null);
    }

    private FieldUpdateDeserializer(ValueDeserializer<?> content) {
        this.content = content;
    }

    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        JavaType type = ctxt.getContextualType() != null ? ctxt.getContextualType() : property.getType();
        JavaType contentType = type.containedTypeOrUnknown(0);
        return new FieldUpdateDeserializer(ctxt.findContextualValueDeserializer(contentType, property));
    }

    @Override
    public FieldUpdate<?> deserialize(JsonParser parser, DeserializationContext ctxt) {
        return FieldUpdate.of(content.deserialize(parser, ctxt));
    }

    /** {@code "campo": null}: borrar el valor. */
    @Override
    public FieldUpdate<?> getNullValue(DeserializationContext ctxt) {
        return FieldUpdate.clear();
    }

    /** Campo ausente: no se toca. */
    @Override
    public Object getAbsentValue(DeserializationContext ctxt) {
        return null;
    }
}
