---
name: solid-principles
description: SOLID 原则清单及 Java 示例。审查类、重构代码或用户询问单一职责、开闭、里氏替换、接口隔离或依赖倒置时使用。
---

# SOLID 原则技能

在 Java 代码中审查和应用 SOLID 原则。

## 何时使用
- 用户说"检查 SOLID" / "SOLID 审查" / "这个类做的太多了吗？"
- 审查类设计
- 重构大型类
- 关注设计的代码审查

---

## 快速参考

| 字母 | 原则 | 一句话 |
|--------|-----------|---------------|
| **S** | 单一职责 | 一个类 = 一个改变的理由 |
| **O** | 开闭 | 对扩展开放，对修改关闭 |
| **L** | 里氏替换 | 子类型必须可替换基类型 |
| **I** | 接口隔离 | 多个特定接口 > 一个通用接口 |
| **D** | 依赖倒置 | 依赖抽象，而非具体 |

---

## S - 单一职责原则（SRP）

> "一个类应该只有一个改变的理由。"

### 违规

```java
// ❌ 不好：UserService 做的太多
public class UserService {

    public User createUser(String name, String email) {
        // 验证逻辑
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid email");
        }

        // 持久化逻辑
        User user = new User(name, email);
        entityManager.persist(user);

        // 通知逻辑
        String subject = "Welcome!";
        String body = "Hello " + name;
        emailClient.send(email, subject, body);

        // 审计逻辑
        auditLog.log("User created: " + email);

        return user;
    }
}
```

**问题：**
- 验证改变？修改 UserService
- 邮件模板改变？修改 UserService
- 审计格式改变？修改 UserService
- 难以单独测试每个关注点

### 重构后

```java
// ✅ 好：每个类有一个职责

public class UserValidator {
    public void validate(String name, String email) {
        if (email == null || !email.contains("@")) {
            throw new ValidationException("Invalid email");
        }
    }
}

public class UserRepository {
    public User save(User user) {
        entityManager.persist(user);
        return user;
    }
}

public class WelcomeEmailSender {
    public void sendWelcome(User user) {
        String subject = "Welcome!";
        String body = "Hello " + user.getName();
        emailClient.send(user.getEmail(), subject, body);
    }
}

public class UserAuditLogger {
    public void logCreation(User user) {
        auditLog.log("User created: " + user.getEmail());
    }
}

public class UserService {
    private final UserValidator validator;
    private final UserRepository repository;
    private final WelcomeEmailSender emailSender;
    private final UserAuditLogger auditLogger;

    public User createUser(String name, String email) {
        validator.validate(name, email);
        User user = repository.save(new User(name, email));
        emailSender.sendWelcome(user);
        auditLogger.logCreation(user);
        return user;
    }
}
```

### 如何检测 SRP 违规

- 类有很多来自不同领域的 `import` 语句
- 类名包含"And"或"Manager"或"Handler"（通常）
- 方法操作不相关的数据
- 一个区域的更改需要触及不相关的方法
- 难以简洁地命名类

### 快速检查问题

1. 你能用一句话描述类的目的而不用"和"吗？
2. 不同的利益相关者会请求更改此类吗？
3. 是否有不使用大多数类字段的方法？

---

## O - 开闭原则（OCP）

> "软件实体应该对扩展开放，对修改关闭。"

### 违规

```java
// ❌ 不好：必须修改类才能添加新折扣类型
public class DiscountCalculator {

    public double calculate(Order order, String discountType) {
        if (discountType.equals("PERCENTAGE")) {
            return order.getTotal() * 0.1;
        } else if (discountType.equals("FIXED")) {
            return 50.0;
        } else if (discountType.equals("LOYALTY")) {
            return order.getTotal() * order.getCustomer().getLoyaltyRate();
        }
        // 每个新折扣类型 = 修改此类
        return 0;
    }
}
```

### 重构后

```java
// ✅ 好：添加新折扣而不修改现有代码

public interface DiscountStrategy {
    double calculate(Order order);
    boolean supports(String discountType);
}

public class PercentageDiscount implements DiscountStrategy {
    @Override
    public double calculate(Order order) {
        return order.getTotal() * 0.1;
    }

    @Override
    public boolean supports(String discountType) {
        return "PERCENTAGE".equals(discountType);
    }
}

public class FixedDiscount implements DiscountStrategy {
    @Override
    public double calculate(Order order) {
        return 50.0;
    }

    @Override
    public boolean supports(String discountType) {
        return "FIXED".equals(discountType);
    }
}

public class LoyaltyDiscount implements DiscountStrategy {
    @Override
    public double calculate(Order order) {
        return order.getTotal() * order.getCustomer().getLoyaltyRate();
    }

    @Override
    public boolean supports(String discountType) {
        return "LOYALTY".equals(discountType);
    }
}

// 新折扣？只需添加新类，无需修改
public class SeasonalDiscount implements DiscountStrategy {
    @Override
    public double calculate(Order order) {
        return order.getTotal() * 0.2;
    }

    @Override
    public boolean supports(String discountType) {
        return "SEASONAL".equals(discountType);
    }
}

public class DiscountCalculator {
    private final List<DiscountStrategy> strategies;

    public DiscountCalculator(List<DiscountStrategy> strategies) {
        this.strategies = strategies;
    }

    public double calculate(Order order, String discountType) {
        return strategies.stream()
            .filter(s -> s.supports(discountType))
            .findFirst()
            .map(s -> s.calculate(order))
            .orElse(0.0);
    }
}
```

