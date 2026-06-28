package juribook.auth_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import juribook.auth_service.entity.Role;
import lombok.Builder;
import lombok.Data;
 
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegisterClientResponse {
 
    private Long id;
    private String name;
    private String email;
    private String phone;
    private Role role;
    private String message;
}