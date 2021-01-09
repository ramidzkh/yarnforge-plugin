# YarnForge Gradle plugin
A Forge specific Gradle plugin to remap its sources to Yarn

**Note:** Although the following says Yarn, you can use anything as long as it is usable by fabric-loom

## Setup
* Clone this repo
* In this repo copy/clone your target project
* In the target's settings.gradle add `includeBuild("..")`
* In the target's build.gradle add the following repositories to the buildscript
  - https://oss.sonatype.org/content/groups/public/
  - https://maven.fabricmc.net/
* In the target's build.gradle add the following to the buildscript dependencies
```
classpath("me.ramidzkh:yarnforge-plugin:<version>")
```
(The version can be found in the build.gradle.kts)
* Apply the `yarnforge-plugin` plugin

## Usage for user mods
`userRemapYarn --mappings net.fabricmc:yarn:<yarn version> --mc-version <mc version> --no-daemon`
* Add `--mixin` for Mixin support
* Make sure at least 1GB of RAM has been assigned to gradle. This should have been done by default already.
* `--no-daemon` is ***extremely important*** or gradle will leak memory until you run `daemon --stop`!

## Usage for Forge
`clean setup setupMCP createMcp2Obf`

`forgeRemapYarn --mappings net.fabricmc:yarn:<yarn version>  --mc-version <mc version> --no-daemon`
* Assign 3GB of RAM to Gradle. If you're starved for RAM, 2GB will usually work but will be slightly slower.
* `--no-daemon` is ***extremely important*** or gradle will leak over a gig of memory until you run `daemon --stop`!
* The `remapped/clean` and `remapped/patched` directories contain Minecraft code

## License
Apache 2.0
