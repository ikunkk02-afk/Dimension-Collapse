# Dimension Collapse · 降维打击

Minecraft **1.21.1 / NeoForge 21.1.264 / Java 21** 模组。

二向箔展开后，球形坍缩前沿持续加速扩散，将地形与生物压到二维平面。服用降维抑制剂的玩家可以在平面内生存、采集二维物质，再将资源送回远处的三维区域。

- 二向箔与蓄力发射器。
- 二维物质供能的扩散抑制装置。
- 跨维资源传送器与三维资源接收箱。
- 坍缩着色器、粒子、镜头效果和带字幕的音效。
- 每维度独立存档、区块增量处理及客户端同步。

详细玩法、合成配方和命令见 [使用说明](使用说明.md)。坍缩会永久修改地形，停止坍缩不会恢复原地形。

## 开发

```sh
./gradlew build
./gradlew runGameTestServer
./gradlew runSmokeClient
```

Windows 使用 `gradlew.bat`。产物位于 `build/libs/`。自动测试使用独立的 `build/gametest-run`、`build/smoke-run`，不会修改普通开发存档。测试类不会进入发布 JAR。

贴图、模型、配方、音效映射和翻译的生成源在 `tools/generate_resources.py`。

项目许可证：[MIT](LICENSE)。NeoForge 模板的原始说明保留在 `TEMPLATE_LICENSE.txt`。
