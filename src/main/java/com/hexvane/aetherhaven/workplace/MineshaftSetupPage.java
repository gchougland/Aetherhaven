package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownTerritoryClaims;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.joml.Vector3i;

/** Unsaved entrance options. Only Save changes a designation; excavation needs a separate review. */
public final class MineshaftSetupPage extends AetherhavenInteractiveCustomUIPage<MineshaftSetupPage.Data> {
    private final UUID townId, editing;
    private final Vector3i ground;
    private MineshaftSelection.Direction direction = MineshaftSelection.Direction.NORTH;
    private ExcavationPlan.Layout layout = ExcavationPlan.Layout.STRAIGHT;
    private String name = "New mineshaft", lengthText = "12", hint;
    private boolean appended, initialized;
    private Snapshot opened;

    private record Context(TownManager manager, TownRecord town, ApprenticeWorkplace site) {}
    private record Snapshot(ApprenticeWorkplace site, String name, WorkplaceArea area,
                            ExcavationPlan plan, UUID planId, long revision, int completed, int fitted,
                            ExcavationPlan.Entrance entrance, ExcavationPlan.Layout layout) {}

    public MineshaftSetupPage(PlayerRef player, UUID townId, UUID editing, Vector3i ground) {
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.townId = townId;
        this.editing = editing;
        this.ground = new Vector3i(Objects.requireNonNull(ground));
    }

