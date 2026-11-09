# 更新日志

本文件记录 ACE for DRS 的功能变更与修复。  
`2.0.0` – `2.0.3` 为初始修复版本，未逐项记录；以下从 `2.0.4` 开始。

---

## 2.2.0

### 新增

- 弩装填物完整检测：逐发检查材质、属性、药水效果数量 / 等级 / 时长，异常弹射物直接从弩中移除
- 拾取扫描递归检查潜影盒与收纳袋内部，支持多层嵌套
- 潜影盒 / 收纳袋修复结果正确写回容器
- `messages.yml` 新增检测原因本地化：`reason-illegal-material`、`reason-abnormal-attribute`、`reason-abnormal-potion`
- 新增 `build.ps1`：无需 Maven 即可编译打包

### 改进

- 白名单容器不再跳过内部扫描，伪造白名单标记无法隐藏容器内异常物品
- 配置补写逻辑拆分到独立的 `Anti255ConfigFile`
- 缺失配置项按默认顺序插入，不再统一追加到段落末尾
- 自动移除废弃的 `exempt-permission`、`exempt-players`
- 配置补写兼容 UTF-8 BOM
- `arrow-max-speed`、`arrow-max-damage`、`max-attribute-amount` 增加有限值校验
- 潜影盒 / 收纳袋内部逐物品异常保护，单个损坏物品不会中断整轮扫描
- 拾取、光标、近战、使用前检测增加异常保护
- 合并玩家效果检查与药水物品检查的重复上限表
- `totalScanned` 改为统计实际扫描物品数

### 修复

- 修复嵌套物品附魔降级后未写回容器的问题
- 修复弩装填物空槽位处理
- 修复 `shift-click` 在玩家背包视图中重复扫描的问题
- 修复配置注释中残留的旧数据目录名

---

## 2.1.1

### 性能

- 无 `ItemMeta` 的普通物品走快速路径
- 违禁材质改为预构建 `Set<Material>`
- 潜影盒 / 收纳袋材质改为静态集合
- 附魔表为空时提前返回
- 白名单命名空间去重并移除重复 `toLowerCase()`
- 属性 key 复用
- 实体查询 `Class` 数组静态复用
- 地面掉落物中的普通物品直接跳过

---

## 2.1.0

### 新增

- 周期扫描错峰：保持每玩家 180 秒，但一轮扫描分摊到整个周期
- 点击 / 拖动只扫描受影响的 `rawSlot`
- 新增光标物品扫描
- 新增 `EntityPickupItemEvent` 拾取拦截
- 新增 `PlayerSwapHandItemsEvent` 副手扫描
- 新增 `PlayerItemConsumeEvent` / `PlayerInteractEvent` 使用前拦截
- `messages.yml` 新增 `illegal-item-use`

### 改进

- 扫描链路中每件物品只读取一次 `ItemMeta`
- 白名单识别结果在扫描链路中复用
- `shift-click` 回退为全量目标容器 + 玩家背包扫描

---

## 2.0.6

### 修复

- 修复白名单物品提前返回导致属性硬上限不生效
- 消除重复 `isWhitelisted()` 调用
- 删除无用的 `arrowInitialSpeed` 与两个未使用常量
- 换甲扫描增加 500ms 节流
- 删除未使用的 `detectAbnormalAttributes(ItemStack)`

---

## 2.0.5

### 变更

- 移除 `anti255.exempt` 权限、`exempt-permission`、`exempt-players` 与 `isExempt()`
- 管理员与普通玩家的唯一区别改为：管理员可使用管理员物品
- 白名单配置移至 `config.yml`
- 新增配置补写：启动与 `/anti255 reload` 时补齐缺失项，修改前备份 `config.yml.bak`

---

## 2.0.4

### 新增

- 属性作弊检测与分属性合理上限
- 白名单伪造防护：属性硬上限 1,000,000
- 近战命中拦截
- 快捷栏 / 换甲即时扫描
- 分效果药水等级上限
