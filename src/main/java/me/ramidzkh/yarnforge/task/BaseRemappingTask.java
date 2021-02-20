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

package me.ramidzkh.yarnforge.task;

import com.amadornes.artifactural.api.artifact.ArtifactIdentifier;
import com.amadornes.artifactural.api.repository.ArtifactProvider;
import me.ramidzkh.yarnforge.patch.YarnForgeRewriter;
import me.ramidzkh.yarnforge.util.MappingBridge;
import me.ramidzkh.yarnforge.util.Pair;
import me.ramidzkh.yarnforge.util.TinyV2BiNamespaceMappingsWriter;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.stitch.commands.CommandMergeJar;
import net.fabricmc.stitch.commands.CommandProposeFieldNames;
import net.minecraftforge.gradle.common.util.Artifact;
import net.minecraftforge.gradle.common.util.MinecraftRepo;
import org.cadixdev.bombe.analysis.CachingInheritanceProvider;
import org.cadixdev.bombe.analysis.CascadingInheritanceProvider;
import org.cadixdev.bombe.analysis.InheritanceProvider;
import org.cadixdev.bombe.analysis.ReflectionInheritanceProvider;
import org.cadixdev.bombe.asm.analysis.ClassProviderInheritanceProvider;
import org.cadixdev.bombe.asm.jar.ClassProvider;
import org.cadixdev.bombe.asm.jar.JarFileClassProvider;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.MappingFormats;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.mixin.MixinRemapper;
import org.cadixdev.mercury.mixin.cleaner.MixinCleaner;
import org.cadixdev.mercury.remapper.MercuryRemapper;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.options.Option;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.jar.JarFile;

public abstract class BaseRemappingTask extends DefaultTask {

    private String version;
    private String mappings;
    private boolean mixin;
    private boolean debugMappings;
    private Supplier<MappingSet> namesProvider;

    public BaseRemappingTask() {
        setGroup("yarnforge");
    }

    @Option(description = "Minecraft version", option = "mc-version")
    public void setVersion(String version) {
        this.version = version;
    }

    @Option(description = "Mappings", option = "mappings")
    public void setMappings(String mappings) {
        this.mappings = mappings;
    }

    @Option(description = "Mixin support", option = "mixin")
    public void setMixin(boolean mixin) {
        this.mixin = mixin;
    }

    @Option(description = "Export a variety of mappings. Does not remap", option = "debugMappings")
    public void setDebugMappings(boolean debugMappings) {
        this.debugMappings = debugMappings;
    }

    public void setNamesProvider(Supplier<MappingSet> namesProvider) {
        this.namesProvider = namesProvider;
    }

    protected Mercury createRemapper() throws IOException {
        Mercury mercury = new Mercury();
        MappingSet mappings = createMcpToYarn();

        if (mixin) {
            mercury.getProcessors().add(MixinRemapper.create(mappings));
            mercury.getProcessors().add(MixinCleaner.create());
        }

        mercury.getProcessors().add(MercuryRemapper.create(mappings));
        mercury.getProcessors().add(new YarnForgeRewriter(mappings));
        return mercury;
    }

    private MappingSet createMcpToYarn() throws IOException {
        if (version == null || mappings == null) {
            throw new GradleException("Missing --mc-version and/or --mappings");
        }

        Project project = getProject();
        Pair<TinyTree, File> pair = loadTree(project, mappings);
        MappingSet obfToYarn = MappingBridge.loadTiny(pair.left, "official", "named");
        MappingSet obfToMcp = namesProvider.get();

        // TODO: Bullet-proof propagation
        CascadingInheritanceProvider cascadingInheritanceProvider = new CascadingInheritanceProvider();

        {
            List<ClassProvider> providers = new ArrayList<>();

            providers.add(new JarFileClassProvider(new JarFile(pair.right)));

            // I did some testing, run this and again without this, and see the differences
            // Between the mapping files. You need this
            for (File dependency : getAllDependencies()) {
                if (dependency.isFile()) {
                    providers.add(new JarFileClassProvider(new JarFile(dependency)));
                }
            }

            cascadingInheritanceProvider.install(new ClassProviderInheritanceProvider(klass -> {
                for (ClassProvider provider : providers) {
                    byte[] bytes = provider.get(klass);

                    if (bytes != null) {
                        return bytes;
                    }
                }

                return null;
            }));
        }

        cascadingInheritanceProvider.install(new ReflectionInheritanceProvider(ClassLoader.getSystemClassLoader())); // For JRE classes

        InheritanceProvider inheritanceProvider = new CachingInheritanceProvider(cascadingInheritanceProvider);
        MappingBridge.iterateClasses(obfToYarn, classMapping -> classMapping.complete(inheritanceProvider));
        MappingBridge.iterateClasses(obfToMcp, classMapping -> classMapping.complete(inheritanceProvider));

        MappingSet mcpToYarn = obfToMcp.reverse().merge(obfToYarn);

        debug("obf", "yarn", obfToYarn);
        debug("obf", "mcp", obfToMcp);
        debug("mcp", "yarn", mcpToYarn);

        if (debugMappings) {
            throw new RuntimeException("Killing remapping, maybe not so gracefully");
        }

        return mcpToYarn;
    }

