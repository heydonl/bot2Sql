---
name: changelog-generator
description: 从 Git 提交历史生成变更日志。当用户说"生成变更日志"、"更新变更日志"、"自上次发布以来有什么变更"或准备新版本发布时使用。
---

# 变更日志生成器技能

为 Java 项目从约定式提交生成变更日志。

## 何时使用
- 发布前
- 用户说"生成变更日志" / "更新变更日志" / "自上次发布以来有什么变更"
- 完成里程碑后

## 版本控制约定检测

按以下优先级顺序检测版本控制风格：

### 1. 检查 CLAUDE.md（如果存在）
```bash
grep -A5 "## Versioning" CLAUDE.md 2>/dev/null
```

查找明确的约定：
```markdown
## Versioning
This project uses Semantic Versioning (x.y.z).
Tag format: `release-x.y.z`
```

### 2. 回退：从 Git 标签检测
```bash
git tag --sort=-version:refname | head -10
```

| 检测到的模式 | 版本控制风格 |
|------------------|---------------------|
| `v3.15.0`, `3.15.0` | SemVer (x.y.z) |
| `release-3.15.0` | 带前缀的 SemVer |
| `v2.1`, `2.1` | 两段式 (x.y) |
| `2026.01`, `26.1` | CalVer |
| 无模式 | 询问用户 |

### 3. 回退：从 CHANGELOG.md 检测
```bash
grep -E "^\\#+ \\[.*\\]" CHANGELOG.md | head -5
```

从现有条目中提取版本格式。

### 4. 最后手段：询问用户
```
未检测到版本控制约定。此项目使用哪种格式？
- 语义化版本控制 (x.y.z) - 例如 3.15.0
- 两段式 (x.y) - 例如 2.1
- 日历版本控制 - 例如 2026.01
```

### 支持的版本控制风格

| 风格 | 格式 | 标签示例 | 版本递增 |
|-------|--------|--------------|--------------|
| SemVer | x.y.z | `v3.15.0`, `release-3.15.0` | major.minor.patch |
| 两段式 | x.y | `v2.1`, `2.1` | major.minor |
| CalVer | YYYY.MM[.patch] | `2026.01`, `2026.01.1` | year.month[.patch] |

### 遗留项目（CLAUDE.md 中没有版本控制部分）

如果 CLAUDE.md 存在但没有版本控制信息：
1. 不要假设 - 从标签/变更日志检测
2. 如果检测到，可选择建议添加到 CLAUDE.md：
   ```
   检测到的版本控制：SemVer (x.y.z)，标签前缀为 'release-'
   要我将此添加到 CLAUDE.md 以供将来参考吗？
   ```

## 输出格式

支持两种格式 - 从现有 CHANGELOG.md 检测或询问用户偏好。

### 格式 A：Keep a Changelog（h2 版本）
```markdown
# Changelog

## [Unreleased]

## [1.2.0] - 2026-01-29

### Added
- [#123]: New feature for plugin dependencies

### Changed
- [#456]: Improved performance of plugin loading

### Fixed
- [#234]: Resolved NPE when directory missing
```

### 格式 B：pf4j 风格（h3 版本）
```markdown
## Change Log

### [Unreleased][unreleased]

### [3.15.0] - 2026-01-29

#### Added
- [#123]: New feature for plugin dependencies

#### Changed
- [#456]: Improved performance of plugin loading

#### Fixed
- [#234]: Resolved NPE when directory missing
```

## 引用样式链接（推荐）

使用引用样式链接以获得更简洁、更易读的条目：

```markdown
#### Fixed
- [#648]: Restore missing `module-info.class` in multi-release JAR
- [#625]: Fix exception handling inconsistency in `startPlugin()`

#### Added
- [#629]: Validate dependency state on plugin start
- [#633]: Allow customization of `PluginClassLoader` parent delegation

<!-- 在文件底部 -->
[#648]: https://github.com/user/repo/issues/648
[#633]: https://github.com/user/repo/pull/633
[#629]: https://github.com/user/repo/pull/629
[#625]: https://github.com/user/repo/pull/625
```

**优点：**
- 阅读更简洁（没有内联的长 URL）
- 链接定义一次，可重用
- 更易于编写和维护

## 版本比较链接

在底部添加比较链接以便于查看差异：

```markdown
[unreleased]: https://github.com/user/repo/compare/release-3.15.0...HEAD
[3.15.0]: https://github.com/user/repo/compare/release-3.14.1...release-3.15.0
[3.14.1]: https://github.com/user/repo/compare/release-3.14.0...release-3.14.1
```

**模式：** `[version]: https://github.com/{owner}/{repo}/compare/{previous-tag}...{current-tag}`

## 章节顺序

适配现有文件，或使用此默认顺序：

| 章节 | 何时使用 |
|---------|-------------|
| Fixed | Bug 修复 |
| Changed | 对现有功能的更改 |
| Added | 新功能 |
| Deprecated | 即将移除的功能 |
| Removed | 已移除的功能 |
| Security | 漏洞修复（CVE） |

