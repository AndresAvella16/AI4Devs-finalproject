-- JP-DB-01 · Esquema inicial de JobPilot (sección 3 del readme).
--
-- Convenciones:
--   · PK UUID generados por la aplicación.
--   · Enumerados como VARCHAR + CHECK (más fáciles de migrar que los ENUM nativos).
--   · Fechas con hora en TIMESTAMPTZ con DEFAULT now().
--   · Borrado en cascada desde el usuario.
--   · Cada FK tiene un índice que la cubre.
--   · Restricciones con nombre explícito (pk_, fk_, uq_, ux_, ck_, ix_) para que los errores sean legibles.
--
-- Nunca edites esta migración una vez commiteada: crea una V<n> nueva.

-- ---------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id            UUID         NOT NULL,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email))
);

-- ---------------------------------------------------------------------------
-- profiles
-- ---------------------------------------------------------------------------
CREATE TABLE profiles (
    id               UUID         NOT NULL,
    user_id          UUID         NOT NULL,
    headline         VARCHAR(160),
    summary          TEXT,
    years_experience SMALLINT,
    location         VARCHAR(120),
    languages        JSONB        NOT NULL DEFAULT '[]'::jsonb,
    education        JSONB        NOT NULL DEFAULT '[]'::jsonb,
    cv_file_name     VARCHAR(255),
    cv_raw_text      TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_profiles PRIMARY KEY (id),
    CONSTRAINT fk_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_profiles_user_id UNIQUE (user_id),
    CONSTRAINT ck_profiles_years_experience CHECK (years_experience BETWEEN 0 AND 60),
    CONSTRAINT ck_profiles_languages_array CHECK (jsonb_typeof(languages) = 'array'),
    CONSTRAINT ck_profiles_education_array CHECK (jsonb_typeof(education) = 'array')
);

-- ---------------------------------------------------------------------------
-- profile_skills
-- ---------------------------------------------------------------------------
CREATE TABLE profile_skills (
    id         UUID        NOT NULL,
    profile_id UUID        NOT NULL,
    name       VARCHAR(80) NOT NULL,
    level      VARCHAR(12),
    years      SMALLINT,
    CONSTRAINT pk_profile_skills PRIMARY KEY (id),
    CONSTRAINT fk_profile_skills_profile FOREIGN KEY (profile_id) REFERENCES profiles (id) ON DELETE CASCADE,
    CONSTRAINT ck_profile_skills_level CHECK (level IN ('BASICO', 'INTERMEDIO', 'AVANZADO')),
    CONSTRAINT ck_profile_skills_years CHECK (years >= 0)
);

-- Unicidad por perfil sin distinguir mayúsculas; cubre también la FK.
CREATE UNIQUE INDEX ux_profile_skills_profile_lower_name ON profile_skills (profile_id, lower(name));

-- ---------------------------------------------------------------------------
-- experiences
-- ---------------------------------------------------------------------------
CREATE TABLE experiences (
    id          UUID         NOT NULL,
    profile_id  UUID         NOT NULL,
    company     VARCHAR(120) NOT NULL,
    role        VARCHAR(120) NOT NULL,
    start_date  DATE         NOT NULL,
    end_date    DATE,
    description TEXT,
    sort_order  SMALLINT     NOT NULL,
    CONSTRAINT pk_experiences PRIMARY KEY (id),
    CONSTRAINT fk_experiences_profile FOREIGN KEY (profile_id) REFERENCES profiles (id) ON DELETE CASCADE,
    CONSTRAINT ck_experiences_dates CHECK (end_date >= start_date)
);

CREATE INDEX ix_experiences_profile_sort ON experiences (profile_id, sort_order);

