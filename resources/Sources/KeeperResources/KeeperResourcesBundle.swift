import Foundation

/// Resources (asset catalog, fonts, Lottie) live in this dependency-free package so it can be
/// linked as a single shared dynamic framework — embedded once in the app and reused by the
/// extensions, instead of being copied into every product that depends on `TKUIKit`.
///
/// `TKUIKit` itself stays statically linked (automatic), which keeps SwiftUI Previews working:
/// importing an explicitly `.dynamic` product force-promotes transitive deps (e.g. Kingfisher)
/// into synthesized `*_PackageProduct.framework`s that XCPreviewAgent can't locate.
public enum KeeperResourcesBundle {
    /// The compiled resource bundle.
    ///
    /// Resolved by hand rather than via SwiftPM's generated `Bundle.module`: as a dynamic
    /// framework the bundle is copied next to whichever product is running (`Bundle.main` for the
    /// app/extension, the build-products dir for a hostless `xctest`), and SwiftPM's accessor only
    /// checks `Bundle.main` + the framework's own resources — so it `fatalError`s under test. This
    /// finder also walks up from the framework location, covering every host.
    public static let bundle: Bundle = {
        let name = String(reflecting: BundleFinder.self)
        guard let moduleName = name.split(separator: ".").first.map(String.init) else {
            fatalError("Unable to determine resource bundle name")
        }
        let bundleName = "\(moduleName)_\(moduleName)"
        let frameworkURL = Bundle(for: BundleFinder.self).bundleURL

        var candidateURLs: [URL?] = [
            // App: the bundle sits at the running product's root.
            Bundle.main.resourceURL,
            Bundle.main.bundleURL,
            // Dynamic framework: the bundle may be inside or next to the framework...
            Bundle(for: BundleFinder.self).resourceURL,
            frameworkURL,
            // ...or in the enclosing dir: `Keeper.app` for the intents extension (the build drops
            // its own copy), the build-products root for a hostless `xctest`.
            frameworkURL.deletingLastPathComponent(),
            frameworkURL.deletingLastPathComponent().deletingLastPathComponent(),
        ]

        if let override = ProcessInfo.processInfo.environment["PACKAGE_RESOURCE_BUNDLE_PATH"] {
            candidateURLs.append(URL(fileURLWithPath: override))
        }

        for case let url? in candidateURLs {
            if let bundle = Bundle(url: url.appendingPathComponent(bundleName + ".bundle")) {
                return bundle
            }
        }

        fatalError("Unable to locate resource bundle \(bundleName).bundle")
    }()
}

private final class BundleFinder {}
