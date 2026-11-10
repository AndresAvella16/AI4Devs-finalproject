package com.jobpilot.shared.error;

/**
 * El PDF es válido pero no tiene texto que extraer, como un CV escaneado (→ 422 {@code cv-without-text}). El
 * frontend sugiere subir un PDF con texto o rellenar el perfil a mano.
 */
public class CvWithoutTextException extends RuntimeException {

    public CvWithoutTextException() {
        super("No se ha podido extraer texto del PDF; puede que sea un documento escaneado. Sube un PDF con texto "
                + "o rellena el perfil a mano.");
    }
}
