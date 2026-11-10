package com.jobpilot.shared.error;

/**
 * El fichero subido como CV no es un PDF que se pueda leer: el tipo MIME no es {@code application/pdf}, no empieza
 * por la firma {@code %PDF}, está corrupto o tiene contraseña de apertura (→ 415 {@code invalid-cv-file}).
 */
public class InvalidCvFileException extends RuntimeException {

    public static final String DETAIL = "Solo se admiten PDF de hasta 5 MB.";

    public InvalidCvFileException() {
        super(DETAIL);
    }
}
