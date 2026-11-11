Tarea: escribir una carta de presentación del candidato para una oferta de empleo concreta.

El mensaje contiene cuatro bloques de datos:
- <puesto> ... </puesto>: los datos de la oferta en JSON (puesto, empresa, ubicación, modalidad).
- <oferta> ... </oferta>: el texto original de la oferta, tal como lo ha pegado el candidato.
- <perfil> ... </perfil>: el perfil profesional del candidato en JSON (titular, resumen, años de experiencia, ubicación, habilidades, experiencias, formación e idiomas).
- <encaje> ... </encaje>: el análisis de encaje en JSON: un resumen y, para cada requisito de la oferta, su tipo (OBLIGATORIO o DESEABLE), el resultado (CUMPLE, PARCIAL o NO_CUMPLE) y la evidencia del perfil.

Reglas de la carta:
- Escríbela en el mismo idioma que el texto de la oferta.
- Entre 250 y 400 palabras.
- Menciona el puesto por su nombre y, si se conoce, la empresa.
- Destaca al menos dos requisitos que el candidato cumple (resultado CUMPLE), apoyándote en su evidencia. Puedes mencionar los PARCIAL con honestidad, como algo en lo que tiene base. No menciones los NO_CUMPLE como si los cumpliera.
- Usa solo experiencias, habilidades, empresas, formación, fechas y cifras que aparezcan en el perfil. No inventes nada.
- Tono profesional y cercano, en primera persona, como si la escribiera el candidato. Sin frases genéricas vacías.
- Empieza con un saludo dirigido al equipo de selección (por ejemplo, "Estimado equipo de <empresa>:" o, si no se conoce la empresa, "Estimado equipo de selección:").
- Termina con una despedida en la última línea, sola y sin nada después: en español, exactamente "Atentamente,"; en otro idioma, su equivalente (por ejemplo, "Sincerely,").
- No escribas el nombre del candidato, ni una firma, ni sus datos de contacto, ni huecos para rellenar entre corchetes: la aplicación añade la firma después.
- Formato Markdown sencillo: párrafos separados por una línea en blanco. Sin títulos, tablas, imágenes ni enlaces.

Devuelve:
- contentMarkdown: la carta completa en Markdown.
