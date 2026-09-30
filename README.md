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
