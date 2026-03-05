---
name: design-patterns
description: 常见设计模式及 Java 示例（工厂、建造者、策略、观察者、装饰器等）。当用户询问"实现模式"、"使用工厂"、"策略模式"或设计可扩展组件时使用。
---

# 设计模式技能

Java 实用设计模式参考及现代示例。

## 何时使用
- 用户要求实现特定模式
- 设计可扩展/灵活的组件
- 重构僵化的代码结构
- 代码审查建议使用模式

---

## 快速参考：何时使用什么

| 问题 | 模式 |
|---------|----------|
| 复杂对象构造 | **建造者** |
| 创建对象不指定类 | **工厂** |
| 多个算法，运行时切换 | **策略** |
| 不改变类添加行为 | **装饰器** |
| 通知多个对象变化 | **观察者** |
| 确保单一实例 | **单例** |
| 转换不兼容接口 | **适配器** |
| 定义算法骨架 | **模板方法** |

---

## 创建型模式

### 建造者

**使用场景：** 对象有很多参数，部分可选。

```java
// ❌ 伸缩构造函数反模式
public class User {
    public User(String name) { }
    public User(String name, String email) { }
    public User(String name, String email, int age) { }
    public User(String name, String email, int age, String phone) { }
    // ... 构造函数爆炸
}

// ✅ 建造者模式
public class User {
    private final String name;      // 必需
    private final String email;     // 必需
    private final int age;          // 可选
    private final String phone;     // 可选
    private final String address;   // 可选

    private User(Builder builder) {
        this.name = builder.name;
        this.email = builder.email;
        this.age = builder.age;
        this.phone = builder.phone;
        this.address = builder.address;
    }

    public static Builder builder(String name, String email) {
        return new Builder(name, email);
    }

    public static class Builder {
        // 必需
        private final String name;
        private final String email;
        // 可选，带默认值
        private int age = 0;
        private String phone = "";
        private String address = "";

        private Builder(String name, String email) {
            this.name = name;
            this.email = email;
        }

        public Builder age(int age) {
            this.age = age;
            return this;
        }

        public Builder phone(String phone) {
            this.phone = phone;
            return this;
        }

        public Builder address(String address) {
            this.address = address;
            return this;
        }

        public User build() {
            return new User(this);
        }
    }
}

// 使用
User user = User.builder("John", "john@example.com")
    .age(30)
    .phone("+1234567890")
    .build();
```

**使用 Lombok：**
```java
@Builder
@Getter
public class User {
    private final String name;
    private final String email;
    @Builder.Default private int age = 0;
    private String phone;
}
```

---

### 工厂方法

**使用场景：** 需要创建对象而不指定确切的类。

```java
// ✅ 工厂方法模式
public interface Notification {
    void send(String message);
}

public class EmailNotification implements Notification {
    @Override
    public void send(String message) {
        System.out.println("Email: " + message);
    }
}

public class SmsNotification implements Notification {
    @Override
    public void send(String message) {
        System.out.println("SMS: " + message);
    }
}

public class PushNotification implements Notification {
    @Override
    public void send(String message) {
        System.out.println("Push: " + message);
    }
}

// 工厂
public class NotificationFactory {

    public static Notification create(String type) {
        return switch (type.toUpperCase()) {
            case "EMAIL" -> new EmailNotification();
            case "SMS" -> new SmsNotification();
            case "PUSH" -> new PushNotification();
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        };
    }
}

// 使用
Notification notification = NotificationFactory.create("EMAIL");
notification.send("Hello!");
```

**使用 Spring（推荐）：**
```java
public interface NotificationSender {
    void send(String message);
    String getType();
}

@Component
public class EmailSender implements NotificationSender {
    @Override public void send(String message) { /* ... */ }
    @Override public String getType() { return "EMAIL"; }
}

@Component
public class SmsSender implements NotificationSender {
    @Override public void send(String message) { /* ... */ }
    @Override public String getType() { return "SMS"; }
}

@Component
public class NotificationFactory {
    private final Map<String, NotificationSender> senders;

    public NotificationFactory(List<NotificationSender> senderList) {
        this.senders = senderList.stream()
            .collect(Collectors.toMap(
                NotificationSender::getType,
                Function.identity()
            ));
    }

    public NotificationSender getSender(String type) {
        return Optional.ofNullable(senders.get(type))
            .orElseThrow(() -> new IllegalArgumentException("Unknown: " + type));
    }
}
```

