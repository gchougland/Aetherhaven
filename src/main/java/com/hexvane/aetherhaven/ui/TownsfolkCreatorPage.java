package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerLifePolicy;
import com.hexvane.aetherhaven.community.TownsfolkSubmissionService;
import com.hexvane.aetherhaven.cosmetics.PlayerSkinModelExporter;
import com.hexvane.aetherhaven.townsfolk.*;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

public final class TownsfolkCreatorPage extends AetherhavenInteractiveCustomUIPage<TownsfolkCreatorPage.Data> {
    private static final int PAGE_SIZE = 7;
    private final TownsfolkCreatorDraft draft = new TownsfolkCreatorDraft();
    private final Set<String> personalities = new TreeSet<>(), races = new TreeSet<>(), voices = new LinkedHashSet<>();
    private record Choice(String id, String name, String search) { }
    private List<Choice> itemChoices;
    private List<Choice> matches = List.of();
    private boolean picker, busy, submitted;
    private int itemPage;
    private String search = "", status = "Your character is shared only when you press Submit to admins.";

    public TownsfolkCreatorPage(PlayerRef player) {
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC);
        draft.name = player.getUsername();
        var plugin = AetherhavenPlugin.get();
        personalities.addAll(plugin.getTownsfolkPersonalityCatalog().allById().keySet());
        races.add("human");
        plugin.getTownsfolkCharacterCatalog().allById().values().forEach(c -> {
            if (c.getRace() != null && !c.getRace().isBlank()) races.add(c.getRace());
        });
        if (!personalities.containsAll(draft.traits) && personalities.size() >= 3) {
            draft.traits.clear(); draft.traits.addAll(personalities.stream().limit(3).toList());
        }
        for (String voice : VillagerLifePolicy.availableVoices()) {
            voices.add(voice); voices.add(voice + "Lower"); voices.add(voice + "Higher");
        }
    }

    @Override public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder b, @Nonnull UIEventBuilder e,
                                @Nonnull Store<EntityStore> store) {
        b.append("Aetherhaven/TownsfolkCreatorPage.ui");
        b.set("#CharacterName.Value", draft.name);
        for (String id : List.of("Gender", "Race", "Voice", "Trait0", "Trait1", "Trait2")) {
            Collection<String> choices = switch (id) { case "Gender" -> List.of("male", "female"); case "Race" -> races; case "Voice" -> voices; default -> personalities; };
            String value = switch (id) { case "Gender" -> draft.gender; case "Race" -> draft.race; case "Voice" -> draft.voice; default -> draft.traits.get(id.charAt(5) - '0'); };
            b.set("#" + id + ".Entries", dropdownEntries(choices)); b.set("#" + id + ".Value", value);
        }
        for (String id : List.of("CharacterName", "Gender", "Race", "Voice", "Trait0", "Trait1", "Trait2", "Search"))
            e.addEventBinding(CustomUIEventBindingType.ValueChanged, "#" + id, EventData.of("Field", id).append("@Value", "#" + id + ".Value"), false);
        for (var attribute : TownsfolkAttribute.values()) stat(b, e, "#Attributes", attribute.id, "attribute", draft.attributes.get(attribute.id));
        int index = 0;
        for (var skill : TownsfolkSkill.values()) stat(b, e, index++ < 6 ? "#SkillsLeft" : "#SkillsRight", skill.id, "skill", draft.skills.get(skill.id));
        b.set("#ItemsEmpty.Visible", draft.items.isEmpty());
        int i = 0;
        for (var item : draft.items.entrySet()) {
            b.append("#Requirements", "Aetherhaven/TownsfolkCreatorRequirement.ui");
            String row = "#Requirements[" + i++ + "]";
            b.set(row + " #Icon.ItemId", item.getKey());
            b.set(row + " #Name.Text", UiMaterialLabels.itemLabelForPlayer(playerRef, item.getKey()));
            b.set(row + " #Count.Text", Integer.toString(item.getValue()));
            action(e, row + " #Minus", "item:-1:" + item.getKey()); action(e, row + " #Plus", "item:1:" + item.getKey());
            action(e, row + " #Remove", "remove:" + item.getKey());
        }
        b.set("#ItemSummary.Text", draft.items.size() + " / 8 items  |  1-64 of each");
        b.set("#AddItem.Disabled", draft.items.size() >= 8);
        for (String action : List.of("AddItem", "ClosePicker", "Previous", "Next", "Submit", "Close")) action(e, "#" + action, action);
        b.set("#Search.Value", search);
        b.set("#Picker.Visible", picker);
        if (picker) renderPicker(b, e);
        b.set("#Editor.Visible", !busy && !submitted);
        b.set("#UploadNotice.Visible", busy || submitted);
        b.set("#UploadNotice.Text", submitted ? "Your townsfolk is ready for admin review. Thank you!" : "Uploading your character sheet and current appearance...");
        b.set("#Submit.Disabled", busy || submitted);
        b.set("#Status.Text", status);
    }

    private void stat(UICommandBuilder b, UIEventBuilder e, String container, String id, String kind, int value) {
        int index = kind.equals("attribute") ? new ArrayList<>(draft.attributes.keySet()).indexOf(id)
            : new ArrayList<>(draft.skills.keySet()).indexOf(id) % 6;
        b.append(container, "Aetherhaven/TownsfolkCreatorScore.ui");
        String row = container + "[" + index + "]";
        b.set(row + " #Name.Text", title(id));
        b.set(row + " #Score.Text", Integer.toString(value));
        b.set(row + " #Note.Text", kind.equals("skill") && value == 0 ? "Cannot learn" : kind.equals("skill") ? "Skill level" : "Attribute");
        b.set(row + " #Minus.Disabled", value <= (kind.equals("skill") ? 0 : 1));
        b.set(row + " #Plus.Disabled", value >= (kind.equals("skill") ? 10 : 20));
        action(e, row + " #Minus", kind + ":-1:" + id); action(e, row + " #Plus", kind + ":1:" + id);
    }

    private ObjectArrayList<DropdownEntryInfo> dropdownEntries(Collection<String> choices) {
        ObjectArrayList<DropdownEntryInfo> entries = new ObjectArrayList<>();
        for (String choice : choices) entries.add(new DropdownEntryInfo(LocalizableString.fromString(title(choice)), choice));
        return entries;
    }
    private static void action(UIEventBuilder e, String selector, String action) {
        e.addEventBinding(CustomUIEventBindingType.Activating, selector, EventData.of("Action", action).append("@Name", "#CharacterName.Value"), false);
    }
    private static String title(String id) {
        if (id.isEmpty()) return id;
        String words = id.replace('_', ' ').replaceAll("([a-z])([A-Z])", "$1 $2");
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
    private static boolean knownItem(String id) { return id != null && Item.getAssetMap().getAsset(id) != null; }

    private void filterItems() {
        if (itemChoices == null) {
            itemChoices = Item.getAssetMap().getAssetMap().keySet().stream().filter(TownsfolkCreatorPage::knownItem).map(id -> {
                String name = UiMaterialLabels.itemLabelForPlayer(playerRef, id);
                return new Choice(id, name, (name + " " + id).toLowerCase(Locale.ROOT));
            }).sorted(Comparator.comparing(Choice::name, String.CASE_INSENSITIVE_ORDER)).toList();
        }
        String needle = search.trim().toLowerCase(Locale.ROOT);
        matches = itemChoices.stream().filter(c -> c.search.contains(needle)).toList();
        itemPage = Math.clamp(itemPage, 0, Math.max(0, (matches.size() - 1) / PAGE_SIZE));
    }
    private void renderPicker(UICommandBuilder b, UIEventBuilder e) {
        b.clear("#Choices");
        for (int i = 0, start = itemPage * PAGE_SIZE; i < PAGE_SIZE && start + i < matches.size(); i++) {
            Choice choice = matches.get(start + i);
            b.append("#Choices", "Aetherhaven/TownsfolkCreatorItem.ui");
            String row = "#Choices[" + i + "]";
            b.set(row + " #Icon.ItemId", choice.id); b.set(row + " #Name.Text", choice.name); b.set(row + " #Id.Text", choice.id);
            action(e, row, "pick:" + choice.id);
        }
        b.set("#NoResults.Visible", matches.isEmpty());
        b.set("#PageCount.Text", matches.size() + " items  |  Page " + (itemPage + 1) + " / " + Math.max(1, (matches.size() + PAGE_SIZE - 1) / PAGE_SIZE));
        b.set("#Previous.Disabled", itemPage == 0); b.set("#Next.Disabled", (itemPage + 1) * PAGE_SIZE >= matches.size());
    }

    @Override public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Data data) {
        if ("Close".equals(data.action)) { close(); return; }
        if (busy || submitted) return;
        if (data.name != null) draft.name = data.name;
        if (data.field != null && data.value != null) {
            switch (data.field) {
                case "CharacterName" -> draft.name = data.value;
                case "Gender" -> { if (List.of("male", "female").contains(data.value)) draft.gender = data.value; }
                case "Race" -> { if (races.contains(data.value)) draft.race = data.value; }
                case "Voice" -> { if (voices.contains(data.value)) draft.voice = data.value; }
                case "Trait0", "Trait1", "Trait2" -> { if (personalities.contains(data.value)) draft.traits.set(data.field.charAt(5) - '0', data.value); }
                case "Search" -> {
                    search = data.value; itemPage = 0;
                    if (picker) { filterItems(); var b = new UICommandBuilder(); var e = new UIEventBuilder(); renderPicker(b, e); sendUpdate(b, e, false); }
                }
            }
            return; // Do not rebuild text fields while the player is typing.
        }
        if (data.action == null) return;
        switch (data.action) {
            case "AddItem" -> { picker = true; itemPage = 0; filterItems(); }
            case "ClosePicker" -> picker = false;
            case "Previous" -> { itemPage--; filterItems(); }
            case "Next" -> { itemPage++; filterItems(); }
            case "Submit" -> submit(ref, store);
            default -> {
                String[] parts = data.action.split(":", 3);
                if (data.action.startsWith("pick:") && picker) { draft.addItem(data.action.substring(5), TownsfolkCreatorPage::knownItem); picker = false; }
                else if (data.action.startsWith("remove:")) draft.items.remove(data.action.substring(7));
                else if (parts.length == 3) {
                    try { draft.adjust(parts[0], parts[2], Integer.parseInt(parts[1])); } catch (NumberFormatException ignored) { }
                }
            }
        }
        rebuild();
    }

    private void submit(Ref<EntityStore> ref, Store<EntityStore> store) {
        var config = AetherhavenPlugin.get().getConfig().get().getCommunityMarketplace();
        if (!config.isEnabled()) { status = "Website submissions are disabled in this server's marketplace settings."; return; }
        try {
            var character = draft.toJson(playerRef.getUuid(), personalities, races, voices, TownsfolkCreatorPage::knownItem);
            var component = store.getComponent(ref, PlayerSkinComponent.getComponentType());
            if (component == null) { status = "Your appearance is not ready yet. Please try again in a moment."; return; }
            var cosmetics = CosmeticsModule.get();
            cosmetics.validateSkin(component.getPlayerSkin());
            var model = PlayerSkinModelExporter.toModelJson(component.getPlayerSkin(), cosmetics.getRegistry());
            String url = config.getApiBaseUrl(), name = playerRef.getUsername(); var author = playerRef.getUuid();
            var world = store.getExternalData().getWorld();
            busy = true; status = "Uploading both files and creating your portrait. You may close this screen while it finishes.";
            CompletableFuture.supplyAsync(() -> TownsfolkSubmissionService.submit(url, author, name, character, model))
                .exceptionally(error -> new TownsfolkSubmissionService.Result(false, "Upload failed. Your sheet is still here; please retry."))
                .thenAccept(result -> world.execute(() -> {
                    busy = false; submitted = result.success(); status = result.message();
                    playerRef.sendMessage(Message.raw(status));
                    if (ref.isValid() && !isDismissed()) rebuild();
                }));
        } catch (IllegalArgumentException e) { status = e.getMessage(); }
        catch (Exception e) { status = "Could not export your current appearance. Please check your avatar and try again."; }
    }

    public static final class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d,v) -> d.action=v, d -> d.action).add()
            .append(new KeyedCodec<>("Field", Codec.STRING), (d,v) -> d.field=v, d -> d.field).add()
            .append(new KeyedCodec<>("@Value", Codec.STRING), (d,v) -> d.value=v, d -> d.value).add()
            .append(new KeyedCodec<>("@Name", Codec.STRING), (d,v) -> d.name=v, d -> d.name).add().build();
        private String action, field, value, name;
    }
}
