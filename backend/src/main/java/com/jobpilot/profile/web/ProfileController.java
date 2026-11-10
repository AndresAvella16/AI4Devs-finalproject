package com.jobpilot.profile.web;

import com.jobpilot.profile.service.ProfileService;
import com.jobpilot.profile.service.SavedProfile;
import com.jobpilot.shared.error.InvalidRequestException;
import com.jobpilot.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Perfil del usuario autenticado (F1). Cada usuario solo tiene acceso al suyo: no hay ids en las rutas. */
@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @Operation(summary = "Sube el CV en PDF y devuelve el borrador del perfil extraído con IA, sin guardarlo",
            description = "PDF de hasta 5 MB y 10 páginas, en el campo multipart 'file'. El PDF no se guarda: su "
                    + "texto se conserva 30 minutos para añadirlo al perfil con PUT /profile. 400 si falta, está "
                    + "vacío o es demasiado grande; 415 invalid-cv-file si no es un PDF legible; 422 "
                    + "cv-without-text si no tiene texto (escaneado); 429 / 502 / 503 / 504 si falla la IA.")
    @PostMapping(path = "/cv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CvDraftResponse uploadCv(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestPart(name = "file", required = false) MultipartFile file) throws IOException {
        if (file == null) {
            throw new InvalidRequestException("file", "Sube tu CV en PDF.");
        }
        return CvDraftResponse.from(profiles.uploadCv(user.userId(), file.getOriginalFilename(),
                file.getContentType(), file.getBytes()));
    }

    @Operation(summary = "Perfil del usuario", description = "404 si todavía no ha creado su perfil.")
    @GetMapping
    public ProfileResponse get(@AuthenticationPrincipal AuthenticatedUser user) {
        return ProfileResponse.from(profiles.get(user.userId()));
    }

    @Operation(summary = "Guarda el perfil revisado: lo crea (201) o lo reemplaza entero (200)",
            description = "Sirve también para el perfil hecho a mano, sin CV. Si se ha subido un CV en los últimos "
                    + "30 minutos, su texto pasa al perfil. 409 profile-concurrent-update si otra petición lo ha "
                    + "guardado a la vez.")
    @PutMapping
    public ResponseEntity<ProfileResponse> save(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ProfileRequest request) {
        SavedProfile saved = profiles.save(user.userId(), request.toData());
        return ResponseEntity.status(saved.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ProfileResponse.from(saved.details()));
    }
}
