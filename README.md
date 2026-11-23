# Forestry Sable Compat

修复 **Forestry: Community Edition** 的方块在 Sable 物理化 / 解除物理化（sub-level 装配 / 拆回方块）时错误掉落（并因此复制）内容物的问题；同时让物理结构上的蜂房按结构**当前所在世界位置**判定群系。

- Minecraft **1.21.1** / NeoForge **21.1.230**
- 依赖 Forestry CE **3.0.0-alpha9+**、Sable **2.0.x**
- 服务端 mod，单人/专用服务器都生效

## 问题

Sable 的 `SubLevelAssemblyHelper.moveBlocks()` 在把方块搬进（或搬出）sub-level 时，顺序是：

1. 把方块实体的完整 NBT 保存下来（内容物也在这个 NBT 里）；
2. 调用 `Clearable.tryClear(blockEntity)`，本意是"清空原方块实体，避免拆方块时掉落"；
3. 拆掉原位置的方块 —— 这会触发方块的 `onRemove`。

Forestry 的 `TileForestry` 通过 `Container` 实现了 `Clearable`，但它的 `clearContent()` 是**空方法**（`ForestryCE:src/main/java/forestry/core/platform/tile/TileForestry.java:254`）。于是第 2 步什么也没做，第 3 步里：

- `BlockBase.onRemove()` 调 `Containers.dropContents()`，把机器库存掉在原位置（`ForestryCE:src/main/java/forestry/core/platform/block/BlockBase.java:134-150`）；
- 同一个 `onRemove` 还调 `InventoryUtil.dropSockets()`，把电路板从 socket 掉出来（电路板是独立的 `InventoryAdapter`，不是 `Container` 的一部分）。

而第 1 步保存的 NBT 已经进了 sub-level 里的新方块实体。结果就是**同一份内容物既掉在地上、又留在方块里**；反方向（解除物理化）走的是同一个 `moveBlocks`，同样会复制。

> 这也是为什么现成的通用修复 mod 不够用：`Sable Assembly Fix` 只处理"没有实现 `Clearable`"的方块实体，而 Forestry **实现了**（`Container extends Clearable`），只是实现是空的；`Sable Fix Mod` 会清 `Container`/`IItemHandler`，能修主库存，但看不到 Forestry 的电路板 socket。

## 修复

### 单方块与多方块成员：清空 `Clearable`

内容物走 Sable 给原版方块实体用的同一个扩展点（`Clearable`，参见 Sable 自己的 `AbstractFurnaceBlockEntityMixin`）：

| Mixin | 作用 |
|---|---|
| `TileForestryMixin` | 在 `TileForestry.clearContent()` 开头清空内部库存 + 所有电路板 socket |
| `MultiblockTileEntityForestryMixin` | 多方块成员覆盖了 `clearContent()`，同样清空（含共享控制器库存 + socket） |

清空逻辑集中在 `ForestryContents.clearBeforeMove()`，幂等，可重复调用。这样：

- Sable 的 `Clearable.tryClear()` 现在真的会清空，`onRemove` 没有东西可掉；
- 内容物完整保留在第 1 步保存的 NBT 里，sub-level 中的机器内容不变；
- 顺带修正了原版 `/setblock ... replace` 对 Forestry 机器不清理内容物的偏差。

### 多方块：让装配目标自己重建控制器

Alveary / Managed Farm 的库存不在成员方块实体上，而在最低成员（holder）托管的 `MultiblockController` 里，跨方块以 `multiblockData` payload 传递。只清空还不够，还有第二个坑：

- Sable 的 plot 和世界**共用同一个 `Level`**（plot 只是远处的坐标），`MultiblockIndex` 也共用；
- 复制过去的 NBT 里 `anchorPos` 仍指向世界里的旧 holder；
- 于是 plot 侧的 `MultiblockValidation.resolveExistingController()` 顺着这个旧锚点找到了**正被搬走的那台控制器**，直接复用它，并清掉每个成员的 stash——payload 刚被读进 stash 就被抹掉，库存凭空消失在 sub-level 里。

