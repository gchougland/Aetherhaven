package com.hexvane.aetherhaven.guide;

import com.hexvane.aetherhaven.ui.NpcPortraitProvider;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.assetstore.AssetPack;
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
import java.nio.file.Files;
import java.nio.file.Path;
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
    /** Fallback when pixel size is unknown for wide wiki hero screenshots. */
    private static final int FALLBACK_WIKI_HERO_WIDTH = 560;
    private static final int FALLBACK_WIKI_HERO_HEIGHT = 280;
    /** Fallback when pixel size is unknown for square NPC portrait icons. */
    private static final int FALLBACK_PORTRAIT_SIZE = 128;

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
            cmd.set(sel + " #Line.TextSpans", GuideMarkdownStyles.heading(h));
            cmd.set(sel + " #Line.Style.FontSize", font);
            rows++;
        }

        private boolean renderParagraph(@Nonnull Paragraph p) {
            boolean wroteText = false;
            for (Node c = p.getFirstChild(); c != null; c = c.getNext()) {
                if (c instanceof Image img) {
                    if (!wroteText) {
                        flushParagraphRich(p);
                        wroteText = true;
                    }
                    appendImage(img.getUrl().toString());
                }
            }
            if (!wroteText) {
                flushParagraphRich(p);
            }
            return true;
        }

        private void flushParagraphRich(@Nonnull Paragraph p) {
            String t = collectInlineText(p).trim();
            if (!room() || t.isEmpty()) {
                return;
            }
            cmd.append(host, "Aetherhaven/GuideMdParagraph.ui");
            cmd.set(host + "[" + rows + "] #Body.TextSpans", GuideMarkdownStyles.paragraph(p));
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
            return renderBulletList(list, 0);
        }

        private boolean renderBulletList(@Nonnull BulletList list, int depth) {
            for (Node item = list.getFirstChild(); item != null; item = item.getNext()) {
                if (!(item instanceof ListItem li)) {
                    continue;
                }
                if (!renderListItem(li, depth, false, 0)) {
                    return false;
                }
            }
            return true;
        }

        private boolean renderOrderedList(@Nonnull OrderedList list) {
            return renderOrderedList(list, 0);
        }

        private boolean renderOrderedList(@Nonnull OrderedList list, int depth) {
            int idx = 1;
            for (Node item = list.getFirstChild(); item != null; item = item.getNext()) {
                if (!(item instanceof ListItem li)) {
                    continue;
                }
                if (!renderListItem(li, depth, true, idx)) {
                    return false;
                }
                idx++;
            }
            return true;
        }

        /** Renders every paragraph and nested list inside a list item (not only the first child). */
        private boolean renderListItem(@Nonnull ListItem li, int depth, boolean ordered, int orderedIndex) {
            boolean wroteOrderedLine = false;
            for (Node child = li.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof Paragraph p) {
                    if (!room()) {
                        return false;
                    }
                    String text = collectInlineText(p).trim();
                    if (text.isEmpty()) {
                        continue;
                    }
                    int lineDepth = depth;
                    if (ordered && wroteOrderedLine) {
                        lineDepth = depth + 1;
                    }
                    String prefix;
                    if (ordered && !wroteOrderedLine) {
                        prefix = GuideMarkdownStyles.bulletPrefixFor(depth, true, orderedIndex);
                        wroteOrderedLine = true;
                    } else {
                        prefix = GuideMarkdownStyles.bulletPrefixFor(lineDepth, false, 0);
                    }
                    String sel = host + "[" + rows + "]";
                    cmd.append(host, GuideMarkdownStyles.bulletUiTemplate(lineDepth));
                    cmd.set(sel + " #Body.TextSpans", GuideMarkdownStyles.bulletLine(p, lineDepth, prefix));
                    rows++;
                } else if (child instanceof BulletList bl) {
                    if (!renderBulletList(bl, depth + 1)) {
                        return false;
                    }
                } else if (child instanceof OrderedList ol) {
                    if (!renderOrderedList(ol, depth + 1)) {
                        return false;
                    }
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
            src = defaultFallbackPixelSize(assetPath);
        }
        return fitWithin(src[0], src[1], MAX_GUIDE_IMAGE_WIDTH, MAX_GUIDE_IMAGE_HEIGHT);
    }

    /** Guess aspect ratio when the texture lives in another mod pack and cannot be probed on the classpath. */
    @Nonnull
    static int[] defaultFallbackPixelSize(@Nonnull String assetPath) {
        String p = assetPath.trim();
        if (p.startsWith("Icons/") || p.contains("/ModelsGenerated/")) {
            return new int[] { FALLBACK_PORTRAIT_SIZE, FALLBACK_PORTRAIT_SIZE };
        }
        return new int[] { FALLBACK_WIKI_HERO_WIDTH, FALLBACK_WIKI_HERO_HEIGHT };
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
        String key = assetPath.trim();
        int[] cached = IMAGE_PIXEL_SIZE_CACHE.get(key);
        if (cached != null) {
            return new int[] { cached[0], cached[1] };
        }
        String cp = assetPathToClasspathResource(assetPath);
        if (cp != null) {
            int[] fromClasspath = readImagePixelSizeFromStream(cl.getResourceAsStream(cp));
            if (fromClasspath != null) {
                IMAGE_PIXEL_SIZE_CACHE.put(key, fromClasspath);
                return fromClasspath;
            }
        }
        int[] fromPack = readImagePixelSizeFromAssetPacks(assetPath);
        if (fromPack != null) {
            IMAGE_PIXEL_SIZE_CACHE.put(key, fromPack);
            return fromPack;
        }
        return null;
    }

    @Nullable
    private static int[] readImagePixelSizeFromAssetPacks(@Nonnull String assetPath) {
        AssetModule module = AssetModule.get();
        if (module == null) {
            return null;
        }
        String relative = assetPathToPackRelativePath(assetPath);
        if (relative == null) {
            return null;
        }
        for (AssetPack pack : module.getAssetPacks()) {
            Path file = pack.getRoot().resolve(relative);
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try (InputStream in = Files.newInputStream(file)) {
                int[] dims = readImagePixelSizeFromStream(in);
                if (dims != null) {
                    return dims;
                }
            } catch (Exception ignored) {
                // try next pack
            }
        }
        return null;
    }

    @Nullable
    private static String assetPathToPackRelativePath(@Nonnull String assetPath) {
        if (assetPath.startsWith("UI/") || assetPath.startsWith("Icons/")) {
            return "Common/" + assetPath;
        }
        return null;
    }

    @Nullable
    private static int[] readImagePixelSizeFromStream(@Nullable InputStream in) {
        if (in == null) {
            return null;
        }
        try (InputStream stream = in) {
            BufferedImage img = ImageIO.read(stream);
            if (img == null) {
                return null;
            }
            int w = img.getWidth();
            int h = img.getHeight();
            if (w <= 0 || h <= 0) {
                return null;
            }
            return new int[] { w, h };
        } catch (Exception ignored) {
            return null;
        }
    }
}
