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

# 

```
加两个config：
vehicleCrashDestroySpeed：用来提高载具摧毁速度的下限，实际行驶速度要超过这个值才允许直接摧毁；由于是提高下限，设置成0的时候应该等价于关掉
vehicleDestructionDamageScale：默认值应该使载具实际运行速度达到100km/h的时候，扣除1.0血量上限的伤害，从而用cbramts手动拉高血量上限且没达到vehicleCrashDestroySpeed的时候，只扣约1.0倍血量上限的值

注意速度都取当前实际行驶速度，即需要考虑speedFactor；以及cbramts改过血量上限，计算的时候也要注意
版本已经限制在仅24.0.0，不需要专门写对源码仓库25.0.0也适用的写法，有更合适的字段优先取更合适的
```

# 

我先看下磁盘上改动过的文件，再研究 MTS 的碰撞摧毁代码。

现在研究 MTS 的碰撞摧毁逻辑。

MTS 的碰撞摧毁代码找到了（`AEntityVehicleD_Moving.correctCollidingMovement`）。我把速度和伤害的默认参数查清楚。

速度单位需要落实。我查一下车包里速度表针的标定。

速度单位我要落实一下：车包的 KPH 速度表针是 `axis: [0,0,5.4], offset: -120`。按 `angle = 5.4 × v − 120`，满偏 240° 对应 v=44.4 —— 正好是 160 km/h ÷ 3.6。核对一下 MPH 表的比例。

确认了：MTS 的 ROTATION 动画里 **axis 的长度就是旋转角度**，KPH 表满偏 240° 对应 `speed`=44.4 —— 正好 160 km/h ÷ 3.6（MPH 表对应 120 mph）。所以 **km/h = `speed` × 3.6**，而碰撞代码用的 `scaledVelocity` 等于实际速度 ÷ `speedFactor`。

我看一下官方车包是否都定义了 `crashSpeedMax`，这决定要不要处理硬度分支。

MTS 的关键字段都确认了。`EntityVehicleF_Physics.indicatedSpeed` 是 public 字段（= `axialVelocity × speedFactor × 20`，m/s），正是速度表读的那个值 —— 比在碰撞代码里自己拼速度更合适。

（顺带一个数据点：`ft17` 坦克这类车**没有** `crashSpeedMax`，走的是另一条"硬度分支"，不受 `crashSpeedDestroyed` 约束。）

实现前有三个点要你定。

明白。第 3 点你选了运动矢量口径，我先把位置积分确认一下，好把单位算准。

位置积分确认：实际位移是 `motion × speedFactor`，所以**实际速度(m/s) = |motion| × speedFactor × 20**，km/h 再 ×3.6。我看一下车包里各车的 crashSpeed 区间，好定 `vehicleDestructionDamageScale` 的默认值。

三点都清楚了。实现前我再确认几个字段/调用点的细节。

三个字段都在实际 jar 里核对通过（`VehicleMotorized` 是 public，`crashSpeedDestroyed` 是 float，`speedFactor` 是父类 public final double）。开始写。

`AEntityVehicleD_Moving` 是包私有类，不能写类字面量。Mixin 支持用 `targets` 字符串指定。

AP 警告说找不到 `@Shadow` 目标 —— 因为字段是**继承自父类**的，静态检查只看目标类本身。运行时虽可能解析成功，但我没法实测，改用更确定的 `@Accessor` 挂在字段的声明类上。

零警告通过。检查产物。

构建通过、零警告。两个新配置加好了。

## 新增配置

| 键 | 类型 | 默认 | 作用 |
|---|---|---|---|
| `vehicleCrashDestroySpeed` | double（km/h） | `0`（不提高） | 抬高"一撞即毁"的速度下限 |
| `vehicleDestructionDamageScale` | double | `1.0` | 碰撞摧毁伤害系数 |

## 实现

