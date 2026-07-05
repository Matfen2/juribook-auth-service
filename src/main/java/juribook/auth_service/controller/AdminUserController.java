package juribook.auth_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
}