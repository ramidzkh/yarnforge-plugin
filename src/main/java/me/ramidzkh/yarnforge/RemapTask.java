/*
 * Copyright 2020 Ramid Khan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.ramidzkh.yarnforge;

import com.cloudbees.diff.Diff;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import net.fabricmc.mapping.tree.*;
import net.minecraftforge.gradle.mcp.task.GenerateSRG;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.objectweb.asm.commons.Remapper;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

public class RemapTask extends DefaultTask {

    private String mappings;

    public RemapTask() {
        setDescription("Remaps src/main/java, src/test/java and patches to other mappings");
    }

    @Option(description = "Mappings", option = "mappings")
    public void setMappings(String mappings) {
        this.mappings = mappings;
    }

    @TaskAction
    public void doTask() throws Exception {
        Project project = getProject();
        Path dir = project.getProjectDir().toPath();

        Map<String, Cls> mappings = toMap(loadTree(project, this.mappings));
        Map<String, Cls> mcp = toMap(((GenerateSRG) project.getTasks().getByPath(":clean:createMcp2Obf")).getOutput(), mappings);
        Map<String, Cls> joined = reverse(join(reverse(mappings), reverse(mcp)));

        MappingSet m = load(joined);

        ConfigurationContainer configurations = project.project(":forge").getConfigurations();
        List<Path> compileClasspath = configurations.getByName("compileClasspath").getFiles().stream().map(File::toPath).collect(Collectors.toList());
        List<Path> testCompileClasspath = configurations.getByName("testCompileClasspath").getFiles().stream().map(File::toPath).collect(Collectors.toList());

        compileClasspath.add(dir.resolve("src/fmllauncher/java"));
        testCompileClasspath.removeAll(compileClasspath);

        Path main = dir.resolve("src/main/java");
        Path test = dir.resolve("src/test/java");
        Path clean = dir.resolve("projects/clean/src/main/java");
        Path patched = dir.resolve("projects/forge/src/main/java");

        Path mappedClean = dir.resolve("remapped/clean");
        Path mappedPatched = dir.resolve("remapped/patched");
        Mercury mercury = new Mercury();
        mercury.getProcessors().add(MercuryRemapper.create(m));
        mercury.getClassPath().addAll(compileClasspath);
        mercury.getClassPath().add(patched);
        mercury.rewrite(main, dir.resolve("remapped/main"));
        project.getLogger().lifecycle(":remapped main");
        mercury.getClassPath().remove(patched);

        mercury.getClassPath().add(main);
        mercury.rewrite(patched, dir.resolve(mappedPatched));
        project.getLogger().lifecycle(":remapped patched");
        mercury.getClassPath().remove(patched);

        mercury.rewrite(clean, mappedClean);
        project.getLogger().lifecycle(":remapped clean");

        {
            project.getLogger().lifecycle(":patching");
            try (Stream<Path> stream = Files.walk(mappedClean)) {
                stream.filter(Files::isRegularFile)
                        .forEach(c -> {
                            Path file = mappedClean.relativize(c);

                            try {
                                String patch = makePatch(file.toString(), new String(Files.readAllBytes(c)), new String(Files.readAllBytes(mappedPatched.resolve(file))));

                                if (patch != null) {
                                    Path p = dir.resolve("remapped/patches").resolve(file + ".patch");
                                    Files.createDirectories(p.getParent());
                                    Files.write(p, patch.getBytes(), StandardOpenOption.CREATE);
                                }
                            } catch (IOException exception) {
                                exception.printStackTrace();
                            }
                        });
            }

        }
    }

    private static String makePatch(String relative, String a, String b) throws IOException {
        String originalRelative = "a/" + relative;
        String modifiedRelative = "b/" + relative;
        String originalData = a.replace("\r\n", "\n");
        String modifiedData = b.replace("\r\n", "\n");
        Diff diff = Diff.diff(new StringReader(originalData), new StringReader(modifiedData), false);
        return !diff.isEmpty() ? diff.toUnifiedDiff(originalRelative, modifiedRelative, new StringReader(originalData), new StringReader(modifiedData), 3).replaceAll("\r?\n", "\n") : null;
    }

    private static MappingSet load(Map<String, Cls> joined) {
        MappingSet mappings = MappingSet.create();

        for (Cls c : joined.values()) {
            ClassMapping<?, ?> classMapping = mappings.getOrCreateClassMapping(c.official).setDeobfuscatedName(c.mapped);

            for (Table.Cell<String, String, String> field : c.fields.cellSet()) {
                classMapping.getOrCreateFieldMapping(field.getRowKey(), field.getColumnKey()).setDeobfuscatedName(field.getValue());
            }

            for (Table.Cell<String, String, String> method : c.methods.cellSet()) {
                classMapping.getOrCreateMethodMapping(method.getRowKey(), method.getColumnKey()).setDeobfuscatedName(method.getValue());
            }
        }

        return mappings;
    }

    private static TinyTree loadTree(Project project, String mappings) throws IOException {
        try (ZipFile archive = new ZipFile(project.getConfigurations().detachedConfiguration(project.getDependencies().create(mappings)).getSingleFile());
             BufferedReader reader = new BufferedReader(new InputStreamReader(archive.getInputStream(archive.getEntry("mappings/mappings.tiny"))))) {
            return TinyMappingFactory.loadWithDetection(reader);
        }
    }

    private static Map<String, Cls> join(Map<String, Cls> yarn2Official, Map<String, Cls> official2Mcp) {
        Map<String, Cls> classes = new HashMap<>();
        Map<String, Cls> copy = new HashMap<>(official2Mcp);

        Remapper remapper = new Remapper() {
            @Override
            public String map(String internalName) {
                Cls c = yarn2Official.get(internalName);
                return c == null ? null : c.mapped;
            }
        };

        for (Cls yarnClass : yarn2Official.values()) {
            Cls mcpClass = copy.remove(yarnClass.mapped);

            if (mcpClass == null) {
                continue;
            }

            Cls bridge = new Cls(yarnClass.official, mcpClass.mapped);

            for (Table.Cell<String, String, String> field : yarnClass.fields.cellSet()) {
                String s = mcpClass.fields.get(field.getValue(), remapper.mapDesc(field.getColumnKey()));
                bridge.fields.put(field.getRowKey(), field.getColumnKey(), s);
            }

            for (Table.Cell<String, String, String> method : yarnClass.methods.cellSet()) {
                String s = method.getValue();

                if (!"<init>".equals(s)) {
                    s = mcpClass.methods.get(s, remapper.mapMethodDesc(method.getColumnKey()));
                }

                try {
                    bridge.methods.put(method.getRowKey(), method.getColumnKey(), s);
                } catch (NullPointerException e) {
                    System.err.println("No bridge found for " + method.getRowKey() + '\t' + method.getColumnKey() + '\t' + method.getValue());
                }
            }

            classes.put(bridge.official, bridge);
        }

        return classes;
    }

    private static Map<String, Cls> reverse(Map<String, Cls> classes) {
        Map<String, Cls> map = new HashMap<>();

        Remapper remapper = new Remapper() {
            @Override
            public String map(String internalName) {
                Cls c = classes.get(internalName);
                return c == null ? null : c.mapped;
            }
        };

        for (Cls c : classes.values()) {
            Cls cc = new Cls(c.mapped, c.official);

            for (Table.Cell<String, String, String> field : c.fields.cellSet()) {
                cc.fields.put(field.getValue(), remapper.mapDesc(field.getColumnKey()), field.getRowKey());
            }

            for (Table.Cell<String, String, String> method : c.methods.cellSet()) {
                cc.methods.put(method.getValue(), remapper.mapMethodDesc(method.getColumnKey()), method.getRowKey());
            }

            map.put(c.mapped, cc);
        }

        return map;
    }

    private static Map<String, Cls> toMap(TinyTree tree) {
        Map<String, Cls> classes = new HashMap<>();

        for (ClassDef c : tree.getClasses()) {
            Cls cls = new Cls(c.getName("official"), c.getName("named"));
            classes.put(cls.official, cls);

            for (FieldDef field : c.getFields()) {
                cls.fields.put(field.getName("official"), field.getDescriptor("official"), field.getName("named"));
            }

            for (MethodDef method : c.getMethods()) {
                cls.methods.put(method.getName("official"), method.getDescriptor("official"), method.getName("named"));
            }
        }

        return classes;
    }

    private static Map<String, Cls> toMap(File mcp2Obf, Map<String, Cls> yarn) throws IOException {
        Map<String, Cls> classes = new HashMap<>();
        Map<String, String> m2o = new HashMap<>();
        Cls last = null;

        for (String line : Files.readAllLines(mcp2Obf.toPath())) {
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("\t")) {
                if (last == null) {
                    throw new IllegalStateException("Invalid line " + line);
                } else {
                    String[] s = line.substring(1).split(" ");

                    if (s.length == 2) {
                        last.fields.put(s[0], "", s[1]);
                    } else if (s.length == 3) {
                        last.methods.put(s[0], s[1], s[2]);
                    } else {
                        throw new IllegalStateException("Invalid line " + line);
                    }
                }
            } else {
                String[] s = line.split(" ");
                classes.put(s[0], last = new Cls(s[0], s[1]));
                m2o.put(s[1], s[0]);
            }
        }

        Remapper remapper = new Remapper() {
            @Override
            public String map(String internalName) {
                return m2o.get(internalName);
            }
        };

        for (Cls c : classes.values()) {
            c.fields = c.fields.cellSet().stream()
                    .collect(Tables.toTable(Table.Cell::getRowKey, cell -> {
                        String desc = yarn.getOrDefault(c.mapped, Cls.DEFAULT).findFieldDesc(cell.getValue());
                        try {
                            return remapper.mapDesc(desc);
                        } catch (StringIndexOutOfBoundsException e) {
                            System.out.println(cell.getValue());
                            System.out.println(desc);
                            throw e;
                        }
                    }, Table.Cell::getValue, HashBasedTable::create));
        }

        return classes;
    }

    private static class Cls {
        public static final Cls DEFAULT = new Cls("def", "def");

        private final String official, mapped;
        private Table<String, String, String> fields;
        private final Table<String, String, String> methods;

        private Cls(String official, String mapped) {
            this.official = official;
            this.mapped = mapped;
            this.fields = HashBasedTable.create();
            this.methods = HashBasedTable.create();
        }

        private String findFieldDesc(String name) {
            for (Map.Entry<String, String> entry : fields.row(name).entrySet()) {
                return entry.getKey();
            }

            return null;
        }
    }
}
