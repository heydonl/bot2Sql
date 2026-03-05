---
name: test-quality
description: 使用 AssertJ 断言编写高质量的 JUnit 5 测试。当用户说"添加测试"、"编写测试"、"提高测试覆盖率"，或审查/创建 Java 代码的测试类时使用。
---

# 测试质量技能（JUnit 5 + AssertJ）

使用现代最佳实践为 Java 项目编写高质量、可维护的测试。

## 何时使用
- 编写新的测试类
- 审查/改进现有测试
- 用户要求"添加测试" / "提高测试覆盖率"
- 代码审查提到缺少测试

## 框架偏好

### JUnit 5 (Jupiter)
```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import static org.assertj.core.api.Assertions.*;
```

### 使用 AssertJ 而非标准断言
✅ **使用 AssertJ**：
```java
assertThat(plugin.getState())
    .as("插件初始化后应该处于启动状态")
    .isEqualTo(PluginState.STARTED);

assertThat(plugins)
    .hasSize(3)
    .extracting(Plugin::getId)
    .containsExactly("plugin1", "plugin2", "plugin3");
```

❌ **避免使用 JUnit 断言**：
```java
assertEquals(PluginState.STARTED, plugin.getState()); // 可读性较差
assertTrue(plugins.size() == 3); // 失败信息不够描述性
```

## 测试结构（AAA 模式）

始终使用 Arrange-Act-Assert 模式：

```java
@Test
@DisplayName("应该从有效目录加载插件")
void shouldLoadPluginFromValidDirectory() {
    // Arrange - 设置测试数据和依赖
    Path pluginDir = Paths.get("test-plugins/valid-plugin");
    PluginLoader loader = new DefaultPluginLoader();

    // Act - 执行被测试的行为
    Plugin plugin = loader.load(pluginDir);

    // Assert - 验证结果
    assertThat(plugin)
        .isNotNull()
        .extracting(Plugin::getId, Plugin::getVersion)
        .containsExactly("test-plugin", "1.0.0");
}
```

## 命名约定

### 测试类名称
```java
// 被测试的类：PluginManager
PluginManagerTest           // ✅ 简单、标准
PluginManagerShould         // ✅ BDD 风格（如果团队偏好）
TestPluginManager           // ❌ 避免
```

### 测试方法名称

**选项 1：should_expectedBehavior_when_condition**（描述性）
```java
@Test
void should_throwException_when_pluginDirectoryNotFound() { }

@Test
void should_returnEmptyList_when_noPluginsAvailable() { }

@Test
void should_loadPluginsInDependencyOrder_when_multipleDependencies() { }
```

**选项 2：自然语言配合 @DisplayName**（代码更简洁）
```java
@Test
@DisplayName("应该从目录加载所有插件")
void loadAllPlugins() { }

@Test
@DisplayName("当插件描述符无效时应该抛出异常")
void invalidPluginDescriptor() { }
```

## AssertJ 强大功能

### 集合断言
```java
// 基本集合检查
assertThat(plugins)
    .isNotEmpty()
    .hasSize(2)
    .doesNotContainNull();

// 高级过滤和提取
assertThat(plugins)
    .filteredOn(p -> p.getState() == PluginState.STARTED)
    .extracting(Plugin::getId)
    .containsExactlyInAnyOrder("plugin-a", "plugin-b");

// 所有元素匹配条件
assertThat(plugins)
    .allMatch(p -> p.getVersion() != null, "所有插件都有版本");
```

### 异常断言
```java
// 基本异常检查
assertThatThrownBy(() -> loader.load(invalidPath))
    .isInstanceOf(PluginException.class)
    .hasMessageContaining("无效的插件描述符");

// 详细的异常验证
assertThatThrownBy(() -> manager.startPlugin("missing-plugin"))
    .isInstanceOf(PluginException.class)
    .hasMessageContaining("插件未找到")
    .hasCauseInstanceOf(IllegalArgumentException.class)
    .hasNoCause(); // 或验证原因链

// 使用 assertThatExceptionOfType（更易读）
assertThatExceptionOfType(PluginException.class)
    .isThrownBy(() -> loader.load(invalidPath))
    .withMessageContaining("无效")
    .withMessageMatching("无效 .* 描述符");
```

### 对象断言
```java
// 提取并验证多个属性
assertThat(plugin)
    .isNotNull()
    .extracting("id", "version", "state")
    .containsExactly("my-plugin", "1.0", PluginState.STARTED);

// 使用方法引用（类型安全）
assertThat(plugin)
    .extracting(Plugin::getId, Plugin::getVersion, Plugin::getState)
    .containsExactly("my-plugin", "1.0", PluginState.STARTED);

// 逐字段比较
assertThat(actualPlugin)
    .usingRecursiveComparison()
    .isEqualTo(expectedPlugin);
```

