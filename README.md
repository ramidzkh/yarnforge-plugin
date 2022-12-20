Hello everyone, it's been a while. Forge now uses official Mojang mappings, and so should you if you wish to bring your Forge codebase to Fabric. Nevertheless, this tool *should* work, but I won't be updating it.

# YarnForge Gradle plugin
A Forge specific Gradle plugin to remap its sources to Yarn.
If your project is using any version of ForgeGradle 3, make sure to switch to the `FG_3.0` tag.
Although the following says Yarn, you can use anything but Mojang mappings as long as it is usable by fabric-loom

## Setup
1. Clone this repo
2. In the target project's build.gradle add the following repositories to the buildscript block
    - https://oss.sonatype.org/content/groups/public/
    - https://maven.fabricmc.net/
   ```
   maven { url = uri("https://oss.sonatype.org/content/groups/public/") }
   maven { url = uri("https://maven.fabricmc.net/") }
   ```
3. In the target project's build.gradle add the following to the buildscript dependencies
   ```
   classpath("me.ramidzkh:yarnforge-plugin")
   ```
4. Apply the `yarnforge-plugin` plugin **after** Forge's plugin is applied
   ```
   apply(plugin: "yarnforge-plugin")
   ```

## Usage for user mods
`./gradlew --include-build <location to where you cloned yarnforge> userRemapYarn --mappings net.fabricmc:yarn:<yarn version> --mc-version <mc version> --no-daemon`
* Add `--mixin` for Mixin support
* Make sure at least 1GB of RAM has been assigned to gradle. This should have been done by default already.
* `--no-daemon` is ***extremely important*** or gradle will leak memory until you run `daemon --stop`!

## Usage for Forge itself (as in https://github.com/MinecraftForge/MinecraftForge)
`./gradlew --include-build <location to where you cloned yarnforge> clean setup forgeRemapYarn --mappings net.fabricmc:yarn:<yarn version> --mc-version <mc version> --no-daemon`
* Assign at least 3GB of RAM to Gradle. If you're starved for RAM, 2GB will usually work but will be slightly slower.
* Add `--skip-clean` to skip remapping the clean source set
* `--no-daemon` is ***extremely important*** or gradle will leak over a gig of memory until you run `daemon --stop`!
* The `remapped/clean` and `remapped/patched` directories contain Minecraft code, so be careful of publishing those

## License
Apache 2.0
