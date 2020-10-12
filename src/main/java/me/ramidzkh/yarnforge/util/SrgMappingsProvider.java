package me.ramidzkh.yarnforge.util;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.srg.tsrg.TSrgReader;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;

/**
 * Lifted from Patchwork Patcher with permission
 */
public class SrgMappingsProvider {
    public static MappingSet provideSrg(String minecraftVersion) {
        try {
            Path tempDir = Files.createTempDirectory(new File(System.getProperty("java.io.tmpdir")).toPath(), "yarnforge-plugin-");
            Path mcpConfig = tempDir.resolve("mcp-config.zip");
            String mcpVersion = getMcpVersion(minecraftVersion);
            FileUtils.copyURLToFile(new URL("https://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_config/" + mcpVersion
                    + "/mcp_config-" + mcpVersion + ".zip"), mcpConfig.toFile());
            // the jar loader opens zips just fine
            URI inputJar = new URI("jar:" + mcpConfig.toUri());

            try (FileSystem fs = FileSystems.newFileSystem(inputJar, Collections.emptyMap())) {
                return new TSrgReader(new StringReader(new String(Files.readAllBytes(fs.getPath("/config/joined.tsrg"))))).read();
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

    }

    private static String getMcpVersion(String minecraftVersion) {
        try {
            Document document = new SAXReader().read(new URL("https://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_config/maven-metadata.xml"));
            return findNewestVersion(document.selectNodes("/metadata/versioning/versions/version"), minecraftVersion);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (DocumentException ex) {
            throw new UncheckedIOException(new IOException(ex));
        }
    }

    /**
     * This uses the fact that maven-metadata.xml has it's versions kept in oldest to newest version.
     * To find the newest version of a Forge dependency for our Minecraft version, we just
     * reverse the list and find the first (newest) dependency.
     */
    private static String findNewestVersion(List<Node> nodes, String minecraftVersion) {
        Collections.reverse(nodes);

        for (Node node : nodes) {
            if (node.getText().startsWith(minecraftVersion)) {
                return node.getText();
            }
        }

        throw new IllegalArgumentException("Could not find a release of MCP for minecraft version " + minecraftVersion);
    }
}
