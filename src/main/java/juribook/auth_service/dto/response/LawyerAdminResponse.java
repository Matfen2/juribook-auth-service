package juribook.auth_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import juribook.auth_service.entity.LawyerStatus;
import juribook.auth_service.entity.Role;
import juribook.auth_service.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO de réponse admin pour un profil avocat.
 * Retourné par GET /api/admin/lawyers et GET /api/admin/lawyers/{id}
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LawyerAdminResponse {

    private Long id;
    private String name;
    private String email;
    private String phone;
    private Role role;
    private String barNumber;
    private String specialty;
    private String city;
    private LawyerStatus lawyerStatus;
    private boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static LawyerAdminResponse from(User user) {
        return LawyerAdminResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .barNumber(user.getBarNumber())
                .specialty(user.getSpecialty())
                .city(user.getCity())
                .lawyerStatus(user.getLawyerStatus())
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}