package juribook.auth_service.service;

import juribook.auth_service.dto.request.UpdateLawyerStatusRequest;
import juribook.auth_service.dto.response.LawyerAdminResponse;
import juribook.auth_service.entity.LawyerStatus;
import juribook.auth_service.entity.Role;
import juribook.auth_service.entity.User;
import juribook.auth_service.event.LawyerEventPublisher;
import juribook.auth_service.exception.UserNotFoundException;
import juribook.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service admin pour la validation des profils avocats (Sprint 2.6 + 7.3).
 *
 * Règles métier :
 *   - Seul un ADMIN peut appeler ces méthodes (contrôlé dans SecurityConfig)
 *   - Un avocat APPROVED voit son compte activé (enabled = true)
 *   - Un avocat REJECTED voit son compte désactivé (enabled = false)
 *   - Seuls les avocats avec statut PENDING sont dans la file d'attente
 *
 * Sprint 7.3 : REJECTED passe désormais par UserSuspensionService.
 * suspendAccount (motif + date tracés) plutôt que de faire enabled=false
 * directement — cohérent avec la désactivation manuelle (7.2) et la
 * suspension automatique pour abus (6.10), qui tracent toutes deux
 * suspendedReason/suspendedAt. APPROVED passe symétriquement par
 * reactivateAccount, pour effacer cette trace si un avocat précédemment
 * refusé est finalement validé (changement d'avis de l'admin).
 *
 * suspendAccount/reactivateAccount modifient la MÊME instance User déjà
 * chargée ici (cache de premier niveau Hibernate, même transaction) —
 * lawyerStatus posé avant l'appel est donc bien persisté au commit même
 * si l'appel délégué fait un no-op (déjà enabled=false par ex.), grâce
 * au dirty checking JPA — pas besoin d'un save() supplémentaire explicite.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private static final String DEFAULT_REJECTION_REASON = "Profil avocat refusé par l'administrateur";

    private final UserRepository userRepository;
    private final UserSuspensionService userSuspensionService;
    private final LawyerEventPublisher lawyerEventPublisher;

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
            user.setLawyerStatus(LawyerStatus.APPROVED);
            userSuspensionService.reactivateAccount(user.getId());
            log.info("Avocat validé : id={}, email={}", user.getId(), user.getEmail());
            lawyerEventPublisher.publishLawyerApproved(user);

        } else if (newStatus == LawyerStatus.REJECTED) {
            user.setLawyerStatus(LawyerStatus.REJECTED);
            String reason = (request.getReason() != null && !request.getReason().isBlank())
                    ? request.getReason() : DEFAULT_REJECTION_REASON;
            userSuspensionService.suspendAccount(user.getId(), reason);
            log.info("Avocat refusé : id={}, email={}, raison={}", user.getId(), user.getEmail(), reason);
            lawyerEventPublisher.publishLawyerRejected(user, reason);

        } else {
            throw new IllegalArgumentException("Statut invalide pour une action admin : " + newStatus);
        }

        return LawyerAdminResponse.from(user);
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