[Mixin 类](core/src/main/java/xiao/cbra/mts/mixin/AEntityVehicleD_MovingMixin.java) 挂在 `correctCollidingMovement` 上：

- **摧毁下限** — `@Redirect` 读 `crashSpeedDestroyed` 那一处，返回 `max(车包值, 配置值换算回来的速度)`。配置 ≤ 0 时**原样返回车包值**，所以 0 是严格的关掉而不是"取 max(包值, 0)"。只经过这条分支，ft17 那类没定义 `crashSpeedMax` 的硬度分支不碰。
- **伤害系数** — `@ModifyArg` 改 `Damage` 构造的第一个参数，两条出伤路径（扣满一条血量的、按曲线扣部分的）都覆盖。

速度换算的关键：载具每 tick 实际位移是 `motion × speedFactor`，所以实际速度 = `velocity × speedFactor × 20`（m/s），km/h 再 × 3.6；而 MTS 碰撞代码比较用的 `scaledVelocity = velocity × 20` **不含** `speedFactor`，两者差一个 `speedFactor`。所以配置的 km/h 门槛换算回去要除以 `speedFactor × 3.6`。[AEntityVehicleC_CollidingAccessor](core/src/main/java/xiao/cbra/mts/mixin/AEntityVehicleC_CollidingAccessor.java) 就是为了取到这个 `speedFactor`。

## 两个实现细节

**`AEntityVehicleD_Moving` 是包私有的**，不能写 `@Mixin(X.class)`，改用了 `targets = "..."` 字符串形式。

**`speedFactor` 我一开始用 `@Shadow`，但 Mixin AP 报了 "Cannot find target for @Shadow field"** —— 因为该字段声明在父类 `AEntityVehicleC_Colliding` 上，静态检查只扫目标类本身。运行时虽可能解析成功，但我没法实测，所以换成在**声明类**上开 `@Accessor`，这是确定的写法。

## 关于默认值 1.0

我按官方车包 16 辆车核对过：在 100km/h 实际速度、默认 `speedFactor=0.35` 下，车包曲线算出的伤害/血量上限比值为 —— 14 辆 ≥ 1.0（scout/merc 1.54、quad 1.70、skyhawk 1.49…），trimotor 0.93、gmcbrig 0.99。所以默认 1.0 确实让"100km/h 就扣满一整条"，你原话里的"约"正是 trimotor 这两辆带来的。

**一个需要你知道的边界**：`attack()` 会把单次伤害钳到血量上限，所以系数 >1 在"已经扣满"的区间看不出效果（本来就封顶了），它的作用体现在没到阈值的区间。系数 <1 则连"扣满一条"的那次也会等比例变小。

## 验证情况

`:forge-compat:build` 通过、Mixin AP **零警告**，refmap 只含 `EntityType` 那条（MTS 的都标了 `remap = false`），5 个 mixin class 都进了 jar。注入目标全部对着实际编译依赖的 MTS jar（24.0.0 / build 7926604）用 `javap` 核过：`correctCollidingMovement()` 存在且方法内恰好两处 `new Damage(...)`、`crashSpeedDestroyed` 是 `float`、`velocity`/`speedFactor` 都是 public。

**仍然没有实际跑过游戏** —— 这个 checkout 下没有 `run/` 环境。要我搭一套（MTS + BattleRoyale + 官方车包装进 `run/mods`）实测这轮的碰撞行为吗？

# 

```
现在的有问题：
我的测试配置放在D:\Temp-File\下了
目前测试Black Mercedes和Black Scout都在最大速度下撞墙直接炸了
- 检查在当前配置下这两辆车实际最大速度是多少km/h？
- 注意cbramts是改过载具最大血量的，我改成了10.0倍（1500），检查伤害计算中是否取了修改过的血量，我需要以1.0倍血量伤害为准
- vehicleCrashDestroySpeed需要是对应载具实体实际运行速度，检查MTS里是否只取了没乘过carSpeedFactor的值

实际运行速度100km/h扣约1.0倍血量上限以Black Mercedes和Black Scout这两辆测试车辆为准，以此确定vehicleDestructionDamageScale默认值
```

