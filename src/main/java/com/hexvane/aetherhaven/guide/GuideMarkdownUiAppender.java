package com.hexvane.aetherhaven.guide;

import com.hexvane.aetherhaven.ui.NpcPortraitProvider;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.vladsch.flexmark.ast.BulletList;
import com.vladsch.flexmark.ast.Emphasis;
import com.vladsch.flexmark.ast.HardLineBreak;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ast.Image;
import com.vladsch.flexmark.ast.ListItem;
import com.vladsch.flexmark.ast.OrderedList;
import com.vladsch.flexmark.ast.Paragraph;
import com.vladsch.flexmark.ast.SoftLineBreak;
import com.vladsch.flexmark.ast.StrongEmphasis;
import com.vladsch.flexmark.ast.Text;
import com.vladsch.flexmark.ast.ThematicBreak;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;

/** Renders a Markdown body into appended Custom UI row documents under {@code hostListSelector}. */
public final class GuideMarkdownUiAppender {
    private static final Parser PARSER = Parser.builder().build();

    /** Max display width for guide images (journal detail column). */
    private static final int MAX_GUIDE_IMAGE_WIDTH = 560;
    /** Max display height (tall images shrink before wide ones hit the width cap). */
    private static final int MAX_GUIDE_IMAGE_HEIGHT = 320;

    private static final Map<String, int[]> IMAGE_PIXEL_SIZE_CACHE = new ConcurrentHashMap<>();

    private GuideMarkdownUiAppender() {}

    /**
     * @param assetClassLoader class loader that can read mod resources (e.g. {@code plugin.getClass().getClassLoader()})
     * @param guideNpcRoleId when set, {@code wiki/villager_*.png} hero images resolve to {@link NpcPortraitProvider} paths
     * @return number of appended rows
     */
    public static int appendMarkdown(
        @Nonnull UICommandBuilder cmd,
        @Nonnull String hostListSelector,
        @Nonnull String markdownBody,
        @Nonnull ClassLoader assetClassLoader,
        @Nullable String guideNpcRoleId,
        int maxRows
    ) {
        cmd.clear(hostListSelector);
        if (maxRows <= 0) {
            return 0;
        }
        String body = markdownBody.trim();
        if (body.isEmpty()) {
            return 0;
        }
        Node doc = PARSER.parse(body);
        RenderState st = new RenderState(cmd, hostListSelector, maxRows, assetClassLoader, guideNpcRoleId);
        for (Node n = doc.getFirstChild(); n != null; n = n.getNext()) {
            if (!st.renderBlock(n)) {
                break;
            }
        }
        return st.rows;
    }

    private static final class RenderState {
        private final UICommandBuilder cmd;
        private final String host;
        private final int maxRows;
        private final ClassLoader assetClassLoader;
        @Nullable
        private final String guideNpcRoleId;
        private int rows;

        private RenderState(
            @Nonnull UICommandBuilder cmd,
            @Nonnull String host,
            int maxRows,
            @Nonnull ClassLoader assetClassLoader,
            @Nullable String guideNpcRoleId
        ) {
            this.cmd = cmd;
            this.host = host;
            this.maxRows = maxRows;
            this.assetClassLoader = assetClassLoader;
            this.guideNpcRoleId = guideNpcRoleId;
        }

        private boolean room() {
            return rows < maxRows;
        }

        private boolean renderBlock(@Nonnull Node n) {
            if (!room()) {
                return false;
            }
            if (n instanceof Heading h) {
                appendHeading(h);
                return true;
            }
            if (n instanceof Paragraph p) {
                return renderParagraph(p);
            }
            if (n instanceof BulletList bl) {
                return renderBulletList(bl);
            }
            if (n instanceof OrderedList ol) {
                return renderOrderedList(ol);
            }
            if (n instanceof ThematicBreak) {
                appendSpacer();
                return true;
            }
            return true;
        }

