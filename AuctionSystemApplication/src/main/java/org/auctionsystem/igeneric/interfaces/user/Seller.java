package org.auctionsystem.igeneric.interfaces.user;


import org.auctionsystem.igeneric.base.items.Item;

import java.util.List;

public interface Seller {
    String getSellerName();
    double getRating();
    void addAuctionItem(Item item);
    void removeAuctionItem(Item item);
    List<Item> getAuctionItems();
}
