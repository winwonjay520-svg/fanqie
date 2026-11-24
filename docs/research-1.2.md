# GitHub 调研与 1.2 优化记录

调研日期：2026-11-20。通过 GitHub 搜索查找相似项目，并阅读下面的 README 和相关实现文件，再对璃光独立实现适合现有结构的改进。

## 研究的项目

| 项目与实际阅读内容 | 观察到的设计 | 璃光中的改进 |
| --- | --- | --- |
| [Goodtime](https://github.com/adrcotfas/Goodtime/blob/master/README.md)；[状态恢复](https://github.com/adrcotfas/Goodtime/blob/master/shared/src/commonMain/kotlin/com/apps/adrcotfas/goodtime/bl/TimerStateRestoration.kt)；[统计模型](https://github.com/adrcotfas/Goodtime/blob/master/shared/src/commonMain/kotlin/com/apps/adrcotfas/goodtime/stats/StatisticsData.kt) | 长休息节奏、正计时、事项维度统计；单调时钟与恢复状态的独立处理 | 四轮番茄提示、每四个完成番茄推荐长休息、事项占比与记录详情；保留单调时钟实现并验证延时不重置已用时间 |
| [Tomato](https://github.com/nsh07/Tomato/blob/dev/README.md)；[TimerService](https://github.com/nsh07/Tomato/blob/dev/androidApp/src/main/java/org/nsh07/pomodoro/service/TimerService.kt)；[通知动作](https://github.com/nsh07/Tomato/blob/dev/androidApp/src/main/java/org/nsh07/pomodoro/service/NotificationUtils.kt) | 独立计时状态、通知操作、简洁的计时与统计呈现 | 单一计时状态驱动沉浸视图；增加快捷延长 5 分钟，UI 操作后仍由原生服务重新安排提醒；统计精确显示不足一分钟的记录 |
| [AndroidLiquidGlass / Backdrop](https://github.com/Kyant0/AndroidLiquidGlass/blob/kmp/README.md)；[LiquidBottomTabs](https://github.com/Kyant0/AndroidLiquidGlass/blob/kmp/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt)；[LiquidButton](https://github.com/Kyant0/AndroidLiquidGlass/blob/kmp/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidButton.kt) | 背景模糊、镜片、高光、阴影分层；选中胶囊与交互位置联动 | CSS 滑动胶囊导航；按住拖动时高光跟随；玻璃边缘更轻，增加清透、柔雾与轻量材质 |
| [liquid-glass-effect](https://github.com/kevinbism/liquid-glass-effect/blob/main/README.md) 与 LICENSE | CSS 模糊、半透明表面、伪元素反光；旧渲染器需要回退 | 延续现有 WebView 材质方案，轻量模式关闭背景过滤，保留不支持 backdrop-filter 时的实色回退 |

## 采用方式

Goodtime 和 Tomato 所读源码头部标注 GPL-3.0-or-later；AndroidLiquidGlass 为 Apache-2.0；liquid-glass-effect 为 MIT。此次没有把这些项目的源码、图片、图标、字体或库打包进 APK，也未引入其依赖。表中的功能和交互思路根据璃光现有 Java + WebView 架构独立实现，源码中保留此调研说明。

Compose Backdrop 的原生镜片渲染依赖不同的 UI 架构；本版采用 CSS 材质和交互实现，不宣称具备该库的原生光学渲染。没有仅为视觉升级而重写整个应用，以保持 Android 8+ 与旧数据兼容。

## 三轮落地

1. **专注流程**：共用同一计时器的沉浸界面、运行或暂停时延长五分钟、总时长上限、四轮进度和长休息建议。
2. **统计回顾**：按名称累计事项时长与占比、点击事项筛选记录、点击日历足迹查看当天明细、空日期提示、精确到秒的累计时长。
3. **材质与性能**：滑动玻璃胶囊、触摸高光跟随、三种材质、减少不变控件的 DOM 重建；计时轮询只在状态版本变化时返回完整历史，应用隐藏时停止界面轮询，原生计时继续运行。

## 范围与限制

- 四轮进度按全部已完成番茄累计分组，手动结束的时段不增加番茄数；推荐休息仍由用户选择，不会自动开始下一轮。
- 轻量模式停用背景模糊和 backdrop-filter；返回其他材质后恢复用户保存的模糊参数。
- 日期统计仍按结束日期归档；不会把暂停期间误分配为专注时间。
- 连续任务记录只保留原始事项名，未增加账号、云同步、网络权限或后台锁应用功能。
- 性能改进以“减少轮询载荷、减少 DOM 重建、关闭过滤器”的可验证行为为依据，没有宣称未经实测的帧率或电量收益。
