package io.github.michelbr84.flapforge.content.defs;

import java.util.List;

/**
 * The root of {@code achievements.json}: the achievement list in file order, plus room for the file-level
 * {@code _comment} that records which fields land in which milestone (E19).
 *
 * @param achievements the achievements
 */
public record AchievementsDef(List<AchievementDef> achievements) {

    /**
     * Copies the list.
     */
    public AchievementsDef {
        achievements = List.copyOf(achievements);
    }
}
