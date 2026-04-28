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
      System.out.println(response);
      assertTrue(response.contains("Error: Invalid player name"), "The player name is valid.");
  }

    @Test
    void testStatePersistence() {
        server.handleCommand("simon: get potion");
        // Verify the potion is no longer in the room
        String lookResponse1 = server.handleCommand("simon: look");
        assertFalse(lookResponse1.contains("potion"), "Potion should be picked up and no longer in the room.");

        File entitiesFile = Paths.get("config" + File.separator + "extended-entities.dot").toAbsolutePath().toFile();
        File actionsFile = Paths.get("config" + File.separator + "extended-actions.xml").toAbsolutePath().toFile();
        // Instantiate a NEW GameServer to simulate a server restart
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
        sendCommandToServer("owen: get | potion!!!???");
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

        // 3. Assert 1: The response MUST NOT contain an error message
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
        String response = sendCommandToServer("owen:    inv     ").toLowerCase();
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
      String invResponse = sendCommandToServer("owen: inv").toLowerCase();
      assertFalse(invResponse.contains("axe"), "You cannot get the axe since you only can perform one action.");
      String lookResponse = sendCommandToServer("owen: look").toLowerCase();
      assertTrue(lookResponse.contains("now you are in a log cabin in the woods."),"You cannot goto forest since you only can perform one action.");

    }

    @Test
    void testAtLeastOneSubject() {
      // 1. test basic action with no subject
      sendCommandToServer("owen: goto ");
      String lookResponse1 = sendCommandToServer("owen: look").toLowerCase();
      assertTrue(lookResponse1.contains("now you are in a log cabin in the woods."),"You cannot goto other places since the command does not have one subject");

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
      assertTrue(response4.contains("You unlock the door and see steps leading down into a cellar"), "command should work with one trigger and at least one subject.");

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

        // 3. artefacts must on the ground
        String lookResponse = sendCommandToServer("simon: look").toLowerCase();
        assertTrue(lookResponse.contains("potion"), "Potion should still be on the ground.");
        assertTrue(lookResponse.contains("axe"), "Axe should still be on the ground.");
    }

    @Test
    void testGreedyDrop() {
        sendCommandToServer("simon: get the potion");
        sendCommandToServer("simon: get the axe");
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

    @Test
    void testGotoMultiplePlaces() {
      // 1. set up
      sendCommandToServer("simon: please goto forest");
      sendCommandToServer("simon: please get the key now");
      sendCommandToServer("simon: goto cabin");
      sendCommandToServer("simon: with the key unlock the trapdoor ");

      // 2. goto multiple places
      sendCommandToServer("simon: goto cellar and forest");

      // 3. look response should still in cabin
      String lookResponse = sendCommandToServer("simon: look");
      assertTrue(lookResponse.contains("Now you are in A log cabin in the woods."), "The player should still be in cabin as he/she cannot goto multiple places.");
      assertTrue(lookResponse.contains("You can see the following furniture: \n" + "trapdoor"), "The player should still be in cabin as he/she cannot goto multiple places.");
    }


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
    // Test the player chop the tree WITHOUT axe (Test point: the INV does not have axe, not the command)
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
        sendCommandToServer("simon: get the axe");
        String response = sendCommandToServer("simon: chop tree").toLowerCase();
        assertTrue(response.contains("cannot do that"), "You cannot do that since there is no tree in cabin.");
        String invResponse = sendCommandToServer("simon: inv").toLowerCase();
        assertTrue(invResponse.contains("axe"), "The axe should be in inventory");
        assertFalse(invResponse.contains("log"), "You cannot have log since you cannot chop the tree when you are not in forest.");
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
        String invResponse = sendCommandToServer("simon: inv").toLowerCase();
        assertFalse(invResponse.contains("axe"), "The axe should not be in inventory");
        String lookResponse = sendCommandToServer("simon: look").toLowerCase();
        assertTrue(lookResponse.contains("cabin"), "The player should still be in cabin.");

    }

    @Test
    void testCharacterInteractionAndHealthSystem() {
        // navigate to the cellar
        sendCommandToServer("simon: goto forest");
        sendCommandToServer("simon: get key");
        sendCommandToServer("simon: goto cabin");
        sendCommandToServer("simon: open trapdoor");
        sendCommandToServer("simon: goto cellar");
        // verify character entity parsing
        String lookResponse = sendCommandToServer("simon: look").toLowerCase();
        assertTrue(lookResponse.contains("elf"), "Entity Parsing Error: The Character 'elf' should be parsed from the DOT file and visible in the cellar.");
        String fightResponse = sendCommandToServer("simon: fight elf").toLowerCase();
        // The narration should reflect the XML response for this action
        assertTrue(fightResponse.contains("attack") || fightResponse.contains("health"),
                "Action Error: Should be able to successfully interact with the Character using a valid custom action.");
        // verify the Health system and 'consumed' logic
        String healthResponse = sendCommandToServer("simon: health").toLowerCase();
        // Assuming starting health is 3, one fight should drop it to 2
        assertTrue(healthResponse.contains("2"), "Health System Error: Player health should decrease after a combat action that consumes 'health'.");
    }

    @Test
    void testStartLocationIsAlwaysFirst() {
        // the start location MUST be the first location in the entities file.
        String lookResponse = sendCommandToServer("simon: look").toLowerCase();
        // In both basic and extended files, 'cabin' is defined first.
        // if the parser used a non-ordered collection (like standard HashSet) and picked randomly, this would fail.
        assertTrue(lookResponse.contains("cabin"), "Entity Loading Error: The player did not spawn in the first location defined in the DOT file.");
    }

    @Test
    void testEntityCategorization() {
        // ensure entities are loaded into their CORRECT categories.
        // E.g., Artefacts can be picked up, Furniture cannot.

        // 1. Try to pick up an Artefact (Should succeed)
        String getAxeResponse = sendCommandToServer("simon: get the axe").toLowerCase();
        assertFalse(getAxeResponse.contains("error") || getAxeResponse.contains("cannot"), "Entity Loading Error: 'axe' should be parsed as an Artefact and be collectible.");
        String invResponse = sendCommandToServer("simon: inv").toLowerCase();
        assertTrue(invResponse.contains("axe"), "The axe should be in inventory");

        // 2. Try to pick up Furniture (Should fail)
        String getTrapdoorResponse = sendCommandToServer("simon: get trapdoor").toLowerCase();
        assertTrue(getTrapdoorResponse.contains("error") || getTrapdoorResponse.contains("cannot"), "Entity Loading Error: 'trapdoor' should be parsed as Furniture and CANNOT be collected.");
        String invResponse2 = sendCommandToServer("simon: inv").toLowerCase();
        assertFalse(invResponse2.contains("trapdoor"), "The trapdoor should not be in inventory");

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
        String lookResponse = sendCommandToServer("simon: look").toLowerCase();
        assertTrue(lookResponse.contains("cabin"), "The player should still be in cabin.");

        // 2. Ensure current location hasn't changed to storeroom
        assertFalse(lookResponse.contains("storeroom"), "Player should not be inside the storeroom.");
    }



}
