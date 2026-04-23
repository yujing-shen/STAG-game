package edu.uob.entities;

import edu.uob.GameEntity;

import java.util.HashMap;

public class Player extends GameEntity {
    private HashMap<String,Artefact> inventory;
    private Location currentLocation;
    private int health;
    public static final int MAX_HEALTH = 3;
    public Player(String name, String description) {
        super(name, description);
        this.inventory = new HashMap<>();
        this.currentLocation = null;
        this.health = MAX_HEALTH;
    }

    // inventory : get all the artefacts from the inventory
    public HashMap<String, Artefact> getInventory() {
        return inventory;
    }
    public Artefact getArtefact(String artefactName) {
        return inventory.get(artefactName);
    }
    public void addArtefact(Artefact artefact) {
        inventory.put(artefact.getName(), artefact);
    }
    public Artefact removeArtefact(String artefactName) {
        return inventory.remove(artefactName);
    }

    // location
    public Location getCurrentLocation() {
        return currentLocation;
    }
    public void setCurrentLocation(Location currentLocation) {
        this.currentLocation = currentLocation;
    }

    // health


    public int getHealth() {
        return health;
    }

    public void setHealth(int health) {
        this.health = health;
    }

    public void increaseHealth() {
        this.health++;
        if (this.health > MAX_HEALTH) this.health = MAX_HEALTH;
    }

    public void decreaseHealth() {
        this.health--;
        if (this.health < 0) this.health = 0;
    }
}
