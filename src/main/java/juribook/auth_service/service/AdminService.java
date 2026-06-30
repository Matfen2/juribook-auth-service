package juribook.auth_service.service;

import juribook.auth_service.dto.request.UpdateLawyerStatusRequest;
import juribook.auth_service.dto.response.LawyerAdminResponse;
import juribook.auth_service.entity.LawyerStatus;
import juribook.auth_service.entity.Role;
import juribook.auth_service.entity.User;
import juribook.auth_service.exception.UserNotFoundException;
import juribook.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service admin pour la validation des profils avocats.
 *
 * Règles métier :
 *   - Seul un ADMIN peut appeler ces méthodes (contrôlé dans SecurityConfig)
 *   - Un avocat APPROVED voit son compte activé (enabled = true)
 *   - Un avocat REJECTED voit son compte désactivé (enabled = false)
 *   - Seuls les avocats avec statut PENDING sont dans la file d'attente
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository userRepository;

    // ── Lister tous les avocats par statut ─────────────────
    @Transactional(readOnly = true)
    public List<LawyerAdminResponse> getLawyersByStatus(LawyerStatus status) {
        return userRepository.findByRoleAndLawyerStatus(Role.LAWYER, status)
                .stream()
                .map(LawyerAdminResponse::from)
                .toList();
    }

    // ── Lister tous les avocats (tous statuts) ──────────────
    @Transactional(readOnly = true)
    public List<LawyerAdminResponse> getAllLawyers() {
        return userRepository.findByRole(Role.LAWYER)
                .stream()
                .map(LawyerAdminResponse::from)
                .toList();
    }

    // ── Détail d'un avocat ──────────────────────────────────
    @Transactional(readOnly = true)
    public LawyerAdminResponse getLawyerById(Long id) {
        User user = userRepository.findById(id)
                .filter(u -> u.getRole() == Role.LAWYER)
                .orElseThrow(() -> new UserNotFoundException("Avocat introuvable : id=" + id));
        return LawyerAdminResponse.from(user);
    }

    // ── Valider ou refuser un profil avocat ─────────────────
    @Transactional
    public LawyerAdminResponse updateLawyerStatus(Long id, UpdateLawyerStatusRequest request) {
        User user = userRepository.findById(id)
                .filter(u -> u.getRole() == Role.LAWYER)
                .orElseThrow(() -> new UserNotFoundException("Avocat introuvable : id=" + id));

        LawyerStatus newStatus = request.getStatus();

        if (newStatus == LawyerStatus.APPROVED) {
            // Valider → activer le compte
            user.setLawyerStatus(LawyerStatus.APPROVED);
            user.setEnabled(true);
            log.info("Avocat validé : id={}, email={}", user.getId(), user.getEmail());

        } else if (newStatus == LawyerStatus.REJECTED) {
            // Refuser → désactiver le compte
            user.setLawyerStatus(LawyerStatus.REJECTED);
            user.setEnabled(false);
            log.info("Avocat refusé : id={}, email={}, raison={}",
                    user.getId(), user.getEmail(), request.getReason());

        } else {
            throw new IllegalArgumentException("Statut invalide pour une action admin : " + newStatus);
        }

        User saved = userRepository.save(user);
        return LawyerAdminResponse.from(saved);
    }

    // ── Stats rapides pour le dashboard ─────────────────────
    @Transactional(readOnly = true)
    public java.util.Map<String, Long> getStats() {
        long pending  = userRepository.countByRoleAndLawyerStatus(Role.LAWYER, LawyerStatus.PENDING);
        long approved = userRepository.countByRoleAndLawyerStatus(Role.LAWYER, LawyerStatus.APPROVED);
        long rejected = userRepository.countByRoleAndLawyerStatus(Role.LAWYER, LawyerStatus.REJECTED);
        long clients  = userRepository.countByRole(Role.CLIENT);

        return java.util.Map.of(
            "pending",  pending,
            "approved", approved,
            "rejected", rejected,
            "clients",  clients
        );
    }
}