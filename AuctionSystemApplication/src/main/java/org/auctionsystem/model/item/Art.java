package org.auctionsystem.model.item;

import org.auctionsystem.igeneric.base.items.Item;

import java.util.List;

public class Art extends Item{
    private List<String> materials;
    private String artist;
    public Art(String name, String description, List<String> materials, String artist) {
        super(name, description);
        this.materials = materials;
        this.artist = artist;
    }
    public Art() {
        super();
    }
    public List<String> getMaterials() {
        return this.materials;
    }
    public void setMaterials(List<String> materials) {
        this.materials = materials;
    }
    public String getArtist() {
        return artist;
    }
    public void setArtist(String artist) {
        this.artist = artist;
    }
}
