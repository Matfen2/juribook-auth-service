# juribook-auth-service

Microservice d'authentification pour **JuriBook**, gestion des inscriptions, connexions, refresh tokens, protection des routes par rôle via JWT, validation admin des profils avocats, gestion des comptes utilisateurs (recherche, désactivation, réactivation) et réaction automatique à la détection d'abus.

## Stack

- Java 21 · Spring Boot 4.1.0 · Maven
- Spring Security · JWT (JJWT 0.12.6) · BCrypt
- PostgreSQL 16 · Flyway
- Apache Kafka - **producer** (`lawyer-events`) **et consumer** (`abuse-events`)
- Springdoc OpenAPI (Swagger UI)
- JUnit 5 + Mockito
- Port : **8081**

## Structure du projet

```
src/main/java/juribook/auth_service/
├── config/
│   ├── SecurityConfig.java           # Règles d'accès par rôle, CORS intégré, filtre JWT, bean PasswordEncoder
│   └── OpenApiConfig.java            # Configuration Swagger UI
├── controller/
│   ├── AuthController.java           # POST /register, /register/lawyer, /login, /refresh, /logout
│   ├── UserController.java           # GET /me, /lawyer-profile, /client-dashboard
│   ├── AdminController.java          # GET/PUT /api/admin/lawyers/** - validation des profils avocats
│   └── AdminUserController.java      # GET/PATCH /api/admin/users/** - recherche + suspension/réactivation
├── dto/
│   ├── request/
│   │   ├── LoginRequest.java
│   │   ├── RegisterClientRequest.java
│   │   ├── RegisterLawyerRequest.java
│   │   ├── RefreshTokenRequest.java
│   │   ├── UpdateLawyerStatusRequest.java
│   │   └── DeactivateUserRequest.java        # record(reason)
│   └── response/
│       ├── LoginResponse.java
│       ├── RegisterClientResponse.java
│       ├── RegisterLawyerResponse.java
│       ├── RefreshTokenResponse.java
│       ├── UserMeResponse.java               # record - réponse GET /me
│       ├── LawyerProfileMeResponse.java      # record - réponse GET /lawyer-profile
│       ├── ClientDashboardResponse.java      # record - réponse GET /client-dashboard
│       ├── LawyerAdminResponse.java          # réponse admin (liste + détail avocat)
│       └── AdminUserResponse.java            # record - recherche admin, inclut les métadonnées de suspension
├── entity/
│   ├── User.java                     # Entité JPA principale - suspendedReason/suspendedAt/suspensionSource inclus
│   ├── Role.java                     # Enum : CLIENT, LAWYER, ADMIN
│   ├── LawyerStatus.java             # Enum : PENDING, APPROVED, REJECTED
│   ├── SuspensionSource.java         # Enum : MANUAL, ABUSE_DETECTION, LAWYER_REJECTION
│   └── RefreshToken.java             # Token de renouvellement de session (7 jours)
├── event/
│   ├── LawyerEventPublisher.java         # Interface - publishLawyerApproved/publishLawyerRejected
│   ├── KafkaLawyerEventPublisher.java     # Impl unique, ObjectProvider<KafkaTemplate>, topic lawyer-events
│   ├── AbuseEventConsumer.java            # @KafkaListener sur abuse-events - 1er consumer Kafka de ce service
│   └── AbuseEvent.java                    # Miroir du payload publié par audit-service (abuse.detected)
├── exception/
│   ├── GlobalExceptionHandler.java   # Handlers 400/404/409/500
│   └── UserNotFoundException.java
├── filter/
│   └── JwtAuthenticationFilter.java  # Filtre Spring Security (OncePerRequestFilter)
├── repository/
│   ├── UserRepository.java           # + findByRole, findByRoleAndLawyerStatus, countByRole(...), search(...)
│   └── RefreshTokenRepository.java
├── security/
│   └── JwtService.java               # Génération et validation des tokens JWT
└── service/
    ├── AuthService.java              # Logique métier inscription + login
    ├── RefreshTokenService.java      # Rotation des refresh tokens
    ├── AdminService.java             # Validation/refus des profils avocats, stats dashboard, publie lawyer-events
    ├── AdminUserService.java         # Recherche paginée + désactivation/réactivation d'un compte
    └── UserSuspensionService.java    # Suspension/réactivation, origine tracée
src/main/resources/
├── application.yml                   # Kafka : producer + consumer désormais actifs (cf. Kafka ci-dessous)
└── db/migration/
    ├── V1__create_users_table.sql
    ├── V2__create_refresh_tokens_table.sql
    ├── V3__add_suspended_to_users.sql        # suspended_reason, suspended_at
    └── V4__add_suspension_source_to_users.sql # suspension_source
src/test/java/juribook/auth_service/
├── AuthServiceApplicationTests.java  # Smoke test (sans @SpringBootTest)
├── controller/
│   ├── AuthControllerTest.java
│   └── AdminUserControllerTest.java  # Sécurité par rôle, filtres, pagination, deactivate/activate
├── security/
│   └── JwtServiceTest.java
├── event/
│   └── AbuseEventConsumerTest.java    # Propagation de SuspensionSource.ABUSE_DETECTION
└── service/
    ├── AuthServiceTest.java
    ├── RefreshTokenServiceTest.java
    ├── AdminServiceTest.java          # + suspendAccount taggé LAWYER_REJECTION
    ├── AdminUserServiceTest.java      # + suspendAccount taggé MANUAL, filtre suspensionSource
    └── UserSuspensionServiceTest.java # + traçage/effacement de suspensionSource
src/test/resources/
└── application-test.yml              # H2 en mémoire + Flyway désactivé pour CI
```

