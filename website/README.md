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

## Avant publication

Dans `src/site.ts` :
- `supabaseRegion` : région du projet Supabase (tableau de bord › Project Settings › General),
  affichée dans la politique de confidentialité.
- `appStoreUrl` : lien App Store, une fois l'app publiée (remplace « Bientôt sur l'App Store »).
