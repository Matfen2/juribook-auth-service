package juribook.auth_service.service;

import juribook.auth_service.entity.SuspensionSource;
import juribook.auth_service.entity.User;
import juribook.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Suspension / réactivation de compte.
 *
 * Réutilise le champ `enabled` déjà existant sur User (déjà vérifié
 * dans AuthService.login, "Ce compte est désactivé") plutôt que
 * d'introduire un nouveau booléen `suspended` redondant. Seuls
 * `suspendedReason`/`suspendedAt`/`suspensionSource` sont des
 * colonnes de métadonnées d'audit, `enabled` reste la seule donnée
 * réellement vérifiée au login.
 *
 * `source` (SuspensionSource) permet désormais de
 * distinguer une suspension automatique (détection d'abus) d'une
 * action admin explicite (désactivation manuelle, refus de profil
 * avocat), les trois passaient auparavant par cette même méthode
 * sans que rien ne les distingue de façon fiable autrement qu'en
 * comparant le texte libre de `reason`.
 *
 * ⚠️ N'invalide PAS un JWT déjà émis, cf. limite déjà documentée :
 * un token émis avant la désactivation reste valide jusqu'à son
 * expiration naturelle (24h), aucune liste de révocation partagée
 * entre les 6 services. Le blocage n'est effectif qu'à la PROCHAINE
 * tentative de connexion.
 *
 * ⚠️ suspendAccount/reactivateAccount échouent silencieusement
 * (log.warn, pas d'exception) si l'utilisateur n'existe pas, comportement
 * voulu pour l'usage Kafka (consumer abuse.detected) qui ne
 * doit jamais planter sur un actorId invalide. L'usage admin (
 * AdminUserService) fait sa propre vérification d'existence en amont
 * pour renvoyer un 404 explicite plutôt que de compter sur ce no-op silencieux.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserSuspensionService {

    private final UserRepository userRepository;

    @Transactional
    public void suspendAccount(Long userId, String reason, SuspensionSource source) {
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
        user.setSuspensionSource(source);
        userRepository.save(user);

        log.warn("Compte désactivé : id={}, email={}, reason={}, source={}",
                user.getId(), user.getEmail(), reason, source);
    }

    /**
     * Réactive un compte désactivé. Efface suspendedReason/
     * suspendedAt/suspensionSource plutôt que de les laisser traîner,
     * sinon un compte réactivé garderait indéfiniment le motif et
     * l'origine de sa dernière suspension, trompeur dans l'écran admin
     * de consultation.
     *
     * Ne restaure PAS lawyerStatus si le compte est un LAWYER, un avocat
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
        user.setSuspensionSource(null);
        userRepository.save(user);

        log.info("Compte réactivé : id={}, email={}", user.getId(), user.getEmail());
    }
}