注意：pf4j 使用 Fixed → Changed → Added → Removed。Keep a Changelog 使用 Added → Changed → Deprecated → Removed → Fixed → Security。

**规则：如果存在，遵循现有文件的顺序。**

## 约定式提交到变更日志的映射

| 提交类型 | 变更日志章节 |
|-------------|-------------------|
| feat | Added |
| fix | Fixed |
| perf | Changed |
| refactor | Changed |
| build(deps) | Changed 或 Security（如果是 CVE） |
| BREAKING CHANGE | Changed（带粗体注释） |
| deprecate | Deprecated |

## 工作流程

1. **检查现有 CHANGELOG.md**
   ```bash
   cat CHANGELOG.md | head -20
   ```
   检测格式（h2 vs h3 版本、章节顺序、链接样式）。

2. **确定版本范围**
   ```bash
   # 查找最后一个标签
   git describe --tags --abbrev=0

   # 列出最近的标签
   git tag --sort=-version:refname | head -5
   ```

3. **获取自上次发布以来的提交**
   ```bash
   git log v3.14.1..HEAD --oneline
   ```

4. **提取 issue/PR 引用**
   查找模式：`#123`、`fixes #123`、`closes #123`、`(#123)`

5. **生成变更日志条目**
   - 按章节分组
   - 使用引用样式链接
   - 添加版本比较链接

6. **建议版本递增**（基于检测到的版本控制风格）

   **SemVer (x.y.z)：**
   - BREAKING CHANGE → Major (3.0.0 → 4.0.0)
   - feat → Minor (3.14.0 → 3.15.0)
   - 仅 fix → Patch (3.14.0 → 3.14.1)

   **两段式 (x.y)：**
   - BREAKING CHANGE → Major (2.0 → 3.0)
   - feat/fix → Minor (2.1 → 2.2)

   **CalVer (YYYY.MM)：**
   - 新月份 → 2026.01 → 2026.02
   - 同月新版本 → 2026.01 → 2026.01.1

## Token 优化

- 使用 `git log --oneline` 进行初始扫描
- 仅在怀疑有 BREAKING CHANGE 时获取完整正文
- 重用文件中现有的链接定义
- 不要重新读取整个变更日志 - 只需在前面添加新章节

## 示例：完整工作流程

**输入：** 用户说"为下一个版本生成变更日志"

**步骤 1：** 检查现有格式
```bash
head -30 CHANGELOG.md
```
→ 检测到 pf4j 风格（h3 版本，Fixed 在前）

**步骤 2：** 查找版本范围
```bash
git describe --tags --abbrev=0
```
→ `release-3.15.0`

**步骤 3：** 获取提交
```bash
git log release-3.15.0..HEAD --oneline
```
→ 找到 5 个提交

**步骤 4：** 生成条目
```markdown
### [Unreleased][unreleased]

#### Fixed
- [#650]: Fix memory leak in extension factory

#### Changed
- [#651]: Rename `LegacyExtension*` to `IndexedExtension*`

#### Added
- [#652]: Add support for plugin priority ordering
```

**步骤 5：** 生成链接定义
```markdown
[#652]: https://github.com/pf4j/pf4j/pull/652
[#651]: https://github.com/pf4j/pf4j/pull/651
[#650]: https://github.com/pf4j/pf4j/issues/650
```

**步骤 6：** 更新版本比较链接
```markdown
[unreleased]: https://github.com/pf4j/pf4j/compare/release-3.15.0...HEAD
```

**步骤 7：** 建议版本
```
建议：3.16.0（minor - 有新功能）
```

## 处理边缘情况

### 没有约定式提交
在"Changed"下列出原始消息：
```markdown
#### Changed
- Updated plugin loading mechanism
- Refactored test utilities
```

### 安全修复
```markdown
#### Security
- [#618], [#623]: Fix path traversal vulnerabilities in ZIP extraction
```

### 破坏性变更
```markdown
#### Changed
- **BREAKING**: [#645] Renamed `LegacyExtension*` classes to `IndexedExtension*`
```

### 同一修复的多个 issue
```markdown
- [#630], [#631]: Set `failedException` when plugin validation fails
```

## 与现有 CHANGELOG.md 集成

1. **读取现有文件**以检测：
   - 标题级别（版本使用 ## 或 ###）
   - 章节顺序
   - 链接样式（引用或内联）
   - 现有链接定义

2. **在 `[Unreleased]` 章节后插入新版本**

3. **合并链接定义** - 添加新的，保留现有的

4. **更新 `[unreleased]` 比较链接**以指向新版本

## 快速参考

| 用户说 | 操作 |
|-----------|--------|
| "生成变更日志" | 自上次标签以来的完整变更日志 |
| "自 v3.14 以来的变更日志" | 从特定版本开始 |
| "未发布的内容是什么" | 预览未发布的变更 |
| "为 3.16 更新变更日志" | 为版本生成并插入 |
| "为 #123 添加变更日志条目" | 单个 issue 条目 |