-- ---------------------------------------------------------------------------
-- job_offers
-- ---------------------------------------------------------------------------
CREATE TABLE job_offers (
    id                UUID          NOT NULL,
    user_id           UUID          NOT NULL,
    title             VARCHAR(160),
    company           VARCHAR(120),
    location          VARCHAR(120),
    work_mode         VARCHAR(12),
    salary_min        INTEGER,
    salary_max        INTEGER,
    currency          VARCHAR(3),
    source_url        VARCHAR(2048),
    raw_text          TEXT          NOT NULL,
    extraction_status VARCHAR(12)   NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_job_offers PRIMARY KEY (id),
    CONSTRAINT fk_job_offers_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    -- Destino de la FK compuesta de applications (la candidatura es del propietario de la oferta).
    CONSTRAINT uq_job_offers_id_user UNIQUE (id, user_id),
    CONSTRAINT ck_job_offers_work_mode CHECK (work_mode IN ('REMOTO', 'HIBRIDO', 'PRESENCIAL', 'DESCONOCIDO')),
    CONSTRAINT ck_job_offers_salary_min CHECK (salary_min >= 0),
    CONSTRAINT ck_job_offers_salary_max CHECK (salary_max >= 0),
    CONSTRAINT ck_job_offers_salary_range CHECK (salary_max >= salary_min),
    CONSTRAINT ck_job_offers_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_job_offers_currency_required
        CHECK ((salary_min IS NULL AND salary_max IS NULL) OR currency IS NOT NULL),
    CONSTRAINT ck_job_offers_raw_text_length CHECK (char_length(raw_text) BETWEEN 200 AND 20000),
    CONSTRAINT ck_job_offers_extraction_status CHECK (extraction_status IN ('PENDIENTE', 'COMPLETADA', 'FALLIDA'))
);

