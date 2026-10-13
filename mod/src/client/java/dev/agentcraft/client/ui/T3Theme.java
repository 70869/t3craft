package dev.agentcraft.client.ui;

import com.google.gson.JsonParser;
import dev.agentcraft.AgentCraft;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared native-surface palette, adapted from the pinned T3 Code built-in colors (MIT). */
public final class T3Theme {
	public record Preset(String id, String label, Map<String, Integer> light, Map<String, Integer> dark) {}
	private static final List<Preset> PRESETS = load();
	private static Map<String, Integer> active = PRESETS.getFirst().light();
	private static String selection = "";
	private static boolean dark;
	private static long revision;
	private T3Theme() {}
	public static List<Preset> presets() { return PRESETS; }
	public static String normalize(String id) { return PRESETS.stream().anyMatch(p -> p.id().equals(id)) ? id : "t3-code"; }
	public static String label(String id) { return PRESETS.stream().filter(p -> p.id().equals(normalize(id))).findFirst().orElseThrow().label(); }
	public static boolean validAccent(String value) { return value == null || value.isBlank() || value.matches("#[0-9a-fA-F]{6}"); }
	public static boolean dark() { return dark; }
	public static long revision() { return revision; }
	public static int color(String role) { return active.getOrDefault(role, active.get("text")); }
	public static void apply(String id, boolean appearance, String accent) {
		id = normalize(id); accent = validAccent(accent) && accent != null ? accent.strip().toUpperCase(java.util.Locale.ROOT) : "";
		String key = id + "|" + appearance + "|" + accent;
		if (key.equals(selection)) return;
		selection = key; dark = appearance;
		String selected = id;
		var preset = PRESETS.stream().filter(p -> p.id().equals(selected)).findFirst().orElseThrow();
		var colors = new LinkedHashMap<>(appearance ? preset.dark() : preset.light());
		if (!accent.isBlank()) {
			int value = parse(accent);
			colors.put("accent", value); colors.put("focus", value); colors.put("messageAction", value);
			colors.put("accentForeground", contrast(value, 0xFFFFFFFF) >= contrast(value, 0xFF000000) ? 0xFFFFFFFF : 0xFF000000);
			colors.put("messageActionForeground", colors.get("accentForeground"));
			colors.put("messageActionHover", mix(value, colors.get("text"), .12));
			colors.put("accentSurface", mix(colors.get("canvas"), value, .15));
			colors.put("accentSurfaceForeground", colors.get("text"));
			colors.put("sidebarRowSelected", colors.get("accentSurface"));
		}
		colors.put("accentText", readable(colors.get("focus"), colors.get("canvas")));
		colors.put("goodText", readable(appearance ? 0xFFA6C69B : 0xFF455D47, colors.get("canvas")));
		colors.put("goodSurface", mix(colors.get("canvas"), colors.get("goodText"), .13));
		colors.put("errorText", readable(colors.get("errorForeground"), colors.get("canvas")));
		active = Map.copyOf(colors); revision++;
	}
	public static Integer token(String path) {
		String role = switch (path) {
			case "paper.text", "paper.text_strong", "palette.ui.text", "monitor.text", "monitor.tool_arg", "monitor_dark.text", "monitor_dark.tool_arg", "ink_ui.text" -> "text";
			case "paper.muted", "paper.disabled", "palette.ui.text_muted", "monitor.muted", "monitor.diff_ctx", "monitor_dark.muted", "monitor_dark.diff_ctx", "ink_ui.activity" -> "textMuted";
			case "palette.ui.panel", "board.label" -> "canvas";
			case "palette.ui.panel_hi" -> "surfaceRaised";
			case "palette.ui.inset", "palette.ui.panel_shade", "board.chip", "monitor.scanline", "monitor_dark.scanline", "paper.code_bg" -> "codeBackground";
			case "palette.ui.border", "palette.ui.edge", "board.rule", "monitor.rule", "monitor_dark.rule" -> "border";
			case "paper.link", "paper.hunk", "paper.path", "monitor.tool", "monitor.path", "monitor.diff_hunk", "monitor.attention", "monitor_dark.tool", "monitor_dark.path", "monitor_dark.diff_hunk", "monitor_dark.attention" -> "accentText";
			case "paper.add_fg", "monitor.result", "monitor.diff_add", "monitor_dark.result", "monitor_dark.diff_add" -> "goodText";
			case "paper.add_bg", "monitor.diff_add_bg", "monitor_dark.diff_add_bg" -> "goodSurface";
			case "paper.del_fg", "monitor.error", "monitor.diff_del", "monitor_dark.error", "monitor_dark.diff_del" -> "errorText";
			case "paper.del_bg", "monitor.diff_del_bg", "monitor_dark.diff_del_bg" -> "errorSurface";
			case "monitor.bg", "monitor_dark.bg", "monitor_dark.bg_top", "monitor_dark.header_bg" -> "surface";
			default -> null;
		};
		return role == null ? null : color(role);
	}
	public static int mix(int a, int b, double amount) {
		int out = 0xFF000000;
		for (int shift : new int[]{16, 8, 0}) out |= (int)Math.round(((a >> shift) & 255) * (1 - amount) + ((b >> shift) & 255) * amount) << shift;
		return out;
	}
	private static double luminance(int c) {
		double out = 0; double[] weights = {.2126, .7152, .0722}; int i = 0;
		for (int shift : new int[]{16, 8, 0}) { double x = ((c >> shift) & 255) / 255.; out += weights[i++] * (x <= .04045 ? x / 12.92 : Math.pow((x + .055) / 1.055, 2.4)); }
		return out;
	}
	public static double contrast(int a, int b) { double x = luminance(a), y = luminance(b); return (Math.max(x, y) + .05) / (Math.min(x, y) + .05); }
	private static int readable(int c, int bg) {
		int end = luminance(bg) > .4 ? 0xFF000000 : 0xFFFFFFFF;
		for (int i = 0; i <= 100; i++) { int result = mix(c, end, i / 100.); if (contrast(result, bg) >= 4.5) return result; }
		return end;
	}
	private static int parse(String hex) { return 0xFF000000 | Integer.parseInt(hex.substring(1), 16); }
	private static Map<String, Integer> read(com.google.gson.JsonObject json) {
		var out = new LinkedHashMap<String, Integer>(); json.entrySet().forEach(e -> out.put(e.getKey(), parse(e.getValue().getAsString()))); return Map.copyOf(out);
	}
	private static List<Preset> load() {
		try (var in = T3Theme.class.getResourceAsStream("/assets/agentcraft/t3-themes.json")) {
			if (in == null) throw new IllegalStateException("Missing T3 theme snapshot");
			var out = new java.util.ArrayList<Preset>();
			for (var value : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("themes")) {
				var p = value.getAsJsonObject(); out.add(new Preset(p.get("id").getAsString(), p.get("label").getAsString(), read(p.getAsJsonObject("light")), read(p.getAsJsonObject("dark"))));
			}
			if (out.isEmpty() || out.stream().noneMatch(p -> p.id().equals("t3-code"))) throw new IllegalStateException("Invalid T3 theme snapshot");
			for (var p : out) for (var colors : List.of(p.light(), p.dark()))
				if (!colors.keySet().containsAll(List.of("canvas", "text", "focus", "accent", "accentForeground", "errorForeground"))) throw new IllegalStateException("Incomplete T3 palette");
			return List.copyOf(out);
		} catch (Exception e) {
			AgentCraft.LOGGER.warn("Could not load T3 palettes", e);
			var fallback = new LinkedHashMap<String, Integer>();
			for (String role : List.of("canvas", "surface", "surfaceRaised", "codeBackground", "toolbarControl", "toolbarControlHover", "input", "accentSurface", "errorSurface", "sidebarRowSelected")) fallback.put(role, 0xFFFCFCFC);
			for (String role : List.of("text", "accentSurfaceForeground", "errorForeground")) fallback.put(role, 0xFF27272A);
			fallback.put("textMuted", 0xFF71717B); fallback.put("border", 0xFFD4D4D8);
			for (String role : List.of("accent", "focus", "messageAction", "messageActionHover")) fallback.put(role, 0xFF1B4ED8);
			fallback.put("accentForeground", 0xFFFFFFFF); fallback.put("messageActionForeground", 0xFFFFFFFF);
			return List.of(new Preset("t3-code", "T3 Code", fallback, fallback));
		}
	}
}
