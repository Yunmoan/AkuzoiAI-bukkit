# AkuzoiAI Bukkit

一个面向 Minecraft 1.20.1+ Paper/Spigot 的 AI 聊天插件骨架。

项目使用 `1.20.1-R0.1-SNAPSHOT` API 编译，避免误用更高版本才出现的 Bukkit/Paper API。一般可运行在 1.20.1 及更高的 1.20.x 服务端上。

当前版本：`0.1.7`

## 功能

- 接入 OpenAI-like Chat Completions API。
- 支持配置 API 地址、Key、模型、温度、最大输出长度等。
- `system-prompt.txt` 与 `prompt-template.txt` 独立编辑。
- 支持关闭记忆、玩家独立记忆、全服统一记忆。
- 支持固定长度上下文与简易压缩摘要上下文。
- 支持命令触发、聊天名称触发、聊天概率触发。
- 支持玩家交互冷却后的主动对话，并附带近几秒行为摘要。
- 支持 AI 通过内部标记概率性赠送物品。
- 支持现实时间感知、可配置时区、可配置 debug 与控制台日志。

## 构建

推荐使用 Gradle：

```powershell
gradle build
```

也可以使用 Maven：

```powershell
mvn package
```

本项目还提供一个 Windows 构建脚本，会优先调用 Gradle 或 Maven；如果两者都没有，则会尝试使用 JDK 直接编译：

```powershell
.\build.ps1
```

如果 JDK 直接编译模式缺少 Paper API 的传递依赖，建议安装 Gradle 或 Maven 后构建。也可以手动指定一个包含 Bukkit/Paper 1.20.1 API 及依赖的 jar：

```powershell
.\build.ps1 -ApiJar C:\path\to\paper-api.jar
```

构建产物位于 `build/libs/` 或 Maven 的 `target/`。

## 首次使用

1. 将 jar 放入服务器 `plugins` 目录。
2. 启动服务器生成配置。
3. 编辑 `plugins/AkuzoiAI/config.yml`，填写 `api-key`、`base-url`、`model`。
4. 按需编辑 `system-prompt.txt` 和 `prompt-template.txt`。
5. 按需设置 `time.zone`，例如 `Asia/Shanghai`。
6. 执行 `/akuzoiai reload`、`/akai reload` 或重启服务器。

## 命令

- `/ai <内容>`：向 AI 对话。
- `/akuzoiai reload`：重载配置和提示词。
- `/akai reload`：`/akuzoiai reload` 的别名。
- `/akuzoiai clear`：
  - `memory.mode=player` 时，普通玩家可清除自己的记忆。
  - `memory.mode=global` 时，普通玩家不能清除全局记忆。
- `/akuzoiai clear <玩家名|global>`：管理员清理指定玩家或全局记忆。
- `/akuzoiai info`：管理员查看插件状态。

## 权限

- `akuzoiai.use`：允许使用 `/ai` 和基础插件命令。
- `akuzoiai.admin`：允许管理插件、清理共享/他人记忆、查看 info。

## 控制台输出说明

默认情况下，AI 对玩家“实际说出来”的可见回复会输出到控制台。

- 开关：`debug.log-replies-to-console: true`
- 日志格式：`[AkuzoiAI/REPLY]`

如果 AI 返回了 `<think>...</think>`，这部分内容：

- 不会发给玩家
- 会先从可见回复中移除
- 可选输出到控制台：`debug.think-tag-to-console: true`
- 日志格式：`[AkuzoiAI/THINK]`

如果开启 `debug.enabled: true`，还会输出额外调试日志：

- 请求触发原因
- 玩家名
- 原始触发消息

## 时区配置

`time.zone` 使用 Java 标准时区 ID，不要填写 `UTC+8` 这类非 IANA 名称，建议直接使用地区名。

常用时区表示例：

