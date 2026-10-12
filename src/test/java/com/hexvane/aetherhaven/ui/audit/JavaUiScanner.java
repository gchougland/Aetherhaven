package com.hexvane.aetherhaven.ui.audit;

import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.YieldTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.tools.ToolProvider;

/**
 * Reads the mod's Java and works out every Custom UI command it can send: which documents a class appends and which
 * selector each {@code set}, {@code clear}, {@code append} and event binding names.
 *
 * <p>Selectors are rarely whole literals. A value is followed back to where it comes from: a local variable (the one
 * in scope where it is used), a loop over a literal list or an enum, a constant, a parameter (through the call that
 * supplies it, into helper classes too), a field set by a constructor (through the {@code new} that supplies it), a
 * function passed in as a lambda, a method that returns text. What cannot be known (a loop counter, a name read from
 * data) stays in the selector as a placeholder: {@link #INT} for a number, {@link #ANY} for anything else.
 *
 * <p>The sources are parsed, not compiled, so names are matched by how they are written. That is enough for how
 * this codebase builds its pages and is checked by what is left over: text that looks like a selector and reached no
 * command is listed ({@link #strays}).
 */
public final class JavaUiScanner {
    /** Stands for text the scanner could not work out. */
    public static final char ANY = '\u0001';
    /** Stands for a whole number the scanner could not work out, such as a row index. */
    public static final char INT = '\u0002';
    private static final String ANY_TEXT = String.valueOf(ANY), INT_TEXT = String.valueOf(INT);
    private static final int MOST = 400, DEEPEST = 9;

    public enum Kind { ROOT, APPEND, APPEND_INLINE, INSERT_BEFORE, INSERT_BEFORE_INLINE, SET, CLEAR, REMOVE, BIND, READ }

    /**
     * One command as far as it could be worked out.
     *
     * @param selector the element, with a property for {@code SET} and {@code READ}; null for {@code ROOT}
     * @param text the document path or the inline markup of an append; the event type of a binding
     * @param file the source file of the call that sends it, and its line
     * @param from the method of the page's class the command was reached from
     * @param within the method that holds the call, as {@link Sender#entered} names it
     */
    public record Send(Kind kind, String selector, String text, String file, int line, String from, String within) {
        public String where() { return file + ":" + line; }
    }

    /** What one source file's classes send. */
    public static final class Sender {
        public final String file;
        public final String name;
        public final List<Send> sends = new ArrayList<>();
        /** Simple names of the project classes this file mentions. */
        public final Set<String> mentions = new HashSet<>();
        /** Methods of other classes this one called with its builder, so their commands were read with what it passed. */
        public final Set<String> entered = new HashSet<>();
        Sender(String file, String name) { this.file = file; this.name = name; }
    }

    /** Text that looks like a selector and never reached a command. */
    public record Stray(String file, int line, String text) {}

    private static final Set<String> COMMANDS = Set.of("append", "appendInline", "insertBefore", "insertBeforeInline", "remove", "clear", "setNull", "set", "setObject");
    private static final Set<String> BUILDERS = Set.of("UICommandBuilder", "UIEventBuilder");
    private static final Set<String> PASS_THROUGH = Set.of("get", "getFirst", "getLast", "next", "iterator", "stream", "toList", "sorted", "distinct",
        "reversed", "intern", "strip", "trim", "toString", "keySet", "values", "orElse", "orElseThrow", "getKey", "getValue", "entrySet", "name");
    private static final Set<String> NUMERIC_CALLS = Set.of("size", "length", "indexOf", "ordinal", "intValue", "parseInt", "min", "max", "abs", "floorMod",
        "clamp", "floorDiv", "round", "floor", "ceil", "getAndIncrement", "incrementAndGet", "count", "hashCode");
    private static final Set<String> INT_TYPES = Set.of("int", "long", "short", "byte", "Integer", "Long", "Short", "Byte");

    private final class ClassInfo {
        final List<String> parents = new ArrayList<>();
        String name, pack, file, superName;
        ClassTree tree;
        CompilationUnitTree unit;
        ClassInfo outer;
        final Map<String, List<MethodInfo>> methods = new LinkedHashMap<>();
        final Map<String, VariableTree> fields = new LinkedHashMap<>();
        final List<ClassInfo> nested = new ArrayList<>();
        Map<String, List<FieldChange>> changes;
        ClassInfo top() { return outer == null ? this : outer.top(); }
        boolean isEnum() { return tree.getKind() == Tree.Kind.ENUM; }
        boolean isConstant(VariableTree field) { return isEnum() && field.getInitializer() instanceof NewClassTree && name.equals(typeName(field.getType())); }
    }

