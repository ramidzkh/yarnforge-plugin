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
import me.ramidzkh.yarnforge.MappingBridge;
import me.ramidzkh.yarnforge.patch.YarnForgeRewriter;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.stitch.commands.CommandMergeJar;
import net.fabricmc.stitch.commands.CommandProposeFieldNames;
import net.minecraftforge.gradle.common.util.Artifact;
import net.minecraftforge.gradle.common.util.MinecraftRepo;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.mixin.MixinRemapper;
import org.cadixdev.mercury.mixin.cleaner.MixinCleaner;
import org.cadixdev.mercury.remapper.MercuryRemapper;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.tasks.options.Option;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

public abstract class BaseRemappingTask extends DefaultTask {

    private String version;
    private String mappings;
    private boolean mixin;
    private Supplier<MappingSet> namesProvider;

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

        mercury.getProcessors().add(MercuryRemapper.create(mappings, false));
        mercury.getProcessors().add(new YarnForgeRewriter(mappings));
        return mercury;
    }

    private MappingSet createMcpToYarn() throws IOException {
        if (version == null || mappings == null) {
            throw new GradleException("Missing --mc-version and/or --mappings");
        }

        Project project = getProject();
        MappingSet obfToYarn = MappingBridge.loadTiny(loadTree(project, mappings), "official", "named");
        MappingSet obfToMcp = namesProvider.get();
        obfToMcp.addFieldTypeProvider(MappingBridge.fromMappings(obfToYarn));
        return MappingBridge.copy(obfToMcp).reverse().merge(obfToYarn);
    }

    public TinyTree loadTree(Project project, String mappings) throws IOException {
        try (FileSystem archive = FileSystems.newFileSystem(project.getConfigurations().detachedConfiguration(project.getDependencies().create(mappings)).getSingleFile().toPath(), null)) {
            Path copy = Files.createTempFile("mappings", ".tiny");
            Path output = Files.createTempFile("mappings", ".tiny");

            Files.deleteIfExists(copy);
            Files.deleteIfExists(output);

            Files.copy(archive.getPath("mappings/mappings.tiny"), copy);

            proposeFieldNames(MinecraftRepo.create(project), version, copy, output);

            try (BufferedReader reader = Files.newBufferedReader(output)) {
                return TinyMappingFactory.loadWithDetection(reader);
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

    private static void proposeFieldNames(ArtifactProvider<ArtifactIdentifier> provider, String version, Path in, Path out) throws Exception {
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
    }
}
