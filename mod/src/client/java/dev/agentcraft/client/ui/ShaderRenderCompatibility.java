package dev.agentcraft.client.ui;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

/** Select the vanilla render types while Iris owns the world shader pipeline. */
public final class ShaderRenderCompatibility {
	private static final boolean IRIS_LOADED = FabricLoader.getInstance().isModLoaded("iris");
	private static volatile boolean shaderPackActive = IRIS_LOADED && queryIris();

	static {
		if (IRIS_LOADED) ClientTickEvents.START_CLIENT_TICK.register(client -> shaderPackActive = queryIris());
	}

	private ShaderRenderCompatibility() { }

	public static boolean useVanillaWorldPipelines() { return shaderPackActive; }

	private static boolean queryIris() {
		try {
			Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
			return Boolean.TRUE.equals(iris.getMethod("isPackInUseQuick").invoke(null));
		} catch (ReflectiveOperationException | LinkageError e) {
			// If Iris is installed but its optional API is unavailable, prefer the compatible path.
			return IRIS_LOADED;
		}
	}
}
