package juribook.auth_service.service;

import juribook.auth_service.entity.User;
import juribook.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Suspension / réactivation de compte (Sprint 6.10 + 7.2).
 *
 * Réutilise le champ `enabled` déjà existant sur User (déjà vérifié
 * dans AuthService.login — "Ce compte est désactivé") plutôt que
 * d'introduire un nouveau booléen `suspended` redondant. Seuls
 * `suspendedReason`/`suspendedAt` sont de nouvelles colonnes, pour
 * garder une trace de POURQUOI le compte a été désactivé — `enabled`
 * reste la seule donnée réellement vérifiée au login, ces deux
 * colonnes ne sont que des métadonnées d'audit.
 *
 * ⚠️ N'invalide PAS un JWT déjà émis — cf. limite déjà documentée :
 * un token émis avant la désactivation reste valide jusqu'à son
 * expiration naturelle (24h), aucune liste de révocation partagée
 * entre les 6 services. Le blocage n'est effectif qu'à la PROCHAINE
 * tentative de connexion.
 *
 * ⚠️ suspendAccount/reactivateAccount échouent silencieusement
 * (log.warn, pas d'exception) si l'utilisateur n'existe pas — comportement
 * voulu pour l'usage Kafka (consumer abuse.detected, Sprint 6.10) qui ne
 * doit jamais planter sur un actorId invalide. L'usage admin (Sprint 7.2,
 * via AdminUserService) fait sa propre vérification d'existence en amont
 * pour renvoyer un 404 explicite plutôt que de compter sur ce no-op silencieux.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserSuspensionService {

    private final UserRepository userRepository;

    @Transactional
    public void suspendAccount(Long userId, String reason) {
        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            log.warn("Suspension impossible — utilisateur introuvable : id={}", userId);
            return;
        }

        if (!user.isEnabled()) {
            log.debug("Compte déjà désactivé, aucune action supplémentaire : id={}", userId);
            return;
        }

        user.setEnabled(false);
        user.setSuspendedReason(reason);
        user.setSuspendedAt(LocalDateTime.now());
        userRepository.save(user);

        log.warn("Compte désactivé : id={}, email={}, reason={}",
                user.getId(), user.getEmail(), reason);
    }

    /**
     * Réactive un compte désactivé. Efface suspendedReason/
     * suspendedAt plutôt que de les laisser trainer, sinon un compte
     * réactivé garderait indéfiniment le motif de sa dernière suspension,
     * trompeur dans un futur écran admin de consultation.
     *
     * Ne restaure PAS lawyerStatus si le compte est un LAWYER — un avocat
     * désactivé puis réactivé reprend directement son statut de validation
     * d'avant (pas de perte de validation), ce champ n'est de toute façon
     * jamais touché par suspendAccount, donc rien à restaurer ici.
     */
    @Transactional
    public void reactivateAccount(Long userId) {
        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            log.warn("Réactivation impossible — utilisateur introuvable : id={}", userId);
            return;
        }

        if (user.isEnabled()) {
            log.debug("Compte déjà actif, aucune action supplémentaire : id={}", userId);
            return;
        }

        user.setEnabled(true);
        user.setSuspendedReason(null);
        user.setSuspendedAt(null);
        userRepository.save(user);

        log.info("Compte réactivé : id={}, email={}", user.getId(), user.getEmail());
    }
}