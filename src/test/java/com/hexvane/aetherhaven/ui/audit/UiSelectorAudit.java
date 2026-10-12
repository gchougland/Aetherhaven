package com.hexvane.aetherhaven.ui.audit;

import com.hexvane.aetherhaven.ui.audit.JavaUiScanner.Kind;
import com.hexvane.aetherhaven.ui.audit.JavaUiScanner.Send;
import com.hexvane.aetherhaven.ui.audit.JavaUiScanner.Sender;
import com.hexvane.aetherhaven.ui.audit.UiMarkup.Library;
import com.hexvane.aetherhaven.ui.audit.UiMarkup.MarkupException;
import com.hexvane.aetherhaven.ui.audit.UiMarkup.Node;
import com.hexvane.aetherhaven.ui.audit.UiTypeTable.Answer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Checks every Custom UI command the mod's Java can send against the documents it is sent to, the way the client
 * applies it. A command the client cannot apply disconnects the player the moment the page opens, and nothing on the
 * server notices.
 *
 * <p>For each class that opens a page or HUD, the page is put together as the client has it: the page document,
 * and below each container every row document the class appends there. Then each selector is followed through that
 * tree as the client follows it: {@code #Id} is the <b>first</b> element with that id in tree order, {@code [n]} is a
 * child, a space starts a new search below the element found so far.
 *
 * <p>Errors (each one is a disconnect or a command that lands on the wrong element):
 * <ul>
 * <li><b>NOTHING</b>: a selector that matches no element;</li>
 * <li><b>AMBIGUOUS</b>: an id searched for where two elements carry it. The client takes the first in tree order,
 * which is how {@code #Seeds.Disabled} landed on a palette row's Label instead of the Seeds button;</li>
 * <li><b>PROPERTY</b>: a property the element's type does not have (table: {@link UiTypeTable});</li>
 * <li><b>SHAPE</b>: a property where an element is needed or the reverse, or rows appended to an element that takes
 * no children.</li>
 * </ul>
 * What could not be decided is listed, never passed over: selectors whose element is not known until the page runs,
 * classes whose page could not be found, element types the table does not know.
 */
public final class UiSelectorAudit {
    private static final Pattern STEP = Pattern.compile("#([A-Za-z0-9\u0001\u0002]+)((?:\\[[^\\]]*\\])*)");
    private static final Pattern WORD = Pattern.compile("[A-Za-z][A-Za-z0-9]*");
    private static final String ANY = String.valueOf(JavaUiScanner.ANY), INT = String.valueOf(JavaUiScanner.INT);

    /** What the audit found. */
    public static final class Report {
        public final List<String> errors = new ArrayList<>();
        /** A property that fits one kind of row below a container and not another. */
        public final List<String> rowDependent = new ArrayList<>();
        /** Commands whose element or document is only known when the page runs. */
        public final List<String> unresolved = new ArrayList<>();
        /** Properties on element types the table cannot answer for. */
        public final List<String> unknown = new ArrayList<>();
        /** Classes that send commands and belong to no page the audit could find. */
        public final List<String> unplaced = new ArrayList<>();
        /** An id that lives in an appended document and is addressed without its container's index. */
        public final List<String> withoutContainer = new ArrayList<>();
        /**
         * Everything the audit could not decide (not resolved, unknown property, depends on the row, no page found),
         * by a name without line numbers, with the full line. A build accepts only the names it was told to accept.
         */
        public final Map<String, String> undecided = new TreeMap<>();
        public final List<String> markup = new ArrayList<>();
        public final List<String> strays = new ArrayList<>();
        public final Map<String, Integer> pages = new TreeMap<>();
        /** Every class that sends commands, with what it sends: what a render case is compared with. */
        public final List<Sender> senders = new ArrayList<>();
        public int javaFiles, documents, sendingClasses, commandSites, commands, checked, properties;

        public String text() {
            var out = new StringBuilder();
            out.append("Custom UI selector audit\n");
            out.append(summary()).append("\n");
            section(out, "ERRORS (each disconnects the player or lands on the wrong element)", errors);
            section(out, "DEPENDS ON THE ROW (fits one kind of row below the container, not another)", rowDependent);
            section(out, "NOT RESOLVED (the element or document is only known when the page runs)", unresolved);
            section(out, "UNKNOWN PROPERTY (the table cannot answer for this element type)", unknown);
            section(out, "NO PAGE FOUND (classes that send commands to a page the audit could not connect them to)", unplaced);
            section(out, "ADDRESSED WITHOUT ITS CONTAINER (the id lives in a document the page appends: right while that document is appended once, the first copy only when it is appended more than once)", withoutContainer);
            section(out, "DOCUMENT PROBLEMS", markup);
            section(out, "SELECTOR TEXT THAT REACHED NO COMMAND", strays);
            out.append("\nPAGES AND HUDS (commands followed to an element)\n");
            pages.forEach((name, count) -> out.append("  ").append(name).append(": ").append(count).append("\n"));
            return out.toString();
        }

        public String summary() {
            return javaFiles + " Java files read, " + documents + " documents; " + sendingClasses + " classes send commands, " + pages.size()
                + " of them open a page or HUD; " + commandSites + " command calls, " + commands + " distinct commands, " + checked
                + " followed to an element, " + properties + " properties checked against the element's type.";
        }

        private static void section(StringBuilder out, String title, List<String> lines) {
            out.append("\n").append(title).append(": ").append(lines.size()).append("\n");
            for (String line : lines) out.append("  ").append(line).append("\n");
        }
    }

    private enum Sort { NOTHING, ERROR, ROW, UNRESOLVED, UNKNOWN, FIRST_ROW }

    /**
     * One thing found on one page. {@code key} is the same wherever the same command gives the same trouble;
     * {@code stable} names it without line numbers.
     */
    private record Finding(Sort sort, String key, String stable, String line) {}

    private record Step(String id, List<String> indexes) {}

    private record Parsed(boolean loose, List<Step> steps, List<String> property, String problem) {}

    private static Parsed parse(String pattern) {
        String s = pattern;
        boolean loose = false;
        if (s.startsWith(ANY)) {
            if (!s.startsWith(" #", 1)) return new Parsed(false, List.of(), List.of(), "the element is not known until the page runs");
            loose = true;
            s = s.substring(2);
        }
        var steps = new ArrayList<Step>();
        List<String> property = List.of();
        String[] parts = s.split(" ", -1);
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (i == parts.length - 1) {
                int dot = part.indexOf('.');
                if (dot >= 0) {
                    property = List.of(part.substring(dot + 1).split("\\.", -1));
                    part = part.substring(0, dot);
                    for (String name : property) if (!WORD.matcher(name).matches()) return new Parsed(loose, steps, property, "the property is not known until the page runs");
                }
            }
            var match = STEP.matcher(part);
            if (!match.matches()) return new Parsed(loose, steps, property, "the element is not known until the page runs");
            String id = match.group(1);
            if (id.replace(ANY, "").replace(INT, "").isEmpty()) return new Parsed(loose, steps, property, "the id is not known until the page runs");
            var indexes = new ArrayList<String>();
            var index = Pattern.compile("\\[([^\\]]*)\\]").matcher(match.group(2));
            while (index.find()) indexes.add(index.group(1));
            steps.add(new Step(id, indexes));
        }
        return new Parsed(loose, steps, property, null);
    }

    private static Pattern pattern(String text, String any) {
        var regex = new StringBuilder();
        for (char c : text.toCharArray()) regex.append(c == JavaUiScanner.ANY ? any : c == JavaUiScanner.INT ? "[0-9]+" : Pattern.quote(String.valueOf(c)));
        return Pattern.compile(regex.toString());
    }

    private static void all(Node node, List<Node> out) {
        for (var kid : node.kids()) { out.add(kid); all(kid, out); }
    }

    private static boolean isAppend(Kind kind) {
        return kind == Kind.APPEND || kind == Kind.APPEND_INLINE || kind == Kind.INSERT_BEFORE || kind == Kind.INSERT_BEFORE_INLINE;
    }

    private static boolean isBefore(Kind kind) { return kind == Kind.INSERT_BEFORE || kind == Kind.INSERT_BEFORE_INLINE; }

    private static String shorten(String text) {
        String flat = text.replaceAll("\\s+", " ");
        return flat.length() > 70 ? flat.substring(0, 67) + "..." : flat;
    }

    /** One page as the client has it once every row is appended. */
    private final class Page {
        final String name;
        final Node root = new Node("(page)", null, "(page)", 0);
        final Set<String> documentNames = new LinkedHashSet<>();
        final List<Finding> found = new ArrayList<>();
        final Set<String> said = new HashSet<>();
        int followed, properties;

        Page(String name) { this.name = name; }

        private void add(Sort sort, Send send, String detail, String line) {
            String key = sort + "|" + send.kind() + "|" + send.where() + "|" + send.selector() + "|" + detail;
            String stable = sort + " | " + name + " | " + send.kind() + " " + JavaUiScanner.show(send.selector());
            if (said.add(key)) found.add(new Finding(sort, key, stable, line));
        }

        private void error(Send send, String kind, String line) {
            add(Sort.ERROR, send, kind, name + " " + send.where() + "  " + kind + "  " + line);
        }

        private void unresolved(Send send, String why) {
            add(Sort.UNRESOLVED, send, why, name + " " + send.where() + "  " + send.kind() + " " + JavaUiScanner.show(send.selector())
                + (send.text() != null && isAppend(send.kind()) || send.kind() == Kind.ROOT ? " <- " + shorten(JavaUiScanner.show(send.text())) : "") + ": " + why);
        }

        /** Elements with this id below {@code scope}, in tree order. */
        private List<Node> find(Node scope, String id) {
            var below = new ArrayList<Node>();
            all(scope, below);
            var out = new ArrayList<Node>();
            for (var node : below) if (id.equals(node.id)) out.add(node);
            return out;
        }

        private String where(Node node) {
            for (var n = node; n != null && n != root; n = n.parent) {
                var parent = n.parent;
                if (parent == null) break;
                for (var block : parent.appended) if (block.roots.contains(n))
                    return node.describe() + (parent == root ? "" : ", appended to " + (parent.id == null ? "a " + parent.type + " of " + parent.document : "#" + parent.id) + " by " + block.where);
            }
            return node.describe();
        }

        private boolean insideAppended(Node node, Node scope) {
            for (var n = node; n != null && n != scope; n = n.parent) {
                var parent = n.parent;
                if (parent == null || parent == root) return false;
                for (var block : parent.appended) if (block.roots.contains(n)) return true;
            }
            return false;
        }

        private String documents() { return String.join(", ", documentNames); }

        private List<String> documentsFor(String text) {
            if (!text.contains(ANY) && !text.contains(INT)) return List.of(text);
            var regex = pattern(text, "[A-Za-z0-9_]*");
            var out = new ArrayList<String>();
            for (String path : library.paths()) if (regex.matcher(path).matches()) out.add(path);
            return out;
        }

        /**
         * Follows a selector's steps from the page. Returns the elements it can end on: one, or one per kind of child
         * where an index picks a row. Trouble on the way is recorded when {@code send} is given.
         */
        private List<Node> follow(Parsed parsed, Send send) {
            List<Node> scopes = List.of(root);
            String walked = "";
            for (var step : parsed.steps) {
                var next = new ArrayList<Node>();
                boolean literal = !step.id.contains(ANY) && !step.id.contains(INT);
                var idPattern = pattern(step.id, "[A-Za-z0-9]*");
                boolean idFound = false;
                for (var scope : scopes) {
                    var ids = new LinkedHashSet<String>();
                    if (literal) ids.add(step.id);
                    else {
                        var below = new ArrayList<Node>();
                        all(scope, below);
                        for (var node : below) if (node.id != null && idPattern.matcher(node.id).matches()) ids.add(node.id);
                    }
                    for (String id : ids) {
                        var matches = find(scope, id);
                        if (matches.isEmpty()) continue;
                        idFound = true;
                        var first = matches.getFirst();
                        if (send != null && matches.size() > 1) {
                            error(send, "AMBIGUOUS", JavaUiScanner.show(send.selector()) + ": #" + id + " is defined " + matches.size() + " times "
                                + (scope == root ? "in the documents this page uses" : "below " + scope.describe()) + ". The client takes the first in tree order: "
                                + where(first) + ". Also: " + where(matches.get(1)) + (matches.size() > 2 ? " and " + (matches.size() - 2) + " more" : "")
                                + ". Give one of them another id, or address it through its container.");
                        } else if (send != null && !isAppend(send.kind()) && insideAppended(first, scope)) {
                            String key = "F|" + id + "|" + first.document;
                            if (said.add(key)) found.add(new Finding(Sort.FIRST_ROW, key, key, name + "  #" + id + " in " + where(first) + ", for example " + send.where() + " " + JavaUiScanner.show(send.selector())));
                        }
                        List<Node> at = List.of(first);
                        for (String index : step.indexes) {
                            var kids = new ArrayList<Node>();
                            for (var node : at) {
                                if (index.matches("[0-9]+") && node.appended.isEmpty()) {
                                    int n = Integer.parseInt(index);
                                    if (n < node.children.size()) kids.add(node.children.get(n));
                                } else kids.addAll(node.kids());
                            }
                            at = kids;
                        }
                        next.addAll(at);
                    }
                }
                walked += (walked.isEmpty() ? "" : " ") + "#" + step.id;
                if (next.isEmpty()) {
                    if (send != null) {
                        String shown = JavaUiScanner.show(send.selector());
                        add(Sort.NOTHING, send, "", name + " " + send.where() + "  NOTHING  " + shown + ": "
                            + (idFound ? "#" + JavaUiScanner.show(step.id) + " has no such child in the documents this page uses, and nothing is appended to it"
                                : "no element " + JavaUiScanner.show(walked) + (scopes.size() == 1 && scopes.getFirst() == root ? "" : " below " + describe(scopes)))
                            + ". Documents: " + documents());
                    }
                    return List.of();
                }
                scopes = next;
            }
            return scopes;
        }

        private String describe(List<Node> scopes) {
            var names = new TreeSet<String>();
            for (var scope : scopes) names.add(scope.describe());
            return String.join(" / ", names);
        }

        /** Puts the page together: its documents, then everything appended below an element (rows may hold rows). */
        void build(List<String> pageDocuments, List<Send> sends) {
            for (String path : pageDocuments) {
                if (!documentNames.add(path)) continue;
                var roots = library.roots(path);
                if (roots == null) continue;
                root.appended.add(UiMarkup.appended(path, "the page", roots));
                for (var node : roots) node.parent = root;
            }
            var pending = new ArrayList<Send>();
            for (var send : sends) if (isAppend(send.kind())) pending.add(send);
            for (int round = 0; round < 12 && !pending.isEmpty(); round++) {
                boolean placed = false;
                for (var send : List.copyOf(pending)) {
                    var parsed = parse(send.selector());
                    if (parsed.problem != null || parsed.loose) { pending.remove(send); continue; }
                    var targets = follow(parsed, null);
                    if (targets.isEmpty()) continue;
                    pending.remove(send);
                    for (var target : targets) {
                        var host = isBefore(send.kind()) ? target.parent : target;
                        if (host == null) continue;
                        if (send.kind() == Kind.APPEND_INLINE || send.kind() == Kind.INSERT_BEFORE_INLINE) placed |= attachInline(host, send);
                        else for (String path : documentsFor(send.text())) placed |= attach(host, path, send);
                    }
                }
                if (!placed) break;
            }
        }

        private boolean attach(Node host, String path, Send send) {
            for (var block : host.appended) if (block.document.equals(path)) return false;
            var roots = library.roots(path);
            if (roots == null) return false;
            documentNames.add(path);
            host.appended.add(UiMarkup.appended(path, send.where(), roots));
            for (var node : roots) node.parent = host;
            return true;
        }

        private boolean attachInline(Node host, Send send) {
            String markup = send.text().replace(JavaUiScanner.ANY, '0').replace(JavaUiScanner.INT, '0');
            String key = "inline: " + markup;
            for (var block : host.appended) if (block.document.equals(key)) return false;
            List<Node> roots;
            try {
                roots = library.inline(markup, send.where());
            } catch (MarkupException e) {
                roots = List.of();
                if (!send.text().contains(ANY)) error(send, "MARKUP", "appendInline does not parse: " + e.getMessage());
                else unresolved(send, "inline markup built from values the page only has when it runs");
            }
            host.appended.add(UiMarkup.appended(key, send.where(), roots));
            for (var node : roots) node.parent = host;
            return true;
        }

        void check(List<Send> sends) {
            for (var send : sends) {
                if (send.kind() == Kind.ROOT) continue;
                var parsed = parse(send.selector());
                if (parsed.problem != null) { unresolved(send, parsed.problem); continue; }
                if (parsed.loose) { loose(send, parsed); continue; }
                String shown = JavaUiScanner.show(send.selector());
                boolean wantsProperty = send.kind() == Kind.SET || send.kind() == Kind.READ;
                if (!wantsProperty && !parsed.property.isEmpty()) {
                    error(send, "SHAPE", send.kind() + " " + shown + ": this command needs an element, and the selector names a property");
                    continue;
                }
                var targets = follow(parsed, send);
                if (targets.isEmpty()) continue;
                followed++;
                if (isAppend(send.kind())) {
                    for (var target : targets) {
                        var host = isBefore(send.kind()) ? target.parent : target;
                        if (host == null || host == root) continue;
                        if (table.children(host.type).equals("no")) error(send, "SHAPE", send.kind() + " " + shown + ": " + where(host) + " is a " + host.type + ", which takes no children");
                    }
                    if (send.kind() == Kind.APPEND || send.kind() == Kind.INSERT_BEFORE) {
                        var paths = documentsFor(send.text());
                        if (paths.isEmpty()) unresolved(send, "no document matches " + JavaUiScanner.show(send.text()));
                        for (String path : paths) if (!library.has(path)) error(send, "NOTHING", "append(" + shown + ", \"" + path + "\"): there is no such document");
                    }
                    continue;
                }
                if (!wantsProperty) continue;
                if (parsed.property.isEmpty()) {
                    error(send, "SHAPE", send.kind() + " " + shown + ": the selector names an element and no property to " + (send.kind() == Kind.SET ? "set" : "read"));
                    continue;
                }
                properties++;
                Map<String, Node> byType = new LinkedHashMap<>();
                for (var target : targets) byType.putIfAbsent(target.type, target);
                var bad = new ArrayList<String>();
                var unsure = new ArrayList<String>();
                int good = 0;
                for (var entry : byType.entrySet()) {
                    if (entry.getKey().equals("?")) { unsure.add(where(entry.getValue()) + ": its template could not be resolved"); continue; }
                    var verdict = table.property(entry.getKey(), parsed.property);
                    switch (verdict.answer()) {
                        case YES -> good++;
                        case NO -> bad.add(where(entry.getValue()) + ": " + verdict.why());
                        case UNKNOWN -> unsure.add(where(entry.getValue()) + ": " + verdict.why());
                    }
                }
                if (!bad.isEmpty() && good == 0 && unsure.isEmpty())
                    error(send, "PROPERTY", shown + " lands on " + bad.getFirst() + (bad.size() > 1 ? " (and " + (bad.size() - 1) + " more kinds of row)" : ""));
                else if (!bad.isEmpty())
                    add(Sort.ROW, send, "", name + " " + send.where() + "  " + shown + ": " + String.join(".", parsed.property) + " does not fit " + String.join("; ", bad));
                for (String line : unsure) add(Sort.UNKNOWN, send, line, name + " " + send.where() + "  " + shown + ": " + line);
            }
        }

        /** A command sent by a helper whose container the audit could not follow: check the id wherever it is. */
        private void loose(Send send, Parsed parsed) {
            var last = parsed.steps.getLast();
            var below = new ArrayList<Node>();
            all(root, below);
            var idPattern = pattern(last.id, "[A-Za-z0-9]*");
            var bad = new TreeSet<String>();
            int matches = 0, good = 0;
            for (var node : below) {
                if (node.id == null || !idPattern.matcher(node.id).matches()) continue;
                matches++;
                if (parsed.property.isEmpty() || !last.indexes.isEmpty()) continue;
                var verdict = table.property(node.type, parsed.property);
                if (verdict.answer() == Answer.NO) bad.add(node.describe() + ": " + verdict.why());
                else good++;
            }
            String id = "#" + JavaUiScanner.show(last.id);
            if (matches == 0) { add(Sort.NOTHING, send, "", name + " " + send.where() + "  NOTHING  " + JavaUiScanner.show(send.selector()) + ": no element " + id + ". Documents: " + documents()); return; }
            if (!bad.isEmpty() && good == 0) { error(send, "PROPERTY", JavaUiScanner.show(send.selector()) + ": no " + id + " in this page's documents takes the property. " + bad.first()); return; }
            unresolved(send, bad.isEmpty() ? "its container is not known until the page runs; every " + id + " in this page's documents takes the property"
                : "its container is not known until the page runs; some " + id + " do not take the property: " + bad.first());
        }
    }

    private final Library library;
    private final UiTypeTable table;
    private final Report report = new Report();
    private JavaUiScanner scanner;
    private final Map<String, List<Page>> pagesByFile = new HashMap<>();

    private UiSelectorAudit(Library library, UiTypeTable table) { this.library = library; this.table = table; }

    /** Reads the Java below {@code javaRoot} and checks every command against {@code library}. */
    public static Report run(Path javaRoot, Library library, UiTypeTable table) throws IOException {
        var audit = new UiSelectorAudit(library, table);
        var scanner = JavaUiScanner.read(javaRoot);
        audit.scanner = scanner;
        audit.report.javaFiles = scanner.filesRead();
        audit.report.documents = library.paths().size();
        audit.check(scanner.senders());
        for (var stray : scanner.strays()) {
            String where = stray.file() + ":" + stray.line() + "  \"" + shorten(stray.text()) + "\"";
            var pages = audit.pagesByFile.get(stray.file());
            var parsed = parse(stray.text());
            if (pages == null || parsed.problem != null || parsed.loose) { audit.report.strays.add(where + (pages == null ? "" : "  (not a whole selector)")); continue; }
            boolean exists = false;
            for (var page : pages) exists |= !page.follow(parsed, null).isEmpty();
            // Handed to the game's own code (a file browser's list, say) or compared with: fine when the element is there.
            if (!exists) audit.report.strays.add(where + "  (no such element in this page's documents)");
        }
        audit.report.markup.addAll(new LinkedHashSet<>(library.problems));
        return audit.report;
    }

    private static boolean opens(Sender sender) {
        for (var send : sender.sends) if (send.kind() == Kind.ROOT) return true;
        return false;
    }

    /** A call reached with its container known makes the same call with the container unknown redundant. */
    private static List<Send> settled(Sender sender) {
        var anchored = new HashSet<String>();
        for (var send : sender.sends) if (send.selector() != null && send.selector().startsWith("#")) anchored.add(send.kind() + "@" + send.where());
        var keep = new ArrayList<Send>();
        for (var send : sender.sends) {
            if (send.selector() != null && !send.selector().startsWith("#") && anchored.contains(send.kind() + "@" + send.where())) continue;
            keep.add(send);
        }
        return keep;
    }

    /** Every way the page can be put together: documents appended to the page by one call are alternatives. */
    private List<List<String>> variants(Sender sender, List<Finding> trouble) {
        Map<String, List<String>> bySite = new LinkedHashMap<>();
        var probe = new Page(sender.name);
        for (var send : sender.sends) {
            if (send.kind() != Kind.ROOT) continue;
            if (send.text().equals(ANY)) { probe.unresolved(send, "the page document is not known until the page runs"); continue; }
            var paths = probe.documentsFor(send.text());
            if (paths.isEmpty()) probe.unresolved(send, "no document matches " + JavaUiScanner.show(send.text()));
            for (String path : paths) {
                if (!library.has(path)) probe.error(send, "NOTHING", "append(\"" + path + "\"): there is no such document");
                else { var list = bySite.computeIfAbsent(send.where(), k -> new ArrayList<>()); if (!list.contains(path)) list.add(path); }
            }
        }
        trouble.addAll(probe.found);
        List<List<String>> out = new ArrayList<>();
        out.add(new ArrayList<>());
        for (var alternatives : bySite.values()) {
            List<List<String>> grown = new ArrayList<>();
            for (var chosen : out) for (String path : alternatives) {
                if (grown.size() >= 12) break;
                var with = new ArrayList<>(chosen);
                if (!with.contains(path)) with.add(path);
                grown.add(with);
            }
            out = grown;
        }
        return out;
    }

    /** Keeps what every way of building the page agrees on: a selector that matches in one of them is not missing. */
    private static List<Finding> agree(List<List<Finding>> perVariant) {
        var out = new ArrayList<Finding>();
        var seen = new HashSet<String>();
        for (var findings : perVariant) for (var finding : findings) {
            if (finding.sort == Sort.NOTHING) {
                boolean everywhere = true;
                for (var other : perVariant) {
                    boolean has = false;
                    for (var f : other) has |= f.sort == Sort.NOTHING && f.key.equals(finding.key);
                    everywhere &= has;
                }
                if (!everywhere) continue;
            }
            if (seen.add(finding.key)) out.add(finding);
        }
        return out;
    }

    private void record(List<Finding> findings) {
        for (var finding : findings) {
            switch (finding.sort) {
                case NOTHING, ERROR -> report.errors.add(finding.line);
                case ROW -> { report.rowDependent.add(finding.line); report.undecided.putIfAbsent(finding.stable, finding.line); }
                case UNRESOLVED -> { report.unresolved.add(finding.line); report.undecided.putIfAbsent(finding.stable, finding.line); }
                case UNKNOWN -> { report.unknown.add(finding.line); report.undecided.putIfAbsent(finding.stable, finding.line); }
                case FIRST_ROW -> report.withoutContainer.add(finding.line);
            }
        }
    }

    private void check(List<Sender> senders) {
        report.senders.addAll(senders);
        report.sendingClasses = senders.size();
        var sites = new HashSet<String>();
        var distinct = new HashSet<String>();
        for (var sender : senders) for (var send : sender.sends) {
            sites.add(send.where());
            distinct.add(send.kind() + "|" + send.selector() + "|" + send.text() + "|" + send.where());
        }
        report.commandSites = sites.size();
        report.commands = distinct.size();

        Map<String, Sender> byName = new HashMap<>();
        for (var sender : senders) byName.putIfAbsent(sender.name, sender);
        var entered = new HashSet<String>();
        for (var sender : senders) if (opens(sender)) entered.addAll(sender.entered);
        var reached = new HashSet<String>();
        Map<Sender, List<List<String>>> pageVariants = new LinkedHashMap<>();
        for (var sender : senders) {
            if (!opens(sender)) continue;
            var sends = settled(sender);
            var trouble = new ArrayList<Finding>();
            var variants = variants(sender, trouble);
            pageVariants.put(sender, variants);
            var perVariant = new ArrayList<List<Finding>>();
            int followed = 0, properties = 0;
            for (var documents : variants) {
                var page = new Page(sender.name);
                page.build(documents, sends);
                page.check(sends);
                perVariant.add(page.found);
                pagesByFile.computeIfAbsent(sender.file, k -> new ArrayList<>()).add(page);
                followed = Math.max(followed, page.followed);
                properties = Math.max(properties, page.properties);
            }
            record(trouble);
            boolean pageUnknown = false;
            for (var finding : trouble) pageUnknown |= finding.sort == Sort.UNRESOLVED;
            var agreed = agree(perVariant);
            // With the page document itself unknown, every selector would be missing; say it once instead.
            if (pageUnknown) agreed.removeIf(finding -> finding.sort == Sort.NOTHING);
            record(agreed);
            report.checked += followed;
            report.properties += properties;
            var names = new TreeSet<String>();
            for (var documents : variants) names.addAll(documents);
            report.pages.put(sender.name + " (" + String.join(" or ", names) + ")", followed);
            for (var send : sends) if (send.selector() == null || send.selector().startsWith("#")) reached.add(send.where());
        }

        // Classes that open nothing themselves. A page that passes them its builder has already checked their commands
        // with the container it gave. What is left is checked on every page that mentions the class.
        for (var sender : senders) {
            if (opens(sender)) continue;
            var left = new ArrayList<Send>();
            var dead = new TreeMap<String, Integer>();
            for (var send : settled(sender)) {
                if (reached.contains(send.where())) continue;
                // A page read this method with what it passes, and this call was not among what it sends: a branch no caller takes.
                if (entered.contains(send.within())) continue;
                String method = send.from().substring(send.from().indexOf('.') + 1);
                if (!method.startsWith("<") && !scanner.isCalled(method)) dead.merge(send.from(), 1, Integer::sum);
                else left.add(send);
            }
            dead.forEach((method, count) -> {
                String line = method + " (" + sender.file + "): " + count + " commands, and nothing in the sources calls this method";
                report.unplaced.add(line);
                report.undecided.put("NO PAGE | " + method, line);
            });
            if (left.isEmpty()) continue;
            var users = new ArrayList<Sender>();
            // The page names the helper, or the helper names the page it updates (a system that refreshes a HUD).
            for (var page : pageVariants.keySet()) if (mentions(page, sender.name, byName, new HashSet<>(), 0) || sender.mentions.contains(page.name)) users.add(page);
            if (users.isEmpty()) {
                var calls = new TreeMap<String, String>();
                for (var send : left) calls.putIfAbsent(send.where(), send.kind() + " " + JavaUiScanner.show(send.selector()));
                var line = new StringBuilder(sender.name + " (" + sender.file + "): " + calls.size() + " command calls, and no page or HUD class mentions this class");
                int shown = 0;
                for (var entry : calls.entrySet()) {
                    if (shown++ == 4) { line.append("; ..."); break; }
                    line.append(shown == 1 ? ": line " : "; ").append(entry.getKey().substring(entry.getKey().lastIndexOf(':') + 1)).append(' ').append(entry.getValue());
                }
                report.unplaced.add(line.toString());
                report.undecided.put("NO PAGE | " + sender.name, line.toString());
                continue;
            }
            var perPage = new ArrayList<List<Finding>>();
            int followed = 0, properties = 0;
            for (var user : users) for (var documents : pageVariants.get(user)) {
                var page = new Page(user.name + " via " + sender.name);
                var sends = new ArrayList<>(settled(user));
                sends.addAll(left);
                page.build(documents, sends);
                page.check(left);
                perPage.add(page.found);
                followed = Math.max(followed, page.followed);
                properties = Math.max(properties, page.properties);
            }
            // The page named in a message is one of several; name the helper instead where they agree.
            record(agree(perPage));
            report.checked += followed;
            report.properties += properties;
        }
    }

    private static boolean mentions(Sender from, String name, Map<String, Sender> byName, Set<String> seen, int depth) {
        if (from.mentions.contains(name)) return true;
        if (depth >= 3) return false;
        for (String other : from.mentions) {
            var next = byName.get(other);
            if (next == null || opens(next) || !seen.add(other)) continue;
            if (mentions(next, name, byName, seen, depth + 1)) return true;
        }
        return false;
    }

    // Render cases --------------------------------------------------------------------------------------------------

    /** What checking the render cases against the Java found. */
    public static final class CaseReport {
        /** Commands in a case that no class sends to that document: the picture shows a page the game never builds. */
        public final List<String> invented = new ArrayList<>();
        /** Per document with a case: properties the Java sets there that no case sets. Not an error; a gap to know. */
        public final Map<String, List<String>> neverDrawn = new TreeMap<>();
        public int steps, commands;
    }

    private static Kind kindOf(String type, boolean hasSelector) {
        return switch (type) {
            case "Append" -> hasSelector ? Kind.APPEND : Kind.ROOT;
            case "AppendInline" -> Kind.APPEND_INLINE;
            case "InsertBefore" -> Kind.INSERT_BEFORE;
            case "InsertBeforeInline" -> Kind.INSERT_BEFORE_INLINE;
            case "Remove" -> Kind.REMOVE;
            case "Clear" -> Kind.CLEAR;
            default -> Kind.SET;
        };
    }

    /**
     * Render cases ({@code tests/hytale/*.test.json}) draw a page from a list of commands. A list written by hand can
     * hold commands the game never sends and leave out ones it does: the Scarecrow Fields cases never held
     * {@code #Seeds.Disabled}, so every picture of that page was drawn without the command that broke it. Here every
     * command of every {@code ui_render} step must be one some class sends to that document.
     *
     * <p>One difference is allowed, because the renderer does not look translations up: a case may set {@code .Text}
     * on an element the Java sets {@code .TextSpans} on, and likewise {@code .TooltipText} for {@code .TooltipTextSpans}.
     */
    public static CaseReport renderCases(Report report, Path folder) throws IOException {
        var out = new CaseReport();
        if (!Files.isDirectory(folder)) return out;
        List<Path> files;
        try (var list = Files.list(folder)) { files = list.filter(f -> f.toString().endsWith(".test.json")).sorted().toList(); }
        Map<String, Set<String>> drawn = new HashMap<>();
        Map<String, List<Sender>> users = new HashMap<>();
        Map<String, Pattern> compiled = new HashMap<>();
        for (var file : files) {
            var parsed = com.google.gson.JsonParser.parseString(Files.readString(file, java.nio.charset.StandardCharsets.UTF_8));
            var steps = new ArrayList<com.google.gson.JsonObject>();
            collectSteps(parsed, steps);
            for (var step : steps) {
                if (!step.has("tool") || !step.get("tool").getAsString().equals("ui_render") || !step.has("arguments")) continue;
                var arguments = step.getAsJsonObject("arguments");
                String path = arguments.has("path") ? arguments.get("path").getAsString() : "";
                int cut = path.indexOf("Common/UI/Custom/");
                if (cut < 0 || !arguments.has("commands")) continue;
                String document = path.substring(cut + "Common/UI/Custom/".length());
                String name = file.getFileName() + " \"" + (step.has("name") ? step.get("name").getAsString() : "") + "\"";
                var using = users.computeIfAbsent(document, d -> {
                    var found = new ArrayList<Sender>();
                    for (var sender : report.senders) for (var send : sender.sends)
                        if ((send.kind() == Kind.ROOT || send.kind() == Kind.APPEND || send.kind() == Kind.INSERT_BEFORE) && pattern(send.text(), "[A-Za-z0-9_]*").matcher(d).matches()) { found.add(sender); break; }
                    return found;
                });
                var opened = new HashSet<Sender>();
                for (var sender : using) for (var send : sender.sends)
                    if (send.kind() == Kind.ROOT && pattern(send.text(), "[A-Za-z0-9_]*").matcher(document).matches()) opened.add(sender);
                out.steps++;
                if (using.isEmpty()) { out.invented.add(name + ": no class in the sources appends " + document + ", so its commands cannot be compared with the Java"); continue; }
                var seen = drawn.computeIfAbsent(document, d -> new HashSet<>());
                for (var element : arguments.getAsJsonArray("commands")) {
                    var command = element.getAsJsonObject();
                    String selector = command.has("selector") && !command.get("selector").isJsonNull() ? command.get("selector").getAsString() : null;
                    var kind = kindOf(command.get("type").getAsString(), selector != null);
                    String text = command.has("text") && !command.get("text").isJsonNull() ? command.get("text").getAsString() : null;
                    out.commands++;
                    if (kind == Kind.ROOT) continue;
                    boolean sent = false;
                    String spans = selector.endsWith(".Text") ? selector.substring(0, selector.length() - 4) + "TextSpans"
                        : selector.endsWith(".TooltipText") ? selector + "Spans" : null;
                    for (var sender : using) for (var send : sender.sends) {
                        if (send.kind() != kind || send.selector() == null) continue;
                        boolean same = false;
                        // A row document drawn on its own is addressed from its root: the Java's selector without the container.
                        String[] parts = send.selector().split(" ");
                        for (int from = 0; from < parts.length && !same; from++) {
                            var regex = compiled.computeIfAbsent(String.join(" ", java.util.Arrays.copyOfRange(parts, from, parts.length)), tail -> pattern(tail, ".*"));
                            same = regex.matcher(selector).matches() || spans != null && regex.matcher(spans).matches();
                            if (opened.contains(sender)) break;
                        }
                        if (same && (kind == Kind.APPEND || kind == Kind.INSERT_BEFORE)) same = text != null && compiled.computeIfAbsent("document " + send.text(), ignored -> pattern(send.text(), "[A-Za-z0-9_]*")).matcher(text).matches();
                        if (!same) continue;
                        sent = true;
                        seen.add(send.where() + "|" + send.selector());
                    }
                    if (!sent) {
                        String line = name + ": " + command.get("type").getAsString() + " " + selector + (kind == Kind.APPEND ? " <- " + text : "") + " is sent by none of " + names(using);
                        if (!out.invented.contains(line)) out.invented.add(line);
                    }
                }
            }
        }
        for (var entry : users.entrySet()) {
            var seen = drawn.getOrDefault(entry.getKey(), Set.of());
            var missing = new ArrayList<String>();
            for (var sender : entry.getValue()) {
                boolean opens = false;
                for (var send : sender.sends) opens |= send.kind() == Kind.ROOT && pattern(send.text(), "[A-Za-z0-9_]*").matcher(entry.getKey()).matches();
                if (!opens) continue;
                var said = new HashSet<String>();
                for (var send : sender.sends) {
                    if (send.kind() != Kind.SET || send.selector() == null || !send.selector().startsWith("#") || seen.contains(send.where() + "|" + send.selector())) continue;
                    if (send.selector().endsWith("TooltipTextSpans") || send.selector().endsWith("TooltipText")) continue;
                    if (said.add(send.selector())) missing.add(sender.name + " " + send.where() + " " + JavaUiScanner.show(send.selector()));
                }
            }
            if (!missing.isEmpty()) out.neverDrawn.put(entry.getKey(), missing);
        }
        return out;
    }

    private static String names(List<Sender> senders) {
        var names = new TreeSet<String>();
        for (var sender : senders) names.add(sender.name);
        return String.join(", ", names);
    }

    private static void collectSteps(com.google.gson.JsonElement element, List<com.google.gson.JsonObject> out) {
        if (element.isJsonArray()) { for (var item : element.getAsJsonArray()) collectSteps(item, out); return; }
        if (!element.isJsonObject()) return;
        var object = element.getAsJsonObject();
        if (object.has("tool")) out.add(object);
        for (String key : List.of("cases", "steps")) if (object.has(key)) collectSteps(object.get(key), out);
    }

    /** Every document of the mod (its own and each bundled pack's) and the game documents they build on. */
    public static Library library(Path project) throws IOException {
        var library = new Library();
        library.addFolder(project.resolve("src/main/resources/Common/UI/Custom"));
        var packs = project.resolve("subplugin-assets");
        if (Files.isDirectory(packs)) {
            try (var list = Files.list(packs)) {
                for (var pack : list.sorted().toList()) library.addFolder(pack.resolve("Common/UI/Custom"));
            }
        }
        UiTypeTable.addVanilla(library, project);
        return library;
    }
}
