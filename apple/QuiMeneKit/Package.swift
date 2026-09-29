// swift-tools-version: 6.2
import PackageDescription

// Doc 10 — Swift 6 strict et zéro avertissement : `SWIFT_TREAT_WARNINGS_AS_ERRORS` du projet Xcode
// ne s'applique pas aux cibles de ce package, d'où le réglage ici, commun à toutes les cibles.
let strictSettings: [SwiftSetting] = [
  .swiftLanguageMode(.v6),
  .treatAllWarnings(as: .error),
]

let package = Package(
  name: "QuiMeneKit",
  platforms: [.iOS(.v18), .macOS(.v14)],
  products: [
    .library(name: "Domain", targets: ["Domain"]),
    .library(name: "Catalog", targets: ["Catalog"]),
    .library(name: "Store", targets: ["Store"]),
    .library(name: "Sync", targets: ["Sync"]),
    .library(name: "DesignSystem", targets: ["DesignSystem"]),
  ],
  // Seule dépendance tierce, exception explicite à l'ADR-0012 : les fonctions en ligne (sessions
  // partagées, boîte aux lettres, écran verrouillé) reposent sur Supabase, comme côté Android,
  // plutôt que sur du code réseau maison (ADR-0016, ADR-0017).
  dependencies: [
    .package(url: "https://github.com/supabase/supabase-swift.git", from: "2.0.0")
  ],
  targets: [
    .target(
      name: "Domain",
      swiftSettings: strictSettings
    ),
    .target(
      name: "Catalog",
      dependencies: ["Domain"],
      // Le dossier ne doit surtout pas s'appeler "Resources" : `codesign` plante dessus
      // dès qu'une vraie signature (Team ID) est utilisée sur ce macOS/Xcode (bug
      // reproduit hors projet, sur un bundle minimal fait à la main — voir README).
      resources: [.copy("GameDefinitions")],
      swiftSettings: strictSettings
    ),
    .target(
      name: "Store",
      dependencies: ["Domain"],
      swiftSettings: strictSettings
    ),
    .target(
      name: "Sync",
      dependencies: [
        "Domain",
        .product(name: "Supabase", package: "supabase-swift"),
      ],
      swiftSettings: strictSettings
    ),
    .target(
      name: "DesignSystem",
      resources: [.process("Resources")],
      swiftSettings: strictSettings
    ),
    .testTarget(
      name: "DomainTests",
      dependencies: ["Domain"],
      swiftSettings: strictSettings
    ),
    .testTarget(
      name: "CatalogTests",
      dependencies: ["Catalog", "Domain"],
      resources: [.copy("GoldenResources")],
      swiftSettings: strictSettings
    ),
    .testTarget(
      name: "StoreTests",
      dependencies: ["Store", "Domain"],
      swiftSettings: strictSettings
    ),
    .testTarget(
      name: "SyncTests",
      dependencies: ["Sync", "Domain"],
      resources: [.copy("SessionResources")],
      swiftSettings: strictSettings
    ),
    .testTarget(
      name: "DesignSystemTests",
      dependencies: ["DesignSystem"],
      swiftSettings: strictSettings
    ),
  ]
)
