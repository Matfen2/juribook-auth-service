package juribook.auth_service.event;

import juribook.auth_service.entity.User;

/**
 * Publication d'événements sur le topic lawyer-events, suite à une
 * action admin de validation/refus de profil avocat.
 *
 * ⚠️ Topic partagé avec un usage préexistant différent : lawyer-events
 * porte déjà (potentiellement) un eventType "lawyer.status-changed" lié
 * à la disponibilité (Lawyer.available, lawyer-service), un concept
 * distinct de la validation admin (User.lawyerStatus, auth-service).
 * Les deux eventTypes cohabitent sur le même topic sans collision,
 * chaque consommateur filtre par eventType.
 *
 * ⚠️ Le champ "lawyerId" du payload correspond à User.id (auth-service),
 * PAS à Lawyer.id (lawyer-service), ce sont deux espaces d'identifiants
 * distincts, reliés uniquement via Lawyer.authUserId. Choisi malgré tout
 * pour rester compatible avec l'extraction d'acteur déjà en place dans
 * AuditService (qui reconnaît "clientId"/"lawyerId"), pas pour désigner
 * un Lawyer.id réel. À renommer si ça crée de la confusion en pratique.
 */
public interface LawyerEventPublisher {

    void publishLawyerApproved(User user);

    void publishLawyerRejected(User user, String reason);
}