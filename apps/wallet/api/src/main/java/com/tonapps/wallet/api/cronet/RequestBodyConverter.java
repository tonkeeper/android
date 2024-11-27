package com.tonapps.wallet.api.cronet;

import java.io.IOException;
import okhttp3.RequestBody;
import org.chromium.net.UploadDataProvider;

/** An interface for classes converting from OkHttp to Cronet request bodies. */
interface RequestBodyConverter {
    UploadDataProvider convertRequestBody(RequestBody requestBody, int writeTimeoutMillis)
            throws IOException;
}