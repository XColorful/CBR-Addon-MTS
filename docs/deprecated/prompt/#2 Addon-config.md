> #2 提示词

# 

```
先按照CGC-Compat-MachineMax的写法，给CBR-Addon-MTS补一个config的框架，只是目前没有config项
```

# 

我先看看两个项目的结构，重点参考 CGC-Compat-MachineMax 的 config 写法。

build 目录干扰了，我排除掉再看源文件。

两个项目结构我都看过了。CGC 的 config 是 `core/config/CgccMMConfig.java` + `core/api/config/CgccMMConfigTag.java`，配 `FILE_NAME` / `gameDirectory` / `configFile` / `reloadConfig()` 那一套；CBR 这边 `CbraMts.java` 和 `CbraMtsForge.java` 已经改了（未提交），只差 config 类本身和 `init` 里的挂载。

有一个结构选择我不确定你的偏好，问一下再动手。

明白，用 `xiao.cbra.mts.config` + `xiao.cbra.mts.api.config`，读写复用 BattleRoyale 的 `JsonObject` 版 JsonUtils。开始写。

现在把 config 挂到 `CbraMts.init` 上。

编译验证一下 `xiao.battleroyale.util.JsonUtils` 在依赖 jar 里能不能访问到。

两个模块都编译通过。确认一下最终的主类。

config 框架补好了，`:core:compileJava` 和 `:forge-compat:compileJava` 都通过。

新增两个文件：

- [CbraMtsConfig.java](core/src/main/java/xiao/cbra/mts/config/CbraMtsConfig.java) — 运行时配置。`load(Path)` 复用 BattleRoyale 的 `JsonUtils.fromJsonString(..., JsonObject.class)` 读取、`JsonUtils.writeJsonToFile(...)` 回写，读失败或字段缺失都退回默认值并照常回写，保证键齐全。`_read(JsonObject)` / `_write(Path)` 现在是空的，配置项往这两处加。
- [CbraMtsConfigTag.java](core/src/main/java/xiao/cbra/mts/api/config/CbraMtsConfigTag.java) — 键名常量，暂时为空。

[CbraMts.java:33](core/src/main/java/xiao/cbra/mts/CbraMts.java:33) 的 `init` 现在会存下 `gameDirectory` / `configFile` 并在注册事件前加载 `config/cbramts.json`，另加了 `reloadConfig()`。`CbraMtsForge` 传 `FMLPaths` 的那部分你之前已经改好了，没动。

一点说明：`_read` 的参数目前没被引用（还没配置项），`_write` 会写出一个 `{}`。要不要我在 README 里也补一段配置文件说明？现在没项可写，所以我先没动。

# 

```
一开始的JsonObject读法参考下CBR的_SubLoadConfigs和AbstractDataManager的写法，只不过不是JsonArray而是直接JsonObject
```

# 

明白，改成 `Files.newInputStream` + `InputStreamReader` + `JsonParser.parseReader` + `isJsonObject()` 判定这套，逐项用 `JsonUtils.getJsonXxx` 取；写入也对齐 `AbstractDataManager` 的 `toJsonString` + `Files.writeString`。

改好了，两个模块编译通过。[CbraMtsConfig.java](core/src/main/java/xiao/cbra/mts/config/CbraMtsConfig.java) 现在：

- 读：`Files.newInputStream` + `InputStreamReader(UTF_8)` try-with-resources，`JsonParser.parseReader` 出 `JsonElement`，非 `JsonObject` 就 warn 跳过，是的话交给 `_read(JsonObject)`。
- `_read` 里逐项用 `JsonUtils.getJsonXxx(jsonObject, tag, 默认值)` 取（现在还没项，空实现）。
- 写：`JsonUtils.toJsonString(jsonObject)` + `Files.createDirectories(parent)` + `Files.writeString`，对齐 `AbstractDataManager.performSaveInternal` 的落盘方式，不再用 `writeJsonToFile`。
- 解析失败（含空文件）走 `IOException | RuntimeException` 统一兜底，回默认值后照常回写。

顺手记了一条 memory：本项目 JSON 文件 IO 以 `_SubLoadConfigs` / `AbstractDataManager` 为准，CGC 那套流式也只是结构上参考。

# 
---

