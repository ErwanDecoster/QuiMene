import SwiftUI
import WidgetKit

@main
struct CaCompteWidgetBundle: WidgetBundle {
  var body: some Widget {
    MatchWidget()
    MatchLiveActivityWidget()
  }
}
