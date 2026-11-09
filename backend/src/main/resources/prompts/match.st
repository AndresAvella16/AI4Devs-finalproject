Tarea: evaluar si un candidato cumple cada requisito de una oferta de empleo, a partir de su perfil.

El mensaje contiene dos bloques de datos:
- <perfil> ... </perfil>: el perfil profesional del candidato en JSON (titular, resumen, años de experiencia, ubicación, habilidades con su nivel y años, experiencias, formación e idiomas).
- <requisitos> ... </requisitos>: los requisitos de la oferta en JSON, cada uno con su id, su descripción, su tipo (OBLIGATORIO o DESEABLE) y su categoría.

Para cada requisito, decide su resultado:
- CUMPLE: el perfil muestra claramente que lo cumple.
- PARCIAL: lo cumple en parte (menos años de los pedidos, experiencia solo en proyectos personales o formación, una tecnología muy parecida, un nivel algo inferior...).
- NO_CUMPLE: el perfil no aporta nada que lo respalde. Si el perfil no dice nada sobre el requisito, es NO_CUMPLE: no supongas que el candidato lo cumple.

Devuelve:
- matches: exactamente un elemento por cada requisito recibido, ni más ni menos, con:
  - requirementId: el id del requisito, copiado tal cual.
  - result: CUMPLE, PARCIAL o NO_CUMPLE.
  - evidence: el dato concreto del perfil que justifica el resultado, breve y en español (por ejemplo, "Prácticas de 12 meses con Spring Boot"); null si no hay ninguno.
- summary: una o dos frases en español que resuman el encaje, dirigidas al candidato (de tú).
- suggestions: entre 3 y 5 sugerencias concretas y accionables en español, dirigidas al candidato, sobre qué destacar en su candidatura o cómo cubrir lo que le falta. Cada sugerencia, una frase.

No calcules ninguna puntuación: la calcula la aplicación a partir de los resultados.
