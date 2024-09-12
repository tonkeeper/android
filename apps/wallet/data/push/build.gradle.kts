plugins {
    id("com.tonapps.wallet.data")
    id("kotlin-parcelize")
}

android {
    namespace = Build.namespacePrefix("wallet.data.push")
}

dependencies {
    implementation(Dependence.Squareup.okhttp)
    
    implementation(platform(Dependence.Firebase.bom))
    implementation(Dependence.Firebase.messaging)

    implementation(project(Dependence.Wallet.api))
    implementation(project(Dependence.Wallet.Data.account))
    implementation(project(Dependence.Wallet.Data.settings))
    implementation(project(Dependence.Wallet.Data.events))
    implementation(project(Dependence.Wallet.Data.tonconnect))
    implementation(project(Dependence.Lib.network))
    implementation(project(Dependence.Lib.blockchain))
    implementation(project(Dependence.Lib.security))
    implementation(project(Dependence.Lib.extensions))
    implementation(project(Dependence.Lib.sqlite))
    implementation(project(Dependence.UIKit.core))
}