# 

先看你放在 D:\Temp-File\ 的测试配置。

核心问题在引擎侧。我看下 `revlimitRPM`（AMC 是 3780，但 `maxRPM` 是 7100）怎么用的 —— 这直接决定最高速。

这一个很关键 —— 我先看 `damage` 这个局部变量在实际 jar 里是否带调试名。

找到关键点了。先确认几处物理细节，再回答速度问题。

`@ModifyVariable` 支持 `name`，且 LVT 里有 `damage`。先确认地面部件集体没有额外的限速。

物理模型确认完了（ground device 只是把 `motion` 拉向轮速，稳态仍是"引擎力 = 阻力"）。我把两个车的最高速算出来。

数字出来了。现在改实现 —— 关键发现：**我原来的 `@ModifyArg` 写法达不到你要的效果**。

原因：MTS 的判定是 `if (damage >= health)`，而"扣满一条"那条分支传给 `Damage` 的是 `definition.general.health`（不是 `damage`）。所以只缩放构造参数的话，100km/h 处得到的永远是 `health × 系数`，把系数调到 1.0 才能得到 1.0 倍 —— 但那正是现在会炸的配置。系数必须乘在 **`damage` 局部变量** 上，让"是否扣满"的判定阈值一起移动。

三个问题都查清了，并且发现并修掉了一个实现错误。

## 1. 这两辆车在当前配置下的最高速

用稳态力平衡算的：MTS 里 `motion.addScaled(totalForce, 1/mass)`，所以**质量不影响最高速**，只影响加速。平衡式是

```
0.5·ρ·v²·5·dragCoeff  =  (maxRPM − rpm)/maxRPM · gear · axle · fuelConsumption · 0.6 · 30
rpm = v · 1200 · gear · axle / π
实际 km/h = v · speedFactor · 20 · 3.6
```
（`carSpeedFactor` 0.35 × `packSpeedFactors["mtsofficialpack"]` 1.0 = **0.35**；轮子 `wheellarge` 高度 1.0；两辆车都没配 `crossSectionalArea`/`wingSpan`，走的是 `0.5ρv²·5·dragCoeff` 那条阻力公式。）

| 车 | 顶齿比 | 主减 | fuelConsumption | dragCoeff | **最高速** | 碰撞用的 ref |
|---|---|---|---|---|---|---|
| scout（engineamci4） | 0.7 | 3.55 | 0.5 | 0.5 | **≈75 km/h** | 59.4 |
| merc230（mercedesm102） | 0.7 | 3.35 | 0.9 | 0.35 | **≈104 km/h** | 82.5 |

这是解析估算，不是实测，误差我估在 ±10% 左右。

对得上你的现象：**车包的 `crashSpeedDestroyed` 是 60**（≈75.6 km/h），scout 的 59.4 刚好卡在下面、merc 的 82.5 明显超过 —— 但两辆都会"炸"，是因为下面第 2 点。

## 2. 伤害用的确实是改过的血量（这正是会炸的原因）

`correctCollidingMovement` 里的 `definition.general.health` **就是** cbramts 改成 10 倍后的 1500 —— 我们在包加载期改的是被所有实例共享的那个定义对象，实体创建时早就是 1500 了。

所以 `damage = 1500 × 曲线比例`。scout 在最高速时比例是 **1.097**，即 1645 ≥ 1500 → 走"扣满一条"分支 → `outOfHealth` → 报废。血量拉高 10 倍并没有救它，因为伤害是**按血量上限等比例放大的**。

你要的"1.0 倍血量上限"在 100km/h 处是能精确拿到的：命中该分支时传下去的就是 `definition.general.health` 本身，也就是恰好 1500 = 1.0×。

## 3. 速度口径：MTS 确实没乘 carSpeedFactor

