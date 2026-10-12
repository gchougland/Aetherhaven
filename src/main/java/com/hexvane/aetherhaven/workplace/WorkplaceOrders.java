package com.hexvane.aetherhaven.workplace;

import java.util.*;

/** Saved intent, not a timer. Only the physical work executor may complete a batch. */
public final class WorkplaceOrders {
    public enum Mode { COUNT, FOREVER, MAINTAIN }
    public static final int MAX_ORDERS = 35, MAX_QUANTITY = 10000;
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
        recordCompletion(id,1);
    }
    /** One delivery can cover several crafts, which are all committed together. */
    public void recordCompletion(UUID id,int crafts) {
        var order=find(id);if(order==null)throw new IllegalStateException("Completed work lost its order");
        order.completed=(int)Math.min(Integer.MAX_VALUE,(long)order.completed+Math.max(1,crafts));revision++;
    }
    /**
     * A new task goes at the end of the list as it is now. The list changes every time a townsfolk finishes a craft,
     * and that is no reason to refuse: only a full list or a bad amount is.
     */
    public String add(String recipeId, Mode mode, int quantity) {
        if (orders.size() >= MAX_ORDERS) return "A workplace can keep up to 35 tasks.";
        if (recipeId == null || recipeId.isBlank() || mode == null || quantity < 1 || quantity > MAX_QUANTITY) return "Choose a recipe and an amount from 1 to 9999.";
        orders.add(new Order(recipeId, mode, quantity)); revision++; return null;
    }
    /**
     * Acts on the task the press named by its id, wherever it sits in the list now. Work finishing elsewhere in the
     * list never refuses it. "Pause" and "Resume" say which state is wanted, so a press seen late cannot undo itself.
     */
    public String change(UUID id, String action) {
        Order order = find(id); if (order == null) return "That task is no longer on the list. Nothing was changed.";
        int index = orders.indexOf(order);
        switch (action) {
            case "Up" -> { if (index == 0) return "That task is already first."; Collections.swap(orders, index, index - 1); }
            case "Down" -> { if (index + 1 >= orders.size()) return "That task is already last."; Collections.swap(orders, index, index + 1); }
            case "Pause" -> order.paused = true;
            case "Resume" -> order.paused = false;
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
        /**
         * The amount line of this task's row.
         *
         * @param cultivation true for a garden task, which counts plants
         * @param stored items of the output in town stores now, or a negative number when that cannot be counted
         * @param incoming items of the output already promised by work that is prepared or under way
         */
        public String amountText(boolean cultivation,long stored,long incoming) {
            return switch(mode) {
                case COUNT -> cultivation?"Grow "+quantity+(quantity==1?" plant | ":" plants | ")+completed+" harvested":"Craft "+quantity+" batches | "+completed+" complete";
                case FOREVER -> cultivation?"Keep growing while seeds are available":"Keep crafting while resources are available";
                case MAINTAIN -> "Keep "+quantity+" in stock"+(stored<0?"":" ("+stored+" now"+(incoming>0?", "+incoming+" on the way":"")+")");
            };
        }
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
