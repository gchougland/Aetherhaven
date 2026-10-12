package com.hexvane.aetherhaven.ui;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The action names of each held tool, and where the player's real keys for them come from.
 *
 * <p>Only the player's client knows which key is bound to an input. The server cannot ask for it (no packet carries
 * bindings) and must not read a settings file: that file belongs to whoever runs the server, not to the player.
 * In Hytale 0.7.0 the client itself puts the player's key beside a root interaction that sets
 * {@code HudInputBindingEntry}. Every tool item therefore sets {@code HudInputBindingEntry} on each input it binds,
 * to the translation key returned by {@link #promptKey(InteractionType)}.
 *
 * <p>For a held item the client only lists one input that way. {@link ToolKeyPromptOverrides} explains the client's
 * rule and gets the other inputs listed too.
 *
 * <p>The mod's own overlays cannot show a key at all: the client fills in a {@code HotkeyLabel} only inside a builder
 * tool's legend, never in a custom HUD. Overlay rows are labelled with the same action name the game shows beside the
 * key ({@link #label(ToolKeybindSlot)}), so a label can never disagree with the player's bindings.
 */
public enum ToolKeyPrompts {
    /** The game's own names for the inputs. For a tool that has no key set of its own yet. */
    GENERIC("generic"),
    PATH_TOOL("pathTool"),
    /** Plot Creator Staff, its bounds form, and the Building Editor Staff. */
    PLOT_CREATOR("plotCreator"),
    PATROL_WAND("patrolWand"),
    /** Every Building Staff tier and the Workplace Tool. */
    BUILDING_STAFF("buildingStaff"),
    POI_TOOL("poiTool"),
    RTS_FLAG("rtsFlag"),
    RTS_SWORD("rtsSword"),
    RTS_STANCE("rtsStance"),
    RTS_FREE("rtsFree"),
    RTS_SELECT("rtsSelect"),
    RTS_EXIT("rtsExit");

    /** Lang file (without extension) that holds every action name. */
    public static final String LANG_FILE = "aetherhaven_tool_keys";

    private static final String KEY_PREFIX = LANG_FILE + ".aetherhaven.toolKeys.";

    /** One line telling the player where the keys are listed. */
    public static final String WHERE_KEYS_KEY = KEY_PREFIX + "whereKeys";

    /** "Crouch +": the first half of a chord row. A chord has no key prompt, so it is named by its two actions. */
    public static final String CROUCH_MODIFIER_KEY = KEY_PREFIX + "crouchModifier";

    /** Every held tool that has key prompts, by item id, with the key set its item JSON uses. */
    private static final Map<String, ToolKeyPrompts> TOOL_ITEMS = toolItems();

    private final String id;

    ToolKeyPrompts(@Nonnull String id) {
        this.id = id;
    }

    @Nonnull
    private static Map<String, ToolKeyPrompts> toolItems() {
        Map<String, ToolKeyPrompts> items = new LinkedHashMap<>();
        items.put("Aetherhaven_Path_Tool", PATH_TOOL);
        items.put("Aetherhaven_Plot_Creator_Staff", PLOT_CREATOR);
        items.put("Aetherhaven_Plot_Creator_Staff_Bounds", PLOT_CREATOR);
        items.put("Aetherhaven_Building_Editor_Staff", PLOT_CREATOR);
        items.put("Aetherhaven_Patrol_Wand", PATROL_WAND);
        items.put("Aetherhaven_Building_Staff", BUILDING_STAFF);
        items.put("Aetherhaven_Building_Staff_Iron", BUILDING_STAFF);
        items.put("Aetherhaven_Building_Staff_Cobalt", BUILDING_STAFF);
        items.put("Aetherhaven_Building_Staff_Thorium", BUILDING_STAFF);
        items.put("Aetherhaven_Building_Staff_Adamantite", BUILDING_STAFF);
        items.put("Aetherhaven_Workplace_Tool", BUILDING_STAFF);
        items.put("Aetherhaven_Poi_Tool", POI_TOOL);
        items.put("Aetherhaven_Rts_Flag", RTS_FLAG);
        items.put("Aetherhaven_Rts_Sword", RTS_SWORD);
        items.put("Aetherhaven_Rts_Stance_Banner", RTS_STANCE);
        items.put("Aetherhaven_Rts_Free", RTS_FREE);
        items.put("Aetherhaven_Rts_Select_All", RTS_SELECT);
        items.put("Aetherhaven_Rts_Select_Knight", RTS_SELECT);
        items.put("Aetherhaven_Rts_Select_Archer", RTS_SELECT);
        items.put("Aetherhaven_Rts_Select_Mage", RTS_SELECT);
        items.put("Aetherhaven_Rts_Exit", RTS_EXIT);
        return Collections.unmodifiableMap(items);
    }

    /** Whether a {@code HudInputBindingEntry} value is one of this mod's tool prompts. */
    public static boolean isPromptKey(@Nullable String hudInputBindingEntry) {
        return hudInputBindingEntry != null && hudInputBindingEntry.startsWith(KEY_PREFIX);
    }

    /** Item id of every tool with key prompts, mapped to its key set. */
    @Nonnull
    public static Map<String, ToolKeyPrompts> items() {
        return TOOL_ITEMS;
    }

    @Nonnull
    public String id() {
        return id;
    }

    /**
     * Translation key of the action name for an input of this tool. The tool's item JSON sets exactly this value as
     * {@code HudInputBindingEntry} on the root interaction bound to {@code type}.
     */
    @Nonnull
    public String promptKey(@Nonnull InteractionType type) {
        return KEY_PREFIX + id + "." + ToolKeybindSlot.langSuffix(type);
    }

    /**
     * Translation key of the name shown for a slot. For a game input that is not an item interaction (sprint, fly
     * down, cancel) it is the game's own name for that input, the same for every tool.
     */
    @Nonnull
    public String promptKey(@Nonnull ToolKeybindSlot slot) {
        InteractionType type = slot.interactionType();
        if (type == null) {
            return KEY_PREFIX + GENERIC.id + "." + slot.langSuffix();
        }
        return promptKey(type);
    }

    /**
     * The row label for a slot: the name of its action. Never a key name. For a crouch chord this is the name of the
     * action used while crouching, and the row puts {@link #crouchModifierLabel()} in front of it.
     */
    @Nonnull
    public Message label(@Nonnull ToolKeybindSlot slot) {
        return Message.translation(promptKey(slot));
    }

    /** "Crouch +", shown in front of the action name on a chord row. */
    @Nonnull
    public static Message crouchModifierLabel() {
        return Message.translation(CROUCH_MODIFIER_KEY);
    }
}
