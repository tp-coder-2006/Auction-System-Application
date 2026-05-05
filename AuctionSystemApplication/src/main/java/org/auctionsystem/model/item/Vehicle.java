package org.auctionsystem.model.item;

import org.auctionsystem.igeneric.base.items.BrandedItem;

import java.time.LocalDateTime;

public class Vehicle extends BrandedItem{
    private double mileage;
    public Vehicle(String name, String description, String brand, int warrantyMonths, double mileage){
        super(name, description, brand, warrantyMonths);
        this.mileage=mileage;
    }
    public Vehicle(){
        super();
    }
    public double getMileage() {
        return this.mileage;
    }
    public void setMileage(double mileage) {
        this.mileage = mileage;
    }
}

