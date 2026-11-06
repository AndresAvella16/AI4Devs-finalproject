---
description: Implementa un ticket del readme.md siguiendo el flujo SDD (especificación → tests → código)
argument-hint: <ID del ticket, p. ej. JP-BE-06>
---

Vas a implementar el ticket **$ARGUMENTS** de `readme.md`, siguiendo las reglas de `CLAUDE.md`.

## Fase 1 — Especificación (no escribas código todavía)

1. Busca el ticket $ARGUMENTS en la sección 6 de `readme.md`. Si no tiene detalle propio (solo aparece en el backlog), constrúyelo a partir de la historia de usuario relacionada (sección 5), la API (sección 4) y el modelo de datos (sección 3).
2. Responde con:
   - **Alcance:** qué incluye y qué queda fuera de este ticket.
   - **Criterios de aceptación:** los del ticket, o los que deduces de la historia, numerados.
   - **Dependencias:** tickets previos que deben existir. Si falta alguno, dilo.
   - **Plan de archivos:** lista de archivos nuevos y modificados, con una línea por archivo.
   - **Tests:** qué tests vas a escribir y de qué tipo (unitario, integración, frontend).
   - **Dudas o incoherencias** que hayas encontrado en el readme.
3. **Para y espera mi confirmación.**

## Fase 2 — Tests

Escribe primero los tests de los criterios de aceptación. Ejecútalos y comprueba que fallan por el motivo esperado.

## Fase 3 — Implementación

Implementa lo mínimo para que los tests pasen, respetando las capas y las reglas de `CLAUDE.md`. Si necesitas desviarte de la especificación, para y pregunta.

## Fase 4 — Verificación

Ejecuta la verificación completa del módulo afectado (`.\mvnw.cmd verify` en `backend/`; `npm test`, `npm run lint` y `npm run build` en `frontend/`). No des el ticket por terminado si algo falla.

## Fase 5 — Resumen

Termina con:
- Qué has hecho (archivos principales).
- Decisiones que has tomado tú y no estaban en la especificación.
- Cambios hechos en `readme.md`, si el contrato ha cambiado.
- Pendientes o deuda técnica.
- **Propuesta para `prompts.md`:** 2–3 líneas con el resultado de este ticket, para que yo añada mi criterio humano.

No hagas commit ni push: lo haré yo cuando revise el resultado.
