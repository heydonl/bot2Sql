---
name: clean-code
description: 整洁代码原则（DRY、KISS、YAGNI）、命名约定、函数设计和重构。当用户说"整理这段代码"、"重构"、"提高可读性"，或审查代码质量时使用。
---

# 整洁代码技能

遵循整洁代码原则编写可读、可维护的代码。

## 何时使用
- 用户说"整理这段代码" / "重构" / "提高可读性"
- 关注可维护性的代码审查
- 降低复杂度
- 改进命名

---

## 核心原则

| 原则 | 含义 | 违反标志 |
|------|------|---------|
| **DRY** | 不要重复自己 | 复制粘贴的代码块 |
| **KISS** | 保持简单 | 过度设计的解决方案 |
| **YAGNI** | 你不会需要它 | "以防万一"的功能 |

---

## DRY - 不要重复自己

> "每一项知识在系统中都必须有单一、明确的表示。"

### 违反

```java
// ❌ 不好：重复的验证逻辑
public class UserController {

    public void createUser(UserRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new ValidationException("邮箱是必需的");
        }
        if (!request.getEmail().contains("@")) {
            throw new ValidationException("无效的邮箱格式");
        }
        // ... 创建用户
    }

    public void updateUser(UserRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new ValidationException("邮箱是必需的");
        }
        if (!request.getEmail().contains("@")) {
            throw new ValidationException("无效的邮箱格式");
        }
        // ... 更新用户
    }
}
```

### 重构后

```java
// ✅ 好：单一真相来源
public class EmailValidator {

    public void validate(String email) {
        if (email == null || email.isBlank()) {
            throw new ValidationException("邮箱是必需的");
        }
        if (!email.contains("@")) {
            throw new ValidationException("无效的邮箱格式");
        }
    }
}

public class UserController {
    private final EmailValidator emailValidator;

    public void createUser(UserRequest request) {
        emailValidator.validate(request.getEmail());
        // ... 创建用户
    }

    public void updateUser(UserRequest request) {
        emailValidator.validate(request.getEmail());
        // ... 更新用户
    }
}
```

### DRY 例外

并非所有重复都是坏的。避免过早抽象：

```java
// 这些看起来相似但服务于不同目的 - 可以重复
public BigDecimal calculateShippingCost(Order order) {
    return order.getWeight().multiply(SHIPPING_RATE);
}

public BigDecimal calculateInsuranceCost(Order order) {
    return order.getValue().multiply(INSURANCE_RATE);
}
// 不要强制将它们合并为一个方法 - 它们会以不同方式演化
```

---

## KISS - 保持简单

> "最简单的解决方案通常是最好的。"

### 违反

```java
// ❌ 不好：简单任务过度设计
public class StringUtils {

    public boolean isEmpty(String str) {
        return Optional.ofNullable(str)
            .map(String::trim)
            .map(String::isEmpty)
            .orElseGet(() -> Boolean.TRUE);
    }
}
```

### 重构后

```java
// ✅ 好：简单清晰
public class StringUtils {

    public boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    // 或使用现有库
    // return StringUtils.isBlank(str);  // Apache Commons
    // return str == null || str.isBlank();  // Java 11+
}
```

### KISS 检查清单

- 初级开发者能在 30 秒内理解吗？
- 使用标准库有更简单的方法吗？
- 我是在为可能永远不会发生的边界情况增加复杂性吗？

---

## YAGNI - 你不会需要它

> "在必要之前不要添加功能。"

### 违反

```java
// ❌ 不好：为假设的未来构建
public interface Repository<T, ID> {
    T findById(ID id);
    List<T> findAll();
    List<T> findAll(Pageable pageable);
    List<T> findAll(Sort sort);
    List<T> findAllById(Iterable<ID> ids);
    T save(T entity);
    List<T> saveAll(Iterable<T> entities);
    void delete(T entity);
    void deleteById(ID id);
    void deleteAll(Iterable<T> entities);
    void deleteAll();
    boolean existsById(ID id);
    long count();
    // ... 另外 20 个"以防万一"的方法
}

// 当前使用：只有 findById 和 save
```

### 重构后

```java
// ✅ 好：只要现在需要的
public interface UserRepository {
    Optional<User> findById(Long id);
    User save(User user);
}

// 在实际需要时添加方法，而不是提前
```

### YAGNI 标志

