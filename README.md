# 连连看（LianLianKan）

一款**单机、离线、中文**的 Android 连连看游戏。玩家在棋盘上依次选中两张图案相同、且可用**不超过 2 个拐点**的折线相连的牌将其消除；在限定时间内消除全部牌即通关。

项目以「好看、好点、有手感」为目标：流畅的连线与消除动画、清晰的选中反馈、Material 3 双主题视觉。

> 本项目是个人作品/作品集项目，按**完整工程流程**推进：先有需求规格说明书，再有开发计划，然后按里程碑逐个实现与提交。两份文档与代码之间的对应关系是可追溯的（每个里程碑记录都标注了对应的 SRS 需求编号）。

---

## 文档

| 文档 | 内容 |
|---|---|
| [`doc/软件需求规格说明书.md`](doc/软件需求规格说明书.md) | SRS V1.2：14 组功能需求、7 组非功能需求、20 条验收标准 |
| [`doc/开发计划.md`](doc/开发计划.md) | 12 个里程碑、需求覆盖矩阵、测试策略、风险与应对、各里程碑实施记录 |
| [`doc/V1.1改进计划.md`](doc/V1.1改进计划.md) | V1.0 验收后的 7 项改进：错误反馈选中态、提示文案、边到边与系统栏、关卡显示 |
| [`doc/V1.2改进计划.md`](doc/V1.2改进计划.md) | 皮肤切换 UI（Level 1：图案集，不含配色） |

需求与实现的对应关系、以及每个里程碑中的**设计取舍与踩坑记录**，都写在开发计划的各「实施记录」小节里。

---

## 版本

| 版本 | 状态 | 说明 |
|---|---|---|
| V1.0 | 已定稿（tag `v1.0.0`） | AC-01 ~ AC-20 全部通过（含真机手动验收），182 个 JVM 单测全绿 |
| V1.1 | 已定稿（tag `v1.1.0`） | 7 项改进：错误反馈统一清空选中态、提示文案后到即替换并居中浅色半透明、边到边与状态栏/导航栏背景延伸、HUD 显示当前关卡。`versionCode = 2`，183 个 JVM 单测全绿 |
| V1.2 | 计划中 | 皮肤切换 UI（Level 1）；见 [`doc/V1.2改进计划.md`](doc/V1.2改进计划.md) |

---

## 技术栈

| 项 | 取值 |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 构建 | AGP 9.3.0 / Gradle 9.5.0（AGP 9 内置 Kotlin 2.2.10） |
| 最低版本 | Android 7.0（API 24） |
| 编译/目标版本 | API 37 |
| 架构 | MVVM：Compose → ViewModel（StateFlow）→ 纯 Kotlin 领域层 → DataStore |
| 持久化 | DataStore (Preferences) |
| 反馈 | SoundPool + Vibrator |
| 测试 | JUnit 4 + kotlinx-coroutines-test（182 个 JVM 单测） |

---

## 构建

```bash
# 单元测试（领域层为主，无需设备）
./gradlew :app:testDebugUnitTest

# 调试包
./gradlew :app:assembleDebug

# 正式包（需要签名配置，见下）
./gradlew :app:assembleRelease
```

### 环境要求

- JDK **25**（项目 `gradle/gradle-daemon-jvm.properties` 指定 `toolchainVersion=25`）
- Android SDK，含 `platforms/android-37.0` 与 `build-tools/37.0.0`

### release 签名

签名信息从 **`local.properties`**（已被 `.gitignore` 排除）读取，密钥与密码不入库：

```properties
RELEASE_STORE_FILE=keystore/lianliankan-release.jks
RELEASE_STORE_PASSWORD=<your-password>
RELEASE_KEY_ALIAS=lianliankan
RELEASE_KEY_PASSWORD=<your-password>
```

缺少这些配置时 `assembleRelease` 会产出**未签名**的包，而 `assembleDebug` 与全部单测不受影响。

> ⚠️ `keystore/` 目录同样被 git 忽略，因此**不会随仓库备份**。请自行另行备份密钥文件与密码，丢失后无法对已安装的应用做覆盖升级。

