package juribook.auth_service.dto.response;

import juribook.auth_service.entity.LawyerStatus;
import juribook.auth_service.entity.Role;
import juribook.auth_service.entity.User;

import java.time.LocalDateTime;

public record AdminUserResponse(
    Long id,
    String name,
    String email,
    String phone,
    Role role,
    boolean enabled,
    String barNumber,
    String specialty,
    String city,
    LawyerStatus lawyerStatus,
    String suspendedReason,
    LocalDateTime suspendedAt,
    LocalDateTime createdAt
) {
    public static AdminUserResponse from(User u) {
        return new AdminUserResponse(
            u.getId(), u.getName(), u.getEmail(), u.getPhone(),
            u.getRole(), u.isEnabled(), u.getBarNumber(), u.getSpecialty(),
            u.getCity(), u.getLawyerStatus(), u.getSuspendedReason(),
            u.getSuspendedAt(), u.getCreatedAt()
        );
    }
}