- "我们以后可能需要这个"
- "让我们以防万一使其可配置"
- "如果将来需要支持 X 怎么办？"
- 只有一个实现的抽象类

---

## 命名约定

### 变量

```java
// ❌ 不好
int d;                  // d 是什么？
String s;               // 无意义
List<User> list;        // 什么类型的列表？
Map<String, Object> m;  // 映射什么？

// ✅ 好
int elapsedTimeInDays;
String customerName;
List<User> activeUsers;
Map<String, Object> sessionAttributes;
```

### 布尔值

```java
// ❌ 不好
boolean flag;
boolean status;
boolean check;

// ✅ 好 - 使用 is/has/can/should 前缀
boolean isActive;
boolean hasPermission;
boolean canEdit;
boolean shouldNotify;
```

### 方法

```java
// ❌ 不好
void process();           // 处理什么？
void handle();            // 处理什么？
void doIt();              // 做什么？
User get();               // 从哪里获取？

// ✅ 好 - 动词 + 名词，描述性
void processPayment();
void handleLoginRequest();
void sendWelcomeEmail();
User findByEmail(String email);
List<Order> fetchPendingOrders();
```

### 类

```java
// ❌ 不好
class Data { }           // 太模糊
class Info { }           // 太模糊
class Manager { }        // 通常是上帝类
class Helper { }         // 通常是垃圾场
class Utils { }          // 静态方法垃圾场

// ✅ 好 - 名词，特定职责
class User { }
class OrderProcessor { }
class EmailValidator { }
class PaymentGateway { }
class ShippingCalculator { }
```

### 命名约定表

| 元素 | 约定 | 示例 |
|------|------|------|
| 类 | 帕斯卡命名，名词 | `OrderService` |
| 接口 | 帕斯卡命名，形容词或名词 | `Comparable`、`List` |
| 方法 | 驼峰命名，动词 | `calculateTotal()` |
| 变量 | 驼峰命名，名词 | `customerEmail` |
| 常量 | 大写下划线 | `MAX_RETRY_COUNT` |
| 包 | 小写 | `com.example.orders` |

---

## 函数/方法

### 保持函数简短

```java
// ❌ 不好：50+ 行方法做多件事
public void processOrder(Order order) {
    // 验证订单（10 行）
    // 计算总额（15 行）
    // 应用折扣（10 行）
    // 更新库存（10 行）
    // 发送通知（10 行）
    // ... 更多
}

// ✅ 好：小而专注的方法
public void processOrder(Order order) {
    validateOrder(order);
    calculateTotals(order);
    applyDiscounts(order);
    updateInventory(order);
    sendNotifications(order);
}
```

### 单一抽象层次

```java
// ❌ 不好：混合抽象层次
public void processOrder(Order order) {
    validateOrder(order);  // 高层次

    // 混入低层次
    BigDecimal total = BigDecimal.ZERO;
    for (OrderItem item : order.getItems()) {
        total = total.add(item.getPrice().multiply(
            BigDecimal.valueOf(item.getQuantity())));
    }

    sendEmail(order);  // 又回到高层次
}

// ✅ 好：一致的抽象层次
public void processOrder(Order order) {
    validateOrder(order);
    calculateTotal(order);
    sendConfirmation(order);
}

private BigDecimal calculateTotal(Order order) {
    return order.getItems().stream()
        .map(item -> item.getPrice().multiply(
            BigDecimal.valueOf(item.getQuantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
}
```

### 限制参数

```java
// ❌ 不好：太多参数
public User createUser(String firstName, String lastName,
                       String email, String phone,
                       String address, String city,
                       String country, String zipCode) {
    // ...
}

// ✅ 好：使用参数对象
public User createUser(CreateUserRequest request) {
    // ...
}

// 或建造者
public User createUser(UserBuilder builder) {
    // ...
}
```

### 避免标志参数

```java
// ❌ 不好：布尔标志改变行为
public void sendMessage(String message, boolean isUrgent) {
    if (isUrgent) {
        // 立即发送
    } else {
        // 排队稍后发送
    }
}

// ✅ 好：分开的方法
public void sendUrgentMessage(String message) {
    // 立即发送
}

public void queueMessage(String message) {
    // 排队稍后发送
}
```

---

## 注释

### 避免明显的注释

```java
// ❌ 不好：噪音注释
// 设置用户的名字
user.setName(name);

// 增加计数器
counter++;

// 检查用户是否为 null
if (user != null) {
    // ...
}
```

### 好的注释