### 软断言（多个检查）
```java
@Test
void shouldHaveValidPluginDescriptor() {
    SoftAssertions softly = new SoftAssertions();

    softly.assertThat(descriptor.getId())
        .as("插件 ID")
        .isNotBlank()
        .matches("[a-z0-9-]+");

    softly.assertThat(descriptor.getVersion())
        .as("插件版本")
        .matches("\\d+\\.\\d+\\.\\d+");

    softly.assertThat(descriptor.getDependencies())
        .as("依赖")
        .isNotNull()
        .doesNotContainNull();

    softly.assertAll(); // 所有断言都会被评估，即使有些失败
}
```

### 字符串断言
```java
assertThat(errorMessage)
    .startsWith("错误:")
    .contains("插件", "失败")
    .doesNotContain("成功")
    .matches("错误: .* 失败")
    .hasLineCount(3);
```

## 测试组织

### 使用嵌套测试提高清晰度
```java
@DisplayName("PluginManager")
class PluginManagerTest {

    private PluginManager manager;

    @BeforeEach
    void setUp() {
        manager = new DefaultPluginManager();
    }

    @Nested
    @DisplayName("启动插件时")
    class WhenStartingPlugins {

        @Test
        @DisplayName("应该按依赖顺序启动所有插件")
        void shouldStartInDependencyOrder() {
            // 测试实现
        }

        @Test
        @DisplayName("应该跳过已禁用的插件")
        void shouldSkipDisabledPlugins() {
            // 测试实现
        }

        @Test
        @DisplayName("检测到循环依赖时应该失败")
        void shouldFailOnCircularDependency() {
            // 测试实现
        }
    }

    @Nested
    @DisplayName("停止插件时")
    class WhenStoppingPlugins {

        @Test
        @DisplayName("应该按反向依赖顺序停止插件")
        void shouldStopInReverseOrder() {
            // 测试实现
        }
    }
}
```

### 参数化测试
```java
@ParameterizedTest
@ValueSource(strings = {"1.0.0", "2.1.3", "10.0.0-SNAPSHOT"})
@DisplayName("应该接受有效的语义化版本")
void shouldAcceptValidVersions(String version) {
    assertThat(VersionParser.parse(version))
        .isNotNull()
        .hasFieldOrPropertyWithValue("valid", true);
}

@ParameterizedTest
@CsvSource({
    "plugin-a, 1.0, STARTED",
    "plugin-b, 2.0, STOPPED",
    "plugin-c, 1.5, DISABLED"
})
@DisplayName("应该加载具有预期状态的插件")
void shouldLoadPluginWithState(String id, String version, PluginState expectedState) {
    Plugin plugin = createPlugin(id, version);

    assertThat(plugin.getState()).isEqualTo(expectedState);
}

@ParameterizedTest
@MethodSource("invalidPluginDescriptors")
@DisplayName("应该拒绝无效的插件描述符")
void shouldRejectInvalidDescriptors(PluginDescriptor descriptor, String expectedError) {
    assertThatThrownBy(() -> validator.validate(descriptor))
        .hasMessageContaining(expectedError);
}

static Stream<Arguments> invalidPluginDescriptors() {
    return Stream.of(
        Arguments.of(descriptorWithoutId(), "缺少插件 ID"),
        Arguments.of(descriptorWithInvalidVersion(), "无效的版本格式"),
        Arguments.of(descriptorWithEmptyId(), "插件 ID 不能为空")
    );
}
```

## 常见模式

### 使用 Mock 测试（Mockito）
```java
@ExtendWith(MockitoExtension.class)
class PluginManagerTest {

    @Mock
    private PluginRepository repository;

    @Mock
    private PluginValidator validator;

    @InjectMocks
    private DefaultPluginManager manager;

    @Test
    @DisplayName("应该从仓库加载插件")
    void shouldLoadPluginsFromRepository() {
        // Given
        List<PluginDescriptor> descriptors = List.of(
            createDescriptor("plugin1"),
            createDescriptor("plugin2")
        );
        when(repository.findAll()).thenReturn(descriptors);

        // When
        List<Plugin> plugins = manager.loadAll();

        // Then
        assertThat(plugins).hasSize(2);
        verify(repository).findAll();
        verify(validator, times(2)).validate(any(PluginDescriptor.class));
    }
}
```

### 使用 @BeforeEach 的测试固件
```java
@BeforeEach
void setUp() throws IOException {
    // 为测试插件创建临时目录
    pluginDir = Files.createTempDirectory("test-plugins");

    // 使用测试配置初始化插件管理器
    PluginConfig config = PluginConfig.builder()
        .pluginDirectory(pluginDir)
        .enableValidation(true)
        .build();

    pluginManager = new DefaultPluginManager(config);
}

@AfterEach
void tearDown() throws IOException {
    // 清理测试资源
    if (pluginManager != null) {
        pluginManager.stopAll();
    }
    if (pluginDir != null) {
        FileUtils.deleteDirectory(pluginDir.toFile());
    }
}
```

### 测试异步操作
```java
@Test
@DisplayName("应该完成异步插件加载")
void shouldCompleteAsyncLoading() {
    CompletableFuture<Plugin> future = manager.loadAsync(pluginPath);

    assertThat(future)
        .succeedsWithin(Duration.ofSeconds(5))
        .satisfies(plugin -> {
            assertThat(plugin.getState()).isEqualTo(PluginState.STARTED);
            assertThat(plugin.getId()).isNotBlank();
        });
}
```

