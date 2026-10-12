package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.ui.audit.UiSelectorAudit;
import com.hexvane.aetherhaven.ui.audit.UiTypeTable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("town")
class CustomUiTemplateContractTest {
    /** Reference the logical image name; the client selects the packaged density variant. */
    @Test void uiIconsDoNotRequestDensityVariantsDirectly() throws Exception {
        var densityVariant = Pattern.compile("@\\d+x\\.png");
        List<String> errors = new ArrayList<>();
        for (String root : List.of("src/main/java", "src/main/resources/Common/UI/Custom/Aetherhaven", "subplugin-assets")) {
            try (var files = Files.walk(Path.of(root))) {
                for (var file : files.filter(p -> p.toString().endsWith(".ui") || p.toString().endsWith(".java")).toList()) {
                    if (densityVariant.matcher(Files.readString(file)).find()) errors.add(file.toString());
                }
            }
        }
        assertTrue(errors.isEmpty(), "Use logical image paths instead of physical density variants: " + String.join("\n", errors));
    }

    /** An underscore in an element ID rejects the whole pack, reported as an unrelated missing HUD. */
    @Test void elementIdsUseNativeAlphanumericNames() throws Exception {
        var elementId = Pattern.compile("#([A-Za-z][A-Za-z0-9_-]*)\\s*\\{");
        List<String> errors = new ArrayList<>();
        for (String root : List.of("src/main/resources/Common/UI/Custom/Aetherhaven", "subplugin-assets")) {
            try (var files = Files.walk(Path.of(root))) {
                for (var file : files.filter(p -> p.toString().endsWith(".ui")).toList()) {
                    String source = Files.readString(file).replaceAll("(?s)/\\*.*?\\*/|//[^\\r\\n]*", "");
                    var matches = elementId.matcher(source);
                    while (matches.find()) if (!matches.group(1).matches("[A-Za-z][A-Za-z0-9]*")) {
                        errors.add(file + ": unsupported element ID " + matches.group(1));
                    }
                }
            }
        }
        assertTrue(errors.isEmpty(), String.join("\n", errors));
    }

    /** Anchor is a structured value. Subfield selectors disconnect the client when a page opens. */
    @Test void anchorUpdatesUseTheStructuredProperty() throws Exception {
        var invalid=Pattern.compile("\\.Anchor\\.(?:Width|Height|Left|Right|Top|Bottom)\"");
        List<String> errors=new ArrayList<>();
        try(var files=Files.walk(Path.of("src/main/java"))) {
            for(var file:files.filter(p->p.toString().endsWith(".java")).toList())
                if(invalid.matcher(Files.readString(file)).find())errors.add(file.toString());
        }
        assertTrue(errors.isEmpty(),String.join("\n",errors));
    }
    /** UI markup supports escaped quotes and backslashes, not Java-style escaped newlines. */
    @Test void markupDoesNotUseJavaWhitespaceEscapes() throws Exception {
        var invalid = Pattern.compile("(?<!\\\\)\\\\[nrt]");
        List<String> errors = new ArrayList<>();
        for (String root : List.of("src/main/resources/Common/UI/Custom/Aetherhaven", "subplugin-assets")) {
            try (var files = Files.walk(Path.of(root))) {
                for (var file : files.filter(p -> p.toString().endsWith(".ui")).toList()) {
                    if (invalid.matcher(Files.readString(file)).find()) errors.add(file + ": unsupported whitespace escape");
                }
            }
        }
        assertTrue(errors.isEmpty(), String.join("\n", errors));
    }

