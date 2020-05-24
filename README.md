# YarnForge Gradle plugin
A Forge specific Gradle plugin to remap its sources to Yarn

## Usage
`clean setup setupMCP createMcp2Obf remapYarn --mappings net.fabricmc:yarn:<yarn version>`
* Assign like 20 GB of RAM to Gradle, swap space works
* The `remapped/clean` and `remapped/patched` directories contain Minecraft code

## License
Apache 2.0