> **Note CORS** - Le CORS est configuré directement dans `SecurityConfig` via `.cors(cors -> cors.configurationSource(...))`, sans bean `CorsFilter` séparé. Un `CorsFilter` externe entre en conflit avec `SecurityFilterChain` dans Spring Boot 4 et empêche son chargement (symptôme : `inMemoryUserDetailsManager` au démarrage à la place des règles de `SecurityConfig`).

> **Note DTOs** - Les réponses de `UserController` utilisent des `record` typés (`UserMeResponse`, `LawyerProfileMeResponse`, `ClientDashboardResponse`) plutôt que des `Map<String, Object>`, pour un contrat d'API sûr à la compilation et une documentation Swagger correcte. `AdminUserResponse` suit le même principe.

## Lancer les tests

```bash
mvn test
```

> Le nombre exact de tests a grandi (suspension, recherche admin, consumer Kafka), vérifie la sortie de `mvn test` plutôt que de te fier à un chiffre figé ici.

## Lancer en local (hors Docker)

```bash
# Prérequis : PostgreSQL sur localhost:5432 avec la base authdb
# Kafka doit être actif : ce service consomme désormais abuse-events
# en plus de produire sur lawyer-events
mvn clean spring-boot:run
```

> ⚠️ Toujours utiliser `mvn clean spring-boot:run` après une modification de `SecurityConfig`, `JwtAuthenticationFilter` ou tout fichier de `config/`, Maven peut réutiliser un bytecode obsolète sans `clean`, ce qui provoque silencieusement le rejet de la configuration de sécurité.

## Lancer via Docker Compose

```bash
# Depuis juribook-docker/docker/
docker compose up -d postgres-auth auth-service
```

## Swagger UI

[http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)

## Health check

