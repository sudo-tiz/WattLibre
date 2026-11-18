# WattLibre

Application Android native en Kotlin pour les batteries compatibles BrightEMS. Fonctionne en local par Bluetooth LE ; aucun accès Internet ni service Google Play.

## État

Prototype : recherche des appareils FOSSIBOT, AFERIY, SYDPOWER ou POWER, connexion BLE, lecture des 80 registres d'entrée (fonction `0x04`) et mesures détaillées. Les boutons USB, DC maître, AC et éclairage sont regroupés dans une grille 2×2. Chaque clic relit l’état juste avant la commande ; aucun registre n’a besoin d’être lu manuellement au préalable.

L’application sépare le choix de l’appareil de son écran de suivi. Le nom se modifie via le crayon près du titre et reste mémorisé localement par adresse BLE. Les détails d’entrée et sortie sont repliés par défaut, par catégorie. Une perte de liaison déclenche une reconnexion progressive ; `Actualiser` relance la liaison si nécessaire et les anciennes mesures sont signalées.

Le diagnostic replié affiche les 80 registres d’entrée et, après lecture, les holding registers disponibles ; il inclut la découverte BLE en cas de problème et permet de copier les valeurs. Sur le P280, `input 8` est affiché comme puissance XT60 en 0,1 W et `input 10` comme puissance de la prise voiture en W. HREG25 ne commande pas séparément le XT60. Le bouton d’éclairage fait défiler Éteint, Allumé, SOS et Clignotant.

BrightEMS choisit sa page V1 depuis `productInfo.protocol_version` ; le registre 5 lu en local est conservé comme valeur brute, sans décider seul du protocole.

Le protocole est non officiel et s'appuie sur `../ESP-FBot/components/fbot`. Les données doivent être vérifiées sur chaque modèle de batterie avant d'ajouter des commandes.

## Build

```sh
./gradlew :app:assembleDebug
```

APK : `app/build/outputs/apk/debug/app-debug.apk`.

Android 8.0 (API 26) minimum. Sur Android 11 et antérieur, le système exige la permission de localisation et l'activation du service de localisation pour scanner le BLE. L'application n'utilise pas ces données et ne demande pas l'autorisation Internet.

## Licence

Apache-2.0. Les UUID BLE sont documentés dans le projet [ESP-FBot](../ESP-FBot), également sous Apache-2.0.
