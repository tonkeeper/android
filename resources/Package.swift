// swift-tools-version: 6.2

import PackageDescription

let package = Package(
    name: "KeeperResources",
    platforms: [.iOS(.v15)],
    products: [
        // Dependency-free, dynamically linked so the asset catalog / fonts / Lottie live in
        // one shared framework instead of every product that statically links `TKUIKit`.
        // SwiftPM still copies the resource bundle into each app/extension; the intents
        // extension drops its copy at build time (see Keeper.xcodeproj). It lives in its own package so
        // consumers depend on it as a *product* (like `SnapKit-Dynamic`) — that's what lets it
        // build as a single shared framework. Having no code dependencies, being `.dynamic`
        // does not break SwiftUI Previews.
        .library(
            name: "KeeperResources",
            type: .dynamic,
            targets: ["KeeperResources"]
        ),
    ],
    targets: [
        .target(
            name: "KeeperResources",
            resources: [
                .process("Resources/Assets.xcassets"),
                .process("Resources/Fonts/font"),
                .process("Resources/Images/drawable-nodpi"),
                .process("Resources/Lottie"),
                .process("Resources/NativeLottie/raw"),
                .process("Resources/ComposeLottie/files/lottie"),
            ],

            swiftSettings: [
                .unsafeFlags(["-Xfrontend", "-warnings-as-errors"]),
            ]
        ),
    ],
    swiftLanguageModes: [.v5]
)
