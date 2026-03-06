# Spring AI Alibaba 集成文档

## 概述

使用 Spring AI Alibaba 框架实现 Text-to-SQL 功能，支持多种 AI 模型。

## 支持的模型

Spring AI Alibaba 支持以下模型：

1. **通义千问（DashScope）** - 阿里云
   - qwen-turbo
   - qwen-plus
   - qwen-max

2. **智谱 AI（ZhiPu）**
   - glm-4
   - glm-4-plus

3. **OpenAI**
   - gpt-4
   - gpt-3.5-turbo

4. **Claude（Anthropic）**
   - claude-3-5-sonnet
   - claude-3-opus

## 配置方式

### 方式 1: 使用通义千问（推荐，国内访问快）

**1. 获取 API Key**
- 访问：https://dashscope.console.aliyun.com/
- 注册并创建 API Key

**2. 配置 application.properties**
```properties
spring.ai.alibaba.api-key=your-dashscope-api-key
spring.ai.alibaba.chat.options.model=qwen-plus
spring.ai.alibaba.chat.options.temperature=0.0
spring.ai.alibaba.chat.options.max-tokens=4096
```

**3. 或使用环境变量**
```bash
export AI_API_KEY=your-dashscope-api-key
```

### 方式 2: 使用 Claude

**1. 获取 API Key**
- 访问：https://console.anthropic.com/
- 创建 API Key

**2. 配置 application.properties**
```properties
spring.ai.alibaba.chat.client=anthropic
spring.ai.alibaba.anthropic.api-key=your-claude-api-key
spring.ai.alibaba.anthropic.chat.options.model=claude-3-5-sonnet-20241022
spring.ai.alibaba.anthropic.chat.options.temperature=0.0
spring.ai.alibaba.anthropic.chat.options.max-tokens=4096
```

### 方式 3: 使用智谱 AI

**1. 获取 API Key**
- 访问：https://open.bigmodel.cn/
- 注册并创建 API Key

**2. 配置 application.properties**
```properties
spring.ai.alibaba.chat.client=zhipu
spring.ai.alibaba.zhipu.api-key=your-zhipu-api-key
spring.ai.alibaba.zhipu.chat.options.model=glm-4
```

## 依赖配置

**pom.xml**:
```xml
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter</artifactId>
    <version>1.0.0-M3.2</version>
</dependency>

<repositories>
    <repository>
        <id>spring-milestones</id>
        <name>Spring Milestones</name>
        <url>https://repo.spring.io/milestone</url>
    </repository>
</repositories>
```

## 核心代码

### AIService.java
```java
@Service
@RequiredArgsConstructor
public class AIService {
    private final ChatClient.Builder chatClientBuilder;

    public String generateSQL(String systemPrompt, String userPrompt) {
        Prompt prompt = new Prompt(List.of(
            new SystemMessage(systemPrompt),
            new UserMessage(userPrompt)
        ));

        ChatClient chatClient = chatClientBuilder.build();
        return chatClient.prompt(prompt).call().content();
    }
}
```

## 使用示例

### 1. 启动应用

```bash
# 设置 API Key
export AI_API_KEY=your-api-key-here

# 启动应用
./mvnw spring-boot:run
```

### 2. 发起查询

```bash
curl -X POST http://localhost:8082/api/query \
  -H "Content-Type: application/json" \
  -d '{
    "datasourceId": 1,
    "question": "查询所有数据源的数量"
  }'
```

### 3. 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": true,
    "sql": "SELECT COUNT(*) as count FROM `datasource`;",
    "explanation": "这个查询统计了数据源表中的总记录数",
    "data": [
      {"count": 1}
    ],
    "rowCount": 1,
    "executionTime": 1234
  }
}
```

## 优势

### 相比直接调用 API

1. **统一接口**: 切换模型只需修改配置，无需改代码
2. **自动配置**: Spring Boot 自动装配，减少样板代码
3. **更好的集成**: 与 Spring 生态无缝集成
4. **错误处理**: 统一的异常处理机制
5. **可观测性**: 自动集成 Spring 的监控和日志

### 模型选择建议

| 模型 | 优势 | 适用场景 |
|------|------|----------|
| 通义千问 | 国内访问快、价格便宜 | 生产环境推荐 |
| Claude | SQL 生成准确率高 | 对准确性要求高的场景 |
| 智谱 AI | 中文理解好 | 中文业务场景 |
| GPT-4 | 综合能力强 | 复杂查询场景 |

## 成本对比

| 模型 | 输入价格 | 输出价格 | 备注 |
|------|---------|---------|------|
| qwen-plus | ¥0.004/1K tokens | ¥0.012/1K tokens | 性价比高 |
| qwen-max | ¥0.04/1K tokens | ¥0.12/1K tokens | 能力最强 |
| claude-3.5-sonnet | $3/1M tokens | $15/1M tokens | 准确率高 |
| glm-4 | ¥0.1/1K tokens | ¥0.1/1K tokens | 中等价格 |

## 测试不同模型

### 测试通义千问
```properties
spring.ai.alibaba.api-key=your-key
spring.ai.alibaba.chat.options.model=qwen-plus
```

### 测试 Claude
```properties
spring.ai.alibaba.chat.client=anthropic
spring.ai.alibaba.anthropic.api-key=your-key
spring.ai.alibaba.anthropic.chat.options.model=claude-3-5-sonnet-20241022
```

### 测试智谱 AI
```properties
spring.ai.alibaba.chat.client=zhipu
spring.ai.alibaba.zhipu.api-key=your-key
spring.ai.alibaba.zhipu.chat.options.model=glm-4
```

## 故障排查

### 问题 1: 依赖下载失败
**解决**: 确保添加了 Spring Milestones 仓库

### 问题 2: API Key 无效
**解决**: 检查 API Key 是否正确，是否有配额

### 问题 3: 模型不支持
**解决**: 查看官方文档确认模型名称是否正确

### 问题 4: 网络超时
**解决**:
- 通义千问：检查阿里云服务是否正常
- Claude：可能需要代理访问

## 参考资源

- [Spring AI Alibaba 官方文档](https://github.com/alibaba/spring-ai-alibaba)
- [通义千问 API 文档](https://help.aliyun.com/zh/dashscope/)
- [智谱 AI API 文档](https://open.bigmodel.cn/dev/api)
- [Claude API 文档](https://docs.anthropic.com/)

## 下一步

1. **性能优化**: 添加响应缓存
2. **多模型对比**: 同时调用多个模型，选择最佳结果
3. **流式输出**: 支持 SSE 流式返回结果
4. **成本控制**: 添加 token 使用统计和限流

---

**版本**: v2.0 (Spring AI Alibaba)
**更新日期**: 2026-03-05
