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

    @Test
    void testCaseInsensitivity() {
        sendCommandToServer("owen:please GeT POTION");

        String invResponse = sendCommandToServer("owen: inv").toLowerCase();
        assertTrue(invResponse.contains("potion"), "Failed to parse potion with punctuation");
    }

    @Test
    void testPunctuation() {
        sendCommandToServer("owen: GET | POTION!!!???");
        String invResponse = sendCommandToServer("owen: inv").toLowerCase();
        assertTrue(invResponse.contains("potion"), "Failed to parse potion with punctuation");
    }

    @Test
    void testDecoratedCommandSuccess() {
        // 1. Add decorative words to basic commands
        sendCommandToServer("owen: get the axe please");
        sendCommandToServer("owen: quickly goto the forest now");

        // 2. Send a decorated custom action command (please chop the tree using the axe)
        String response = sendCommandToServer("owen : please chop the tree using the axe").toLowerCase();

        // 3. Assert 1: The response MUST NOPT contain an error message
        assertFalse(response.contains("error") || response.contains("cannot"),
                "the action triggered successfully");
        // 4. Assert 2: Verify the game state to see if the tree was actually consumed and the log produced
        String lookResponse = sendCommandToServer("owen: look around the room").toLowerCase();
        assertTrue(lookResponse.contains("\nlog -"), "The log should appear after the decorated chop command.");
        assertFalse(lookResponse.contains("tree"), "The tree should be consumed and removed from the forest.");

    }

    @Test
    void testDecoratedCommandMultiWordTriggerFailure() {
       // 1. set up
       sendCommandToServer("owen: get the axe");
       sendCommandToServer("owen: quickly goto the forest");
       // 2. split a multi-word trigger
       // "cut quickly down


    }
    @Test
    void testVariableWhitespace() {
        sendCommandToServer("owen: get        the axe      ");
        String response = sendCommandToServer("owen: inv").toLowerCase();
        assertTrue(response.contains("axe"), "Variable whitespace is allowed.");
        sendCommandToServer("owen: goto forest      ");
        sendCommandToServer("owen: cut       down the tree with the axe");
        String response2 = sendCommandToServer("owen: look");
        // variable whitespace is allowed within trigger phrases that contain multiple words.
        assertTrue(response2.contains("\nlog"), "Variable whitespace is still allowed within trigger phrases that contain multiple words..");
    }

    // command can only involve one trigger
    @Test
    void testMultipleTrigger() {
      String response = sendCommandToServer("owen: get the axe and goto forest").toLowerCase();
      assertTrue(response.contains("error"), "Multiple triggers are not allowed.");

    }

    @Test
    void testAtLeastOneSubject() {
      // 1. test basic action with no subject
      String response1 = sendCommandToServer("owen: goto ");
      assertFalse(response1.contains("forest"), "command cannot work with no subject");
      assertTrue(response1.contains("cannot") || response1.contains("error"), "no subject no goto");

      // 2. set up
      sendCommandToServer("owen: goto forest");
      sendCommandToServer("owen: get the key");
      String response2 = sendCommandToServer("owen: goto cabin");
      assertTrue(response2.toLowerCase().contains("log cabin"), "Should successfully return to cabin.");

      // 3. test custom action with no subject
      String response3 = sendCommandToServer("owen: unlock the ");
      assertTrue(response3.contains("error") || response3.contains("cannot"), "command cannot work with no subject");

      // 4. test custom action with full subjects (partial command is allowed)
      String response4 = sendCommandToServer("owen: unlock the trapdoor with key");
      assertTrue(response4.contains("You unlock the trapdoor and see steps leading down into a cellar"), "command should work with one trigger and two subjects.");

      // 5. test custom action with partial subject
      sendCommandToServer("owen: get the axe");
      sendCommandToServer("owen: goto forest");
      sendCommandToServer("owen: chop with the axe");
      String response5 = sendCommandToServer("owen: look");
      assertTrue(response5.toLowerCase().contains("\nlog"), "command works even with partial subject");

    }

  // Test that composite commands (getting/dropping multiple items) are explicitly REJECTED
    @Test
        void testGreedyGet() {
        // 1. Get the potion and the axe at the same time
        String getResponse = sendCommandToServer("simon: get the potion and axe please").toLowerCase();
        assertTrue(getResponse.contains("error") || getResponse.contains("cannot"), "Game should reject composite get commands.");
        // 2. check the inv : find nothing
        String invResponse = sendCommandToServer("simon: inv").toLowerCase();
        assertFalse(invResponse.contains("potion"), "Potion should not be in inventory due to rejected command.");
        assertFalse(invResponse.contains("axe"), "Axe should not be in inventory due to rejected command.");

        // 3. rtefacts must on the grounf
        String lookResponse = sendCommandToServer("simon: look").toLowerCase();
        assertTrue(lookResponse.contains("potion"), "Potion should still be on the ground.");
        assertTrue(lookResponse.contains("axe"), "Axe should still be on the ground.");
    }

    @Test
    void testGreedyDrop() {
        sendCommandToServer("simon: get potion");
        sendCommandToServer("simon: get axe");

        // Greedy drop

        // 1. try to dorp axe and potion
        String dropResponse = sendCommandToServer("simon: quickly drop the axe and potion now").toLowerCase();
        assertTrue(dropResponse.contains("error") || dropResponse.contains("cannot"), "Game should reject composite drop commands.");

        // 2. check the inv: two artefacts must be in the inv
         String invResponse = sendCommandToServer("simon: inv").toLowerCase();
        assertTrue(invResponse.contains("potion"), "Potion should still be in inventory.");
        assertTrue(invResponse.contains("axe"), "Axe should still be in inventory.");

        // 3. check the location: LOOK cannot see the artefacts player want to drop
        String lookResponse = sendCommandToServer("simon: look").toLowerCase();
        assertFalse(lookResponse.contains("potion -"), "Potion should not be on the ground.");
        assertFalse(lookResponse.contains("axe -"), "Axe should not be on the ground.");
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


    @Test
    // Test the player chop a tree with axe
    void testCustomerActionChopTreeWithAxe() {
        sendCommandToServer("simon: please get the axe now");
        sendCommandToServer("simon: goto forest");
        sendCommandToServer("simon: please loudly chop the huge tree now");
        String lookResponse = sendCommandToServer("simon: look").toLowerCase();
        // 1. assert the produced
        assertTrue(lookResponse.contains("log"), "After chopping the tree, the player should see the log");
        // 2. assert the consumed
        assertFalse(lookResponse.contains("tree"), "After chopping the tree, the player should not see the tree since tree is in storeroom now.");
        // 3. assert the inv
        String invResponse = sendCommandToServer("simon: inv").toLowerCase();
        assertTrue(invResponse.contains("axe"), "The axe should be in inventory");
    }

    @Test
    // Test the player chop the tree WITHOUT axe
    void testCustomerActionChopTreeWithoutAxe() {
      sendCommandToServer("simon: please goto forest now.");
      String response = sendCommandToServer("simon: chop tree").toLowerCase();
      assertTrue(response.contains("cannot do that"), "You cannot do that without axe.");
      String lookResponse = sendCommandToServer("simon: look").toLowerCase();
      assertFalse(lookResponse.contains("log"), "The log cannot be seen as no tree is chopped.");
      assertTrue(lookResponse.contains("tree"), "The tree still exists.");
    }

    @Test
    // Test the player chop the tree NOT in forest
    void testCustomerActionChopTreeNotInForest() {
        sendCommandToServer("simon: get axe");
        String response = sendCommandToServer("simon: chop tree").toLowerCase();
        assertTrue(response.contains("cannot do that"), "You cannot do that since there is no tree in cabin.");
        String lookResponse = sendCommandToServer("simon: look").toLowerCase();
        // EDGE CASE: Do NOT just assert "log".
        // The room's description is "a log cabin", which will cause a false positive.
        // Asserting "\nlog-" ensures we are only looking for the dropped artefact.
        assertFalse(lookResponse.contains("\nlog -"), "The log artefact should not be on the ground.");
        assertFalse(lookResponse.contains("heavy wooden"), "The log cannot be seen as no tree is chopped.");
    }

    @Test
    void testMultipleCommands() {
        String response = sendCommandToServer("simon: get the axe and goto forest");
        assertTrue(response.contains("Error: You can only perform one action at a time."), "Error: You can only perform one action at a time.");

    }



}
