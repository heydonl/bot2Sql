package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.QueryLog;
import com.tecdo.mac.sql2bot.domain.UserQueryTemplate;
import com.tecdo.mac.sql2bot.dto.QueryRequest;
import com.tecdo.mac.sql2bot.dto.QueryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 用户反馈系统集成测试
 * 验证满意/不满意流程
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserQueryTemplateFeedbackTest {

    @InjectMocks
    private TextToSQLService textToSQLService;

    @Mock private AIService aiService;
    @Mock private SemanticContextService semanticContextService;
    @Mock private QueryExecutorService queryExecutorService;
    @Mock private ConversationService conversationService;
    @Mock private MessageService messageService;
    @Mock private QueryTemplateService queryTemplateService;
    @Mock private TemplateParameterService templateParameterService;
    @Mock private QueryLogService queryLogService;
    @Mock private SchemaVectorStoreService schemaVectorStoreService;
    @Mock private EmbeddingService embeddingService;
    @Mock private ModelService modelService;
    @Mock private RelationshipService relationshipService;
    @Mock private ColumnDefinitionService columnDefinitionService;
    @Mock private DataSourceService dataSourceService;
    @Mock private DatabaseService databaseService;
    @Mock private QueryStepLogService queryStepLogService;
    @Mock private UserQueryTemplateService userQueryTemplateService;
    @Mock private TemplateVectorStoreService templateVectorStoreService;

    @BeforeEach
    void setUp() throws Exception {
        // 注入 @Value 字段（Mockito 不会自动注入 @Value）
        setField("schemaSearchTopK", 10);
        setField("schemaSearchBfsRetryTopK", 20);
        setField("schemaSearchSimilarityThreshold", 0.5);
        setField("templateSearchSimilarityThreshold", 0.6);
        setField("objectMapper", new ObjectMapper());
    }

    private void setField(String name, Object value) throws Exception {
        java.lang.reflect.Field field = TextToSQLService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(textToSQLService, value);
    }

    /**
     * 测试场景1：用户满意 - 全新问答对
     * 期望：创建新的 user_query_template，返回感谢响应
     */
    @Test
    void testUserSatisfied_NewQuestionAnswer() {
        // 准备 query_log 记录（sourceType=bfs，无已存在模板）
        QueryLog mockLog = new QueryLog();
        mockLog.setId(100L);
        mockLog.setUserId(1L);
        mockLog.setQuestion("测试问题：查询广告主消费");
        mockLog.setGeneratedSql("SELECT * FROM advertiser WHERE id = 1");
        mockLog.setDatasourceId(1L);
        mockLog.setSourceType("bfs");
        mockLog.setExecutionSuccess(true);
        mockLog.setSatisfied(null); // 未评价

        when(queryLogService.findById(100L)).thenReturn(mockLog);
        when(userQueryTemplateService.findByQuestionAndSql(anyString(), anyString())).thenReturn(null);

        // 构造满意反馈请求
        QueryRequest request = new QueryRequest();
        request.setUserId(1L);
        request.setSatisfied(true);
        request.setRetryQueryLogId(100L);

        QueryResponse response = textToSQLService.processQuery(request);

        // 验证响应
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getExplanation()).contains("感谢");

        // 验证 satisfied 已更新
        verify(queryLogService).updateSatisfied(100L, true);

        // 验证创建了新的 user_query_template
        verify(userQueryTemplateService).create(
            "测试问题：查询广告主消费",
            "SELECT * FROM advertiser WHERE id = 1",
            1L
        );
    }

    /**
     * 测试场景2：用户满意 - 已存在问答对
     * 期望：更新已有模板评分，不重复创建
     */
    @Test
    void testUserSatisfied_ExistingQuestionAnswer() {
        // 准备已存在的 user_query_template
        UserQueryTemplate existing = new UserQueryTemplate();
        existing.setId(50L);
        existing.setQuestion("已存在问题：查询媒体账户");
        existing.setGeneratedSql("SELECT * FROM media_account WHERE id = 1");
        existing.setDatasourceId(1L);
        existing.setTotalScore(1);
        existing.setRatingCount(1);

        // 准备 query_log 记录
        QueryLog mockLog = new QueryLog();
        mockLog.setId(101L);
        mockLog.setUserId(1L);
        mockLog.setQuestion("已存在问题：查询媒体账户");
        mockLog.setGeneratedSql("SELECT * FROM media_account WHERE id = 1");
        mockLog.setDatasourceId(1L);
        mockLog.setSourceType("user_template");
        mockLog.setSourceTemplateId(50L);
        mockLog.setExecutionSuccess(true);
        mockLog.setSatisfied(null);

        when(queryLogService.findById(101L)).thenReturn(mockLog);
        when(userQueryTemplateService.findByQuestionAndSql(anyString(), anyString())).thenReturn(existing);

        // 构造满意反馈请求
        QueryRequest request = new QueryRequest();
        request.setUserId(1L);
        request.setSatisfied(true);
        request.setRetryQueryLogId(101L);

        QueryResponse response = textToSQLService.processQuery(request);

        // 验证响应
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getExplanation()).contains("感谢");

        // 验证 satisfied 已更新
        verify(queryLogService).updateSatisfied(101L, true);

        // 验证更新了已有模板评分，而不是创建新模板
        verify(userQueryTemplateService).updateScoreOnSatisfied(50L);
        verify(userQueryTemplateService, never()).create(anyString(), anyString(), anyLong());
    }

    /**
     * 测试场景3：用户不满意 - 触发重试
     * 期望：标记原记录不满意，继续生成新答案
     */
    @Test
    void testUserUnsatisfied_TriggerRetry() {
        // 准备 query_log 记录
        QueryLog mockLog = new QueryLog();
        mockLog.setId(102L);
        mockLog.setUserId(1L);
        mockLog.setQuestion("测试问题：查询订单数据");
        mockLog.setGeneratedSql("SELECT * FROM orders WHERE id = 1");
        mockLog.setDatasourceId(1L);
        mockLog.setSourceType("bfs");
        mockLog.setExecutionSuccess(true);
        mockLog.setSatisfied(null);

        when(queryLogService.findById(102L)).thenReturn(mockLog);

        // mock BFS 路径所需的依赖（返回空列表，避免 NPE）
        when(schemaVectorStoreService.searchSchemas(anyString(), isNull(), anyInt()))
            .thenReturn(java.util.Collections.emptyList());
        when(modelService.listAll()).thenReturn(java.util.Collections.emptyList());
        when(relationshipService.listAll()).thenReturn(java.util.Collections.emptyList());
        when(columnDefinitionService.getByModelId(anyLong())).thenReturn(java.util.Collections.emptyList());
        when(aiService.generateSQL(anyString(), anyString())).thenReturn("[]");
        when(queryLogService.logQuery(any())).thenReturn(103L);

        // 构造不满意反馈请求
        QueryRequest request = new QueryRequest();
        request.setUserId(1L);
        request.setQuestion("测试问题：查询订单数据");
        request.setSatisfied(false);
        request.setRetryQueryLogId(102L);

        QueryResponse response = textToSQLService.processQuery(request);

        // 验证响应不为空
        assertThat(response).isNotNull();

        // 验证原 query_log 标记为不满意
        verify(queryLogService).updateSatisfied(102L, false);

        // 验证不满意时不创建用户模板
        verify(userQueryTemplateService, never()).create(anyString(), anyString(), anyLong());
    }
}
