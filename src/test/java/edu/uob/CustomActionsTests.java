package edu.uob;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.nio.file.Paths;
import java.time.Duration;

class CustomActionsTests {

    private GameServer server;

    @BeforeEach
    void setup() {
        File entitiesFile = Paths.get("config" + File.separator + "extended-entities.dot").toAbsolutePath().toFile();
        File actionsFile = Paths.get("config" + File.separator + "extended-actions.xml").toAbsolutePath().toFile();
        server = new GameServer(entitiesFile, actionsFile);
    }

    String sendCommandToServer(String command) {
        return assertTimeoutPreemptively(Duration.ofMillis(1000), () -> { return server.handleCommand(command);},
                "Server took too long to respond (probably stuck in an infinite loop)");
    }

    // Test the player chop a tree with axe
    @Test
    void testCustomActionChopTreeWithAxe() {
        sendCommandToServer("simon: please get the axe now");
        sendCommandToServer("simon: goto forest");
        sendCommandToServer("simon: please loudly chop the huge tree now");

        String lookResponse = sendCommandToServer("simon: look").toLowerCase();
        // 1. Assert the produced
        assertTrue(lookResponse.contains("log"), "After chopping the tree, the player should see the log");
        // 2. Assert the consumed
        assertFalse(lookResponse.contains("tree"), "After chopping the tree, the player should not see the tree since tree is in storeroom now.");
        // 3. Assert the inv
        String invResponse = sendCommandToServer("simon: inv").toLowerCase();
        assertTrue(invResponse.contains("axe"), "The axe should be in inventory");
    }

    // Test the player chop the tree WITHOUT axe (Test point: the INV does not have axe, not the command)
    @Test
    void testCustomActionChopTreeWithoutAxe() {
        sendCommandToServer("simon: please goto forest now.");
        String response = sendCommandToServer("simon: chop tree").toLowerCase();
        assertTrue(response.contains("cannot do that"), "You cannot do that without axe.");

        String lookResponse = sendCommandToServer("simon: look").toLowerCase();
        assertFalse(lookResponse.contains("log"), "The log cannot be seen as no tree is chopped.");
        assertTrue(lookResponse.contains("tree"), "The tree still exists.");
    }

    // Test the player chop the tree NOT in forest
    @Test
    void testCustomActionChopTreeNotInForest() {
        sendCommandToServer("simon: get the axe");
        String response = sendCommandToServer("simon: chop tree").toLowerCase();
        assertTrue(response.contains("cannot do that"), "You cannot do that since there is no tree in cabin.");

        String invResponse = sendCommandToServer("simon: inv").toLowerCase();
        assertTrue(invResponse.contains("axe"), "The axe should be in inventory");

        String lookResponse = sendCommandToServer("simon: look").toLowerCase();
        // EDGE CASE: Do NOT just assert "log".
        // The room's description is "a log cabin", which will cause a false positive.
        // Asserting "\nlog -" ensures we are only looking for the dropped artefact.
        assertFalse(lookResponse.contains("\nlog -"), "The log artefact should not be on the ground.");
        assertFalse(lookResponse.contains("heavy wooden"), "The tree cannot be seen as no tree is chopped.");
    }

    @Test
    void testStartLocationIsAlwaysFirst() {
        // The start location MUST be the first location in the entities file.
        String lookResponse = sendCommandToServer("simon: look").toLowerCase();
        // In both basic and extended files, 'cabin' is defined first.
        // If the parser used a non-ordered collection (like standard HashSet) and picked randomly, this would fail.
        assertTrue(lookResponse.contains("cabin"), "Entity Loading Error: The player did not spawn in the first location defined in the DOT file.");
    }

    @Test
    void testEntityCategorization() {
        // Ensure entities are loaded into their CORRECT categories.
        // E.g., Artefacts can be picked up, Furniture cannot.

        // 1. Try to pick up an Artefact (Should succeed)
        String getAxeResponse = sendCommandToServer("simon: get the axe").toLowerCase();
        assertFalse(getAxeResponse.contains("error") || getAxeResponse.contains("cannot"), "Entity Loading Error: 'axe' should be parsed as an Artefact and be collectible.");
        String invResponse = sendCommandToServer("simon: inv").toLowerCase();
        assertTrue(invResponse.contains("axe"), "The axe should be in inventory");

        // 2. Try to pick up Furniture (Should fail)
        String getTrapdoorResponse = sendCommandToServer("simon: get trapdoor").toLowerCase();
        assertTrue(getTrapdoorResponse.contains("error") || getTrapdoorResponse.contains("cannot"), "Entity Loading Error: 'trapdoor' should be parsed as Furniture and CANNOT be collected.");

        // 3. Verify they are printed in the correct sections during 'look'
        String lookResponse = sendCommandToServer("simon: look").toLowerCase();
        // Just as an extra layer of defense, ensure 'trapdoor' wasn't accidentally moved.
        assertTrue(lookResponse.contains("trapdoor"), "The trapdoor furniture should still be in the room.");
    }

