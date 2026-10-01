# 

```
查一下D:\Temp-File\车包里的Black Ford Mustang的速度跟README example config里的两辆车的速度如何，用一个统一的标准列出他们在0.35carSpeedFactor下的最大km/h，并且需要补充它的example config，我手动拼完之后F3+I如下供参考：

/summon mts:builder_existing -444.50 10.42 972.50 {variables1: "body_pitch", variables2: "p_brake", part_6: {packID: "mtsofficialpack", variablescount: 1, part_active: 1.0d, temp: 8.47999736745412d, variables0: "part_active", systemName: "enginefordfe428", spawnedDefaultParts: 1b, subName: "", pressure: 65.98402525355547d, uniqueUUID: "f6355a9d-b050-4dba-ac65-acfa481eaf2d"}, instrument4_systemName: "instrument_car_clock", part_5: {packID: "mtsofficialpack", variablescount: 1, part_active: 1.0d, variables0: "part_active", systemName: "carseat", spawnedDefaultParts: 1b, subName: "_black", uniqueUUID: "1e16d1f4-4f82-4284-9881-b4e1733b1deb"}, variables0: "batteryCapacity", part_4: {packID: "mtsofficialpack", variablescount: 1, part_active: 1.0d, variables0: "part_active", systemName: "carseat", spawnedDefaultParts: 1b, subName: "_black", uniqueUUID: "af8db0ac-870e-4e95-bd09-a2b6bda012e2"}, part_9: {packID: "mtsofficialpack", variablescount: 3, variables1: "springae", part_active: 1.0d, variables2: "springpositionae", variables0: "part_active", systemName: "mirrorornament", springae: -5.337711190804839E-4d, spawnedDefaultParts: 1b, subName: "", springpositionae: 0.01542579010128975d, uniqueUUID: "5a5b3b0d-ba09-493c-8332-7504bf40a5a4"}, part_8: {packID: "mtsofficialpack", variablescount: 1, part_active: 1.0d, variables0: "part_active", systemName: "gloveboxscout", spawnedDefaultParts: 1b, subName: "", inventory: {Items: [], uniqueUUID: "c84d6322-ee81-49b6-8943-4e5d9fb01182"}, uniqueUUID: "8f5d3a56-1edc-4711-8841-a97668d2111a"}, Invulnerable: 0b, entityid: "EntityVehicleF_Physics", PortalCooldown: 0, part_3: {packID: "mtsofficialpack", variablescount: 1, part_active: 1.0d, variables0: "part_active", systemName: "wheellarge", spawnedDefaultParts: 1b, subName: "", uniqueUUID: "6ac81ead-6fef-471d-822d-f977da20f087"}, textnull: "(%f)", part_2: {packID: "mtsofficialpack", variablescount: 1, part_active: 1.0d, variables0: "part_active", systemName: "wheellarge", spawnedDefaultParts: 1b, subName: "", uniqueUUID: "c9348a45-6b97-4162-8b76-40b6618a072e"}, part_1: {packID: "mtsofficialpack", variablescount: 1, part_active: 1.0d, variables0: "part_active", systemName: "wheellarge", spawnedDefaultParts: 1b, subName: "", uniqueUUID: "149ceadc-5e44-4a85-bd9b-fc165e21a4ab"}, radio: {volume: 10, currentURL: "", uniqueUUID: "9b52c1c8-7170-4656-b8bc-81d9f9a52c79"}, "textLicense Plate": "M06U5", part_0: {packID: "mtsofficialpack", variablescount: 1, part_active: 1.0d, variables0: "part_active", systemName: "wheellarge", spawnedDefaultParts: 1b, subName: "", uniqueUUID: "e8889d5b-58ae-407a-9dc9-9368e85228ba"}, packID: "mtsofficialpack", FallDistance: 0.0f, serverDeltaMy: -0.14187499999999936d, systemName: "fordmustang69", batteryCapacity: 14.0d, uniqueUUID: "34518246-519a-4030-bcf0-a0d1f0a687b3", Motion: [0.0d, 0.0d, 0.0d], fuelTank: {currentFluidMod: "wildcard", currentFluid: "", uniqueUUID: "6a060113-74d6-4e85-9e28-903be14c2552"}, body_pitch: 3.0304480589488494E-41d, Air: 300s, OnGround: 0b, p_brake: 1.0d, Rotation: [0.0f, 0.0f], instrument4_packID: "mtsofficialpack", electricPower: 12.107377913636137d, positionx: -444.5d, positiony: 10.4175d, variablescount: 3, positionz: 972.5d, selectedBeaconName: "", spawnedDefaultParts: 1b, anglesy: 160.0904541015625d, subName: "_black", Fire: -1s}
```

