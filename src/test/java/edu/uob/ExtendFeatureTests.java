package edu.uob;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.nio.file.Paths;
import java.time.Duration;

class ExtendedFeaturesTests {

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

    @Test
    void testMultiplayerInventoryIsolation() {
        // Player 1 (Owen) gets the axe
        sendCommandToServer("owen: get the axe");

        // Player 2 (Simon) tries to chop the tree
        // Even though Simon is in the cabin, the action requires the axe.
        // The axe is NOT in the room, and NOT in Simon's inventory.
        String simonResponse = sendCommandToServer("simon: chop tree with axe").toLowerCase();
        assertTrue(simonResponse.contains("error") || simonResponse.contains("cannot do that"),"Multiplayer Error: Simon should not be able to use an item in Owen's inv.");

        // Assert that Simon cannot see the axe in the room because Owen has it
        String simonLookResponse = sendCommandToServer("simon: look").toLowerCase();
        assertFalse(simonLookResponse.contains("axe"),"The axe is picked up by the other player and should be invisible.");
    }

    @Test
    void testMultiplayerLookVisibility() {
        // Task 9：Multiple Players - Visibility
        // Players must be able to see each other in the same room, but not see themselves.

        // 1. Owen and Simon both join the game (they spawn in the cabin)
        sendCommandToServer("owen: look");
        sendCommandToServer("simon: look");

        // 2. Owen looks around the cabin
        String owenLookResponse = sendCommandToServer("owen: look").toLowerCase();

        // Assertion 1: Owen MUST see Simon
        assertTrue(owenLookResponse.contains("simon"), "Multiplayer Error: Owen should be able to see Simon in the cabin.");
        // Assertion 2: Owen MUST NOT see himself
        assertFalse(owenLookResponse.contains("owen"), "Multiplayer Error: Owen should not see his own name when looking around.");

        // 3. Simon leaves the cabin and goes to the forest
        sendCommandToServer("simon: goto forest");

        // 4. Owen looks around the cabin again
        String owenLookAgainResponse = sendCommandToServer("owen: look").toLowerCase();

        // Assertion 3: Simon should be gone from Owen's sight
        assertFalse(owenLookAgainResponse.contains("simon"), "Multiplayer Error: Owen should no longer see Simon because Simon went to the forest.");

        // 5. Simon looks around the forest
        String simonLookResponse = sendCommandToServer("simon: look").toLowerCase();

        // Assertion 4: Simon should not see Owen in the forest
        assertFalse(simonLookResponse.contains("owen"), "Multiplayer Error: Simon should not see Owen in the forest.");
    }

    @Test
    void testCharacterInteractionAndHealthSystem() {
        // Navigate to the cellar
        sendCommandToServer("simon: goto forest");
        sendCommandToServer("simon: get key");
        sendCommandToServer("simon: goto cabin");
        sendCommandToServer("simon: open trapdoor");
        sendCommandToServer("simon: goto cellar");

        // Verify character entity parsing
        String lookResponse = sendCommandToServer("simon: look").toLowerCase();
        assertTrue(lookResponse.contains("elf"), "Entity Parsing Error: The Character 'elf' should be parsed from the DOT file and visible in the cellar.");

        // Perform combat action
        String fightResponse = sendCommandToServer("simon: fight elf").toLowerCase();
        // The narration should reflect the XML response for this action
        assertTrue(fightResponse.contains("attack") || fightResponse.contains("health"),
                "Action Error: Should be able to successfully interact with the Character using a valid custom action.");

        // Verify the Health system and 'consumed' logic
        String healthResponse = sendCommandToServer("simon: health").toLowerCase();
        // Assuming starting health is 3, one fight should drop it to 2
        assertTrue(healthResponse.contains("2"), "Health System Error: Player health should decrease after a combat action that consumes 'health'.");
    }

    @Test
    void testDeathAndRespawn() {
        // Task 10 : Player Death, Item Drop, and Respawn mechanics
        File testEntities = Paths.get("config" + File.separator + "basic-entities.dot").toAbsolutePath().toFile();
        File testActions = Paths.get("config" + File.separator + "test-death-actions.xml").toAbsolutePath().toFile();
        GameServer deathServer = new GameServer(testEntities, testActions);

        // 1. Setup: Player gets an item so we can verify they drop it upon death
        deathServer.handleCommand("simon: get potion");
        String invResponse = deathServer.handleCommand("simon: inv").toLowerCase();
        assertTrue(invResponse.contains("potion"), "Player should have the potion before dying.");

        // 2. Player drinks poison from the potion 3 times to die
        deathServer.handleCommand("simon: drink poison from potion"); // Health -> 2
        deathServer.handleCommand("simon: drink poison from potion"); // Health -> 1
        String deathResponse = deathServer.handleCommand("simon: drink poison from potion").toLowerCase(); // Health -> 0 (Death!)

        // 3. Assert 1: The correct death message is returned
        assertTrue(deathResponse.contains("died") && deathResponse.contains("return to the start"),
                "Death Error: The server should return the specific death narration.");

        // 4. Assert 2: Respawn at start location (cabin)
        String lookResponse = deathServer.handleCommand("simon: look").toLowerCase();
        assertTrue(lookResponse.contains("cabin"), "Respawn Error: Player must be teleported back to the start location.");

        // 5. Assert 3: Inventory is emptied
        String invAfterDeath = deathServer.handleCommand("simon: inv").toLowerCase();
        assertFalse(invAfterDeath.contains("potion"), "Inventory Error: Player must lose all items upon death.");

        // 6. Assert 4: Health is fully restored to 3
        String healthResponse = deathServer.handleCommand("simon: health").toLowerCase();
        assertTrue(healthResponse.contains("3"), "Health Error: Player health must be restored to maximum (3) after respawning.");

        // 7. Assert 5: The dropped item is now in the room where they died!
        // (Since they died in the cabin, the potion should be on the cabin floor)
        assertTrue(lookResponse.contains("potion"), "Drop Error: The player's items must be dropped in the location where they died.");
    }
}