`SubLevelAssemblyHelperMixin` 在 Sable 复制 NBT 时（`saveWithFullMetadata` 返回之后、`loadWithComponents` 之前）把多方块方块实体的 `anchorPos` 从**复制的那份**标签里去掉：世界侧方块实体自己的锚点不受影响，plot 侧则必须按自己的结构重新验证、从 payload 里 seed 出新控制器，内容物随之保留。原位置的内容清空仍由 `ForestryContents` 完成，所以世界侧的拆除逻辑也没有东西可掉。

### 群系判定（蜂房 / 简易蜂房 / 蜂房组 / 蜂巢）

Sable 把 sub-level 的方块存在远离世界的 plot 里，并在**装配时**把锚点群系写进 plot chunk。因此 Forestry 直接 `level.getBiome(getBlockPos())` 读到的是装配时的群系；结构开走、旋转、传送到别的群系后都不会变。

本 mod 只改 Forestry 的判定，不碰 Sable。下面这些位置在 Sable plot 内时，会先把 plot 坐标经 `SubLevel#logicalPose()` 投影到世界坐标，再查世界群系；不在 plot 内则完全走原逻辑：

| Mixin | 覆盖的判定 |
|---|---|
| `AbstractBeeHousingBlockEntityMixin` | `getBiome()`：蜂房、简易蜂房 |
| `AlvearyControllerMixin` | `getBiome()`：蜂房组控制器 |
| `HiveBlockEntityMixin` | `getBiome()`：蜂巢 |
| `ClimateProviderMixin` | 所有蜂房/蜂房组的温度、湿度缓存（每 64 tick 重建） |
| `ForestryClimateManagerMixin` | `getBiomeState()`：API / 附加模组使用的直接查询 |

结果是：大结构旋转/移动后，每个蜂房按自己当前的世界位置判定群系；同一结构跨群系时，不同位置的蜂房可以得到不同群系。Sable 自己的 `level.getBiome(plotPos)` 仍返回装配群系，未做修改。

## 验证

`src/test` 是一个独立的 GameTest mod（`forestry_sable_compat_gametest`），用真实方块跑 6 个用例：

1. `inventorySurvivesPhysicalization` —— 离心机库存放 5 个钻石，物理化后：sub-level 里仍是 5 个钻石，原位置**没有**掉落钻石；
2. `socketSurvivesPhysicalization` —— 离心机 socket 放一块电路板，物理化后：sub-level 里电路板还在，原位置**没有**掉落电路板；
3. `contentsSurviveRoundTrip` —— 物理化后再用 `moveBlocks` 反方向搬回原位置，库存和电路板都还在，且没有掉落；
4. `alvearyContentsSurvivePhysicalization` —— 现场搭 3×3×3 Alveary（27 个方块 + 3×3 木台阶），控制器库存放 3 个钻石：物理化后 sub-level 里的控制器仍是 3 个钻石，原位置**没有**掉落；
5. `alvearyContentsSurviveRoundTrip` —— Alveary 物理化后再整组搬回原位置，控制器和内容物都还在，且没有掉落；
6. `apiaryUsesWorldBiomeOfSubLevel` —— 在 Nether 的群系 A 装配蜂房，再把 sub-level 的 pose 移到群系 B：蜂房的 `getBiome()` 和温度/湿度变成 B，而 Sable 的 `level.getBiome(plotPos)` 仍是装配时的 A。

> 说明：`docs/verification/01`–`04` 是加多方块用例之前的日志，`05`/`06` 是当前 6 个用例的完整结果。

结果（完整日志见 [`docs/verification/`](docs/verification/)）：

| 环境 | 库存 | 电路板 | 往返 |
|---|---|---|---|
| Sable 2.0.6，无修复 | ❌ 掉落 5 钻石 | ❌ 掉落电路板 | — |
| Sable + Sable Fix Mod 1.1.0 | ✅ | ❌ 仍掉落电路板 | — |
| **Sable + 本 mod** | ✅ | ✅ | ✅ |
| Sable + 本 mod + Sable Fix Mod 1.1.0 | ✅ | ✅ | ✅ |

多方块（Alveary）两个用例只在安装本 mod 时通过；不装本 mod 时装配不会掉落，但内容物会在 sub-level 里丢失（控制器被 plot 侧复用，见上节）。

运行：

```bash
./gradlew runGameTestServer                    # 本 mod + 通用修复 mod（默认）
./gradlew runGameTestServer -Pwith_sable_fix=false   # 只跑本 mod
```

