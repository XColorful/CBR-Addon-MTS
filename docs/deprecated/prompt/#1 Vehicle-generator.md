> #1 提示词

# 

```
1.载具伤害兼容：看下对于ImmersiveVehicle启用allowExternalDamage配置，是否能让CGC的ProjectileImpactManager能对其hurt
以及CGC的ProjectilePhysicsManager的射线检测能否命中vehicle；如不能的话，是否能注入重载Entity#hurt来扣对应血量
2.客户端能否通过mixin禁用一些交互，如禁止右键碰引擎盖
3.能否直接通过NBT修改放置的载具的血量
4.载具是否有加速度，用来防止载具刚起步就慢速度
1.21.1ImmersiveVehicle使用的配置放在D:\Temp-File\下了
```

# 

I'll investigate this in parallel: the ImmersiveVehicle config files, the CGC managers, and the worktree layout.

MTS = MinecraftTransportSimulator (Immersive Vehicles), and its source is an additional working directory. Let me read the configs and check that repo's state.

Noted the cwd drift — I'll use absolute paths from here. Now let me find the key MTS code paths.

Critical finding: MTS does **not** override `hurt` on the vehicle — it routes external damage through a `BuilderEntityExisting` entity. Let me read that class.

Now the CGC side. Let me locate the two managers and check the config values.

`allowExternalDamage` is currently `true`. Now let me read the two CGC managers (checking whether core/ differs between branches first).

Path bug in my loop — let me redo it explicitly.

Core is byte-identical across branches. Reading both managers.

Core is shared; now let me read the entity ray trace and the damage application path.

Key finding forming. Let me confirm the entity hierarchy and CGC's hitbox helper, then dig into IV's vehicle internals.

Confirmed: IV's proxy entity is a plain `Entity`, not a `LivingEntity`. Now let me dig into IV's vehicle internals for the other three questions.

I'll avoid `cd` from now on. Let me trace health, NBT, and interaction through the IV class hierarchy.

Health lives in a "variable" system. Let me read the interactable/attack code and the NBT save/load path.

Now let me examine the interaction path (Q2) and vehicle motion/acceleration (Q4).

Found the interaction entry point. Let me read it.

That grep is too broad. Let me use the faster search tool instead.

Let me verify whether `Entity.isPickable()` exists in 1.21.1 via the IDE index.

Let me check CGC's designed extension point for non-LivingEntity victims.

Let me check how the builder entity round-trips NBT, which determines whether `/data` edits apply live.

Let me verify exactly how the damage variable is read on load.

调查完成。核心事实都已从 MTS 源码和 CGC core 源码确认。

## 结论速览

| 问题 | 结论 |
|---|---|
| 1. 射线能命中载具？ | **能** |
| 1. CGC 能 hurt 载具？ | **不能**（`allowExternalDamage` 开了也没用，缺口在 CGC 侧） |
| 2. 客户端 mixin 禁交互？ | **可以**，IV 不走原版 `Entity.interact` |
| 3. NBT 直接改血量？ | **存档层可以**，但**已加载的载具即时改无效** |
| 4. 有加速度吗？ | **有，但是涌现的**（F/m），无全局开关 |

---

## 1. 载具伤害兼容

**射线检测：能命中。** CGC 的过滤器是 `isAlive() && isPickable() && !isSpectator()`（[ProjectilePhysicsManager.java:53](core/src/main/java/dev/xcolorful/customgun/core/projectile/physics/ProjectilePhysicsManager.java:53)）。IV 在世界里放的其实是 `BuilderEntityExisting`，它：

- `isPickable()` 对载具返回 `true`（`mcinterfaceneoforge1211/.../BuilderEntityExisting.java:226-230`，有意为投射物留的钩子）
- `isAlive()` = `!isRemoved()` = `true`
- 包围盒被设成整体 `WrapperAABBCollective`（同文件 `:113`），而 CGC 取 `entity.getBoundingBox()`（[EntityUtils.java:70](core/src/main/java/dev/xcolorful/customgun/core/util/EntityUtils.java:70)）

**出伤：不能，且和 `allowExternalDamage` 无关。** `_executeProjectileHitEntity` 出伤的两个分支都硬性要求 `victimLivingEntity != null`（[_ProjectileHit.java:111-141](core/src/main/java/dev/xcolorful/customgun/core/projectile/impact/_ProjectileHit.java:111)）。而 `ABuilderEntityBase extends Entity`（**不是** `LivingEntity`），所以 `victimLivingEntity == null` → **一次 `hurt()` 都不会被调用**。近战路径同理，`_doMelee` 直接 `getEntitiesOfClass(LivingEntity.class, ...)`（[_DefaultGunAttack.java:260](core/src/main/java/dev/xcolorful/customgun/core/gun/attack/_DefaultGunAttack.java:260)）。

你的 `mtsconfig.json` 里 `allowExternalDamage` **已经是 `true`**，所以这条配置已经到位了，但改变不了上面的结果——缺口在 CGC 侧，不在 IV 侧。

**关于"注入 Entity#hurt"这个思路：** 单独立个 `Entity#hurt` 的 mixin 没用，因为 CGC 根本没走到调用点。IV 自己的 `hurt` 入口是 `BuilderEntityExisting.hurt`（`:169-215`），它无条件 `return true`；只在 `allowExternalDamage` 且 `source.getDirectEntity() != null`、且攻击者位置落在交互盒内时出伤，否则退化成"沿攻击者本 tick 位移再射线一次"。

**推荐做法**：走 CGC 已经设计好的受弹实体扩展点，而不是新造 `hurt` 钩子——让 `BuilderEntityExisting` 通过 compat mixin 实现 `IBulletVictimEntity`。`IBulletVictimEntityGetter.fromEntity` 是纯 instanceof（[IBulletVictimEntityGetter.java:13](core/src/main/java/dev/xcolorful/customgun/core/api/entity/victim/IBulletVictimEntityGetter.java:13)），`impactTick` 会自动分流到 `onBulletVictimHit`（[ProjectileImpactManager.java:102-105](core/src/main/java/dev/xcolorful/customgun/core/projectile/impact/ProjectileImpactManager.java:102)）。这和现有 `LivingEntityMixin` 的做法是同一套路。

两个需要提前决定的点：
- **判定盒不一致**：CGC 命中的是整体包围盒，IV 内部 `interactAttackBoxes` 是部件级盒。擦边命中时 IV 侧可能没有任何盒相交 → 复合判定要自己对齐。
- 另一条路是给弹药配 `managerGroupTag` + `registerRuntimeGroup`（[ProjectileManager.java:42](core/src/main/java/dev/xcolorful/customgun/core/projectile/ProjectileManager.java:42)）塞自己的 `ProjectileImpactManager` 子类，但那是按弹药走的，比 mixin 接口绕。

---

## 2. 客户端 mixin 禁用交互

**可以。** IV 完全不走原版 `Entity.interact`：

- 客户端入口 `ControlSystem.handleClick`：自己射线（`getMultipartEntityIntersect`），命中就发 `PacketEntityInteract`（`mccore/.../systems/ControlSystem.java:182-207`）
- 服务端 `PacketEntityInteract.handle` 的判定顺序：部件槽位 → **动作盒** `hitBox.definition.action != null` → `entity.performAction(...)` → 最后 `entity.interact(player)`（`mccore/.../packets/instances/PacketEntityInteract.java:61-160`）

"右键碰引擎盖"基本就是动作盒那条分支。

- **客户端抑制**（不发包）：mixin `ControlSystem.handleClick`，只影响该客户端，纯防误触；改过的客户端仍能绕过。
- **服务端拒绝**：mixin `PacketEntityInteract.handle`，权威。

现成参照：IV 已经在 `InterfaceInput.onIVInteractionKeyMapping` 里用原版事件取消骑乘时的 use-item（`mcinterface1211/.../InterfaceInput.java:330-336`），说明这种抑制是它认可的模式。

---

## 3. NBT 改血量

血量**不是**直接的 health 字段，而是累加伤害变量：`DAMAGE_VARIABLE = "damage"`（`mccore/.../AEntityE_Interactable.java:111`），`0` = 完好，等于 `definition.general.health`（`AJSONItem.java:37`）即报废；`attack()` 就是累加它（`:406-420`）。

NBT 形态：`BuilderEntityExisting` 的实体 NBT 里一个 double 键 `damage`，仅非 0 时写入（`ComputedVariable.saveToNBT:217-222`），构造时直接 `data.getDouble("damage")` 读回（`AEntityE_Interactable.java:207`）——**不依赖 `variables` 列表**。

**但对已放置、正在 tick 的载具，`/data modify` 无效。** `ABuilderEntityBase.load()` 只把 tag 存进 `lastLoadedNBT`，真正生效要等 `baseTick` 里 `!loadedFromSavedNBT` 时重建 MTS 实体（`BuilderEntityExisting.java:132-151`）；一旦 `loadedFromSavedNBT == true`，新 NBT 被永久忽略。更麻烦的是 `saveWithoutId` 会把 `lastLoadedNBT` 合并回去（`ABuilderEntityBase.java:131-141`），所以你可能得到"下次区块重载才突然生效"的延迟效果。

可行路径：改存档/区块 NBT 后重载让实体重建，或者服务端直接走 IV 的变量 API（`PacketEntityVariableSet`）/ `multipart.attack(new Damage(...))`。

---

## 4. 加速度

**有，但是物理涌现的，不是配置项。** `EntityVehicleF_Physics.getForcesAndMotions()` 里力与扭矩累加后：`motion.addScaled(totalForce, 1 / currentMass)`（`EntityVehicleF_Physics.java:498`）—— 每 tick 就是 `a = F/m`。HUD 里那个 `acceleration` 变量只是 `motion.length() - prevMotion.length()` 的显示量（`:767`）。

"起步慢"由这些**包 JSON**决定（都不在 `D:\Temp-File` 的两个 config 里）：

- 引擎 `fuelConsumption`（原始功率）+ `maxRPM` / `gearRatios`，wheelForce 计算在 `PartEngine.java:1156-1204`
- 车辆 `motorized.emptyMass`（`JSONVehicle.java:100`）、`axleRatio` / `differentialRatio`
- `dragCoefficient`（`JSONVehicle.java:151`）、`revResistance` / `engineWinddownRate`

所以没有全局加速度开关，只能调 JSON 或换引擎/齿比。

---

一个提醒：`mtsconfig.json` 是服务端配置、`mtsconfigclient.json` 是客户端配置，两边都有 `allowExternalDamage` 之类的键——真要下结论得确认改的是哪一侧。

要我先做哪一块？我的建议顺序是 **1**（先打通实际伤害，其余都是体验问题）→ **2**。第 1 项需要先定：走 `IBulletVictimEntity` mixin（推荐），还是给弹药配 `managerGroupTag`；以及命中判定用整体包围盒还是对齐 IV 的部件盒。

# 

```
第一点我改成这样似乎就已经完成功能了，你看怎么回事?
```

# 

没有看到你贴的改动内容——让我直接查一下工作区。

找到了——改的是 [_ProjectileHit.java](core/src/main/java/dev/xcolorful/customgun/core/projectile/impact/_ProjectileHit.java)。看下 diff。

改动是：把"必须是 LivingEntity"改成"非 LivingEntity 也放行"，让 `hurt()` 真的被调用。先说清它为什么能生效，再说它有什么问题。让我先确认几个关键前提。

Tick 顺序确认了：`physicTick`(射线) → `impactTick`(出伤) → `physicMove`(位移)。这正是它能生效的原因。再确认一下改动的影响面。

影响面确认了。再看一个决定性的点：CGC 的伤害类型带了哪些 tag。

## 为什么这一改就通了

关键在于**出伤时子弹还没位移**。

`ProjectileProcessManager` 的 tick 顺序是 [`physicTick`(射线) → `impactTick`(出伤) → `physicMove`(位移)](core/src/main/java/dev/xcolorful/customgun/core/projectile/process/ProjectileProcessManager.java:27)。所以在 `hurt()` 被调用的那一刻：

- `gunProjectile.position()` 还是本 tick 的**起点**
- `getDeltaMovement()` 是本 tick 的**速度**

而 IV 的 `BuilderEntityExisting.hurt` 内部有一条兜底路径：取不到"攻击者落在交互盒内"时，它会拿 `attacker.getDeltaMovement()`，**从攻击者当前位置沿这个位移再射线一次**，命中就交给 `multipart.attack(...)`（`mcinterfaceneoforge1211/.../BuilderEntityExisting.java:200-211`）。

这条兜底射线的起止点，恰好等于 CGC 刚刚测过的那一段。两边用的 `BULLET` 伤害源的 `getDirectEntity()` 又是子弹实体本身（[_ProjectileHit.java:210](core/src/main/java/dev/xcolorful/customgun/core/projectile/impact/_ProjectileHit.java:210)、[_ProjectileHit.java:87](core/src/main/java/dev/xcolorful/customgun/core/projectile/impact/_ProjectileHit.java:87)）。所以对得上，就出伤了。换句话说：**IV 自己重新做了一遍同样的射线检测**，你那行改动只是把"发令枪"交到它手上。

## 但它没表面看起来那么"完成"

