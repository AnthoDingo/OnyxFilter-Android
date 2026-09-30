# OnyxFilter pour Android

Application Android pour piloter une instance [OnyxFilter](https://github.com/AnthoDingo/OnyxFilter) :
après connexion avec un compte de l'interface web, elle permet d'**activer ou de désactiver le
filtrage DNS**, sans limite de durée ou temporairement.

## Fonctionnalités

- Connexion à l'instance (adresse, nom d'utilisateur, mot de passe du compte OnyxFilter) ; la session
  est conservée entre deux lancements et le jeton d'accès est renouvelé automatiquement.
- État de la protection en temps réel : actualisation toutes les 15 secondes quand l'écran est
  visible, compte à rebours et heure de réactivation pendant une désactivation temporaire.
- Désactivation **sans limite de durée** (bouton principal, comme sur le tableau de bord web).
- Désactivation **temporaire** : 30 secondes, 1 minute, 10 minutes, 1 heure, jusqu'à demain
  (minuit, heure du téléphone), ou durée personnalisée jusqu'à 30 jours. La durée d'une désactivation
  en cours peut être modifiée.
- Réactivation immédiate.

La protection est gérée par le serveur : elle est réactivée automatiquement à l'échéance, même si le
téléphone est éteint, et toujours réactivée au redémarrage d'OnyxFilter.

### Widgets de l'écran d'accueil

- **Bascule OnyxFilter** (1 × 1) : bouclier vert ou rouge et état en un coup d'œil (heure de
  réactivation pendant une désactivation temporaire) ; un toucher désactive la protection sans
  limite de durée, ou la réactive.
- **Protection OnyxFilter** (4 × 2, redimensionnable) : état détaillé, boutons *10 min*, *1 h* et
  *Demain* pour une désactivation temporaire, et bouton *Désactiver* / *Activer*. Toucher l'en-tête
  ouvre l'application.

Les widgets se mettent à jour après chacune de leurs actions, à chaque changement fait dans
l'application, juste après la fin d'une désactivation temporaire, et toutes les 30 minutes (pour
refléter les changements faits depuis l'interface web). Serveur injoignable : ils gardent le dernier
état connu et l'indiquent (« Hors ligne ») ; un toucher relance la lecture. Sans session, ils ouvrent
l'écran de connexion.

## Prérequis côté serveur : l'API mobile

L'interface web d'OnyxFilter (Blazor Server) n'expose pas d'API HTTP : l'application s'appuie sur une
petite API REST à ajouter au serveur, fournie dans [`server/onyxfilter-api-mobile.patch`](server/onyxfilter-api-mobile.patch)
(nouveau fichier `src/OnyxFilter/Api/MobileApiEndpoints.cs` et deux ajouts dans `Program.cs`).

Depuis la racine du dépôt OnyxFilter :

```sh
git apply /chemin/vers/OnyxFilter-Android/server/onyxfilter-api-mobile.patch
```

L'API utilise les comptes existants (ASP.NET Core Identity) avec des jetons porteurs (« Bearer ») ;
l'interface web reste authentifiée par cookie et n'est pas modifiée. Sans cette API, l'application
affiche « Ce serveur ne propose pas l'API de l'application mobile ».

### Contrat de l'API

| Méthode | Chemin | Corps | Réponse |
|---|---|---|---|
| `POST` | `/api/auth/login` | `{ "username", "password" }` | `{ "tokenType", "accessToken", "expiresIn", "refreshToken" }` ; `401` + `application/problem+json` si refusé |
| `POST` | `/api/auth/refresh` | `{ "refreshToken" }` | nouveaux jetons ; `401` si expiré ou révoqué |
| `GET` | `/api/protection` | — | état (voir ci-dessous) |
| `PUT` | `/api/protection` | `{ "enabled": true }` : réactive<br>`{ "enabled": false }` : désactive sans limite<br>`{ "enabled": false, "durationSeconds": 600 }` : désactive pendant 10 min (1 s à 30 jours) | état |

Les appels `/api/protection` exigent l'en-tête `Authorization: Bearer <accessToken>`. État renvoyé :

```json
{ "enabled": false, "disabledUntilUtc": "2026-09-30T12:00:00Z", "remainingSeconds": 540 }
```

La durée d'une désactivation est transmise en secondes (et la durée restante renvoyée par le serveur)
pour ne pas dépendre de la synchronisation des horloges du téléphone et du serveur.

Le jeton d'accès est valable 1 heure et le jeton de rafraîchissement 14 jours (valeurs par défaut
d'ASP.NET Core, réglables via `AddBearerToken`) : au-delà de 14 jours sans ouvrir l'application, il
faut se reconnecter. Un changement de mot de passe (par exemple avec `--reset`) révoque le jeton de
rafraîchissement : la session de l'application prend fin au plus tard à l'expiration du jeton d'accès.

## Utilisation

Saisir l'adresse de l'interface web d'OnyxFilter, par exemple `https://onyxfilter.maison:7037` ou
`http://192.168.1.10:5259` (sans schéma, `https://` est ajouté). Un chemin est accepté pour une instance
servie derrière un proxy inverse (`https://maison.example/onyxfilter`).

- **HTTP** est autorisé pour les instances uniquement joignables sur le réseau local ; l'écran de
  connexion signale alors que le mot de passe transite en clair.
- **Certificat auto-signé** : installer le certificat de l'autorité qui l'a émis dans
  *Paramètres → Sécurité → Chiffrement et identifiants → Installer un certificat → Certificat CA* ;
  l'application fait confiance aux autorités installées par l'utilisateur.

## Compilation

Prérequis : JDK 17 et le SDK Android (API 36), ou Android Studio.

```sh
./gradlew assembleDebug          # APK : app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # tests unitaires (JVM)
./gradlew lintDebug
```

L'intégration continue (GitHub Actions, `.github/workflows/android.yml`) exécute les tests et le lint
et publie l'APK de débogage en artefact de chaque exécution.

Version minimale : Android 8.0 (API 26).

## Sécurité

- Le mot de passe n'est jamais enregistré : seuls les jetons le sont, chiffrés (AES-GCM) avec une clé
  de l'Android Keystore qui ne quitte pas l'appareil. Ils sont exclus des sauvegardes et des transferts
  entre appareils.
- Comme la page de connexion web, l'API ne verrouille pas le compte après des échecs répétés : pour une
  instance exposée sur Internet, préférer un accès via VPN ou un proxy inverse limitant les tentatives.

## Structure

```
app/src/main/java/io/github/anthodingo/onyxfilter/
├── data/     client HTTP (OkHttp), modèles JSON, session chiffrée, dépôt (rafraîchissement des jetons)
├── domain/   durées de désactivation, état de la protection, calculs d'affichage
├── ui/       écrans Compose (connexion, protection), ViewModels, thème
└── widget/   widgets de l'écran d'accueil (RemoteViews) et exécution de leurs actions
server/       patch de l'API mobile pour le serveur OnyxFilter
```
