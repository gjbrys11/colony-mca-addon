# Colony MCA（开发版）

## 下载 0.4.0-dev

- [附属 mod JAR](https://github.com/gjbrys11/colony-mca-addon/raw/refs/heads/main/downloads/0.4.0-dev/colony-mca-addon-0.4.0-dev.jar)
- [安装压缩包](https://github.com/gjbrys11/colony-mca-addon/raw/refs/heads/main/downloads/0.4.0-dev/colony-mca-addon-0.4.0-dev.zip)
- [源码压缩包](https://github.com/gjbrys11/colony-mca-addon/raw/refs/heads/main/downloads/0.4.0-dev/colony-mca-addon-0.4.0-dev-sources.zip)
- [SHA256 校验值](downloads/0.4.0-dev/SHA256SUMS.txt)

0.4.0 新增城建配置及依赖，不能只替换旧 JAR。请解压安装包，关闭游戏后运行 `tools/Install-GameplayProfile.ps1 -GameDirectory 目标游戏目录`；客户端和服务端都要安装。完整说明见 [城建配置](profile/README.md)。安装器核验下载地址和 SHA256，备份旧文件并预置资源注册脚本。当前为开发版，安装前请备份存档。

Minecraft 1.21.1 NeoForge 的独立附属 mod。必须同时安装 MineColonies、MCA Reborn 及其前置。原始两个源码目录没有修改；所有运行时适配、资源覆盖和构建文件都在本项目内。

对接原则：对话、赠礼、婚恋和交易调用 MCA 自身的消息、数据和处理器，附属 mod 负责实体身份、数据持久化和统一界面桥接。工作、建造、物流、防御、住宅与儿童出生按本项目约定由殖民地管理；不重新编写一套替代 MCA 的社交玩法。

## 固定开发依赖

- Java 21、NeoForge 21.1.233。
- MineColonies 1.1.1403-1.21.1。
- MCA Reborn 发布编号 7.7.36；该下载的 jar 内部版本为 **7.7.36-beta.3+1.21.1**。
- Structurize 1.0.832、BlockUI 1.0.199、Multi-Piston 1.2.51（均为对应 1.21.1 snapshot）。
- Domum Ornamentum 1.0.223 snapshot；开发仓库将其分为 main 和 api 两个构建。
- Pathfinding Edition 1.0.5-fix-6、Tweaks 3.33。
- Material Unification 3.4.1、Material Unification Addon: MineColonies 3.1。
- KubeJS 2101.7.2-build.377、Rhino 2101.2.7-build.85。

当前严格限制两个主 mod 的版本，以免未验证的上游更新破坏 Mixin 接入点。运行时双方都必须安装。上游 jar、Java 和 NeoForge 不打包进附属 jar。

源项目：
- <https://github.com/ldtteam/minecolonies/tree/version/1.21>
- <https://github.com/Luke100000/minecraft-comes-alive/tree/1.21.1>

## 已写入的适配

- 殖民地实体和 AI 继续负责工作、建筑、物流、防御、技能、教育、住宅及领地权限。
- 每个居民持有不加入世界、不运行 AI 的 MCA 数据代理。复用 MCA 的真实模型、分层皮肤、头发、服装及社交关系；世界里仍只有一个居民。
- 右键直接打开殖民地属性主面板。主面板及原有需求、幸福度、家庭、职业页面左侧常驻 MCA 人物栏，原生导航与殖民地属性保留。
- 左侧可滚动查看真实殖民地职业、对当前玩家的好感、情绪、性格、性别、年龄、婚姻、亲属、特征和遗传属性；“社交互动”在同一窗口内提供对话选项、赠送手中物品、交易、生育和行动指令。交易与背包仍使用相应的原生物品容器。
- 对话、送礼、婚恋、家谱与交易接入同一个居民身份。礼物进入殖民地背包，放不下时掉落到居民身旁。
- 跟随、停留暂停普通居民工作，解除后恢复之前的暂停状态；殖民地战斗行为优先。
- NPC 自动婚配、生育与玩家婚后生育统一使用殖民地的人口名额、空余床位、儿童注册、技能继承及成长。MCA 家谱记录双方父母，MCA 外貌基因和特征可继承；不生成婴儿物品。
- 不为家庭成员重新分配住宅。伴侣、子女、父母、兄弟姐妹的同住情况影响幸福度；在线玩家亲属以其重生床是否位于该住宅中判断，离线玩家亲属暂不参与该项评分。
- 关闭 MCA 的普通村民／僵尸替换、独立 MCA 村民生成及感染。普通村庄保留原版村民。
- 死亡仍使用殖民地墓碑。生命法杖或已充能的死神镰刀可对墓碑复活；保留 MCA 身份，物品从实际墓碑取回，防止克隆历史背包。使用镰刀近战杀死居民可收集灵魂。
- MCA 状态、稳定身份和控制状态随 CitizenData 保存，因此可以进入殖民地墓碑快照。复活后的工作、住宅重新分配仍遵循殖民地原生流程。

## 构建与检查

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\tools\Fetch-Dependencies.ps1
.\gradlew.bat build
```

下载地址和 SHA256 记录在 `dependencies.lock.json`。构建产物为 `build/libs/colony-mca-addon-0.4.0-dev.jar`，需要客户端与服务端同时安装。

0.3.0 修复社交计时、交易成长、行动指令与家谱同步，详见 CHANGELOG.md；完整验收边界见 TESTING.md。

0.2.0 将 MCA 属性和对话嵌入殖民地市民窗口。后台刷新人物资料时保留当前页和对话，不反复打开主面板。通信格式已更新，客户端和服务端必须一起替换。

0.1.1 修复了行走动画随渲染帧率加速、管理入口打开临时对话窗口以及已就业居民显示“无业”的问题。管理入口会请求服务器最新的原生市民视图，再打开殖民地属性主面板。实际移动速度继续由殖民地控制。请同时更新客户端和服务端，移除旧版附属 jar。

`build` 包含 9 项纯 Java 住房规则回归检查。`smokeServer` 在独立开发世界执行 59 项检查，涵盖实际 mod 加载、村民转换拦截、MCA 数据代理、行走动画、职业、资料请求和 NBT 保存／恢复，随后自动关闭服务端。测试代码位于 `src/smoke`，不会进入发行 jar。

```powershell
.\gradlew.bat smokeServer
```

如果 NeoForge 仓库不可达，可为 `smokeServer` 传入已下载的对应版本 ModDev 配置 JSON：

```powershell
.\gradlew.bat smokeServer --offline '-PneoForgeConfigFile=绝对路径\neoforge-21.1.233-moddev-config.json'
```

该参数仅影响开发启动。自动测试只绑定 127.0.0.1:25566，不连接或修改已有游戏实例。测试结果见 `build/smoke-result.txt`。

## 当前验证边界

这是开发版，不是经过完整游玩验收的正式版本。统一界面的中文主面板、社交页、嵌入式对话和幸福度页在独立客户端进行渲染检查并截图；视觉夹具使用测试人物数据，不能代替真实玩家与居民的多人和长时间游玩验证。仍需在新测试存档验证角色的所有工作动画、真实婚恋与生育流程、跨区块跟随、各职业物流及战斗，以及墓碑搬运后复活的物品守恒。

视觉测试位于 `src/uiSmoke`，不进入发行 jar。`smokeClient` 仅供开发时使用：它需要 `run/saves/ui-smoke` 测试世界，进入独立客户端后自动构造测试界面，验证导航位置，在 `build/ui-smoke` 保存截图并退出。与真实服务器交互的玩法验证需使用正常游戏流程。

交易职业在首次创建资料时尽量参考殖民地职业；尚未就业的居民按稳定身份选择交易职业。之后保留交易职业与交易进度，不因换工作重置交易。有殖民地工作建筑的成年居民每 12000 个游戏 tick 可补货一次，取代原版工作站检查。

本阶段不提供旧存档迁移，不声称兼容其他 MineColonies 或 MCA 构建。MCA 特有的村庄管理、雇佣兵、家务劳动、感染以及独立婴儿流程不参与殖民地居民的运行。

## 授权与来源

附属项目使用 GPL-3.0-only。MCA 模型及贴图在运行时从已安装 MCA 引用，本项目没有复制其贴图。MCA 菜单 JSON 由附属包覆盖。Gradle Wrapper 来自上游工程；双方上游均有自己的许可证和署名，使用及分发上游构建时应保留其许可信息。