    @Test
    void testStoreroomExistenceAndIsolation() {
        // The 'storeroom' must exist (either loaded or auto-created),
        // and it MUST NOT be accessible via normal paths.

        // 1. Try to blatantly walk into the storeroom (Should fail)
        String gotoResponse = sendCommandToServer("simon: goto storeroom").toLowerCase();
        assertTrue(gotoResponse.contains("error") || gotoResponse.contains("cannot"), "Map Loading Error: The 'storeroom' must not be accessible via normal movement commands.");

        // 2. Ensure current location hasn't changed to storeroom
        String lookResponse = sendCommandToServer("simon: look").toLowerCase();
        assertFalse(lookResponse.contains("storeroom"), "Player should not be inside the storeroom.");
    }

    @Test
    void testLocationAsProducedAndConsumedEntities() {
        // Test locations as Produced/Consumed entities.
        // 1. Verify that the path to 'cellar' does NOT exist initially
        String gotoResponse01 = sendCommandToServer("simon: goto cellar").toLowerCase();
        assertTrue(gotoResponse01.contains("error") || gotoResponse01.contains("cannot"), "Path Error: The path to cellar should not exist before the required action.");

        // Setup
        sendCommandToServer("simon: goto forest");
        sendCommandToServer("simon: get key");
        sendCommandToServer("simon: goto cabin");

        // 2. Trigger the action that produces the 'cellar' location
        sendCommandToServer("simon: open trapdoor");

        // 3. Verify the path is successfully produced and appended to the current location
        String lookResponse = sendCommandToServer("simon: look").toLowerCase();
        assertTrue(lookResponse.contains("cellar"), "Action Error: The path to 'cellar' should be generated and visible after opening the trapdoor.");

        // 4. Verify physical travel is now possible via the newly produced path
        String gotoResponse02 = sendCommandToServer("simon: goto cellar").toLowerCase();
        assertTrue(gotoResponse02.contains("cellar"), "Path Error: Player should be able to travel through the newly produced path to the cellar.");
    }

    @Test
    void testXMLActionParsingAndNarration() {
        // [Task 7 Validation] Verify that actions from the XML file are correctly parsed,
        // loaded into memory, and that the EXACT narration is returned upon success.

        // 1. Set up the state to perform a known action from extended-actions.xml
        sendCommandToServer("simon: goto forest");
        sendCommandToServer("simon: get key");
        sendCommandToServer("simon: goto cabin");

        // 2. Perform the action
        String actionResponse = sendCommandToServer("simon: unlock trapdoor with key").toLowerCase();

        // 3. Assert that the specific narration from the XML is returned
        assertTrue(actionResponse.contains("you unlock the door and see steps leading down into a cellar"),
                "XML Parsing Error: The server did not return the correct narration string parsed from the XML file.");

        sendCommandToServer("simon: get potion");
        String drinkResponse = sendCommandToServer("simon: drink potion").toLowerCase();

        // Assert it doesn't return an error, proving the alternative trigger was parsed successfully
        assertFalse(drinkResponse.contains("error") || drinkResponse.contains("cannot"),
                "XML Parsing Error: Alternative triggers for the same action were not parsed correctly.");
    }

    @Test
    void testLocationAsSubject() {
        // Edge Case Validation Context Sensitive Actions (Location as Subject)
        // Verifies that the player's CURRENT location is accepted as a valid subject.
        File testEntities = Paths.get("config" + File.separator + "basic-entities.dot").toAbsolutePath().toFile();
        File testActions = Paths.get("config" + File.separator + "test-location-subject-actions.xml").toAbsolutePath().toFile();
        GameServer locationServer = new GameServer(testEntities, testActions);

        // 1. Success Test: Player is in the cabin (starting location). Action requires 'cabin'.
        String successResponse = locationServer.handleCommand("simon: sleep in the cabin").toLowerCase();

        // Assert the action is successfully performed because 'cabin' is the current location
        assertTrue(successResponse.contains("comfortably"),
                "Location Subject Error: The server must accept the current room's name as a valid subject.");

        // 2. Move the player out of the cabin
        locationServer.handleCommand("simon: goto forest");

        // 3. Failure Test: Player tries to sleep in the cabin while physically in the forest.
        String failResponse = locationServer.handleCommand("simon: sleep in the cabin").toLowerCase();

        // Assert the action is rejected because the player is no longer in the cabin
        assertTrue(failResponse.contains("cannot do that") || failResponse.contains("error"),
                "Location Subject Error: The action should fail if the required location subject is NOT the current location.");
    }
}