1. **判定盒不对齐**。CGC 命中的是整体包围盒 `getBoundingBox()`（`BuilderEntityExisting.java:113` 把它设成了 encompassing box），IV 内部却按**部件级** `interactAttackBoxes` 重新判。擦着包围盒边缘、没进任何部件盒的命中 → IV 侧射不中，**依然零伤害**。而 CGC 这边 `hurt` 恒返回 `true`，pierce 已经扣掉、特效也放了——玩家会看到"打中了但没伤害"。

2. **`victimLivingEntity == null || !isDeadOrDying()` 这个写法有歧义**。它等价于 `!(有生物 && 生物已死)`，但读起来像"非生物就跳过"，和旁边的注释 `// 没死亡 (生物)` 已经相反了。

3. **影响面溢出到所有非生物可命中实体**。我把 1.21.1 的实体类过了一遍，覆盖 `isPickable()` 的非生物类有：`AbstractArrow`、`ShulkerBullet`、`Boat`、`AbstractMinecart`、`PrimedTnt`、`FallingBlockEntity`、`EndCrystal`、`BlockAttachedEntity`(物品展示框/画)、`EnderDragonPart`。实际后果大多无害——Boat/Minecart/TNT 这些继承 `Entity.hurt`，只 `markHurt()` 一下就 `return false`；展示框/末地水晶会被打掉，但那本来就和原版一致。**不过这终究是行为变化**：以前这些实体一次 `hurt()` 都不会收到。

4. **爆头判定现在也生效了**。`checkHeadshot` 对没配 `HeadAABBData` 的实体走 `getEyeHeight()` 兜底（[ProjectilePhysicsManager.java:254-268](core/src/main/java/dev/xcolorful/customgun/core/projectile/physics/ProjectilePhysicsManager.java:254)）。载具在"眼高"附近中弹会吃爆头倍率——以前不出伤所以无所谓，现在会体现在伤害数字上。

5. **这是 `core/` 改动**。core 被 1.20.1 → 26.3 全分支共享，这是通用代码路径不是平台差异，改它等于对所有版本生效。

## 更贴合项目设计的写法

`_ProjectileHit` 里其实已经拿到了 `hitEvent.getIBulletVictimEntity()`（[_ProjectileHit.java:150](core/src/main/java/dev/xcolorful/customgun/core/projectile/impact/_ProjectileHit.java:150)）。项目本身就有"受弹实体"这个显式扩展点，`LivingEntityMixin` 就是这么接进去的。

两条路：

| | 你现在的改法 | 走 `IBulletVictimEntity` |
|---|---|---|
| core 改动 | 1 行，语义变"所有非生物可伤害" | 0 行（或只加一个显式放行） |
| IV 侧 | 不用动 | compat mixin 让 `BuilderEntityExisting` 实现接口 |
| 影响面 | 全局所有非生物实体 | 只有显式接入的实体 |
| 顺带解决判定盒问题 | 否 | 是（可以在 `cgc$onProjectileImpact` 里用 CGC 给的 `hitEntityResult.hitPos()` 直接定点出伤，绕开 IV 的二次射线） |

代价是 mixin 要实现 `cgc$onProjectileImpact` + `IBulletVictimKnockback` 三个方法（`IBulletVictimVision` 有 default 不用管）。

## 建议

如果你只想要"能打载具"这个结果，现在的改法**能用**，但第 1 条（边缘命中无伤害）和第 3 条（全局影响）是实打实的债。我倾向于走接口那条，顺带把"命中点直接出伤"做对，这样也不用依赖 IV 的二次射线兜底。

要我按接口方案在 1.21.1neoforge 的 compat 层搭一版给你对比吗？还是你想先把现在这版跑一段观察？

# 

```
载具最外面那个白碰撞箱，似乎不会消耗掉子弹，穿过白色碰撞箱而没碰到绿色碰撞箱的子弹似乎是能穿过去的，而且载具也不掉血，这应该是没问题的？
```

# 

这个观察很关键——它说明白箱和绿箱在代码里是两套东西。让我把两侧的判定都查实。

`WrapperAABBCollective` **重写了 `clip()`** —— 这就是答案。让我看完整实现。

关键是 `WrapperAABBCollective.clip()` 只测**真实子盒**，外接盒本身从不参与命中。再确认两边用的是不是同一套子盒。

白箱找到了——是 `encompassingBox`。确认一下渲染上下文和默认色。

## 白箱是什么

白箱就是 `encompassingBox`——**纯外接包围体，不是碰撞盒**。它只做广相剔除，渲染本身还带闪烁（`System.currentTimeMillis() % 1000 > 500` 才画，`mccore/.../AEntityF_Multipart.java:903-907`），是调试可视化的东西。绿箱才是真正的碰撞盒（`AEntityE_Interactable.renderBoundingBoxes` 渲染的 `collisionBoxes`，`AEntityE_Interactable.java:428`）。

而且 CGC 的射线**根本没机会碰到白箱**：`entity.getBoundingBox()` 返回的是 IV 设的 `WrapperAABBCollective`，它重写了 `clip()`，只遍历真实子盒求最近交点，外接盒本身直接跳过（`mcinterface1211/WrapperAABBCollective.java:77-96`）。连广相 `intersects()` 也被重写成"外接盒相交 **且** 有子盒相交"。

顺带一提，IV 自己的子弹也是这个逻辑：`getHitBoxes` 先 `encompassingBox.intersects(movementBounds)` 做粗筛，再逐盒求交（`AEntityF_Multipart.java:284-290`）。所以**你看到的现象是对的，也是 IV 的设计意图**——子弹从外接盒范围穿过去、不碰实体就不掉血、不消耗，没问题。

## 但有一个不对称值得盯

两边用的子盒集合**不是同一套**：

- CGC 射线 → `getBoundingBox()` → `collisionBoxes` collective（`collision=true`）→ 只含 **`ENTITY`** 类型盒
- IV 出伤 → `interactAttackBoxes`（`ATTACK|CLICK`）+ `getHitBoxes(..., isBullet=false)` → 只认 **`ATTACK`**

`collisionTypes` 是 `@JSONRequired` 的 `Set<CollisionType>`，包作者自己选，所以两边不重合是可能的：

| 盒子标注 | CGC 能命中 | IV 会出伤 | 结果 |
|---|---|---|---|
| `ENTITY` + `ATTACK` | ✅ | ✅ | 正常 |
| `ENTITY` + `BULLET`（无 ATTACK） | ✅ | ❌ | **子弹被吃掉，零伤害** |
| 只有 `ENTITY` | ✅ | ❌ | **子弹被吃掉，零伤害** |
| `ATTACK`（无 ENTITY） | ❌ | — | 子弹穿过去，打不到 |
| 只有 `BULLET` | ❌ | — | 子弹穿过去，打不到 |

中间两行才是问题：CGC 判定命中 → 扣 pierce、放击打特效、pierce 归零就 `discard()`（[ProjectileImpactManager.java:107-113](core/src/main/java/dev/xcolorful/customgun/core/projectile/impact/ProjectileImpactManager.java:107)）——但 IV 侧找不到 `ATTACK` 盒，一点血都不掉。玩家看到的是"打中了、有火花、子弹没了、车没事"。

第一列的 `BULLET` 标注尤其容易踩：IV 的语义里 `BULLET` 就是"允许子弹交互但不允许通用攻击"，正是该让子弹生效的标注，可外部伤害这条路走 `isBullet=false`，**把 `BULLET`-only 的盒直接忽略了**——这更像是 IV 自己的遗漏。

## 结论

白箱那部分你判断得对，不用管。要验的是车辆包的 JSON 里，可被子弹打中的那些组是不是都同时标了 `ENTITY` 和 `ATTACK`。只要标全了，现在的表现就是正常的；漏标的组就会"吃子弹不出伤"。

要我在 1.21.1neoforge 的 compat 层加一段诊断（比如命中载具时把 `hurt` 前后 IV 的 `damage` 变量打出来），拿一辆车实测一下哪些组会吃子弹？还是你先按这个表去核对车辆包的 JSON？

# 

```
这个伤害判定先不管了，我现在要用CBR的实体刷新配置刷车，我装好了两辆车并F3+I如下，目前还没油
配置模板放在D:\Temp-File\了，看下怎么填1.21.1的配置使能直接刷出完整的车，以及CBR EntityData+LootGenerator是否能强行设置最大生命值？

/summon mts:builder_existing -266.50 26.49 531.50 {variables1: "hasOpenTop", part_7: {packID: "mtsofficialpack", variablescount: 1, part_active: 1.0d, variables0: "part_active", systemName: "scoutbedpickup", spawnedDefaultParts: 1b, subName: "", uniqueUUID: "c162671f-d35b-41ca-9e8f-a32d7ed3329b"}, variables2: "body_pitch", part_5: {packID: "mtsofficialpack", variablescount: 1, part_active: 1.0d, variables0: "part_active", systemName: "seat", spawnedDefaultParts: 1b, subName: "black", uniqueUUID: "bc940db4-926e-4715-a36c-c3062fc8c311"}, variables0: "batteryCapacity", part_4: {packID: "mtsofficialpack", variablescount: 1, part_active: 1.0d, variables0: "part_active", systemName: "seat", spawnedDefaultParts: 1b, subName: "black", uniqueUUID: "43e7aaa1-dee0-4547-8198-ef6cab511447"}, part_8: {packID: "mtsofficialpack", variablescount: 1, part_active: 1.0d, temp: 21.198145744839398d, variables0: "part_active", systemName: "engineamci4", spawnedDefaultParts: 1b, subName: "", uniqueUUID: "5d1ae09a-1f4d-40b1-878d-b817250e2627"}, Invulnerable: 0b, entityid: "EntityVehicleF_Physics", PortalCooldown: 0, part_3: {packID: "mtsofficialpack", variablescount: 1, part_active: 1.0d, variables0: "part_active", systemName: "wheellarge", spawnedDefaultParts: 1b, subName: "", uniqueUUID: "8bfbeac9-1508-478e-9fef-b0a31b616e46"}, part_15: {packID: "mtsofficialpack", variablescount: 1, part_active: 1.0d, variables0: "part_active", systemName: "gloveboxscout", spawnedDefaultParts: 1b, subName: "", inventory: {Items: [], uniqueUUID: "f5a185e0-a736-4bbe-a095-882a0105da52"}, uniqueUUID: "9feb8015-1f7e-4bbc-9efc-e754fd270ed2"}, textnull: "(%f)", part_2: {packID: "mtsofficialpack", variablescount: 1, part_active: 1.0d, variables0: "part_active", systemName: "wheellarge", spawnedDefaultParts: 1b, subName: "", uniqueUUID: "56575b74-e093-4b64-b3c4-a65255737dec"}, part_1: {packID: "mtsofficialpack", variablescount: 1, part_active: 1.0d, variables0: "part_active", systemName: "wheellarge", spawnedDefaultParts: 1b, subName: "", uniqueUUID: "7359b5e4-c075-4612-9bd6-a3b089bde47c"}, radio: {volume: 10, currentURL: "", uniqueUUID: "92bb2097-dbea-4019-9544-0ce9b3ae50c2"}, "textLicense Plate": "IM SCOUT", part_0: {packID: "mtsofficialpack", variablescount: 1, part_active: 1.0d, variables0: "part_active", systemName: "wheellarge", spawnedDefaultParts: 1b, subName: "", uniqueUUID: "36a02aed-e3e1-4208-b9f5-d8449223ba31"}, packID: "mtsofficialpack", FallDistance: 0.0f, serverDeltaMy: -0.0625000014878514d, systemName: "scout", part_10: {packID: "mtsofficialpack", variablescount: 1, part_active: 1.0d, variables0: "part_active", systemName: "bumpersticker", spawnedDefaultParts: 1b, subName: "_roostair", uniqueUUID: "35a3606c-9730-4d6d-921c-51c69606b962"}, batteryCapacity: 14.0d, uniqueUUID: "acf443f3-be96-48f5-afa4-718b69be24e2", Motion: [0.0d, 0.0d, 0.0d], fuelTank: {currentFluidMod: "wildcard", currentFluid: "", uniqueUUID: "835211d6-8f3e-45ef-8ea9-7bd3d766b46d"}, hasOpenTop: 1.0d, body_pitch: 2.802596928649634E-45d, Air: 300s, OnGround: 0b, Rotation: [0.0f, 0.0f], electricPower: 12.0001d, positionx: -266.5d, positiony: 26.4875d, variablescount: 3, positionz: 531.5d, selectedBeaconName: "", spawnedDefaultParts: 1b, anglesy: 9.235870361328123d, subName: "_black", Fire: -1s}



/summon mts:builder_existing -271.50 26.44 523.50 {variables1: "body_pitch", part_7: {packID: "mtsofficialpack", variablescount: 1, part_active: 1.0d, variables0: "part_active", systemName: "seat", spawnedDefaultParts: 1b, subName: "black", uniqueUUID: "7909f7a0-28d7-45d4-b22b-c905bf141aed"}, part_6: {packID: "mtsofficialpack", variablescount: 1, part_active: 1.0d, variables0: "part_active", systemName: "seat", spawnedDefaultParts: 1b, subName: "black", uniqueUUID: "cc1f211b-6faa-4b7a-8baa-c9c6b4a6c8ed"}, part_5: {packID: "mtsofficialpack", variablescount: 1, part_active: 1.0d, variables0: "part_active", systemName: "seat", spawnedDefaultParts: 1b, subName: "black", uniqueUUID: "7a7f3b86-a44f-4a82-99f3-6c4423728119"}, variables0: "batteryCapacity", part_4: {packID: "mtsofficialpack", variablescount: 1, part_active: 1.0d, temp: 23.70198860293834d, variables0: "part_active", systemName: "enginemercedesm102", spawnedDefaultParts: 1b, subName: "", uniqueUUID: "a15281c8-2de3-4dc5-869c-fd2b3a29332e"}, part_9: {packID: "mts", variablescount: 1, part_active: 1.0d, variables0: "part_active", systemName: "invisible_standing", spawnedDefaultParts: 1b, subName: "", uniqueUUID: "541e269e-c64d-40ff-87b5-d365528d2deb"}, part_8: {packID: "mtsofficialpack", variablescount: 1, part_active: 1.0d, variables0: "part_active", systemName: "seat", spawnedDefaultParts: 1b, subName: "black", uniqueUUID: "db722a09-fff7-407f-b0cf-bc5945288cf2"}, Invulnerable: 0b, entityid: "EntityVehicleF_Physics", PortalCooldown: 0, part_3: {packID: "mtsofficialpack", variablescount: 1, part_active: 1.0d, variables0: "part_active", systemName: "wheellarge", spawnedDefaultParts: 1b, subName: "", uniqueUUID: "08a3ec7f-a13d-4baa-8621-3797f9665ee4"}, textnull: "(%f)", part_2: {packID: "mtsofficialpack", variablescount: 1, part_active: 1.0d, variables0: "part_active", systemName: "wheellarge", spawnedDefaultParts: 1b, subName: "", uniqueUUID: "d4fcf080-22c4-4e60-93cd-5a182ad05849"}, part_1: {packID: "mtsofficialpack", variablescount: 1, part_active: 1.0d, variables0: "part_active", systemName: "wheellarge", spawnedDefaultParts: 1b, subName: "", uniqueUUID: "51caa7eb-d091-4763-9d24-08d4b3e4e81a"}, radio: {volume: 10, currentURL: "", uniqueUUID: "d1db519c-4bfc-41db-8a5d-a90d0539a30f"}, "textLicense Plate": "JW1955", part_0: {packID: "mtsofficialpack", variablescount: 1, part_active: 1.0d, variables0: "part_active", systemName: "wheellarge", spawnedDefaultParts: 1b, subName: "", uniqueUUID: "a43ecbe3-33d9-4947-9c2a-d7773353a767"}, packID: "mtsofficialpack", part_11: {packID: "mtsofficialpack", variablescount: 1, part_active: 1.0d, variables0: "part_active", systemName: "gloveboxscout", spawnedDefaultParts: 1b, subName: "", inventory: {Items: [], uniqueUUID: "e7c4b4b0-09d4-42af-bb63-8d6f8c50df13"}, uniqueUUID: "f904ea78-17e3-4664-9a29-759dcd441938"}, FallDistance: 0.0f, serverDeltaMy: -0.17249998986376347d, systemName: "merc230", batteryCapacity: 14.0d, uniqueUUID: "be593388-d6bb-44c8-838d-1b6270b21c50", Motion: [0.0d, 0.0d, 0.0d], fuelTank: {currentFluidMod: "wildcard", currentFluid: "", uniqueUUID: "a92f2cca-91fe-462d-8f2d-1015fc892cfd"}, body_pitch: 2.802596928649634E-45d, Air: 300s, OnGround: 0b, Rotation: [0.0f, 0.0f], electricPower: 12.0001d, positionx: -271.5d, positiony: 26.44d, variablescount: 2, positionz: 523.5d, selectedBeaconName: "", spawnedDefaultParts: 1b, anglesy: -12.214599609374998d, subName: "_black", Fire: -1s}
```

