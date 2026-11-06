-- JP-DB-01 · Datos de prueba para revisar JobPilot en local. SOLO se carga en el perfil dev
-- (spring.flyway.locations de application-dev.yml); nunca en e2e ni en prod.
--
-- ⚠ AVISO: todas las inserciones usan ON CONFLICT DO NOTHING. Si cambias este archivo, Flyway lo vuelve a
--   ejecutar (es una migración repetible), pero las filas que ya existen NO se actualizan. Para recargar el
--   seed desde cero, borra la base de datos de desarrollo y su volumen:
--       docker compose down -v
--       docker compose up -d postgres
--
-- Credenciales ficticias (documentadas en el readme, sección 1.4):
--   email: demo@jobpilot.dev · contraseña: Demo1234! (hash BCrypt de coste 12, generado con pgcrypto)
--
-- Contenido:
--   · 1 usuario con su perfil (8 habilidades, 2 experiencias).
--   · 3 ofertas con requisitos y un análisis cada una. Puntuaciones coherentes con la fórmula de la sección 1.2:
--       oferta 1 → 85: obligatorios C,C,C · deseables P,N,C         (ejemplo 2 de la sección 4)
--       oferta 2 → 68: obligatorios C,C,P · deseables C,N,N         (68,33)
--       oferta 3 → 42: obligatorios P,N,N · deseables C,C,C         (41,67, con requisito bloqueante)
--   · 3 candidaturas: ENTREVISTA (oferta 1), ENVIADA con seguimiento vencido (oferta 2) y GUARDADA (oferta 3),
--     cada una con su historial de estados.
--
-- UUIDs fijos con prefijo por tabla, para que el script sea idempotente y fácil de leer:
--   11111111 users · 22222222 profiles · 33333333 profile_skills · 44444444 experiences · 55555555 job_offers
--   66666666 offer_requirements · 77777777 match_analyses · 88888888 requirement_matches
--   99999999 applications · aaaaaaaa application_status_history

-- ---------------------------------------------------------------------------
-- Usuario
-- ---------------------------------------------------------------------------
INSERT INTO users (id, email, password_hash, full_name, created_at, updated_at)
VALUES ('11111111-0000-4000-8000-000000000001', 'demo@jobpilot.dev',
        '$2a$12$7mBrRXC1qzH7zkq0m6TxrOKAHm/Skie.kK1z7wlLp5YBQy92T3VMm', 'Lucía Demo Martínez',
        now() - INTERVAL '30 days', now() - INTERVAL '30 days')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Perfil
-- ---------------------------------------------------------------------------
INSERT INTO profiles (id, user_id, headline, summary, years_experience, location, languages, education,
                      cv_file_name, cv_raw_text, created_at, updated_at)
VALUES ('22222222-0000-4000-8000-000000000001', '11111111-0000-4000-8000-000000000001',
        'Desarrolladora Web Junior · Java',
        'Desarrolladora Java con experiencia en APIs REST con Spring Boot y bases de datos PostgreSQL. '
            || 'Conocimientos básicos de React y TypeScript en proyectos personales.',
        2, 'Valencia',
        '[{"language": "Español", "level": "Nativo"}, {"language": "Inglés", "level": "B2"}]'::jsonb,
        '[{"title": "CFGS Desarrollo de Aplicaciones Web (DAW)", "institution": "IES Ficticio de Valencia", "year": 2024}]'::jsonb,
        'cv-lucia-demo.pdf',
        'Lucía Demo Martínez. Desarrolladora Web Junior. Valencia. '
            || 'Experiencia: Desarrolladora Junior en Tecnologías Turia (2025 - actualidad): APIs REST con Java 21 '
            || 'y Spring Boot, PostgreSQL, tests con JUnit. Prácticas como desarrolladora Java (Spring Boot) en '
            || 'Soluciones Levante durante 12 meses. Formación: CFGS DAW (2024). Idiomas: inglés B2. '
            || 'Proyectos personales con React y TypeScript.',
        now() - INTERVAL '30 days', now() - INTERVAL '30 days')
ON CONFLICT DO NOTHING;

