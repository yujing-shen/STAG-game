package edu.uob;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.nio.file.Paths;
import java.time.Duration;

class BasicCommandsTests {

    private GameServer server;

    @BeforeEach
    void setup() {
        File entitiesFile = Paths.get("config" + File.separator + "extended-entities.dot").toAbsolutePath().toFile();
        File actionsFile = Paths.get("config" + File.separator + "extended-actions.xml").toAbsolutePath().toFile();
        server = new GameServer(entitiesFile, actionsFile);
    }

    String sendCommandToServer(String command) {
        // Try to send a command to the server - this call will timeout if it takes too long (in case the server enters an infinite loop)
        return assertTimeoutPreemptively(Duration.ofMillis(1000), () -> { return server.handleCommand(command);},
                "Server took too long to respond (probably stuck in an infinite loop)");
    }

    @Test
    void testNullCommand() {
        String response = sendCommandToServer("simon: ");
        assertTrue(response.contains("Error: No action specified"), "The command is empty.");
    }

    @Test
    void testInvalidPlayerName() {
        String  response = sendCommandToServer("si@mon: ");
        assertTrue(response.contains("Error: Invalid player name"), "The player name is valid.");
    }

    @Test
    void testStatePersistence() {
        server.handleCommand("simon: get potion");
        // Verify the potion is no longer in the room
        String lookResponse1 = server.handleCommand("simon: look");
        assertFalse(lookResponse1.contains("potion"), "Potion should be picked up and no longer in the room.");

        // Instantiate a NEW GameServer to simulate a server restart
        File entitiesFile = Paths.get("config" + File.separator + "extended-entities.dot").toAbsolutePath().toFile();
        File actionsFile = Paths.get("config" + File.separator + "extended-actions.xml").toAbsolutePath().toFile();
        GameServer newServer = new GameServer(entitiesFile, actionsFile);

        // Player 'simon' looks around in the freshly started server
        String lookResponse2 = newServer.handleCommand("simon: look");

        // Assertion: The potion MUST be back in the starting room!
        // This proves the game state is loaded afresh and original config files were NOT modified.
        assertTrue(lookResponse2.contains("potion"), "State leaked! Potion should be back in the room after server restart.");
    }

    // A lot of tests will probably check the game state using 'look' - so we better make sure 'look' works well !
    @Test
    void testLook() {
        String response = sendCommandToServer("simon: look").toLowerCase();
        assertTrue(response.contains("cabin"), "Did not see the name of the current room in response to look");
        assertTrue(response.contains("log cabin"), "Did not see a description of the room in response to look");
        assertTrue(response.contains("magic potion"), "Did not see a description of artifacts in response to look");
        assertTrue(response.contains("wooden trapdoor"), "Did not see description of furniture in response to look");
        assertTrue(response.contains("forest"), "Did not see available paths in response to look");
    }

    // Test that we can pick something up and that it appears in our inventory
    @Test
    void testGet() {
        // 1. Action: Attempt to get an existing artefact
        sendCommandToServer("simon: get potion");

        // 2. Assert Inventory: The artefact should appear in the player's inventory
        String invResponse = sendCommandToServer("simon: inv").toLowerCase();
        assertTrue(invResponse.contains("potion"), "Did not see the potion in the inventory after an attempt was made to get it");

        // 3. Assert Location: The artefact should be removed from the room
        String lookResponse = sendCommandToServer("simon: look").toLowerCase();
        assertFalse(lookResponse.contains("potion"), "Potion is still present in the room after an attempt was made to get it");
    }

    // Test that we can goto a different location (we won't get very far if we can't move around the game !)
    @Test
    void testGoto() {
        sendCommandToServer("simon: goto forest");
        String response = sendCommandToServer("simon: look").toLowerCase();
        assertTrue(response.contains("key"), "Failed attempt to use 'goto' command to move to the forest - there is no key in the current location");
    }

    @Test
    void testValidGotoAndReturn() {
        // 1. Player goes to forest from cabin
        sendCommandToServer("simon: goto forest");

        // Assert: Make sure the key of forest can be seen and potion of cabin cannot be seen
        String response = sendCommandToServer("simon: look").toLowerCase();
        assertTrue(response.contains("key"), "After goto forest, player should see the key on the ground.");
        assertFalse(response.contains("potion"), "After goto forest, potion of cabin should not be on the ground anymore");

        // 2. Player returns to cabin from forest
        sendCommandToServer("simon: goto cabin");
        String lookCabinResponse = sendCommandToServer("simon: look").toLowerCase();
        assertTrue(lookCabinResponse.contains("potion"), "Return to cabin, player should see the potion again.");
    }

