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
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public final class GameServer {

    private static final char END_OF_TRANSMISSION = 4;
    private HashMap<String, Location> gameMap = new HashMap<>();
    private HashMap<String, Player> players = new HashMap<>();
    private HashSet<GameAction> gameActions = new HashSet<>();
    private Location startingLocation = null;

    public static void main(String[] args) throws IOException {
        File entitiesFile = Paths.get("config" + File.separator + "basic-entities.dot").toAbsolutePath().toFile();
        File actionsFile = Paths.get("config" + File.separator + "basic-actions.xml").toAbsolutePath().toFile();
        GameServer server = new GameServer(entitiesFile, actionsFile);
        server.blockingListenOn(8888);
    }

    /**
    * Do not change the following method signature or we won't be able to mark your submission
    * Instanciates a new server instance, specifying a game with some configuration files
    *
    * @param entitiesFile The game configuration file containing all game entities to use in your game
    * @param actionsFile The game configuration file containing all game actions to use in your game
    */
    public GameServer(File entitiesFile, File actionsFile) {
        // TODO implement your server logic here
        loadEntitiesFile(entitiesFile);
        try {
            loadActionsFile(actionsFile);
        } catch (Exception e) {
            System.err.println("Error loading actions file");
            e.printStackTrace();
        }

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
            String actionCommand = cleanCommand(partsOfCommand[1]);

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
            String firstActionToken = actionCommand.split(" ")[0];
            switch (firstActionToken) {
                case "inventory":
                case "inv":
                    return handleInv(currentPlayer);
                case "get":
                    return handleGet(currentPlayer, actionCommand);
                case "drop":
                    return handleDrop(currentPlayer, actionCommand);
                case "goto":
                    return handleGoto(currentPlayer, actionCommand);
                case "look":
                    return handleLook(currentPlayer);
                default:
                    return handleCustomAction(currentPlayer, actionCommand);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: Something went wrong executing your command: " + command;
        }
        //return "";
    }



    /**
     * Parse the file to the graph
     *
     * @param entitiesFile The file to parse
     */
    private void loadEntitiesFile(File entitiesFile) {
        try {
            // 1. Create a new parser
            Parser parser = new Parser();
            // 2. FileReader
            FileReader reader = new FileReader(entitiesFile);
            // 3. Parse
            parser.parse(reader);
            // 4. Get the layout graph
            Graph layoutGraph = parser.getGraphs().get(0);
            // 5. Get the subgraph locations and paths
            ArrayList<Graph> topSubgraphs = layoutGraph.getSubgraphs();
            for (Graph subgraph : topSubgraphs) {
                String graphId = subgraph.getId().getId();

                if (graphId.equals("locations")) {
                    parseLocations(subgraph);
                } else if (graphId.equals("paths")) {
                    parsePaths(subgraph);
                }
            }

        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }

    }

    /**
     * Parse the actionFile
     * @param actionsFile The file of actions to be parsed
     */
    private void loadActionsFile(File actionsFile) throws IOException, SAXException, ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(actionsFile);
        document.getDocumentElement().normalize();
        // Get all the <action> nodes
        NodeList nodeList = document.getElementsByTagName("action");
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node actioNode = nodeList.item(i);
            if (actioNode.getNodeType() == Node.ELEMENT_NODE) {
                Element actionElement = (Element) actioNode;

                HashSet<String> triggersSet = extractEntities(actionElement,"triggers","keyphrase");
                HashSet<String> subjectsSet = extractEntities(actionElement,"subjects","entity");
                HashSet<String> consumedSet = extractEntities(actionElement,"consumed","entity");
                HashSet<String> producedSet = extractEntities(actionElement,"produced","entity");
                String narration = "";

                NodeList narrationList = actionElement.getElementsByTagName("narration");
                if (narrationList.getLength() > 0) {
                    narration = narrationList.item(0).getTextContent();
                }
                GameAction currentAction = new GameAction(triggersSet, subjectsSet, consumedSet, producedSet, narration);
                gameActions.add(currentAction);
            }
        }

    }

    /**
     * extract the text from specified layer
     * @param actionElement
     * @param parentTag
     * @param childTag
     * @return
     */
    private HashSet<String> extractEntities(Element actionElement, String parentTag, String childTag) {
        HashSet<String> resultSet = new HashSet<>();
        // Find the parent
        NodeList parentList = actionElement.getElementsByTagName(parentTag);
        if  (parentList.getLength() > 0) {
            Element parentElement = (Element) parentList.item(0);

            // Find the inner child
            NodeList childList = parentElement.getElementsByTagName(childTag);

            for (int i = 0; i < childList.getLength(); i++) {
                resultSet.add(childList.item(i).getTextContent());
            }
        }
        return resultSet;
    }

    /**
     * Parse the locations
     * @param locationsGraph The graph of locations
     */
    private void parseLocations(Graph locationsGraph) {
        try {
            ArrayList<Graph> locationsGraphSubgraphs = locationsGraph.getSubgraphs();
            for (Graph cluster : locationsGraphSubgraphs) {
                // get the nodes of this cluster
                ArrayList<com.alexmerz.graphviz.objects.Node> nodes = cluster.getNodes(false);
                if (nodes.size() > 0) {
                    com.alexmerz.graphviz.objects.Node locNode = nodes.get(0);
                    String locationName = locNode.getId().getId();
                    String locationDescription = locNode.getAttribute("description");

                    if (locationDescription != null) {
                        Location currentLocation = new Location(locationName, locationDescription);
                        gameMap.put(locationName, currentLocation);

                        if (startingLocation == null) {
                            startingLocation = currentLocation;
                        }

                        // after the room is built, put all the entities in the room immediately
                        parseLocationEntities(currentLocation, cluster);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parse the entities in the location
     * @param location currentLocation
     * @param clusterGraph
     */
    private void parseLocationEntities(Location location, Graph clusterGraph) {
        // artefacts & furniture & character
        for (Graph subgraph : clusterGraph.getSubgraphs()) {
            String graphId = subgraph.getId().getId();
            for (com.alexmerz.graphviz.objects.Node node : subgraph.getNodes(false)) {

                String name = node.getId().getId();
                String description = node.getAttribute("description");

                // Real node has the description
                if (description != null) {
                    switch (graphId) {
                        case "artefacts":
                            location.addArtefact(new Artefact(name, description));
                            // System.out.println("Artefacts added." + name + " -> " + location.getName());
                            break;
                        case "furniture":
                            location.addFurniture(new Furniture(name, description));
                            break;
                        case "characters":
                            location.addCharacter(new Character(name, description));
                            break;
                    }
                }
            }
        }
    }

    /**
     * Parse the paths
     * @param pathsGraph The graph of path
     */
    private void parsePaths(Graph pathsGraph) {
        try {
            ArrayList<Edge> edges = pathsGraph.getEdges();
            for (Edge edge : edges) {
                // Get the name of start
                String fromName = edge.getSource().getNode().getId().getId();
                // Get the name of destination
                String toName = edge.getTarget().getNode().getId().getId();

                // Get the start location and destination location
                Location startLocation = gameMap.get(fromName);
                Location endLocation = gameMap.get(toName);
                if (startLocation != null && endLocation != null) {
                    startLocation.addPath(endLocation);
                } else {
                    System.out.println("Warning: try to connect non-existent location: " + fromName + " -> " + toName);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
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
     * Pickes up a specified artefact from current location
     * and adds it to the player's inventory
     *
     * @param
     * @return
     */
    private String handleGet(Player player, String actionCommand) {
        Location currentLocation = player.getCurrentLocation();
        HashMap<String, Artefact> roomArtefacts = currentLocation.getAllArtefacts();
        // multiple entities to get
        // to check what is mentioned in the actionCommand from allArtefacts
        ArrayList<Artefact> mentionedArtefacts = new ArrayList<>();
        for (String artefactName : roomArtefacts.keySet()){
            if (actionCommand.contains(artefactName)){
                mentionedArtefacts.add(roomArtefacts.get(artefactName));
            }
        }
        // if nothing is mentioned
        if (mentionedArtefacts.isEmpty()) {
            return "There is nothing like that here to pick up.";
        }

        StringBuilder pickedUpItems = new StringBuilder();
        for (Artefact artefact : mentionedArtefacts) {
            String artefactName = artefact.getName();
            currentLocation.removeArtefact(artefactName);
            player.addArtefact(artefact);
            pickedUpItems.append(artefactName).append(",");
        }
        // strip ',' in the end
        String result = pickedUpItems.toString();
        result = result.substring(0, result.length() - 1);

        return "You picked up: " + result + ".";

    }

    /**
     * Moves the player to a new location
     * (only if there is a valid path to that location)
     *
     * @param
     * @return
     */
    private String handleGoto(Player player, String actionCommand) {

        return "";
    }

    /**
     * Puts down an artefact from player's inventory
     * and places it into the current location
     *
     * @param
     * @return
     */
    private String handleDrop(Player player, String actionCommand) {
        Location currentLocation = player.getCurrentLocation();
        HashMap<String, Artefact> inventory = player.getInventory();
        //
        ArrayList<Artefact> mentionedArtefacts = new ArrayList<>();
        for (String artefactName : inventory.keySet()) {
            if  (actionCommand.contains(artefactName)) {
                mentionedArtefacts.add(inventory.get(artefactName));
            }
        }
        if (mentionedArtefacts.isEmpty()) {
            return "There is nothing like that here to drop.";
        }
        StringBuilder droppedItems = new StringBuilder();
        for (Artefact targetArtefact : mentionedArtefacts) {
            String artefactName = targetArtefact.getName();
            // player drop the item to the currentLocation
            player.removeArtefact(artefactName);
            currentLocation.addArtefact(targetArtefact);
            droppedItems.append(artefactName).append(",");
        }
        String result = droppedItems.toString();
        result = result.substring(0, result.length() - 1);
        return "You dropped: " + result + ".";

    }

    /**
     * Describes the current location,
     * including all entities in that location and paths to other locations
     *
     * @param
     * @return
     */
    private String handleLook(Player player) {
        // get the current room where current player is in
        Location currentLocation = player.getCurrentLocation();

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
        // System.out.println(result.toString());
        return result.toString();

    }

    /**
     * clean the command
     * strip all the punctuations
     * @param rawCommand
     * @return
     */
    private String cleanCommand(String rawCommand) {
        // 1. convert command to lower case
        String cleanStr = rawCommand.toLowerCase();
        // 2. strip all of punctuations
        cleanStr = cleanStr.replace("\\p{Punct}", " ");
        // 3. combine multiple and consecutive space to one space
        cleanStr = cleanStr.replace("\\s+", "").trim();

        return cleanStr;
    }

    private String handleCustomAction(Player currentPlayer, String actionCommand) {
        return "";
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
