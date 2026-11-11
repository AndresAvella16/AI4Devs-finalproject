## Índice

0. [Ficha del proyecto](#0-ficha-del-proyecto)
1. [Descripción general del producto](#1-descripción-general-del-producto)
2. [Arquitectura del sistema](#2-arquitectura-del-sistema)
3. [Modelo de datos](#3-modelo-de-datos)
4. [Especificación de la API](#4-especificación-de-la-api)
5. [Historias de usuario](#5-historias-de-usuario)
6. [Tickets de trabajo](#6-tickets-de-trabajo)
7. [Pull requests](#7-pull-requests)
8. [Deuda técnica y fuera de alcance](#8-deuda-técnica-y-fuera-de-alcance)

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

> **Sin despliegue público:** es opcional en el máster y queda fuera de alcance (ver [sección 8](#8-deuda-técnica-y-fuera-de-alcance)).
>
> **Evidencia de funcionamiento:**
>
> - **Vídeo del flujo principal (2–3 min):** pendiente de enlace — VIDEO_URL
> - **El repositorio**, que se arranca en local con un solo comando (`docker compose up --build`) y las **credenciales de prueba** `demo@jobpilot.dev` / `Demo1234!`. Los pasos para el revisor están en la [sección 1.4](#14-instrucciones-de-instalación).
> - Las capturas de la [sección 1.3](#13-diseño-y-experiencia-de-usuario).

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

#### Estado de implementación (Entrega 3 — final)

Las cinco funcionalidades del MVP funcionan de extremo a extremo: navegador → React (nginx) → API Spring Boot → PostgreSQL, con la IA detrás del puerto `LlmGateway`.

| # | Funcionalidad | Estado | Tickets |
|---|---|---|---|
| — | Registro e inicio de sesión (JWT) | ✅ Operativa | JP-BE-02, JP-FE-01 |
| F1 | Perfil a partir del CV | ✅ Operativa: subida del PDF en `/perfil`, borrador extraído por la IA, formulario editable y edición posterior del perfil | JP-BE-04, JP-FE-02 |
| F2 | Importar oferta | ✅ Operativa: texto pegado → datos y requisitos extraídos por la IA; si la IA falla, la oferta queda FALLIDA y se puede reintentar | JP-BE-05, JP-FE-03 |
| F3 | Análisis de encaje | ✅ Operativa: clasificación de cada requisito por la IA, puntuación calculada en Java, aviso de bloqueante y sugerencias | JP-BE-06, JP-FE-04 |
| F4 | Carta y CV adaptados con PDF | ✅ Operativa: pestañas "Carta" y "CV adaptado" en el detalle de la oferta para generar con IA, editar (cada guardado es una versión nueva "Editada por mí"), ver el historial y exportar a PDF | JP-BE-07, JP-FE-05 |
| F5 | Tablero de candidaturas | ✅ Operativa: "Guardar en el tablero" desde el detalle de la oferta y kanban en `/tablero`, con arrastre por ratón y teclado, panel de notas y fechas, y pestañas con "Mover a…" en móvil | JP-BE-08, JP-FE-06 |

**No implementado:** los *should-have* S1 y S2 y el despliegue público, que es opcional. El detalle está en la [sección 8](#8-deuda-técnica-y-fuera-de-alcance).

> **Sobre la IA.** Por defecto la app funciona en modo `LLM_MODE=fake`: `FakeLlmGateway` devuelve respuestas fijas (`backend/src/main/resources/fake-llm/*.json`) para el CV, la oferta, el encaje, la carta y el CV adaptado. Así, todo el flujo es real (se guarda en la BD, Java calcula la puntuación y se ve en el navegador) menos la respuesta del modelo, y **se puede revisar sin API key**. Con `LLM_MODE=real` y una `ANTHROPIC_API_KEY`, `SpringAiLlmGateway` llama a Claude (por defecto `claude-sonnet-5-5`); se probó con la API real (capturas 06 y 07 de la sección 1.3). La validación automática con el modelo real es `LlmLiveIT` (JP-AI-01, ver 2.6).

### **1.3. Diseño y experiencia de usuario:**

> Capturas con el usuario de prueba `demo@jobpilot.dev`, en modo `fake`, salvo las de la "Prueba con la IA real" (`LLM_MODE=real`). La del perfil es de una cuenta recién registrada, con el CV procesado por el LLM fake.
>
> **Vídeo del recorrido completo:** pendiente de enlace — VIDEO_URL

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

**Onboarding de perfil (JP-FE-02)**: el formulario prerrellenado con el borrador que ha extraído la IA del CV, antes de guardarlo. Las habilidades son *chips* con su nivel y sus años, editables y con botón para quitarlas; las experiencias, la formación y los idiomas, listas en las que se puede añadir y quitar. "Puesto actual" deja vacía la fecha de fin.

![Onboarding de perfil](docs/screenshots/09-perfil.png)

**Carta y CV adaptado (JP-FE-05)**: panel "Documentos" del detalle de la oferta, con las pestañas Carta / CV adaptado. La carta generada por la IA (LLM fake) se edita como texto, con contador de caracteres; debajo, "Guardar cambios", "Exportar PDF" y el historial de versiones, donde se ve una versión "Editada por mí" entre dos generadas por la IA.

![Carta y CV adaptado](docs/screenshots/10-carta.png)

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

**Pantallas del producto** (todas implementadas)

| Pantalla | Contenido principal |
|---|---|
| **Login / Registro** | Formulario de email y contraseña, con validación en cliente. |
| **Onboarding de perfil** (`/perfil`) | Zona de arrastre para el PDF (o botón para elegirlo, también con el teclado) → indicador de procesamiento → formulario editable con lo que ha extraído la IA (habilidades como *chips*; experiencias, formación e idiomas como listas). También "Rellenar a mano". Con perfil, la misma pantalla sirve para editarlo, y volver a subir un CV pide confirmación antes de sustituir el formulario. |
| **Dashboard** | Lista de ofertas importadas con su puntuación de encaje (badge de color: rojo < 50, ámbar 50–74, verde ≥ 75) y botón "Nueva oferta". |
| **Nueva oferta** | Área de texto grande para pegar la oferta y campo opcional para la URL. |
| **Detalle de oferta** | Cabecera (puesto, empresa, modalidad, salario), requisitos agrupados en *obligatorios* y *deseables*, panel de análisis (puntuación circular, tabla de requisitos con ✅ / ⚠️ / ❌ y su evidencia, sugerencias) y pestañas "Carta" / "CV adaptado" con editor y botón "Exportar PDF". |
| **Tablero** | Kanban de 5 columnas con tarjetas arrastrables (puesto, empresa, puntuación, fecha de seguimiento con aviso si está vencida) y un panel lateral para notas y fechas. |

> **Cómo se llega al onboarding (JP-FE-02).** El registro lleva directamente a `/perfil` (un usuario nuevo nunca tiene perfil). Si sale sin crearlo, el dashboard muestra el aviso "Crea tu perfil" mientras `GET /profile` dé 404, y el detalle de oferta lo lleva a `/perfil` si calcula el encaje sin perfil (409 `profile-required`). Antes de subir nada, el cliente comprueba que el fichero sea un PDF (`application/pdf`) de hasta 5 MB ("Solo se admiten PDF de hasta 5 MB."); el resto de rechazos del backend (400 en `file`, 415) muestran su mensaje. Con 422 `cv-without-text` sugiere subir un PDF con texto o rellenarlo a mano, y con los errores de la IA (429/502/503/504) permite reintentar y conserva "Rellenar a mano". El primer guardado (201) lleva al dashboard; los siguientes (200) se quedan en la página.

> **Carta y CV adaptado (JP-FE-05).** En el detalle de una oferta COMPLETADA, debajo del análisis, el panel "Documentos" tiene dos pestañas (se cambian también con las flechas del teclado). "Generar carta" / "Generar CV adaptado" está desactivado mientras la oferta no tenga análisis ("Calcula primero tu encaje…"); los errores de la IA (409 `analysis-required`, 429/502/503/504 con `Retry-After`) se muestran en la pestaña. El Markdown se edita y se muestra **como texto** en un `<textarea>`, sin vista previa en HTML. "Guardar cambios" crea la versión siguiente ("Editada por mí") a partir de la que se está editando; si otra pestaña se ha adelantado (409 `document-version-conflict`), se avisa, se conserva el texto y se puede "Cargar la última versión". "Volver a generar" con cambios sin guardar pide confirmación. "Exportar PDF" descarga la versión guardada (con el JWT, como `blob:`, con el nombre de `Content-Disposition`) y está desactivado mientras haya cambios sin guardar ("Guarda los cambios para exportarlos."). El historial de versiones es de solo lectura: la API no devuelve el contenido de las versiones antiguas.

### **1.4. Instrucciones de instalación:**

> **Guía para el revisor.** Instrucciones comprobadas desde cero en una copia limpia del repositorio, en Windows 10 con Docker Desktop (WSL 2). Basta con la **opción A**: no hace falta tener Java ni Node instalados, ni API key.

#### Requisitos previos

| Para… | Necesitas |
|---|---|
| Arrancar la app (opción A) | **Git** y **Docker Desktop** (o Docker Engine + Docker Compose v2), y los puertos **5173**, **8080** y **5432** libres |
| Desarrollar en local (opción B) y ejecutar los tests | Además: **Java 21** (JDK; el proyecto trae Maven Wrapper, no hace falta instalar Maven) y **Node.js 20.19+** con npm (lo exige Vite 8) |
| Usar la IA real (opcional) | Una **API key de Anthropic**. Sin ella todo funciona en modo `fake` |

#### Paso 1 — Clonar y crear el `.env`

```bash
git clone https://github.com/AndresAvella16/AI4Devs-finalproject.git
cd AI4Devs-finalproject
cp .env.example .env               # PowerShell: Copy-Item .env.example .env
```

`.env.example` trae todas las variables, con los secretos vacíos. Solo hay que **rellenar tres** en el `.env`; el resto de valores por defecto ya sirven:

```dotenv
# ⚠️ Valores SOLO para pruebas en local. Nunca los uses en un entorno real.
POSTGRES_USER=jobpilot
POSTGRES_PASSWORD=jobpilot_local_pass
JWT_SECRET=jobpilot-local-review-secret-not-for-prod-2026
```

- **`JWT_SECRET` tiene que tener al menos 32 bytes** (256 bits, el mínimo de HS256). Si es más corto, el backend no arranca (`JwtConfig`); el de arriba tiene 46 caracteres. Si prefieres generar uno aleatorio (64 caracteres hexadecimales):

  ```bash
  openssl rand -hex 32
  ```

  ```powershell
  $b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); -join ($b | ForEach-Object { $_.ToString('x2') })
  ```

- `POSTGRES_USER` y `POSTGRES_PASSWORD` crean el usuario de la base de datos la **primera vez** que arranca el volumen `pgdata`. Si ya habías arrancado el proyecto con otras credenciales, bórralo antes con `docker compose down -v` (borra los datos locales).
- No toques `LLM_MODE=fake`: con él la app funciona sin API key (ver "Usar la IA real" más abajo).

Todas las variables de `.env.example`:

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

#### Paso 2 — Opción A: todo con Docker Compose (recomendada)

```bash
docker compose up --build          # o "docker compose up -d --build --wait" para dejarlo en segundo plano
```

La primera vez tarda unos minutos (descarga las imágenes y compila backend y frontend). Levanta tres servicios, cada uno cuando el anterior está *healthy*: `postgres` → `backend` (perfil `dev`: aplica las migraciones y carga los datos de prueba) → `frontend` (nginx). Dentro de Compose el backend se conecta a `postgres:5432`; el `DB_URL` del `.env` solo se usa al arrancar el backend con `mvnw` (opción B).

- **Aplicación:** http://localhost:5173
- API: http://localhost:8080/api/v1
- Documentación interactiva de la API (Swagger UI): http://localhost:8080/swagger-ui.html

Para pararlo: `docker compose stop` (o `docker compose down`; con `-v` borra también la base de datos).

> **`VITE_API_BASE_URL` se fija al construir la imagen del frontend, no al arrancarla.** Si lo cambias en `.env`, reconstruye con `docker compose up --build`. Con nginx tiene que ser la ruta relativa **`/api/v1`**: una URL absoluta (p. ej. `http://localhost:8080/api/v1`) apunta a otro origen, la CSP (`connect-src 'self'`) bloquea la llamada y el login falla con *"No se ha podido conectar con el servidor"*. `scripts/smoke-test.sh` lo detecta revisando el JS generado.

#### Paso 2 — Opción B: desarrollo en local

```bash
# 1. Base de datos
docker compose up -d postgres

# 2. Backend (aplica las migraciones Flyway y carga los datos de prueba en el perfil dev; lee el .env de la raíz)
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
# PowerShell: .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"

# 3. Frontend (en otra terminal)
cd frontend
npm ci
npm run dev
```

El frontend llama siempre a la ruta relativa `/api/v1`. En desarrollo, el servidor de Vite (http://localhost:5173) reenvía `/api` al backend en `http://localhost:8080` (`server.proxy` en `frontend/vite.config.ts`), así que el navegador no hace peticiones de otro origen y no interviene CORS. En producción, nginx hace lo mismo.

#### Paso 3 — Credenciales de prueba

Con el perfil `dev` (opciones A y B) se carga `db/seed/R__demo_data.sql`: un usuario con perfil completo, tres ofertas ya analizadas (puntuaciones **42**, **68** y **85**) y tres candidaturas en el tablero (Guardada, Enviada con el seguimiento vencido y Entrevista).

| Credenciales de prueba | |
|---|---|
| Email | `demo@jobpilot.dev` |
| Contraseña | `Demo1234!` |

> Son credenciales ficticias, solo para revisar el proyecto en local. Nunca se suben secretos reales al repositorio.

#### Paso 4 — Recorrido sugerido por las 5 funcionalidades

En modo `fake`, la IA devuelve siempre las mismas respuestas: el perfil de ejemplo, la oferta del Ejemplo 1 de la sección 4 (con cualquier texto que pegues) y su encaje de **85**.

1. **Login** en http://localhost:5173 con `demo@jobpilot.dev` / `Demo1234!`. El dashboard ("Ofertas") muestra las tres ofertas del seed con su puntuación de color y el aviso de bloqueante en la de 42.
2. **F1 — Perfil desde el CV.** En "Perfil" se ve el perfil guardado. Sube **cualquier PDF con texto** (de hasta 5 MB y 10 páginas; vale cualquier documento "imprimido a PDF" desde el navegador) arrastrándolo o con "Selecciona tu CV en PDF". Confirma "Sí, reemplazar": el formulario se rellena con lo que ha extraído la IA. Edita algún campo y pulsa "Guardar perfil". Con un fichero que no sea PDF verás "Solo se admiten PDF de hasta 5 MB."
3. **F2 — Importar oferta.** "Ofertas" → "Nueva oferta". Pega un texto de al menos 200 caracteres, por ejemplo:

   > Buscamos Desarrollador/a Java Junior para nuestro equipo de producto en Valencia (modelo híbrido, 2 días en oficina). Requisitos: experiencia de al menos 1 año con Java y Spring Boot, conocimientos de SQL y PostgreSQL, uso de Git. Se valorará: experiencia con React, Docker y nivel de inglés B2. Ofrecemos 24.000 - 28.000 € brutos anuales.

   Pulsa "Analizar oferta": el detalle muestra el puesto (Desarrollador/a Java Junior), la ubicación (Valencia), la modalidad (híbrido), el salario (24.000–28.000 EUR) y los requisitos agrupados en obligatorios (3) y deseables (3). La empresa sale vacía porque el texto de ejemplo no la nombra (en modo fake, siempre).
4. **F3 — Análisis de encaje.** En el detalle, "Calcular encaje": puntuación **85** (verde), cada requisito con Cumple / Parcial / No cumple y su evidencia, y las sugerencias. Para ver el aviso de requisito bloqueante, abre la oferta del seed con 42.
5. **F4 — Carta y CV adaptado.** En el panel "Documentos" del mismo detalle, "Generar carta". Edita el texto, pulsa "Guardar cambios" (aparece la versión 2, "Editada por mí", en el historial) y "Exportar PDF" (se descarga `carta-v2.pdf`). La pestaña "CV adaptado" funciona igual ("Generar CV adaptado").
6. **F5 — Tablero.** En el detalle, "Guardar en el tablero" y después "Ver en el tablero" (o "Tablero" en el menú). Arrastra la tarjeta nueva (Desarrollador/a Java Junior, 85, en "Guardada"; la del seed con el mismo puesto está en "Entrevista") a "Enviada" y recarga: sigue en "Enviada" y su fecha de envío es la de hoy. Si intentas un movimiento no permitido (p. ej. de "Entrevista" a "Guardada"), la tarjeta vuelve a su sitio con un aviso. Con el teclado: tabula hasta el asa ⠿ de una tarjeta, Espacio para cogerla, flechas para moverla y Espacio para soltarla. Al hacer clic en una tarjeta se abre el panel de notas y fechas. La tarjeta "Enviada" del seed muestra "Seguimiento pendiente".

#### Usar la IA real (`LLM_MODE=real`)

**Sin API key todo funciona en modo `fake`** (el valor por defecto): es el modo con el que se revisa el proyecto, el que usan los tests y el único que admite el stack E2E. Para que la app llame a Claude, **cambia el `.env`**: es lo que manda, tanto en Docker Compose como con `mvnw spring-boot:run`.

```dotenv
LLM_MODE=real
ANTHROPIC_API_KEY=<tu clave>      # solo en .env, que está en .gitignore
ANTHROPIC_MODEL=                  # vacío = claude-sonnet-5-5
```

Después reinicia el backend: `docker compose up -d backend` (recrea el contenedor con el `.env` nuevo) o vuelve a lanzar `mvnw spring-boot:run`. Cuando termines, vuelve a poner `LLM_MODE=fake`.

- Con la IA real, cada llamada tarda varios segundos y el resultado depende del texto que subas o pegues. Hay un límite de 30 llamadas a la IA por hora y usuario (ver 2.5).
- **No uses el perfil de Spring `llm-live` para arrancar la app.** Solo existe en el classpath de test y es para `LlmLiveIT` (`-Pllm-live`, ver 2.6). Aunque se active, el `LLM_MODE=fake` del `.env` gana a su `llm.mode: real` y la app sigue en modo fake sin avisar.
- **Cómo comprobar que es la IA real:** con el perfil `dev`, cada llamada deja en el log del backend (`docker compose logs backend`) una línea como `LLM 'cv-extraction' respondió en 9613 ms (intento 1/2; tokens entrada=3083 salida=1594 parada=end_turn)`. En modo fake no aparece, y las respuestas son siempre las mismas y tardan milisegundos.
- Si `LLM_MODE=real` y falta la clave, la app no arranca (`LlmModeEnvironmentPostProcessor`). Con el perfil `e2e`, `real` no se admite.

#### Migraciones y datos de prueba (seed)

- Las migraciones están en `backend/src/main/resources/db/migration` (Flyway, `V1`–`V3`) y se aplican automáticamente al arrancar.
- `db/seed/` (usuario `demo@jobpilot.dev`) solo se carga con el perfil `dev`; `db/e2e-seed/` (usuario `e2e@jobpilot.test`), solo con el perfil `e2e`; el perfil `prod` no carga ninguno.

#### Ejecutar los tests

Requieren Java 21, Node 20.19+ y Docker en marcha (Testcontainers y el stack E2E).

**Backend — unitarios + integración** (Testcontainers arranca un PostgreSQL 16 real; incluye la regla de cobertura de JaCoCo):

```bash
cd backend && ./mvnw verify               # PowerShell: cd backend; .\mvnw.cmd verify
```

**Frontend — lint, unitarios de componentes (Vitest) y build:**

```bash
cd frontend && npm ci && npm run lint && npm test && npm run build
# PowerShell: cd frontend; npm ci; npm run lint; npm test; npm run build
```

**E2E — Playwright for Java contra el stack de `docker-compose.e2e.yml`** (proyecto `jobpilot-e2e`, perfil `e2e`, `LLM_MODE=fake` fijo y BD vacía en cada arranque; lee `POSTGRES_USER`, `POSTGRES_PASSWORD` y `JWT_SECRET` del mismo `.env`). Usa los puertos 5173 y 8080, así que antes hay que parar el stack de desarrollo (sin `-v`, para no borrar sus datos):

```bash
docker compose stop                                         # para el stack de desarrollo
docker compose -f docker-compose.e2e.yml up -d --build --wait
bash scripts/smoke-test.sh                                  # smoke test HTTP (nginx, CSP, login); en Windows, desde Git Bash
cd e2e && ./mvnw test                                       # PowerShell: cd e2e; .\mvnw.cmd test
cd e2e && ./mvnw test -Dheadless=false -DslowMo=600         # modo visible: abre Chromium y va más despacio
#                                                             PowerShell: .\mvnw.cmd test "-Dheadless=false" "-DslowMo=600"
docker compose -f docker-compose.e2e.yml down -v            # borra el stack E2E (no toca el de desarrollo)
docker compose up -d                                        # vuelve a levantar el stack de desarrollo
```

La primera ejecución de Playwright descarga Chromium. Por defecto va sin ventana y sin pausas. `-Dheadless=false` abre Chromium y `-DslowMo=<ms>` hace una pausa después de cada acción (clic, escritura, recarga, cada `mouse.move`…; los pasos intermedios de un mismo `mouse.move` no llevan pausa, así que el arrastre se ve fluido). Medido en local: la suite pasa de ~32 s a ~38 s con 600 ms; para un vídeo más pausado, sube el valor (p. ej. 1200). Las esperas máximas crecen con el `slowMo`, así que no fallan por ir despacio. Las mismas opciones se pueden dar con las variables de entorno `E2E_HEADLESS` y `E2E_SLOW_MO`. En PowerShell, cada `-D` va entre comillas.

**Validación con el LLM real** (solo a mano, gasta cupo de la API; ver 2.6):

```bash
cd backend && ./mvnw verify -Pllm-live    # sin ANTHROPIC_API_KEY los tests se saltan
```

Son los mismos pasos que la CI (`.github/workflows/ci.yml`, ver 2.4), que ahora se ejecutan en local porque GitHub Actions está desactivado en el repositorio (ver [Estado de la CI](#estado-de-la-ci-github-actions-desactivado)).

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

    subgraph BE["Backend · Spring Boot 4.1 · Java 21 (monolito modular)"]
        SEC["shared<br/>Spring Security (JWT) · límites Bucket4j<br/>errores ProblemDetail"] -.-> API
        API["Controladores REST<br/>+ validación DTO"] --> SVC["Servicios de dominio<br/>auth · profile · offer · analysis · document · tracker"]
        SVC --> REPO["Repositorios<br/>Spring Data JPA"]
        SVC --> PDFX["CvTextExtractor<br/>Apache PDFBox"]
        SVC --> PDFG["PdfRenderer<br/>OpenHTMLtoPDF"]
        SVC --> PORT["ai · LlmGateway<br/>(puerto / interfaz)"]
        PORT --> REAL["SpringAiLlmGateway<br/>(adaptador real · Spring AI)"]
        PORT --> FAKE["FakeLlmGateway<br/>(modo fake · tests · e2e)"]
    end

    REPO --> DB[("PostgreSQL 16<br/>migraciones Flyway")]
    REAL -- "HTTPS" --> LLM["API de Anthropic<br/>Claude"]
```

**Patrón elegido: monolito modular en capas + SPA desacoplada.**

- El **backend** es un único despliegue de Spring Boot organizado **por funcionalidad** (*package-by-feature*): `auth`, `profile`, `offer`, `analysis`, `document` y `tracker`, más dos módulos transversales: `ai` (el puerto `LlmGateway` y sus adaptadores) y `shared` (configuración, seguridad, límites de peticiones, validación y manejo de errores). Dentro de cada módulo de funcionalidad se respetan las capas clásicas **Controller → Service → Repository** (`web/`, `service/`, `domain/`, `repository/`).
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
| Framework backend | **Spring Boot 4.1.2** (Spring Web MVC, Spring Security, Spring Data JPA, Bean Validation, Actuator) + springdoc-openapi 3.2 | Estándar del mercado Java; Spring Boot 3.5 dejó de tener soporte OSS en junio de 2026. |
| Autenticación | **Spring Security OAuth2 Resource Server** (Nimbus JOSE + JWT) | Emite y valida los JWT HS256 con `JwtEncoder`/`JwtDecoder` de Spring, sin librerías JWT de terceros ni un filtro propio; compatible con el Jackson 3 de Spring Boot 4. |
| Integración IA | **Spring AI 2.0.2** (`spring-ai-starter-model-anthropic`) con **Claude** — por defecto `claude-sonnet-5-5`, `max-tokens` 4096, configurable con `ANTHROPIC_MODEL` | Abstracción oficial de Spring para LLMs; Spring AI 2.0 requiere Spring Boot 4 (la 2.0.2 está compilada contra Boot 4.1.2). Sonnet es más barato y rápido que Opus y suficiente para extracción y encaje. |
| Base de datos | **PostgreSQL 16** + **Flyway** | Relacional, ACID, JSONB para los campos semiestructurados y migraciones versionadas. |
| Frontend | **React 19** + **TypeScript** + **Vite** | SPA moderna y tipada; React es lo más demandado junto a Java en las ofertas. |
| UI / estado | Tailwind CSS 4 · TanStack Query 5 · React Router 7 · React Hook Form 7 + Zod 4 · dnd-kit (`core` 6, `sortable` 10) | Productividad, caché del servidor, rutas protegidas, formularios validados y kanban accesible. |
| Herramientas frontend | Vite 8 · TypeScript 6.0 (`strict`) · ESLint 9 (typescript-eslint, react-hooks, jsx-a11y) · Vitest 5 + React Testing Library + MSW 2 | TypeScript 6.0 y ESLint 9 porque typescript-eslint aún no admite TypeScript 7 y jsx-a11y aún no admite ESLint 10. |
| Documentos | Apache PDFBox 3.0 (leer el CV) · CommonMark 0.32 + OpenHTMLtoPDF 1.1 (Markdown → HTML → PDF) | Librerías Java maduras y sin servicios externos. |
| Límites de uso | Bucket4j 8.14 | Límite de llamadas a la IA por usuario y de intentos de login, en memoria (ver 2.5). |
| Tests | JUnit 6 (Jupiter 6.0.3) · Mockito · AssertJ · Testcontainers · MockMvc · JaCoCo · Vitest 5 · React Testing Library · MSW 2 · Playwright for Java 1.64 | Cubren unitarios, integración y E2E con el mismo lenguaje en el backend y el E2E. |
| Infraestructura | Docker · Docker Compose · GitHub Actions (desactivado por GitHub, ver 2.4) · Dependabot | Puesta en marcha en local con un comando y CI en cada push. |


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
| **Módulo `shared`** | Spring Security, Bucket4j, Bean Validation | Configuración (`Clock` de `Europe/Madrid`, OpenAPI, comprobación de variables obligatorias), seguridad JWT, límites de peticiones, validadores comunes y el `@RestControllerAdvice` único que devuelve `ProblemDetail`. |
| **Tests** | JUnit 6, Mockito, AssertJ, Testcontainers, MockMvc, JaCoCo, Vitest, React Testing Library, MSW, Playwright for Java | Ver sección 2.6. |
| **CI** | GitHub Actions | Build, tests y lint en cada push y PR (desactivado por GitHub desde el 11/11/2026; se verifica en local con los mismos pasos, ver 2.4). |

### **2.3. Descripción de alto nivel del proyecto y estructura de ficheros**

Monorepo con tres proyectos: `backend` (Maven), `frontend` (npm) y `e2e` (Maven + Playwright). El backend sigue *package-by-feature* con capas dentro de cada módulo; el frontend se organiza en `features/` con la misma división funcional, de modo que cada historia de usuario se localiza fácilmente en ambos lados.

```
AI4Devs-finalproject/
├── backend/
│   ├── pom.xml · mvnw · Dockerfile
│   └── src/
│       ├── main/
│       │   ├── java/com/jobpilot/
│       │   │   ├── JobPilotApplication.java
│       │   │   ├── auth/            # registro, login, emisión del JWT
│       │   │   ├── profile/         # F1: perfil, habilidades, experiencias, CvTextExtractor, CvDraftStore
│       │   │   ├── offer/           # F2: ofertas y requisitos
│       │   │   ├── analysis/        # F3: análisis de encaje, MatchScoreCalculator
│       │   │   ├── document/        # F4: carta / CV adaptado, versiones, PdfRenderer
│       │   │   ├── tracker/         # F5: candidaturas, ApplicationStatusPolicy, historial
│       │   │   │                    #     (cada uno con web/ · service/ · domain/ · repository/)
│       │   │   ├── ai/              # LlmGateway (puerto), SpringAiLlmGateway, FakeLlmGateway, validadores
│       │   │   └── shared/          # config, security, ratelimit, validation, web, error (GlobalExceptionHandler)
│       │   └── resources/
│       │       ├── application.yml  # + application-dev.yml, application-e2e.yml, application-prod.yml
│       │       ├── db/migration/    # V1__init_schema.sql, V2__work_mode_not_null.sql, V3__match_analyses_summary_suggestions.sql
│       │       ├── db/seed/         # R__demo_data.sql (solo perfil dev)
│       │       ├── db/e2e-seed/     # R__e2e_data.sql (solo perfil e2e)
│       │       ├── prompts/         # _system-base.st, cv-extraction.st, offer-extraction.st, match.st,
│       │       │                    # document-carta.st, document-cv-adaptado.st
│       │       └── fake-llm/        # respuestas fijas del modo fake (JSON), usadas en dev, tests y E2E
│       └── test/
│           ├── java/com/jobpilot/   # unitarios (*Test) e integración (*IT)
│           └── resources/           # config de tests, respuestas y prompts solo para tests, fixtures/llm-live/ (LlmLiveIT)
├── frontend/
│   ├── package.json · vite.config.ts · eslint.config.js
│   ├── Dockerfile · nginx.conf      # build de Vite servido por nginx (cabeceras de seguridad y proxy /api/)
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
├── e2e/                             # tests E2E con Playwright for Java (pom.xml, mvnw, MainFlowE2ETest, RegistrationE2ETest)
├── docs/screenshots/                # capturas de la app y de la CI
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
```

- `backend` y `frontend` se ejecutan en paralelo; `e2e` solo si los dos pasan. No hay job de despliegue (fuera de alcance, ver más abajo).
- El job `e2e` genera en cada ejecución credenciales de un solo uso (`POSTGRES_USER`, `POSTGRES_PASSWORD`, `JWT_SECRET`), comprueba que `docker-compose.e2e.yml` fija `LLM_MODE=fake` aunque el entorno diga `real`, levanta el stack con `--wait`, ejecuta `scripts/smoke-test.sh` y después la suite de Playwright (`e2e/`, JP-QA-01): instala Chromium con sus dependencias (`./mvnw exec:java -Dexec.args="install --with-deps chromium"`) y ejecuta `./mvnw test`. Si falla, sube como artefacto las trazas de Playwright y las capturas (`e2e/target/e2e-artifacts/`).
- La CI no usa secretos del repositorio ni llama a la API de Anthropic.
- Imágenes: `backend/Dockerfile` (Maven → `eclipse-temurin:21-jre`, usuario sin privilegios, *healthcheck* con `curl` contra `/actuator/health`; si la imagen base dejara de traer `curl`, se instala al construir) y `frontend/Dockerfile` (Node 22 → `nginx:1.28-alpine`; `nginx.conf` sirve la SPA, reenvía `/api/` al backend y añade las cabeceras de seguridad de 2.5).

#### Estado de la CI: GitHub Actions desactivado

El **11/11/2026** GitHub desactivó Actions en el repositorio. La pestaña *Actions* muestra *"GitHub Actions is currently disabled for this repository. Please reach out to GitHub Support for assistance."* y el soporte de las cuentas gratuitas no revisa el caso.

- **Hasta entonces, las 41 ejecuciones del workflow `CI` terminaron en verde** (los tres jobs). La n.º 42, la del push de JP-BE-07, se quedó en cola y no llegó a ejecutarse.

  ![GitHub Actions desactivado: 41 ejecuciones en verde y la 42 en cola](docs/screenshots/github-actions-desactivado.png)

- **Desde entonces se verifica en local con los mismos pasos que `ci.yml`.** Son los de "Ejecutar los tests" de la sección 1.4:
  - `backend`: `./mvnw -B -ntp verify`.
  - `frontend`: `npm ci`, `lint`, `test` y `build`.
  - `e2e`: credenciales de un solo uso, comprobación de que el stack fuerza `LLM_MODE=fake`, `docker compose -f docker-compose.e2e.yml up -d --build --wait`, smoke test, Playwright y `down -v`.

  Así se fusionaron las PRs #20 (JP-BE-07) y #21 (JP-FE-05), con los tres jobs en verde a la primera:

  ![Verificación local equivalente a ci.yml](docs/screenshots/ci-local-verificacion.png)

- **El workflow sigue en el repositorio** (`.github/workflows/ci.yml`) sin cambios y volverá a ejecutarse en cada push y PR si GitHub reactiva Actions.

**Despliegue: fuera de alcance**

El despliegue público es opcional en el máster y no se ha hecho. La evidencia de funcionamiento es el vídeo del flujo principal (ver 0.4) y el repositorio, que se arranca en local con un solo comando, credenciales de prueba y pasos de verificación (sección 1.4). Si se desplegara, este era el diseño previsto:

1. Solo se despliega si el pipeline de CI pasa.
2. El backend se construye con su imagen Docker (build multi-stage: Maven → JRE 21) y se despliega en un PaaS (p. ej. **Render**, como *Web Service*) con un **PostgreSQL gestionado**.
3. El frontend se despliega con su imagen de nginx (`frontend/Dockerfile`), igual que en local: llama a la ruta relativa `/api/v1` y nginx la reenvía al backend, así que no hay CORS ni hace falta cambiar `VITE_API_BASE_URL`. Habría que parametrizar el `proxy_pass` de `nginx.conf`, que apunta a `backend:8080` (el nombre del servicio en Compose).
4. Las migraciones de Flyway se aplican solas al arrancar el backend.
5. Los secretos (`JWT_SECRET`, `ANTHROPIC_API_KEY`, credenciales de la BD) se configuran como variables de entorno de la plataforma y nunca se versionan. El perfil `prod` no arranca si falta alguno (`RequiredEnvironmentCheck`) y no carga datos de prueba.
6. El endpoint `/actuator/health` sirve como *health check* del servicio.

### **2.5. Seguridad**

| Práctica | Implementación |
|---|---|
| **Contraseñas** | Hash con BCrypt (coste 12). Nunca se devuelven ni se registran en los logs. Política mínima: 8 caracteres, con letras y números. |
| **Autenticación sin estado** | JWT firmado con HS256, caducidad de 60 min y secreto de al menos 256 bits leído de una variable de entorno. Se envía en la cabecera `Authorization: Bearer …`. |
| **Autorización por propietario** | Todas las consultas filtran por el `userId` del token (`findByIdAndUserId`). Si un recurso pertenece a otro usuario se responde `404` (no `403`) para no revelar que existe. Hay tests de integración específicos para esto. |
| **Validación de entrada** | DTOs con Bean Validation (`@NotBlank`, `@Size`, `@Email`…). Ejemplo: el texto de una oferta debe tener entre 200 y 20.000 caracteres. El frontend valida también con Zod, pero la validación del backend es la que manda. |
| **Subida de ficheros** | Solo se aceptan PDF de hasta 5 MB y 10 páginas; se comprueba el tipo MIME y la firma `%PDF`. El fichero no se guarda en disco (ni siquiera en temporales: multipart y PDFBox trabajan en memoria): se extrae su texto y se descarta. Todo se comprueba antes de llamar a la IA, así que un fichero rechazado no gasta cupo. El texto extraído (máx. 50.000 caracteres) espera en memoria 30 minutos a que el usuario confirme el perfil con `PUT /profile`, y solo entonces se guarda en `cv_raw_text` (JP-BE-04). |
| **Exportación a PDF** | El Markdown de la carta y del CV no es de fiar (lo escribe el usuario o la IA a partir de una oferta pegada). CommonMark escapa el HTML crudo (`escapeHtml`): un `<script>` o un `<img>` se ven como texto. Las imágenes de Markdown no se insertan (se escribe su texto alternativo) y solo se conservan los enlaces `http`, `https` y `mailto` (de `javascript:`, `file:`, `data:`... queda solo el texto). Además, OpenHTMLtoPDF no puede cargar ningún recurso externo: un control de acceso que lo rechaza todo antes y después de resolver la URI, un resolvedor que no resuelve nada y ninguna implementación de protocolos (http, https, file, data, jar...), así que no hay SSRF ni lectura de ficheros del servidor. Lo cubre `PdfRendererTest` con un servidor HTTP local que cuenta las peticiones (JP-BE-07). |
| **Mitigación de *prompt injection*** | El texto del CV y de la oferta se envía delimitado y marcado como *datos* en el prompt de sistema. El modelo no tiene herramientas ni acceso a nada. Su respuesta se valida contra un esquema estricto antes de usarla, y la puntuación la calcula Java, no el modelo. |
| **Sin SSRF** | El backend nunca descarga la URL que introduce el usuario; solo la guarda como texto. |
| **Límite de peticiones** | Única fuente de verdad de los límites de uso (IA y login). Por usuario: 30 peticiones por hora a los endpoints que llaman al LLM, con Bucket4j; si se supera, `429` con `Retry-After`. Global: un `Semaphore` permite como máximo 3 llamadas simultáneas al LLM; si no hay hueco, `503` con `Retry-After`. Esto protege del abuso, controla el coste y evita agotar los hilos del servidor. **Login:** máximo 10 intentos por minuto por IP + email en `POST /auth/login` (cuentan todos, correctos o no, y se comprueba antes de la contraseña), también con Bucket4j; si se supera, `429` con `Retry-After`, para frenar los ataques de fuerza bruta. Los cubos se recargan de forma progresiva (30/h = una petición cada 2 min), las peticiones que no pasan la validación (400) o que no llegan a la IA (404, 409) no gastan cupo, y el 429 es `rate-limit-exceeded`. La IP del cliente la da Tomcat (`server.forward-headers-strategy: native`): solo acepta `X-Forwarded-For` si la petición llega desde un proxy interno (nginx en la red de Docker), y nginx sustituye esa cabecera en lugar de añadir a la que mande el cliente (JP-BE-05). |
| **CORS** | Solo se permite el origen del frontend (`CORS_ALLOWED_ORIGINS`). |
| **Cabeceras de seguridad (nginx)** | `frontend/nginx.conf` envía `Content-Security-Policy`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, `X-Frame-Options: DENY` y `Permissions-Policy`. La CSP es `default-src 'self'` con **`script-src 'self'` estricto** (sin scripts en línea ni `eval`): es la mitigación principal frente a un XSS que intentara leer el JWT de `localStorage`. **`style-src` permite `'unsafe-inline'`** porque dnd-kit (tablero, JP-FE-06) mueve las tarjetas con estilos en línea (`style="transform: …"`); los estilos en línea no ejecutan código, y el riesgo que queda (inyección de CSS) es mucho menor. Además: `connect-src 'self'`, `object-src 'none'`, `base-uri 'self'`, `form-action 'self'` y `frame-ancestors 'none'`. |
| **Gestión de secretos** | Variables de entorno; `.env` está en `.gitignore` y `.env.example` va con los valores vacíos. |
| **Errores seguros** | Manejador global (`@RestControllerAdvice`) que devuelve `ProblemDetail` sin trazas internas. |
| **Datos personales** | El CV contiene datos personales: solo se guarda lo necesario (texto extraído y perfil), se borra en cascada al eliminar la cuenta y nunca se envía al LLM nada que no haga falta para la tarea. Tampoco el nombre ni el email: la carta generada termina en la despedida y el servidor añade el `full_name` como firma antes de guardarla (JP-BE-07). |
| **Dependencias** | Dependabot (`.github/dependabot.yml`) para Maven, npm, GitHub Actions y las imágenes Docker: revisión semanal, un PR agrupado por ecosistema y como mucho 3 PRs abiertos por ecosistema. |

### **2.6. Tests**

La estrategia sigue la pirámide de tests y la recomendación del máster para sistemas con LLM: **los componentes que rodean al modelo se prueban de forma determinista con respuestas simuladas**, y las salidas reales del modelo se validan por estructura y reglas, no comparando el texto exacto.

**Estado de la suite** (recuentos del cierre de la Entrega 3, 11/11/2026: todo en verde, 0 fallos)

| Capa | Herramientas | Tests | Notas |
|---|---|---|---|
| Backend: unitarios | JUnit 6, AssertJ, Mockito | 437 | Incluye `MatchScoreCalculatorTest` (13 casos parametrizados), validadores de las respuestas del LLM, `StructuredLlmCall`, traducción de errores, límites de peticiones y el perfil (`CvTextExtractorTest` con PDFs generados en el test, `CvDraftStoreTest`, `CvExtractionParserTest`, `ProfileServiceTest`) y `AnthropicRequestOptionsTest`, que construye la petición con el `AnthropicChatModel` real (sin red) para comprobar que las opciones por llamada llegan a Anthropic. Documentos (JP-BE-07): `DocumentServiceTest`, `PdfRendererTest` (contenido, nombre en la cabecera y seguridad del PDF), `GeneratedDocumentValidatorTest` y `GeneratedDocumentParserTest`. |
| Backend: integración | Spring Boot Test, Testcontainers (PostgreSQL 16), MockMvc | 279 | Migraciones y restricciones de la BD, auth/JWT, perfil, ofertas, análisis, documentos, propiedad de los datos (404 con recursos de otro usuario) y mapeo de errores de IA. |
| Cobertura | JaCoCo | 100 % de líneas en `analysis.domain` | Regla `check` (≥ 90 %) en `mvnw verify`, como pide la definición de hecho de JP-BE-06. |
| Frontend | Vitest, React Testing Library, MSW | 262 (23 ficheros) | Cliente API (también la subida multipart y la descarga de ficheros con `apiDownload`), sesión, formularios, dashboard, detalle, panel de análisis, onboarding del perfil (`ProfilePage.test.tsx`: subida, errores del fichero y de la IA, edición, guardado y confirmación al volver a subir; `schemas.test.ts` y `cvFile.test.ts` del perfil) y tablero: `canTransition`, helpers del tablero, schema del panel lateral, "Guardar en el tablero" (`SaveToBoard.test.tsx`, también con un 409) y `KanbanBoard.test.tsx`. Este último mueve tarjetas con el `KeyboardSensor` real de dnd-kit sobre un layout simulado, comprueba que una transición prohibida se bloquea sin llamar al backend y falla si hay errores o avisos en la consola. Documentos (JP-FE-05): `DocumentsPanel.test.tsx` (pestañas con el teclado, generar sin análisis y con errores de la IA, editar y guardar como versión nueva, validación, conflicto de versiones, confirmación al regenerar, exportar con el nombre del servidor y desactivado con cambios sin guardar, y que el contenido nunca se pinta como HTML) y el `schemas.test.ts` de documentos. |
| Stack completo | `scripts/smoke-test.sh` contra `docker-compose.e2e.yml` | Smoke test HTTP | Health, nginx (fallback de la SPA, CSP, proxy `/api`), login y comprobación de que el JS no lleva una URL absoluta de la API. |
| E2E (JP-QA-01, JP-FE-02, JP-FE-05) | Playwright for Java + JUnit (`e2e/`) contra `docker-compose.e2e.yml` | 2 | Flujo principal completo (ver abajo), desde el registro y la subida del CV hasta la carta, el PDF y el tablero, y registro. |
| Validación con el LLM real | `LlmLiveIT` (`@Tag("llm-live")`) | Fuera de `verify` | Solo a mano, con `-Pllm-live` y API key (ver abajo). |
| CI | GitHub Actions | 3 jobs | `backend` y `frontend` en paralelo; `e2e` (smoke test + Playwright) si los dos pasan. GitHub Actions está desactivado desde el 11/11/2026: se ejecutan los mismos pasos en local (ver [Estado de la CI](#estado-de-la-ci-github-actions-desactivado)). |

Además, en cada ticket se hizo una **prueba manual con el stack real** (perfil `dev` o Docker Compose). Detectó problemas que los tests automáticos no veían: un `.env` mal formado, una URL de la API absoluta que la CSP bloqueaba, la IP del cliente falsificable a través de nginx y un error de validación de la URL que no se mostraba.

Todo lo que se describe a continuación está implementado.

**Unitarios (JUnit 6 + Mockito + AssertJ / Vitest + React Testing Library)**

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
- `DocumentControllerIT` (JP-BE-07): generar la carta → 201 en la versión 1, firmada por el servidor con el nombre del usuario (que la IA no recibe); regenerar → versión 2, con numeración propia por tipo; sin análisis → 409 `analysis-required` sin llamar a la IA; fallos de la IA → 502/503/504 sin guardar nada; historial con la última versión completa y las demás sin contenido; editar → versión nueva con `editedByUser: true`, y editar a partir de una versión que ya no es la última → 409 `document-version-conflict`; PDF descargable con el contenido y el nombre en la cabecera; documentos u ofertas de otro usuario → 404.
- **Propiedad de los datos:** cada `*ControllerIT` (`OfferControllerIT`, `AnalysisControllerIT`, `DocumentControllerIT`, `ProfileControllerIT`, `ApplicationControllerIT`) comprueba que el usuario A no puede leer, analizar, editar ni mover recursos del usuario B (→ 404).
- `ApplicationControllerIT`: crear candidatura → 201 en GUARDADA con historial `from = NULL`, y crearla de nuevo para la misma oferta → 409; cambio de estado válido → 200 y se registra en el historial; mismo estado en `PATCH /status` → 400; transición no permitida → 409; `PATCH /applications/{id}` con una `version` antigua → 409.

**E2E (Playwright for Java contra `docker-compose.e2e.yml` con `LLM_MODE=fake`)**

- **Flujo principal:** registro → subir el CV de ejemplo → revisar el perfil → pegar una oferta de ejemplo → comprobar que se ven los requisitos y la puntuación esperada → generar la carta → exportar el PDF (se verifica la descarga) → guardar en el tablero → arrastrar la tarjeta a "Enviada" → recargar la página y comprobar que se ha guardado.
- **Implementado en JP-QA-01, JP-FE-02 y JP-FE-05** (`MainFlowE2ETest`): registro de una cuenta nueva → `/perfil` → subir un CV ficticio (`SampleCv`: un PDF de una página con texto, generado en memoria en el propio test, sin fichero binario en el repositorio) → revisar el borrador del LLM fake (titular y 5 habilidades) → cambiar la ubicación → guardar (se espera el `201` del `PUT /profile`) → dashboard sin el aviso "Crea tu perfil" → pegar la oferta → 3 requisitos obligatorios y 3 deseables → calcular el encaje (85, con 6 filas) → **generar la carta** (menciona el puesto y termina con la firma del servidor: el nombre de la cuenta) → añadir una línea ("Exportar PDF" se desactiva) → guardar (se espera el `201` del `POST /documents/{id}/versions`) → "Versión 2 · Editada por mí" → **exportar el PDF** (`page.waitForDownload`: `carta-v2.pdf`, no vacío y empieza por `%PDF-`; el contenido y el nombre en la cabecera los prueban `PdfRendererTest` y `DocumentControllerIT`) → "Guardar en el tablero" → la tarjeta aparece en "Guardada" → arrastrarla con el ratón a "Enviada" (se espera la respuesta del `PATCH /status`) → recargar → sigue en "Enviada". `RegistrationE2ETest` prueba que una cuenta nueva va al onboarding y que, si sale sin crear el perfil, el dashboard se lo recuerda.
- **Seed de E2E** (`db/e2e-seed/R__e2e_data.sql`, solo perfil `e2e`): usuario `e2e@jobpilot.test` / `Demo1234!` con perfil y sin ofertas. Se creó porque un usuario recién registrado no podía analizar ofertas sin la subida del CV (`409 profile-required`); desde JP-FE-02 ningún test E2E lo usa (los dos crean su propia cuenta), pero se mantiene para probar a mano el stack E2E con un usuario que ya tiene perfil. Está fuera de `db/seed/` porque Flyway busca también en las subcarpetas y el perfil `dev` lo cargaría. Lo cubre `E2eSeedIT`, y `SeedDataIT` comprueba que `dev` no lo carga.
- **Repetible sobre el mismo stack:** cada ejecución registra sus propias cuentas (email aleatorio), importa su propia oferta y localiza su tarjeta por `data-offer-id`. Cada cuenta gasta 4 llamadas a la IA (CV, oferta, encaje y carta), muy por debajo del cupo por usuario; el perfil `e2e` lo sigue subiendo a 10.000/h por si un test vuelve a usar el usuario del seed. La suite ya no hace login (el registro devuelve el token), así que el límite de login (10/min) no interviene.
- **Sin esperas fijas:** solo el auto-wait de Playwright, `assertThat(...)` y `waitForResponse`. El arrastre no usa `dragTo()`: el `PointerSensor` de dnd-kit no empieza hasta mover el puntero 5 px con el botón pulsado, así que se hace `mouse.down` → `mouse.move` en varios pasos → `mouse.up`, desde el asa ⠿ de la tarjeta.
- **Opciones** (propiedad `-D` de Maven o variable de entorno): `frontendUrl` / `FRONTEND_URL` (por defecto `http://localhost:5173`), `headless` / `E2E_HEADLESS` (`true`) y `slowMo` / `E2E_SLOW_MO` (ms, `0`). Ver 1.4. Si un test falla, su traza (`npx playwright show-trace <zip>`) y una captura quedan en `e2e/target/e2e-artifacts/`.

**Validación con el LLM real (manual / *smoke*, fuera de la CI)**

- `LlmLiveIT` (`@Tag("llm-live")`, JP-AI-01) arranca la aplicación con `llm.mode=real` y llama al `SpringAiLlmGateway` real. Hace la extracción sobre 3 ofertas ficticias cortas (`src/test/resources/fixtures/llm-live/`), evalúa el encaje de una de ellas con un perfil de ejemplo y, con ese encaje, genera una carta (JP-BE-07). Los criterios son flexibles y nunca se compara texto exacto:
  - las respuestas pasan Bean Validation y los validadores existentes (`OfferExtractionValidator`, `MatchEvaluationValidator`);
  - cada oferta tiene al menos un requisito obligatorio y una modalidad válida;
  - cada requisito tiene exactamente un resultado, y las evidencias de `CUMPLE` y `PARCIAL` no están vacías;
  - hay entre 3 y 5 sugerencias;
  - la puntuación de `MatchScoreCalculator` está entre 0 y 100;
  - la carta cumple la HU-04: 250–400 palabras (el rango exacto del prompt; la aplicación solo la rechaza fuera de 200–500), acaba en "Atentamente," (oferta en español) sin firma ni huecos entre corchetes, menciona el puesto y al menos dos requisitos que el candidato cumple.
- **Coste:** 5 llamadas por ejecución (3 extracciones, 1 encaje que reutiliza una de ellas y 1 carta que reutiliza el encaje), más un posible reintento por respuesta no válida.
- **Excluido de `mvnw verify` y de la CI** (`excludedGroups` en Surefire y Failsafe). Se ejecuta a mano:

  ```powershell
  cd backend; .\mvnw.cmd verify -Pllm-live
  ```

  El perfil Maven `llm-live` solo ejecuta ese grupo, y salta los unitarios y JaCoCo. Necesita Docker para el PostgreSQL de Testcontainers. La clave se lee de `ANTHROPIC_API_KEY`: de una variable de entorno o, si no está, del `.env` de la raíz (perfil de Spring `llm-live`, con `spring.config.import`, como el perfil `dev`). Así no hay que escribirla en la terminal ni queda en el historial. El test fuerza `llm.mode=real` (`@SpringBootTest(properties = ...)`), así que funciona aunque el `.env` diga `LLM_MODE=fake`; para usar la IA real en la app, ver "Usar la IA real" en 1.4. Si no hay clave, los tests aparecen como saltados y el build no falla. El modelo es el de `ANTHROPIC_MODEL` o, si está vacío, `claude-sonnet-5-5`.

**Deuda técnica:** consolidada en la [sección 8](#8-deuda-técnica-y-fuera-de-alcance).

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
| 409 | `analysis-required` | La oferta no tiene análisis de encaje | `POST /offers/{offerId}/documents` sobre una oferta que aún no tiene ningún análisis (no llega a la IA ni gasta cupo) |
| 409 | `document-version-conflict` | El documento ha cambiado | `POST /documents/{documentId}/versions` a partir de una versión que ya no es la última (bloqueo optimista: la versión siguiente a la de `documentId` ya existe), o una regeneración que choca dos veces con otra escritura simultánea |
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
| POST | `/offers/{offerId}/documents` 🤖 | `CreateDocumentRequest` `{type}` | 201 `DocumentResponse`: versión nueva generada por la IA a partir del perfil, la oferta y su último análisis, con el número siguiente al más alto de ese tipo y `editedByUser: false`. La carta se guarda firmada: la IA termina en la despedida y el servidor añade el `full_name` del usuario debajo, con los caracteres de Markdown escapados para que se vea literal (la IA no lo recibe); el CV adaptado, tal cual. Si otra escritura se queda ese número a la vez, se recalcula una vez sin volver a llamar a la IA | 400, 404, 409 `analysis-required` / `document-version-conflict` | F4 — Generar carta o CV adaptado |
| GET | `/offers/{offerId}/documents?type=CARTA\|CV_ADAPTADO` | — | 200 `DocumentHistoryResponse` (última versión completa + lista de versiones) | 400 (falta `type` o no es válido), 404 (también si la oferta aún no tiene ningún documento de ese tipo) | F4 — Ver el documento y su historial |
| POST | `/documents/{documentId}/versions` | `CreateDocumentVersionRequest` `{contentMarkdown}` | 201 `DocumentResponse` con id nuevo, versión = la de `documentId` + 1 y `editedByUser: true` | 400, 404, 409 `document-version-conflict` (otra edición o regeneración ha creado esa versión antes) | F4 — Guardar la edición como versión nueva |
| GET | `/documents/{documentId}/pdf` | — | 200 `application/pdf` generado en el momento, con el nombre del usuario en la cabecera; `Content-Disposition: attachment; filename="carta-v{n}.pdf"` (o `cv-adaptado-v{n}.pdf`) y `Cache-Control: no-store` | 404 | F4 — Exportar a PDF |
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
        contentMarkdown: { type: string, minLength: 1, maxLength: 20000, description: No puede estar vacío ni tener solo espacios }

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

**Backlog del MVP (vista general y estado final)**

Los 18 tickets del backlog están hechos y fusionados en `main`. Lo que queda fuera (S1, S2, despliegue) y la deuda técnica están en la [sección 8](#8-deuda-técnica-y-fuera-de-alcance).

| ID | Tipo | Título | HU | SP | Estado |
|---|---|---|---|---|---|
| JP-DB-01 | Base de datos | Esquema inicial con Flyway y datos de prueba | Todas | 3 | ✅ Hecho (PR #2) |
| JP-BE-01 | Backend | Scaffolding Spring Boot, configuración, manejo global de errores | — | 2 | ✅ Hecho (PR #1) |
| JP-BE-02 | Backend | Registro, login y filtro JWT | — | 3 | ✅ Hecho (PR #3) |
| JP-BE-03 | Backend | Puerto `LlmGateway`, adaptador Spring AI y `FakeLlmGateway` | 1–4 | 3 | ✅ Hecho (PR #4) |
| JP-BE-04 | Backend | Subida de CV y extracción de perfil | HU-01 | 5 | ✅ Hecho (PR #18) |
| JP-BE-05 | Backend | Importación de ofertas y extracción de requisitos + límite de peticiones (Bucket4j: IA y login, ver 2.5) | HU-02 | 3 | ✅ Hecho (PR #8) |
| **JP-BE-06** | **Backend** | **Análisis de encaje: `MatchScoreCalculator` y endpoint** | HU-03 | 5 | ✅ Hecho (PR #9) |
| JP-BE-07 | Backend | Generación, versionado y exportación PDF de documentos | HU-04 | 5 | ✅ Hecho (PR #20) |
| JP-BE-08 | Backend | API de candidaturas y política de transiciones | HU-05 | 3 | ✅ Hecho (PR #15) |
| JP-FE-01 | Frontend | Scaffolding Vite + React, router, cliente API, auth | — | 3 | ✅ Hecho (PR #6) |
| JP-FE-02 | Frontend | Onboarding: subida de CV y formulario de perfil | HU-01 | 5 | ✅ Hecho (PR #19) |
| JP-FE-03 | Frontend | Dashboard y formulario de nueva oferta | HU-02 | 3 | ✅ Hecho (PR #10) |
| JP-FE-04 | Frontend | Detalle de oferta y panel de análisis | HU-03 | 5 | ✅ Hecho (PR #11) |
| JP-FE-05 | Frontend | Editor de documentos y exportación | HU-04 | 3 | ✅ Hecho (PR #21) |
| **JP-FE-06** | **Frontend** | **Tablero kanban de candidaturas** | HU-05 | 5 | ✅ Hecho (PR #16) |
| JP-QA-01 | Tests | Suite E2E del flujo principal con Playwright | Todas | 3 | ✅ Hecho (PR #17) |
| JP-OPS-01 | DevOps | Docker Compose y pipeline de CI en GitHub Actions | — | 3 | ✅ Hecho (PR #7); el deploy opcional, fuera de alcance |
| JP-AI-01 | Tests | Validación con el LLM real (`@Tag("llm-live")`, fuera de la CI) | HU-02, HU-03 | 2 | ✅ Hecho (PR #14) |

A continuación se detallan los tres tickets representativos que pide la plantilla: uno de base de datos, uno de backend y uno de frontend.

### Ticket 1 — JP-DB-01: Esquema inicial de base de datos con Flyway y datos de prueba

**Tipo:** Base de datos · **Prioridad:** Alta (bloquea al resto de tickets de backend) · **Estimación:** 3 SP · **Historias:** todas  
**Estado:** ✅ Hecho — PR #2

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

- [x] `./mvnw spring-boot:run` contra una BD vacía crea el esquema sin errores y Hibernate lo valida.
- [x] Con el perfil `dev`, se puede iniciar sesión con las credenciales de prueba y el tablero muestra 3 candidaturas.
- [x] Falla por restricción: insertar un email con mayúsculas o duplicado, una puntuación de 101, un estado no válido, un salario sin moneda, una candidatura ENVIADA sin `applied_at` o un historial con el mismo estado de origen y destino.
- [x] Falla por FK compuesta: un `requirement_match` cuyo requisito es de otra oferta, o una candidatura cuyo `user_id` no es el propietario de la oferta.
- [x] Borrar un usuario elimina en cascada su perfil, ofertas, análisis, documentos y candidaturas.
- [x] El perfil `prod` no carga los datos de prueba.

**Tests**

- `SchemaMigrationIT` (Testcontainers con `postgres:16`): aplica las migraciones y comprueba que existen todas las tablas e índices consultando `information_schema`.
- `ConstraintsIT`: un caso por restricción crítica, incluidos los dos de FK compuesta (se espera `DataIntegrityViolationException`).
- `CascadeDeleteIT`: borra un usuario y comprueba que quedan 0 filas relacionadas.

**Definición de hecho:** migraciones revisadas, tests en verde en la CI (o, desde que GitHub desactivó Actions, en la verificación local equivalente, ver 2.4), diagrama de la sección 3 actualizado si ha cambiado algo, PR aprobado.

### Ticket 2 — JP-BE-06: Análisis de encaje (`MatchScoreCalculator` y `POST /offers/{offerId}/analysis`)

**Tipo:** Backend · **Prioridad:** Alta (núcleo del producto) · **Estimación:** 5 SP · **Historia:** HU-03
**Dependencias:** JP-DB-01, JP-BE-03 (`LlmGateway`), JP-BE-04 (perfil), JP-BE-05 (ofertas)  
**Estado:** ✅ Hecho — PR #9

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

- [x] Con 3 obligatorios CUMPLE y deseables PARCIAL, NO_CUMPLE y CUMPLE, la respuesta tiene `score = 85` y `hasBlockingGap = false`.
- [x] Con un obligatorio NO_CUMPLE, `hasBlockingGap = true`.
- [x] Un usuario no puede analizar ofertas de otro (404).
- [x] Sin perfil → 409 con `type` `.../profile-required`.
- [x] Una respuesta del LLM no válida dos veces seguidas → 502 y no se guarda nada.
- [x] Timeout del LLM → 504; concurrencia agotada → 503 con cabecera `Retry-After`.
- [x] El endpoint aparece documentado en Swagger UI.

**Tests**

- `MatchScoreCalculatorTest` (`@ParameterizedTest`, al menos 8 casos: todo cumple, nada cumple, sin deseables, solo un requisito, casos límite de redondeo 84,5 → 85, lista vacía).
- `MatchEvaluationValidatorTest`: un id que falta, un id duplicado, un id desconocido, pocas o demasiadas sugerencias.
- `MatchEvaluationParserTest` (adaptador real con un `ChatModel` simulado): perfil y requisitos delimitados como datos; reintento ante una respuesta no válida → `LlmInvalidResponseException`. El reintento lo hace `StructuredLlmCall`, dentro del gateway, no el servicio.
- `MatchAnalysisServiceTest` (Mockito): orquestación, orden de las comprobaciones (404 → 409), no se llama al LLM ni se gasta cupo si no hay perfil, y no se guarda nada si el LLM falla.
- `AnalysisControllerIT` (Testcontainers + MockMvc + `FakeLlmGateway`): 201 / 401 / 404 / 409.

**Definición de hecho:** cobertura ≥ 90 % en `analysis.domain` (la comprueba JaCoCo en `mvnw verify`: `jacoco:check` sobre las líneas de ese paquete, con los unitarios y los IT; informe en `backend/target/site/jacoco/index.html`), tests en verde en la CI (o, desde que GitHub desactivó Actions, en la verificación local equivalente, ver 2.4), contrato OpenAPI actualizado, PR revisado.

### Ticket 3 — JP-FE-06: Tablero kanban de candidaturas

**Tipo:** Frontend · **Prioridad:** Alta · **Estimación:** 5 SP · **Historia:** HU-05
**Dependencias:** JP-FE-01 (cliente API y auth), JP-BE-08 (API de candidaturas)  
**Estado:** ✅ Hecho — PR #16

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

- [x] Se ven 5 columnas con su contador y las tarjetas ordenadas por `boardPosition`.
- [x] Al arrastrar de Guardada a Enviada, la tarjeta cambia de columna al instante y el cambio se mantiene al recargar.
- [x] Al reordenar dentro de una columna se llama a `PATCH /applications/{id}` (no a `/status`) y no se crea ninguna entrada en el historial.
- [x] Si el backend responde 409, la tarjeta vuelve a su sitio y aparece un *toast* con el `detail` del error.
- [x] Se puede mover una tarjeta solo con el teclado (Espacio para cogerla, flechas para moverla, Espacio para soltarla).
- [x] Las tarjetas con seguimiento vencido muestran el indicador.
- [x] No aparecen errores ni avisos en la consola; Lighthouse Accessibility ≥ 90 en la página.

**Tests**

- `KanbanBoard.test.tsx` (Vitest + React Testing Library + MSW): pinta columnas y tarjetas desde la respuesta simulada; el estado vacío; la vuelta atrás ante un 409.
- `canTransition.test.ts`: tabla completa de transiciones permitidas y prohibidas.
- Cubierto también por el E2E del flujo principal (JP-QA-01): arrastrar a "Enviada" y comprobar que se mantiene.

**Definición de hecho:** componentes tipados sin `any`, tests en verde, lint sin errores, revisado en móvil y escritorio, PR aprobado.

**Revisión manual (hecha el 10/11/2026, con `docker compose up --build`):** ✅ guardar en el tablero desde el detalle · ✅ arrastrar con el ratón y que el cambio se mantenga tras F5 · ✅ movimiento prohibido bloqueado sin ninguna petición en Network · ✅ reordenar con `PATCH /applications/{id}` · ✅ mover solo con el teclado · ✅ panel lateral · ✅ vista móvil con pestañas y "Mover a…" · ✅ Lighthouse Accessibility ≥ 90. Captura en 1.3.

---

## 7. Pull Requests

Cada ticket se desarrolla en su propia rama y se fusiona en `main` con una PR **dentro de mi repositorio**. Las PRs no son la entrega: la entrega es rama + Typeform. Convención: una rama por ticket (`feat/JP-XX-NN-descripcion`), título con [Conventional Commits](https://www.conventionalcommits.org/), descripción con el resumen de la fase 5 de `/ticket` (qué se ha hecho, tests, decisiones y cambios en el readme) y **CI en verde antes de fusionar** (desde la PR #7). Las PRs #20 y #21 se fusionaron con la verificación local equivalente, porque GitHub había desactivado Actions (ver 2.4).

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

**Todas las PRs**

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
| #12 | — | docs: cierre de la Entrega 2 (estado, capturas, tests, PRs y registro de IA) |
| #13 | — | docs(prompts): índice de prompts clave y cómo se tomó el criterio humano |
| #14 | JP-AI-01 | test(ai): validación con el LLM real (@Tag("llm-live")) fuera de la CI |
| #15 | JP-BE-08 | feat(tracker): API de candidaturas y política de transiciones |
| #16 | JP-FE-06 | feat(tracker): tablero kanban con dnd-kit y botón "Guardar en el tablero" |
| #17 | JP-QA-01 | test(e2e): suite E2E con Playwright for Java del flujo principal |
| #18 | JP-BE-04 | feat(profile): subida de CV y extracción de perfil con IA |
| #19 | JP-FE-02 | feat(profile): onboarding con subida de CV y formulario de perfil |
| #20 | JP-BE-07 | feat(document): generación de carta y CV adaptado con IA, versionado y PDF |
| #21 | JP-FE-05 | feat(documents): editor de carta y CV adaptado con exportación a PDF |

---

## 8. Deuda técnica y fuera de alcance

Único sitio donde se recoge lo que no se ha hecho y la deuda técnica conocida. Entre paréntesis, el ticket en el que se detectó.

### 8.1. Fuera de alcance y no implementado

- **Should-have S1 (preparación de entrevista) y S2 (recordatorios por email):** no se han implementado. Eran deseables, fuera del alcance obligatorio (ver 1.2).
- **Despliegue público:** es opcional en el máster y no se ha hecho. La evidencia de funcionamiento es el vídeo (ver 0.4) y el repositorio con credenciales de prueba y pasos de verificación (ver 1.4). El diseño previsto está en 2.4.
- **Decisiones explícitas de producto:** ni scraping de portales de empleo ni envío automático de candidaturas (ver 1.2).
- **CI en GitHub Actions desactivada por GitHub (11/11/2026):** es externo al proyecto. Desde entonces se verifica en local con los mismos pasos de `ci.yml` (ver 2.4).

### 8.2. Deuda técnica conocida

- **`save()` de Spring Data hace `merge` en las entidades nuevas.** Los UUID se asignan en el constructor, así que Spring Data no distingue una entidad nueva de una existente y ejecuta un `SELECT` antes de cada `INSERT`. Funciona, pero cuesta una consulta de más. Se resolverá con una clase base que implemente `Persistable` (JP-DB-01).
- **JWT en `localStorage` (JP-FE-01).** La sesión (token, `expiresAt` y datos del usuario) se guarda en `localStorage` para que aguante las recargas y sirva en todas las pestañas, pero un XSS podría leer el token. Mitigaciones actuales: el token caduca a los 60 min (el frontend lo descarta al llegar a `expiresAt` y ante cualquier `401`), React escapa el contenido que pinta, no se usa `dangerouslySetInnerHTML` y nginx envía una CSP con `script-src 'self'` estricto (JP-OPS-01, ver 2.5). El Markdown que genera la IA no hay que sanearlo **por diseño** (JP-FE-05): el frontend lo edita y lo muestra como texto en un `<textarea>`, sin convertirlo nunca a HTML (lo comprueba `DocumentsPanel.test.tsx`); el único sitio donde se convierte es el PDF del backend, que ya lo trata como no fiable (ver 2.5).
- **La sesión no se sincroniza entre pestañas (JP-FE-01).** El frontend no escucha el evento `storage`: si se cierra la sesión en una pestaña, las demás siguen mostrando la sesión hasta que su siguiente llamada a la API recibe un `401` (o hasta que llega `expiresAt`), y entonces vuelven a `/login`.
- **Aviso moderado de `npm audit` en `minimatch` (JP-FE-01).** Llega como dependencia de typescript-eslint, que solo se usa al pasar el lint: no forma parte del bundle que se sirve al navegador (`npm audit --omit=dev`: 0 vulnerabilidades; el 11/11/2026, `npm audit` cuenta 7 moderadas, todas de la misma cadena). `npm audit fix --force` lo resolvería rebajando typescript-eslint a una versión antigua, así que se deja hasta que typescript-eslint actualice la dependencia.
- **Límites de peticiones en memoria (JP-BE-05).** Los cubos de Bucket4j viven en la memoria del backend: valen para una sola instancia (el despliegue previsto) y se reinician al reiniciarlo. Con varias instancias habría que pasarlos a un almacén compartido (Bucket4j tiene integración con Redis o PostgreSQL). Para acotar la memoria, al llegar a `jobpilot.rate-limit.max-keys` claves se olvidan las que tienen el cupo lleno.
- **Borrador del CV en memoria (JP-BE-04).** El texto de un CV subido con `POST /profile/cv` espera en la memoria del backend (`CvDraftStore`, 30 min, como mucho `jobpilot.cv-draft.max-entries` borradores) hasta el `PUT /profile`. Como los cubos de Bucket4j, vale para una sola instancia y se pierde al reiniciar; si se pierde o caduca, el perfil se guarda igualmente, sin `cv_raw_text` ni `cv_file_name`. Con varias instancias habría que pasarlo a una tabla o a Redis.
- **Proxies de confianza para `X-Forwarded-For` (JP-BE-05).** Tomcat da por internas las IPs privadas (10/8, 172.16/12, 192.168/16, 127/8…). En local, el puerto 8080 del backend también está publicado y las peticiones del host llegan desde la puerta de enlace de Docker (172.x), así que quien llame directamente a `:8080` puede falsear su IP. Por nginx (`:5173`) no, porque nginx sustituye la cabecera. Si se desplegara, habría que ajustar `server.tomcat.remoteip.internal-proxies` al proxy de la plataforma y no publicar el backend.
- **Aviso de Mockito al ejecutar los tests** (*"A Java agent has been loaded dynamically"*). En Java 21 es inofensivo; una versión futura del JDK lo bloqueará. Se resolverá configurando Mockito como `-javaagent` en Surefire y Failsafe (JP-BE-01).
- **Los E2E usan el mismo puerto que el stack de desarrollo (JP-OPS-01).** `docker-compose.e2e.yml` publica 5173 y 8080, como `docker-compose.yml`: no se pueden tener los dos levantados a la vez. En JP-QA-01 se decidió no cambiarlos: antes de los E2E hay que parar el stack de desarrollo (`docker compose stop backend frontend`). Si molesta, se pueden mover a otros puertos sin tocar los tests: el smoke test los lee de `FRONTEND_URL` y `BACKEND_URL`, y Playwright, de `FRONTEND_URL`.
- **El `proxy_pass` de nginx apunta al nombre del servicio de Compose (JP-OPS-01).** `backend:8080` vale en local y en la CI; si se desplegara, habría que parametrizarlo (ver 2.4).
- **La carga inicial del frontend ronda los 500 kB (JP-FE-04 → JP-FE-02).** Antes era un único chunk de 504,6 kB (155,1 kB con gzip) y `npm run build` avisaba. Desde JP-FE-06, el tablero, el detalle de oferta y la nueva oferta se cargan con `lazy` de React Router, cada uno con un `HydrateFallback`, y desde JP-FE-02 también el perfil (19,9 kB; 6,3 kB con gzip). Con JP-FE-02 el chunk inicial llegaba a 501,4 kB (lo nuevo es el cliente del perfil y el aviso "Crea tu perfil" del dashboard) y volvía el aviso, así que React, React DOM y React Router van ahora en su propio chunk (`vendor-react`, con `build.rolldownOptions.output.codeSplitting` en `vite.config.ts`): `index` 190,8 kB (58,1 kB con gzip) + `vendor-react` 310,3 kB (98,0 kB con gzip) + `rolldown-runtime` 0,6 kB (medido al cierre de la Entrega 3). **La primera carga sigue siendo de ~502 kB (~156 kB con gzip)**; lo que se gana es que ningún chunk pasa de 500 kB y que el de React, que cambia poco, se queda en la caché del navegador entre despliegues. Si la carga inicial sigue creciendo, lo siguiente es sacar del chunk inicial lo que solo usan algunas páginas (Zod y React Hook Form los usan el login y el registro).
- **La carta gasta ~2.100 tokens de salida (JP-BE-07), unas 3 veces lo esperado.** En la prueba con la IA real (Claude Sonnet 5.5), una carta de 301 palabras costó 2.666 tokens de entrada y 2.099 de salida (`end_turn`, al primer intento), cuando ~300 palabras en español deberían rondar los 600–700. El CV adaptado, en cambio, gastó lo razonable (652 de salida para 171 palabras). La causa está pendiente de investigar: el contenido de las respuestas no se registra a propósito (datos personales), así que habrá que reproducirlo en un test `llm-live` que mida la longitud de la respuesta en bruto frente a los tokens.
- **Fuentes estándar en el PDF (JP-BE-07).** El PDF usa las fuentes base de PDF (Helvetica), que cubren el español, el inglés y "€". Los caracteres que no cubren (emojis, alfabetos no latinos) salen como `#`. Se resolverá incrustando una fuente TTF libre (p. ej. DejaVu Sans o Noto Sans) con `useFont` de OpenHTMLtoPDF.
- **El historial de documentos no muestra versiones antiguas (JP-FE-05).** `GET /offers/{offerId}/documents` devuelve completa solo la última versión; de las demás, solo los metadatos. El historial del frontend es de solo lectura y no permite ver ni restaurar una versión anterior. Haría falta un endpoint `GET /documents/{documentId}`.
- **Comentario desfasado en `application-e2e.yml` (JP-QA-01).** Dice que cada ejecución de la suite gasta 2 llamadas a la IA del usuario del seed; hoy cada test registra su propia cuenta y gasta 4 (ver 2.6). El valor (10.000/h) sigue siendo correcto; solo hay que corregir el comentario.
- **Sin `llm_model` en `generated_documents` (JP-BE-07).** Era la incoherencia 24 de la revisión de la Entrega 1 (opcional, ver `prompts.md`, sección 4): los documentos generados no guardan qué modelo los escribió, a diferencia de `match_analyses.llm_model`. Se resolvería con una migración nueva (columna que admita NULL para las filas existentes) y rellenándola en `DocumentService`.
