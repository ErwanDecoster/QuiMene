/// SplitMix64 — générateur déterministe : un test qui échoue redevient reproductible à partir
/// de son seed, contrairement à `SystemRandomNumberGenerator`. Copie de
/// `DomainTests/SeededGenerator.swift` : `CatalogTests` ne dépend pas de `DomainTests` (voir
/// `Package.swift`), donc pas de partage direct entre cibles de test.
struct SeededGenerator: RandomNumberGenerator {
  private var state: UInt64

  init(seed: Int) {
    state = UInt64(bitPattern: Int64(seed)) &+ 0x9E37_79B9_7F4A_7C15
  }

  mutating func next() -> UInt64 {
    state &+= 0x9E37_79B9_7F4A_7C15
    var z = state
    z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
    z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
    return z ^ (z >> 31)
  }
}
