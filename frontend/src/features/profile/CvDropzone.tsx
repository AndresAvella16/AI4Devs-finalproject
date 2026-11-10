import { useState, type DragEvent } from 'react';

interface CvDropzoneProps {
  onFile: (file: File) => void;
  disabled?: boolean;
}

/**
 * Zona para subir el CV: se suelta el PDF encima o se elige con el botón. El botón es la `<label>` de un
 * `<input type="file">` oculto a la vista pero no al teclado ni a los lectores de pantalla, así que se usa igual con
 * el ratón, el teclado o un lector. La comprobación del fichero la hace quien recibe `onFile`.
 */
export function CvDropzone({ onFile, disabled = false }: CvDropzoneProps) {
  const [dragging, setDragging] = useState(false);

  const onDragOver = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    if (!disabled) setDragging(true);
  };
  const onDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setDragging(false);
    const file = event.dataTransfer.files[0];
    if (file && !disabled) onFile(file);
  };

  return (
    <div
      data-testid="cv-dropzone"
      onDragOver={onDragOver}
      onDragEnter={onDragOver}
      onDragLeave={() => setDragging(false)}
      onDrop={onDrop}
      className={`flex flex-col items-center gap-3 rounded-lg border-2 border-dashed p-8 text-center transition-colors ${
        dragging ? 'border-indigo-500 bg-indigo-50' : 'border-slate-300 bg-white'
      } ${disabled ? 'opacity-60' : ''}`}
    >
      <p className="font-medium text-slate-800">Arrastra aquí tu CV en PDF</p>
      <p className="text-sm text-slate-500">o</p>
      <input
        id="cv-file-input"
        data-testid="cv-file-input"
        type="file"
        accept="application/pdf"
        disabled={disabled}
        aria-describedby="cv-file-hint"
        className="peer sr-only"
        onChange={(event) => {
          const file = event.target.files?.[0];
          // Se vacía para poder elegir otra vez el mismo fichero (p. ej. al reintentar tras cancelar).
          event.target.value = '';
          if (file) onFile(file);
        }}
      />
      <label
        htmlFor="cv-file-input"
        className="cursor-pointer rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-indigo-700 peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-indigo-600 peer-disabled:cursor-not-allowed peer-disabled:bg-indigo-400"
      >
        Selecciona tu CV en PDF
      </label>
      <p id="cv-file-hint" className="text-sm text-slate-500">
        PDF con texto, de hasta 5 MB y 10 páginas. No guardamos el fichero, solo su texto.
      </p>
    </div>
  );
}