```
现在要在CBR-Addon-MTS加config，通过mixin来实现：
- 预期在config加配置项一键改全载具生命上限缩放比例（默认1.0），例如原本载具包血量上限为150，设置成10.0后应该为1500，需要mixin实现
- config加一个boolean，让所有part都是可攻击的类型（attack），见./docs/deprecated/prompt/#1 Vehicle-generator.md
- 检查immersive vehicle的载具渲染距离，可能需要像CGC-Compat-MachineMax加两个配置来修改渲染距离
- 再加两个config改声音传播距离，不需要音量自愈

如果需要mixin extra，则按照Custom Gun Continued相同的方式补充到gradle配置

可以开子agent分别研究
```

# 

我先看一下项目结构和相关文档。

我先并行开四个子agent研究，同时自己看当前仓库的剩余源码。

四个子agent在跑。我先看清当前仓库的接线代码。

四个研究 agent 已在跑（MTS 血量/攻击盒、MTS 渲染/音效距离、MachineMax 渲染距离配置范式、CGC 的 MixinExtras 接线），同时我起了个基线编译。

已确认的现状：

- 配置框架在 [CbraMtsConfig.java](core/src/main/java/xiao/cbra/mts/config/CbraMtsConfig.java)（`_read`/`_write` 都还是空的）+ [CbraMtsConfigTag.java](core/src/main/java/xiao/cbra/mts/api/config/CbraMtsConfigTag.java)。
- Mixin 环境已就绪：[cbramts.mixins.json](core/src/main/resources/cbramts.mixins.json) 的 `mixins` 数组为空、没有 `client` 段；forge-compat 绑了 refmap 和 MixinConfigs manifest。
- 一个问题先记着：mixin 类该放 `core/src` 还是 `forge-compat/src` —— 参考项目的 mixin 都在 `core/src/main/java/.../core/mixin` 和 `.../client/mixin`，但本项目 `core/build.gradle` 目前没有 mixin 依赖，放 core 的话得补一行 `compileOnly`。等 MachineMax 那份调研回来再定。

等 agent 结果回来我就开始写配置项和 mixin。

基线编译通过（exit 0）。趁 agent 还在跑，我确认一下 BattleRoyale 的 JsonUtils 有哪些取值方法可用。

`JsonUtils` 有 `getJsonDouble` / `getJsonBool`，配置读写可直接用。四个研究 agent 还在跑，等结果回来就开始写。

MixinExtras 的接线方式拿到了：`0.5.4`，`io.github.llamalad7:mixinextras-common` + `mixinextras-forge`（mavenCentral 即可），core 里 compileOnly + AP，forge-compat 里 `jarJar(implementation(...))` + `jarJar.ranged`，不需要单独的 mixin json。等另外三个 agent（MTS 血量/攻击盒、MTS 渲染/音效、MachineMax 范式）回来后再决定是否真的要引入。

三份调研已到。等最后一份（MTS 渲染/音效）的同时，我核对一下血量与攻击盒的确切代码。

四份调研都回来了。我先核对 MachineMax 音效那两个配置的确切语义（倍数还是绝对值），以及 1.20.1 里 `EntityType` 的字段名。

四份调研齐了。核心事实已确认：

- **血量**：没有统一 getter，`definition.general.health` 在 8 处被直接读（`AEntityE_Interactable` 的 `attack`/`outOfHealth`/`damage_percent`、HUD、维修提示、碰撞伤害）。而且 `AEntityF_Multipart.attack` 会把命中转发给部件、部件再按 `forwardsDamageMultiplier` 转发回车体 —— 所以**部件血量也算数**，只缩车体会不一致。
- **攻击盒**：`BoundingBox(JSONCollisionBox, JSONCollisionGroup)` 里 `collisionTypes` 是直接引用 `groupDef.collisionTypes`，且 `CollisionType` 是 `JSONCollisionGroup` 的内部枚举 —— 单个注入点就能让全部部件的盒带 `ATTACK`。
- **渲染距离**：MTS 客户端**没有**自己的距离剔除，`InterfaceRender.doRenderCall` 全部渲染；真正的上限是注册时写死的 `clientTrackingRange(32 * 16)`（512 格）→ 和 MachineMax 一样可以走 `@Accessor("clientTrackingRange")`（1.20.1 里确是 `private final int`）。
- **音效**：MTS 不走原版 `playSound`，是自己 OpenAL 那套，传播距离完全由 `SoundInstance` 的 volume 计算决定，兜底上限 `DEFAULT_MAX_DISTANCE = 32`，另有每个音效 JSON 自声明的 `maxDistance/minDistance/middleDistance`。

