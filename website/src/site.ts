// Réglages du site — une seule source pour ce qui change au lancement.

export const site = {
  /** Nom de l'app, avec l'espace insécable avant « ? » (charte §12.3). */
  name: 'Qui Mène ?',
  publisher: 'Erwan Decoster',
  email: 'contact@erwan-decoster.com',
  /** Lien App Store — `null` tant que l'app n'est pas publiée. */
  appStoreUrl: null as string | null,
  /** Région d'hébergement du projet Supabase (`eu-west-1`), affichée par la politique de confidentialité. */
  supabaseRegion: { fr: 'Union européenne (Irlande)', en: 'European Union (Ireland)' },
  /** Date de dernière mise à jour de la politique de confidentialité. */
  privacyUpdated: { fr: '25 septembre 2026', en: 'September 25, 2026' },
};

/** Les 20 jeux du catalogue (`spec/games`), dans l'ordre alphabétique. */
export const games = [
  'Belote', '6 qui prend', 'Cornhole', 'Flip 7', 'Jeu libre', 'Mölkky', 'Odin', 'Pétanque',
  'Pictionary', 'Qwixx', 'Rami', 'Rummikub', 'Scrabble', 'Skyjo', 'Tarot', 'Time’s Up !',
  'Triominos', 'Trivial Pursuit', 'Wizard', 'Yams',
];