---

## 项目结构

```
app/src/main/java/com/ldxy/lianliankan/
├── domain/          纯 Kotlin 领域层，禁止 import android.*（可用 JVM 单测直接覆盖）
│   ├── model/       Position / Tile / Board / GamePhase / Settings / Progress
│   ├── config/      LevelConfig / LevelCatalog（10 关配置表）
│   ├── board/       BoardGenerator / ShuffleService / DeadlockDetector
│   ├── path/        PathFinder（0/1/2 拐点连通判定）/ PathResult
│   ├── score/       ScoreEngine / GameTimer
│   └── session/     GameSession（一局游戏的聚合根与状态机）
├── data/            DataStore 仓库与 Preferences 映射
├── feedback/        SoundPool 音效与振动反馈
└── ui/
    ├── theme/       Material 3 双主题、圆角、间距
    ├── nav/         导航图
    ├── menu/        主菜单
    ├── level/       关卡选择
    ├── game/        棋盘渲染、连线与消除动画、HUD
    ├── dialog/      暂停与结算面板
    └── settings/    设置

tools/generate_sfx.py   音效素材生成脚本（纯标准库，可重复执行）
```

**分层约束**：`domain/` 不得依赖 Android SDK，也不依赖 `ui/`；所有游戏规则在该层闭环，因此连通判定、棋盘生成、洗牌可解性、计分与状态机都能在 JVM 上直接单测。

---

## 几个值得一提的实现点

- **连通判定不构造扩展棋盘**，而是把外圈表达为坐标范围（`row ∈ -1..rows`、`col ∈ -1..cols`），落在棋盘外但在这一圈内的格子恒为可通行。效果与「外扩一圈」等价，但省掉一份棋盘表示。
- **洗牌的兜底策略有构造性证明**：随机重排有限次仍不可解时，会按「同一行/列取相邻两格」或「任取两格走对角」来摆放一对同类型牌，可证明只要还剩 ≥2 张牌就一定有解，因此不存在死循环。
- **计分全程走整数**。倍率恒为 0.1 的整数倍，用「十分之一」为单位表示即可避开 `10 × 1.1` 这类浮点尾差。
- **主题对比度是可回归的断言**。按 WCAG 2.1 相对亮度公式校验成对颜色达到 4.5:1，防止后续调色时无意把某个深色配对压到阈值以下。
- **音效素材全部自制**（`tools/generate_sfx.py` 用正弦波合成），无第三方版权风险。

---

## 已知取舍

| 项 | 说明 |
|---|---|
| 窄屏单格尺寸 | 棋盘按 `(cols+2) × (rows+2)` 格分配空间（外圈用于绕行连线），8 列关卡在 360dp 宽屏上单格约 36dp，低于 Material 建议的 48dp 最小触控尺寸。SRS 未将无障碍列入范围。 |
| release 未开启 R8 | 项目当前没有设备/模拟器验证通道，开启混淆需要真机回归确认 keep 规则完整，因此暂缓。 |
| 皮肤切换未实现 UI | `TileFaces` 已支持按 `skinId` 取图案集（两套：水果/动物），但设置页尚未把 `skinId` 接到牌面渲染。SRS 将该需求列为 P2。 |
| 进行中进度粒度 | 按 SRS DR-3 的授权降级为「仅保存关卡编号」，不保存棋盘快照。 |

---

## 测试

```bash
./gradlew :app:testDebugUnitTest
```

182 个 JVM 单测，覆盖：连通判定的 0/1/2 拐点与外侧绕行、棋盘生成、洗牌可解性、死局检测、计分与连击、倒计时、游戏会话状态机、偏好映射、反馈开关与波形、主题对比度、图案集不变量。

**视觉与交互行为不在单测覆盖范围内**（棋盘渲染、动画、导航、弹窗、音效与振动手感），需要真机手动验收。验收清单见 [`doc/开发计划.md`](doc/开发计划.md) 的 M11 一节。