有三个语义选择需要你定，定完我就开始写。

明白了。第 2 点你提到"引擎和破损声音不修改"，那棵声音分类树我得先摸清 —— 我核实一下 MTS 的音效到底走不走原版衰减，以及有哪些音效类别。

核对完了，有一点和你的假设不一样，需要你定夺：

**MTS 并不走原版声音播放。** 它自己实现了 OpenAL 音效系统（`InterfaceSound`），并且在 `InterfaceSound.update()` 里显式 `AL10.alSourcef(..., AL_ROLLOFF_FACTOR, 0)` **关掉了 OpenAL 的距离衰减**。音量完全由 MTS 自己在 `AEntityD_Definable.updateSounds()` 里按距离算：兜底上限是 `SoundInstance.DEFAULT_MAX_DISTANCE = 32` 格，另外每个音效可以在 JSON 里自声明 `minDistance/middleDistance/maxDistance`。所以没有原版距离衰减可以挂钩，只能改这两个地方。

另外 MTS 的 `JSONSound` **没有分类字段**，音效就是一组列表（`rendering.sounds`），引擎音效在引擎**部件**的 JSON 上，不是车体上。所以"只改载具声音"得按"挂在哪个实体上"或"音效名"来分。

明白：只改引擎音，用 `vehicleSoundDistance`（区块）绝对值替换。我看一下引擎的 Java 类和车包里的引擎音效定义。

引擎音效确实定义在引擎部件的 JSON 里（`rendering.sounds`），且 `PartEngine` 是独立 Java 类。我确认两个字段结构再动手。

车包确认：引擎音效就定义在引擎部件 JSON 的 `rendering.sounds` 里（如 `engine_cooling` 的 0/5/16 距离），且 `PartEngine` 是独立类 —— 按"解析出的是引擎部件"就能精确圈定。再确认一处调用时序。

`PackParser.registerItem` 是唯一的注册入口，里面先调 `LegacyCompatSystem.performLegacyCompats`（会补默认 health=100）再建物品 —— 这正是血量缩放的正确挂点。确认一下它的结构和调用次数。

设计已经收敛。确认两个 API 细节后开始写代码。

设计收敛，开始写代码。

`JsonUtils` 没有 setter，本项目约定是直接 `JsonObject.addProperty`。改掉。

Mixin AP 试图把 MTS 的类名/方法名按混淆映射重映射。目标是普通 mod 类，应该标 `remap = false`。

编译通过。检查 refmap 是否给 `clientTrackingRange` 生成了映射，以及 mixin 类是否进产物。

产物完整。确认一下车包走的 jar 路径确实经过 `registerItem`。

运行时环境没装（`run/` 不存在），先确认一处接线正确性：Forge 是否会注册 `@Mod` 类上的静态 `@SubscribeEvent`。

Forge 的自动注册行为我不想靠记忆，改成明确的 `@EventBusSubscriber`，和本项目 MTS、MachineMax 的写法一致。

README 双语补齐了。最后核对一处可能的遗漏：部件自身的 `boundingBox` 是否也需要 ATTACK。

部件自身的 `boundingBox` 本来就带 `ATTACK|CLICK`，缺的正是 JSON 碰撞组 —— 改动覆盖的正是这一块。看一下完整 diff。

Diff 没问题。关键验证：本项目实际编译依赖的是 Curse 上的 MTS jar，我得确认那些注入目标在**实际 jar** 里存在（而不是只在我读的源码里）。

所有注入目标都在实际依赖 jar 里核对通过。做最后的完整构建确认。

全部完成，构建通过。

## 新增配置（`config/cbramts.json`）

| 键 | 类型 | 默认 | 作用 |
|---|---|---|---|
| `vehicleHealthScale` | double | `1.0` | 全载具生命上限缩放，150 → 1500 设 `10.0` |
| `allPartsAttackable` | boolean | `false` | 所有碰撞盒补 `ATTACK` 类型 |
| `modifyRenderDistance` | boolean | `true` | 是否改载具可见范围 |
| `renderDistance` | int（区块） | `32` | 可见范围，MTS 原值 |
| `modifyVehicleSoundDistance` | boolean | `true` | 是否改引擎音效传播距离 |
| `vehicleSoundDistance` | int（区块） | `20` | 引擎音效传播距离 |