### 如何检测 OCP 违规

- 基于类型/状态的 `if/else` 或 `switch` 随时间增长
- 基于枚举的分发，频繁添加新值
- 更改需要修改核心类

### 常见 OCP 模式

| 模式 | 使用场景 |
|---------|--------------|
| 策略 | 同一操作的多个算法 |
| 模板方法 | 相同结构，不同步骤 |
| 装饰器 | 动态添加行为 |
| 工厂 | 创建对象而不指定类 |

---

## L - 里氏替换原则（LSP）

> "子类型必须可替换其基类型。"

### 违规

```java
// ❌ 不好：Square 违反 Rectangle 契约
public class Rectangle {
    protected int width;
    protected int height;

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getArea() {
        return width * height;
    }
}

public class Square extends Rectangle {
    @Override
    public void setWidth(int width) {
        this.width = width;
        this.height = width;  // 违反预期行为！
    }

    @Override
    public void setHeight(int height) {
        this.width = height;  // 违反预期行为！
        this.height = height;
    }
}

// 此测试对 Square 失败！
void testRectangle(Rectangle r) {
    r.setWidth(5);
    r.setHeight(4);
    assert r.getArea() == 20;  // Square 返回 16！
}
```

### 重构后

```java
// ✅ 好：分离抽象

public interface Shape {
    int getArea();
}

public class Rectangle implements Shape {
    private final int width;
    private final int height;

    public Rectangle(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public int getArea() {
        return width * height;
    }
}

public class Square implements Shape {
    private final int side;

    public Square(int side) {
        this.side = side;
    }

    @Override
    public int getArea() {
        return side * side;
    }
}
```

### LSP 规则

| 规则 | 含义 |
|------|----------|
| 前置条件 | 子类不能加强（要求更多） |
| 后置条件 | 子类不能削弱（承诺更少） |
| 不变量 | 子类必须维护父类的不变量 |
| 历史 | 子类不能意外修改继承的状态 |

### 如何检测 LSP 违规

- 子类抛出父类不抛出的异常
- 子类在父类返回对象的地方返回 null
- 子类意外忽略或覆盖父类行为
- 调用方法前的 `instanceof` 检查
- 接口方法的空实现或抛出实现

### 快速检查

```java
// 如果你看到这个，LSP 可能被违反
if (bird instanceof Penguin) {
    // 不要调用 fly()
} else {
    bird.fly();
}
```

---

## I - 接口隔离原则（ISP）

> "客户端不应被迫依赖它们不使用的接口。"

### 违规

```java
// ❌ 不好：臃肿接口强制不必要的实现
public interface Worker {
    void work();
    void eat();
    void sleep();
    void attendMeeting();
    void writeReport();
}

// 机器人不能吃或睡！
public class Robot implements Worker {
    @Override public void work() { /* OK */ }
    @Override public void eat() { /* 不能吃！ */ }
    @Override public void sleep() { /* 不能睡！ */ }
    @Override public void attendMeeting() { /* OK */ }
    @Override public void writeReport() { /* 也许 */ }
}

// 实习生不参加会议或写报告
public class Intern implements Worker {
    @Override public void work() { /* OK */ }
    @Override public void eat() { /* OK */ }
    @Override public void sleep() { /* OK */ }
    @Override public void attendMeeting() { /* 不允许！ */ }
    @Override public void writeReport() { /* 不期望！ */ }
}
```

### 重构后

```java
// ✅ 好：隔离的接口

public interface Workable {
    void work();
}

public interface Feedable {
    void eat();
    void sleep();
}

public interface Manageable {
    void attendMeeting();
    void writeReport();
}

// 组合你需要的
public class Employee implements Workable, Feedable, Manageable {
    @Override public void work() { /* ... */ }
    @Override public void eat() { /* ... */ }
    @Override public void sleep() { /* ... */ }
    @Override public void attendMeeting() { /* ... */ }
    @Override public void writeReport() { /* ... */ }
}

public class Robot implements Workable {
    @Override public void work() { /* ... */ }
    // 没有不必要的方法！
}

public class Intern implements Workable, Feedable {
    @Override public void work() { /* ... */ }
    @Override public void eat() { /* ... */ }
    @Override public void sleep() { /* ... */ }
    // 没有会议/报告方法！
}
```

### 如何检测 ISP 违规

- 实现有空方法或 `throw new UnsupportedOperationException()`
- 接口有 10+ 个方法
- 不同客户端使用完全不同的方法子集
- 接口更改影响不相关的实现