[http://localhost:8081/actuator/health](http://localhost:8081/actuator/health)

---

## Endpoints

### Publics (pas de token requis)

| Méthode | URL | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Inscription client |
| `POST` | `/api/auth/register/lawyer` | Inscription avocat (statut PENDING) |
| `POST` | `/api/auth/login` | Connexion → JWT (24h) + refresh token (7j) |
| `POST` | `/api/auth/refresh` | Renouveler le JWT sans redemander le mot de passe |
| `POST` | `/api/auth/logout` | Révoquer tous les refresh tokens de l'utilisateur |

### Protégés (token JWT requis)

| Méthode | URL | Rôle requis | Description |
|---|---|---|---|
| `GET` | `/api/users/me` | Tous | Profil de l'utilisateur connecté |
| `GET` | `/api/users/lawyer-profile` | `LAWYER` | Profil avocat |
| `GET` | `/api/users/client-dashboard` | `CLIENT` | Dashboard client |

### Administration - profils avocats (rôle ADMIN requis)

| Méthode | URL | Description |
|---|---|---|
| `GET` | `/api/admin/lawyers` | Liste tous les avocats (tous statuts) |
| `GET` | `/api/admin/lawyers/pending` | Liste les avocats en attente de validation |
| `GET` | `/api/admin/lawyers/by-status?status=APPROVED` | Liste les avocats par statut |
| `GET` | `/api/admin/lawyers/{id}` | Détail d'un profil avocat |
| `PUT` | `/api/admin/lawyers/{id}/status` | Valider (`APPROVED`) ou refuser (`REJECTED`) un avocat - publie `lawyer.approved`/`lawyer.rejected` |
| `GET` | `/api/admin/stats` | Compteurs dashboard (pending, approved, rejected, clients) |

### Administration - gestion des comptes (rôle ADMIN requis)

| Méthode | URL | Description |
|---|---|---|
| `GET` | `/api/admin/users` | Recherche paginée - filtres cumulables optionnels : `role`, `enabled`, `city`, `suspensionSource` |
| `PATCH` | `/api/admin/users/{id}/deactivate` | Désactive un compte (motif optionnel dans le corps), tracé `SuspensionSource.MANUAL` |
| `PATCH` | `/api/admin/users/{id}/activate` | Réactive un compte, efface le motif/date/origine de suspension |

---

## Exemples Postman

### Inscription client
```json
POST http://localhost:8081/api/auth/register
Content-Type: application/json

{
    "name": "Jean Dupont",
    "email": "jean.dupont@gmail.com",
    "password": "motdepasse123",
    "phone": "0612345678"
}
```

### Inscription avocat
```json
POST http://localhost:8081/api/auth/register/lawyer
Content-Type: application/json

{
    "name": "Maître Sophie Martin",
    "email": "sophie.martin@avocat.fr",
    "password": "motdepasse123",
    "phone": "0698765432",
    "barNumber": "75001",
    "specialty": "Droit du travail",
    "city": "Paris"
}
```

### Login
```json
POST http://localhost:8081/api/auth/login
Content-Type: application/json

{
    "email": "jean.dupont@gmail.com",
    "password": "motdepasse123"
}
```

Réponse :
```json
{
    "token": "eyJhbGci...",
    "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
    "type": "Bearer",
    "id": 1,
    "name": "Jean Dupont",
    "email": "jean.dupont@gmail.com",
    "role": "CLIENT"
}
```

### Renouveler le token (refresh)
```json
POST http://localhost:8081/api/auth/refresh
Content-Type: application/json

{
    "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

Réponse :
```json
{
    "accessToken": "eyJhbGci...",
    "refreshToken": "nouveau-uuid-généré",
    "type": "Bearer"
}
```

> ⚠️ Le refresh token est **à usage unique** (rotation). Chaque appel révoque l'ancien et en génère un nouveau.

### Utiliser le token dans Postman
> **Authorization** → **Bearer Token** → coller le token retourné par `/login`

Ou dans le header :
```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### GET /api/users/me - profil de l'utilisateur connecté (tous rôles)
```
GET http://localhost:8081/api/users/me
Authorization: Bearer <token>
```
Réponse - 200 :
```json
{
    "id": 1,
    "name": "Jean Dupont",
    "email": "jean.dupont@gmail.com",
    "role": "CLIENT"
}
```

### GET /api/users/lawyer-profile - réservé aux avocats
```
GET http://localhost:8081/api/users/lawyer-profile
Authorization: Bearer <token_avocat>
```
Réponse - 200 (LAWYER) :
```json
{
    "id": 2,
    "name": "Maître Sophie Martin",
    "barNumber": "75001",
    "specialty": "Droit du travail",
    "city": "Paris",
    "lawyerStatus": "PENDING"
}
```

### GET /api/users/client-dashboard - réservé aux clients
```
GET http://localhost:8081/api/users/client-dashboard
Authorization: Bearer <token_client>
```
Réponse - 200 (CLIENT) :
```json
{
    "id": 1,
    "name": "Jean Dupont",
    "email": "jean.dupont@gmail.com",
    "message": "Bienvenue sur votre espace client"
}
```

---

### Administration - validation des profils avocats

#### Lister les avocats en attente
```
GET http://localhost:8081/api/admin/lawyers/pending
Authorization: Bearer <token_admin>
```
Réponse — 200 :
```json
[
    {
        "id": 2,
        "name": "Maître Sophie Martin",
        "email": "sophie.martin@avocat.fr",
        "phone": "0698765432",
        "role": "LAWYER",
        "barNumber": "75001",
        "specialty": "Droit du travail",
        "city": "Paris",
        "lawyerStatus": "PENDING",
        "enabled": true,
        "createdAt": "2026-06-29T22:46:00",
        "updatedAt": "2026-06-29T22:46:00"
    }
]
```

#### Valider un avocat
```json
PUT http://localhost:8081/api/admin/lawyers/2/status
Authorization: Bearer <token_admin>
Content-Type: application/json

{
    "status": "APPROVED"
}
```
Effet : `lawyerStatus` passe à `APPROVED`, réactive le compte (`reactivateAccount` - efface un éventuel motif/origine de suspension antérieur), publie `lawyer.approved` sur `lawyer-events`.

#### Refuser un avocat
```json
PUT http://localhost:8081/api/admin/lawyers/2/status
Authorization: Bearer <token_admin>
Content-Type: application/json

{
    "status": "REJECTED",
    "reason": "Numéro de barreau non vérifiable"
}
```
Effet : `lawyerStatus` passe à `REJECTED`, le compte est suspendu (`suspendAccount` avec `SuspensionSource.LAWYER_REJECTION`, motif tracé) - l'avocat ne peut plus se connecter. Publie `lawyer.rejected` sur `lawyer-events`.

#### Stats dashboard
```
GET http://localhost:8081/api/admin/stats
Authorization: Bearer <token_admin>
```
Réponse — 200 :
```json
{
    "pending": 2,
    "approved": 0,
    "rejected": 0,
    "clients": 1
}
```

---

### Administration - gestion des comptes

#### Rechercher des utilisateurs (filtres cumulables, tous optionnels)
```
GET http://localhost:8081/api/admin/users?role=CLIENT&enabled=false&suspensionSource=ABUSE_DETECTION&page=0&size=20
Authorization: Bearer <token_admin>
```
Réponse - 200 :
```json
{
    "content": [
        {
            "id": 12,
            "name": "Marc Lefèvre",
            "email": "marc.lefevre@example.com",
            "phone": null,
            "role": "CLIENT",
            "enabled": false,
            "barNumber": null,
            "specialty": null,
            "city": null,
            "lawyerStatus": null,
            "suspendedReason": "Plus de 5 annulations en 7 jours",
            "suspendedAt": "2026-07-09T20:43:40.512246",
            "suspensionSource": "ABUSE_DETECTION",
            "createdAt": "2026-07-01T09:00:00"
        }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "size": 20,
    "number": 0
}
```

> `suspensionSource=ABUSE_DETECTION` combiné à `enabled=false` isole précisément les comptes suspendus **automatiquement** par détection d'abus, par opposition à une désactivation manuelle (`MANUAL`) ou un refus de profil avocat (`LAWYER_REJECTION`) — les trois passent par le même `UserSuspensionService.suspendAccount`, seule cette colonne les distingue de façon fiable.

#### Désactiver un compte manuellement
```json
PATCH http://localhost:8081/api/admin/users/12/deactivate
Authorization: Bearer <token_admin>
Content-Type: application/json

{
    "reason": "Comportement inapproprié signalé par un avocat"
}
```
Effet : `enabled` passe à `false`, `suspendedReason`/`suspendedAt` tracés, `suspensionSource = MANUAL`. Motif optionnel, par défaut : *"Désactivé manuellement par un administrateur"*. N'invalide pas un JWT déjà émis (reste valide jusqu'à expiration naturelle, 24h max).

