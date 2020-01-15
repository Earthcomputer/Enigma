package cuchaz.enigma.translation.mapping.serde;

import cuchaz.enigma.ProgressListener;
import cuchaz.enigma.throwables.MappingParseException;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.MappingSaveParameters;
import cuchaz.enigma.translation.mapping.tree.EntryTree;
import cuchaz.enigma.translation.mapping.tree.HashEntryTree;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public enum AlphaMcpReader implements MappingsReader {
    INSTANCE;

    @Override
    public EntryTree<EntryMapping> read(Path path, ProgressListener progress, MappingSaveParameters saveParameters) throws MappingParseException, IOException {
        List<String[]> classes = readCsv(path.resolve("conf/classes.csv"), 4);
        List<String[]> fields = readCsv(path.resolve("conf/fields.csv"), 3);
        List<String[]> methods = readCsv(path.resolve("conf/methods.csv"), 4);

        Map<String, String> classDocs = new HashMap<>();
        for (String[] line : classes) {
            String docs = getDocs(line, 5);
            if (!"*".equals(docs))
                classDocs.put(line[2], docs);
        }

        Map<String, String> fieldNames = new HashMap<>();
        Map<String, String> fieldDocs = new HashMap<>();
        for (String[] line : fields) {
            if (!"*".equals(line[2])) {
                if (!"*".equals(line[6]))
                    fieldNames.put(line[2], line[6]);
                String docs = getDocs(line, 7);
                if (!"*".equals(docs))
                    fieldDocs.put(line[2], docs);
            }
        }
        Map<String, String> fieldDescs = getAllFieldDescs(path.resolve("jars/Minecraft.jar"));
        Map<String, String> methodNames = new HashMap<>();
        Map<String, String> methodDocs = new HashMap<>();
        for (String[] line : methods) {
            if (!"*".equals(line[1])) {
                if (!"*".equals(line[4]))
                    methodNames.put(line[1], line[4]);
                String docs = getDocs(line, 5);
                if (!"*".equals(docs))
                    methodDocs.put(line[1], docs);
            }
        }

        EntryTree<EntryMapping> tree = new HashEntryTree<>();

        Files.lines(path.resolve("conf/minecraft.rgs"))
                .filter(it -> it.startsWith(".class_map") || it.startsWith(".field_map") || it.startsWith(".method_map"))
                .map(it -> it.split(" "))
                .forEach(line -> {
                    if (".class_map".equals(line[0])) {
                        tree.insert(new ClassEntry(line[1]), new EntryMapping("net/minecraft/src/" + line[2], classDocs.get(line[1])));
                    } else if (".field_map".equals(line[0])) {
                        String[] obf = line[1].split("/");
                        if (obf[0].length() < 3 && obf[1].length() < 3) {
                            tree.insert(FieldEntry.parse(obf[0], obf[1], fieldDescs.get(line[1])), new EntryMapping(fieldNames.getOrDefault(line[2], line[2]), fieldDocs.get(line[2])));
                        }
                    } else {
                        String[] obf = line[1].split("/");
                        tree.insert(MethodEntry.parse(obf[0], obf[1], line[2]), new EntryMapping(methodNames.getOrDefault(line[3], line[3]), methodDocs.get(line[3])));
                    }
                });

        return tree;
    }

    private static List<String[]> readCsv(Path file, int skip) throws IOException {
        return Files.lines(file).skip(skip).map(line -> line.split(",")).collect(Collectors.toList());
    }

    private static String getDocs(String[] arr, int start) {
        if (start >= arr.length)
            return "*";
        if (arr[start].startsWith("\"")) {
            String docs = Arrays.stream(arr).skip(start).collect(Collectors.joining(", "));
            return docs.substring(1, docs.lastIndexOf('"'));
        }
        return arr[start];
    }

    private static Map<String, String> getAllFieldDescs(Path file) throws IOException {
        Map<String, String> fieldDescs = new HashMap<>();
        JarFile jar = new JarFile(file.toFile());
        for (JarEntry entry : Collections.list(jar.entries())) {
            if (entry.getName().endsWith(".class")) {
                ClassReader reader = new ClassReader(jar.getInputStream(entry));
                reader.accept(new ClassVisitor(Opcodes.ASM7) {
                    String className;

                    @Override
                    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                        this.className = name;
                        super.visit(version, access, name, signature, superName, interfaces);
                    }

                    @Override
                    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                        fieldDescs.put(className + "/" + name, descriptor);
                        return super.visitField(access, name, descriptor, signature, value);
                    }
                }, ClassReader.SKIP_CODE);
            }
        }
        return fieldDescs;
    }

}
