/** Tamaño máximo del CV (sección 2.5): 5 MB, como `spring.servlet.multipart.max-file-size`. */
export const CV_MAX_BYTES = 5 * 1024 * 1024;

/** Mensaje de la HU-01 para un fichero que no es PDF o pesa más de 5 MB (el mismo `detail` que el 415 del backend). */
export const CV_FILE_ERROR = 'Solo se admiten PDF de hasta 5 MB.';

/**
 * Comprobación en el cliente antes de subir el CV, para no enviar nada a la IA con un fichero que el backend va a
 * rechazar. Exige el tipo MIME `application/pdf`, como el backend (la extensión sola no basta). Las 10 páginas y la
 * firma `%PDF-` solo las comprueba el backend.
 */
export function validateCvFile(file: File): string | null {
  if (file.type !== 'application/pdf' || file.size > CV_MAX_BYTES) return CV_FILE_ERROR;
  if (file.size === 0) return 'El fichero está vacío.';
  return null;
}