    /** A bad label alignment prevents every custom document, including unrelated HUDs, from loading. */
    @Test void horizontalLabelAlignmentUsesNativeValues() throws Exception {
        var alignment = Pattern.compile("\\bHorizontalAlignment\\s*:\\s*(\\w+)");
        List<String> errors = new ArrayList<>();
        for (String root : List.of("src/main/resources/Common/UI/Custom/Aetherhaven", "subplugin-assets")) {
            try (var files = Files.walk(Path.of(root))) {
                for (var file : files.filter(p -> p.toString().endsWith(".ui")).toList()) {
                    var matches = alignment.matcher(Files.readString(file));
                    while (matches.find()) if (!List.of("Start", "Center", "End").contains(matches.group(1))) {
                        errors.add(file + ": unsupported horizontal label alignment " + matches.group(1));
                    }
                }
            }
        }
        assertTrue(errors.isEmpty(), String.join("\n", errors));
    }

    /**
     * A command the client cannot apply disconnects the player the moment the page opens: a selector that matches no
     * element, or a property the element's type does not have. The client takes the <b>first</b> element with an id
     * in tree order, appended rows included, so an id that a row document shares with the page sends the command to
     * the row ({@code #Seeds.Disabled} landed on a palette row's Label and not on the Seeds button).
     *
     * <p>{@link UiSelectorAudit} reads every selector the Java can send and follows it through the page's documents
     * the way the client does. The whole report is written to {@code build/reports/custom-ui-selector-audit.txt}.
     */
    @Test void everySelectorTheJavaSendsLandsOnAnElementThatTakesIt() throws Exception {
        var report = audit();
        Files.createDirectories(Path.of("build/reports"));
        Files.writeString(Path.of("build/reports/custom-ui-selector-audit.txt"), report.text(), StandardCharsets.UTF_8);
        assertTrue(report.pages.size() >= 50 && report.checked >= 2000, "The audit read too little to mean anything: " + report.summary());
        assertTrue(report.errors.isEmpty(), report.errors.size() + " Custom UI commands would disconnect the player or land on the wrong element:\n"
            + String.join("\n", report.errors) + "\nWhole report: build/reports/custom-ui-selector-audit.txt");
        assertTrue(report.markup.isEmpty(), "Custom UI documents use a template that cannot be resolved:\n" + String.join("\n", report.markup));
        var accepted = Files.readAllLines(Path.of("src/test/resources/customui/audit-accepted.txt"), StandardCharsets.UTF_8);
        var undecided = new ArrayList<String>();
        report.undecided.forEach((name, line) -> { if (!accepted.contains(name)) undecided.add(name + "\n    " + line); });
        assertTrue(undecided.isEmpty(), undecided.size() + " Custom UI commands could not be checked, so nothing says they are safe:\n" + String.join("\n", undecided)
            + "\nMake the selector readable (a literal, a constant, a loop over a literal list), or, once a person has checked it in the game,"
            + " add its name (the first line of each entry) to src/test/resources/customui/audit-accepted.txt.");
    }

    private static UiSelectorAudit.Report audited;

    /** The audit of the whole mod, run once for the tests that read it. */
    private static synchronized UiSelectorAudit.Report audit() throws Exception {
        var project = Path.of("");
        if (audited == null) audited = UiSelectorAudit.run(project.resolve("src/main/java"), UiSelectorAudit.library(project), UiTypeTable.load(project));
        return audited;
    }

    /**
     * The pictures under {@code tests/hytale} are drawn from lists of commands. A list written by hand can hold
     * commands the game never sends, and then the picture shows a page no player gets. Every command of every
     * {@code ui_render} step must be one some class sends to that document. What the Java sets and no case draws is
     * written to {@code build/reports/custom-ui-render-cases.txt}: those commands have never been in a picture.
     */
    @Test void theRenderCasesSendOnlyWhatTheJavaSends() throws Exception {
        var cases = UiSelectorAudit.renderCases(audit(), Path.of("tests/hytale"));
        var text = new StringBuilder(cases.steps + " ui_render steps, " + cases.commands + " commands.\n\nSET BY THE JAVA AND DRAWN BY NO CASE\n");
        cases.neverDrawn.forEach((document, lines) -> {
            text.append(document).append(": ").append(lines.size()).append("\n");
            for (String line : lines) text.append("  ").append(line).append("\n");
        });
        Files.createDirectories(Path.of("build/reports"));
        Files.writeString(Path.of("build/reports/custom-ui-render-cases.txt"), text.toString(), StandardCharsets.UTF_8);
        assertTrue(cases.invented.isEmpty(), cases.invented.size() + " commands in the render cases are not what the Java sends, so their pictures show a page the game never builds:\n"
            + String.join("\n", cases.invented) + "\nTake the commands from the page itself (see FarmerFieldRenderCasesTest), or bring the case back in step with the Java.");
    }