INSERT INTO profile_skills (id, profile_id, name, level, years)
VALUES ('33333333-0000-4000-8000-000000000001', '22222222-0000-4000-8000-000000000001', 'Java', 'AVANZADO', 2),
       ('33333333-0000-4000-8000-000000000002', '22222222-0000-4000-8000-000000000001', 'Spring Boot', 'INTERMEDIO', 2),
       ('33333333-0000-4000-8000-000000000003', '22222222-0000-4000-8000-000000000001', 'SQL', 'INTERMEDIO', 2),
       ('33333333-0000-4000-8000-000000000004', '22222222-0000-4000-8000-000000000001', 'PostgreSQL', 'INTERMEDIO', 1),
       ('33333333-0000-4000-8000-000000000005', '22222222-0000-4000-8000-000000000001', 'Git', 'INTERMEDIO', 2),
       ('33333333-0000-4000-8000-000000000006', '22222222-0000-4000-8000-000000000001', 'JUnit', 'INTERMEDIO', 1),
       ('33333333-0000-4000-8000-000000000007', '22222222-0000-4000-8000-000000000001', 'React', 'BASICO', 1),
       ('33333333-0000-4000-8000-000000000008', '22222222-0000-4000-8000-000000000001', 'TypeScript', 'BASICO', 1)
ON CONFLICT DO NOTHING;

INSERT INTO experiences (id, profile_id, company, role, start_date, end_date, description, sort_order)
VALUES ('44444444-0000-4000-8000-000000000001', '22222222-0000-4000-8000-000000000001',
        'Tecnologías Turia S.L.', 'Desarrolladora Junior', DATE '2025-03-01', NULL,
        'Desarrollo de APIs REST con Java 21 y Spring Boot sobre PostgreSQL. Tests unitarios y de integración con JUnit.',
        0),
       ('44444444-0000-4000-8000-000000000002', '22222222-0000-4000-8000-000000000001',
        'Soluciones Levante S.L.', 'Desarrolladora Java en prácticas', DATE '2024-03-01', DATE '2025-02-28',
        'Prácticas de 12 meses: mantenimiento de una aplicación Spring Boot, consultas SQL y control de versiones con Git.',
        1)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Ofertas
-- ---------------------------------------------------------------------------
INSERT INTO job_offers (id, user_id, title, company, location, work_mode, salary_min, salary_max, currency,
                        source_url, raw_text, extraction_status, created_at)
VALUES ('55555555-0000-4000-8000-000000000001', '11111111-0000-4000-8000-000000000001',
        'Desarrollador/a Java Junior', 'Innova Software Ficticio', 'Valencia', 'HIBRIDO', 24000, 28000, 'EUR',
        'https://empleo.example.com/ofertas/java-junior',
        'Buscamos un/a Desarrollador/a Java Junior para incorporarse a nuestro equipo de producto en Valencia, '
            || 'con modalidad híbrida (3 días en oficina). Requisitos: al menos 1 año de experiencia con Java y '
            || 'Spring Boot, conocimientos de SQL y PostgreSQL y uso habitual de Git. Se valorará experiencia con '
            || 'React, Docker e inglés B2. Ofrecemos salario de 24.000 a 28.000 euros brutos anuales, formación '
            || 'continua y horario flexible.',
        'COMPLETADA', now() - INTERVAL '20 days'),
       ('55555555-0000-4000-8000-000000000002', '11111111-0000-4000-8000-000000000001',
        'Desarrollador/a Full Stack', 'Plataformas Digitales Ejemplo', 'Madrid', 'REMOTO', NULL, NULL, NULL,
        NULL,
        'Empresa de comercio electrónico busca Desarrollador/a Full Stack en remoto. Funciones: desarrollo de '
            || 'APIs REST con Java y Spring Boot y de la interfaz web con React. Requisitos: experiencia con Java y '
            || 'Spring Boot, diseño de APIs REST y 2 años de experiencia con React y TypeScript. Valorable: testing '
            || 'con JUnit, Kubernetes y experiencia con AWS. Contrato indefinido y presupuesto anual de formación.',
        'COMPLETADA', now() - INTERVAL '12 days'),
       ('55555555-0000-4000-8000-000000000003', '11111111-0000-4000-8000-000000000001',
        'Desarrollador/a Frontend Senior (Angular)', 'Consultora Tecnológica Muestra', 'Barcelona', 'PRESENCIAL',
        40000, 48000, 'EUR', NULL,
        'Consultora tecnológica en Barcelona incorpora Desarrollador/a Frontend Senior para un proyecto del sector '
            || 'financiero, en modalidad presencial. Requisitos: más de 3 años con TypeScript, dominio avanzado de '
            || 'Angular y al menos 5 años de experiencia en desarrollo frontend. Valorable: maquetación con HTML y '
            || 'CSS, uso de Git e inglés B2. Salario de 40.000 a 48.000 euros brutos anuales.',
        'COMPLETADA', now() - INTERVAL '2 days')
ON CONFLICT DO NOTHING;