---

### 单例

**使用场景：** 需要恰好一个实例（谨慎使用！）。

```java
// ✅ 现代单例（基于枚举，线程安全）
public enum DatabaseConnection {
    INSTANCE;

    private Connection connection;

    DatabaseConnection() {
        // 初始化连接
    }

    public Connection getConnection() {
        return connection;
    }
}

// 使用
Connection conn = DatabaseConnection.INSTANCE.getConnection();
```

**使用 Spring（推荐）：**
```java
@Component  // 默认作用域是单例
public class DatabaseConnection {
    // Spring 管理单一实例
}
```

**警告：** 单例可能有问题：
- 难以测试（全局状态）
- 隐藏依赖
- 考虑使用依赖注入代替

---

## 行为型模式

### 策略

**使用场景：** 同一操作有多个算法，需要运行时切换。

```java
// ✅ 策略模式
public interface PaymentStrategy {
    void pay(BigDecimal amount);
}

public class CreditCardPayment implements PaymentStrategy {
    private final String cardNumber;

    public CreditCardPayment(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    @Override
    public void pay(BigDecimal amount) {
        System.out.println("Paid " + amount + " with card " + cardNumber);
    }
}

public class PayPalPayment implements PaymentStrategy {
    private final String email;

    public PayPalPayment(String email) {
        this.email = email;
    }

    @Override
    public void pay(BigDecimal amount) {
        System.out.println("Paid " + amount + " via PayPal: " + email);
    }
}

public class CryptoPayment implements PaymentStrategy {
    private final String walletAddress;

    public CryptoPayment(String walletAddress) {
        this.walletAddress = walletAddress;
    }

    @Override
    public void pay(BigDecimal amount) {
        System.out.println("Paid " + amount + " to wallet: " + walletAddress);
    }
}

// 上下文
public class ShoppingCart {
    private PaymentStrategy paymentStrategy;

    public void setPaymentStrategy(PaymentStrategy strategy) {
        this.paymentStrategy = strategy;
    }

    public void checkout(BigDecimal total) {
        paymentStrategy.pay(total);
    }
}

// 使用
ShoppingCart cart = new ShoppingCart();
cart.setPaymentStrategy(new CreditCardPayment("4111-1111-1111-1111"));
cart.checkout(new BigDecimal("99.99"));

// 运行时更改策略
cart.setPaymentStrategy(new PayPalPayment("user@example.com"));
cart.checkout(new BigDecimal("49.99"));
```

**使用 Java 8+（函数式）：**
```java
// 策略作为函数式接口
@FunctionalInterface
public interface PaymentStrategy {
    void pay(BigDecimal amount);
}

// 使用 lambda
PaymentStrategy creditCard = amount ->
    System.out.println("Card payment: " + amount);

PaymentStrategy paypal = amount ->
    System.out.println("PayPal payment: " + amount);

cart.setPaymentStrategy(creditCard);
```

---

### 观察者

**使用场景：** 对象需要被通知另一个对象的变化。

```java
// ✅ 观察者模式（现代 Java）
public interface OrderObserver {
    void onOrderPlaced(Order order);
}

public class OrderService {
    private final List<OrderObserver> observers = new ArrayList<>();

    public void addObserver(OrderObserver observer) {
        observers.add(observer);
    }

    public void removeObserver(OrderObserver observer) {
        observers.remove(observer);
    }

    public void placeOrder(Order order) {
        // 处理订单
        saveOrder(order);

        // 通知所有观察者
        observers.forEach(observer -> observer.onOrderPlaced(order));
    }
}

// 观察者
public class InventoryService implements OrderObserver {
    @Override
    public void onOrderPlaced(Order order) {
        // 减少库存
        order.getItems().forEach(item ->
            reduceStock(item.getProductId(), item.getQuantity())
        );
    }
}

public class EmailNotificationService implements OrderObserver {
    @Override
    public void onOrderPlaced(Order order) {
        sendConfirmationEmail(order.getCustomerEmail(), order);
    }
}

public class AnalyticsService implements OrderObserver {
    @Override
    public void onOrderPlaced(Order order) {
        trackOrderEvent(order);
    }
}

// 设置
OrderService orderService = new OrderService();
orderService.addObserver(new InventoryService());
orderService.addObserver(new EmailNotificationService());
orderService.addObserver(new AnalyticsService());
```

