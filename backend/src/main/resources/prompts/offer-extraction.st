Tarea: extraer los datos clave y los requisitos de una oferta de empleo.

El mensaje contiene el texto de la oferta entre <oferta> y </oferta>, tal como lo ha pegado el candidato.

Extrae:
- title: el puesto, tal como lo nombra la oferta (máximo 160 caracteres).
- company: la empresa que contrata; null si no aparece.
- location: la ciudad o zona; null si no aparece.
- workMode: REMOTO, HIBRIDO o PRESENCIAL según la oferta; DESCONOCIDO si no lo dice.
- salary: el salario bruto anual solo si aparece de forma explícita, con min y/o max en unidades (24.000 € → 24000) y currency en código ISO 4217 (EUR, USD, GBP...). Si solo aparece un extremo, el otro es null. Si no aparece salario, salary es null. No conviertas salarios mensuales ni por horas: en ese caso salary es null.
- requirements: todos los requisitos que se piden al candidato, en el orden en que aparecen (entre 1 y 40). Para cada uno:
  - description: el requisito redactado de forma breve y autocontenida, en el idioma de la oferta (máximo 300 caracteres). Un requisito por elemento: si una frase pide varias cosas independientes, sepáralas.
  - type: OBLIGATORIO si la oferta lo exige ("requisitos", "imprescindible", "se requiere"...); DESEABLE si solo lo valora ("se valorará", "deseable", "plus"...). Si no está claro, OBLIGATORIO.
  - category: TECNICO (tecnologías, herramientas, conocimientos técnicos), IDIOMA, EXPERIENCIA (años o tipo de experiencia), FORMACION (titulaciones, certificaciones) u OTRO (carné, disponibilidad, habilidades personales...).

No incluyas como requisitos lo que la empresa ofrece (salario, beneficios, horario, formación que imparte).