## 实现

**血量** — [LegacyCompatSystemMixin.java](core/src/main/java/xiao/cbra/mts/mixin/LegacyCompatSystemMixin.java) 注入 `LegacyCompatSystem.performLegacyCompats` 的 RETURN。挑这个点是因为 `PackParser.registerItem` 对每个包件都先调它一次（jar 包和文件夹包两条路径的唯一汇合处），且它**之后**才会把缺失的 health 补成 100，所以缩放结果不会被覆盖。做法是直接改 `definition.general.health`，而不是逐个改 8 处读取点 —— 这样 HUD 的 `damage_percent`、维修提示、碰撞伤害会一致地跟着变。按你的选择，载具和部件一起缩（部件有自己的血量，还会按 `forwardsDamageMultiplier` 转发给车体，只改车体会对不上）。

**攻击盒** — [BoundingBoxMixin.java](core/src/main/java/xiao/cbra/mts/mixin/BoundingBoxMixin.java) 注入 `BoundingBox(JSONCollisionBox, JSONCollisionGroup)` 的 RETURN，往 `groupDef.collisionTypes` 补 `ATTACK`。MTS 的 JSON 构造函数把这个 Set **按引用**传给盒，所以补一次就同时覆盖载具本体和全部部件。顺带说明：部件自身的 `boundingBox` 本来就已经带 `ATTACK|CLICK`，缺的正是 JSON 碰撞组这块。

**渲染距离** — [EntityTypeAccessor.java](core/src/main/java/xiao/cbra/mts/mixin/EntityTypeAccessor.java) 用 `@Mutable @Accessor("clientTrackingRange")`，在 [CbraMtsForgeSetup.java](forge-compat/src/main/java/xiao/cbra/mts/compat/forge/CbraMtsForgeSetup.java) 的 `FMLCommonSetupEvent` 里对 `builder_existing` / `builder_seat` / `builder_rendering` 三个实体类型下发。**不需要客户端注入** —— MTS 客户端渲染循环不做任何距离剔除，所以可见范围完全由服务端这个 tracking range 决定，README 不用加"需同时装客户端"的说明。

**音效距离** — 也在同一个解析挂点上（[VehicleScaling.java](core/src/main/java/xiao/cbra/mts/common/VehicleScaling.java)），只改引擎部件的音效：把可听上限换成 `vehicleSoundDistance`，`min/middle/max` 三个距离按比例一起缩，避免三段插值曲线变形。

**MixinExtras 没用上** —— 三个 mixin 都只用 `@Inject` / `@Accessor`，所以 gradle 没动。MTS 的 mixin 目标标了 `remap = false`（普通 mod 类不参与混淆重映射）。

## 两点要跟你确认

1. **音效默认值可能偏激进**。引擎音效在车包里声明的是 16~24 格，默认 `20` 区块 = 320 格，是 20 倍的跳变。照 MachineMax 取的默认值，改小改成都是一行的事。
2. **`allPartsAttackable` 默认 `false`**（你没指定默认值，我按"不改原行为"取的）。

## 验证情况

`:forge-compat:build` 通过，`cbramts.mixins.json`、refmap、三个 mixin class 都进了 jar，refmap 里 `clientTrackingRange → f_20542_` 映射正常生成。我还把**全部注入目标对着实际编译依赖的 MTS jar（build 7926604）用 javap 核过**：`performLegacyCompats(AJSONBase)`、`BoundingBox(JSONCollisionBox, JSONCollisionGroup)`、`JSONPart.engine`、`JSONRendering.sounds`、`JSONSound` 的三个距离字段都一致。

但**我没有实际跑过游戏** —— 这个 checkout 下没有 `run/` 目录，MTS 和 BattleRoyale 的 jar 都没装进去。所以注入能生效只是静态层面确认的，运行时行为（尤其是 mixin 是否真的挂在目标方法上）没验证。要我搭一套 run 环境（把 MTS / BattleRoyale / 官方车包装进 `run/mods`）实测一轮吗？
