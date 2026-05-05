package org.auctionsystem.model.item;

import org.auctionsystem.igeneric.base.items.BrandedItem;

import java.time.LocalDateTime;

public class Electronics extends BrandedItem{
    public Electronics(String name, String description, String brand, int warrantyMonths){
        super(name, description, brand, warrantyMonths);
    }
    public Electronics(){
        super();
    }
}

