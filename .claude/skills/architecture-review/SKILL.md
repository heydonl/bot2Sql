---
name: architecture-review
description: 在宏观层面分析 Java 项目架构 - 包结构、模块边界、依赖方向和分层。当用户询问"审查架构"、"检查结构"、"包组织"或评估代码库是否遵循整洁架构原则时使用。
---

# 架构审查技能

在宏观层面分析项目结构 - 包、模块、层和边界。

## 何时使用
- 用户询问"审查架构" / "检查项目结构"
- 评估包组织
- 检查层之间的依赖方向
- 识别架构违规
- 评估整洁/六边形架构合规性

---

## 快速参考：架构问题

| 问题 | 症状 | 影响 |
|-------|---------|-----------|
| 按层打包膨胀 | `service/` 有 50+ 个类 | 难以找到相关代码 |
| 领域 → 基础设施依赖 | 实体导入 `@Repository` | 核心逻辑绑定到框架 |
| 循环依赖 | A → B → C → A | 不可测试、脆弱 |
| 上帝包 | `util/` 或 `common/` 增长 | 错位代码的垃圾场 |
| 泄漏抽象 | 控制器知道 SQL | 层边界被违反 |

---

## 包组织策略

### 按层打包（传统）

```
com.example.app/
├── controller/
│   ├── UserController.java
│   ├── OrderController.java
│   └── ProductController.java
├── service/
│   ├── UserService.java
│   ├── OrderService.java
│   └── ProductService.java
├── repository/
│   ├── UserRepository.java
│   ├── OrderRepository.java
│   └── ProductRepository.java
└── model/
    ├── User.java
    ├── Order.java
    └── Product.java
```

**优点**：熟悉，小项目简单
**缺点**：分散相关代码，不可扩展，难以提取模块

### 按功能打包（推荐）

```
com.example.app/
├── user/
│   ├── UserController.java
│   ├── UserService.java
│   ├── UserRepository.java
│   └── User.java
├── order/
│   ├── OrderController.java
│   ├── OrderService.java
│   ├── OrderRepository.java
│   └── Order.java
└── product/
    ├── ProductController.java
    ├── ProductService.java
    ├── ProductRepository.java
    └── Product.java
```

**优点**：相关代码在一起，易于提取，边界清晰
**缺点**：可能需要共享内核处理横切关注点

### 六边形/整洁架构

```
com.example.app/
├── domain/                    # 纯业务逻辑（无框架导入）
│   ├── model/
│   │   └── User.java
│   ├── port/
│   │   ├── in/               # 用例（被驱动）
│   │   │   └── CreateUserUseCase.java
│   │   └── out/              # 仓库（驱动）
│   │       └── UserRepository.java
│   └── service/
│       └── UserDomainService.java
├── application/               # 用例实现
│   └── CreateUserService.java
├── adapter/
│   ├── in/
│   │   └── web/
│   │       └── UserController.java
│   └── out/
│       └── persistence/
│           ├── UserJpaRepository.java
│           └── UserEntity.java
└── config/
    └── BeanConfiguration.java
```

**关键规则**：依赖指向内部（适配器 → 应用 → 领域）

---

## 依赖方向规则

### 黄金法则

```
┌─────────────────────────────────────────┐
│              框架                       │  ← 外层（易变）
├─────────────────────────────────────────┤
│         适配器（Web、DB）                │
├─────────────────────────────────────────┤
│         应用服务                         │
├─────────────────────────────────────────┤
│          领域（核心逻辑）                │  ← 内层（稳定）
└─────────────────────────────────────────┘

依赖必须仅指向内部。
内层不得知道外层。
```

### 需要标记的违规

```java
// ❌ 领域依赖基础设施
package com.example.domain.model;

import org.springframework.data.jpa.repository.JpaRepository;  // 框架泄漏！
import javax.persistence.Entity;  // 领域中的 JPA！

@Entity
public class User {
    // 领域被持久化关注点污染
}

// ❌ 领域依赖适配器
package com.example.domain.service;

import com.example.adapter.out.persistence.UserJpaRepository;  // 方向错误！

// ✅ 领域定义端口，适配器实现
package com.example.domain.port.out;

public interface UserRepository {  // 纯接口，无 JPA
    User findById(UserId id);
    void save(User user);
}
```

---

## 架构审查清单

