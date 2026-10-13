// Adapted from maxwellyoung/t3craft (MIT), copyright 2026 Maxwell Young.
// See THIRD-PARTY-NOTICES.md and licenses/t3craft-client-MIT.txt.
package dev.agentcraft.client.t3;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The in-game T3 panel: thread list on the left, the focused conversation on the right,
 * approvals above the composer. Enter sends and drops you back into the world;
 * Shift+Enter sends and keeps the panel open.
 */
final class T3Screen extends T3MenuScreen {
	@Override public void onClose() { minecraft.gui.setScreen(null); }
	private static final int MARGIN = 10;
	private static final int SIDEBAR = 160;
	private static final int ROW = 34;
	private static final int HEADER = 26;
	private static final int COMPOSER = 20;
	private static final int FOOTER = 20;
	private static final int APPROVAL = 34;
	private static final int NEW_WIDTH = 52;
	private static final int FILTER = 14;

	private final T3CraftClient mod;
	private final long openedAt = System.currentTimeMillis();
	private EditBox composer;
	private EditBox search;
	private String query = "";
	private String sidebarFocused;
	private boolean filtersOpen;
	private boolean workingExpanded = true;
	private boolean settledExpanded;
	private int workingScroll, settledScroll;
	private final List<SidebarHit> sidebarHits = new ArrayList<>();
	private record SidebarHit(T3State.ThreadRow row, int top, int bottom) {}
	private int workingHeaderY = -1, settledHeaderY = -1;
	private Button approve;
	private Button deny;
	private Button stop;
	private Button review;
	private Button terminal;
	private Button pin;
	private String selectedRequest;
	private T3State.Focus shownFocus;
	private boolean newThread;
	private boolean focusComposer;
	private int scroll;
	/** Machine shown in the sidebar; null means all of them. */
	private String envFilter;
	private String projectFilter;
	private T3Workflow.View workFilter = T3Workflow.View.ALL;
	private record Project(String key, String label) {}
	private int sidebarScroll;
	/** Model chosen in the picker for the next send; null keeps the thread's current model. */
	private JsonObject pickedModel;
	private boolean pickerOpen;
	private int pickerScroll;

	// Answering the agent's questions: which request, which question, and the answers so far.
	private String answeringRequest;
	private int questionIndex;
	private JsonObject answers = new JsonObject();
	private final java.util.LinkedHashSet<String> multiPicked = new java.util.LinkedHashSet<>();
	private final List<int[]> optionRows = new ArrayList<>();

	private record PickerEntry(T3Api.Provider provider, T3Api.Model model, boolean needsNewThread) {}

	private T3State.Focus cachedFocus;
	private int cachedWidth;
	private List<T3Markdown.Line> cachedLines = List.of();
	private String loadedDraftKey;
	private boolean closed;
	private Button delivery;
	static record Navigation(String thread, String machine, String project, String query, T3Workflow.View view, int scroll,
		boolean filters, boolean working, boolean settled, int workingScroll, int settledScroll) {}

	T3Screen(T3CraftClient mod) { this(mod, null); }

	T3Screen(T3CraftClient mod, String selectedRequest) {
		super(Component.literal("T3"));
		this.mod = mod;
		this.selectedRequest = selectedRequest;
		var nav = mod.chatNavigation;
		if (nav != null && java.util.Objects.equals(nav.thread(), mod.state().focusedThreadId())) {
			envFilter = nav.machine(); projectFilter = nav.project(); query = nav.query(); workFilter = nav.view(); sidebarScroll = nav.scroll();
			filtersOpen = nav.filters(); workingExpanded = nav.working(); settledExpanded = nav.settled(); workingScroll = nav.workingScroll(); settledScroll = nav.settledScroll();
		}
	}

