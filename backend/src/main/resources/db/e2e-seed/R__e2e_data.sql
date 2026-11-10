-- JP-QA-01 · Datos de los tests E2E (Playwright). SOLO se carga en el perfil e2e (spring.flyway.locations de
-- application-e2e.yml); nunca en dev ni en prod. Está fuera de db/seed a propósito: Flyway busca también en las
-- subcarpetas, así que db/seed/e2e lo cargaría también el perfil dev.
--
-- Un usuario con perfil (sin ofertas ni candidaturas): el flujo principal lo necesita para analizar ofertas
-- mientras no se pueda subir el CV (JP-BE-04 / JP-FE-02). Cada ejecución de la suite crea sus propias ofertas.
--
-- Credenciales ficticias, solo para el stack E2E (docker-compose.e2e.yml, BD efímera):
--   email: e2e@jobpilot.test · contraseña: Demo1234! (el mismo hash BCrypt de coste 12 que el usuario demo)
--
-- UUIDs fijos con prefijo por tabla y sufijo e2e (…0e2e…) para distinguirlos de los del seed de dev.

INSERT INTO users (id, email, password_hash, full_name, created_at, updated_at)
VALUES ('11111111-0e2e-4000-8000-000000000001', 'e2e@jobpilot.test',
        '$2a$12$7mBrRXC1qzH7zkq0m6TxrOKAHm/Skie.kK1z7wlLp5YBQy92T3VMm', 'Usuaria E2E Prueba', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO profiles (id, user_id, headline, summary, years_experience, location, languages, education,
                      cv_file_name, cv_raw_text, created_at, updated_at)
VALUES ('22222222-0e2e-4000-8000-000000000001', '11111111-0e2e-4000-8000-000000000001',
        'Desarrolladora Web Junior · Java',
        'Desarrolladora Java con experiencia en APIs REST con Spring Boot y PostgreSQL. React en proyectos personales.',
        2, 'Valencia',
        '[{"language": "Español", "level": "Nativo"}, {"language": "Inglés", "level": "B2"}]'::jsonb,
        '[{"title": "CFGS Desarrollo de Aplicaciones Web (DAW)", "institution": "IES Ficticio de Valencia", "year": 2024}]'::jsonb,
        'cv-e2e.pdf',
        'Usuaria E2E Prueba. Desarrolladora Web Junior. Valencia. Experiencia: APIs REST con Java 21 y Spring Boot, '
            || 'PostgreSQL, Git y tests con JUnit. Prácticas de 12 meses como desarrolladora Java. Inglés B2.',
        now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO profile_skills (id, profile_id, name, level, years)
VALUES ('33333333-0e2e-4000-8000-000000000001', '22222222-0e2e-4000-8000-000000000001', 'Java', 'AVANZADO', 2),
       ('33333333-0e2e-4000-8000-000000000002', '22222222-0e2e-4000-8000-000000000001', 'Spring Boot', 'INTERMEDIO', 2),
       ('33333333-0e2e-4000-8000-000000000003', '22222222-0e2e-4000-8000-000000000001', 'PostgreSQL', 'INTERMEDIO', 1),
       ('33333333-0e2e-4000-8000-000000000004', '22222222-0e2e-4000-8000-000000000001', 'Git', 'INTERMEDIO', 2),
       ('33333333-0e2e-4000-8000-000000000005', '22222222-0e2e-4000-8000-000000000001', 'React', 'BASICO', 1)
ON CONFLICT DO NOTHING;

INSERT INTO experiences (id, profile_id, company, role, start_date, end_date, description, sort_order)
VALUES ('44444444-0e2e-4000-8000-000000000001', '22222222-0e2e-4000-8000-000000000001',
        'Tecnologías Turia S.L.', 'Desarrolladora Junior', DATE '2025-03-01', NULL,
        'APIs REST con Java 21 y Spring Boot sobre PostgreSQL. Tests con JUnit.', 0)
ON CONFLICT DO NOTHING;
