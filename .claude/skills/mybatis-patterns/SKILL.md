---
name: mybatis-patterns
description: MyBatis 模式和最佳实践（SQL 映射、动态 SQL、结果映射、性能优化）。当用户遇到 MyBatis 性能问题、SQL 映射问题或询问查询优化时使用。
---

# MyBatis 模式技能

Spring 应用中 MyBatis 的最佳实践和常见陷阱。

## 何时使用
- 用户提到"N+1 问题" / "太多查询"
- SQL 映射配置问题
- 动态 SQL 构建
- 结果映射问题
- 查询性能优化
- 批量操作

---

## 快速参考：常见问题

| 问题 | 症状 | 解决方案 |
|---------|---------|----------|
| N+1 查询 | 许多 SELECT 语句 | 使用 association/collection、嵌套查询 |
| 结果映射错误 | 字段为 null | 配置 resultMap、开启驼峰转换 |
| 慢查询 | 性能问题 | 分页、索引、批量操作 |
| SQL 注入风险 | 安全漏洞 | 使用 #{} 而不是 ${} |
| 缓存失效 | 数据不一致 | 配置二级缓存、刷新策略 |

---

## Mapper 接口与 XML 映射

### 基础配置

```java
// ✅ 好：Mapper 接口
@Mapper
public interface UserMapper {

    User selectById(@Param("id") Long id);

    List<User> selectAll();

    int insert(User user);

    int update(User user);

    int deleteById(@Param("id") Long id);
}
```

```xml
<!-- ✅ 好：对应的 XML 映射文件 -->
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
    "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.mapper.UserMapper">

    <resultMap id="BaseResultMap" type="com.example.entity.User">
        <id column="id" property="id"/>
        <result column="user_name" property="userName"/>
        <result column="email" property="email"/>
        <result column="create_time" property="createTime"/>
    </resultMap>

    <sql id="Base_Column_List">
        id, user_name, email, create_time
    </sql>

    <select id="selectById" resultMap="BaseResultMap">
        SELECT <include refid="Base_Column_List"/>
        FROM user
        WHERE id = #{id}
    </select>

</mapper>
```

### 注解方式 vs XML 方式

```java
// ✅ 简单查询：使用注解
@Mapper
public interface UserMapper {

    @Select("SELECT * FROM user WHERE id = #{id}")
    User selectById(@Param("id") Long id);

    @Insert("INSERT INTO user(user_name, email) VALUES(#{userName}, #{email})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);
}

// ✅ 复杂查询：使用 XML
// 动态 SQL、复杂 JOIN、结果映射推荐使用 XML
```

---

## 防止 SQL 注入

### #{} vs ${}

```java
// ✅ 好：使用 #{} 预编译参数
@Select("SELECT * FROM user WHERE name = #{name}")
User selectByName(@Param("name") String name);
// 生成：SELECT * FROM user WHERE name = ?

// ❌ 危险：${} 直接拼接，有 SQL 注入风险
@Select("SELECT * FROM user WHERE name = '${name}'")
User selectByNameUnsafe(@Param("name") String name);
// 如果 name = "admin' OR '1'='1"
// 生成：SELECT * FROM user WHERE name = 'admin' OR '1'='1'

// ✅ ${} 的合法用途：动态表名或列名（需严格验证）
@Select("SELECT * FROM ${tableName} WHERE id = #{id}")
User selectFromTable(@Param("tableName") String tableName, @Param("id") Long id);
```

```xml
<!-- ✅ 好：使用 #{} -->
<select id="selectByName" resultType="User">
    SELECT * FROM user WHERE name = #{name}
</select>

<!-- ❌ 危险：${} 直接拼接 -->
<select id="selectByNameUnsafe" resultType="User">
    SELECT * FROM user WHERE name = '${name}'
</select>

<!-- ✅ ${} 合法用途：ORDER BY 动态列名 -->
<select id="selectAllOrdered" resultType="User">
    SELECT * FROM user
    ORDER BY ${orderColumn} ${orderDirection}
</select>
```

---

## 动态 SQL

### if 标签