# 

明白了，切到 CBR 刷车。先看配置模板和 CBR 的代码结构。

配置模板找到了，是 `immersive_vehicles.json`。一起看模板和 CBR 的 EntityData。

模板结构清楚了。现在看 `nbt` 字段怎么解析，以及 IV 的油箱 NBT 格式。

`nbt` 是 **SNBT 字符串**。再看油箱液位字段、LootGenerator 的生成流程，以及 `spawnedDefaultParts` 的作用。

油箱字段是 `fluidLevel`。再确认 `spawnedDefaultParts` 的读取点、LootGenerator 的后续处理，以及燃料流体名。

LootGenerator 会 `setPos` 覆盖坐标——但 MTS 内部坐标可能把它顶回去。让我确认 MTS 的坐标 NBT 处理。

这里有个坐标冲突要确认。让我看 `getPoint3d` 的键名和缺省行为，以及 IV 自己是怎么放置载具的。

确认坐标来源。同时看 CBR 有没有能强制血量的入口。

ItemVehicle 直接改 `vehicle.position`——证实坐标只能由 NBT 决定。最后确认几件事。

没有 command 条目，`NbtEntry` 是 TODO 桩。最后确认 JSON 字段名，然后给配置。

`uniqueUUID` 是全局键控的——这点很关键。看它缺失时怎么处理。

调查完了。先说结论：**能刷出完整的车，但位置只能由 NBT 决定；CBR 侧没有强制血量的能力。**

## 1. 配置怎么写

文件放在 CBR 的 loot entity_spawner 目录（`LootConfigManager.LOOT_CONFIG_PATH` 下的 `entity_spawner/`，即你那个 `immersive_vehicles.json`）。

