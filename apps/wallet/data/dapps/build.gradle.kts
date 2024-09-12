plugins {
    id("com.tonapps.wallet.data")
}

android {
    namespace = Build.namespacePrefix("wallet.data.dapps")
}


dependencies {
    implementation(project(Dependence.Lib.sqlite))
}