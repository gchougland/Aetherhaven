package com.hexvane.aetherhaven.community;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Minimal outbound HTTP for the community marketplace API. */
public final class CommunityHttpClient {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new Gson();
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 120_000;

    private CommunityHttpClient() {}

    public static final class HttpResult {
        private final int statusCode;
        @Nullable
        private final String body;

        public HttpResult(int statusCode, @Nullable String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        public int getStatusCode() {
            return statusCode;
        }

        @Nullable
        public String getBody() {
            return body;
        }

        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }
    }

    @Nullable
    public static byte[] getBytes(@Nonnull String url) {
        return getBytes(url, Map.of());
    }

    @Nullable
    public static byte[] getBytes(@Nonnull String url, @Nonnull Map<String, String> headers) {
        try {
            HttpURLConnection http = open(url, "GET", headers);
            int code = http.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? http.getInputStream() : http.getErrorStream();
            if (in == null || code < 200 || code >= 300) {
                LOGGER.atWarning().log("Community GET %s failed: HTTP %s", url, code);
                return null;
            }
            byte[] body = readAll(in);
            http.disconnect();
            return body;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Community GET %s failed", url);
            return null;
        }
    }

    /** @return HTTP status code, or {@code -1} when the request could not be made */
    public static int getResponseCode(@Nonnull String url, @Nonnull Map<String, String> headers) {
        try {
            HttpURLConnection http = open(url, "GET", headers);
            int code = http.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? http.getInputStream() : http.getErrorStream();
            if (in != null) {
                readAll(in);
            }
            http.disconnect();
            return code;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Community GET %s failed", url);
            return -1;
        }
    }

    @Nullable
    public static String getString(@Nonnull String url) {
        byte[] bytes = getBytes(url);
        return bytes != null ? new String(bytes, StandardCharsets.UTF_8) : null;
    }

    @Nullable
    public static String getString(@Nonnull String url, @Nonnull Map<String, String> headers) {
        byte[] bytes = getBytes(url, headers);
        return bytes != null ? new String(bytes, StandardCharsets.UTF_8) : null;
    }

    @Nullable
    public static String postJson(@Nonnull String url, @Nonnull Map<String, String> headers, @Nonnull String jsonBody) {
        try {
            HttpURLConnection http = open(url, "POST", headers);
            http.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] body = jsonBody.getBytes(StandardCharsets.UTF_8);
            http.setFixedLengthStreamingMode(body.length);
            try (OutputStream os = http.getOutputStream()) {
                os.write(body);
            }
            int code = http.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? http.getInputStream() : http.getErrorStream();
            String response = in != null ? new String(readAll(in), StandardCharsets.UTF_8) : "";
            http.disconnect();
            if (code < 200 || code >= 300) {
                LOGGER.atWarning().log("Community POST %s failed: HTTP %s %s", url, code, response);
                return null;
            }
            return response;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Community POST %s failed", url);
            return null;
        }
    }

    @Nullable
    public static String postMultipart(
        @Nonnull String url,
        @Nonnull Map<String, String> headers,
        @Nonnull String boundary,
        @Nonnull byte[] body
    ) {
        HttpResult result = sendMultipart(url, "POST", headers, boundary, body);
        return result.isSuccess() ? result.getBody() : null;
    }

    @Nullable
    public static String putMultipart(
        @Nonnull String url,
        @Nonnull Map<String, String> headers,
        @Nonnull String boundary,
        @Nonnull byte[] body
    ) {
        HttpResult result = sendMultipart(url, "PUT", headers, boundary, body);
        return result.isSuccess() ? result.getBody() : null;
    }

    @Nonnull
    public static HttpResult postMultipartResult(
        @Nonnull String url,
        @Nonnull Map<String, String> headers,
        @Nonnull String boundary,
        @Nonnull byte[] body
    ) {
        return sendMultipart(url, "POST", headers, boundary, body);
    }

    @Nonnull
    public static HttpResult putMultipartResult(
        @Nonnull String url,
        @Nonnull Map<String, String> headers,
        @Nonnull String boundary,
        @Nonnull byte[] body
    ) {
        return sendMultipart(url, "PUT", headers, boundary, body);
    }

    /** Reads {@code error} from a marketplace JSON error body, if present. */
    @Nullable
    public static String parseErrorKey(@Nullable String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonObject root = GSON.fromJson(responseBody, JsonObject.class);
            if (root == null || !root.has("error") || root.get("error").isJsonNull()) {
                return null;
            }
            String error = root.get("error").getAsString();
            return error != null && !error.isBlank() ? error.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Nonnull
    private static HttpResult sendMultipart(
        @Nonnull String url,
        @Nonnull String method,
        @Nonnull Map<String, String> headers,
        @Nonnull String boundary,
        @Nonnull byte[] body
    ) {
        try {
            HttpURLConnection http = open(url, method, headers);
            http.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            http.setFixedLengthStreamingMode(body.length);
            try (OutputStream os = http.getOutputStream()) {
                os.write(body);
            }
            int code = http.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? http.getInputStream() : http.getErrorStream();
            String response = in != null ? new String(readAll(in), StandardCharsets.UTF_8) : "";
            http.disconnect();
            if (code < 200 || code >= 300) {
                LOGGER.atWarning().log("Community %s %s failed: HTTP %s %s", method, url, code, response);
            }
            return new HttpResult(code, response);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Community %s %s failed", method, url);
            return new HttpResult(-1, null);
        }
    }

    @Nonnull
    private static HttpURLConnection open(@Nonnull String url, @Nonnull String method, @Nullable Map<String, String> headers)
        throws Exception {
        HttpURLConnection http = (HttpURLConnection) URI.create(url).toURL().openConnection();
        http.setRequestMethod(method);
        http.setConnectTimeout(CONNECT_TIMEOUT_MS);
        http.setReadTimeout(READ_TIMEOUT_MS);
        http.setDoInput(true);
        if ("POST".equals(method) || "PUT".equals(method)) {
            http.setDoOutput(true);
        }
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                http.setRequestProperty(e.getKey(), e.getValue());
            }
        }
        return http;
    }

    @Nonnull
    private static byte[] readAll(@Nonnull InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
