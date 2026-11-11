# CLAUDE.md — JobPilot

## Idioma

Responde siempre en español (también los resúmenes y las dudas). El código, los identificadores y los nombres técnicos van en inglés; los commits, en español con Conventional Commits.

Copiloto de búsqueda de empleo con IA. Proyecto final del máster AI4Devs (Andrés Avellá Navarro).
El usuario sube su CV, pega ofertas de trabajo y obtiene un análisis de encaje, una carta y un CV adaptados, y un tablero kanban de candidaturas.

## Fuente de verdad

- **`readme.md` es la especificación.** El modelo de datos (sección 3), la API (sección 4), las historias de usuario (sección 5) y los tickets (sección 6) mandan sobre cualquier suposición.
- Si al implementar algo la especificación es ambigua, contradictoria o imposible: **para y pregunta**. No inventes campos, endpoints, estados ni reglas de negocio.
- Si un cambio acordado altera el contrato (tablas, endpoints, schemas), actualiza también la sección correspondiente de `readme.md` en el mismo ticket.
- `prompts.md` lo mantiene el humano. No lo edites; al cerrar cada ticket, propón en tu resumen qué anotar en él.

## Stack (no añadas dependencias fuera de esta lista sin preguntar)

| Capa | Tecnología |
|---|---|
| Backend | Java 21, **Spring Boot 4.1.x**, Spring Web, Spring Security (+ OAuth2 Resource Server: JWT HS256 con Nimbus), Spring Data JPA, Bean Validation, Actuator, springdoc-openapi |
| IA | **Spring AI 2.0** (`spring-ai-starter-model-anthropic`), modelo Claude |
| BD | PostgreSQL 16, Flyway (`spring-boot-starter-flyway` + `flyway-database-postgresql`; en Boot 4 `flyway-core` solo no activa la autoconfiguración) |
| Utilidades backend | Bucket4j, Apache PDFBox, CommonMark, OpenHTMLtoPDF |
| Frontend | React 19, TypeScript (strict), Vite, React Router, TanStack Query, React Hook Form + Zod, Tailwind CSS, dnd-kit |
| Tests | JUnit 6 (Jupiter 6.0.3), AssertJ, Mockito, Testcontainers, MockMvc · Vitest, React Testing Library, MSW · Playwright for Java (`e2e/`) |
| Infra | Docker, Docker Compose, GitHub Actions |

Build: Maven Wrapper (`mvnw`) en `backend/` y `e2e/`; npm en `frontend/`.

## Estructura

```
backend/   Spring Boot, package-by-feature: com.jobpilot.{auth,profile,offer,analysis,document,tracker,ai,shared}
frontend/  React SPA: src/{app,api,features/{auth,profile,offers,analysis,documents,tracker},components,test}
e2e/       Tests E2E con Playwright for Java
```

Dentro de cada módulo del backend: `web/` (controllers + DTOs), `service/`, `domain/` (entidades JPA, enums, lógica pura), `repository/`.

## Reglas del backend