## 开发环境

| 组件 | 版本 |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.230（ModDevGradle 2.0.140） |
| Parchment | 1.21 / 2024.11.17 |
| Forestry CE | 3.0.0-alpha9（Maven Local） |
| Sable | 2.0.6（Maven Local，jar 内 JarJar 了 companion / Rapier natives / Veil） |
| Patchouli | 1.21.1-93-NEOFORGE（Forestry 的必需依赖） |

本机访问不到 `maven.neoforged.net`，所以：

- `neoforge_local_mirror=../ForestryCE/build/local-m2` 提供 NeoForge 产物；
- `settings.gradle` 默认离线（`-Ponline=true` 才联网）；
- Sable 的 jar/pom 需要预置到 `~/.m2/repository/dev/ryanhcode/sable/sable-neoforge-1.21.1/2.0.6/`，`sable-companion-common-1.21.1:1.6.0` 预置到 `~/.m2/repository/dev/ryanhcode/sable-companion/...`（仅编译期使用，运行期由 Sable 的 JarJar 提供）。

常用命令：

```bash
./gradlew build                 # 产出 build/libs/forestry_sable_compat-1.21.1-0.1.0.jar
./gradlew runGameTestServer     # GameTest 服务端，跑完自动退出
./gradlew runClient             # 开发客户端
```

## 目录

```
src/main/java/com/zhichaoxi/forestrysablecompat/  mod 入口 + 清空逻辑 + 群系投影 + Mixin
src/main/resources/                              neoforge.mods.toml、mixins 配置
src/test/java/.../gametest/                      GameTest 用例（独立 mod）
src/test/resources/                              测试 mod 元数据 + empty 结构模板
docs/verification/                               各配置的 GameTest 日志
libs/                                            可选：通用修复 mod jar（不入库）
```

## 已知限制

- Managed Farm（5×3×5 多方块）没有单独的 GameTest；它和 Alveary 走同一套 `MultiblockTileEntityForestry` 与同一个锚点修复，但没有实测。
- `SubLevelAssemblyHelperMixin` 对所有 `MultiblockTileEntityForestry` 生效（Alveary、Managed Farm 及附属多方块）。它只改 Sable 复制出的 NBT、不动世界侧方块实体，但依赖 Sable 2.0.x "先 `saveWithFullMetadata`、再 `loadWithComponents`" 的顺序；Sable 若改变这段流程需要重新验证。
- `TileForestry.onDropContents()` 的非物品副作用没有处理：`SolarEngineBlockEntity` 会重置太阳能板的连接状态，`TradeStationBlockEntity` 会把交易站从注册表里注销——移动后行为不正确，但不会复制物品。
- 没有用 Sable 的 `sable:silent_assembly_removal` 方块标签方案：它需要枚举方块 ID，而且同样管不到电路板 socket 和多方块 payload，不如 `Clearable` + 锚点修复。
- `require = 1` 的注入点依赖 Sable 2.0.x 的 `moveBlocks` 行为；如果将来 Sable 改了这块流程，需要重新验证（本 mod 依赖声明为 `[2.0.0,)`，未设上限）。
- 群系判定只覆盖 Forestry 的蜂房/蜂房组/蜂巢与 `ClimateProvider`；其他按位置查群系的 Forestry 逻辑（世界生成、树、蝴蝶等）没有改。
- 群系判定按结构当前位置，但 Sable 的 plot chunk palette（草/水颜色等渲染）仍保持装配群系，两者可能不一致；Sable 自己的 `level.getBiome(plotPos)` 也仍是装配群系。
- 天气和“能否看到天空”仍走 Sable 的宿主天气 / plot sky light，本次没有处理；这会影响蜜蜂的 `isRaining()` / `canBlockSeeTheSky()` 判定。
- 群系判定用 `SubLevel#logicalPose()` 投影；如果 Sable 将来把 pose 的语义或 plot 坐标空间改了，需要重新验证。

## 许可

[LGPL-3.0](LICENSE)（GNU Lesser General Public License v3.0，与 Forestry: Community Edition 一致）。

- [`LICENSE`](LICENSE)：LGPL-3.0 完整文本
- [`COPYING`](COPYING)：LGPL-3.0 所引用的 GPL-3.0 完整文本
