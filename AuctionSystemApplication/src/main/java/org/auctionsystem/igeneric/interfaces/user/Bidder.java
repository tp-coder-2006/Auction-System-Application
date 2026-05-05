package org.auctionsystem.igeneric.interfaces.user;

import org.auctionsystem.model.BidTransaction;
import org.auctionsystem.igeneric.base.items.Item;

import java.util.List;

public interface Bidder {
    String getBidderName();
    List<BidTransaction> getBidHistory();
    List<Item> getWonItems();
    void addWonItem(Item item);
}
