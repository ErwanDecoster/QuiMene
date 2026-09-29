import Foundation

/// Clé **anon/publique** Supabase : conçue pour être embarquée dans un client (l'accès aux tables
/// passe par des fonctions SQL qui exigent un code ou un identifiant, pas par le secret), à la
/// différence d'une clé `service_role`. Le contenu des parties partagées reste protégé par
/// `SessionCrypto` (chiffrement dérivé du code d'appairage, doc 09 « Sécurité et vie privée »), pas
/// par cette clé. Même projet que l'app Android.
enum SupabaseSyncConfig {
  static let projectURL = URL(string: "https://hcjehnnvqmkdwirgpcgu.supabase.co")!
  static let anonKey = "sb_publishable_YhV5A3mH3aUCejLx1QC2OQ_Hc18SA_b"
}
