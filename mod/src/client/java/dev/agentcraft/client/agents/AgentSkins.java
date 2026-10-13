package dev.agentcraft.client.agents;

import dev.agentcraft.AgentCraft;
import dev.agentcraft.Cast;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Agent skins: {@code assets/agentcraft/textures/entity/agent/<skin>.png} (64x64, both layers) with
 * the arm model from cast.json ({@code slim} = 3 px arms). Unknown skins use the Shadow Scout look.
 */
public final class AgentSkins {
	private static final Map<String, PlayerSkin> CACHE = new HashMap<>();

	private AgentSkins() {
	}

	public static PlayerSkin get(String agentId, String skinId) {
		return CACHE.computeIfAbsent(agentId + "|" + skinId, k -> create(agentId, skinId));
	}

	private static PlayerSkin create(String agentId, String skinId) {
		Identifier asset = AgentCraft.id("entity/agent/" + skinId);
		ClientAsset.ResourceTexture tex = new ClientAsset.ResourceTexture(asset);
		Cast.Member m = Cast.get(agentId);
		if (m == null) {
			m = Cast.get(skinId);
		}
		boolean exists = Minecraft.getInstance().getResourceManager().getResource(tex.texturePath()).isPresent();
		if (!exists) {
			AgentCraft.LOGGER.warn("No skin texture {} for agent {}; using Shadow Scout", tex.texturePath(), agentId);
			tex = new ClientAsset.ResourceTexture(AgentCraft.id("entity/agent/spartan"));
			skinId = "spartan";
		}
		return PlayerSkin.insecure(tex, null, null, dev.agentcraft.NpcLooks.slim(m == null ? agentId : m.id(), skinId) ? PlayerModelType.SLIM : PlayerModelType.WIDE);
	}

	public static void clear() {
		CACHE.clear();
	}
}
