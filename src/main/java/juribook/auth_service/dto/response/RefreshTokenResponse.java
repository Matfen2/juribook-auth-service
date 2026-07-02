package juribook.auth_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RefreshTokenResponse {

    private String accessToken;   // nouveau JWT (24h)
    private String refreshToken;  // nouveau refresh token (rotation)
    private String type;          // "Bearer"
    private String message;
}