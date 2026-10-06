package com.hexvane.aetherhaven.workplace;

import java.util.*;

/** Saved intent, not a timer. Only the physical work executor may complete a batch. */
public final class WorkplaceOrders {
    public enum Mode { COUNT, FOREVER, MAINTAIN }
    public static final int MAX_ORDERS = 32, MAX_QUANTITY = 9999;
    private List<Order> orders = new ArrayList<>();
    private long revision;
    public List<Order> list() { return Collections.unmodifiableList(orders); }
    public long revision() { return revision; }
    public Order find(UUID id) { return orders.stream().filter(o -> o.id.equals(id)).findFirst().orElse(null); }
    public boolean canComplete(UUID id,String recipe) {
        var order=find(id);
        return order!=null&&!order.paused&&order.recipeId.equals(recipe)
            &&(order.mode!=Mode.COUNT||order.completed<order.quantity);
    }
    /** The caller commits the actual output on the same world thread before recording completion. */
    public void recordCompletion(UUID id) {
        var order=find(id);if(order==null)throw new IllegalStateException("Completed work lost its order");
        if(order.completed<Integer.MAX_VALUE)order.completed++;revision++;
    }
    public String add(long expected, String recipeId, Mode mode, int quantity) {
        if (expected != revision) return "The task list changed. Review it and try again.";
        if (orders.size() >= MAX_ORDERS) return "A workplace can keep up to 32 tasks.";
        if (recipeId == null || recipeId.isBlank() || mode == null || quantity < 1 || quantity > MAX_QUANTITY) return "Choose a recipe and an amount from 1 to 9999.";
        orders.add(new Order(recipeId, mode, quantity)); revision++; return null;
    }
    public String change(long expected, UUID id, String action) {
        if (expected != revision) return "The task list changed. Review it and try again.";
        Order order = find(id); if (order == null) return "This task no longer exists.";
        int index = orders.indexOf(order);
        switch (action) {
            case "Up" -> { if (index > 0) Collections.swap(orders, index, index - 1); }
            case "Down" -> { if (index + 1 < orders.size()) Collections.swap(orders, index, index + 1); }
            case "Pause" -> order.paused = !order.paused;
            case "Remove" -> orders.remove(order);
            default -> { return "Choose a task action."; }
        }
        revision++; return null;
    }
    public static final class Order {
        private UUID id = UUID.randomUUID();
        private String recipeId;
        private Mode mode;
        private int quantity, completed;
        private boolean paused;
        private Order(String recipeId, Mode mode, int quantity) { this.recipeId=recipeId; this.mode=mode; this.quantity=quantity; }
        public UUID id() { return id; }
        public String recipeId() { return recipeId; }
        public Mode mode() { return mode; }
        public int quantity() { return quantity; }
        public int completed() { return completed; }
        public boolean paused() { return paused; }
        /** Include reserved/in transit output to prevent parallel sites from overfilling a maintain target. */
        public int batchesNeeded(long stored, long incoming, int outputPerBatch) {
            return batchesNeeded(stored,incoming,outputPerBatch,0);
        }
        public int batchesNeeded(long stored,long incoming,int outputPerBatch,int queuedBatches) {
            if (paused || outputPerBatch <= 0) return 0;
            if (mode == Mode.COUNT) return (int)Math.max(0L,(long)quantity-completed-Math.max(0,queuedBatches));
            if (mode == Mode.FOREVER) return Integer.MAX_VALUE;
            long available = Math.min(quantity, Math.max(0, stored)) + Math.min(quantity, Math.max(0, incoming));
            long missing = Math.max(0, quantity - available);
            return (int) ((missing + outputPerBatch - 1) / outputPerBatch);
        }
    }
}
