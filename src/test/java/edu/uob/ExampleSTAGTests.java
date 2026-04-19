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

    @Test
    void testValidGotoAndReturn() {
        // 1. player goto forest from cabin
        sendCommandToServer("simon: goto forest");
        // make sure the key of forest can be seen
        String response = sendCommandToServer("simon: look").toLowerCase();
        assertTrue(response.contains("key"), "After goto forest, player should see the key on the ground.");
        // make sure potion of cabin cannot been seen
        assertFalse(response.contains("potion"), "After goto forest, potion of cabin should not be on the ground anymore");

        // 2. player return cabin from forest
        sendCommandToServer("simon: goto cabin");
        String lookCabinResponse = sendCommandToServer("simon: look").toLowerCase();
        assertTrue(lookCabinResponse.contains("potion"), "Return to cabin, player should see the potion again.");
    }

    @Test
    void testGotoNowhere() {
        // 1. player wanna goto bristol
        String response = sendCommandToServer("simon: please goto bristol now").toLowerCase();
        // 2. response should contain "cannot goto"
        assertTrue(response.contains("cannot go"), "Game should deny illegal goto.");
        // 3. check the player still in the cabin, goto no where
        String lookCabinResponse = sendCommandToServer("simon: look").toLowerCase();
        assertTrue(lookCabinResponse.contains("trapdoor"), "player should still see the trapdoor as he/she did not go to other place.");
        assertTrue(lookCabinResponse.contains("axe"), "player should still see the axe as he/she did not go to other place.");
    }

    //@Test
    //void testGotoMultiplePlaces() {
        // 假设我们要测试的房间是森林，它有去 cabin 的路
        // 为了测试触发 size > 1，我们可以故意在句子里塞进多个目的地名字
        // （具体要看你的地图里哪个房间有两条以上的出路，这里假设森林可以去 cabin，其实基础地图 cabin 只有一条出路 forest）

        // 假设当前在房间A，有通往 B 和 C 的路。玩家试图同时去两个地方：
        // sendCommandToServer("simon: goto B and C");

        // 验证玩家是不是还在原地
        // String lookResponse = sendCommandToServer("simon: look").toLowerCase();
        // assertTrue(lookResponse.contains("A房间的特有物品"), "试图同时去两个地方应该被拦截，留在原地");
    }

}
