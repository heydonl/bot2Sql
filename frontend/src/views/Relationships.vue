<template>
  <div class="relationships-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>表关系配置</span>
          <el-button type="primary" @click="showCreateDialog">
            <el-icon><Plus /></el-icon>
            新建关系
          </el-button>
        </div>
      </template>

      <!-- 关系列表 -->
      <el-table :data="relationships" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="源表" width="200">
          <template #default="{ row }">
            <el-tag>{{ getModelName(row.fromModelId) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="目标表" width="200">
          <template #default="{ row }">
            <el-tag type="success">{{ getModelName(row.toModelId) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="关系类型" width="150">
          <template #default="{ row }">
            <el-tag v-if="row.joinType === 'one_to_one'" type="primary">一对一</el-tag>
            <el-tag v-else-if="row.joinType === 'one_to_many'" type="success">一对多</el-tag>
            <el-tag v-else-if="row.joinType === 'many_to_many'" type="warning">多对多</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="JOIN条件" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatJoinCondition(row.joinCondition) }}
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="editRelationship(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="deleteRelationship(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建/编辑关系对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑关系' : '新建关系'"
      width="700px"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="源表" prop="fromModelId">
          <el-select
            v-model="form.fromModelId"
            placeholder="选择源表"
            style="width: 100%"
            @change="onFromModelChange"
          >
            <el-option
              v-for="model in models"
              :key="model.id"
              :label="`${model.displayName || model.tableName} (${model.tableName})`"
              :value="model.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="目标表" prop="toModelId">
          <el-select
            v-model="form.toModelId"
            placeholder="选择目标表"
            style="width: 100%"
            @change="onToModelChange"
          >
            <el-option
              v-for="model in models"
              :key="model.id"
              :label="`${model.displayName || model.tableName} (${model.tableName})`"
              :value="model.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="关系类型" prop="joinType">
          <el-radio-group v-model="form.joinType">
            <el-radio label="one_to_one">一对一</el-radio>
            <el-radio label="one_to_many">一对多</el-radio>
            <el-radio label="many_to_many">多对多</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="JOIN条件">
          <div class="join-conditions">
            <div
              v-for="(condition, index) in joinConditions"
              :key="index"
              class="condition-row"
            >
              <el-select
                v-model="condition.from"
                placeholder="源表字段"
                style="width: 200px"
              >
                <el-option
                  v-for="col in fromColumns"
                  :key="col.columnName"
                  :label="col.displayName || col.columnName"
                  :value="col.columnName"
                />
              </el-select>
              <span class="equals">=</span>
              <el-select
                v-model="condition.to"
                placeholder="目标表字段"
                style="width: 200px"
              >
                <el-option
                  v-for="col in toColumns"
                  :key="col.columnName"
                  :label="col.displayName || col.columnName"
                  :value="col.columnName"
                />
              </el-select>
              <el-button
                v-if="joinConditions.length > 1"
                type="danger"
                size="small"
                :icon="Delete"
                circle
                @click="removeCondition(index)"
              />
            </div>
            <el-button
              type="primary"
              size="small"
              @click="addCondition"
              style="margin-top: 10px"
            >
              <el-icon><Plus /></el-icon>
              添加条件
            </el-button>
          </div>
        </el-form-item>

        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="描述该关系的业务含义"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import { modelAPI, relationshipAPI, columnAPI } from '@/api'

const loading = ref(false)
const relationships = ref([])
const models = ref([])
const fromColumns = ref([])
const toColumns = ref([])

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const form = ref({
  fromModelId: null,
  toModelId: null,
  joinType: 'one_to_many',
  joinCondition: '',
  description: ''
})

const joinConditions = ref([{ from: '', to: '' }])

const rules = {
  fromModelId: [{ required: true, message: '请选择源表', trigger: 'change' }],
  toModelId: [{ required: true, message: '请选择目标表', trigger: 'change' }],
  joinType: [{ required: true, message: '请选择关系类型', trigger: 'change' }]
}

onMounted(() => {
  loadModels()
  loadRelationships()
})

const loadModels = async () => {
  try {
    const res = await modelAPI.list()
    if (res.data.code === 200) {
      models.value = res.data.data
    }
  } catch (error) {
    ElMessage.error('加载模型失败')
  }
}

const loadRelationships = async () => {
  loading.value = true
  try {
    const res = await relationshipAPI.list()
    if (res.data.code === 200) {
      relationships.value = res.data.data
    }
  } catch (error) {
    ElMessage.error('加载关系失败')
  } finally {
    loading.value = false
  }
}

const getModelName = (modelId) => {
  const model = models.value.find(m => m.id === modelId)
  return model ? (model.displayName || model.tableName) : '-'
}

const formatJoinCondition = (condition) => {
  if (!condition) return '-'
  try {
    const conditions = JSON.parse(condition)
    return conditions.map(c => `${c.from} = ${c.to}`).join(', ')
  } catch {
    return condition
  }
}

const onFromModelChange = async (modelId) => {
  fromColumns.value = []
  if (modelId) {
    try {
      const res = await columnAPI.listByModel(modelId)
      if (res.data.code === 200) {
        fromColumns.value = res.data.data
      }
    } catch (error) {
      ElMessage.error('加载字段失败')
    }
  }
}

const onToModelChange = async (modelId) => {
  toColumns.value = []
  if (modelId) {
    try {
      const res = await columnAPI.listByModel(modelId)
      if (res.data.code === 200) {
        toColumns.value = res.data.data
      }
    } catch (error) {
      ElMessage.error('加载字段失败')
    }
  }
}

const addCondition = () => {
  joinConditions.value.push({ from: '', to: '' })
}

const removeCondition = (index) => {
  joinConditions.value.splice(index, 1)
}

const showCreateDialog = () => {
  isEdit.value = false
  form.value = {
    fromModelId: null,
    toModelId: null,
    joinType: 'one_to_many',
    joinCondition: '',
    description: ''
  }
  joinConditions.value = [{ from: '', to: '' }]
  fromColumns.value = []
  toColumns.value = []
  dialogVisible.value = true
}

const editRelationship = async (row) => {
  isEdit.value = true
  form.value = { ...row }

  // 加载字段
  await onFromModelChange(row.fromModelId)
  await onToModelChange(row.toModelId)

  // 解析JOIN条件
  try {
    joinConditions.value = JSON.parse(row.joinCondition)
  } catch {
    joinConditions.value = [{ from: '', to: '' }]
  }

  dialogVisible.value = true
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return

    // 验证JOIN条件
    const validConditions = joinConditions.value.filter(c => c.from && c.to)
    if (validConditions.length === 0) {
      ElMessage.warning('请至少配置一个JOIN条件')
      return
    }

    try {
      const data = {
        ...form.value,
        joinCondition: JSON.stringify(validConditions)
      }

      if (isEdit.value) {
        await relationshipAPI.update(form.value.id, data)
        ElMessage.success('更新成功')
      } else {
        await relationshipAPI.create(data)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      loadRelationships()
    } catch (error) {
      ElMessage.error(isEdit.value ? '更新失败' : '创建失败')
    }
  })
}

const deleteRelationship = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除该关系吗？', '提示', {
      type: 'warning'
    })
    await relationshipAPI.delete(row.id)
    ElMessage.success('删除成功')
    loadRelationships()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}
</script>

<style scoped>
.relationships-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.join-conditions {
  width: 100%;
}

.condition-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.equals {
  font-weight: bold;
  color: #409eff;
}
</style>