`nbt` 字段是**字符串（SNBT）**，走 `NBTUtils.stringToNBT` → `EntityEntry` → `EntityData.getEntity()` 就是 `entityType.create()` + `entity.load(nbt)`。而 MTS 的 `BuilderEntityExisting` 恰好走"从 NBT 恢复"这条路（`entityid` 决定恢复成哪个 MTS 类，`baseTick` 里 `entityMap.get(...).restoreEntityFromData(...)`）——所以 F3+I 那一整串直接能用。

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
          { "weight": 35.0, "entry": { "lootType": "entity", "entity": "mts:builder_existing", "count": 1, "nbt": "<scout 的 SNBT>" } },
          { "weight": 15.0, "entry": { "lootType": "entity", "entity": "mts:builder_existing", "count": 1, "nbt": "<merc230 的 SNBT>" } }
        ]
      }
    }
  }
]
```

SNBT 里**保留**：`entityid`（必需）、`packID`/`systemName`/`subName`、`variablescount`+`variables0..N`、全部 `part_N`、`spawnedDefaultParts:1b`（必需，否则 IV 会再补一遍默认部件）、`anglesy`、`batteryCapacity`/`hasOpenTop`/`body_pitch`/`electricPower`、`textLicense Plate`、`radio`、`positionx/y/z`、`fuelTank`。

**丢掉**原版实体状态：`Invulnerable`、`PortalCooldown`、`FallDistance`、`Air`、`OnGround`、`Motion`、`Rotation`、`Fire`、`serverDeltaMy`——MTS 不读，留着容易出意外。

两个小修正：`body_pitch: 2.8E-45d` 是浮点残余，写 `0.0d`；`count` 保持 1。

`uniqueUUID` **不用手动改**：`AEntityA_Base` 构造函数会查 `EntityManager`，发现 UUID 已被占用就自动换新的（源码注释明说是为"同一份数据块复制实体"设计的）。所以同一份 NBT 刷多辆不会撞 UUID。

**加油**——`fuelTank` 补一个 `fluidLevel`：

```
fuelTank:{currentFluid:"lava",currentFluidMod:"minecraft",fluidLevel:XXX.0d}
```

你 `mtsconfig.json` 的 `lastLoadedFluids` 里只有 `lava`/`water`，而 `fuels` 表里 `lava` 在 diesel/gasoline/avgas 下都是 1.0，所以用 `lava`。引擎校验只看 `getFluid()` 名字不查 mod（`PartEngine.java:198`），`currentFluidMod` 写 `minecraft` 即可。`fluidLevel` 填车辆 JSON 的 `motorized.fuelCapacity`——**别往大了填**，`getMass() = fluidLevel/50`，超量会实打实改车重和物理（加载时不钳制上限）。

## 2. 最大生命值

- **普通生物**：可以。`nbt` 里写 `Attributes:[{Name:"minecraft:generic.max_health",Base:100}],Health:100f` 就行，CBR 原样透传。
- **IV 载具**：**不行**。IV 没有 max_health 属性，上限来自车辆包 JSON 的 `general.health`（加载自资源包，NBT 碰不到）。NBT 里能操作的只有累加伤害变量 `damage`：顶层 `damage:0d` 是完好，`== general.health` 是报废——所以"满血车"就是**不写**这个键。真想加血只能把 `damage` 写负数当缓冲区（`attack()` 只钳上界不钳下界），代价是 HUD 的 `damage_percent` 显示负值。

而且 CBR 侧没有任何后处理：`EntityData.getEntity()` 之后 `LootGenerator` 只做 `setPos` + `addFreshEntity`；`NbtEntry` 是 TODO 桩（`generateLootData` 直接返回空）。所以"强行设置"只能靠 NBT 本身。

## 3. 一个必须先定夺的坑：位置

MTS 的坐标是**单向同步**的——`BuilderEntityExisting.baseTick` 每 tick 把内部 `position` 写回 MC 实体（`BuilderEntityExisting.java:88`），而内部 `position` 只来自 NBT。CBR 的 `setPos` 只在第 1 tick 有效，第 2 tick 就被顶回 NBT 坐标。

后果：**`range`、`attempts` 和掉落点位置对载具完全无效**，所有车都会跑去 `positionx/y/z` 指定的那个点。两个选择：

- **A（现在就能用）**：接受固定点——把坐标写死在 NBT 里，每个车位一个 entry、`count:1`。`range`/掉落点不用管。
- **B（要改 MTS）**：在 `BuilderEntityExisting.baseTick` 恢复实体后，让 MTS 实体改用 MC 实体当前的坐标（正常从存档加载时两者本来就一致，因为 `Pos` 和 `positionx/y/z` 是一起存的，所以这个改动对其他场景是行为保持的）。改完就能让 CBR 的随机落点生效。

我建议 B，否则 CBR 的刷车等于是个固定车位表。要我按 B 改 MTS，还是你先按 A 出一版配置验证载具能完整刷出来？另外如果你把两串 F3+I 存成文件给我，我可以直接转好带转义的 JSON 给你，省得手抄错。

# 

```
坐标不要紧，载具会自己浮到地上或沉到地上，entityId这个为什么要填？物资配置不能保证entityId不重复，就我F3+I复制的那两个NBT帮我把必要信息填到D:\Temp-File\模板里，燃料先不管
```

# 

明白，坐标不纠结。先澄清 `entityid`，再确认两个 MTS 字段，然后写文件。

`entityid` 确认了：它是**类名常量**（`entity.getClass().getSimpleName()`，`ItemVehicle.java:74` 注册到 `entityMap`），不是唯一 ID——所有载具都是 `"EntityVehicleF_Physics"`，重复是正常的。你担心的唯一性由 `uniqueUUID` 负责，那个 MTS 会自动去重。

现在写配置。

先回答 `entityid`：它**不是 ID，是类名常量**。`BuilderEntityExisting.saveWithoutId` 写的是 `entity.getClass().getSimpleName()`，`baseTick` 恢复时用它去 `entityMap` 查工厂（`ItemVehicle.java:74` 注册 `EntityVehicleF_Physics`）。所有载具都是这一个值，重复是预期行为。真正需要唯一的是 `uniqueUUID`，而 `AEntityA_Base` 构造函数会查 `EntityManager`，撞了就自动换新的（源码注释明写这是为"同一份数据块复制多个实体"设计的），所以也不用你管。

## 已生成

写到了 [immersive_vehicles.json](D:/Temp-File/immersive_vehicles.json)，装到 `config/battleroyale/loot/entity_spawner/` 下即可。

保留了：`entityid`、`packID`/`systemName`/`subName`、`spawnedDefaultParts`、`variablescount` + `variables0..N`、全部 `part_N`（发动机的 `temp`、手套箱的 `inventory` 原样）、`anglesy`、`batteryCapacity`/`hasOpenTop`/`body_pitch`/`electricPower`、`textLicense Plate`、`radio`、`fuelTank`、`positionx/y/z`、`uniqueUUID`。scout 10 个部件、merc230 11 个部件，全部与 F3+I 一致。

丢掉的是原版实体状态：`Invulnerable`、`PortalCooldown`、`FallDistance`、`Air`、`OnGround`、`Motion`、`Rotation`、`Fire`，另外两个：
- `serverDeltaMy` —— MTS 的客户端预测补偿量（`AEntityVehicleD_Moving:126`），是 F3+I 那一刻的瞬时值，留着会让新车带一个假初速，去掉即归零。
- `selectedBeaconName` —— MTS 里查无此字段，不是它的。

`body_pitch: 2.802596928649634E-45d` 是浮点残余，改成 `0.0d`。燃料按你说的没动，坦克是空的。

校验过：JSON 可重新解析、SNBT 括号配平、字符串闭合、部件 UUID 与 F3+I 完全对齐。

## 两点提醒

- 两个载具放在 `weight` 节点里（35 / 15），**每次触发只刷其中一辆**，不是两辆都出。要一起出得套 `multi`。
- `count` 保持 1。同一 entry 刷多辆会共用 NBT 里烤死的坐标，全叠在一个点上。

# 

```
我这个是用在1.21.1的配置，是否需要用components还是说CBR已经能同时用于1.20.1/1.21.1？entityId这个一定要填吗？mts:builder_existing还不够?uniqueUUID是否能留空？
```

# 

三个问题都值得查实。先看 CBR 的跨版本处理和 `uniqueUUID`/`entityid` 的缺省行为。

三个都查实了。

## 1. `components` —— 只跟物品条目有关，实体路径没这回事

我对比了两棵树：

| 文件 | 1.20.1 vs 1.21.1 |
|---|---|
| `EntityEntry.java` | **完全相同** |
| `LootEntryTag.java` | **完全相同** |
| `ItemEntry.java` | 不同 |

差异全在 `ItemEntry`：1.21.1 版把物品 NBT 转成 `{components:{...}}`、战利品函数用 `set_components`；1.20.1 版用 `{Item:{...tag:...}}` + `set_nbt`。而 `ComponentsTag.COMPONENTS` 只被 `ItemData`、`ItemEntry`、`WorldText` 引用，**没有任何实体路径**。

`EntityEntry` 只做 `entityType.create()` + `entity.load(nbt)`，那个 nbt 是 MTS 自己的复合标签，跟原版 item components 是两个体系。所以**这份载具配置不需要 components，1.20.1 / 1.21.1 通用**。

## 2. `entityid` 必须填，`mts:builder_existing` 不够——两者不是一层的东西

- `mts:builder_existing` 是 **MC 实体类型**，决定创建哪个外壳实体。
- `entityid` 是 **NBT 里的字段**，决定外壳里装哪个 MTS 内部实体。

外壳是通用的，可以是载具、放置的部件、玩家枪：`EntityVehicleF_Physics` / `EntityPlacedPart` / `EntityPlayerGun` 分别注册进 `entityMap`（`ItemVehicle.java:74`、`AItemPart.java:66`、`ItemPartGun.java:56`）。不给 `entityid`，MTS 不知道要造哪个。

缺失时会怎样：`lastLoadedNBT.getString("entityid")` 对不存在的键返回 `""` → `entityMap.get("")` 是 `null` → `.restoreEntityFromData(...)` 抛 NPE → 被 `try/catch` 吞掉 → `discard()`。表现是**车刷出来立刻消失**，日志里一条 `Failed to load entity on builder from saved NBT`。

## 3. `uniqueUUID` 可以省略

`AEntityA_Base:46`：

```java
if (shouldSync() && data != null && data.hasKey(UNIQUE_UUID_TAG_NAME)) { ...撞了就换新的... }
else { this.uniqueUUID = UUID.randomUUID(); }
```

载具的 `shouldSync()` 用基类默认值 `true`（只有 `EntityBullet`/`EntityParticle` 覆写成 false），所以**键一省略就直接生成随机 UUID**。

而且 `save()` 会把 UUID 写回存档（`AEntityA_Base:114`），第一次存档后就固定了——不影响拖车连接这类依赖稳定 UUID 的功能。

保留也无害（有去重兜底），但**省略更干净**：不依赖那条去重检查，语义也更直白。

要我把生成好的文件里的 `uniqueUUID` 全部去掉、重出一版吗？（车辆级的和每个 `part_N` 里的都去掉）

# 

```
我只要刷出来跟我F3+I时的车辆拼装状态一样就行，多的NBT不要留
```

# 

要做得准确，得先看 MTS 到底读哪些键。让我读部件加载逻辑。

`spawnedDefaultParts` **只写不读**——全仓库只有一处 `setBoolean`。再确认 `part_active` 和部件构造函数读了什么。

`spawnedDefaultParts` 是死键。再确认几个部件级字段是否真被读。

`selectedBeaconName` 我之前判断错了——它确实是 MTS 的字段（`AEntityVehicleE_Powered.java:83`），上一次 grep 被 `head` 截断了，是我的疏忽。用户数据里它是空串，去掉等价。

现在重出一版精简的。

已重写 [immersive_vehicles.json](D:/Temp-File/immersive_vehicles.json)：scout 从 2800 压到 **1138** 字符，merc 从 2946 压到 **1196**，部件一个不少（10 / 11 个）。

## 保留的（都是构造时真会读的）

- `entityid`、`packID`/`systemName`/`subName` —— `data.getPackItem()` 靠这三个解析车辆定义
- `positionx/y/z` —— 不给就落到 0,0,0
- `anglesy` —— 朝向，就 1 个键，不要可以说
- `batteryCapacity`、`hasOpenTop`、`electricPower` —— 构造里都是显式 `ComputedVariable(..., data)` / `data.getDouble(...)` 读的
- 每个 `part_N`：只留 `packID` + `systemName` + `subName` + `part_active:1.0d`

## 删掉的

**原版实体状态**：`Invulnerable`、`PortalCooldown`、`FallDistance`、`Air`、`OnGround`、`Motion`、`Rotation`、`Fire`

**MTS 的死键 / 自动生成**：
- `spawnedDefaultParts` —— 全仓库只有 `AEntityF_Multipart:974` 一处 `setBoolean`，**从来没被读过**，纯死键
- `uniqueUUID` —— 缺键时走 `UUID.randomUUID()`，且 `save()` 会写回
- `temp` —— 引擎温度（`PartEngine:125` 读），瞬时状态，新车冷机
- `serverDeltaMy` —— 客户端预测补偿的瞬时量

**删了等价**：
- `selectedBeaconName` —— 你的是空串，`getByNameFromWorld(world, "")` 和不写一样
- `radio` —— 缺省会建一个默认收音机
- `fuelTank` —— `EntityFluidTank` 构造对 null 安全，缺省就是空油箱（正好你没油）
- `inventory` —— `EntityInventoryContainer` 构造对 null 安全，缺省是空背包
- `variablescount` / `variables0..N` —— `getStrings` 由 `variablescount` 驱动，缺省为 0；而 `batteryCapacity`(`AEntityVehicleE_Powered:104`)、`hasOpenTop`(`EntityVehicleF_Physics:158`)、`part_active`(`APart:167`) 在代码里都是显式创建的，不依赖这个列表
- `body_pitch` —— 唯一靠列表恢复的，值≈0，默认也是 0
- `textnull`、`textLicense Plate` —— 文本对象

## 两点说明

**更正**：上一轮我说 `selectedBeaconName` "不是 MTS 字段"是错的——它确实是（`AEntityVehicleE_Powered.java:83`）。当时那条 grep 被 `head` 截断了，我看了不完整的输出就下了结论。

**`part_active` 我留着了**。`APart.update()` 每 tick 都会根据 `partOn` 和 switchbox 重算 `isActiveVar`，所以严格说它可以删；但留着能保证首 tick 之前状态就是"已装好"，成本是每个部件 20 字符。

车牌文字（`IM SCOUT` / `JW1955`）按"不要多余的"删掉了，需要的话说一声我加回去。

# 

```
实体没刷出来：[30Sep2026 21:49:28.513] [Server thread/INFO] [xiao.battleroyale.BattleRoyale/]: Loot generation task initialized for 841 chunks, loot gameId: 6fb47bf7-bdd8-4273-a988-7d80fb369152
[30Sep2026 21:49:28.514] [Server thread/INFO] [net.minecraft.server.MinecraftServer/]: [XiaoColorful: 开始执行物资刷新 (区块数: 841)]
[30Sep2026 21:49:28.520] [Render thread/INFO] [net.minecraft.client.gui.components.ChatComponent/]: [System] [CHAT] 开始执行物资刷新 (区块数: 841)
[30Sep2026 21:49:28.546] [Server thread/DEBUG] [xiao.battleroyale.BattleRoyale/]: Clear entity with no gameId jsonTag: entity.mts.builder_rendering (UUID: f4732daa-b935-4d46-b79a-ec2ef8f40876) at (-284.9833853492011, 28.117082824525827, 536.0596137206409)
[30Sep2026 21:49:28.547] [Server thread/DEBUG] [xiao.battleroyale.BattleRoyale/]: Clear old game entity object: entity.mts.builder_existing (UUID: 4b123cff-0292-48f6-8a06-7c9d7e5d7c8d) (GameId: 143f5d75-6674-4bac-8d31-598b407c1f75) at (-266.5, 26.4875, 531.5)
[30Sep2026 21:49:28.547] [Server thread/DEBUG] [xiao.battleroyale.BattleRoyale/]: Clear old game entity object: entity.mts.builder_existing (UUID: 4517625a-5fc0-4dff-9bcb-c7c6d44fd492) (GameId: 143f5d75-6674-4bac-8d31-598b407c1f75) at (-266.5, 26.4875, 531.5)
[30Sep2026 21:49:28.547] [Server thread/DEBUG] [xiao.battleroyale.BattleRoyale/]: Clear old game entity object: entity.mts.builder_existing (UUID: 68eef07f-f227-4063-8cc3-374b69f95d19) (GameId: 143f5d75-6674-4bac-8d31-598b407c1f75) at (-266.5, 26.4875, 531.5)
[30Sep2026 21:49:28.548] [Server thread/DEBUG] [xiao.battleroyale.BattleRoyale/]: Clear old game entity object: entity.mts.builder_existing (UUID: 405f8e2b-9764-49b6-b014-a0bdd0b64636) (GameId: 143f5d75-6674-4bac-8d31-598b407c1f75) at (-266.5, 26.4875, 531.5)
[30Sep2026 21:49:28.555] [Server thread/ERROR] [net.minecraft.world.item.ItemStack/]: Tried to load invalid item: 'Unknown registry key in ResourceKey[minecraft:root / minecraft:item]: lrtactical:throwable'
[30Sep2026 21:49:28.555] [Server thread/ERROR] [net.minecraft.world.item.ItemStack/]: Tried to load invalid item: 'Item must not be minecraft:air'
[30Sep2026 21:49:28.556] [Server thread/ERROR] [net.minecraft.world.item.ItemStack/]: Tried to load invalid item: 'Item must not be minecraft:air'
[30Sep2026 21:49:28.556] [Server thread/ERROR] [net.minecraft.world.item.ItemStack/]: Tried to load invalid item: 'Unknown registry key in ResourceKey[minecraft:root / minecraft:item]: lrtactical:throwable'
[30Sep2026 21:49:28.557] [Server thread/ERROR] [net.minecraft.world.item.ItemStack/]: Tried to load invalid item: 'Unknown registry key in ResourceKey[minecraft:root / minecraft:item]: lrtactical:throwable'
[30Sep2026 21:49:28.561] [Server thread/ERROR] [net.minecraft.world.item.ItemStack/]: Tried to load invalid item: 'Unknown registry key in ResourceKey[minecraft:root / minecraft:item]: lrtactical:throwable'
[30Sep2026 21:49:28.561] [Server thread/ERROR] [net.minecraft.world.item.ItemStack/]: Tried to load invalid item: 'Item must not be minecraft:air'
[30Sep2026 21:49:28.561] [Server thread/ERROR] [net.minecraft.world.item.ItemStack/]: Tried to load invalid item: 'Item must not be minecraft:air'
[30Sep2026 21:49:28.562] [Server thread/ERROR] [net.minecraft.world.item.ItemStack/]: Tried to load invalid item: 'Item must not be minecraft:air'
[30Sep2026 21:49:28.608] [Server thread/INFO] [net.minecraft.server.MinecraftServer/]: [XiaoColorful: 刷新物资及实体刷新方块共14个]
[30Sep2026 21:49:28.608] [Server thread/INFO] [xiao.battleroyale.BattleRoyale/]: Loot generation batch finished. Total refreshed: 14
```

# 

实体没刷出来，而且日志里没有 MTS 的报错——这更像**配置在加载阶段就被拒了**（`EntityEntry` 的构造函数里就调 `NBTUtils.stringToNBT`，抛异常会让整条 entry 被丢弃，报错在配置加载时而不是刷物资时）。

让我查解析器和 `WrapperNBT` 的取键语义。

`stringToNBT` 解析失败**不抛异常**，只 warn 并返回空 tag——那样 `EntityData.getEntity` 里的 `!this.nbt.isEmpty()` 就是 false，`load()` 整个不调用，载具变成一个没有 `entityid` 的空壳，下一 tick 被 MTS `discard()`。症状和你看到的完全吻合，而且 MTS 的报错在 tick 时，比你的日志片段晚一点。

让我再确认构造阶段有没有会因为缺键抛异常的地方。

构造阶段没有会因缺键抛异常的地方——所有 `data.getX()` 都是缺省安全的。所以问题大概率不在这几个字段本身。

让我先把最小 NBT 拼成一条 `/summon` 命令，好做隔离测试。

最小版和之前那版的差别其实很小，我需要先隔离出问题在哪一层。给你一条对照测试。

先说明：日志里那 4 个被清的 `builder_existing` 都带 GameId（`143f5d75`）且全在 scout 的坐标上——说明**上一版配置确实被 CBR 刷出来过**，所以最小版是主要嫌疑。但我逐行看过构造路径，删掉的那些键应该都是缺省安全的，所以也要排除 CBR 侧（文件位置/没重载配置）。

## 隔离测试

把你之前 `/summon` 的写法套上最小 NBT 跑一条（坐标我改了一点，避免和旧车重叠）：

见附件 `summon_cmd.txt`。

- **车出来了** → NBT 没问题，问题在 CBR 侧：文件是否放在 `config/battleroyale/loot/entity_spawner/`（不是 `loot_spawner/`）、配置有没有重载、刷方块是不是 entity spawner 类型
- **车没出来** → 就是 NBT 的问题，接着看下面

## 日志里搜这四个

| 字符串 | 含义 |
|---|---|
| `Failed to parse NBT` | SNBT 解析失败（`NBTUtils.stringToNBT` 只 warn 不抛，返回空 tag） |
| `Failed to load entity on builder from saved NBT` | MTS 恢复时抛异常 → `discard()` |
| `Could not load part from NBT` | 部件加载失败（只会少部件，不会整车消失） |
| `Failed to deserialize loot entry` | CBR 整条配置被丢弃 |

## 为什么报错不在你贴的窗口里

两条候选路径都以 MTS `discard()` 收尾，而 `discard()` 发生在**实体第一个 tick**：

1. SNBT 解析失败 → `stringToNBT` 返回空 tag → `EntityData.getEntity` 里 `!this.nbt.isEmpty()` 为 false → `entity.load()` **根本不执行** → 空壳没有 `entityid` → tick 时 `entityMap.get("")` 返回 null → NPE → discard
2. NBT 解析正常但恢复过程抛异常 → catch → discard

两种都要等到 tick 才报，而你的日志正好断在 `21:49:28.608`（batch finished 那一刻）——再往后一两行就该有。

把窗口往后拉一两秒，或者直接对日志文件 grep 上面那四个字符串，就能定位。

# 

4个日志都没有出现，并且附件没看到，在对话里输出，坐标不要用绝对坐标

# 

附件没发出去，我直接贴。同时——4 个报错都没有，说明解析和恢复都成功了，那车**可能其实刷出来了只是不可见**。日志里那条 `entity.mts.builder_rendering` 值得查。

这条线索更强。日志里那句 `Clear entity with no gameId jsonTag: entity.mts.builder_rendering` 很可疑——MTS 源码里有一句警告，说的正是这个。

CBR 的清理逻辑找到了。看下它的配置键名，好给你精确的改法。

4 个报错都没出现，说明解析和恢复都成功了——那先别猜 NBT 了，得先分清「没刷出来」还是「刷出来了但看不见」。

## 先跑这条（回答你自己的问题）

```
/summon mts:builder_existing ~ ~ ~ {entityid:"EntityVehicleF_Physics",packID:"mtsofficialpack",systemName:"scout",subName:"_black",positionx:-266.5d,positiony:26.4875d,positionz:531.5d,anglesy:9.235870361328123d,batteryCapacity:14.0d,hasOpenTop:1.0d,electricPower:12.0001d,"part_0":{packID:"mtsofficialpack",systemName:"wheellarge",subName:"",part_active:1.0d},"part_1":{packID:"mtsofficialpack",systemName:"wheellarge",subName:"",part_active:1.0d},"part_2":{packID:"mtsofficialpack",systemName:"wheellarge",subName:"",part_active:1.0d},"part_3":{packID:"mtsofficialpack",systemName:"wheellarge",subName:"",part_active:1.0d},"part_4":{packID:"mtsofficialpack",systemName:"seat",subName:"black",part_active:1.0d},"part_5":{packID:"mtsofficialpack",systemName:"seat",subName:"black",part_active:1.0d},"part_7":{packID:"mtsofficialpack",systemName:"scoutbedpickup",subName:"",part_active:1.0d},"part_8":{packID:"mtsofficialpack",systemName:"engineamci4",subName:"",part_active:1.0d},"part_10":{packID:"mtsofficialpack",systemName:"bumpersticker",subName:"_roostair",part_active:1.0d},"part_15":{packID:"mtsofficialpack",systemName:"gloveboxscout",subName:"",part_active:1.0d}}
```

注意：`~ ~ ~` 只决定初始落点，**MTS 在第 2 tick 会把车挪到 NBT 里的 `positionx/y/z`**（就是 -266.5 / 26.4875 / 531.5）。所以要嘛站到那附近，要嘛把那三个数改成你脚下坐标。

## 再跑这条判断到底存不存在

```
/data get entity @e[type=mts:builder_existing,limit=1,sort=nearest]
```

- **返回一堆 NBT** → 车在，只是**看不见** → 是渲染问题，看下面
- **报 No entity was found** → 确实没生成 → 得回到 CBR 那条线查

## 如果车在但看不见

MTS 的 `builder_existing` **自己不渲染**——`ABuilderEntityBase.shouldRenderAtSqrDistance()` 直接 `return false`，注释写着 "Don't render entities, this gets done by the overrider"。真正的渲染锚点是另一个实体 `mts:builder_rendering`（`BuilderEntityRenderForwarder`）。MTS 源码里有一句专门针对这个的警告：

> ENTITY CULLING MOD IS PRESENT. WHITELIST "mts:builder_existing", "mts:builder_rendering", AND "mts:builder_seat". IN CONFIG FILE OR VEHICLES MAY BE CULLED.

而你的日志里正好有：

```
Clear entity with no gameId jsonTag: entity.mts.builder_rendering (UUID: f4732daa-...) at (-284.98, 28.11, 536.06)
```

**CBR 把 `mts:builder_rendering` 当"无 gameId 的杂项实体"清掉了。**

改法在 CBR 的 server/performance 配置（`GeneratorEntry`，键名见 `GeneratorEntryTag`）：

- `removeNoGameidEntity` — 是否清理无 gameId 实体
- `removeEntityWhiteList` — 会被清理的实体（`mts:builder_rendering` 应该在这）
- `removeEntityBlackList` — **豁免名单，命中就永不清除**

`RemoveEntityFilter.shouldRemove` 的逻辑是：命中白名单 且 未命中黑名单 → 清理。所以要把这三个加进 **`removeEntityBlackList`**：

```
mts:builder_existing
mts:builder_rendering
mts:builder_seat
```

（注意语义有点反直觉：`*BlackList` 才是"别删"。）

---

补一句我的判断：服务端那个 `builder_rendering` 其实会**自己 discard**（`BuilderEntityRenderForwarder.baseTick` 里 `if (!level().isClientSide) discard()`），客户端另建自己的实例，所以严格说这条未必是你看不见车的直接原因。但既然实体存在性还没确定，先跑上面两条命令分流，比继续猜 NBT 有效。

# 

```
这个放数据包mcfunction summon不出来，直接打指令长度超限
/cbr loot generate刷掉已有的无gameId的车之后过一会又回来了，应该只清掉了渲染实体
重新改刷新配置NBT，应该不需要指定坐标，无关的都去掉，必要的保留
```

# 

坐标是真删不得——`restoreEntityFromData` 就是 `new EntityVehicleF_Physics(world, null, data.getPackItem(), data)`，位置只能从 `data.getPoint3d("position")` 来，没有 NBT 坐标就是 **(0,0,0)**。让我最后确认一个变量默认值的问题，然后给你配置。

`batteryCapacity` 也能去——`EntityManager:209` 每 tick 都会调 `setVariableDefaults()`，它把 `batteryCapacityVar` 强制设成包定义里的值。最后确认一下补丁要用的 API。

两个 API 都确认了。生成最终配置。

配置已重写到 [immersive_vehicles.json](D:/Temp-File/immersive_vehicles.json)（1032 / 1090 字符）。但**坐标这条得先纠正你**。

## 坐标删不掉——删了就是 (0,0,0)

`BuilderEntityExisting` 恢复时走的是：

```java
entityMap.get(lastLoadedNBT.getString("entityid")).restoreEntityFromData(worldWrapper, data)
```

而 `ItemVehicle.java:74` 注册的工厂是 `new EntityVehicleF_Physics(world, null, data.getPackItem(), data)`，位置**只**来自 `AEntityB_Existing` 构造里的 `data.getPoint3d("position")`。

CBR 那边的 `entity.setPos(spawnPos...)` 只改 MC 外壳实体，而 MTS 每个 tick 都会用自己内部的 `position` 反写外壳（`BuilderEntityExisting.java:88`）。所以没有 NBT 坐标 → 内部位置 (0,0,0) → 第 2 tick 整车跳到世界原点。CBR 算出来的 `range`/落点全被丢掉。

要让"不用写坐标"成立，得给 MTS 打一行补丁。在同一文件的恢复块里，`addPartsPostAddition` 之后加：

```java
// NBT 没带坐标（外部刷新器刷的），采纳调用方放置本体时的位置，而不是退回原点
if (!data.hasKey("positionx")) {
    entity.position.set(position().x, position().y, position().z);
    entity.prevPosition.set(entity.position);
}
```

从存档正常加载时 NBT 一定带 `positionx`，走不到这个分支，行为不变；只有外部刷新器才命中。这样 CBR 的落点就真的生效了。

## 新配置的去留

**去掉**：`positionx/y/z`、`anglesy`、`batteryCapacity`、`spawnedDefaultParts`、`uniqueUUID`、`temp`、`serverDeltaMy`、`selectedBeaconName`、`radio`、`fuelTank`、`inventory`、`variablescount/variablesN`、`body_pitch`、文本键、全部原版实体键。

`batteryCapacity` 是这轮新查出来的：`EntityManager.java:209` **每 tick** 对所有 definable 实体调 `setVariableDefaults()`，里面 `batteryCapacityVar.setTo(definition.motorized.batteryCapacity, false)` 会从包定义强制覆盖，所以 NBT 里这个值根本留不住。

**保留**：`entityid`、`packID`/`systemName`/`subName`、每个 `part_N` 的 `packID`/`systemName`/`subName` + `part_active`、`hasOpenTop`（scout 敞篷状态）、`electricPower:12`（电池电量，缺省是 0；新车默认就是 12）。

`part_active` 严格说也能删（`APart.update()` 每 tick 会按 `partOn` 重算），留着是保证首 tick 之前状态就是"装好"。要极简可以去掉。

scout：

```
{entityid:"EntityVehicleF_Physics",packID:"mtsofficialpack",systemName:"scout",subName:"_black",hasOpenTop:1.0d,electricPower:12.0d,"part_0":{packID:"mtsofficialpack",systemName:"wheellarge",subName:"",part_active:1.0d},"part_1":{packID:"mtsofficialpack",systemName:"wheellarge",subName:"",part_active:1.0d},"part_2":{packID:"mtsofficialpack",systemName:"wheellarge",subName:"",part_active:1.0d},"part_3":{packID:"mtsofficialpack",systemName:"wheellarge",subName:"",part_active:1.0d},"part_4":{packID:"mtsofficialpack",systemName:"seat",subName:"black",part_active:1.0d},"part_5":{packID:"mtsofficialpack",systemName:"seat",subName:"black",part_active:1.0d},"part_7":{packID:"mtsofficialpack",systemName:"scoutbedpickup",subName:"",part_active:1.0d},"part_8":{packID:"mtsofficialpack",systemName:"engineamci4",subName:"",part_active:1.0d},"part_10":{packID:"mtsofficialpack",systemName:"bumpersticker",subName:"_roostair",part_active:1.0d},"part_15":{packID:"mtsofficialpack",systemName:"gloveboxscout",subName:"",part_active:1.0d}}
```

merc230：

```
{entityid:"EntityVehicleF_Physics",packID:"mtsofficialpack",systemName:"merc230",subName:"_black",electricPower:12.0d,"part_0":{packID:"mtsofficialpack",systemName:"wheellarge",subName:"",part_active:1.0d},"part_1":{packID:"mtsofficialpack",systemName:"wheellarge",subName:"",part_active:1.0d},"part_2":{packID:"mtsofficialpack",systemName:"wheellarge",subName:"",part_active:1.0d},"part_3":{packID:"mtsofficialpack",systemName:"wheellarge",subName:"",part_active:1.0d},"part_4":{packID:"mtsofficialpack",systemName:"enginemercedesm102",subName:"",part_active:1.0d},"part_5":{packID:"mtsofficialpack",systemName:"seat",subName:"black",part_active:1.0d},"part_6":{packID:"mtsofficialpack",systemName:"seat",subName:"black",part_active:1.0d},"part_7":{packID:"mtsofficialpack",systemName:"seat",subName:"black",part_active:1.0d},"part_8":{packID:"mtsofficialpack",systemName:"seat",subName:"black",part_active:1.0d},"part_9":{packID:"mts",systemName:"invisible_standing",subName:"",part_active:1.0d},"part_11":{packID:"mtsofficialpack",systemName:"gloveboxscout",subName:"",part_active:1.0d}}
```

## 你那条观察很重要

"CBR 刷掉无 gameId 的车之后过一会又回来了，应该只清掉了渲染实体"——这印证了：**车本体一直在，被清的是 `mts:builder_rendering`**（渲染锚点），客户端随后重建它。

MTS 源码里对此有明确警告（`InterfaceClient.java:407`）：

> ENTITY CULLING MOD IS PRESENT. WHITELIST "mts:builder_existing", "mts:builder_rendering", AND "mts:builder_seat". IN CONFIG FILE OR VEHICLES MAY BE CULLED.

建议在 CBR 的 server/performance 配置（`GeneratorEntryTag`）里把这三个加进 **`removeEntityBlackList`**（命中黑名单 = 不清除；反直觉但 `RemoveEntityFilter.shouldRemove` 就是这么判的），顺便可以考虑关掉 `removeNoGameidEntity`。

这样至少能确定：之前"没刷出来"到底是车没生成，还是生成后被清了渲染锚点。

# 

```
到底能不能通过NBT刷车？是否需要专门做兼容mod做事件刷新类型来刷？
```

# 

## 能，而且不需要兼容模组

关键点是：**你写的这个 NBT 就是 MTS 自己的存档格式**，不是某种旁路。

`BuilderEntityExisting.saveWithoutId` 把 `entityid` 和 MTS 内部实体的整个复合标签写进**同一个** tag；恢复时 `baseTick` 读的正是这一套。所以下面三条路径终点完全一样：

- 存档里读出来
- `/summon mts:builder_existing ... {这一串}`
- CBR 的 `EntityEntry` → `EntityData.getEntity()` → `create()` + `load(nbt)` + `addFreshEntity()`

CBR 这一侧对 MTS 一无所知也不需要知道——`EntityEntry` 是通用的"给任意实体套任意 NBT"。而且 MTS 在 `AEntityA_Base` 里专门做了 UUID 去重，注释写得很明白：

> This lets us copy entities by using the same data blocks for each, but creating a new UUID each time for syncing.

**这个机制就是为"同一份数据块刷多个实体"设计的。** 所以"能不能通过 NBT 刷车"——能，而且这是它支持的用法。

## 你踩的两个坑都不是"能不能刷"的问题

| 现象 | 真正的原因 | 层面 |
|---|---|---|
| 车都在同一个点 | MTS 内部坐标只从 NBT 来，是单向同步；外部 `setPos` 第 2 tick 被顶掉 | MTS |
| "没刷出来" | CBR 把 `mts:builder_rendering` 当无 gameId 杂项清了 → 车本体在但不可见 | CBR 配置 |

两个都是配置/一行补丁的量级，不构成"另做一个刷新类型"的理由。

## 什么时候才值得做兼容模组

只有这些需求才值得：

- **随机化**（每次刷出的车部件/颜色/血量不同）——NBT 是静态的，要做随机得在外面包一层生成 NBT
- **不改 MTS 又要接管落点**——兼容模组可以在实体加入世界后反射写 MTS 内部 `position`（但那比直接给 MTS 打一行脏得多）
- **血量上限**——IV 的上限在包 JSON 里，NBT 只能改 `damage`

单纯"把车刷出来"不值得。

## 但还差一步实证

我们到现在**没有一条日志能证明 loot 真的把车加进了世界**。之前那 4 台带 gameId 的车在 scout 的烤死坐标上，既可能是 CBR 刷的，也可能是你手放的那台被 CBR 纳管了——分不清。

最便宜的确认（避开命令行长度问题）：把 `chance` 改成 `1.0`，用一个 loot 方块放你脚边单独测，刷完立刻跑这条短命令：

```
/data get entity @e[type=mts:builder_existing,limit=1,sort=nearest]
```

- **返回 NBT** → 刷出来了，剩下只是可见性和落点（看返回里的 `positionx/y/z` 是不是 0）
- **报 No entity was found** → 确实没生成，那再回头查 CBR 那条链路

顺带说一句你 mcfunction 的问题：函数文件单行上限是 3 万多字符，1100 字符远不是长度问题。刷不出来更可能是没 `/reload`，或者路径不对——1.21 是 `data/<命名空间>/function/<名字>.mcfunction`（单数 `function`）。

先跑那条 `/data get`，我们就能从"猜"进入"定位"。

# 

```
那也就是不能解决刷在刷新处的问题?
```

# 

**纯配置解决不了。** 要解决只有两条路，都不在 CBR 配置里。

## 为什么纯配置不行

NBT 里的 `positionx/y/z` 是唯一能决定落点的地方，而它被写死在每个 entry 里 → 同一 entry 刷出的车必然落在同一个点。CBR 那边算出来的落点（`range` / `attempts` / 刷新方块坐标）只作用于 MC 外壳实体，而 MTS 每 tick 用自己内部坐标反写外壳（`BuilderEntityExisting.java:88`），第 2 tick 就把它顶掉了。

CBR 也没有可用的"实体已生成"钩子给配置层用——`api/event/loot/generate` 那几个（`CustomGenerateEvent` 等）是 special/custom 协议专用，target 还是 `BlockEntity`，标准 `EntityEntry` 不经过它们。

## 路线 A：改 MTS 一行（推荐）

`mcinterfaceneoforge1211/src/main/java/mcinterface1211/BuilderEntityExisting.java`，`baseTick()` 恢复块里，`entity.world.addEntity(entity);` 之后插：

```java
                    entity = entityMap.get(lastLoadedNBT.getString("entityid")).restoreEntityFromData(worldWrapper, data);
                    entity.world.addEntity(entity);
