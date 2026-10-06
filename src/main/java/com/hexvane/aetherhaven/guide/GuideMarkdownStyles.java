package com.hexvane.aetherhaven.guide;

import com.hypixel.hytale.server.core.Message;
import com.vladsch.flexmark.ast.Code;
import com.vladsch.flexmark.ast.Emphasis;
import com.vladsch.flexmark.ast.HardLineBreak;
import com.vladsch.flexmark.ast.Image;
import com.vladsch.flexmark.ast.Paragraph;
import com.vladsch.flexmark.ast.SoftLineBreak;
import com.vladsch.flexmark.ast.StrongEmphasis;
import com.vladsch.flexmark.ast.Text;
import com.vladsch.flexmark.util.ast.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;

/** Colors and rich {@link Message} assembly for journal guide markdown. */
public final class GuideMarkdownStyles {
    static final String BODY = "#d8ccb8";
    static final String HEADING = "#f4e8c8";
    static final String COMMAND = "#8ecae6";
    static final String LABEL = "#d4a574";
    static final String PARAM = "#e8c9a0";
    static final String CODE = "#b8d4a8";
    static final String MUTED = "#9a9080";
    static final String ACCESS_ADVENTURE = "#8ecf9a";
    static final String ACCESS_CREATIVE = "#c9a8e8";
    static final String BULLET_L0 = "#e8c878";
    static final String BULLET_L1 = "#8a9aac";
    static final String BULLET_L2 = "#6a7a8a";

    private GuideMarkdownStyles() {}

    @Nonnull
    public static Message heading(@Nonnull Node headingNode) {
        return Message.raw(collectPlain(headingNode)).color(HEADING).bold(true);
    }

    @Nonnull
    public static Message paragraph(@Nonnull Paragraph p) {
        return buildInline(p, BODY, false);
    }

    @Nonnull
    public static Message bulletLine(@Nonnull Paragraph p, int depth, @Nonnull String prefix) {
        Message bullet = Message.raw(prefix).color(bulletColor(depth));
        Message body = buildInline(p, bodyColor(depth), true);
        return Message.join(bullet, body);
    }

    @Nonnull
    static String bulletUiTemplate(int depth) {
        return depth <= 0 ? "Aetherhaven/GuideMdBullet.ui" : "Aetherhaven/GuideMdBulletNested.ui";
    }

    @Nonnull
    private static String bulletColor(int depth) {
        return switch (Math.min(depth, 2)) {
            case 0 -> BULLET_L0;
            case 1 -> BULLET_L1;
            default -> BULLET_L2;
        };
    }

    @Nonnull
    private static String bulletPrefix(int depth, boolean ordered, int orderedIndex) {
        if (ordered) {
            return orderedIndex + ". ";
        }
        return switch (Math.min(depth, 2)) {
            case 0 -> "• ";
            case 1 -> "– ";
            default -> "· ";
        };
    }

    @Nonnull
    public static String bulletPrefixFor(int depth, boolean ordered, int orderedIndex) {
        return bulletPrefix(depth, ordered, orderedIndex);
    }

    @Nonnull
    private static String bodyColor(int depth) {
        return depth <= 0 ? BODY : MUTED;
    }

    @Nonnull
    private static Message buildInline(@Nonnull Node node, @Nonnull String defaultColor, boolean bulletContext) {
        List<Message> parts = new ArrayList<>();
        collectInlineParts(node, defaultColor, bulletContext, parts);
        if (parts.isEmpty()) {
            return Message.raw("");
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return Message.join(parts.toArray(Message[]::new));
    }

    private static void collectInlineParts(
        @Nonnull Node node,
        @Nonnull String defaultColor,
        boolean bulletContext,
        @Nonnull List<Message> out
    ) {
        for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
            if (c instanceof Image) {
                continue;
            } else if (c instanceof Text t) {
                appendTextSegments(t.getChars().toString(), defaultColor, bulletContext, out);
            } else if (c instanceof Code code) {
                String raw = code.getChars().toString();
                Message m = Message.raw(raw).color(CODE).monospace(true);
                out.add(m);
            } else if (c instanceof StrongEmphasis se) {
                String raw = collectPlain(se);
                Message m = Message.raw(raw).bold(true);
                if (raw.startsWith("/ah")) {
                    m = m.color(COMMAND);
                } else {
                    m = m.color(HEADING);
                }
                out.add(m);
            } else if (c instanceof Emphasis em) {
                out.add(Message.raw(collectPlain(em)).color(defaultColor).italic(true));
            } else if (c instanceof SoftLineBreak || c instanceof HardLineBreak) {
                out.add(Message.raw(" "));
            } else {
                collectInlineParts(c, defaultColor, bulletContext, out);
            }
        }
    }

    private static void appendTextSegments(
        @Nonnull String text,
        @Nonnull String defaultColor,
        boolean bulletContext,
        @Nonnull List<Message> out
    ) {
        if (text.isEmpty()) {
            return;
        }
        if (bulletContext) {
            String trimmed = text.trim();
            if (trimmed.startsWith("Permission:")) {
                out.add(Message.raw("Permission:").color(LABEL).bold(true));
                String rest = text.substring(text.indexOf("Permission:") + "Permission:".length());
                if (!rest.isEmpty()) {
                    appendTextSegments(rest, defaultColor, false, out);
                }
                return;
            }
            if (trimmed.startsWith("Access:")) {
                out.add(Message.raw("Access:").color(LABEL).bold(true));
                String rest = text.substring(text.indexOf("Access:") + "Access:".length()).trim();
                if (rest.equalsIgnoreCase("Adventure")) {
                    out.add(Message.raw(" Adventure").color(ACCESS_ADVENTURE).bold(true));
                } else if (rest.equalsIgnoreCase("Creative")) {
                    out.add(Message.raw(" Creative").color(ACCESS_CREATIVE).bold(true));
                } else if (!rest.isEmpty()) {
                    out.add(Message.raw(" " + rest).color(defaultColor));
                }
                return;
            }
            if (trimmed.startsWith("<") || trimmed.startsWith("[")) {
                int sep = text.indexOf(" — ");
                if (sep < 0) {
                    sep = text.indexOf(" - ");
                }
                if (sep > 0) {
                    out.add(Message.raw(text.substring(0, sep)).color(PARAM).monospace(true));
                    out.add(Message.raw(text.substring(sep)).color(defaultColor));
                    return;
                }
                out.add(Message.raw(text).color(PARAM).monospace(true));
                return;
            }
        }
        out.add(Message.raw(text).color(defaultColor));
    }

    @Nonnull
    private static String collectPlain(@Nonnull Node node) {
        StringBuilder sb = new StringBuilder();
        for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
            if (c instanceof Image) {
                continue;
            } else if (c instanceof Text t) {
                sb.append(t.getChars());
            } else if (c instanceof Code code) {
                sb.append(code.getChars());
            } else if (c instanceof SoftLineBreak || c instanceof HardLineBreak) {
                sb.append(' ');
            } else {
                sb.append(collectPlain(c));
            }
        }
        return sb.toString();
    }
}
