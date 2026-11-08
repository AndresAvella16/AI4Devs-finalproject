-- JP-BE-05: la modalidad de una oferta siempre tiene valor. Una oferta PENDIENTE o FALLIDA es DESCONOCIDO,
-- así la API devuelve siempre workMode (sección 4) y la BD lo garantiza (sección 3.2).
UPDATE job_offers SET work_mode = 'DESCONOCIDO' WHERE work_mode IS NULL;

ALTER TABLE job_offers
    ALTER COLUMN work_mode SET DEFAULT 'DESCONOCIDO',
    ALTER COLUMN work_mode SET NOT NULL;
