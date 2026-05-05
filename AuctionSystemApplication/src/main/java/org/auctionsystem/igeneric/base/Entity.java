package org.auctionsystem.igeneric.base;

import java.util.UUID;

public abstract class Entity{
    private String id;
    private String name;
    public Entity(String name){
        this.id = UUID.randomUUID().toString();
        this.name=name;
    }
    public Entity(){}
    public String getId(){
        return this.id;
    }
    public void setId(String id){
        this.id=id;
    }
    public String getName(){
        return this.name;
    }
    public void setName(String name){
        this.name=name;
    }
}