INSERT INTO offer_requirements (id, offer_id, description, type, category, sort_order)
VALUES -- Oferta 1
       ('66666666-0000-4000-8000-000000000011', '55555555-0000-4000-8000-000000000001',
        'Al menos 1 año de experiencia con Java y Spring Boot', 'OBLIGATORIO', 'EXPERIENCIA', 0),
       ('66666666-0000-4000-8000-000000000012', '55555555-0000-4000-8000-000000000001',
        'Conocimientos de SQL y PostgreSQL', 'OBLIGATORIO', 'TECNICO', 1),
       ('66666666-0000-4000-8000-000000000013', '55555555-0000-4000-8000-000000000001',
        'Uso de Git', 'OBLIGATORIO', 'TECNICO', 2),
       ('66666666-0000-4000-8000-000000000014', '55555555-0000-4000-8000-000000000001',
        'Experiencia con React', 'DESEABLE', 'TECNICO', 3),
       ('66666666-0000-4000-8000-000000000015', '55555555-0000-4000-8000-000000000001',
        'Docker', 'DESEABLE', 'TECNICO', 4),
       ('66666666-0000-4000-8000-000000000016', '55555555-0000-4000-8000-000000000001',
        'Inglés B2', 'DESEABLE', 'IDIOMA', 5),
       -- Oferta 2
       ('66666666-0000-4000-8000-000000000021', '55555555-0000-4000-8000-000000000002',
        'Experiencia con Java y Spring Boot', 'OBLIGATORIO', 'TECNICO', 0),
       ('66666666-0000-4000-8000-000000000022', '55555555-0000-4000-8000-000000000002',
        'Diseño de APIs REST', 'OBLIGATORIO', 'TECNICO', 1),
       ('66666666-0000-4000-8000-000000000023', '55555555-0000-4000-8000-000000000002',
        '2 años de experiencia con React y TypeScript', 'OBLIGATORIO', 'EXPERIENCIA', 2),
       ('66666666-0000-4000-8000-000000000024', '55555555-0000-4000-8000-000000000002',
        'Testing con JUnit', 'DESEABLE', 'TECNICO', 3),
       ('66666666-0000-4000-8000-000000000025', '55555555-0000-4000-8000-000000000002',
        'Kubernetes', 'DESEABLE', 'TECNICO', 4),
       ('66666666-0000-4000-8000-000000000026', '55555555-0000-4000-8000-000000000002',
        'Experiencia con AWS', 'DESEABLE', 'TECNICO', 5),
       -- Oferta 3
       ('66666666-0000-4000-8000-000000000031', '55555555-0000-4000-8000-000000000003',
        'Más de 3 años con TypeScript', 'OBLIGATORIO', 'TECNICO', 0),
       ('66666666-0000-4000-8000-000000000032', '55555555-0000-4000-8000-000000000003',
        'Dominio avanzado de Angular', 'OBLIGATORIO', 'TECNICO', 1),
       ('66666666-0000-4000-8000-000000000033', '55555555-0000-4000-8000-000000000003',
        'Al menos 5 años de experiencia en desarrollo frontend', 'OBLIGATORIO', 'EXPERIENCIA', 2),
       ('66666666-0000-4000-8000-000000000034', '55555555-0000-4000-8000-000000000003',
        'Maquetación con HTML y CSS', 'DESEABLE', 'TECNICO', 3),
       ('66666666-0000-4000-8000-000000000035', '55555555-0000-4000-8000-000000000003',
        'Uso de Git', 'DESEABLE', 'TECNICO', 4),
       ('66666666-0000-4000-8000-000000000036', '55555555-0000-4000-8000-000000000003',
        'Inglés B2', 'DESEABLE', 'IDIOMA', 5)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Análisis de encaje
-- ---------------------------------------------------------------------------
INSERT INTO match_analyses (id, offer_id, profile_id, score, has_blocking_gap, summary, suggestions, llm_model,
                            created_at)
