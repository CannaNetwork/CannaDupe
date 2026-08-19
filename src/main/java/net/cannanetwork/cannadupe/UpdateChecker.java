package net.cannanetwork.cannadupe;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Checks GitHub Releases only; it never downloads or replaces files. */
public final class UpdateChecker {
    private static final URI LATEST_RELEASE = URI.create("https://api.github.com/repos/CannaNetwork/CannaDupe/releases/latest");
    private static final Pattern RELEASE_VERSION = Pattern.compile("\"tag_name\"\\s*:\\s*\"v([0-9]+(?:\\.[0-9]+)*)-mc-");
    private static final Pattern RELEASE_NOTES = Pattern.compile("\"body\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static volatile String pendingMessage;
    private static boolean started;

    private UpdateChecker() { }

    public static synchronized void checkOnce() {
        if (started) return;
        started = true;
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(LATEST_RELEASE)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "CannaDupe")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
                String body = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();
                String current = currentVersion();
                ReleaseInfo latest = latestRelease(body);
                if (latest != null && compareVersions(latest.version, current) > 0) {
                    pendingMessage = "CannaDupe update v" + latest.version + ": " + latest.notes
                        + " Download the matching Minecraft version from GitHub Releases.";
                }
            } catch (Exception ignored) {
                // Update checks must never affect startup or gameplay.
            }
        });
    }

    public static void notifyWhenPlayerReady() {
        String message = pendingMessage;
        Minecraft client = Minecraft.getInstance();
        if (message != null && client.player != null) {
            pendingMessage = null;
            client.player.sendSystemMessage(Component.literal(message));
        }
    }

    private static String currentVersion() {
        return FabricLoader.getInstance().getModContainer("cannadupe")
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("0.0.0")
            .split("\\+", 2)[0];
    }

    private static ReleaseInfo latestRelease(String release) {
        Matcher version = RELEASE_VERSION.matcher(release);
        if (!version.find()) return null;
        Matcher body = RELEASE_NOTES.matcher(release);
        String notes = body.find() ? body.group(1).replace("\\n", " ").replace("\\r", " ") : "See the release notes.";
        notes = notes.replaceAll("\\s+", " ").strip();
        if (notes.length() > 180) notes = notes.substring(0, 177) + "...";
        return new ReleaseInfo(version.group(1), notes.isEmpty() ? "See the release notes." : notes);
    }

    private static int compareVersions(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int ai = i < a.length ? Integer.parseInt(a[i]) : 0;
            int bi = i < b.length ? Integer.parseInt(b[i]) : 0;
            if (ai != bi) return Integer.compare(ai, bi);
        }
        return 0;
    }

    private record ReleaseInfo(String version, String notes) { }
}
