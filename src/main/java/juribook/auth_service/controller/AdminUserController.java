package juribook.auth_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import juribook.auth_service.dto.request.DeactivateUserRequest;
import juribook.auth_service.dto.response.AdminUserResponse;
import juribook.auth_service.entity.Role;
import juribook.auth_service.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Route déjà couverte par SecurityConfig : "/api/admin/**" → hasRole("ADMIN").
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Gestion des utilisateurs (clients + avocats)")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @Operation(summary = "Rechercher les utilisateurs",
        description = "Filtres cumulables optionnels : role, enabled, city. Réservé aux ADMIN.")
    public ResponseEntity<Page<AdminUserResponse>> search(
            @Parameter(description = "CLIENT, LAWYER ou ADMIN") @RequestParam(required = false) Role role,
            @Parameter(description = "true = actif, false = désactivé/suspendu") @RequestParam(required = false) Boolean enabled,
            @Parameter(description = "Ville d'exercice (avocats uniquement)") @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(adminUserService.searchUsers(role, enabled, city, page, size));
    }

    // ── Activer / désactiver un compte ──────────
    @PatchMapping("/{id}/deactivate")
    @Operation(
        summary = "Désactiver un compte",
        description = """
            Le compte désactivé ne peut plus se connecter (vérifié dans
            AuthService.login). N'invalide pas un JWT déjà émis — reste
            valide jusqu'à expiration naturelle. Motif optionnel dans le
            corps de la requête ; par défaut : "Désactivé manuellement
            par un administrateur".
            """
    )
    public ResponseEntity<AdminUserResponse> deactivate(
            @PathVariable Long id,
            @RequestBody(required = false) DeactivateUserRequest request) {

        String reason = request != null ? request.reason() : null;
        return ResponseEntity.ok(adminUserService.deactivateUser(id, reason));
    }

    @PatchMapping("/{id}/activate")
    @Operation(
        summary = "Réactiver un compte",
        description = "Efface le motif/date de suspension. Un avocat réactivé reprend directement son lawyerStatus d'avant, pas de re-validation forcée."
    )
    public ResponseEntity<AdminUserResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(adminUserService.activateUser(id));
    }
}