# 

我先看一下项目结构和那个车包目录。

车包是个 jar，我需要解压看 `fordmustang69` 的配置。同时读 README 里的 example config。

车包 jar 其实在 `D:/Temp-File/` 下。先看项目里 `carSpeedFactor` 的定义和 jar 内 mustang 相关文件。

项目里已经有一份关于这个口径的文档，先读它。

文档里已有完整口径和 scout/merc 的数字。现在需要 mustang 的实际配置。

MTS 的 jsondefs 带注释，先看一下格式。

Heredoc 吃掉了反斜杠，改用 Write 写脚本。

`[00,0,0]` 这种前导零 MTS 能解析，Python 不能。修一下加载器。

三个车的参数都拿到了。现在按 memory 的规则，去实际 MTS jar 里核对这个速度公式，而不是只信文档。

找到 MTS 依赖 jar 了。现在反编译引擎/轮子物理，核对公式。

核对到真实公式了。现在确认 `engineTargetRPM` 的取值和阻力公式。

MTS 物理公式已核对（含 `0.6/30/1200` 常量与 `engineTargetRPM = throttle·(maxRPM−idleRPM)+idleRPM`）。现在读 Mustang 的实际部件表，准备 example config。

部件结构清楚了。现在确认 README 里 subName 列表的取值口径（是否等于 definitions 顺序），以及 addon 怎么用这些字段。

README 的 subName 顺序确实等于 definitions 顺序。再确认无翼车的阻力分支常数并跑一遍求解。

确认 `carseat` 是包里 generic type 为 seat 的部件，与召唤数据一致。再核对一下碰撞曲线公式，然后写配置。

公式链全部核对完毕。现在读 README 示例块，精确插入 Mustang 条目。

已核对完，README 也补好了。

## 统一口径

三种车全部按同一套稳态力平衡算的（数据取自 `MTS Official Pack-1.20.1-V29.jar`，物理取自依赖 jar `minecraft-transport-simulator-286703:7926604`，我 javap 核过字节码，不是照抄文档）：

```
0.5·ρ·v²·5·C_d  =  (maxRPM − rpm)/maxRPM · gear · axleRatio · fuelConsumption · 0.6 · 30
rpm             =  v · (1200/π) · gear · axleRatio
实际 km/h        =  v · 0.35 · 20 · 3.6
```

- `0.6` 和 `30.0` 来自 [PartEngine.addToForceOutput](PartEngine.class) 第 173/177 条常量
- 分子是 `engineTargetRPM`，满油门、零磨损时 `= maxRPM`
- 阻力走无翼分支 `0.5·airDensity·v²·5.0·C_d`（`crossSectionalArea`/`wingSpan` 都缺省为 0）
- ρ 取海平面 1.225；`carSpeedFactor` 0.35 × 车包 `packSpeedFactors` 1.0（V29 的 packdefinition 里没这项，就是 1.0）

## 结果

