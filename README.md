# 连连看（LianLianKan）

一款**单机、离线、中文**的 Android 连连看游戏。玩家在棋盘上依次选中两张图案相同、且可用**不超过 2 个拐点**的折线相连的牌将其消除；在限定时间内消除全部牌即通关。

项目以「好看、好点、有手感」为目标：流畅的连线与消除动画、清晰的选中反馈、Material 3 视觉，以及可自由组合的 **4 种主题色 × 3 套皮肤**。

> 本项目是个人作品/作品集项目，按**完整工程流程**推进：先有需求规格说明书，再有开发计划，然后按里程碑逐个实现与提交。两份文档与代码之间的对应关系是可追溯的（每个里程碑记录都标注了对应的 SRS 需求编号）。

---

## 文档

| 文档 | 内容 |
|---|---|
| [`doc/软件需求规格说明书.md`](doc/软件需求规格说明书.md) | SRS V1.2：14 组功能需求、7 组非功能需求、20 条验收标准 |
| [`doc/开发计划.md`](doc/开发计划.md) | 12 个里程碑、需求覆盖矩阵、测试策略、风险与应对、各里程碑实施记录 |
| [`doc/V1.1改进计划.md`](doc/V1.1改进计划.md) | V1.0 验收后的 7 项改进：错误反馈选中态、提示文案、边到边与系统栏、关卡显示 |
| [`doc/V1.2改进计划.md`](doc/V1.2改进计划.md) | 皮肤切换 UI（Level 1：图案集，不含配色） |
| [`doc/V1.3改进计划.md`](doc/V1.3改进计划.md) | 主题改为 4 种主题色（配色经标准 M3 色调板算法生成），深色模式整体移除。**已实施，待真机验收** |
| [`doc/V1.4改进计划.md`](doc/V1.4改进计划.md) | APP 图标（矢量 + 光栅双轨）与启动白屏。**已实施，待真机验收** |

需求与实现的对应关系、以及每个里程碑中的**设计取舍与踩坑记录**，都写在开发计划的各「实施记录」小节里。

---

## 版本

| 版本 | 状态 | 说明 |
|---|---|---|
| V1.0 | 已定稿（tag `v1.0.0`） | AC-01 ~ AC-20 全部通过（含真机手动验收），182 个 JVM 单测全绿 |
| V1.1 | 已定稿（tag `v1.1.0`） | 7 项改进：错误反馈统一清空选中态、提示文案后到即替换并居中浅色半透明、边到边与状态栏/导航栏背景延伸、HUD 显示当前关卡。`versionCode = 2`，183 个 JVM 单测全绿 |
| **V1.2** | 已定稿（tag `v1.2.0`） | 皮肤切换 UI（Level 1）：3 套图案皮肤（蔬果 / 动物 / 符号），可即时切换并持久化。`versionCode = 3`，186 个 JVM 单测全绿 |
| V1.3 | 已实施（待真机验收） | 主题由「深浅三态」改为 **4 种主题色**（配色经标准 M3 色调板算法从种子色生成），**深色模式整体移除**。`versionCode = 4`，189 个 JVM 单测全绿 |
| V1.4 | 已实施（待真机验收） | **自绘 APP 图标**（自适应矢量 + API 24–25 光栅 + Android 13+ 单色层，由 `tools/generate_icon.py` 从同一份形状定义产出），**启动白屏消除**（引入 `core-splashscreen`，并在设置读到首值之前按住启动画面）。`versionCode = 5`，194 个 JVM 单测全绿 |

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
| 启动画面 | `androidx.core:core-splashscreen` 1.2.0（API 31+ 走系统实现，更低版本由库兼容） |
| 反馈 | SoundPool + Vibrator |
| 配色 | 标准 M3 色调板算法（`material-color-utilities`），从 4 个种子色生成整套配色 |
| 测试 | JUnit 4 + kotlinx-coroutines-test（JVM 单测） |

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
    ├── theme/       主题色（标准 M3 色调板生成）、皮肤、圆角、间距
    ├── nav/         导航图
    ├── menu/        主菜单
    ├── level/       关卡选择
    ├── game/        棋盘渲染、连线与消除动画、HUD
    ├── dialog/      暂停与结算面板
    └── settings/    设置

