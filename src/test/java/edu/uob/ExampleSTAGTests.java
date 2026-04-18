package edu.uob;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.nio.file.Paths;
import java.io.IOException;
import java.time.Duration;

class ExampleSTAGTests {

  private GameServer server;

  // Create a new server _before_ every @Test
  @BeforeEach
  void setup() {
      File entitiesFile = Paths.get("config" + File.separator + "basic-entities.dot").toAbsolutePath().toFile();
      File actionsFile = Paths.get("config" + File.separator + "basic-actions.xml").toAbsolutePath().toFile();
      server = new GameServer(entitiesFile, actionsFile);
  }

  String sendCommandToServer(String command) {
      // Try to send a command to the server - this call will timeout if it takes too long (in case the server enters an infinite loop)
      return assertTimeoutPreemptively(Duration.ofMillis(1000), () -> { return server.handleCommand(command);},
      "Server took too long to respond (probably stuck in an infinite loop)");
  }

  // A lot of tests will probably check the game state using 'look' - so we better make sure 'look' works well !
  @Test
  void testLook() {
    String response = sendCommandToServer("simon: look");
    response = response.toLowerCase();
    assertTrue(response.contains("cabin"), "Did not see the name of the current room in response to look");
    assertTrue(response.contains("log cabin"), "Did not see a description of the room in response to look");
    assertTrue(response.contains("magic potion"), "Did not see a description of artifacts in response to look");
    assertTrue(response.contains("wooden trapdoor"), "Did not see description of furniture in response to look");
    assertTrue(response.contains("forest"), "Did not see available paths in response to look");
  }

  // Test that we can pick something up and that it appears in our inventory
  @Test
  void testGet()
  {
      String response;
      sendCommandToServer("simon: get potion");
      response = sendCommandToServer("simon: inv");
      response = response.toLowerCase();
      assertTrue(response.contains("potion"), "Did not see the potion in the inventory after an attempt was made to get it");
      response = sendCommandToServer("simon: look");
      response = response.toLowerCase();
      assertFalse(response.contains("potion"), "Potion is still present in the room after an attempt was made to get it");
  }

  // Test that we can goto a different location (we won't get very far if we can't move around the game !)
  @Test
  void testGoto()
  {
      sendCommandToServer("simon: goto forest");
      String response = sendCommandToServer("simon: look");
      response = response.toLowerCase();
      assertTrue(response.contains("key"), "Failed attempt to use 'goto' command to move to the forest - there is no key in the current location");
  }

  // Add more unit tests or integration tests here.
    // Test that we can get and drop multiple artefacts at one time
    @Test
        void testGreedyGetAndDrop() {
        // 1. Get the potion and the axe at the same time
        sendCommandToServer("simon: get the potion and axe please");
        String invResponse = sendCommandToServer("simon: inv").toLowerCase();
        assertTrue(invResponse.contains("potion"), "Did not see the potion in the inventory");
        assertTrue(invResponse.contains("axe"), "Did not see the axe in the inventory");
        // 2. look if the location has nothing
        String lookResponse = sendCommandToServer("simon: look").toLowerCase();
        assertFalse(lookResponse.contains("potion"), "Potion should not be on the ground anymore");
        assertFalse(lookResponse.contains("axe"), "Axe should not be on the ground anymore");
        // 3. drop the portion and axe at the same time
        sendCommandToServer("simon: quickly drop the axe and potion now");
        invResponse = sendCommandToServer("simon: look").toLowerCase();
        assertFalse(invResponse.contains("potion"), "Potion should be removed from inventory");
        assertFalse(invResponse.contains("axe"), "Axe should not be on the ground anymore");

    }
    @Test
    void testPunctuationAndCaseInsensitivity() {
        sendCommandToServer("owen: GET POTION!!! AND AXE???");

        String invResponse = sendCommandToServer("owen: inv").toLowerCase();
        assertTrue(invResponse.contains("potion"), "Failed to parse potion with punctuation");
        assertTrue(invResponse.contains("axe"), "Failed to parse axe with punctuation");
    }
    @Test
    void testInvalidGetAndDrop() {
        // 1. try to get the FURNITURE
        sendCommandToServer("simon: get trapdoor");
        String invResponse = sendCommandToServer("simon: inv").toLowerCase();
        assertFalse(invResponse.contains("trapdoor"), "Player should not be able to pick up furniture");

        // 2. try to drop UNEXISTING artefact of the inv
        String dropResponse = sendCommandToServer("simon: drop gold").toLowerCase();
        String lookResponse = sendCommandToServer("simon: look").toLowerCase();
        assertFalse(lookResponse.contains("gold"), "Dropping an item you do not have should not create it in the room.");
    }
    @Test
    void testEmptyInventory() {
      // the inv should be empty when opening the game
      String invResponse = sendCommandToServer("simon: inv").toLowerCase();
      assertFalse(invResponse.contains("potion"), "Inventory should be empty at the start");
      assertFalse(invResponse.contains("axe"), "Inventory should be empty at the start");
    }

}