#### Réactiver un compte
```
PATCH http://localhost:8081/api/admin/users/12/activate
Authorization: Bearer <token_admin>
```
Réponse - 200 :
```json
{
    "id": 12,
    "enabled": true,
    "suspendedReason": null,
    "suspendedAt": null,
    "suspensionSource": null
}
```
Efface entièrement le motif/date/origine, un compte réactivé ne garde aucune trace de sa dernière suspension.

---

## Codes HTTP retournés

| Code | Cas |
|---|---|
| 201 | Inscription réussie |
| 200 | Login, consultation, validation admin, recherche/désactivation/réactivation de compte réussis |
| 204 | Logout réussi |
| 400 | Données invalides (champ manquant, format incorrect, statut admin invalide) |
| 401 | Token absent ou invalide |
| 403 | Rôle insuffisant (ex : CLIENT sur une route ADMIN) |
| 404 | Utilisateur introuvable (email/mot de passe incorrect, avocat introuvable côté admin, ou id inconnu sur deactivate/activate) |
| 409 | Email ou numéro de barreau déjà utilisé |
| 500 | Erreur inattendue |

---

## Suspension d'un compte - origine tracée

Un compte suspendu a `enabled = false`. Trois origines possibles, toutes tracées par les mêmes colonnes (`suspendedReason`, `suspendedAt`, `suspensionSource`) via l'unique point d'entrée `UserSuspensionService.suspendAccount(userId, reason, source)` :

