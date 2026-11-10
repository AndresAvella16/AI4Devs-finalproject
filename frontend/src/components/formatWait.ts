/** Espera de un `Retry-After` en texto: "30 segundos", "1 minuto", "2 minutos" (se redondea hacia arriba). */
export function formatWait(seconds: number): string {
  if (seconds < 60) return seconds === 1 ? '1 segundo' : `${seconds} segundos`;
  const minutes = Math.ceil(seconds / 60);
  return minutes === 1 ? '1 minuto' : `${minutes} minutos`;
}
