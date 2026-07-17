package cn.iocoder.yudao.module.bpm.framework.datasource;

import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils.HEADER_TENANT_ID;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.*;

/** Calls only explicitly allow-listed relative paths on the fixed internal platform origin. */
@Component
public class BpmPlatformApiDataSourceProvider implements BpmFormDataSourceProvider {

    private static final Pattern SAFE_PATH = Pattern.compile("^/[A-Za-z0-9_/-]+$");
    private static final Set<String> METHODS = Set.of("GET", "POST");
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private final BpmFormDataSourceProperties properties;
    private final HttpClient httpClient;

    public BpmPlatformApiDataSourceProvider(BpmFormDataSourceProperties properties,
                                            @Qualifier("bpmFormDataSourceHttpClient") HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    @Override
    public int getType() {
        return TYPE_PLATFORM_API;
    }

    @Override
    public BpmFormDataSourceQueryResult execute(BpmFormDataSourceExecutionContext context) {
        Map<String, Object> config = parseConfig(context.getVersion().getSourceConfig());
        String path = config.get("path") instanceof String value ? value : null;
        String method = config.get("method") instanceof String value ? value.toUpperCase(Locale.ROOT) : "GET";
        URI target = resolveAllowedTarget(path);
        if (!METHODS.contains(method)) {
            throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
        HttpRequest request = buildRequest(target, method, context);
        try {
            HttpResponse<byte[]> response = httpClient.send(request, boundedBodyHandler());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                // Redirects are never followed. Their bodies are consumed by the discarding subscriber.
                throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_EXECUTION_FAILED);
            }
            if (response.body().length > MAX_RESPONSE_BYTES) {
                throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_EXECUTION_FAILED);
            }
            int maxRows = bounded(context.getVersion().getMaxRows(), properties.getMaxRows());
            return parseResponse(response.body(), context.getVersion().getVersion(), maxRows);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_EXECUTION_FAILED);
        } catch (IOException ex) {
            throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_EXECUTION_FAILED);
        }
    }

    private static HttpResponse.BodyHandler<byte[]> boundedBodyHandler() {
        return responseInfo -> {
            if (responseInfo.statusCode() < 200 || responseInfo.statusCode() >= 300) {
                return HttpResponse.BodySubscribers.mapping(
                        HttpResponse.BodySubscribers.discarding(), ignored -> new byte[0]);
            }
            return new BoundedByteArraySubscriber(MAX_RESPONSE_BYTES);
        };
    }

    private HttpRequest buildRequest(URI target, String method, BpmFormDataSourceExecutionContext context) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .timeout(Duration.ofSeconds(Math.max(1, context.getVersion().getTimeoutSeconds() == null
                        ? properties.getTimeoutSeconds() : Math.min(context.getVersion().getTimeoutSeconds(),
                        properties.getTimeoutSeconds()))));
        if (StringUtils.hasText(context.getAuthorization())) {
            builder.header("Authorization", context.getAuthorization());
        }
        builder.header(HEADER_TENANT_ID, String.valueOf(context.getTenantId()));
        if ("GET".equals(method)) {
            builder.uri(withQuery(target, context.getParameters())).GET();
        } else {
            builder.uri(target).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJsonString(context.getParameters())));
        }
        return builder.build();
    }

    private URI resolveAllowedTarget(String path) {
        if (!StringUtils.hasText(path) || !SAFE_PATH.matcher(path).matches() || path.contains("..")
                || path.contains("//") || path.contains("\\")) {
            throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
        List<String> allowList = properties.getApi().getAllowedPaths();
        if (allowList == null || allowList.stream().noneMatch(path::equals)) {
            throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
        String baseUrl = properties.getApi().getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
        try {
            URI base = URI.create(baseUrl);
            if (!("http".equalsIgnoreCase(base.getScheme()) || "https".equalsIgnoreCase(base.getScheme()))
                    || base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null
                    || base.getFragment() != null) {
                throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_CONFIG_INVALID);
            }
            URI target = base.resolve(path);
            if (!Objects.equals(base.getScheme(), target.getScheme())
                    || !Objects.equals(base.getHost(), target.getHost()) || effectivePort(base) != effectivePort(target)) {
                throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_CONFIG_INVALID);
            }
            return target;
        } catch (IllegalArgumentException ex) {
            throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static URI withQuery(URI target, Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return target;
        }
        String query = parameters.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .filter(entry -> entry.getValue() != null)
                .map(entry -> encode(entry.getKey()) + "=" + encode(String.valueOf(entry.getValue())))
                .reduce((left, right) -> left + "&" + right).orElse("");
        return URI.create(target + (query.isEmpty() ? "" : "?" + query));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static BpmFormDataSourceQueryResult parseResponse(byte[] body, Integer version, int maxRows) {
        try {
            // Do not use the logging JSON helper here: an invalid upstream body may contain sensitive data.
            JsonNode root = JsonUtils.getObjectMapper().readTree(body);
            JsonNode data = root;
            if (root.isObject() && root.has("code")) {
                if (root.path("code").asInt(-1) != 0) {
                    throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_EXECUTION_FAILED);
                }
                data = root.path("data");
            }
            int total;
            JsonNode rowsNode;
            if (data.isArray()) {
                rowsNode = data;
                total = data.size();
            } else if (data.isObject() && data.path("list").isArray()) {
                rowsNode = data.path("list");
                total = data.has("total") ? data.path("total").asInt(rowsNode.size()) : rowsNode.size();
            } else if (data.isObject()) {
                rowsNode = JsonUtils.getObjectMapper().createArrayNode().add(data);
                total = 1;
            } else {
                throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_EXECUTION_FAILED);
            }
            if (rowsNode.size() > maxRows) {
                throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_ROW_LIMIT);
            }
            List<Map<String, Object>> rows = JsonUtils.convertObject(rowsNode, new TypeReference<>() {});
            return new BpmFormDataSourceQueryResult(rows, total, version);
        } catch (cn.iocoder.yudao.framework.common.exception.ServiceException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_EXECUTION_FAILED);
        }
    }

    private static int bounded(Integer configured, int globalLimit) {
        int safeGlobal = Math.max(1, globalLimit);
        return configured == null ? safeGlobal : Math.max(1, Math.min(configured, safeGlobal));
    }

    private static Map<String, Object> parseConfig(String json) {
        Map<String, Object> result = JsonUtils.parseObjectQuietly(json, new TypeReference<>() {});
        if (result == null) {
            throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_CONFIG_INVALID);
        }
        return result;
    }

    /**
     * Buffers an HTTP body up to a hard byte limit while it is still part of {@link HttpClient#send}.
     * Cancelling the subscription on overflow prevents an unbounded response from occupying memory or a worker thread.
     */
    static final class BoundedByteArraySubscriber implements HttpResponse.BodySubscriber<byte[]> {

        private final int maxBytes;
        private final ByteArrayOutputStream output;
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;
        private int receivedBytes;

        BoundedByteArraySubscriber(int maxBytes) {
            if (maxBytes <= 0) {
                throw new IllegalArgumentException("maxBytes must be positive");
            }
            this.maxBytes = maxBytes;
            this.output = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription newSubscription) {
            Objects.requireNonNull(newSubscription, "subscription");
            if (subscription != null) {
                newSubscription.cancel();
                return;
            }
            subscription = newSubscription;
            newSubscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (body.isDone()) {
                return;
            }
            for (ByteBuffer buffer : buffers) {
                int remaining = buffer.remaining();
                if (remaining > maxBytes - receivedBytes) {
                    subscription.cancel();
                    body.completeExceptionally(new IOException("platform API response exceeds byte limit"));
                    return;
                }
                byte[] chunk = new byte[remaining];
                buffer.get(chunk);
                output.writeBytes(chunk);
                receivedBytes += remaining;
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete(output.toByteArray());
        }
    }

}
