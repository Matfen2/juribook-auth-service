package juribook.auth_service.dto.response;

import juribook.auth_service.entity.LawyerStatus;

/**
 * Réponse de GET /api/users/lawyer-profile.
 */
public record LawyerProfileMeResponse(
    Long id,
    String name,
    String barNumber,
    String specialty,
    String city,
    LawyerStatus lawyerStatus
) {}