# YarnForge Gradle plugin
A Forge specific Gradle plugin to remap its sources to Yarn

**Note:** Although the following says Yarn, you can use anything as long as it is usable by fabric-loom

## Setup
1. Clone this repo
2. In this repo copy/clone your target project
3. In the target's settings.gradle add `includeBuild("..")`
4. In the target's build.gradle add the following repositories to the buildscript block
   - https://oss.sonatype.org/content/groups/public/
   - https://maven.fabricmc.net/
5. In the target's build.gradle add the following to the buildscript dependencies
```
classpath("me.ramidzkh:yarnforge-plugin")
```
(The version can be found in the build.gradle.kts)
6. Apply the `yarnforge-plugin` plugin **after** Forge's plugin is applied

## Usage for user mods
`userRemapYarn --mappings net.fabricmc:yarn:<yarn version> --mc-version <mc version> --no-daemon`
* Add `--mixin` for Mixin support
* Make sure at least 1GB of RAM has been assigned to gradle. This should have been done by default already.
* `--no-daemon` is ***extremely important*** or gradle will leak memory until you run `daemon --stop`!

## Usage for Forge itself (like https://github.com/MinecraftForge/MinecraftForge)
`clean setup setupMCP createMcp2Obf`

`forgeRemapYarn --mappings net.fabricmc:yarn:<yarn version>  --mc-version <mc version> --no-daemon`
* Assign 3GB of RAM to Gradle. If you're starved for RAM, 2GB will usually work but will be slightly slower.
* `--no-daemon` is ***extremely important*** or gradle will leak over a gig of memory until you run `daemon --stop`!
* The `remapped/clean` and `remapped/patched` directories contain Minecraft code

## License
Apache 2.0
