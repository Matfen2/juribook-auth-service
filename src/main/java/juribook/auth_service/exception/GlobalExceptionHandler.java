package juribook.auth_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Gestionnaire global des exceptions HTTP pour l'auth-service.
 *
 * Codes HTTP retournés :
 *   400 → validation des champs (@Valid) ou argument invalide
 *   404 → utilisateur introuvable (UserNotFoundException)
 *   409 → email ou barreau déjà utilisé (IllegalArgumentException avec message "existe déjà")
 *   500 → erreur inattendue
 *
 * Règle de distinction 400 vs 409 :
 *   On inspecte le message de l'exception pour déterminer le code.
 *   Si le message contient "existe déjà" ou "déjà utilisé" → 409 Conflict
 *   Sinon → 400 Bad Request
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 400 — Validation des champs (@Valid) ─────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field   = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            errors.put(field, message);
        });

        return ResponseEntity.badRequest()
                .body(Map.of("errors", errors, "message", "Données invalides"));
    }

    // ── 404 — Utilisateur introuvable ─────────────────────────
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserNotFound(
            UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", ex.getMessage()));
    }

    // ── 400 / 409 — Règle métier ─────────────────────────────
    // IllegalArgumentException est utilisée pour deux cas distincts :
    //   - Email/barreau déjà utilisé → 409 Conflict
    //   - Compte désactivé ou autre règle → 400 Bad Request
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(
            IllegalArgumentException ex) {

        String message = ex.getMessage();

        // Détection des cas de conflit (doublon)
        boolean isConflict = message != null && (
            message.contains("existe déjà") ||
            message.contains("déjà utilisé") ||
            message.contains("déjà enregistré")
        );

        HttpStatus status = isConflict ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(Map.of("message", message != null ? message : "Requête invalide"));
    }

    // ── 500 — Erreur inattendue ───────────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Erreur interne du serveur"));
    }
}