```xml
<!-- ✅ 好：条件查询 -->
<select id="selectByCondition" resultMap="BaseResultMap">
    SELECT <include refid="Base_Column_List"/>
    FROM user
    WHERE 1=1
    <if test="userName != null and userName != ''">
        AND user_name LIKE CONCAT('%', #{userName}, '%')
    </if>
    <if test="email != null and email != ''">
        AND email = #{email}
    </if>
    <if test="status != null">
        AND status = #{status}
    </if>
</select>
```

### where 标签

```xml
<!-- ✅ 更好：使用 where 标签自动处理 AND/OR -->
<select id="selectByCondition" resultMap="BaseResultMap">
    SELECT <include refid="Base_Column_List"/>
    FROM user
    <where>
        <if test="userName != null and userName != ''">
            AND user_name LIKE CONCAT('%', #{userName}, '%')
        </if>
        <if test="email != null and email != ''">
            AND email = #{email}
        </if>
        <if test="status != null">
            AND status = #{status}
        </if>
    </where>
</select>
```

### set 标签

```xml
<!-- ✅ 好：动态更新 -->
<update id="updateSelective">
    UPDATE user
    <set>
        <if test="userName != null">user_name = #{userName},</if>
        <if test="email != null">email = #{email},</if>
        <if test="status != null">status = #{status},</if>
        update_time = NOW()
    </set>
    WHERE id = #{id}
</update>
```

### choose/when/otherwise 标签

```xml
<!-- ✅ 好：类似 switch-case -->
<select id="selectByCondition" resultMap="BaseResultMap">
    SELECT <include refid="Base_Column_List"/>
    FROM user
    WHERE 1=1
    <choose>
        <when test="id != null">
            AND id = #{id}
        </when>
        <when test="userName != null">
            AND user_name = #{userName}
        </when>
        <otherwise>
            AND status = 1
        </otherwise>
    </choose>
</select>
```

### foreach 标签

```xml
<!-- ✅ 好：IN 查询 -->
<select id="selectByIds" resultMap="BaseResultMap">
    SELECT <include refid="Base_Column_List"/>
    FROM user
    WHERE id IN
    <foreach collection="ids" item="id" open="(" separator="," close=")">
        #{id}
    </foreach>
</select>

<!-- ✅ 好：批量插入 -->
<insert id="batchInsert">
    INSERT INTO user (user_name, email, create_time)
    VALUES
    <foreach collection="list" item="user" separator=",">
        (#{user.userName}, #{user.email}, #{user.createTime})
    </foreach>
</insert>
```

---

## 结果映射

### 自动映射 vs 手动映射

```xml
<!-- ✅ 简单映射：使用 resultType -->
<select id="selectById" resultType="com.example.entity.User">
    SELECT id, user_name as userName, email
    FROM user
    WHERE id = #{id}
</select>

<!-- ✅ 复杂映射：使用 resultMap -->
<resultMap id="UserResultMap" type="com.example.entity.User">
    <id column="id" property="id"/>
    <result column="user_name" property="userName"/>
    <result column="email" property="email"/>
    <result column="create_time" property="createTime"/>
</resultMap>

<select id="selectById" resultMap="UserResultMap">
    SELECT id, user_name, email, create_time
    FROM user
    WHERE id = #{id}
</select>
```

### 驼峰命名转换

```properties
# application.properties
# ✅ 开启驼峰转换：user_name -> userName
mybatis.configuration.map-underscore-to-camel-case=true
```

```xml
<!-- 开启驼峰转换后，无需 AS 别名 -->
<select id="selectById" resultType="User">
    SELECT id, user_name, email, create_time
    FROM user
    WHERE id = #{id}
</select>
```

### 一对一关联（association）

```java
// 实体类
public class Order {
    private Long id;
    private String orderNo;
    private User user;  // 一对一关联
}
```