| `SuspensionSource` | Déclencheur | Motif typique |
|---|---|---|
| `MANUAL` | `AdminUserService.deactivateUser` | Motif libre saisi par l'admin |
| `ABUSE_DETECTION` | `AbuseEventConsumer`, suite à `abuse.detected` reçu d'`audit-service` | `"Plus de 5 annulations en 7 jours"` / `"Plus de 3 avis 1-étoile en 24h"` (constantes fixes) |
| `LAWYER_REJECTION` | `AdminService.updateLawyerStatus`, branche `REJECTED` | Motif libre ou *"Profil avocat refusé par l'administrateur"* |

`reactivateAccount` efface les trois colonnes d'un coup, quelle qu'ait été l'origine, un compte réactivé ne garde aucune trace de sa dernière suspension.

⚠️ **N'invalide pas un JWT déjà émis** : un token émis avant la désactivation reste valide jusqu'à son expiration naturelle (24h max), aucune liste de révocation partagée entre les 6 services. Le blocage n'est effectif qu'à la **prochaine tentative de connexion** (`AuthService.login` vérifie `enabled`).

⚠️ **`suspendAccount`/`reactivateAccount` échouent silencieusement** (log, pas d'exception) si l'utilisateur n'existe pas, comportement voulu pour l'usage Kafka (`AbuseEventConsumer`, un `actorId` invalide ne doit jamais faire planter le consumer). L'usage admin (`AdminUserService`) fait sa propre vérification d'existence en amont pour renvoyer un 404 explicite.

---

## Kafka

### Ce que ce service consomme

| Topic | Consumer | Événement | Effet |
|---|---|---|---|
| `abuse-events` | `AbuseEventConsumer` | `abuse.detected` | `suspendAccount(actorId, reason, SuspensionSource.ABUSE_DETECTION)` |

Premier consumer Kafka de ce service, qui n'avait fait que produire (`audit-events`) jusqu'ici.

### Ce que ce service produit

| Topic | Événement | Déclencheur |
|---|---|---|
| `lawyer-events` | `lawyer.approved` | `PUT /api/admin/lawyers/{id}/status` avec `APPROVED` (Sprint 7.3) |
| `lawyer-events` | `lawyer.rejected` | Idem avec `REJECTED`, `reason` inclus dans le payload |

⚠️ **Topic partagé avec un usage préexistant différent** : `lawyer-events` porte aussi `lawyer.status-changed`, publié par `lawyer-service` sur un changement de disponibilité (`Lawyer.available`), un concept distinct de la validation admin (`User.lawyerStatus`, ce service). Les deux cohabitent sans collision, chaque consommateur filtre par `eventType`.

⚠️ **`lawyerId` du payload = `User.id` (ce service), PAS `Lawyer.id` (lawyer-service)**, deux espaces d'identifiants distincts, reliés uniquement via `Lawyer.authUserId`. Choisi pour rester compatible avec l'extraction d'acteur déjà en place dans `AuditService` (qui reconnaît `clientId`/`lawyerId`), pas pour désigner un `Lawyer.id` réel.

### Activation

`KafkaLawyerEventPublisher` décide **à l'exécution** si Kafka est disponible, via `ObjectProvider<KafkaTemplate<String, String>>`, même pattern que tous les autres publishers du projet, jamais `@ConditionalOnBean` (piège d'ordre de scan vs autoconfiguration Spring Boot, déjà rencontré et documenté côté `booking-service`).

