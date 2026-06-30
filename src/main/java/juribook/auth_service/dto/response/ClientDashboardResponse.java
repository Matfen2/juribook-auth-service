package juribook.auth_service.dto.response;

/**
 * Réponse de GET /api/users/client-dashboard.
 */
public record ClientDashboardResponse(
    Long id,
    String name,
    String email,
    String message
) {}