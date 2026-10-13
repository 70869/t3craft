package dev.agentcraft.world;

import java.nio.file.Files;
import java.nio.file.Path;

/** A renamed saved HQ is still recognized; unrelated and corrupt marker files are not. */
public final class HqWorldChecks {
	private static void check(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	public static void run() throws Exception {
		Path root = Files.createTempDirectory("agentcraft-world-identity-");
		Path marker = root.resolve("agentcraft-world.json");
		try {
			check(HqWorld.isHq("AgentCraft HQ", root), "The canonical HQ name remains recognized");
			check(!HqWorld.isHq("T3 HQ", root), "An unrelated world without an HQ marker stays untouched");
			Files.writeString(marker, "{\"createdBy\":\"agentcraft\"}");
			check(HqWorld.isHq("T3 HQ", root), "A renamed saved HQ is recognized by its marker");
			Files.writeString(marker, "{\"createdBy\":\"other\"}");
			check(!HqWorld.isHq("T3 HQ", root), "A marker from another world is not accepted");
			Files.writeString(marker, "not json");
			check(!HqWorld.isHq("T3 HQ", root), "A corrupt marker does not classify another world as HQ");
		} finally {
			Files.deleteIfExists(marker);
			Files.deleteIfExists(root);
		}
		System.out.println("PASS HQ identity survives a renamed world; unrelated/corrupt markers are rejected");
	}
}