### Java 标准库违规

```java
// java.util.List 有很多方法 - 但对于集合这是可以接受的
// 但是，小心你自己的接口！

// ❌ 此接口对大多数用例来说太臃肿
public interface Repository<T> {
    T findById(Long id);
    List<T> findAll();
    T save(T entity);
    void delete(T entity);
    void deleteById(Long id);
    List<T> findByExample(T example);
    Page<T> findAll(Pageable pageable);
    List<T> findAllById(Iterable<Long> ids);
    long count();
    boolean existsById(Long id);
    // ... 20 个更多方法
}

// ✅ 更好：按用例拆分
public interface ReadRepository<T> {
    Optional<T> findById(Long id);
    List<T> findAll();
}

public interface WriteRepository<T> {
    T save(T entity);
    void delete(T entity);
}
```

---

## D - 依赖倒置原则（DIP）

> "高层模块不应依赖低层模块。两者都应依赖抽象。"

### 违规

```java
// ❌ 不好：高层直接依赖低层
public class OrderService {
    private MySqlOrderRepository repository;  // 具体类！
    private SmtpEmailSender emailSender;      // 具体类！

    public OrderService() {
        this.repository = new MySqlOrderRepository();  // 硬依赖
        this.emailSender = new SmtpEmailSender();      // 硬依赖
    }

    public void createOrder(Order order) {
        repository.save(order);
        emailSender.send(order.getCustomerEmail(), "Order confirmed");
    }
}
```

**问题：**
- 没有真实 MySQL 数据库无法测试
- 无法切换邮件提供商
- OrderService 知道 MySQL、SMTP 细节

### 重构后

```java
// ✅ 好：依赖抽象

// 抽象（接口）
public interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(Long id);
}

public interface NotificationSender {
    void send(String recipient, String message);
}

// 高层模块依赖抽象
public class OrderService {
    private final OrderRepository repository;
    private final NotificationSender notificationSender;

    // 依赖注入
    public OrderService(OrderRepository repository,
                        NotificationSender notificationSender) {
        this.repository = repository;
        this.notificationSender = notificationSender;
    }

    public void createOrder(Order order) {
        repository.save(order);
        notificationSender.send(order.getCustomerEmail(), "Order confirmed");
    }
}

// 低层模块实现抽象
public class MySqlOrderRepository implements OrderRepository {
    @Override
    public void save(Order order) { /* MySQL 特定 */ }

    @Override
    public Optional<Order> findById(Long id) { /* MySQL 特定 */ }
}

public class SmtpEmailSender implements NotificationSender {
    @Override
    public void send(String recipient, String message) { /* SMTP 特定 */ }
}

// 易于用模拟测试！
public class InMemoryOrderRepository implements OrderRepository {
    private Map<Long, Order> orders = new HashMap<>();

    @Override
    public void save(Order order) {
        orders.put(order.getId(), order);
    }

    @Override
    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(orders.get(id));
    }
}
```

### DIP 与 Spring

```java
// Spring 自动处理依赖注入

@Service
public class OrderService {
    private final OrderRepository repository;
    private final NotificationSender notificationSender;

    // 构造函数注入（推荐）
    public OrderService(OrderRepository repository,
                        NotificationSender notificationSender) {
        this.repository = repository;
        this.notificationSender = notificationSender;
    }
}

@Repository
public class JpaOrderRepository implements OrderRepository {
    // Spring 提供实现
}

@Component
@Profile("production")
public class SmtpEmailSender implements NotificationSender { }

@Component
@Profile("test")
public class MockEmailSender implements NotificationSender { }
```

### 如何检测 DIP 违规

- 业务逻辑中的 `new ConcreteClass()`
- import 语句包含实现包（例如 `com.mysql`、`org.apache.http`）
- 无法轻松切换实现
- 测试需要真实基础设施（数据库、网络）

---

## SOLID 审查清单

审查代码时，检查：

| 原则 | 问题 |
|-----------|-------------|
| **SRP** | 此类是否有多个改变的理由？ |
| **OCP** | 添加新类型/功能是否需要修改此类？ |
| **LSP** | 子类能否在期望父类的地方使用？ |
| **ISP** | 是否有空的或抛出异常的方法实现？ |
| **DIP** | 高层代码是否依赖具体实现？ |

---

## 常见重构模式

| 违规 | 重构 |
|-----------|-----------------|
| SRP - 上帝类 | 提取类、移动方法 |
| OCP - 类型切换 | 策略模式、工厂 |
| LSP - 破坏的继承 | 组合优于继承、提取接口 |
| ISP - 臃肿接口 | 拆分接口、角色接口 |
| DIP - 硬依赖 | 依赖注入、抽象工厂 |

---

## 相关技能

- `design-patterns` - 实现模式（工厂、策略、观察者等）
- `clean-code` - 代码级原则（DRY、KISS、命名）
- `java-code-review` - 综合审查清单
