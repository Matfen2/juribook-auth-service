package juribook.auth_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import juribook.auth_service.dto.request.UpdateLawyerStatusRequest;
import juribook.auth_service.dto.response.LawyerAdminResponse;
import juribook.auth_service.entity.LawyerStatus;
import juribook.auth_service.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller REST pour l'administration des profils avocats.
 *
 * Toutes les routes sont protégées par @PreAuthorize("hasRole('ADMIN')")
 * → uniquement accessible avec un token JWT ayant le rôle ADMIN.
 *
 * Endpoints :
 *   GET    /api/admin/lawyers            → tous les avocats
 *   GET    /api/admin/lawyers/pending    → en attente de validation
 *   GET    /api/admin/lawyers/{id}       → détail d'un avocat
 *   PUT    /api/admin/lawyers/{id}/status → valider / refuser
 *   GET    /api/admin/stats              → compteurs dashboard
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Administration", description = "Validation des profils avocats — ADMIN uniquement")
public class AdminController {

    private final AdminService adminService;

    // ── GET /api/admin/lawyers — tous les avocats ───────────
    @GetMapping("/lawyers")
    @Operation(summary = "Lister tous les avocats",
               description = "Retourne tous les avocats (PENDING + APPROVED + REJECTED)")
    @ApiResponse(responseCode = "200", description = "Liste retournée")
    public ResponseEntity<List<LawyerAdminResponse>> getAllLawyers() {
        return ResponseEntity.ok(adminService.getAllLawyers());
    }

    // ── GET /api/admin/lawyers/pending — file d'attente ─────
    @GetMapping("/lawyers/pending")
    @Operation(summary = "Avocats en attente de validation")
    @ApiResponse(responseCode = "200", description = "Liste retournée")
    public ResponseEntity<List<LawyerAdminResponse>> getPendingLawyers() {
        return ResponseEntity.ok(adminService.getLawyersByStatus(LawyerStatus.PENDING));
    }

    // ── GET /api/admin/lawyers?status=APPROVED ──────────────
    @GetMapping("/lawyers/by-status")
    @Operation(summary = "Avocats par statut (PENDING | APPROVED | REJECTED)")
    public ResponseEntity<List<LawyerAdminResponse>> getLawyersByStatus(
            @RequestParam LawyerStatus status) {
        return ResponseEntity.ok(adminService.getLawyersByStatus(status));
    }

    // ── GET /api/admin/lawyers/{id} — détail avocat ─────────
    @GetMapping("/lawyers/{id}")
    @Operation(summary = "Détail d'un profil avocat")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Profil retourné"),
        @ApiResponse(responseCode = "404", description = "Avocat introuvable")
    })
    public ResponseEntity<LawyerAdminResponse> getLawyerById(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getLawyerById(id));
    }

    // ── PUT /api/admin/lawyers/{id}/status — valider/refuser
    @PutMapping("/lawyers/{id}/status")
    @Operation(
        summary = "Valider ou refuser un profil avocat",
        description = """
            APPROVED → active le compte (enabled = true)
            REJECTED → désactive le compte (enabled = false)
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Statut mis à jour"),
        @ApiResponse(responseCode = "400", description = "Statut invalide"),
        @ApiResponse(responseCode = "404", description = "Avocat introuvable")
    })
    public ResponseEntity<LawyerAdminResponse> updateLawyerStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLawyerStatusRequest request) {
        return ResponseEntity.ok(adminService.updateLawyerStatus(id, request));
    }

    // ── GET /api/admin/stats — compteurs dashboard ──────────
    @GetMapping("/stats")
    @Operation(summary = "Statistiques du dashboard admin",
               description = "Retourne le nombre d'avocats par statut et le nombre de clients")
    @ApiResponse(responseCode = "200", description = "Stats retournées")
    public ResponseEntity<Map<String, Long>> getStats() {
        return ResponseEntity.ok(adminService.getStats());
    }
}