| 车 | 引擎 | 顶齿比 | 主减 | maxRPM | revlimit | fuelCons | C_d | **最高速** | 碰撞 ref |
|---|---|---|---|---|---|---|---|---|---|
| scout | engineamci4 | 0.7 | 3.55 | 7100 | 3780 | 0.5 | 0.5 | **74.8 km/h** | 59.4 |
| merc230 | enginemercedesm102 | 0.7 | 3.35 | 7100 | 5500 | 0.9 | 0.35 | **103.9 km/h** | 82.5 |
| **fordmustang69 (_black)** | **enginefordfe428** | **0.6** | **3.25** | **8500** | 6110 | **1.35** | **0.3** | **132.8 km/h** | **105.4** |

和前两个数字（75 / 104）跟你之前实测的刻度是对上的。Mustang 比 merc 高约 28 km/h。

几个会影响你实测结果的点：

- **低档位够不到平衡点**，被 revlimit 卡住。Mustang 的 3 档平衡点 6107 rpm，紧贴它的 6110 revlimit，只有 4 档（0.6）能跑到 132.8。所以它 3→4 换挡必须成功，跑不出 4 档就只有约 124。
- **Mustang 是手动挡**（引擎 json 里没有 `isAutomatic`，和三辆里另外两台不同），刷新出来的车不会自己升到 4 档。
- **空气密度随高度变**：`airDensity = 1.225 · 2^(−(y−seaLevel)·256/(500·maxHeight))`。你召唤点 y≈10 在海平面以下，密度约 1.30，那台车实测大概 **129 km/h** 而不是 133。整体误差我给 ±5%。

## README 已补的 example config

在 [README.md:243](README.md:243) 的三条 weight 列表末尾加了 `fordmustang69` 条目：

- `subName` 13 个，按车包 `definitions` 顺序（`_red … _police`）
- `parts` 按你 F3+I 的实际构件表：`part_0..3` wheellarge、`part_4/5` carseat `_black`、`part_6` enginefordfe428、`part_8` gloveboxscout、`part_9` mirrorornament
- `part_7`(sign_window)、`part_10`(grille)、`part_11`(steeringwheel)、`part_12`(dashornament) 在车包里没有 `defaultPart`，是空槽，所以和你召唤数据一样不写

JSON 三个块都验证过能解析。**weight 我暂填 10.0**（现有 35 / 15），这是你的平衡决策，要改直接改那个数。

## 顺带一个和你之前碰撞配置相关的数

按碰撞曲线 `damage = health · (ref − crashSpeedMin)/(crashSpeedMax − crashSpeedMin)`（[AEntityVehicleD_Moving](AEntityVehicleD_Moving.class) 里 `scaledVelocity = velocity × 20`），在 `vehicleDestructionDamageScale = 0.65` 下满速撞墙：

| 车 | min/max | 满速时扣血 | 结果 |
|---|---|---|---|
| scout | 10/55 | 0.71 条 | 存活 |
| merc230 | 10/55 | 1.05 条 | 扣满一条 |
| mustang | 10/65 | 1.13 条 | 扣满一条 |

注意 Mustang 的 `crashSpeedMax` 是 **65**（不是 55），曲线更缓：它要 **119 km/h** 才扣满一条，比另外两台的 100 km/h 高。所以 0.65 这个默认值对 Mustang 而言门槛是错位的 —— 如果你希望 Mustang 也按 100 km/h 扣满，得单独调它的 `crashSpeedMax` 或者接受这个差异。

要我把 Mustang 也纳入之前的 `vehicleDestructionDamageScale` 取舍讨论吗？

# 

```
加权用三个速度取倒数后乘以1000，保留一位小数，如13.4,9.6,7.5，概率高的写在example config前面
```

# 

验证一致：13.4 / 9.6 / 7.5。改 README 里的权重。

权重算好了，README 已更新。

## 加权

倒数 ×1000，保留一位小数：

| 顺序 | 车 | 最高速 | `1000 / speed` | weight | 概率 |
|---|---|---|---|---|---|
| 1 | scout | 74.8 km/h | 13.369 | **13.4** | 43.9% |
| 2 | merc230 | 103.9 km/h | 9.623 | **9.6** | 31.5% |
| 3 | fordmustang69 | 132.8 km/h | 7.531 | **7.5** | 24.6% |

