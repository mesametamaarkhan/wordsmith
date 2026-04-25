import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

public class Main {
    private static final Map<String, List<String>> SEED_DATA = Map.of(
        "nouns", List.of(
            "cloud", "elephant", "go language", "laptop", "container", "microservice",
            "turtle", "whale", "gopher", "server", "bicycle", "viking",
            "mermaid", "fjord", "lego", "smorrebrod"
        ),
        "verbs", List.of(
            "will drink", "smashes", "eats", "walks toward", "loves",
            "helps", "pushes", "debugs", "invites", "hides", "will ship"
        ),
        "adjectives", List.of(
            "the exquisite", "a pink", "the red", "the serverless", "a broken",
            "a shiny", "the pretty", "the impressive", "an awesome", "the famous",
            "a gigantic", "the glorious", "the nordic", "the welcoming"
        )
    );

    public static void main(String[] args) throws Exception {
        String redisHost = envOrDefault("REDIS_HOST", "redis");
        int redisPort = envAsInt("REDIS_PORT", 6379);
        int servicePort = envAsInt("SERVICE_PORT", 8080);

        JedisPool pool = new JedisPool(poolConfig(), redisHost, redisPort);
        waitForRedis(pool);
        seedRedis(pool);

        HttpServer server = HttpServer.create(new InetSocketAddress(servicePort), 0);
        server.createContext("/noun", handler(pool, "nouns"));
        server.createContext("/verb", handler(pool, "verbs"));
        server.createContext("/adjective", handler(pool, "adjectives"));
        server.start();

        System.out.printf("Words API listening on port %d and using Redis %s:%d%n", servicePort, redisHost, redisPort);
    }

    private static JedisPoolConfig poolConfig() {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(8);
        config.setMaxIdle(8);
        config.setMinIdle(1);
        return config;
    }

    private static void waitForRedis(JedisPool pool) throws InterruptedException {
        int attempts = 0;

        while (attempts < 30) {
            try (Jedis jedis = pool.getResource()) {
                String response = jedis.ping();
                if ("PONG".equalsIgnoreCase(response)) {
                    return;
                }
            } catch (Exception ignored) {
            }

            attempts++;
            Thread.sleep(Duration.ofSeconds(2).toMillis());
        }

        throw new IllegalStateException("Redis did not become ready in time");
    }

    private static void seedRedis(JedisPool pool) {
        try (Jedis jedis = pool.getResource()) {
            for (Map.Entry<String, List<String>> entry : SEED_DATA.entrySet()) {
                if (!jedis.exists(entry.getKey())) {
                    jedis.sadd(entry.getKey(), entry.getValue().toArray(new String[0]));
                }
            }
        }
    }

    private static HttpHandler handler(JedisPool pool, String key) {
        return exchange -> {
            String selected = randomWord(pool, key);
            String payload = "{\"word\":\"" + escapeJson(selected) + "\"}";
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().add("content-type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().add("cache-control", "private, no-cache, no-store, must-revalidate, max-age=0");
            exchange.getResponseHeaders().add("pragma", "no-cache");
            exchange.getResponseHeaders().add("source", envOrDefault("HOSTNAME", "words"));
            exchange.sendResponseHeaders(200, bytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        };
    }

    private static String randomWord(JedisPool pool, String key) {
        try (Jedis jedis = pool.getResource()) {
            String word = jedis.srandmember(key);
            if (word == null) {
                throw new NoSuchElementException(key);
            }

            return word;
        }
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int envAsInt(String name, int fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            System.err.printf(Locale.ROOT, "Invalid integer for %s=%s, using %d%n", name, value, fallback);
            return fallback;
        }
    }
}
