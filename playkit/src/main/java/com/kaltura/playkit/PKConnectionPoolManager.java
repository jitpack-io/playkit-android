package com.kaltura.playkit;

import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class PKConnectionPoolManager {

    private static final String userAgent = PlayKitManager.CLIENT_TAG;

    private static final int MAX_IDLE_CONNECTIONS = 10;
    private static final int KEEP_ALIVE_DURATION = 5;
    private static final int WARMUP_TIMES = 2;

    private static final OkHttpClient okClient = new OkHttpClient.Builder()
            .followRedirects(false)
            .connectionPool(new ConnectionPool(MAX_IDLE_CONNECTIONS, KEEP_ALIVE_DURATION, TimeUnit.MINUTES))
            .protocols(Collections.singletonList(Protocol.HTTP_1_1))    // Avoid http/2 due to https://github.com/google/ExoPlayer/issues/4078
            .build();

    public static OkHttpClient.Builder newClientBuilder() {
        return okClient.newBuilder().followRedirects(true);
    }

    public static void warmUp(String... hosts) {

        CountDownLatch latch = new CountDownLatch(hosts.length * WARMUP_TIMES);

        for (String host : hosts) {
            for (int i = 0; i < WARMUP_TIMES; i++) {
                warmUrl("https://" + host + "/playkit-warmup", latch);
            }
        }

        try {
            latch.await(6, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void warmUrl(String url, CountDownLatch latch) {

        final Call call = okClient.newCall(
                new Request.Builder()
                        .url(url)
                        .header("user-agent", userAgent)
                        .build()
        );

        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                latch.countDown();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final ResponseBody body = response.body();
                if (body != null) {
                    if (body.contentLength() < 10_000_000) {
                        body.bytes();
                    }
                    body.close();
                }
                latch.countDown();
            }
        });
    }
}
