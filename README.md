# STAG: Simple Text Adventure Game Engine

## Overview 

STAG (Simple Text Adventure Game) is a robust, versatile, socket-based game engine written in Java. Rather than hardcoding a single game, this engine dynamically loads game worlds and actions from configuration files (`.dot` for entities and `.xml` for actions), allowing it to run *any* text adventure game that conforms to the rules.

This project was developed with a strong focus on **Object-Oriented Programming (OOP) principles**, **Clean Code architecture**, and **Test-Driven Development (TDD)**.

## Key Features

### 1. Robust Natural Language Processing (NLP) Parser

The engine features a highly fault-tolerant command interpreter designed to handle human-like inputs:

* **Fuzzy Matching:** Ignores extraneous "decorative" words (e.g., *"please quickly get the axe now"* is flawlessly interpreted as *"get axe"*).
* **Word Ordering:** **Trigger phrases and subjects can be entered in flexible orders.
* **Partial Commands:** Executes actions as long as a valid trigger and at least one core subject are provided.
* **Ambiguity & Extraneous Entity Defence:** Strictly rejects ambiguous commands and illegal composite actions (e.g., trying to use items not required by the game logic).

### 2. Advanced Game Extensions 

Beyond the core requirements, this engine fully implements the extended features:

* **Multiplayer Support:** The server handles multiple concurrent players seamlessly. It maintains isolated game states (e.g., separate inventories and locations) and implements visibility logic (player can see each other if in the same room).
* **Health & Combat System:** Players have a tracked health system (Max 3). Health can be consumed or produced via custom XML actions. 
* **Death & Respawn Mechanics:** If a player's health drops to 0, they "die"-dropping all inventory items in their current location and instantly respawning at the starting location with full health.

## Architecture & Design Patterns

The system is deeply decoupled to ensure maintainability:

* **Domain Entities:** Entities (`Artefact`,`Furniture`, `Character`, `Location`, `Player`)are rigorously encapsulated within an `entities` package, all inheriting from a base `GameEntity` class to maximise code reuse.
* **Dedicated Parsers:** 
  * `DOTParser` handles the structural parsing of the map and items.
  * `XMLParser` handles the dynamic game actions.
  * `CommandParser` is an isolated component entirely dedicated to parsing and sanitising complex natural language inputs.
* **Server Routing:** `GameServer` acts as the central controller, routing parsed commands to specific Handler method (`handleGet`, `handleCustomAction`, etc.) using the Single Responsibility Principle.

## Comprehensive Black-Box Testing

The project is fortified by an extensive JUnit test suite divided into **4 dedicated test files** containing **over 90 assertions**:

1. `BasicCommandsTests`: Verifies state persistence, core built-in commands (get, drop, goto, look), and edge-case rejections.
2. `NLPParserTests`: Validates the command flexibility, punctuation immunity, and ambiguous command interception.
3. `CustomActionsTests`: Ensures XML actions correctly consume/produce entities, manipulate paths, and trigger strict contextual responses.
4. `ExtendedFeaturesTests`: Specifically targets multiplayer isolation, visibility rules, and the full lifecycle of the death/respawn system.

## Getting Started

### Prerequisites

* Java 17 or higher
* Maven

### Running the Server

To start the game server listening on port 8888, open a terminal and run:

```bash
mvnw exec:java@server
```

### Running the Client

To connect to the server as a player, open a **new** terminal window and run:

```bash
mvnw exec:java@client -Dexec.args="[YourPlayerName]"
```

*(Replace `[YourPlayerName]`) with any valid name containing letters, spaces, hyphens, or apostrophes)*.

## Bug Fixes & Self-Corrections

This section documents bugs identified during testing and subsequently resolved, demonstrating an iterative, test-driven development approach.

### Fix 1: `Character` Entities Not Produced by Custom Actions

**Problem:** The `processProducedEntities()` method in `GameServer` only handled `Artefact` and `Furniture` types when moving entities out of the storeroom. `Character` entities (e.g., `lumberjack`) were silently ignored, so actions like `blow horn` appeared to succeed (returning the correct narration) but the character never appeared in the game world.

**Root Cause:** A missing `else if` branch for `storeroom.getAllCharacters()` in the produced-entity processing loop.

**Fix:** Added the missing `Character` branch so characters are correctly moved from the storeroom to the player's current location upon production.

### Fix 2: Duplicate Entity Production Not Prevented

**Problem:** Once an entity had been produced into the game world, it was possible to trigger the same action again and attempt to produce it a second time — violating the game rule that each entity must exist in exactly one place at a time.

**Root Cause:** No guard condition existed before the produce logic to check whether the target entity was already present somewhere in the game world (any non-storeroom location or a player's inventory).

**Fix:** Introduced a helper method `isEntityAlreadyInGame()` that scans all active locations and player inventories. The produce step is skipped if the entity is found, ensuring no duplicates can ever be created.

**Tests Added:** Three new test cases in `ExtendedFeaturesTests` were written to cover and validate both fixes:
- `testProduceCharacterFromStoreroom` — verifies a character is correctly summoned.
- `testProduceCharacterSummonedToCurrentLocation` — verifies the character appears at the player's location.
- `testNoDuplicateEntityProduction` — verifies a second produce attempt is a no-op.