VALUES ('77777777-0000-4000-8000-000000000001', '55555555-0000-4000-8000-000000000001',
        '22222222-0000-4000-8000-000000000001', 85, false,
        'Cumples todos los requisitos obligatorios. Tu principal margen de mejora está en Docker.',
        '["Menciona en la carta tu experiencia con Spring Boot y PostgreSQL en Tecnologías Turia.",
          "Añade al CV un proyecto en el que hayas usado React, aunque sea personal.",
          "Haz un curso corto de Docker y dockeriza uno de tus proyectos."]'::jsonb,
        'fake', now() - INTERVAL '19 days'),
       ('77777777-0000-4000-8000-000000000002', '55555555-0000-4000-8000-000000000002',
        '22222222-0000-4000-8000-000000000001', 68, false,
        'Encajas bien en la parte de backend; te falta experiencia en React y en cloud.',
        '["Destaca el diseño de APIs REST que haces en tu puesto actual.",
          "Detalla en el CV los proyectos con React y TypeScript y su duración.",
          "Prepara un proyecto pequeño desplegado en AWS.",
          "Repasa los conceptos básicos de Kubernetes antes de la entrevista."]'::jsonb,
        'fake', now() - INTERVAL '11 days'),
       ('77777777-0000-4000-8000-000000000003', '55555555-0000-4000-8000-000000000003',
        '22222222-0000-4000-8000-000000000001', 42, true,
        'La oferta pide un perfil frontend senior con Angular; tu experiencia es sobre todo backend.',
        '["Valora si esta oferta encaja con tu objetivo profesional antes de invertir tiempo.",
          "Si quieres orientarte a frontend, empieza un proyecto con Angular.",
          "Destaca tu nivel de inglés B2 y tu manejo de Git."]'::jsonb,
        'fake', now() - INTERVAL '1 day')
ON CONFLICT DO NOTHING;

INSERT INTO requirement_matches (id, analysis_id, requirement_id, offer_id, result, evidence)
VALUES -- Oferta 1 → 85
       ('88888888-0000-4000-8000-000000000011', '77777777-0000-4000-8000-000000000001',
        '66666666-0000-4000-8000-000000000011', '55555555-0000-4000-8000-000000000001', 'CUMPLE',
        'Prácticas de 12 meses como desarrolladora Java (Spring Boot) y puesto actual con Spring Boot'),
       ('88888888-0000-4000-8000-000000000012', '77777777-0000-4000-8000-000000000001',
        '66666666-0000-4000-8000-000000000012', '55555555-0000-4000-8000-000000000001', 'CUMPLE',
        'APIs REST sobre PostgreSQL en Tecnologías Turia'),
       ('88888888-0000-4000-8000-000000000013', '77777777-0000-4000-8000-000000000001',
        '66666666-0000-4000-8000-000000000013', '55555555-0000-4000-8000-000000000001', 'CUMPLE',
        'Control de versiones con Git en las prácticas'),
       ('88888888-0000-4000-8000-000000000014', '77777777-0000-4000-8000-000000000001',
        '66666666-0000-4000-8000-000000000014', '55555555-0000-4000-8000-000000000001', 'PARCIAL',
        'Proyectos personales con React; sin experiencia profesional'),
       ('88888888-0000-4000-8000-000000000015', '77777777-0000-4000-8000-000000000001',
        '66666666-0000-4000-8000-000000000015', '55555555-0000-4000-8000-000000000001', 'NO_CUMPLE', NULL),
       ('88888888-0000-4000-8000-000000000016', '77777777-0000-4000-8000-000000000001',
        '66666666-0000-4000-8000-000000000016', '55555555-0000-4000-8000-000000000001', 'CUMPLE',
        'Inglés B2'),
       -- Oferta 2 → 68
       ('88888888-0000-4000-8000-000000000021', '77777777-0000-4000-8000-000000000002',
        '66666666-0000-4000-8000-000000000021', '55555555-0000-4000-8000-000000000002', 'CUMPLE',
        'Puesto actual con Java 21 y Spring Boot'),
       ('88888888-0000-4000-8000-000000000022', '77777777-0000-4000-8000-000000000002',
        '66666666-0000-4000-8000-000000000022', '55555555-0000-4000-8000-000000000002', 'CUMPLE',
        'Desarrollo de APIs REST en Tecnologías Turia'),
       ('88888888-0000-4000-8000-000000000023', '77777777-0000-4000-8000-000000000002',
        '66666666-0000-4000-8000-000000000023', '55555555-0000-4000-8000-000000000002', 'PARCIAL',
        'React y TypeScript en proyectos personales, nivel básico'),
       ('88888888-0000-4000-8000-000000000024', '77777777-0000-4000-8000-000000000002',
        '66666666-0000-4000-8000-000000000024', '55555555-0000-4000-8000-000000000002', 'CUMPLE',
        'Tests unitarios y de integración con JUnit'),
       ('88888888-0000-4000-8000-000000000025', '77777777-0000-4000-8000-000000000002',
        '66666666-0000-4000-8000-000000000025', '55555555-0000-4000-8000-000000000002', 'NO_CUMPLE', NULL),
       ('88888888-0000-4000-8000-000000000026', '77777777-0000-4000-8000-000000000002',
        '66666666-0000-4000-8000-000000000026', '55555555-0000-4000-8000-000000000002', 'NO_CUMPLE', NULL),
       -- Oferta 3 → 42 (bloqueante)
       ('88888888-0000-4000-8000-000000000031', '77777777-0000-4000-8000-000000000003',
        '66666666-0000-4000-8000-000000000031', '55555555-0000-4000-8000-000000000003', 'PARCIAL',
        'TypeScript en proyectos personales, sin experiencia profesional'),
       ('88888888-0000-4000-8000-000000000032', '77777777-0000-4000-8000-000000000003',
        '66666666-0000-4000-8000-000000000032', '55555555-0000-4000-8000-000000000003', 'NO_CUMPLE', NULL),
       ('88888888-0000-4000-8000-000000000033', '77777777-0000-4000-8000-000000000003',
        '66666666-0000-4000-8000-000000000033', '55555555-0000-4000-8000-000000000003', 'NO_CUMPLE', NULL),
       ('88888888-0000-4000-8000-000000000034', '77777777-0000-4000-8000-000000000003',
        '66666666-0000-4000-8000-000000000034', '55555555-0000-4000-8000-000000000003', 'CUMPLE',
        'Formación en DAW (HTML y CSS)'),
       ('88888888-0000-4000-8000-000000000035', '77777777-0000-4000-8000-000000000003',
        '66666666-0000-4000-8000-000000000035', '55555555-0000-4000-8000-000000000003', 'CUMPLE',
        'Uso habitual de Git'),
       ('88888888-0000-4000-8000-000000000036', '77777777-0000-4000-8000-000000000003',
        '66666666-0000-4000-8000-000000000036', '55555555-0000-4000-8000-000000000003', 'CUMPLE',
        'Inglés B2')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Candidaturas e historial