确认了 —— `AEntityB_Existing:177` 是 `velocity = motion.length()`，而碰撞代码用 `scaledVelocity = velocity * 20`。但载具每 tick 实际位移是 `motion × speedFactor`（`position.add(motionApplied)`，`motionApplied = motion.scale(speedFactor)`）。所以：

```
实际速度(m/s) = scaledVelocity × speedFactor
配置(km/h) 换算回 scaledVelocity 单位 → ÷ (speedFactor × 3.6)
```
我之前就是这么写的，这次重新核过，是对的。

## 我改了什么

**原来的 `@ModifyArg` 写法达不到你的要求，已换成 `@ModifyVariable`。**

原因：MTS 随后要用 `if (damage >= 血量上限)` 判断这次撞击算不算"扣满一条"，而那条分支传给 `Damage` 的是 `血量上限` 而不是 `damage`。所以只缩放构造参数的话，分支的产物恒为 `血量上限 × 系数` —— 系数调到 1.0 才是 1.0 倍，而那正是现在会炸的配置；系数调小则变成一个与速度无关的固定值。

现在改成乘在 **`damage` 局部变量**上（`@ModifyVariable(at=@At("STORE"), name="damage")`，实际 jar 的 LocalVariableTable 里有这个名字），"扣满一条"的门槛就跟着系数一起移动了。

**默认值改成 `0.65`**，来源：两辆车都是 `crashSpeedMin` 10 / `crashSpeedMax` 55，100km/h 对应 ref = 100/(0.35×3.6) = 79.365，原配方比例 (79.365−10)/45 = **1.5414**，取倒数 **0.6487**。乘 0.65 后"扣满一条"的门槛正好落在实际行驶 100km/h。

改完的效果（按上面的估算）：

| | 原配置（系数 1.0） | 系数 0.65 |
|---|---|---|
| scout 最高速 75km/h | 1.10 条 → 报废 | **0.71 条 → 存活** |
| merc 最高速 104km/h | 1.61 条 → 摧毁 | 1.05 条 → **仍会摧毁** |

## 一个要你定的取舍

merc 的最高速（≈104）**超过**了你定的 100km/h 基准线，所以 0.65 下它满速撞墙依然扣满一条。两条路：

- **A（已实现）**：按你的原话取 `0.65`，merc 满速撞击靠 `vehicleCrashDestroySpeed` 拦住"直接摧毁"，但仍会扣满一条报废。
- **B**：取 `0.6`，那么 100km/h 扣 0.92 条、merc 满速扣 0.97 条 —— **两辆车满速撞击都能活下来**，代价是 100km/h 不再是精确的 1.0 倍。

你要哪条？另外 `vehicleCrashDestroySpeed` 目前是 `0`（不提高），要让 merc 不被"直接摧毁"需要填到 ≈110 以上。

改动已编译通过（`:forge-compat:build`，零警告），5 个 mixin class 和 refmap 都在 jar 里。仍然**没有实测过**——这次的最高速是解析估算，尤其是 merc 那条贴着 100km/h 的线，建议你实测确认一下它的仪表盘极速。

# 

```
如果最高速超过100km/h，那么vehicleCrashDestroySpeed默认值给拉到150km/h来解决，允许104km/h扣的血高于150，但不能直接满血（缩放过后载具实际最大生命为1500）给秒杀了
允许在100km/h扣的不是精确的1.0倍（150），只要让这两辆车在这个速度下扣血量在150左右即可，以此调整vehicleDestructionDamageScale默认值
注意计算载具血量的时候是否是用vehicleHealthScale修改过的值，我加这个值就是为了使在100km/h的时候只扣约1.0倍的血（150）左右），从而能多抗几次满撞
```

# 

三点都查清了，其中第 2 点正是问题根源。

构建通过。用 ASCII 输出重跑一下数值核对。

三个问题都确认了，第 2 点正是根因，已修。