        private void appendHeading(@Nonnull Heading h) {
            if (!room()) {
                return;
            }
            int level = h.getLevel();
            float font = level <= 1 ? 20f : (level == 2 ? 17f : 15f);
            cmd.append(host, "Aetherhaven/GuideMdHeading.ui");
            String sel = host + "[" + rows + "]";
            cmd.set(sel + " #Line.TextSpans", Message.raw(collectInlineText(h)));
            cmd.set(sel + " #Line.Style.FontSize", font);
            rows++;
        }

        private boolean renderParagraph(@Nonnull Paragraph p) {
            StringBuilder text = new StringBuilder();
            boolean anyImage = false;
            for (Node c = p.getFirstChild(); c != null; c = c.getNext()) {
                if (c instanceof Image img) {
                    if (text.length() > 0) {
                        flushParagraph(text.toString());
                        text.setLength(0);
                    }
                    appendImage(img.getUrl().toString());
                    anyImage = true;
                } else {
                    appendInlineFragment(text, c);
                }
            }
            if (text.length() > 0 || !anyImage) {
                flushParagraph(text.toString().trim());
            }
            return true;
        }

        private void flushParagraph(@Nonnull String t) {
            if (!room() || t.isEmpty()) {
                return;
            }
            cmd.append(host, "Aetherhaven/GuideMdParagraph.ui");
            cmd.set(host + "[" + rows + "] #Body.TextSpans", Message.raw(t));
            rows++;
        }

        private void appendImage(@Nonnull String urlRaw) {
            if (!room()) {
                return;
            }
            String path = resolveImageAssetPath(urlRaw.trim());
            cmd.append(host, "Aetherhaven/GuideMdImageRow.ui");
            String sel = host + "[" + rows + "]";
            cmd.set(sel + " #MdImage.AssetPath", path);
            int[] display = displayPixelSize(assetClassLoader, path);
            cmd.setObject(sel + " #MdImage.Anchor", anchorImage(display[0], display[1]));
            rows++;
        }

        private void appendSpacer() {
            if (!room()) {
                return;
            }
            cmd.append(host, "Aetherhaven/GuideMdSpacer.ui");
            rows++;
        }

        private boolean renderBulletList(@Nonnull BulletList list) {
            for (Node item = list.getFirstChild(); item != null; item = item.getNext()) {
                if (!(item instanceof ListItem li)) {
                    continue;
                }
                Node inner = li.getFirstChild();
                if (inner instanceof Paragraph p) {
                    if (!room()) {
                        return false;
                    }
                    String bulletText = "• " + collectInlineText(p).trim();
                    cmd.append(host, "Aetherhaven/GuideMdBullet.ui");
                    cmd.set(host + "[" + rows + "] #Body.TextSpans", Message.raw(bulletText));
                    rows++;
                }
            }
            return true;
        }

        private boolean renderOrderedList(@Nonnull OrderedList list) {
            int idx = 1;
            for (Node item = list.getFirstChild(); item != null; item = item.getNext()) {
                if (!(item instanceof ListItem li)) {
                    continue;
                }
                Node inner = li.getFirstChild();
                if (inner instanceof Paragraph p) {
                    if (!room()) {
                        return false;
                    }
                    String line = idx + ". " + collectInlineText(p).trim();
                    cmd.append(host, "Aetherhaven/GuideMdBullet.ui");
                    cmd.set(host + "[" + rows + "] #Body.TextSpans", Message.raw(line));
                    rows++;
                    idx++;
                }
            }
            return true;
        }

        private void appendInlineFragment(@Nonnull StringBuilder out, @Nonnull Node c) {
            if (c instanceof Text t) {
                out.append(t.getChars());
            } else if (c instanceof StrongEmphasis || c instanceof Emphasis) {
                out.append(collectInlineText(c));
            } else if (c instanceof SoftLineBreak || c instanceof HardLineBreak) {
                out.append(' ');
            } else {
                out.append(collectInlineText(c));
            }
        }

        @Nonnull
        private String collectInlineText(@Nonnull Node node) {
            StringBuilder sb = new StringBuilder();
            for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
                appendInlineFragment(sb, c);
            }
            return sb.toString();
        }

