package edu.uob;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.xml.xpath.XPath;
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
        // [Task 8 Validation] Multi-word triggers CANNOT be separated by decorative words
        // We test the FAILURES first to guarantee the game state remains untouched!

        File testEntities = Paths.get("config" + File.separator + "basic-entities.dot").toAbsolutePath().toFile();
        File testActions = Paths.get("config" + File.separator + "test-nlp-actions.xml").toAbsolutePath().toFile();
        GameServer nlpTestServer = new GameServer(testEntities, testActions);

        // 1. Destructive Test 1: Splitting the trigger phrase.
        // Inserting "quickly" inside "drink up" should completely break the matching.
        String splitTriggerResponse = nlpTestServer.handleCommand("simon: drink quickly up the potion").toLowerCase();
        assertTrue(splitTriggerResponse.contains("error") || splitTriggerResponse.contains("cannot"),
                "NLP Error: Multi-word triggers CANNOT have decorative words inserted inside them.");

        // 2. Destructive Test 2: Reversing the word order of the trigger phrase.
        String reversedTriggerResponse = nlpTestServer.handleCommand("simon: up drink the potion").toLowerCase();
        assertTrue(reversedTriggerResponse.contains("error") || reversedTriggerResponse.contains("cannot"),
                "NLP Error: Multi-word triggers CANNOT have their internal word order reversed.");

        // 3. Baseline Test: A valid multi-word trigger wrapped in decorative words.
        String successResponse = nlpTestServer.handleCommand("simon: please drink up the potion now").toLowerCase();
        assertTrue(successResponse.contains("successfully"),
                "Baseline Error: A valid multi-word trigger should work even with leading/trailing decorative words.");
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
        // 1. try to drop axe and potion
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

        // 2. try to drop NON-EXISTENT artefact of the inv
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
    void testCustomActionChopTreeWithAxe() {
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
    void testCustomActionChopTreeWithoutAxe() {
      sendCommandToServer("simon: please goto forest now.");
      String response = sendCommandToServer("simon: chop tree").toLowerCase();
      assertTrue(response.contains("cannot do that"), "You cannot do that without axe.");
      String lookResponse = sendCommandToServer("simon: look").toLowerCase();
      assertFalse(lookResponse.contains("log"), "The log cannot be seen as no tree is chopped.");
      assertTrue(lookResponse.contains("tree"), "The tree still exists.");
    }

    @Test
    // Test the player chop the tree NOT in forest
    void testCustomActionChopTreeNotInForest() {
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

    @Test
    void testMultiplayerInventoryIsolation() {
        // player 1 Owen gets the axe
        sendCommandToServer("owen: get the axe");

        // player 2 Simon tries to chop the tree
        // even though Simon is in the cabin, the action requires the axe.
        // The axe is NOT in the room, and NOT in Simon's inventory.
        String simonResponse = sendCommandToServer("simon: chop tree with axe").toLowerCase();
        assertTrue(simonResponse.contains("error") || simonResponse.contains("cannot do that"),"Multiplayer Error: Simon should not be able to use an item in Owen's inv.");
        String simonLookResponse = sendCommandToServer("simon: look").toLowerCase();
        assertFalse(simonLookResponse.contains("axe"),"The axe is picked up by the other player.");
    }

    @Test
    void testLocationAsProducedAndConsumedEntities() {
        // test locations as Produced/Consumed entities.
        // 1. verify that the path to 'cellar' does NOT exist initially
        String gotoResponse01 = sendCommandToServer("simon: goto cellar").toLowerCase();
        assertTrue(gotoResponse01.contains("error") || gotoResponse01.contains("cannot"), "Path Error: The path to cellar should not exist before the required action.");

        // setup
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
        // Task 7： Verify that actions from the XML file are correctly parsed,
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
        String invResponse = sendCommandToServer("simon: inv").toLowerCase();
        assertFalse(invResponse.contains("potion"), "The potion is consumed.");
    }

    @Test
    void testCustomActionExtraneousEntities() {
        // [Task 9 Validation] Extraneous Entities in Custom Actions
        // An action MUST fail if the user includes entities that are NOT specified in the XML.

        // 1. Setup: Get a valid item (key) and a totally unrelated item (potion)
        sendCommandToServer("simon: get potion");
        sendCommandToServer("simon: goto forest");
        sendCommandToServer("simon: get key");
        sendCommandToServer("simon: goto cabin");

        // 2. Destructive Test: Try to perform a valid action but inject an extraneous entity ("potion")
        // The action expects 'trapdoor' and 'key', but we force 'potion' into the sentence.
        String response = sendCommandToServer("simon: unlock the trapdoor with the key and the potion").toLowerCase();

        // 3. Assert it gets rejected
        assertTrue(response.contains("error") || response.contains("cannot"),
                "Extraneous Entity Error: The action should be rejected because 'potion' is an extraneous entity not specified in this XML action.");

        // 4. Verify game state hasn't changed (the cellar path hasn't been produced)
        String lookResponse = sendCommandToServer("simon: look").toLowerCase();
        assertFalse(lookResponse.contains("cellar"),
                "Game State Error: The trapdoor should not be unlocked due to the extraneous entity rule.");
    }

    @Test
    void testAmbiguousCommandRejection() {
        // task 9 Ambiguous Command
        // We bypass the finicky DOT parser by using the provided basic-entities.dot

        // 1. set up (USING THE SAFE BASIC ENTITIES FILE!)
        File testEntities = Paths.get("config" + File.separator + "basic-entities.dot").toAbsolutePath().toFile();
        File testActions = Paths.get("config" + File.separator + "test-ambiguous-actions.xml").toAbsolutePath().toFile();

        GameServer ambiguousServer = new GameServer(testEntities, testActions);

        // 2. The player types an ambiguous command
        // The player mentions 'axe' (passes the >=1 subject rule).
        // But BOTH "cut potion" and "cut trapdoor" are fully performable because all 3 items are in the cabin!
        // The server's validActions list will have a size of 2.
        String ambiguousResponse = ambiguousServer.handleCommand("simon: cut with axe").toLowerCase();

        // assert that your specific ambiguity error message is returned
        assertTrue(ambiguousResponse.contains("ambiguous") || ambiguousResponse.contains("more than one"),
                "NLP Error: The server must reject commands that match multiple performable actions.");

        // 3. Success Test: The player specifies BOTH subjects to break the ambiguity
        // By adding "potion", the Extraneous Entity rule will eliminate the trapdoor action!
        // The validActions list size drops to exactly 1.
        String clearResponse = ambiguousServer.handleCommand("simon: cut the potion with the axe").toLowerCase();

        // Assert that the action is now successfully performed
        assertTrue(clearResponse.contains("cut the potion"),
                "NLP Resolution: The server should successfully perform the action once the ambiguity is resolved.");
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