## 2. 伤害确实用的是 vehicleHealthScale 改过的血量 —— 这就是拉高血量没用的原因

`correctCollidingMovement` 里算的是 `definition.general.health × 曲线比例`，而这个 `definition.general.health` **就是** cbramts 改成 1500 的那个值（改的是包加载期共享的定义对象）。所以伤害跟着血量一起放大了 10 倍，撞几次照样报废 —— 你说的"我加这个值就是为了多抗几次满撞"没有生效，根因在这。

**已修**：mixin 现在会先除掉 `vehicleHealthScale` 再乘系数，让撞击伤害回到"以车包原始血量为基准"（原始 150）。

## 1. 两辆车的最高速（解析估算）

| 车 | 最高速 | 碰撞用的 scaledVelocity |
|---|---|---|
| scout（engineamci4） | **≈75 km/h** | 59.4 |
| merc230（mercedesm102） | **≈104 km/h** | 82.5 |

`vehicleCrashDestroySpeed` 已按你说的默认拉到 **150**，盖过两者（150km/h 对应 scaledVelocity 119.05 > 82.5）。

## 3. 速度口径确认无误

`AEntityB_Existing:177` 是 `velocity = motion.length()`，碰撞代码用 `scaledVelocity = velocity * 20` —— **不含** speedFactor，而载具每 tick 实际位移是 `motion × speedFactor`。所以换算就是 `÷ (speedFactor × 3.6)`，redirect 里就是这么写的（150km/h → 119.05，与上面一致）。

## 新默认值下的结果

`vehicleDestructionDamageScale = 0.65`（= 100km/h 处曲线比例 1.5414 的倒数）

| 速度 | MTS 原伤害 | 修后伤害 | 占 1500 的比例 |
|---|---|---|---|
| 75 km/h（scout 极速） | 1651 | **107** | 7.2% |
| 100 km/h | 2312 | **150** | 10.0% |
| 104 km/h（merc 极速） | 2418 | **157** | 10.5% |
| 150 km/h | 3635 | 236 | 15.8% |
| 想扣满 1500 需要 | — | — | **≈885 km/h**（不可能） |

**100km/h 刚好扣 150**（原始血量 1.0 倍），104km/h 扣 157 —— 符合你说的"允许高于 150 但不能满血秒杀"。**能扛的撞击次数正好等于 `vehicleHealthScale`**：现在填 10 就能扛 10 次满速撞击；以后想更耐撞直接调 vehicleHealthScale 就行，撞击伤害不受它影响。

`:forge-compat:build` 通过、零警告。

## 一点提醒

最高速那列是解析估算（稳态力平衡，`0.5ρv²·5·dragCoeff = (maxRPM−rpm)/maxRPM·gear·axle·fuelConsumption·0.6·30`），不是实测。scout 的 59.4 卡在 `crashSpeedDestroyed` 60 的下方、merc 的 82.5 在上方，这一点和你观察到的"两辆都会炸"是对上的，所以模型应该没错；但 merc 那条 104km/h 值得你实测确认一下仪表盘读数。

# 

```
还是不对
我用scout撞一次剩83/1500血，用mercedes撞直接炸
再检查，可以加log排查问题
```

# 

数值对不上我预期的 107，说明有地方没按我设想走。我先查几个关键前提，再加日志。

HUD 是 `health - damageVar.currentValue`，所以 83/1500 意味着**累计吃了 1417 伤害**，而我预期的是 107。数字对不上，说明要么 mixin 没生效，要么伤害来自别处。按你说的加日志排查。

网络解析失败（之前也遇到过）。用 `--offline` 重试。

日志加好了，构建通过并确认进了 jar。先说为什么现在的数字对不上。

## 83/1500 反推不出任何"我的代码生效"的情形

HUD 那行是 `health - damageVar.currentValue`，所以 83/1500 = **单次吃了 1417 伤害**。而我这套代码在撞击分支里应该是：

