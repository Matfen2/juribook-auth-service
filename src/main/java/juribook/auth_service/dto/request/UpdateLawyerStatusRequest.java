package juribook.auth_service.dto.request;

import jakarta.validation.constraints.NotNull;
import juribook.auth_service.entity.LawyerStatus;
import lombok.Data;

/**
 * DTO pour la mise à jour du statut avocat par l'admin.
 * Appelé par PUT /api/admin/lawyers/{id}/status
 */
@Data
public class UpdateLawyerStatusRequest {

    @NotNull(message = "Le statut est obligatoire")
    private LawyerStatus status;  // APPROVED | REJECTED

    // Motif de refus (obligatoire si status = REJECTED)
    private String reason;
}