+                   // NBT 没带坐标 = 外部刷新器刷的，采纳它把我们放到的位置，而不是退回原点。
+                   // 从存档正常加载时 NBT 一定带 positionx，走不到这里，行为不变。
+                   if (!data.hasKey("positionx")) {
+                       entity.position.set(position().x, position().y, position().z);
+                       entity.prevPosition.set(entity.position);
+                   }
                    if (entity instanceof AEntityF_Multipart) {
                        ((AEntityF_Multipart<?>) entity).addPartsPostAddition(null, data);
                    }
```

用到的 API 我都核过了：`Point3D.set(double,double,double)`（`Point3D.java:49`）、`prevPosition` 是 `public final Point3D`（`AEntityB_Existing.java:41`）、`WrapperNBT.hasKey` → `tag.contains`（`WrapperNBT.java:237`）。

打完这个补丁，配置里就能彻底不写坐标（也就是我上一版给的那份），CBR 的 `range` 和刷新方块位置才真正生效。

我倾向这条：**这本来就是 MTS 的设计缺口**——外部刷新器只有 NBT 一条路进来，位置又只认 NBT，等于把外部刷新器的落点能力整个堵死了。而且对存档加载是行为保持的（`Pos` 和 `positionx/y/z` 是一起存的）。

## 路线 B：不改 MTS，做兼容模组

能做，但代价不划算：

- CBR 没有通用的实体生成后钩子 → 只能自己听 NeoForge 的 `EntityJoinLevelEvent`，筛出 `mts:builder_existing`
- 然后**反射**去写 MTS 的 `position` / `prevPosition`（不是 MC 外壳的位置，写外壳没用）
- MTS 一改字段名或访问修饰符就崩

比改一行脏得多，而且要维护两边的版本矩阵。

## 路线 C：不解决

每个车位一条 entry，坐标写死在 NBT 里，`count` 保持 1。能用，但刷新方块放哪儿都一样——本质上是"固定车位表"，`range`/`attempts` 白填。

---

如果你能动 MTS，我建议直接走 A。要我把补丁按你 MTS 仓库的实际行号上下文整理成可直接应用的 diff 吗？还是你先自己试一下这一行？

# 

```
先按CBR-Addon-MachineMax对称地补上Protocol，类名保持相同的VehicleDataProtocol，VehicleGeneratorManager和Generator也是一样的，CustomEventHandler页补上
完全同构，注释相应位置也改，只是Generator和protocol字段留空
```

# 

两个仓库都看到了。让我读完 MachineMax 的全部对称文件。

MTS 那边是 **Forge 1.20.1**（`mods.toml` + `net.minecraftforge.gradle` + Java 17），MachineMax 是 NeoForge——这个差异我会保留。再确认构建参数和入口。

结构清楚了。MTS 那边是 Forge 1.20.1，入口 `CbraMtsForge` 已经在调 `CbraMts.init`，所以只需要补 loot/event 这几层。开始写。

`onReceiveWrongEvent` 是默认方法，Javadoc 里的 `gameId` 字段也确认存在。最后接上注册入口。

补完了。跑一次编译验证——这个 addon 是对着 CBR 0.5.6 / 1.20.1 的 jar 编译的，和我读的 1.21.1 源码不是同一份，值得验一下 API 对得上。

趁编译跑着，做一次归一化对比，确认除了包名/特有内容之外真的同构。

`VehicleGeneratorManager` 和 `CustomEventHandler` 归一化后**逐字节相同**；两个 Protocol 类只差在留空的字段。再核对 Generator 的签名。

补完了，5 个新文件 + 1 处接线。归一化对比结果：`VehicleGeneratorManager` 和 `CustomEventHandler` 与 MachineMax **逐字节相同**，两个 Protocol 类只差在留空的字段，`generateVehicle` 签名一致。

新增：
- [VehicleDataProtocol.java](D:/Github/CBR-Addon-MTS/core/src/main/java/xiao/cbra/mts/common/loot/data/VehicleDataProtocol.java)
- [VehicleDataProtocol056.java](D:/Github/CBR-Addon-MTS/core/src/main/java/xiao/cbra/mts/common/loot/data/VehicleDataProtocol056.java)
- [VehicleGenerator.java](D:/Github/CBR-Addon-MTS/core/src/main/java/xiao/cbra/mts/common/loot/VehicleGenerator.java)
- [VehicleGeneratorManager.java](D:/Github/CBR-Addon-MTS/core/src/main/java/xiao/cbra/mts/common/loot/VehicleGeneratorManager.java)
- [CustomEventHandler.java](D:/Github/CBR-Addon-MTS/core/src/main/java/xiao/cbra/mts/event/custom/CustomEventHandler.java)

修改：
- [CbraMts.java](D:/Github/CBR-Addon-MTS/core/src/main/java/xiao/cbra/mts/CbraMts.java) — `init()` 里加 `CustomEventHandler.registerAll(BattleRoyale.getEventRegister())`，与 MachineMax 一致

留空的只有你说的两处：`VehicleDataProtocol` / `VehicleDataProtocol056` 的载具字段（注释写成"载具字段待定"），以及 `VehicleGenerator.generateVehicle` 的方法体。

## 三个需要你确认的点

**1. 协议版本号我填了 `0.5.6`**，`switch` 分支和类名 `VehicleDataProtocol056` 都跟着它走。这是照抄 MachineMax 的（它 `mod_version=0.5.6`）。但你 MTS 的 `mod_version=0.0.0`——如果协议版本应该跟 addon 版本走，那应该是 `0.0.0` 和 `VehicleDataProtocol000`。一行改动的事，你定。

**2. 没建 `SubPartMixin`。** 你列的四项里没有它，而且它的目标是 `io.github.sweetzonzi.machine_max.*`（MachineMax 的 `SubPart#refreshPartEntity` 会重建部件实体导致丢 gameId），MTS 的部件模型完全不同，不能照搬。另外 MTS 这个 addon 目前没有 `mixins.json`，`mods.toml` 里也没有 `[[mixins]]` 段——真要做的话这两处也要补。

