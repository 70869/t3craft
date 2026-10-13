package dev.agentcraft.client.world;

import dev.agentcraft.block.entity.StationBlockEntity;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Right-click on a station block, handled on the client (e.g. open the console screen on a terminal,
 * the library on an archive, the decision GUI on the podium). One handler per block; the click is
 * consumed client-side and not sent to the server. Sneak-right-click with an item still places
 * blocks normally, except on monitors and the atrium projector, where it opens display controls.
 *
 * <pre>
 * StationInteractions.onUse(ModBlocks.CONSOLE_TERMINAL, (player, pos, state, be) -> Minecraft.getInstance().gui.setScreen(new ConsoleScreen()));
 * </pre>
 */
public final class StationInteractions {
	@FunctionalInterface
	public interface Handler {
		void use(Player player, BlockPos pos, BlockState state, @Nullable StationBlockEntity be);
	}

	private static final Map<Block, Handler> HANDLERS = new ConcurrentHashMap<>();
	private static boolean registered;

	private StationInteractions() {
	}

	public static synchronized void onUse(Block block, Handler handler) {
		HANDLERS.put(block, handler);
		if (!registered) {
			registered = true;
			UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
				if (!level.isClientSide()) {
					return InteractionResult.PASS;
				}
				BlockPos pos = hit.getBlockPos();
				BlockState state = level.getBlockState(pos);
				boolean monitor = state.getBlock() instanceof dev.agentcraft.block.MonitorBlock;
				boolean statusLamp = state.getBlock() instanceof dev.agentcraft.block.StatusLampBlock;
				boolean displayWithItem = monitor || statusLamp
					&& level.getBlockEntity(pos) instanceof dev.agentcraft.block.entity.StatusLampBlockEntity lamp
					&& "goal:atrium".equals(lamp.binding());
				boolean holdingItem = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
				// Shift-use opens display controls on monitors even while holding a block. The atrium
				// projector has the same control; other stations retain normal item placement behavior.
				if (player.isSecondaryUseActive()
					&& ((!monitor && !statusLamp) || (holdingItem && !displayWithItem))) return InteractionResult.PASS;
				Handler h = HANDLERS.get(state.getBlock());
				if (h == null) {
					return InteractionResult.PASS;
				}
				h.use(player, pos, state, level.getBlockEntity(pos) instanceof StationBlockEntity be ? be : null);
				return InteractionResult.FAIL;
			});
		}
	}
}
