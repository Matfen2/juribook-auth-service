package juribook.auth_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import juribook.auth_service.entity.Role;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponse {

    private String token;          // JWT access token (24h)
    private String refreshToken;   // Refresh token opaque (7 jours)
    private String type;           // "Bearer"
    private Long id;
    private String name;
    private String email;
    private Role role;
    private String message;
}