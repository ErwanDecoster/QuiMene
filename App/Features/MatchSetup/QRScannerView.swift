@preconcurrency import AVFoundation
import SwiftUI

/// Doc utilisateur — scanner intégré plutôt que de dépendre uniquement de l'appareil photo
/// système. `AVCaptureMetadataOutput` est l'API QR la plus largement compatible (aucune exigence
/// matérielle au-delà d'une caméra, contrairement à `VisionKit.DataScannerViewController`),
/// système, zéro dépendance (ADR-0012).
struct QRScannerView: UIViewControllerRepresentable {
    let onScan: (String) -> Void

    func makeUIViewController(context: Context) -> QRScannerViewController {
        let controller = QRScannerViewController()
        controller.onScan = onScan
        return controller
    }

    func updateUIViewController(_ uiViewController: QRScannerViewController, context: Context) {}
}

final class QRScannerViewController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    var onScan: ((String) -> Void)?

    private let session = AVCaptureSession()
    private var hasScanned = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
            guard granted else { return }
            DispatchQueue.main.async {
                self?.configureSession()
            }
        }
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        guard !session.isRunning else { return }
        let session = session
        DispatchQueue.global(qos: .userInitiated).async { session.startRunning() }
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        guard session.isRunning else { return }
        let session = session
        DispatchQueue.global(qos: .userInitiated).async { session.stopRunning() }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
        updateVideoRotation()
    }

    // Doc utilisateur — remontée : sur iPad, l'aperçu caméra restait dans l'orientation par
    // défaut du capteur (mis en boîtier en paysage) quel que soit celui de l'écran, y compris
    // après rotation. `AVCaptureVideoPreviewLayer` ne suit jamais l'orientation de l'interface
    // tout seul — contrairement à ce qu'on pourrait attendre, il faut le lui dire explicitement
    // (`videoRotationAngle`, iOS 17+) et le refaire à chaque rotation, pas seulement une fois au
    // démarrage.
    override func viewWillTransition(to size: CGSize, with coordinator: any UIViewControllerTransitionCoordinator) {
        super.viewWillTransition(to: size, with: coordinator)
        coordinator.animate(alongsideTransition: { [weak self] _ in
            self?.updateVideoRotation()
        })
    }

    private var previewLayer: AVCaptureVideoPreviewLayer?

    private func configureSession() {
        guard let device = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else { return }
        session.addInput(input)

        let output = AVCaptureMetadataOutput()
        guard session.canAddOutput(output) else { return }
        session.addOutput(output)
        output.setMetadataObjectsDelegate(self, queue: .main)
        output.metadataObjectTypes = [.qr]

        let layer = AVCaptureVideoPreviewLayer(session: session)
        layer.frame = view.bounds
        layer.videoGravity = .resizeAspectFill
        view.layer.addSublayer(layer)
        previewLayer = layer
        updateVideoRotation()

        if !session.isRunning {
            let session = session
            DispatchQueue.global(qos: .userInitiated).async { session.startRunning() }
        }
    }

    private func updateVideoRotation() {
        guard let connection = previewLayer?.connection else { return }
        let angle: CGFloat
        switch view.window?.windowScene?.interfaceOrientation {
        case .landscapeLeft: angle = 180
        case .landscapeRight: angle = 0
        case .portraitUpsideDown: angle = 270
        default: angle = 90 // portrait, et repli si l'orientation n'est pas encore connue
        }
        guard connection.isVideoRotationAngleSupported(angle) else { return }
        connection.videoRotationAngle = angle
    }

    // `AVCaptureMetadataOutputObjectsDelegate` n'est pas isolé à un acteur (API AVFoundation,
    // pré-Swift-concurrency) ; `UIViewController` l'est implicitement à `@MainActor`. `nonisolated`
    // ici, puis un saut explicite vers `@MainActor` pour la mutation d'état — le callback arrive
    // déjà sur `.main` (`setMetadataObjectsDelegate(self, queue: .main)`), ce saut ne fait que
    // satisfaire le compilateur, pas changer le thread réel.
    nonisolated func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
        guard let object = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let value = object.stringValue else { return }
        Task { @MainActor in
            guard !self.hasScanned else { return }
            self.hasScanned = true
            self.onScan?(value)
        }
    }
}
