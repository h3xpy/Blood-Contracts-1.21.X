package dev.bloodcontracts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bloodcontracts.core.BountyError;
import dev.bloodcontracts.core.BountyState;
import dev.bloodcontracts.core.BountyType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Every translation key the mod uses exists in fr_fr and en_us, with the same placeholders. */
class TranslationKeysTest {
    private static final Pattern LITERAL = Pattern.compile("\"((?:key\\.categories\\.|key\\.)?bloodcontracts\\.[a-z0-9_.]*[a-z0-9_])\"");
    private static final Pattern TRANSLATE = Pattern.compile("\"translate\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PLACEHOLDER = Pattern.compile("%(\\d+\\$)?s");

    private static Path project() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("src/main/java/dev/bloodcontracts"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("project directory not found from " + Path.of("").toAbsolutePath());
        }
        return dir;
    }

    private static Set<String> usedKeys(Path root) throws IOException {
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> files = Files.walk(root.resolve("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = LITERAL.matcher(Files.readString(f, StandardCharsets.UTF_8));
                while (m.find()) {
                    keys.add(m.group(1));
                }
            }
        }
        try (Stream<Path> files = Files.walk(root.resolve("src/main/resources/data"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                Matcher m = TRANSLATE.matcher(Files.readString(f, StandardCharsets.UTF_8));
                while (m.find()) {
                    keys.add(m.group(1));
                }
            }
        }
        // Keys built from enum names.
        for (BountyError e : BountyError.values()) {
            keys.add("bloodcontracts.error." + e.name().toLowerCase(Locale.ROOT));
        }
        for (BountyState s : BountyState.values()) {
            keys.add("bloodcontracts.state." + s.name().toLowerCase(Locale.ROOT));
        }
        for (BountyType t : BountyType.values()) {
            keys.add("bloodcontracts.type." + t.name().toLowerCase(Locale.ROOT));
        }
        // The mod id itself and the config file name are not translation keys.
        keys.remove("bloodcontracts.toml");
        return keys;
    }

    private static JsonObject lang(Path root, String name) throws IOException {
        return JsonParser.parseString(Files.readString(root.resolve("src/main/resources/assets/bloodcontracts/lang/" + name + ".json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static long placeholders(String text) {
        return PLACEHOLDER.matcher(text).results().count();
    }

    @Test
    void everyUsedKeyIsTranslatedInBothLanguages() throws IOException {
        Path root = project();
        Set<String> used = usedKeys(root);
        assertTrue(used.size() > 100, "key scan found too little: " + used.size());
        for (String name : List.of("fr_fr", "en_us")) {
            JsonObject lang = lang(root, name);
            List<String> missing = new ArrayList<>();
            for (String key : used) {
                if (!lang.has(key)) {
                    missing.add(key);
                }
            }
            assertTrue(missing.isEmpty(), name + " is missing " + missing);
        }
    }

    @Test
    void bothLanguagesHaveTheSameKeysAndPlaceholders() throws IOException {
        Path root = project();
        JsonObject fr = lang(root, "fr_fr");
        JsonObject en = lang(root, "en_us");
        assertEquals(new TreeSet<>(fr.keySet()), new TreeSet<>(en.keySet()), "fr_fr and en_us differ");
        for (String key : fr.keySet()) {
            assertEquals(placeholders(fr.get(key).getAsString()), placeholders(en.get(key).getAsString()), "placeholders differ for " + key);
        }
    }
}
