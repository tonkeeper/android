plugins {
    id("com.tonapps.wallet.data")
    id("kotlin-parcelize")
}

android {
    namespace = Build.namespacePrefix("wallet.data.rn")
}

dependencies {
    api(platform(Dependence.Firebase.bom))
    api(Dependence.Firebase.crashlytics)

    implementation(Dependence.AndroidX.biometric)
    implementation(Dependence.TON.crypto)

    implementation(project(Dependence.Lib.sqlite))
    implementation(project(Dependence.Lib.security))
    implementation(project(Dependence.Lib.extensions))
}


