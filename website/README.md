# Site de Qui Mène ?

Site vitrine, politique de confidentialité et assistance de l'app. Astro (template officiel
`minimal`), statique, sans JavaScript côté client. Styles : tokens de la charte graphique
([docs/07](../docs/07-charte-graphique.md)) dans `src/styles/global.css`, mode sombre compris.

| Page | URL |
|---|---|
| Accueil | `/` |
| Confidentialité (URL à donner à App Store Connect) | `/confidentialite` · `/en/privacy` |
| Assistance (URL d'assistance App Store Connect) | `/assistance` · `/en/support` |

## Développer

```sh
npm install
npm run dev      # http://localhost:4321
npm run build    # génère dist/
```

## Déployer sur Vercel

1. Importer le dépôt dans Vercel.
2. **Root Directory : `website`** (le dépôt est un monorepo). Le preset Astro est détecté seul.
3. Le site est servi sur le sous-domaine `*.vercel.app` choisi à la création du projet.

## Réglages

`src/site.ts` rassemble ce qui change au lancement : éditeur et contact, région d'hébergement
Supabase affichée par la politique de confidentialité, date de mise à jour de cette politique,
liste des jeux, et `appStoreUrl` (`null` tant que l'app n'est pas publiée : la page affiche alors
« Bientôt sur l'App Store »).
