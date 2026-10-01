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