---

## Commandes SQL utiles

### Accéder à la base PostgreSQL

```bash
docker exec -it juribook-postgres-auth psql -U juribook -d authdb
```

> ⚠️ Pour insérer un hash BCrypt en une commande `-c` depuis PowerShell, le `$` peut être interprété comme une variable shell et tronquer le hash. Préférer le mode interactif (`docker exec -it ... psql -U juribook -d authdb` puis coller la requête directement dans le prompt `authdb=#`).

### Créer un compte ADMIN

```sql
INSERT INTO users (name, email, password, role, enabled, created_at, updated_at)
VALUES (
  'Admin JuriBook',
  'admin@juribook.fr',
  '$2a$10$K6Fs7CHCaGkVMfdkU4k/wuE/NyCAD4wbNBEshiBIU5CuSIkOxOGqu',
  'ADMIN',
  true,
  NOW(),
  NOW()
);
```
> ⚠️ Le préfixe du hash BCrypt doit être `$2a$`, pas `$2b$`, un hash généré par une lib externe (ex: Python `bcrypt`) produit souvent `$2b$`, fonctionnellement identique pour un mot de passe de longueur normale, mais a déjà causé un `500 Internal Server Error` sur `/login` plutôt qu'un rejet propre. `$2a$`/`$2b$` sont interchangeables en remplaçant simplement le préfixe si besoin.

### Lister tous les utilisateurs

```bash
docker exec -it juribook-postgres-auth psql -U juribook -d authdb -c "SELECT * FROM users;"
```

### Lister uniquement les clients

```bash
docker exec -it juribook-postgres-auth psql -U juribook -d authdb -c "SELECT id, name, email, role, enabled, created_at FROM users WHERE role = 'CLIENT';"
```

### Lister uniquement les avocats

```bash
docker exec -it juribook-postgres-auth psql -U juribook -d authdb -c "SELECT id, name, email, bar_number, specialty, city, lawyer_status FROM users WHERE role = 'LAWYER';"
```

### Lister les avocats en attente de validation

```bash
docker exec -it juribook-postgres-auth psql -U juribook -d authdb -c "SELECT id, name, email, bar_number, specialty, city, lawyer_status FROM users WHERE role = 'LAWYER' AND lawyer_status = 'PENDING';"
```

### Lister les comptes suspendus, avec leur origine (Sprint 7.8)

```bash
docker exec -it juribook-postgres-auth psql -U juribook -d authdb -c "SELECT id, name, email, suspended_reason, suspended_at, suspension_source FROM users WHERE enabled = false ORDER BY suspended_at DESC;"
```

### Ne voir que les suspensions automatiques (détection d'abus)

```bash
docker exec -it juribook-postgres-auth psql -U juribook -d authdb -c "SELECT id, name, email, suspended_reason, suspended_at FROM users WHERE enabled = false AND suspension_source = 'ABUSE_DETECTION';"
```

### Vérifier les refresh tokens actifs

```bash
docker exec -it juribook-postgres-auth psql -U juribook -d authdb -c "SELECT id, user_id, LEFT(token, 8) AS token_preview, expires_at, revoked FROM refresh_tokens WHERE revoked = false;"
```

