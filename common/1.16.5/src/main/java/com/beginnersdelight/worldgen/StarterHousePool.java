package com.beginnersdelight.worldgen;

import com.beginnersdelight.BeginnersDelight;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Random;
import java.util.List;
import java.util.Optional;
import java.lang.reflect.Field;

/**
 * Loads the data-pack-defined starter house candidate pool.
 */
public final class StarterHousePool {

    public static final int SCHEMA_VERSION = 1;
    private static final String CURRENT_MINECRAFT_VERSION = "1.16.5";
    private static final ResourceLocation RESOURCE = new ResourceLocation(
            BeginnersDelight.MOD_ID, "beginners_delight/starter_house_pool.json");
    private static final List<Entry> FALLBACK_ENTRIES = Arrays.asList(
            new Entry(new ResourceLocation(BeginnersDelight.MOD_ID, "starter_house1"), 1, LootMode.STARTER),
            new Entry(new ResourceLocation(BeginnersDelight.MOD_ID, "starter_house3"), 1, LootMode.STARTER)
    );

    private StarterHousePool() {}

    public static Optional<Entry> select(MinecraftServer server, Random random) {
        List<Entry> entries = loadMergedEntries(server);

        int totalWeight = entries.stream().mapToInt(Entry::weight).sum();
        int selectedWeight = random.nextInt(totalWeight);
        for (Entry entry : entries) {
            selectedWeight -= entry.weight();
            if (selectedWeight < 0) {
                return Optional.of(entry);
            }
        }
        return Optional.of(entries.get(entries.size() - 1));
    }

    /**
     * Returns every entry currently registered in the pool (datapack-merged, with
     * built-in fallbacks applied), without picking one. Used to inspect the whole
     * candidate set, e.g. to size plots to whatever structures are actually loaded.
     */
    public static List<Entry> allEntries(MinecraftServer server) {
        return loadMergedEntries(server);
    }

    private static List<Entry> loadMergedEntries(MinecraftServer server) {
        List<Entry> entries = new ArrayList<>();
        ResourceManager resourceManager = findResourceManager(server);
        if (resourceManager != null) {
            try {
                for (Resource resource : resourceManager.getResources(RESOURCE)) {
                    applyResource(resource, entries);
                }
            } catch (IOException exception) {
                BeginnersDelight.LOGGER.error("Could not enumerate starter house pool resources", exception);
            }
        }
        if (entries.isEmpty()) {
            BeginnersDelight.LOGGER.warn("Starter house pool is empty; using built-in defaults");
            entries.addAll(FALLBACK_ENTRIES);
        }
        return entries;
    }

