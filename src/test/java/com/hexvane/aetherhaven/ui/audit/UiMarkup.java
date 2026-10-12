package com.hexvane.aetherhaven.ui.audit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads Custom UI documents far enough to know which elements a page is made of: each element's type, its id, how
 * elements nest, and what a template turns into where it is used. Property values are skipped; only the names of the
 * properties written on an element are kept.
 *
 * <p>The grammar is the one every shipped document follows: imports ({@code $C = "../Common.ui";}), named values
 * ({@code @Row = Group { ... };}), properties ({@code Text: "x";}) and elements ({@code Label #Name { ... }},
 * {@code $C.@TextButton #Ok { ... }}, {@code #Content { ... }} inside a template instance).
 */
public final class UiMarkup {
    private UiMarkup() {}

    /** One element as written, before templates are resolved. */
    public static final class RawElement {
        /** The element type when the head names one, otherwise null. */
        public String type;
        /** The import alias of a template head such as {@code $C.@TextButton}, without the dollar sign. */
        public String alias;
        /** The template name of a template head, without the at sign. */
        public String template;
        public String id;
        /** True when the head is only {@code #Id}: the block reaches into a template's descendant. */
        public boolean override;
        public int line;
        /** Named values of this body that hold an element. */
        public final Map<String, RawElement> templates = new LinkedHashMap<>();
        /** Named values of this body that only point at another named value: name to {alias or null, name}. */
        public final Map<String, String[]> references = new LinkedHashMap<>();
        public final List<String> properties = new ArrayList<>();
        public final List<RawElement> children = new ArrayList<>();
    }

    /** One document as written. Its roots are the children of {@link #top}; its templates are top's templates. */
    public static final class RawDocument {
        /** Path below {@code Common/UI/Custom}, with forward slashes. */
        public String path;
        /** Where it was read from, for messages. */
        public String source;
        /** Import alias to the path it names, resolved against this document's folder. */
        public final Map<String, String> imports = new LinkedHashMap<>();
        public final RawElement top = new RawElement();
    }

    /** An element of a finished tree: templates resolved, overrides applied. */
    public static final class Node {
        public final String type;
        public String id;
        /** The document the element is written in (for a template, the document that defines the template). */
        public final String document;
        public final int line;
        /** The template the element came from, such as {@code $C.@TextButton}, or null. */
        public String via;
        public Node parent;
        public final List<Node> children = new ArrayList<>();
        /** Names of the properties some document writes on this element. */
        public final Set<String> written = new LinkedHashSet<>();
        /** Documents appended below this element while a page runs, in the order the page appends them. */
        public final List<Appended> appended = new ArrayList<>();

        Node(String type, String id, String document, int line) {
            this.type = type; this.id = id; this.document = document; this.line = line;
        }

        /** Static children first, then everything a page appends here. */
        public List<Node> kids() {
            if (appended.isEmpty()) return children;
            var all = new ArrayList<>(children);
            for (var block : appended) all.addAll(block.roots);
            return all;
        }

        public String describe() {
            return type + (id == null ? "" : " #" + id) + " (" + document + ":" + line + (via == null ? "" : ", from " + via) + ")";
        }
    }

    /** The roots of one document appended below an element. */
    public static final class Appended {
        public final String document;
        public final String where;
        public final List<Node> roots;
        Appended(String document, String where, List<Node> roots) { this.document = document; this.where = where; this.roots = roots; }
    }

    public static final class MarkupException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public MarkupException(String message) { super(message); }
    }

    // Reading ------------------------------------------------------------------------------------------------------

    private enum Kind { IDENT, NUMBER, STRING, HASH, PUNCT, END }

    private record Token(Kind kind, String text, int line) {}

    private static List<Token> tokens(String source, String where) {
        var out = new ArrayList<Token>();
        int i = 0, line = 1, n = source.length();
        if (n > 0 && source.charAt(0) == '﻿') i = 1;
        while (i < n) {
            char c = source.charAt(i);
            if (c == '\n') { line++; i++; continue; }
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                while (i < n && source.charAt(i) != '\n') i++;
                continue;
            }
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                int end = source.indexOf("*/", i + 2);
                if (end < 0) throw new MarkupException(where + ":" + line + " comment is not closed");
                for (int k = i; k < end; k++) if (source.charAt(k) == '\n') line++;
                i = end + 2;
                continue;
            }
            if (c == '"') {
                int start = line;
                var text = new StringBuilder();
                i++;
                while (true) {
                    if (i >= n) throw new MarkupException(where + ":" + start + " string is not closed");
                    char s = source.charAt(i);
                    if (s == '\\' && i + 1 < n) { text.append(source.charAt(i + 1)); i += 2; continue; }
                    if (s == '"') { i++; break; }
                    if (s == '\n') line++;
                    text.append(s);
                    i++;
                }
                out.add(new Token(Kind.STRING, text.toString(), start));
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < n && (Character.isLetterOrDigit(source.charAt(i)) || source.charAt(i) == '_')) i++;
                out.add(new Token(Kind.IDENT, source.substring(start, i), line));
                continue;
            }
            if (Character.isDigit(c)) {
                int start = i;
                while (i < n && (Character.isDigit(source.charAt(i)) || source.charAt(i) == '.' && i + 1 < n && Character.isDigit(source.charAt(i + 1)))) i++;
                out.add(new Token(Kind.NUMBER, source.substring(start, i), line));
                continue;
            }
            if (c == '#') {
                int start = ++i;
                while (i < n && (Character.isLetterOrDigit(source.charAt(i)) || source.charAt(i) == '_' || source.charAt(i) == '-')) i++;
                out.add(new Token(Kind.HASH, source.substring(start, i), line));
                continue;
            }
            out.add(new Token(Kind.PUNCT, String.valueOf(c), line));
            i++;
        }
        out.add(new Token(Kind.END, "", line));
        return out;
    }

    private static final class Parser {
        final List<Token> tokens;
        final String where;
        int at;

        Parser(String source, String where) { this.tokens = tokens(source, where); this.where = where; }

        Token peek(int ahead) { return tokens.get(Math.min(at + ahead, tokens.size() - 1)); }
        boolean punct(int ahead, String text) { var t = peek(ahead); return t.kind == Kind.PUNCT && t.text.equals(text); }
        boolean kind(int ahead, Kind kind) { return peek(ahead).kind == kind; }

        MarkupException fail(String message) {
            var t = peek(0);
            return new MarkupException(where + ":" + t.line + " " + message + ", found " + (t.kind == Kind.END ? "end of file" : "'" + t.text + "'"));
        }

        void expect(String text) {
            if (!punct(0, text)) throw fail("Expected " + text);
            at++;
        }

        /** Skips a value up to and including the semicolon that ends it. */
        void skipValue() {
            int depth = 0;
            while (true) {
                var t = peek(0);
                if (t.kind == Kind.END) throw fail("Expected ;");
                at++;
                if (t.kind != Kind.PUNCT) continue;
                switch (t.text) {
                    case "(", "[", "{" -> depth++;
                    case ")", "]", "}" -> depth--;
                    case ";" -> { if (depth <= 0) return; }
                    default -> {}
                }
            }
        }

        /** How many tokens an element head takes when one starts here, or 0. The token after it is the brace. */
        int headLength() {
            int n;
            if (kind(0, Kind.IDENT)) n = 1;
            else if (punct(0, "@") && kind(1, Kind.IDENT)) n = 2;
            else if (punct(0, "$") && kind(1, Kind.IDENT) && punct(2, ".") && punct(3, "@") && kind(4, Kind.IDENT)) n = 5;
            else if (kind(0, Kind.HASH)) return punct(1, "{") ? 1 : 0;
            else return 0;
            if (kind(n, Kind.HASH)) n++;
            return punct(n, "{") ? n : 0;
        }

        void statement(RawElement into, RawDocument document) {
            if (punct(0, "$") && kind(1, Kind.IDENT) && punct(2, "=")) {
                String alias = peek(1).text;
                at += 3;
                if (!kind(0, Kind.STRING)) throw fail("Expected a document path");
                if (document != null) document.imports.put(alias, peek(0).text);
                at++;
                expect(";");
                return;
            }
            if (punct(0, "@") && kind(1, Kind.IDENT) && punct(2, "=")) {
                String name = peek(1).text;
                at += 3;
                if (headLength() > 0 && !kind(0, Kind.HASH)) {
                    into.templates.put(name, element(document));
                    if (punct(0, ";")) at++;
                    return;
                }
                if (punct(0, "@") && kind(1, Kind.IDENT) && punct(2, ";")) into.references.put(name, new String[] {null, peek(1).text});
                else if (punct(0, "$") && kind(1, Kind.IDENT) && punct(2, ".") && punct(3, "@") && kind(4, Kind.IDENT) && punct(5, ";"))
                    into.references.put(name, new String[] {peek(1).text, peek(4).text});
                skipValue();
                return;
            }
            if (kind(0, Kind.IDENT) && punct(1, ":")) {
                into.properties.add(peek(0).text);
                at += 2;
                skipValue();
                return;
            }
            if (headLength() > 0) {
                into.children.add(element(document));
                return;
            }
            throw fail("Expected a property, a named value or an element");
        }

        RawElement element(RawDocument document) {
            var e = new RawElement();
            e.line = peek(0).line;
            if (kind(0, Kind.IDENT)) { e.type = peek(0).text; at++; }
            else if (punct(0, "@")) { e.template = peek(1).text; at += 2; }
            else if (punct(0, "$")) { e.alias = peek(1).text; e.template = peek(4).text; at += 5; }
            else { e.override = true; }
            if (kind(0, Kind.HASH)) { e.id = peek(0).text; at++; }
            expect("{");
            while (!punct(0, "}")) {
                if (kind(0, Kind.END)) throw fail("Expected }");
                statement(e, document);
            }
            at++;
            return e;
        }
    }

    /** Parses one document. {@code path} is its path below {@code Common/UI/Custom}. */
    public static RawDocument parse(String source, String path, String where) {
        var document = new RawDocument();
        document.path = path;
        document.source = where;
        var parser = new Parser(source, where);
        while (!parser.kind(0, Kind.END)) parser.statement(document.top, document);
        int slash = path.lastIndexOf('/');
        String folder = slash < 0 ? "" : path.substring(0, slash);
        for (var entry : document.imports.entrySet()) entry.setValue(normalize(folder, entry.getValue()));
        return document;
    }

    /** {@code Aetherhaven} + {@code ../Common.ui} is {@code Common.ui}. */
    static String normalize(String folder, String relative) {
        var parts = new ArrayList<String>();
        if (!folder.isEmpty()) parts.addAll(List.of(folder.split("/")));
        for (String part : relative.replace('\\', '/').split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) { if (!parts.isEmpty()) parts.removeLast(); continue; }
            parts.add(part);
        }
        return String.join("/", parts);
    }

    // Resolving templates ------------------------------------------------------------------------------------------

    /** Every document a pack and the game ship, by its path below {@code Common/UI/Custom}. */
    public static final class Library {
        private final Map<String, RawDocument> documents = new LinkedHashMap<>();
        /** Things the documents get wrong that do not stop a tree being built. */
        public final List<String> problems = new ArrayList<>();
        private int inline;

        public void add(RawDocument document) { documents.putIfAbsent(document.path, document); }
        public RawDocument get(String path) { return documents.get(path); }
        public boolean has(String path) { return documents.containsKey(path); }
        public Set<String> paths() { return documents.keySet(); }

        /** Reads every {@code .ui} file below a {@code Common/UI/Custom} folder. Earlier folders win. */
        public void addFolder(Path custom) throws IOException {
            if (!Files.isDirectory(custom)) return;
            try (var files = Files.walk(custom)) {
                for (var file : files.filter(p -> p.toString().endsWith(".ui")).sorted().toList()) {
                    String path = custom.relativize(file).toString().replace('\\', '/');
                    if (documents.containsKey(path)) continue;
                    add(parse(Files.readString(file, StandardCharsets.UTF_8), path, file.toString().replace('\\', '/')));
                }
            }
        }

        private record Scope(RawDocument document, Scope parent, Map<String, RawElement> templates, Map<String, String[]> references) {}

        private record Found(RawElement element, Scope scope) {}

        private Found find(String alias, String name, Scope scope, int depth) {
            if (depth > 20) return null;
            if (alias != null) {
                String target = scope.document.imports.get(alias);
                var other = target == null ? null : documents.get(target);
                if (other == null) return null;
                var top = new Scope(other, null, other.top.templates, other.top.references);
                var element = other.top.templates.get(name);
                if (element != null) return new Found(element, top);
                var reference = other.top.references.get(name);
                return reference == null ? null : find(reference[0], reference[1], top, depth + 1);
            }
            for (var s = scope; s != null; s = s.parent) {
                var element = s.templates.get(name);
                if (element != null) return new Found(element, s);
                var reference = s.references.get(name);
                if (reference != null) return find(reference[0], reference[1], s, depth + 1);
            }
            return null;
        }

        private static Node descendant(Node node, String id) {
            for (var child : node.children) {
                if (id.equals(child.id)) return child;
                var deeper = descendant(child, id);
                if (deeper != null) return deeper;
            }
            return null;
        }

        private Node build(RawElement raw, Scope scope, Map<String, RawElement> replaced, int depth) {
            if (depth > 60) throw new MarkupException(scope.document.source + ":" + raw.line + " templates nest without end");
            var templates = raw.templates;
            if (replaced != null && !replaced.isEmpty()) {
                templates = new LinkedHashMap<>(raw.templates);
                templates.putAll(replaced);
            }
            Node node;
            if (raw.type != null) {
                node = new Node(raw.type, raw.id, scope.document.path, raw.line);
            } else if (raw.template != null) {
                var found = find(raw.alias, raw.template, scope, 0);
                String name = (raw.alias == null ? "" : "$" + raw.alias + ".") + "@" + raw.template;
                if (found == null) {
                    problems.add(scope.document.source + ":" + raw.line + " Could not resolve template node " + name);
                    node = new Node("?", raw.id, scope.document.path, raw.line);
                } else {
                    node = build(found.element, found.scope, raw.templates, depth + 1);
                    if (raw.id != null) node.id = raw.id;
                }
                node.via = node.via == null ? name : name + " > " + node.via;
            } else {
                problems.add(scope.document.source + ":" + raw.line + " #" + raw.id + " { } is written outside a template instance");
                node = new Node("?", raw.id, scope.document.path, raw.line);
            }
            apply(node, raw, new Scope(scope.document, scope, templates, raw.references), depth);
            return node;
        }

        private void apply(Node node, RawElement body, Scope inner, int depth) {
            node.written.addAll(body.properties);
            for (var child : body.children) {
                if (child.override) {
                    var target = descendant(node, child.id);
                    if (target == null) {
                        problems.add(inner.document.source + ":" + child.line + " Could not find node named " + child.id + " in " + node.describe());
                        continue;
                    }
                    apply(target, child, new Scope(inner.document, inner, child.templates, child.references), depth);
                } else {
                    var made = build(child, inner, null, depth + 1);
                    made.parent = node;
                    node.children.add(made);
                }
            }
        }

        private static void link(Node node) {
            for (var child : node.children) { child.parent = node; link(child); }
        }

        /** The roots of a document as the client builds them, or null when the pack has no such document. */
        public List<Node> roots(String path) {
            var document = documents.get(path);
            if (document == null) return null;
            var scope = new Scope(document, null, document.top.templates, document.top.references);
            var out = new ArrayList<Node>();
            for (var child : document.top.children) {
                if (child.override) continue;
                var node = build(child, scope, null, 0);
                link(node);
                out.add(node);
            }
            return out;
        }

        /** Every document-level template of a document, built on its own. */
        public Map<String, Node> templates(String path) {
            var document = documents.get(path);
            var out = new LinkedHashMap<String, Node>();
            if (document == null) return out;
            var scope = new Scope(document, null, document.top.templates, document.top.references);
            for (var entry : document.top.templates.entrySet()) {
                var node = build(entry.getValue(), scope, null, 0);
                link(node);
                out.put(entry.getKey(), node);
            }
            return out;
        }

        /** Markup sent as text ({@code appendInline}). It has no file, so it can import nothing. */
        public List<Node> inline(String markup, String where) {
            var document = parse(markup, "(inline " + (++inline) + ")", where);
            var scope = new Scope(document, null, document.top.templates, document.top.references);
            var out = new ArrayList<Node>();
            for (var child : document.top.children) {
                var node = build(child, scope, null, 0);
                link(node);
                out.add(node);
            }
            return out;
        }
    }

    public static Appended appended(String document, String where, List<Node> roots) {
        return new Appended(document, where, roots);
    }
}
