package dev.agentcraft.client.t3;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import dev.agentcraft.client.console.TextFieldView;
import dev.agentcraft.client.console.TextKeys;
import dev.agentcraft.client.console.TextModel;
import dev.agentcraft.client.hud.UiBits;
import dev.agentcraft.client.ui.Kit;
import dev.agentcraft.client.ui.Panels;
import dev.agentcraft.client.ui.TextUtil;
import dev.agentcraft.client.ui.UiStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

/** The original studio's paper dialog, bound to one T3 thread and one question request. */
final class T3QuestionScreen extends Screen {
	private final T3CraftClient mod;
	private final String threadId, cast;
	private String requestId;
	private T3State.UserInput input;
	private final JsonObject answers = new JsonObject();
	private final Set<String> picked = new LinkedHashSet<>();
	private final Set<Integer> down = new HashSet<>();
	private final TextModel answer = new TextModel(8000);
	private final TextFieldView field = new TextFieldView();
	private final TextUtil.Scroll scroll = new TextUtil.Scroll();
	private final List<Choice> choices = new ArrayList<>();
	private int index, highlight, x, y, w, h, bodyY, bodyH, fieldY, fieldH, actionY;
	private boolean textFocused, pending, busy, sent, loaded, resetScroll = true;
	private String error;
	private long armedAt;
	private record Choice(int index, int y, int h) {}

