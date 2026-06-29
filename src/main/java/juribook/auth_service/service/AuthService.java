package juribook.auth_service.service;

import juribook.auth_service.dto.request.LoginRequest;
import juribook.auth_service.dto.request.RegisterClientRequest;
import juribook.auth_service.dto.request.RegisterLawyerRequest;
import juribook.auth_service.dto.response.LoginResponse;
import juribook.auth_service.dto.response.RegisterClientResponse;
import juribook.auth_service.entity.LawyerStatus;
import juribook.auth_service.entity.Role;
import juribook.auth_service.entity.User;
import juribook.auth_service.exception.UserNotFoundException;
import juribook.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    // ── Inscription client ───────────────────────────────────
    @Transactional
    public RegisterClientResponse registerClient(RegisterClientRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Un compte existe déjà avec cet email");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail().toLowerCase().trim());
        user.setPhone(request.getPhone());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.CLIENT);
        user.setEnabled(true);

        User saved = userRepository.save(user);
        log.info("Nouveau client inscrit : id={}, email={}", saved.getId(), saved.getEmail());

        return RegisterClientResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .email(saved.getEmail())
                .phone(saved.getPhone())
                .role(saved.getRole())
                .message("Inscription réussie")
                .build();
    }

    // ── Inscription avocat ───────────────────────────────────
    @Transactional
    public void registerLawyer(RegisterLawyerRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email déjà utilisé");
        }

        if (userRepository.existsByBarNumber(request.getBarNumber())) {
            throw new IllegalArgumentException("Ce numéro de barreau est déjà enregistré");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.LAWYER);
        user.setBarNumber(request.getBarNumber());
        user.setSpecialty(request.getSpecialty());
        user.setCity(request.getCity());
        user.setLawyerStatus(LawyerStatus.PENDING);

        userRepository.save(user);
        log.info("Nouvel avocat inscrit : email={}, barNumber={}, status=PENDING",
                user.getEmail(), user.getBarNumber());
    }

    // ── Login ────────────────────────────────────────────────
    @Transactional
    public LoginResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new UserNotFoundException("Email ou mot de passe incorrect"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UserNotFoundException("Email ou mot de passe incorrect");
        }

        if (!user.isEnabled()) {
            throw new IllegalArgumentException("Ce compte est désactivé");
        }

        String accessToken = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user).getToken();

        log.info("Connexion réussie : id={}, email={}, role={}", user.getId(), user.getEmail(), user.getRole());

        return LoginResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .type("Bearer")
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }
}