-- Dashboard: ofertas del usuario, más recientes primero; cubre también la FK.
CREATE INDEX ix_job_offers_user_created ON job_offers (user_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- offer_requirements
-- ---------------------------------------------------------------------------
CREATE TABLE offer_requirements (
    id          UUID         NOT NULL,
    offer_id    UUID         NOT NULL,
    description VARCHAR(300) NOT NULL,
    type        VARCHAR(12)  NOT NULL,
    category    VARCHAR(12)  NOT NULL,
    sort_order  SMALLINT     NOT NULL,
    CONSTRAINT pk_offer_requirements PRIMARY KEY (id),
    CONSTRAINT fk_offer_requirements_offer FOREIGN KEY (offer_id) REFERENCES job_offers (id) ON DELETE CASCADE,
    -- Destino de la FK compuesta de requirement_matches; cubre también la FK.
    CONSTRAINT uq_offer_requirements_offer_id UNIQUE (offer_id, id),
    CONSTRAINT ck_offer_requirements_type CHECK (type IN ('OBLIGATORIO', 'DESEABLE')),
    CONSTRAINT ck_offer_requirements_category
        CHECK (category IN ('TECNICO', 'IDIOMA', 'EXPERIENCIA', 'FORMACION', 'OTRO'))
);

-- ---------------------------------------------------------------------------
-- match_analyses
-- ---------------------------------------------------------------------------
CREATE TABLE match_analyses (
    id               UUID        NOT NULL,
    offer_id         UUID        NOT NULL,
    profile_id       UUID        NOT NULL,
    score            SMALLINT    NOT NULL,
    has_blocking_gap BOOLEAN     NOT NULL,
    summary          TEXT,
    suggestions      JSONB       NOT NULL DEFAULT '[]'::jsonb,
    llm_model        VARCHAR(80) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_match_analyses PRIMARY KEY (id),
    CONSTRAINT fk_match_analyses_offer FOREIGN KEY (offer_id) REFERENCES job_offers (id) ON DELETE CASCADE,
    CONSTRAINT fk_match_analyses_profile FOREIGN KEY (profile_id) REFERENCES profiles (id) ON DELETE CASCADE,
    -- Destino de la FK compuesta de requirement_matches.
    CONSTRAINT uq_match_analyses_offer_id UNIQUE (offer_id, id),
    CONSTRAINT ck_match_analyses_score CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT ck_match_analyses_suggestions_array CHECK (jsonb_typeof(suggestions) = 'array')
);

-- Dashboard y tablero: último análisis de cada oferta; cubre también la FK.
CREATE INDEX ix_match_analyses_offer_created ON match_analyses (offer_id, created_at DESC);
CREATE INDEX ix_match_analyses_profile ON match_analyses (profile_id);

-- ---------------------------------------------------------------------------
-- requirement_matches
-- ---------------------------------------------------------------------------
-- offer_id es redundante a propósito: al compartirlo las dos FKs compuestas, el análisis y el requisito
-- tienen que ser de la misma oferta.
CREATE TABLE requirement_matches (
    id             UUID        NOT NULL,
    analysis_id    UUID        NOT NULL,
    requirement_id UUID        NOT NULL,
    offer_id       UUID        NOT NULL,
    result         VARCHAR(10) NOT NULL,
    evidence       TEXT,
    CONSTRAINT pk_requirement_matches PRIMARY KEY (id),
    CONSTRAINT fk_requirement_matches_analysis FOREIGN KEY (offer_id, analysis_id)
        REFERENCES match_analyses (offer_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_requirement_matches_requirement FOREIGN KEY (offer_id, requirement_id)
        REFERENCES offer_requirements (offer_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_requirement_matches_analysis_requirement UNIQUE (analysis_id, requirement_id),
    CONSTRAINT ck_requirement_matches_result CHECK (result IN ('CUMPLE', 'PARCIAL', 'NO_CUMPLE'))
);

CREATE INDEX ix_requirement_matches_offer_requirement ON requirement_matches (offer_id, requirement_id);

-- ---------------------------------------------------------------------------
-- generated_documents
-- ---------------------------------------------------------------------------
CREATE TABLE generated_documents (
    id               UUID        NOT NULL,
    offer_id         UUID        NOT NULL,
    type             VARCHAR(12) NOT NULL,
    content_markdown TEXT        NOT NULL,
    version          SMALLINT    NOT NULL,
    edited_by_user   BOOLEAN     NOT NULL DEFAULT false,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_generated_documents PRIMARY KEY (id),
    CONSTRAINT fk_generated_documents_offer FOREIGN KEY (offer_id) REFERENCES job_offers (id) ON DELETE CASCADE,
    -- Última versión de cada documento; cubre también la FK.
    CONSTRAINT uq_generated_documents_offer_type_version UNIQUE (offer_id, type, version),
    CONSTRAINT ck_generated_documents_type CHECK (type IN ('CARTA', 'CV_ADAPTADO')),
    CONSTRAINT ck_generated_documents_version CHECK (version >= 1)
);

-- ---------------------------------------------------------------------------
-- applications
-- ---------------------------------------------------------------------------
CREATE TABLE applications (
    id             UUID        NOT NULL,
    user_id        UUID        NOT NULL,
    offer_id       UUID        NOT NULL,
    status         VARCHAR(12) NOT NULL DEFAULT 'GUARDADA',
    board_position INTEGER     NOT NULL,
    applied_at     DATE,
    follow_up_date DATE,
    notes          TEXT,
    version        BIGINT      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_applications PRIMARY KEY (id),
    CONSTRAINT fk_applications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    -- La candidatura es siempre del propietario de la oferta.
    CONSTRAINT fk_applications_offer_owner FOREIGN KEY (offer_id, user_id)
        REFERENCES job_offers (id, user_id) ON DELETE CASCADE,
    -- Una candidatura por oferta; cubre también la FK compuesta.
    CONSTRAINT uq_applications_offer UNIQUE (offer_id),
    CONSTRAINT ck_applications_status
        CHECK (status IN ('GUARDADA', 'ENVIADA', 'ENTREVISTA', 'OFERTA', 'DESCARTADA')),
    CONSTRAINT ck_applications_board_position CHECK (board_position >= 0),
    CONSTRAINT ck_applications_applied_at
        CHECK (status NOT IN ('ENVIADA', 'ENTREVISTA', 'OFERTA') OR applied_at IS NOT NULL)
);

-- Tablero: candidaturas por columna y en orden; cubre también la FK de user_id.
CREATE INDEX ix_applications_user_status_position ON applications (user_id, status, board_position);

-- ---------------------------------------------------------------------------
-- application_status_history
-- ---------------------------------------------------------------------------
-- Las transiciones permitidas las aplica ApplicationStatusPolicy en Java (aquí solo los valores válidos).
CREATE TABLE application_status_history (
    id             UUID        NOT NULL,
    application_id UUID        NOT NULL,
    from_status    VARCHAR(12),
    to_status      VARCHAR(12) NOT NULL,
    changed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_application_status_history PRIMARY KEY (id),
    CONSTRAINT fk_status_history_application FOREIGN KEY (application_id)
        REFERENCES applications (id) ON DELETE CASCADE,
    CONSTRAINT ck_status_history_from_status
        CHECK (from_status IN ('GUARDADA', 'ENVIADA', 'ENTREVISTA', 'OFERTA', 'DESCARTADA')),
    CONSTRAINT ck_status_history_to_status
        CHECK (to_status IN ('GUARDADA', 'ENVIADA', 'ENTREVISTA', 'OFERTA', 'DESCARTADA')),
    CONSTRAINT ck_status_history_distinct CHECK (from_status IS DISTINCT FROM to_status)
);

CREATE INDEX ix_status_history_application_changed ON application_status_history (application_id, changed_at);