**使用 Spring 事件（推荐）：**
```java
// 事件
public record OrderPlacedEvent(Order order) {}

// 发布者
@Service
public class OrderService {
    private final ApplicationEventPublisher eventPublisher;

    public void placeOrder(Order order) {
        saveOrder(order);
        eventPublisher.publishEvent(new OrderPlacedEvent(order));
    }
}

// 监听器（观察者）
@Component
public class InventoryListener {
    @EventListener
    public void handleOrderPlaced(OrderPlacedEvent event) {
        // 减少库存
    }
}

@Component
public class EmailListener {
    @EventListener
    public void handleOrderPlaced(OrderPlacedEvent event) {
        // 发送邮件
    }

    @EventListener
    @Async  // 异步处理
    public void handleOrderPlacedAsync(OrderPlacedEvent event) {
        // 异步发送邮件
    }
}
```

---

### 模板方法

**使用场景：** 定义算法骨架，让子类填充步骤。

```java
// ✅ 模板方法模式
public abstract class DataProcessor {

    // 模板方法 - 定义算法
    public final void process() {
        readData();
        processData();
        writeData();
        if (shouldNotify()) {
            notifyCompletion();
        }
    }

    // 由子类实现的步骤
    protected abstract void readData();
    protected abstract void processData();
    protected abstract void writeData();

    // 钩子 - 可选覆盖
    protected boolean shouldNotify() {
        return true;
    }

    protected void notifyCompletion() {
        System.out.println("Processing completed!");
    }
}

public class CsvDataProcessor extends DataProcessor {
    @Override
    protected void readData() {
        System.out.println("Reading CSV file...");
    }

    @Override
    protected void processData() {
        System.out.println("Processing CSV data...");
    }

    @Override
    protected void writeData() {
        System.out.println("Writing to database...");
    }
}

public class ApiDataProcessor extends DataProcessor {
    @Override
    protected void readData() {
        System.out.println("Fetching from API...");
    }

    @Override
    protected void processData() {
        System.out.println("Transforming API response...");
    }

    @Override
    protected void writeData() {
        System.out.println("Writing to cache...");
    }

    @Override
    protected boolean shouldNotify() {
        return false;  // 覆盖钩子
    }
}

// 使用
DataProcessor csvProcessor = new CsvDataProcessor();
csvProcessor.process();

DataProcessor apiProcessor = new ApiDataProcessor();
apiProcessor.process();
```

---

## 结构型模式

### 装饰器

**使用场景：** 动态添加行为而不修改现有类。

