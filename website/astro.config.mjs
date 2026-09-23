// @ts-check
import { defineConfig } from 'astro/config';

// https://astro.build/config
export default defineConfig({
  // La compression supprime les retours à la ligne voisins d'une balise en ligne : « données :
  // <a> » devenait « données :contact@… ». Les pages sont petites, le gain ne vaut pas le défaut.
  compressHTML: false,
});
