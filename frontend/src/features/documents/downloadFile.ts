/**
 * Guarda un `Blob` en el equipo con un `<a download>` temporal. La URL `blob:` se libera en la siguiente vuelta del
 * bucle de eventos: revocarla en el mismo instante del clic puede cancelar la descarga en algunos navegadores.
 */
export function downloadFile(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.rel = 'noopener';
  link.style.display = 'none';
  document.body.append(link);
  try {
    link.click();
  } finally {
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }
}