### Vérifier qu'un mot de passe est bien hashé (BCrypt)

```bash
docker exec -it juribook-postgres-auth psql -U juribook -d authdb -c "SELECT email, LEFT(password, 20) AS password_hash_preview FROM users;"
```

### Compter les utilisateurs par rôle

```bash
docker exec -it juribook-postgres-auth psql -U juribook -d authdb -c "SELECT role, COUNT(*) FROM users GROUP BY role;"
```

### Compter les suspensions par origine

```bash
docker exec -it juribook-postgres-auth psql -U juribook -d authdb -c "SELECT suspension_source, COUNT(*) FROM users WHERE enabled = false GROUP BY suspension_source;"
```

### Supprimer un utilisateur de test

```bash
docker exec -it juribook-postgres-auth psql -U juribook -d authdb -c "DELETE FROM users WHERE email = 'jean.dupont@gmail.com';"
```

### Vérifier les migrations Flyway

```bash
docker exec -it juribook-postgres-auth psql -U juribook -d authdb -c "SELECT version, description, installed_on, success FROM flyway_schema_history ORDER BY installed_rank;"
```

---

## Variables d'environnement

| Variable | Description | Valeur par défaut |
|---|---|---|
| `SPRING_DATASOURCE_URL` | URL PostgreSQL | `jdbc:postgresql://localhost:5432/authdb` |
| `SPRING_DATASOURCE_USERNAME` | Utilisateur PostgreSQL | `juribook` |
| `SPRING_DATASOURCE_PASSWORD` | Mot de passe PostgreSQL | `juribook` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Adresse Kafka | `localhost:9092` |
| `JWT_SECRET` | Secret JWT (min. 256 bits) | valeur de dev |

---

## Rôles et règles d'accès

| Rôle | Description | Accès |
|---|---|---|
| `CLIENT` | Particulier cherchant un avocat | Recherche, réservation, avis |
| `LAWYER` | Avocat inscrit (validé par admin) | Gestion profil, disponibilités, rendez-vous |
| `ADMIN` | Administrateur plateforme | Tout + validation avocats + gestion des comptes + audit |

> ⚠️ Un avocat nouvellement inscrit a le statut `PENDING`, il ne peut pas accéder aux routes `LAWYER` tant que l'admin ne l'a pas validé (`APPROVED`). Un avocat `REJECTED` a `enabled = false` (`SuspensionSource.LAWYER_REJECTION`) et ne peut plus se connecter du tout.

---

## Refresh token - fonctionnement

```
Login           → JWT (24h) + refresh token (7 jours)
JWT expiré      → POST /api/auth/refresh → nouveau JWT + nouveau refresh token
Logout          → révocation de tous les refresh tokens de l'utilisateur
Token révoqué   → 400 Refresh token révoqué
Token expiré    → 400 Refresh token expiré, veuillez vous reconnecter
```

La **rotation** est obligatoire : chaque utilisation d'un refresh token révoque l'ancien et en génère un nouveau. Si un token déjà consommé est réutilisé, c'est un signal de vol détecté.

---

## Limites connues

- **Aucune invalidation de JWT déjà émis** à la suspension d'un compte (cf. section dédiée), reste valide jusqu'à expiration naturelle (24h max), aucune liste de révocation partagée entre les 6 services.
- **`enabled`/`suspendedReason`/`suspendedAt`/`suspensionSource` sont de simples colonnes sur `User`**, pas une table d'historique séparée : une nouvelle suspension écrase silencieusement les métadonnées de la précédente (pas un problème pratique à ce stade, mais aucun historique des suspensions passées n'est conservé au-delà de la dernière).
- **`AbuseEventConsumer` fait confiance à l'`actorId` reçu** sans revalidation contre `audit-service`, cohérent avec le principe "database per service", mais signifie qu'un message Kafka malformé avec un `actorId` erroné suspendrait le mauvais compte s'il existe (échoue silencieusement seulement si l'id n'existe pas du tout).