跟你给的 13.4 / 9.6 / 7.5 完全对上。

概率从高到低正好也是速度从慢到快，跟原有的排列顺序一致，所以只是把三个数字换掉、Mustang 条目追加在最末尾，没有重排。

改动在 [README.md](README.md)：

- 第 161 行 `"weight": 35.0` → `13.4`
- 第 200 行 `"weight": 15.0` → `9.6`
- 第 241 行起追加 Mustang 条目，`"weight": 7.5`

三个 JSON 块重新验证过都能解析。

另外提醒一下：`git status` 里还有一个 `docs/deprecated/prompt/ocp example config.md`（104 行新增）不是我动的，是你那边新加的，我没碰。

# 

```
这三辆车的极速、100km/h扣的血，该载具最大速度时扣的血都列出来
```

# 

Mixin 读完了，伤害口径确认。算一下数值。

三辆车在同一口径下的伤害如下。伤害以**车包原始血量 150 为一条**为基准（`vehicleDestructionDamageScale` 就是按这个基准标定的）。

口径：`ref = km/h ÷ (0.35 × 3.6)`，`扣血 = 150 × (ref − min)/(max − min) × 0.65`

| 车 | 碰撞曲线 min/max | **极速** | **100 km/h 扣血** | **极速时扣血** |
|---|---|---|---|---|
| scout | 10 / 55 | 74.8 km/h | 150.3（1.00 条） | 107.0（0.71 条） |
| merc230 | 10 / 55 | 103.9 km/h | 150.3（1.00 条） | 157.0（1.05 条） |
| fordmustang69 | 10 / 65 | 132.8 km/h | 123.0（0.82 条） | 169.1（1.13 条） |

几点：

- **scout 和 merc230 的 100 km/h 扣血完全相同**，因为两台车的碰撞曲线是同一组 `10 / 55`，伤害只跟速度走，跟极速无关。
- **mustang 的曲线更缓**（`max` 是 65 不是 55），所以同样 100 km/h 只扣 123 而不是 150。要让它在 100 km/h 也扣满一条，得把 `crashSpeedMax` 改到 55，或者接受这个错位。
- 极速那一列，**只有 scout 扣不满一条**（0.71）；merc 的 1.05 和 mustang 的 1.13 都过线。

## 「扣满一条」的门槛（km/h）

`扣血 ≥ 一条` 即 `比值 ≥ 1/0.65 = 1.538`：

| 车 | 扣满一条需达到 |
|---|---|
| scout / merc230 | 99.8 km/h |
| fordmustang69 | 119.2 km/h |

这正好就是 0.65 这个默认值的来源——它让 scout/merc 的「扣满」线落在 100 km/h。

## 一个需要注意的交互

上面所有数字都是**相对原始血量 150**。但 MTS 里那个判断是拿缩放后的 `damage` 去比 `general.health`：

- `vehicleHealthScale = 1.0`（README 默认）：血量上限 150，扣满门槛就是上表的 99.8 / 119.2 km/h，能正常触发。
- `vehicleHealthScale = 10.0`（你之前测试用的）：血量上限 1500，而 mixin 已经把伤害除过 10，实际伤害还是 150 量级。要扣满需要比值 ≥ 15.4，**永远够不到** —— 也就是这条分支在高血量倍数下是死的，每次撞击只会扣 else 分支那个值（100 km/h 扣 150，正好十分之一个血条，撞 10 次报废）。

这跟你「拉高血量让车多抗几次满撞」的意图是一致的，但如果你本来指望 10 倍血量下还能靠一次高速撞击「扣满一条」来触发摧毁，那它不会发生，只会走 `crashSpeedDestroyed` 那条直接摧毁分支。要改的话是改 mixin 里那个除 `healthScale` 的动作，不是在配置层能调的。

这轮只读代码和算数，没动文件。
