# FitLog Vault 流程可靠性复盘与工程实践

本文档记录了 FitLog 在串联底部导航栏（FAB）与资料库（Vault）交互过程中发现的**四大可靠性问题、根因剖析、重构修正方案**，以及从中沉淀出的**移动端异步导航架构设计经验**。

---

## 一、背景与问题概述

FitLog 是以 Markdown 日记为基础的本地优先（Local-First）训练记录工具。用户通过 Android SAF（Storage Access Framework）授权连接外部目录作为 Vault。

在串联 FAB 菜单的两个核心动作：
- **Add New File（新建记录）**
- **Import Folder（导入/关联已有目录）**

进入实际联调阶段后，代码审查发现了多个 P2 级由于**异步时序与导航生命周期脱节**导致的稳定性隐患。

---

## 二、核心解决的问题与根因剖析

### 问题 1：保存目录失败，仍无感知退出设置页

* **故障现象**：在 `VaultSetupScreen` 中选择文件夹后，如果底层 DataStore 发生 I/O 错误或磁盘写满，界面依然自动退回主页，用户以为已经绑定成功，再次操作时却报“未配置”或继续使用旧目录。
* **产生原因**：
  ```kotlin
  // 缺陷代码模式
  coroutineScope.launch {
      vaultPreferences.setVaultUri(uri) // 返回了 Result<Unit>，但被调用方完全忽略
      backStack.removeLastOrNull()      // 无条件执行返回
  }
  ```
  底层虽然通过 `Result<Unit>` 抛出了错误，但调用方未做任何匹配检查，将异步操作的“执行完毕”错误地等同于“执行成功”。

---

### 问题 2：保存完成后误弹出其他页面，甚至导致栈空应用退出

* **故障现象**：在异步保存期间，若用户通过手势或按键主动退出了设置页，稍后保存完成时，会把当前新打开的页面甚至主页（`Today`）从栈顶弹走，导致白屏或应用意外退出。
* **产生原因**：
  1. 协程运行在整个 `FitLogApp` 级别的长生命周期作用域中；
  2. 弹出导航使用了无差别的 `backStack.removeLastOrNull()`，未校验**当前栈顶是不是当初发起保存的那个页面实例**。

---

### 问题 3：Add 异步检查允许重复执行与过期导航覆盖（Stale Navigation）

* **故障现象**：
  1. **重复压栈**：点击 Add 时若底层 SAF 跨进程查询较慢，连续点击会启动多个协程，最后把多个连续的 `Editor` 压入导航栈；
  2. **幽灵覆盖**：点击 Add 处于检查耗时中，用户中途主动切换到底栏 `Log` 或点击了 `Import`，稍后 Add 的检查完成，硬生生把 `Editor` 强行盖在用户后来打开的页面之上。
* **产生原因**：
  1. 缺少在途并发保护（In-flight lock）；
  2. **盲点死代码**：调用方试图通过 `when (config) { VaultConfigState.Loading -> ... }` 防重，但底层 `getVaultConfig()` 内部已经用 `.filter { it !is VaultConfigState.Loading }` 过滤掉了 `Loading`。该分支永远无法命中；
  3. 未校验**当前用户所处的上下文路由是否仍是发起请求时的那个起点**。

---

### 问题 4：操作意图丢失，Add 与 Import 流程混淆

* **故障现象**：
  1. 用户因为想记日记（Add）而首次去选文件夹，选完后直接退回主页，还要用户再去点一次 Add；
  2. 用户选择了一个只读文件夹，系统未区分场景直接报错，无法支撑“只读目录允许导入浏览”的产品原则。
* **产生原因**：
  导航目标只传了静态的 `FitLogRoute.VaultSetup`，没有携带“为何而来、去往何处”的上下文意图参数。

---

## 三、修正方法与架构重构

为了彻底解决上述问题，项目没有在 UI 组合项（Composable）中打补丁，而是重构引入了独立的协调器——**`VaultFlowController`**。

### 1. 架构职责划分

```
┌─────────────────────────────────────────────────────────┐
│                      FitLogApp                          │
│               (声明式 UI、Scaffold、Snackbar)            │
└───────────────────────────┬─────────────────────────────┘
                            │ 委托事件
                            ▼
┌─────────────────────────────────────────────────────────┐
│                  VaultFlowController                    │
│   - 在途任务管理 (busy, saving)                           │
│   - 时序与代数令牌校验 (generation token)                 │
│   - 导航栈原子操作与目标防护 (backStack guard)            │
└─────────────┬─────────────────────────────┬─────────────┘
              ▼                             ▼
┌───────────────────────────┐ ┌───────────────────────────┐
│     VaultPreferences      │ │      VaultRepository      │
│      (DataStore 配置)     │ │     (SAF 权限与元数据)     │
└───────────────────────────┘ └───────────────────────────┘
```

---

### 2. 核心机制实现

#### 机制 A：代数令牌（Generation Token）与在途锁（In-flight Lock）

用于杜绝重复执行与过期导航。

