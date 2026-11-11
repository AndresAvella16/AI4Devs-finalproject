Tarea: escribir un CV adaptado del candidato, orientado a una oferta de empleo concreta.

El mensaje contiene cuatro bloques de datos:
- <puesto> ... </puesto>: los datos de la oferta en JSON (puesto, empresa, ubicación, modalidad).
- <oferta> ... </oferta>: el texto original de la oferta, tal como lo ha pegado el candidato.
- <perfil> ... </perfil>: el perfil profesional del candidato en JSON (titular, resumen, años de experiencia, ubicación, habilidades, experiencias, formación e idiomas).
- <encaje> ... </encaje>: el análisis de encaje en JSON: un resumen y, para cada requisito de la oferta, su tipo (OBLIGATORIO o DESEABLE), el resultado (CUMPLE, PARCIAL o NO_CUMPLE) y la evidencia del perfil.

Reglas del CV adaptado:
- Escríbelo en el mismo idioma que el texto de la oferta.
- Reordena y redacta el contenido del perfil para que lo más relevante para esta oferta aparezca primero: un titular y un resumen orientados al puesto, las habilidades que piden los requisitos CUMPLE y PARCIAL antes que las demás, y en cada experiencia los logros relacionados con la oferta.
- Usa solo experiencias, habilidades, empresas, formación, fechas, cifras e idiomas que aparezcan en el perfil. No inventes nada ni añadas lo que el perfil no dice, aunque la oferta lo pida.
- No escribas el nombre del candidato ni sus datos de contacto: la aplicación los pone en la cabecera. Sin huecos para rellenar entre corchetes.
- Formato Markdown: un título de nivel 1 con el titular, títulos de nivel 2 para las secciones (Resumen, Habilidades, Experiencia, Formación, Idiomas) y listas con guiones. Sin tablas, imágenes ni enlaces.

Devuelve:
- contentMarkdown: el CV completo en Markdown.
