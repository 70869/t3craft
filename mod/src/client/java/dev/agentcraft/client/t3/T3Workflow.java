package dev.agentcraft.client.t3;

import java.time.Instant;
import java.util.Comparator;

/** Shared, read-only next actions for chats, residents and workspace navigation. */
public final class T3Workflow {
    private T3Workflow() {}
    public enum View {
        ALL("All chats"), WAITING("Needs you"), ACTIVE("Active runs"), DESKS("At desks"), RECENT("Recent chats"), FAILED("Failed runs");
        public final String label;
        View(String label) { this.label = label; }
    }
    public enum Action { CONNECT, ANSWER, REVIEW, CHAT }
    public static Action next(T3State.ThreadRow row, boolean online, boolean request, boolean review) {
        if (!online) return Action.CONNECT;
        if (request || row.status() == T3State.Status.NEEDS_YOU) return Action.ANSWER;
        if (row.status() == T3State.Status.ERROR) return Action.CHAT;
        if (review && row.status() != T3State.Status.WORKING) return Action.REVIEW;
        return Action.CHAT;
    }
    public static String label(Action action) {
        return switch (action) { case CONNECT -> "Reconnect machine"; case ANSWER -> "Answer request"; case REVIEW -> "Review changes"; case CHAT -> "Open chat"; };
    }
    public static String label(Action action, T3State.Status status) {
        if (action != Action.CHAT) return label(action);
        return status == T3State.Status.ERROR ? "Inspect failure" : status == T3State.Status.WORKING ? "Follow run" : "Resume chat";
    }
    public static String hint(Action action, T3State.Status status) {
        return switch (action) {
            case CONNECT -> "Check the owning machine; saved work remains available.";
            case ANSWER -> "Answer the current question or approval to continue.";
            case REVIEW -> "A checkpoint is ready for your review.";
            case CHAT -> status == T3State.Status.ERROR ? "Read the failure and decide how to continue."
                : status == T3State.Status.WORKING ? "Follow this run; replies queue unless you choose Steer."
                : "Read the latest response or continue this conversation.";
        };
    }
    public static boolean matches(View view, T3State.ThreadRow row, boolean desk) {
        return switch (view) { case WAITING -> row.status() == T3State.Status.NEEDS_YOU; case ACTIVE -> row.status() == T3State.Status.WORKING;
            case DESKS -> desk; case FAILED -> row.status() == T3State.Status.ERROR; default -> true; };
    }
    public static Comparator<T3State.ThreadRow> order(View view) {
        Comparator<T3State.ThreadRow> recent = Comparator.comparingLong(T3Workflow::updated).reversed().thenComparing(T3State.ThreadRow::id);
        return view == View.RECENT ? recent : Comparator.comparingInt((T3State.ThreadRow row) -> switch (row.status()) {
            case NEEDS_YOU -> 0; case ERROR -> 1; case WORKING -> 2; case DONE -> 3; case IDLE -> 4;
        }).thenComparing(recent);
    }
    private static long updated(T3State.ThreadRow row) {
        try { String value = T3Protocol.string(row.raw(), "updatedAt"); return value == null ? 0 : Instant.parse(value).toEpochMilli(); }
        catch (RuntimeException ignored) { return 0; }
    }
}
