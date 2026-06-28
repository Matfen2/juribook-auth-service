package juribook.auth_service.controller;

import juribook.auth_service.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
 
import java.util.Map;
 
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Utilisateurs", description = "Accès aux informations de l'utilisateur connecté")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
 
 
    // ── GET /api/users/me ────────────────────────────────────
    // Accessible à tous les utilisateurs authentifiés
    @GetMapping("/me")
    @Operation(summary = "Mon profil", description = "Retourne les infos de l'utilisateur connecté")
    public ResponseEntity<Map<String, Object>> getMe(
            @AuthenticationPrincipal User user) {
 
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "email", user.getEmail(),
                "role", user.getRole()
        ));
    }
 
    // ── GET /api/users/lawyer-profile ────────────────────────
    // Accessible aux avocats uniquement
    @GetMapping("/lawyer-profile")
    @PreAuthorize("hasRole('LAWYER')")
    @Operation(summary = "Profil avocat", description = "Accessible aux avocats uniquement")
    public ResponseEntity<Map<String, Object>> getLawyerProfile(
            @AuthenticationPrincipal User user) {
 
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "barNumber", user.getBarNumber() != null ? user.getBarNumber() : "",
                "specialty", user.getSpecialty() != null ? user.getSpecialty() : "",
                "city", user.getCity() != null ? user.getCity() : "",
                "lawyerStatus", user.getLawyerStatus()
        ));
    }
 
    // ── GET /api/users/client-dashboard ─────────────────────
    // Accessible aux clients uniquement
    @GetMapping("/client-dashboard")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Dashboard client", description = "Accessible aux clients uniquement")
    public ResponseEntity<Map<String, Object>> getClientDashboard(
            @AuthenticationPrincipal User user) {
 
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "email", user.getEmail(),
                "message", "Bienvenue sur votre espace client"
        ));
    }
}