-- JP-BE-06: un análisis siempre tiene resumen y entre 3 y 5 sugerencias (HU-03, sección 3.2).
--
-- La aplicación ya lo valida en la respuesta del LLM; la BD lo garantiza también, como el resto de reglas de la
-- sección 3.2. Sin DEFAULT para suggestions: un análisis sin sugerencias no es válido, y '[]' no cumple el CHECK.
--
-- Datos existentes: los 3 análisis del seed de dev (R__demo_data.sql) ya cumplen (resumen no nulo y 3, 4 y 3
-- sugerencias), y hasta este ticket no había ningún endpoint que creara análisis, así que no hace falta corregir
-- ninguna fila antes de añadir las restricciones. En una BD nueva, esta migración se aplica antes que el seed
-- repetible, que también las cumple.
--
-- El CASE evita que jsonb_array_length falle con un error que no es de restricción si suggestions no es un array:
-- ese caso lo rechaza ck_match_analyses_suggestions_array (V1).

ALTER TABLE match_analyses
    ALTER COLUMN summary SET NOT NULL,
    ALTER COLUMN suggestions DROP DEFAULT,
    ADD CONSTRAINT ck_match_analyses_suggestions_count CHECK (
        CASE WHEN jsonb_typeof(suggestions) = 'array'
             THEN jsonb_array_length(suggestions) BETWEEN 3 AND 5
             ELSE false
        END);
