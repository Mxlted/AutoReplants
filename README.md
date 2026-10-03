# AutoReplants

A client-side Fabric mod that automatically replants crops when you break them while holding a hoe.

## Features

- Automatically replants crops when broken with any hoe (wooden, stone, iron, golden, diamond, netherite)
- Works with wheat, carrots, potatoes, beetroots, nether wart, torchflower, and pitcher crops
- Seeds must be in your hotbar to replant
- Works at any crop growth stage by default, including fully grown torchflowers and either half of pitcher crops
- Rechecks reach and planting space, restores the selected hotbar slot, and retries failed attempts within a short limit
- Client-side only; replanting uses normal item-use packets, so servers can still reject placement if the crop space changes or a server rule blocks it

## Settings

Install [Mod Menu](https://modrinth.com/mod/modmenu) and [Cloth Config](https://modrinth.com/mod/cloth-config) to open the settings screen. Both are optional; without them, a fresh install uses the defaults below.

| Setting | Default |
|---------|---------|
| Enabled | On |
| Require a hoe | On |
| Mature crops only | Off |
| Replant delay | 2 ticks (0–10) |
| Sneak to bypass | Off |

Sneak to bypass lets you hold sneak to clear a field without replanting. Settings are saved in `config/autoreplants.json` and still apply if the settings-screen mods are removed. You can also edit that file while the game is closed.

## Version Compatibility

- Minecraft: 26.1, 26.1.1, 26.1.2, 26.2, 26.3
- Fabric Loader: 0.19.3 or higher
- Fabric API: a build compatible with your Minecraft version
- Java: 25

## Build from Source

```bash
git clone https://github.com/Mxlted/autoreplants.git
cd autoreplants
gradlew build
```
