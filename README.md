# YarnForge Gradle plugin
A Forge specific Gradle plugin to remap its sources to Yarn

**Note:** Although the following says Yarn, you can use anything as long as it is usable by fabric-loom

## Usage for user mods
`userRemapYarn --mappings net.fabricmc:yarn:<yarn version> --no-damon`
* Make sure at least 1GB of RAM has been assigned to gradle. This should have been done by default already.
* `--no-daemon` is ***extremely important*** or gradle will leak memory until you run `daemon --stop`!
## Usage for Forge
`clean setup setupMCP createMcp2Obf`

`forgeRemapYarn --mappings net.fabricmc:yarn:<yarn version> --no-daemon`
* Assign 3GB of RAM to Gradle. If you're starved for RAM, 2GB will usually work but will be slightly slower.
* `--no-daemon` is ***extremely important*** or gradle will leak over a gig of memory until you run `daemon --stop`!
* The `remapped/clean` and `remapped/patched` directories contain Minecraft code

## License
Apache 2.0