	T3QuestionScreen(T3CraftClient mod, String threadId, String requestId, String cast) {
		super(Component.literal("Agent question"));
		this.mod = mod; this.threadId = threadId; this.requestId = requestId; this.cast = cast;
	}
	@Override protected void init() {
		mod.state().setPanelOpen(true);
		arm(); tick();
	}
	@Override public boolean isPauseScreen() { return false; }
	@Override public boolean isInputCaptured() { return textFocused; }
	@Override public void removed() { focusText(false); mod.state().setPanelOpen(false); }
	private void arm() {
		armedAt = Util.getMillis() + 350;
		for (int key = InputConstants.KEY_1; key <= InputConstants.KEY_9; key++) if (InputConstants.isKeyDown(key)) down.add(key);
		for (int key = InputConstants.KEY_NUMPAD1; key <= InputConstants.KEY_NUMPAD9; key++) if (InputConstants.isKeyDown(key)) down.add(key);
		for (int key : new int[]{InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER}) if (InputConstants.isKeyDown(key)) down.add(key);
	}
	@Override public void tick() {
		var snapshot = mod.state().snapshot();
		T3State.Focus detail = snapshot.focus() != null && snapshot.focus().threadId().equals(threadId) ? snapshot.focus() : null;
		if (detail == null) {
			var raw = mod.state().studioDetails().get(threadId);
			if (raw != null) detail = T3State.focus(raw);
		}
		if (detail == null) return;
		loaded = true;
		T3State.UserInput live = requestId == null ? detail.userInputs().stream().findFirst().orElse(null)
			: detail.userInputs().stream().filter(i -> i.requestId().equals(requestId)).findFirst().orElse(null);
		pending = live != null;
		if (live != null && input == null) {
			input = live; requestId = live.requestId();
			resetScroll = true;
			focusText(question().options().isEmpty());
			arm();
		}
		if (!canAnswer()) focusText(false);
	}
	private T3State.Question question() { return input == null ? null : input.questions().get(index); }
	private boolean canAnswer() { return input != null && pending && !busy && !sent && mod.state().online(threadId); }
	private void focusText(boolean focused) {
		if (textFocused == focused) return;
		textFocused = focused; answer.touch();
		if (minecraft != null) minecraft.onTextInputFocusChange(this, focused);
	}
	private TextFieldView.Style fieldStyle() {
		return new TextFieldView.Style(null, UiBits.ink(), question() != null && question().options().isEmpty() ? "Type your answer…" : "Or type your own answer…", null, null, UiBits.muted(), 2);
	}
	private void choose(int choice) {
		var q = question();
		if (!canAnswer() || Util.getMillis() < armedAt || choice < 0 || choice >= q.options().size()) return;
		focusText(false); highlight = choice;
		String value = q.options().get(choice).value();
		if (q.multiSelect()) { if (!picked.remove(value)) picked.add(value); }
		else record(new JsonPrimitive(value));
	}
	private void confirm() {
		if (!canAnswer() || Util.getMillis() < armedAt) return;
		var q = question();
		if (q.allowCustomAnswer() && !answer.value().isBlank()) record(new JsonPrimitive(answer.value().strip()));
		else if (q.multiSelect() && !picked.isEmpty()) {
			JsonArray values = new JsonArray(); picked.forEach(values::add); record(values);
		} else if (answers.size() == input.questions().size()) send();
		else if (!q.multiSelect() && !q.options().isEmpty()) choose(highlight);
	}
	private void record(JsonElement value) {
		answers.add(question().id(), value);
		if (index + 1 < input.questions().size()) {
			index++; picked.clear(); answer.clear(); highlight = 0; resetScroll = true; error = null;
			focusText(question().options().isEmpty()); arm();
		} else send();
	}
	private void send() {
		busy = true; error = null; focusText(false);
		mod.state().respond(threadId, requestId, null, answers.deepCopy(), failure -> minecraft.execute(() -> {
			busy = false; error = failure.getMessage() == null ? "Could not send your answer. Try again." : failure.getMessage();
		}), () -> minecraft.execute(() -> { busy = false; sent = true; }));
	}
	@Override public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == InputConstants.KEY_ESCAPE) { onClose(); return true; }
		boolean repeat = !down.add(key);
		if (repeat && (TextKeys.isEnter(event) || !textFocused && TextKeys.digit(event) > 0)) return true;
		if (!canAnswer()) return super.keyPressed(event);
		var q = question();
		if (key == InputConstants.KEY_TAB) { focusText(q.allowCustomAnswer() && !textFocused); return true; }
		if (TextKeys.isEnter(event)) {
			if (textFocused && event.hasShiftDown()) answer.insert("\n"); else confirm();
			return true;
		}
		if (textFocused) {
			if (TextKeys.handle(event, answer)) return true;
			if (key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN) {
				answer.vertical(font, TextFieldView.wrapWidth(font, answer, w - 24, fieldStyle()), key == InputConstants.KEY_UP ? -1 : 1, event.hasShiftDown());
				return true;
			}
		} else {
			int digit = TextKeys.digit(event);
			if (digit > 0) { choose(digit - 1); return true; }
			if ((key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN) && !q.options().isEmpty()) {
				highlight = Math.floorMod(highlight + (key == InputConstants.KEY_UP ? -1 : 1), q.options().size());
				Choice c = choices.stream().filter(item -> item.index() == highlight).findFirst().orElse(null);
				if (c != null) {
					if (c.y() < scroll.offset()) scroll.scrollBy(c.y() - scroll.offset());
					else if (c.y() + c.h() > scroll.offset() + bodyH) scroll.scrollBy(c.y() + c.h() - scroll.offset() - bodyH);
				}
				return true;
			}
			if (q.allowCustomAnswer() && TextKeys.isPaste(event)) { focusText(true); return TextKeys.handle(event, answer); }
		}
		return super.keyPressed(event);
	}
	@Override public boolean keyReleased(KeyEvent event) { down.remove(event.key()); return super.keyReleased(event); }
	@Override public boolean charTyped(CharacterEvent event) {
		if (!canAnswer() || !question().allowCustomAnswer()) return false;
		int cp = event.codepoint();
		if (!textFocused && cp >= '0' && cp <= '9') return true;
		if (cp >= 32) { focusText(true); answer.insert(event.codepointAsString()); }
		return true;
	}
	@Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
		double mx = event.x(), my = event.y();
		if (inside(mx, my, x + 12, actionY, 64, 20)) { onClose(); return true; }
		if (inside(mx, my, x + 82, actionY, 74, 20)) { mod.focus(threadId); mod.openPanel(); return true; }
		if (inside(mx, my, x + w - 104, actionY, 92, 20)) { confirm(); return true; }
		if (!canAnswer()) return true;
		if (fieldH > 0 && inside(mx, my, x + 12, fieldY, w - 24, fieldH)) {
			focusText(true); answer.moveTo(field.hit(font, answer, x + 12, fieldY, w - 24, fieldStyle(), mx, my), false); return true;
		}
		if (inside(mx, my, x + 12, bodyY, w - 24, bodyH)) for (Choice c : choices) {
			if (inside(mx, my, x + 14, bodyY + c.y() - scroll.offset(), w - 34, c.h())) { choose(c.index()); return true; }
		}
		focusText(false); return true;
	}
	private static boolean inside(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
	JsonObject inspectFixture() {
		var api = mod.state().apiFor(threadId);
		if (api == null || !api.fixtureMode()) throw new IllegalStateException("Question inspection requires the isolated fixture.");
		JsonObject result = new JsonObject();
		result.addProperty("threadId", threadId); result.addProperty("requestId", requestId);
		result.addProperty("questionIndex", index); result.addProperty("questionCount", input == null ? 0 : input.questions().size());
		result.addProperty("busy", busy); result.addProperty("sent", sent); result.addProperty("canAnswer", canAnswer());
		result.addProperty("error", error); result.addProperty("answer", answer.value()); result.addProperty("textFocused", textFocused);
		result.addProperty("selectedCount", picked.size()); result.addProperty("scroll", scroll.offset()); result.addProperty("scrollMax", scroll.max());
		result.addProperty("x", x); result.addProperty("y", y); result.addProperty("width", w); result.addProperty("height", h);
		result.addProperty("bodyY", bodyY); result.addProperty("bodyHeight", bodyH);
		result.addProperty("fieldY", fieldY); result.addProperty("fieldHeight", fieldH); result.addProperty("actionY", actionY);
		JsonArray buttons = new JsonArray();
		for (Choice c : choices) { JsonObject button = new JsonObject(); button.addProperty("index", c.index()); button.addProperty("x", x + 14); button.addProperty("y", bodyY + c.y() - scroll.offset()); button.addProperty("height", c.h()); buttons.add(button); }
		result.add("choices", buttons); return result;
	}
	@Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
		if (inside(mx, my, x, bodyY, w, bodyH)) { scroll.scrollBy((int) (-vertical * 20)); return true; }
		return super.mouseScrolled(mx, my, horizontal, vertical);
	}
	@Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		w = Math.min(440, width - 24); int cw = w - 24;
		var q = question(); choices.clear();
		List<FormattedCharSequence> lines = font.split(Component.literal(q == null ? loaded ? "This agent no longer has a question for you." : "Loading the agent's question…" : q.question() == null ? "What would you like to do?" : q.question()).withStyle(ChatFormatting.BOLD), cw - 16);
		int natural = lines.size() * 11 + 10;
		if (q != null) for (int i = 0; i < q.options().size(); i++) {
			var option = q.options().get(i);
			int labelLines = font.split(Component.literal(option.label()), cw - 44).size();
			int descriptionLines = option.description() == null ? 0 : font.split(Component.literal(option.description()), cw - 44).size();
			int ch = Math.max(24, 12 + (labelLines + descriptionLines) * 10);
			choices.add(new Choice(i, natural, ch)); natural += ch + 6;
		}
		fieldH = q != null && q.allowCustomAnswer() && !sent ? field.height(font, answer, cw, fieldStyle()) : 0;
		int statusH = error == null ? 14 : Math.min(44, font.split(Component.literal(error), cw).size() * 10 + 4);
		int fixed = 64 + (fieldH > 0 ? fieldH + 8 : 0) + 28 + statusH + 12;
		bodyH = Math.min(natural, Math.max(28, height - 24 - fixed));
		h = fixed + bodyH; x = (width - w) / 2; y = Math.max(8, (height - h) / 2);
		bodyY = y + 62; fieldY = bodyY + bodyH + 6; actionY = fieldY + (fieldH > 0 ? fieldH + 8 : 0);
		scroll.update(natural, bodyH);
		if (resetScroll) { scroll.toTop(); resetScroll = false; }
		Panels.framed(g, x, y, w, h);
		Panels.header(g, font, sent ? "Answer sent" : "A question for you", x + 10, y + 10, cw + 4);
		if (input != null && input.questions().size() > 1) {
			String step = (index + 1) + " / " + input.questions().size();
			Panels.text(g, font, step, x + w - 16 - font.width(step), y + 13, UiBits.muted());
		}
		UiBits.framedPortrait(g, cast, x + 12, y + 32, 1);
		String name = cast == null ? "Your agent" : mod.npcName(cast);
		Panels.text(g, font, TextUtil.ellipsize(font, name + " asks you", cw - 30), x + 40, y + 33, UiBits.nameOnLight(cast));
		var row = mod.state().snapshot().threads().stream().filter(r -> r.id().equals(threadId)).findFirst().orElse(null);
		String context = row == null ? "T3 Code" : row.title() + " · " + row.environment();
		Panels.text(g, font, TextUtil.ellipsize(font, context, cw - 30), x + 40, y + 44, UiBits.muted());
		g.enableScissor(x + 12, bodyY, x + w - 12, bodyY + bodyH);
		int ty = bodyY + 2 - scroll.offset();
		for (var line : lines) { g.text(font, line, x + 16, ty, UiBits.ink(), false); ty += 11; }
		for (Choice c : choices) {
			var option = q.options().get(c.index()); int cy = bodyY + c.y() - scroll.offset();
			boolean primary = q.multiSelect() ? picked.contains(option.value()) : highlight == c.index();
			boolean hover = inside(mx, my, x + 14, cy, cw - 10, c.h());
			dev.agentcraft.client.ui.T3ThemeWidgets.button(g, x + 14, cy, cw - 10, c.h(), primary, hover, !canAnswer());
			int ink = !canAnswer() ? UiStyle.color("paper.disabled") : primary ? dev.agentcraft.client.ui.T3Theme.color("accentForeground") : UiBits.ink();
			Panels.text(g, font, q.multiSelect() ? (primary ? UiBits.CHECK : "○") : c.index() < 9 ? Integer.toString(c.index() + 1) : "·", x + 22, cy + 7, ink);
			int oy = cy + 6;
			for (var line : font.split(Component.literal(option.label()), cw - 44)) { g.text(font, line, x + 40, oy, ink, false); oy += 10; }
			if (option.description() != null) for (var line : font.split(Component.literal(option.description()), cw - 44)) {
				g.text(font, line, x + 40, oy, primary && canAnswer() ? dev.agentcraft.client.ui.T3Theme.color("accentForeground") : UiBits.muted(), false); oy += 10;
			}
		}
		g.disableScissor(); Panels.scrollbar(g, x + w - 18, bodyY, bodyH, scroll, mx >= x + w - 22);
		if (fieldH > 0) field.draw(g, font, answer, x + 12, fieldY, cw, textFocused, fieldStyle());
		UiBits.button(g, font, sent ? "Done" : "Later", 0, x + 12, actionY, 64, false, inside(mx, my, x + 12, actionY, 64, 20) ? UiBits.ButtonState.HOVER : UiBits.ButtonState.NORMAL, false);
		UiBits.button(g, font, "Open chat", 0, x + 82, actionY, 74, false, inside(mx, my, x + 82, actionY, 74, 20) ? UiBits.ButtonState.HOVER : UiBits.ButtonState.NORMAL, false);
		boolean ready = canAnswer() && (q.allowCustomAnswer() && !answer.value().isBlank()
			|| q.multiSelect() && !picked.isEmpty() || !q.multiSelect() && !q.options().isEmpty());
		UiBits.button(g, font, busy ? "Sending…" : index + 1 < (input == null ? 1 : input.questions().size()) ? "Next" : "Send answer", 0, x + w - 104, actionY, 92, true, !ready ? UiBits.ButtonState.DISABLED : inside(mx, my, x + w - 104, actionY, 92, 20) ? UiBits.ButtonState.HOVER : UiBits.ButtonState.NORMAL, false);
		String status = error != null ? error : sent ? "Your answer was received by T3." : busy ? "Waiting for T3…" : !mod.state().online(threadId) ? "Machine offline. Your answer has not been sent." : input != null && !pending ? "This question was answered or withdrawn." : "1–9 choose   Enter confirm   Tab type   Esc later";
		int sy = actionY + 28;
		g.enableScissor(x + 12, sy, x + w - 12, y + h - 8);
		for (var line : font.split(Component.literal(status), cw)) { g.text(font, line, x + 12, sy, error != null ? UiBits.errorText() : sent ? UiBits.okText() : UiBits.muted(), false); sy += 10; }
		g.disableScissor();
	}
}
