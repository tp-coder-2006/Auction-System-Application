package org.auctionsystem.model.item.factory;

import org.auctionsystem.igeneric.base.items.Item;
import org.auctionsystem.igeneric.interfaces.items.ItemFactory;
import org.auctionsystem.model.item.Electronics;

public class ElectronicsFactory implements ItemFactory {
    private String name;
    private String description;
    private String brand;
    private int warrantyMonths;
    public ElectronicsFactory(String name, String description, String brand, int warrantyMonths){
        this.name = name;
        this.description = description;
        this.brand = brand;
        this.warrantyMonths = warrantyMonths;
    }
    public Item createItem(){
        return new Electronics(this.name, this.description, brand, warrantyMonths);
    }
}