**3. `VehicleGeneratorManager` 照样 `event.setCanceled(true)`。** 完全同构的代价是：一旦有配置写了 `cbramts:` 协议，它会走空实现然后取消事件 → 什么都不刷，同时默认生成也被压掉。在 `generateVehicle` 实现之前，这类条目会是"静默无产出"。如果你希望留空期间退回默认行为，把那行去掉即可。

编译验证还在后台跑（Gradle 冷启动 + 拉依赖比较慢）。这个 addon 是对着 CBR 0.5.6 / 1.20.1 的 jar 编译的，我读的是 1.21.1 源码，API 是否完全对齐得等编译结果——出来我告诉你。

编译通过（exit 0，只有 JVM native-access 警告）。确认一下 class 文件真的产出了。

编译通过，7 个 class 全部产出（含 `VehicleGeneratorManager$VehicleGeneratorManagerHolder` 静态内部类，说明继承 `ICustomEventHandler` 之类的接口实现也都对上了）。

**结论：1.20.1 的 CBR 0.5.6 API 和我写的代码完全对齐**，`ICustomEventHandler.onReceiveWrongEvent`（默认方法）、`CustomEventType.CUSTOM_GENERATE_EVENT`、`CustomGenerateEvent` 的 `getProtocol/getJsonTag/getLootContext/getTarget/getLootData`、`LootGenerator.LootContext` 这些签名在 1.20.1 上都成立，不用担心跨版本差异。

前面的三个确认点仍然待你定夺，都不影响编译：

1. 协议版本号填的是 `0.5.6`（照抄 MachineMax），类名 `VehicleDataProtocol056` 跟着它。要跟 `mod_version=0.0.0` 走的话是 `0.0.0` + `VehicleDataProtocol000`。
2. 没建 `SubPartMixin`（目标是 MachineMax 专有类，且本仓库没有 `mixins.json`／`mods.toml` 的 `[[mixins]]` 段）。
3. `VehicleGeneratorManager` 会 `setCanceled(true)`，所以 `cbramts:` 条目在 `generateVehicle` 实现前是静默无产出（默认生成也被压掉）。

