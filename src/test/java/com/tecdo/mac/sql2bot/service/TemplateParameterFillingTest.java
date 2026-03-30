package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.QueryLog;
import com.tecdo.mac.sql2bot.domain.QueryTemplate;
import com.tecdo.mac.sql2bot.dto.QueryRequest;
import com.tecdo.mac.sql2bot.dto.QueryResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 模板参数填充功能测试
 * 测试通过公共API验证参数填充逻辑
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TemplateParameterFillingTest {

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

    @Mock
    private DataSourceService dataSourceService;

    @Mock
    private DatabaseService databaseService;

    @Mock
    private QueryStepLogService queryStepLogService;

    private ObjectMapper objectMapper;
    private QueryTemplate testTemplate;
    private QueryLog testQueryLog;
    private QueryRequest testRequest;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();

        // 注入 @Value 字段（Mockito 不会自动注入 @Value）
        java.lang.reflect.Field topKField = TextToSQLService.class.getDeclaredField("schemaSearchTopK");
        topKField.setAccessible(true);
        topKField.setInt(textToSQLService, 10);

        java.lang.reflect.Field bfsTopKField = TextToSQLService.class.getDeclaredField("schemaSearchBfsRetryTopK");
        bfsTopKField.setAccessible(true);
        bfsTopKField.setInt(textToSQLService, 20);

        java.lang.reflect.Field thresholdField = TextToSQLService.class.getDeclaredField("schemaSearchSimilarityThreshold");
        thresholdField.setAccessible(true);
        thresholdField.setDouble(textToSQLService, 0.5);

        java.lang.reflect.Field templateThresholdField = TextToSQLService.class.getDeclaredField("templateSearchSimilarityThreshold");
        templateThresholdField.setAccessible(true);
        templateThresholdField.setDouble(textToSQLService, 0.6);

        java.lang.reflect.Field omField = TextToSQLService.class.getDeclaredField("objectMapper");
        omField.setAccessible(true);
        omField.set(textToSQLService, objectMapper);

        // 初始化测试模板
        testTemplate = new QueryTemplate();
        testTemplate.setId(1L);
        testTemplate.setSqlTemplate("[{\"sql_template\":\"SELECT * FROM advertiser WHERE id = {{advertiserId}}\",\"datasource_id\":1}]");
        testTemplate.setParameters("{\"advertiserId\":\"广告主ID\"}");

        // 初始化测试查询日志
        testQueryLog = new QueryLog();
        testQueryLog.setId(1L);
        testQueryLog.setQuestion("查询广告主123的消费数据");
        testQueryLog.setGeneratedSql("SELECT * FROM advertiser WHERE id = 123");
        testQueryLog.setIntentJson("{\"tables\":[{\"name\":\"advertiser\",\"database\":\"test_db\"}]}");

        // 初始化测试请求
        testRequest = new QueryRequest();
        testRequest.setUserId(1L);
        testRequest.setQuestion("查询广告主123的数据");
        testRequest.setCreateNewConversation(true);
    }

    /**
     * 测试模板参数填充功能 - 通过模板匹配路径
     */
    @Test
    void testTemplateParameterFilling_ThroughTemplateMatch() throws Exception {
        // 准备模板搜索结果
        TemplateVectorSearchService.TemplateSearchResult searchResult =
            new TemplateVectorSearchService.TemplateSearchResult();
        TemplateVectorSearchService.TemplateMeta meta =
            new TemplateVectorSearchService.TemplateMeta();
        meta.setTemplateId(1L);
        searchResult.setMeta(meta);
        searchResult.setScore(0.85);

        // Mock 依赖调用
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
            .thenReturn(createTestQueryResult());
        when(queryLogService.getBestRecentExample(anyLong()))
            .thenReturn(testQueryLog);

        // 执行测试
        QueryResponse response = textToSQLService.processQuery(testRequest);

        // 验证参数填充成功
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertTrue(response.isFromTemplate());
        assertEquals(1L, response.getTemplateId());

        // 验证AI服务被调用进行参数填充
        verify(aiService, atLeast(1)).generateSQL(anyString(), anyString());
    }

    /**
     * 测试模板参数填充 - 处理复杂模板
     */
    @Test
    void testTemplateParameterFilling_ComplexTemplate() throws Exception {
        // 创建复杂模板
        QueryTemplate complexTemplate = new QueryTemplate();
        complexTemplate.setId(2L);
        complexTemplate.setSqlTemplate("[{\"sql_template\":\"SELECT * FROM advertiser WHERE id = {{advertiserId}} AND date >= '{{startDate}}'\",\"datasource_id\":1}]");
        complexTemplate.setParameters("{\"advertiserId\":\"广告主ID\",\"startDate\":\"开始日期\"}");

        testRequest.setQuestion("查询广告主123在2024年1月的数据");

        // 准备模板搜索结果
        TemplateVectorSearchService.TemplateSearchResult searchResult =
            new TemplateVectorSearchService.TemplateSearchResult();
        TemplateVectorSearchService.TemplateMeta meta =
            new TemplateVectorSearchService.TemplateMeta();
        meta.setTemplateId(2L);
        searchResult.setMeta(meta);
        searchResult.setScore(0.80);

        // Mock 依赖调用
        when(templateVectorSearchService.searchSimilarTemplates(anyString(), isNull(), eq(5)))
            .thenReturn(List.of(searchResult));
        when(queryTemplateService.getById(2L)).thenReturn(complexTemplate);
        when(conversationService.create(anyLong(), anyString(), isNull()))
            .thenReturn(createTestConversation());
        when(aiService.generateSQL(anyString(), anyString()))
            .thenReturn("SELECT * FROM advertiser WHERE id = 123 AND date >= '2024-01-01'");
        when(aiService.extractSQL(anyString()))
            .thenReturn("SELECT * FROM advertiser WHERE id = 123 AND date >= '2024-01-01'");
        when(semanticContextService.inferDataSourceFromSQL(anyString()))
            .thenReturn(1L);
        when(queryExecutorService.executeQuery(eq(1L), anyString()))
            .thenReturn(createTestQueryResult());
        when(queryLogService.getBestRecentExample(anyLong()))
            .thenReturn(testQueryLog);

        // 执行测试
        QueryResponse response = textToSQLService.processQuery(testRequest);

        // 验证复杂参数填充成功
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertTrue(response.isFromTemplate());
        assertEquals(2L, response.getTemplateId());

        // 验证AI服务被调用进行参数填充
        verify(aiService, atLeast(1)).generateSQL(anyString(), anyString());
    }

    /**
     * 测试模板参数填充 - 验证示例上下文的使用
     */
    @Test
    void testTemplateParameterFilling_WithExampleContext() throws Exception {
        // 准备有历史示例的模板搜索结果
        TemplateVectorSearchService.TemplateSearchResult searchResult =
            new TemplateVectorSearchService.TemplateSearchResult();
        TemplateVectorSearchService.TemplateMeta meta =
            new TemplateVectorSearchService.TemplateMeta();
        meta.setTemplateId(1L);
        searchResult.setMeta(meta);
        searchResult.setScore(0.85);

        // Mock 依赖调用，包括示例查询
        when(templateVectorSearchService.searchSimilarTemplates(anyString(), isNull(), eq(5)))
            .thenReturn(List.of(searchResult));
        when(queryTemplateService.getById(1L)).thenReturn(testTemplate);
        when(queryLogService.getBestExampleByTemplateId(1L)).thenReturn(testQueryLog);
        when(conversationService.create(anyLong(), anyString(), isNull()))
            .thenReturn(createTestConversation());
        when(aiService.generateSQL(anyString(), anyString()))
            .thenReturn("SELECT * FROM advertiser WHERE id = 123");
        when(aiService.extractSQL(anyString()))
            .thenReturn("SELECT * FROM advertiser WHERE id = 123");
        when(semanticContextService.inferDataSourceFromSQL(anyString()))
            .thenReturn(1L);
        when(queryExecutorService.executeQuery(eq(1L), anyString()))
            .thenReturn(createTestQueryResult());
        when(queryLogService.getBestRecentExample(anyLong()))
            .thenReturn(testQueryLog);

        // 执行测试
        QueryResponse response = textToSQLService.processQuery(testRequest);

        // 验证示例上下文被使用
        assertNotNull(response);
        assertTrue(response.getSuccess());
        verify(queryLogService).getBestExampleByTemplateId(1L);
    }

    /**
     * 测试模板参数填充 - 处理AI服务异常
     */
    @Test
    void testTemplateParameterFilling_AIServiceException() throws Exception {
        // 准备模板搜索结果
        TemplateVectorSearchService.TemplateSearchResult searchResult =
            new TemplateVectorSearchService.TemplateSearchResult();
        TemplateVectorSearchService.TemplateMeta meta =
            new TemplateVectorSearchService.TemplateMeta();
        meta.setTemplateId(1L);
        searchResult.setMeta(meta);
        searchResult.setScore(0.85);

        // Mock AI服务抛出异常，且路径2也失败（schema搜索失败）
        when(templateVectorSearchService.searchSimilarTemplates(anyString(), isNull(), eq(5)))
            .thenReturn(List.of(searchResult));
        when(queryTemplateService.getById(1L)).thenReturn(testTemplate);
        when(conversationService.create(anyLong(), anyString(), isNull()))
            .thenReturn(createTestConversation());
        when(aiService.generateSQL(anyString(), anyString()))
            .thenThrow(new RuntimeException("AI服务异常"));
        when(schemaVectorStoreService.searchSchemas(anyString(), isNull(), anyInt()))
            .thenThrow(new RuntimeException("AI服务异常"));

        // 执行测试
        QueryResponse response = textToSQLService.processQuery(testRequest);

        // 验证异常处理
        assertNotNull(response);
        assertFalse(response.getSuccess());
        assertNotNull(response.getErrorMessage());
        assertTrue(response.getErrorMessage().contains("异常"));
    }

    /**
     * 测试模板参数填充 - 验证JSON格式处理
     */
    @Test
    void testTemplateParameterFilling_JsonFormatHandling() throws Exception {
        // 创建包含JSON格式SQL模板的模板
        QueryTemplate jsonTemplate = new QueryTemplate();
        jsonTemplate.setId(3L);
        jsonTemplate.setSqlTemplate("[{\"sql_template\":\"SELECT * FROM advertiser WHERE id = {{advertiserId}}\",\"datasource_id\":1}]");
        jsonTemplate.setParameters("{\"advertiserId\":\"广告主ID\"}");

        // 准备模板搜索结果
        TemplateVectorSearchService.TemplateSearchResult searchResult =
            new TemplateVectorSearchService.TemplateSearchResult();
        TemplateVectorSearchService.TemplateMeta meta =
            new TemplateVectorSearchService.TemplateMeta();
        meta.setTemplateId(3L);
        searchResult.setMeta(meta);
        searchResult.setScore(0.90);

        // Mock 依赖调用
        when(templateVectorSearchService.searchSimilarTemplates(anyString(), isNull(), eq(5)))
            .thenReturn(List.of(searchResult));
        when(queryTemplateService.getById(3L)).thenReturn(jsonTemplate);
        when(conversationService.create(anyLong(), anyString(), isNull()))
            .thenReturn(createTestConversation());
        when(aiService.generateSQL(anyString(), anyString()))
            .thenReturn("SELECT * FROM advertiser WHERE id = 123");
        when(aiService.extractSQL(anyString()))
            .thenReturn("SELECT * FROM advertiser WHERE id = 123");
        when(semanticContextService.inferDataSourceFromSQL(anyString()))
            .thenReturn(1L);
        when(queryExecutorService.executeQuery(eq(1L), anyString()))
            .thenReturn(createTestQueryResult());
        when(queryLogService.getBestRecentExample(anyLong()))
            .thenReturn(testQueryLog);

        // 执行测试
        QueryResponse response = textToSQLService.processQuery(testRequest);

        // 验证JSON格式处理成功
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertTrue(response.isFromTemplate());
        assertEquals(3L, response.getTemplateId());
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

    // 辅助方法：创建测试查询结果
    private List<Map<String, Object>> createTestQueryResult() {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("id", 123);
        row.put("name", "测试广告主");
        row.put("consumption", 1000.0);
        result.add(row);
        return result;
    }

    // 辅助方法：创建测试模型
    private com.tecdo.mac.sql2bot.domain.Model createTestModel() {
        com.tecdo.mac.sql2bot.domain.Model model = new com.tecdo.mac.sql2bot.domain.Model();
        model.setId(1L);
        model.setTableName("advertiser");
        model.setDisplayName("广告主表");
        model.setDescription("广告主信息表");
        model.setDatasourceId(1L);
        return model;
    }

    // 辅助方法：创建消费表模型
    private com.tecdo.mac.sql2bot.domain.Model createConsumptionModel() {
        com.tecdo.mac.sql2bot.domain.Model model = new com.tecdo.mac.sql2bot.domain.Model();
        model.setId(2L);
        model.setTableName("consumption");
        model.setDisplayName("消费表");
        model.setDescription("广告消费数据表");
        model.setDatasourceId(1L);
        return model;
    }
}