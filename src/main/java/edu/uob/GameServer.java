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
import java.util.Map;

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
    * <p>Initialisation process:</p>
    * <ol>
    *   <li>Parse the DOT entities file to build the game world map ({@code gameMap}),
    *       identify the starting location, and collect all entity names into
    *       {@code allGameEntities} for later NLP matching.</li>
    *   <li>Parse the XML actions file to load all custom game actions
    *       (triggers, subjects, consumed, produced, narration) into {@code gameActions}.</li>
    * </ol>
    * <p>After construction, the server is ready to accept player commands via
    * {@link #handleCommand(String)}.</p>
    *
    * @param entitiesFile The game configuration file (.dot) containing all game entities
    *                     (locations, artefacts, furniture, characters) and paths
    * @param actionsFile  The game configuration file (.xml) containing all custom game actions
    *                     (triggers, subjects, consumed, produced, narration)
    */
    public GameServer(File entitiesFile, File actionsFile) {
        // TODO implement your server logic here

        // 1. Parse DOT file for entities and world map
        DOTParser dotParser = new DOTParser();
        dotParser.parseEntitiesFile(entitiesFile);

        // Retrieve parsed map data
        this.gameMap = dotParser.getGameMap();
        this.startingLocation = dotParser.getStartingLocation();
        this.allGameEntities = dotParser.getAllGameEntities();

        // 2. Parse XML file for custom actions
        XMLParser xmlParser = new XMLParser();
        xmlParser.parseXMLGraph(actionsFile);

        // Retrieve parsed action data
        this.gameActions = xmlParser.getGameActions();

    }

    /**
    * This method handles all incoming game commands and carries out the corresponding actions.</p>
    *
    * <p>Command processing pipeline:</p>
    * <ol>
    *   <li><b>Validation</b> &mdash; reject null/empty commands and malformed format
    *       (must follow {@code "name:action"}).</li>
    *   <li><b>Sanitisation</b> &mdash; extract and trim the player name, clean the action
    *       string via {@link CommandParser#cleanCommand(String)} (lowercase, collapse whitespace).</li>
    *   <li><b>Multi-command guard</b> &mdash; reject composite commands that trigger more than
    *       one logical action via {@link #hasMultipleCommands(String)}.</li>
    *   <li><b>Player resolution</b> &mdash; look up an existing player or create a new one
    *       placed at the starting location (multiplayer support).</li>
    *   <li><b>Dispatch</b> &mdash; route the cleaned action to the appropriate handler:
    *       <ul>
    *         <li>Built-in commands: {@code inv}, {@code get}, {@code drop}, {@code goto},
    *             {@code look}, {@code health}</li>
    *         <li>Custom actions: {@link #handleCustomAction(Player, String)}</li>
    *       </ul>
    *   </li>
    * </ol>
    *
    * @param command The incoming command string in the format {@code "playerName:action"}
    * @return A response string describing the result of the action, or an error message
    */
    public String handleCommand(String command) {
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
     * Handles the built-in {@code inv} / {@code inventory} command.
     *
     * <p>Returns a formatted list of all artefacts currently held by the player.
     * If the player's inventory is empty, returns a descriptive message instead.</p>
     *
     * @param player The current player whose inventory is being inspected
     * @return A string listing the player's carried artefacts, or an empty-inventory message
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
        String targetEntityName = extractSingleTargetEntity(actionCommand);

        if (targetEntityName.equals("ERROR_EMPTY")) return "There is nothing like that here to pick up.";
        if (targetEntityName.equals("ERROR_MULTIPLE")) return "Error: Extraneous entities detected. You can only specify one item at a time.";

        Location currentLocation = player.getCurrentLocation();
        HashMap<String, Artefact> roomArtefacts = currentLocation.getAllArtefacts();

        if (roomArtefacts.containsKey(targetEntityName)) {
            player.addArtefact(roomArtefacts.remove(targetEntityName));
            return "You picked up a " + targetEntityName + ".";
        }
        return "Error: You cannot get that here.";
    }

    /**
     * Updates the player's current location by navigating through an available path.
     * <p>
     *     This method validates the command structure to ensure exactly one destination
     *     location is specified, verifies topological connectivity from the current room,
     *     and updates the player's spatial state upon success.
     * </p>
     *
     * @param player        The active {@link Player} instance executing the navigation.
     * @param actionCommand The sanitized natural language command string from the client.
     * @return              A status narration  string representing the outcome or specific error context.
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
        String targetEntityName = extractSingleTargetEntity(actionCommand);

        if (targetEntityName.equals("ERROR_EMPTY")) return "You are not meant to drop anything";
        if (targetEntityName.equals("ERROR_MULTIPLE")) {
            return "ERROR: Extraneous entities detected. You can only specify one item at a time.";
        }

        Location currentLocation = player.getCurrentLocation();
        HashMap<String, Artefact> inventory = player.getInventory();

        if (inventory.containsKey(targetEntityName)) {
            currentLocation.addArtefact(inventory.remove(targetEntityName));
            return "You dropped a " + targetEntityName + ".";
        }
        return "ERROR: You cannot drop that here.";
    }

    /**
     * Handles the built-in {@code look} command.
     *
     * <p>Generates a comprehensive description of the player's current location,
     * assembling the following sections in order:</p>
     * <ol>
     *   <li>Location description</li>
     *   <li>Artefacts present in the location</li>
     *   <li>Furniture present in the location</li>
     *   <li>Characters present in the location</li>
     *   <li>Paths accessible from the location</li>
     *   <li>Other players in the same location (multiplayer visibility)</li>
     * </ol>
     * <p>Empty sections are omitted from the output.</p>
     *
     * @param currentPlayer The player executing the look command
     * @return A formatted string describing everything visible in the current location
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
     * Processes complex natural language commands against all defined custom XML game actions.
     * <p>
     * This core engine method acts as strict filtering pipeline. It evaluates the command
     * against all loaded game actions by enforcing the following NLP business rules:
     * <ul>
     *     <li><b>Partial Commands:</b> At least one valid subject must be explicitly mentioned.</li>
     *     <li><b>Extraneous Entities:</b> The action is bypassed if the command entities unrelated to it.</li>
     *     <li><b>Ambiguity Resolution:</b> If multiple actions are fully performable, execution is aborted.</li>
     *     <li><b>State Mutation:</b> Upon finding a unique valid action, it triggers physical effects and evaluates player health.</li>
     * </ul>
     * </p>
     *
     * @param currentPlayer The active {@link Player} initiating the custom action.
     * @param actionCommand The sanitized natural language command string.
     * @return The action narration, a death sequence message, or an appropriate error context.
     */
    private String handleCustomAction(Player currentPlayer, String actionCommand) {
        // Phase 1: Entity Extraction
        // Extract all recognized game entities from the raw command for subsequent validation
        ArrayList<String> mentionedEntities = commandParser.getMentionedEntities(actionCommand, allGameEntities);

        // prepare for ambiguous commands defense: create a list for valid candidate ations
        ArrayList<GameAction> validActions = new ArrayList<>();

        // Phase 2: Action Candidate Filtering
        for (GameAction gameAction : gameActions) {
            //  Check if the command contains the correct trigger verb and at least one core subject
            if (commandParser.isActionTriggered(gameAction, actionCommand)) {

                // Phase 3: Extraneous Entity Defense (Strict Validation)
                // Reject the action if the user mentions items not involved in this specific action's lifecycle
                if (commandParser.countMentionedSubjects(gameAction, actionCommand) >= 1) {

                    boolean hasExtraneous = false;
                    for (String entityName : mentionedEntities) {
                        // if the mentioned entity is neither a subject, nor consumed, nor produced by this action...
                        if (!gameAction.getSubjects().contains(entityName) &&
                            !gameAction.getConsumed().contains(entityName) &&
                            !gameAction.getProduced().contains(entityName)) {
                            hasExtraneous = true;
                            break; // Extraneous entity found, abort checking this action
                        }
                    }

                    // Bypass this action if it violates the extraneous entities rule
                    if (hasExtraneous) {
                        continue;
                    }

                    // Phase 4: Physical State Verification
                    // Confirm the player possesses the required subjects (either in inventory or current room)
                    if (verifySubjects(currentPlayer, gameAction)) {
                        validActions.add(gameAction);
                    }
                }
            }
        }

        // Phase 5: Ambiguity Resolution & Execution
        if (validActions.isEmpty()) {
            return "You cannot do that here, or you do not have the required items.";
        }
        else if (validActions.size() > 1) {
            // Defense against ambiguous overlapping triggers (e.g., "open" with shared subjects)
            return "Error: Ambiguous command. There is more than one valid action possible.";
        } else {
            // Exactly one valid action identified: Execute state mutations and evaluate health
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
            boolean isCurrentLocation = currentLocation.getName().equals(subject);
            // If a required subject is nowhere to be found, verification fails immediately
            if (!inInv && !inRoomArtefacts && !inRoomFurniture && !inRoomCharacters && !isCurrentLocation) {
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
        return handlePlayerDeath(player);
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
            // Duplicate defense: if entity already exists in any non-storeroom location, skip it
            if (isEntityAlreadyInGame(entityName)) {
                continue;
            }
            if (storeroom.getAllArtefacts().containsKey(entityName)) {
                currentLocation.addArtefact(storeroom.removeArtefact(entityName));
            } else if (storeroom.getAllFurniture().containsKey(entityName)) {
                currentLocation.addFurniture(storeroom.removeFurniture(entityName));
            } else if (storeroom.getAllCharacters().containsKey(entityName)) {
                currentLocation.addCharacter(storeroom.removeCharacter(entityName));
            } else if (gameMap.containsKey(entityName)) {
                currentLocation.addPath(gameMap.get(entityName));
            } else if (entityName.equals("health")) {
                player.increaseHealth();
            }
        }
    }

    /**
     * Checks if a named entity already exists in any non-storeroom location.
     * Used to prevent duplicate entity creation.
     */
    private boolean isEntityAlreadyInGame(String entityName) {
        for (Map.Entry<String, Location> entry : gameMap.entrySet()) {
            if (entry.getKey().equals("storeroom")) continue;
            Location loc = entry.getValue();
            if (loc.getAllArtefacts().containsKey(entityName)) return true;
            if (loc.getAllFurniture().containsKey(entityName)) return true;
            if (loc.getAllCharacters().containsKey(entityName)) return true;
        }
        // Also check all players' inventories
        for (Player p : players.values()) {
            if (p.getInventory().containsKey(entityName)) return true;
        }
        return false;
    }

    /**
     * check and handle player's death
     * @param player
     * @return
     */
    private boolean handlePlayerDeath(Player player) {
        if (player.getHealth() == 0) {
            Location currentLocation = player.getCurrentLocation();
            HashMap<String, Artefact> inv = player.getInventory();

            // all the items to be dropped
            ArrayList<Artefact> itemsToDrop = new ArrayList<>(inv.values());
            for (Artefact artefact : itemsToDrop) {
                player.removeArtefact(artefact.getName());
                currentLocation.addArtefact(artefact);
            }

            player.setCurrentLocation(startingLocation);
            player.setHealth(MAX_HEALTH);
            return true; // the player is dead
        }
        return false; // the player is alive
    }

    /**
     * Helper to enforce DRY principle for entity extraction and validation.
     */
    private String extractSingleTargetEntity(String actionCommand){
        ArrayList<String> mentionedEntities = commandParser.getMentionedEntities(actionCommand, allGameEntities);
        if (mentionedEntities.isEmpty()) return "ERROR_EMPTY";
        if (mentionedEntities.size() > 1) return "ERROR_MULTIPLE";
        return mentionedEntities.get(0);

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
