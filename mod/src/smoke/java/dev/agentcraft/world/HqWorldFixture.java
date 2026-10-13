package dev.agentcraft.world;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/** Builds a credential-free copy of the reviewed release world with a renamed Minecraft title. */
public final class HqWorldFixture {
	private static final String PREFIX = "minecraft/saves/AgentCraft HQ/";

	public static void main(String[] args) throws Exception {
		if (args.length != 2) throw new IllegalArgumentException("Supply a reviewed release ZIP and a new isolated game directory");
		Path archivePath = Path.of(args[0]).toRealPath();
		Path game = Path.of(args[1]).toAbsolutePath().normalize();
		if (Files.exists(game)) throw new IOException("Fixture destination already exists; refusing to overwrite it");
		Path world = game.resolve("saves/AgentCraft HQ");
		try (var archive = new ZipFile(archivePath.toFile())) {
			var entries = archive.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (entry.isDirectory()) continue;
				String relative = entry.getName().startsWith(PREFIX)
					? entry.getName().substring(PREFIX.length()) : entry.getName();
				Path target = world.resolve(relative).normalize();
				if (!target.startsWith(world) || relative.isBlank()) throw new IOException("Unsafe world template entry");
				Files.createDirectories(target.getParent());
				try (var input = archive.getInputStream(entry)) { Files.copy(input, target); }
			}
		}
		Path levelDat = world.resolve("level.dat");
		CompoundTag root = NbtIo.readCompressed(levelDat, NbtAccounter.unlimitedHeap());
		CompoundTag data = root.getCompoundOrEmpty("Data");
		data.putString("LevelName", "T3 HQ");
		NbtIo.writeCompressed(root, levelDat);
		if (!Files.isRegularFile(world.resolve("agentcraft-world.json"))
			|| !Files.isRegularFile(world.resolve("agentcraft-anchors.json"))) throw new IOException("Reviewed campus template is incomplete");
		System.out.println("Prepared clean renamed-HQ fixture at " + game);
	}
}