- **Capas:** Controller → Service → Repository. Los controllers solo validan, llaman al servicio y mapean a DTO. La lógica de negocio va en los servicios o en `domain/`.
- **DTOs como `record`**. Nunca expongas entidades JPA en la API.
- **Sin Lombok.**
- **Propiedad de los datos:** toda consulta de recursos de usuario filtra por el `userId` del token (`findByIdAndUserId`). Si no es suyo → `404`, nunca `403`.
- **Errores:** un único `@RestControllerAdvice` en `shared/` que devuelve `ProblemDetail` (RFC 9457). Excepciones de dominio propias (`NotFoundException`, `ConflictException`, `LlmTimeoutException` → 504, `LlmOverloadedException` → 503 + `Retry-After`, `LlmInvalidResponseException` → 502). Sin trazas en la respuesta.
- **IA:** solo a través de la interfaz `ai.LlmGateway`. Dos implementaciones: `SpringAiLlmGateway` (`llm.mode=real`) y `FakeLlmGateway` (`llm.mode=fake`, respuestas fijas desde `src/main/resources/fake-llm/*.json`), seleccionadas con `@ConditionalOnProperty`. Los prompts viven en `resources/prompts/*.st`. Ningún otro código importa clases de Spring AI.
- **Las llamadas al LLM van fuera de transacciones de BD.** Ninguna transacción ni conexión queda abierta mientras se espera al LLM. Lo que el usuario ha enviado se puede guardar antes en una transacción corta (p. ej. la oferta en `PENDIENTE`, para que su texto no se pierda); después se llama al LLM y su resultado se persiste en otra transacción corta.
- **La puntuación de encaje la calcula `MatchScoreCalculator`** (Java puro, sin Spring), con la fórmula de la sección 1.2 del readme. Nunca la calcula el LLM.
- **Esquema:** solo con migraciones Flyway (`db/migration/V<n>__descripcion.sql`). `spring.jpa.hibernate.ddl-auto=validate`. Nunca edites una migración ya commiteada: crea una nueva. Los datos de prueba van en `db/seed/` y solo se cargan en el perfil `dev`. **Excepción:** los datos de los E2E van en `db/e2e-seed/` (solo perfil `e2e`), no en una subcarpeta de `db/seed/`: Flyway busca también en las subcarpetas y el perfil `dev` los cargaría.
- **IDs:** `UUID` generados en la aplicación.
- **Fechas "de hoy":** usa un `java.time.Clock` inyectable con zona `Europe/Madrid` (nada de `LocalDate.now()` directo).
- **Perfiles:** `dev` (local, con seed), `e2e` (fake LLM, BD limpia + seed de E2E), `prod`. Configuración en `application.yml` + `application-<perfil>.yml`, con los valores sensibles leídos de variables de entorno.

## Reglas del frontend

- TypeScript `strict`. **Prohibido `any`**.
- Llamadas HTTP solo desde `src/api/` (cliente `fetch` con el JWT). Los componentes usan hooks de TanStack Query; no hay librería de estado global.
- Formularios con React Hook Form + Zod; los schemas Zod reflejan los de la sección 4 del readme.
- Cada funcionalidad vive en su carpeta de `src/features/`. Componentes compartidos en `src/components/`.
- Añade `data-testid` a los elementos con los que interactúan los tests E2E.
- Accesible: etiquetas en los formularios, foco visible y el kanban usable con el teclado.

## Tests (un ticket no está terminado sin sus tests en verde)

- Backend: `*Test` = unitarios (Surefire); `*IT` = integración con Testcontainers + PostgreSQL 16 real (Failsafe). Para los tests que tocan IA se usa `FakeLlmGateway` o un mock de `LlmGateway`; nunca la API real.
- Frontend: Vitest + React Testing Library, con MSW para simular la API.
- Escribe primero los tests de los criterios de aceptación del ticket.
- **Nunca borres ni debilites un test para que pase.** Si un test parece incorrecto, explícalo y pregunta.

## Comandos (Windows, PowerShell)

```powershell
docker compose up -d postgres                      # solo la BD
cd backend; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
cd backend; .\mvnw.cmd verify                      # unit + IT (requiere Docker Desktop)
cd frontend; npm run dev                           # http://localhost:5173
cd frontend; npm test; npm run lint; npm run build
docker compose up --build                          # todo el stack
```

## Seguridad

- **Nunca escribas secretos** (API keys, contraseñas reales, `JWT_SECRET`) en el código ni en archivos versionados. Las variables nuevas se añaden vacías a `.env.example`.
- `.env` está en `.gitignore`.
- Por defecto se desarrolla con `LLM_MODE=fake`: aún no hay API key de Anthropic.

## Flujo de trabajo (SDD: especificación → arquitectura → código)

Para cada ticket (usa `/ticket <ID>`):
1. Lee el ticket y las secciones del readme de las que depende. Resume el alcance y los criterios de aceptación, y lista los archivos que vas a crear o modificar. **Espera confirmación.**
2. Escribe los tests de los criterios de aceptación.
3. Implementa hasta que los tests pasen.
4. Ejecuta la verificación completa del módulo (`mvnw verify` y/o `npm test` + `lint` + `build`).
5. Termina con un resumen: qué has hecho, qué decisiones has tomado tú, qué queda pendiente y qué propones anotar en `prompts.md`.

## Git

- Una rama por ticket: `feat/JP-XX-NN-descripcion-corta`, creada desde `main`.
- Commits con Conventional Commits en español: `feat(analysis): calcular puntuación de encaje`.
- **No hagas commit, push ni merge sin que te lo pida.**
