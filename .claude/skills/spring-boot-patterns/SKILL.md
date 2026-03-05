---
name: spring-boot-patterns
description: Spring Boot 最佳实践和模式。在创建控制器、服务、Mapper 时使用，或当用户询问 Spring Boot 架构、REST API、异常处理或 MyBatis 模式时使用。
---

# Spring Boot 模式技能

Spring Boot 应用的最佳实践和模式。

## 何时使用
- 用户说"创建控制器" / "添加服务" / "Spring Boot 帮助"
- 审查 Spring Boot 代码
- 设置新的 Spring Boot 项目结构

## 项目结构

```
src/main/java/com/example/myapp/
├── MyAppApplication.java          # @SpringBootApplication
├── config/                        # 配置类
│   ├── SecurityConfig.java
│   └── WebConfig.java
├── controller/                    # REST 控制器
│   └── UserController.java
├── service/                       # 业务逻辑
│   ├── UserService.java
│   └── impl/
│       └── UserServiceImpl.java
├── mapper/                        # MyBatis Mapper 接口
│   └── UserMapper.java
├── entity/                        # 实体类
│   └── User.java
├── dto/                           # 数据传输对象
│   ├── request/
│   │   └── CreateUserRequest.java
│   └── response/
│       └── UserResponse.java
├── exception/                     # 自定义异常
│   ├── ResourceNotFoundException.java
│   └── GlobalExceptionHandler.java
└── util/                          # 工具类
    └── DateUtils.java

src/main/resources/
├── mapper/                        # MyBatis XML 映射文件
│   └── UserMapper.xml
└── application.yml
```

---

## 控制器模式

### REST 控制器模板
```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor  // Lombok 构造器注入
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAll() {
        return ResponseEntity.ok(userService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(
            @Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.getId())
            .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

### 控制器最佳实践

| 实践 | 示例 |
|------|------|
| 版本化 API | `/api/v1/users` |
| 复数名词 | `/users` 而不是 `/user` |
| HTTP 方法 | GET=读取, POST=创建, PUT=更新, DELETE=删除 |
| 状态码 | 200=OK, 201=Created, 204=NoContent, 404=NotFound |
| 验证 | 请求体上使用 `@Valid` |

### ❌ 反模式
```java
// ❌ 控制器中包含业务逻辑
@PostMapping
public User create(@RequestBody User user) {
    user.setCreatedAt(LocalDateTime.now());  // 逻辑应该在服务层
    return userMapper.insert(user);           // 直接访问 Mapper
}

// ❌ 直接返回实体（暴露内部结构）
@GetMapping("/{id}")
public User getById(@PathVariable Long id) {
    return userMapper.selectById(id);
}
```

---

## 服务模式

### 服务接口 + 实现
```java
// 接口
public interface UserService {
    List<UserResponse> findAll();
    UserResponse findById(Long id);
    UserResponse create(CreateUserRequest request);
    UserResponse update(Long id, UpdateUserRequest request);
    void delete(Long id);
}

// 实现
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // 默认只读
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserDtoMapper userDtoMapper;

    @Override
    public List<UserResponse> findAll() {
        return userMapper.selectAll().stream()
            .map(userDtoMapper::toResponse)
            .toList();
    }

    @Override
    public UserResponse findById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new ResourceNotFoundException("User", id);
        }
        return userDtoMapper.toResponse(user);
    }

    @Override
    @Transactional  // 写事务
    public UserResponse create(CreateUserRequest request) {
        User user = userDtoMapper.toEntity(request);
        userMapper.insert(user);
        return userDtoMapper.toResponse(user);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (userMapper.selectById(id) == null) {
            throw new ResourceNotFoundException("User", id);
        }
        userMapper.deleteById(id);
    }
}
```

### 服务最佳实践

- 接口 + 实现以提高可测试性
- 类级别使用 `@Transactional(readOnly = true)`
- 写方法使用 `@Transactional`
- 抛出领域异常，而不是通用异常
- 使用映射器（MapStruct）进行实体 ↔ DTO 转换
- Mapper 返回 null 时检查并抛出异常

---

## Mapper 模式（MyBatis）

### MyBatis Mapper 接口
```java
@Mapper
public interface UserMapper {

    // 查询单个
    User selectById(@Param("id") Long id);

    // 查询列表
    List<User> selectAll();

    // 条件查询
    List<User> selectByCondition(@Param("name") String name,
                                  @Param("email") String email);

    // 插入
    int insert(User user);

    // 更新
    int update(User user);

    // 删除
    int deleteById(@Param("id") Long id);

    // 存在性检查
    int countById(@Param("id") Long id);

