package dev.agentcraft.hq;

import com.google.gson.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.*;

/** Exports a closed authored world, never the development fixture or the user's private profile. */
public final class WorldTemplateExport {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> DATA = Set.of("data/fabric/registry_entries.dat", "data/minecraft/game_rules.dat",
        "data/minecraft/weather.dat", "data/minecraft/world_clocks.dat", "data/minecraft/world_gen_settings.dat");
    private static final Set<String> PRIVATE = Set.of("Player", "player", "Owner", "owner", "UUID", "uuid",
        "LoveCause", "LastHurtBy", "Trusted", "TrustedUUIDs", "SkullOwner", "profile", "minecraft:profile",
        "Items", "items", "Inventory", "EnderItems", "CustomName", "custom_name", "minecraft:custom_name",
        "minecraft:written_book_content", "minecraft:writable_book_content", "minecraft:custom_data");

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || args[0].isBlank()) throw new IllegalArgumentException("Set -PworldSource to the closed native AgentCraft HQ save");
        Path source = Path.of(args[0]).toRealPath(), output = Path.of(args[1]).toAbsolutePath().normalize();
        if (output.startsWith(source) || source.startsWith(output)) throw new IOException("Export must be outside the source world");
        JsonObject anchors = JsonParser.parseString(Files.readString(source.resolve("agentcraft-anchors.json"))).getAsJsonObject();
        if (!anchors.get("layout").getAsString().equals("modern") || anchors.getAsJsonObject("anchors").size() < 100)
            throw new IOException("Expected the authored modern campus with its saved anchors");
        // Hold Minecraft's own lock throughout the verified snapshot; never stop a running game.
        try (var channel = FileChannel.open(source.resolve("session.lock"), StandardOpenOption.WRITE); var lock = channel.tryLock()) {
            if (lock == null) throw new IOException("World is running; save and close Minecraft first");
            List<Path> files;
            try (var stream = Files.walk(source)) { files = stream.filter(Files::isRegularFile)
                .filter(p -> allowed(source.relativize(p).toString().replace('\\', '/'))).sorted().toList(); }
            Map<String, String> before = hashes(source, files);
            var level = NbtIo.readCompressed(source.resolve("level.dat"), NbtAccounter.unlimitedHeap());
            var data = level.getCompoundOrEmpty("Data");
            if (!data.getCompoundOrEmpty("Version").getStringOr("Name", "").equals(args[2]))
                throw new IOException("World Minecraft version does not match the release target");
            Files.createDirectories(output);
            Path temporary = Files.createTempDirectory("t3craft-world-export-");
            try {
                Path raw = temporary.resolve("raw"), clean = temporary.resolve("clean");
                for (Path file : files) {
                    Path target = raw.resolve(source.relativize(file)); Files.createDirectories(target.getParent());
                    Files.copy(file, target);
                }
                if (!before.equals(hashes(raw, files.stream().map(p -> raw.resolve(source.relativize(p))).toList()))
                    || !before.equals(hashes(source, files))) throw new IOException("World changed while copying; export refused");
                int chunks = 0;
                for (Path file : files) {
                    String relative = source.relativize(file).toString().replace('\\', '/');
                    Path input = raw.resolve(relative), target = clean.resolve(relative); Files.createDirectories(target.getParent());
                    if (relative.endsWith(".mca")) chunks += region(input, target);
                    else if (relative.equals("level.dat") || DATA.contains(relative) || relative.endsWith("world_border.dat")) {
                        CompoundTag tag = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap()); scrub(tag);
                        if (relative.equals("level.dat")) {
                            var metadata = tag.getCompoundOrEmpty("Data");
                            metadata.putString("LevelName", "AgentCraft HQ"); metadata.putLong("LastPlayed", 0);
                            metadata.remove("WanderingTraderId"); metadata.remove("DragonFight");
                        }
                        NbtIo.writeCompressed(tag, target);
                    } else if (relative.equals("agentcraft-world.json")) {
                        Files.writeString(target, "{\"createdBy\":\"agentcraft\"}\n");
                    } else Files.copy(input, target);
                }
                if (!before.equals(hashes(source, files))) throw new IOException("Source changed during export");
                Map<String, String> cleanHashes;
                try (var stream = Files.walk(clean)) { cleanHashes = hashes(clean, stream.filter(Files::isRegularFile).sorted().toList()); }
                Path zip = temporary.resolve("world-template.zip");
                try (var archive = new ZipOutputStream(Files.newOutputStream(zip))) {
                    for (String name : cleanHashes.keySet()) {
                        ZipEntry entry = new ZipEntry(name); entry.setTime(0); archive.putNextEntry(entry);
                        Files.copy(clean.resolve(name), archive); archive.closeEntry();
                    }
                }
                var manifest = new JsonObject(); manifest.addProperty("schemaVersion", 1); manifest.addProperty("minecraft", args[2]);
                manifest.addProperty("worldName", "AgentCraft HQ"); manifest.addProperty("layout", "modern");
                manifest.addProperty("layoutRevision", anchors.get("revision").getAsLong());
                manifest.addProperty("capturedAt", Instant.now().toString());
                manifest.addProperty("sourceSavedAt", Files.getLastModifiedTime(source.resolve("level.dat")).toInstant().toString());
                manifest.addProperty("sourceSha256", hash(JSON.toJson(before).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                manifest.addProperty("sha256", hash(Files.readAllBytes(zip))); manifest.addProperty("chunks", chunks);
                manifest.add("files", JSON.toJsonTree(cleanHashes));
                manifest.addProperty("sanitization", "Player files, entities, inventories, profiles, scoreboards, scheduled events and personal configuration excluded; authored blocks/signs and station bindings retained");
                Files.move(zip, output.resolve("world-template.zip"), StandardCopyOption.REPLACE_EXISTING);
                Files.writeString(output.resolve("world-template.json"), JSON.toJson(manifest) + "\n");
                System.out.println("Exported latest saved modern campus: revision " + anchors.get("revision") + ", " + chunks + " chunks; source unchanged");
            } finally {
                // This is our freshly created temporary directory, never a computed source/profile path.
                try (var stream = Files.walk(temporary)) { for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
            }
        }
    }

    static boolean allowed(String path) {
        return Set.of("level.dat", "agentcraft-anchors.json", "agentcraft-hq-plan.dat", "agentcraft-world.json").contains(path)
            || DATA.contains(path) || path.matches("dimensions/minecraft/(overworld|the_nether|the_end)/data/minecraft/world_border\\.dat")
            || path.matches("dimensions/minecraft/(overworld|the_nether|the_end)/(region|poi)/r\\.-?\\d+\\.-?\\d+\\.mca");
    }
    private static Map<String, String> hashes(Path root, List<Path> files) throws Exception {
        Map<String, String> out = new TreeMap<>();
        for (Path file : files) {
            if (Files.isSymbolicLink(file)) throw new IOException("World symlink refused");
            out.put(root.relativize(file).toString().replace('\\', '/'), hash(Files.readAllBytes(file)));
        }
        return out;
    }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    static void scrub(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            for (String key : new ArrayList<>(compound.keySet())) {
                if (PRIVATE.contains(key)) compound.remove(key); else scrub(compound.get(key));
            }
        } else if (tag instanceof ListTag list) for (Tag child : list) scrub(child);
    }
    private static int region(Path source, Path target) throws IOException {
        String[] name = source.getFileName().toString().split("\\."); int rx = Integer.parseInt(name[1]), rz = Integer.parseInt(name[2]);
        var dimension = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
            net.minecraft.resources.Identifier.withDefaultNamespace("overworld"));
        var info = new RegionStorageInfo("release", dimension, "template");
        int count = 0;
        // Rewrite every live chunk into a fresh region, so old deleted payload bytes cannot ship.
        try (var input = new RegionFile(info, source, source.getParent(), false);
             var output = new RegionFile(info, target, target.getParent(), false)) {
            for (int z = 0; z < 32; z++) for (int x = 0; x < 32; x++) {
                var pos = new ChunkPos(rx * 32 + x, rz * 32 + z);
                try (var data = input.getChunkDataInputStream(pos)) {
                    if (data == null) continue;
                    var tag = NbtIo.read(data, NbtAccounter.unlimitedHeap()); scrub(tag);
                    try (var destination = output.getChunkDataOutputStream(pos)) { NbtIo.write(tag, (DataOutput) destination); }
                    count++;
                }
            }
        }
        return count;
    }
}
