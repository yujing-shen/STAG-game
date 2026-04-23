package edu.uob;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.HashSet;

/**
 * A dedicated parser for reading and extracting custom game actions
 * from the actions XML configuration file.
 */
public class XMLParser {
    private HashSet<GameAction> gameActions;

    public XMLParser(HashSet<GameAction> gameActions) {
        this.gameActions = gameActions;
    }

    public XMLParser() {

    }

    /**
     * Parses the given XML file and populates the gameActions set.
     * Any exceptions during parsing are caught and printed to standard error,
     * fulfilling the requirement to handle file loading gracefully.
     * * @param actionsFile The XML file containing custom game actions.
     */
    public void parse(File actionsFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(actionsFile);
            document.getDocumentElement().normalize();

            // Retrieve all <action> nodes from the document
            NodeList nodeList = document.getElementsByTagName("action");
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node actionNode = nodeList.item(i);

                if (actionNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element actionElement = (Element) actionNode;

                    // Extract required sets of entities
                    HashSet<String> triggersSet = extractEntities(actionElement, "triggers", "keyphrase");
                    HashSet<String> subjectsSet = extractEntities(actionElement, "subjects", "entity");
                    HashSet<String> consumedSet = extractEntities(actionElement, "consumed", "entity");
                    HashSet<String> producedSet = extractEntities(actionElement, "produced", "entity");
                    String narration = "";

                    // Extract the narration string if it exists
                    NodeList narrationList = actionElement.getElementsByTagName("narration");
                    if (narrationList.getLength() > 0) {
                        narration = narrationList.item(0).getTextContent();
                    }

                    // Create the GameAction object and add it to our collection
                    GameAction currentAction = new GameAction(triggersSet, subjectsSet, consumedSet, producedSet, narration);
                    gameActions.add(currentAction);
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing XML actions file: " + e.getMessage());
        }
    }

    /**
     * Getter method for the server to retrieve the parsed actions.
     * * @return A HashSet containing all successfully parsed GameActions.
     */
    public HashSet<GameAction> getGameActions() {
        return gameActions;
    }

    /**
     * Helper method to extract text content from specific inner child tags
     * (e.g., extracting "keyphrase" strings from within the "triggers" block).
     * * @param actionElement The root action XML element.
     * @param parentTag The name of the parent block (e.g., "subjects").
     * @param childTag The name of the child node containing the text (e.g., "entity").
     * @return A HashSet of strings extracted from the specified tags.
     */
    private HashSet<String> extractEntities(Element actionElement, String parentTag, String childTag) {
        HashSet<String> resultSet = new HashSet<>();

        // Find the parent block
        NodeList parentList = actionElement.getElementsByTagName(parentTag);
        if (parentList.getLength() > 0) {
            Element parentElement = (Element) parentList.item(0);

            // Find all inner child nodes
            NodeList childList = parentElement.getElementsByTagName(childTag);
            for (int i = 0; i < childList.getLength(); i++) {
                resultSet.add(childList.item(i).getTextContent());
            }
        }
        return resultSet;
    }
}
