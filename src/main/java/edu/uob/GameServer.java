package edu.uob;

import com.alexmerz.graphviz.objects.Edge;
import com.alexmerz.graphviz.objects.Graph;
import com.alexmerz.graphviz.Parser;
import com.alexmerz.graphviz.objects.Node;
import edu.uob.entities.Artefact;
import edu.uob.entities.Character;
import edu.uob.entities.Furniture;
import edu.uob.entities.Location;

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

public final class GameServer {

    private static final char END_OF_TRANSMISSION = 4;
    private HashMap<String, Location> gameMap;
    private HashMap<String, Location> playerMap;

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

    }

    /**
    * Do not change the following method signature or we won't be able to mark your submission
    * This method handles all incoming game commands and carries out the corresponding actions.</p>
    *
    * @param command The incoming command to be processed
    */
    public String handleCommand(String command) {
        // TODO implement your server logic here
        String firstToken = command.split(" ")[0];
        switch (firstToken) {
            case "inventory":
            case "inv":
                return handleInv(command);
            case "get":
                return handleGet(command);
            case "drop":
                return handleDrop(command);
            case "goto":
                return handleGoto(command);
            case "look":
                return handleLook(command);
            default:
                return "";
        }
        return "";
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
     * Parse the locations
     * @param locationsGraph The graph of locations
     */
    private void parseLocations(Graph locationsGraph) {
        try {
            ArrayList<Graph> locationsGraphSubgraphs = locationsGraph.getSubgraphs();
            for (Graph cluster : locationsGraphSubgraphs) {
                Location currentLocation = null;
                // Get all the nodes of the sub cluster
                // locations
                ArrayList<Node> nodes = cluster.getNodes(false);
                for (Node node : nodes) {
                    String locationName = node.getId().getId();
                    String locationDescription = node.getAttribute("description");

                    if (locationDescription != null) {
                        currentLocation = new Location(locationName, locationDescription);
                        // Add the location to the gameMap
                        gameMap.put(locationName, currentLocation);
                    }
                }

                // Ensure the location is parsed successfully
                if (currentLocation != null) parseLocationEntities(currentLocation,cluster);

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
            for (Node node : subgraph.getNodes(false)) {
                String name = node.getId().getId();
                String description = node.getAttribute("description");
                // Real node has the description
                if (description != null) {
                    switch (graphId) {
                        case "artefacts":
                            location.addArtefact(new Artefact(name, description));
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
     * @param command The client's command
     * @return
     */
    public String handleInv(String command) {

    }

    /**
     * Pickes up a specified artefact from current location
     * and adds it to the player's inventory
     *
     * @param command
     * @return
     */
    public String handleGet(String command) {

    }

    /**
     * Moves the player to a new location
     * (only if there is a valid path to that location)
     *
     * @param command
     * @return
     */
    public String handleGoto(String command) {

    }

    /**
     * Puts down an artefact from player's inventory
     * and places it into the current location
     *
     * @param command
     * @return
     */
    public String handleDrop(String command) {

    }

    /**
     * Describes the current location,
     * including all entities in that location and paths to other locations
     *
     * @param command
     * @return
     */
    public String handleLook(String command) {

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
