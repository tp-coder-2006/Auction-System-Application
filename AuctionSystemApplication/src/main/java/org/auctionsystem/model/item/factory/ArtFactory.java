package org.auctionsystem.model.item.factory;

import org.auctionsystem.igeneric.base.items.Item;
import org.auctionsystem.igeneric.interfaces.items.ItemFactory;
import org.auctionsystem.model.item.Art;

import java.time.LocalDateTime;
import java.util.List;

public class ArtFactory implements ItemFactory {
    private String name;
    private String description;
    private List<String> materials;
    private String artist;
    public ArtFactory(String name, String description, List<String> materials, String artist) {
        this.name = name;
        this.description = description;
        this.materials = materials;
        this.artist = artist;
    }
    public Item createItem(){
        return new Art(this.name, this.description, this.materials, this.artist);
    }
}
