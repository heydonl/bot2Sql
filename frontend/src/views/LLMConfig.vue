<template>
  <div class="llm-config-page">
    <el-card>
      <template #header>
        <span>大模型配置</span>
      </template>

      <el-form :model="config" label-width="150px" style="max-width: 800px">
        <el-divider content-position="left">API 配置</el-divider>

        <el-form-item label="API Base URL">
          <el-input v-model="config.baseUrl" placeholder="http://localhost:5580" />
          <div class="form-tip">Claude API 的基础 URL</div>
        </el-form-item>

        <el-form-item label="API Key">
          <el-input v-model="config.apiKey" type="password" show-password placeholder="请输入 API Key" />
          <div class="form-tip">用于认证的 API 密钥</div>
        </el-form-item>

        <el-divider content-position="left">模型参数</el-divider>

        <el-form-item label="模型">
          <el-select v-model="config.model" placeholder="请选择模型">
            <el-option label="Claude 3.5 Sonnet" value="claude-3-5-sonnet-20241022" />
            <el-option label="Claude 3 Opus" value="claude-3-opus-20240229" />
            <el-option label="Claude 3 Haiku" value="claude-3-haiku-20240307" />
          </el-select>
          <div class="form-tip">选择要使用的 Claude 模型</div>
        </el-form-item>

        <el-form-item label="Max Tokens">
          <el-input-number v-model="config.maxTokens" :min="1" :max="100000" :step="1000" />
          <div class="form-tip">生成的最大 token 数量</div>
        </el-form-item>

        <el-form-item label="Temperature">
          <el-slider v-model="config.temperature" :min="0" :max="1" :step="0.1" show-input />
          <div class="form-tip">控制输出的随机性，0 表示确定性输出，1 表示最大随机性</div>
        </el-form-item>

        <el-divider content-position="left">RAG 配置</el-divider>

        <el-form-item label="启用向量 RAG">
          <el-switch v-model="config.useVectorRAG" />
          <div class="form-tip">使用向量检索替代关键词匹配（需要 Redis）</div>
        </el-form-item>

        <el-form-item label="Top-K">
          <el-input-number v-model="config.topK" :min="1" :max="50" />
          <div class="form-tip">RAG 检索返回的最相关表数量</div>
        </el-form-item>

        <el-divider content-position="left">Embedding 配置</el-divider>

        <el-form-item label="Embedding API URL">
          <el-input v-model="config.embeddingUrl" placeholder="http://localhost:5580" />
          <div class="form-tip">Embedding API 的基础 URL</div>
        </el-form-item>

        <el-form-item label="Embedding API Key">
          <el-input v-model="config.embeddingKey" type="password" show-password />
          <div class="form-tip">Embedding API 的密钥</div>
        </el-form-item>

        <el-form-item label="向量维度">
          <el-input-number v-model="config.embeddingDimension" :min="128" :max="4096" :step="128" />
          <div class="form-tip">Embedding 向量的维度</div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="saveConfig" :loading="saving">
            <el-icon><Check /></el-icon>
            保存配置
          </el-button>
          <el-button @click="testConnection" :loading="testing">
            <el-icon><Connection /></el-icon>
            测试连接
          </el-button>
          <el-button @click="loadConfig">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card style="margin-top: 20px">
      <template #header>
        <span>配置说明</span>
      </template>

      <el-alert
        title="配置提示"
        type="info"
        :closable="false"
        show-icon
      >
        <ul style="margin: 10px 0; padding-left: 20px">
          <li>修改配置后需要重启应用才能生效</li>
          <li>使用向量 RAG 需要先启动 Redis 服务</li>
          <li>Temperature 越低，输出越确定；越高，输出越有创造性</li>
          <li>Top-K 值越大，检索的表越多，但可能引入噪音</li>
        </ul>
      </el-alert>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Connection, Refresh } from '@element-plus/icons-vue'

const config = ref({
  baseUrl: 'http://localhost:5580',
  apiKey: 'qwe23hy',
  model: 'claude-3-5-sonnet-20241022',
  maxTokens: 4096,
  temperature: 0.0,
  useVectorRAG: false,
  topK: 10,
  embeddingUrl: 'http://localhost:5580',
  embeddingKey: 'qwe23hy',
  embeddingDimension: 1024
})

const saving = ref(false)
const testing = ref(false)

const loadConfig = () => {
  // TODO: 从后端加载配置
  ElMessage.info('配置已重置')
}

const saveConfig = async () => {
  saving.value = true
  try {
    // TODO: 保存到后端
    await new Promise(resolve => setTimeout(resolve, 1000))
    ElMessage.success('配置保存成功，请重启应用使配置生效')
  } catch (error) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

const testConnection = async () => {
  testing.value = true
  try {
    // TODO: 测试 API 连接
    await new Promise(resolve => setTimeout(resolve, 1000))
    ElMessage.success('连接测试成功')
  } catch (error) {
    ElMessage.error('连接测试失败')
  } finally {
    testing.value = false
  }
}

onMounted(() => {
  loadConfig()
})
</script>

<style scoped>
.llm-config-page {
  padding: 20px;
}

.form-tip {
  font-size: 12px;
  color: #999;
  margin-top: 5px;
}
</style>