    /** The audit must keep failing on the fault it was written for. If this passes with no error, the audit is blind. */
    @Test void theAuditCatchesAnIdSharedByAPageAndItsRows() throws Exception {
        var fault = CustomUiSelectorAuditTest.audit(CustomUiSelectorAuditTest.FIELD_PAGE, CustomUiSelectorAuditTest.CROP_ROW.replace("#SeedCount", "#Seeds"),
            CustomUiSelectorAuditTest.FIELD_JAVA.replace("#SeedCount", "#Seeds"));
        String errors = String.join("\n", fault.errors);
        assertTrue(errors.contains("AMBIGUOUS  #Seeds.Disabled: #Seeds is defined 2 times"), errors);
        assertTrue(errors.contains("PROPERTY  #Seeds.Disabled lands on Label #Seeds (Fixture/Crop.ui:3)"), errors);
        assertTrue(errors.contains("a Label has no property Disabled"), errors);
        var mended = CustomUiSelectorAuditTest.audit(CustomUiSelectorAuditTest.FIELD_PAGE, CustomUiSelectorAuditTest.CROP_ROW, CustomUiSelectorAuditTest.FIELD_JAVA);
        assertTrue(mended.errors.isEmpty(), String.join("\n", mended.errors));
    }

    /**
     * The property table is read from one build of the game. After a game update the table is rebuilt, or it would
     * vouch for properties the new client may not have: run the tests once with AETHERHAVEN_WRITE_UI_TABLE set.
     */
    @Test void theElementTableIsForTheInstalledGame() throws Exception {
        var project = Path.of("");
        var game = UiTypeTable.installedGame(UiTypeTable.load(project).patchline());
        Assumptions.assumeTrue(game != null, "No installed game on this machine to compare the element table with.");
        if (System.getenv("AETHERHAVEN_WRITE_UI_TABLE") != null) System.out.println(UiTypeTable.regenerate(game, project));
        assertEquals(UiTypeTable.installedVersion(game), UiTypeTable.load(project).gameVersion(),
            "src/test/resources/customui/element-properties.json was built from another game version than the one installed at " + game
                + ". Run the tests once with the environment variable AETHERHAVEN_WRITE_UI_TABLE set, then run the build again and read what the audit says.");
    }

    /** Common.ui uses Text: @Text with no default. One unbound template can break the client's entire UI load. */
    @Test void sharedTextButtonInstancesSupplyTheirRequiredText() throws Exception {
        var button = Pattern.compile("\\$C\\.@(?:Small)?(?:Secondary|Tertiary|Danger)?TextButton\\s*(?:#\\w+)?\\s*\\{([^{}]*)}");
        var text = Pattern.compile("(?:@Text\\s*=|\\bText\\s*:)");
        List<String> errors = new ArrayList<>();
        for (String root : List.of("src/main/resources/Common/UI/Custom/Aetherhaven", "subplugin-assets")) {
            try (var files = Files.walk(Path.of(root))) {
                for (var file : files.filter(p -> p.toString().endsWith(".ui")).toList()) {
                    String source = Files.readString(file);
                    var matches = button.matcher(source);
                    while (matches.find()) if (!text.matcher(matches.group(1)).find()) {
                        long line = source.substring(0, matches.start()).chars().filter(c -> c == '\n').count() + 1;
                        errors.add(file + ":" + line + " must set Text or @Text");
                    }
                }
            }
        }
        assertTrue(errors.isEmpty(), String.join("\n", errors));
    }
}
