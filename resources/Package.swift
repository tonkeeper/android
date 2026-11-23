// swift-tools-version: 6.2

import PackageDescription

let package = Package(
    name: "KeeperResources",
    platforms: [.iOS(.v15)],
    products: [
        // Linked only by `TKUIKit`, so the code lives once in the shared `TKUIKit.framework`.
        // Not `.dynamic`: that would break the previews of `TKUIKit`'s own views (see its
        // manifest). SwiftPM still copies the resource bundle into each app/extension; the
        // intents extension drops its copy at build time (see Keeper.xcodeproj).
        .library(
            name: "KeeperResources",
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
