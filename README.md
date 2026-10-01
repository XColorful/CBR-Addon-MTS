# 自定义大逃杀扩展：沉浸车辆 | CBR Addon: Immersive Vehicles
  
[中文](#自定义大逃杀扩展沉浸车辆) | [English](#cbr-addon-immersive-vehicles)

# 自定义大逃杀扩展：沉浸车辆

本模组是[自定义大逃杀](https://github.com/XColorful/BattleRoyale)的扩展模组。
- 提供[沉浸车辆](https://github.com/DonBruce64/MinecraftTransportSimulator)扩展功能

`该模组需要安装在服务端`

## 使用说明

### 前置模组

- 自定义大逃杀
    - [CurseForge](https://www.curseforge.com/minecraft/mc-mods/custom-battleroyale) | [Modrinth](https://modrinth.com/mod/custom-battleroyale) | [Github Releases](https://github.com/XColorful/BattleRoyale/releases)
- 沉浸车辆
    - [CurseForge](https://www.curseforge.com/minecraft/mc-mods/minecraft-transport-simulator) | [Modrinth](https://modrinth.com/mod/immersive-vehicles)

### 主要特色

添加[通用事件刷新](https://github.com/XColorful/BattleRoyale/wiki/General-loot-config#通用事件刷新)协议：

- packID：载具包ID
- systemName：载具系统名
- subName：载具子定义名，可取字符串或字符串列表；给列表则每次刷新随机取一个，留空则用空 subName
- parts：可选，按槽位指定部件，键为 part_序号，值为该部件的NBT字符串，留空则用包里的默认部件
- fuel：可选，燃料流体名；留空则按引擎的燃料类型自动挑选效力最高且实际存在的流体
- fuelQty：可选，燃料量；留空则按载具配置加满
- electricPower：可选，电池电量；留空则按载具配置充满

```json
{
	"lootType": "event",
	"protocol": "cbramts:0.5.6",
	"jsonTag": {
		"packID": "mtsofficialpack",
		"systemName": "scout",
		"subName": "_black",
		"parts": {
			"part_0": "{packID:\"mtsofficialpack\",systemName:\"wheellarge\",subName:\"\"}"
		}
	}
}
```

### 配置文件

配置文件为 `config/cbramts.json`，服务端与客户端各自读取本地的文件。

- vehicleHealthScale：全载具生命上限的缩放比例，默认 `1.0`；载具包原本为 150 时，设为 `10.0` 即为 1500。同时作用于载具本体与部件，值为 0（无限血量）的定义不受影响
- allPartsAttackable：是否让所有碰撞盒都带上 `ATTACK` 类型，默认 `false`；开启后原本只标注了其他类型、被子弹命中却不掉血的部件也会正常出伤
- modifyRenderDistance：是否修改载具的可见范围，默认 `true`
- renderDistance：载具的可见范围，单位为区块，默认 `32`（MTS 原值）
- modifyVehicleSoundDistance：是否修改引擎音效的传播距离，默认 `true`
- vehicleSoundDistance：引擎音效的传播距离，单位为区块，默认 `20`；只作用于引擎部件，衰减曲线的形状按比例保留
- vehicleDropOnDestroy：载具被摧毁时是否掉落部件（轮胎、引擎等）与已装仪器，默认 `true`；置 `false` 后载具只做爆炸和消失，不再散落物品
- vehicleCrashDestroySpeed：允许直接摧毁载具的速度下限，单位为 km/h，默认 `150`。只作用于定义了 `crashSpeedMax` 的车，走硬度分支的车（如 ft17）不受影响
- vehicleDestructionDamageScale：碰撞摧毁伤害的系数，默认 `0.65`，以车包原始血量为基准

生命上限与引擎音效距离在车包加载时改写，改动后需重进世界或重载资源包。

## 内容披露

### 衍生内容

- [自定义大逃杀](https://github.com/XColorful/BattleRoyale)：本模组是采用 [GPL-3.0-or-later](https://www.gnu.org/licenses/gpl-3.0.txt) 许可证的[自定义大逃杀](https://github.com/XColorful/BattleRoyale)的扩展模组

## 许可证

- 代码：[GPL-3.0-or-later](https://www.gnu.org/licenses/gpl-3.0.txt)

# CBR Addon: Immersive Vehicles

This mod is an addon mod for [Custom BattleRoyale](https://github.com/XColorful/BattleRoyale).
- Provides extended features of [Immersive Vehicles](https://github.com/DonBruce64/MinecraftTransportSimulator)

---

`This mod needs to be installed on the server.`

## Usage Instructions

### Prerequisites

- Custom BattleRoyale
    - [CurseForge](https://www.curseforge.com/minecraft/mc-mods/custom-battleroyale) | [Modrinth](https://modrinth.com/mod/custom-battleroyale) | [Github Releases](https://github.com/XColorful/BattleRoyale/releases)
- Immersive Vehicles
    - [CurseForge](https://www.curseforge.com/minecraft/mc-mods/minecraft-transport-simulator) | [Modrinth](https://modrinth.com/mod/immersive-vehicles)

### Main Features

Add [Common event loot](https://github.com/XColorful/BattleRoyale/wiki/General-loot-config#common-event-loot) protocol:

- packID: Vehicle pack ID
- systemName: Vehicle system name
- subName: Vehicle sub-definition name, either a string or a list of strings; a list is picked from at random on each spawn, empty means the empty subName
- parts: Optional, parts for specific slots, keyed by part_<index>, each value an NBT string; empty slots use the pack's default part
- fuel: Optional, fuel fluid name; if omitted, the most potent available fluid for the engine's fuel type is used
- fuelQty: Optional, fuel amount; if omitted, filled to the vehicle's capacity
- electricPower: Optional, battery charge; if omitted, filled to the vehicle's capacity

```json
{
	"lootType": "event",
	"protocol": "cbramts:0.5.6",
	"jsonTag": {
		"packID": "mtsofficialpack",
		"systemName": "scout",
		"subName": "_black",
		"parts": {
			"part_0": "{packID:\"mtsofficialpack\",systemName:\"wheellarge\",subName:\"\"}"
		}
	}
}
```

### Config file

The config file is `config/cbramts.json`, read locally by the server and each client.

- vehicleHealthScale: Scale of every vehicle's max health, default `1.0`; a pack value of 150 becomes 1500 at `10.0`. Applies to vehicles and their parts alike; definitions with a value of 0 (infinite health) are left alone
- allPartsAttackable: Whether every collision box should carry the `ATTACK` type, default `false`; parts previously tagged with other types only (hit by bullets but taking no damage) will take damage normally
- modifyRenderDistance: Whether to modify the vehicle render distance, default `true`
- renderDistance: Vehicle render distance, in chunks, default `32` (the MTS value)
- modifyVehicleSoundDistance: Whether to modify the engine sound travel distance, default `true`
- vehicleSoundDistance: Engine sound travel distance, in chunks, default `20`; applies to engine parts only, the attenuation curve shape is preserved
- vehicleDropOnDestroy: Whether a destroyed vehicle drops its parts (tires, engines, ...) and installed instruments, default `true`; when `false` the vehicle only explodes and vanishes, leaving nothing behind
- vehicleCrashDestroySpeed: Speed floor for direct vehicle destruction, in km/h, default `150`. Applies to vehicles that define `crashSpeedMax` only, so vehicles on the hardness branch (e.g. ft17) are unaffected
- vehicleDestructionDamageScale: Multiplier for crash destruction damage, default `0.65`, relative to the pack's original health.

Max health and engine sound distance are rewritten while vehicle packs load, so a world re-entry or resource reload is needed after changing them.

## Content disclosures

### Derivative content

- [Custom BattleRoyale](https://github.com/XColorful/BattleRoyale): This mod is an addon mod for [Custom BattleRoyale](https://github.com/XColorful/BattleRoyale), licensed under [GPL-3.0-or-later](https://www.gnu.org/licenses/gpl-3.0.txt)

## License

- Code: [GPL-3.0-or-later](https://www.gnu.org/licenses/gpl-3.0.txt)

# Example config

```json
[
  {
    "lootId": 0,
    "default": true,
    "name": "Immersive vehicles",
    "color": "#FFFFFFAA",
    "entry": {
      "lootType": "random",
      "chance": 0.8,
      "entry": {
        "lootType": "weight",
        "entries": [
          {
            "weight": 35.0,
            "entry": {
              "lootType": "event",
              "protocol": "cbramts:0.5.6",
              "jsonTag": {
                "packID": "mtsofficialpack",
                "systemName": "scout",
                "subName": [
                  "_blue",
                  "_yellow",
                  "_white",
                  "_red",
                  "_olive",
                  "_black",
                  "_gray",
                  "_gold",
                  "_tan",
                  "_orange",
                  "_brown",
                  "_yellowwhite",
                  "_seagreen",
                  "_maroon"
                ],
                "parts": {
                  "part_0": "{packID:\"mtsofficialpack\",systemName:\"wheellarge\",subName:\"\"}",
                  "part_1": "{packID:\"mtsofficialpack\",systemName:\"wheellarge\",subName:\"\"}",
                  "part_2": "{packID:\"mtsofficialpack\",systemName:\"wheellarge\",subName:\"\"}",
                  "part_3": "{packID:\"mtsofficialpack\",systemName:\"wheellarge\",subName:\"\"}",
                  "part_4": "{packID:\"mtsofficialpack\",systemName:\"carseat\",subName:\"_black\"}",
                  "part_5": "{packID:\"mtsofficialpack\",systemName:\"carseat\",subName:\"_black\"}",
                  "part_7": "{packID:\"mtsofficialpack\",systemName:\"scoutbedpickup\",subName:\"\"}",
                  "part_8": "{packID:\"mtsofficialpack\",systemName:\"engineamci4\",subName:\"\"}",
                  "part_10": "{packID:\"mtsofficialpack\",systemName:\"bumpersticker\",subName:\"_roostair\"}",
                  "part_15": "{packID:\"mtsofficialpack\",systemName:\"gloveboxscout\",subName:\"\"}"
                }
              }
            }
          },
          {
            "weight": 15.0,
            "entry": {
              "lootType": "event",
              "protocol": "cbramts:0.5.6",
              "jsonTag": {
                "packID": "mtsofficialpack",
                "systemName": "merc230",
                "subName": [
                  "_blue",
                  "_white",
                  "_seagreen",
                  "_maroon",
                  "_brown",
                  "_green",
                  "_police",
                  "_police2",
                  "_black",
                  "_red",
                  "_gray",
                  "_extravagant",
                  "_olive",
                  "_beige",
                  "_silver",
                  "_yellow",
                  "_salmon"
                ],
                "parts": {
                  "part_0": "{packID:\"mtsofficialpack\",systemName:\"wheellarge\",subName:\"\"}",
                  "part_1": "{packID:\"mtsofficialpack\",systemName:\"wheellarge\",subName:\"\"}",
                  "part_2": "{packID:\"mtsofficialpack\",systemName:\"wheellarge\",subName:\"\"}",
                  "part_3": "{packID:\"mtsofficialpack\",systemName:\"wheellarge\",subName:\"\"}",
                  "part_4": "{packID:\"mtsofficialpack\",systemName:\"enginemercedesm102\",subName:\"\"}",
                  "part_5": "{packID:\"mtsofficialpack\",systemName:\"carseat\",subName:\"_black\"}",
                  "part_6": "{packID:\"mtsofficialpack\",systemName:\"carseat\",subName:\"_black\"}",
                  "part_7": "{packID:\"mtsofficialpack\",systemName:\"carseat\",subName:\"_black\"}",
                  "part_8": "{packID:\"mtsofficialpack\",systemName:\"carseat\",subName:\"_black\"}",
                  "part_9": "{packID:\"mts\",systemName:\"invisible_standing\",subName:\"\"}",
                  "part_11": "{packID:\"mtsofficialpack\",systemName:\"gloveboxscout\",subName:\"\"}"
                }
              }
            }
          }
        ]
      }
    }
  }
]
```
