package juribook.auth_service.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
 
@Data
public class LoginRequest {
 
    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide")
    private String email;
 
    @NotBlank(message = "Le mot de passe est obligatoire")
    private String password;
}