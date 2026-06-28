package juribook.auth_service.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
 
@Data
public class RegisterLawyerRequest {
 
    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 100, message = "Le nom ne peut pas dépasser 100 caractères")
    private String name;
 
    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide")
    private String email;
 
    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
    private String password;
 
    @Size(max = 20, message = "Le téléphone ne peut pas dépasser 20 caractères")
    private String phone;
 
    @NotBlank(message = "Le numéro de barreau est obligatoire")
    @Pattern(regexp = "^[0-9]{5}$", message = "Le numéro de barreau doit contenir exactement 5 chiffres")
    private String barNumber;
 
    @NotBlank(message = "La spécialité est obligatoire")
    @Size(max = 100, message = "La spécialité ne peut pas dépasser 100 caractères")
    private String specialty;
 
    @NotBlank(message = "La ville est obligatoire")
    @Size(max = 100, message = "La ville ne peut pas dépasser 100 caractères")
    private String city;
}