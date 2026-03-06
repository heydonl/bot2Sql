# 前后端分离迁移 - 完整方案

## 📋 概述

SQL2Bot 是一个生产项目，需要采用前后端分离的最佳实践。本方案使用 **Git Submodule** 将前端项目独立管理。

## 🎯 目标

- ✅ 前端独立为 `sql2bot-frontend` 仓库
- ✅ 主项目通过 submodule 引用前端
- ✅ 保持开发流程简单
- ✅ 支持灵活部署

## 📚 文档清单

| 文档 | 用途 | 适用人群 |
|------|------|----------|
| **QUICK_MIGRATION_CHECKLIST.md** | 快速执行清单 | 执行迁移的人 |
| **MIGRATION_EXECUTION_PLAN.md** | 详细执行计划 | 项目负责人 |
| **FRONTEND_MIGRATION_GUIDE.md** | 完整迁移指南 | 所有开发者 |
| **FRONTEND_SEPARATION.md** | 架构说明 | 技术决策者 |
| **.gitmodules.example** | 配置示例 | 参考 |

## 🚀 快速开始

### 对于执行迁移的人

1. 阅读 `QUICK_MIGRATION_CHECKLIST.md`
2. 按照清单逐步执行
3. 总耗时约 35 分钟

### 对于团队成员

迁移完成后，克隆项目：
```bash
git clone --recurse-submodules <repo-url>
```

或更新现有项目：
```bash
git pull
git submodule init
git submodule update
```

## 🛠️ 工具

### 自动迁移脚本

- **migrate-frontend.sh** - Linux/Mac/Git Bash
- **migrate-frontend.bat** - Windows

### 配置文件

- **.gitmodules.example** - Submodule 配置示例
- **.gitignore** - 已更新，包含 submodule 说明

## 📖 关键概念

### Git Submodule

Submodule 允许你在一个 Git 仓库中包含另一个 Git 仓库作为子目录。

**优势**:
- 独立版本控制
- 清晰的项目边界
- 灵活的部署选项

**注意事项**:
- 需要额外的 `git submodule` 命令
- 团队成员需要了解基本用法
- CI/CD 需要配置 `--recurse-submodules`

## 🔄 工作流程

### 前端开发
```bash
cd frontend
git checkout -b feature/xxx
# 开发...
git commit -m "feat: xxx"
git push origin feature/xxx
# 创建 PR，合并后
git checkout main && git pull
cd .. && git add frontend && git commit -m "chore: 更新前端"
```

### 后端开发
```bash
# 正常的 git 流程，不受影响
git checkout -b feature/xxx
# 开发...
git commit -m "feat: xxx"
git push
```

## 🚨 回滚方案

如果迁移出现问题，可以快速回滚：

```bash
git checkout backup-before-frontend-migration
git checkout -b main-restored
git push origin main-restored --force
```

## ✅ 验证清单

迁移完成后，确认：

- [ ] 前端仓库已创建并推送
- [ ] 主项目已配置 submodule
- [ ] 本地测试通过（后端 + 前端都能启动）
- [ ] 新克隆测试通过
- [ ] 团队成员已通知
- [ ] CI/CD 已更新（如果有）
- [ ] 文档已更新

## 📞 支持

遇到问题？

1. 查看 `FRONTEND_MIGRATION_GUIDE.md` 的常见问题部分
2. 查看 Git Submodule 官方文档
3. 联系项目负责人

## 🎓 学习资源

- [Git Submodules 官方文档](https://git-scm.com/book/en/v2/Git-Tools-Submodules)
- [Pro Git Book - Submodules](https://git-scm.com/book/en/v2/Git-Tools-Submodules)
- 项目内文档：`FRONTEND_MIGRATION_GUIDE.md`

## 📊 迁移时间线

```
准备阶段 (5分钟)
  ↓
执行阶段 (15分钟)
  ↓
验证阶段 (10分钟)
  ↓
完成阶段 (5分钟)
  ↓
总计: 35分钟
```

## 🎯 下一步

1. **立即执行**: 使用 `QUICK_MIGRATION_CHECKLIST.md`
2. **详细了解**: 阅读 `MIGRATION_EXECUTION_PLAN.md`
3. **团队培训**: 分享 `FRONTEND_MIGRATION_GUIDE.md`

---

**准备好了吗？开始迁移！** 🚀

建议执行时间：非高峰期，确保有时间处理可能的问题。
