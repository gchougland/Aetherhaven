package com.hexvane.aetherhaven.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
