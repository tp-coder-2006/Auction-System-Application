package org.auctionsystem.igeneric.base.items;

import org.auctionsystem.igeneric.interfaces.items.IBrand;
import org.auctionsystem.igeneric.interfaces.items.IWarrantyMonths;

abstract public class BrandedItem extends Item implements IBrand, IWarrantyMonths {
    private String brand;
    private int warrantyMonths;
    public BrandedItem(String name, String description, String brand, int warrantyMonths) {
        super(name, description);
        this.brand=brand;
        this.warrantyMonths=warrantyMonths;
    }
    public BrandedItem(){
        super();
    }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public int getWarrantyMonths() { return warrantyMonths; }
    public void setWarrantyMonths(int warrantyMonths) { this.warrantyMonths = warrantyMonths; }
}
