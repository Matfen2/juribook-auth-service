package juribook.auth_service.service;

import juribook.auth_service.dto.response.AdminUserResponse;
import juribook.auth_service.entity.Role;
import juribook.auth_service.entity.User;
import juribook.auth_service.exception.UserNotFoundException;
import juribook.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recherche et actions admin sur les utilisateurs.
 * Aucun appel inter-services : role/enabled/city sont tous des champs
 * locaux de User (city est dénormalisé côté avocat, cf. User.java).
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final String DEFAULT_DEACTIVATION_REASON = "Désactivé manuellement par un administrateur";

    private final UserRepository userRepository;
    private final UserSuspensionService userSuspensionService;

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> searchUsers(Role role, Boolean enabled, String city, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
        return userRepository.search(role, enabled, city, pageable)
                .map(AdminUserResponse::from);
    }

    /**
     * Désactive un compte. Vérifie l'existence ici (404
     * explicite) plutôt que de compter sur le no-op silencieux de
     * UserSuspensionService, pensé pour l'usage Kafka (abuse.detected),
     * pas pour une action admin explicite qui doit signaler une erreur
     * claire sur un id inconnu.
     *
     * Relit la même instance User après délégation : dans la même
     * transaction (propagation REQUIRED par défaut), Hibernate renvoie
     * l'entité déjà chargée en cache de premier niveau plutôt que
     * d'exécuter une nouvelle requête, `user` reflète donc déjà l'état
     * mis à jour par suspendAccount sans requête supplémentaire.
     */
    @Transactional
    public AdminUserResponse deactivateUser(Long userId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Utilisateur introuvable : id=" + userId));

        String effectiveReason = (reason != null && !reason.isBlank()) ? reason : DEFAULT_DEACTIVATION_REASON;
        userSuspensionService.suspendAccount(userId, effectiveReason);

        return AdminUserResponse.from(user);
    }

    /** Réactive un compte. Même logique de 404 explicite que deactivateUser. */
    @Transactional
    public AdminUserResponse activateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Utilisateur introuvable : id=" + userId));

        userSuspensionService.reactivateAccount(userId);

        return AdminUserResponse.from(user);
    }
}