```java
// ✅ 好：解释为什么，而不是什么

// 使用指数退避重试以避免在高负载期间压垮服务器
// （参见事件 #1234）
for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
    Thread.sleep((long) Math.pow(2, attempt) * 1000);
    // ...
}

// TODO: 基础设施升级后替换为 Redis 缓存（2026 年 Q2）
private Map<String, User> userCache = new ConcurrentHashMap<>();

// 警告：顺序很重要！折扣必须在税费计算之前应用
applyDiscounts(order);
calculateTax(order);
```

### 让代码自己说话

```java
// ❌ 不好：注释解释糟糕的代码
// 检查用户是否是管理员或有特殊权限
// 并且该操作对其角色是允许的
if ((user.getRole() == 1 || user.getRole() == 2) &&
    (action == 3 || action == 4 || action == 7)) {
    // ...
}

// ✅ 好：自文档化代码
if (user.hasAdminPrivileges() && action.isAllowedFor(user.getRole())) {
    // ...
}
```

---

## 常见代码异味

| 异味 | 描述 | 重构 |
|------|------|------|
| **长方法** | 方法 > 20 行 | 提取方法 |
| **长参数列表** | > 3 个参数 | 参数对象 |
| **重复代码** | 多处相同代码 | 提取方法/类 |
| **死代码** | 未使用的代码 | 删除它 |
| **魔法数字** | 未解释的字面量 | 命名常量 |
| **上帝类** | 类做太多事 | 提取类 |
| **特性依恋** | 方法使用另一个类的数据 | 移动方法 |
| **基本类型偏执** | 基本类型而不是对象 | 值对象 |

### 魔法数字

```java
// ❌ 不好
if (user.getAge() >= 18) { }
if (order.getTotal() > 100) { }
Thread.sleep(86400000);

// ✅ 好
private static final int ADULT_AGE = 18;
private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("100");
private static final long ONE_DAY_MS = TimeUnit.DAYS.toMillis(1);

if (user.getAge() >= ADULT_AGE) { }
if (order.getTotal().compareTo(FREE_SHIPPING_THRESHOLD) > 0) { }
Thread.sleep(ONE_DAY_MS);
```

### 基本类型偏执

```java
// ❌ 不好：到处都是基本类型
public void createUser(String email, String phone, String zipCode) {
    // 没有验证，容易混淆参数
}

createUser("12345", "john@email.com", "555-1234");  // 顺序错误，但能编译！

// ✅ 好：值对象
public record Email(String value) {
    public Email {
        if (!value.contains("@")) {
            throw new IllegalArgumentException("无效的邮箱");
        }
    }
}

public record PhoneNumber(String value) {
    // 验证
}

public void createUser(Email email, PhoneNumber phone, ZipCode zipCode) {
    // 类型安全，自验证
}
```

---

## 重构快速参考

| 从 | 到 | 技术 |
|----|----|----|
| 长方法 | 短方法 | 提取方法 |
| 重复代码 | 单一方法 | 提取方法 |
| 复杂条件 | 多态 | 用多态替换条件 |
| 多个参数 | 对象 | 引入参数对象 |
| 临时变量 | 查询方法 | 用查询替换临时变量 |
| 解释代码的注释 | 自文档化代码 | 重命名、提取 |
| 嵌套条件 | 提前返回 | 卫语句 |

### 卫语句

```java
// ❌ 不好：深度嵌套
public void processOrder(Order order) {
    if (order != null) {
        if (order.isValid()) {
            if (order.hasItems()) {
                // 实际逻辑埋在这里
            }
        }
    }
}

// ✅ 好：卫语句
public void processOrder(Order order) {
    if (order == null) return;
    if (!order.isValid()) return;
    if (!order.hasItems()) return;

    // 实际逻辑在顶层
}
```

---

## 整洁代码检查清单

审查代码时，检查：

- [ ] 名称是否有意义且可发音？
- [ ] 函数是否小而专注？
- [ ] 是否有重复的代码？
- [ ] 是否有魔法数字或字符串？
- [ ] 注释是否解释"为什么"而不是"什么"？
- [ ] 代码是否在一致的抽象层次？
- [ ] 是否可以简化任何代码？
- [ ] 是否有死代码/未使用的代码？

---

## 相关技能

- `solid-principles` - 类结构的设计原则
- `design-patterns` - 常见问题的通用解决方案
- `java-code-review` - 全面的审查清单