## Token 优化

编写测试时：

### 1. 首先生成测试骨架
```java
// 阶段 1：将测试用例列为注释
// @Test void shouldLoadPlugin() { }
// @Test void shouldThrowExceptionForInvalidPlugin() { }
// @Test void shouldHandleMissingDependencies() { }
```

### 2. 增量实现
- 一次一个测试
- 每次后验证编译
- 运行测试进行验证
- 如需要则重构

### 3. 重用模式
```java
// 将通用设置提取到辅助方法
private Plugin createTestPlugin(String id, String version) {
    return Plugin.builder()
        .id(id)
        .version(version)
        .build();
}
```

## 代码覆盖率指南

- **目标**：核心逻辑 80%+ 行覆盖率
- **关注**：业务逻辑、复杂算法、边界情况
- **跳过**：简单的 getter/setter、POJO、生成的代码
- **测试**：正常路径 + 错误条件 + 边界情况

### 应该测试什么
✅ **高优先级**：
- 公共 API
- 复杂的业务逻辑
- 错误处理
- 边界情况和边界
- 集成点

❌ **低优先级**：
```java
// 简单的 getter/setter
public String getId() { return id; }
public void setId(String id) { this.id = id; }

// 没有逻辑的简单 POJO
public class PluginInfo {
    private String id;
    private String version;
    // ... 只有 getter/setter
}
```

## 反模式

❌ **避免**：
```java
// 1. 通用测试名称
@Test void test1() { }
@Test void testPlugin() { }

// 2. 测试实现细节
assertThat(plugin.internalState.flag).isTrue(); // 耦合到内部

// 3. 带时间戳的脆弱断言
assertThat(message).isEqualTo("错误发生在 2024-01-26 10:30:15");

// 4. 多个不相关的断言
@Test void testEverything() {
    // 50 个不相关的断言
    assertThat(plugin.getId()).isNotNull();
    assertThat(manager.getCount()).isEqualTo(5);
    assertThat(config.isEnabled()).isTrue();
    // ... 混合多个关注点
}

// 5. 忽略异常
@Test void shouldFail() {
    try {
        loader.load(invalidPath);
        fail("应该抛出异常");
    } catch (Exception e) {
        // 吞掉异常细节
    }
}
```

✅ **推荐**：
```java
@Test
@DisplayName("应该拒绝缺少依赖的插件")
void shouldRejectPluginWithMissingDependencies() {
    PluginDescriptor descriptor = PluginDescriptor.builder()
        .id("test-plugin")
        .dependencies(List.of("missing-dep"))
        .build();

    assertThatThrownBy(() -> manager.load(descriptor))
        .isInstanceOf(PluginException.class)
        .hasMessageContaining("缺少依赖: missing-dep");
}
```

## 与覆盖率工具集成

### Maven 配置
```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### 生成测试后，建议：
```bash
# 运行带覆盖率的测试
mvn clean test jacoco:report

# 查看覆盖率报告
open target/site/jacoco/index.html

# 检查覆盖率阈值
mvn verify # 如果低于配置的阈值则失败
```

## 快速参考

```java
// ===== 基本断言 =====
assertThat(value).isEqualTo(expected);
assertThat(value).isNotNull();
assertThat(value).isInstanceOf(String.class);
assertThat(number).isPositive().isGreaterThan(5);

// ===== 集合 =====
assertThat(list).hasSize(3);
assertThat(list).contains(item);
assertThat(list).containsExactly(item1, item2, item3);
assertThat(list).containsExactlyInAnyOrder(item2, item1, item3);
assertThat(list).doesNotContain(item);
assertThat(list).allMatch(predicate);

// ===== 字符串 =====
assertThat(str).isNotBlank();
assertThat(str).startsWith("前缀");
assertThat(str).endsWith("后缀");
assertThat(str).contains("子串");
assertThat(str).matches("regex\\d+");

// ===== 异常 =====
assertThatThrownBy(() -> code())
    .isInstanceOf(PluginException.class)
    .hasMessageContaining("错误");

assertThatNoException().isThrownBy(() -> code());

// ===== 自定义描述 =====
assertThat(userId)
    .as("用户 ID 应该为正数")
    .isPositive();

// ===== 对象比较 =====
assertThat(actual)
    .usingRecursiveComparison()
    .ignoringFields("timestamp", "id")
    .isEqualTo(expected);
```

## 最佳实践总结

1. **使用 AssertJ** 进行所有断言
2. **遵循 AAA 模式**（Arrange-Act-Assert）
3. **描述性名称** 配合 @DisplayName
4. **每个测试一个概念**
5. **测试行为**，而非实现
6. **提取辅助方法** 用于通用设置
7. **使用 @Nested** 进行逻辑分组
8. **参数化** 相似的测试
9. **软断言** 用于多个检查
10. **覆盖率** 关注业务逻辑，而非样板代码

## 参考资料

- [AssertJ 文档](https://assertj.github.io/doc/)
- [JUnit 5 用户指南](https://junit.org/junit5/docs/current/user-guide/)
