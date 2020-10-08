package me.ramidzkh.yarnforge.task;

import com.google.gson.*;
import me.ramidzkh.yarnforge.util.Match;
import me.ramidzkh.yarnforge.util.McpMappingsProvider;
import org.apache.commons.io.FileUtils;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class MigrateMappingsTask extends DefaultTask {
    private String start;
    private String target;
    private Supplier<MappingSet> namesProvider;

    public MigrateMappingsTask() {
        setGroup("yarnforge");
    }

    @Option(description = "The version we are starting on", option = "start")
    public void setStart(String start) {
        this.start = start;
    }

    @Option(description = "The version that will be migrated to", option = "target")
    public void setTarget(String target) {
        this.target = target;
    }

    public void setNamesProvider(Supplier<MappingSet> namesProvider) {
        this.namesProvider = namesProvider;
    }

    private Mercury createRemapper() throws IOException {
        Path tempDir = Files.createTempDirectory(new File(System.getProperty("java.io.tmpdir")).toPath(), "yarnforge-plugin-");

        List<String> versionList = new ArrayList<>();
        Path versionManifest = tempDir.resolve("mc-version-manifest.json");
        FileUtils.copyURLToFile(new URL("https://launchermeta.mojang.com/mc/game/version_manifest.json"), versionManifest.toFile());

        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        JsonArray versions = gson.fromJson(new String(Files.readAllBytes(versionManifest), StandardCharsets.UTF_8), JsonObject.class)
                .get("versions").getAsJsonArray();
        boolean collect = false;

        for (JsonElement jsonElement : versions) {
            if (jsonElement.isJsonObject()) {
                JsonObject object = jsonElement.getAsJsonObject();
                String id = object.get("id").getAsJsonPrimitive().getAsString();
                // TODO: clean this control flow
                if (id.equals(target)) {
                    collect = true;
                } else if (id.equals(start)) {
                    versionList.add(id);
                    break;
                }

                if (collect && !id.contains("Shareware") && !id.contains("infinite")) {
                    versionList.add(id);
                }
            }
        }

        Collections.reverse(versionList);
        Match match = null;

        String previous = null;
        for (String next : versionList) {
            if (previous == null) {
                previous = next;
                continue;
            }
            System.out.println(previous + "-" + next);
            Path matchCache = tempDir.resolve(previous + next);

            try {
                FileUtils.copyURLToFile(new URL("https://raw.githubusercontent.com/FabricMC/intermediary/master/matches/"
                        + previous.replace(" ", "%20") + "-" + next.replace(" ", "%20") + ".match"), matchCache.toFile());

            } catch (IOException ex) {
                try {
                    FileUtils.copyURLToFile(new URL("https://raw.githubusercontent.com/Legacy-Fabric/Legacy-Intermediaries/master/matches/"
                            + previous + "-" + next + ".match"), matchCache.toFile());
                } catch (IOException ex2) {
                    throw new IllegalArgumentException("unable to find match for version " + new URL("https://raw.githubusercontent.com/Legacy-Fabric/Legacy-Intermediaries/master/matches/"
                    + previous.replace(" ", "%20") + "-" + next.replace(" ", "%20") + ".match"));
                }
            }

            Match newMatch = Match.parse(matchCache.toFile());
            if (match == null) {
                match = newMatch;
            } else {
                match = match.chain(newMatch);
            }

            previous = next;
        }

        // MCP resolver at home.
        McpMappingsProvider provider = new McpMappingsProvider();
        MappingSet start = namesProvider.get();
        MappingSet end = provider.getModernMappings(this.target);
        MappingSet result = match.updateMappings(start);
        result = result.reverse().merge(end);

        Mercury mercury = new Mercury();
        mercury.getProcessors().add(MercuryRemapper.create(result, false));
        return mercury;
    }

    private Set<File> getAllDependencies() {
        Set<File> files = new HashSet<>();

        for (Configuration configuration : getProject().getConfigurations()) {
            try {
                files.addAll(configuration.getFiles());
            } catch (Throwable ignored) {
            }
        }

        return files;
    }

    @TaskAction
    public void doTheThing() {
        Project project = getProject();

        try {
            Mercury mercury = createRemapper();

            for (File file : getAllDependencies()) {
                mercury.getClassPath().add(file.toPath());
            }

            project.getLogger().lifecycle(":remapping");
            mercury.rewrite(project.file("src/main/java").toPath(), project.file("remapped").toPath());
        } catch (Exception ex) {
            throw new RuntimeException("failed to remap", ex);
        }
        finally {
            System.gc();
        }
    }
}