```xml
<!-- ✅ 好：嵌套结果映射 -->
<resultMap id="OrderWithUserMap" type="Order">
    <id column="id" property="id"/>
    <result column="order_no" property="orderNo"/>
    <association property="user" javaType="User">
        <id column="user_id" property="id"/>
        <result column="user_name" property="userName"/>
        <result column="email" property="email"/>
    </association>
</resultMap>

<select id="selectOrderWithUser" resultMap="OrderWithUserMap">
    SELECT
        o.id, o.order_no,
        u.id as user_id, u.user_name, u.email
    FROM `order` o
    LEFT JOIN user u ON o.user_id = u.id
    WHERE o.id = #{id}
</select>

<!-- ❌ 避免：嵌套查询（N+1 问题） -->
<resultMap id="OrderWithUserMap" type="Order">
    <id column="id" property="id"/>
    <result column="order_no" property="orderNo"/>
    <association property="user" column="user_id" select="selectUserById"/>
</resultMap>

<select id="selectOrderWithUser" resultMap="OrderWithUserMap">
    SELECT id, order_no, user_id FROM `order` WHERE id = #{id}
</select>

<select id="selectUserById" resultType="User">
    SELECT * FROM user WHERE id = #{id}
</select>
<!-- 这会导致 N+1 问题：查询 N 个订单会执行 N+1 次 SQL -->
```

### 一对多关联（collection）

```java
// 实体类
public class User {
    private Long id;
    private String userName;
    private List<Order> orders;  // 一对多关联
}
```

```xml
<!-- ✅ 好：嵌套结果映射 -->
<resultMap id="UserWithOrdersMap" type="User">
    <id column="id" property="id"/>
    <result column="user_name" property="userName"/>
    <collection property="orders" ofType="Order">
        <id column="order_id" property="id"/>
        <result column="order_no" property="orderNo"/>
        <result column="total_amount" property="totalAmount"/>
    </collection>
</resultMap>

<select id="selectUserWithOrders" resultMap="UserWithOrdersMap">
    SELECT
        u.id, u.user_name,
        o.id as order_id, o.order_no, o.total_amount
    FROM user u
    LEFT JOIN `order` o ON u.id = o.user_id
    WHERE u.id = #{id}
</select>
```

---

## N+1 问题解决

### 问题示例

```java
// ❌ 不好：N+1 查询
List<Order> orders = orderMapper.selectAll();  // 1 个查询
for (Order order : orders) {
    User user = userMapper.selectById(order.getUserId());  // N 个查询
    order.setUser(user);
}
// 结果：1 + N 个查询
```

### 解决方案 1：JOIN 查询

```xml
<!-- ✅ 好：使用 JOIN 一次查询 -->
<resultMap id="OrderWithUserMap" type="Order">
    <id column="id" property="id"/>
    <result column="order_no" property="orderNo"/>
    <association property="user" javaType="User">
        <id column="user_id" property="id"/>
        <result column="user_name" property="userName"/>
    </association>
</resultMap>

<select id="selectAllWithUser" resultMap="OrderWithUserMap">
    SELECT
        o.id, o.order_no,
        u.id as user_id, u.user_name
    FROM `order` o
    LEFT JOIN user u ON o.user_id = u.id
</select>
```

### 解决方案 2：批量查询

```java
// ✅ 好：批量查询
List<Order> orders = orderMapper.selectAll();
List<Long> userIds = orders.stream()
    .map(Order::getUserId)
    .distinct()
    .collect(Collectors.toList());

// 一次查询获取所有用户
List<User> users = userMapper.selectByIds(userIds);
Map<Long, User> userMap = users.stream()
    .collect(Collectors.toMap(User::getId, u -> u));

// 组装数据
orders.forEach(order -> order.setUser(userMap.get(order.getUserId())));
```

```xml
<select id="selectByIds" resultMap="BaseResultMap">
    SELECT <include refid="Base_Column_List"/>
    FROM user
    WHERE id IN
    <foreach collection="ids" item="id" open="(" separator="," close=")">
        #{id}
    </foreach>
</select>
```

---

## 分页查询

### 使用 PageHelper

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.github.pagehelper</groupId>
    <artifactId>pagehelper-spring-boot-starter</artifactId>
    <version>2.1.0</version>
</dependency>
```

```java
// ✅ 好：使用 PageHelper 分页
@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    public PageInfo<User> listUsers(int pageNum, int pageSize) {
        // 开启分页
        PageHelper.startPage(pageNum, pageSize);

        // 执行查询
        List<User> users = userMapper.selectAll();

        // 封装分页信息
        return new PageInfo<>(users);
    }
}
```

### 手动分页

```xml
<!-- ✅ 手动分页 -->
<select id="selectByPage" resultMap="BaseResultMap">
    SELECT <include refid="Base_Column_List"/>
    FROM user
    WHERE status = 1
    ORDER BY create_time DESC
    LIMIT #{offset}, #{limit}
