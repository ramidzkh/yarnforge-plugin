package me.ramidzkh.yarnforge.util;

import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.MappingFormats;
import org.cadixdev.lorenz.model.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shamelessly lifted from Alef, by phase.
 * This is similar to the orignal file, but things are changed to be smaller and less hardcoded.
 * You can find the original code (and license) here:
 * https://github.com/phase/alef/tree/853a9787d077f767dab7fc4938b95a750d618885
 */
public class McpMappingsProvider {
    public static final String SNAPSHOT_URL = "https://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_snapshot/{}/mcp_snapshot-{}.zip";

    public String getMcpVersion(String version) {
        if (version.equals("1.15.2") || version.equals("1.15.1")) {
            return "20201007-1.15.1";
        } else {
            throw new IllegalArgumentException("No MCP version cached for " + version);
        }
    }

    public void downloadSnapshotCsvs(Path destinationDir, String mcpVersion) throws IOException {
        try {
            Files.createDirectories(destinationDir);
            URI uri = new URI("jar:" + SNAPSHOT_URL.replace("{}", mcpVersion));

            try (FileSystem fs = FileSystems.getFileSystem(uri)) {
                Files.copy(fs.getPath("fields.csv"), destinationDir.resolve("fields.csv"));
                Files.copy(fs.getPath("methods.csv"), destinationDir.resolve("methods.csv"));
            }
        } catch (URISyntaxException ex) {
            throw new IOException(ex);
        }
    }

    public Map<String, String> parseCsv(Path csv) throws IOException {
        HashMap<String, String> mappings = new HashMap<>();
        List<String> lines = Files.readAllLines(csv);
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split(",");
            mappings.put(parts[0], parts[1]);
        }
        return mappings;
    }

    public MappingSet getModernMappings(String version) throws IOException {
        return getModernMappings(version, getMcpVersion(version));
    }

    public MappingSet getModernMappings(String minecraftVersion, String mcpVersion) throws IOException {
        Path tempDir = Files.createTempDirectory(new File(System.getProperty("java.io.tmpdir")).toPath(), "yarnforge-plugin-");
        Path versionDir = tempDir.resolve(minecraftVersion);
        Path mcpFile = tempDir.resolve("mcp.tsrg");
        downloadSnapshotCsvs(versionDir, mcpVersion);
        Map<String, String> mcpFields = parseCsv(tempDir.resolve("fields.csv"));
        Map<String, String> mcpMethods = parseCsv(tempDir.resolve("methods.csv"));
        MappingSet srgMappings = SrgMappingsProvider.provideSrg(minecraftVersion);

        for (TopLevelClassMapping classMapping : srgMappings.getTopLevelClassMappings()) {
            replaceSrgNames(mcpFields, mcpMethods, classMapping);
        }

        MappingFormats.TSRG.write(srgMappings, mcpFile);
        return srgMappings;
    }

    private void replaceSrgNames(Map<String, String> mcpFields, Map<String, String> mcpMethods, ClassMapping<?, ?> classMapping) {
        for (FieldMapping fieldMapping : classMapping.getFieldMappings()) {
            String mcpField = mcpFields.get(fieldMapping.getDeobfuscatedName());
            if (mcpField != null) {
                fieldMapping.setDeobfuscatedName(mcpField);
            }
        }
        for (MethodMapping methodMapping : classMapping.getMethodMappings()) {
            String mcpMethod = mcpMethods.get(methodMapping.getDeobfuscatedName());
            if (mcpMethod != null) {
                methodMapping.setDeobfuscatedName(mcpMethod);
            }
        }
        for (InnerClassMapping innerClassMapping : classMapping.getInnerClassMappings()) {
            replaceSrgNames(mcpFields, mcpMethods, innerClassMapping);
        }
    }
}