| 假设 | 需要 scout 撞击时达到的速度 |
|---|---|
| 伤害系数完全没生效（×1.0） | ~66 km/h |
| 只乘了 0.65、`/vehicleHealthScale` 没生效 | ~95 km/h |
| 按我写的 `× 0.65 / 10` | ~836 km/h（不可能） |

也就是说：**要么这个 mixin 根本没起作用，要么这 1417 不是（只）来自撞击分支。** 另外 merc 被"直接炸"，也符合"提高摧毁速度下限的 redirect 没生效"（它的 scaledVelocity 82.5 > 车包原值 60）。

## 加了三条日志

新构建的 jar 里：

1. `[cbramts-debug] config loaded: healthScale=... destructionDamageScale=... crashDestroySpeed=...` —— 确认模组读到的配置值。
2. `[cbramts-debug] health mtsofficialpack scout 150 -> 1500` —— 确认包加载期血量确实被改了（只对载具打）。
3. 撞击路径两条：
    - `[cbramts-debug] crashDamage on ...: raw=... damageScale=... healthScale=... -> ...` —— **这条不出现就说明伤害 mixin 没生效**
    - `[cbramts-debug] destroySpeed on ...: pack=... config=...km/h speedFactor=... ref=... effective=...`
4. 新增 [AEntityE_InteractableMixin](core/src/main/java/xiao/cbra/mts/mixin/AEntityE_InteractableMixin.java)：`[cbramts-debug] attack on <实体> amount=... current=...` —— **记录每一次伤害施加**，用来抓 1417 的真实来源（是不是撞击、有没有别的东西在扣血）。

请重新构建（网络抽风就加 `--offline`）后各撞一次，把日志里这些行发我。重点看两件事：

- **有没有 `crashDamage on ...` 这行**。没有 → mixin 没挂上，问题在注入点。
- **`attack on ... amount=?`** 是撞击那一下的真实伤害值。

顺便在日志里搜一下 `Mixin` / `cbramts` 有没有报错（比如 `InvalidInjectionException`、`could not find` 之类）。

## 我现在最怀疑的点

`@ModifyVariable(name = "damage")` 依赖 MTS 的 **LocalVariableTable**。我在 Curse 上的 build 7926604（就是 `gradle.properties` 里锁的那个）里确认过 `damage` 这个名字存在，但你实际 `mods/` 里跑的 MTS jar 如果是别的构建，这张表可能没有 —— 那样按名字找局部变量就会失败。

所以麻烦你顺便告诉我 **你测试用的 MTS jar 具体是哪个版本/构建号**。如果确认是注入点的问题，我就换成按槽位（这个构建里是 slot 12）或者改成不依赖局部变量表的写法。

另外这次的 mixin 列表里多了 `AEntityE_InteractableMixin`，全部六个 class 都已确认在 jar 内。

# 

```
这次测试两辆车都直接炸了，日志在D:\Temp-File\debug.log
```

# 

日志直接给出了答案 —— 关键在这儿：

```
crashDamage on EntityVehicleF_Physics: raw=1507.51 damageScale=0.65 healthScale=10.0 -> 97.99
attack on EntityVehicleF_Physics@f932470 amount=1507.51 current=0.0 explosion=false
```

我的 mixin **执行了**，算出了 97.99，但 `attack()` 收到的却是**没缩放的 1507.51**。而且日志里没有任何 `destroySpeed` 行 —— 说明 `if (damage >= 血量上限)` 判断用的是缩放后的值（97.99 < 1500 → 走 else 分支），**但 else 分支里 `new Damage(damage, ...)` 用的却是原始值**。同一个局部变量，两处读到的值不一样。我看一下字节码。

字节码是决定性的。让我把它打印出来看清楚。

字节码把问题彻底暴露了 —— **实际跑的 MTS jar 里，else 分支的伤害是「就地重算」的，不是读那个局部变量**：

