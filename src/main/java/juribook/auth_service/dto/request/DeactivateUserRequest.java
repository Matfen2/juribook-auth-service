package juribook.auth_service.dto.request;

/**
 * Corps optionnel pour PATCH /api/admin/users/{id}/deactivate.
 * reason peut être null/absent, AdminUserService applique alors un
 * motif par défaut ("Désactivé manuellement par un administrateur").
 */
public record DeactivateUserRequest(String reason) {
}