    @Test
    void testGotoNowhere() {
        // 1. Player tries to go to an invalid location
        String response = sendCommandToServer("simon: please goto bristol now").toLowerCase();

        // 2. Assert: Response should contain "cannot go"
        assertTrue(response.contains("cannot go"), "Game should deny illegal goto.");

        // 3. Assert: Check the player is still in the cabin and went nowhere
        String lookCabinResponse = sendCommandToServer("simon: look").toLowerCase();
        assertTrue(lookCabinResponse.contains("trapdoor"), "player should still see the trapdoor as he/she did not go to other place.");
    }

    @Test
    void testGotoMultiplePlaces() {
        // 1. Set up: Navigate and unlock required paths
        sendCommandToServer("simon: please goto forest");
        sendCommandToServer("simon: please get the key now");
        sendCommandToServer("simon: goto cabin");
        sendCommandToServer("simon: with the key unlock the trapdoor ");

        // 2. Action: Try to go to multiple places at once
        sendCommandToServer("simon: goto cellar and forest");

        // 3. Assert: Look response should confirm player is still in cabin
        String lookResponse = sendCommandToServer("simon: look");
        assertTrue(lookResponse.contains("Now you are in A log cabin in the woods."), "The player should still be in cabin as he/she cannot goto multiple places.");
    }

    // Test that composite commands (getting/dropping multiple items) are explicitly REJECTED
    @Test
    void testGreedyGet() {
        // 1. Try to get the potion and the axe at the same time
        String getResponse = sendCommandToServer("simon: get the potion and axe please").toLowerCase();
        assertTrue(getResponse.contains("error") || getResponse.contains("cannot"), "Game should reject composite get commands.");

        // 2. Check the inventory: find nothing
        String invResponse = sendCommandToServer("simon: inv").toLowerCase();
        assertFalse(invResponse.contains("potion"), "Potion should not be in inventory due to rejected command.");
        assertFalse(invResponse.contains("axe"), "Axe should not be in inventory due to rejected command.");

        // 3. Artefacts must still be on the ground
        String lookResponse = sendCommandToServer("simon: look").toLowerCase();
        assertTrue(lookResponse.contains("potion"), "Potion should still be on the ground.");
        assertTrue(lookResponse.contains("axe"), "Axe should still be on the ground.");
    }

    @Test
    void testGreedyDrop() {
        // Set up: Acquire items
        sendCommandToServer("simon: get the potion");
        sendCommandToServer("simon: get the axe");

        // 1. Greedy drop: try to drop axe and potion simultaneously
        String dropResponse = sendCommandToServer("simon: quickly drop the axe and potion now").toLowerCase();
        assertTrue(dropResponse.contains("error") || dropResponse.contains("cannot"), "Game should reject composite drop commands.");

        // 2. Check the inventory: two artefacts must still be in the inv
        String invResponse = sendCommandToServer("simon: inv").toLowerCase();
        assertTrue(invResponse.contains("potion"), "Potion should still be in inventory.");
        assertTrue(invResponse.contains("axe"), "Axe should still be in inventory.");

        // 3. Check the location: LOOK cannot see the artefacts player wanted to drop
        String lookResponse = sendCommandToServer("simon: look").toLowerCase();
        assertFalse(lookResponse.contains("potion -"), "Potion should not be on the ground.");
        assertFalse(lookResponse.contains("axe -"), "Axe should not be on the ground.");
    }

    @Test
    void testInvalidGetAndDrop() {
        // 1. Try to get the FURNITURE
        sendCommandToServer("simon: get trapdoor");
        String invResponse = sendCommandToServer("simon: inv").toLowerCase();
        assertFalse(invResponse.contains("trapdoor"), "Player should not be able to pick up furniture");

        // 2. Try to drop a NON-EXISTENT artefact from the inv
        sendCommandToServer("simon: drop gold");
        String lookResponse = sendCommandToServer("simon: look").toLowerCase();
        assertFalse(lookResponse.contains("gold"), "Dropping an item you do not have should not create it in the room.");
    }

    @Test
    void testEmptyInventory() {
        // The inventory should be empty when opening the game
        String invResponse = sendCommandToServer("simon: inv").toLowerCase();
        assertFalse(invResponse.contains("potion"), "Inventory should be empty at the start");
        assertFalse(invResponse.contains("axe"), "Inventory should be empty at the start");
    }
}