package com.hexvane.aetherhaven.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.ui.audit.UiMarkup;
import com.hexvane.aetherhaven.ui.audit.UiSelectorAudit;
import com.hexvane.aetherhaven.ui.audit.UiTypeTable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The selector audit ({@link UiSelectorAudit}) on a page small enough to read: what it must call an error, what it
 * must let through, and what it must list as undecided. The page is a cut-down Scarecrow Fields screen, the one whose
 * {@code #Seeds.Disabled} disconnected a player on 2026-10-11.
 */
@Tag("town")
class CustomUiSelectorAuditTest {
    static final String FIELD_PAGE = """
        $C = "../Common.ui";
        Group #Page {
          Group #Palette { LayoutMode: Left; }
          Group #Tools {
            $C.@SecondaryTextButton #Fill { Text: "Fill"; }
            $C.@SecondaryTextButton #Seeds { Text: "Add seeds"; }
          }
          Label #Status { Text: ""; }
        }
        """;
    /** One crop of the palette. Its seed count was {@code #Seeds} when the page broke. */
    static final String CROP_ROW = """
        Button {
          ItemIcon #Icon { Anchor: (Width: 40, Height: 40); }
          Label #SeedCount { Text: ""; }
        }
        """;
    /** The same shapes the real page uses: rows through their container's index, the tools by name from a list. */
    static final String FIELD_JAVA = """
        package fixture;
        import java.util.List;
        final class FieldPage {
            void build(UICommandBuilder b, UIEventBuilder e) {
                b.append("Fixture/FieldPage.ui");
                for (int i = 0; i < 3; i++) {
                    b.append("#Palette", "Fixture/Crop.ui");
                    String path = "#Palette[" + i + "]";
                    b.set(path + " #Icon.ItemId", "Plant_Crop_Wheat_Item");
                    b.set(path + " #SeedCount.Text", "4");
                }
                for (String tool : List.of("Fill", "Seeds")) b.set("#" + tool + ".Disabled", true);
                b.set("#Status.Text", "ready");
                // more
            }
        }
        """;

    /** Runs the audit over one page document, one row document and one or more Java classes written out as files. */
    static UiSelectorAudit.Report audit(String page, String row, String... java) throws IOException {
        Path folder = Files.createTempDirectory("aetherhaven-ui-audit");
        try {
            Files.createDirectories(folder.resolve("ui/Fixture"));
            Files.createDirectories(folder.resolve("java/fixture"));
            Files.writeString(folder.resolve("ui/Fixture/FieldPage.ui"), page, StandardCharsets.UTF_8);
            Files.writeString(folder.resolve("ui/Fixture/Crop.ui"), row, StandardCharsets.UTF_8);
            for (int i = 0; i < java.length; i++) Files.writeString(folder.resolve("java/fixture/Fixture" + i + ".java"), java[i], StandardCharsets.UTF_8);
            var library = new UiMarkup.Library();
            library.addFolder(folder.resolve("ui"));
            UiTypeTable.addVanilla(library, Path.of(""));
            return UiSelectorAudit.run(folder.resolve("java"), library, UiTypeTable.load(Path.of("")));
        } finally {
            try (var files = Files.walk(folder)) {
                for (var file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }

    private static UiSelectorAudit.Report withLine(String line) throws IOException {
        return audit(FIELD_PAGE, CROP_ROW, FIELD_JAVA.replace("// more", line));
    }

    private static String errors(UiSelectorAudit.Report report) { return String.join("\n", report.errors); }

    @Test void thePageAsItShouldBeHasNothingToReport() throws IOException {
        var report = audit(FIELD_PAGE, CROP_ROW, FIELD_JAVA);
        assertTrue(report.errors.isEmpty(), errors(report));
        assertTrue(report.undecided.isEmpty(), report.undecided.toString());
        assertEquals(1, report.pages.size(), report.pages.toString());
        // The append, the two row commands, the two tools and the status line.
        assertEquals(6, report.checked, report.text());
        assertEquals(5, report.properties, report.text());
    }

    @Test void anIdSharedByThePageAndARowIsAnErrorWhereverItIsAddressedWithoutTheRow() throws IOException {
        var report = audit(FIELD_PAGE, CROP_ROW.replace("#SeedCount", "#Seeds"), FIELD_JAVA.replace("#SeedCount", "#Seeds"));
        String errors = errors(report);
        // Both definitions are named, and the one the client takes.
        assertTrue(errors.contains("The client takes the first in tree order: Label #Seeds (Fixture/Crop.ui:3), appended to #Palette by fixture/Fixture0.java:7."), errors);
        assertTrue(errors.contains("Also: TextButton #Seeds (Common.ui:"), errors);
        assertTrue(errors.contains("a Label has no property Disabled"), errors);
        // The row's own commands go through the container's index and stay fine.
        assertTrue(!errors.contains("#Palette[<n>] #Seeds.Text"), errors);
        assertEquals(2, report.errors.size(), errors);
    }

    @Test void aSelectorThatMatchesNoElementIsAnError() throws IOException {
        String errors = errors(withLine("b.set(\"#Stauts.Text\", \"x\");"));
        assertTrue(errors.contains("NOTHING  #Stauts.Text: no element #Stauts"), errors);
        errors = errors(withLine("b.set(\"#Palette[0] #Count.Text\", \"x\");"));
        assertTrue(errors.contains("NOTHING  #Palette[0] #Count.Text: no element #Palette #Count below Button (Fixture/Crop.ui:1)"), errors);
    }

    @Test void aPropertyTheElementTypeDoesNotHaveIsAnError() throws IOException {
        String errors = errors(withLine("b.set(\"#Status.Value\", \"x\");"));
        assertTrue(errors.contains("PROPERTY  #Status.Value lands on Label #Status (Fixture/FieldPage.ui:8): a Label has no property Value"), errors);
        errors = errors(withLine("b.set(\"#Palette[2] #Icon.Text\", \"x\");"));
        assertTrue(errors.contains("an ItemIcon has no property Text"), errors);
        errors = errors(withLine("b.set(\"#Status.Style.Colour\", \"#ffffff\");"));
        assertTrue(errors.contains("Label.Style (LabelStyle) has no field Colour"), errors);
        errors = errors(withLine("b.set(\"#Status.Anchor.Width\", 5);"));
        assertTrue(errors.contains("Anchor cannot be addressed by field"), errors);
        // What every element has, a style field and a state of a button's style are all fine.
        var fine = withLine("b.set(\"#Status.Visible\", true); b.set(\"#Status.Style.TextColor\", \"#ffffff\"); b.set(\"#Fill.Style.Default.LabelStyle.TextColor\", \"#ffffff\"); b.set(\"#Page.Background.Color\", \"#000000\");");
        assertTrue(fine.errors.isEmpty(), errors(fine));
    }

    @Test void aCommandOfTheWrongShapeIsAnError() throws IOException {
        String errors = errors(withLine("b.clear(\"#Palette.Visible\");"));
        assertTrue(errors.contains("SHAPE  CLEAR #Palette.Visible: this command needs an element"), errors);
        errors = errors(withLine("b.set(\"#Status\", \"x\");"));
        assertTrue(errors.contains("SHAPE  SET #Status: the selector names an element and no property to set"), errors);
        errors = errors(withLine("b.append(\"#Status\", \"Fixture/Crop.ui\");"));
        assertTrue(errors.contains("is a Label, which takes no children"), errors);
        errors = errors(withLine("b.append(\"#Palette\", \"Fixture/Missing.ui\");"));
        assertTrue(errors.contains("there is no such document"), errors);
    }

    @Test void anEventBindingAndAValueReadAreCheckedLikeACommand() throws IOException {
        String errors = errors(withLine("e.addEventBinding(CustomUIEventBindingType.Activating, \"#Seedz\", EventData.of(\"Action\", \"Seeds\"), false);"));
        assertTrue(errors.contains("NOTHING  #Seedz: no element #Seedz"), errors);
        errors = errors(withLine("e.addEventBinding(CustomUIEventBindingType.Activating, \"#Fill\", EventData.of(\"@Text\", \"#Status.Value\"), false);"));
        assertTrue(errors.contains("PROPERTY  #Status.Value lands on Label #Status"), errors);
    }

    @Test void aHelperInAnotherClassIsCheckedWithTheContainerThePageGivesIt() throws IOException {
        String helper = """
            package fixture;
            final class CropCells {
                static void fill(UICommandBuilder b, String row, boolean locked) {
                    b.set(row + " #Icon.ItemId", "Plant_Crop_Corn_Item");
                    if (locked) b.set(row + " #Lock.Visible", true);
                    b.set(row + " #SeedCount.Disabled", locked);
                }
            }
            """;
        var report = audit(FIELD_PAGE, CROP_ROW, FIELD_JAVA.replace("// more", "CropCells.fill(b, \"#Palette[\" + 1 + \"]\", false);"), helper);
        String errors = errors(report);
        // The lock line is behind a condition the page settles with false, so it is never sent and is not reported.
        assertTrue(!errors.contains("#Lock"), errors);
        assertTrue(errors.contains("fixture/Fixture1.java:6  PROPERTY  #Palette[1] #SeedCount.Disabled lands on Label #SeedCount (Fixture/Crop.ui:3)"), errors);
        assertEquals(1, report.errors.size(), errors);
    }

    @Test void aRenderCaseMaySendOnlyWhatTheJavaSends() throws IOException {
        var report = audit(FIELD_PAGE, CROP_ROW, FIELD_JAVA.replace("// more", "b.set(\"#Status.TextSpans\", message);"));
        Path folder = Files.createTempDirectory("aetherhaven-ui-cases");
        try {
            String commands = """
                {"type": "Append", "selector": "#Palette", "text": "Fixture/Crop.ui"},
                {"type": "Set", "selector": "#Palette[0] #SeedCount.Text", "data": "4"},
                {"type": "Set", "selector": "#Seeds.Disabled", "data": true},
                {"type": "Set", "selector": "#Status.TextSpans", "data": {"MessageId": "x"}},
                {"type": "Set", "selector": "#Status.Text", "data": "ready"}
                """;
            String invented = """
                , {"type": "Set", "selector": "#Fill.Visible", "data": false},
                {"type": "Append", "selector": "#Palette", "text": "Fixture/Other.ui"},
                {"type": "Set", "selector": "#Palette[0] #Icon.Visible", "data": true}
                """;
            String step = "{\"cases\": [{\"name\": \"field\", \"steps\": [{\"tool\": \"ui_render\", \"name\": \"palette\", \"arguments\": {\"root\": \"projects\","
                + " \"path\": \"Mod/src/main/resources/Common/UI/Custom/Fixture/FieldPage.ui\", \"commands\": [%s]}}]}]}";
            Files.writeString(folder.resolve("field.test.json"), step.formatted(commands), StandardCharsets.UTF_8);
            var cases = UiSelectorAudit.renderCases(report, folder);
            assertTrue(cases.invented.isEmpty(), cases.invented.toString());
            assertEquals(5, cases.commands);
            // What the page sets and the case never draws is kept for the report: here the Fill button and a row's icon.
            assertEquals("[FieldPage fixture/Fixture0.java:9 #Palette[<n>] #Icon.ItemId, FieldPage fixture/Fixture0.java:12 #Fill.Disabled]",
                cases.neverDrawn.get("Fixture/FieldPage.ui").toString());
            Files.writeString(folder.resolve("field.test.json"), step.formatted(commands + invented), StandardCharsets.UTF_8);
            cases = UiSelectorAudit.renderCases(report, folder);
            assertEquals(3, cases.invented.size(), cases.invented.toString());
            assertTrue(cases.invented.get(0).contains("Set #Fill.Visible is sent by none of FieldPage"), cases.invented.toString());
            assertTrue(cases.invented.get(1).contains("Append #Palette <- Fixture/Other.ui"), cases.invented.toString());
            assertTrue(cases.invented.get(2).contains("Set #Palette[0] #Icon.Visible"), cases.invented.toString());
        } finally {
            try (var files = Files.walk(folder)) {
                for (var file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }

    @Test void aSelectorThatIsOnlyKnownWhenThePageRunsIsListedAndNotPassedOver() throws IOException {
        var report = withLine("b.set(\"#\" + System.getProperty(\"which\") + \".Text\", \"x\");");
        assertTrue(report.errors.isEmpty(), errors(report));
        assertEquals("[UNRESOLVED | FieldPage | SET #<?>.Text]", report.undecided.keySet().toString());
        // A class that sends commands and belongs to no page is listed too.
        String orphan = """
            package fixture;
            final class Orphan {
                static void fill(UICommandBuilder b) { b.set("#Nowhere.Text", "x"); }
            }
            """;
        report = audit(FIELD_PAGE, CROP_ROW, FIELD_JAVA, orphan);
        assertEquals("[NO PAGE | Orphan.fill]", report.undecided.keySet().toString());
    }
}
