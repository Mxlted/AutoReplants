# AutoReplants

A client-side Fabric mod that automatically replants crops when you break them while holding a hoe.

## Features

- Automatically replants crops when broken with any hoe (wooden, stone, iron, golden, diamond, netherite)
- Works with wheat, carrots, potatoes, beetroots, nether wart, torchflower, and pitcher crops
- Seeds must be in your hotbar to replant
- Works at any crop growth stage
- Client-side only; replanting uses normal item-use packets, so servers can still reject placement if the crop space changes or a server rule blocks it

## Version Compatibility

- Minecraft: 26.2
- Fabric Loader: 0.19.3 or higher
- Fabric API: 0.152.1+26.2

## Build from Source

```bash
git clone https://github.com/Mxlted/autoreplants.git
cd autoreplants
gradlew build
```
