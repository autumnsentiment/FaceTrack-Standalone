import SwiftUI

@main
struct FaceTrackStandaloneApp: App {
    @StateObject private var viewModel = FaceTrackViewModel()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(viewModel)
        }
    }
}
