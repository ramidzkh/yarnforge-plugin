# YarnForge Gradle plugin
A Forge specific Gradle plugin to remap its sources to Yarn

## Usage
`clean setup setupMCP createMcp2Obf`

`remapYarn --mappings net.fabricmc:yarn:<yarn version> --no-daemon`
* Assign 3GB of RAM to gradle. If you're starved for RAM, 2GB will usually work but will be slightly slower.
* `--no-daemon` is ***extremely important*** or gradle will leak over a gig of memory until you run `daemon --stop`!
* The `remapped/clean` and `remapped/patched` directories contain Minecraft code

## License
Apache 2.0