### 1. 包结构
- [ ] 清晰的组织策略（按层、按功能或六边形）
- [ ] 模块间命名一致
- [ ] 没有无限增长的 `util/` 或 `common/` 包
- [ ] 功能包是内聚的（相关代码在一起）

### 2. 依赖方向
- [ ] 领域零框架导入（Spring、JPA、Jackson）
- [ ] 适配器依赖领域，反之则不然
- [ ] 包之间无循环依赖
- [ ] 清晰的依赖层次结构

### 3. 层边界
- [ ] 控制器不包含业务逻辑
- [ ] 服务不知道 HTTP（无 HttpServletRequest）
- [ ] 仓库不泄漏到控制器
- [ ] 边界处使用 DTO，内部使用领域对象

### 4. 模块边界
- [ ] 每个模块有清晰的公共 API
- [ ] 内部类是包私有的
- [ ] 跨模块通信通过接口
- [ ] 不"跨越"模块访问内部

### 5. 可扩展性指标
- [ ] 能否将功能提取到单独的服务？（微服务就绪）
- [ ] 边界是强制的还是约定的？
- [ ] 添加功能是否需要触及多个包？

---

## 常见反模式

### 1. 大泥球

```
src/main/java/com/example/
└── app/
    ├── User.java
    ├── UserController.java
    ├── UserService.java
    ├── UserRepository.java
    ├── Order.java
    ├── OrderController.java
    ├── ... (一个包中 100+ 个文件)
```

**修复**：引入包结构（从按功能开始）

### 2. Util 垃圾场

```
util/
├── StringUtils.java
├── DateUtils.java
├── ValidationUtils.java
├── SecurityUtils.java
├── EmailUtils.java      # 应该在通知模块中
├── OrderCalculator.java # 应该在订单领域中
└── UserHelper.java      # 应该在用户领域中
```

**修复**：将领域逻辑移到适当的模块，仅保留真正通用的工具

### 3. 贫血领域模型

```java
// 领域对象只是数据
public class Order {
    private Long id;
    private List<OrderLine> lines;
    private BigDecimal total;
    // 只有 getter/setter，无行为
}

// 所有逻辑在"服务"中
public class OrderService {
    public void addLine(Order order, Product product, int qty) { ... }
    public void calculateTotal(Order order) { ... }
    public void applyDiscount(Order order, Discount discount) { ... }
}
```

**修复**：将行为移到领域对象（充血领域模型）

### 4. 领域中的框架耦合

```java
package com.example.domain;

@Entity  // JPA
@Data    // Lombok
@JsonIgnoreProperties(ignoreUnknown = true)  // Jackson
public class User {
    @Id @GeneratedValue
    private Long id;

    @NotBlank  // 验证
    private String email;
}
```

**修复**：将领域模型与持久化/API 模型分离

---

## 分析命令

审查架构时，检查：

```bash
# 包结构概览
find src/main/java -type d | head -30

# 最大的包（潜在的上帝包）
find src/main/java -name "*.java" | xargs dirname | sort | uniq -c | sort -rn | head -10

# 检查领域中的框架导入
grep -r "import org.springframework" src/main/java/*/domain/ 2>/dev/null
grep -r "import javax.persistence" src/main/java/*/domain/ 2>/dev/null

# 查找循环依赖（查找双向导入）
# 检查包 A 是否从 B 导入，B 是否从 A 导入
```

---

## 建议格式

报告发现时：

```markdown
## 架构审查：[项目名称]

### 结构评估
- **组织**：按层打包 / 按功能打包 / 六边形
- **清晰度**：清晰 / 混合 / 不清晰

### 发现

| 严重性 | 问题 | 位置 | 建议 |
|----------|-------|----------|-------------------|
| 高 | 领域导入 Spring | `domain/model/User.java` | 提取纯领域模型 |
| 中 | 上帝包 | `util/`（23 个类） | 分发到功能模块 |
| 低 | 命名不一致 | `service/` vs `services/` | 标准化为 `service/` |

### 依赖分析
[描述依赖流，发现的违规]

### 建议
1. [最高优先级修复]
2. [第二优先级]
3. [锦上添花]
```

---

## Token 优化

对于大型代码库：
1. 从 `find` 开始了解结构
2. 仅检查领域包的框架导入
3. 采样 2-3 个功能进行模式分析
4. 不要读取每个文件 - 寻找模式