下一步就是把 `VehicleGenerator.generateVehicle` 填上——按之前查的 MTS 侧结论，核心是构造 `BuilderEntityExisting`（`entityid` + `packID`/`systemName`/`subName` + `part_N` 那套），并在落点上补 `entity.position` 的接管。要我接着做吗？

# 

```
必要的字段都补到protocol里，跟README一样，并且注意ResourceLocation用BattleRoyale.getMcRegistry()构造，左值类型用var而不要显式写ResourceLocation，generator生成时的位置设置逻辑也完成
```

# 

README 里 MachineMax 那版的 `jsonTag` 字段是 `vehicleLocation`，MTS 这版目前是空的。先确认我手里 MTS 源码版本和 addon 实际编译用的 jar 是否一致。

jar 找到了。看一下 MTS 侧实际可用的 API 签名。

`spawnEntityInternal` 返回 `BuilderEntityExisting`——正是写 gameId 需要的。看它的可见性和 Builder 的字段。

`spawnEntityInternal` 是 `protected`，拿不到 builder。但有个更好的路子——先确认 README 是否已更新字段。

README 的 `jsonTag` 还是空的。我先确认几个 MTS 常量，好把生成逻辑写对。

CBR 0.5.6 / 1.20.1 的 `getMcRegistry()`、`createResourceLocation`、`getEntityType` 都在。确认一下实际编译用的 jar 版本。

全部 API 都在实际编译依赖（8053316）上确认过了。写代码。

泛型不变性导致的：`PackParser.getItem` 在 `var` 下推断成 `AItemPack<AJSONItem>`，跟 `ItemVehicle` 无从属关系，`instanceof` 就编译不过。用 `AItemPack<?>` 声明即可。

编译通过。

## 改了什么

**协议字段**（[VehicleDataProtocol.java](D:/Github/CBR-Addon-MTS/core/src/main/java/xiao/cbra/mts/common/loot/data/VehicleDataProtocol.java)）

```java
// 载具ResourceLocation
public final @NotNull ResourceLocation vehicleLocation;

// 载具子定义名
public final @NotNull String subName;
```

**解析**（[VehicleDataProtocol056.java](D:/Github/CBR-Addon-MTS/core/src/main/java/xiao/cbra/mts/common/loot/data/VehicleDataProtocol056.java)）——按你的要求用 mcRegistry 构造、左值 `var`：

```java
var vehicleLocation = BattleRoyale.getMcRegistry().createResourceLocation(vehicleLocationString);
```

**生成逻辑**（[VehicleGenerator.java](D:/Github/CBR-Addon-MTS/core/src/main/java/xiao/cbra/mts/common/loot/VehicleGenerator.java)）已经填完。

## 位置问题在这里被解决了

这是关键点：**addon 在运行时构造 NBT，所以能把实际刷新坐标写进 `positionx/y/z`**。

之前纯 CBR 配置做不到，是因为 `EntityEntry` 用的是配置里烤死的静态 NBT。现在流程是：

1. `targetBlockEntity.getBlockPos()` → 落点 `Vec3(x+0.5, y+1, z+0.5)`
2. 构造 `CompoundTag`：`entityid`（取 `EntityVehicleF_Physics.class.getSimpleName()`）、`packID`/`systemName`（从 RL 拆）、`subName`、**`positionx/y/z` = 本次落点**
3. `mcRegistry.getEntityType("mts:builder_existing")` → `entityType.create(level)` → `load(data)` → `addFreshEntity`
4. `addGameId(entity, lootContext.gameId)`

因为 MTS 恢复内部实体时位置只认 NBT，而这次 NBT 里的坐标就是我们算出来的——**不需要改 MTS**，之前那条一行补丁的方案可以作废。

同时用 `PackParser.getItem(packID, systemName, subName)` 做了存在性校验，未知载具直接 debug 日志返回，不会留个被 MTS 默默丢弃的空壳。

## 四点需要你确认

**1. `subName` 是我加的。** README 的 `jsonTag` 目前还是空的，MachineMax 那版只有 `vehicleLocation`。加 `subName` 是为了能刷出 `_black` 这种子定义变体（你 F3+I 那两台都是 `subName: "_black"`）。只想要 `vehicleLocation` 的话删掉即可。

**2. 有一处用不了 `var`。** `PackParser.getItem` 是双泛型方法，`var` 会推断成 `AItemPack<AJSONItem>`，与 `ItemVehicle` 因泛型不变性无从属关系，`instanceof` 直接编译不过。所以那行必须是 `AItemPack<?> packItem = ...`（其余左值都是 `var`）。

**3. 电池和燃料没管。** 没写 `electricPower`，所以载具刷出来电池是空的，加上没燃料——继承来的 Javadoc 那句"刷出来的就能直接开"目前**不成立**。要让它成立需要补 `electricPower`（新车是 `batteryCapacity * 0.85715`）和燃料。

**4. README 的 `jsonTag` 还是空的**，建议同步补上字段说明，否则和代码对不上。

要我把 3 和 4 一起做掉吗？

# 

```
README要补充，并且mts的字段应该不是叫vehicleLocation，protocol改成mts的名称
只要ResourceLocation左值用var就行，其他的可以不用var
燃料先不管
```

# 

这里有个我不该猜的命名问题——我先确认，避免又白改一轮。「mts的字段」和「mts的名称」有几种读法，而且会连带决定 README 怎么写。

明白了：三个 MTS 原生键名 + protocol 保持 `cbramts:0.5.6`。改代码。

README 里 JSON 块的缩进要先看清（制表符还是空格）。

控制台编码打不出中文，改用脚本做替换（只输出 ASCII 诊断），再靠 `git diff` 验收。

README 两处都补上了。看一下结果，顺便编译。

都按你说的改完了，编译通过。

## 改动

**协议字段改成 MTS 自己的三个键名**（[VehicleDataProtocol.java](D:/Github/CBR-Addon-MTS/core/src/main/java/xiao/cbra/mts/common/loot/data/VehicleDataProtocol.java)）

```java
public final @NotNull String packID;
public final @NotNull String systemName;
public final @NotNull String subName;
```

[VehicleDataProtocol056.java](D:/Github/CBR-Addon-MTS/core/src/main/java/xiao/cbra/mts/common/loot/data/VehicleDataProtocol056.java) 直接从 jsonTag 读这三个键，不再构造载具 ResourceLocation。protocol 字符串保持 `cbramts:0.5.6`，`getConfigFromProtocol` 仍然比较 `CbraMts.MOD_ID`。

**`var` 只剩一处**——就是那个 ResourceLocation 左值：

```
VehicleGenerator.java:48:  var builderEntityRl = mcRegistry.createResourceLocation(BUILDER_ENTITY);
```

其余左值都改成显式类型（`IMcRegistry` / `AItemPack<?>` / `EntityType<?>` / `Entity` / `Vec3` / `BlockPos`）。

**README 双语都补了**（[README.md](D:/Github/CBR-Addon-MTS/README.md)）：中英文各自加了字段说明和填好的 jsonTag 示例。

```json
{
	"lootType": "event",
	"protocol": "cbramts:0.5.6",
	"jsonTag": {
		"packID": "mtsofficialpack",
		"systemName": "scout",
		"subName": "_black"
	}
}
```

燃料按你说的没动。

## 还剩一件事

`VehicleGenerator` 的 Javadoc 里那句「刷出来的就能直接开」**目前不成立**——没写 `electricPower`，载具电池是空的，加上没燃料，实际上开不走。

要么补 `electricPower`（新车 = `batteryCapacity * 0.85715`，由 `AEntityVehicleE_Powered.BATTERY_DEFAULT_CHARGE` 给出）、要么把那句注释先删掉免得误导。你定。

# 

```
protocol还要给parts列表，之前F3+I里每个轮子和座位都是一个parts
像这样
parts: {
    "part_1": ""
}
electricPower和燃料看下能不能直接查当前准备刷的车的配置，如果没填就默认用最大值
写完后把D:\Temp-File\下的模板也改成这个protocol的写法
```

# 

三件事：加 `parts`、电池/燃料从车辆配置取、然后把模板改成新写法。先查电池/燃料能查到什么。

`AEntityVehicleE_Powered:259` 已经有一段"挑最猛的燃料"的现成逻辑。看完整上下文，顺便确认字段名。

`AEntityVehicleE_Powered.addPartsPostAddition` 里已经有一段**自动加油**逻辑——但只在 `data == null` 且包定义了 `defaultFuelQty` 时触发。再看几处就能定方案。

这里有两个会同时决定代码、README 和模板的设计点，我先定下来再动手。

先说燃料的现状：MTS **自己就有自动加油**逻辑（`AEntityVehicleE_Powered.addPartsPostAddition:250`），规则是"取有效引擎 `fuelType` 下效力最高、且 `isFluidValid` 的流体，加到 `defaultFuelQty`"。但它只在 `data == null` 且包设了 `defaultFuelQty` 时触发——我们走的是 NBT 路线（`data != null`），所以被跳过了。

这里有个坑：引擎类型可能是 ELECTRIC（要 `electricity` 而不是燃料），而 protocol 的 `parts` 又能覆盖引擎，所以"该加什么"取决于最终装的是哪个引擎。

两个都定了。`parts` 是 SNBT 字符串（用 CBR 的 `NBTUtils.stringToNBT` 解析），燃料/电池自动 + 可选覆盖字段。最后确认默认部件怎么走。

API 都齐了。确认 event 条目的 JSON 键名，然后开写。

两个决定都清楚了。开始写代码。

编译通过。现在把模板改成 protocol 写法。

模板已生成（scout 10 个部件、merc 11 个）。最后补 README 的新字段说明。

全部完成，编译通过。

## 代码

**`parts`**（[VehicleDataProtocol056.java](D:/Github/CBR-Addon-MTS/core/src/main/java/xiao/cbra/mts/common/loot/data/VehicleDataProtocol056.java)）

jsonTag 里的 `parts` 是 SNBT 字符串，用 CBR 的 `NBTUtils.stringToNBT` 解析成 `CompoundTag`：

```java
CompoundTag parts = NBTUtils.stringToNBT(JsonUtils.getJsonString(jsonTag, PARTS, ""));
```

生成时直接铺进载具 NBT（[VehicleGenerator.java](D:/Github/CBR-Addon-MTS/core/src/main/java/xiao/cbra/mts/common/loot/VehicleGenerator.java)）：

```java
data.merge(protocol.parts);   // part_N
data.putString("entityid", ...);   // 载具自身字段后写，防止被部件数据覆盖
```

因为键名就是 MTS 存档里的 `part_序号`，F3+I 里的 `part_N` 可以直接搬过来。缺的字段（`part_active`/`uniqueUUID` 等）MTS 会补默认值。

**电池 / 燃料**——自动，协议里可覆盖：

| 字段 | 留空时 |
|---|---|
| `electricPower` | `batteryCapacity * BATTERY_DEFAULT_CHARGE`（充满） |
| `fuel` | 找最终装上的引擎，按它 `fuelType` 挑效力最高且 `isFluidValid` 的流体；ELECTRIC 引擎直接用 `electricity` |
| `fuelQty` | `motorized.fuelCapacity`（加满） |

引擎判定优先看 protocol 的 `parts`（可能覆盖引擎），其次看车辆包各槽位的 `defaultPart` / `conditionalDefaultParts`。取流体的规则和 `AEntityVehicleE_Powered.addPartsPostAddition` 完全一致，所以不会出现"MTS 自己选得出、我们选不出"的偏差。

## 模板与文档

[D:/Temp-File/immersive_vehicles.json](D:/Temp-File/immersive_vehicles.json) 已改成 `lootType: event` + `protocol: cbramts:0.5.6`，两辆车各自带完整 parts（scout 10 个 / merc 11 个），和你 F3+I 的拼装一致。如果只想要包默认组装，把 `parts` 整个删掉即可。

[README.md](D:/Github/CBR-Addon-MTS/README.md) 中英文都补了这四个字段的说明和 `parts` 的转义示例。

## 两点提醒

**1. 用 `<= 0` 表示"未指定"，所以没法显式刷一辆没油/没电的车。** 想让某辆车空油箱，目前表达不出来（删掉字段等于自动加满）。要支持的话得换成可空类型（`Double` / `Optional`），你说一声我改。

**2. 多个引擎只取第一个。** 如果车辆默认部件里有多个不同类型的引擎（比如油电混动），只按先找到的那个决定燃料；`parts` 显式指定引擎时同样只取第一个。

# 

```
parts改用JsonObject，效果应该是这样：
"parts": {
    "part_0": "{packID:\"mtsofficialpack\",systemName:\"wheellarge\",subName:\"\"}"
    "part_1": ""
}
然后在mod里把他拼起来
```

# 

`parts` 改成 JsonObject，每个槽位一个 NBT 字符串，mod 里拼装。

模板已换成对象形式。最后同步 README 的示例和字段说明。

改完了，编译通过。

## 改动

**`parts` 变成 JsonObject**（[VehicleDataProtocol056.java](D:/Github/CBR-Addon-MTS/core/src/main/java/xiao/cbra/mts/common/loot/data/VehicleDataProtocol056.java)），mod 里逐个拼装：

