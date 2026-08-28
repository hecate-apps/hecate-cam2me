import SwiftUI

/// Skeleton entry point. Proves the FFI wiring end to end -- generating a
/// puzzle-hardened Ed25519 identity via `FfiKeyPair` and rendering its
/// node_id -- without yet touching the mesh (no connect/advertise/stream
/// here). Camera capture and a real mesh session are the next feature
/// pass, not this one. Mirrors the Android skeleton's MainActivity
/// exactly in scope.
struct ContentView: View {
    private let nodeIdHex: String

    init() {
        let identity = FfiKeyPair.generate()
        nodeIdHex = identity.nodeId().map { String(format: "%02x", $0) }.joined()
    }

    var body: some View {
        VStack(spacing: 12) {
            Text("Hecate Cam2Me")
                .font(.title)
            Text("macula-rust-sdk-ffi is alive.")
            Text("node_id: \(nodeIdHex)")
                .font(.system(.footnote, design: .monospaced))
                .multilineTextAlignment(.center)
                .padding(.horizontal)
        }
        .padding()
    }
}

#Preview {
    ContentView()
}
