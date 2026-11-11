import { z } from 'zod';

/**
 * `CreateDocumentVersionRequest` (sección 4): `@NotBlank @Size(max = 20000)`. `@Size` cuenta unidades UTF-16
 * (`String.length()` en Java), igual que `length` en JS. El texto se envía tal cual, sin recortar.
 */
export const CONTENT_MAX = 20_000;

export const documentVersionSchema = z.object({
  contentMarkdown: z
    .string()
    .refine((value) => value.trim() !== '', { message: 'Escribe el contenido del documento.' })
    .refine((value) => value.length <= CONTENT_MAX, {
      message: 'El documento no puede superar los 20.000 caracteres.',
    }),
});

export type DocumentVersionForm = z.infer<typeof documentVersionSchema>;
