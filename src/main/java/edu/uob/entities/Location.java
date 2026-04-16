package edu.uob.entities;

import edu.uob.GameEntity;

import java.util.HashMap;

public class Location extends GameEntity {
    // Path
    private HashMap<String, Location> paths;
    // Characters
    private HashMap<String, Character> characters;
    // Artefacts
    private HashMap<String, Artefact> artefacts;
    // Furniture
    private HashMap<String,Furniture> furniture;

    public Location(String name, String description) {
        super(name, description);
        this.paths = new HashMap<String, Location>();
        this.characters = new HashMap<String, Character>();
        this.artefacts = new HashMap<String,Artefact>();
        this.furniture = new HashMap<String,Furniture>();
    }

    // Path : get all the paths to locations
    public HashMap<String, Location> getAllPaths() {
        return paths;
    }
    // Path : get the location by the specific location name
    public Location getPathByName(String locationName) {
        return paths.get(locationName);
    }
    public void addPath(Location location) {
        paths.put(location.getName(), location);
    }
    public Location removePath(String locationName) {
       return paths.remove(locationName);
    }

    // Character
    public HashMap<String, Character> getAllCharacters() {
        return characters;
    }
    public Character getCharacterByName(String characterName) {
        return characters.get(characterName);
    }
    public void addCharacter(Character character) {
        characters.put(character.getName(), character);
    }
    public Character removeCharacter(String characterName) {
        return characters.remove(characterName);
    }

    // Look: To see all the artefacts
    public HashMap<String, Artefact> getAllArtefacts() {
        return artefacts;
    }

    // Get the artefact by the name
    public Artefact getArtefactByName(String name) {
        return artefacts.get(name);
    }
    public void addArtefact(Artefact artefact) {
        artefacts.put(artefact.getName(), artefact);
    }
    // remove the artefact by the name and return it
    public Artefact removeArtefact(String name) {
        return artefacts.remove(name);
    }

    public HashMap<String, Furniture> getAllFurniture() {
        return furniture;
    }
    public Furniture getFurnitureByName(String furnitureName) {
        return furniture.get(furnitureName);
    }
    public void addFurniture(Furniture furniture) {
        this.furniture.put(furniture.getName(), furniture);
    }
    public Furniture removeFurniture(String furnitureName) {
       return furniture.remove(furnitureName);
    }




}