</select>
```

```java
public interface UserMapper {
    List<User> selectByPage(@Param("offset") int offset, @Param("limit") int limit);
}

// 使用
int pageNum = 1;
int pageSize = 20;
int offset = (pageNum - 1) * pageSize;
List<User> users = userMapper.selectByPage(offset, pageSize);
```

---

## 批量操作

### 批量插入

```xml
<!-- ✅ 好：批量插入 -->
<insert id="batchInsert" parameterType="java.util.List">
    INSERT INTO user (user_name, email, create_time)
    VALUES
    <foreach collection="list" item="user" separator=",">
        (#{user.userName}, #{user.email}, #{user.createTime})
    </foreach>
</insert>
```

### 批量更新

```xml
<!-- ✅ 好：批量更新（MySQL） -->
<update id="batchUpdate">
    <foreach collection="list" item="user" separator=";">
        UPDATE user
        SET user_name = #{user.userName}, email = #{user.email}
        WHERE id = #{user.id}
    </foreach>
</update>
```

```properties
# 需要在连接 URL 中添加
spring.datasource.url=jdbc:mysql://localhost:3306/db?allowMultiQueries=true
```

### 使用 ExecutorType.BATCH

```java
// ✅ 好：使用批处理模式
@Service
public class UserService {

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Transactional
    public void batchInsert(List<User> users) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);

            for (User user : users) {
                mapper.insert(user);
            }

            sqlSession.flushStatements();
        }
    }
}
```

---

## 缓存

### 一级缓存（默认开启）

```java
// 一级缓存：SqlSession 级别
SqlSession sqlSession = sqlSessionFactory.openSession();
UserMapper mapper = sqlSession.getMapper(UserMapper.class);

User user1 = mapper.selectById(1L);  // 查询数据库
User user2 = mapper.selectById(1L);  // 从缓存获取，不查询数据库

sqlSession.close();
```

### 二级缓存（需配置）

```xml
<!-- Mapper XML 中开启二级缓存 -->
<mapper namespace="com.example.mapper.UserMapper">

    <cache
        eviction="LRU"
        flushInterval="60000"
        size="512"
        readOnly="true"/>

    <!-- 查询语句 -->
</mapper>
```

```properties
# application.properties
mybatis.configuration.cache-enabled=true
```

```java
// 实体类需要实现 Serializable
public class User implements Serializable {
    private static final long serialVersionUID = 1L;
    // ...
}
```

### 缓存注意事项

```xml
<!-- ✅ 禁用特定查询的缓存 -->
<select id="selectById" resultMap="BaseResultMap" useCache="false">
    SELECT * FROM user WHERE id = #{id}
</select>

<!-- ✅ 更新后刷新缓存 -->
<update id="update" flushCache="true">
    UPDATE user SET user_name = #{userName} WHERE id = #{id}
</update>
```

---

## 事务管理

### 声明式事务

```java
// ✅ 好：使用 @Transactional
@Service
public class OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Transactional(rollbackFor = Exception.class)
    public void createOrder(Order order, List<OrderItem> items) {
        // 插入订单
        orderMapper.insert(order);

        // 插入订单项
        for (OrderItem item : items) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }

        // 如果抛出异常，整个事务回滚
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long id) {
        return orderMapper.selectById(id);
    }
}
```

### 编程式事务

```java
// ✅ 编程式事务控制
@Service
public class OrderService {

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private OrderMapper orderMapper;

    public void createOrder(Order order) {
        transactionTemplate.execute(status -> {
            try {
                orderMapper.insert(order);
                // 其他操作
                return null;
            } catch (Exception e) {
                status.setRollbackOnly();
                throw e;
            }
        });
    }
}
```

---

## 性能优化

### 1. 只查询需要的字段

```xml
<!-- ❌ 不好：查询所有字段 -->
<select id="selectAll" resultMap="BaseResultMap">
    SELECT * FROM user
</select>

<!-- ✅ 好：只查询需要的字段 -->
<select id="selectUserNames" resultType="String">
    SELECT user_name FROM user WHERE status = 1
