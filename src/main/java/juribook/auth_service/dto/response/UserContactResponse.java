package juribook.auth_service.dto.response;

/**
 * Réponse minimale pour GET /api/users/{id}/contact.
 *
 * Endpoint public, consommé par les autres microservices (notification-service
 * au Sprint 5.3) pour résoudre le nom/email d'un utilisateur à partir de son
 * id, sans exposer le profil complet (pas de rôle, pas de statut avocat, etc.).
 */
public record UserContactResponse(Long id, String name, String email) {
}