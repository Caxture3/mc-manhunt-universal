package com.caxture.manhuntuniversal.fabric.ui;
import com.caxture.manhuntuniversal.protocol.TrackingTargetDescriptor;
import java.util.*;
/** Pure dynamic selector state; time estimates never grant server authorization. */
public final class SelectorPageState {
    private List<TrackingTargetDescriptor> all = List.of();
    private Optional<String> selected = Optional.empty();
    private long revision = -1, receivedAt;
    private int page;
    public SelectorPageState(long revision, Optional<String> selected, List<TrackingTargetDescriptor> options, long now) { update(revision, selected, options, now); }
    public void update(long revision, Optional<String> selected, List<TrackingTargetDescriptor> options, long now) {
        if (revision < this.revision) return;
        this.revision = revision; this.selected = selected; receivedAt = now;
        all = options.stream().sorted(Comparator.comparingInt(TrackingTargetDescriptor::order).thenComparing(TrackingTargetDescriptor::id)).toList();
        page = Math.min(page, pages() - 1);
    }
    public long revision() { return revision; }
    public Optional<String> selection() { return selected; }
    public int page() { return page; }
    public int pages() { return Math.max(1, Math.ceilDiv(all.size(), 3)); }
    public void next() { page = (page + 1) % pages(); }
    public void previous() { page = Math.floorMod(page - 1, pages()); }
    public List<TrackingTargetDescriptor> options() { return all.subList(page * 3, Math.min(all.size(), page * 3 + 3)); }
    public long remaining(TrackingTargetDescriptor option, long now) { return Math.max(0, option.remainingMillis() - Math.max(0, (now - receivedAt) / 1_000_000L)); }
    public boolean enabled(TrackingTargetDescriptor option, long now) { return option.selectable() || option.remainingMillis() > 0 && remaining(option, now) == 0; }
    public SelectorRect panel(SelectorRect canvas, int index) {
        int count = options().size(); if (index < 0 || index >= count) throw new IllegalArgumentException("Invalid panel");
        int left = canvas.x() + (int)((long)index * canvas.width()/count), right = canvas.x() + (int)((long)(index + 1) * canvas.width()/count);
        return new SelectorRect(left, canvas.y(), right-left, canvas.height());
    }
    public OptionalInt hit(SelectorRect canvas, int x, int y) {
        for (int index = 0; index < options().size(); index++) if (panel(canvas,index).contains(x,y)) return OptionalInt.of(index);
        return OptionalInt.empty();
    }
}
