package com.tonapps.wallet.api.cronet;

import static com.google.firebase.components.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.util.Log;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import okhttp3.Call;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.chromium.net.CronetEngine;
import org.chromium.net.UrlRequest;

/**
 * An OkHttp interceptor that redirects HTTP traffic to use Cronet instead of using the OkHttp
 * network stack.
 *
 * <p>The interceptor should be used as the last application interceptor to ensure that all other
 * interceptors are visited before sending the request on wire and after a response is returned.
 *
 * <p>The interceptor is a plug-and-play replacement for the OkHttp stack for the most part,
 * however, there are some caveats to keep in mind:
 *
 * <ol>
 *   <li>The entirety of OkHttp core is bypassed. This includes caching configuration and network
 *       interceptors.
 *   <li>Some response fields are not being populated due to mismatches between Cronet's and
 *       OkHttp's architecture. TODO(danstahr): add a concrete list).
 * </ol>
 */
public final class CronetInterceptor implements Interceptor, AutoCloseable {
    private static final String TAG = "CronetInterceptor";

    private static final int CANCELLATION_CHECK_INTERVAL_MILLIS = 500;

    private final RequestResponseConverter converter;
    private final Map<Call, UrlRequest> activeCalls = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduledExecutor = new ScheduledThreadPoolExecutor(1);

    private CronetInterceptor(RequestResponseConverter converter) {
        this.converter = checkNotNull(converter);

        // TODO(danstahr): There's no other way to know if the call is canceled but polling
        //  (https://github.com/square/okhttp/issues/7164).
        ScheduledFuture<?> unusedFuture =
                scheduledExecutor.scheduleAtFixedRate(
                        () -> {
                            Iterator<Entry<Call, UrlRequest>> activeCallsIterator =
                                    activeCalls.entrySet().iterator();

                            while (activeCallsIterator.hasNext()) {
                                try {
                                    Entry<Call, UrlRequest> activeCall = activeCallsIterator.next();
                                    if (activeCall.getKey().isCanceled()) {
                                        activeCallsIterator.remove();
                                        activeCall.getValue().cancel();
                                    }
                                } catch (RuntimeException e) {
                                    Log.w(TAG, "Unable to propagate cancellation status", e);
                                }
                            }
                        },
                        CANCELLATION_CHECK_INTERVAL_MILLIS,
                        CANCELLATION_CHECK_INTERVAL_MILLIS,
                        MILLISECONDS);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        if (chain.call().isCanceled()) {
            throw new IOException("Canceled");
        }

        Request request = chain.request();

        RequestResponseConverter.CronetRequestAndOkHttpResponse requestAndOkHttpResponse =
                converter.convert(request, chain.readTimeoutMillis(), chain.writeTimeoutMillis());

        activeCalls.put(chain.call(), requestAndOkHttpResponse.getRequest());

        try {
            requestAndOkHttpResponse.getRequest().start();
            return toInterceptorResponse(requestAndOkHttpResponse.getResponse(), chain.call());
        } catch (RuntimeException | IOException e) {
            // If the response is retrieved successfully the caller is responsible for closing
            // the response, which will remove it from the active calls map.
            activeCalls.remove(chain.call());
            throw e;
        }
    }

    /** Creates a {@link CronetInterceptor} builder. */
    public static Builder newBuilder(CronetEngine cronetEngine) {
        return new Builder(cronetEngine);
    }

    @Override
    public void close() {
        scheduledExecutor.shutdown();
    }

    /** A builder for {@link CronetInterceptor}. */
    public static final class Builder
            extends RequestResponseConverterBasedBuilder<Builder, CronetInterceptor> {

        Builder(CronetEngine cronetEngine) {
            super(cronetEngine, Builder.class);
        }

        /** Builds the interceptor. The same builder can be used to build multiple interceptors. */
        @Override
        public CronetInterceptor build(RequestResponseConverter converter) {
            return new CronetInterceptor(converter);
        }
    }

    private Response toInterceptorResponse(Response response, Call call) {
        assert response.body() != null;

        if (response.body() instanceof CronetInterceptorResponseBody) {
            return response;
        }

        return response
                .newBuilder()
                .body(new CronetInterceptorResponseBody(response.body(), call))
                .build();
    }

    private class CronetInterceptorResponseBody extends CronetTransportResponseBody {
        private final Call call;

        private CronetInterceptorResponseBody(ResponseBody delegate, Call call) {
            super(delegate);
            this.call = call;
        }

        @Override
        void customCloseHook() {
            activeCalls.remove(call);
        }
    }
}