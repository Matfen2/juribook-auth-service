package juribook.auth_service.dto.response;

import juribook.auth_service.entity.Role;

/**
 * Réponse de GET /api/users/me.
 * Record plutôt que Map<String, Object> — suite à la review d'Abdelhadi (mentor) :
 * un DTO typé documente le contrat d'API et évite les erreurs de clé/cast à l'exécution.
 */
public record UserMeResponse(
    Long id,
    String name,
    String email,
    Role role
) {}