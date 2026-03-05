# 安全审计

**加载**: `view .claude/skills/security-audit/SKILL.md`

---

## 描述

基于 OWASP Top 10 和安全编码实践的 Java 安全检查清单。框架无关的核心内容，包含 Spring、Quarkus 和 Jakarta EE 的特定部分。

---

## 使用场景

- "审查此代码的安全问题"
- "检查 SQL 注入漏洞"
- "这个身份验证安全吗？"
- "发布前的安全审计"
- "OWASP 合规性检查"

---

## 涵盖的主题

| 主题 | 适用于 |
|------|--------|
| **输入验证** | 所有 Java（Bean Validation JSR 380） |
| **SQL 注入** | JPA、Hibernate、JDBC |
| **XSS 防护** | Web 应用 |
| **CSRF 保护** | Spring、Quarkus |
| **身份验证** | 所有框架 |
| **密钥管理** | 所有应用 |
| **安全反序列化** | 所有 Java |
| **依赖安全** | Maven、Gradle |
| **安全头** | Web 应用 |

---

## OWASP Top 10 覆盖

| 风险 | 已覆盖 |
|------|--------|
| A01 访问控制失效 | ✅ |
| A02 加密失败 | ✅ |
| A03 注入 | ✅ |
| A04 不安全设计 | ✅ |
| A05 安全配置错误 | ✅ |
| A06 易受攻击的组件 | ✅ |
| A07 身份验证失败 | ✅ |
| A08 数据完整性失败 | ✅ |
| A09 日志记录失败 | ✅ |
| A10 服务器端请求伪造 | ✅ |

---

## 相关技能

- `java-code-review` - 通用审查
- `maven-dependency-audit` - 依赖扫描
- `logging-patterns` - 安全日志

---

## 资源

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [OWASP Java 安全备忘单](https://cheatsheetseries.owasp.org/cheatsheets/Java_Security_Cheat_Sheet.html)
- [Spring Boot 安全最佳实践（Snyk）](https://snyk.io/blog/spring-boot-security-best-practices/)
- [OWASP 依赖检查](https://owasp.org/www-project-dependency-check/)
