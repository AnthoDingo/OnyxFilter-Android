# OnyxFilter pour Android

Application Android pour piloter une instance [OnyxFilter](https://github.com/AnthoDingo/OnyxFilter) :
elle permet d'**activer ou de désactiver le filtrage DNS**, sans limite de durée ou temporairement,
depuis l'application, des widgets de l'écran d'accueil ou une tuile des réglages rapides.

## Fonctionnalités

- Connexion à l'instance avec son adresse et un **jeton d'API** ; la session est conservée entre deux
  lancements.
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

### Widgets de statistiques

- **Statistiques OnyxFilter** (2 × 2) : nombre de requêtes DNS des dernières 24 heures, nombre et
  part des requêtes bloquées, heure de la dernière mise à jour.
- **Activité OnyxFilter** (4 × 2, redimensionnable) : requêtes, bloquées et part bloquée, et
  histogramme des requêtes heure par heure sur 24 heures (dont l'heure de pointe est lue par
  TalkBack).

Mis à jour toutes les 30 minutes, à chaque lecture faite par l'application (au plus une fois par
minute quand l'écran de la protection est ouvert, qui affiche aussi ces chiffres) et d'un toucher sur
leur bouton d'actualisation. Serveur injoignable : ils gardent les derniers chiffres et l'heure de leur
lecture. Toucher le widget ouvre l'application.

### Tuile des réglages rapides

Une tuile **OnyxFilter** s'ajoute au volet des réglages rapides (à côté de la lampe torche, du Wi-Fi…) :
allumée quand le filtrage est actif, elle le désactive sans limite de durée ou le réactive d'un
toucher. Sous son nom (Android 10+) : *Activée*, *Désactivée*, *Jusqu'à 14:32* pendant une
désactivation temporaire, ou *Hors ligne*. Un appui long ouvre l'application, pour choisir une durée.
Depuis l'écran de verrouillage, le téléphone doit d'abord être déverrouillé.

Les widgets et la tuile se mettent à jour après chacune de leurs actions, à chaque changement fait dans
l'application, juste après la fin d'une désactivation temporaire, et toutes les 30 minutes (pour
refléter les changements faits depuis l'interface web) ; la tuile relit aussi l'état à l'ouverture du
volet (au plus une fois toutes les 30 secondes). Serveur injoignable : ils gardent le dernier
état connu et l'indiquent (« Hors ligne ») ; un toucher relance la lecture. Sans session, ils ouvrent
l'écran de connexion.

## Installation et mises à jour

Chaque version est publiée, sous forme d'APK signé, dans les
[releases GitHub](https://github.com/AnthoDingo/OnyxFilter-Android/releases).

### Mises à jour automatiques avec Obtainium

[Obtainium](https://obtainium.imranr.dev) suit les releases GitHub de l'application et installe les nouvelles
versions : plus besoin de télécharger chaque APK.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22io.github.anthodingo.onyxfilter%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FAnthoDingo%2FOnyxFilter-Android%22%2C%22author%22%3A%22AnthoDingo%22%2C%22name%22%3A%22OnyxFilter%22%7D"><img src=".github/assets/badge_obtainium.png" alt="Obtenir avec Obtainium" height="48"></a>

1. Installer Obtainium, depuis [ses releases GitHub](https://github.com/ImranR98/Obtainium/releases) ou F-Droid.
2. Depuis le téléphone, toucher le badge ci-dessus, ou l'entrée *Mises à jour avec Obtainium* du menu ⋮
   d'OnyxFilter ; ou, dans Obtainium, *Ajouter une appli* avec l'adresse
   `https://github.com/AnthoDingo/OnyxFilter-Android`.
3. Confirmer l'ajout.

Une application déjà installée est reconnue, et ses mises à jour conservent la session. Obtainium vérifie les
releases en arrière-plan et signale chaque nouvelle version ; sur Android 12 ou plus récent, une fois qu'il a
installé une première version, il installe les suivantes sans confirmation. L'entrée du menu disparaît alors.

### Vérifier l'APK

Android n'installe une mise à jour que si elle est signée par le même certificat que l'application en place :
un APK modifié est refusé. Cette vérification ne couvre pas la première installation : comparer l'empreinte
SHA-256 du certificat de signature, également indiquée dans les notes des releases publiées après la v0.1.3.

```
82:89:16:44:30:0E:F9:8E:A2:DE:3B:48:00:0C:E6:65:6F:39:E7:6E:AF:FF:19:C1:48:F6:47:4F:A0:A7:EC:DD
```

Avec le SDK Android : `apksigner verify --print-certs OnyxFilter-vX.Y.Z.apk` ; sur le téléphone, avec
[AppVerifier](https://github.com/soupslurpr/AppVerifier).

## Côté serveur : l'API HTTP d'OnyxFilter

L'application utilise l'API HTTP d'OnyxFilter (`/api/v1`), sans modification du serveur. Elle
s'authentifie par un **jeton d'API** : dans l'interface web, ouvrir *Paramètres › Accès API*, créer un
jeton (par exemple « Téléphone ») et le copier — il n'est affiché qu'une fois. Le révoquer depuis la même
page déconnecte l'application.

Points d'accès utilisés (en-tête `Authorization: Bearer <jeton>`) :

| Méthode | Chemin | Usage |
|---|---|---|
| `GET` | `/api/v1/protection` | état ; sert aussi à vérifier le jeton à la connexion |
| `POST` | `/api/v1/protection/disable` | `{}` : sans limite ; `{ "durationSeconds": 600 }` : pendant 10 min |
| `POST` | `/api/v1/protection/enable` | réactivation |
| `GET` | `/api/v1/stats` | statistiques des dernières 24 heures |

La durée d'une désactivation est transmise en secondes, et l'application affiche la durée restante
renvoyée par le serveur : l'affichage ne dépend pas de la synchronisation des horloges du téléphone et du
serveur. Une instance sans `/api/v1` (version antérieure) est signalée à la connexion.

## Utilisation

Saisir l'adresse de l'interface web d'OnyxFilter, par exemple `https://onyxfilter.maison:7037` ou
`http://192.168.1.10:5259` (sans schéma, `https://` est ajouté), et le jeton d'API. Un chemin est accepté
pour une instance servie derrière un proxy inverse (`https://maison.example/onyxfilter`).

- **HTTP** est autorisé pour les instances uniquement joignables sur le réseau local ; l'écran de
  connexion signale alors que le jeton transite en clair.
- **Certificat auto-signé** : installer le certificat de l'autorité qui l'a émis dans
  *Paramètres → Sécurité → Chiffrement et identifiants → Installer un certificat → Certificat CA* ;
  l'application fait confiance aux autorités installées par l'utilisateur.

## Compilation

Android Studio (récent), ou en ligne de commande avec un JDK 17 ou plus récent (jusqu'au JDK 25) et le
SDK Android (API 37). Le wrapper télécharge Gradle 9.8 et en vérifie l'empreinte.

```sh
./gradlew assembleDebug          # APK : app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # tests unitaires (JVM)
./gradlew lintDebug
```

Outils : Android Gradle Plugin 9.4 (Kotlin intégré), Kotlin 2.4, Jetpack Compose (BOM 2026.09),
Material 3. L'intégration continue (GitHub Actions, `.github/workflows/android.yml`) exécute les tests et
le lint et publie l'APK de débogage en artefact de chaque exécution.

Publier une version : pousser un tag `vX.Y.Z`. Le workflow `.github/workflows/release.yml` compile l'APK,
le signe avec la clé conservée dans les secrets du dépôt, vérifie sa signature et son `versionCode`
(X × 1 000 000 + Y × 1 000 + Z : Android refuse une mise à jour dont le `versionCode` est plus petit), puis
crée la release.

Version minimale : Android 8.0 (API 26) ; cible : Android 17 (API 37).

## Sécurité

- Le jeton d'API est chiffré (AES-GCM) avec une clé de l'Android Keystore qui ne quitte pas l'appareil ;
  il est exclu des sauvegardes et des transferts entre appareils. Aucun mot de passe n'est demandé ni
  conservé.
- Un jeton donne accès à toute l'API : en créer un par appareil, pour pouvoir le révoquer seul.

## Structure

```
app/src/main/java/io/github/anthodingo/onyxfilter/
├── data/     client HTTP (OkHttp), modèles JSON, session chiffrée, dépôt
├── domain/   durées de désactivation, état de la protection, statistiques, calculs d'affichage
├── ui/       écrans Compose (connexion, protection), ViewModels, thème
├── tile/     tuile des réglages rapides
├── update/   ajout à Obtainium, pour les mises à jour
└── widget/   widgets de l'écran d'accueil (RemoteViews) et état partagé avec la tuile
```
