package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.QueryLog;
import com.tecdo.mac.sql2bot.domain.QueryTemplate;
import com.tecdo.mac.sql2bot.dto.QueryRequest;
import com.tecdo.mac.sql2bot.dto.QueryResponse;
import com.tecdo.mac.sql2bot.dto.SqlStep;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TextToSQLService 三路径查询执行逻辑测试
 * 测试路径1（RAG模板匹配）、路径2（Schema RAG + BFS）、路径3（高召回重试）
 */
@ExtendWith(MockitoExtension.class)
class TextToSQLServicePathTest {

    @InjectMocks
    private TextToSQLService textToSQLService;

    @Mock
    private AIService aiService;

    @Mock
    private SemanticContextService semanticContextService;

    @Mock
    private QueryExecutorService queryExecutorService;

    @Mock
    private ConversationService conversationService;

    @Mock
    private MessageService messageService;

    @Mock
    private IntentAnalysisService intentAnalysisService;

    @Mock
    private QueryTemplateService queryTemplateService;

    @Mock
    private TemplateParameterService templateParameterService;

    @Mock
    private QueryLogService queryLogService;

    @Mock
    private SchemaVectorStoreService schemaVectorStoreService;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private TemplateVectorSearchService templateVectorSearchService;

    @Mock
    private ModelService modelService;

    @Mock
    private RelationshipService relationshipService;

    @Mock
    private ColumnDefinitionService columnDefinitionService;

    @Mock
    private IntentFewShotService intentFewShotService;

    private ObjectMapper objectMapper;
    private QueryRequest testRequest;
    private QueryTemplate testTemplate;
    private List<Map<String, Object>> testQueryResult;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        // 初始化测试请求
        testRequest = new QueryRequest();
        testRequest.setUserId(1L);
        testRequest.setQuestion("查询广告主123的消费数据");
        testRequest.setCreateNewConversation(true);

        // 初始化测试模板
        testTemplate = new QueryTemplate();
        testTemplate.setId(1L);
        testTemplate.setSqlTemplate("[{\"sql_template\":\"SELECT * FROM advertiser WHERE id = {{advertiserId}}\",\"datasource_id\":1}]");
        testTemplate.setParameters("{\"advertiserId\":\"广告主ID\"}");

