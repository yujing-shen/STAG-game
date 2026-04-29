package edu.uob;

import com.alexmerz.graphviz.objects.Edge;
import com.alexmerz.graphviz.objects.Graph;
import com.alexmerz.graphviz.Parser;

import edu.uob.entities.*;
import edu.uob.entities.Character;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.FileReader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Array;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

import static edu.uob.entities.Player.MAX_HEALTH;

public final class GameServer {

    private static final char END_OF_TRANSMISSION = 4;
    private HashMap<String, Location> gameMap = new HashMap<>();
    private HashMap<String, Player> players = new HashMap<>();
    private HashSet<GameAction> gameActions = new HashSet<>();
    private Location startingLocation = null;
    private HashSet<String> allGameEntities = new HashSet<>();
    private CommandParser commandParser = new CommandParser();

    public static void main(String[] args) throws IOException {
        File entitiesFile = Paths.get("config" + File.separator + "extended-entities.dot").toAbsolutePath().toFile();
        File actionsFile = Paths.get("config" + File.separator + "extended-actions.xml").toAbsolutePath().toFile();
        GameServer server = new GameServer(entitiesFile, actionsFile);
        server.blockingListenOn(8888);
    }

    /**
    * Do not change the following method signature or we won't be able to mark your submission
    * Instantiates a new server instance, specifying a game with some configuration files
    *
    * @param entitiesFile The game configuration file containing all game entities to use in your game
    * @param actionsFile The game configuration file containing all game actions to use in your game
    */
    public GameServer(File entitiesFile, File actionsFile) {
        // TODO implement your server logic here

        // 1. Parse DOT file for entities and world map
        DOTParser dotParser = new DOTParser();
        dotParser.parse(entitiesFile);

        // Retrieve parsed map data
        this.gameMap = dotParser.getGameMap();
        this.startingLocation = dotParser.getStartingLocation();
        this.allGameEntities = dotParser.getAllGameEntities();

        // 2. Parse XML file for custom actions
        XMLParser xmlParser = new XMLParser();
        xmlParser.parse(actionsFile);

        // Retrieve parsed action data
        this.gameActions = xmlParser.getGameActions();


    }

