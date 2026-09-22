# Repository Work Notes

- ForgeGradle 7 modules for Minecraft 1.21.x must call `repositories.clear()` before adding `minecraft.mavenizer(it)`, `fg.forgeMaven`, and `fg.minecraftLibsMaven`. The macOS LWJGL native patch is published on Forge Maven and can be shadowed by the inherited NeoForge repository otherwise.