        // 初始化测试查询结果
        testQueryResult = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("id", 123);
        row.put("name", "测试广告主");
        row.put("consumption", 1000.0);
        testQueryResult.add(row);
    }

    /**
     * 测试路径1：RAG模板匹配成功的情况
     */
    @Test
    void testPath1_RAGTemplateMatchSuccess() throws Exception {
        // 准备模板搜索结果
        TemplateVectorSearchService.TemplateSearchResult searchResult =
            new TemplateVectorSearchService.TemplateSearchResult();
        TemplateVectorSearchService.TemplateMeta meta =
            new TemplateVectorSearchService.TemplateMeta();
        meta.setTemplateId(1L);
        searchResult.setMeta(meta);
        searchResult.setScore(0.85);

        List<TemplateVectorSearchService.TemplateSearchResult> templateResults = List.of(searchResult);

        // Mock 依赖调用
        when(templateVectorSearchService.searchSimilarTemplates(anyString(), isNull(), eq(5)))
            .thenReturn(templateResults);
        when(queryTemplateService.getById(1L)).thenReturn(testTemplate);
        when(conversationService.create(anyLong(), anyString(), isNull()))
            .thenReturn(createTestConversation());
        when(aiService.generateSQL(anyString(), anyString()))
            .thenReturn("SELECT * FROM advertiser WHERE id = 123");
        when(aiService.extractSQL(anyString()))
            .thenReturn("SELECT * FROM advertiser WHERE id = 123");
        when(semanticContextService.inferDataSourceFromSQL(anyString()))
            .thenReturn(1L);
        when(queryExecutorService.executeQuery(eq(1L), anyString()))
            .thenReturn(testQueryResult);
        when(queryLogService.getBestRecentExample(anyLong()))
            .thenReturn(createTestQueryLog());

        // 执行测试
        QueryResponse response = textToSQLService.processQuery(testRequest);

        // 验证结果
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertEquals("通过模板匹配生成查询", response.getExplanation());
        assertTrue(response.isFromTemplate());
        assertEquals(1L, response.getTemplateId());
        assertEquals(0.85, response.getTemplateSimilarity(), 0.01);
        assertNotNull(response.getData());
        assertEquals(1, response.getData().size());

        // 验证关键方法调用
        verify(templateVectorSearchService).searchSimilarTemplates(anyString(), isNull(), eq(5));
        verify(queryTemplateService).getById(1L);
        verify(queryTemplateService).incrementUsageCount(1L);
        verify(queryExecutorService).executeQuery(eq(1L), anyString());
        verify(queryLogService).logQuery(any(QueryLog.class));
    }

    /**
     * 测试路径1失败降级到路径2的情况
     */
    @Test
    void testPath1_FailureFallbackToPath2() throws Exception {
        // Mock 模板搜索失败
        when(templateVectorSearchService.searchSimilarTemplates(anyString(), isNull(), eq(5)))
            .thenThrow(new RuntimeException("模板搜索服务异常"));

        // Mock 路径2的依赖
        mockPath2Dependencies();

        // 执行测试
        QueryResponse response = textToSQLService.processQuery(testRequest);

        // 验证结果
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertEquals("通过BFS表发现生成查询", response.getExplanation());
        assertFalse(response.isFromTemplate());
        assertNull(response.getTemplateId());

        // 验证降级逻辑
        verify(templateVectorSearchService).searchSimilarTemplates(anyString(), isNull(), eq(5));
        verify(schemaVectorStoreService).searchSchemas(anyString(), isNull(), eq(10));
    }

    /**
     * 测试路径2：Schema RAG + BFS正常扩展
     */
    @Test
    void testPath2_SchemaRAGWithBFS() throws Exception {
        // Mock 模板搜索返回空结果，触发路径2
        when(templateVectorSearchService.searchSimilarTemplates(anyString(), isNull(), eq(5)))
            .thenReturn(new ArrayList<>());

        // Mock 路径2的依赖
        mockPath2Dependencies();

        // 执行测试
        QueryResponse response = textToSQLService.processQuery(testRequest);

        // 验证结果
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertEquals("通过BFS表发现生成查询", response.getExplanation());
        assertFalse(response.isFromTemplate());

        // 验证路径2的关键调用
        verify(schemaVectorStoreService).searchSchemas(anyString(), isNull(), eq(10));
        verify(relationshipService).findByModelId(anyLong());
        verify(modelService).getById(anyLong());
        verify(columnDefinitionService).getByModelId(anyLong());
    }

    /**
     * 测试路径3：用户不满意触发高召回重试
     */
    @Test
    void testPath3_HighRecallRetry() throws Exception {
        // 设置用户不满意的请求
        testRequest.setSatisfied(false);
        testRequest.setRetryQueryLogId(100L);

        // Mock 高召回搜索结果
        mockPath3Dependencies();

        // 执行测试
        QueryResponse response = textToSQLService.processQuery(testRequest);

        // 验证结果
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertEquals("通过BFS表发现生成查询", response.getExplanation());

        // 验证路径3的关键调用（使用更高的top-k值）
        verify(schemaVectorStoreService).searchSchemas(anyString(), isNull(), eq(20));
    }

    /**
     * 测试用户满意时的向量索引存储
     */
    @Test
    void testUserSatisfiedVectorIndexing() throws Exception {
        // 设置用户满意的请求
        testRequest.setSatisfied(true);
        testRequest.setRetryQueryLogId(100L);

        QueryLog satisfiedQueryLog = createTestQueryLog();
        when(queryLogService.findById(100L)).thenReturn(satisfiedQueryLog);

        // Mock 其他依赖
        mockPath2Dependencies();

        // 执行测试
        QueryResponse response = textToSQLService.processQuery(testRequest);

        // 验证向量索引调用
        verify(queryLogService).findById(100L);
        verify(schemaVectorStoreService).indexQuestionAnswer(
            eq(satisfiedQueryLog.getId()),
            eq(satisfiedQueryLog.getQuestion()),
            eq(satisfiedQueryLog.getGeneratedSql())
        );
    }

    /**
     * 测试配置参数的正确使用
     */
    @Test
    void testConfigurationParameters() throws Exception {
        // Mock 空模板搜索结果，触发路径2
        when(templateVectorSearchService.searchSimilarTemplates(anyString(), isNull(), eq(5)))
            .thenReturn(new ArrayList<>());

        mockPath2Dependencies();

        // 执行测试
        textToSQLService.processQuery(testRequest);

        // 验证配置参数的使用
        verify(schemaVectorStoreService).searchSchemas(anyString(), isNull(), eq(10)); // schema.search.top-k

        // 测试路径3的配置参数
        testRequest.setSatisfied(false);
        testRequest.setRetryQueryLogId(100L);
        mockPath3Dependencies();

        textToSQLService.processQuery(testRequest);
        verify(schemaVectorStoreService).searchSchemas(anyString(), isNull(), eq(20)); // schema.search.bfs-retry-top-k
    }

    /**
     * 测试异常处理和错误降级
     */
    @Test
    void testExceptionHandlingAndFallback() throws Exception {
        // Mock 所有路径都失败的情况
        when(templateVectorSearchService.searchSimilarTemplates(anyString(), isNull(), eq(5)))
            .thenThrow(new RuntimeException("模板搜索失败"));
        when(schemaVectorStoreService.searchSchemas(anyString(), isNull(), anyInt()))
            .thenThrow(new RuntimeException("Schema搜索失败"));
        when(semanticContextService.generateSystemPrompt(isNull(), anyString()))
            .thenThrow(new RuntimeException("语义上下文生成失败"));

        // 执行测试
        QueryResponse response = textToSQLService.processQuery(testRequest);

        // 验证错误处理
        assertNotNull(response);
        assertFalse(response.getSuccess());
        assertNotNull(response.getErrorMessage());
        assertTrue(response.getErrorMessage().contains("失败"));
    }

    /**
     * 测试SQL执行失败的处理
     */
    @Test
    void testSQLExecutionFailure() throws Exception {
        // Mock 模板匹配成功但SQL执行失败
        TemplateVectorSearchService.TemplateSearchResult searchResult =
            new TemplateVectorSearchService.TemplateSearchResult();
        TemplateVectorSearchService.TemplateMeta meta =
            new TemplateVectorSearchService.TemplateMeta();
        meta.setTemplateId(1L);
        searchResult.setMeta(meta);
        searchResult.setScore(0.85);

        when(templateVectorSearchService.searchSimilarTemplates(anyString(), isNull(), eq(5)))
            .thenReturn(List.of(searchResult));
        when(queryTemplateService.getById(1L)).thenReturn(testTemplate);
        when(conversationService.create(anyLong(), anyString(), isNull()))
            .thenReturn(createTestConversation());
        when(aiService.generateSQL(anyString(), anyString()))
            .thenReturn("SELECT * FROM advertiser WHERE id = 123");
        when(aiService.extractSQL(anyString()))
            .thenReturn("SELECT * FROM advertiser WHERE id = 123");
        when(semanticContextService.inferDataSourceFromSQL(anyString()))
            .thenReturn(1L);
        when(queryExecutorService.executeQuery(eq(1L), anyString()))
            .thenThrow(new RuntimeException("SQL执行失败：表不存在"));

        // 执行测试
        QueryResponse response = textToSQLService.processQuery(testRequest);

        // 验证错误处理
        assertNotNull(response);
        assertFalse(response.getSuccess());
        assertTrue(response.getErrorMessage().contains("SQL执行失败"));

        // 验证失败日志记录
        verify(queryLogService).logQuery(argThat(log ->
            !log.getExecutionSuccess() && log.getGeneratedSql() != null));
    }

    // 辅助方法：创建测试会话
    private com.tecdo.mac.sql2bot.domain.Conversation createTestConversation() {
        com.tecdo.mac.sql2bot.domain.Conversation conversation =
            new com.tecdo.mac.sql2bot.domain.Conversation();
        conversation.setId(1L);
        conversation.setUserId(1L);
        conversation.setTitle("测试会话");
        return conversation;
    }

    // 辅助方法：创建测试查询日志
    private QueryLog createTestQueryLog() {
        QueryLog queryLog = new QueryLog();
        queryLog.setId(1L);
        queryLog.setQuestion("查询广告主消费数据");
        queryLog.setGeneratedSql("SELECT * FROM advertiser WHERE id = 123");
        queryLog.setIntentJson("{\"tables\":[{\"name\":\"advertiser\",\"database\":\"test_db\"}]}");
        return queryLog;
    }

    // 辅助方法：Mock路径2的依赖
    private void mockPath2Dependencies() throws Exception {
        // Mock Schema搜索结果
        SchemaVectorStoreService.SchemaSearchResult schemaResult =
            new SchemaVectorStoreService.SchemaSearchResult();
        SchemaVectorStoreService.SchemaMeta schemaMeta =
            new SchemaVectorStoreService.SchemaMeta();
        schemaMeta.setModelId(1L);
        schemaMeta.setTableName("advertiser");
        schemaResult.setMeta(schemaMeta);
        schemaResult.setScore(0.8);

        when(schemaVectorStoreService.searchSchemas(anyString(), isNull(), eq(10)))
            .thenReturn(List.of(schemaResult));

        // Mock BFS扩展相关
        when(relationshipService.findByModelId(anyLong()))
            .thenReturn(new ArrayList<>());
        when(modelService.getById(anyLong()))
            .thenReturn(createTestModel());
        when(columnDefinitionService.getByModelId(anyLong()))
            .thenReturn(new ArrayList<>());
        when(relationshipService.listAll())
            .thenReturn(new ArrayList<>());
        when(intentFewShotService.getFewShotExamples(isNull(), anyString()))
            .thenReturn("示例：查询广告主数据");

        // Mock LLM生成执行计划
        when(aiService.generateSQL(anyString(), anyString()))
            .thenReturn("[{\"sql_template\":\"SELECT * FROM advertiser WHERE id = {{advertiserId}}\",\"datasource_id\":1}]");

        // Mock 其他依赖
        when(conversationService.create(anyLong(), anyString(), isNull()))
            .thenReturn(createTestConversation());
        when(aiService.extractSQL(anyString()))
            .thenReturn("SELECT * FROM advertiser WHERE id = 123");
        when(semanticContextService.inferDataSourceFromSQL(anyString()))
            .thenReturn(1L);
        when(queryExecutorService.executeQuery(eq(1L), anyString()))
            .thenReturn(testQueryResult);
        when(queryLogService.getBestRecentExample(anyLong()))
            .thenReturn(createTestQueryLog());
    }

    // 辅助方法：Mock路径3的依赖
    private void mockPath3Dependencies() throws Exception {
        // Mock 高召回Schema搜索结果
        SchemaVectorStoreService.SchemaSearchResult schemaResult =
            new SchemaVectorStoreService.SchemaSearchResult();
        SchemaVectorStoreService.SchemaMeta schemaMeta =
            new SchemaVectorStoreService.SchemaMeta();
        schemaMeta.setModelId(1L);
        schemaMeta.setTableName("advertiser");
        schemaResult.setMeta(schemaMeta);
        schemaResult.setScore(0.3); // 低相似度，但在高召回模式下仍被包含

        when(schemaVectorStoreService.searchSchemas(anyString(), isNull(), eq(20)))
            .thenReturn(List.of(schemaResult));

        // 复用路径2的其他Mock
        mockPath2DependenciesCommon();
    }

    // 辅助方法：路径2和路径3的通用Mock
    private void mockPath2DependenciesCommon() throws Exception {
        when(relationshipService.findByModelId(anyLong()))
            .thenReturn(new ArrayList<>());
        when(modelService.getById(anyLong()))
            .thenReturn(createTestModel());
        when(columnDefinitionService.getByModelId(anyLong()))
            .thenReturn(new ArrayList<>());
        when(relationshipService.listAll())
            .thenReturn(new ArrayList<>());
        when(intentFewShotService.getFewShotExamples(isNull(), anyString()))
            .thenReturn("示例：查询广告主数据");
        when(aiService.generateSQL(anyString(), anyString()))
            .thenReturn("[{\"sql_template\":\"SELECT * FROM advertiser WHERE id = {{advertiserId}}\",\"datasource_id\":1}]");
        when(conversationService.create(anyLong(), anyString(), isNull()))
            .thenReturn(createTestConversation());
        when(aiService.extractSQL(anyString()))
            .thenReturn("SELECT * FROM advertiser WHERE id = 123");
        when(semanticContextService.inferDataSourceFromSQL(anyString()))
            .thenReturn(1L);
        when(queryExecutorService.executeQuery(eq(1L), anyString()))
            .thenReturn(testQueryResult);
        when(queryLogService.getBestRecentExample(anyLong()))
            .thenReturn(createTestQueryLog());
    }

    // 辅助方法：创建测试模型
    private com.tecdo.mac.sql2bot.domain.Model createTestModel() {
        com.tecdo.mac.sql2bot.domain.Model model = new com.tecdo.mac.sql2bot.domain.Model();
        model.setId(1L);
        model.setTableName("advertiser");
        model.setDisplayName("广告主表");
        model.setDescription("存储广告主信息的表");
        model.setDatasourceId(1L);
        return model;
    }
}