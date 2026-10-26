# KeeperResources

`KeeperResources` is the resource package both apps share: a dynamic SwiftPM package that iOS
uses through `TKUIKit`. Android reads these source directories directly:

| Directory under `shared/resources/Sources/KeeperResources/Resources` | Consumer |
| --- | --- |
| `Fonts/font` | Android native `R.font` and Compose typography; iOS CoreText |
| `NativeLottie/raw` | Android UIKit `R.raw`; iOS Lottie |
| `ComposeLottie/files/lottie` | Android Compose assets and Browser Lottie; iOS Lottie |
| `Images/drawable-nodpi` | Android `R.drawable` (staking module); iOS `UIImage.TKUIKit.Shared` |
| `Assets.xcassets`, `Lottie` | iOS resources |

There are no source synchronization steps or symlinks. SwiftPM, Compose and AAPT package
the common files during their normal builds. Compose typography uses native `R.font`, so
the shared TTF files are also packaged only once in Android.

The two public mirrors include this package as `resources/` from the same release tag as their platform tree.
The dependency paths support both `ios/` / `android/` in this monorepo and flat public repos.

After changing iOS resource names, run `make -C ios resources`. The SwiftGen configuration
keeps Swift API aliases and font PostScript names independent of the shared filenames.

A raster both apps show goes to `Images/drawable-nodpi`, not to an imageset: an asset
catalog only takes files inside its imagesets. iOS loads these as loose bundle files at
scale 1 — what a single-scale imageset compiles to — so a name must suit both platforms
(`[a-z0-9_]`).
