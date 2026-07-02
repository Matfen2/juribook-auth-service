package juribook.auth_service.controller;

import juribook.auth_service.dto.response.ClientDashboardResponse;
import juribook.auth_service.dto.response.LawyerProfileMeResponse;
import juribook.auth_service.dto.response.UserContactResponse;
import juribook.auth_service.dto.response.UserMeResponse;
import juribook.auth_service.entity.User;
import juribook.auth_service.exception.UserNotFoundException;
import juribook.auth_service.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Controller des informations de l'utilisateur connecté.
 *
 * Les réponses utilisent des DTOs records (UserMeResponse,
 * LawyerProfileMeResponse, ClientDashboardResponse) plutôt que
 * des Map<String, Object> — suite à la review d'Abdelhadi (mentor) :
 * un contrat de réponse typé est plus sûr et plus lisible qu'une Map
 * dont les clés ne sont vérifiées qu'à l'exécution.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Utilisateurs", description = "Accès aux informations de l'utilisateur connecté")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserRepository userRepository;

    // ── GET /api/users/me ────────────────────────────────────
    @GetMapping("/me")
    @Operation(summary = "Mon profil", description = "Retourne les infos de l'utilisateur connecté")
    public ResponseEntity<UserMeResponse> getMe(@AuthenticationPrincipal User user) {
        UserMeResponse response = new UserMeResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getRole()
        );
        return ResponseEntity.ok(response);
    }

    // ── GET /api/users/lawyer-profile ────────────────────────
    @GetMapping("/lawyer-profile")
    @PreAuthorize("hasRole('LAWYER')")
    @Operation(summary = "Profil avocat", description = "Accessible aux avocats uniquement")
    public ResponseEntity<LawyerProfileMeResponse> getLawyerProfile(@AuthenticationPrincipal User user) {
        LawyerProfileMeResponse response = new LawyerProfileMeResponse(
            user.getId(),
            user.getName(),
            user.getBarNumber() != null ? user.getBarNumber() : "",
            user.getSpecialty() != null ? user.getSpecialty() : "",
            user.getCity() != null ? user.getCity() : "",
            user.getLawyerStatus()
        );
        return ResponseEntity.ok(response);
    }

    // ── GET /api/users/client-dashboard ─────────────────────
    @GetMapping("/client-dashboard")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Dashboard client", description = "Accessible aux clients uniquement")
    public ResponseEntity<ClientDashboardResponse> getClientDashboard(@AuthenticationPrincipal User user) {
        ClientDashboardResponse response = new ClientDashboardResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            "Bienvenue sur votre espace client"
        );
        return ResponseEntity.ok(response);
    }

    // ── GET /api/users/{id}/contact ──────────────────────────
    // Sprint 5.3 : endpoint PUBLIC (pas de token requis), consommé par
    // les autres microservices pour résoudre nom/email à partir d'un id
    // (ex: notification-service, pour l'email de confirmation client).
    // Volontairement minimal : pas de rôle, pas de statut avocat, pas de
    // mot de passe — juste ce qui est nécessaire à un email.
    @GetMapping("/{id}/contact")
    @Operation(
        summary = "Contact minimal d'un utilisateur (nom + email)",
        description = "Public — utilisé en interne par les autres microservices pour l'envoi d'emails."
    )
    public ResponseEntity<UserContactResponse> getContact(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Utilisateur introuvable : id=" + id));
        return ResponseEntity.ok(new UserContactResponse(user.getId(), user.getName(), user.getEmail()));
    }
}