    public Pair<TinyTree, File> loadTree(Project project, String mappings) throws IOException {
        try (FileSystem archive = FileSystems.newFileSystem(project.getConfigurations().detachedConfiguration(project.getDependencies().create(mappings)).getSingleFile().toPath(), (ClassLoader) null)) {
            Path copy = Files.createTempFile("mappings", ".tiny");
            Path output = Files.createTempFile("mappings", ".tiny");

            Files.deleteIfExists(copy);
            Files.deleteIfExists(output);

            Files.copy(archive.getPath("mappings/mappings.tiny"), copy);

            File merged = proposeFieldNames(MinecraftRepo.create(project), version, copy, output);

            try (BufferedReader reader = Files.newBufferedReader(output)) {
                return new Pair<>(TinyMappingFactory.loadWithDetection(reader), merged);
            } finally {
                Files.deleteIfExists(copy);
                Files.deleteIfExists(output);
            }
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private static File proposeFieldNames(ArtifactProvider<ArtifactIdentifier> provider, String version, Path in, Path out) throws Exception {
        File client = provider.getArtifact(Artifact.from("net.minecraft:client:" + version)).optionallyCache(null).asFile();
        File server = provider.getArtifact(Artifact.from("net.minecraft:server:" + version)).optionallyCache(null).asFile();
        File merged = File.createTempFile("merged", ".jar");

        if (merged.exists()) {
            merged.delete();
        }

        String[] mergeArgs = {
                client.getAbsolutePath(),
                server.getAbsolutePath(),
                merged.getAbsolutePath()
        };

        new CommandMergeJar().run(mergeArgs);

        String[] proposeArgs = {
                merged.getAbsolutePath(),
                in.toAbsolutePath().toString(),
                out.toAbsolutePath().toString()
        };

        new CommandProposeFieldNames().run(proposeArgs);

        return merged;
    }

    protected Set<File> getAllDependencies() {
        Set<File> files = new HashSet<>();

        for (Configuration configuration : getProject().getConfigurations()) {
            try {
                files.addAll(configuration.getFiles());
            } catch (Throwable ignored) {
            }
        }

        return files;
    }

    private void debug(String a, String b, MappingSet mappings) throws IOException {
        if (debugMappings) {
            Path path = getProject().file("remapped/" + a + "To" + capitaliseFirstCharacter(b) + ".srg").toPath();
            Files.createDirectories(path.getParent());

            try (BufferedWriter writer = Files.newBufferedWriter(path)) {
                MappingFormats.SRG.createWriter(writer).write(mappings);
            }
        } else {
            {
                Path aToB = getProject().file("remapped/" + a + "To" + capitaliseFirstCharacter(b) + ".tiny").toPath();
                Files.createDirectories(aToB.getParent());

                try (BufferedWriter writer = Files.newBufferedWriter(aToB)) {
                    new TinyV2BiNamespaceMappingsWriter(writer, a, b).write(mappings);
                }
            }

            {
                Path bToA = getProject().file("remapped/" + b + "To" + capitaliseFirstCharacter(a) + ".tiny").toPath();
                Files.createDirectories(bToA.getParent());

                try (BufferedWriter writer = Files.newBufferedWriter(bToA)) {
                    new TinyV2BiNamespaceMappingsWriter(writer, b, a).write(mappings.reverse());
                }
            }
        }
    }

    private static String capitaliseFirstCharacter(String s) {
        if (s.length() > 0) {
            return Character.toUpperCase(s.charAt(0)) + s.substring(1);
        } else {
            return s;
        }
    }
}
