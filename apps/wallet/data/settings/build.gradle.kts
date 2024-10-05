plugins {
    id("com.tonapps.wallet.data")
}

android {
    namespace = Build.namespacePrefix("wallet.data.settings")
}

dependencies {
    implementation(project(Dependence.Lib.extensions))
    implementation(project(Dependence.Wallet.Data.core))
    implementation(project(Dependence.Wallet.Data.rn))
    implementation(project(Dependence.Wallet.localization))
}
