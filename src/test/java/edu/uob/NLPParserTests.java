package edu.uob;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.nio.file.Paths;
import java.time.Duration;

class NLPParserTests {

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
    void testCaseInsensitivity() {
        sendCommandToServer("owen:please GeT POTION");
        String invResponse = sendCommandToServer("owen: inv").toLowerCase();
        assertTrue(invResponse.contains("potion"), "Failed to parse potion with punctuation/case insensitivity");
    }

    @Test
    void testPunctuation() {
        sendCommandToServer("owen: get | potion!!!???");
        String invResponse = sendCommandToServer("owen: inv").toLowerCase();
        assertTrue(invResponse.contains("potion"), "Failed to parse potion with punctuation");
    }

    @Test
    void testVariableWhitespace() {
        sendCommandToServer("owen: get        the axe      ");
        String response = sendCommandToServer("owen:    inv     ").toLowerCase();
        assertTrue(response.contains("axe"), "Variable whitespace is allowed.");

        sendCommandToServer("owen: goto forest      ");
        sendCommandToServer("owen: cut       down the tree with the axe");
        String response2 = sendCommandToServer("owen: look");
        // Variable whitespace is allowed within trigger phrases that contain multiple words.
        assertTrue(response2.contains("\nlog"), "Variable whitespace is still allowed within trigger phrases that contain multiple words.");
    }

    @Test
    void testDecoratedCommandSuccess() {
        // 1. Add decorative words to basic commands
        sendCommandToServer("owen: get the axe please");
        sendCommandToServer("owen: quickly goto the forest now");

        // 2. Send a decorated custom action command
        String response = sendCommandToServer("owen : please chop the tree using the axe").toLowerCase();

        // 3. Assert 1: The response MUST NOT contain an error message
        assertFalse(response.contains("error") || response.contains("cannot"), "the action triggered successfully");

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

    // Command can only involve one trigger
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
    void testMultipleCommands() {
        String response = sendCommandToServer("simon: get the axe and goto forest");
        assertTrue(response.contains("Error: You can only perform one action at a time."), "Error: You can only perform one action at a time.");
        String invResponse = sendCommandToServer("simon: inv").toLowerCase();
        assertFalse(invResponse.contains("axe"), "The axe should not be in inventory");
        String lookResponse = sendCommandToServer("simon: look").toLowerCase();
        assertTrue(lookResponse.contains("cabin"), "The player should still be in cabin.");
    }

    @Test
    void testAtLeastOneSubject() {
        // 1. Test basic action with no subject
        sendCommandToServer("owen: goto ");
        String lookResponse1 = sendCommandToServer("owen: look").toLowerCase();
        assertTrue(lookResponse1.contains("now you are in a log cabin in the woods."),"You cannot goto other places since the command does not have one subject");

        // 2. Set up
        sendCommandToServer("owen: goto forest");
        sendCommandToServer("owen: get the key");
        String response2 = sendCommandToServer("owen: goto cabin");
        assertTrue(response2.toLowerCase().contains("log cabin"), "Should successfully return to cabin.");

        // 3. Test custom action with no subject
        String response3 = sendCommandToServer("owen: unlock the ");
        assertTrue(response3.contains("error") || response3.contains("cannot"), "command cannot work with no subject");

        // 4. Test custom action with full subjects (partial command is allowed)
        String response4 = sendCommandToServer("owen: unlock the trapdoor with key");
        assertTrue(response4.contains("You unlock the door and see steps leading down into a cellar"), "command should work with one trigger and at least one subject.");

        // 5. Test custom action with partial subject
        sendCommandToServer("owen: get the axe");
        sendCommandToServer("owen: goto forest");
        sendCommandToServer("owen: chop with the axe");
        String response5 = sendCommandToServer("owen: look");
        assertTrue(response5.toLowerCase().contains("\nlog"), "command works even with partial subject");
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
                "Extraneous Entity Error: The action should be rejected because 'potion' is an extraneous entity.");

        // 4. Verify game state hasn't changed (the cellar path hasn't been produced)
        String lookResponse = sendCommandToServer("simon: look").toLowerCase();
        assertFalse(lookResponse.contains("cellar"), "Game State Error: The trapdoor should not be unlocked due to the extraneous entity rule.");
    }

    @Test
    void testAmbiguousCommandRejection() {
        // [Task 9 Validation] Ambiguous Command
        // We bypass the finicky DOT parser by using the provided basic-entities.dot

        // 1. Set up (USING THE SAFE BASIC ENTITIES FILE!)
        File testEntities = Paths.get("config" + File.separator + "basic-entities.dot").toAbsolutePath().toFile();
        File testActions = Paths.get("config" + File.separator + "test-ambiguous-actions.xml").toAbsolutePath().toFile();
        GameServer ambiguousServer = new GameServer(testEntities, testActions);

        // 2. The player types an ambiguous command
        // The player mentions 'axe' (passes the >=1 subject rule).
        // But BOTH "cut potion" and "cut trapdoor" are fully performable because all 3 items are in the cabin!
        String ambiguousResponse = ambiguousServer.handleCommand("simon: cut with axe").toLowerCase();
        assertTrue(ambiguousResponse.contains("ambiguous") || ambiguousResponse.contains("more than one"),
                "NLP Error: The server must reject commands that match multiple performable actions.");

        // 3. Success Test: The player specifies BOTH subjects to break the ambiguity
        // By adding "potion", the Extraneous Entity rule will eliminate the trapdoor action!
        String clearResponse = ambiguousServer.handleCommand("simon: cut the potion with the axe").toLowerCase();
        assertTrue(clearResponse.contains("cut the potion"),
                "NLP Resolution: The server should successfully perform the action once the ambiguity is resolved.");
    }
}