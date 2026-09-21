plugins {
    id("target.android.library")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
}

dependencies {
    implementation(libs.ton.tvm)
    implementation(libs.ton.crypto)
    implementation(libs.ton.tlb)
    implementation(libs.ton.blockTlb)
    implementation(libs.ton.tonapiTl)
    implementation(libs.ton.contract)
    implementation(libs.koin.core)
    implementation(libs.chainkit.chain.ethereum)
    implementation(libs.chainkit.net.provider)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.sqlite.bundled)
    ksp(libs.androidx.room.compiler)

    implementation(projects.apps.wallet.api)
    implementation(projects.apps.wallet.data.core)
    implementation(projects.apps.wallet.data.rn)
    implementation(projects.apps.wallet.data.account)
    implementation(projects.apps.wallet.data.multichain.wallet)
    implementation(projects.lib.blockchain)
    implementation(projects.lib.extensions)
    implementation(projects.lib.sqlite)
    implementation(projects.lib.wc)
    implementation(projects.lib.security)
    implementation(projects.lib.network)
    implementation(projects.lib.base64)
    implementation(projects.kmp.async)
}