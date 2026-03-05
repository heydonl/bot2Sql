# 变更日志生成器

**加载**: `view .claude/skills/changelog-generator/SKILL.md`

---

## 描述

从 Git 提交历史生成遵循既定约定的变更日志。自动从现有项目文件（CLAUDE.md、Git 标签、CHANGELOG.md）检测版本控制风格和变更日志格式。

---

## 使用场景

- "生成自上次发布以来的变更日志"
- "v3.14.0 以来有什么变更？"
- "为版本 3.16 更新 CHANGELOG.md"
- "预览未发布的变更"

---

## 示例

```
> view .claude/skills/changelog-generator/SKILL.md
> "为 pf4j 生成变更日志"
→ 检测 pf4j 格式和 SemVer 风格，输出匹配的变更日志
```

---

## 主要特性

- **版本控制检测**: SemVer (x.y.z)、两段式 (x.y)、CalVer (YYYY.MM)
- **格式检测**: 适配现有 CHANGELOG.md 风格
- **引用样式链接**: 简洁的 `[#123]` 格式，定义放在底部
- **版本比较链接**: 自动生成 GitHub 比较 URL
- **遗留项目支持**: 适用于没有明确约定的项目

---

## 检测优先级

1. CLAUDE.md 的 `## Versioning` 部分
2. Git 标签模式分析
3. 现有 CHANGELOG.md 格式
4. 询问用户（最后手段）

---

## 注意事项 / 提示

- 与约定式提交配合使用效果最佳（可与 git-commit 技能配对）
- 对于遗留项目，建议在 CLAUDE.md 中添加版本控制约定
- 更新时保留现有链接定义
