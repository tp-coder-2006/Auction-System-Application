package org.auctionsystem.model.item.factory;

import org.auctionsystem.igeneric.base.items.Item;
import org.auctionsystem.igeneric.interfaces.items.ItemFactory;
import org.auctionsystem.model.item.Vehicle;

import java.time.LocalDateTime;

public class VehicleFactory implements ItemFactory {
    private String name;
    private String description;
    private String brand;
    private int warrantyMonths;
    private double mileage;
    public VehicleFactory(String name, String description, String brand, int warrantyMonths, double mileage) {
        this.name = name;
        this.description = description;
        this.brand = brand;
        this.warrantyMonths = warrantyMonths;
        this.mileage = mileage;
    }
    public Item createItem(){
        return new Vehicle(this.name, this.description, brand, warrantyMonths, this.mileage);
    }
}
