package edu.uob;

import java.util.ArrayList;
import java.util.HashSet;

public class CommandParser {

    public String cleanCommand(String rawCommand) {
        String cleanStr = rawCommand.toLowerCase();
        cleanStr = cleanStr.replace("\\p{Punct}", " ");
        cleanStr = cleanStr.replaceAll("\\s+", " ").trim();
        return cleanStr;
    }

    public ArrayList<String> getMentionedEntities(String actionCommand, HashSet<String> allGameEntities) {
        ArrayList<String> mentionedEntities = new ArrayList<>();
        for (String entityName : allGameEntities) {
            if (actionCommand.contains(entityName)) {
                mentionedEntities.add(entityName);
            }
        }
        return mentionedEntities;
    }

    public boolean isActionTriggered(GameAction gameAction, String actionCommand) {
        for (String trigger : gameAction.getTriggers()) {
            if (actionCommand.contains(trigger)) {
                return true;
            }
        }
        return false;
    }

    public int countMentionedSubjects(GameAction gameAction, String actionCommand) {
        int count = 0;
        for (String subject : gameAction.getSubjects()) {
            if (actionCommand.contains(subject)) {
                count++;
            }
        }
        return count;
    }
}