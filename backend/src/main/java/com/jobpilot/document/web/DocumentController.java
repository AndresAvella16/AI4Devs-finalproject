package com.jobpilot.document.web;

import com.jobpilot.document.domain.DocumentType;
import com.jobpilot.document.service.DocumentPdf;
import com.jobpilot.document.service.DocumentService;
import com.jobpilot.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Carta y CV adaptado de una oferta: generación con IA, historial de versiones, edición y exportación a PDF (F4). */
@Tag(name = "Documents")
@RestController
@RequestMapping("/api/v1")
public class DocumentController {

    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @Operation(summary = "Genera con IA una carta o un CV adaptado a la oferta",
            description = "Parte del perfil, de la oferta y de su último análisis de encaje, y crea la versión "
                    + "siguiente (editedByUser: false). La carta se firma con el nombre del usuario, que la IA no "
                    + "recibe. 404 si la oferta no existe o es de otro usuario; 409 analysis-required si la oferta "
                    + "no tiene análisis; 409 document-version-conflict si otra escritura crea esa versión a la vez; "
                    + "429 / 502 / 503 / 504 si falla la IA.")
    @PostMapping("/offers/{offerId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse generate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID offerId,
            @Valid @RequestBody CreateDocumentRequest request) {
        return DocumentResponse.from(documents.generate(user.userId(), offerId, request.type()));
    }

    @Operation(summary = "Última versión de la carta o del CV adaptado y su historial",
            description = "latest es la versión más alta, completa; versions, todas, de la más reciente a la más "
                    + "antigua y sin el contenido. 404 si la oferta no existe, es de otro usuario o no tiene "
                    + "ningún documento de ese tipo.")
    @GetMapping("/offers/{offerId}/documents")
    public DocumentHistoryResponse history(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID offerId, @RequestParam DocumentType type) {
        return DocumentHistoryResponse.from(documents.history(user.userId(), offerId, type));
    }

    @Operation(summary = "Guarda la edición del usuario como versión nueva",
            description = "Crea la versión siguiente a la del documento indicado, con editedByUser: true. 409 "
                    + "document-version-conflict si ese documento ya no es la última versión (otra edición o "
                    + "regeneración se ha adelantado); 404 si el documento no existe o es de otro usuario.")
    @PostMapping("/documents/{documentId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse createVersion(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID documentId, @Valid @RequestBody CreateDocumentVersionRequest request) {
        return DocumentResponse.from(documents.createVersion(user.userId(), documentId, request.contentMarkdown()));
    }

    @Operation(summary = "Exporta esa versión del documento a PDF",
            description = "PDF generado en el momento con el nombre del usuario en la cabecera, como descarga "
                    + "(carta-v{n}.pdf o cv-adaptado-v{n}.pdf) y sin caché. 404 si el documento no existe o es de "
                    + "otro usuario.")
    @GetMapping(value = "/documents/{documentId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID documentId) {
        DocumentPdf pdf = documents.pdf(user.userId(), documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(pdf.fileName()).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(pdf.content());
    }
}
