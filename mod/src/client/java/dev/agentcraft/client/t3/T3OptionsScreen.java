package dev.agentcraft.client.t3;

import com.google.gson.*;
import java.util.ArrayList;
import java.util.List;

final class T3OptionsScreen extends T3FormScreen {
	private JsonObject selection;
	private final List<String> ids = new ArrayList<>();
	private final List<Boolean> booleans = new ArrayList<>();
	private boolean loaded;
	T3OptionsScreen(T3CraftClient mod, T3State.ThreadRow row) {
		super("Model options", mod, row);
		action("Save options", () -> {
			JsonObject picked = selection.deepCopy(); JsonArray options = T3Protocol.array(picked, "options").deepCopy();
			for (int i = 0; i < ids.size(); i++) {
				String id = ids.get(i); String value = fields.get(i).value;
				for (int n = options.size() - 1; n >= 0; n--) if (id.equals(text(options.get(n).getAsJsonObject(), "id"))) options.remove(n);
				if (!value.equals("Inherit")) { JsonObject option = values("id", id); if (booleans.get(i)) option.addProperty("value", value.equals("On")); else option.addProperty("value", value); options.add(option); }
			}
			picked.add("options", options);
			request(() -> { var current = api.thread(row.id(), 4); JsonObject currentModel = T3Protocol.object(current, "modelSelection"); if (currentModel == null || !text(currentModel, "instanceId").equals(text(picked, "instanceId")) || !text(currentModel, "model").equals(text(picked, "model"))) throw new java.io.IOException("The model changed. Reopen options before saving."); JsonObject f = new JsonObject(); f.add("modelSelection", picked); api.operate("thread.model-selection.set", row.id(), f); return "Options saved for " + text(picked, "model") + "."; });
		});
	}
	@Override protected void init() {
		super.init(); if (loaded) return; loaded = true;
		final List<JsonObject> descriptors = new ArrayList<>();
		request(() -> {
			selection = T3Protocol.object(api.thread(row.id(), 4), "modelSelection").deepCopy();
			try (var socket = api.openSocket(reason -> {})) {
				for (var provider : api.providers(socket)) if (provider.instanceId().equals(text(selection, "instanceId"))) for (var model : provider.models()) if (model.slug().equals(text(selection, "model"))) for (var d : model.options()) descriptors.add(d.getAsJsonObject());
			}
			return descriptors.isEmpty() ? "This model exposes no editable options." : "Choose reasoning effort and the options this provider supports. Inherit uses its default.";
		}, () -> {
			for (var d : descriptors) {
				String id = text(d, "id"), value = "Inherit"; boolean bool = text(d, "type").equals("boolean");
				for (var option : T3Protocol.array(selection, "options")) if (id.equals(text(option.getAsJsonObject(), "id"))) value = bool ? (option.getAsJsonObject().get("value").getAsBoolean() ? "On" : "Off") : text(option.getAsJsonObject(), "value");
				List<String> choices = new ArrayList<>(List.of("Inherit")); if (bool) choices.addAll(List.of("On", "Off")); else for (var choice : T3Protocol.array(d, "options")) choices.add(text(choice.getAsJsonObject(), "id"));
				if (!choices.contains(value)) choices.add(value);
				fields.add(new Field(text(d, "label"), value, choices)); ids.add(id); booleans.add(bool);
			}
			rebuildWidgets();
		});
	}
}
