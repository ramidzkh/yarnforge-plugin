package me.ramidzkh.yarnforge.util;

import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.MappingFormats;
import org.cadixdev.lorenz.model.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Shamelessly lifted from Alef, by phase.
 * This is similar to the orignal file, but things are changed to be smaller and less hardcoded.
 * You can find the original code (and license) here:
 * https://github.com/phase/alef/tree/853a9787d077f767dab7fc4938b95a750d618885
 */
public class McpMappingsProvider {
    public static final String SNAPSHOT_URL = "http://export.mcpbot.bspk.rs/mcp_snapshot_nodoc/%s/mcp_snapshot_nodoc-%s.zip";

    public String getMcpVersion(String version) {
        if(version.equals("1.15.2") || version.equals("1.15.1")) {
            return "20201007-1.15.1";
        }
        else {
            throw new IllegalArgumentException("No MCP version cached for " + version);
        }
    }

    public void downloadSnapshotCsvs(File destinationDir, String mcpVersion) throws IOException {
        destinationDir.mkdirs();
        URL url = new URL(SNAPSHOT_URL.replaceAll("%s", mcpVersion));
        ZipInputStream inputStream = new ZipInputStream(url.openStream());
        ZipEntry entry = inputStream.getNextEntry();
        while (entry != null) {
            if (entry.getName().equals("fields.csv") || entry.getName().equals("methods.csv")) {
                copyToFile(inputStream, new File(destinationDir, entry.getName()));
            }
            entry = inputStream.getNextEntry();
        }
        inputStream.close();
    }

    public Map<String, String> parseCsv(File csv) throws IOException {
        HashMap<String, String> mappings = new HashMap<>();
        List<String> lines = Files.readAllLines(csv.toPath());
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
        File versionDir = new File(tempDir.toFile(), minecraftVersion);
        File mcpFile = new File(versionDir, "mcp.tsrg");
        downloadSnapshotCsvs(versionDir, mcpVersion);
        Map<String, String> mcpFields = parseCsv(new File(versionDir, "fields.csv"));
        Map<String, String> mcpMethods = parseCsv(new File(versionDir, "methods.csv"));
        MappingSet srgMappings = SrgMappingsProvider.provideSrg(minecraftVersion);

        for (TopLevelClassMapping classMapping : srgMappings.getTopLevelClassMappings()) {
            replaceSrgNames(mcpFields, mcpMethods, classMapping);
        }

        MappingFormats.TSRG.write(srgMappings, mcpFile.toPath());
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

    private static void copyToFile(InputStream inputStream, File file) throws IOException {
        ReadableByteChannel readChannel = Channels.newChannel(inputStream);
        FileOutputStream output = new FileOutputStream(file);
        FileChannel writeChannel = output.getChannel();
        writeChannel.transferFrom(readChannel, 0, Long.MAX_VALUE);
        writeChannel.force(false);
        writeChannel.close();
        output.close();
    }
}