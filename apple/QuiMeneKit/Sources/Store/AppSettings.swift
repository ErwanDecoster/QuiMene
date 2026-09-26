import Foundation
import Observation

/// Doc 03 « AppSettings ». Le consentement iCloud ne peut pas vivre *dans* le container
/// CloudKit qu'il conditionne (poule et l'œuf : le container n'existe pas encore quand on le
/// lit) — et le synchroniser via iCloud serait de toute façon contradictoire avec un réglage
/// pensé par appareil. Stocké en `UserDefaults`, en dehors du schéma SwiftData.
@Observable
public final class AppSettings {
  private let defaults: UserDefaults
  private static let iCloudSyncEnabledKey = "iCloudSyncEnabled"

  public var iCloudSyncEnabled: Bool {
    didSet { defaults.set(iCloudSyncEnabled, forKey: Self.iCloudSyncEnabledKey) }
  }

  public init(defaults: UserDefaults = .standard) {
    self.defaults = defaults
    self.iCloudSyncEnabled = defaults.object(forKey: Self.iCloudSyncEnabledKey) as? Bool ?? true
  }
}