</select>
```

### 2. 使用索引

```xml
<!-- ✅ 确保查询条件有索引 -->
<select id="selectByEmail" resultMap="BaseResultMap">
    SELECT <include refid="Base_Column_List"/>
    FROM user
    WHERE email = #{email}  <!-- email 字段需要有索引 -->
</select>
```

### 3. 避免在循环中查询

```java
// ❌ 不好：循环查询
for (Long userId : userIds) {
    User user = userMapper.selectById(userId);  // N 次查询
    // ...
}

// ✅ 好：批量查询
List<User> users = userMapper.selectByIds(userIds);  // 1 次查询
```

### 4. 使用延迟加载

```xml
<!-- ✅ 延迟加载关联对象 -->
<resultMap id="UserWithOrdersMap" type="User">
    <id column="id" property="id"/>
    <result column="user_name" property="userName"/>
    <collection property="orders"
                ofType="Order"
                select="selectOrdersByUserId"
                column="id"
                fetchType="lazy"/>
</resultMap>
```

```properties
# 开启延迟加载
mybatis.configuration.lazy-loading-enabled=true
mybatis.configuration.aggressive-lazy-loading=false
```

---

## 常见错误

### 1. 参数传递错误

```java
// ❌ 不好：多参数未使用 @Param
User selectByNameAndEmail(String name, String email);

// ✅ 好：使用 @Param 注解
User selectByNameAndEmail(@Param("name") String name, @Param("email") String email);
```

### 2. resultType vs resultMap 混淆

```xml
<!-- ❌ 错误：字段名不匹配 -->
<select id="selectById" resultType="User">
    SELECT id, user_name, email FROM user WHERE id = #{id}
</select>
<!-- user_name 无法映射到 userName -->

<!-- ✅ 方案1：使用别名 -->
<select id="selectById" resultType="User">
    SELECT id, user_name as userName, email FROM user WHERE id = #{id}
</select>

<!-- ✅ 方案2：使用 resultMap -->
<select id="selectById" resultMap="BaseResultMap">
    SELECT id, user_name, email FROM user WHERE id = #{id}
</select>

<!-- ✅ 方案3：开启驼峰转换 -->
mybatis.configuration.map-underscore-to-camel-case=true
```

### 3. 忘记提交事务

```java
// ❌ 不好：手动管理 SqlSession 忘记提交
SqlSession sqlSession = sqlSessionFactory.openSession();
UserMapper mapper = sqlSession.getMapper(UserMapper.class);
mapper.insert(user);
sqlSession.close();  // 忘记 commit，数据不会保存

// ✅ 好：记得提交
SqlSession sqlSession = sqlSessionFactory.openSession();
try {
    UserMapper mapper = sqlSession.getMapper(UserMapper.class);
    mapper.insert(user);
    sqlSession.commit();
} finally {
    sqlSession.close();
}

// ✅ 更好：使用 @Transactional
@Transactional
public void insertUser(User user) {
    userMapper.insert(user);
}
```

---

## 性能检查清单

审查 MyBatis 代码时，检查：

- [ ] 无 N+1 查询（使用 JOIN 或批量查询）
- [ ] 使用 #{} 而不是 ${} 防止 SQL 注入
- [ ] 大结果集使用分页
- [ ] 批量操作使用 foreach 或 ExecutorType.BATCH
- [ ] 查询条件字段有索引
- [ ] 只查询需要的字段，避免 SELECT *
- [ ] 复杂查询使用 resultMap
- [ ] 开启驼峰命名转换
- [ ] 适当使用缓存
- [ ] 使用 @Transactional 管理事务

---

## 相关技能

- `spring-boot-patterns` - Spring Boot 控制器/服务模式
- `java-code-review` - 通用代码审查清单
- `clean-code` - 代码质量原则
- `security-audit` - SQL 注入防护

---

## 参考资源

- [MyBatis 官方文档](https://mybatis.org/mybatis-3/zh/index.html)
- [MyBatis-Spring-Boot-Starter](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)
- [PageHelper 文档](https://github.com/pagehelper/Mybatis-PageHelper)
- [Druid 连接池](https://github.com/alibaba/druid/wiki)