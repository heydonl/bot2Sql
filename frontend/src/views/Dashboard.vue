<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <el-icon class="stat-icon" color="#1890ff"><Database /></el-icon>
            <div class="stat-info">
              <div class="stat-value">{{ stats.datasources }}</div>
              <div class="stat-label">数据源</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <el-icon class="stat-icon" color="#52c41a"><Grid /></el-icon>
            <div class="stat-info">
              <div class="stat-value">{{ stats.models }}</div>
              <div class="stat-label">表模型</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <el-icon class="stat-icon" color="#faad14"><Connection /></el-icon>
            <div class="stat-info">
              <div class="stat-value">{{ stats.relationships }}</div>
              <div class="stat-label">表关系</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <el-icon class="stat-icon" color="#f5222d"><ChatDotRound /></el-icon>
            <div class="stat-info">
              <div class="stat-value">{{ stats.queries }}</div>
              <div class="stat-label">查询次数</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>快速操作</span>
            </div>
          </template>
          <div class="quick-actions">
            <el-button type="primary" @click="$router.push('/datasources')">
              <el-icon><Plus /></el-icon>
              添加数据源
            </el-button>
            <el-button type="success" @click="$router.push('/models')">
              <el-icon><Plus /></el-icon>
              创建表模型
            </el-button>
            <el-button type="warning" @click="$router.push('/query')">
              <el-icon><Search /></el-icon>
              测试查询
            </el-button>
          </div>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>系统信息</span>
            </div>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="后端版本">1.0.0</el-descriptions-item>
            <el-descriptions-item label="Spring Boot">4.0.3</el-descriptions-item>
            <el-descriptions-item label="Java">17</el-descriptions-item>
            <el-descriptions-item label="数据库">MySQL 8.0</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Database, Grid, Connection, ChatDotRound, Plus, Search } from '@element-plus/icons-vue'
import { datasourceAPI, modelAPI, relationshipAPI } from '../api'

const stats = ref({
  datasources: 0,
  models: 0,
  relationships: 0,
  queries: 0
})

const loadStats = async () => {
  try {
    const [datasources, models, relationships] = await Promise.all([
      datasourceAPI.list(),
      modelAPI.list(),
      relationshipAPI.list()
    ])

    stats.value.datasources = datasources.data?.length || 0
    stats.value.models = models.data?.length || 0
    stats.value.relationships = relationships.data?.length || 0
    stats.value.queries = 0 // TODO: 从后端获取
  } catch (error) {
    console.error('加载统计数据失败:', error)
  }
}

onMounted(() => {
  loadStats()
})
</script>

<style scoped>
.dashboard {
  padding: 20px;
}

.stat-card {
  cursor: pointer;
  transition: all 0.3s;
}

.stat-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.stat-content {
  display: flex;
  align-items: center;
  padding: 10px 0;
}

.stat-icon {
  font-size: 48px;
  margin-right: 20px;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 32px;
  font-weight: bold;
  color: #333;
}

.stat-label {
  font-size: 14px;
  color: #999;
  margin-top: 5px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.quick-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
</style>
