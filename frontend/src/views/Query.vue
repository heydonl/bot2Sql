<template>
  <div class="query-page">
    <el-row :gutter="20">
      <!-- 左侧：查询输入 -->
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>查询测试</span>
          </template>

          <el-form :model="queryForm" label-width="100px">
            <el-form-item label="用户 ID">
              <el-input-number v-model="queryForm.userId" :min="1" />
            </el-form-item>

            <el-form-item label="数据源">
              <el-select v-model="queryForm.datasourceId" placeholder="自动检测" clearable>
                <el-option
                  v-for="ds in datasources"
                  :key="ds.id"
                  :label="ds.name"
                  :value="ds.id"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="会话">
              <el-select v-model="queryForm.conversationId" placeholder="新建会话" clearable>
                <el-option
                  v-for="conv in conversations"
                  :key="conv.id"
                  :label="conv.title"
                  :value="conv.id"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="问题">
              <el-input
                v-model="queryForm.question"
                type="textarea"
                :rows="4"
                placeholder="请输入您的问题，例如：查询所有数据源"
              />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="executeQuery" :loading="querying" :disabled="!queryForm.question">
                <el-icon><Search /></el-icon>
                执行查询
              </el-button>
              <el-button @click="clearForm">
                <el-icon><Delete /></el-icon>
                清空
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 查询历史 -->
        <el-card style="margin-top: 20px">
          <template #header>
            <span>查询历史</span>
          </template>

          <el-timeline>
            <el-timeline-item
              v-for="(item, index) in queryHistory"
              :key="index"
              :timestamp="item.timestamp"
              placement="top"
            >
              <el-card>
                <p><strong>问题：</strong>{{ item.question }}</p>
                <p><strong>SQL：</strong><code>{{ item.sql }}</code></p>
                <p><strong>结果：</strong>{{ item.rowCount }} 行</p>
              </el-card>
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>

      <!-- 右侧：查询结果 -->
      <el-col :span="12">
        <el-card v-if="queryResult">
          <template #header>
            <div class="result-header">
              <span>查询结果</span>
              <el-tag v-if="queryResult.success" type="success">成功</el-tag>
              <el-tag v-else type="danger">失败</el-tag>
            </div>
          </template>

          <div v-if="queryResult.success">
            <!-- SQL 语句 -->
            <el-divider content-position="left">生成的 SQL</el-divider>
            <el-input
              v-model="queryResult.sql"
              type="textarea"
              :rows="4"
              readonly
            />
            <el-button size="small" style="margin-top: 10px" @click="copySql">
              <el-icon><CopyDocument /></el-icon>
              复制 SQL
            </el-button>

            <!-- 解释 -->
            <el-divider content-position="left">查询解释</el-divider>
            <div class="explanation">{{ queryResult.explanation }}</div>

            <!-- 数据表格 -->
            <el-divider content-position="left">查询数据（{{ queryResult.rowCount }} 行）</el-divider>
            <el-table
              :data="queryResult.data"
              border
              stripe
              max-height="400"
              v-if="queryResult.data && queryResult.data.length > 0"
            >
              <el-table-column
                v-for="(value, key) in queryResult.data[0]"
                :key="key"
                :prop="key"
                :label="key"
              />
            </el-table>
            <el-empty v-else description="无数据" />

            <!-- 执行信息 -->
            <el-divider content-position="left">执行信息</el-divider>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="会话 ID">{{ queryResult.conversationId }}</el-descriptions-item>
              <el-descriptions-item label="执行时间">{{ queryResult.executionTime }} ms</el-descriptions-item>
              <el-descriptions-item label="返回行数">{{ queryResult.rowCount }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag v-if="queryResult.success" type="success">成功</el-tag>
                <el-tag v-else type="danger">失败</el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </div>

          <el-alert
            v-else
            :title="queryResult.message"
            type="error"
            :closable="false"
            show-icon
          />
        </el-card>

        <el-empty v-else description="暂无查询结果" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Delete, CopyDocument } from '@element-plus/icons-vue'
import { queryAPI, datasourceAPI } from '../api'

const queryForm = ref({
  userId: 1,
  datasourceId: null,
  conversationId: null,
  question: '',
  createNewConversation: false
})

const datasources = ref([])
const conversations = ref([])
const queryResult = ref(null)
const queryHistory = ref([])
const querying = ref(false)

const loadDatasources = async () => {
  try {
    const res = await datasourceAPI.list()
    datasources.value = res.data || []
  } catch (error) {
    console.error('加载数据源失败:', error)
  }
}

const loadConversations = async () => {
  try {
    const res = await queryAPI.getConversations(queryForm.value.userId)
    conversations.value = res.data || []
  } catch (error) {
    console.error('加载会话失败:', error)
  }
}

const executeQuery = async () => {
  querying.value = true
  queryResult.value = null

  try {
    const params = {
      ...queryForm.value,
      createNewConversation: !queryForm.value.conversationId
    }

    const res = await queryAPI.execute(params)
    queryResult.value = res.data

    // 添加到历史
    queryHistory.value.unshift({
      question: queryForm.value.question,
      sql: res.data.sql,
      rowCount: res.data.rowCount,
      timestamp: new Date().toLocaleString()
    })

    // 限制历史记录数量
    if (queryHistory.value.length > 10) {
      queryHistory.value.pop()
    }

    ElMessage.success('查询执行成功')
  } catch (error) {
    queryResult.value = {
      success: false,
      message: error.message || '查询执行失败'
    }
    ElMessage.error('查询执行失败')
  } finally {
    querying.value = false
  }
}

const clearForm = () => {
  queryForm.value.question = ''
  queryResult.value = null
}

const copySql = () => {
  if (queryResult.value?.sql) {
    navigator.clipboard.writeText(queryResult.value.sql)
    ElMessage.success('SQL 已复制到剪贴板')
  }
}

onMounted(() => {
  loadDatasources()
  loadConversations()
})
</script>

<style scoped>
.query-page {
  padding: 20px;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.explanation {
  padding: 15px;
  background: #f5f7fa;
  border-radius: 4px;
  line-height: 1.6;
  white-space: pre-wrap;
}

code {
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
}
</style>
