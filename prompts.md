> Registro del uso de IA en el proyecto **JobPilot** (Andrés Avellá Navarro).
> Para cada sección se recoge el prompt con el que se generó el contenido y los prompts de revisión con los que se refina. Versión de la **entrega final** (Entrega 3): todos los prompts que estaban planificados indican si se ejecutaron, cómo y, si no, por qué.

## Índice

0. [Flujo de trabajo con IA](#0-flujo-de-trabajo-con-ia)
   - [0.1. Prompts y workflows clave](#01-prompts-y-workflows-clave)
   - [0.2. Herramientas y modelos por fase](#02-herramientas-y-modelos-por-fase)
   - [0.3. Contexto avanzado](#03-contexto-avanzado)
   - [0.4. Criterio humano](#04-criterio-humano)
   - [0.5. Evolución del uso de la IA](#05-evolución-del-uso-de-la-ia)
1. [Descripción general del producto](#1-descripción-general-del-producto)
2. [Arquitectura del sistema](#2-arquitectura-del-sistema)
3. [Modelo de datos](#3-modelo-de-datos)
4. [Especificación de la API](#4-especificación-de-la-api)
5. [Historias de usuario](#5-historias-de-usuario)
6. [Tickets de trabajo](#6-tickets-de-trabajo)
7. [Pull requests](#7-pull-requests)

---

## 0. Flujo de trabajo con IA

### 0.1. Prompts y workflows clave

Los 5 que fueron el núcleo del proyecto, agrupados por fase, de la idea a la entrega:

| # | Fase | Prompt o workflow | Dónde |
|---|---|---|---|
| 1 | Análisis y diseño | De un problema real (estoy buscando trabajo) a la idea acotada a 5 funcionalidades y a la documentación completa de la Entrega 1 con las plantillas oficiales | Sección 1 → Prompts 1 y 3 |
| 2 | Diseño (revisión) | Cruzar API, modelo de datos y tickets en busca de incoherencias, en un chat nuevo que no había escrito el documento: encontró 24 | Sección 4 → Prompt 3 |
| 3 | Implementación y pruebas | **Workflow `/ticket` (SDD)**: especificación → revisión del plan por un segundo modelo → mi OK → tests primero → código → verificación → prueba real → resumen revisado → PR. Aplicado a los 18 tickets | Sección 6 → Prompt 3 |
| 4 | Validación de la IA del producto | **Prueba con la IA real**: comparar el borrador del CV con mi CV real y corregir la plantilla del prompt para cualquier CV (JP-BE-04), y validación automática con criterios flexibles (`LlmLiveIT`, JP-AI-01) | Sección 2.6 → Prompt 4 |
| 5 | Mantenimiento y entrega | **Cierre**: revisar el readme de principio a fin contra el código y verificar la instalación como un revisor, desde un clon limpio | Sección 7 → Prompt 4 |

### 0.2. Herramientas y modelos por fase

| Fase del ciclo de vida | Herramienta | Modelo | Uso |
|---|---|---|---|
| **Análisis** | Claude (claude.ai, Proyecto "Proyecto Final Master") | Claude Opus 5.5 | Generar la idea a partir de un problema real (búsqueda de empleo), acotar el MVP a 5 funcionalidades y redactar las historias de usuario. |
| **Diseño** | Claude (claude.ai) | Claude Opus 5.5 | Arquitectura, modelo de datos, contrato OpenAPI, tickets y la documentación de la Entrega 1. Revisiones en chats nuevos que no habían escrito el documento. |
| **Implementación** | Claude Code (terminal, Windows) | Claude Opus 5.5 (modelo por defecto de Claude Code) | Implementación ticket a ticket con el comando `/ticket`, guiada por el `readme.md` y el `CLAUDE.md`. |
| **Revisión de cada plan y resumen** | Claude (claude.ai, chat del Proyecto "Proyecto Final Master") | Claude Opus 5.5 | Revisar el plan de cada `/ticket` antes de dar el OK, preparar las respuestas a sus dudas, revisar el resumen final y redactar las descripciones de las PRs. Un segundo modelo que no ha escrito el código actúa como revisor. |
| **Pruebas** | Claude Code | Claude Opus 5.5 | Tests primero en cada ticket (fase 2 de `/ticket`): unitarios, de integración con Testcontainers, frontend con Vitest + MSW, **E2E con Playwright for Java** (JP-QA-01) y **validación con el LLM real** (`LlmLiveIT`, JP-AI-01). Además, prueba real con el perfil `dev` o Docker Compose en cada ticket. |
| **Despliegue e infraestructura** | Claude Code | Claude Opus 5.5 | Dockerfiles multi-stage, nginx con CSP, `docker-compose.yml` y `docker-compose.e2e.yml`, pipeline de GitHub Actions y Dependabot (JP-OPS-01). El despliegue público (opcional) quedó fuera de alcance. Desde que GitHub desactivó Actions (11/11/2026), Claude Code ejecuta en local los mismos pasos de `ci.yml`. |
| **Mantenimiento** | Claude (claude.ai) y Claude Code | Claude Opus 5.5 | Readme actualizado dentro de cada ticket, deuda técnica documentada, cierre de cada entrega (revisión del readme contra el código y verificación desde un clon limpio) y este archivo. |
| **Dentro del producto** | API de Anthropic a través de Spring AI 2.0 | **Claude Sonnet 5.5** (`claude-sonnet-5-5`, configurable con `ANTHROPIC_MODEL`) | Estructurar el CV, extraer los requisitos de la oferta, clasificar el encaje y redactar la carta y el CV adaptado. Elegido por coste y velocidad. En desarrollo y en los tests se usa `FakeLlmGateway` (respuestas fijas). |

### 0.3. Contexto avanzado

- **Proyecto de claude.ai** con la descripción y las reglas del máster como contexto permanente, para que todas las respuestas respeten los requisitos de evaluación (3–5 funcionalidades, tests, IA en las fases del ciclo de vida).
- **Plantillas oficiales** del `readme.md` y el `prompts.md` pasadas como entrada para que la documentación siga exactamente su estructura.
- **`CLAUDE.md`** en la raíz del repo (ver sección 2.3 → Prompt 2): el readme como fuente de verdad, stack con versiones, capas *package-by-feature*, reglas obligatorias (DTOs como `record`, `ProblemDetail`, consultas filtradas por `userId`, `LlmGateway` como único acceso al LLM, la puntuación la calcula Java, llamadas al LLM fuera de transacciones), comandos para Windows, el flujo SDD y la regla de **no hacer commit sin que se lo pida**. Claude Code lo lee en cada sesión, y lo he ido actualizando con lo aprendido en los tickets: Flyway en Boot 4, JWT con Spring Security, el seed de E2E fuera de `db/seed/` y la **regla de idioma** (responder siempre en español), que añadí en JP-BE-08 cuando la IA empezó a contestar en inglés después de compactar el contexto.
- **Comando personalizado `/ticket <ID>`** (`.claude/commands/ticket.md`): aplica el flujo SDD de la Lección 2 a cada ticket en 5 fases: especificación con alcance, criterios, plan de archivos y dudas → **espera mi OK** → tests primero → implementación → verificación completa → resumen con una propuesta para este archivo (ver sección 6 → Prompt 3).
- **Una conversación por ticket:** `/export` para guardar la conversación al cerrar el ticket y `/clear` antes del siguiente `/ticket`, para que el contexto no se llene y no haya que compactar a mitad de ticket.
- **Segundo modelo como revisor antes de cada OK:** el plan de la fase 1 y el resumen de la fase 5 de cada ticket los revisaba Claude en el chat del proyecto de claude.ai, que no escribía el código. Con esa revisión preparaba mis respuestas a las dudas y las correcciones del plan, y solo después daba el OK a Claude Code.
- **Subagente de revisión:** estaba previsto en la Entrega 1, pero no se usó. Lo sustituyó el segundo modelo de claude.ai, que cumple el mismo objetivo: que el código no se valide a sí mismo.

### 0.4. Criterio humano

Ajustes y decisiones míos sobre la salida de la IA.

**Entregas 1 y 2**

| Momento | Decisión tomada por mí |
|---|---|
| Elección de la idea | Partí de mi situación real (estoy buscando trabajo) para que el producto me sirva mientras lo construyo y funcione como portfolio. |
| Stack | Pedí que todo girara en torno a **Java**, que es lo que domino como desarrollador web junior, y elegí **React** frente a Thymeleaf para el frontend. |
| Estructura de la documentación | Exigí seguir las plantillas oficiales del máster en lugar del formato libre que proponía la IA. |
| Revisión de versiones | Pedí revisar que la documentación cumpliera los requisitos de la entrega; a raíz de esa revisión se actualizó el stack a Spring Boot 4 / Spring AI 2.0, porque la propuesta inicial usaba una versión ya sin soporte. |
| Revisión de arquitectura (2.1) | Ejecuté la revisión en un chat nuevo, para que no la hiciera la misma conversación que había escrito el documento. Acepté, entre otros, los cambios sobre la gestión de errores del LLM (504 / 503 / 502). |
| Revisión de coherencia (secciones 3, 4 y 6) | La revisión destapó que tres cambios que yo había aceptado en la ronda de la 2.1 contradecían otras secciones. **Lección:** después de aceptar un cambio hay que cruzarlo con el resto del documento. De las 24 incoherencias encontradas, **decidí corregir la 1 a la 11**, que son las que afectan al flujo principal, y dejar la 12 a la 24 (tipos, textos y ajustes menores) para antes de la Entrega 2. **Resultado final:** resueltas todas salvo la 24 (`llm_model` en `generated_documents`, opcional), que no se implementó (ver sección 4). |
| Revisión del modelo de datos (sección 3) | Apliqué el diff completo de la IA (FKs compuestas, índices documentados, `CHECK` nuevos, `VARCHAR(3)` para la moneda) y alineé el ticket de base de datos. Mantuve JSONB en idiomas, formación y sugerencias porque nunca se filtran por ellos en SQL. |
| Revisión de cada plan de `/ticket` | No di nunca el OK a un plan sin revisarlo, y respondí a **todas** sus dudas numeradas, no solo a algunas. El detalle por ticket está en la sección 6 → Prompt 3. |
| Prueba real además de los tests | Exigí en cada ticket arrancar la app con el perfil `dev` contra la BD de Docker antes del commit. En JP-BE-01 detectó un `.env` vacío y comentarios en la misma línea que el valor, que los tests con Testcontainers no ven. |
| Versiones por compatibilidad | Fijé Spring Boot 4.1.x (no una versión menor posterior) porque Spring AI 2.0 solo soporta Boot 4.0/4.1. |
| Seguridad: menos código propio | La IA detectó que JJWT arrastra Jackson 2 y Boot 4 usa Jackson 3, y propuso un adaptador propio. Elegí el soporte de JWT de Spring Security (OAuth2 Resource Server, Nimbus) para no escribir ni el adaptador ni el filtro JWT. |
| Coste del LLM | Elegí `claude-sonnet-5-5` como modelo por defecto del producto en vez de Opus: es suficiente para extraer y evaluar requisitos, y más rápido y barato. |
| Datos personales | Pedí que no se registren en los logs ni los cuerpos de las peticiones ni los prompts y respuestas del modelo, porque contienen datos del CV. Al LLM solo se envía el perfil estructurado, sin el texto del CV, nombre ni email. Las capturas del readme se recortaron para que no se viera mi navegador. |
| Probarlo yo en el navegador | Además de la prueba de Claude Code con curl, probé cada pantalla en el navegador. Encontré dos fallos que ni los tests ni el smoke test veían: una URL de la API absoluta que la CSP bloqueaba (JP-OPS-01; ahora el smoke test lo comprueba) y una URL no válida que bloqueaba el botón sin mostrar el error (JP-FE-03; ahora tiene test). |
| No perder datos del usuario | En JP-BE-05 la IA propuso guardar la oferta solo después de llamar al LLM. Lo cambié a PENDIENTE → LLM → COMPLETADA/FALLIDA para que el texto pegado no se pierda nunca, ni con errores que no son de IA. |
| Cumplir lo comprometido | El plan de JP-BE-06 se dejaba las incoherencias 15–17 y la cobertura ≥ 90 % de la definición de hecho del readme. Pedí incluirlas (migración V3 y JaCoCo), y comprobar antes que los datos de prueba cumplían las nuevas restricciones para que la migración no fallara sobre una BD existente. |
| Pensar en quien evalúa | En JP-FE-04, un usuario recién registrado no tiene perfil: en vez de mandarlo a una 404, pedí un placeholder en `/perfil` que explica que llegará en la próxima versión (lo sustituyó el onboarding real de JP-FE-02). |
| IA real frente a coste | Decidí cerrar la Entrega 2 en modo `fake`, documentándolo con claridad en el readme, y decidir en la Entrega 3 entre la API de Anthropic y un modelo local con Ollama. **Decisión en la Entrega 3:** la API de Anthropic (ver abajo). |

**Entrega 3**

| Momento | Decisión tomada por mí |
|---|---|
| IA real: proveedor y coste | Elegí la API de Anthropic frente a Ollama. Para controlar el coste usé saldo de prepago **sin recarga automática**. La clave nunca la escribo en la terminal ni en el chat con la IA: solo en el `.env`, que está en `.gitignore`. |
| JP-AI-01: validar un sistema no determinista | Pedí que los tests `llm-live` usaran **criterios flexibles** (estructura y reglas de negocio: validadores, un resultado por requisito, de 3 a 5 sugerencias, puntuación entre 0 y 100), nunca comparar el texto exacto. Al ejecutarlos detecté que 3 tests de integración (`ApplicationContextIT`, `DemoUserLoginIT` y `SeedDataIT`) dependían del `LLM_MODE` de mi `.env`: ahora fijan `llm.mode=fake`. |
| JP-BE-08: PATCH parcial con Jackson 3 | Acepté `FieldUpdate<T>` para distinguir en el `PATCH` un campo ausente de un `null` explícito, por cómo trata Jackson 3 los `Optional`. Cuando la IA empezó a responder en inglés después de compactar el contexto, añadí la regla de idioma a `CLAUDE.md`. |
| JP-FE-06: cuando la IA decide sola | Mis respuestas al menú de dudas se perdieron y la IA eligió por su cuenta la opción "más fiel a la spec": dejó fuera el botón **"Guardar en el tablero"** (sin él, ninguna oferta llegaba al tablero) y validaba las transiciones prohibidas solo en el backend. Lo corregí: botón incluido y bloqueo en el cliente con un *toast*, con el 409 del backend como red de seguridad. También pedí *lazy loading* de las páginas para quitar el aviso de 500 kB del build. |
| JP-QA-01: un E2E estable | Pedí el arrastre con `mouse.down` / `move` / `up` (con dnd-kit, `dragTo()` falla), ninguna espera fija, revisar si el límite de peticiones rompía la suite y ejecutarla varias veces seguidas: 6 ejecuciones en verde. El seed de E2E va en `db/e2e-seed/` para no contaminar el perfil `dev`. Modo visible con `slowMo` para grabar el vídeo. (Ver sección 2.6 → Prompt 3.) |
| JP-BE-04: una spec contradictoria | La spec pedía guardar el texto del CV, pero el perfil aún no existe al subirlo. Se resolvió con un **borrador en memoria** de 30 minutos que se guarda con el `PUT /profile`. Pedí resolver ya con un 409 la carrera del primer guardado en vez de dejarla como deuda, un límite de páginas y protección contra *prompt injection* en la plantilla. |
| JP-BE-04: probar con mi CV real | Probé con la IA real y mi CV real, y comparé el borrador con el PDF. Detecté que una nota oculta de la plantilla del CV ("PHP / Java (según tu caso)") hacía que la IA descartara PHP y Java, y que el titular se inventaba tecnologías. Pedí reglas en la plantilla del prompt para cualquier CV (ver sección 2.6 → Prompt 4): el resultado pasó a 13 habilidades, con PHP y Java. Al pedir `temperature` 0, la API la rechazó para este modelo y se quitó. También se descubrió que el `LLM_MODE` del `.env` gana al perfil `llm-live`, y se documentó en el readme. |
| JP-FE-02: datos personales en los tests | El E2E empieza ya en el registro y sube un **CV ficticio generado en memoria**, nunca mi CV real, que no entra en el repositorio. |
| JP-BE-07: la IA no ve mis datos | La IA no recibe el nombre del usuario: el servidor añade la firma, y pedí escapar el Markdown del nombre. El PDF no carga ningún recurso externo, con tests que cuentan las peticiones a un servidor local (0). Revisé los PDF reales generados: una carta de 301 palabras, honesta con mi nivel de React. |
| JP-FE-05: menos es más seguro | "Exportar PDF" queda desactivado mientras haya cambios sin guardar, en lugar de guardar automáticamente. Sin vista previa HTML, para no tener que sanear el Markdown que genera la IA: el frontend nunca lo convierte a HTML. |
| CI desactivada por GitHub | GitHub desactivó Actions en el repositorio el 11/11/2026. Desde entonces verifico en local con los mismos comandos de `ci.yml` (backend, frontend y E2E) antes de cada fusión, y lo documenté en el readme con capturas. |
| Cierre: el readme fiel al código | Pedí revisar el readme de principio a fin contra el código. Corrigió referencias inventadas o desfasadas (`OwnershipIT` y `cover-letter.st`, que no existen, y JUnit 5 → 6) y consolidó la deuda técnica en un solo sitio. Después pedí la verificación como revisor desde un clon limpio, que encontró dos instrucciones ambiguas (ver sección 7 → Prompt 4). |

### 0.5. Evolución del uso de la IA

| | Entrega 1 — Documentación técnica | Entrega 2 — Código funcional | Entrega 3 — Entrega final |
|---|---|---|---|
| **Herramienta principal** | Claude en claude.ai (chat) | Claude Code en la terminal + Claude en claude.ai como revisor | Lo mismo: una conversación por ticket con `/export` y `/clear` |
| **Papel de la IA** | Generar la idea, la arquitectura y la documentación | Implementar ticket a ticket con tests primero | Además, ser **parte del producto** (Claude Sonnet 5.5 con la API real) y verificar la entrega como un revisor |
| **Contexto** | Proyecto de claude.ai y plantillas oficiales | `CLAUDE.md`, comando `/ticket` y el readme como fuente de verdad | `CLAUDE.md` ampliado con lo aprendido (regla de idioma, seed de E2E) |
| **Revisión** | Revisiones en un chat nuevo que no había escrito el documento (24 incoherencias) | Un segundo modelo revisa cada plan y resumen antes de mi OK; prueba real con el perfil `dev` | Lo mismo, más pruebas con la IA real y con mi CV real, y verificación desde un clon limpio |
| **Pruebas de la IA** | Diseño de la estrategia: el LLM clasifica y Java puntúa | Tests deterministas con `FakeLlmGateway` y respuestas simuladas | Validación con el modelo real y criterios flexibles (`llm-live`), y E2E con Playwright |
| **Mi papel** | Elegir la idea y el stack, y decidir qué incoherencias corregir | Responder a todas las dudas, exigir la prueba real y no perder datos del usuario | Detectar lo que la IA no ve: decisiones tomadas sola (JP-FE-06), sesgos de una plantilla de CV real (JP-BE-04), dependencias ocultas del `.env` (JP-AI-01) y la CI desactivada |

**Qué aprendí:**

- La IA rinde más con una **especificación cerrada** (readme + `CLAUDE.md`) y un **flujo fijo** (`/ticket`). Cuando la spec es ambigua o contradictoria, lo mejor es que pare y pregunte.
- **Un segundo modelo que no ha escrito el código** encuentra problemas que la conversación que lo escribió no ve, igual que en la Entrega 1 con la documentación.
- **Los tests con respuestas simuladas no bastan para un sistema con LLM:** hacen falta pruebas con el modelo real y datos reales (mi CV), pero con criterios flexibles y sin datos personales en el repositorio.
- **El contexto se gestiona:** una conversación por ticket y reglas explícitas en `CLAUDE.md` evitan que la IA olvide decisiones o cambie de idioma después de compactar.

---

## 1. Descripción general del producto

**Prompt 1:** Generación de la idea (Claude Opus 5.5, claude.ai)

```
Tengo que realizar un Proyecto para un producto de software end-to-end apoyándome
en IA en todas las fases del ciclo de vida por lo que necesito que me des una idea
de que podría ser el producto yo en este caso me encuentro sin trabajo por lo que
podrías hacer algo que me solucione ese problema.
```

*Resultado:* propuesta de **JobPilot** (copiloto de búsqueda de empleo) con 5 funcionalidades must-have (perfil desde CV, importar oferta, análisis de encaje, carta y CV adaptados, tablero de candidaturas), 2 should-have y dos ideas alternativas (simulador de entrevistas, radar de empresas). La IA recomendó además evitar el scraping de portales de empleo, por sus condiciones de uso y su fragilidad.

**Prompt 2:** Adaptación del stack a mi perfil

```
Soy un Desarrollador Web Junior y manejo Java
```

*Resultado:* stack ajustado a Java 21 + Spring Boot 3, Spring Data JPA + PostgreSQL + Flyway, Spring AI para Claude, PDFBox para leer el CV, JUnit 5 + Mockito + Testcontainers + Playwright for Java para los tests, y dos opciones de frontend (React o Thymeleaf + HTMX). También propuso qué funcionalidad entregar en cada fase.

**Prompt 3:** Generación de la documentación de la Entrega 1 con las plantillas oficiales

```
Me parece bien y el frontend prefiero React, ahora solo faltaria realizar el
readme.md y el prompts.md. Ten en cuenta que tienes que seguir las plantillas
oficiales de mi master que son estas:
[plantilla de prompts.md adjunta en el mensaje]
[plantilla de readme.md adjunta como fichero]
Nombre Completo: Andrés Avellá Navarro
URL de mi repo: https://github.com/AndresAvella16/AI4Devs-finalproject
```

*Resultado:* primera versión completa del `readme.md` (secciones 0–7) y de este `prompts.md`. Durante la generación, la IA tomó una decisión de diseño relevante: **el LLM clasifica cada requisito y Java calcula la puntuación**, para que el número sea determinista y se pueda testear.

---

## 2. Arquitectura del Sistema

### **2.1. Diagrama de arquitectura:**

**Prompt 1:** Generado dentro del Prompt 3 de la sección 1 (documentación completa de la Entrega 1).

**Prompt 2:** Revisión crítica de la arquitectura (Claude, chat nuevo "Revisión de arquitectura y modelo de datos")

```
Actúa como arquitecto de software senior revisando el proyecto de un desarrollador
junior. Te paso la sección 2.1 de mi readme.md (diagrama Mermaid y justificación).
Contexto: MVP de 5 funcionalidades, una sola persona, Spring Boot 4 + React + PostgreSQL
+ Claude vía Spring AI, a entregar en pocas semanas.
1. ¿La elección de monolito modular está bien justificada para este alcance?
2. ¿Falta algún componente o flecha en el diagrama?
3. ¿Qué riesgos tienen las llamadas síncronas al LLM dentro de la petición HTTP y
   cuál es la mitigación mínima razonable para un MVP?
Responde con una lista de cambios concretos, ordenados por impacto. No reescribas
la sección entera.

[A continuación pegué la sección 2.1 completa de mi readme.md]
```

*Resultado:* la IA propuso una lista de cambios ordenada por impacto. Entre los que acepté:
- Un nodo de almacenamiento de ficheros (`FileStorage`, `bytea`).
- Un límite de 20 peticiones al día con un contador en base de datos.
- Distinguir los errores del LLM: `504` por timeout, `503` con `Retry-After` por saturación y `502` por respuesta no válida.
- Citar el RFC 9457 en lugar del 7807.

En esa misma conversación revisé también el modelo de datos (sección 3 → Prompt 2).

*Ajuste humano:* la revisión de coherencia posterior (sección 4 → Prompt 3) mostró que dos de los cambios aceptados contradecían otras secciones del readme:
- **Almacenamiento de ficheros:** la sección 2.5 y la HU-01 dicen que el PDF no se guarda. Eliminé el nodo y dejé explícito que no se guardan ficheros.
- **Límite de peticiones:** la sección 2.5 ya definía 30/h con Bucket4j. Dejé una sola fuente de verdad en la 2.5, añadiendo un `Semaphore` de 3 llamadas simultáneas, y la 2.1 remite a ella.

Mantuve la gestión de errores 504/503/502 y el RFC 9457 en todo el documento.

**Prompt 3:** Validación de los diagramas Mermaid (Claude Opus 5.5, claude.ai)

```
Los diagramas no se pueden ver [...] no voy a guardar los cambios sin que se vean
los diagramas de flujo principal de usuario, Diagrama de arquitectura, etc
```

*Resultado:* la IA renderizó los 5 diagramas del readme con `mermaid-cli`, el motor oficial de Mermaid, y todos se generaron sin errores. Así descartó un problema de sintaxis. El origen era un fallo conocido del editor web de GitHub: la pestaña *Preview* no renderiza Mermaid, pero el archivo guardado sí.

*Ajuste humano:* en lugar de modificar los diagramas, guardé el readme en la rama `feature/entrega-1-aan` y comprobé el resultado en la vista normal del archivo.

### **2.2. Descripción de componentes principales:**

**Prompt 1:** Generado dentro del Prompt 3 de la sección 1.

**Prompt 2:** Comprobación de compatibilidad de librerías. *No se lanzó como prompt aparte: la hizo Claude Code dentro de la fase 1 de cada ticket, consultando Maven Central. Resultado: Spring Boot 4.1.2, springdoc-openapi 3.2.0, Spring AI 2.0.2 (compilado contra Boot 4.1.2 y Jackson 3), y se descartó Spring AI 2.1.0-RC2 por ser una versión candidata.*

```
Para este listado de componentes (Spring Boot 4, Java 21, Spring AI 2.0 con Anthropic,
springdoc-openapi, JJWT, Bucket4j, PDFBox, OpenHTMLtoPDF, Flyway, Testcontainers,
React 19, Vite, TanStack Query, dnd-kit, React Hook Form, Zod, Tailwind, Vitest,
MSW, Playwright for Java):
dime la última versión estable compatible entre sí y señala cualquier conflicto
conocido (por ejemplo, Spring AI y la versión de Spring Boot). Formato: tabla
componente | versión | nota.
```

**Prompt 3:** Simplificación para un perfil junior. *No se ejecutó: no hizo falta. El stack se mantuvo hasta el final, y el único cambio fue sustituir JJWT por el soporte de JWT de Spring Security (ver sección 6).*

```
De estos componentes, ¿cuáles podría eliminar o sustituir por algo más simple sin
perder ninguna de las 5 funcionalidades ni la calidad de los tests? Justifica cada
recorte en una línea.
```

### **2.3. Descripción de alto nivel del proyecto y estructura de ficheros**

**Prompt 1:** Generado dentro del Prompt 3 de la sección 1.

**Prompt 2:** Generación del `CLAUDE.md` y del comando `/ticket` (Claude Opus 5.5, claude.ai, con la Lección 2 como contexto)

```
Tengo Windows 10 con Java 21 (Temurin), Node.js LTS, Docker Desktop con WSL 2, Git y
VS Code con Extension Pack for Java y Spring Boot Extension Pack. Voy a programar con
Claude Code, como dice `prompts.md`. Todavía no tengo API key de Anthropic:
desarrollamos con `LLM_MODE=fake` y la saco antes de la demo. El repo está en
`C:\dev\AI4Devs-finalproject`.
```

*Resultado:* a partir de las secciones 2.2, 2.3 y 2.5 del readme y de la Lección 2 (SDD por ticket; "define tu arquitectura desde el minuto uno"; *rules* estrictas para React), la IA generó `CLAUDE.md`, el comando `.claude/commands/ticket.md`, `.gitignore`, `.gitattributes` (LF en el repo y CRLF solo en los scripts de Windows) y `.env.example`, y los dejó directamente en el repo.

*Ajuste humano:*
- Antes de usarlos, comprobé contra la Lección 1 que trabajar en `main` con una rama por ticket y crear `feature/entrega-2-aan` al final cumple la regla de "partir del estado más reciente".
- Mantuve la regla de que Claude Code no haga commit, push ni merge sin pedírselo.
- Cuando la IA puso los comentarios de `.env.example` en la misma línea que el valor, se corrigió en JP-BE-01, porque al leer el `.env` en formato `.properties` se tragaban como parte del valor.

**Prompt 3:** Scaffolding inicial. *El del backend se hizo con `/ticket JP-BE-01` (ver sección 6 → Prompt 3); el del frontend, con `/ticket JP-FE-01`. Este prompt queda como referencia del alcance.*

```
Usando el CLAUDE.md del repositorio, crea el scaffolding de backend/ (Spring Boot 4,
Java 21, Maven Wrapper) y frontend/ (Vite + React + TypeScript) con la estructura de
carpetas de la sección 2.3, una ruta /actuator/health y una página de inicio que
llame a la API. No implementes todavía ninguna funcionalidad. Verifica que
./mvnw verify y npm run build pasan.
```

### **2.4. Infraestructura y despliegue**

**Prompt 1:** Generado dentro del Prompt 3 de la sección 1.

**Prompt 2:** Docker Compose y Dockerfiles. *Se hizo con `/ticket JP-OPS-01` (ver sección 6 → Prompt 3).* Ajuste humano: CSP con `script-src 'self'` estricto y `style-src 'unsafe-inline'` (dnd-kit usa estilos en línea), comprobar que la herramienta del healthcheck existe en la imagen y que el perfil `e2e` no arranque con la IA real.

```
Genera un Dockerfile multi-stage para el backend (Maven → eclipse-temurin:21-jre,
usuario no root, healthcheck sobre /actuator/health), un Dockerfile para el frontend
(build de Vite → nginx sirviendo estáticos y redirigiendo /api al backend) y un
docker-compose.yml con postgres:16 (volumen persistente), backend y frontend, leyendo
las variables de .env. Añade docker-compose.e2e.yml con LLM_MODE=fake.
```

**Prompt 3:** Pipeline de CI. *Se hizo con `/ticket JP-OPS-01`: jobs `backend` y `frontend` en paralelo y `e2e` con smoke test, al que JP-QA-01 añadió después Playwright.* Ajuste humano: Dependabot semanal, agrupado y con un máximo de 3 PRs abiertas; la primera ejecución real en GitHub salió en verde.

```
Crea .github/workflows/ci.yml con tres jobs: backend (./mvnw verify, que incluye los
tests de Testcontainers), frontend (npm ci, lint, test, build) y e2e (depende de los
dos anteriores; levanta docker-compose.e2e.yml y ejecuta Playwright). Usa caché de
Maven y npm. El deploy a Render solo debe ejecutarse en main y si todo está en verde.
```

### **2.5. Seguridad**

**Prompt 1:** Generado dentro del Prompt 3 de la sección 1.

**Prompt 2:** Revisión de amenazas. *No se ejecutó como prompt aparte. Las amenazas se trataron dentro de cada ticket, con un test que demuestra cada control:*
- ***Prompt injection:*** *los datos del usuario van delimitados y marcados como no confiables en las plantillas (`_system-base.st`, JP-BE-03; `cv-extraction.st`, JP-BE-04). `ProfileControllerIT` sube un CV con instrucciones incrustadas.*
- ***PDF sin recursos externos:*** *OpenHTMLtoPDF no carga nada. `PdfRendererTest` cuenta las peticiones a un servidor HTTP local (0) y prueba `<script>`, imágenes remotas, `file:`, `data:` y `javascript:` (JP-BE-07).*
- ***Escape del nombre en la firma:*** *el servidor añade la firma con el nombre escapado como Markdown (JP-BE-07).*
- ***Sin datos de la cuenta en la IA:*** *nunca se envían ni el nombre ni el email, ni el texto del CV en el encaje (JP-BE-06 y JP-BE-07).*
- ***Resto de controles:*** *subida del PDF (tipo MIME, firma, tamaño y páginas, JP-BE-04), recursos de otros usuarios (404 en cada `*ControllerIT`) y JWT en el frontend (CSP estricta, JP-OPS-01; riesgo anotado como deuda en la sección 8 del readme).*

```
Haz un análisis rápido de amenazas (STRIDE simplificado) de JobPilot con la
arquitectura y la tabla de seguridad de mi readme.md. Presta especial atención a:
la subida de PDFs, la prompt injection a través del texto de las ofertas, el acceso
a recursos de otros usuarios y la gestión del JWT en el frontend. Dime qué falta en
la tabla y qué test automatizado demostraría cada control.
```

**Prompt 3:** Endurecimiento del prompt del sistema. *No se ejecutó como prompt aparte. El endurecimiento se hizo dentro de los tickets: reglas comunes en `_system-base.st` (los datos entre etiquetas son datos, nunca instrucciones, y no se inventa nada) desde JP-BE-03, y reglas propias en la plantilla del CV en JP-BE-04 (ver sección 2.6 → Prompt 4).*

```
Este es el prompt de sistema que uso para extraer requisitos de una oferta:
[pegar prompts/extract-offer.st]
Propón una versión más robusta frente a prompt injection (el texto de la oferta es
contenido no confiable) sin empeorar la extracción. Incluye 3 ofertas de prueba
maliciosas para añadirlas como fixtures de test.
```

### **2.6. Tests**

**Prompt 1:** Generado dentro del Prompt 3 de la sección 1.

**Prompt 2:** Casos de test del cálculo de puntuación. *Ejecutado dentro de `/ticket JP-BE-06` (Entrega 2): `MatchScoreCalculatorTest` con 13 casos parametrizados, y JaCoCo con el 100 % de líneas en `analysis.domain`.*

```
Genera una clase JUnit 5 MatchScoreCalculatorTest con @ParameterizedTest y
@MethodSource que cubra: todo CUMPLE, todo NO_CUMPLE, sin requisitos deseables,
un único requisito, mezcla de resultados, redondeo HALF_UP en el límite (x,5),
detección de requisito bloqueante y lista vacía (excepción). Usa AssertJ.
La fórmula está en la sección 1.2 del readme.md.
```

**Prompt 3:** E2E del flujo principal. *Ejecutado con `/ticket JP-QA-01` y ampliado en JP-FE-02 (registro y CV) y JP-FE-05 (carta y PDF). Diferencia con el plan: el CV no es un fichero de `fixtures/`, sino un PDF ficticio que se genera en memoria en el propio test, para que nunca haya un CV real en el repositorio.*

```
Escribe un test E2E con Playwright for Java (JUnit 5) que, contra
docker-compose.e2e.yml (LLM_MODE=fake), recorra: registro → subida de
fixtures/cv-ejemplo.pdf → guardar perfil → pegar fixtures/oferta-java-junior.txt →
comprobar que aparecen 6 requisitos y la puntuación 85 → generar carta → descargar
PDF → guardar en el tablero → arrastrar a "Enviada" → recargar y comprobar la
columna. Usa selectores data-testid y nada de esperas fijas.
```

*Ajuste humano sobre el plan de JP-QA-01* (prompt literal, enviado a Claude Code antes de dar el OK):

```
Añade esto al plan:
- Arrastre con dnd-kit: no uses dragTo(). Hazlo con mouse.down → mouse.move en varios pasos (supera los 5 px de activación) → mouse.up, y espera la respuesta del PATCH /status antes de recargar.
- Ninguna espera fija (Thread.sleep): solo auto-wait y assertThat de Playwright o waitForResponse.
- Comprueba que el rate limiting (Bucket4j) de login/registro no rompe la suite al ejecutarla varias veces seguidas; si lo hace, ajústalo solo en el perfil e2e.
- Ejecuta la suite 3 veces seguidas en local contra el stack e2e para comprobar que es estable, y dime cuánto tarda.
```

*Resultado:* arrastre desde el asa ⠿ de la tarjeta con `mouse.down` / `move` / `up` y `waitForResponse`, sin esperas fijas. El cupo de IA se sube solo en el perfil `e2e`, y el seed de E2E va en `db/e2e-seed/` para no contaminar el perfil `dev`. La suite pasó 6 veces seguidas en verde. Además, un modo visible (`-Dheadless=false -DslowMo=600`) para grabar el vídeo.

**Prompt 4:** Prueba con la IA real. **Workflow clave de la Entrega 3.**

En dos partes:

**a) Validación automática con criterios flexibles (JP-AI-01).** *Descrito como workflow: no conservo el texto literal de los prompts.* Con `/ticket JP-AI-01`, Claude Code creó `LlmLiveIT` (`@Tag("llm-live")`), que llama al `SpringAiLlmGateway` real con 3 ofertas ficticias, un perfil de ejemplo y, desde JP-BE-07, una carta. Mi criterio fue **validar estructura y reglas, nunca el texto exacto**: las respuestas pasan los validadores, cada requisito tiene un resultado, hay de 3 a 5 sugerencias, la puntuación está entre 0 y 100 y la carta cumple la HU-04. El grupo queda fuera de `mvnw verify` y de la CI, y se ejecuta a mano con `mvnw verify -Pllm-live`, que lee la clave del `.env`. Al ejecutarlo detecté que 3 tests de integración dependían del `LLM_MODE` de mi `.env`; ahora fijan `llm.mode=fake`.

**b) Validación con mi CV real (JP-BE-04).** Probé la subida del CV con la IA real y comparé el borrador con el PDF. Prompt literal:

```
He comparado el borrador con mi CV. Está bien salvo dos cosas, y quiero que la plantilla las resuelva para cualquier CV:
1. Habilidades con barra o agrupadas: mi CV pone "PHP / Java" (con una nota de plantilla oculta "(según tu caso)") y la IA descartó las dos. Indica en cv-extraction.st que las habilidades separadas por "/", "," o "y" se extraen por separado, y que las notas o aclaraciones entre paréntesis se ignoran, pero no la habilidad. Añade un caso al test del parser o de la plantilla con "PHP / Java (según tu caso)".
2. Titular: mi CV pone "DESARROLLADOR WEB" y la IA añadió "Angular · TypeScript". Indica que el titular se toma literal del CV si existe (normalizando mayúsculas), sin añadir tecnologías.
```

*Resultado:* reglas nuevas en `cv-extraction.st` (habilidades agrupadas separadas, notas entre paréntesis ignoradas y titular literal). Con la IA real, el borrador pasó a 13 habilidades, con PHP y Java, y el titular dejó de inventar tecnologías. En la misma prueba, la API rechazó `temperature` para `claude-sonnet-5-5` y se quitó, y se descubrió que el `LLM_MODE` del `.env` gana al perfil `llm-live` (documentado en el readme, 1.4).

*Ajuste humano:* el CV real solo se usó en local, con la clave en el `.env`. Los tests usan PDFs generados en memoria.

---

## 3. Modelo de Datos

**Prompt 1:** Generado dentro del Prompt 3 de la sección 1.

**Prompt 2:** Revisión de normalización y restricciones (Claude, chat "Revisión de arquitectura y modelo de datos")

```
Revisa este modelo de datos (diagrama Mermaid + tablas de la sección 3 de mi
readme.md) como DBA de PostgreSQL:
1. ¿Está bien usar JSONB para idiomas, formación y sugerencias, o debería normalizarlo?
2. ¿Faltan índices para las consultas del dashboard y del tablero?
3. ¿Hay alguna restricción que no se pueda expresar como está escrita (por ejemplo,
   UNIQUE sobre lower(email))?
4. ¿Cómo garantizo que un requirement_match apunta a un requisito de la misma oferta
   que su análisis?
Devuelve los cambios como diff sobre la sección.
```

*Resultado:* la IA devolvió un diff completo de la sección 3. Respuestas a las cuatro preguntas:
1. **JSONB:** se mantiene para idiomas, formación y sugerencias, porque nunca se filtran en SQL. Se añaden `NOT NULL` y `CHECK (jsonb_typeof(...) = 'array')`, y la forma de cada elemento la valida la aplicación.
2. **Índices:** nueva tabla de índices con la consulta a la que sirve cada uno, más la consulta `LATERAL` del dashboard. Recordó que PostgreSQL no indexa las FKs automáticamente.
3. **`UNIQUE (lower(email))`:** no se puede declarar así como restricción. Se sustituye por normalizar en la aplicación + `CHECK (email = lower(email))` + `UNIQUE` normal. Para las habilidades se usa un índice único de expresión.
4. **Requisito de la misma oferta:** columna `offer_id` en `requirement_matches` y dos **FKs compuestas** que la comparten. También una FK compuesta en `applications` para que la candidatura sea siempre del propietario de la oferta.

Además propuso `currency VARCHAR(3)` en lugar de `CHAR(3)`, porque Hibernate falla al validar el esquema con `bpchar`, y varios `CHECK` nuevos (moneda obligatoria si hay salario, `board_position >= 0`, `applied_at` obligatorio desde ENVIADA, historial sin estados repetidos).

*Ajuste humano:* primero corregí las incoherencias 1–11 de la revisión de coherencia y después **apliqué el diff completo** antes de cerrar la Entrega 1. Al aplicarlo:
- Mantuve la referencia al endpoint de reintento (`POST /offers/{offerId}/extraction`) que había añadido con la fila 5.
- Actualicé el ticket JP-DB-01 (restricciones, criterios de aceptación y datos de prueba con `applied_at`) para que no quedara desalineado. Esto era la fila 19 de la revisión de coherencia.

**Prompt 3:** Generación de la migración Flyway. *Se hizo con `/ticket JP-DB-01` (ver sección 6 → Prompt 3): `V1__init_schema.sql` con las 11 tablas, las restricciones con nombre explícito y los 16 índices de la sección 3.2, y `R__demo_data.sql` con los datos de prueba.*

```
A partir de la sección 3.2 de mi readme.md, genera V1__init_schema.sql para
PostgreSQL 16 con todas las tablas, FKs con ON DELETE CASCADE, CHECKs para los
enumerados, índices únicos funcionales e índices de las FKs. Después genera
R__demo_data.sql con los datos de prueba descritos en el ticket JP-DB-01,
idempotente con ON CONFLICT DO NOTHING.
```

---

## 4. Especificación de la API

**Prompt 1:** Generado dentro del Prompt 3 de la sección 1.

**Prompt 2:** Validación del contrato OpenAPI. *No se ejecutó como prompt aparte. En la Entrega 2, la sintaxis del YAML y los `$ref` se validaron con `openapi-spec-validator`; cada ticket comprobó sus endpoints en Swagger UI y actualizó la sección 4 del readme cuando cambiaba el contrato. Al cerrar la Entrega 3 se comprobó que los 18 endpoints de la sección 4 coinciden con los controladores.*

```
Valida este YAML OpenAPI 3.0.3 (sección 4 de mi readme.md): errores de sintaxis,
$ref rotos, códigos de estado incoherentes con las historias de usuario y campos
que falten respecto al modelo de datos. Comprueba también que el ejemplo de
puntuación (85) coincide con la fórmula de la sección 1.2.
```

**Prompt 3:** Coherencia entre API, modelo y tickets (Claude, chat "Revisión de arquitectura y modelo de datos")

```
Cruza la sección 4 (API), la 3 (modelo de datos) y la 6 (tickets) de mi readme.md y
dame una tabla de incoherencias: campo o endpoint | dónde aparece | dónde falta o
difiere | corrección propuesta.
```

*Resultado:* tabla de **24 incoherencias** ordenadas por impacto. La IA indicó que las tres primeras eran contradicciones introducidas por cambios suyos de la ronda de la 2.1 y separó las filas 1–11, las que un revisor encontraría siguiendo el flujo principal, de las 12–24, ajustes de tipos y de texto. Después de que yo eligiera el alcance, explicó cómo se relacionaban algunas filas entre sí.

*Ajuste humano:* **decidí corregir las filas 1 a 11** y aplicarlas en el readme:

| # | Corrección aplicada |
|---|---|
| 1 | Eliminado el almacenamiento de ficheros: solo se guarda `cv_raw_text` y los PDF se generan bajo demanda. |
| 2 | Límite de uso de la IA con una sola fuente de verdad en la 2.5: Bucket4j a 30/h por usuario y `Semaphore` de 3 llamadas simultáneas. |
| 3 | Errores de IA en la API: `LlmInvalidResponse` (502), `LlmOverloaded` (503 + `Retry-After`) y `LlmTimeout` (504). Actualizados el ticket 2 y los tests. |
| 4 | `POST /offers` responde siempre 201 y guarda la oferta con `FALLIDA` si la IA falla, así que no devuelve códigos 50x. |
| 5 | Nuevo `POST /offers/{offerId}/extraction` para reintentar: borra los requisitos y análisis anteriores en una transacción y devuelve 409 si la oferta ya está COMPLETADA. |
| 6 | Nuevo schema `OfferSummary` para el dashboard, con `latestScore`, `hasBlockingGap` y `applicationStatus`. |
| 7 | `GET /offers/{offerId}/documents?type=` con `DocumentHistoryResponse` (última versión + historial) y schema `DocumentResponse`. |
| 8 | `PUT /documents/{id}` sustituido por `POST /documents/{id}/versions`, que devuelve 201 con un id nuevo; cuerpo `{type}` para generar; 409 si dos ediciones chocan. |
| 9 | `POST /profile/cv` devuelve un borrador (`CvDraftResponse`) sin guardarlo; `PUT /profile` crea (201) o reemplaza (200). Schemas `ProfileRequest` y `ProfileResponse`. |
| 10 | `PATCH /status` solo cambia de columna (mismo estado → 400); el orden dentro de la columna se cambia con `PATCH /applications/{id}`. Ticket 3 actualizado con un hook propio. |
| 11 | Schemas `CreateApplicationRequest` y `UpdateApplicationRequest` (con `version` obligatoria); 409 si la candidatura ya existe. |

Siguiendo el consejo de la IA, marqué como `nullable` los campos de los schemas nuevos que admiten NULL en la base de datos. Al pasar `POST /offers` a devolver ofertas FALLIDA sin título, también marqué como `nullable` `OfferResponse.title` y `ApplicationResponse.offerTitle`, para no abrir una incoherencia nueva. Después apliqué también el diff de la sección 3 (ver sección 3 → Prompt 2), lo que resolvió además las filas 19 (ticket 1 alineado) y 21 (`CHECK (board_position >= 0)`). El resto de las filas 12–24 quedan **pendientes para antes de la Entrega 2**.

**Seguimiento en las Entregas 2 y 3:**

| Filas | Cómo se resolvieron |
|---|---|
| 12, 19, 21, 23 | Ya en la Entrega 1 (títulos nullable, diff de la sección 3, RFC 9457). |
| 13, 14 | JP-BE-05 (PR #8): salario con min/max nullable por separado y `work_mode NOT NULL DEFAULT 'DESCONOCIDO'` (migración V2). |
| 15, 16, 17 | JP-BE-06 (PR #9): `summary NOT NULL`, `CHECK` de 3 a 5 sugerencias (migración V3) y Ejemplo 2 con los 6 resultados. |
| 18, 20, 22 | Cierre de la Entrega 2: regla de `followUpOverdue` alineada con la HU-05, justificación de `profile_skills`/`experiences` y convención del `Clock` en `Europe/Madrid` en la sección 3.2. |
| 24 | **No implementada** (era opcional): `generated_documents` no tiene `llm_model` ni en la migración ni en la entidad. JP-BE-07 no la incluyó. La trazabilidad del modelo queda solo en `match_analyses.llm_model`; anotado como deuda en la sección 8 del readme. |

---

## 5. Historias de Usuario

**Prompt 1:** Generado dentro del Prompt 3 de la sección 1.

**Prompt 2:** Revisión INVEST. *No se ejecutó como prompt aparte: los criterios de aceptación de cada historia se cubrieron con los tests de su ticket (fase 2 de `/ticket`, tests primero).*

```
Actúa como Product Owner. Evalúa cada una de estas 5 historias de usuario con los
criterios INVEST y dime cuál no es lo bastante pequeña o testeable. Para cada
criterio de aceptación en Gherkin, dime si es verificable de forma automática y
con qué tipo de test (unitario, integración o E2E).
```

**Prompt 3:** Casos límite que faltan. *No se ejecutó como prompt aparte: los casos límite salieron de las dudas de la fase 1 de cada ticket y quedaron cubiertos con tests (p. ej. la carrera del primer `PUT /profile` → 409, el conflicto de versiones en el tablero y en los documentos, o la transición prohibida bloqueada en el cliente).*

```
Para la HU-03 (análisis de encaje) y la HU-05 (tablero), propón los escenarios
Gherkin de casos límite que faltan (concurrencia, datos vacíos, errores de red),
priorizando los que podría encontrarse un usuario real en el MVP.
```

---

## 6. Tickets de Trabajo

**Prompt 1:** Generado dentro del Prompt 3 de la sección 1.

**Prompt 2:** Orden de implementación y dependencias. *Se resolvió en la conversación de revisión con Claude (claude.ai): primero la base (JP-BE-01 → JP-DB-01 → JP-BE-02 → JP-BE-03 → JP-FE-01 → JP-OPS-01) y después el flujo de extremo a extremo (JP-BE-05 → JP-BE-06 → JP-FE-03 → JP-FE-04). Ajuste humano: dejar el perfil desde el CV (JP-BE-04 / JP-FE-02) para la Entrega 3 y usar el perfil de los datos de prueba, para centrar la Entrega 2 en el flujo principal.*

```
Con el backlog de la sección 6 de mi readme.md, ordena los tickets para que al final
de la Entrega 2 funcione de extremo a extremo "importar oferta → análisis de encaje",
y al final de la Entrega 3 estén las 5 funcionalidades y la suite de tests. Dame un
plan por semanas con las dependencias entre tickets y señala el camino crítico.
```

**Prompt 3:** Implementación guiada por ticket. **Workflow principal de las Entregas 2 y 3**, aplicado a los 18 tickets del backlog (PRs #1–#21).

Esta plantilla se convirtió en el comando de Claude Code `.claude/commands/ticket.md`. Se lanza con `/ticket <ID>` y tiene 5 fases:
1. **Especificación:** alcance, criterios de aceptación (deducidos de la historia de usuario si el ticket no tiene detalle), dependencias, plan de archivos, tests y dudas o incoherencias del readme. **Espera mi OK.**
2. **Tests primero.**
3. **Implementación.**
4. **Verificación completa** (`mvnw verify`).
5. **Resumen** con las decisiones propias de la IA, los cambios en el readme y una propuesta para este archivo.

Proceso en cada ticket: reviso el plan y el resumen con un segundo modelo (Claude en claude.ai), respondo a **todas** las dudas, exijo la **prueba real** con el perfil `dev` o Docker Compose antes del commit y cierro con una PR hacia `main`. Una conversación por ticket: `/export` al terminar y `/clear` antes del siguiente.

> **Cómo se tomó el criterio humano.** Revisé cada plan de Claude Code con un segundo modelo (Claude en claude.ai), que no escribía el código y actuaba como revisor: muchas de las correcciones de la columna "Mi criterio humano" surgieron de esa revisión. La decisión final de aceptarlas, cambiarlas o descartarlas fue siempre mía, y algunas salieron directamente de mis propias pruebas en el navegador (la CSP que bloqueaba el login y el error de URL que no se mostraba).

| Ticket | PR | Lo que aportó la IA | Mi criterio humano |
|---|---|---|---|
| **JP-BE-01** Scaffolding backend y errores | #1 | Detectó que JP-BE-01 debía ir antes que JP-DB-01 y propuso no adelantar Spring Security. Corrigió ella sola acentos corrompidos por PowerShell 5.1. | Spring Boot 4.1.x por compatibilidad con Spring AI 2.0. Que el 500 se registre en el log con la traza. No registrar cuerpos de petición (CVs). Swagger desactivado en `prod`. Adelantar un compose mínimo con Postgres y leer el `.env` en `dev`. La prueba real destapó el `.env` vacío. |
| **JP-DB-01** Esquema Flyway, seed y entidades | #2 | Detectó que en Boot 4 hace falta `spring-boot-starter-flyway`, porque con `flyway-core` solo no se ejecuta. Encontró dos errores del readme: la cardinalidad del diagrama 3.1 y la redacción de la convención de índices. Restricciones con nombre para que los tests comprueben cuál falla. | Respondí a las 8 dudas, no solo a las 3 que pedía como prioritarias. Pedí un aviso en el seed sobre `ON CONFLICT DO NOTHING`. Revisé y acepté el cambio en un test de JP-BE-01: arrancaba tres perfiles sobre la misma BD y Flyway lo rechazaba con razón. |
| **JP-BE-02** Registro, login y JWT | #3 | Detectó el conflicto JJWT/Jackson 2 con Boot 4/Jackson 3. Previó el límite de 72 bytes de BCrypt y que el login tarde lo mismo exista o no el email. Hallazgo: ignorar el token en rutas públicas para que uno caducado no impida volver a entrar. | Cambié JJWT por Spring Security OAuth2 Resource Server (Nimbus): menos código de seguridad propio. Pedí validar el emisor (`iss`). Anoté el límite de intentos de login para JP-BE-05 y abrí la lista de deuda técnica del readme. |
| **JP-BE-03** `LlmGateway` y modo fake | #4 | Propuso un puerto mínimo en el que cada ticket añade su método, para no inventar DTOs antes de tiempo. Descubrió que la autoconfiguración de Anthropic se activa siempre, y lo resolvió con un `EnvironmentPostProcessor`. Usó `ChatModel` + `BeanOutputConverter` para que el texto del usuario nunca se renderice como plantilla. Añadió un test de arquitectura que impide importar Spring AI fuera de `ai/`. | Sonnet 5.5 como modelo **del producto** (no confundir con el de Claude Code), en vez de Opus, por coste. Prueba real en `dev` sin API key. Anoté como deuda que `LLM_MODE` en el entorno tiene prioridad sobre el perfil `e2e`, con riesgo de gastar API en los tests. |
| **JP-FE-01** Scaffolding frontend y auth | #6 | Eligió TypeScript 6 y ESLint 9 por compatibilidad con typescript-eslint y jsx-a11y. No guardar la ruta de origen al cerrar sesión (privacidad). Un test detectó dos redirecciones compitiendo tras el login. | Proxy de Vite y `/api/v1` relativa en vez de que Vite lea el `.env` de la raíz (igual que nginx, sin CORS y sin exponer secretos). Acepté `localStorage` para el JWT, pero con el riesgo XSS anotado y sus mitigaciones. Probé el login en el navegador: primer flujo navegador → React → Spring → Postgres. |
| **JP-OPS-01** Docker Compose, CI y Dependabot | #7 | Dockerfiles multi-stage sin root, nginx con CSP, stack E2E aislado y smoke test. Un test mal planteado reveló que `--llm.mode` como argumento no reproduce la variable de entorno; corrigió el test, no la protección. | CSP compatible con dnd-kit, healthcheck verificado en la imagen, Dependabot con límites. Al probarlo yo, el login fallaba: un `VITE_API_BASE_URL` absoluto en mi `.env` hacía que la CSP bloqueara la llamada. Pedí que el smoke test lo detecte. |
| **JP-BE-05** Importar ofertas y límites | #8 | Detectó el XSS por URLs `javascript:`, que la BD y Java cuentan caracteres distinto y que el límite por IP no funciona detrás de nginx. | Oferta en PENDIENTE antes de llamar al LLM. Resolver ya la IP real (`forward-headers-strategy: native`) en vez de dejarlo como deuda: la prueba con Docker destapó que nginx permitía falsear la IP. Cerrar las incoherencias 13 y 14 con una migración V2. Borrar los datos de la prueba. |
| **JP-BE-06** Análisis de encaje | #9 | `MatchScoreCalculator` con `BigDecimal` y redondeo exacto. Fake que asigna resultados por posición (los UUID cambian en cada ejecución). Decidió la regla "sin obligatorios → 100 % deseables" y pidió que la revisara. | Incluir las incoherencias 15–17 y JaCoCo (100 % en `analysis.domain`). No enviar al LLM nombre, email ni el texto del CV. Revisé y acepté la regla de negocio que propuso. |
| **JP-FE-03** Dashboard, nueva oferta y detalle | #10 | Contador de caracteres igual que el backend, rangos de color con tests en los valores frontera y detalle básico para cerrar la HU-02. | Probándolo en el navegador encontré que una URL no válida bloqueaba el botón sin mostrar el error; ahora se valida al salir del campo, con tests. |
| **JP-FE-04** Panel de análisis | #11 | Accesible: el resultado lleva texto e icono, no solo color. Conserva el análisis anterior si falla un recálculo. | Placeholder en `/perfil` en vez de una 404 para usuarios sin perfil. Avisar de que recalcular consume una consulta de IA. Capturas del análisis (85 y 42) para el readme. |
| **JP-AI-01** Validación con el LLM real | #14 | `LlmLiveIT` fuera de `verify` y de la CI (`excludedGroups`), con un perfil Maven `-Pllm-live` que lee la clave del `.env` y salta los tests si no la hay. Capturas con la IA real para el readme. | Criterios flexibles (estructura y reglas, nunca texto exacto). Detecté que 3 tests de integración dependían del `LLM_MODE` de mi `.env`: ahora fijan `llm.mode=fake`. Proveedor: API de Anthropic con prepago sin recarga automática, y la clave solo en el `.env`. |
| **JP-BE-08** API de candidaturas | #15 | `ApplicationStatusPolicy`, renumeración de las columnas en el servidor (`BoardColumn`) y bloqueo optimista con `@Version` (409 `version-conflict`). `FieldUpdate<T>` con un `ValueExtractor` para Bean Validation. | Acepté `FieldUpdate<T>` por cómo trata Jackson 3 los `Optional`. Añadí la regla de idioma a `CLAUDE.md` cuando la IA respondió en inglés después de compactar. |
| **JP-FE-06** Tablero kanban | #16 | dnd-kit con ratón y teclado, actualización optimista con vuelta atrás, vista móvil con pestañas y "Mover a…", y panel lateral con React Hook Form + Zod. | Mis respuestas al menú se perdieron y la IA eligió la opción "más fiel a la spec": sin botón "Guardar en el tablero" y con las transiciones prohibidas validadas solo en el backend. Lo corregí: botón incluido y bloqueo en el cliente con *toast*, con el 409 como red de seguridad. *Lazy loading* para quitar el aviso de 500 kB. |
| **JP-QA-01** E2E con Playwright | #17 | Proyecto `e2e/` con Playwright for Java y JUnit 6, `data-offer-id` en las tarjetas para no depender del título y trazas y capturas como artefacto si falla. | Arrastre con `mouse.down` / `move` / `up`, sin esperas fijas, revisar el límite de peticiones y ejecutar la suite varias veces seguidas (6 en verde). Seed de E2E en `db/e2e-seed/`. Modo visible con `slowMo` para el vídeo (ver sección 2.6 → Prompt 3). |
| **JP-BE-04** Perfil desde el CV | #18 | Validación del PDF en memoria (tipo MIME, firma `%PDF`, 10 páginas) sin gastar cupo de IA, `max-tokens` 8.192 solo para el CV y un log por llamada solo con metadatos (tokens, motivo de parada). | La spec era contradictoria (guardar el texto del CV sin perfil): se resolvió con un borrador en memoria. 409 en la carrera del primer guardado en vez de deuda, límite de páginas y protección contra *prompt injection*. Prueba con mi CV real que corrigió la plantilla (ver sección 2.6 → Prompt 4); `temperature` quitada porque la API la rechaza; documentado que el `LLM_MODE` del `.env` gana al perfil `llm-live`. |
| **JP-FE-02** Onboarding de perfil | #19 | Zona de arrastre accesible, validación del fichero en el cliente sin petición, listas editables con `useFieldArray` y confirmación dentro de la página al volver a subir un CV. Chunk `vendor-react` aparte para que ningún chunk pase de 500 kB. | El E2E empieza ya en el registro con un CV ficticio generado en memoria, nunca el CV real. |
| **JP-BE-07** Carta, CV adaptado y PDF | #20 | Versionado con bloqueo optimista sobre `UNIQUE (offer_id, type, version)`, un validador de 200–500 palabras con el prompt pidiendo 250–400 y PDF con CommonMark (`escapeHtml`) y OpenHTMLtoPDF. | La IA no recibe el nombre: el servidor añade la firma, y pedí escapar el Markdown del nombre. PDF sin recursos externos, con tests que cuentan peticiones a un servidor local (0). Revisé los PDF reales generados: carta de 301 palabras, honesta con mi nivel de React. |
| **JP-FE-05** Editor de documentos | #21 | Descarga del PDF con `fetch` + JWT y `blob` (nombre de `Content-Disposition`), pestañas accesibles con el teclado, aviso de conflicto de versiones con "Cargar la última versión" y E2E del flujo completo. | "Exportar PDF" desactivado con cambios sin guardar, en lugar de guardar automáticamente. Sin vista previa HTML, para no tener que sanear el Markdown. GitHub había desactivado Actions: verificación en local con los mismos pasos de `ci.yml`. |

---

## 7. Pull Requests

> Desde la Entrega 2, cada ticket se cierra con una PR de su rama hacia `main` **dentro de mi repositorio**. No son la entrega, que es rama + Typeform. Hay 21 PRs (#1–#11 en la Entrega 2 y #12–#21 en la Entrega 3), listadas en la sección 7 del readme. Desde la #7 la CI tiene que estar en verde antes de fusionar. GitHub desactivó Actions el 11/11/2026, así que la #20 y la #21 se fusionaron después de ejecutar en local los mismos pasos de `ci.yml`.

**Prompt 1:** Descripción de la PR. *En la práctica la sustituye la fase 5 de `/ticket`: su resumen (qué se ha hecho, tests, decisiones y cambios en el readme) es la descripción de cada PR. El título es el mensaje del commit en Conventional Commits.* Ajuste humano: comprobar en cada PR que el destino es mi `main` y no el repositorio original de LIDR (es un fork), porque, al ser un fork, GitHub puede proponer el repositorio original como destino.

```
A partir del diff de esta rama y del ticket [ID] del readme.md, redacta la
descripción de la PR con: contexto y ticket enlazado, cambios principales,
cómo probarlo en local, riesgos y checklist (tests, documentación, migraciones).
Título en formato Conventional Commits.
```

**Prompt 2:** Revisión independiente (subagente). *No se usó un subagente. Este papel lo hizo, en las Entregas 2 y 3, la revisión del plan y del resumen de cada ticket con Claude en el chat del proyecto de claude.ai: un segundo modelo que no escribe el código.*

```
Eres un revisor que no ha participado en el desarrollo. Revisa este diff contra los
criterios de aceptación del ticket [ID]. Busca: criterios sin test, consultas que no
filtran por userId, errores no manejados de LlmGateway, secretos o datos personales
en los logs y código muerto. Devuelve solo los hallazgos, ordenados por gravedad.
```

**Prompt 3:** Actualización de la documentación tras la fusión. *No se ejecutó como prompt aparte: lo sustituyó la regla de `CLAUDE.md` de actualizar el readme en el mismo ticket cuando cambia el contrato, más la fase 5 de `/ticket`, que lista los cambios en el readme. La sección 7 del readme se completó al cerrar cada entrega.*

```
Esta PR ya se ha fusionado. Actualiza las secciones del readme.md afectadas
(modelo de datos, API, instrucciones de instalación) y añade la entrada
correspondiente en la sección 7 con un resumen de la PR. No cambies nada más.
```

**Prompt 4:** Cierre de la entrega: el readme fiel al código y verificación como revisor. **Workflow clave de la Entrega 3.** (Claude Code, Opus 5.5)

Primer prompt literal (revisión del readme):

```
Vamos a cerrar la documentación de la Entrega 3 en la rama docs/cierre-entrega-3. Primero solo readme.md (prompts.md lo haremos después). Revisa readme.md de principio a fin contra el código real y déjalo fiel a lo construido:

1. Estado de implementación (1.2): las 5 funcionalidades operativas (F1 perfil desde CV, F2 importar oferta, F3 análisis de encaje, F4 carta y CV adaptado con PDF, F5 tablero). Quita las notas tipo "se añadirá en la Entrega 3" que ya no apliquen; el vídeo déjalo como "pendiente de enlace" con un marcador VIDEO_URL que sustituiré.
2. Arquitectura, stack y dependencias reales (PDFBox, CommonMark, OpenHTMLtoPDF, Playwright for Java, dnd-kit, etc.); el diagrama y los componentes deben coincidir con los módulos del código.
3. Tickets (sección 6): marca el estado real de cada uno; los no hechos, como fuera de alcance o deuda.
4. Credenciales de prueba y pasos de verificación para el revisor: requisitos, clonar, copiar .env.example a .env, docker compose up --build, usuario demo@jobpilot.dev / Demo1234!, recorrido sugerido de las 5 funcionalidades, cómo usar la IA real (LLM_MODE=real) y que sin clave todo funciona en modo fake. Indica exactamente qué valores de prueba poner en POSTGRES_USER, POSTGRES_PASSWORD y JWT_SECRET (con la longitud mínima que exige el backend), o un comando para generarlo, para que el revisor pueda arrancar sin adivinar.
5. Cómo ejecutar los tests: backend (mvnw verify), frontend (npm test), E2E (stack e2e + mvnw test en e2e/, y modo visible).
6. Nueva subsección sobre la CI: GitHub desactivó Actions en el repo el 11-nov-2026 ("GitHub Actions is currently disabled for this repository"); el soporte de cuenta gratuita no lo revisa. Hasta entonces las 41 ejecuciones pasaron en verde (captura docs/screenshots/github-actions-desactivado.png) y desde entonces se verifica en local con los mismos comandos de ci.yml (captura docs/screenshots/ci-local-verificacion.png). El workflow sigue en el repo y funcionará si se reactiva.
7. Revisa que .env.example tenga todas las variables necesarias y sin valores secretos, y que no haya ningún secreto en el repo.
8. Recuentos de tests actualizados y la deuda técnica consolidada en un solo sitio.

Antes de editar, dame la lista de lo que vas a cambiar sección por sección y espera mi OK.
```

Segundo prompt literal (verificación como revisor), tras responder a sus dudas:

```
Adelante con las ediciones. Al terminar, haz el paso de revisor: sigue al pie de la letra la sección de instalación y verificación del readme desde cero, en una copia limpia del repo (git worktree o clon en una carpeta temporal fuera del repo), con un .env creado solo a partir de .env.example y las instrucciones del readme. Para el stack de dev antes (stop, sin -v) y vuelve a levantarlo al acabar. Dime si algún paso falla o es ambiguo y corrígelo. Borra la carpeta temporal al terminar. No hagas commit.
```

*Resultado:*

- **Referencias inventadas o desfasadas corregidas:** `OwnershipIT` y `cover-letter.st` no existían; JUnit 5 → 6 (Jupiter 6.0.3, también en `CLAUDE.md`); faltaba Bucket4j en el stack.
- **Cambios en el readme:**
  - estado real de los 18 tickets con su PR;
  - guía del revisor con valores de prueba exactos y comandos para generar el `JWT_SECRET` (mínimo 32 bytes);
  - subsección sobre la CI desactivada;
  - nueva sección 8 con toda la deuda técnica y lo fuera de alcance.
- **Comprobaciones:** ningún secreto en el repositorio ni en su historial.
- **Verificación desde un clon limpio de GitHub:**
  - stack con Docker Compose, smoke test y recorrido de las 5 funcionalidades;
  - `mvnw verify`: 437 + 279 tests;
  - frontend: 262 tests, lint y build;
  - E2E: 2/2, también en modo visible.

  Todo en verde. Encontró dos instrucciones ambiguas, que se corrigieron: la empresa sale vacía en modo fake, y había que aclarar qué tarjeta mover en el tablero, porque había dos con el mismo puesto.

*Ajuste humano:*

- Antes del OK respondí a sus 4 dudas: JUnit 6 también en `CLAUDE.md`, valor fijo de ejemplo más comandos para el `JWT_SECRET`, captura de la CI recortada y despliegue fuera de alcance.
- Pedí que la verificación se hiciera en una copia limpia, sin tocar los datos de mi stack de desarrollo.