-- ---------------------------------------------------------------------------
INSERT INTO applications (id, user_id, offer_id, status, board_position, applied_at, follow_up_date, notes,
                          created_at, updated_at)
VALUES ('99999999-0000-4000-8000-000000000001', '11111111-0000-4000-8000-000000000001',
        '55555555-0000-4000-8000-000000000001', 'ENTREVISTA', 0, CURRENT_DATE - 15, CURRENT_DATE + 2,
        'Entrevista técnica con el equipo de producto. Repasar Spring Security.',
        now() - INTERVAL '18 days', now() - INTERVAL '5 days'),
       ('99999999-0000-4000-8000-000000000002', '11111111-0000-4000-8000-000000000001',
        '55555555-0000-4000-8000-000000000002', 'ENVIADA', 0, CURRENT_DATE - 10, CURRENT_DATE - 3,
        'Enviada por la web de la empresa. Escribir a la persona de selección si no hay respuesta.',
        now() - INTERVAL '11 days', now() - INTERVAL '10 days'),
       ('99999999-0000-4000-8000-000000000003', '11111111-0000-4000-8000-000000000001',
        '55555555-0000-4000-8000-000000000003', 'GUARDADA', 0, NULL, NULL, NULL,
        now() - INTERVAL '1 day', now() - INTERVAL '1 day')
ON CONFLICT DO NOTHING;

INSERT INTO application_status_history (id, application_id, from_status, to_status, changed_at)
VALUES -- ENTREVISTA: GUARDADA → ENVIADA → ENTREVISTA
       ('aaaaaaaa-0000-4000-8000-000000000011', '99999999-0000-4000-8000-000000000001', NULL, 'GUARDADA',
        now() - INTERVAL '18 days'),
       ('aaaaaaaa-0000-4000-8000-000000000012', '99999999-0000-4000-8000-000000000001', 'GUARDADA', 'ENVIADA',
        now() - INTERVAL '15 days'),
       ('aaaaaaaa-0000-4000-8000-000000000013', '99999999-0000-4000-8000-000000000001', 'ENVIADA', 'ENTREVISTA',
        now() - INTERVAL '5 days'),
       -- ENVIADA: GUARDADA → ENVIADA
       ('aaaaaaaa-0000-4000-8000-000000000021', '99999999-0000-4000-8000-000000000002', NULL, 'GUARDADA',
        now() - INTERVAL '11 days'),
       ('aaaaaaaa-0000-4000-8000-000000000022', '99999999-0000-4000-8000-000000000002', 'GUARDADA', 'ENVIADA',
        now() - INTERVAL '10 days'),
       -- GUARDADA
       ('aaaaaaaa-0000-4000-8000-000000000031', '99999999-0000-4000-8000-000000000003', NULL, 'GUARDADA',
        now() - INTERVAL '1 day')
ON CONFLICT DO NOTHING;
