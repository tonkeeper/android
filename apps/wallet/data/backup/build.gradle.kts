plugins {
    id("com.tonapps.wallet.data")
    id("kotlin-parcelize")
}

android {
    namespace = Build.namespacePrefix("wallet.data.backup")
}

dependencies {
    implementation(project(Dependence.Lib.sqlite))
    implementation(project(Dependence.Lib.extensions))
    implementation(project(Dependence.Wallet.Data.rn))
}