    private Context current(Ref<EntityStore> ref, Store<EntityStore> store) {
        var plugin = AetherhavenPlugin.get();
        if (plugin == null) return null;
        var manager = AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin);
        var town = manager.getTown(townId);
        var player = playerRef.getUuid();
        if (town == null || !town.hasMemberOrOwner(player) || !town.playerCanManageConstructions(player)
                || !town.playerCanBreakBlocks(player)) return null;
        var transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return null;
        var position = transform.getPosition();
        if (!TownTerritoryClaims.contains(town, ChunkUtil.chunkCoordinate((int) Math.floor(position.x)),
                ChunkUtil.chunkCoordinate((int) Math.floor(position.z)))) return null;
        var site = editing == null ? null : town.getApprenticeWorkplaces().find(editing);
        if (editing != null && (site == null || site.type() != TownsfolkWorkType.MINING)) return null;
        return new Context(manager, town, site);
    }

    private void initialize(Context context) {
        if (initialized) return;
        initialized = true;
        var site = context.site();
        if (site == null) return;
        var plan = site.existingExcavation();
        opened = new Snapshot(site, site.name(), site.area(), plan, plan == null ? null : plan.id(),
                plan == null ? 0 : plan.revision(), plan == null ? 0 : plan.completed(),
                plan == null ? 0 : plan.fitted(), site.mineshaftEntrance(), site.mineshaftLayout());
        name = Objects.requireNonNullElse(site.name(), "New mineshaft");
        layout = site.mineshaftLayout();
        direction = MineshaftSelection.direction(site.mineshaftEntrance());
        long existingLength = (direction == MineshaftSelection.Direction.NORTH || direction == MineshaftSelection.Direction.SOUTH
                ? (long) site.area().maxZ() - site.area().minZ()
                : (long) site.area().maxX() - site.area().minX()) - 3;
        if (existingLength >= MineshaftSelection.MINIMUM_LENGTH && existingLength <= MineshaftSelection.maximumLength(layout))
            lengthText = Long.toString(existingLength);
    }

    private String staleIssue(Context context) {
        if (editing == null) return null;
        var site = context.site();
        var plan = site.existingExcavation();
        if (opened == null || opened.site() != site || !Objects.equals(opened.name(), site.name())
                || !Objects.equals(opened.area(), site.area()) || opened.plan() != plan
                || opened.entrance() != site.mineshaftEntrance() || opened.layout() != site.mineshaftLayout()
                || plan != null && (!Objects.equals(opened.planId(), plan.id()) || opened.revision() != plan.revision()
                || opened.completed() != plan.completed() || opened.fitted() != plan.fitted()))
            return "This mineshaft changed. Close this page and select it again.";
        return plan != null && plan.held() ? "Let the Apprentice deliver collected material before changing this mineshaft." : null;
    }

    private Integer length() {
        try {
            int value = Integer.parseInt(lengthText.strip());
            return value >= MineshaftSelection.MINIMUM_LENGTH && value <= MineshaftSelection.maximumLength(layout) ? value : null;
        } catch (NumberFormatException error) { return null; }
    }

    private WorkplaceArea selectedArea() {
        var length = length();
        if (length == null) return null;
        try { return MineshaftSelection.area(ground.x, ground.y, ground.z, direction, length, layout); }
        catch (IllegalArgumentException | ArithmeticException error) { return null; }
    }

    private String inputIssue() {
        if (length() == null)
            return "Enter a whole tunnel length from " + MineshaftSelection.MINIMUM_LENGTH + " to " + MineshaftSelection.maximumLength(layout) + " blocks.";
        if (selectedArea() == null || !selectedArea().valid()) return "Choose another ground block for an entrance at a supported height.";
        if (name == null || name.strip().isEmpty() || name.strip().length() > 48 || name.chars().anyMatch(Character::isISOControl))
            return "Choose a name with 1 to 48 characters.";
        return null;
    }

    @Override public void build(Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store) {
        var context = current(ref, store);
        if (context == null) { close(); return; }
        initialize(context);
        if (!appended) {
            commands.append("Aetherhaven/MineshaftSetupPage.ui");
            commands.set("#Name.Value", name);
            commands.set("#Length.Value", lengthText);
            for (var action : List.of("Direction", "Layout", "Shorter", "Longer", "Save", "Cancel"))
                events.addEventBinding(CustomUIEventBindingType.Activating, "#" + action,
                        new EventData().append("Action", action).append("@Name", "#Name.Value").append("@Length", "#Length.Value"), false);
            events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#Name",
                    new EventData().append("Action", "Name").append("@Name", "#Name.Value"), false);
            events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#Length",
                    new EventData().append("Action", "Length").append("@Length", "#Length.Value"), false);
            appended = true;
        }
        render(context, commands);
    }

    private void render(Context context, UICommandBuilder commands) {
        String directionName = switch (direction) { case NORTH -> "North"; case EAST -> "East"; case SOUTH -> "South"; case WEST -> "West"; };
        commands.set("#Direction.Text", "Dig toward " + directionName);
        commands.set("#Layout.Text", layout.label);
        commands.set("#LayoutDescription.Text", layout.description);
        commands.set("#LengthRange.Text", MineshaftSelection.MINIMUM_LENGTH + " to " + MineshaftSelection.maximumLength(layout) + " blocks");
        commands.set("#EntrancePosition.Text", "Selected ground: " + ground.x + ", " + ground.y + ", " + ground.z);
        var length = length();
        commands.set("#Shorter.Disabled", length == null || length <= MineshaftSelection.MINIMUM_LENGTH);
        commands.set("#Longer.Disabled", length == null || length >= MineshaftSelection.maximumLength(layout));
        var area = selectedArea();
        if (area == null || !area.valid()) {
            commands.set("#Area.Text", "Enter a valid length to see the workplace size.");
            commands.set("#Coordinates.Text", "");
            commands.set("#FirstRow.Text", "");
        } else {
            commands.set("#Area.Text", "Workplace: " + (area.maxX() - area.minX()) + " by " + (area.maxZ() - area.minZ())
                    + " blocks | " + WorkplaceArea.HEIGHT + " blocks high");
            commands.set("#Coordinates.Text", "Corners: " + area.minX() + ", " + area.floorY() + ", " + area.minZ()
                    + " to " + (area.maxX() - 1) + ", " + (area.floorY() + WorkplaceArea.HEIGHT - 1) + ", " + (area.maxZ() - 1));
            var tunnel = ExcavationPlan.tunnel(area, MineshaftSelection.entrance(direction), layout);
            var first = tunnel.isEmpty() ? null : tunnel.getFirst();
            int firstX = ground.x + (direction == MineshaftSelection.Direction.EAST ? 1 : direction == MineshaftSelection.Direction.WEST ? -1 : 0);
            int firstZ = ground.z + (direction == MineshaftSelection.Direction.SOUTH ? 1 : direction == MineshaftSelection.Direction.NORTH ? -1 : 0);
            commands.set("#FirstRow.Text", first == null ? "" : "First digging row center: " + firstX + ", " + firstZ
                    + " | Walking height " + ExcavationPlan.floorAt(area, MineshaftSelection.entrance(direction), layout, first));
        }
        String issue = staleIssue(context);
        if (issue == null) issue = inputIssue();
        commands.set("#Save.Disabled", issue != null);
        commands.set("#Hint.Text", issue != null ? issue : hint != null ? hint
                : "Save the workplace, then review the rock. Digging begins only when you start the reviewed tunnel.");
    }

    @Override public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, Data data) {
        if ("Cancel".equals(data.action)) {
            var state = WorkplaceToolState.obtain(ref, store);
            state.clearSelection();
            state.hint = "Mineshaft setup cancelled. Your saved workplaces are unchanged.";
            close();
            return;
        }
        var context = current(ref, store);
        if (context == null) { close(); return; }
        initialize(context);
        if (data.name != null) name = data.name;
        if (data.length != null) lengthText = data.length;
        hint = null;
        boolean writeLength = false;
        switch (Objects.requireNonNullElse(data.action, "")) {
            case "Direction" -> direction = MineshaftSelection.Direction.values()[(direction.ordinal() + 1) % MineshaftSelection.Direction.values().length];
            case "Layout" -> {
                Integer previousLength = length();
                layout = ExcavationPlan.Layout.values()[(layout.ordinal() + 1) % ExcavationPlan.Layout.values().length];
                if (previousLength != null && previousLength > MineshaftSelection.maximumLength(layout)) {
                    lengthText = Integer.toString(MineshaftSelection.maximumLength(layout));
                    writeLength = true;
                    hint = "This layout supports up to " + lengthText + " blocks. The length was shortened to fit.";
                }
            }
            case "Shorter", "Longer" -> {
                Integer value = length();
                if (value != null) {
                    int next = value + ("Longer".equals(data.action) ? 1 : -1);
                    if (next >= MineshaftSelection.MINIMUM_LENGTH && next <= MineshaftSelection.maximumLength(layout)) {
                        lengthText = Integer.toString(next);
                        writeLength = true;
                    }
                }
            }
            case "Save" -> {
                hint = staleIssue(context);
                if (hint == null) hint = inputIssue();
                var player = store.getComponent(ref, Player.getComponentType());
                if (hint == null && player != null) {
                    var area = selectedArea();
                    var sites = context.town().getApprenticeWorkplaces();
                    hint = sites.validate(context.town(), editing, TownsfolkWorkType.MINING, area, name);
                    if (hint == null) hint = sites.save(context.town(), editing, TownsfolkWorkType.MINING, area, name);
                    if (hint == null) {
                        var saved = editing == null ? sites.sites().stream()
                                .filter(site -> site.type() == TownsfolkWorkType.MINING && area.equals(site.area()))
                                .findFirst().orElse(null) : sites.find(editing);
                        if (saved != null) saved.setMineshaftConfiguration(MineshaftSelection.entrance(direction), layout);
                        context.manager().updateTown(context.town());
                        var state = WorkplaceToolState.obtain(ref, store);
                        state.clearSelection();
                        state.hint = "Mineshaft saved. Review the rock before starting excavation.";
                        if (saved != null) player.getPageManager().openCustomPage(ref, store,
                                new ExcavationPage(playerRef, townId, saved.id(), null, MineshaftSelection.entrance(direction), layout));
                        else close();
                        return;
                    }
                }
            }
            case "Name", "Length" -> { }
            default -> { return; }
        }
        var commands = new UICommandBuilder();
        if (writeLength) commands.set("#Length.Value", lengthText);
        render(context, commands);
        sendUpdate(commands, new UIEventBuilder(), false);
    }

    public static final class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
                .append(new KeyedCodec<>("@Name", Codec.STRING), (d, v) -> d.name = v, d -> d.name).add()
                .append(new KeyedCodec<>("@Length", Codec.STRING), (d, v) -> d.length = v, d -> d.length).add().build();
        String action, name, length;
    }
}