```kotlin
var busy by mutableStateOf(false)
    private set
private var generation = 0

fun createFile() {
    // 1. 在途排他锁 + 上下文合法性检查
    if (busy || !isTopLevel()) return
    val origin = backStack.lastOrNull() ?: return

    // 2. 派发当前操作的代数令牌
    val token = ++generation
    busy = true

    job = scope.launch {
        try {
            val config = getConfig()
            val access = if (config is VaultConfigState.Configured) checkAccess(config.uri) else null

            // 3. 结果落地前进行双重校验：代数未过时 且 用户仍在原页面
            if (token != generation || backStack.lastOrNull() != origin) return@launch

            // 4. 执行后续状态流转
            ...
        } finally {
            if (token == generation) busy = false
        }
    }
}
```

* **失效场景自动作废**：只要用户切换 Tab、按返回、或触发了其他导航，`invalidate()` 会递增 `generation` 并取消上一个任务。旧协程恢复后发现 `token != generation`，立即安全丢弃结果。

---

#### 机制 B：携带意图与唯一标识的类型化路由

在 `FitLogRoute.kt` 中强化路由定义：

```kotlin
@Serializable
data class VaultSetup(
    val createAfterSetup: Boolean = false,                        // 意图：选完是否自动进 Editor
    val requestId: String = java.util.UUID.randomUUID().toString(), // 身份：每次打开独一无二
) : FitLogRoute
```

在保存选定目录时实现精准校验：

```kotlin
fun selectFolder(route: FitLogRoute.VaultSetup, uri: Uri) {
    if (busy || backStack.lastOrNull() != route) return
    val token = ++generation
    ...
    job = scope.launch {
        saving = true
        val result = saveUri(uri)

        // 挂起返回后，严格比对栈顶依然是刚才那个具体的 route 实例
        if (token != generation || backStack.lastOrNull() != route) return@launch

        if (result.isFailure) {
            setupError = R.string.vault_error_save_config_failed // 留在当前页提示错误
        } else if (route.createAfterSetup) {
            backStack[backStack.lastIndex] = FitLogRoute.Editor  // 顺畅直达编辑页
        } else if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)              // 正常返回主列表
        }
    }
}
```

---

#### 机制 C：场景化权限策略划分

在目录权限检查中，根据用户动作意图做精细化控制：

```kotlin
// Import 允许只读目录挂载（用于后续仅浏览/解析），但 Add 必须要求支持创建文件
val allowed = access == VaultAccessStatus.CanCreateFiles ||
    (!route.createAfterSetup && access == VaultAccessStatus.ReadOnly)

if (!allowed) {
    setupError = accessError(access)
    return@launch
}
```

---

## 四、沉淀与工程经验总结（Key Takeaways）

### 1. 移动端异步导航的“代数令牌（Generation Token）”模式
- **适用场景**：任何存在挂起操作（网络请求、SAF 查询、本地 DataStore I/O），并在恢复后需要操作导航栈的场景。
- **最佳实践**：维护一个简单的整数 `generation`。发起时生成快照 `token = ++generation`，恢复后检查 `token == generation`。一旦期间有新操作发生，直接让旧操作的结果“天然失效”，避免了手动维护复杂状态转移表的开销。

### 2. 警惕挂起函数调用处的“伪状态”死代码
- 如果被调用的 `suspend fun` 内部已经进行了状态过滤（例如通过 Flow 的 `.filter { !Loading }.first()`），该函数就**物理上绝不可能返回中间态**。
- 在外层 `when` 中编写 `Loading -> {}` 不仅无法起到防重作用，还会产生“已经处理了加载状态”的安全错觉。防御必须在发起调用的外层显式维护布尔锁（如 `busy`）。

### 3. 避免任何无前提假定的 `removeLast()`
- 在单 Activity / Compose 导航中，用户的返回手势随时会改变栈深度与栈顶内容。
- **金科玉律**：
  1. 绝不在未校验栈顶身份的情况下调用 `removeLastOrNull()`；
  2. 永远做栈深度保底检查（`backStack.size > 1`），绝不允许业务返回操作清空根页面；
  3. 异步操作中的返回必须绑定特定 `NavKey` 实例，若目标已不在栈顶，说明用户已经先行离开，应直接放弃弹出。

### 4. 路由参数应当能够表达“用户为什么来”
- 页面不仅是数据的展示器，更是用户交互链路的一环。
- 让路由携带 `createAfterSetup: Boolean` 这样的意图标志，使设置页面既能服务于“初次记录引导”，又能服务于“日常设置更换”，并在完成后准确流转到符合用户心智的下一阶段。

### 5. 100% 纯 JVM 驱动的并发导航单元测试
- 通过将导航栈抽象为 `MutableList<NavKey>`，并为 `VaultFlowController` 注入挂起 Lambda 函数；
- 配合 Kotlin 协程测试库中的 `CompletableDeferred`、`runCurrent()`、`advanceUntilIdle()`，可以在**不启动 Android 设备、不依赖真实 SAF** 的情况下，毫秒级模拟出：
  - “挂起写入中用户点了返回”
  - “检查目录耗时期间用户切了 Tab”
  - “并发狂按按钮”
- 这种高内聚的控制层设计使复杂的生命周期 Bug 得以被持续自动化守护。
