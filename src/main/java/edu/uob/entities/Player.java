package edu.uob.entities;

import edu.uob.GameEntity;

import java.util.HashMap;

public class Player extends GameEntity {
    private HashMap<String,Artefact> inventory;
    private Location currentLocation;

    public Player(String name, String description) {
        super(name, description);
        this.inventory = new HashMap<>();
        this.currentLocation = null;
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
    public Artefact removeArtefact(Artefact artefact) {
        return inventory.remove(artefact.getName());
    }

    // location
    public Location getCurrentLocation() {
        return currentLocation;
    }
    public void setCurrentLocation(Location currentLocation) {
        this.currentLocation = currentLocation;
    }




}