```
466: dload 12                    // 比较用的是局部变量 damage
...
512: new Damage ... health      // 扣满分支
544: new Damage ... health        // else 分支：重新算了一遍
     563: dload 10 (scaledVelocity) ... ddiv  // 不读 slot 12
```

`@ModifyVariable` 只改了比较用的那份，`attack()` 拿到的是重算出来的原始值 —— 这正是日志里 `raw=1507.51 -> 97.99` 但 `attack amount=1507.51` 的原因。我读的源码仓库和实际编译的 jar 版本在这个分支上写法不同。

补一个 `@ModifyArg`，把 else 分支那个重算出来的参数也乘上系数。

确认方法内恰好两处 `Damage.<init>`（偏移 535 = 扣满分支，614 = else 分支），`ordinal = 1` 正好命中后者。编译。

构建通过。

## 根因确认

日志里的两个数字完全对上了：

```
crashDamage on EntityVehicleF_Physics: raw=1507.51 ... -> 97.99
attack on EntityVehicleF_Physics@f932470 amount=1507.51 current=0.0
```

**实际编译的 MTS 24.0.0 jar 里，else 分支的伤害是就地重算的**，不读那个局部变量：

```
466: dload 12                              ← 比较用的局部变量
535: Damage.<init>(...health...)           ← 扣满分支，传血量上限
614: Damage.<init>(...重算的表达式...)      ← else 分支，不读 slot 12
```

所以 `@ModifyVariable` 只改了"判断用的那份"（97.99 < 1500 → 走 else ✓），而 `attack()` 拿到的是重算出来的原始值 1507.51。它 ≥ 1500，被 `attack()` 统一钳到 1500 → 血量归零 → 两辆车都炸。日志里没有 `destroySpeed` 行也印证了：扣满分支根本没进。

顺带说明一个我之前踩的坑：**我读的 GitHub 源码工作副本和实际编译的 24.0.0 jar 在这个方法上写法不同**（源码里 else 分支是 `new Damage(damage, ...)`，jar 里是重算）。以后这类注入我都直接对 jar 的字节码核。

## 修法

在原来的 `@ModifyVariable` 之外，补一个 `@ModifyArg` 专门改 else 分支那次 `Damage` 构造（方法内第 2 个，字节码偏移 614，已核对全方法只有这两处）：

```java
@ModifyArg(method = "correctCollidingDirections", ... ordinal = 1, index = 0)
```

只改 else 那次、不动扣满分支 —— 扣满分支传的是血量上限本身，缩放它会让"扣满一条"不再是一条。

## 按你日志里的实际撞击速度推算（修复后）

| 日志那次撞击 | 原伤害 | 修复后 |
|---|---|---|
| 69.6 km/h（raw 1507.5） | 1507 → 被钳成 1500，直接炸 | **98** |
| 89.2 km/h（raw 2028.2） | 2028 → 被钳成 1500，直接炸 | **132** |
| 100 km/h（参考点） | — | **150** |

1500 血下分别能扛 15 次 / 11 次这种撞击。

请重新构建再撞一次，日志里应该出现成对的：

```
[cbramts-debug] crashDamage on ...: raw=1507.5 ... -> 98.0
[cbramts-debug] crashDamageArg on ...: raw=1507.5 -> 98.0
[cbramts-debug] attack on ... amount=98.0 current=0.0
```

`amount` 从 1507 变成 ~100 就对了。

## 一个要提醒你的副作用

伤害缩小之后，`damage >= 血量上限` 这个"扣满一条"的分支在任何现实速度下都不会进入了（需要 ~886km/h），而 MTS 的"直接摧毁"是**嵌在这个分支里面**的。所以 **`vehicleCrashDestroySpeed` 现在实际上不会生效了** —— 撞击只会掉血，永远不会直接摧毁。

如果你希望"高速撞击仍然能摧毁、只是门槛由 vehicleCrashDestroySpeed 决定"，那得把摧毁判断从那个分支里挪出来，这是另一个改法，你要的话我加。