    private static ResourceManager findResourceManager(MinecraftServer server) {
        for (Field field : MinecraftServer.class.getDeclaredFields()) {
            if (!ServerResources.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(server);
                if (value instanceof ServerResources) {
                    return ((ServerResources) value).getResourceManager();
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        BeginnersDelight.LOGGER.error("Could not access the 1.16.5 server resource manager");
        return null;
    }

    private static void applyResource(Resource resource, List<Entry> entries) {
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            JsonElement parsed = new JsonParser().parse(reader);
            if (!parsed.isJsonObject()) {
                BeginnersDelight.LOGGER.error("Starter house pool must be a JSON object");
                return;
            }
            applyObject(parsed.getAsJsonObject(), "data pack", entries);
        } catch (IOException | RuntimeException exception) {
            BeginnersDelight.LOGGER.error("Could not read starter house pool", exception);
        }
    }

    private static void applyObject(JsonObject pool, String source, List<Entry> entries) {
        if (!pool.has("schema_version") || !pool.get("schema_version").isJsonPrimitive()
                || !pool.get("schema_version").getAsJsonPrimitive().isNumber()) {
            BeginnersDelight.LOGGER.error("Starter house pool in {} is missing integer schema_version", source);
            return;
        }
        if (pool.get("schema_version").getAsInt() != SCHEMA_VERSION) {
            BeginnersDelight.LOGGER.error("Starter house pool in {} uses unsupported schema_version {}", source,
                    pool.get("schema_version").getAsInt());
            return;
        }
        if (pool.has("replace") && pool.get("replace").isJsonPrimitive()
                && pool.get("replace").getAsBoolean()) {
            entries.clear();
        }
        if (pool.has("remove") && pool.get("remove").isJsonArray()) {
            for (JsonElement removed : pool.getAsJsonArray("remove")) {
                if (!removed.isJsonPrimitive()) continue;
                ResourceLocation template = ResourceLocation.tryParse(removed.getAsString());
                if (template == null) {
                    BeginnersDelight.LOGGER.error("Invalid starter house template '{}' in {}", removed, source);
                    continue;
                }
                entries.removeIf(entry -> entry.template().equals(template));
            }
        }
        if (pool.has("entries") && pool.get("entries").isJsonArray()) {
            for (JsonElement element : pool.getAsJsonArray("entries")) {
                if (!element.isJsonObject()) {
                    BeginnersDelight.LOGGER.error("Invalid starter house entry in {}", source);
                    continue;
                }
                parseEntry(element.getAsJsonObject(), source).ifPresent(entries::add);
            }
        }
    }

    private static Optional<Entry> parseEntry(JsonObject object, String source) {
        if (!object.has("template") || !object.get("template").isJsonPrimitive()) {
            BeginnersDelight.LOGGER.error("Starter house entry in {} is missing template", source);
            return Optional.empty();
        }
        ResourceLocation template = ResourceLocation.tryParse(object.get("template").getAsString());
        if (template == null) {
            BeginnersDelight.LOGGER.error("Invalid starter house template '{}' in {}", object.get("template"), source);
            return Optional.empty();
        }
        int weight = object.has("weight") ? object.get("weight").getAsInt() : 1;
        if (weight <= 0) {
            BeginnersDelight.LOGGER.error("Starter house template '{}' in {} has non-positive weight", template, source);
            return Optional.empty();
        }
        LootMode lootMode = LootMode.PRESERVE;
        if (object.has("loot")) {
            if (!object.get("loot").isJsonPrimitive()) {
                BeginnersDelight.LOGGER.error("Starter house template '{}' in {} has invalid loot mode", template, source);
                return Optional.empty();
            }
            lootMode = LootMode.fromSerializedName(object.get("loot").getAsString());
            if (lootMode == null) {
                BeginnersDelight.LOGGER.error("Starter house template '{}' in {} has unknown loot mode", template, source);
                return Optional.empty();
            }
        }
        if (object.has("minimum_minecraft_version")) {
            if (!object.get("minimum_minecraft_version").isJsonPrimitive()
                    || !object.getAsJsonPrimitive("minimum_minecraft_version").isString()) {
                BeginnersDelight.LOGGER.error("Ignoring starter house pool entry from {}: minimum_minecraft_version must be a string",
                        source);
                return Optional.empty();
            }

            String minimumMinecraftVersion = object.get("minimum_minecraft_version").getAsString();
            if (!isMinecraftVersion(minimumMinecraftVersion)) {
                BeginnersDelight.LOGGER.error("Ignoring starter house pool entry from {}: minimum_minecraft_version '{}' is invalid",
                        source, minimumMinecraftVersion);
                return Optional.empty();
            }

            if (compareMinecraftVersions(CURRENT_MINECRAFT_VERSION, minimumMinecraftVersion) < 0) {
                BeginnersDelight.LOGGER.debug("Skipping starter house pool entry {} from {}: requires Minecraft {}+",
                        template, source, minimumMinecraftVersion);
                return Optional.empty();
            }
        }

        return Optional.of(new Entry(template, weight, lootMode));
    }

    private static boolean isMinecraftVersion(String version) {
        if (!version.matches("\\d+(\\.\\d+){1,2}")) {
            return false;
        }
        for (String part : version.split("\\.")) {
            try {
                Integer.parseInt(part);
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return true;
    }

    private static int compareMinecraftVersions(String first, String second) {
        String[] firstParts = first.split("\\.");
        String[] secondParts = second.split("\\.");
        int partCount = Math.max(firstParts.length, secondParts.length);
        for (int index = 0; index < partCount; index++) {
            int firstPart = index < firstParts.length ? Integer.parseInt(firstParts[index]) : 0;
            int secondPart = index < secondParts.length ? Integer.parseInt(secondParts[index]) : 0;
            if (firstPart != secondPart) {
                return Integer.compare(firstPart, secondPart);
            }
        }
        return 0;
    }

    public static final class Entry {
        private final ResourceLocation template;
        private final int weight;
        private final LootMode lootMode;
        public Entry(ResourceLocation template, int weight, LootMode lootMode) { this.template = template; this.weight = weight; this.lootMode = lootMode; }
        public ResourceLocation template() { return template; }
        public int weight() { return weight; }
        public LootMode lootMode() { return lootMode; }
    }

    private static ResourceLocation parse(String value) {
        try { return new ResourceLocation(value); } catch (IllegalArgumentException exception) { return null; }
    }

    public enum LootMode {
        PRESERVE("preserve"),
        STARTER("starter");

        private final String serializedName;

        LootMode(String serializedName) {
            this.serializedName = serializedName;
        }

        private static LootMode fromSerializedName(String value) {
            for (LootMode mode : values()) {
                if (mode.serializedName.equals(value)) return mode;
            }
            return null;
        }
    }
}