```java
private static @NotNull CompoundTag getParts(@NotNull JsonObject jsonTag) {
    CompoundTag parts = new CompoundTag();
    JsonObject partsJson = JsonUtils.getJsonObject(jsonTag, PARTS, null);
    if (partsJson == null) {
        return parts;
    }
    for (String partKey : partsJson.keySet()) {
        CompoundTag partData = NBTUtils.stringToNBT(JsonUtils.getJsonString(partsJson, partKey, ""));
        if (!partData.isEmpty()) {
            parts.put(partKey, partData);
        }
    }
    return parts;
}
```

键就是 `part_序号`，值是那个部件的 NBT 字符串，拼出来的 `CompoundTag` 直接 `merge` 进载具 NBT——所以和你写的形式一致：

```json
"parts": {
    "part_0": "{packID:\"mtsofficialpack\",systemName:\"wheellarge\",subName:\"\"}"
}
```

**空值等于跳过**：`NBTUtils.stringToNBT("")` 返回空 CompoundTag，我判空后不写入。所以你例子里那个 `"part_1": ""` 和不写 `part_1` 效果一样——该槽位交给包里的默认部件。

## 同步更新

[D:/Temp-File/immersive_vehicles.json](D:/Temp-File/immersive_vehicles.json) 已重新生成成对象形式（scout 10 个槽位、merc 11 个），[README.md](D:/Github/CBR-Addon-MTS/README.md) 中英文的示例和 `parts` 字段说明也一并改了：

```
- parts：可选，按槽位指定部件，键为 part_序号，值为该部件的NBT字符串，留空则用包里的默认部件
```

上一轮提的两点限制仍然存在（`fuelQty`/`electricPower` 用 `<= 0` 表示未指定、多引擎只取第一个），需要的话再说。

# 

```
看一下subName在MTS里是否是非必要字段，如果这个配置为空则随机从可选的subName里随机取一个
```

# 

先看下你改后的 generator，同时查 MTS 里 subName 的语义。

`getItem` 的键是 `systemName + subName`，空 subName 是合法键。再看子定义列表存在哪、以及注册逻辑。

`getItem` 的键是 `systemName + subName` 精确匹配——看看变体信息怎么取。

`subDefinition` 是 public，能直接读到。结论先说：

**`subName` 不是"可选带回退"的字段。** `PackParser.getItem` 是 `packItemMap.get(packID).get(systemName + subName)` 的**精确键匹配**，而注册时每个变体都是 `systemName + subDefinition.subName`。所以空 subName 只有在"包确实注册了一个 subName 为空的变体"时才查得到；像你的 scout/merc230 只定义了 `_black` 这类命名变体，传空就是查不到 → null。

按你说的实现：配置留空时从该载具已注册的变体里随机取一个。

编译通过。README 那句"可为空"现在有歧义了，同步一下。

改完了，编译通过。

## 先回答你的问题：`subName` 不是"可选带回退"的字段

`PackParser.getItem` 是**精确键匹配**：

```java
return (PackItem) packItemMap.get(packID).get(systemName + subName);
```

而注册时（`parseAllDefinitions`）每个变体写进 map 的键是：

```java
packItems.put(item.definition.systemName + subDefinition.subName, item);
```

所以空 subName 只有在**包确实注册了一个 subName 为空的变体**时才查得到。你的 scout/merc230 只定义了 `_black` 这类命名变体，传空 → `null`。

## 实现

[VehicleGenerator.java](D:/Github/CBR-Addon-MTS/core/src/main/java/xiao/cbra/mts/common/loot/VehicleGenerator.java) 加了 `resolveVehicle`：

```java
private static @Nullable ItemVehicle resolveVehicle(VehicleDataProtocol protocol, LootGenerator.LootContext lootContext) {
    if (!protocol.subName.isEmpty()) {
        AItemPack<?> packItem = PackParser.getItem(protocol.packID, protocol.systemName, protocol.subName);
        return packItem instanceof ItemVehicle vehicle ? vehicle : null;
    }

    List<ItemVehicle> variants = getVehicleVariants(protocol.packID, protocol.systemName);
    if (variants.isEmpty()) {
        return null;
    }
    float roll = lootContext.random.get();
    int index = Math.min(Math.max((int) (roll * variants.size()), 0), variants.size() - 1);
    return variants.get(index);
}
```

变体列表用 `PackParser.getAllItemsForPack(packID, false)` 过滤 `vehicle.definition.systemName` 得到——同一载具的所有子变体共享同一个 `JSONVehicle` 主定义，所以这么筛是准的。随机源用的是 **`lootContext.random`**（刷新上下文自带的 `Supplier<Float>`）而不是 `new Random()`，这样和刷新种子保持一致、可复现。

之后一律用实际生效的 `vehicleItem.subDefinition.subName`：写进 NBT、日志、燃料判定的日志都改了。

README 里 subName 的说明也同步成"留空则从该载具已注册的变体里随机取一个"。

## 一点提醒

**留空就没法精确指定"就用空 subName 的那个变体"了。** 如果某个包同时注册了空 subName 的变体和命名变体，留空会把这些一起纳入随机池；要指定就用精确值。

模板里目前还是 `"_black"`，你要随机变体的话把那行删掉（或留空）即可。

# 

```
算了，改成subName优先读成字符串列表，其次读成String，不在每次generate的时候都查一遍
```

# 

`JsonUtils.getJsonStringList` 正好能用，`getJsonArray` 也是带守卫的。改造。

改完了，编译通过，没有残留的旧引用。

## 改动

**解析**（[VehicleDataProtocol056.java](D:/Github/CBR-Addon-MTS/core/src/main/java/xiao/cbra/mts/common/loot/data/VehicleDataProtocol056.java)）—— 优先列表、其次字符串，**在解析阶段一次性读好**：

```java
private static @NotNull List<String> getSubNames(@NotNull JsonObject jsonTag) {
    List<String> subNames = JsonUtils.getJsonStringList(jsonTag, SUB_NAME);
    if (!subNames.isEmpty()) {
        return subNames;
    }
    String subName = JsonUtils.getJsonString(jsonTag, SUB_NAME, "");
    return subName.isEmpty() ? List.of() : List.of(subName);
}
```

`JsonUtils.getJsonStringList` 内部走的是带守卫的 `getJsonArray`（不是数组就返回默认），所以两种写法都能吃：

```json
"subName": "_black"
"subName": ["_black", "_red", "_white"]
```

**协议字段**（[VehicleDataProtocol.java](D:/Github/CBR-Addon-MTS/core/src/main/java/xiao/cbra/mts/common/loot/data/VehicleDataProtocol.java)）从 `String subName` 改成 `List<String> subNames`。

**生成**（[VehicleGenerator.java](D:/Github/CBR-Addon-MTS/core/src/main/java/xiao/cbra/mts/common/loot/VehicleGenerator.java)）—— 去掉了每次 generate 扫包的那套（`getVehicleVariants` / `getAllItemsForPack`），现在只是在候选列表里取一个：

```java
private static @Nullable ItemVehicle resolveVehicle(VehicleDataProtocol protocol, LootGenerator.LootContext lootContext) {
    int size = protocol.subNames.size();
    String subName = "";
    if (size > 0) {
        float roll = lootContext.random.get();
        subName = protocol.subNames.get(Math.min(Math.max((int) (roll * size), 0), size - 1));
    }
    AItemPack<?> packItem = PackParser.getItem(protocol.packID, protocol.systemName, subName);
    return packItem instanceof ItemVehicle vehicle ? vehicle : null;
}
```

列表为空 → `subName = ""`，不再回退去猜。随机仍然用 `lootContext.random`，和刷新种子一致。实际生效的 subName 依旧从 `vehicleItem.subDefinition.subName` 取，写进 NBT 和日志。

README 的 subName 说明也同步成"可取字符串或字符串列表；给列表则每次刷新随机取一个，留空则用空 subName"。

模板里还是 `"_black"`，要随机变体就把那儿改成数组。

# 

```
1.21.1merge了没问题，完成26.1.x移植，VehicleGenerator需要改，ValueInput可以看我CGC 26.1.x的NBTUtils封装
```

# 

切到 26.1.x 移植。先看两个仓库的现状。

这是我的疏忽——26.1.x 的 NBT API 变了（`Identifier`、`ValueInput/ValueOutput`、`getString` 返回 Optional）。先看 26.1.x 那边现在是什么状态，同时编译一遍把差异全列出来。

MTS 26.1.x 的 API 和 1.20.1 一致。看编译结果。

编译还在跑（26.1.x 首次要下 NeoForge 并解包）。先查清关键点——你提到 `ValueInput`，说明 26.1.x 里实体的 NBT IO 应该改成 `ValueInput` 了。

MC 26.1.x 的源码就在 CGC 的 build 目录里。查关键签名。

`Entity.load` 已改成 `ValueInput`。看 `TagValueInput` 怎么构造，以及 MTS 26.1.x 的外壳是怎么接的。

MTS 26.1.x 保留了 `lastLoadedNBT` 机制，但 `ABuilderEntityBase` 不再覆写 `load(CompoundTag)`。看 `BuilderEntityExisting` 怎么接的。

`MTS_NBT_KEY` 是 26.1.x 新加的，说明那边的存取方式变了。找 MTS 的 26.1.x 源码。

关键发现：26.1.x 里 MTS 实体数据**嵌在 `mts_entity` 键下**了。确认一下。

确认了：26.1.x 里 MTS 数据嵌在实体的 `mts_entity` 键下（`readAdditionalSaveData` 读它并赋给 `lastLoadedNBT`）。看编译结果。

`Entity.load` 确实会调 `readAdditionalSaveData`。查最后一处 API，然后写移植。

CBR 侧完全没变。停掉后台编译，直接写移植。

CBR 26.1.x 自己的刷实体就是这个写法。看它用哪个 ProblemReporter。

CBR 用的是 `ProblemReporter.DISCARDING` + `EntitySpawnReason.COMMAND`。写 26.1.x 版。

网络问题（Gradle 想重新拉 version manifest）。先确认那个符号错误——我怀疑是 `getAllKeys` 改名了。

`getAllKeys()` 在 26.1.x 已改名为 `keySet()`（CGC 的 NBTUtils 里也是这么用的）。修掉。

编译通过。确认 class 产出。

26.1.x 移植完成，编译通过，7 个 class 全部产出。

## 改动只在 [VehicleGenerator.java](D:/Github/CBR-Addon-MTS-26.1.xneoforge/core/src/main/java/xiao/cbra/mts/common/loot/VehicleGenerator.java)，四个点

**1. MTS 数据要嵌在 `mts_entity` 键下** —— 这是 26.1.x 最重要的行为变化。我从 MTS 的 `mcinterface261.ABuilderEntityBase` 反编译出来的：

```java
protected void readAdditionalSaveData(ValueInput input) {
    input.read("mts_entity", CompoundTag.CODEC).ifPresent(tag -> this.lastLoadedNBT = tag);
}
```

26.1.x 起 MTS 不再覆写 `load(CompoundTag)` 直接把整个 tag 当 `lastLoadedNBT`，而是从实体 NBT 的 `mts_entity` 子键里取。所以载荷要包一层：

```java
CompoundTag data = new CompoundTag();
CompoundTag mtsData = new CompoundTag(); {
    mtsData.merge(protocol.parts);
    mtsData.putString(ENTITY_ID, ...);
    ...
    applyPower(mtsData, vehicleItem, protocol);
}
data.put(MTS_NBT_KEY, mtsData);   // MTS_NBT_KEY = "mts_entity"
```

`MTS_NBT_KEY` 在 MTS 里是 `protected`，拿不到，只能在 addon 里硬编码常量。

**2. `Entity.load` 改成吃 `ValueInput`**，照 CBR 26.1.x 自己刷实体的写法：

```java
Entity entity = entityType.create(lootContext.serverLevel, EntitySpawnReason.COMMAND);
ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING,
        lootContext.serverLevel.registryAccess(), data);
entity.load(input);
```

`EntityType.create(Level)` 单参重载没了，必须带 `EntitySpawnReason`；`ProblemReporter.DISCARDING` 和 `EntitySpawnReason.COMMAND` 都是 CBR 那边用的同一个选择（反编译 `EntityData.getEntity` 确认的）。

**3. NBT 取值改 Optional** —— 按你 CGC `NBTUtils` 里那套写法：

```java
CompoundTag partData = protocol.parts.getCompound(partKey).orElse(null);
...partData.getString(PACK_ID).orElse("")
```

**4. `getAllKeys()` → `keySet()`** —— 这个是编译报错暴露的，`CompoundTag` 里方法改名了（你 CGC 的 NBTUtils 用的也是 `keySet()`）。

## 没要改的

`createResourceLocation` 的返回类型从 `ResourceLocation` 变成了 `Identifier`，但因为当初按你说的左值用 `var`，这行**零改动**。

`VehicleDataProtocol` / `VehicleDataProtocol056` / `VehicleGeneratorManager` / `CustomEventHandler` / `CbraMts` 都没动 —— 它们不碰 MC 的 NBT API。README 是从 1.20.1 合并过来的，内容本来就已经是新字段的版本，也不用改。

## 一点提醒

这次编译是加了 `--offline` 才过的——不带的话 Gradle 会在 configure 阶段去 `piston-meta.mojang.com` 拉 version manifest 然后失败（网络被挡）。第一次成功那次是缓存在。你本地正常联网的话应该没这个问题。
