package com.hexvane.aetherhaven.guide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** One wiki topic loaded from {@code Common/Docs/Hexvane_AetherhavenWiki/<id>.md}. */
public final class GuideTopicFile {
    private final String id;
    private final String displayName;
    private final String description;
    @Nullable
    private final String npcRoleId;
    @Nonnull
    private final List<String> subTopicIds;
    @Nonnull
    private final String markdownBody;

    public GuideTopicFile(
        @Nonnull String id,
        @Nonnull String displayName,
        @Nonnull String description,
        @Nullable String npcRoleId,
        @Nonnull List<String> subTopicIds,
        @Nonnull String markdownBody
    ) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.npcRoleId = npcRoleId;
        this.subTopicIds = List.copyOf(subTopicIds);
        this.markdownBody = markdownBody;
    }

    @Nonnull
    public String id() {
        return id;
    }

    @Nonnull
    public String displayName() {
        return displayName;
    }

    @Nonnull
    public String description() {
        return description;
    }

    @Nullable
    public String npcRoleId() {
        return npcRoleId;
    }

    @Nonnull
    public List<String> subTopicIds() {
        return subTopicIds;
    }

    @Nonnull
    public String markdownBody() {
        return markdownBody;
    }

    @Nonnull
    public static GuideTopicFile parse(@Nonnull String id, @Nonnull String rawFileText) {
        String text = rawFileText.replace("\r\n", "\n");
        if (!text.startsWith("---\n")) {
            return new GuideTopicFile(id, humanizeId(id), "", null, List.of(), text.trim());
        }
        int end = text.indexOf("\n---\n", 4);
        if (end < 0) {
            return new GuideTopicFile(id, humanizeId(id), "", null, List.of(), text.trim());
        }
        String fm = text.substring(4, end);
        String body = text.substring(end + 5).trim();
        String name = "";
        String desc = "";
        String npc = null;
        List<String> subs = new ArrayList<>();
        boolean inSubs = false;
        for (String line : fm.split("\n")) {
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (inSubs) {
                if (t.startsWith("- ")) {
                    subs.add(t.substring(2).trim());
                    continue;
                }
                int colonEarly = t.indexOf(':');
                if (colonEarly > 0 && !t.startsWith("-")) {
                    String maybeKey = t.substring(0, colonEarly).trim();
                    if (!maybeKey.isEmpty() && maybeKey.indexOf(' ') < 0) {
                        inSubs = false;
                    } else {
                        continue;
                    }
                } else {
                    continue;
                }
            }
            if (t.startsWith("sub-topics:")) {
                inSubs = true;
                continue;
            }
            int colon = t.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = t.substring(0, colon).trim();
            String val = stripYamlScalarQuotes(t.substring(colon + 1).trim());
            switch (key) {
                case "name" -> name = val;
                case "description" -> desc = val;
                case "npcRoleId" -> npc = val.isEmpty() ? null : val;
                default -> {}
            }
        }
        if (name.isBlank()) {
            name = humanizeId(id);
        }
        return new GuideTopicFile(id, name, desc, npc, subs, body);
    }

    @Nonnull
    private static String humanizeId(@Nonnull String id) {
        return id.replace('_', ' ');
    }

    /**
     * Strips a single pair of YAML-style surrounding quotes so {@code name: "Corin Mosscup"} yields {@code Corin
     * Mosscup}.
     */
    @Nonnull
    private static String stripYamlScalarQuotes(@Nonnull String val) {
        if (val.length() < 2) {
            return val;
        }
        char a = val.charAt(0);
        char b = val.charAt(val.length() - 1);
        if ((a == '"' && b == '"') || (a == '\'' && b == '\'')) {
            return val.substring(1, val.length() - 1);
        }
        return val;
    }

    @Nonnull
    public static GuideTopicFile missing(@Nonnull String id) {
        return new GuideTopicFile(id, id, "", null, Collections.emptyList(), "");
    }
}