        /**
         * Markdown uses short paths like {@code wiki/villager_farmer.png}; {@code AssetImage.AssetPath} expects pack ids
         * such as {@code UI/Custom/Aetherhaven/wiki/...} (same convention as dialogue portraits and {@code Feasts.ui}).
         * Villager topics with {@code npcRoleId} remap {@code wiki/villager_*.png} to {@link NpcPortraitProvider} assets.
         */
        @Nonnull
        private String resolveImageAssetPath(@Nonnull String url) {
            if (guideNpcRoleId != null
                && !guideNpcRoleId.isBlank()
                && isWikiVillagerHeroScreenshot(url)) {
                return NpcPortraitProvider.portraitPathForRoleId(guideNpcRoleId);
            }
            return toTexturePath(url);
        }

        private static boolean isWikiVillagerHeroScreenshot(@Nonnull String url) {
            String u = url.trim();
            if (u.startsWith("/")) {
                u = u.substring(1);
            }
            if (!u.regionMatches(true, 0, "wiki/villager_", 0, "wiki/villager_".length())) {
                return false;
            }
            return u.toLowerCase(Locale.ROOT).endsWith(".png");
        }

        @Nonnull
        private static String toTexturePath(@Nonnull String url) {
            String u = url.trim();
            if (u.isEmpty()) {
                return u;
            }
            if (u.startsWith("UI/") || u.startsWith("Icons/")) {
                return u;
            }
            if (u.startsWith("wiki/")) {
                return "UI/Custom/Aetherhaven/" + u;
            }
            if (u.startsWith("/wiki/")) {
                return "UI/Custom/Aetherhaven" + u;
            }
            return u;
        }
    }

    /**
     * Width and height for {@link com.hypixel.hytale.server.core.ui.Anchor} on {@code AssetImage}, scaled to fit the
     * journal while preserving aspect ratio (same approach as Voile's {@code TopicContentRenderer#visit(Image)}).
     */
    @Nonnull
    private static int[] displayPixelSize(@Nonnull ClassLoader cl, @Nonnull String assetPath) {
        int[] src = readImagePixelSize(cl, assetPath);
        if (src == null) {
            return fitWithin(560, 280, MAX_GUIDE_IMAGE_WIDTH, MAX_GUIDE_IMAGE_HEIGHT);
        }
        return fitWithin(src[0], src[1], MAX_GUIDE_IMAGE_WIDTH, MAX_GUIDE_IMAGE_HEIGHT);
    }

    @Nonnull
    private static int[] fitWithin(int w, int h, int maxW, int maxH) {
        if (w <= 0 || h <= 0) {
            return new int[] { 1, 1 };
        }
        double scale = Math.min(1.0, Math.min((double) maxW / w, (double) maxH / h));
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        return new int[] { nw, nh };
    }

    @Nonnull
    private static Anchor anchorImage(int w, int h) {
        Anchor a = new Anchor();
        a.setWidth(Value.of(w));
        a.setHeight(Value.of(h));
        return a;
    }

    @Nullable
    private static String assetPathToClasspathResource(@Nonnull String assetPath) {
        if (assetPath.startsWith("UI/") || assetPath.startsWith("Icons/")) {
            return "Common/" + assetPath;
        }
        return null;
    }

    @Nullable
    private static int[] readImagePixelSize(@Nonnull ClassLoader cl, @Nonnull String assetPath) {
        String cp = assetPathToClasspathResource(assetPath);
        if (cp == null) {
            return null;
        }
        int[] cached = IMAGE_PIXEL_SIZE_CACHE.get(cp);
        if (cached != null) {
            return new int[] { cached[0], cached[1] };
        }
        try (InputStream in = cl.getResourceAsStream(cp)) {
            if (in == null) {
                return null;
            }
            BufferedImage img = ImageIO.read(in);
            if (img == null) {
                return null;
            }
            int w = img.getWidth();
            int h = img.getHeight();
            if (w <= 0 || h <= 0) {
                return null;
            }
            int[] dims = new int[] { w, h };
            IMAGE_PIXEL_SIZE_CACHE.put(cp, dims);
            return new int[] { w, h };
        } catch (Exception ignored) {
            return null;
        }
    }
}