tools/generate_sfx.py     音效素材生成脚本（纯标准库，可重复执行）
tools/generate_icon.py    图标素材生成脚本（纯标准库，矢量 + 光栅同一来源，可重复执行）
```

**分层约束**：`domain/` 不得依赖 Android SDK，也不依赖 `ui/`；所有游戏规则在该层闭环，因此连通判定、棋盘生成、洗牌可解性、计分与状态机都能在 JVM 上直接单测。

### 素材生成（脚本）

音效与图标都是**自制素材**，由脚本从源码描述生成，产物可 diff、可重复产出：

```bash
python tools/generate_sfx.py     # 5 段音效 → app/src/main/res/raw/sfx_*.wav
python tools/generate_icon.py    # 图标三轨 → drawable/ 与 mipmap-*dpi/
```

> ⚠️ **改动图标必须重跑 `python tools/generate_icon.py`。**
> `drawable/ic_launcher_{background,foreground,monochrome}.xml`（自适应图标，
> API 26+）与 `mipmap-*dpi/ic_launcher{,_round}.png`（光栅，API 24–25）是**同一份
> 形状定义的两个产物**，各自单独改一处必然漂移 —— 最典型的是「矢量换了新图标，
> 老设备上还是旧图标」。生成器顶部还固化了主题色的字面值（见下），
> 改种子色后也需要重新生成，否则图标颜色会与 APP 脱节。

**图标的语义**：两块同图案的圆角牌，由一条**恰好两个拐点**的折线相连 —— 就是本游戏的
核心规则（两张图案相同的牌，可用不超过 2 个拐点的折线连通即可消除）。

---

## 几个值得一提的实现点

- **连通判定不构造扩展棋盘**，而是把外圈表达为坐标范围（`row ∈ -1..rows`、`col ∈ -1..cols`），落在棋盘外但在这一圈内的格子恒为可通行。效果与「外扩一圈」等价，但省掉一份棋盘表示。
- **洗牌的兜底策略有构造性证明**：随机重排有限次仍不可解时，会按「同一行/列取相邻两格」或「任取两格走对角」来摆放一对同类型牌，可证明只要还剩 ≥2 张牌就一定有解，因此不存在死循环。
- **计分全程走整数**。倍率恒为 0.1 的整数倍，用「十分之一」为单位表示即可避开 `10 × 1.1` 这类浮点尾差。
- **配色由算法生成、对比度由测试保证**。4 套主题色只提供种子色，整套 Material 3 配色交给标准 M3 色调板算法（`material-color-utilities`）；`ThemePaletteContrastTest` 再独立按 WCAG 2.1 相对亮度公式复算一遍，要求正文 ≥ 4.5:1、图形元素 ≥ 3:1。这样「换主题不会踩对比度坑」成为可回归的保证，而不是靠肉眼。
- **音效素材全部自制**（`tools/generate_sfx.py` 用正弦波合成），无第三方版权风险。
- **图标由一份形状定义产出矢量与光栅两轨**（`tools/generate_icon.py`，纯 `zlib` + `struct` 手写 PNG，4× 超采样抗锯齿）。同一份几何既生成 VectorDrawable 也生成 5 档 PNG，并带一条**安全区自检**：前景若越出自适应图标的 66dp 安全圆，脚本直接报错退出，而不是等到真机上被遮罩切掉才发现。
- **启动不白屏，也不跳色**。冷启动的窗口白底交给 `core-splashscreen`；同时把「设置是否读到首个真实值」做成仓库层的一个明确信号（`SettingsRepository.isLoaded`）来放行启动画面 —— 否则会先按默认配色渲染一帧、再跳到玩家选的配色。这条放行逻辑单独抽成 `ui/SplashGate.kt`，因为它含一条**高风险**分支：读盘异常时若永不放行，应用就再也起不来，所以超时兜底被写成了可单测的代码。

---

## 已知取舍

| 项 | 说明 |
|---|---|
| 窄屏单格尺寸 | 棋盘按 `(cols+2) × (rows+2)` 格分配空间（外圈用于绕行连线），8 列关卡在 360dp 宽屏上单格约 36dp，低于 Material 建议的 48dp 最小触控尺寸。SRS 未将无障碍列入范围。 |
| release 未开启 R8 | 项目当前没有设备/模拟器验证通道，开启混淆需要真机回归确认 keep 规则完整，因此暂缓。 |
| emoji 皮肤的跨设备差异 | 「蔬果」「动物」两套皮肤用 emoji，同一串字符在不同 Android 版本与厂商字体下可能呈现为彩色、单色甚至方框。为此额外提供第三套**符号**皮肤（`▲ ● ■ ◆ ★ ✚ ✦ …`）—— 普通 Unicode 几何字符由系统字体渲染，跨设备一致性远好于 emoji，可作兜底。 |
| **无深色模式**（V1.3） | 主题由「跟随系统 / 浅色 / 深色」三态改为 4 种主题色，**深色模式整体移除**。玩家无法使用深色界面，暗光环境下观感偏亮，系统处于深色模式时本应用仍以浅色呈现。这是产品定位取舍（SRS 的 `FR-13.2` 与 `NFR-5.3` 已随之删除，SRS 修订历史中有专门的降级说明）；作为部分补偿，`NFR-5.3` 的对比度要求转化为「**每套主题色下均须可辨**」，由测试强制保证。 |
| 皮肤不含配色 | SRS FR-11.4 的原文是「图案/配色主题」。当前皮肤只承载图案；配色是独立的选择器（4 种主题色）。两个维度正交、可自由组合。 |
| 进行中进度粒度 | 按 SRS DR-3 的授权降级为「仅保存关卡编号」，不保存棋盘快照。 |
| **启动图标无法跟随 APP 内的主题色设置**（V1.4） | 自适应图标是**静态资源**，运行期改不了（除非用 `activity-alias` 切换，代价大且会在桌面上留下多个入口）。因此图标固定用默认主题色（浅蓝）生成出的 `primaryContainer` / `primary`：`#FF8FD3F6` 作底、`#FF126684` 作牌面与连线。玩家把 APP 内配色换成黄绿/玫红/琥珀后，桌面图标仍是浅蓝 —— 这是系统层面的限制，不是实现取舍。代价是图标与 APP 内的观感可能不一致；好处是 `primaryContainer` 是 tone 90 的浅色，不受 V1.3「`primary` 偏暗」那个已知问题影响。相关色值在 `tools/generate_icon.py` 顶部有出处注释。 |
| 启动画面图标偏小（V1.4） | 启动主题直接复用自适应图标的前景（它已按 66dp 安全区作画，任何遮罩都切不到）。代价是系统在 API 31+ 会把这张 108dp 画布放进 192dp 的可见圆里，图标看起来比常见 APP 的启动图标小一些。若真机观感不佳，再单独出一张放大版启动图标即可（不影响自适应图标）。 |

---

## 测试

```bash
./gradlew :app:testDebugUnitTest
```

194 个 JVM 单测，覆盖：连通判定的 0/1/2 拐点与外侧绕行、棋盘生成、洗牌可解性、死局检测、计分与连击、倒计时、游戏会话状态机、偏好映射、反馈开关与波形、**主题色对比度与色相**、**皮肤与主题色注册表不变量**、**设置加载信号与启动画面超时兜底**。

**视觉与交互行为不在单测覆盖范围内**（棋盘渲染、动画、导航、弹窗、音效与振动手感），需要真机手动验收。验收清单见 [`doc/开发计划.md`](doc/开发计划.md) 的 M11 一节。