    private final class MethodInfo {
        ClassInfo owner;
        String name;
        Tree body, returns;
        boolean constructor;
        List<? extends VariableTree> parameters = List.of();
        Locals locals;
        boolean sends;
        boolean callsFunction() {
            var mine = new HashSet<String>();
            for (var parameter : parameters) {
                String type = typeName(parameter.getType());
                if (type == null) continue;
                boolean function = type.matches(".*(Consumer|Function|Operator|Runnable|Supplier|Predicate)");
                for (var cls : classes.getOrDefault(type, List.of())) function |= cls.tree.getKind() == Tree.Kind.INTERFACE;
                if (function) mine.add(parameter.getName().toString());
            }
            if (mine.isEmpty()) return false;
            boolean[] found = {false};
            new TreeScanner<Void, Void>() {
                @Override public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
                    if (receiver(node) instanceof IdentifierTree id && mine.contains(id.getName().toString())) found[0] = true;
                    return super.visitMethodInvocation(node, unused);
                }
            }.scan(body, null);
            return found[0];
        }
        boolean takesBuilder() {
            for (var parameter : parameters) { String type = typeName(parameter.getType()); if (type != null && BUILDERS.contains(type)) return true; }
            return false;
        }
    }

    private enum DeclKind { VARIABLE, EACH, LAMBDA }

    private record Decl(DeclKind kind, VariableTree variable, ExpressionTree value, MethodInvocationTree lambdaCall, long start, long end) {}

    private record Change(boolean append, ExpressionTree value, long at) {}

    private record FieldChange(MethodInfo method, ExpressionTree value) {}

    private record Created(NewClassTree tree, ClassInfo cls, MethodInfo method) {}

    private static final class Locals {
        final Map<String, List<Decl>> declared = new HashMap<>();
        final Map<String, List<Change>> changed = new HashMap<>();
        Decl at(String name, long position) {
            Decl best = null;
            for (var decl : declared.getOrDefault(name, List.of())) if (decl.start <= position && position <= decl.end && (best == null || decl.start > best.start)) best = decl;
            return best;
        }
    }

    private record Function(LambdaExpressionTree lambda, Frame frame) {}

    /** One method being read with what its caller passed. */
    private final class Frame {
        final ClassInfo cls;
        final MethodInfo method;
        final Map<String, Set<String>> bound;
        Map<String, Function> functions = Map.of();
        Map<String, Set<String>> overrides = Map.of();
        /** Fields of the object the method runs on, when the {@code new} that made it was found. */
        Map<String, Set<String>> fields;
        final Map<String, Set<String>> known = new HashMap<>();
        final Set<String> busy = new HashSet<>();
        final int depth;
        Frame(ClassInfo cls, MethodInfo method, Map<String, Set<String>> bound, int depth) { this.cls = cls; this.method = method; this.bound = bound; this.depth = depth; }
        long position(Tree tree) { return positions.getStartPosition(cls.unit, tree); }
    }

    private final Path sourceRoot;
    private final Map<String, List<ClassInfo>> classes = new HashMap<>();
    private final List<ClassInfo> topLevel = new ArrayList<>();
    private final Map<String, List<Created>> created = new HashMap<>();
    private final Map<String, List<ClassInfo>> children = new HashMap<>();
    private final Map<Tree, Integer> literalLines = new IdentityHashMap<>();
    private final Map<Tree, String> literalFiles = new IdentityHashMap<>();
    private final Set<Tree> used = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<String> sendingNames = new HashSet<>();
    private final Set<String> invoked = new HashSet<>();
    private final Set<String> documents = new HashSet<>();
    private SourcePositions positions;
    private int files;

    private JavaUiScanner(Path sourceRoot) { this.sourceRoot = sourceRoot; }

    public int filesRead() { return files; }

    /** Whether any call or method reference in the sources names a method like this. */
    public boolean isCalled(String methodName) { return invoked.contains(methodName); }

    /** Every piece of text in the sources that ends in {@code .ui}. */
    public Set<String> documentsNamed() { return documents; }

    // Reading the sources -------------------------------------------------------------------------------------------

    /** Parses every Java file below {@code sourceRoot}. Nothing is compiled. */
    public static JavaUiScanner read(Path sourceRoot) throws IOException {
        var scanner = new JavaUiScanner(sourceRoot);
        List<Path> sources;
        try (var walk = Files.walk(sourceRoot)) { sources = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList(); }
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IOException("The audit needs a JDK: no Java compiler is available to read the sources.");
        try (var manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(null, manager, diagnostic -> {}, List.of("-proc:none"), null, manager.getJavaFileObjectsFromPaths(sources));
            scanner.positions = Trees.instance(task).getSourcePositions();
            for (var unit : task.parse()) scanner.index(unit);
        }
        scanner.files = sources.size();
        scanner.findSendingNames();
        return scanner;
    }

    private String fileOf(CompilationUnitTree unit) {
        return sourceRoot.toAbsolutePath().relativize(Path.of(unit.getSourceFile().toUri()).toAbsolutePath()).toString().replace('\\', '/');
    }

    private void index(CompilationUnitTree unit) {
        String pack = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
        String file = fileOf(unit);
        for (var declared : unit.getTypeDecls()) if (declared instanceof ClassTree tree) topLevel.add(index(tree, unit, pack, file, null));
        new TreeScanner<Void, Void>() {
            @Override public Void visitLiteral(LiteralTree node, Void unused) {
                if (node.getValue() instanceof String text && text.endsWith(".ui")) documents.add(text);
                if (node.getValue() instanceof String text && looksLikeSelector(text)) {
                    literalLines.put(node, (int) unit.getLineMap().getLineNumber(positions.getStartPosition(unit, node)));
                    literalFiles.put(node, file);
                }
                return null;
            }
        }.scan(unit, null);
    }

    private static boolean looksLikeSelector(String text) {
        if (text.matches("#[0-9a-fA-F]{3,8}(\\(.*)?")) return false;
        return text.matches("(?s)(.* )?#[A-Za-z][A-Za-z0-9]*((\\[.*)|( .*)|(\\..*))?");
    }

    private ClassInfo index(ClassTree tree, CompilationUnitTree unit, String pack, String file, ClassInfo outer) {
        var info = new ClassInfo();
        info.name = tree.getSimpleName().toString(); info.pack = pack; info.file = file; info.tree = tree; info.unit = unit; info.outer = outer;
        info.superName = tree.getExtendsClause() == null ? null : typeName(tree.getExtendsClause());
        if (info.superName != null) info.parents.add(info.superName);
        for (var parent : tree.getImplementsClause()) { String type = typeName(parent); if (type != null) info.parents.add(type); }
        for (String parent : info.parents) children.computeIfAbsent(parent, k -> new ArrayList<>()).add(info);
        classes.computeIfAbsent(info.name, k -> new ArrayList<>()).add(info);
        int initializers = 0;
        for (var member : tree.getMembers()) {
            var m = new MethodInfo();
            m.owner = info;
            switch (member) {
                case MethodTree method -> {
                    if (method.getBody() == null) continue;
                    m.constructor = method.getName().contentEquals("<init>");
                    m.name = method.getName().toString(); m.body = method.getBody(); m.parameters = method.getParameters(); m.returns = method.getReturnType();
                }
                case VariableTree field -> {
                    info.fields.put(field.getName().toString(), field);
                    if (field.getInitializer() == null) continue;
                    m.name = "<field " + field.getName() + ">"; m.body = field.getInitializer();
                }
                case ClassTree inner -> { info.nested.add(index(inner, unit, pack, file, info)); continue; }
                default -> { m.name = "<initializer " + (initializers++) + ">"; m.body = member; }
            }
            info.methods.computeIfAbsent(m.name, k -> new ArrayList<>()).add(m);
        }
        return info;
    }

    private static String typeName(Tree type) {
        return switch (type) {
            case null -> null;
            case IdentifierTree id -> id.getName().toString();
            case MemberSelectTree select -> select.getIdentifier().toString();
            case ParameterizedTypeTree generic -> typeName(generic.getType());
            case PrimitiveTypeTree primitive -> primitive.getPrimitiveTypeKind().name().toLowerCase();
            case com.sun.source.tree.AnnotatedTypeTree annotated -> typeName(annotated.getUnderlyingType());
            case com.sun.source.tree.ArrayTypeTree array -> typeName(array.getType());
            default -> null;
        };
    }

    private static String declaredType(VariableTree variable) {
        String type = typeName(variable.getType());
        if (type != null && !type.equals("var")) return type;
        return variable.getInitializer() instanceof NewClassTree made ? typeName(made.getIdentifier()) : null;
    }

    private static String callName(MethodInvocationTree call) {
        return switch (call.getMethodSelect()) {
            case IdentifierTree id -> id.getName().toString();
            case MemberSelectTree select -> select.getIdentifier().toString();
            default -> "";
        };
    }

    private static ExpressionTree receiver(MethodInvocationTree call) {
        return call.getMethodSelect() instanceof MemberSelectTree select ? select.getExpression() : null;
    }

    /** Which methods send commands or call something that does, by name only; and every {@code new} in the sources. */
    private void findSendingNames() {
        Map<MethodInfo, Set<String>> calls = new IdentityHashMap<>();
        var all = new ArrayList<MethodInfo>();
        for (var list : classes.values()) for (var cls : list) for (var methods : cls.methods.values()) all.addAll(methods);
        for (var method : all) {
            var names = new HashSet<String>();
            new TreeScanner<Void, Void>() {
                @Override public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
                    names.add(callName(node));
                    return super.visitMethodInvocation(node, unused);
                }
                @Override public Void visitMemberReference(MemberReferenceTree node, Void unused) {
                    names.add(node.getName().toString());
                    return super.visitMemberReference(node, unused);
                }
                @Override public Void visitNewClass(NewClassTree node, Void unused) {
                    String type = typeName(node.getIdentifier());
                    if (type != null) created.computeIfAbsent(type, k -> new ArrayList<>()).add(new Created(node, method.owner, method));
                    return super.visitNewClass(node, unused);
                }
            }.scan(method.body, null);
            calls.put(method, names);
            invoked.addAll(names);
        }
        sendingNames.addAll(COMMANDS);
        sendingNames.add("addEventBinding");
        var handed = Collections.<MethodInfo>newSetFromMap(new IdentityHashMap<>());
        for (var method : all) if (method.callsFunction()) { handed.add(method); sendingNames.add(method.name); }
        boolean grew = true;
        while (grew) {
            grew = false;
            for (var method : all) {
                if (sendingNames.contains(method.name)) continue;
                for (String called : calls.get(method)) if (sendingNames.contains(called)) { sendingNames.add(method.name); grew = true; break; }
            }
        }
        for (var method : all) {
            method.sends = handed.contains(method);
            for (String called : calls.get(method)) if (sendingNames.contains(called)) { method.sends = true; break; }
        }
    }

    // Local names ---------------------------------------------------------------------------------------------------

    private Locals locals(MethodInfo method) {
        if (method.locals != null) return method.locals;
        var found = new Locals();
        var unit = method.owner.unit;
        new TreeScanner<Void, MethodInvocationTree>() {
            final Deque<Long> ends = new ArrayDeque<>();
            long end() { return ends.isEmpty() ? Long.MAX_VALUE : ends.peek(); }
            void declare(String name, Decl decl) { found.declared.computeIfAbsent(name, k -> new ArrayList<>()).add(decl); }
            Void inside(Tree node, Runnable scan) {
                long end = positions.getEndPosition(unit, node);
                ends.push(end < 0 ? Long.MAX_VALUE : end);
                try { scan.run(); } finally { ends.pop(); }
                return null;
            }
            @Override public Void visitBlock(BlockTree node, MethodInvocationTree call) { return inside(node, () -> super.visitBlock(node, null)); }
            @Override public Void visitForLoop(ForLoopTree node, MethodInvocationTree call) { return inside(node, () -> super.visitForLoop(node, null)); }
            @Override public Void visitCatch(CatchTree node, MethodInvocationTree call) { return inside(node, () -> super.visitCatch(node, null)); }
            @Override public Void visitTry(TryTree node, MethodInvocationTree call) { return inside(node, () -> super.visitTry(node, null)); }
            @Override public Void visitCase(CaseTree node, MethodInvocationTree call) { return inside(node, () -> super.visitCase(node, null)); }
            @Override public Void visitClass(ClassTree node, MethodInvocationTree call) { return inside(node, () -> super.visitClass(node, null)); }
            @Override public Void visitVariable(VariableTree node, MethodInvocationTree call) {
                declare(node.getName().toString(), new Decl(DeclKind.VARIABLE, node, node.getInitializer(), null, positions.getStartPosition(unit, node), end()));
                return super.visitVariable(node, null);
            }
            @Override public Void visitEnhancedForLoop(EnhancedForLoopTree node, MethodInvocationTree call) {
                scan(node.getExpression(), null);
                return inside(node, () -> {
                    declare(node.getVariable().getName().toString(), new Decl(DeclKind.EACH, node.getVariable(), node.getExpression(), null, positions.getStartPosition(unit, node), end()));
                    scan(node.getStatement(), null);
                });
            }
            @Override public Void visitAssignment(AssignmentTree node, MethodInvocationTree call) {
                if (node.getVariable() instanceof IdentifierTree id)
                    found.changed.computeIfAbsent(id.getName().toString(), k -> new ArrayList<>()).add(new Change(false, node.getExpression(), positions.getStartPosition(unit, node)));
                return super.visitAssignment(node, null);
            }
            @Override public Void visitCompoundAssignment(CompoundAssignmentTree node, MethodInvocationTree call) {
                if (node.getVariable() instanceof IdentifierTree id && node.getKind() == Tree.Kind.PLUS_ASSIGNMENT)
                    found.changed.computeIfAbsent(id.getName().toString(), k -> new ArrayList<>()).add(new Change(true, node.getExpression(), positions.getStartPosition(unit, node)));
                return super.visitCompoundAssignment(node, null);
            }
            @Override public Void visitMethodInvocation(MethodInvocationTree node, MethodInvocationTree call) {
                scan(node.getMethodSelect(), null);
                for (var argument : node.getArguments()) scan(argument, node);
                return null;
            }
            @Override public Void visitLambdaExpression(LambdaExpressionTree node, MethodInvocationTree call) {
                return inside(node, () -> {
                    for (var parameter : node.getParameters())
                        declare(parameter.getName().toString(), new Decl(DeclKind.LAMBDA, parameter, null, call, positions.getStartPosition(unit, node), end()));
                    scan(node.getBody(), null);
                });
            }
        }.scan(method.body, null);
        method.locals = found;
        return found;
    }

    private static VariableTree parameter(MethodInfo method, String name) {
        for (var parameter : method.parameters) if (parameter.getName().contentEquals(name)) return parameter;
        return null;
    }

    // Classes and calls ---------------------------------------------------------------------------------------------

    private List<ClassInfo> named(String simple, ClassInfo from) {
        var all = classes.get(simple);
        if (all == null) return List.of();
        if (all.size() == 1 || from == null) return all;
        for (var c = from; c != null; c = c.outer) for (var candidate : all) if (candidate.outer == c || candidate == c) return List.of(candidate);
        for (ImportTree imported : from.unit.getImports()) {
            String text = imported.getQualifiedIdentifier().toString();
            for (var candidate : all) if (text.equals(candidate.pack + "." + candidate.name) || text.endsWith("." + candidate.top().name + "." + candidate.name)) return List.of(candidate);
        }
        for (var candidate : all) if (candidate.pack.equals(from.pack) && candidate.outer == null) return List.of(candidate);
        for (var candidate : all) if (candidate.pack.equals(from.pack)) return List.of(candidate);
        return all;
    }

    /** The classes a type written as {@code Name} or {@code Outer.Name} can mean. */
    private List<ClassInfo> typed(Tree type, ClassInfo from) {
        if (type instanceof MemberSelectTree select) {
            String outer = typeName(select.getExpression());
            var out = new ArrayList<ClassInfo>();
            for (var candidate : classes.getOrDefault(select.getIdentifier().toString(), List.of())) if (candidate.outer != null && candidate.outer.name.equals(outer)) out.add(candidate);
            if (!out.isEmpty()) return out;
        }
        String name = typeName(type);
        return name == null ? List.of() : named(name, from);
    }

    private List<ClassInfo> withSupers(ClassInfo cls) {
        var out = new ArrayList<ClassInfo>();
        var seen = Collections.<ClassInfo>newSetFromMap(new IdentityHashMap<>());
        var queue = new ArrayDeque<ClassInfo>();
        queue.add(cls);
        while (!queue.isEmpty()) {
            var next = queue.poll();
            if (!seen.add(next)) continue;
            out.add(next);
            if (next.superName != null) queue.addAll(named(next.superName, next));
        }
        return out;
    }

    /** The class and the classes around it, each with what it extends. */
    private List<ClassInfo> scopeOf(ClassInfo cls) {
        var out = new ArrayList<ClassInfo>();
        for (var c = cls; c != null; c = c.outer) out.addAll(withSupers(c));
        return out;
    }

    private VariableTree variableOf(String name, long position, Frame frame) {
        if (frame.method != null) {
            var decl = locals(frame.method).at(name, position);
            if (decl != null) return decl.variable;
            var parameter = parameter(frame.method, name);
            if (parameter != null) return parameter;
        }
        for (var cls : scopeOf(frame.cls)) {
            var field = cls.fields.get(name);
            if (field != null) return field;
        }
        return null;
    }

    private final Map<Tree, Object> typeTrees = new IdentityHashMap<>();
    private final Map<Tree, List<MethodInfo>> resolved = new IdentityHashMap<>();
    private static final Object NONE = new Object();

    private Tree typeTreeOf(ExpressionTree e, Frame frame) {
        var known = typeTrees.get(e);
        if (known != null) return known == NONE ? null : (Tree) known;
        typeTrees.put(e, NONE);
        var type = typeTreeOfUncached(e, frame);
        typeTrees.put(e, type == null ? NONE : type);
        return type;
    }

    private Tree typeTreeOfUncached(ExpressionTree e, Frame frame) {
        return switch (e) {
            case IdentifierTree id -> {
                if (id.getName().contentEquals("this")) yield null;
                var variable = variableOf(id.getName().toString(), frame.position(id), frame);
                if (variable == null) yield null;
                String type = typeName(variable.getType());
                yield type != null && !type.equals("var") ? variable.getType() : variable.getInitializer() instanceof NewClassTree made ? made.getIdentifier() : null;
            }
            case MemberSelectTree select -> {
                for (var cls : classesOf(select.getExpression(), frame)) for (var c : withSupers(cls)) {
                    var field = c.fields.get(select.getIdentifier().toString());
                    if (field != null) yield field.getType();
                }
                yield null;
            }
            case NewClassTree made -> made.getIdentifier();
            case ParenthesizedTree inner -> typeTreeOf(inner.getExpression(), frame);
            case TypeCastTree cast -> cast.getType();
            case MethodInvocationTree call -> {
                for (var method : resolve(call, frame)) if (method.returns != null) yield method.returns;
                yield null;
            }
            default -> null;
        };
    }

    private String typeOf(ExpressionTree e, Frame frame) { return typeName(typeTreeOf(e, frame)); }

    /** The project classes an expression is an object of, or names. */
    private List<ClassInfo> classesOf(ExpressionTree e, Frame frame) {
        if (e instanceof IdentifierTree id) {
            String text = id.getName().toString();
            if (text.equals("this") || text.equals("super")) return List.of(frame.cls);
            if (variableOf(text, frame.position(id), frame) == null) return named(text, frame.cls);
        } else if (e instanceof MemberSelectTree select && typeTreeOf(e, frame) == null) {
            return typed(select, frame.cls);
        }
        var type = typeTreeOf(e, frame);
        return type == null ? List.of() : typed(type, frame.cls);
    }

    private List<MethodInfo> resolve(MethodInvocationTree call, Frame frame) {
        var known = resolved.get(call);
        if (known != null) return known;
        resolved.put(call, List.of());
        var out = resolveUncached(call, frame);
        resolved.put(call, out);
        return out;
    }

    private List<MethodInfo> resolveUncached(MethodInvocationTree call, Frame frame) {
        String name = callName(call);
        int count = call.getArguments().size();
        var on = receiver(call);
        var where = new ArrayList<ClassInfo>();
        if (on == null || on instanceof IdentifierTree id && (id.getName().contentEquals("this") || id.getName().contentEquals("super"))) where.addAll(scopeOf(frame.cls));
        else {
            for (var cls : classesOf(on, frame)) where.addAll(withSupers(cls));
            // An object held as a base type (the game's or the mod's) runs whichever class of the mod extends it.
            String type = typeOf(on, frame);
            var queue = new ArrayDeque<>(children.getOrDefault(type, List.of()));
            for (int guard = 0; !queue.isEmpty() && guard < 200; guard++) {
                var next = queue.poll();
                if (where.contains(next)) continue;
                where.add(next);
                queue.addAll(children.getOrDefault(next.name, List.of()));
            }
        }
        var out = new ArrayList<MethodInfo>();
        for (var cls : where) for (var method : cls.methods.getOrDefault(name, List.of())) {
            int declared = method.parameters.size();
            boolean variable = declared > 0 && method.parameters.get(declared - 1).toString().contains("...");
            if ((declared == count || variable && count >= declared - 1) && !out.contains(method)) out.add(method);
        }
        return out;
    }

    // Values --------------------------------------------------------------------------------------------------------

    private static Set<String> one(String text) { var set = new LinkedHashSet<String>(); set.add(text); return set; }

    private static Set<String> join(Set<String> left, Set<String> right) {
        if ((long) left.size() * right.size() > MOST) return one(ANY_TEXT);
        var out = new LinkedHashSet<String>();
        for (String a : left) for (String b : right) out.add(a + b);
        return out;
    }

    private static Set<String> capped(Set<String> values) { return values.size() > MOST ? one(ANY_TEXT) : values; }

    private static boolean unknown(Set<String> values) { return values.size() == 1 && values.contains(ANY_TEXT); }

    private boolean numeric(ExpressionTree e, Frame frame) {
        return switch (e) {
            case LiteralTree literal -> literal.getValue() instanceof Number;
            case ParenthesizedTree inner -> numeric(inner.getExpression(), frame);
            case UnaryTree unary -> unary.getKind() != Tree.Kind.LOGICAL_COMPLEMENT;
            case BinaryTree binary -> binary.getKind() != Tree.Kind.PLUS || numeric(binary.getLeftOperand(), frame) && numeric(binary.getRightOperand(), frame);
            case IdentifierTree id -> { String type = typeOf(id, frame); yield type != null && INT_TYPES.contains(type); }
            case MethodInvocationTree call -> NUMERIC_CALLS.contains(callName(call));
            case MemberSelectTree select -> select.getIdentifier().contentEquals("length");
            case TypeCastTree cast -> { String type = typeName(cast.getType()); yield type != null && INT_TYPES.contains(type); }
            case ConditionalExpressionTree choice -> numeric(choice.getTrueExpression(), frame) && numeric(choice.getFalseExpression(), frame);
            case CompoundAssignmentTree ignored -> true;
            default -> false;
        };
    }

    /**
     * Whether a condition is settled by what the caller passed: {@code list} when the call says {@code render(.., true)}.
     * Only a parameter that is never assigned counts; a local or a field can change while the page runs.
     */
    private Boolean decided(ExpressionTree e, Frame frame) {
        return switch (e) {
            case LiteralTree literal -> literal.getValue() instanceof Boolean flag ? flag : null;
            case ParenthesizedTree inner -> decided(inner.getExpression(), frame);
            case UnaryTree unary when unary.getKind() == Tree.Kind.LOGICAL_COMPLEMENT -> {
                var inner = decided(unary.getExpression(), frame);
                yield inner == null ? null : !inner;
            }
            case BinaryTree binary when binary.getKind() == Tree.Kind.CONDITIONAL_AND || binary.getKind() == Tree.Kind.CONDITIONAL_OR -> {
                var left = decided(binary.getLeftOperand(), frame);
                var right = decided(binary.getRightOperand(), frame);
                boolean and = binary.getKind() == Tree.Kind.CONDITIONAL_AND;
                if (left != null && left != and) yield left;
                if (right != null && right != and) yield right;
                yield left != null && right != null ? and : null;
            }
            case IdentifierTree id -> {
                String fixed = fixed(id, frame);
                yield fixed == null ? null : fixed.equals("true") ? Boolean.TRUE : fixed.equals("false") ? Boolean.FALSE : null;
            }
            case MethodInvocationTree call when callName(call).equals("equals") && call.getArguments().size() == 1 && receiver(call) != null -> {
                String left = fixed(receiver(call), frame), right = fixed(call.getArguments().getFirst(), frame);
                yield left == null || right == null ? null : left.equals(right);
            }
            default -> null;
        };
    }

    /** The one value an expression has for this call: a literal, or a parameter the caller gave a literal and nobody assigns. */
    private String fixed(ExpressionTree e, Frame frame) {
        if (e instanceof ParenthesizedTree inner) return fixed(inner.getExpression(), frame);
        if (e instanceof LiteralTree literal) return literal.getValue() instanceof String || literal.getValue() instanceof Boolean ? String.valueOf(literal.getValue()) : null;
        if (!(e instanceof IdentifierTree id)) return null;
        String text = id.getName().toString();
        if (frame.method == null || locals(frame.method).at(text, frame.position(id)) != null || parameter(frame.method, text) == null) return null;
        if (locals(frame.method).changed.containsKey(text)) return null;
        var bound = frame.bound.get(text);
        if (bound == null || bound.size() != 1) return null;
        String only = bound.iterator().next();
        return only.contains(ANY_TEXT) || only.contains(INT_TEXT) ? null : only;
    }

    private Set<String> value(ExpressionTree e, Frame frame) {
        if (e == null) return one(ANY_TEXT);
        if (e instanceof LiteralTree literal) {
            used.add(literal);
            Object v = literal.getValue();
            return v instanceof String || v instanceof Number || v instanceof Character || v instanceof Boolean ? one(String.valueOf(v)) : one(ANY_TEXT);
        }
        if (numeric(e, frame)) return one(INT_TEXT);
        return capped(switch (e) {
            case ParenthesizedTree inner -> value(inner.getExpression(), frame);
            case TypeCastTree cast -> value(cast.getExpression(), frame);
            case BinaryTree binary -> binary.getKind() == Tree.Kind.PLUS
                ? join(value(binary.getLeftOperand(), frame), value(binary.getRightOperand(), frame)) : one(ANY_TEXT);
            case ConditionalExpressionTree choice -> {
                var settled = decided(choice.getCondition(), frame);
                if (settled != null) yield value(settled ? choice.getTrueExpression() : choice.getFalseExpression(), frame);
                var out = new LinkedHashSet<>(value(choice.getTrueExpression(), frame));
                out.addAll(value(choice.getFalseExpression(), frame));
                yield out;
            }
            case SwitchExpressionTree chosen -> {
                var out = new LinkedHashSet<String>();
                for (var branch : chosen.getCases()) {
                    if (branch.getBody() instanceof ExpressionTree result) out.addAll(value(result, frame));
                    else if (branch.getBody() != null) new TreeScanner<Void, Void>() {
                        @Override public Void visitYield(YieldTree node, Void unused) { out.addAll(value(node.getValue(), frame)); return null; }
                    }.scan(branch.getBody(), null);
                }
                yield out.isEmpty() ? one(ANY_TEXT) : out;
            }
            case IdentifierTree id -> name(id.getName().toString(), frame.position(id), frame);
            case MemberSelectTree select -> member(select, frame);
            case ArrayAccessTree access -> value(access.getExpression(), frame);
            case AssignmentTree assignment -> value(assignment.getExpression(), frame);
            case NewArrayTree array -> {
                if (array.getInitializers() == null) yield one(ANY_TEXT);
                var out = new LinkedHashSet<String>();
                for (var item : array.getInitializers()) out.addAll(value(item, frame));
                yield out.isEmpty() ? one(ANY_TEXT) : out;
            }
            case MethodInvocationTree call -> result(call, frame);
            default -> one(ANY_TEXT);
        });
    }

    /** A whole number stays a placeholder whatever it starts at; anything else is {@code otherwise}. */
    private Set<String> orNumber(VariableTree variable, Set<String> otherwise, Frame frame) {
        String type = typeName(variable.getType());
        if (type != null && INT_TYPES.contains(type)) return one(INT_TEXT);
        if ((type == null || type.equals("var")) && variable.getInitializer() != null && numeric(variable.getInitializer(), frame)) return one(INT_TEXT);
        return otherwise;
    }

    private Set<String> name(String name, long position, Frame frame) {
        var replaced = frame.overrides.get(name);
        if (replaced != null) return replaced;
        Decl decl = frame.method == null ? null : locals(frame.method).at(name, position);
        VariableTree parameter = decl != null || frame.method == null ? null : parameter(frame.method, name);
        if (decl == null && parameter == null) {
            if (frame.fields != null && frame.fields.containsKey(name)) return frame.fields.get(name);
            var own = field(name, frame.cls);
            return own == null ? one(ANY_TEXT) : own;
        }
        String key = name + "@" + (decl == null ? -1 : decl.start);
        var known = frame.known.get(key);
        if (known != null) return known;
        if (!frame.busy.add(key)) return one(ANY_TEXT);
        var all = new LinkedHashSet<String>();
        try {
            if (decl == null) {
                var bound = frame.bound.get(name);
                all.addAll(bound != null ? bound : orNumber(parameter, one(ANY_TEXT), frame));
            } else switch (decl.kind) {
                case VARIABLE -> all.addAll(orNumber(decl.variable, decl.value == null ? Set.of() : value(decl.value, frame), frame));
                case EACH -> all.addAll(orNumber(decl.variable, value(decl.value, frame), frame));
                case LAMBDA -> {
                    var call = decl.lambdaCall;
                    all.addAll(call != null && callName(call).equals("forEach") && receiver(call) != null ? value(receiver(call), frame) : one(ANY_TEXT));
                }
            }
            var changes = new ArrayList<Change>();
            for (var change : locals(frame.method).changed.getOrDefault(name, List.of())) if (locals(frame.method).at(name, change.at) == decl) changes.add(change);
            for (var change : changes) if (!change.append) all.addAll(value(change.value, frame));
            for (var change : changes) if (change.append) all.addAll(join(new LinkedHashSet<>(all), value(change.value, frame)));
        } finally {
            frame.busy.remove(key);
        }
        var out = all.isEmpty() ? one(ANY_TEXT) : capped(all);
        frame.known.put(key, out);
        return out;
    }

    private final Map<String, Set<String>> fieldValues = new HashMap<>();
    private final Set<String> fieldsBusy = new HashSet<>();

    private Map<String, List<FieldChange>> changes(ClassInfo cls) {
        if (cls.changes != null) return cls.changes;
        var into = new HashMap<String, List<FieldChange>>();
        for (var methods : cls.methods.values()) for (var method : methods) {
            new TreeScanner<Void, Void>() {
                @Override public Void visitAssignment(AssignmentTree node, Void unused) {
                    String field = null;
                    if (node.getVariable() instanceof MemberSelectTree select && select.getExpression() instanceof IdentifierTree id && id.getName().contentEquals("this")) field = select.getIdentifier().toString();
                    else if (node.getVariable() instanceof IdentifierTree id) {
                        String text = id.getName().toString();
                        if (cls.fields.containsKey(text) && locals(method).at(text, positions.getStartPosition(cls.unit, id)) == null && parameter(method, text) == null) field = text;
                    }
                    if (field != null && cls.fields.containsKey(field)) into.computeIfAbsent(field, k -> new ArrayList<>()).add(new FieldChange(method, node.getExpression()));
                    return super.visitAssignment(node, unused);
                }
                @Override public Void visitClass(ClassTree node, Void unused) { return null; }
            }.scan(method.body, null);
        }
        cls.changes = into;
        return into;
    }

    /** What the fields of an object are once the given {@code new} has run its constructor. */
    private Map<String, Set<String>> made(NewClassTree tree, Frame frame) {
        var out = new HashMap<String, Set<String>>();
        if (frame.depth >= DEEPEST) return out;
        for (var cls : typed(tree.getIdentifier(), frame.cls)) {
            var constructors = cls.methods.getOrDefault("<init>", List.of());
            boolean any = false;
            for (var constructor : constructors) {
                if (constructor.parameters.size() != tree.getArguments().size()) continue;
                any = true;
                var inner = enter(constructor, tree.getArguments(), frame, null);
                for (var entry : changes(cls).entrySet()) for (var change : entry.getValue())
                    if (change.method == constructor) out.computeIfAbsent(entry.getKey(), k -> new LinkedHashSet<>()).addAll(value(change.value, inner));
            }
            if (!any && cls.tree.getKind() == Tree.Kind.RECORD) {
                int i = 0;
                for (var field : cls.fields.values()) {
                    if (field.getInitializer() != null || field.getModifiers().getFlags().contains(javax.lang.model.element.Modifier.STATIC)) continue;
                    if (i < tree.getArguments().size()) out.computeIfAbsent(field.getName().toString(), k -> new LinkedHashSet<>()).addAll(value(tree.getArguments().get(i), frame));
                    i++;
                }
            }
        }
        return out;
    }

    /** A field of the class, of a class around it or of one it extends; null when there is none of that name. */
    private Set<String> field(String name, ClassInfo from) {
        for (var cls : scopeOf(from)) {
            var declared = cls.fields.get(name);
            if (declared == null) continue;
            if (cls.isConstant(declared)) return one(name);
            String key = cls.file + "#" + cls.name + "#" + name;
            var known = fieldValues.get(key);
            if (known != null) return known;
            if (!fieldsBusy.add(key)) return one(ANY_TEXT);
            var all = new LinkedHashSet<String>();
            try {
                String type = typeName(declared.getType());
                if (type != null && INT_TYPES.contains(type)) all.add(INT_TEXT);
                else {
                    if (declared.getInitializer() != null) all.addAll(value(declared.getInitializer(), new Frame(cls, cls.methods.get("<field " + name + ">").getFirst(), Map.of(), 0)));
                    for (var change : changes(cls).getOrDefault(name, List.of())) {
                        if (!change.method.constructor) { all.addAll(value(change.value, new Frame(cls, change.method, Map.of(), 0))); continue; }
                        // Set by a constructor: every new of this class in the sources supplies a value.
                        boolean supplied = false;
                        for (var site : created.getOrDefault(cls.name, List.of())) {
                            if (site.tree.getArguments().size() != change.method.parameters.size() || !typed(site.tree.getIdentifier(), site.cls).contains(cls)) continue;
                            supplied = true;
                            all.addAll(value(change.value, enter(change.method, site.tree.getArguments(), new Frame(site.cls, site.method, Map.of(), 1), null)));
                        }
                        if (!supplied) all.add(ANY_TEXT);
                    }
                    if (cls.tree.getKind() == Tree.Kind.RECORD && declared.getInitializer() == null) {
                        int index = 0, mine = -1;
                        for (var field : cls.fields.values()) {
                            if (field.getInitializer() != null || field.getModifiers().getFlags().contains(javax.lang.model.element.Modifier.STATIC)) continue;
                            if (field == declared) mine = index;
                            index++;
                        }
                        for (var site : created.getOrDefault(cls.name, List.of())) {
                            if (mine < 0 || site.tree.getArguments().size() != index || !typed(site.tree.getIdentifier(), site.cls).contains(cls)) continue;
                            all.addAll(value(site.tree.getArguments().get(mine), new Frame(site.cls, site.method, Map.of(), 1)));
                        }
                    }
                }
            } finally {
                fieldsBusy.remove(key);
            }
            var out = all.isEmpty() ? one(ANY_TEXT) : capped(all);
            fieldValues.put(key, out);
            return out;
        }
        return null;
    }

    private Set<String> constants(ClassInfo cls) {
        var out = new LinkedHashSet<String>();
        for (var field : cls.fields.values()) if (cls.isConstant(field)) out.add(field.getName().toString());
        return out;
    }

    private Set<String> member(MemberSelectTree select, Frame frame) {
        String name = select.getIdentifier().toString();
        if (select.getExpression() instanceof IdentifierTree id && id.getName().contentEquals("this")) {
            if (frame.fields != null && frame.fields.containsKey(name)) return frame.fields.get(name);
            var own = field(name, frame.cls);
            return own == null ? one(ANY_TEXT) : own;
        }
        var object = creation(select.getExpression(), frame);
        if (object != null) {
            var fields = made(object.tree, object.frame);
            if (fields.containsKey(name)) return fields.get(name);
        }
        for (var cls : classesOf(select.getExpression(), frame)) { var found = field(name, cls); if (found != null) return found; }
        return one(ANY_TEXT);
    }

    private record Creation(NewClassTree tree, Frame frame) {}

    /** The {@code new} that made the object an expression names, when the sources show one. */
    private Creation creation(ExpressionTree e, Frame frame) {
        switch (e) {
            case NewClassTree made -> { return new Creation(made, frame); }
            case ParenthesizedTree inner -> { return creation(inner.getExpression(), frame); }
            case IdentifierTree id -> {
                String text = id.getName().toString();
                if (frame.method != null) {
                    var decl = locals(frame.method).at(text, frame.position(id));
                    if (decl != null) return decl.value instanceof NewClassTree made ? new Creation(made, frame) : null;
                    if (parameter(frame.method, text) != null) return null;
                }
                for (var cls : scopeOf(frame.cls)) {
                    var field = cls.fields.get(text);
                    if (field != null) return field.getInitializer() instanceof NewClassTree made ? new Creation(made, new Frame(cls, cls.methods.get("<field " + text + ">").getFirst(), Map.of(), frame.depth)) : null;
                }
                return null;
            }
            case MemberSelectTree select -> {
                String text = select.getIdentifier().toString();
                for (var cls : classesOf(select.getExpression(), frame)) for (var c : withSupers(cls)) {
                    var field = c.fields.get(text);
                    if (field != null) return field.getInitializer() instanceof NewClassTree made ? new Creation(made, new Frame(c, c.methods.get("<field " + text + ">").getFirst(), Map.of(), frame.depth)) : null;
                }
                return null;
            }
            default -> { return null; }
        }
    }

    private Set<String> returned(Tree body, Frame inner) {
        var out = new LinkedHashSet<String>();
        if (body instanceof ExpressionTree expression) return value(expression, inner);
        new TreeScanner<Void, Void>() {
            @Override public Void visitReturn(ReturnTree node, Void unused) { if (node.getExpression() != null) out.addAll(value(node.getExpression(), inner)); return null; }
            @Override public Void visitLambdaExpression(LambdaExpressionTree node, Void unused) { return null; }
            @Override public Void visitClass(ClassTree node, Void unused) { return null; }
        }.scan(body, null);
        return out;
    }

    private Set<String> result(MethodInvocationTree call, Frame frame) {
        String name = callName(call);
        var on = receiver(call);
        var arguments = call.getArguments();
        String onName = on instanceof IdentifierTree id ? id.getName().toString() : "";
        if (on instanceof IdentifierTree id && frame.functions.containsKey(onName) && variableOf(onName, frame.position(id), frame) == parameter(frame.method, onName)) {
            var function = frame.functions.get(onName);
            if (frame.depth >= DEEPEST) return one(ANY_TEXT);
            var inner = new Frame(function.frame.cls, function.frame.method, function.frame.bound, frame.depth + 1);
            inner.functions = function.frame.functions;
            inner.fields = function.frame.fields;
            var overrides = new HashMap<>(function.frame.overrides);
            var parameters = function.lambda.getParameters();
            for (int i = 0; i < parameters.size() && i < arguments.size(); i++) overrides.put(parameters.get(i).getName().toString(), value(arguments.get(i), frame));
            inner.overrides = overrides;
            var out = returned(function.lambda.getBody(), inner);
            return out.isEmpty() ? one(ANY_TEXT) : out;
        }
        if ((name.equals("valueOf") || name.equals("toString")) && arguments.size() == 1) return value(arguments.getFirst(), frame);
        if (name.equals("of") || name.equals("asList") || name.equals("listOf")) {
            if (onName.equals("Map")) return one(ANY_TEXT);
            var out = new LinkedHashSet<String>();
            for (var argument : arguments) out.addAll(value(argument, frame));
            return out.isEmpty() ? one(ANY_TEXT) : out;
        }
        if ((name.equals("format") && onName.equals("String") && !arguments.isEmpty()) || name.equals("formatted") && on != null) {
            var formats = name.equals("format") ? value(arguments.getFirst(), frame) : value(on, frame);
            if (name.equals("format") && unknown(formats) && arguments.size() > 1) formats = value(arguments.get(1), frame);
            var out = new LinkedHashSet<String>();
            for (String format : formats) out.add(format.replaceAll("%[-0-9.,]*d", INT_TEXT).replaceAll("%[-0-9.]*[a-zA-Z]", ANY_TEXT));
            return out;
        }
        if (on != null && arguments.isEmpty() && (name.equals("values") || name.equals("name") || name.equals("toString"))) {
            // An enum's constants, by its name or through a variable of its type.
            var classes = name.equals("values") ? classesOf(on, frame) : List.<ClassInfo>of();
            if (!name.equals("values")) {
                var known = value(on, frame);
                if (!unknown(known)) return known;
                classes = classesOf(on, frame);
            }
            for (var cls : classes) if (cls.isEnum() && !constants(cls).isEmpty()) return constants(cls);
        }
        if (frame.depth < DEEPEST) {
            var out = new LinkedHashSet<String>();
            var object = on == null ? null : creation(on, frame);
            for (var method : resolve(call, frame)) {
                var inner = enter(method, arguments, frame, object);
                out.addAll(returned(method.body, inner));
            }
            if (!out.isEmpty()) return out;
            // A record's own accessor: row.selector().
            if (on != null && arguments.isEmpty()) {
                if (object != null) { var fields = made(object.tree, object.frame); if (fields.containsKey(name)) return fields.get(name); }
                for (var cls : classesOf(on, frame)) if (cls.tree.getKind() == Tree.Kind.RECORD) { var found = field(name, cls); if (found != null) return found; }
            }
        }
        if (on != null && arguments.size() <= 1 && PASS_THROUGH.contains(name)) return value(on, frame);
        return one(ANY_TEXT);
    }

    private Frame enter(MethodInfo method, List<? extends ExpressionTree> arguments, Frame frame, Creation object) {
        var bound = new HashMap<String, Set<String>>();
        var functions = new HashMap<String, Function>();
        for (int i = 0; i < method.parameters.size() && i < arguments.size(); i++) {
            var parameter = method.parameters.get(i);
            String type = typeName(parameter.getType()), name = parameter.getName().toString();
            if (type != null && INT_TYPES.contains(type)) continue;
            var argument = arguments.get(i);
            if (argument instanceof LambdaExpressionTree lambda) { functions.put(name, new Function(lambda, frame)); continue; }
            if (argument instanceof IdentifierTree id && frame.functions.containsKey(id.getName().toString())) { functions.put(name, frame.functions.get(id.getName().toString())); continue; }
            if (i == method.parameters.size() - 1 && parameter.toString().contains("...")) {
                var rest = new LinkedHashSet<String>();
                for (int k = i; k < arguments.size(); k++) rest.addAll(value(arguments.get(k), frame));
                bound.put(name, capped(rest));
            } else bound.put(name, value(argument, frame));
        }
        var inner = new Frame(method.owner, method, bound, frame.depth + 1);
        inner.functions = functions;
        if (object != null) inner.fields = made(object.tree, object.frame);
        else if (scopeOf(frame.cls).contains(method.owner)) inner.fields = frame.fields;
        return inner;
    }

    // Commands ------------------------------------------------------------------------------------------------------

    private String methodKey(MethodInfo method) {
        return method == null ? "" : method.owner.file + "#" + method.owner.name + "." + method.name + "@" + positions.getStartPosition(method.owner.unit, method.body);
    }

    private enum Receiver { COMMANDS, EVENTS, OTHER, UNKNOWN }

    private Receiver receiverKind(ExpressionTree on, Frame frame) {
        if (on instanceof MethodInvocationTree inner && (COMMANDS.contains(callName(inner)) || callName(inner).equals("addEventBinding")) && receiver(inner) != null)
            return receiverKind(receiver(inner), frame);
        if (on instanceof ParenthesizedTree inner) return receiverKind(inner.getExpression(), frame);
        String type;
        if (on instanceof IdentifierTree id && variableOf(id.getName().toString(), frame.position(id), frame) == null) type = id.getName().toString();
        else type = typeOf(on, frame);
        if (type == null) return Receiver.UNKNOWN;
        return type.equals("UICommandBuilder") ? Receiver.COMMANDS : type.equals("UIEventBuilder") ? Receiver.EVENTS : Receiver.OTHER;
    }

    private static boolean selectorLike(Set<String> values) {
        if (values.isEmpty()) return false;
        for (String v : values) if (!v.matches("(?s)#[A-Za-z\u0001\u0002].*")) return false;
        return true;
    }

    private final class Run {
        final Sender sender;
        final ClassInfo top;
        final Set<String> done = new HashSet<>();
        final Deque<MethodInfo> stack = new ArrayDeque<>();
        final Set<String> seen = new HashSet<>();
        String from;
        Run(Sender sender, ClassInfo top) { this.sender = sender; this.top = top; }

        void send(Kind kind, Set<String> selectors, Set<String> texts, MethodInvocationTree call, Frame frame) {
            int line = (int) frame.cls.unit.getLineMap().getLineNumber(frame.position(call));
            for (String selector : selectors == null ? Collections.<String>singleton(null) : selectors)
                for (String text : texts == null ? Collections.<String>singleton(null) : texts) {
                    if (seen.add(kind + "|" + selector + "|" + text + "|" + frame.cls.file + ":" + line))
                        sender.sends.add(new Send(kind, selector, text, frame.cls.file, line, from, methodKey(frame.method)));
                }
        }

        void read(MethodInfo method, Frame frame) {
            if (!method.sends || stack.contains(method)) return;
            String key = System.identityHashCode(method) + "|" + new TreeMap<>(frame.bound) + "|" + (frame.fields == null ? "" : new TreeMap<>(frame.fields)) + "|" + functionKey(frame);
            if (!done.add(key)) return;
            stack.push(method);
            if (frame.depth > 0) sender.entered.add(methodKey(method));
            try {
                scan(method.body, frame);
            } finally {
                stack.pop();
            }
        }

        private String functionKey(Frame frame) {
            var out = new StringBuilder();
            new TreeMap<>(frame.functions).forEach((name, function) -> out.append(name).append('=').append(System.identityHashCode(function.lambda)).append(','));
            return out.toString();
        }

        private String positionKey(Tree tree, Frame frame) { return frame.cls.file + ":" + frame.position(tree); }

        private void scan(Tree body, Frame frame) {
            new TreeScanner<Void, Void>() {
                @Override public Void visitMethodInvocation(MethodInvocationTree call, Void unused) {
                    command(call, frame);
                    return super.visitMethodInvocation(call, unused);
                }
                @Override public Void visitIf(IfTree node, Void unused) {
                    var settled = decided(node.getCondition(), frame);
                    if (settled == null) return super.visitIf(node, unused);
                    scan(node.getCondition(), unused);
                    return scan(settled ? node.getThenStatement() : node.getElseStatement(), unused);
                }
            }.scan(body, null);
        }

        void command(MethodInvocationTree call, Frame frame) {
            String name = callName(call);
            var on = receiver(call);
            var arguments = call.getArguments();
            int count = arguments.size();
            if (on instanceof IdentifierTree id && frame.functions.containsKey(id.getName().toString()) && frame.depth < DEEPEST
                && frame.method != null && parameter(frame.method, id.getName().toString()) != null) {
                var function = frame.functions.get(id.getName().toString());
                String key = System.identityHashCode(function.lambda) + "|" + positionKey(call, frame);
                var inner = new Frame(function.frame.cls, function.frame.method, function.frame.bound, frame.depth + 1);
                inner.functions = function.frame.functions;
                inner.fields = function.frame.fields;
                var overrides = new HashMap<>(function.frame.overrides);
                var parameters = function.lambda.getParameters();
                for (int i = 0; i < parameters.size() && i < count; i++) {
                    overrides.put(parameters.get(i).getName().toString(), value(arguments.get(i), frame));
                }
                inner.overrides = overrides;
                if (done.add(key + "|" + new TreeMap<>(overrides))) scan(function.lambda.getBody(), inner);
                return;
            }
            if (on != null && (COMMANDS.contains(name) || name.equals("addEventBinding"))) {
                var kind = receiverKind(on, frame);
                if (kind != Receiver.OTHER) {
                    if (name.equals("addEventBinding")) {
                        if (count >= 2 && kind != Receiver.COMMANDS) {
                            var selectors = value(arguments.get(1), frame);
                            if (kind == Receiver.EVENTS || selectorLike(selectors)) {
                                String type = arguments.getFirst().toString();
                                send(Kind.BIND, selectors, one(type.substring(type.lastIndexOf('.') + 1)), call, frame);
                                return;
                            }
                        }
                    } else if (kind != Receiver.EVENTS) {
                        boolean sure = kind == Receiver.COMMANDS;
                        if (name.equals("append") && count == 1) {
                            var documents = value(arguments.getFirst(), frame);
                            boolean paths = !documents.isEmpty();
                            for (String d : documents) paths &= d.endsWith(".ui");
                            if (sure || paths) { send(Kind.ROOT, null, documents, call, frame); return; }
                        } else if (count >= 1) {
                            var selectors = value(arguments.getFirst(), frame);
                            if (sure || selectorLike(selectors)) {
                                Kind what = switch (name) {
                                    case "append" -> Kind.APPEND;
                                    case "appendInline" -> Kind.APPEND_INLINE;
                                    case "insertBefore" -> Kind.INSERT_BEFORE;
                                    case "insertBeforeInline" -> Kind.INSERT_BEFORE_INLINE;
                                    case "remove" -> Kind.REMOVE;
                                    case "clear" -> Kind.CLEAR;
                                    default -> Kind.SET;
                                };
                                boolean second = what == Kind.APPEND || what == Kind.APPEND_INLINE || what == Kind.INSERT_BEFORE || what == Kind.INSERT_BEFORE_INLINE;
                                if (!second || count == 2) {
                                    send(what, selectors, second ? value(arguments.get(1), frame) : null, call, frame);
                                    return;
                                }
                            }
                        }
                    }
                }
            }
            if ((name.equals("of") || name.equals("append") || name.equals("put")) && count == 2 && arguments.getFirst() instanceof LiteralTree key
                && key.getValue() instanceof String text && text.startsWith("@")) {
                var selectors = value(arguments.get(1), frame);
                if (selectorLike(selectors)) send(Kind.READ, selectors, one(text), call, frame);
                return;
            }
            if (frame.depth >= DEEPEST || !sendingNames.contains(name)) return;
            Creation object = null;
            boolean looked = false;
            for (var method : resolve(call, frame)) {
                if (!method.sends) continue;
                // Another class only adds to this page when it is handed the builder; otherwise it is building its own.
                boolean same = method.owner.top() == frame.cls.top() || scopeOf(frame.cls).contains(method.owner);
                if (!same && !method.takesBuilder()) continue;
                if (!looked && on != null) { object = creation(on, frame); looked = true; }
                read(method, enter(method, arguments, frame, object));
            }
        }
    }

    /** Every source file whose classes send commands, read method by method as the game may call them. */
    public List<Sender> senders() {
        var out = new ArrayList<Sender>();
        for (var top : topLevel) {
            var sender = new Sender(top.file, top.name);
            var run = new Run(sender, top);
            var all = new ArrayList<ClassInfo>();
            var queue = new ArrayDeque<ClassInfo>();
            queue.add(top);
            while (!queue.isEmpty()) { var next = queue.poll(); all.add(next); queue.addAll(next.nested); }
            for (var cls : all) for (var methods : cls.methods.values()) for (var method : methods) {
                run.from = cls.name + "." + method.name;
                run.read(method, new Frame(cls, method, Map.of(), 0));
            }
            if (sender.sends.isEmpty()) continue;
            new TreeScanner<Void, Void>() {
                @Override public Void visitIdentifier(IdentifierTree node, Void unused) {
                    if (classes.containsKey(node.getName().toString())) sender.mentions.add(node.getName().toString());
                    return null;
                }
            }.scan(top.tree, null);
            out.add(sender);
        }
        return out;
    }

    /** Text in the sources that looks like a selector and was never part of a command the scanner followed. */
    public List<Stray> strays() {
        var out = new ArrayList<Stray>();
        for (var entry : literalLines.entrySet()) {
            if (used.contains(entry.getKey())) continue;
            out.add(new Stray(literalFiles.get(entry.getKey()), entry.getValue(), String.valueOf(((LiteralTree) entry.getKey()).getValue())));
        }
        out.sort((a, b) -> a.file().equals(b.file()) ? Integer.compare(a.line(), b.line()) : a.file().compareTo(b.file()));
        return out;
    }

    /** A selector with its placeholders spelt out, for messages. */
    public static String show(String pattern) {
        return pattern == null ? "(page)" : pattern.replace(ANY_TEXT, "<?>").replace(INT_TEXT, "<n>");
    }
}
