package com.hexvane.aetherhaven.construction.prefabmaterials;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;

final class ConversionRule {
    final boolean skip;
    @Nonnull
    final List<OutputSpec> outputs;

    private ConversionRule(boolean skip, @Nonnull List<OutputSpec> outputs) {
        this.skip = skip;
        this.outputs = outputs;
    }

    @Nonnull
    static ConversionRule skip() {
        return new ConversionRule(true, List.of());
    }

    @Nonnull
    static ConversionRule parse(@Nonnull String raw) {
        String text = raw.strip();
        if (text.isEmpty() || "skip".equalsIgnoreCase(text)) {
            return skip();
        }
        List<OutputSpec> outputs = new ArrayList<>();
        for (String part : text.split(",")) {
            part = part.strip();
            if (part.isEmpty()) {
                continue;
            }
            String lower = part.toLowerCase(Locale.ROOT);
            if (lower.startsWith("resource:")) {
                String body = part.substring(9).strip();
                int sep = body.indexOf(':');
                if (sep < 0) {
                    throw new IllegalArgumentException("Invalid resource output: " + part);
                }
                outputs.add(
                    new OutputSpec(OutputKind.RESOURCE, body.substring(0, sep).strip(), Integer.parseInt(body.substring(sep + 1).strip()))
                );
            } else if (lower.startsWith("item:")) {
                String body = part.substring(5).strip();
                int sep = body.indexOf(':');
                if (sep < 0) {
                    throw new IllegalArgumentException("Invalid item output: " + part);
                }
                outputs.add(
                    new OutputSpec(OutputKind.ITEM, body.substring(0, sep).strip(), Integer.parseInt(body.substring(sep + 1).strip()))
                );
            } else {
                int sep = part.indexOf(':');
                if (sep < 0) {
                    outputs.add(new OutputSpec(OutputKind.ITEM, part, 1));
                } else {
                    outputs.add(new OutputSpec(OutputKind.ITEM, part.substring(0, sep).strip(), Integer.parseInt(part.substring(sep + 1).strip())));
                }
            }
        }
        if (outputs.isEmpty()) {
            return skip();
        }
        return new ConversionRule(false, List.copyOf(outputs));
    }
}

enum OutputKind {
    ITEM,
    RESOURCE
}

final class OutputSpec {
    final OutputKind kind;
    @Nonnull
    final String id;
    final int amount;

    OutputSpec(@Nonnull OutputKind kind, @Nonnull String id, int amount) {
        this.kind = kind;
        this.id = id;
        this.amount = amount;
    }
}
