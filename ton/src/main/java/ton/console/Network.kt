package ton.console

import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request

object Network {

    const val ENDPOINT = "https://keeper.tonapi.io/v2/"

    val okHttpClient = OkHttpClient().newBuilder()
        .addInterceptor(
            AuthorizationInterceptor(
            AuthorizationInterceptor.Type.BEARER,
            "AF77F5JND26OLHQAAAAKQMSCYW3UVPFRA7CF2XHX6QG4M5WAMF5QRS24R7J4TF2UTSXOZEY"
        )
        )
        .build()

    fun newRequest(url: String) = Request.Builder().url(url)

    fun newRequest(uri: Uri) = newRequest(uri.toString())

    fun newCall(request: Request) = okHttpClient.newCall(request)

    fun request(request: Request) = newCall(request).execute()

    fun get(url: String): String {
        val request = newRequest(url).build()
        val response = newCall(request).execute()
        return response.body?.string()!!
    }

}