    // 批量操作
    int batchInsert(@Param("list") List<User> users);
}
```

### 对应的 XML 映射
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
    "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.myapp.mapper.UserMapper">

    <resultMap id="BaseResultMap" type="com.example.myapp.entity.User">
        <id column="id" property="id"/>
        <result column="user_name" property="userName"/>
        <result column="email" property="email"/>
        <result column="created_at" property="createdAt"/>
    </resultMap>

    <sql id="Base_Column_List">
        id, user_name, email, created_at
    </sql>

    <select id="selectById" resultMap="BaseResultMap">
        SELECT <include refid="Base_Column_List"/>
        FROM user
        WHERE id = #{id}
    </select>

    <select id="selectAll" resultMap="BaseResultMap">
        SELECT <include refid="Base_Column_List"/>
        FROM user
        ORDER BY created_at DESC
    </select>

    <insert id="insert" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO user (user_name, email, created_at)
        VALUES (#{userName}, #{email}, #{createdAt})
    </insert>

    <update id="update">
        UPDATE user
        SET user_name = #{userName},
            email = #{email}
        WHERE id = #{id}
    </update>

    <delete id="deleteById">
        DELETE FROM user WHERE id = #{id}
    </delete>

</mapper>
```

### Mapper 最佳实践

- 使用 `@Mapper` 注解标记接口
- 多参数使用 `@Param` 注解
- 使用 `resultMap` 处理复杂映射
- 开启驼峰命名转换：`mybatis.configuration.map-underscore-to-camel-case=true`
- 使用 `<sql>` 标签复用 SQL 片段
- 插入时使用 `useGeneratedKeys` 获取自增主键
- 避免使用 `${}` 防止 SQL 注入，使用 `#{}`

---

## DTO 模式

### 请求/响应 DTO
```java
// 带验证的请求 DTO
public record CreateUserRequest(
    @NotBlank(message = "名称不能为空")
    @Size(min = 2, max = 100)
    String name,

    @NotBlank
    @Email(message = "邮箱格式无效")
    String email,

    @NotNull
    @Min(18)
    Integer age
) {}

// 响应 DTO
public record UserResponse(
    Long id,
    String name,
    String email,
    LocalDateTime createdAt
) {}
```

### MapStruct 映射器（DTO 转换）
```java
@Mapper(componentModel = "spring")
public interface UserDtoMapper {

    UserResponse toResponse(User entity);

    List<UserResponse> toResponseList(List<User> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    User toEntity(CreateUserRequest request);
}
```

---

## 异常处理

### 自定义异常
```java
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Long id) {
        super(String.format("%s 未找到，id: %d", resource, id));
    }
}

public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }
}
```

### 全局异常处理器
```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        log.warn("资源未找到: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .toList();
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("VALIDATION_ERROR", errors.toString()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("意外错误", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("INTERNAL_ERROR", "发生意外错误"));
    }
}

public record ErrorResponse(String code, String message) {}
```

---

## 配置模式

### 应用配置
```yaml
# application.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mydb
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.example.myapp.entity
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl

app:
  jwt:
    secret: ${JWT_SECRET}
    expiration: 86400000
```

### 配置属性类
```java
@Configuration
@ConfigurationProperties(prefix = "app.jwt")
@Validated
public class JwtProperties {

    @NotBlank
    private String secret;

    @Min(60000)
    private long expiration;

    // getters and setters
}
```

### 特定环境配置
```
src/main/resources/
├── application.yml           # 通用配置
├── application-dev.yml       # 开发环境
├── application-test.yml      # 测试环境
└── application-prod.yml      # 生产环境
```

---

## 常用注解快速参考

| 注解 | 用途 |
|------|------|
| `@RestController` | REST 控制器（组合 @Controller + @ResponseBody） |
| `@Service` | 业务逻辑组件 |
| `@Mapper` | MyBatis Mapper 接口 |
| `@Configuration` | 配置类 |
| `@RequiredArgsConstructor` | Lombok：构造器注入 |
| `@Transactional` | 事务管理 |
| `@Valid` | 触发验证 |
| `@ConfigurationProperties` | 将属性绑定到类 |
| `@Profile("dev")` | 特定环境的 Bean |
| `@Scheduled` | 定时任务 |

---

## 测试模式

### 控制器测试（MockMvc）
```java
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    void shouldReturnUser() throws Exception {
        when(userService.findById(1L))
            .thenReturn(new UserResponse(1L, "John", "john@example.com", null));

        mockMvc.perform(get("/api/v1/users/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("John"));
    }
}
```

### 服务测试
```java
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserDtoMapper userDtoMapper;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void shouldThrowWhenUserNotFound() {
        when(userMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> userService.findById(1L))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
```

### 集成测试
```java
@SpringBootTest
@AutoConfigureMockMvc
@MybatisTest
class UserIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Test
    void shouldCreateUser() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "John", "email": "john@example.com", "age": 25}
                    """))
            .andExpect(status().isCreated());

        // 验证数据库
        User user = userMapper.selectByEmail("john@example.com");
        assertThat(user).isNotNull();
        assertThat(user.getName()).isEqualTo("John");
    }
}
```

---

## 快速参考卡

| 层次 | 职责 | 注解 |
|------|------|------|
| 控制器 | HTTP 处理、验证 | `@RestController`, `@Valid` |
| 服务 | 业务逻辑、事务 | `@Service`, `@Transactional` |
| Mapper | 数据访问（MyBatis） | `@Mapper` |
| DTO | 数据传输 | 带验证注解的 Record |
| 配置 | 配置管理 | `@Configuration`, `@ConfigurationProperties` |
| 异常 | 错误处理 | `@RestControllerAdvice` |