    /**
    * Do not change the following method signature or we won't be able to mark your submission
    * This method handles all incoming game commands and carries out the corresponding actions.</p>
    *
    * @param command The incoming command to be processed
    */
    public String handleCommand(String command) {
        // TODO implement your server logic here
        try {
            // command cannot be null or empty
            if (command == null || command.isEmpty()) {
                return "Error: Invalid command";
            }

            String[] partsOfCommand = command.split(":",2);
            if (partsOfCommand.length != 2) {
                return "Error: Command must be in the format 'name:action";
            }

            // clean the parts of command
            String playerName = partsOfCommand[0].trim().toLowerCase();
            if (!playerName.matches("^[a-z '\\-]+$")) return "Error: Invalid player name";
            String actionCommand = commandParser.cleanCommand(partsOfCommand[1]);
            if (actionCommand.isEmpty()) return "Error: No action specified";

            // ONLY perform one action at a time
            if (hasMultipleCommands(actionCommand)) {
                return "Error: You can only perform one action at a time.";
            }

            // check if the current player is already in the players
            Player currentPlayer;
            if (players.containsKey(playerName)) {
                currentPlayer = players.get(playerName);
            } else {
                currentPlayer = new Player(playerName, "A player character");
                // put the new player in the startingLocation
                if (startingLocation != null) {
                    currentPlayer.setCurrentLocation(startingLocation);
                }
                players.put(playerName, currentPlayer);
            }
            if (actionCommand.contains("inv") || actionCommand.contains("inventory")) {
                return handleInv(currentPlayer);
            } else if (actionCommand.contains("get")) {
                return handleGet(currentPlayer, actionCommand);
            } else if (actionCommand.contains("drop")) {
                return handleDrop(currentPlayer, actionCommand);
            } else if (actionCommand.contains("goto")) {
                return handleGoto(currentPlayer, actionCommand);
            } else if (actionCommand.contains("look")) {
                return handleLook(currentPlayer);
            } else if (actionCommand.contains("health")) {
                return "Your current health is: " + currentPlayer.getHealth();
            } else {
                return handleCustomAction(currentPlayer, actionCommand);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: Something went wrong executing your command: " + command;
        }
    }


    /**
     * Lists all of the artefacts currently in the possession of the player
     *
     * @param
     * @return
     */
    private String handleInv(Player player) {
        HashMap<String, Artefact> inventory = player.getInventory();
        // check if the inventory is null
        if (inventory == null) {
            return "Your inventory is empty.";
        }
        StringBuilder result = new StringBuilder();
        result.append("You are carrying:").append("\n");
        for (Artefact artefact : inventory.values()) {
            result.append(artefact.getName()).append("\n");
        }

        return result.toString();
    }

    /**
     * Picks up a specified artefact from current location
     * and adds it to the player's inventory
     *
     * @param player The current player executing the command
     * @param actionCommand The cleaned command string entered by the player
     * @return A String describing the result of the action
     */
    private String handleGet(Player player, String actionCommand) {
        // 1. scan how many entities are mentioned in the command
        ArrayList<String> mentionedEntities = commandParser.getMentionedEntities(actionCommand, allGameEntities);

        // 2. check if the user mentioned nothing
        if (mentionedEntities.isEmpty()) {
            return "There is nothing like that here to pick up.";
        }

        // 3. check extraneous entities & composite commands
        // NOT allowed "get the potion and axe) AND "get key from forest"
        else if (mentionedEntities.size() > 1) {
            return "Error: Extraneous entities detected. You can only specify one item at a time.";
        }

        // 4. since the command is valid, extract the ONLY mentioned entity
        String targetEntityName = mentionedEntities.get(0);
        Location currentLocation = player.getCurrentLocation();
        HashMap<String, Artefact> roomArtefacts = currentLocation.getAllArtefacts();

        // 5. physical verification: is this entity actually an artefact on the floor?
        if (roomArtefacts.containsKey(targetEntityName)) {
            Artefact targetArtefact = roomArtefacts.get(targetEntityName);

            // execute the transfer
            currentLocation.removeArtefact(targetEntityName);
            player.addArtefact(targetArtefact);

            return "You picked up a " + targetEntityName + ".";
        } else {
            return "Error: You cannot get that here.";
        }
    }

    /**
     * Moves the player to a new location
     * (only if there is a valid path to that location)
     *
     * @param
     * @return
     */
    private String handleGoto(Player player, String actionCommand) {
        // 1. get the currentRoom the player is in
        Location currentLocation = player.getCurrentLocation();
        HashMap<String, Location> allPaths = currentLocation.getAllPaths();
        if (allPaths == null) {
            return "There is nowhere to go.";
        }
        // 2. check which room is mentioned in the command
        ArrayList<String> mentionedLocations = new ArrayList<>();
        for (String locationName : allPaths.keySet()) {
            if (actionCommand.contains(locationName)) {
                mentionedLocations.add(locationName);
            }
        }
        // 3. check the size of mentionedLocation
        if (mentionedLocations.isEmpty()) {
            return "You cannot go there from here." ;
        }
        if (mentionedLocations.size() > 1) {
            return "You can only go to one place.";
        }
        if (mentionedLocations.size() == 1) {
            // 4. go to the location
            player.setCurrentLocation(allPaths.get(mentionedLocations.get(0)));
            return "Now you are in " + player.getCurrentLocation().getDescription();
        }
        return "";
    }

    /**
     * Puts down an artefact from player's inventory
     * and places it into the current location
     *
     * @param player The current player executing the command
     * @param actionCommand The cleaned command string entered by the player
     * @return A String describing the result of the action
     */
    private String handleDrop(Player player, String actionCommand) {
        // 1. scan how many global entities are mentioned in the command
        ArrayList<String> mentionedEntities = commandParser.getMentionedEntities(actionCommand, allGameEntities);

        // 2. check if the user mentioned nothing
        if (mentionedEntities.isEmpty()) {
            return "You are not meant to drop anything";
        }

        // 3. check extraneous entities & composite commands
        // NOT allowed "drop potion and axe" AND " drop key from forest"
        else if (mentionedEntities.size() > 1) {
            return "Error: Extraneous entities detected. You can only specify one item at a time.";
        }

        // 4. since the command is valid, extract the ONLY mentioned entity
        String targetEntityName = mentionedEntities.get(0);
        Location currentLocation = player.getCurrentLocation();
        HashMap<String, Artefact> inventory = player.getInventory();

        // 5. physical verification : is this entity actually in the inventory?
        if (inventory.containsKey(targetEntityName)) {
            Artefact targetArtefact = inventory.get(targetEntityName);

            // execute the transfer
            inventory.remove(targetEntityName);
            currentLocation.addArtefact(targetArtefact);

            return "You dropped a " + targetEntityName + ".";
        } else {
            return "Error: You cannot drop that here.";
        }
    }

    /**
     * Describes the current location,
     * including all entities in that location and paths to other locations
     *
     * @param
     * @return
     */
    private String handleLook(Player currentPlayer) {
        // get the current room where current player is in
        Location currentLocation = currentPlayer.getCurrentLocation();

        StringBuilder result = new StringBuilder();
        // 1. basic description of the location
        result.append("Now you are in ").append(currentLocation.getDescription()).append(".\n");
        // 2. Artefacts
        HashMap<String, Artefact> allArtefacts = currentLocation.getAllArtefacts();
        if (!allArtefacts.isEmpty()) {
            result.append("You can see the following artefacts: \n");
            for (Artefact artefact : allArtefacts.values()) {
                result.append(artefact.getName()).append(" - ").append(artefact.getDescription()).append("\n");
            }
        }
        // 3. Furniture
        HashMap<String, Furniture> allFurniture = currentLocation.getAllFurniture();
        if (!allFurniture.isEmpty()) {
            result.append("You can see the following furniture: \n");
            for (Furniture furniture : allFurniture.values()) {
                result.append(furniture.getName()).append(" - ").append(furniture.getDescription()).append("\n");
            }
        }
        // 4. Characters
        HashMap<String, Character> allCharacters = currentLocation.getAllCharacters();
        if (!allCharacters.isEmpty()) {
            result.append("You can see the following characters: \n");
            for (Character character : allCharacters.values()) {
                result.append(character.getName()).append(" - ").append(character.getDescription()).append("\n");
            }
        }
        // 5. Paths
        HashMap<String, Location> allPaths = currentLocation.getAllPaths();
        if (!allPaths.isEmpty()) {
            result.append("From here you can access : \n");
            for (Location location : allPaths.values()) {
                result.append(location.getName()).append("\n");
            }
        }
        // 6. other players
        ArrayList<Player> playersInRoom = new ArrayList<>();

        // scan other players
        for (Player p : players.values()) {
            // check if the other players are in the same location as the current player
            if (p.getCurrentLocation().equals(currentLocation) &&
                    !p.getName().equals(currentPlayer.getName())) {
                playersInRoom.add(p);
            }
        }
        if  (!playersInRoom.isEmpty()) {
            result.append("You can see the following players: \n");

            for (Player p : playersInRoom) {
                    result.append(p.getName()).append(" - ").append(p.getDescription()).append("\n");
                }
            }

        // System.out.println(result.toString());
        return result.toString();

    }



    /**
     * Main engine for handling all custom XML actions.
     * Enforces "Extraneous Entities", "Partial Commands", and "Ambiguous Commands" rules.
     *
     * @param currentPlayer The current player executing the command
     * @param actionCommand The cleaned command string entered by the player
     * @return A String describing the result of the action
     */
    private String handleCustomAction(Player currentPlayer, String actionCommand) {
        // 1. scan for all game entities mentioned in the command
        ArrayList<String> mentionedEntities = commandParser.getMentionedEntities(actionCommand, allGameEntities);

        // prepare for ambiguous commands defense: create a list for valid candidate ations
         ArrayList<GameAction> validActions = new ArrayList<>();

        for (GameAction gameAction : gameActions) {
            // 2. trigger word check: does the command contain a valid trigger for this action?
            if (commandParser.isActionTriggered(gameAction, actionCommand)) {
                // 3. partial command rule: at least 1 subject must be explicitly mentioned
                if (commandParser.countMentionedSubjects(gameAction, actionCommand) >= 1) {

                    // 4. extraneous entities defense line
                    boolean hasExtraneous = false;
                    for (String entityName : mentionedEntities) {
                        // if the mentioned entity is neither a subject, nor consumed, nor produced by this action...
                        if (!gameAction.getSubjects().contains(entityName) &&
                            !gameAction.getConsumed().contains(entityName) &&
                            !gameAction.getProduced().contains(entityName)) {

                            hasExtraneous = true;
                            break;
                        }
                    }

                    // if the command contains extraneous entities for this specific action
                    // skip this action and proceed to check the next one.
                    if (hasExtraneous) {
                        continue;
                    }

                    // 5. physical verification : are the required subjects actually in the room or inventory?
                    if (verifySubjects(currentPlayer, gameAction)) {
                        validActions.add(gameAction);
                    }
                }
            }
        }


        // 6. final verdict: handle "ambiguous commands"
        if (validActions.isEmpty()) {
            return "You cannot do that here, or you do not have the required items.";
        }
        else if (validActions.size() > 1) {
            // ambiguity caught1 multiple actions are fully valid and performable
            // (e.h, "open door" or "open car" with a shared key
            return "Error: Ambiguous command. There is more than one valid action possible.";
        } else {
            GameAction finalGameAction = validActions.get(0);
            boolean isDead = executeEffects(currentPlayer, finalGameAction);

            if (!isDead) {
                return finalGameAction.getNarration();
            } else {
                return "You died and lost all of your items, you must return to the start if the game";
            }
        }
    }


    /**
     * Helper 2: Subject Verification
     */
    private boolean verifySubjects(Player currentPlayer, GameAction gameAction) {
        Location currentLocation = currentPlayer.getCurrentLocation();

        for (String subject : gameAction.getSubjects()) {
            boolean inInv = currentPlayer.getInventory().containsKey(subject);
            boolean inRoomArtefacts = currentLocation.getAllArtefacts().containsKey(subject);
            boolean inRoomFurniture = currentLocation.getAllFurniture().containsKey(subject);
            boolean inRoomCharacters = currentLocation.getAllCharacters().containsKey(subject);

            // If a required subject is nowhere to be found, verification fails immediately
            if (!inInv && !inRoomArtefacts && !inRoomFurniture && !inRoomCharacters) {
                return false;
            }
        }
        return true; // All subjects are present

    }

    /**
     * Helper 3: Mass and Energy Transfer (Executing Effects)
     */
    private boolean executeEffects(Player player, GameAction action) {
        processConsumedEntities(player, action);
        processProducedEntities(player, action);
        return checkAndHandlePlayerDeath(player);
    }


    private boolean hasMultipleCommands(String actionCommand) {
        if (actionCommand.contains("and")) return true;

        int actionCount = 0;

        // 1. scan the basic command
        if (actionCommand.contains("look")) actionCount++;
        if (actionCommand.contains("inv") || actionCommand.contains("inventory")) actionCount++;
        if (actionCommand.contains("get")) actionCount++;
        if (actionCommand.contains("drop")) actionCount++;
        if (actionCommand.contains("goto")) actionCount++;

        // 2. scan the custom action
        boolean foundCustomAction = false;
        for (GameAction gameAction : gameActions) {
            for (String trigger : gameAction.getTriggers()) {
                if (actionCommand.contains(trigger)) {
                    // find any custom trigger, the foundCustomAction is true
                    foundCustomAction = true;
                    break;
                }
            }
        }
        // Normalize matched actions by name:
        // multiple matches with the same name are counted as one logical action
        // to avoid ambiguity being treated as multiple actions.
        if (foundCustomAction) {
            actionCount++;
        }

        // 3. if actionCount > 1 that is illegal
        return actionCount > 1;
    }

    /**
     * process the consumed entities
     * @param player
     * @param action
     */
    private void processConsumedEntities(Player player, GameAction action) {
        Location currentLocation = player.getCurrentLocation();
        Location storeroom = gameMap.get("storeroom");

        for (String entityName : action.getConsumed()) {
            if (currentLocation.getAllArtefacts().containsKey(entityName)) {
                storeroom.addArtefact(currentLocation.removeArtefact(entityName));
            } else if (player.getInventory().containsKey(entityName)) {
                storeroom.addArtefact(player.removeArtefact(entityName));
            } else if (currentLocation.getAllFurniture().containsKey(entityName)) {
                storeroom.addFurniture(currentLocation.removeFurniture(entityName));
            } else if (currentLocation.getAllCharacters().containsKey(entityName)) {
                storeroom.addCharacter(currentLocation.removeCharacter(entityName));
            } else if (gameMap.containsKey(entityName)) {
                currentLocation.removePath(entityName);
            } else if (entityName.equals("health")) {
                player.decreaseHealth();
            }
        }
    }

    /**
     * process the produced entities
     * @param player
     * @param action
     */
    private void processProducedEntities(Player player, GameAction action) {
        Location currentLocation = player.getCurrentLocation();
        Location storeroom = gameMap.get("storeroom");

        for (String entityName : action.getProduced()) {
            if (storeroom.getAllArtefacts().containsKey(entityName)) {
                currentLocation.addArtefact(storeroom.removeArtefact(entityName));
            } else if (storeroom.getAllFurniture().containsKey(entityName)) {
                currentLocation.addFurniture(storeroom.removeFurniture(entityName));
            } else if (gameMap.containsKey(entityName)) {
                currentLocation.addPath(gameMap.get(entityName));
            } else if (entityName.equals("health")) {
                player.increaseHealth();
            }
        }
    }

    /**
     * check and handle player's death
     * @param player
     * @return
     */
    private boolean checkAndHandlePlayerDeath(Player player) {
        if (player.getHealth() == 0) {
            Location currentLocation = player.getCurrentLocation();
            HashMap<String, Artefact> inv = player.getInventory();

            // all the items to be dropped
            ArrayList<Artefact> itemsToDrop = new ArrayList<>(inv.values());
            for (Artefact artefact : itemsToDrop) {
                player.removeArtefact(artefact.getName());
                currentLocation.addArtefact(artefact);
            }

            //
            player.setCurrentLocation(startingLocation);
            player.setHealth(MAX_HEALTH);
            return true; // the player is dead
        }
        return false; // the player is alive
    }


    /**
    * Do not change the following method signature or we won't be able to mark your submission
    * Starts a *blocking* socket server listening for new connections.
    *
    * @param portNumber The port to listen on.
    * @throws IOException If any IO related operation fails.
    */
    public void blockingListenOn(int portNumber) throws IOException {
        try (ServerSocket s = new ServerSocket(portNumber)) {
            System.out.println("Server listening on port " + portNumber);
            while (!Thread.interrupted()) {
                try {
                    blockingHandleConnection(s);
                } catch (IOException e) {
                    System.out.println("Connection closed");
                }
            }
        }
    }

    /**
    * Do not change the following method signature or we won't be able to mark your submission
    * Handles an incoming connection from the socket server.
    *
    * @param serverSocket The client socket to read/write from.
    * @throws IOException If any IO related operation fails.
    */
    private void blockingHandleConnection(ServerSocket serverSocket) throws IOException {
        try (Socket s = serverSocket.accept();
        BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()))) {
            System.out.println("Connection established");
            String incomingCommand = reader.readLine();
            if(incomingCommand != null) {
                System.out.println("Received message from " + incomingCommand);
                String result = handleCommand(incomingCommand);
                writer.write(result);
                writer.write("\n" + END_OF_TRANSMISSION + "\n");
                writer.flush();
            }
        }
    }
}
