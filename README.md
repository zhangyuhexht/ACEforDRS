# ACE for DRS

面向 Paper 服务器的双系统反作弊插件：**超限附魔降级** + **违禁物品 / 异常 NBT 检测**。  
针对 Slimefun、无尽贪婪（Infinity Expansion）等附属模组做了白名单适配，同时保留对伪造白名单标记的基础防护。

当前版本：**2.2.0**  
服务端：**Paper 1.21+**（开发与测试环境为 Paper 26.2）  
Java：**21+**

---

## 功能概览

### 系统 1：附魔降级

- 周期性扫描在线玩家背包（默认 180 秒，一轮扫描分摊到整个周期内执行，避免卡顿尖峰）
- 上线检查、换甲检查、快捷栏切换检查、容器打开检查
- 把超过原版上限的附魔降级到合法等级
- 可选择保留 `耐久 255`（`keep-unbreaking-255`）
- 移除物品上不可能存在的附魔组合（例如剑上的保护）
- 支持服务端注册的附魔上限（数据包 / 插件）
- 递归扫描潜影盒、收纳袋内部，深度可配置

### 系统 2：违禁物品与异常 NBT

- 违禁材质：基岩、屏障、结构方块、命令方块、刷怪蛋等
- 异常属性：攻击伤害、最大生命、移动速度、交互距离等，含分属性合理上限
- 绝对属性上限：即使物品带白名单标记，也不允许出现 1,000,000 以上的属性值
- 异常药水：效果数量、效果等级、持续时间、自定义效果
- 弩装填物：逐发检查弹射物的材质、属性、药水数据，异常弹射物直接移除
- 拾取拦截：物品进入背包前检查，包含潜影盒 / 收纳袋内部
- 使用前拦截：消耗品、交互物品在使用前检查
- 近战拦截：高伤害命中时立即检查武器，异常则取消攻击
- 地面掉落物扫描：定期清理地上违禁品
- 替换为《滚木》（`OAK_LOG`），默认删除前备份原物品

### 玩家状态检查

- 定期检查玩家身上的状态效果
- 超过原版上限的效果会降级，但保留持续时间与图标等属性

### 箭矢限速

- 发射时限速
- 飞行中周期扫描
- 命中伤害封顶
- 覆盖普通箭、药箭、光灵箭、三叉戟等

### 其他

- 点击 / 拖动只扫描受影响的槽位，shift-click 回退到全量扫描
- 光标物品单独扫描
- 同一件物品只读取一次 `ItemMeta`，普通物品走快速路径
- 白名单识别结果在整条扫描链路复用
- 配置自动补写：升级后缺失的配置项会按默认顺序补回，旧文件备份为 `config.yml.bak`
- 自动清理已废弃的 `exempt-*` 配置
- 中文 / 英文 / 双语消息
- 扫描日志文件与删除备份

---

## 安装

1. 下载 `ACEforDRS-2.2.0.jar`
2. 放入服务端 `plugins/` 目录
3. 启动服务器
4. 根据需要修改 `plugins/ACEforDRS/config.yml`
5. 执行 `/anti255 reload` 或重启服务器

首次启动会生成：

```text
plugins/ACEforDRS/
├── config.yml
├── messages.yml
├── logs/
└── backups/
```

---

## 命令与权限

| 命令 | 说明 |
|---|---|
| `/anti255 reload` | 补写缺失配置并重载 |
| `/anti255 scan <玩家\|all>` | 扫描指定玩家或全部在线玩家 |
| `/anti255 inspect <玩家>` | 查看玩家背包与末影箱的检查详情 |
| `/anti255 stats` | 查看运行统计 |
| `/check255 <玩家>` | 快速检查玩家背包 |

| 权限 | 说明 | 默认 |
|---|---|---|
| `anti255.admin` | 使用管理命令；管理员可使用管理员物品 | OP |

### 管理员说明

管理员与普通玩家的唯一区别是：**管理员跳过系统 2**（违禁物品删除、异常属性、异常药水、近战拦截、拾取拦截、使用拦截），因此可以使用管理员物品。

管理员仍然受以下检查约束：

- 附魔降级
- 玩家状态效果检查
- 背包 / 容器 / 箭矢限速

插件没有“完全豁免”权限，也不存在按玩家名豁免的配置。

---

## 白名单（Slimefun / 无尽贪婪）

白名单位于 `config.yml` 的 `whitelist` 段：

```yaml
whitelist:
  enabled: true
  pdc-namespaces:
    - slimefun
    - sf_addon
    - infinityexpansion
    - infinitylib
    - infinity_expansion
  materials: []
  name-contains: []
  enchants:
    unbreaking: 25
```

匹配规则（任意一条命中即视为白名单）：

- `pdc-namespaces`：物品 PDC 中包含指定命名空间
- `materials`：Bukkit `Material` 名称完全匹配
- `name-contains`：显示名称包含指定子串（不区分大小写）

