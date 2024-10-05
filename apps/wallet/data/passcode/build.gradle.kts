plugins {
    id("com.tonapps.wallet.data")
}

android {
    namespace = Build.namespacePrefix("wallet.data.passcode")
}

dependencies {

    implementation(Dependence.AndroidX.biometric)

    implementation(project(Dependence.UIKit.core))

    implementation(project(Dependence.Wallet.Data.core))
    implementation(project(Dependence.Wallet.Data.account))
    implementation(project(Dependence.Wallet.Data.settings))
    implementation(project(Dependence.Wallet.Data.rn))
    implementation(project(Dependence.Lib.extensions))
    implementation(project(Dependence.Lib.security))
}