	@Override
	protected void init() {
		closed = false;
		mod.state().setPanelOpen(true);
		int mainX = MARGIN + SIDEBAR + 8;
		int mainWidth = width - mainX - MARGIN;
		int composerY = height - MARGIN - FOOTER - COMPOSER;

		composer = new EditBox(font, mainX, composerY, mainWidth, COMPOSER, composer, Component.literal("Prompt"));
		composer.setMaxLength(120000);
		loadedDraftKey = draftKey();
		if (composer.getValue().isEmpty()) composer.setValue(mod.draft(loadedDraftKey));
		composer.setResponder(value -> mod.saveDraft(loadedDraftKey, value));
		updateHint();
		addRenderableWidget(composer);
		setInitialFocus(composer);
		addRenderableWidget(Button.builder(Component.literal("+ New"), b -> {
			if (mod.state().snapshot().focusedRow() == null) { minecraft.gui.setScreen(new T3WorkspaceScreen(mod, null, false)); return; }
			newThread = !newThread; loadDraft(); updateHint(); focusComposer = true;
		})
			.bounds(MARGIN + SIDEBAR - NEW_WIDTH, MARGIN + 3, NEW_WIDTH, 18).build());
		search = new EditBox(font, MARGIN + 6, MARGIN + 3, SIDEBAR - NEW_WIDTH - 16, 18, Component.literal("Search chats"));
		search.setMaxLength(200); search.setValue(query);
		placeholder(search, "Search");
		search.setResponder(value -> { query = value; sidebarScroll = 0; }); addRenderableWidget(search);
		var filters = addRenderableWidget(Button.builder(Component.literal("Filter"), b -> { filtersOpen = !filtersOpen; sidebarScroll = 0; })
			.bounds(MARGIN + 6, MARGIN + HEADER, SIDEBAR - 12, FILTER).build());
		filters.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Filter by machine, project or work status. Search: Ctrl+F")));
		// Buttons sit on the card's title row so the command gets the full second row.
		int approvalY = composerY - APPROVAL - 14;
		approve = addRenderableWidget(Button.builder(Component.literal("Yes (Y)"), b -> answer("accept"))
			.bounds(mainX + mainWidth - 128, approvalY + 2, 60, 14).build());
		deny = addRenderableWidget(Button.builder(Component.literal("No (N)"), b -> answer("decline"))
			.bounds(mainX + mainWidth - 66, approvalY + 2, 60, 14).build());
		stop = addRenderableWidget(Button.builder(Component.literal("Stop"), b -> mod.interrupt())
			.bounds(width - MARGIN - 44, MARGIN + 3, 44, 18).build());
		review = addRenderableWidget(Button.builder(Component.literal("Review"), b -> {
			var row = mod.state().snapshot().focusedRow(); if (row == null) return;
			mod.openNextAction(row.id());
		})
			.bounds(width - MARGIN - 102, MARGIN + 3, 54, 18).build());
		addRenderableWidget(Button.builder(Component.literal("Your work"), b -> mod.openWork())
			.bounds(MARGIN + 5, height - MARGIN - 25, 78, 18).build());
		addRenderableWidget(Button.builder(Component.literal("Campus"), b -> mod.openCampus()).bounds(MARGIN + 87, height - MARGIN - 25, SIDEBAR - 92, 18).build());
		pin = addRenderableWidget(Button.builder(Component.literal("Pin desk"), b -> mod.togglePin(mod.state().snapshot().focusedRow()))
			.bounds(MARGIN + 5, height - MARGIN - 47, 78, 18).build());
		addRenderableWidget(Button.builder(Component.literal("Pins"), b -> minecraft.gui.setScreen(new T3PinsScreen(mod)))
			.bounds(MARGIN + 87, height - MARGIN - 47, SIDEBAR - 92, 18).build());
		addRenderableWidget(Button.builder(Component.literal("Connections"), b -> mod.openConnections())
			.bounds(MARGIN + 5, height - MARGIN - 69, 78, 18).build());
        terminal = addRenderableWidget(Button.builder(Component.literal("Terminal"), b -> { var row = mod.state().snapshot().focusedRow(); if (row != null) minecraft.gui.setScreen(new T3TerminalScreen(mod, row)); }).bounds(MARGIN + 87, height - MARGIN - 69, SIDEBAR - 92, 18).build());
        terminal.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Shell in this chat's checkout · Ctrl+`")));

		syncWidgets(mod.state().snapshot());
		revealFocused();
		int count = mainWidth >= 340 ? 5 : 4, actionWidth = (mainWidth - (count - 1) * 6) / count;
        int actionY = height - MARGIN - FOOTER;
        addRenderableWidget(Button.builder(Component.literal("Workspace"), b -> { var row = mod.state().snapshot().focusedRow(); if (newThread || row == null) minecraft.gui.setScreen(new T3WorkspaceScreen(mod, row, false)); else minecraft.gui.setScreen(new T3ToolsScreen(mod, row)); }).bounds(mainX, actionY, actionWidth, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Attach"), b -> minecraft.gui.setScreen(new T3AttachmentsScreen(mod, this, loadedDraftKey))).bounds(mainX + actionWidth + 6, actionY, actionWidth, 18).build());
        delivery = addRenderableWidget(Button.builder(Component.literal("Queue"), b -> {
            var entry = mod.draftEntry(loadedDraftKey); if (entry.sending || entry.prepared != null) return;
            entry.delivery = entry.delivery.equals("steer") ? "queue" : "steer"; entry.prepared = null; mod.draftChanged();
        }).bounds(mainX + 2 * (actionWidth + 6), actionY, actionWidth, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Expand"), b -> { var row = mod.state().snapshot().focusedRow(); if (row != null) minecraft.gui.setScreen(new T3DraftEditorScreen(mod, row, loadedDraftKey, newThread)); }).bounds(mainX + 3 * (actionWidth + 6), actionY, actionWidth, 18).build());
        if (count == 5) addRenderableWidget(Button.builder(Component.literal("Copy reply"), b -> { var focus = mod.state().snapshot().focus(); if (focus != null) { var replies = focus.messages().stream().filter(m -> m.role().equals("assistant")).toList(); if (!replies.isEmpty()) minecraft.keyboardHandler.setClipboard(replies.getLast().text()); } }).bounds(mainX + 4 * (actionWidth + 6), actionY, actionWidth, 18).build());
	}

	/** Opening on a thread further down (a ping, a villager, /t3 open) scrolls the sidebar to it. */
	private void revealFocused() {
		List<T3State.ThreadRow> rows = primaryRows();
		String focused = mod.state().focusedThreadId();
		if (shelves()) {
			var working = workingRows();
			for (int i = 0; i < working.size(); i++) if (working.get(i).id().equals(focused)) {
				workingExpanded = true; workingScroll = Math.max(0, i - shelfVisible(working) / 2); return;
			}
			var settled = settledRows();
			for (int i = 0; i < settled.size(); i++) if (settled.get(i).id().equals(focused)) {
				settledExpanded = true; settledScroll = Math.max(0, i - shelfVisible(settled) / 2); return;
			}
		}
		for (int i = 0; i < rows.size(); i++) {
			if (!rows.get(i).id().equals(focused)) continue;
			int visible = visibleRows();
			if (i < sidebarScroll || i >= sidebarScroll + visible) sidebarScroll = Math.max(0, i - visible / 2);
			clampSidebarScroll(rows.size());
			return;
		}
	}

	@Override
	public void removed() {
		closed = true;
		mod.chatNavigation = new Navigation(mod.state().focusedThreadId(), envFilter, projectFilter, query, workFilter, sidebarScroll, filtersOpen, workingExpanded, settledExpanded, workingScroll, settledScroll);
		if (!mod.draftEntry(loadedDraftKey).status.equals("Sent to T3")) mod.saveDraft(loadedDraftKey, composer.getValue()); mod.flushDrafts();
		mod.state().setPanelOpen(false);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		// The paper workspace stays readable over a softly blurred world.
		return false;
	}

	@Override
	public void tick() {
		if (focusComposer) { setFocused(composer); composer.setFocused(true); focusComposer = false; }
		if (!java.util.Objects.equals(loadedDraftKey, draftKey())) loadDraft();
		var entry = mod.draftEntry(loadedDraftKey);
		composer.setEditable(!entry.sending);
		if (!entry.sending && entry.text.isEmpty() && entry.status.equals("Sent to T3") && !composer.getValue().isEmpty()) composer.setValue("");
		if (delivery != null) { delivery.setMessage(Component.literal(entry.delivery.equals("steer") ? "Steer" : "Queue")); delivery.active = !newThread && !entry.sending && entry.prepared == null && mod.state().online(mod.state().focusedThreadId()); }
		syncWidgets(mod.state().snapshot());
		updateHint();
	}

	private void syncWidgets(T3State.Snapshot snapshot) {
		if (!java.util.Objects.equals(sidebarFocused, snapshot.focusedId())) { sidebarFocused = snapshot.focusedId(); revealFocused(); }
		boolean hasApproval = currentApproval() != null;
		boolean online = mod.state().online(snapshot.focusedId());
		approve.active = deny.active = online && (!hasApproval || !mod.responding(shownFocus.threadId(), currentApproval().requestId()));
		review.active = snapshot.focusedRow() != null && !newThread;
		if (snapshot.focusedRow() != null) {
			var next = mod.nextAction(snapshot.focusedRow());
			review.setMessage(Component.literal(next == T3Workflow.Action.ANSWER ? "Answer" : next == T3Workflow.Action.CONNECT ? "Connect" : next == T3Workflow.Action.REVIEW ? "Review" : snapshot.focusedRow().status() == T3State.Status.ERROR ? "Inspect" : snapshot.focusedRow().status() == T3State.Status.WORKING ? "Follow" : "Resume"));
			review.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(T3Workflow.hint(next, snapshot.focusedRow().status()))));
		}
		var terminalApi = mod.state().apiFor(snapshot.focusedId());
		terminal.active = !newThread && online && terminalApi != null && terminalApi.supportsAdvanced();
		stop.active = online;
		approve.visible = deny.visible = hasApproval;
		T3State.ThreadRow row = snapshot.focusedRow();
		stop.visible = row != null && row.status() == T3State.Status.WORKING;
		pin.active = row != null && mod.canPin() && !newThread;
		pin.setMessage(Component.literal(mod.pinned(row) ? "Unpin desk" : "Pin desk"));
	}

	/** Filter identities use owner URLs; duplicate names remain independent. */
	private List<String> machines() {
		return mod.state().snapshot().threads().stream().map(T3State.ThreadRow::ownerKey).distinct().sorted().toList();
	}

	private List<Project> projects() {
		java.util.Map<String, Project> projects = new java.util.LinkedHashMap<>();
		boolean showMachine = envFilter == null && machines().size() > 1;
		for (T3State.ThreadRow row : mod.state().snapshot().threads()) {
			if (envFilter != null && !envFilter.equals(row.ownerKey())) continue;
			String key = OfficeRoster.projectKey(row);
			String label = row.projectTitle() + (showMachine ? " · " + mod.machineLabel(row.ownerKey()) : "");
			projects.putIfAbsent(key, new Project(key, label));
		}
		return projects.values().stream().sorted(java.util.Comparator.comparing(Project::label)).toList();
	}

	private List<T3State.ThreadRow> sidebarRows() {
		String needle = query.strip().toLowerCase(java.util.Locale.ROOT);
		var desks = workFilter == T3Workflow.View.DESKS ? mod.studioBindings().values().stream().map(T3State.ThreadRow::id).collect(java.util.stream.Collectors.toSet()) : java.util.Set.<String>of();
		return mod.state().snapshot().threads().stream()
			.filter(r -> envFilter == null || envFilter.equals(r.ownerKey()))
			.filter(r -> projectFilter == null || projectFilter.equals(OfficeRoster.projectKey(r)))
			.filter(r -> T3Workflow.matches(workFilter, r, desks.contains(r.id())))
			.filter(r -> needle.isEmpty() || (r.title() + " " + r.projectTitle() + " " + mod.machineLabel(r.ownerKey())).toLowerCase(java.util.Locale.ROOT).contains(needle))
			.sorted(T3Workflow.order(workFilter)).toList();
	}

	private int machineTop() { return MARGIN + HEADER + FILTER + 2; }
	private int projectTop() { return machineTop() + (filtersOpen && machines().size() > 1 ? FILTER : 0); }
	private int workFilterTop() { return projectTop() + (projects().size() > 1 ? FILTER : 0); }
	private int filterBottom() { return filtersOpen ? workFilterTop() + FILTER : machineTop(); }
	private int listTop() { return filterBottom() + 4; }
	com.google.gson.JsonObject inspectFixture() {
		var out = new com.google.gson.JsonObject(); out.addProperty("query", query); out.addProperty("matches", sidebarRows().size());
		out.addProperty("focusedThread", mod.state().focusedThreadId()); out.addProperty("composer", composer.getValue());
		out.addProperty("filtersOpen", filtersOpen); out.addProperty("workingCount", workingRows().size()); out.addProperty("settledCount", settledRows().size());
		out.addProperty("workingExpanded", workingExpanded); out.addProperty("settledExpanded", settledExpanded);
		out.addProperty("workingHeaderY", workingHeaderY); out.addProperty("settledHeaderY", settledHeaderY);
		out.addProperty("focusedVisible", sidebarHits.stream().anyMatch(hit -> hit.row().id().equals(mod.state().focusedThreadId())));
		return out;
	}
	private void cycleMachine() {
		List<String> machines = machines();
		int at = envFilter == null ? -1 : machines.indexOf(envFilter);
		envFilter = at + 1 < machines.size() ? machines.get(at + 1) : null;
		projectFilter = null; sidebarScroll = 0; revealFocused();
	}
	private void cycleProject() {
		List<Project> projects = projects();
		int at = -1;
		for (int i = 0; i < projects.size(); i++) if (projects.get(i).key().equals(projectFilter)) at = i;
		projectFilter = at + 1 < projects.size() ? projects.get(at + 1).key() : null;
		sidebarScroll = 0; revealFocused();
	}

	private int visibleRows() {
		return Math.max(1, (primaryBottom() - listTop()) / ROW);
	}

	private void clampSidebarScroll(int total) {
		sidebarScroll = Math.max(0, Math.min(sidebarScroll, primaryRows().size() - visibleRows()));
	}

	private boolean shelves() { return query.isBlank() && (workFilter == T3Workflow.View.ALL || workFilter == T3Workflow.View.RECENT); }
	private boolean settled(T3State.ThreadRow row) {
		if (row.status() == T3State.Status.WORKING || row.status() == T3State.Status.NEEDS_YOU) return false;
		String override = T3Protocol.string(row.raw(), "settledOverride");
		return "settled".equals(override) || !"active".equals(override) && T3Protocol.string(row.raw(), "settledAt") != null;
	}
	private List<T3State.ThreadRow> primaryRows() { return shelves() ? sidebarRows().stream().filter(r -> r.status() != T3State.Status.WORKING && !settled(r)).toList() : sidebarRows(); }
	private List<T3State.ThreadRow> workingRows() { return shelves() ? sidebarRows().stream().filter(r -> r.status() == T3State.Status.WORKING).toList() : List.of(); }
	private List<T3State.ThreadRow> settledRows() { return shelves() ? sidebarRows().stream().filter(this::settled).toList() : List.of(); }
	private int listBottom() { return height - MARGIN - 74; }
	private int shelfVisible(List<T3State.ThreadRow> rows) { return Math.min(rows.size(), Math.max(1, (listBottom() - listTop()) / (ROW * 3))); }
	private int shelfHeight(List<T3State.ThreadRow> rows, boolean expanded) { return rows.isEmpty() ? 0 : FILTER + (expanded ? shelfVisible(rows) * ROW : 0); }
	private int primaryBottom() { return listBottom() - shelfHeight(workingRows(), workingExpanded) - shelfHeight(settledRows(), settledExpanded); }

	private String draftKey() {
		var row = mod.state().snapshot().focusedRow();
		return row == null ? "unselected" : T3Drafts.key(row.ownerKey(), row.id(), newThread);
	}
	private void loadDraft() {
		if (loadedDraftKey != null && !mod.draftEntry(loadedDraftKey).status.equals("Sent to T3")) mod.saveDraft(loadedDraftKey, composer.getValue());
		loadedDraftKey = draftKey(); composer.setValue(mod.draft(loadedDraftKey));
		pickedModel = mod.draftEntry(loadedDraftKey).model;
	}

	private void updateHint() {
		T3State.ThreadRow row = mod.state().snapshot().focusedRow();
		String hint = newThread
			? "New thread in " + (row == null ? "this project" : row.projectTitle()) + "…"
			: "Ask " + (row == null ? "the agent" : "\"" + row.title() + "\"") + "…";
		T3State.Question question = currentQuestion();
		if (question != null && !newThread) {
			hint = question.options().isEmpty() ? "Type your answer…"
				: "Press 1–" + Math.min(9, question.options().size()) + (question.allowCustomAnswer() ? ", or type your own answer" : "")
				+ (question.multiSelect() ? ", then Enter" : "") + "…";
		}
		placeholder(composer, hint);
	}


	private T3State.Approval currentApproval() {
		var focus = shownFocus;
		if (focus == null || focus.approvals().isEmpty()) return null;
		return selectedRequest == null ? focus.approvals().getFirst()
			: focus.approvals().stream().filter(a -> a.requestId().equals(selectedRequest)).findFirst().orElse(null);
	}

	private void answer(String decision) {
		T3State.Focus focus = shownFocus;
		T3State.Approval approval = currentApproval();
		if (approval != null && mod.state().online(focus.threadId())) mod.respond(focus.threadId(), approval, decision);
	}

	private T3State.UserInput currentInput() {
		T3State.Focus focus = shownFocus;
		if (focus == null || focus.userInputs().isEmpty() || !mod.state().online(focus.threadId())) return null;
		T3State.UserInput input = selectedRequest == null ? focus.userInputs().getFirst()
			: focus.userInputs().stream().filter(i -> i.requestId().equals(selectedRequest)).findFirst().orElse(null);
		if (input == null) return null;
		if (!(focus.threadId() + ":" + input.requestId()).equals(answeringRequest)) {
			answeringRequest = focus.threadId() + ":" + input.requestId();
			questionIndex = 0;
			answers = new JsonObject();
			multiPicked.clear();
		}
		return input;
	}

	private T3State.Question currentQuestion() {
		T3State.UserInput input = currentInput();
		return input == null || questionIndex >= input.questions().size() ? null : input.questions().get(questionIndex);
	}

	/** Records one answer; after the last question, sends the whole set. */
	private void answerQuestion(com.google.gson.JsonElement value) {
		T3State.UserInput input = currentInput();
		T3State.Question question = currentQuestion();
		if (input == null || question == null) return;
		answers.add(question.id(), value);
		multiPicked.clear();
		questionIndex++;
		if (questionIndex >= input.questions().size()) {
			mod.answer(shownFocus.threadId(), input, answers);
			answeringRequest = null;
		}
		updateHint();
	}

	private void pickOption(int index) {
		T3State.Question question = currentQuestion();
		if (question == null || index < 0 || index >= question.options().size()) return;
		String value = question.options().get(index).value();
		if (!question.multiSelect()) {
			answerQuestion(new com.google.gson.JsonPrimitive(value));
			return;
		}
		if (!multiPicked.remove(value)) multiPicked.add(value);
	}

	private void confirmMulti() {
		com.google.gson.JsonArray values = new com.google.gson.JsonArray();
		multiPicked.forEach(values::add);
		answerQuestion(values);
	}

	/** Used by the dev self-test: presses a number key on the question card. */
	void pressOption(int number) {
		keyPressed(new KeyEvent(InputConstants.KEY_1 + number - 1, 0, 0));
	}

	/** Used by the dev self-test. */
	void setComposerForTest(String text) {
		composer.setValue(text);
	}

	/** Used by the dev self-test. */
	String composerValueForTest() {
		return composer.getValue();
	}

	/** Used by the dev self-test: same as clicking the machine filter row. */
	String cycleMachineForTest() { cycleMachine(); return envFilter == null ? null : mod.machineLabel(envFilter); }

	/** Used by the dev self-test: whether the focused thread's row is within the visible window. */
	boolean focusedRowVisibleForTest() {
		List<T3State.ThreadRow> rows = sidebarRows();
		for (int i = 0; i < rows.size(); i++) {
			if (rows.get(i).id().equals(mod.state().focusedThreadId())) return i >= sidebarScroll && i < sidebarScroll + visibleRows();
		}
		return false;
	}

	/** Used by the dev self-test: rows in the sidebar under the current filter. */
	int sidebarCountForTest() {
		return sidebarRows().size();
	}

	/** Used by the dev self-test: the same path as the mouse wheel over the sidebar. */
	void scrollSidebarForTest(int notches) {
		for (int i = 0; i < Math.abs(notches); i++) mouseScrolled(MARGIN + 5, height / 2.0, 0, notches > 0 ? -1 : 1);
	}

	/** Used by the dev self-test: same as clicking + New. */
	void startNewThread() {
		newThread = true;
		loadDraft();
		updateHint();
	}

	/** Used by the dev self-test. */
	void openPicker() {
		pickerOpen = true;
	}

	/** Used by the dev self-test to drive the real composer path. */
	void typeAndSubmit(String text, boolean stayOpen) {
		composer.setValue(text);
		submit(stayOpen);
	}

	private void submit(boolean stayOpen) {
		String text = composer.getValue().trim();
		var row = mod.state().snapshot().focusedRow();
		var entry = mod.draftEntry(loadedDraftKey);
		if (row == null || entry.sending || (text.isEmpty() && entry.files.isEmpty())) return;
		mod.saveDraft(loadedDraftKey, text); if (pickedModel != null) entry.model = pickedModel;
		String key = loadedDraftKey;
		mod.sendDraft(key, row, newThread, id -> {
			if (closed || !key.equals(loadedDraftKey)) return;
			newThread = false; loadedDraftKey = draftKey(); composer.setValue(mod.draft(loadedDraftKey));
			pickedModel = null; scroll = 0; updateHint();
			if (!stayOpen) onClose();
		});
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.hasControlDown() && event.key() == InputConstants.KEY_GRAVE) {
			var row = mod.state().snapshot().focusedRow();
			if (row != null && terminal.active) minecraft.gui.setScreen(new T3TerminalScreen(mod, row));
			return true;
		}
		if (!pickerOpen && event.hasControlDown() && event.key() == InputConstants.KEY_TAB) {
			var rows = sidebarRows(); int index = -1;
			for (int i = 0; i < rows.size(); i++) if (rows.get(i).id().equals(mod.state().focusedThreadId())) index = i;
			if (!rows.isEmpty()) {
				mod.saveDraft(loadedDraftKey, composer.getValue());
				mod.focus(rows.get(Math.floorMod(index + (event.hasShiftDown() ? -1 : 1), rows.size())).id());
				newThread = false; selectedRequest = null; loadDraft(); scroll = 0; revealFocused(); setFocused(composer);
			}
			return true;
		}
		if (!pickerOpen && event.hasControlDown() && event.key() == InputConstants.KEY_F) { setFocused(search); search.setFocused(true); return true; }
		if (search.isFocused()) {
			if (event.isEscape() && !query.isEmpty()) { search.setValue(""); return true; }
			if (event.isConfirmation()) {
				var rows = sidebarRows();
				if (!rows.isEmpty()) { mod.saveDraft(loadedDraftKey, composer.getValue()); mod.focus(rows.getFirst().id()); newThread = false; selectedRequest = null; loadDraft(); scroll = 0; setFocused(composer); }
				return true;
			}
			return super.keyPressed(event);
		}
		if (pickerOpen && event.isEscape()) {
			pickerOpen = false;
			return true;
		}
		T3State.Question question = newThread ? null : currentQuestion();
		boolean composerEmpty = composer.getValue().isEmpty();
		if (question != null && composerEmpty && event.key() >= InputConstants.KEY_1 && event.key() <= InputConstants.KEY_9) {
			pickOption(event.key() - InputConstants.KEY_1);
			return true;
		}
		if (question != null && event.isConfirmation()) {
			String text = composer.getValue().trim();
			if (!text.isEmpty() && question.allowCustomAnswer()) {
				composer.setValue("");
				answerQuestion(new com.google.gson.JsonPrimitive(text));
			} else if (text.isEmpty() && question.multiSelect() && !multiPicked.isEmpty()) {
				confirmMulti();
			}
			return true;
		}
		if (event.isConfirmation() && composer.isFocused()) {
			submit(event.hasShiftDown());
			return true;
		}
		if (composerEmpty && (event.key() == InputConstants.KEY_Y || event.key() == InputConstants.KEY_N)
			&& approve.visible) {
			answer(event.key() == InputConstants.KEY_Y ? "accept" : "decline");
			return true;
		}
		if (composerEmpty && event.key() == InputConstants.KEY_GRAVE && System.currentTimeMillis() - openedAt > 250) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (search.isFocused()) return super.charTyped(event);
		// Swallow the ` that opened the panel, digits that picked an option, and Y/N that answered an approval.
		if (composer.getValue().isEmpty() && !newThread && currentQuestion() != null && event.codepoint() >= '1' && event.codepoint() <= '9') {
			return true;
		}
		if (composer.getValue().isEmpty() && (event.codepoint() == '`'
			|| (approve.visible && (event.codepoint() == 'y' || event.codepoint() == 'n' || event.codepoint() == 'Y' || event.codepoint() == 'N')))) {
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if ((mod.draftEntry(loadedDraftKey).sending || mod.draftEntry(loadedDraftKey).prepared != null) && (pickerOpen || chipContains(event.x(), event.y()))) return true;
		if (pickerOpen) {
			int[] box = pickerBox();
			if (event.x() >= box[0] && event.x() < box[2] && event.y() >= box[1] && event.y() < box[3]) {
				int index = (int) ((event.y() - box[1] - 2) / PICKER_ROW) + pickerScroll;
				List<Object> rows = pickerRows();
				if (index >= 0 && index < rows.size() && rows.get(index) instanceof PickerEntry entry) {
					if (entry.needsNewThread()) return true; // Choose + New explicitly before a provider that requires a new chat.
					pickedModel = T3Api.modelSelection(entry.provider().instanceId(), entry.model().slug());
					mod.draftEntry(loadedDraftKey).model = pickedModel; mod.draftEntry(loadedDraftKey).prepared = null; mod.draftChanged();
					updateHint();
					pickerOpen = false;
					setFocused(composer);
				}
				return true;
			}
			pickerOpen = false;
			return true;
		}
		if (chipContains(event.x(), event.y())) {
			pickerOpen = true;
			pickerScroll = 0;
			return true;
		}
		for (int[] optionRow : optionRows) {
			if (event.x() >= optionRow[0] && event.x() < optionRow[2] && event.y() >= optionRow[1] && event.y() < optionRow[3]) {
				pickOption(optionRow[4]);
				return true;
			}
		}
		List<T3State.ThreadRow> rows = sidebarRows();
		int listTop = listTop();
		List<String> machines = machines();
		if (filtersOpen && machines.size() > 1 && event.x() >= MARGIN && event.x() < MARGIN + SIDEBAR
			&& event.y() >= machineTop() && event.y() < projectTop()) {
			cycleMachine(); return true;
		}
		if (filtersOpen && projects().size() > 1 && event.x() >= MARGIN && event.x() < MARGIN + SIDEBAR
			&& event.y() >= projectTop() && event.y() < workFilterTop()) {
			cycleProject(); return true;
		}
		if (filtersOpen && event.x() >= MARGIN && event.x() < MARGIN + SIDEBAR && event.y() >= workFilterTop() && event.y() < filterBottom()) {
			workFilter = T3Workflow.View.values()[(workFilter.ordinal() + 1) % T3Workflow.View.values().length]; sidebarScroll = 0; return true;
		}
        if (event.x() >= MARGIN && event.x() < MARGIN + SIDEBAR) {
            if (workingHeaderY >= 0 && event.y() >= workingHeaderY && event.y() < workingHeaderY + FILTER) { workingExpanded = !workingExpanded; return true; }
            if (settledHeaderY >= 0 && event.y() >= settledHeaderY && event.y() < settledHeaderY + FILTER) { settledExpanded = !settledExpanded; return true; }
            for (var hit : sidebarHits) if (event.y() >= hit.top() && event.y() < hit.bottom()) {
                mod.saveDraft(loadedDraftKey, composer.getValue());
                mod.focus(hit.row().id()); selectedRequest = null; newThread = false;
                loadDraft(); pickedModel = null; pickerOpen = false; scroll = 0; updateHint();
                return true;
            }
        }
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (!pickerOpen && x < MARGIN + SIDEBAR) {
            int delta = (int) Math.signum(scrollY) * 2;
            if (settledHeaderY >= 0 && y >= settledHeaderY) settledScroll = Math.max(0, settledScroll - delta);
            else if (workingHeaderY >= 0 && y >= workingHeaderY) workingScroll = Math.max(0, workingScroll - delta);
            else { sidebarScroll -= delta; clampSidebarScroll(sidebarRows().size()); }
            return true;
        }
		if (pickerOpen) {
			int visible = (pickerBox()[3] - pickerBox()[1] - 4) / PICKER_ROW;
			pickerScroll = Math.max(0, Math.min(pickerRows().size() - visible, pickerScroll - (int) Math.signum(scrollY) * 2));
			return true;
		}
		scroll = Math.max(0, scroll + (int) Math.signum(scrollY) * 3);
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		T3State.Snapshot snapshot = mod.state().snapshot();
		shownFocus = snapshot.focus();
		mouseXCache = mouseX;
		mouseYCache = mouseY;
		extractSidebar(graphics, snapshot, mouseX, mouseY);
		extractConversation(graphics, snapshot);
		super.extractRenderState(graphics, mouseX, mouseY, a);
		extractModelChip(graphics, snapshot, mouseX, mouseY);
		if (pickerOpen) {
			graphics.nextStratum();
			extractPicker(graphics, mouseX, mouseY);
		}
	}

	private void extractSidebar(GuiGraphicsExtractor graphics, T3State.Snapshot snapshot, int mouseX, int mouseY) {
        int x0 = MARGIN, x1 = MARGIN + SIDEBAR;
        paper(graphics, x0, MARGIN, x1, height - MARGIN);
        sidebarHits.clear(); workingHeaderY = settledHeaderY = -1;
        if (envFilter != null && !machines().contains(envFilter)) envFilter = null;
        if (projectFilter != null && projects().stream().noneMatch(p -> p.key().equals(projectFilter))) projectFilter = null;
        if (filtersOpen) {
            if (machines().size() > 1) graphics.text(font, T3Hud.ellipsize(font, (envFilter == null ? "All machines" : mod.machineLabel(envFilter)) + " ▾", SIDEBAR - 16), x0 + 8, machineTop() + 1, MUTED, false);
            if (projects().size() > 1) {
                String project = projectFilter == null ? "All projects" : projects().stream().filter(p -> p.key().equals(projectFilter)).findFirst().map(Project::label).orElse("All projects");
                graphics.text(font, T3Hud.ellipsize(font, project + " ▾", SIDEBAR - 16), x0 + 8, projectTop() + 1, MUTED, false);
            }
            graphics.text(font, workFilter.label + " · " + sidebarRows().size() + " ▾", x0 + 8, workFilterTop() + 1, ACCENT, false);
        }
        clampSidebarScroll(sidebarRows().size());
        var primary = primaryRows();
        extractSidebarRows(graphics, primary, sidebarScroll, listTop(), primaryBottom(), mouseX, mouseY);
        if (primary.isEmpty() && !query.isBlank()) graphics.text(font, "No matching chats", x0 + 8, listTop() + 6, MUTED, false);
        int y = primaryBottom();
        var working = workingRows();
        if (!working.isEmpty()) {
            workingHeaderY = y;
            extractShelfHeader(graphics, "Working", working.size(), workingExpanded, y);
            y += FILTER;
            if (workingExpanded) {
                workingScroll = Math.max(0, Math.min(workingScroll, working.size() - shelfVisible(working)));
                extractSidebarRows(graphics, working, workingScroll, y, y + shelfVisible(working) * ROW, mouseX, mouseY);
                y += shelfVisible(working) * ROW;
            }
        }
        var settled = settledRows();
        if (!settled.isEmpty()) {
            settledHeaderY = y;
            extractShelfHeader(graphics, "Settled", settled.size(), settledExpanded, y);
            y += FILTER;
            if (settledExpanded) {
                settledScroll = Math.max(0, Math.min(settledScroll, settled.size() - shelfVisible(settled)));
                extractSidebarRows(graphics, settled, settledScroll, y, listBottom(), mouseX, mouseY);
            }
        }
    }

    private void extractShelfHeader(GuiGraphicsExtractor graphics, String label, int count, boolean expanded, int y) {
        graphics.text(font, label + " (" + count + ")", MARGIN + 8, y + 2, MUTED, false);
        graphics.text(font, expanded ? "▴" : "▾", MARGIN + SIDEBAR - 14, y + 2, MUTED, false);
    }

    private void extractSidebarRows(GuiGraphicsExtractor graphics, List<T3State.ThreadRow> rows, int first, int top, int bottom, int mouseX, int mouseY) {
        int x0 = MARGIN, x1 = MARGIN + SIDEBAR;
        int visible = Math.max(0, (bottom - top) / ROW);
        String focused = mod.state().focusedThreadId();
        int y = top;
        for (int i = first; i < rows.size() && i < first + visible; i++, y += ROW) {
            var row = rows.get(i);
            sidebarHits.add(new SidebarHit(row, y, y + ROW));
            boolean hovered = mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + ROW;
            if (row.id().equals(focused) && !newThread) {
                graphics.fill(x0 + 2, y + 1, x1 - 2, y + ROW - 1, SELECTED);
                graphics.fill(x0 + 2, y + 3, x0 + 4, y + ROW - 3, ACCENT);
            }
            else if (hovered) graphics.fill(x0 + 2, y + 1, x1 - 2, y + ROW - 1, WELL);
            boolean online = mod.state().online(row.id());
            int color = online ? T3Hud.paperColor(row.status()) : MUTED;
            graphics.fill(x0 + 8, y + 5, x0 + 12, y + 9, color);
            String time = sidebarTime(row);
            int timeWidth = font.width(time);
            graphics.text(font, T3Hud.ellipsize(font, row.projectTitle(), SIDEBAR - 26 - timeWidth), x0 + 16, y + 3, INK, false);
            if (!time.isEmpty()) graphics.text(font, time, x1 - 8 - timeWidth, y + 3, MUTED, false);
            graphics.text(font, T3Hud.ellipsize(font, (mod.pinned(row) ? "* " : "") + row.title(), SIDEBAR - 16), x0 + 8, y + 13, INK, false);
            String activity = online ? T3Hud.label(row.status()) : "Offline";
            String branch = T3Protocol.string(row.raw(), "branch");
            String context = row.status() == T3State.Status.WORKING && row.step() != null ? row.step() : branch;
            if (context == null && machines().size() > 1) context = mod.machineLabel(row.ownerKey());
            graphics.text(font, T3Hud.ellipsize(font, activity + (context == null ? "" : " · " + context), SIDEBAR - 16), x0 + 8, y + 23, color, false);
        }
        if (rows.size() > visible && visible > 0) {
            int track = visible * ROW;
            int thumb = Math.max(10, track * visible / rows.size());
            int thumbTop = top + (track - thumb) * first / Math.max(1, rows.size() - visible);
            graphics.fill(x1 - 4, thumbTop, x1 - 2, thumbTop + thumb, MUTED);
        }
    }

	private String sidebarTime(T3State.ThreadRow row) {
		JsonObject turn = T3Protocol.object(row.raw(), "latestTurn");
		String stamp = turn == null ? null : T3Protocol.string(turn, row.status() == T3State.Status.WORKING ? "startedAt" : "finishedAt");
		if (stamp == null && turn != null) stamp = T3Protocol.string(turn, "requestedAt");
		if (stamp == null) return "";
		try {
			long elapsed = Math.max(0L, java.time.Duration.between(java.time.Instant.parse(stamp), java.time.Instant.now()).toMinutes());
			return elapsed < 1 ? "now" : elapsed < 60 ? elapsed + "m" : elapsed < 1440 ? (elapsed / 60) + "h" : (elapsed / 1440) + "d";
		} catch (RuntimeException ignored) { return ""; }
	}

	private void extractConversation(GuiGraphicsExtractor graphics, T3State.Snapshot snapshot) {
		int x0 = MARGIN + SIDEBAR + 8;
		int x1 = width - MARGIN;
		paper(graphics, x0, MARGIN, x1, height - MARGIN);

		T3State.ThreadRow row = snapshot.focusedRow();
		T3State.Focus focus = snapshot.focus();
		if (!snapshot.connected() && row == null) {
			String message = snapshot.error() == null ? "Connecting to T3…" : snapshot.error();
			graphics.textWithWordWrap(font, Component.literal(message), x0 + 10, MARGIN + 10, x1 - x0 - 20, ERROR);
			return;
		}
		if (row == null && !newThread) {
			String hint = snapshot.threads().isEmpty() ? "No threads yet. Click + New to start one." : "Pick a thread on the left, or click + New.";
			graphics.text(font, hint, x0 + 10, MARGIN + 10, MUTED, false);
			return;
		}
		if (row == null) row = snapshot.threads().isEmpty() ? null : snapshot.threads().getFirst();
		if (row == null) return;

		String title = newThread ? "New thread" : row.title();
		graphics.text(font, T3Hud.ellipsize(font, title, chipX() - x0 - 20), x0 + 10, MARGIN + 6, INK, false);
		String status = mod.state().online(row.id()) ? T3Hud.label(row.status()) : "Offline · last-known status";
		if (row.status() == T3State.Status.WORKING) status += " " + T3Hud.elapsed(row.workingSince()) + (row.step() == null ? "" : " · " + row.step());
		String threadRef = row.id().length() > 8 ? row.id().substring(0, 8) : row.id();
		String meta = (row.environment() == null ? "" : mod.machineLabel(row.ownerKey()) + " · ") + row.projectTitle() + " · Thread " + threadRef;
		graphics.text(font, T3Hud.ellipsize(font, status + " · " + meta, chipX() - x0 - 20), x0 + 10, MARGIN + 16,
			mod.state().online(row.id()) ? T3Hud.paperColor(row.status()) : MUTED, false);
		graphics.horizontalLine(x0, x1 - 1, MARGIN + HEADER, WELL);

		boolean hasApproval = currentApproval() != null;
		int composerY = height - MARGIN - FOOTER - COMPOSER;
		T3State.Question question = newThread ? null : currentQuestion();
		int questionHeight = question == null ? 0 : questionCardHeight(question, x1 - x0 - 24);
		int bottom = composerY - 18 - (hasApproval ? APPROVAL : 0) - questionHeight;
		int top = MARGIN + HEADER + 4;
		String connectionError = mod.state().connectionError(row.id());
		if (connectionError != null) {
			int errorHeight = Math.min(Math.max(0, bottom - top - 10), font.split(Component.literal(connectionError), x1 - x0 - 20).size() * font.lineHeight + 8);
			graphics.enableScissor(x0, top, x1, top + errorHeight);
			graphics.fill(x0, top, x1, top + errorHeight, dev.agentcraft.client.ui.T3Theme.color("errorSurface"));
			graphics.textWithWordWrap(font, Component.literal(connectionError), x0 + 10, top + 4, x1 - x0 - 20, ERROR);
			graphics.disableScissor();
			top += errorHeight + 6;
		}

		if (!pickerOpen && !newThread && focus != null && focus.threadId().equals(row.id())) {
			List<T3Markdown.Line> lines = lines(focus, x1 - x0 - 28);
			int visible = Math.max(1, (bottom - top) / font.lineHeight);
			scroll = Math.min(scroll, Math.max(0, lines.size() - visible));
			int end = lines.size() - scroll;
			int start = Math.max(0, end - visible);
			graphics.enableScissor(x0, top, x1, bottom);
			int y = bottom - (end - start) * font.lineHeight;
			for (int i = start; i < end; i++) {
				T3Markdown.Line line = lines.get(i);
				int textX = x0 + 10 + line.indent();
				switch (line.kind()) {
					case RULE -> graphics.horizontalLine(x0 + 10, x1 - 12, y + 4, SELECTED);
					case CODE -> graphics.fill(x0 + 10, y - 1, x1 - 12, y + font.lineHeight, WELL);
					case QUOTE -> graphics.fill(x0 + 10, y - 1, x0 + 12, y + font.lineHeight, MUTED);
					default -> {
					}
				}
				if (line.marker() != null) {
					graphics.text(font, line.marker(), textX - 4 - font.width(line.marker()), y, T3Markdown.MUTED, false);
				}
				graphics.text(font, line.text(), textX, y, T3Markdown.TEXT, false);
				y += font.lineHeight;
			}
			graphics.disableScissor();
			if (lines.isEmpty()) graphics.text(font, "No messages yet.", x0 + 10, top + 4, MUTED, false);
		}

		optionRows.clear();
		if (question != null) {
			extractQuestion(graphics, question, x0, x1, composerY - (hasApproval ? APPROVAL : 0) - questionHeight - 14, mouseXCache, mouseYCache);
		}
		if (hasApproval) {
			T3State.Approval approval = currentApproval();
			int y = composerY - APPROVAL - 14;
			graphics.fill(x0 + 6, y, x1 - 6, y + APPROVAL - 2, dev.agentcraft.client.ui.T3Theme.color("warningSurface"));
			graphics.text(font, mod.responding(row.id(), approval.requestId()) ? "Waiting for T3…" : "Approve " + approval.kind() + "?", x0 + 12, y + 5, ACCENT, false);
			String detail = approval.detail() == null ? "" : approval.detail();
			graphics.text(font, T3Hud.ellipsize(font, detail, x1 - x0 - 24), x0 + 12, y + 18, INK, false);
		}

		var entry = mod.draftEntry(loadedDraftKey);
		String footer = entry.status + (entry.files.isEmpty() ? "" : " · " + entry.files.size() + " attached");
		graphics.text(font, T3Hud.ellipsize(font, footer, x1 - x0 - 4), x0 + 2, composerY - 12, entry.status.startsWith("Send failed") ? ERROR : MUTED, false);
	}

	private int mouseXCache;
	private int mouseYCache;

	private int questionCardHeight(T3State.Question question, int width) {
		int lines = 1 + font.split(Component.literal(question.question() == null ? "" : question.question()), width).size();
		// + one line for the key hint (the input's own placeholder is hidden while it has focus).
		return 8 + lines * font.lineHeight + question.options().size() * (font.lineHeight + 2) + 4 + font.lineHeight + 2;
	}

	private void extractQuestion(GuiGraphicsExtractor graphics, T3State.Question question, int x0, int x1, int y, int mouseX, int mouseY) {
		T3State.UserInput input = currentInput();
		int width = x1 - x0 - 24;
		int height = questionCardHeight(question, width);
		graphics.fill(x0 + 6, y, x1 - 6, y + height, WELL);
		String step = input != null && input.questions().size() > 1 ? "  " + (questionIndex + 1) + "/" + input.questions().size() : "";
		graphics.text(font, (question.header() == null ? "Question" : question.header()) + step, x0 + 12, y + 4, ACCENT, false);
		int lineY = y + 4 + font.lineHeight;
		for (var part : font.split(Component.literal(question.question() == null ? "" : question.question()), width)) {
			graphics.text(font, part, x0 + 12, lineY, INK, false);
			lineY += font.lineHeight;
		}
		lineY += 2;
		for (int i = 0; i < question.options().size(); i++) {
			T3State.Option option = question.options().get(i);
			int rowTop = lineY - 1;
			int rowBottom = lineY + font.lineHeight + 1;
			boolean hovered = mouseX >= x0 + 8 && mouseX < x1 - 8 && mouseY >= rowTop && mouseY < rowBottom;
			boolean picked = multiPicked.contains(option.value());
			if (hovered || picked) graphics.fill(x0 + 8, rowTop, x1 - 8, rowBottom, picked ? SELECTED : WELL);
			String key = (i < 9 ? String.valueOf(i + 1) : " ") + (question.multiSelect() ? (picked ? " [x] " : " [ ] ") : "  ");
			graphics.text(font, key, x0 + 12, lineY, ACCENT, false);
			int labelX = x0 + 12 + font.width(key);
			String label = option.label();
			graphics.text(font, T3Hud.ellipsize(font, label, x1 - labelX - 14), labelX, lineY, INK, false);
			if (option.description() != null && !option.description().isBlank()) {
				int descX = labelX + font.width(label) + 6;
				if (descX < x1 - 40) {
					graphics.text(font, T3Hud.ellipsize(font, "— " + option.description(), x1 - descX - 14), descX, lineY, MUTED, false);
				}
			}
			optionRows.add(new int[] {x0 + 8, rowTop, x1 - 8, rowBottom, i});
			lineY += font.lineHeight + 2;
		}
		String keys = question.options().isEmpty() ? "Type your answer below, then Enter"
			: "Press 1–" + Math.min(9, question.options().size()) + (question.multiSelect() ? ", then Enter" : "")
			+ (question.allowCustomAnswer() ? ", or type your own" : "");
		graphics.text(font, T3Hud.ellipsize(font, keys, width), x0 + 12, lineY + 1, MUTED, false);
	}

	private static final int PICKER_ROW = 11;
	private static final int PICKER_WIDTH = 210;

	/** The model the next send will use: the picked one, else the thread's. */
	private JsonObject effectiveModel() {
		if (pickedModel != null) return pickedModel;
		T3State.ThreadRow row = mod.state().snapshot().focusedRow();
		if (row == null || !row.raw().has("modelSelection") || !row.raw().get("modelSelection").isJsonObject()) return null;
		return row.raw().getAsJsonObject("modelSelection");
	}

	private String modelLabel(JsonObject selection) {
		if (selection == null) return "Model";
		String instanceId = selection.get("instanceId").getAsString();
		String slug = selection.get("model").getAsString();
		for (T3Api.Provider provider : mod.state().providers()) {
			if (!provider.instanceId().equals(instanceId)) continue;
			for (T3Api.Model model : provider.models()) if (model.slug().equals(slug)) return model.name();
		}
		return slug;
	}

	// The chip's right edge stays put whether or not Stop is showing.
	private int chipRight() {
		return width - MARGIN - 110;
	}

	private int chipX() {
		return chipRight() - font.width(modelLabel(effectiveModel()) + " ▾") - 10;
	}

	private boolean chipContains(double x, double y) {
		return x >= chipX() && x < chipRight() && y >= MARGIN + 4 && y < MARGIN + 20;
	}

	private void extractModelChip(GuiGraphicsExtractor graphics, T3State.Snapshot snapshot, int mouseX, int mouseY) {
		if (snapshot.focusedRow() == null) return;
		String label = modelLabel(effectiveModel()) + " ▾";
		boolean hovered = chipContains(mouseX, mouseY);
		graphics.fill(chipX(), MARGIN + 4, chipRight(), MARGIN + 20, pickerOpen || hovered ? SELECTED : WELL);
		graphics.text(font, label, chipX() + 5, MARGIN + 8, pickedModel != null ? ACCENT : INK, false);
	}

	/** Provider headers (String) and selectable models (PickerEntry), in display order. */
	private List<Object> pickerRows() {
		List<Object> rows = new ArrayList<>();
		T3State.ThreadRow row = mod.state().snapshot().focusedRow();
		JsonObject current = row == null ? null : row.raw().getAsJsonObject("modelSelection");
		boolean started = row != null && row.raw().has("latestTurn") && !row.raw().get("latestTurn").isJsonNull();
		String currentInstance = current == null ? null : current.get("instanceId").getAsString();
		boolean currentLocked = mod.state().providers().stream()
			.anyMatch(p -> p.instanceId().equals(currentInstance) && p.requiresNewThreadForModelChange());
		for (T3Api.Provider provider : mod.state().providers()) {
			rows.add(provider.name());
			for (T3Api.Model model : provider.models()) {
				boolean same = current != null && provider.instanceId().equals(currentInstance)
					&& model.slug().equals(current.get("model").getAsString());
				// Same rule as T3's composer: only providers that forbid switching force a new thread.
				boolean needsNewThread = !newThread && started && !same && (currentLocked || provider.requiresNewThreadForModelChange());
				rows.add(new PickerEntry(provider, model, needsNewThread));
			}
		}
		return rows;
	}

	private int[] pickerBox() {
		int x1 = chipRight();
		int x0 = x1 - PICKER_WIDTH;
		int y0 = MARGIN + 21;
		int maxBottom = height - MARGIN - FOOTER - COMPOSER - 4;
		int rows = Math.max(1, pickerRows().size());
		return new int[] {x0, y0, x1, Math.min(maxBottom, y0 + 4 + rows * PICKER_ROW)};
	}

	private void extractPicker(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int[] box = pickerBox();
		paper(graphics, box[0], box[1], box[2], box[3]);
		graphics.outline(box[0], box[1], box[2] - box[0], box[3] - box[1], SELECTED);
		List<Object> rows = pickerRows();
		if (rows.isEmpty()) {
			graphics.text(font, "Loading models…", box[0] + 6, box[1] + 4, MUTED, false);
			return;
		}
		JsonObject selected = effectiveModel();
		int visible = (box[3] - box[1] - 4) / PICKER_ROW;
		graphics.enableScissor(box[0], box[1], box[2], box[3]);
		for (int i = 0; i < visible && i + pickerScroll < rows.size(); i++) {
			Object entry = rows.get(i + pickerScroll);
			int y = box[1] + 2 + i * PICKER_ROW;
			if (entry instanceof String header) {
				graphics.text(font, header, box[0] + 6, y + 2, MUTED, false);
				continue;
			}
			PickerEntry pick = (PickerEntry) entry;
			boolean isSelected = selected != null && pick.provider().instanceId().equals(selected.get("instanceId").getAsString())
				&& pick.model().slug().equals(selected.get("model").getAsString());
			if (mouseX >= box[0] && mouseX < box[2] && mouseY >= y && mouseY < y + PICKER_ROW) {
				graphics.fill(box[0] + 1, y, box[2] - 1, y + PICKER_ROW, WELL);
			}
			String suffix = pick.needsNewThread() ? "  use + New" : "";
			String name = T3Hud.ellipsize(font, pick.model().name(), PICKER_WIDTH - 24 - font.width(suffix));
			graphics.text(font, (isSelected ? "• " : "  ") + name, box[0] + 8, y + 2, isSelected ? INK : INK, false);
			if (!suffix.isEmpty()) graphics.text(font, suffix, box[2] - 6 - font.width(suffix), y + 2, MUTED, false);
		}
		graphics.disableScissor();
	}

	/** Rendered conversation rows, rebuilt only when the focus snapshot or width changes. */
	private List<T3Markdown.Line> lines(T3State.Focus focus, int width) {
		if (focus == cachedFocus && width == cachedWidth) return cachedLines;
		List<T3Markdown.Line> lines = new ArrayList<>();
		for (T3State.Message message : focus.messages()) {
			boolean user = "user".equals(message.role());
			if (!lines.isEmpty()) lines.add(T3Markdown.Line.GAP);
			Component label = Component.literal(user ? "You" : "Agent").withStyle(style -> style.withBold(true)
				.withColor(user ? ACCENT : GOOD));
			T3Markdown.wrap(font, label, width, 0, null, T3Markdown.Kind.LABEL, lines);
			String text = message.text() + (message.streaming() ? " ▍" : "");
			T3Markdown.render(font, text, width - 8, user ? INK : T3Markdown.TEXT, lines);
		}
		cachedFocus = focus;
		cachedWidth = width;
		cachedLines = lines;
		return lines;
	}
}
