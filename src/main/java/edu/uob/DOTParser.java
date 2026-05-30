package edu.uob;

import com.alexmerz.graphviz.Parser;
import com.alexmerz.graphviz.objects.Edge;
import com.alexmerz.graphviz.objects.Graph;
import edu.uob.entities.Artefact;
import edu.uob.entities.Character;
import edu.uob.entities.Furniture;
import edu.uob.entities.Location;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * A dedicated parser for reading and extracting the game map, locations, paths
 * and all entities from the DOT configuration file.
 */
public class DOTParser {
    private HashMap<String, Location> gameMap = new HashMap<>();
    // A comprehensive set containing the names of EVERY entity in the game
    // (locations, artefacts, furniture, characters) for Extraneous Entity checking
    private HashSet<String> allGameEntities = new HashSet<>();

    // The designated starting location for all new players
    private Location startingLocation = null;

    public DOTParser(HashMap<String, Location> gameMap, HashSet<String> allGameEntities, Location startingLocation) {
        this.gameMap = new HashMap<>();
        this.allGameEntities = new HashSet<>();
        this.startingLocation = null;
    }

    public DOTParser() {

    }

    /**
     * Parses the provided DOT file to construct the game world.
     * @param entitiesFile The DOT file containing game map and entities.
     */
    public void parseEntitiesFile(File entitiesFile) {
        try {
            Parser parser = new Parser();
            FileReader fileReader = new FileReader(entitiesFile);
            parser.parse(fileReader);

            // extract the top-level layout graph
            Graph layoutGraph = parser.getGraphs().get(0);
            ArrayList<Graph> topSubgraphs = layoutGraph.getSubgraphs();

            // process locations and paths subgraphs separately
            for  (Graph subgraph : topSubgraphs) {
                String graphId = subgraph.getId().getId();
                if (graphId.equals("locations")) {
                    parseLocations(subgraph);
                } else if (graphId.equals("paths")) {
                    parsePaths(subgraph);
                }
            }

            // automatically generate a 'storeroom' if it does not exist
            if (!gameMap.containsKey("storeroom")) {
                Location storeroom = new Location("storeroom",
                        "Storage for any entities not placed in the game");
                gameMap.put("storeroom", storeroom);
                allGameEntities.add("storeroom");
            }

        } catch (Exception e) {
            System.err.println("Error parsing DOT file: " + e.getMessage());
        }
    }

    public HashMap<String, Location> getGameMap() {
        return gameMap;
    }

    public HashSet<String> getAllGameEntities() {
        return allGameEntities;
    }

    public Location getStartingLocation() {
        return startingLocation;
    }

    /**
     * Parses the locations graph to instantiate Location objects.
     * * @param locationsGraph The subgraph containing all location clusters.
     */
    private void parseLocations(Graph locationsGraph) {
        ArrayList<Graph> locationsGraphSubgraphs = locationsGraph.getSubgraphs();
        for (Graph cluster : locationsGraphSubgraphs) {
            ArrayList<com.alexmerz.graphviz.objects.Node> nodes = cluster.getNodes(false);
            if (nodes.size() > 0) {
                com.alexmerz.graphviz.objects.Node locNode = nodes.get(0);
                String locationName = locNode.getId().getId();
                String locationDescription = locNode.getAttribute("description");

                if (locationDescription != null) {
                    Location currentLocation = new Location(locationName, locationDescription);
                    gameMap.put(locationName, currentLocation);

                    // The very first location parsed is set as the starting location
                    if (startingLocation == null) {
                        startingLocation = currentLocation;
                    }

                    // Parse internal entities (artefacts, furniture, characters) for this location
                    parseLocationEntities(currentLocation, cluster);
                }
            }
        }
    }

    /**
     * Extracts entities within a specific location cluster and registers them.
     * * @param location The Location object to add entities to.
     * @param clusterGraph The graph cluster representing this location.
     */
    private void parseLocationEntities(Location location, Graph clusterGraph) {
        // CRITICAL: Add the location's own name to the global entity list
        allGameEntities.add(location.getName());

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
                            allGameEntities.add(name);
                            break;
                        case "furniture":
                            location.addFurniture(new Furniture(name, description));
                            allGameEntities.add(name);
                            break;
                        case "characters":
                            location.addCharacter(new Character(name, description));
                            allGameEntities.add(name);
                            break;
                    }
                }
            }
        }
    }

    /**
     * Parses the paths graph to establish directional links between locations.
     * * @param pathsGraph The subgraph containing edges representing paths.
     */
    private void parsePaths(Graph pathsGraph) {
        ArrayList<Edge> edges = pathsGraph.getEdges();
        for (Edge edge : edges) {
            String fromName = edge.getSource().getNode().getId().getId();
            String toName = edge.getTarget().getNode().getId().getId();

            Location startLocation = gameMap.get(fromName);
            Location endLocation = gameMap.get(toName);

            // Establish the connection if both locations exist
            if (startLocation != null && endLocation != null) {
                startLocation.addPath(endLocation);
            } else {
                System.err.println("Warning: Attempted to connect non-existent location: " + fromName + " -> " + toName);
            }
        }
    }
}
