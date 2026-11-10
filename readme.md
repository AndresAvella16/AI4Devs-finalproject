## Índice

0. [Ficha del proyecto](#0-ficha-del-proyecto)
1. [Descripción general del producto](#1-descripción-general-del-producto)
2. [Arquitectura del sistema](#2-arquitectura-del-sistema)
3. [Modelo de datos](#3-modelo-de-datos)
4. [Especificación de la API](#4-especificación-de-la-api)
5. [Historias de usuario](#5-historias-de-usuario)
6. [Tickets de trabajo](#6-tickets-de-trabajo)
7. [Pull requests](#7-pull-requests)

---

## 0. Ficha del proyecto

### **0.1. Tu nombre completo:**

Andrés Avellá Navarro

### **0.2. Nombre del proyecto:**

**JobPilot** — Copiloto de búsqueda de empleo con IA

### **0.3. Descripción breve del proyecto:**

JobPilot es una aplicación web que ayuda a las personas en búsqueda activa de empleo a postular **mejor y en menos tiempo**. El usuario sube su CV en PDF y la IA lo convierte en un perfil estructurado. Después pega el texto de una oferta de trabajo y el sistema:

1. extrae los requisitos de la oferta (obligatorios y deseables),
2. calcula una **puntuación de encaje (0–100)** que separa lo que cumple de lo que le falta, con sugerencias concretas,
3. genera una **carta de presentación** y un **CV adaptado** a esa oferta, editables y exportables a PDF,
4. registra la candidatura en un **tablero kanban** (Guardada → Enviada → Entrevista → Oferta / Descartada) con fechas de seguimiento.

Stack: **Java 21 + Spring Boot 4** (API REST), **PostgreSQL**, **React + TypeScript** (SPA) y **Claude** (Anthropic) a través de **Spring AI**.

### **0.4. URL del proyecto:**

> Sin despliegue público por ahora (es opcional en el máster; si se hace, se publicará aquí en la Entrega 3). **Evidencia de funcionamiento en la Entrega 2:** acceso al repositorio con credenciales de prueba y la app completa en local con un solo comando (`docker compose up --build`, ver 1.4), más las capturas de la sección 1.3. En la Entrega 3 se añadirá un **vídeo de 2–3 minutos** del flujo principal.

### 0.5. URL o archivo comprimido del repositorio

https://github.com/AndresAvella16/AI4Devs-finalproject

Ramas de entrega:

| Entrega | Rama |
|---|---|
| Entrega 1 — Documentación técnica | `feature/entrega-1-aan` |
| Entrega 2 — Código funcional | `feature/entrega-2-aan` |
| Entrega 3 — Entrega final | `final-project-aan` |

---

## 1. Descripción general del producto

### **1.1. Objetivo:**

**Problema.** Buscar trabajo es, en sí mismo, un trabajo a tiempo completo. Por cada oferta interesante, el candidato tiene que:

- leerla con detalle y decidir si encaja con su perfil,
- adaptar su CV para destacar lo que la oferta pide,
- redactar una carta de presentación específica,
- recordar en qué estado está cada candidatura y cuándo hacer seguimiento.

Todo esto es manual, repetitivo y acaba disperso entre pestañas del navegador, correos y hojas de cálculo. El resultado: candidaturas genéricas (menor tasa de respuesta), oportunidades olvidadas y mucho tiempo perdido.

**Para quién.** Personas en búsqueda activa de empleo —especialmente perfiles técnicos junior y en transición de carrera— que envían un volumen alto de candidaturas y quieren que cada una esté bien dirigida.

**Qué aporta.**

| Sin JobPilot | Con JobPilot |
|---|---|
| Lees la oferta y "calculas a ojo" si encajas | Puntuación 0–100 explicable, requisito a requisito |
| No sabes qué te falta ni qué destacar | Lista de *gaps* y sugerencias concretas |
| 30–45 min por carta de presentación | Borrador personalizado en segundos, editable |
| Un único CV para todo | CV adaptado a cada oferta, exportable a PDF |
| Excel o memoria para el seguimiento | Kanban con estados, notas y fechas de seguimiento |

**Qué introduce el usuario → qué devuelve el sistema.**

- **Entrada:** su CV en PDF (una vez) y el texto de cada oferta (copiar/pegar).
- **Salida:** perfil estructurado, requisitos de la oferta, análisis de encaje con puntuación y sugerencias, carta y CV adaptados en PDF, y el estado de todas sus candidaturas en un solo lugar.

### **1.2. Características y funcionalidades principales:**

#### Must-have (alcance del MVP)

| # | Funcionalidad | Descripción |
|---|---|---|
| F1 | **Perfil a partir del CV** | El usuario sube su CV en PDF (máx. 5 MB). El backend extrae el texto con Apache PDFBox y el LLM lo estructura en: titular, resumen, años de experiencia, habilidades (con nivel), experiencias, formación e idiomas. El usuario revisa y edita el perfil antes de guardarlo. |
| F2 | **Importar oferta** | El usuario pega el texto de una oferta (y opcionalmente su URL como referencia). El LLM extrae: puesto, empresa, ubicación, modalidad (remoto/híbrido/presencial), rango salarial si aparece y la lista de requisitos clasificados como **obligatorios** o **deseables** y por categoría (técnico, idioma, experiencia, formación, otro). |
| F3 | **Análisis de encaje** | Para cada requisito, el LLM decide si el perfil lo **cumple**, lo cumple **parcialmente** o **no lo cumple**, y aporta la evidencia del CV. La **puntuación se calcula en Java de forma determinista** a partir de esos resultados (ver fórmula abajo), por lo que es reproducible y testeable. Se muestran los *gaps* y 3–5 sugerencias accionables. |
| F4 | **Carta y CV adaptados** | Genera una carta de presentación y una versión del CV orientada a la oferta, apoyándose en el perfil y en el análisis de encaje. Ambos documentos se editan en la app (Markdown) y se exportan a PDF. Se guarda un historial de versiones. |
| F5 | **Tablero de candidaturas** | Kanban con las columnas *Guardada → Enviada → Entrevista → Oferta / Descartada*. Las tarjetas se mueven arrastrando, guardan notas, fecha de envío y fecha de seguimiento, y se marcan cuando el seguimiento está vencido. Cada cambio de estado queda registrado en un historial. |

**Fórmula de la puntuación de encaje (F3).** Cada requisito vale `CUMPLE = 1`, `PARCIAL = 0,5`, `NO_CUMPLE = 0`.

```
puntuación_obligatorios = media de valores de requisitos OBLIGATORIOS
puntuación_deseables    = media de valores de requisitos DESEABLES

si hay obligatorios y deseables: score = round(100 × (0,7 × obligatorios + 0,3 × deseables))
si no hay deseables:             score = round(100 × obligatorios)
si no hay obligatorios:          score = round(100 × deseables)
```

El redondeo es `HALF_UP` (84,5 → 85) y se aplica sobre el valor exacto.

Si algún requisito obligatorio es `NO_CUMPLE`, el análisis muestra además el aviso **"requisito bloqueante"**. Que el LLM *clasifique* y Java *puntúe* es una decisión de diseño deliberada: el número que ve el usuario no depende de la aleatoriedad del modelo y se puede cubrir con tests unitarios.

#### Should-have (deseables, fuera del alcance obligatorio)

| # | Funcionalidad | Descripción |
|---|---|---|
| S1 | Preparación de entrevista | Genera preguntas probables para la entrevista a partir de la oferta y de los *gaps* detectados, con pistas de respuesta basadas en el perfil. |
| S2 | Recordatorios por email | Un job programado (`@Scheduled`) envía un resumen diario con las candidaturas cuyo seguimiento vence ese día. |

#### Fuera de alcance (decisiones explícitas)

- **Scraping de portales de empleo** (LinkedIn, InfoJobs…): sus condiciones de uso lo prohíben y el scraping es frágil. La URL de la oferta solo se guarda como referencia y el contenido se pega a mano.
- **Envío automático de candidaturas**: el usuario sigue enviando su candidatura en el portal correspondiente; JobPilot prepara y registra.

#### Estado de implementación (Entrega 2)

| # | Funcionalidad | Estado | Tickets |
|---|---|---|---|
| — | Registro e inicio de sesión (JWT) | ✅ Operativa | JP-BE-02, JP-FE-01 |
| F2 | Importar oferta | ✅ Operativa de extremo a extremo | JP-BE-05, JP-FE-03 |
| F3 | Análisis de encaje | ✅ Operativa de extremo a extremo | JP-BE-06, JP-FE-04 |
| F1 | Perfil a partir del CV | 🟡 API operativa (JP-BE-04: subida del CV, borrador con IA y guardado del perfil); falta el onboarding del frontend (JP-FE-02), así que la app sigue usando el perfil de los datos de prueba | JP-BE-04, JP-FE-02 |
| F4 | Carta y CV adaptados | ⏳ Entrega 3 | JP-BE-07, JP-FE-05 |
| F5 | Tablero de candidaturas | ✅ Operativa de extremo a extremo: "Guardar en el tablero" en el detalle de oferta (o "Ver en el tablero" si ya está) y kanban en `/tablero` con arrastre por ratón y teclado y pestañas con "Mover a…" en móvil | JP-BE-08, JP-FE-06 |

**Flujo operativo:** navegador → React (nginx) → API Spring Boot → PostgreSQL, con la IA detrás del puerto `LlmGateway`.

> **Sobre la IA en la Entrega 2.** La app funciona en modo `LLM_MODE=fake` (valor por defecto): `FakeLlmGateway` devuelve respuestas fijas (la extracción de la oferta del Ejemplo 1 de la sección 4 y su evaluación), así que el flujo completo es real (se guarda en la BD, Java calcula la puntuación, se muestra en el navegador) salvo la respuesta del modelo. La integración con Claude (`SpringAiLlmGateway`, `LLM_MODE=real`) está implementada y cubierta por tests con respuestas simuladas (`ScriptedChatModel`): prompts, validación, reintento y traducción de errores a 502/503/504. **La prueba contra la API real de Anthropic queda pendiente para la Entrega 3**, junto con la decisión de usar la API de Anthropic o un modelo local con Ollama (que gracias al puerto `LlmGateway` sería solo un adaptador más).

### **1.3. Diseño y experiencia de usuario:**

> Capturas con el usuario de prueba `demo@jobpilot.dev`, en modo `fake`, salvo las de la "Prueba con la IA real" (`LLM_MODE=real`). Las 01–07 son de la Entrega 2 y la del tablero, de JP-FE-06. El vídeo del recorrido completo se añadirá en la Entrega 3.

**Inicio tras el login**

![Inicio tras el login](docs/screenshots/01-inicio-tras-login.png)

**Dashboard de ofertas** — puntuación del último análisis con su color (rojo < 50, ámbar 50–74, verde ≥ 75), aviso de requisito bloqueante y estado de la candidatura.

![Dashboard de ofertas](docs/screenshots/02-dashboard-ofertas.png)

**Detalle de una oferta importada** — datos extraídos por la IA y requisitos agrupados en obligatorios y deseables.

![Detalle de oferta](docs/screenshots/03-detalle-oferta.png)

**Análisis de encaje** — puntuación 85 (Ejemplo 2 de la sección 4): resultado de cada requisito con texto e icono (no solo color), evidencia del perfil y sugerencias.

![Análisis de encaje con 85](docs/screenshots/04-analisis-encaje-85.png)

**Análisis con requisitos bloqueantes** — puntuación 42: aviso de requisitos obligatorios no cumplidos y filas señaladas como "Bloqueante".

![Análisis con requisitos bloqueantes](docs/screenshots/05-analisis-bloqueante-42.png)

**Prueba con la IA real (Claude Sonnet 5.5)**

Oferta real importada con `LLM_MODE=real`: puesto, empresa, modalidad, salario y requisitos extraídos por el modelo.

![Prueba con la IA real: oferta importada](docs/screenshots/06-ia-real-oferta.png)

Análisis de esa oferta: puntuación 32 calculada por `MatchScoreCalculator` a partir de la clasificación del modelo, requisitos bloqueantes y evidencia de cada requisito. JP-AI-01 automatiza esta validación (ver 2.6).

![Prueba con la IA real: análisis de encaje](docs/screenshots/07-ia-real-analisis.png)

**Tablero de candidaturas (JP-FE-06)**: 5 columnas con su contador. Cada tarjeta lleva el puesto, la empresa, la puntuación, la fecha de seguimiento y el aviso "Seguimiento pendiente" si está vencida. Las tarjetas se arrastran con el ratón o, desde el asa ⠿, con el teclado. En Enviada está la oferta de la prueba con la IA real, guardada desde su detalle con "Guardar en el tablero".

![Tablero de candidaturas](docs/screenshots/08-tablero.png)

**Flujo principal de usuario**

```mermaid
flowchart LR
    A[Registro / Login] --> B{¿Tiene perfil?}
    B -- No --> C[Subir CV en PDF]
    C --> D[Revisar y editar perfil extraído]
    D --> E
    B -- Sí --> E[Dashboard de ofertas]
    E --> F[Pegar nueva oferta]
    F --> G[Ver requisitos extraídos]
    G --> H[Análisis de encaje<br/>score + gaps + sugerencias]
    H --> I[Generar carta y CV adaptado]
    I --> J[Editar y exportar PDF]
    J --> K[Guardar en el tablero]
    K --> L[Kanban de candidaturas]
```

**Pantallas del producto** (implementadas en la Entrega 2: login/registro, dashboard, nueva oferta y detalle con el panel de análisis; el tablero, con JP-FE-06; el resto, en la Entrega 3)

| Pantalla | Contenido principal |
|---|---|
| **Login / Registro** | Formulario de email y contraseña, con validación en cliente. |
| **Onboarding de perfil** | Zona de arrastre para el PDF → indicador de procesamiento → formulario editable con lo que ha extraído la IA (habilidades como *chips*, experiencias como lista). |
| **Dashboard** | Lista de ofertas importadas con su puntuación de encaje (badge de color: rojo < 50, ámbar 50–74, verde ≥ 75) y botón "Nueva oferta". |
| **Nueva oferta** | Área de texto grande para pegar la oferta y campo opcional para la URL. |
| **Detalle de oferta** | Cabecera (puesto, empresa, modalidad, salario), requisitos agrupados en *obligatorios* y *deseables*, panel de análisis (puntuación circular, tabla de requisitos con ✅ / ⚠️ / ❌ y su evidencia, sugerencias) y pestañas "Carta" / "CV adaptado" con editor y botón "Exportar PDF". |
| **Tablero** | Kanban de 5 columnas con tarjetas arrastrables (puesto, empresa, puntuación, fecha de seguimiento con aviso si está vencida) y un panel lateral para notas y fechas. |

> **`/perfil` es provisional (JP-FE-04).** Mientras no exista el onboarding, el detalle de oferta lleva a `/perfil` al usuario sin perfil (409 `profile-required` al calcular el encaje). Es una ruta protegida con un *placeholder* ("Crea tu perfil", aviso de que llegará en la próxima versión, sugerencia de probar con `demo@jobpilot.dev` y enlace al dashboard) para que un usuario nuevo no acabe en una 404. JP-FE-02 lo sustituye por el onboarding en la misma ruta.

### **1.4. Instrucciones de instalación:**

> Instrucciones verificadas en la Entrega 2 (Windows 10 con Docker Desktop y WSL 2; la CI las ejecuta en Linux en cada push). La forma recomendada de revisar el proyecto es la **opción A**.

#### Requisitos previos

- **Java 21** (JDK) — el proyecto incluye Maven Wrapper (`./mvnw`), no hace falta instalar Maven.
- **Node.js 20.19+** y npm (lo exige Vite 8).
- **Docker** y **Docker Compose** (para PostgreSQL y para los tests de integración con Testcontainers).
- Una **API key de Anthropic** (opcional: sin ella, la app puede arrancar en modo `fake` con respuestas simuladas del LLM).

#### Variables de entorno (`.env.example`)

```dotenv
# Base de datos
POSTGRES_DB=jobpilot
POSTGRES_USER=
POSTGRES_PASSWORD=
DB_URL=jdbc:postgresql://localhost:5432/jobpilot

# Seguridad
# Mínimo 32 caracteres aleatorios
JWT_SECRET=
JWT_EXPIRATION_MINUTES=60
CORS_ALLOWED_ORIGINS=http://localhost:5173

# IA
# fake | real
LLM_MODE=fake
ANTHROPIC_API_KEY=
# Vacío = claude-sonnet-5-5
ANTHROPIC_MODEL=

# Frontend
# Ruta relativa: en desarrollo la reenvía el proxy de Vite (:5173 → :8080) y en producción, nginx.
# Se aplica al construir la imagen del frontend; npm run dev no lee este .env y usa /api/v1 por defecto.
VITE_API_BASE_URL=/api/v1
```

#### Opción A — Todo con Docker Compose

```bash
git clone https://github.com/AndresAvella16/AI4Devs-finalproject.git
cd AI4Devs-finalproject
cp .env.example .env        # rellenar al menos POSTGRES_USER, POSTGRES_PASSWORD y JWT_SECRET
docker compose up --build
```

Levanta tres servicios, cada uno cuando el anterior está *healthy*: `postgres` → `backend` (perfil `dev`, con migraciones y datos de prueba) → `frontend` (nginx). Dentro de Compose el backend se conecta a `postgres:5432`; el `DB_URL` de `.env` solo se usa al arrancar el backend con `mvnw`.

> **`VITE_API_BASE_URL` se fija al construir la imagen del frontend, no al arrancarla.** Si lo cambias en `.env`, reconstruye con `docker compose up --build`. Con nginx tiene que ser la ruta relativa **`/api/v1`**: una URL absoluta (p. ej. `http://localhost:8080/api/v1`) apunta a otro origen, la CSP (`connect-src 'self'`) bloquea la llamada y el login falla con *"No se ha podido conectar con el servidor"*. `scripts/smoke-test.sh` lo detecta revisando el JS generado.

- Frontend: http://localhost:5173
- API: http://localhost:8080/api/v1
- Documentación interactiva de la API (Swagger UI): http://localhost:8080/swagger-ui.html

#### Opción B — Desarrollo en local

```bash
# 1. Base de datos
docker compose up -d postgres

# 2. Backend (aplica las migraciones Flyway y carga los datos de prueba en el perfil dev)
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 3. Frontend (en otra terminal)
cd frontend
npm ci
npm run dev
```

El frontend llama siempre a la ruta relativa `/api/v1`. En desarrollo, el servidor de Vite (http://localhost:5173) reenvía `/api` al backend en `http://localhost:8080` (`server.proxy` en `frontend/vite.config.ts`), así que el navegador no hace peticiones de otro origen y no interviene CORS. En producción, nginx hace lo mismo.

#### Usar la IA real (`LLM_MODE=real`)

Por defecto la app usa respuestas fijas (`LLM_MODE=fake`). Para que llame a Claude, **cambia el `.env`**: es lo que manda, tanto en Docker Compose como con `mvnw spring-boot:run -Dspring-boot.run.profiles=dev`.

```dotenv
LLM_MODE=real
ANTHROPIC_API_KEY=<tu clave>      # solo en .env, que está en .gitignore
ANTHROPIC_MODEL=                  # vacío = claude-sonnet-5-5
```

Después reinicia el backend: `docker compose up -d backend` (recrea el contenedor con el `.env` nuevo) o vuelve a lanzar `mvnw spring-boot:run`. Cuando termines, vuelve a poner `LLM_MODE=fake`.

- **No uses el perfil de Spring `llm-live` para arrancar la app.** Solo existe en el classpath de test y es para `LlmLiveIT` (`-Pllm-live`, ver 2.6). Aunque se active, el `LLM_MODE=fake` del `.env` gana a su `llm.mode: real` y la app sigue en modo fake sin avisar (comprobado en JP-BE-04).
- **Cómo comprobar que es la IA real:** con el perfil `dev`, cada llamada deja en el log una línea como `LLM 'cv-extraction' respondió en 9613 ms (intento 1/2; tokens entrada=3083 salida=1594 parada=end_turn)`. En modo fake no aparece, y las respuestas son siempre las mismas (la oferta del Ejemplo 1 y el perfil de los datos de prueba) y tardan milisegundos.
- Si `LLM_MODE=real` y falta la clave, la app no arranca (`LlmModeEnvironmentPostProcessor`). Con el perfil `e2e`, `real` no se admite.

#### Migraciones y datos de prueba (seed)

- Las migraciones están en `backend/src/main/resources/db/migration` (Flyway) y se aplican automáticamente al arrancar.
- Con el perfil `dev` se carga `db/seed/R__demo_data.sql`, que crea un usuario de prueba con perfil, tres ofertas analizadas y candidaturas en distintas columnas del tablero.

| Credenciales de prueba | |
|---|---|
| Email | `demo@jobpilot.dev` |
| Contraseña | `Demo1234!` |

> Son credenciales ficticias, solo para revisar el proyecto en local. Nunca se suben secretos reales al repositorio.

#### Ejecutar los tests

```bash
cd backend && ./mvnw verify                # unitarios + integración (Testcontainers)
cd frontend && npm test                    # unitarios de componentes (Vitest)
cd backend && ./mvnw verify -Pllm-live     # solo a mano: validación con el LLM real (JP-AI-01, ver 2.6)

# E2E: stack aislado (proyecto jobpilot-e2e, perfil e2e, LLM_MODE=fake fijo, BD vacía en cada arranque).
# Usa los puertos 5173 y 8080: para antes el stack de desarrollo (docker compose stop backend frontend).
docker compose -f docker-compose.e2e.yml up -d --build --wait
bash scripts/smoke-test.sh                 # smoke test HTTP (nginx, CSP, login)
cd e2e && ./mvnw test                      # Playwright: flujo principal (JP-QA-01). En Windows: .\mvnw.cmd test
cd e2e && ./mvnw test -Dheadless=false -DslowMo=600   # con el navegador visible y más lento (p. ej. para grabar un vídeo)
docker compose -f docker-compose.e2e.yml down
```

Por defecto, Playwright va sin ventana y sin pausas. `-Dheadless=false` abre Chromium y `-DslowMo=<ms>` hace una pausa después de cada acción (clic, escritura, recarga, cada `mouse.move`…; los pasos intermedios de un mismo `mouse.move` no llevan pausa, así que el arrastre se ve fluido). Medido en local: la suite pasa de ~32 s a ~38 s con 600 ms; para un vídeo más pausado, sube el valor (p. ej. 1200). Las esperas máximas crecen con el `slowMo`, así que no fallan por ir despacio. Las mismas opciones se pueden dar con las variables de entorno `E2E_HEADLESS` y `E2E_SLOW_MO`. En PowerShell, cada `-D` va entre comillas: `.\mvnw.cmd test "-Dheadless=false" "-DslowMo=600"`.

La CI (`.github/workflows/ci.yml`) ejecuta lo mismo en cada push y PR (ver 2.4).

---

## 2. Arquitectura del Sistema

### **2.1. Diagrama de arquitectura:**

```mermaid
flowchart LR
    U["Usuario<br/>(navegador)"] --> FE

    subgraph FE_BOX["Frontend · SPA"]
        FE["React + TypeScript + Vite<br/>React Router · TanStack Query · dnd-kit"]
    end

    FE -- "HTTPS · JSON · JWT Bearer" --> API

    subgraph BE["Backend · Spring Boot 4 · Java 21 (monolito modular)"]
        SEC["Spring Security<br/>filtro JWT"] -.-> API
        API["Controladores REST<br/>+ validación DTO"] --> SVC["Servicios de dominio<br/>auth · profile · offer · analysis · document · tracker"]
        SVC --> REPO["Repositorios<br/>Spring Data JPA"]
        SVC --> PDFX["CvTextExtractor<br/>Apache PDFBox"]
        SVC --> PDFG["PdfRenderer<br/>OpenHTMLtoPDF"]
        SVC --> PORT["LlmGateway<br/>(puerto / interfaz)"]
        PORT --> REAL["SpringAiLlmGateway<br/>(adaptador real)"]
        PORT --> FAKE["FakeLlmGateway<br/>(tests · e2e · modo fake)"]
    end

    REPO --> DB[("PostgreSQL 16<br/>migraciones Flyway")]
    REAL -- "HTTPS" --> LLM["API de Anthropic<br/>Claude"]
```

**Patrón elegido: monolito modular en capas + SPA desacoplada.**

- El **backend** es un único despliegue de Spring Boot organizado **por funcionalidad** (*package-by-feature*): `auth`, `profile`, `offer`, `analysis`, `document`, `tracker`. Dentro de cada módulo se respetan las capas clásicas **Controller → Service → Repository**.
- La integración con la IA aplica una idea de la **arquitectura hexagonal** (puertos y adaptadores) solo donde aporta valor: los servicios dependen de la interfaz `LlmGateway`, no de Spring AI ni de Anthropic. Hay dos implementaciones: la real (`SpringAiLlmGateway`) y una simulada (`FakeLlmGateway`) que devuelve respuestas fijas.
- El **frontend** es una SPA independiente que solo se comunica con la API REST.

**Por qué esta arquitectura**

- **Adecuada al tamaño del proyecto:** un MVP con 5 funcionalidades y un solo desarrollador no justifica microservicios. Un monolito se desarrolla, testea y despliega con mucha menos complejidad.
- **Modularidad sin coste operativo:** separar por funcionalidad mantiene el código ordenado y permitiría extraer un módulo (por ejemplo, el de IA) a un servicio aparte si algún día hiciera falta.
- **Testabilidad del componente no determinista:** el puerto `LlmGateway` permite testear toda la lógica que rodea al LLM (parseo, validación, cálculo de la puntuación, orquestación) con respuestas simuladas, tal como recomienda el máster para sistemas con LLM.
- **Stack demandado en el mercado:** Spring Boot + React es una de las combinaciones más pedidas en ofertas de desarrollador Java junior, así que el proyecto también funciona como portfolio.

**Beneficios**

- Un solo artefacto de backend: CI/CD y despliegue sencillos.
- Transacciones ACID locales con PostgreSQL, sin consistencia eventual.
- Cambiar de proveedor de LLM (o de modelo) solo afecta a un adaptador.
- Frontend y backend evolucionan y se despliegan por separado.

**Sacrificios / déficits**

- **Escalado conjunto:** no se puede escalar solo la parte de IA (la más costosa en latencia); toda la app escala junta.
- **Llamadas síncronas al LLM:** la extracción y el análisis tardan varios segundos y se ejecutan dentro de la petición HTTP. Para el MVP es aceptable (la UI muestra un estado de carga), con estas mitigaciones mínimas:
  - **Timeout** de 60 s en el cliente HTTP del LLM, sin reintentos de red. Si se supera, se responde `504` (`LlmTimeout`).
  - **Concurrencia limitada:** un máximo de 3 llamadas simultáneas al LLM (ver 2.5). Si están todas ocupadas, se responde al momento `503` con `Retry-After: 10`; si el proveedor está saturado (HTTP 429, 529 u otro 5xx) o no es accesible, `503` con el `Retry-After` que indique o, si no lo indica, 30 s (`LlmOverloaded`).
  - **Respuesta no válida** tras un reintento: se responde `502` (`LlmInvalidResponse`).
  - Las llamadas al LLM se hacen **fuera de la transacción de base de datos**, para no tener conexiones bloqueadas mientras el modelo responde.
  - A futuro se podría pasar a procesamiento asíncrono con una cola y *polling* o SSE.
- **Dependencia de un servicio externo:** si la API del LLM no está disponible, F1–F4 se degradan con los códigos anteriores y un mensaje claro. F5 (tablero) sigue funcionando. Al importar una oferta (F2), el texto se guarda igualmente con estado `FALLIDA` y se puede reintentar la extracción más tarde.
- **Coste por uso del LLM:** se mitiga con el límite de peticiones por usuario (ver 2.5) y guardando los resultados para no recalcularlos.

**Ficheros:** no se guardan ficheros. Del CV solo se guarda el texto extraído (`cv_raw_text`), y los PDF de la carta y del CV adaptado se generan bajo demanda en cada exportación.

### **2.2. Descripción de componentes principales:**

#### Stack tecnológico (resumen)

| Capa | Tecnología | Motivo de la elección |
|---|---|---|
| Lenguaje backend | **Java 21** (LTS) | Es el lenguaje que domino; *records*, *pattern matching* y *virtual threads*. |
| Framework backend | **Spring Boot 4** + Spring Security + Spring Data JPA | Estándar del mercado Java; Spring Boot 3.5 dejó de tener soporte OSS en junio de 2026. |
| Autenticación | **Spring Security OAuth2 Resource Server** (Nimbus JOSE + JWT) | Emite y valida los JWT HS256 con `JwtEncoder`/`JwtDecoder` de Spring, sin librerías JWT de terceros ni un filtro propio; compatible con el Jackson 3 de Spring Boot 4. |
| Integración IA | **Spring AI 2.0.2** (`spring-ai-starter-model-anthropic`) con **Claude** — por defecto `claude-sonnet-5-5`, `max-tokens` 4096, configurable con `ANTHROPIC_MODEL` | Abstracción oficial de Spring para LLMs; Spring AI 2.0 requiere Spring Boot 4 (la 2.0.2 está compilada contra Boot 4.1.2). Sonnet es más barato y rápido que Opus y suficiente para extracción y encaje. |
| Base de datos | **PostgreSQL 16** + **Flyway** | Relacional, ACID, JSONB para los campos semiestructurados y migraciones versionadas. |
| Frontend | **React 19** + **TypeScript** + **Vite** | SPA moderna y tipada; React es lo más demandado junto a Java en las ofertas. |
| UI / estado | Tailwind CSS 4 · TanStack Query 5 · React Router 7 · React Hook Form 7 + Zod 4 · dnd-kit | Productividad, caché del servidor, rutas protegidas, formularios validados y kanban accesible. |
| Herramientas frontend | Vite 8 · TypeScript 6.0 (`strict`) · ESLint 9 (typescript-eslint, react-hooks, jsx-a11y) · Vitest 5 + React Testing Library + MSW 2 | TypeScript 6.0 y ESLint 9 porque typescript-eslint aún no admite TypeScript 7 y jsx-a11y aún no admite ESLint 10. |
| Documentos | Apache PDFBox (leer CV) · CommonMark + OpenHTMLtoPDF (generar PDF) | Librerías Java maduras y sin servicios externos. |
| Tests | JUnit 5 · Mockito · AssertJ · Testcontainers · MockMvc · Vitest · React Testing Library · MSW · Playwright for Java | Cubren unitarios, integración y E2E con el mismo lenguaje en el backend y el E2E. |
| Infraestructura | Docker · Docker Compose · GitHub Actions · Render (opcional) | Puesta en marcha en local con un comando, CI en cada push y despliegue sencillo. |


| Componente | Tecnología | Responsabilidad |
|---|---|---|
| **SPA** | React 19, TypeScript, Vite | Interfaz de usuario. Rutas protegidas con React Router. |
| Estado de servidor | TanStack Query | Caché, reintentos y estados de carga/error de las llamadas a la API. |
| Formularios | React Hook Form + Zod | Formularios de perfil, oferta y login con validación tipada. |
| Kanban | dnd-kit | Arrastrar y soltar tarjetas entre columnas, accesible por teclado. |
| Estilos | Tailwind CSS | Estilos utilitarios y diseño responsive. |
| **API REST** | Spring Boot 4 (Spring Web), Java 21 | Endpoints `/api/v1/**`, DTOs como `record`, validación con Bean Validation y errores en formato RFC 9457 (`ProblemDetail`). |
| Documentación API | springdoc-openapi | Genera el contrato OpenAPI y Swagger UI a partir del código. |
| **Seguridad** | Spring Security, OAuth2 Resource Server (Nimbus), BCrypt | Registro/login, emisión (`JwtEncoder`) y validación (`JwtDecoder`) de JWT, API sin estado, CORS. |
| **Módulo `profile`** | PDFBox + `LlmGateway` | Extrae el texto del CV y lo estructura en un perfil editable. |
| **Módulo `offer`** | `LlmGateway` | Extrae los requisitos de la oferta pegada. |
| **Módulo `analysis`** | `LlmGateway` + `MatchScoreCalculator` | El LLM clasifica cada requisito; `MatchScoreCalculator` (Java puro, sin dependencias) calcula la puntuación. |
| **Módulo `document`** | `LlmGateway`, CommonMark, OpenHTMLtoPDF | Genera la carta y el CV adaptado en Markdown, versiona los cambios y exporta a PDF. |
| **Módulo `tracker`** | Spring Data JPA | Candidaturas, reglas de transición de estado (`ApplicationStatusPolicy`) e historial de cambios. |
| **Módulo `ai`** | Spring AI (`ChatModel` + `BeanOutputConverter`) | Adaptador al modelo Claude. Plantillas de prompt versionadas en `resources/prompts/*.st` (con las reglas comunes en `_system-base.st`), conversión de la respuesta a `record` Java, validación del esquema y un reintento si la respuesta no es válida. `LLM_MODE` (`fake` por defecto, o `real`) elige el adaptador; en `real`, sin `ANTHROPIC_API_KEY` la app no arranca. Con el perfil `e2e`, `real` no se admite: la app no arranca aunque haya API key. Cada llamada puede cambiar `maxTokens` sobre la configuración general (`spring.ai.anthropic.chat.*`, 4.096 tokens): la extracción del CV usa 8.192, para que un CV largo no se corte; ofertas y análisis usan la general. No se envía `temperature`: `claude-sonnet-5-5` la rechaza con un 400 (*"`temperature` is deprecated for this model"*, comprobado en JP-BE-04). |
| **Persistencia** | PostgreSQL 16, Spring Data JPA (Hibernate), Flyway | Modelo relacional, migraciones versionadas y datos de prueba. |
| **Tests** | JUnit 5, Mockito, AssertJ, Testcontainers, MockMvc, Vitest, React Testing Library, MSW, Playwright for Java | Ver sección 2.6. |
| **CI** | GitHub Actions | Build, tests y lint en cada push y PR. |

### **2.3. Descripción de alto nivel del proyecto y estructura de ficheros**

Monorepo con tres proyectos: `backend` (Maven), `frontend` (npm) y `e2e` (Maven + Playwright). El backend sigue *package-by-feature* con capas dentro de cada módulo; el frontend se organiza en `features/` con la misma división funcional, de modo que cada historia de usuario se localiza fácilmente en ambos lados.

```
AI4Devs-finalproject/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/jobpilot/
│       │   │   ├── JobPilotApplication.java
│       │   │   ├── auth/            # registro, login, JWT (controller, service, dto)
│       │   │   ├── profile/         # F1: perfil, habilidades, experiencias, CvTextExtractor
│       │   │   ├── offer/           # F2: ofertas y requisitos
│       │   │   ├── analysis/        # F3: análisis de encaje, MatchScoreCalculator
│       │   │   ├── document/        # F4: carta / CV adaptado, PdfRenderer
│       │   │   ├── tracker/         # F5: candidaturas, ApplicationStatusPolicy, historial
│       │   │   ├── ai/              # LlmGateway (puerto), SpringAiLlmGateway, FakeLlmGateway
│       │   │   └── shared/          # config, seguridad, manejo global de errores, auditoría
│       │   └── resources/
│       │       ├── application.yml  # + application-dev.yml, application-e2e.yml, application-prod.yml
│       │       ├── db/migration/    # V1__init_schema.sql, V2__...
│       │       ├── db/seed/         # R__demo_data.sql (solo perfil dev)
│       │       ├── prompts/         # _system-base.st, cv-extraction.st, offer-extraction.st, match.st, cover-letter.st
│       │       └── fake-llm/        # respuestas fijas del modo fake (JSON), usadas en dev, tests y E2E
│       └── test/
│           ├── java/com/jobpilot/   # unitarios (*Test) e integración (*IT)
│           └── resources/fixtures/  # CVs de ejemplo y variantes de respuesta solo para tests (JSON roto, campos que faltan…)
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
│       ├── app/                     # router, providers, layout
│       ├── api/                     # cliente HTTP (fetch + interceptor JWT) y tipos
│       ├── features/
│       │   ├── auth/
│       │   ├── profile/
│       │   ├── offers/
│       │   ├── analysis/
│       │   ├── documents/
│       │   └── tracker/             # tablero kanban
│       ├── components/              # componentes UI reutilizables
│       └── test/                    # setup de Vitest y handlers de MSW
├── e2e/                             # tests E2E con Playwright for Java
├── docs/                            # diagramas y capturas
├── scripts/smoke-test.sh            # smoke test HTTP del stack de Compose (CI, job e2e)
├── .github/
│   ├── workflows/ci.yml
│   └── dependabot.yml
├── docker-compose.yml               # postgres + backend (Dockerfile en backend/) + frontend (Dockerfile y nginx.conf en frontend/)
├── docker-compose.e2e.yml
├── .env.example
├── CLAUDE.md                        # contexto y reglas para el asistente de IA
├── readme.md
└── prompts.md
```

### **2.4. Infraestructura y despliegue**

**Entorno local y de revisión (obligatorio):** Docker Compose con tres servicios.

```mermaid
flowchart TB
    subgraph DEV["Máquina local · docker compose"]
        FEC["frontend<br/>Nginx sirviendo el build de Vite<br/>:5173"]
        BEC["backend<br/>eclipse-temurin:21-jre<br/>:8080"]
        PGC[("postgres:16<br/>:5432<br/>volumen pgdata")]
        FEC -->|/api/v1| BEC
        BEC --> PGC
    end
    BEC -. "LLM_MODE=real" .-> ANT["API de Anthropic"]
```

**Pipeline de CI (GitHub Actions)**

```mermaid
flowchart LR
    P["push / PR"] --> B1["backend:<br/>./mvnw verify<br/>unit + IT (Testcontainers)"]
    P --> F1["frontend:<br/>npm ci · lint · test · build"]
    B1 --> E["e2e:<br/>docker-compose.e2e.yml (LLM_MODE=fake)<br/>smoke test · Playwright (JP-QA-01)"]
    F1 --> E
    E --> D{"¿rama main?"}
    D -- sí --> DEP["Deploy (opcional)<br/>Render: deploy hook"]
```

- `backend` y `frontend` se ejecutan en paralelo; `e2e` solo si los dos pasan. El deploy aún no existe (Entrega 3).
- El job `e2e` genera en cada ejecución credenciales de un solo uso (`POSTGRES_USER`, `POSTGRES_PASSWORD`, `JWT_SECRET`), comprueba que `docker-compose.e2e.yml` fija `LLM_MODE=fake` aunque el entorno diga `real`, levanta el stack con `--wait`, ejecuta `scripts/smoke-test.sh` y después la suite de Playwright (`e2e/`, JP-QA-01): instala Chromium con sus dependencias (`./mvnw exec:java -Dexec.args="install --with-deps chromium"`) y ejecuta `./mvnw test`. Si falla, sube como artefacto las trazas de Playwright y las capturas (`e2e/target/e2e-artifacts/`).
- La CI no usa secretos del repositorio ni llama a la API de Anthropic.
- Imágenes: `backend/Dockerfile` (Maven → `eclipse-temurin:21-jre`, usuario sin privilegios, *healthcheck* con `curl` contra `/actuator/health`; si la imagen base dejara de traer `curl`, se instala al construir) y `frontend/Dockerfile` (Node 22 → `nginx:1.28-alpine`; `nginx.conf` sirve la SPA, reenvía `/api/` al backend y añade las cabeceras de seguridad de 2.5).

**Proceso de despliegue (opcional, previsto para la Entrega 3)**

1. Cada push o PR ejecuta el pipeline de CI. Si un test falla, no se despliega.
2. En `main`, el backend se construye como imagen Docker (build multi-stage: Maven → JRE 21) y se despliega en **Render** como *Web Service*, conectado a un **PostgreSQL gestionado** de Render.
3. El frontend se despliega con su imagen de nginx (`frontend/Dockerfile`), igual que en local: llama a la ruta relativa `/api/v1` y nginx la reenvía al backend, así que no hay CORS ni hace falta cambiar `VITE_API_BASE_URL`. *A revisar al desplegar* (Entrega 3): el `proxy_pass` de `nginx.conf` apunta a `backend:8080`, el nombre del servicio en Compose, y en Render habrá que apuntarlo a la URL interna del backend.
4. Las migraciones de Flyway se aplican solas al arrancar el backend.
5. Los secretos (`JWT_SECRET`, `ANTHROPIC_API_KEY`, credenciales de la BD) se configuran como variables de entorno en Render y nunca se versionan.
6. El endpoint `/actuator/health` se usa como *health check* del servicio.

### **2.5. Seguridad**

| Práctica | Implementación |
|---|---|
| **Contraseñas** | Hash con BCrypt (coste 12). Nunca se devuelven ni se registran en los logs. Política mínima: 8 caracteres, con letras y números. |
| **Autenticación sin estado** | JWT firmado con HS256, caducidad de 60 min y secreto de al menos 256 bits leído de una variable de entorno. Se envía en la cabecera `Authorization: Bearer …`. |
| **Autorización por propietario** | Todas las consultas filtran por el `userId` del token (`findByIdAndUserId`). Si un recurso pertenece a otro usuario se responde `404` (no `403`) para no revelar que existe. Hay tests de integración específicos para esto. |
| **Validación de entrada** | DTOs con Bean Validation (`@NotBlank`, `@Size`, `@Email`…). Ejemplo: el texto de una oferta debe tener entre 200 y 20.000 caracteres. El frontend valida también con Zod, pero la validación del backend es la que manda. |
| **Subida de ficheros** | Solo se aceptan PDF de hasta 5 MB y 10 páginas; se comprueba el tipo MIME y la firma `%PDF`. El fichero no se guarda en disco (ni siquiera en temporales: multipart y PDFBox trabajan en memoria): se extrae su texto y se descarta. Todo se comprueba antes de llamar a la IA, así que un fichero rechazado no gasta cupo. El texto extraído (máx. 50.000 caracteres) espera en memoria 30 minutos a que el usuario confirme el perfil con `PUT /profile`, y solo entonces se guarda en `cv_raw_text` (JP-BE-04). |
| **Mitigación de *prompt injection*** | El texto del CV y de la oferta se envía delimitado y marcado como *datos* en el prompt de sistema. El modelo no tiene herramientas ni acceso a nada. Su respuesta se valida contra un esquema estricto antes de usarla, y la puntuación la calcula Java, no el modelo. |
| **Sin SSRF** | El backend nunca descarga la URL que introduce el usuario; solo la guarda como texto. |
| **Límite de peticiones** | Única fuente de verdad de los límites de uso (IA y login). Por usuario: 30 peticiones por hora a los endpoints que llaman al LLM, con Bucket4j; si se supera, `429` con `Retry-After`. Global: un `Semaphore` permite como máximo 3 llamadas simultáneas al LLM; si no hay hueco, `503` con `Retry-After`. Esto protege del abuso, controla el coste y evita agotar los hilos del servidor. **Login:** máximo 10 intentos por minuto por IP + email en `POST /auth/login` (cuentan todos, correctos o no, y se comprueba antes de la contraseña), también con Bucket4j; si se supera, `429` con `Retry-After`, para frenar los ataques de fuerza bruta. Los cubos se recargan de forma progresiva (30/h = una petición cada 2 min), las peticiones que no pasan la validación (400) o que no llegan a la IA (404, 409) no gastan cupo, y el 429 es `rate-limit-exceeded`. La IP del cliente la da Tomcat (`server.forward-headers-strategy: native`): solo acepta `X-Forwarded-For` si la petición llega desde un proxy interno (nginx en la red de Docker), y nginx sustituye esa cabecera en lugar de añadir a la que mande el cliente (JP-BE-05). |
| **CORS** | Solo se permite el origen del frontend (`CORS_ALLOWED_ORIGINS`). |
| **Cabeceras de seguridad (nginx)** | `frontend/nginx.conf` envía `Content-Security-Policy`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, `X-Frame-Options: DENY` y `Permissions-Policy`. La CSP es `default-src 'self'` con **`script-src 'self'` estricto** (sin scripts en línea ni `eval`): es la mitigación principal frente a un XSS que intentara leer el JWT de `localStorage`. **`style-src` permite `'unsafe-inline'`** porque dnd-kit (tablero, JP-FE-06) mueve las tarjetas con estilos en línea (`style="transform: …"`); los estilos en línea no ejecutan código, y el riesgo que queda (inyección de CSS) es mucho menor. Además: `connect-src 'self'`, `object-src 'none'`, `base-uri 'self'`, `form-action 'self'` y `frame-ancestors 'none'`. |
| **Gestión de secretos** | Variables de entorno; `.env` está en `.gitignore` y `.env.example` va con los valores vacíos. |
| **Errores seguros** | Manejador global (`@RestControllerAdvice`) que devuelve `ProblemDetail` sin trazas internas. |
| **Datos personales** | El CV contiene datos personales: solo se guarda lo necesario (texto extraído y perfil), se borra en cascada al eliminar la cuenta y nunca se envía al LLM nada que no haga falta para la tarea. |
| **Dependencias** | Dependabot (`.github/dependabot.yml`) para Maven, npm, GitHub Actions y las imágenes Docker: revisión semanal, un PR agrupado por ecosistema y como mucho 3 PRs abiertos por ecosistema. |

### **2.6. Tests**

La estrategia sigue la pirámide de tests y la recomendación del máster para sistemas con LLM: **los componentes que rodean al modelo se prueban de forma determinista con respuestas simuladas**, y las salidas reales del modelo se validan por estructura y reglas, no comparando el texto exacto.

**Estado de la suite** (recuentos actualizados en JP-QA-01)

| Capa | Herramientas | Tests | Notas |
|---|---|---|---|
| Backend: unitarios | JUnit 5, AssertJ, Mockito | 380 | Incluye `MatchScoreCalculatorTest` (13 casos parametrizados), validadores de las respuestas del LLM, `StructuredLlmCall`, traducción de errores, límites de peticiones y el perfil (`CvTextExtractorTest` con PDFs generados en el test, `CvDraftStoreTest`, `CvExtractionParserTest`, `ProfileServiceTest`) y `AnthropicRequestOptionsTest`, que construye la petición con el `AnthropicChatModel` real (sin red) para comprobar que las opciones por llamada llegan a Anthropic. |
| Backend: integración | Spring Boot Test, Testcontainers (PostgreSQL 16), MockMvc | 255 | Migraciones y restricciones de la BD, auth/JWT, perfil, ofertas, análisis, propiedad de los datos (404 con recursos de otro usuario) y mapeo de errores de IA. |
| Cobertura | JaCoCo | 100 % de líneas en `analysis.domain` | Regla `check` (≥ 90 %) en `mvnw verify`, como pide la definición de hecho de JP-BE-06. |
| Frontend | Vitest, React Testing Library, MSW | 195 | Cliente API, sesión, formularios, dashboard, detalle, panel de análisis y tablero: `canTransition`, helpers del tablero, schema del panel lateral, "Guardar en el tablero" (`SaveToBoard.test.tsx`, también con un 409) y `KanbanBoard.test.tsx`. Este último mueve tarjetas con el `KeyboardSensor` real de dnd-kit sobre un layout simulado, comprueba que una transición prohibida se bloquea sin llamar al backend y falla si hay errores o avisos en la consola. |
| Stack completo | `scripts/smoke-test.sh` contra `docker-compose.e2e.yml` | Smoke test HTTP | Health, nginx (fallback de la SPA, CSP, proxy `/api`), login y comprobación de que el JS no lleva una URL absoluta de la API. |
| E2E (JP-QA-01) | Playwright for Java + JUnit (`e2e/`) contra `docker-compose.e2e.yml` | 2 | Flujo principal con lo que hay implementado (ver abajo) y registro. ~26 s en local, con el stack ya levantado (6 ejecuciones seguidas sobre el mismo stack, todas en verde). |
| CI | GitHub Actions | 3 jobs | `backend` y `frontend` en paralelo; `e2e` (smoke test + Playwright) si los dos pasan. |

Además, en cada ticket se hizo una **prueba manual con el stack real** (perfil `dev` o Docker Compose). Detectó problemas que los tests automáticos no veían: un `.env` mal formado, una URL de la API absoluta que la CSP bloqueaba, la IP del cliente falsificable a través de nginx y un error de validación de la URL que no se mostraba.

**Previsto para la Entrega 3:** completar el E2E con la subida del CV y los documentos. Los tests del perfil en el backend (`CvTextExtractorTest`, `ProfileControllerIT`) ya están hechos (JP-BE-04). El E2E con Playwright del flujo principal ya está hecho (JP-QA-01), con los pasos que existen hoy. Los tests del tablero en el backend (`ApplicationStatusPolicyTest`, `BoardColumnTest`, `ApplicationServiceTest`, `ApplicationControllerIT`) ya están hechos (JP-BE-08). La validación con el LLM real (`@Tag("llm-live")`) ya está hecha (JP-AI-01). La lista de abajo es el diseño completo de la suite.

**Unitarios (JUnit 5 + Mockito + AssertJ / Vitest + React Testing Library)**

- `MatchScoreCalculatorTest`: tests parametrizados de la fórmula. Todo cumplido → 100; sin deseables → solo cuentan los obligatorios; mezcla de CUMPLE/PARCIAL/NO_CUMPLE → valor esperado; requisito obligatorio sin cumplir → aviso de bloqueante.
- `ApplicationStatusPolicyTest`: transiciones permitidas (`GUARDADA→ENVIADA`, `ENTREVISTA→OFERTA`, `OFERTA→DESCARTADA`, cualquier estado → `DESCARTADA`, `DESCARTADA→GUARDADA`) y prohibidas (retrocesos como `OFERTA→GUARDADA` y saltos como `GUARDADA→OFERTA`).
- `OfferExtractionParserTest`: parseo de respuestas simuladas del LLM (válida, con campos que faltan, con JSON roto → reintento → `LlmInvalidResponseException`).
- `CvTextExtractorTest`: extracción de texto de PDFs de prueba; un PDF corrupto o escaneado sin texto devuelve un error controlado.
- Frontend: `MatchScoreBadge` (colores por rango), formulario de oferta (validación de longitud) y `KanbanBoard` (renderiza columnas y tarjetas, y llama a la mutación al soltar una tarjeta).

**Integración (Spring Boot Test + Testcontainers + MockMvc)**

- Las migraciones de Flyway se aplican sobre un PostgreSQL 16 real y se validan las restricciones (unicidad, `CHECK`, borrado en cascada).
- `OfferControllerIT`: `POST /offers` con `FakeLlmGateway` → 201, oferta y requisitos guardados; el LLM falla → 201 con `extractionStatus: FALLIDA` y el texto guardado; `POST /offers/{id}/extraction` sobre una oferta FALLIDA → 200 con requisitos nuevos, y sobre una COMPLETADA → 409; texto demasiado corto → 400; sin token → 401.
- `LlmErrorMappingIT`: timeout del LLM → 504; concurrencia agotada → 503 con `Retry-After`; respuesta no válida dos veces → 502.
- `ProfileControllerIT` (JP-BE-04): subir un CV → 200 con el borrador y sin crear el perfil; no es PDF → 415, más de 5 MB, vacío o más de 10 páginas → 400, escaneado → 422, sin llamar a la IA ni gastar cupo; fallos de la IA → 502/503/504; un CV con instrucciones incrustadas → borrador válido; `PUT /profile` → 201 y después 200 (conserva el id, el CV y los análisis), con y sin borrador; validaciones → 400; carrera del primer `PUT` → 409. `CvDraftExpiryIT`: con el borrador caducado el perfil se guarda sin CV.
- `AnalysisControllerIT`: análisis de punta a punta API → servicio → BD con una puntuación esperada exacta; usuario sin perfil → 409.
- `OwnershipIT`: el usuario A no puede leer, analizar ni mover recursos del usuario B (→ 404).
- `ApplicationControllerIT`: crear candidatura → 201 en GUARDADA con historial `from = NULL`, y crearla de nuevo para la misma oferta → 409; cambio de estado válido → 200 y se registra en el historial; mismo estado en `PATCH /status` → 400; transición no permitida → 409; `PATCH /applications/{id}` con una `version` antigua → 409.

**E2E (Playwright for Java contra `docker-compose.e2e.yml` con `LLM_MODE=fake`)**

- **Flujo principal:** registro → subir el CV de ejemplo → revisar el perfil → pegar una oferta de ejemplo → comprobar que se ven los requisitos y la puntuación esperada → generar la carta → exportar el PDF (se verifica la descarga) → guardar en el tablero → arrastrar la tarjeta a "Enviada" → recargar la página y comprobar que se ha guardado.
- **Implementado en JP-QA-01** (`MainFlowE2ETest`): login con el usuario del seed de E2E → pegar la oferta → 3 requisitos obligatorios y 3 deseables → calcular el encaje (85, con 6 filas) → "Guardar en el tablero" → la tarjeta aparece en "Guardada" → arrastrarla con el ratón a "Enviada" (se espera la respuesta del `PATCH /status`) → recargar → sigue en "Enviada". `RegistrationE2ETest` prueba el registro de una cuenta nueva. **Pendiente**, marcado con `TODO` en el test: subir el CV y revisar el perfil (JP-BE-04 / JP-FE-02) y generar la carta y exportar el PDF (JP-BE-07 / JP-FE-05).
- **Seed de E2E** (`db/e2e-seed/R__e2e_data.sql`, solo perfil `e2e`): usuario `e2e@jobpilot.test` / `Demo1234!` con perfil y sin ofertas, porque un usuario recién registrado no puede analizar ofertas hasta que exista la subida del CV (`409 profile-required`). Está fuera de `db/seed/` porque Flyway busca también en las subcarpetas y el perfil `dev` lo cargaría. Lo cubre `E2eSeedIT`, y `SeedDataIT` comprueba que `dev` no lo carga.
- **Repetible sobre el mismo stack:** cada ejecución importa su propia oferta y localiza su tarjeta por `data-offer-id`; el registro usa un email aleatorio. El perfil `e2e` sube el cupo de IA por usuario a 10.000/h: cada ejecución gasta 2 llamadas del usuario del seed y con 30/h la 16.ª ejecución en una hora daría `429`. El límite de login (10/min) no se toca: la suite hace un login por ejecución y tarda ~26 s.
- **Sin esperas fijas:** solo el auto-wait de Playwright, `assertThat(...)` y `waitForResponse`. El arrastre no usa `dragTo()`: el `PointerSensor` de dnd-kit no empieza hasta mover el puntero 5 px con el botón pulsado, así que se hace `mouse.down` → `mouse.move` en varios pasos → `mouse.up`, desde el asa ⠿ de la tarjeta.
- **Opciones** (propiedad `-D` de Maven o variable de entorno): `frontendUrl` / `FRONTEND_URL` (por defecto `http://localhost:5173`), `headless` / `E2E_HEADLESS` (`true`) y `slowMo` / `E2E_SLOW_MO` (ms, `0`). Ver 1.4. Si un test falla, su traza (`npx playwright show-trace <zip>`) y una captura quedan en `e2e/target/e2e-artifacts/`.

**Validación con el LLM real (manual / *smoke*, fuera de la CI)**

- `LlmLiveIT` (`@Tag("llm-live")`, JP-AI-01) arranca la aplicación con `llm.mode=real` y llama al `SpringAiLlmGateway` real. Hace la extracción sobre 3 ofertas ficticias cortas (`src/test/resources/fixtures/llm-live/`) y evalúa el encaje de una de ellas con un perfil de ejemplo. Los criterios son flexibles y nunca se compara texto exacto:
  - las respuestas pasan Bean Validation y los validadores existentes (`OfferExtractionValidator`, `MatchEvaluationValidator`);
  - cada oferta tiene al menos un requisito obligatorio y una modalidad válida;
  - cada requisito tiene exactamente un resultado, y las evidencias de `CUMPLE` y `PARCIAL` no están vacías;
  - hay entre 3 y 5 sugerencias;
  - la puntuación de `MatchScoreCalculator` está entre 0 y 100.
- **Coste:** 4 llamadas por ejecución (3 extracciones y 1 encaje que reutiliza una de ellas), más un posible reintento por respuesta no válida.
- **Excluido de `mvnw verify` y de la CI** (`excludedGroups` en Surefire y Failsafe). Se ejecuta a mano:

  ```powershell
  cd backend; .\mvnw.cmd verify -Pllm-live
  ```

  El perfil Maven `llm-live` solo ejecuta ese grupo, y salta los unitarios y JaCoCo. Necesita Docker para el PostgreSQL de Testcontainers. La clave se lee de `ANTHROPIC_API_KEY`: de una variable de entorno o, si no está, del `.env` de la raíz (perfil de Spring `llm-live`, con `spring.config.import`, como el perfil `dev`). Así no hay que escribirla en la terminal ni queda en el historial. El test fuerza `llm.mode=real` (`@SpringBootTest(properties = ...)`), así que funciona aunque el `.env` diga `LLM_MODE=fake`; para usar la IA real en la app, ver "Usar la IA real" en 1.4. Si no hay clave, los tests aparecen como saltados y el build no falla. El modelo es el de `ANTHROPIC_MODEL` o, si está vacío, `claude-sonnet-5-5`.

**Deuda técnica conocida**

- **`save()` de Spring Data hace `merge` en las entidades nuevas.** Los UUID se asignan en el constructor, así que Spring Data no distingue una entidad nueva de una existente y ejecuta un `SELECT` antes de cada `INSERT`. Funciona, pero cuesta una consulta de más. Se resolverá con una clase base que implemente `Persistable` (JP-DB-01).
- **JWT en `localStorage` (JP-FE-01).** La sesión (token, `expiresAt` y datos del usuario) se guarda en `localStorage` para que aguante las recargas y sirva en todas las pestañas, pero un XSS podría leer el token. Mitigaciones actuales: el token caduca a los 60 min (el frontend lo descarta al llegar a `expiresAt` y ante cualquier `401`), React escapa el contenido que pinta, no se usa `dangerouslySetInnerHTML` y nginx envía una CSP con `script-src 'self'` estricto (JP-OPS-01, ver 2.5). Pendiente: sanear el Markdown generado por la IA al mostrarlo (JP-FE-05).
- **La sesión no se sincroniza entre pestañas (JP-FE-01).** El frontend no escucha el evento `storage`: si se cierra la sesión en una pestaña, las demás siguen mostrando la sesión hasta que su siguiente llamada a la API recibe un `401` (o hasta que llega `expiresAt`), y entonces vuelven a `/login`.
- **Aviso moderado de `npm audit` en `minimatch` (JP-FE-01).** Llega como dependencia de typescript-eslint, que solo se usa al pasar el lint: no forma parte del bundle que se sirve al navegador. `npm audit fix --force` lo resolvería rebajando typescript-eslint a una versión antigua, así que se deja hasta que typescript-eslint actualice la dependencia.
- **Límites de peticiones en memoria (JP-BE-05).** Los cubos de Bucket4j viven en la memoria del backend: valen para una sola instancia (el despliegue previsto) y se reinician al reiniciarlo. Con varias instancias habría que pasarlos a un almacén compartido (Bucket4j tiene integración con Redis o PostgreSQL). Para acotar la memoria, al llegar a `jobpilot.rate-limit.max-keys` claves se olvidan las que tienen el cupo lleno.
- **Borrador del CV en memoria (JP-BE-04).** El texto de un CV subido con `POST /profile/cv` espera en la memoria del backend (`CvDraftStore`, 30 min, como mucho `jobpilot.cv-draft.max-entries` borradores) hasta el `PUT /profile`. Como los cubos de Bucket4j, vale para una sola instancia y se pierde al reiniciar; si se pierde o caduca, el perfil se guarda igualmente, sin `cv_raw_text` ni `cv_file_name`. Con varias instancias habría que pasarlo a una tabla o a Redis.
- **Proxies de confianza para `X-Forwarded-For` (JP-BE-05).** Tomcat da por internas las IPs privadas (10/8, 172.16/12, 192.168/16, 127/8…). En local, el puerto 8080 del backend también está publicado y las peticiones del host llegan desde la puerta de enlace de Docker (172.x), así que quien llame directamente a `:8080` puede falsear su IP. Por nginx (`:5173`) no, porque nginx sustituye la cabecera. Al desplegar en Render hay que ajustar `server.tomcat.remoteip.internal-proxies` al proxy de la plataforma y no publicar el backend.
- **Aviso de Mockito al ejecutar los tests** (*"A Java agent has been loaded dynamically"*). En Java 21 es inofensivo; una versión futura del JDK lo bloqueará. Se resolverá configurando Mockito como `-javaagent` en Surefire y Failsafe (JP-BE-01).
- **Los E2E usan el mismo puerto que el stack de desarrollo (JP-OPS-01).** `docker-compose.e2e.yml` publica 5173 y 8080, como `docker-compose.yml`: no se pueden tener los dos levantados a la vez. En JP-QA-01 se decidió no cambiarlos: antes de los E2E hay que parar el stack de desarrollo (`docker compose stop backend frontend`). Si molesta, se pueden mover a otros puertos sin tocar los tests: el smoke test los lee de `FRONTEND_URL` y `BACKEND_URL`, y Playwright, de `FRONTEND_URL`.
- **El `proxy_pass` de nginx apunta al nombre del servicio de Compose (JP-OPS-01).** `backend:8080` vale en local y en la CI; para desplegar en Render habrá que parametrizarlo (ver 2.4).
- **El bundle inicial del frontend está justo por debajo de 500 kB (JP-FE-04 → JP-FE-06).** Antes era un único chunk de 504,6 kB (155,1 kB con gzip) y `npm run build` avisaba. Desde JP-FE-06, el tablero, el detalle de oferta y la nueva oferta se cargan con `lazy` de React Router, cada uno con un `HydrateFallback`. El chunk inicial queda en 494,4 kB (153,6 kB con gzip) y el tablero en 62,5 kB (20,7 kB con gzip, con dnd-kit). El margen es pequeño: casi todo el chunk inicial son React, React Router, TanStack Query, React Hook Form y Zod, que usan el login y el dashboard. Si una página nueva vuelve a disparar el aviso (p. ej. el editor de documentos de JP-FE-05), hay que cargarla también con `lazy` o separar las dependencias comunes en su propio chunk (`build.rolldownOptions.output`).

---

## 3. Modelo de Datos

### **3.1. Diagrama del modelo de datos:**

```mermaid
erDiagram
    USERS ||--o| PROFILES : "tiene"
    PROFILES ||--o{ PROFILE_SKILLS : "incluye"
    PROFILES ||--o{ EXPERIENCES : "incluye"
    USERS ||--o{ JOB_OFFERS : "importa"
    JOB_OFFERS ||--o{ OFFER_REQUIREMENTS : "contiene"
    JOB_OFFERS ||--o{ MATCH_ANALYSES : "se analiza en"
    PROFILES ||--o{ MATCH_ANALYSES : "se compara en"
    MATCH_ANALYSES ||--|{ REQUIREMENT_MATCHES : "detalla"
    OFFER_REQUIREMENTS ||--o{ REQUIREMENT_MATCHES : "se evalúa en"
    JOB_OFFERS ||--o{ GENERATED_DOCUMENTS : "genera"
    USERS ||--o{ APPLICATIONS : "gestiona"
    JOB_OFFERS ||--o| APPLICATIONS : "se registra como"
    APPLICATIONS ||--o{ APPLICATION_STATUS_HISTORY : "registra"

    USERS {
        uuid id PK
        varchar email UK "NOT NULL, max 254, CHECK email = lower(email)"
        varchar password_hash "NOT NULL, BCrypt"
        varchar full_name "NOT NULL, max 120"
        timestamptz created_at "NOT NULL"
        timestamptz updated_at "NOT NULL"
    }

    PROFILES {
        uuid id PK
        uuid user_id FK,UK "NOT NULL"
        varchar headline "max 160"
        text summary
        smallint years_experience "CHECK 0-60"
        varchar location "max 120"
        jsonb languages "NOT NULL, array JSON idioma + nivel"
        jsonb education "NOT NULL, array JSON titulación + centro + año"
        varchar cv_file_name "max 255"
        text cv_raw_text "texto extraído del PDF"
        timestamptz created_at "NOT NULL"
        timestamptz updated_at "NOT NULL"
    }

    PROFILE_SKILLS {
        uuid id PK
        uuid profile_id FK "NOT NULL"
        varchar name "NOT NULL, max 80, único por perfil sin distinguir mayúsculas"
        varchar level "BASICO | INTERMEDIO | AVANZADO"
        smallint years "CHECK >= 0"
    }

    EXPERIENCES {
        uuid id PK
        uuid profile_id FK "NOT NULL"
        varchar company "NOT NULL, max 120"
        varchar role "NOT NULL, max 120"
        date start_date "NOT NULL"
        date end_date "NULL = actual"
        text description
        smallint sort_order "NOT NULL"
    }

    JOB_OFFERS {
        uuid id PK
        uuid user_id FK "NOT NULL, UK (id, user_id)"
        varchar title "max 160"
        varchar company "max 120"
        varchar location "max 120"
        varchar work_mode "NOT NULL DEFAULT DESCONOCIDO, REMOTO | HIBRIDO | PRESENCIAL | DESCONOCIDO"
        integer salary_min "CHECK >= 0"
        integer salary_max "CHECK >= 0 y >= salary_min"
        varchar currency "ISO 4217, obligatoria si hay salario"
        varchar source_url "max 2048"
        text raw_text "NOT NULL, 200-20000 chars"
        varchar extraction_status "NOT NULL, PENDIENTE | COMPLETADA | FALLIDA"
        timestamptz created_at "NOT NULL"
    }

    OFFER_REQUIREMENTS {
        uuid id PK
        uuid offer_id FK "NOT NULL, UK (offer_id, id)"
        varchar description "NOT NULL, max 300"
        varchar type "OBLIGATORIO | DESEABLE"
        varchar category "TECNICO | IDIOMA | EXPERIENCIA | FORMACION | OTRO"
        smallint sort_order "NOT NULL"
    }

    MATCH_ANALYSES {
        uuid id PK
        uuid offer_id FK "NOT NULL, UK (offer_id, id)"
        uuid profile_id FK "NOT NULL"
        smallint score "NOT NULL, CHECK 0-100"
        boolean has_blocking_gap "NOT NULL"
        text summary "NOT NULL"
        jsonb suggestions "NOT NULL, array JSON de 3 a 5 strings"
        varchar llm_model "NOT NULL"
        timestamptz created_at "NOT NULL"
    }

    REQUIREMENT_MATCHES {
        uuid id PK
        uuid analysis_id FK "NOT NULL"
        uuid requirement_id FK "NOT NULL"
        uuid offer_id FK "NOT NULL, misma oferta en análisis y requisito"
        varchar result "CUMPLE | PARCIAL | NO_CUMPLE"
        text evidence
    }

    GENERATED_DOCUMENTS {
        uuid id PK
        uuid offer_id FK "NOT NULL"
        varchar type "CARTA | CV_ADAPTADO"
        text content_markdown "NOT NULL"
        smallint version "NOT NULL, >= 1"
        boolean edited_by_user "NOT NULL"
        timestamptz created_at "NOT NULL"
    }

    APPLICATIONS {
        uuid id PK
        uuid user_id FK "NOT NULL, = propietario de la oferta"
        uuid offer_id FK,UK "NOT NULL"
        varchar status "GUARDADA | ENVIADA | ENTREVISTA | OFERTA | DESCARTADA"
        integer board_position "NOT NULL, orden en la columna"
        date applied_at
        date follow_up_date
        text notes
        bigint version "NOT NULL, bloqueo optimista"
        timestamptz created_at "NOT NULL"
        timestamptz updated_at "NOT NULL"
    }

    APPLICATION_STATUS_HISTORY {
        uuid id PK
        uuid application_id FK "NOT NULL"
        varchar from_status "NULL en la creación"
        varchar to_status "NOT NULL"
        timestamptz changed_at "NOT NULL"
    }
```

### **3.2. Descripción de entidades principales:**

> Convenciones: todas las claves primarias son `UUID` generados por la aplicación. Los enumerados se guardan como `VARCHAR` con restricción `CHECK` (más fácil de migrar que los `ENUM` nativos de PostgreSQL). Las fechas con hora usan `TIMESTAMPTZ` en UTC, con `DEFAULT now()`. Los borrados se propagan en cascada desde el usuario. Las fechas "de hoy" (`applied_at`, seguimiento vencido) se calculan con un `java.time.Clock` inyectable en la zona `Europe/Madrid`, no en UTC, para que entre las 0:00 y la 1:00 "hoy" no siga siendo ayer; además hace los tests deterministas. PostgreSQL no indexa las claves foráneas automáticamente, así que cada FK tiene un índice que la cubre, es decir, que empieza por sus columnas o permite buscar por ellas (ver [Índices](#índices)). Las restricciones que no caben en un `UNIQUE` normal (unicidad sin distinguir mayúsculas, coherencia entre tablas) se implementan como índices únicos de expresión o como claves foráneas compuestas, y se indican así en las tablas.

#### `users` — Usuario de la aplicación

| Atributo | Tipo | Restricciones | Descripción |
|---|---|---|---|
| id | UUID | PK | Identificador. |
| email | VARCHAR(254) | NOT NULL, UNIQUE, CHECK (email = lower(email)) | Email de acceso. La aplicación lo normaliza a minúsculas antes de guardarlo y de buscarlo; el `CHECK` impide que entre uno sin normalizar y el `UNIQUE` normal sirve para el login. |
| password_hash | VARCHAR(100) | NOT NULL | Hash BCrypt. |
| full_name | VARCHAR(120) | NOT NULL | Nombre que aparece en las cartas y CVs. |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL | Auditoría. |

Relaciones: 1–0..1 con `profiles`; 1–N con `job_offers` y `applications`.

#### `profiles` — Perfil profesional extraído del CV

| Atributo | Tipo | Restricciones | Descripción |
|---|---|---|---|
| id | UUID | PK | |
| user_id | UUID | FK → users.id, NOT NULL, UNIQUE, ON DELETE CASCADE | Un perfil por usuario. |
| headline | VARCHAR(160) | | Titular, p. ej. "Desarrollador Web Junior · Java". |
| summary | TEXT | | Resumen profesional. |
| years_experience | SMALLINT | CHECK (0–60) | Años de experiencia totales. |
| location | VARCHAR(120) | | Ubicación. |
| languages | JSONB | NOT NULL DEFAULT '[]', CHECK (jsonb_typeof(languages) = 'array') | `[{"language":"Inglés","level":"B2"}]`. |
| education | JSONB | NOT NULL DEFAULT '[]', CHECK (jsonb_typeof(education) = 'array') | `[{"title":"DAW","institution":"…","year":2024}]`. |
| cv_file_name | VARCHAR(255) | | Nombre del PDF original. |
| cv_raw_text | TEXT | | Texto extraído del PDF. Se guarda al confirmar el perfil con `PUT /profile` (no al subir el CV) y se conserva al reemplazarlo; se usa como contexto para el LLM. |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL | |

Idiomas y formación se guardan en JSONB porque solo se muestran y se envían como contexto al LLM, nunca se consultan por separado. Las habilidades y experiencias, en cambio, tienen tabla propia por la unicidad sin distinguir mayúsculas de las habilidades, el `CHECK` del nivel y el orden de las experiencias. La base de datos solo garantiza que el JSONB es un array; la forma de cada elemento la valida la aplicación (records Java con Bean Validation, mapeados con `@JdbcTypeCode(SqlTypes.JSON)`). Si algún día hiciera falta filtrar por idioma o nivel en SQL, se normalizarían a tabla propia.

#### `profile_skills` — Habilidades del perfil

| Atributo | Tipo | Restricciones | Descripción |
|---|---|---|---|
| id | UUID | PK | |
| profile_id | UUID | FK → profiles.id, NOT NULL, ON DELETE CASCADE | |
| name | VARCHAR(80) | NOT NULL; índice único de expresión `(profile_id, lower(name))` | Ej. "Spring Boot". Se guarda tal como lo escribe el usuario; la unicidad no distingue mayúsculas. |
| level | VARCHAR(12) | CHECK IN (BASICO, INTERMEDIO, AVANZADO) | |
| years | SMALLINT | CHECK ≥ 0 | Años de uso. |

#### `experiences` — Experiencia laboral

| Atributo | Tipo | Restricciones | Descripción |
|---|---|---|---|
| id | UUID | PK | |
| profile_id | UUID | FK → profiles.id, NOT NULL, ON DELETE CASCADE | |
| company | VARCHAR(120) | NOT NULL | |
| role | VARCHAR(120) | NOT NULL | |
| start_date | DATE | NOT NULL | |
| end_date | DATE | NULL = puesto actual; CHECK end_date ≥ start_date | |
| description | TEXT | | Responsabilidades y logros. |
| sort_order | SMALLINT | NOT NULL | Orden en el que se muestra. |

#### `job_offers` — Oferta de empleo importada

| Atributo | Tipo | Restricciones | Descripción |
|---|---|---|---|
| id | UUID | PK; UNIQUE (id, user_id) | El `UNIQUE (id, user_id)` es el destino de la FK compuesta de `applications`. |
| user_id | UUID | FK → users.id, NOT NULL, ON DELETE CASCADE; INDEX (user_id, created_at DESC) | |
| title | VARCHAR(160) | | Puesto extraído. |
| company | VARCHAR(120) | | |
| location | VARCHAR(120) | | |
| work_mode | VARCHAR(12) | NOT NULL DEFAULT 'DESCONOCIDO', CHECK IN (REMOTO, HIBRIDO, PRESENCIAL, DESCONOCIDO) | `DESCONOCIDO` mientras la extracción está PENDIENTE o FALLIDA, o si la oferta no indica la modalidad. `NOT NULL` desde `V2__work_mode_not_null.sql` (JP-BE-05). |
| salary_min / salary_max | INTEGER | CHECK ≥ 0; CHECK de tabla salary_max ≥ salary_min | Salario bruto anual, si aparece. Cualquiera de los dos puede ser NULL. |
| currency | VARCHAR(3) | CHECK (currency ~ '^[A-Z]{3}$'); CHECK de tabla: obligatoria si hay salario | ISO 4217 (EUR, USD…). `VARCHAR` y no `CHAR(3)`: Hibernate mapea `String` a `varchar` y la validación del esquema falla con `bpchar`. |
| source_url | VARCHAR(2048) | | Solo como referencia; nunca se descarga. |
| raw_text | TEXT | NOT NULL, CHECK longitud 200–20000 | Texto original pegado. |
| extraction_status | VARCHAR(12) | NOT NULL, CHECK IN (PENDIENTE, COMPLETADA, FALLIDA) | La oferta se guarda en PENDIENTE antes de llamar al LLM (el texto no se pierde nunca) y pasa a COMPLETADA o FALLIDA después. Una oferta PENDIENTE (p. ej. por un error que no es de IA) o FALLIDA se puede reintentar con `POST /offers/{offerId}/extraction`. |
| created_at | TIMESTAMPTZ | NOT NULL | |

Restricción de tabla para la moneda: `CHECK ((salary_min IS NULL AND salary_max IS NULL) OR currency IS NOT NULL)`.

#### `offer_requirements` — Requisitos de la oferta

| Atributo | Tipo | Restricciones | Descripción |
|---|---|---|---|
| id | UUID | PK; UNIQUE (offer_id, id) | El `UNIQUE (offer_id, id)` es el destino de la FK compuesta de `requirement_matches` y además sirve de índice de `offer_id`. |
| offer_id | UUID | FK → job_offers.id, NOT NULL, ON DELETE CASCADE | |
| description | VARCHAR(300) | NOT NULL | Ej. "3+ años con Spring Boot". |
| type | VARCHAR(12) | NOT NULL, CHECK IN (OBLIGATORIO, DESEABLE) | |
| category | VARCHAR(12) | NOT NULL, CHECK IN (TECNICO, IDIOMA, EXPERIENCIA, FORMACION, OTRO) | |
| sort_order | SMALLINT | NOT NULL | |

Al reintentar la extracción de una oferta se borran sus requisitos **y sus análisis** en la misma transacción, para que ningún análisis quede con el detalle a medias por el borrado en cascada de `requirement_matches`.

#### `match_analyses` — Análisis de encaje perfil ↔ oferta

| Atributo | Tipo | Restricciones | Descripción |
|---|---|---|---|
| id | UUID | PK; UNIQUE (offer_id, id) | El `UNIQUE (offer_id, id)` es el destino de la FK compuesta de `requirement_matches`. |
| offer_id | UUID | FK → job_offers.id, NOT NULL, ON DELETE CASCADE; INDEX (offer_id, created_at DESC) | Se guarda un histórico; la UI muestra el más reciente. |
| profile_id | UUID | FK → profiles.id, NOT NULL, ON DELETE CASCADE; INDEX (profile_id) | |
| score | SMALLINT | NOT NULL, CHECK 0–100 | Calculado por `MatchScoreCalculator`. |
| has_blocking_gap | BOOLEAN | NOT NULL | Algún requisito obligatorio sin cumplir. |
| summary | TEXT | NOT NULL | Resumen breve del encaje. `NOT NULL` desde `V3__match_analyses_summary_suggestions.sql` (JP-BE-06). |
| suggestions | JSONB | NOT NULL (sin valor por defecto), CHECK (jsonb_typeof(suggestions) = 'array'), CHECK de 3 a 5 elementos | 3–5 sugerencias accionables. La aplicación lo valida en la respuesta del LLM y la BD lo garantiza con `ck_match_analyses_suggestions_count` (`V3`, JP-BE-06): `CASE WHEN jsonb_typeof(suggestions) = 'array' THEN jsonb_array_length(suggestions) BETWEEN 3 AND 5 ELSE false END`. El `CASE` evita que `jsonb_array_length` falle con un error que no es de restricción si el valor no es un array. |
| llm_model | VARCHAR(80) | NOT NULL | Modelo usado (trazabilidad). |
| created_at | TIMESTAMPTZ | NOT NULL | |

#### `requirement_matches` — Resultado por requisito

| Atributo | Tipo | Restricciones | Descripción |
|---|---|---|---|
| id | UUID | PK | |
| analysis_id | UUID | NOT NULL; UNIQUE (analysis_id, requirement_id) | |
| requirement_id | UUID | NOT NULL; INDEX (offer_id, requirement_id) | |
| offer_id | UUID | NOT NULL | Columna redundante a propósito: es la que obliga a que análisis y requisito sean de la misma oferta. |
| result | VARCHAR(10) | NOT NULL, CHECK IN (CUMPLE, PARCIAL, NO_CUMPLE) | |
| evidence | TEXT | | Fragmento del perfil que lo justifica. |

Coherencia con la oferta mediante dos claves foráneas compuestas que comparten `offer_id`:

```sql
FOREIGN KEY (offer_id, analysis_id)    REFERENCES match_analyses (offer_id, id)     ON DELETE CASCADE,
FOREIGN KEY (offer_id, requirement_id) REFERENCES offer_requirements (offer_id, id) ON DELETE CASCADE
```

Como `offer_id` es `NOT NULL` y es la misma columna en las dos FKs, es imposible guardar un resultado que apunte a un requisito de otra oferta. En JPA, `analysisId`, `requirementId` y `offerId` se mapean como columnas `UUID` normales (la entidad se crea siempre desde el análisis, que aporta el `offerId`), y las asociaciones `@ManyToOne`, si se usan, son de solo lectura (`insertable = false, updatable = false`).

#### `generated_documents` — Carta y CV adaptado

| Atributo | Tipo | Restricciones | Descripción |
|---|---|---|---|
| id | UUID | PK | |
| offer_id | UUID | FK → job_offers.id, NOT NULL, ON DELETE CASCADE | |
| type | VARCHAR(12) | NOT NULL, CHECK IN (CARTA, CV_ADAPTADO) | |
| content_markdown | TEXT | NOT NULL | Contenido editable. |
| version | SMALLINT | NOT NULL, CHECK ≥ 1; UNIQUE (offer_id, type, version) | Cada edición o regeneración crea una versión nueva. La última se obtiene con `ORDER BY version DESC LIMIT 1` sobre este mismo índice. |
| edited_by_user | BOOLEAN | NOT NULL DEFAULT false | Distingue lo generado de lo editado a mano. |
| created_at | TIMESTAMPTZ | NOT NULL | |

#### `applications` — Candidatura en el tablero

| Atributo | Tipo | Restricciones | Descripción |
|---|---|---|---|
| id | UUID | PK | |
| user_id | UUID | FK → users.id, NOT NULL, ON DELETE CASCADE; INDEX (user_id, status, board_position) | Redundante con el propietario de la oferta; la FK compuesta de abajo garantiza que coinciden. |
| offer_id | UUID | NOT NULL, UNIQUE; FK compuesta (offer_id, user_id) → job_offers (id, user_id), ON DELETE CASCADE | Una candidatura por oferta, y siempre del mismo usuario que la oferta. |
| status | VARCHAR(12) | NOT NULL DEFAULT 'GUARDADA', CHECK IN (GUARDADA, ENVIADA, ENTREVISTA, OFERTA, DESCARTADA) | Columna del kanban. |
| board_position | INTEGER | NOT NULL, CHECK ≥ 0 | Índice de la tarjeta dentro de su columna. Al colocar una tarjeta (cambio de estado o reordenación), el servidor la pone en el índice pedido y renumera de 0 a n−1 la columna de destino y, si cambia de columna, también la de origen (`BoardColumn`, JP-BE-08); un índice mayor que el tamaño de la columna la deja al final, y una tarjeta nueva va al final de GUARDADA. No es único en la BD (exigiría una restricción diferida); si hubiera empates, se ordena por `created_at`. |
| applied_at | DATE | CHECK (status NOT IN ('ENVIADA','ENTREVISTA','OFERTA') OR applied_at IS NOT NULL) | Se rellena al pasar a ENVIADA. |
| follow_up_date | DATE | | Si es anterior a hoy y la candidatura no está en OFERTA ni DESCARTADA, la tarjeta se marca como vencida (`followUpOverdue`; se calcula al consultar con el `Clock` de la aplicación, no se guarda), como dice la HU-05. |
| notes | TEXT | | |
| version | BIGINT | NOT NULL DEFAULT 0 | `@Version` para el bloqueo optimista. |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL | |

#### `application_status_history` — Historial de estados

| Atributo | Tipo | Restricciones | Descripción |
|---|---|---|---|
| id | UUID | PK | |
| application_id | UUID | FK → applications.id, NOT NULL, ON DELETE CASCADE; INDEX (application_id, changed_at) | |
| from_status | VARCHAR(12) | NULL en la creación; CHECK IN (mismos valores que `applications.status`) | |
| to_status | VARCHAR(12) | NOT NULL, CHECK IN (mismos valores que `applications.status`); CHECK (from_status IS DISTINCT FROM to_status) | |
| changed_at | TIMESTAMPTZ | NOT NULL | |

Las transiciones permitidas no se expresan en la base de datos (necesitarían un *trigger*); las aplica `ApplicationStatusPolicy` en Java.

#### Índices

| Tabla | Índice | Consulta a la que sirve |
|---|---|---|
| `users` | UNIQUE (email) | Login. |
| `profiles` | UNIQUE (user_id) | Perfil del usuario; FK. |
| `profile_skills` | UNIQUE (profile_id, lower(name)) | Unicidad sin distinguir mayúsculas; FK. |
| `experiences` | (profile_id, sort_order) | Cargar el perfil ordenado; FK. |
| `job_offers` | (user_id, created_at DESC) | **Dashboard:** ofertas del usuario, más recientes primero. |
| `job_offers` | UNIQUE (id, user_id) | Destino de la FK compuesta de `applications`. |
| `offer_requirements` | UNIQUE (offer_id, id) | Requisitos de una oferta; FK; destino de la FK compuesta. |
| `match_analyses` | (offer_id, created_at DESC) | **Dashboard y tablero:** último análisis (puntuación) de cada oferta. |
| `match_analyses` | (profile_id) | Borrado en cascada del perfil. |
| `match_analyses` | UNIQUE (offer_id, id) | Destino de la FK compuesta de `requirement_matches`. |
| `requirement_matches` | UNIQUE (analysis_id, requirement_id) | Detalle de un análisis; FK al análisis. |
| `requirement_matches` | (offer_id, requirement_id) | Borrado en cascada desde un requisito. |
| `generated_documents` | UNIQUE (offer_id, type, version) | Última versión de cada documento; FK. |
| `applications` | UNIQUE (offer_id) | Una candidatura por oferta; FK compuesta. |
| `applications` | (user_id, status, board_position) | **Tablero:** candidaturas por columna y en orden. |
| `application_status_history` | (application_id, changed_at) | Historial de una candidatura; FK. |

Consulta del dashboard (la puntuación es la del último análisis de cada oferta):

```sql
SELECT o.id, o.title, o.company, o.created_at, a.score, a.has_blocking_gap
FROM job_offers o
LEFT JOIN LATERAL (
    SELECT score, has_blocking_gap
    FROM match_analyses
    WHERE offer_id = o.id
    ORDER BY created_at DESC
    LIMIT 1
) a ON true
WHERE o.user_id = :userId
ORDER BY o.created_at DESC;
```

El tablero usa el mismo `LATERAL` sobre `applications` filtrado por `user_id`, ordenado en el orden de las columnas (GUARDADA, ENVIADA, ENTREVISTA, OFERTA, DESCARTADA, con un `CASE` sobre `status`) y, dentro de cada una, por `board_position, created_at`.

---

## 4. Especificación de la API

API REST con versión en la ruta (`/api/v1`), JSON, autenticación Bearer JWT y errores en formato **RFC 9457** (`application/problem+json`). El contrato completo se generará con springdoc-openapi y estará en Swagger UI.

**Resumen de endpoints del MVP**

Los schemas citados están definidos en `components.schemas` del YAML de abajo.

**Errores de IA** (endpoints marcados con 🤖): `429` límite por usuario superado · `502` `LlmInvalidResponse` (respuesta no válida tras un reintento) · `503` `LlmOverloaded` (concurrencia agotada o proveedor saturado, con `Retry-After`) · `504` `LlmTimeout`.

**Tipos de error** (`ProblemDetail`, RFC 9457). Todas las respuestas de error usan `application/problem+json`, con `instance` = ruta de la petición y nunca trazas ni mensajes internos. El campo `type` vale `https://jobpilot.dev/problems/<slug>`:

| Estado | `slug` | `title` | Cuándo |
|---|---|---|---|
| 400 | `validation-error` | Datos de entrada no válidos | Falla Bean Validation; incluye `errors: [{field, message}]`. También el CV de `POST /profile/cv` que falta, está vacío, pesa más de 5 MB, tiene más de 10 páginas o más de 50.000 caracteres de texto (en el campo `file`) |
| 400 | `malformed-request` | Petición mal formada | El cuerpo no es un JSON válido o no tiene el formato esperado |
| 401 | `unauthorized` | No autenticado | Falta el token, no es válido o ha caducado; con la cabecera `WWW-Authenticate: Bearer` |
| 401 | `invalid-credentials` | Credenciales incorrectas | Login con email o contraseña incorrectos; misma respuesta exista o no el email |
| 404 | `not-found` | Recurso no encontrado | El recurso no existe o es de otro usuario |
| 409 | `email-already-registered` | Email ya registrado | Registro con un email que ya existe (sin distinguir mayúsculas) |
| 409 | `offer-already-extracted` | La oferta ya tiene sus requisitos extraídos | `POST /offers/{offerId}/extraction` sobre una oferta COMPLETADA |
| 409 | `profile-concurrent-update` | El perfil se ha guardado a la vez desde otra petición | `PUT /profile` que choca en la BD con otro `PUT` simultáneo del mismo usuario (p. ej. dos pestañas creando el perfil a la vez) |
| 409 | `profile-required` | Necesitas crear tu perfil | `POST /offers/{offerId}/analysis` de un usuario que aún no tiene perfil (el frontend lo lleva a crearlo) |
| 409 | `offer-not-extracted` | La oferta no tiene requisitos extraídos | `POST /offers/{offerId}/analysis` sobre una oferta PENDIENTE o FALLIDA, o sin requisitos |
| 409 | `application-already-exists` | La oferta ya está en el tablero | `POST /applications` de una oferta que ya tiene candidatura |
| 409 | `invalid-status-transition` | Transición de estado no permitida | `PATCH /applications/{applicationId}/status` que no cumple las reglas de transición de la HU-05; el `detail` dice de qué estado a cuál |
| 409 | `version-conflict` | La candidatura ha cambiado | `PATCH /applications/{applicationId}` o `/status` con una `version` que ya no es la guardada (bloqueo optimista) |
| 409 | *(uno por caso)* | *(uno por caso)* | Otros conflictos de negocio que se añadan |
| 415 | `invalid-cv-file` | Fichero no válido | `POST /profile/cv` con un fichero que no es un PDF legible: tipo MIME distinto de `application/pdf`, sin la firma `%PDF-`, corrupto o con contraseña de apertura. `detail`: "Solo se admiten PDF de hasta 5 MB." |
| 422 | `cv-without-text` | El PDF no tiene texto | `POST /profile/cv` con un PDF del que no se extraen al menos 50 caracteres (p. ej. escaneado); el frontend sugiere subir un PDF con texto o rellenar el perfil a mano |
| 429 | `rate-limit-exceeded` | Demasiadas peticiones | Límite de IA por usuario o de intentos de login superado (ver 2.5), con la cabecera `Retry-After` (segundos) |
| 500 | `internal-error` | Error interno | Error no controlado (la traza solo va al log) |
| 502 | `llm-invalid-response` | Respuesta de la IA no válida | `LlmInvalidResponse` |
| 503 | `llm-overloaded` | La IA está saturada | `LlmOverloaded`, con la cabecera `Retry-After` (segundos) |
| 504 | `llm-timeout` | La IA no ha respondido a tiempo | `LlmTimeout` |

Los errores estándar del framework que no son de dominio (405, 406, 415, 404 de ruta inexistente…) también se devuelven como `ProblemDetail`, con el `type` por defecto (`about:blank`) y el título estándar de HTTP.

| Método | Ruta | Cuerpo | Respuesta OK | Otros errores | Funcionalidad |
|---|---|---|---|---|---|
| POST | `/auth/register` · `/auth/login` (públicos) | `RegisterRequest` · `LoginRequest` | 201 / 200 `AuthResponse` | 400, 401 (login), 409 (email ya registrado), 429 (login: demasiados intentos, ver 2.5) | Autenticación |
| POST | `/profile/cv` 🤖 | multipart (`file`, PDF ≤ 5 MB y ≤ 10 páginas) | 200 `CvDraftResponse`: el **borrador** del perfil, **sin guardarlo**. El texto extraído y el nombre del fichero esperan en memoria 30 min y pasan a `cv_raw_text` y `cv_file_name` con el siguiente `PUT /profile` | 400, 415 `invalid-cv-file`, 422 `cv-without-text` (ninguno llega a la IA ni gasta cupo) | F1 — Subir CV y obtener el borrador |
| GET | `/profile` | — | 200 `ProfileResponse` | 404 (aún no hay perfil) | F1 — Consultar el perfil |
| PUT | `/profile` | `ProfileRequest` | **201** la primera vez (crea el perfil, sirve también para el perfil hecho a mano) · **200** después (lo reemplaza entero y conserva su id, sus análisis y su CV). Añade el CV subido en los últimos 30 min, si lo hay; sin él (o caducado) se guarda igual, sin error | 400 (también habilidades repetidas sin distinguir mayúsculas o `endDate` anterior a `startDate`), 409 `profile-concurrent-update` | F1 — Guardar el perfil revisado |
| POST | `/offers` ⭐ | `CreateOfferRequest` | **201** `OfferResponse` + `Location` siempre que el texto sea válido. Si la IA falla, la oferta se guarda con `extractionStatus: FALLIDA` | 400, 429 (la oferta no se guarda) | **F2 — Importar oferta** |
| POST | `/offers/{offerId}/extraction` 🤖 | — | 200 `OfferResponse`. Reintenta la extracción de una oferta PENDIENTE o FALLIDA y borra los requisitos y análisis anteriores en una sola transacción. Si la IA vuelve a fallar, queda FALLIDA | 404, 409 `offer-already-extracted` (la oferta ya está COMPLETADA) | F2 — Reintentar la extracción |
| GET | `/offers` | — | 200 `OfferSummary[]` (incluye `latestScore`, `hasBlockingGap` y `applicationStatus`) | — | F2 — Dashboard |
| GET | `/offers/{offerId}` | — | 200 `OfferResponse` | 404 | F2 — Detalle |
| POST | `/offers/{offerId}/analysis` ⭐ 🤖 | — | 201 `AnalysisResponse`. Crea un análisis nuevo cada vez (se guarda un histórico) | 404, 409 `profile-required` / `offer-not-extracted` | **F3 — Calcular el encaje** |
| GET | `/offers/{offerId}/analysis` | — | 200 `AnalysisResponse` (el más reciente) | 404 (la oferta no existe, es de otro usuario o aún no tiene análisis) | F3 — Último análisis |
| POST | `/offers/{offerId}/documents` 🤖 | `CreateDocumentRequest` `{type}` | 201 `DocumentResponse` (versión nueva generada por la IA) | 404, 409 (no hay análisis) | F4 — Generar carta o CV adaptado |
| GET | `/offers/{offerId}/documents?type=CARTA\|CV_ADAPTADO` | — | 200 `DocumentHistoryResponse` (última versión completa + lista de versiones) | 404 | F4 — Ver el documento y su historial |
| POST | `/documents/{documentId}/versions` | `CreateDocumentVersionRequest` `{contentMarkdown}` | 201 `DocumentResponse` con id nuevo y `editedByUser: true` | 404, 409 (otra edición ha creado esa versión antes) | F4 — Guardar la edición como versión nueva |
| GET | `/documents/{documentId}/pdf` | — | 200 `application/pdf` (generado en el momento) | 404 | F4 — Exportar a PDF |
| GET | `/applications` | — | 200 `ApplicationResponse[]` | — | F5 — Tablero |
| POST | `/applications` | `CreateApplicationRequest` `{offerId}` | 201 `ApplicationResponse` en GUARDADA (historial con `from = NULL`) | 404 (la oferta no existe o es de otro usuario), 409 `application-already-exists` | F5 — Guardar en el tablero (también ofertas PENDIENTE o FALLIDA) |
| PATCH | `/applications/{applicationId}` | `UpdateApplicationRequest` (`version` obligatoria) | 200 `ApplicationResponse`. Solo cambia los campos enviados (`null` borra el valor); no escribe en el historial | 400 (también si se vacía `appliedAt` en ENVIADA, ENTREVISTA u OFERTA), 404, 409 `version-conflict` | F5 — Notas, fechas y **orden dentro de la columna** |
| PATCH | `/applications/{applicationId}/status` ⭐ | `ChangeStatusRequest` | 200 `ApplicationResponse`. Al pasar a ENVIADA, `appliedAt` pasa a ser hoy si estaba vacía | 400 `validation-error` en `status` (mismo estado), 404, 409 `invalid-status-transition` / `version-conflict` | **F5 — Cambiar de columna** |

⭐ Endpoints principales detallados en OpenAPI a continuación (la plantilla pide un máximo de 3).

```yaml
openapi: 3.0.3
info:
  title: JobPilot API
  version: 1.0.0
  description: API del copiloto de búsqueda de empleo JobPilot.
servers:
  - url: http://localhost:8080/api/v1
security:
  - bearerAuth: []

paths:
  /offers:
    post:
      tags: [Offers]
      summary: Importa una oferta y extrae sus requisitos con IA
      description: >
        El texto en bruto se guarda siempre. Si la extracción con IA falla
        (timeout, saturación o respuesta no válida), se responde igualmente 201
        con extractionStatus FALLIDA y el usuario puede reintentar con
        POST /offers/{offerId}/extraction. Por eso este endpoint no devuelve
        códigos 50x.
      operationId: createOffer
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateOfferRequest'
      responses:
        '201':
          description: Oferta creada (requisitos extraídos, o extractionStatus FALLIDA si la IA ha fallado)
          headers:
            Location:
              schema: { type: string, example: /api/v1/offers/7c1e6a52-3b8f-4d0a-9a51-0f2c7f1d9b10 }
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/OfferResponse'
        '400': { $ref: '#/components/responses/ValidationError' }
        '401': { $ref: '#/components/responses/Unauthorized' }
        '429': { $ref: '#/components/responses/TooManyRequests' }

  /offers/{offerId}/analysis:
    post:
      tags: [Analysis]
      summary: Calcula el análisis de encaje entre el perfil del usuario y la oferta
      operationId: createAnalysis
      parameters:
        - $ref: '#/components/parameters/OfferId'
      responses:
        '201':
          description: Análisis calculado
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/AnalysisResponse'
        '401': { $ref: '#/components/responses/Unauthorized' }
        '404': { $ref: '#/components/responses/NotFound' }
        '409':
          description: El usuario no tiene perfil (profile-required) o la oferta no tiene requisitos extraídos (offer-not-extracted)
          content:
            application/problem+json:
              schema: { $ref: '#/components/schemas/ProblemDetail' }
        '429': { $ref: '#/components/responses/TooManyRequests' }
        '502': { $ref: '#/components/responses/LlmInvalidResponse' }
        '503': { $ref: '#/components/responses/LlmOverloaded' }
        '504': { $ref: '#/components/responses/LlmTimeout' }

  /applications/{applicationId}/status:
    patch:
      tags: [Applications]
      summary: Mueve una candidatura a otra columna del tablero
      operationId: changeApplicationStatus
      parameters:
        - name: applicationId
          in: path
          required: true
          schema: { type: string, format: uuid }
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ChangeStatusRequest'
      responses:
        '200':
          description: Estado actualizado y registrado en el historial
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApplicationResponse'
        '400':
          description: >
            Datos no válidos, o el estado pedido es el mismo que el actual. Para
            reordenar dentro de la misma columna se usa PATCH /applications/{applicationId}.
          content:
            application/problem+json:
              schema: { $ref: '#/components/schemas/ProblemDetail' }
        '401': { $ref: '#/components/responses/Unauthorized' }
        '404': { $ref: '#/components/responses/NotFound' }
        '409':
          description: Transición de estado no permitida o conflicto de versión
          content:
            application/problem+json:
              schema: { $ref: '#/components/schemas/ProblemDetail' }

components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT

  parameters:
    OfferId:
      name: offerId
      in: path
      required: true
      schema: { type: string, format: uuid }

  schemas:
    CreateOfferRequest:
      type: object
      required: [rawText]
      properties:
        rawText:
          type: string
          minLength: 200
          maxLength: 20000
          description: Texto completo de la oferta, copiado del portal. La longitud se cuenta en caracteres Unicode, como char_length en PostgreSQL
        sourceUrl:
          type: string
          nullable: true
          format: uri
          maxLength: 2048
          pattern: '^$|(?i)https?://[^\s/?#]+\S*'
          description: URL de referencia (no se descarga). Solo http o https; vacía equivale a no enviarla

    Requirement:
      type: object
      properties:
        id: { type: string, format: uuid }
        description: { type: string }
        type: { type: string, enum: [OBLIGATORIO, DESEABLE] }
        category: { type: string, enum: [TECNICO, IDIOMA, EXPERIENCIA, FORMACION, OTRO] }

    OfferResponse:
      type: object
      properties:
        id: { type: string, format: uuid }
        title:
          type: string
          nullable: true
          description: null si la extracción está PENDIENTE o FALLIDA
        company: { type: string, nullable: true }
        location: { type: string, nullable: true }
        workMode:
          type: string
          enum: [REMOTO, HIBRIDO, PRESENCIAL, DESCONOCIDO]
          description: Siempre tiene valor; DESCONOCIDO si la extracción está PENDIENTE o FALLIDA o la oferta no la indica
        salary:
          type: object
          nullable: true
          description: null si la oferta no indica salario. Como en la BD, puede faltar un extremo pero no los dos, y la moneda es obligatoria
          required: [currency]
          properties:
            min: { type: integer, minimum: 0, nullable: true }
            max: { type: integer, minimum: 0, nullable: true }
            currency: { type: string, pattern: '^[A-Z]{3}$', example: EUR }
        sourceUrl: { type: string, nullable: true }
        extractionStatus: { type: string, enum: [PENDIENTE, COMPLETADA, FALLIDA] }
        requirements:
          type: array
          items: { $ref: '#/components/schemas/Requirement' }
        createdAt: { type: string, format: date-time }

    RequirementMatch:
      type: object
      properties:
        requirementId: { type: string, format: uuid }
        description: { type: string }
        type: { type: string, enum: [OBLIGATORIO, DESEABLE] }
        result: { type: string, enum: [CUMPLE, PARCIAL, NO_CUMPLE] }
        evidence: { type: string, nullable: true }

    AnalysisResponse:
      type: object
      properties:
        id: { type: string, format: uuid }
        offerId: { type: string, format: uuid }
        score: { type: integer, minimum: 0, maximum: 100 }
        hasBlockingGap: { type: boolean }
        summary: { type: string }
        matches:
          type: array
          items: { $ref: '#/components/schemas/RequirementMatch' }
        suggestions:
          type: array
          items: { type: string }
          minItems: 3
          maxItems: 5
        createdAt: { type: string, format: date-time }

    ChangeStatusRequest:
      type: object
      required: [status, boardPosition, version]
      properties:
        status: { type: string, enum: [GUARDADA, ENVIADA, ENTREVISTA, OFERTA, DESCARTADA] }
        boardPosition:
          type: integer
          minimum: 0
          description: Índice en la columna de destino; el servidor renumera la columna. Si es mayor que su tamaño, la tarjeta queda al final
        version:
          type: integer
          format: int64
          description: Versión actual (bloqueo optimista)

    ApplicationResponse:
      type: object
      properties:
        id: { type: string, format: uuid }
        offerId: { type: string, format: uuid }
        offerTitle: { type: string, nullable: true }
        company: { type: string, nullable: true }
        score: { type: integer, nullable: true }
        status: { type: string, enum: [GUARDADA, ENVIADA, ENTREVISTA, OFERTA, DESCARTADA] }
        boardPosition: { type: integer }
        appliedAt: { type: string, format: date, nullable: true }
        followUpDate: { type: string, format: date, nullable: true }
        followUpOverdue: { type: boolean }
        notes: { type: string, nullable: true }
        version: { type: integer, format: int64 }

    CreateApplicationRequest:
      type: object
      required: [offerId]
      properties:
        offerId: { type: string, format: uuid }

    UpdateApplicationRequest:
      type: object
      required: [version]
      description: Campos opcionales; solo se modifican los que se envían. Sirve también para reordenar dentro de la columna.
      properties:
        notes: { type: string, nullable: true, maxLength: 5000 }
        appliedAt: { type: string, format: date, nullable: true }
        followUpDate: { type: string, format: date, nullable: true }
        boardPosition:
          type: integer
          minimum: 0
          description: Nuevo índice dentro de la misma columna; el servidor renumera la columna
        version:
          type: integer
          format: int64
          description: Versión actual (bloqueo optimista)

    OfferSummary:
      type: object
      description: Elemento del dashboard (GET /offers)
      properties:
        id: { type: string, format: uuid }
        title: { type: string, nullable: true }
        company: { type: string, nullable: true }
        createdAt: { type: string, format: date-time }
        extractionStatus: { type: string, enum: [PENDIENTE, COMPLETADA, FALLIDA] }
        latestScore: { type: integer, minimum: 0, maximum: 100, nullable: true }
        hasBlockingGap: { type: boolean, nullable: true }
        applicationStatus:
          type: string
          nullable: true
          enum: [GUARDADA, ENVIADA, ENTREVISTA, OFERTA, DESCARTADA]

    Skill:
      type: object
      required: [name]
      properties:
        name: { type: string, maxLength: 80 }
        level: { type: string, nullable: true, enum: [BASICO, INTERMEDIO, AVANZADO] }
        years: { type: integer, minimum: 0, maximum: 60, nullable: true }

    Experience:
      type: object
      required: [company, role, startDate]
      properties:
        company: { type: string, maxLength: 120 }
        role: { type: string, maxLength: 120 }
        startDate: { type: string, format: date }
        endDate: { type: string, format: date, nullable: true, description: 'null = puesto actual; nunca anterior a startDate' }
        description: { type: string, maxLength: 4000, nullable: true }

    ProfileRequest:
      type: object
      description: Perfil completo que se guarda con PUT /profile (reemplaza el anterior). Todos los campos son opcionales; una lista que no se envía equivale a una vacía
      properties:
        headline: { type: string, maxLength: 160, nullable: true }
        summary: { type: string, maxLength: 4000, nullable: true }
        yearsExperience: { type: integer, minimum: 0, maximum: 60, nullable: true }
        location: { type: string, maxLength: 120, nullable: true }
        languages:
          type: array
          maxItems: 20
          items:
            type: object
            required: [language, level]
            properties:
              language: { type: string, maxLength: 60 }
              level: { type: string, maxLength: 40, example: B2 }
        education:
          type: array
          maxItems: 20
          items:
            type: object
            required: [title]
            properties:
              title: { type: string, maxLength: 160 }
              institution: { type: string, maxLength: 160, nullable: true }
              year: { type: integer, nullable: true }
        skills:
          type: array
          maxItems: 100
          description: Sin nombres repetidos (no se distinguen mayúsculas)
          items: { $ref: '#/components/schemas/Skill' }
        experiences:
          type: array
          maxItems: 50
          description: El orden del array es el orden en que se muestran
          items: { $ref: '#/components/schemas/Experience' }

    ProfileResponse:
      allOf:
        - $ref: '#/components/schemas/ProfileRequest'
        - type: object
          properties:
            id: { type: string, format: uuid }
            cvFileName: { type: string, nullable: true }
            updatedAt: { type: string, format: date-time }

    CvDraftResponse:
      allOf:
        - $ref: '#/components/schemas/ProfileRequest'
        - type: object
          description: Borrador extraído por la IA. No se guarda como perfil hasta que el usuario lo confirma con PUT /profile. Las habilidades repetidas que devuelva la IA ya vienen quitadas.
          properties:
            cvFileName: { type: string }

    CreateDocumentRequest:
      type: object
      required: [type]
      properties:
        type: { type: string, enum: [CARTA, CV_ADAPTADO] }

    CreateDocumentVersionRequest:
      type: object
      required: [contentMarkdown]
      properties:
        contentMarkdown: { type: string, minLength: 1, maxLength: 20000 }

    DocumentResponse:
      type: object
      properties:
        id: { type: string, format: uuid }
        type: { type: string, enum: [CARTA, CV_ADAPTADO] }
        version: { type: integer, minimum: 1 }
        contentMarkdown: { type: string }
        editedByUser: { type: boolean }
        createdAt: { type: string, format: date-time }

    DocumentHistoryResponse:
      type: object
      properties:
        latest: { $ref: '#/components/schemas/DocumentResponse' }
        versions:
          type: array
          description: Todas las versiones, de la más reciente a la más antigua, sin el contenido
          items:
            type: object
            properties:
              id: { type: string, format: uuid }
              version: { type: integer }
              editedByUser: { type: boolean }
              createdAt: { type: string, format: date-time }

    RegisterRequest:
      type: object
      required: [email, password, fullName]
      properties:
        email: { type: string, format: email, maxLength: 254, description: Se guarda en minúsculas }
        password:
          type: string
          minLength: 8
          description: Al menos una letra y un número; como máximo 72 bytes en UTF-8 (límite de BCrypt)
        fullName: { type: string, minLength: 1, maxLength: 120 }

    LoginRequest:
      type: object
      required: [email, password]
      properties:
        email: { type: string, format: email, description: Sin distinguir mayúsculas }
        password: { type: string }

    AuthResponse:
      type: object
      properties:
        accessToken: { type: string, description: 'JWT HS256 (sub = id del usuario, iss = jobpilot)' }
        tokenType: { type: string, enum: [Bearer] }
        expiresIn: { type: integer, description: Segundos de validez del token, example: 3600 }
        user:
          type: object
          properties:
            id: { type: string, format: uuid }
            email: { type: string, format: email }
            fullName: { type: string }

    ProblemDetail:
      type: object
      properties:
        type: { type: string, format: uri }
        title: { type: string }
        status: { type: integer }
        detail: { type: string }
        instance: { type: string }
        errors:
          type: array
          items:
            type: object
            properties:
              field: { type: string }
              message: { type: string }

  responses:
    ValidationError:
      description: Datos de entrada no válidos
      content:
        application/problem+json:
          schema: { $ref: '#/components/schemas/ProblemDetail' }
    Unauthorized:
      description: Token ausente, caducado o no válido
      content:
        application/problem+json:
          schema: { $ref: '#/components/schemas/ProblemDetail' }
    NotFound:
      description: El recurso no existe o no pertenece al usuario
      content:
        application/problem+json:
          schema: { $ref: '#/components/schemas/ProblemDetail' }
    TooManyRequests:
      description: Límite de peticiones superado (rate-limit-exceeded; IA por usuario o intentos de login)
      headers:
        Retry-After:
          schema: { type: integer }
      content:
        application/problem+json:
          schema: { $ref: '#/components/schemas/ProblemDetail' }
    LlmInvalidResponse:
      description: La IA ha devuelto una respuesta que no cumple el esquema, también tras un reintento
      content:
        application/problem+json:
          schema: { $ref: '#/components/schemas/ProblemDetail' }
    LlmOverloaded:
      description: No hay hueco para más llamadas simultáneas a la IA, o el proveedor está saturado
      headers:
        Retry-After:
          schema: { type: integer }
      content:
        application/problem+json:
          schema: { $ref: '#/components/schemas/ProblemDetail' }
    LlmTimeout:
      description: La IA no ha respondido dentro del tiempo máximo configurado
      content:
        application/problem+json:
          schema: { $ref: '#/components/schemas/ProblemDetail' }
```

#### Ejemplo 1 — `POST /api/v1/offers`

Petición:

```http
POST /api/v1/offers
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json

{
  "rawText": "Buscamos Desarrollador/a Java Junior para nuestro equipo de producto en Valencia (modelo híbrido, 2 días en oficina). Requisitos: experiencia de al menos 1 año con Java y Spring Boot, conocimientos de SQL y PostgreSQL, uso de Git. Se valorará: experiencia con React, Docker y nivel de inglés B2. Ofrecemos 24.000 - 28.000 € brutos anuales...",
  "sourceUrl": "https://empresa-ejemplo.com/empleo/java-junior"
}
```

Respuesta `201 Created`:

```json
{
  "id": "7c1e6a52-3b8f-4d0a-9a51-0f2c7f1d9b10",
  "title": "Desarrollador/a Java Junior",
  "company": null,
  "location": "Valencia",
  "workMode": "HIBRIDO",
  "salary": { "min": 24000, "max": 28000, "currency": "EUR" },
  "sourceUrl": "https://empresa-ejemplo.com/empleo/java-junior",
  "extractionStatus": "COMPLETADA",
  "requirements": [
    { "id": "a1…", "description": "Al menos 1 año de experiencia con Java y Spring Boot", "type": "OBLIGATORIO", "category": "EXPERIENCIA" },
    { "id": "a2…", "description": "Conocimientos de SQL y PostgreSQL", "type": "OBLIGATORIO", "category": "TECNICO" },
    { "id": "a3…", "description": "Uso de Git", "type": "OBLIGATORIO", "category": "TECNICO" },
    { "id": "a4…", "description": "Experiencia con React", "type": "DESEABLE", "category": "TECNICO" },
    { "id": "a5…", "description": "Docker", "type": "DESEABLE", "category": "TECNICO" },
    { "id": "a6…", "description": "Inglés B2", "type": "DESEABLE", "category": "IDIOMA" }
  ],
  "createdAt": "2026-11-04T16:30:12Z"
}
```

#### Ejemplo 2 — `POST /api/v1/offers/{offerId}/analysis`

Respuesta `201 Created` para la oferta del Ejemplo 1 (obligatorios a1–a3: CUMPLE, CUMPLE, CUMPLE → 1,0; deseables a4–a6: PARCIAL, NO_CUMPLE, CUMPLE → 0,5 · score = round(100 × (0,7 × 1,0 + 0,3 × 0,5)) = **85**). `matches` lleva un elemento por requisito, en el orden de la oferta:

```json
{
  "id": "e3b1f0c4-…",
  "offerId": "7c1e6a52-3b8f-4d0a-9a51-0f2c7f1d9b10",
  "score": 85,
  "hasBlockingGap": false,
  "summary": "Cumples todos los requisitos obligatorios. Tu principal margen de mejora está en Docker.",
  "matches": [
    { "requirementId": "a1…", "description": "Al menos 1 año de experiencia con Java y Spring Boot", "type": "OBLIGATORIO", "result": "CUMPLE", "evidence": "Prácticas de 12 meses como desarrollador Java (Spring Boot) en …" },
    { "requirementId": "a2…", "description": "Conocimientos de SQL y PostgreSQL", "type": "OBLIGATORIO", "result": "CUMPLE", "evidence": "APIs REST sobre PostgreSQL en el puesto actual" },
    { "requirementId": "a3…", "description": "Uso de Git", "type": "OBLIGATORIO", "result": "CUMPLE", "evidence": "Control de versiones con Git en todos los proyectos" },
    { "requirementId": "a4…", "description": "Experiencia con React", "type": "DESEABLE", "result": "PARCIAL", "evidence": "Proyecto personal con React; sin experiencia profesional" },
    { "requirementId": "a5…", "description": "Docker", "type": "DESEABLE", "result": "NO_CUMPLE", "evidence": null },
    { "requirementId": "a6…", "description": "Inglés B2", "type": "DESEABLE", "result": "CUMPLE", "evidence": "Inglés B2" }
  ],
  "suggestions": [
    "Pon tu experiencia con Spring Boot en la primera línea del resumen.",
    "Menciona tu proyecto con React con un enlace al repositorio.",
    "Añade un Dockerfile a uno de tus proyectos públicos y menciónalo."
  ],
  "createdAt": "2026-11-04T16:31:02Z"
}
```

#### Ejemplo 3 — `PATCH /api/v1/applications/{applicationId}/status`

```json
// Petición
{ "status": "ENVIADA", "boardPosition": 0, "version": 3 }

// Respuesta 409 Conflict (transición no permitida)
{
  "type": "https://jobpilot.dev/problems/invalid-status-transition",
  "title": "Transición de estado no permitida",
  "status": 409,
  "detail": "No se puede pasar de OFERTA a ENVIADA.",
  "instance": "/api/v1/applications/5f0c…/status"
}
```

---

## 5. Historias de Usuario

> El MVP se compone de **5 historias de usuario must-have**, una por funcionalidad. Se documentan las cinco porque cada una es la unidad de alcance del proyecto. Formato: *Como / Quiero / Para*, criterios de aceptación en Gherkin, prioridad (MoSCoW) y estimación en puntos de historia.

### Historia de Usuario 1 — HU-01: Crear mi perfil a partir del CV

**Como** candidato en búsqueda de empleo,
**quiero** subir mi CV en PDF y que la aplicación cree mi perfil profesional automáticamente,
**para** no tener que escribir a mano mis datos y poder compararme con cualquier oferta.

- **Prioridad:** Must · **Estimación:** 5 SP · **Funcionalidad:** F1

**Criterios de aceptación**

```gherkin
Escenario: Extracción correcta del CV
  Dado que estoy autenticado y no tengo perfil
  Cuando subo un CV en PDF con texto de menos de 5 MB
  Entonces veo un formulario prerrellenado con titular, resumen, años de experiencia,
       habilidades, experiencias, formación e idiomas
  Y puedo editar, añadir o eliminar cualquier campo antes de guardar

Escenario: Fichero no válido
  Cuando subo un fichero que no es PDF o que pesa más de 5 MB
  Entonces veo el error "Solo se admiten PDF de hasta 5 MB" y no se envía nada a la IA

Escenario: PDF sin texto (escaneado)
  Cuando subo un PDF del que no se puede extraer texto
  Entonces veo un aviso que me sugiere subir un PDF con texto o rellenar el perfil a mano

Escenario: Servicio de IA no disponible
  Cuando la IA no responde
  Entonces veo un mensaje para reintentar y conservo la opción de rellenar el perfil a mano
```

**Notas:** el PDF no se guarda; solo se guarda el texto extraído. Si se vuelve a subir un CV, se pide confirmación antes de sobrescribir el perfil.

### Historia de Usuario 2 — HU-02: Importar una oferta de trabajo

**Como** candidato,
**quiero** pegar el texto de una oferta y que la aplicación identifique sus datos clave y requisitos,
**para** tener cada oferta estructurada sin leerla y copiarla línea a línea.

- **Prioridad:** Must · **Estimación:** 3 SP · **Funcionalidad:** F2

**Criterios de aceptación**

```gherkin
Escenario: Importación correcta
  Dado que estoy autenticado
  Cuando pego el texto de una oferta (entre 200 y 20.000 caracteres) y pulso "Analizar oferta"
  Entonces veo el puesto, la empresa, la ubicación, la modalidad y el salario (si aparece)
  Y veo los requisitos agrupados en "Obligatorios" y "Deseables"
  Y la oferta aparece en mi dashboard

Escenario: Texto demasiado corto
  Cuando pego menos de 200 caracteres
  Entonces el botón queda deshabilitado y veo cuántos caracteres faltan

Escenario: Fallo de extracción
  Cuando la IA falla tras un reintento
  Entonces la oferta se guarda con estado FALLIDA y veo un botón "Reintentar extracción"
```

### Historia de Usuario 3 — HU-03: Ver mi encaje con una oferta

**Como** candidato,
**quiero** ver una puntuación de encaje con el detalle de qué requisitos cumplo y cuáles no,
**para** decidir rápido si vale la pena postular y qué destacar en mi candidatura.

- **Prioridad:** Must · **Estimación:** 8 SP · **Funcionalidad:** F3

**Criterios de aceptación**

```gherkin
Escenario: Análisis con perfil completo
  Dado que tengo perfil y una oferta con requisitos extraídos
  Cuando abro la oferta y pulso "Calcular encaje"
  Entonces veo una puntuación de 0 a 100 con su color (rojo < 50, ámbar 50-74, verde ≥ 75)
  Y cada requisito aparece marcado como Cumple / Parcial / No cumple, con la evidencia de mi perfil
  Y veo entre 3 y 5 sugerencias concretas

Escenario: Requisito bloqueante
  Cuando no cumplo algún requisito obligatorio
  Entonces veo el aviso "Hay requisitos obligatorios que no cumples" con esos requisitos señalados

Escenario: Puntuación reproducible
  Dado el mismo resultado por requisito
  Entonces la puntuación es siempre la misma, según la fórmula documentada

Escenario: Sin perfil
  Dado que no he creado mi perfil
  Cuando intento calcular el encaje
  Entonces se me redirige a crear el perfil
```

### Historia de Usuario 4 — HU-04: Generar carta y CV adaptados

**Como** candidato,
**quiero** generar una carta de presentación y un CV adaptados a una oferta concreta,
**para** enviar candidaturas personalizadas en minutos en lugar de horas.

- **Prioridad:** Must · **Estimación:** 5 SP · **Funcionalidad:** F4

**Criterios de aceptación**

```gherkin
Escenario: Generación de la carta
  Dado que la oferta tiene un análisis de encaje
  Cuando pulso "Generar carta"
  Entonces veo en un editor una carta de 250-400 palabras, en el idioma de la oferta,
       que menciona el puesto y al menos dos requisitos que cumplo
  Y no incluye experiencias ni habilidades que no estén en mi perfil

Escenario: Edición y versionado
  Cuando edito el texto y guardo
  Entonces se crea una nueva versión marcada como "editada por mí"

Escenario: Exportación
  Cuando pulso "Exportar PDF"
  Entonces se descarga un PDF con el contenido actual y mi nombre en la cabecera
```

### Historia de Usuario 5 — HU-05: Gestionar mis candidaturas en un tablero

**Como** candidato que postula a muchas ofertas,
**quiero** ver todas mis candidaturas en un tablero kanban y moverlas según avanzan,
**para** no perder ninguna oportunidad y saber cuándo hacer seguimiento.

- **Prioridad:** Must · **Estimación:** 5 SP · **Funcionalidad:** F5

**Criterios de aceptación**

```gherkin
Escenario: Ver el tablero
  Dado que tengo candidaturas guardadas
  Cuando abro "Tablero"
  Entonces veo las columnas Guardada, Enviada, Entrevista, Oferta y Descartada
  Y cada tarjeta muestra puesto, empresa, puntuación y fecha de seguimiento

Escenario: Mover una tarjeta
  Cuando arrastro una tarjeta de "Guardada" a "Enviada"
  Entonces se guarda el nuevo estado, la fecha de envío pasa a ser hoy si estaba vacía
  Y el cambio se mantiene al recargar la página

Escenario: Transición no permitida
  Cuando intento mover una tarjeta de "Oferta" a "Guardada"
  Entonces la tarjeta vuelve a su columna y veo un mensaje explicativo

Escenario: Seguimiento vencido
  Dado que una candidatura tiene fecha de seguimiento anterior a hoy y no está en Oferta ni Descartada
  Entonces su tarjeta muestra el indicador "Seguimiento pendiente"
```

**Reglas de transición:** solo se avanza al estado siguiente (`GUARDADA → ENVIADA → ENTREVISTA → OFERTA`); desde cualquier estado distinto de `DESCARTADA` se puede pasar a `DESCARTADA` (incluida `OFERTA`, si el candidato rechaza la oferta); `DESCARTADA → GUARDADA` permite reabrir. No se puede retroceder ni saltar estados. Dentro de la misma columna se puede reordenar libremente.

---

## 6. Tickets de Trabajo

**Backlog del MVP (vista general)**

| ID | Tipo | Título | HU | SP |
|---|---|---|---|---|
| JP-DB-01 | Base de datos | Esquema inicial con Flyway y datos de prueba | Todas | 3 |
| JP-BE-01 | Backend | Scaffolding Spring Boot, configuración, manejo global de errores | — | 2 |
| JP-BE-02 | Backend | Registro, login y filtro JWT | — | 3 |
| JP-BE-03 | Backend | Puerto `LlmGateway`, adaptador Spring AI y `FakeLlmGateway` | 1–4 | 3 |
| JP-BE-04 | Backend | Subida de CV y extracción de perfil | HU-01 | 5 |
| JP-BE-05 | Backend | Importación de ofertas y extracción de requisitos + límite de peticiones (Bucket4j: IA y login, ver 2.5) | HU-02 | 3 |
| **JP-BE-06** | **Backend** | **Análisis de encaje: `MatchScoreCalculator` y endpoint** | HU-03 | 5 |
| JP-BE-07 | Backend | Generación, versionado y exportación PDF de documentos | HU-04 | 5 |
| JP-BE-08 | Backend | API de candidaturas y política de transiciones | HU-05 | 3 |
| JP-FE-01 | Frontend | Scaffolding Vite + React, router, cliente API, auth | — | 3 |
| JP-FE-02 | Frontend | Onboarding: subida de CV y formulario de perfil | HU-01 | 5 |
| JP-FE-03 | Frontend | Dashboard y formulario de nueva oferta | HU-02 | 3 |
| JP-FE-04 | Frontend | Detalle de oferta y panel de análisis | HU-03 | 5 |
| JP-FE-05 | Frontend | Editor de documentos y exportación | HU-04 | 3 |
| **JP-FE-06** | **Frontend** | **Tablero kanban de candidaturas** | HU-05 | 5 |
| JP-QA-01 | Tests | Suite E2E del flujo principal con Playwright | Todas | 3 |
| JP-OPS-01 | DevOps | Docker Compose y pipeline de CI en GitHub Actions | — | 3 |
| JP-AI-01 | Tests | Validación con el LLM real (`@Tag("llm-live")`, fuera de la CI) | HU-02, HU-03 | 2 |

A continuación se detallan los tres tickets representativos que pide la plantilla: uno de base de datos, uno de backend y uno de frontend.

### Ticket 1 — JP-DB-01: Esquema inicial de base de datos con Flyway y datos de prueba

**Tipo:** Base de datos · **Prioridad:** Alta (bloquea al resto de tickets de backend) · **Estimación:** 3 SP · **Historias:** todas

**Descripción**
Crear la migración inicial de Flyway con todas las tablas del modelo de datos (sección 3) y un script de datos de prueba que solo se cargue en el perfil `dev`, para que el proyecto se pueda revisar en local con datos realistas.

**Alcance**

1. Añadir `spring-boot-starter-flyway` (incluye `flyway-core`; en Spring Boot 4 la autoconfiguración de Flyway está en su propio módulo) y `flyway-database-postgresql` al `pom.xml` y configurar `spring.flyway.locations` (`classpath:db/migration` siempre; además `classpath:db/seed` en el perfil `dev`).
2. Crear `V1__init_schema.sql` con las 11 tablas: `users`, `profiles`, `profile_skills`, `experiences`, `job_offers`, `offer_requirements`, `match_analyses`, `requirement_matches`, `generated_documents`, `applications`, `application_status_history`.
3. Definir para cada tabla:
   - PK `UUID`; FKs con `ON DELETE CASCADE` según la sección 3.2.
   - Enumerados como `VARCHAR` + `CHECK` (por ejemplo, `CHECK (status IN ('GUARDADA','ENVIADA','ENTREVISTA','OFERTA','DESCARTADA'))`).
   - Restricciones: exactamente las de las tablas de la sección 3.2. Las más delicadas:
     - `users`: `UNIQUE (email)` + `CHECK (email = lower(email))`; la aplicación normaliza el email antes de guardarlo.
     - Índice único de expresión `(profile_id, lower(name))` en `profile_skills`.
     - JSONB con `NOT NULL DEFAULT '[]'` y `CHECK (jsonb_typeof(...) = 'array')`.
     - `job_offers`: `currency VARCHAR(3)` con `CHECK (currency ~ '^[A-Z]{3}$')` y el `CHECK` de tabla que la hace obligatoria si hay salario.
     - Claves únicas de destino (`job_offers (id, user_id)`, `offer_requirements (offer_id, id)`, `match_analyses (offer_id, id)`) y las **FKs compuestas** de `applications` y `requirement_matches`.
     - `applications`: `CHECK (board_position >= 0)` y `applied_at` obligatorio en ENVIADA, ENTREVISTA y OFERTA.
     - `application_status_history`: `CHECK (from_status IS DISTINCT FROM to_status)`.
   - Índices: los de la tabla [Índices](#índices) de la sección 3.2 (cada FK tiene un índice que la cubre: que empieza por sus columnas o permite buscar por ellas).
4. Crear `db/seed/R__demo_data.sql` (migración repetible) con: el usuario `demo@jobpilot.dev` (hash BCrypt de `Demo1234!`), un perfil completo con 8 habilidades y 2 experiencias, 3 ofertas con requisitos y análisis (puntuaciones 42, 68 y 85) y 3 candidaturas en GUARDADA, ENVIADA (con seguimiento vencido) y ENTREVISTA. Las dos últimas llevan `applied_at`, que la restricción exige, y el email del usuario va en minúsculas. Debe poder ejecutarse varias veces sin duplicar datos (`ON CONFLICT DO NOTHING`).
5. Mapear las entidades JPA con `spring.jpa.hibernate.ddl-auto=validate`, de modo que Hibernate falle al arrancar si el código y el esquema no coinciden.

**Criterios de aceptación**

- [ ] `./mvnw spring-boot:run` contra una BD vacía crea el esquema sin errores y Hibernate lo valida.
- [ ] Con el perfil `dev`, se puede iniciar sesión con las credenciales de prueba y el tablero muestra 3 candidaturas.
- [ ] Falla por restricción: insertar un email con mayúsculas o duplicado, una puntuación de 101, un estado no válido, un salario sin moneda, una candidatura ENVIADA sin `applied_at` o un historial con el mismo estado de origen y destino.
- [ ] Falla por FK compuesta: un `requirement_match` cuyo requisito es de otra oferta, o una candidatura cuyo `user_id` no es el propietario de la oferta.
- [ ] Borrar un usuario elimina en cascada su perfil, ofertas, análisis, documentos y candidaturas.
- [ ] El perfil `prod` no carga los datos de prueba.

**Tests**

- `SchemaMigrationIT` (Testcontainers con `postgres:16`): aplica las migraciones y comprueba que existen todas las tablas e índices consultando `information_schema`.
- `ConstraintsIT`: un caso por restricción crítica, incluidos los dos de FK compuesta (se espera `DataIntegrityViolationException`).
- `CascadeDeleteIT`: borra un usuario y comprueba que quedan 0 filas relacionadas.

**Definición de hecho:** migraciones revisadas, tests en verde en la CI, diagrama de la sección 3 actualizado si ha cambiado algo, PR aprobado.

### Ticket 2 — JP-BE-06: Análisis de encaje (`MatchScoreCalculator` y `POST /offers/{offerId}/analysis`)

**Tipo:** Backend · **Prioridad:** Alta (núcleo del producto) · **Estimación:** 5 SP · **Historia:** HU-03
**Dependencias:** JP-DB-01, JP-BE-03 (`LlmGateway`), JP-BE-04 (perfil), JP-BE-05 (ofertas)

**Descripción**
Implementar el análisis de encaje entre el perfil del usuario y una oferta. El LLM clasifica cada requisito (CUMPLE / PARCIAL / NO_CUMPLE) con su evidencia y propone sugerencias; la puntuación se calcula en Java de forma determinista.

**Alcance técnico**

1. **Dominio** (`analysis/domain`)
   - `enum MatchResult { CUMPLE(1.0), PARCIAL(0.5), NO_CUMPLE(0.0) }`.
   - `MatchScoreCalculator` (clase sin dependencias de Spring):
     `ScoreResult calculate(List<RequirementEvaluation> evaluations)` → `score` (0–100) y `hasBlockingGap`.
     Fórmula: 70 % la media de los obligatorios + 30 % la media de los deseables; si no hay deseables, el 100 % son los obligatorios. Redondeo `HALF_UP`. Si la lista está vacía → `IllegalArgumentException`.
2. **Integración con la IA** (`ai`)
   - Plantilla `prompts/match.st`: el prompt de sistema define el rol, marca el perfil y la oferta como *datos no confiables* entre delimitadores y exige una salida JSON con `{ matches: [{ requirementId, result, evidence }], summary, suggestions[3..5] }`.
   - Método `LlmGateway.evaluateMatch(ProfileSnapshot, List<RequirementSnapshot>) → MatchEvaluation`.
   - Validación de la respuesta: todos los `requirementId` existen y no se repiten, ninguno falta, `result` es válido y hay entre 3 y 5 sugerencias. Si no es válida → un reintento → `LlmInvalidResponseException` (→ 502). Un timeout lanza `LlmTimeoutException` (→ 504), y la concurrencia agotada o el proveedor saturado lanzan `LlmOverloadedException` (→ 503 con `Retry-After`). Un `@RestControllerAdvice` común hace la conversión a `ProblemDetail`.
3. **Servicio** `MatchAnalysisService.analyze(userId, offerId)`
   - Carga la oferta con `findByIdAndUserId` (si no existe → `NotFoundException` → 404).
   - Comprueba que existe el perfil y que la oferta está `COMPLETADA` con al menos un requisito (si no → `ConflictException` → 409).
   - Llama al LLM fuera de la transacción de BD; después persiste `match_analyses` + `requirement_matches` en una transacción.
   - Guarda `llm_model` para la trazabilidad.
4. **Controlador** `AnalysisController`
   - `POST /api/v1/offers/{offerId}/analysis` → 201 + `AnalysisResponse`.
   - `GET /api/v1/offers/{offerId}/analysis` → último análisis o 404.
   - Límite de peticiones (Bucket4j, 30/h por usuario) y `Semaphore` de 3 llamadas simultáneas, compartidos con el resto de endpoints de IA (ver 2.5) → 429 / 503.
5. **Fake** — `FakeLlmGateway` devuelve una evaluación fija leída de `src/main/resources/fake-llm/match-evaluation.json` para los tests de integración y E2E. Como los ids de los requisitos se generan en cada ejecución, el JSON lleva los resultados **por posición** (CUMPLE, CUMPLE, CUMPLE, PARCIAL, NO_CUMPLE, CUMPLE) y el fake los asigna en orden a los requisitos que recibe, repitiendo la secuencia si hay más. Con la oferta fake del Ejemplo 1 da **85**.
6. **Datos que van al LLM** — solo el perfil estructurado (titular, resumen, años de experiencia, ubicación, habilidades, experiencias, formación e idiomas) y los requisitos (id, descripción, tipo y categoría), como JSON en los bloques `<perfil>` y `<requisitos>`. Nunca el texto en bruto del CV, ni el nombre ni el email del usuario.
7. **Esquema** — `V3__match_analyses_summary_suggestions.sql`: `summary NOT NULL` y entre 3 y 5 sugerencias (ver 3.2).

**Criterios de aceptación**

- [ ] Con 3 obligatorios CUMPLE y deseables PARCIAL, NO_CUMPLE y CUMPLE, la respuesta tiene `score = 85` y `hasBlockingGap = false`.
- [ ] Con un obligatorio NO_CUMPLE, `hasBlockingGap = true`.
- [ ] Un usuario no puede analizar ofertas de otro (404).
- [ ] Sin perfil → 409 con `type` `.../profile-required`.
- [ ] Una respuesta del LLM no válida dos veces seguidas → 502 y no se guarda nada.
- [ ] Timeout del LLM → 504; concurrencia agotada → 503 con cabecera `Retry-After`.
- [ ] El endpoint aparece documentado en Swagger UI.

**Tests**

- `MatchScoreCalculatorTest` (`@ParameterizedTest`, al menos 8 casos: todo cumple, nada cumple, sin deseables, solo un requisito, casos límite de redondeo 84,5 → 85, lista vacía).
- `MatchEvaluationValidatorTest`: un id que falta, un id duplicado, un id desconocido, pocas o demasiadas sugerencias.
- `MatchEvaluationParserTest` (adaptador real con un `ChatModel` simulado): perfil y requisitos delimitados como datos; reintento ante una respuesta no válida → `LlmInvalidResponseException`. El reintento lo hace `StructuredLlmCall`, dentro del gateway, no el servicio.
- `MatchAnalysisServiceTest` (Mockito): orquestación, orden de las comprobaciones (404 → 409), no se llama al LLM ni se gasta cupo si no hay perfil, y no se guarda nada si el LLM falla.
- `AnalysisControllerIT` (Testcontainers + MockMvc + `FakeLlmGateway`): 201 / 401 / 404 / 409.

**Definición de hecho:** cobertura ≥ 90 % en `analysis.domain` (la comprueba JaCoCo en `mvnw verify`: `jacoco:check` sobre las líneas de ese paquete, con los unitarios y los IT; informe en `backend/target/site/jacoco/index.html`), tests en verde en la CI, contrato OpenAPI actualizado, PR revisado.

### Ticket 3 — JP-FE-06: Tablero kanban de candidaturas

**Tipo:** Frontend · **Prioridad:** Alta · **Estimación:** 5 SP · **Historia:** HU-05
**Dependencias:** JP-FE-01 (cliente API y auth), JP-BE-08 (API de candidaturas)

**Descripción**
Crear la página `/tablero`, con las candidaturas del usuario en 5 columnas y la posibilidad de moverlas arrastrando (y con el teclado), con actualización optimista y vuelta atrás si el backend rechaza el cambio.

**Alcance técnico**

1. **Ruta y datos**
   - Ruta protegida `/tablero` en React Router.
   - Hook `useApplications()` con TanStack Query (`GET /applications`), agrupando por `status` y ordenando por `boardPosition`.
   - Hook `useChangeStatus()` (`PATCH /applications/{id}/status`), **solo para mover entre columnas**, con actualización optimista: `onMutate` mueve la tarjeta en la caché, `onError` restaura el estado anterior y muestra un *toast*, y `onSettled` invalida la consulta.
   - Hook `useReorderApplication()` (`PATCH /applications/{id}` con `boardPosition` y `version`) para **reordenar dentro de la misma columna**, también con actualización optimista. Así el historial de estados no se llena de filas sin cambio de estado.
2. **Componentes** (`features/tracker/`)
   - `KanbanBoard`: `DndContext` de dnd-kit con `PointerSensor` y `KeyboardSensor` (accesibilidad).
   - `KanbanColumn`: título, contador de tarjetas y lista ordenable (`SortableContext`).
   - `ApplicationCard`: puesto, empresa, `MatchScoreBadge`, fecha de seguimiento e indicador "Seguimiento pendiente" si `followUpOverdue`.
   - `ApplicationDrawer`: panel lateral al hacer clic en una tarjeta, con notas, fecha de envío y fecha de seguimiento (React Hook Form + Zod) → `PATCH /applications/{id}`.
3. **Reglas en cliente:** un helper `canTransition(from, to)` replica la política del backend para resaltar las columnas no válidas mientras se arrastra. Si se suelta una tarjeta en una de ellas, no se llama al backend: la tarjeta vuelve a su sitio y un *toast* explica por qué. Si aun así el backend responde 409 (p. ej. un estado desfasado), se deshace el movimiento.
4. **Estados de la UI:** *skeleton* mientras carga, estado vacío ("Aún no tienes candidaturas — importa una oferta") con enlace al formulario, y error con botón de reintentar.
5. **Responsive:** en pantallas de menos de 768 px las columnas pasan a pestañas y el cambio de estado se hace con un menú "Mover a…".
6. **Guardar en el tablero:** en el detalle de la oferta, el botón "Guardar en el tablero" (`POST /applications`), también para ofertas PENDIENTE o FALLIDA. Si la oferta ya tiene candidatura (se busca en `GET /applications`), se muestra el enlace "Ver en el tablero". Tras guardar se muestra un *toast* y se recargan el tablero y la oferta. Un 409 `application-already-exists` se trata como guardada.

**Criterios de aceptación**

- [ ] Se ven 5 columnas con su contador y las tarjetas ordenadas por `boardPosition`.
- [ ] Al arrastrar de Guardada a Enviada, la tarjeta cambia de columna al instante y el cambio se mantiene al recargar.
- [ ] Al reordenar dentro de una columna se llama a `PATCH /applications/{id}` (no a `/status`) y no se crea ninguna entrada en el historial.
- [ ] Si el backend responde 409, la tarjeta vuelve a su sitio y aparece un *toast* con el `detail` del error.
- [ ] Se puede mover una tarjeta solo con el teclado (Espacio para cogerla, flechas para moverla, Espacio para soltarla).
- [ ] Las tarjetas con seguimiento vencido muestran el indicador.
- [ ] No aparecen errores ni avisos en la consola; Lighthouse Accessibility ≥ 90 en la página.

**Tests**

- `KanbanBoard.test.tsx` (Vitest + React Testing Library + MSW): pinta columnas y tarjetas desde la respuesta simulada; el estado vacío; la vuelta atrás ante un 409.
- `canTransition.test.ts`: tabla completa de transiciones permitidas y prohibidas.
- Cubierto también por el E2E del flujo principal (JP-QA-01): arrastrar a "Enviada" y comprobar que se mantiene.

**Definición de hecho:** componentes tipados sin `any`, tests en verde, lint sin errores, revisado en móvil y escritorio, PR aprobado.

**Revisión manual (hecha el 10/11/2026, con `docker compose up --build`):** ✅ guardar en el tablero desde el detalle · ✅ arrastrar con el ratón y que el cambio se mantenga tras F5 · ✅ movimiento prohibido bloqueado sin ninguna petición en Network · ✅ reordenar con `PATCH /applications/{id}` · ✅ mover solo con el teclado · ✅ panel lateral · ✅ vista móvil con pestañas y "Mover a…" · ✅ Lighthouse Accessibility ≥ 90. Captura en 1.3.

---

## 7. Pull Requests

Cada ticket se desarrolla en su propia rama y se fusiona en `main` con una PR **dentro de mi repositorio**. Las PRs no son la entrega: la entrega es rama + Typeform. Convención: una rama por ticket (`feat/JP-XX-NN-descripcion`), título con [Conventional Commits](https://www.conventionalcommits.org/), descripción con el resumen de la fase 5 de `/ticket` (qué se ha hecho, tests, decisiones y cambios en el readme) y **CI en verde antes de fusionar** (desde la PR #7).

**Pull Request 1** — [#1 `feat(backend): scaffolding Spring Boot, configuración y manejo global de errores`](https://github.com/AndresAvella16/AI4Devs-finalproject/pull/1) (JP-BE-01)

- Proyecto Maven con Spring Boot 4.1.2 (compatible con Spring AI 2.0), springdoc 3.2.0 y Failsafe para los `*IT` con Testcontainers.
- `GlobalExceptionHandler` sobre `ResponseEntityExceptionHandler`: todos los errores salen como `ProblemDetail` (RFC 9457) sin trazas; el 500 registra la excepción en el log. Excepciones de IA traducidas a 502/503/504.
- Perfiles `dev`, `e2e` y `prod` (en `prod`, las variables sensibles son obligatorias y Swagger está desactivado). `docker-compose.yml` mínimo con PostgreSQL.
- Decisión humana: no adelantar Spring Security y no registrar cuerpos de petición (datos del CV). La prueba real con el perfil `dev` detectó un `.env` mal formado.

**Pull Request 2** — [#8 `feat(offer): importar ofertas con extracción de requisitos y límites de peticiones`](https://github.com/AndresAvella16/AI4Devs-finalproject/pull/8) (JP-BE-05)

- `POST /offers`, reintento de extracción, dashboard (`GET /offers` con consulta `LATERAL`) y detalle, todo filtrado por usuario.
- La oferta se guarda en PENDIENTE → el LLM se llama fuera de transacción → pasa a COMPLETADA o FALLIDA: el texto del usuario no se pierde nunca (decisión humana frente a la propuesta inicial de la IA).
- Límites con Bucket4j: 30 llamadas de IA por hora y usuario, y 10 intentos de login por minuto por IP + email (429 con `Retry-After`).
- La prueba con el stack de Docker destapó que nginx permitía falsear la IP del cliente; ahora sustituye `X-Forwarded-For` por la IP real. Cierra las incoherencias 13 y 14 de la revisión de coherencia (ver `prompts.md`, sección 4 → Prompt 3) con la migración V2.

**Pull Request 3** — [#9 `feat(analysis): análisis de encaje con puntuación determinista`](https://github.com/AndresAvella16/AI4Devs-finalproject/pull/9) (JP-BE-06)

- `MatchScoreCalculator` en Java puro (`BigDecimal`, redondeo `HALF_UP`): la IA clasifica cada requisito y Java calcula la puntuación. El Ejemplo 2 da 85.
- `LlmGateway.evaluateMatch` con validación de la respuesta (todos los requisitos, sin duplicados, de 3 a 5 sugerencias) y reintento. Al LLM solo se envía el perfil estructurado, sin el texto del CV, nombre ni email.
- JaCoCo con regla de cobertura ≥ 90 % en `analysis.domain` (resultado: 100 %). Migración V3 que cierra las incoherencias 15, 16 y 17 de esa misma revisión.

**Todas las PRs de la Entrega 2**

| PR | Ticket | Título |
|---|---|---|
| #1 | JP-BE-01 | feat(backend): scaffolding Spring Boot, configuración y manejo global de errores |
| #2 | JP-DB-01 | feat(db): esquema inicial con Flyway, datos de prueba y entidades JPA |
| #3 | JP-BE-02 | feat(auth): registro, login y validación de JWT con Spring Security |
| #4 | JP-BE-03 | feat(ai): puerto LlmGateway con adaptador Spring AI y modo fake |
| #5 | — | docs(prompts): registrar el uso de IA en los tickets de backend de la Entrega 2 |
| #6 | JP-FE-01 | feat(frontend): scaffolding de Vite + React con router, cliente API y autenticación |
| #7 | JP-OPS-01 | feat(ops): stack completo con Docker Compose y pipeline de CI en GitHub Actions |
| #8 | JP-BE-05 | feat(offer): importar ofertas con extracción de requisitos y límites de peticiones |
| #9 | JP-BE-06 | feat(analysis): análisis de encaje con puntuación determinista |
| #10 | JP-FE-03 | feat(offers): dashboard de ofertas, formulario de nueva oferta y detalle básico |
| #11 | JP-FE-04 | feat(analysis): panel de análisis de encaje en el detalle de la oferta |