```java
// ✅ 装饰器模式
public interface Coffee {
    String getDescription();
    BigDecimal getCost();
}

public class SimpleCoffee implements Coffee {
    @Override
    public String getDescription() {
        return "Coffee";
    }

    @Override
    public BigDecimal getCost() {
        return new BigDecimal("2.00");
    }
}

// 基础装饰器
public abstract class CoffeeDecorator implements Coffee {
    protected final Coffee coffee;

    public CoffeeDecorator(Coffee coffee) {
        this.coffee = coffee;
    }

    @Override
    public String getDescription() {
        return coffee.getDescription();
    }

    @Override
    public BigDecimal getCost() {
        return coffee.getCost();
    }
}

// 具体装饰器
public class MilkDecorator extends CoffeeDecorator {
    public MilkDecorator(Coffee coffee) {
        super(coffee);
    }

    @Override
    public String getDescription() {
        return coffee.getDescription() + ", Milk";
    }

    @Override
    public BigDecimal getCost() {
        return coffee.getCost().add(new BigDecimal("0.50"));
    }
}

public class SugarDecorator extends CoffeeDecorator {
    public SugarDecorator(Coffee coffee) {
        super(coffee);
    }

    @Override
    public String getDescription() {
        return coffee.getDescription() + ", Sugar";
    }

    @Override
    public BigDecimal getCost() {
        return coffee.getCost().add(new BigDecimal("0.20"));
    }
}

public class WhippedCreamDecorator extends CoffeeDecorator {
    public WhippedCreamDecorator(Coffee coffee) {
        super(coffee);
    }

    @Override
    public String getDescription() {
        return coffee.getDescription() + ", Whipped Cream";
    }

    @Override
    public BigDecimal getCost() {
        return coffee.getCost().add(new BigDecimal("0.70"));
    }
}

// 使用 - 组合装饰器
Coffee coffee = new SimpleCoffee();
coffee = new MilkDecorator(coffee);
coffee = new SugarDecorator(coffee);
coffee = new WhippedCreamDecorator(coffee);

System.out.println(coffee.getDescription());  // Coffee, Milk, Sugar, Whipped Cream
System.out.println(coffee.getCost());         // 3.40
```

**Java I/O 使用装饰器：**
```java
// Java 的经典示例
BufferedReader reader = new BufferedReader(
    new InputStreamReader(
        new FileInputStream("file.txt")
    )
);
```

---

### 适配器

**使用场景：** 使不兼容的接口协同工作。

```java
// ✅ 适配器模式

// 我们代码使用的现有接口
public interface MediaPlayer {
    void play(String filename);
}

// 遗留/第三方接口
public class LegacyAudioPlayer {
    public void playMp3(String filename) {
        System.out.println("Playing MP3: " + filename);
    }
}

public class AdvancedVideoPlayer {
    public void playMp4(String filename) {
        System.out.println("Playing MP4: " + filename);
    }

    public void playAvi(String filename) {
        System.out.println("Playing AVI: " + filename);
    }
}

// 适配器
public class Mp3PlayerAdapter implements MediaPlayer {
    private final LegacyAudioPlayer legacyPlayer = new LegacyAudioPlayer();

    @Override
    public void play(String filename) {
        legacyPlayer.playMp3(filename);
    }
}

public class VideoPlayerAdapter implements MediaPlayer {
    private final AdvancedVideoPlayer videoPlayer = new AdvancedVideoPlayer();

    @Override
    public void play(String filename) {
        if (filename.endsWith(".mp4")) {
            videoPlayer.playMp4(filename);
        } else if (filename.endsWith(".avi")) {
            videoPlayer.playAvi(filename);
        }
    }
}

// 使用
MediaPlayer mp3Player = new Mp3PlayerAdapter();
mp3Player.play("song.mp3");

MediaPlayer videoPlayer = new VideoPlayerAdapter();
videoPlayer.play("movie.mp4");
```

---

## 模式选择指南

| 情况 | 考虑 |
|-----------|-------------|
| 对象创建复杂 | 建造者、工厂 |
| 需要动态添加功能 | 装饰器 |
| 算法的多个实现 | 策略 |
| 对状态变化做出反应 | 观察者 |
| 与遗留代码集成 | 适配器 |
| 通用算法，不同步骤 | 模板方法 |
| 需要单一实例 | 单例（谨慎使用） |

---

## 要避免的反模式

| 反模式 | 问题 | 更好的方法 |
|--------------|---------|-----------------------|
| 滥用单例 | 全局状态，难以测试 | 依赖注入 |
| 到处都是工厂 | 过度工程 | 如果类型已知，简单 `new` |
| 深层装饰器链 | 难以调试 | 保持链短，考虑组合 |
| 观察者有很多事件 | 意大利面式通知 | 事件总线，清晰的事件层次 |

---

## 相关技能

- `solid-principles` - 模式帮助实现的设计原则
- `clean-code` - 代码级最佳实践
- `spring-boot-patterns` - Spring 特定实现
