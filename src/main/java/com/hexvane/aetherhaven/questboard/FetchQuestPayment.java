package com.hexvane.aetherhaven.questboard;

import com.hexvane.aetherhaven.quest.data.QuestReward;
import com.hexvane.aetherhaven.questboard.data.QuestBoardFetchEntryJson;
import com.hexvane.aetherhaven.shopspot.ShopPriceCatalog;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/** Quote once when an offer is posted. Saved offers and accepted quests retain their quoted rewards. */
public final class FetchQuestPayment {
    private static final String GOLD = "Aetherhaven_Gold_Coin";
    private FetchQuestPayment() {}

    /** Empty means the configured payment cannot fit an item reward and the offer must be skipped. */
    public static Optional<List<QuestReward>> quote(QuestBoardFetchEntryJson entry,
            List<QuestBoardItemRequirement> selectedItems, ShopPriceCatalog prices) {
        var rewards = new ArrayList<>(entry.rewardsOrEmpty());
        if (entry.materialPaymentBonusGold() == 0) return Optional.of(rewards);
        int firstGold = -1;
        long authoredGold = 0;
        for (int i = 0; i < rewards.size(); i++) {
            var reward = rewards.get(i);
            if (!"item".equalsIgnoreCase(reward.kind()) || !GOLD.equals(reward.itemId())
                || (reward.grantTo() != null && !"player".equalsIgnoreCase(reward.grantTo()))) continue;
            if (firstGold < 0) firstGold = i;
            authoredGold = Math.addExact(authoredGold, reward.count());
        }
        // An opt-in must still author a player gold reward. Never invent payment targets.
        if (firstGold < 0) return Optional.of(rewards);
        var quantities = new LinkedHashMap<String, BigInteger>();
        for (var item : selectedItems) {
            if (item.itemIdOrEmpty().isBlank()) return Optional.empty();
            quantities.merge(item.itemIdOrEmpty(), BigInteger.valueOf(item.count()), BigInteger::add);
        }
        BigInteger floor = BigInteger.valueOf(entry.materialPaymentBonusGold());
        for (var item : quantities.entrySet()) {
            var price = prices.getEntry(item.getKey());
            var divided = item.getValue().multiply(BigInteger.valueOf(price.getGoldPerBatch()))
                .divideAndRemainder(BigInteger.valueOf(price.getBatchSize()));
            // Round each distinct item up to whole gold, without multiplying a per-batch price per item.
            floor = floor.add(divided[0]).add(divided[1].signum() == 0 ? BigInteger.ZERO : BigInteger.ONE);
        }
        BigInteger extra = floor.subtract(BigInteger.valueOf(authoredGold));
        if (extra.signum() <= 0) return Optional.of(rewards);
        var first = rewards.get(firstGold);
        BigInteger adjusted = extra.add(BigInteger.valueOf(first.count()));
        if (adjusted.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) return Optional.empty();
        rewards.set(firstGold, first.withItemCount(adjusted.intValueExact()));
        return Optional.of(rewards);
    }
}
