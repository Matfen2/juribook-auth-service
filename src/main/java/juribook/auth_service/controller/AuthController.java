package juribook.auth_service.controller;

import juribook.auth_service.dto.request.LoginRequest;
import juribook.auth_service.dto.request.RefreshTokenRequest;
import juribook.auth_service.dto.request.RegisterClientRequest;
import juribook.auth_service.dto.request.RegisterLawyerRequest;
import juribook.auth_service.dto.response.LoginResponse;
import juribook.auth_service.dto.response.RefreshTokenResponse;
import juribook.auth_service.dto.response.RegisterClientResponse;
import juribook.auth_service.entity.User;
import juribook.auth_service.service.AuthService;
import juribook.auth_service.service.RefreshTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Inscription, connexion et gestion des tokens")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;

    // ── POST /api/auth/register ──────────────────────────────
    @PostMapping("/register")
    @Operation(summary = "Inscription client")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Compte créé"),
        @ApiResponse(responseCode = "400", description = "Données invalides"),
        @ApiResponse(responseCode = "409", description = "Email déjà utilisé")
    })
    public ResponseEntity<RegisterClientResponse> registerClient(
            @Valid @RequestBody RegisterClientRequest request) {
        // Ligne séparée en deux statements pour la lisibilité,
        // suite à la review d'Abdelhadi (mentor)
        RegisterClientResponse response = authService.registerClient(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── POST /api/auth/register/lawyer ───────────────────────
    @PostMapping("/register/lawyer")
    @Operation(summary = "Inscription avocat", description = "Statut PENDING — en attente de validation admin")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Compte avocat créé, statut PENDING"),
        @ApiResponse(responseCode = "400", description = "Données invalides"),
        @ApiResponse(responseCode = "409", description = "Email ou numéro de barreau déjà utilisé")
    })
    public ResponseEntity<Void> registerLawyer(
            @Valid @RequestBody RegisterLawyerRequest request) {
        authService.registerLawyer(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // ── POST /api/auth/login ─────────────────────────────────
    @PostMapping("/login")
    @Operation(summary = "Connexion", description = "Retourne un access token JWT (24h) + refresh token (7j)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Connexion réussie"),
        @ApiResponse(responseCode = "400", description = "Données invalides"),
        @ApiResponse(responseCode = "404", description = "Email ou mot de passe incorrect")
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    // ── POST /api/auth/refresh ───────────────────────────────
    @PostMapping("/refresh")
    @Operation(
        summary = "Renouveler le token",
        description = "Échange un refresh token valide contre un nouveau JWT + nouveau refresh token (rotation)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Nouveaux tokens générés"),
        @ApiResponse(responseCode = "400", description = "Refresh token invalide, révoqué ou expiré")
    })
    public ResponseEntity<RefreshTokenResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        RefreshTokenResponse response = refreshTokenService.refresh(request);
        return ResponseEntity.ok(response);
    }

    // ── POST /api/auth/logout ────────────────────────────────
    @PostMapping("/logout")
    @Operation(summary = "Déconnexion", description = "Révoque tous les refresh tokens de l'utilisateur connecté")
    @ApiResponse(responseCode = "204", description = "Déconnexion réussie")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal User user) {
        if (user != null) {
            refreshTokenService.revokeAllTokens(user);
        }
        return ResponseEntity.noContent().build();
    }
}