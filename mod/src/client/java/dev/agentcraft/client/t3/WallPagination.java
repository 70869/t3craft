package dev.agentcraft.client.t3;

/** Keeps every chat reachable without shrinking the physical wall's cards. */
public record WallPagination(int page, int pages, int start, int end) {
    public static WallPagination of(int count, int capacity, int requestedPage) {
        if (count < 0 || capacity < 1) throw new IllegalArgumentException("Invalid wall capacity");
        int pages = Math.max(1, (int)(((long)count + capacity - 1) / capacity));
        int page = Math.clamp(requestedPage, 0, pages - 1);
        int start = page * capacity;
        return new WallPagination(page, pages, start, (int)Math.min(count, (long)start + capacity));
    }
}
