# 城建与远程管理配置

本配置从本机《剑与王国》的实际配置核对而来，运行在 Minecraft 1.21.1 / NeoForge，两个原模组源码不变。原 1.20.1 Forge JAR 不复制进来；使用各作者提供的对应平台实现。

| 项目 | 实现与范围 |
| --- | --- |
| 远程终端 | 附属 mod 的 N 键入口，可搜索当前维度内有访问权限的殖民地建筑，并打开殖民地原生建筑窗口。以服务端实际数据同步职业、建筑、居民和研究信息，操作继续经过原生权限检查。不是将旧版 Auto Recipes Patch 二进制直接加载到 NeoForge。 |
| 建筑效率 | Tweaks 3.33；放块间隔 1，挖掘基础间隔 150；合成进度参数 10、敲击时间 3、回岗等待 200。参数仍参与原生技能、材料和工作状态计算，不代表所有工人一律每 tick 放一个方块。 |
| AI 与寻路 | Pathfinding Edition 1.0.5-fix-6；建筑工、矿工、采石工 SENTRY，道路代价 0.4，水路附加代价 8，潜水 24，最大寻路距离 1000；其余明确迁入值在 settings.json。复用该模组自己的 AI。 |
| 资源与配方 | Material Unification 3.4.1 + MineColonies 附属 3.1 + KubeJS/Rhino；统一资源、分拣台与殖民地原生工匠配方。未加入其额外下界资源扩展包。 |

范围约束：保留殖民地原来的住宅等级/人口容量规则；关闭寻路附属额外加入的不死图腾手动复活及指南针交互入口；继续使用原有 MCA 死神物品与殖民地墓碑复活。未导入剑与王国的士兵、科研倍率、袭击难度、领地尺寸、额外职业兼容包或任务进度。

兼容补丁：寻路附属在 CitizenData.update 开头取消原函数，使 Tweaks 的函数末尾“关闭休闲”处理无法执行；附属 mod 在休闲读取入口尊重该开关，未改写任何职业工作流程。

## 安装

先关闭游戏/服务器，解压本附属 mod 安装包到单独文件夹，运行：

```powershell
& ./tools/Install-GameplayProfile.ps1 -GameDirectory '你的 NeoForge 1.21.1 游戏目录'
```

安装器从 dependencies.lock.json 下载并核验依赖，安装本附属 mod，备份并移出同 mod ID 的旧版本，仅合并 settings.json 列出的配置。其他模组和无关配置保留。已有文件的备份位于目标目录的 colony-mca-profile-backups 下。客户端和服务器均需安装同一套依赖；资源统合改变资源获取与配方，建议在备份后使用。

安装器会在第一次启动之前从作者 JAR 提取其 KubeJS 脚本，避免物品注册晚于配方加载。开发环境使用 -SkipDownload -ConfigOnly，以免在已有开发 classpath 外重复加载 mods 目录中的 JAR。

远程终端按当前维度和原生访问权限列出建筑。打开时刷新快照；需要再次读取时返回终端刷新。不会替你跨维度传送或持续强制加载远方区块，库存容器和配方教学仍使用殖民地自身窗口及规则。

本机源整合包目录 E:/MC/mc/.minecraft/versions/剑与王国 只读取，没有修改。

## 原作者项目

- https://github.com/ARxyt/ColonyPathingEdition
- https://github.com/gisellevonbingen-Minecraft/MineColonies_Tweaks
- https://www.curseforge.com/minecraft/mc-mods/material-unification
- https://www.curseforge.com/minecraft/mc-mods/material-unification-minecolonies
- https://kubejs.com/

依赖 JAR 不嵌入本附属 mod；由安装器按锁定地址获取。各依赖的许可随原文件提供。