- 中国标准时间：`Asia/Shanghai`
- 日本时间：`Asia/Tokyo`
- 韩国时间：`Asia/Seoul`
- 新加坡时间：`Asia/Singapore`
- 协调世界时：`UTC`
- 英国时间：`Europe/London`
- 德国/中欧时间：`Europe/Berlin`
- 美国东部时间：`America/New_York`
- 美国中部时间：`America/Chicago`
- 美国山地时间：`America/Denver`
- 美国西部时间：`America/Los_Angeles`
- 澳大利亚悉尼时间：`Australia/Sydney`
- 印度时间：`Asia/Kolkata`

时间格式 `time.format` 使用 Java `DateTimeFormatter` 模式，例如：

- `yyyy-MM-dd HH:mm:ss z`
- `yyyy/MM/dd HH:mm`
- `MM-dd HH:mm EEEE`

## 提示词模板变量

### `prompt-template.txt` 可用变量

- `{trigger}`：触发类型，如 `command`、`name`、`random`、`proactive`
- `{player}`：玩家名
- `{time}`：按 `time.zone` 和 `time.format` 生成的当前现实时间
- `{message}`：当前触发消息内容

默认模板：

```text
触发类型：{trigger}
玩家：{player}
现实时间：{time}
消息：{message}

请基于以上内容和历史上下文回复。
```

### 由系统自动附加给 AI 的上下文

除了模板变量外，插件还会自动向 AI 注入以下上下文：

- `system-prompt.txt` 内容
- 当前现实时间说明
- 历史对话记忆
- 历史摘要压缩内容
- 主动触发时的最近玩家行为摘要
- 可选礼物能力说明与触发 token

## 配置项说明

### `ai`

- `ai.base-url`：Chat Completions API 地址
- `ai.api-key`：API Key
- `ai.model`：模型名
- `ai.temperature`：采样温度
- `ai.max-tokens`：最大输出 token
- `ai.timeout-seconds`：请求超时秒数

### `prompt`

- `prompt.system-file`：系统提示词文件名
- `prompt.template-file`：用户提示词模板文件名

### `debug`

- `debug.enabled`：是否输出额外调试日志
- `debug.log-replies-to-console`：是否把 AI 可见回复输出到控制台
- `debug.show-thinking-message`：是否向玩家显示“AI 正在思考...”
- `debug.think-tag-to-console`：是否把 `<think>` 内容输出到控制台

### `time`

- `time.enabled`：是否启用现实时间上下文
- `time.zone`：时区 ID
- `time.format`：时间格式

### `memory`

- `memory.mode`：`none` / `player` / `global`
- `memory.max-messages`：保留消息数，`-1` 表示无限
- `memory.compression-enabled`：是否启用摘要压缩
- `memory.compress-after-messages`：达到多少条后压缩
- `memory.summary-max-tokens`：摘要 token 上限

### `trigger`

- `trigger.names`：聊天中命中即触发的名字列表
- `trigger.command-enabled`：是否允许 `/ai` 命令触发
- `trigger.random-chat-enabled`：是否启用随机聊天触发
- `trigger.random-chat-probability`：随机触发概率
- `trigger.random-chat-cooldown-seconds`：随机触发冷却

### `proactive`

- `proactive.enabled`：是否启用主动对话
- `proactive.after-interaction-seconds`：玩家交互后延迟多少秒再尝试主动触发
- `proactive.cooldown-seconds`：主动触发冷却
- `proactive.probability`：主动触发概率

### `behavior`

- `behavior.window-seconds`：主动对话时提供给 AI 的最近行为窗口
- `behavior.retention-seconds`：行为记录在内存中保留多久

### `gift`

- `gift.enabled`：是否启用 AI 礼物能力
- `gift.trigger-token`：AI 请求送礼的内部标记
- `gift.options[].material`：服务端物品 ID，推荐 `minecraft.torch` 这种格式
- `gift.options[].min-amount`：最小数量
- `gift.options[].max-amount`：最大数量
- `gift.options[].probability`：命中概率

### `chat`

- `chat.prefix`：前缀
- `chat.thinking`：正在思考提示文本
- `chat.error`：错误提示文本
- `chat.format`：AI 对玩家发言格式，支持 `{message}`
