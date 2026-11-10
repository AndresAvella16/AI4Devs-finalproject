package com.jobpilot.e2e.support;

/**
 * Datos de los E2E. El usuario lo crea el seed del perfil e2e
 * ({@code backend/src/main/resources/db/e2e-seed/R__e2e_data.sql}) con perfil ya hecho, porque aún no se puede
 * subir el CV (JP-BE-04 / JP-FE-02). Credenciales ficticias, solo para el stack E2E.
 */
public final class E2EData {

    public static final String SEED_EMAIL = "e2e@jobpilot.test";
    public static final String SEED_PASSWORD = "Demo1234!";
    public static final String SEED_FULL_NAME = "Usuaria E2E Prueba";

    /**
     * Texto de la oferta que se pega en el formulario (≥ 200 caracteres). Con {@code LLM_MODE=fake} el texto da
     * igual: la extracción es siempre la del Ejemplo 1 del readme (3 obligatorios + 3 deseables) y el análisis, el
     * del Ejemplo 2 (→ 85).
     */
    public static final String OFFER_TEXT = """
            Buscamos un/a Desarrollador/a Java Junior para incorporarse a nuestro equipo de producto en Valencia, \
            con modalidad híbrida (3 días en oficina). Requisitos: al menos 1 año de experiencia con Java y Spring \
            Boot, conocimientos de SQL y PostgreSQL y uso habitual de Git. Se valorará experiencia con React, Docker \
            e inglés B2. Ofrecemos salario de 24.000 a 28.000 euros brutos anuales, formación continua y horario \
            flexible.""";

    /** Título y puntuación que da el LLM fake (fake-llm/offer-extraction.json y match-evaluation.json). */
    public static final String FAKE_OFFER_TITLE = "Desarrollador/a Java Junior";
    public static final int FAKE_SCORE = 85;

    private E2EData() {
    }
}