白名单物品会跳过：

- 附魔降级
- 违禁材质检查
- 异常药水检查
- 深度 NBT 修复

但白名单物品仍然会执行：

- 属性硬上限检查（1,000,000）
- 弩装填物检查
- 容器内部递归扫描
- 拾取 / 使用 / 近战入口的异常检测

> `name-contains` 可被玩家通过铁砧改名伪造，除非确实需要，建议保持为空。  
> Slimefun 与无尽贪婪物品已经可以通过 PDC 命名空间识别。

如果附属模组使用了别的命名空间，把对应命名空间加入 `pdc-namespaces` 即可。

---

## 主要配置项

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `check-online-seconds` | `180` | 在线玩家扫描周期（秒） |
| `check-on-container-open` | `true` | 打开容器时检查内容 |
| `check-on-join` | `true` | 上线时检查背包 |
| `check-ender-chest-on-join` | `true` | 上线时检查末影箱 |
| `keep-unbreaking-255` | `true` | 保留耐久 255 |
| `remove-invalid-enchants` | `true` | 移除非法附魔组合 |
| `respect-server-max-level` | `true` | 尊重服务端附魔上限 |
| `shulker-check-depth` | `3` | 潜影盒 / 收纳袋递归深度（0-6） |
| `delete-illegal-items` | `true` | 是否替换违禁物品 |
| `max-attribute-amount` | `1000` | 非白名单物品的通用属性上限 |
| `backup-before-delete` | `true` | 删除前备份 |
| `potion-max-effects` | `6` | 单件药水最大效果数量 |
| `potion-max-potency` | `9` | 未知 / 自定义效果的最大等级（0 基） |
| `potion-max-duration` | `12000` | 非瞬时效果最大持续时间（tick） |
| `ground-item-scan-seconds` | `40` | 地面物品扫描周期（0 关闭） |
| `effect-check-seconds` | `15` | 玩家状态效果检查周期 |
| `arrow-speed-enabled` | `true` | 箭矢限速开关 |
| `arrow-max-speed` | `5.0` | 最大箭矢速度（格/tick） |
| `arrow-max-damage` | `30` | 最大箭矢伤害 |
| `melee-check-enabled` | `true` | 近战命中检查开关 |
| `melee-check-min-damage` | `30` | 触发检查的最低伤害（0 = 每次命中） |
| `admin-permission` | `anti255.admin` | 管理员权限节点 |

完整注释见 `config.yml`。

---

## 已知限制与设计取舍

1. **白名单依赖 PDC 命名空间判断**  
   PDC 命名空间属于玩家可写 NBT，伪造 `slimefun` 标记仍可能绕过附魔 / 药水检查。属性有 1,000,000 硬上限，容器内容始终递归扫描，但白名单物品本身不会被降级。需要更严格校验时可接入 Slimefun API 验证真实物品 ID。

2. **不检测超堆叠**  
   按服务器玩法需要，允许超过原版堆叠上限。

3. **不检测 255 耐久**  
   `keep-unbreaking-255: true` 时保留耐久 255；这是有意设计。

4. **不扫描玩家自身属性**  
   目前只检查物品属性，不检查通过 `/attribute` 或其他插件直接给玩家添加的属性。

5. **异常弹射物清理依赖删除开关**  
   `delete-illegal-items: false` 时只记录，不移除。

6. **周期扫描是最终兜底**  
   点击、拾取、使用、近战等入口提供即时检查；即使某个入口因版本差异失效，周期扫描仍会处理。

---

## 构建

### 方式一：构建脚本（无需 Maven）

需要 JDK 21，并且 `build/lib/` 中已放置依赖 jar（Paper API、Adventure 等）。

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

脚本会：

1. 清理 `build/classes`
2. 编译全部源码
3. 复制 `src/main/resources`
4. 从 `plugin.yml` 读取版本号
5. 生成 `ACEforDRS-<version>.jar`

### 方式二：Maven

```bash
mvn clean package
```

产物位于 `target/`。

---

## 项目结构

```text
src/main/java/com/acefordrs/
├── Anti255Plugin.java              事件处理、命令、调度、生命周期
├── Anti255Scanner.java             扫描主流程、附魔修复、容器递归、拾取扫描
├── Anti255IllegalItemDetector.java 违禁材质、属性、药水、弩装填物检测
├── Anti255MaliciousNBTDetector.java 白名单识别、深度 NBT 修复
├── Anti255ArrowSpeedLimiter.java   箭矢速度与伤害限制
├── Anti255Config.java              配置字段与加载
├── Anti255ConfigFile.java          配置补写与废弃键清理
└── Anti255Messages.java            多语言消息

src/main/resources/
├── config.yml
├── messages.yml
└── plugin.yml
```

---

## 更新日志

见 [CHANGELOG.md](CHANGELOG.md)。

---

## 许可

[MIT](LICENSE)
