<template>
  <div class="models-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>表模型管理</span>
          <el-button type="primary" @click="showCreateDialog">
            <el-icon><Plus /></el-icon>
            新建模型
          </el-button>
        </div>
      </template>

      <!-- 数据源筛选 -->
      <div class="filter-bar">
        <el-select
          v-model="selectedDatasourceId"
          placeholder="选择数据源"
          clearable
          @change="loadModels"
          style="width: 300px"
        >
          <el-option
            v-for="ds in datasources"
            :key="ds.id"
            :label="ds.name"
            :value="ds.id"
          />
        </el-select>
        <el-button @click="loadModels" :icon="Refresh">刷新</el-button>
      </div>

      <!-- 模型列表 -->
      <el-table :data="models" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="tableName" label="物理表名" width="200" />
        <el-table-column prop="displayName" label="显示名称" width="200" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="primaryKey" label="主键" width="150" />
        <el-table-column label="可见性" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isVisible ? 'success' : 'info'">
              {{ row.isVisible ? '可见' : '隐藏' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="viewColumns(row)">字段</el-button>
            <el-button size="small" @click="editModel(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="deleteModel(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建/编辑模型对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑模型' : '新建模型'"
      width="600px"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="数据源" prop="datasourceId">
          <el-select v-model="form.datasourceId" placeholder="选择数据源" style="width: 100%">
            <el-option
              v-for="ds in datasources"
              :key="ds.id"
              :label="ds.name"
              :value="ds.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="物理表名" prop="tableName">
          <el-input v-model="form.tableName" placeholder="例如: users" />
        </el-form-item>
        <el-form-item label="显示名称" prop="displayName">
          <el-input v-model="form.displayName" placeholder="例如: 用户表" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="描述该表的业务含义"
          />
        </el-form-item>
        <el-form-item label="主键">
          <el-input v-model="form.primaryKey" placeholder="例如: id" />
        </el-form-item>
        <el-form-item label="可见性">
          <el-switch v-model="form.isVisible" active-text="可见" inactive-text="隐藏" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 字段管理对话框 -->
    <el-dialog
      v-model="columnsDialogVisible"
      :title="`${currentModel?.displayName || currentModel?.tableName} - 字段管理`"
      width="80%"
    >
      <div class="columns-header">
        <el-button type="primary" size="small" @click="showAddColumnDialog">
          <el-icon><Plus /></el-icon>
          添加字段
        </el-button>
      </div>
      <el-table :data="columns" v-loading="columnsLoading" stripe>
        <el-table-column prop="columnName" label="字段名" width="150" />
        <el-table-column prop="displayName" label="显示名称" width="150" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="dataType" label="数据类型" width="120" />
        <el-table-column label="字段类型" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.columnType === 'dimension'" type="primary">维度</el-tag>
            <el-tag v-else-if="row.columnType === 'measure'" type="success">度量</el-tag>
            <el-tag v-else type="info">-</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="可空" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isNullable ? 'warning' : 'success'">
              {{ row.isNullable ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="editColumn(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="deleteColumn(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 添加/编辑字段对话框 -->
    <el-dialog
      v-model="columnDialogVisible"
      :title="isEditColumn ? '编辑字段' : '添加字段'"
      width="600px"
    >
      <el-form :model="columnForm" :rules="columnRules" ref="columnFormRef" label-width="100px">
        <el-form-item label="字段名" prop="columnName">
          <el-input v-model="columnForm.columnName" placeholder="例如: user_id" />
        </el-form-item>
        <el-form-item label="显示名称" prop="displayName">
          <el-input v-model="columnForm.displayName" placeholder="例如: 用户ID" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="columnForm.description"
            type="textarea"
            :rows="2"
            placeholder="描述该字段的业务含义"
          />
        </el-form-item>
        <el-form-item label="数据类型" prop="dataType">
          <el-select v-model="columnForm.dataType" placeholder="选择数据类型" style="width: 100%">
            <el-option label="VARCHAR" value="VARCHAR" />
            <el-option label="INT" value="INT" />
            <el-option label="BIGINT" value="BIGINT" />
            <el-option label="DECIMAL" value="DECIMAL" />
            <el-option label="DATE" value="DATE" />
            <el-option label="DATETIME" value="DATETIME" />
            <el-option label="TIMESTAMP" value="TIMESTAMP" />
            <el-option label="TEXT" value="TEXT" />
            <el-option label="BOOLEAN" value="BOOLEAN" />
          </el-select>
        </el-form-item>
        <el-form-item label="字段类型" prop="columnType">
          <el-radio-group v-model="columnForm.columnType">
            <el-radio label="dimension">维度（用于分组筛选）</el-radio>
            <el-radio label="measure">度量（用于聚合计算）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="可空">
          <el-switch v-model="columnForm.isNullable" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="columnDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitColumnForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { datasourceAPI, modelAPI, columnAPI } from '@/api'

const loading = ref(false)
const models = ref([])
const datasources = ref([])
const selectedDatasourceId = ref(null)

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const form = ref({
  datasourceId: null,
  tableName: '',
  displayName: '',
  description: '',
  primaryKey: '',
  isVisible: true
})

const rules = {
  datasourceId: [{ required: true, message: '请选择数据源', trigger: 'change' }],
  tableName: [{ required: true, message: '请输入物理表名', trigger: 'blur' }],
  displayName: [{ required: true, message: '请输入显示名称', trigger: 'blur' }]
}

// 字段管理相关
const columnsDialogVisible = ref(false)
const columnsLoading = ref(false)
const columns = ref([])
const currentModel = ref(null)

const columnDialogVisible = ref(false)
const isEditColumn = ref(false)
const columnFormRef = ref(null)
const columnForm = ref({
  modelId: null,
  columnName: '',
  displayName: '',
  description: '',
  dataType: '',
  columnType: 'dimension',
  isNullable: true
})

const columnRules = {
  columnName: [{ required: true, message: '请输入字段名', trigger: 'blur' }],
  displayName: [{ required: true, message: '请输入显示名称', trigger: 'blur' }],
  dataType: [{ required: true, message: '请选择数据类型', trigger: 'change' }],
  columnType: [{ required: true, message: '请选择字段类型', trigger: 'change' }]
}

onMounted(() => {
  loadDatasources()
  loadModels()
})

const loadDatasources = async () => {
  try {
    const res = await datasourceAPI.list()
    if (res.data.code === 200) {
      datasources.value = res.data.data
    }
  } catch (error) {
    ElMessage.error('加载数据源失败')
  }
}

const loadModels = async () => {
  loading.value = true
  try {
    let res
    if (selectedDatasourceId.value) {
      res = await modelAPI.listByDatasource(selectedDatasourceId.value)
    } else {
      res = await modelAPI.list()
    }
    if (res.data.code === 200) {
      models.value = res.data.data
    }
  } catch (error) {
    ElMessage.error('加载模型失败')
  } finally {
    loading.value = false
  }
}

const showCreateDialog = () => {
  isEdit.value = false
  form.value = {
    datasourceId: selectedDatasourceId.value,
    tableName: '',
    displayName: '',
    description: '',
    primaryKey: '',
    isVisible: true
  }
  dialogVisible.value = true
}

const editModel = (row) => {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return

    try {
      if (isEdit.value) {
        await modelAPI.update(form.value.id, form.value)
        ElMessage.success('更新成功')
      } else {
        await modelAPI.create(form.value)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      loadModels()
    } catch (error) {
      ElMessage.error(isEdit.value ? '更新失败' : '创建失败')
    }
  })
}

const deleteModel = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除该模型吗？', '提示', {
      type: 'warning'
    })
    await modelAPI.delete(row.id)
    ElMessage.success('删除成功')
    loadModels()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 字段管理
const viewColumns = async (row) => {
  currentModel.value = row
  columnsDialogVisible.value = true
  await loadColumns(row.id)
}

const loadColumns = async (modelId) => {
  columnsLoading.value = true
  try {
    const res = await columnAPI.listByModel(modelId)
    if (res.data.code === 200) {
      columns.value = res.data.data
    }
  } catch (error) {
    ElMessage.error('加载字段失败')
  } finally {
    columnsLoading.value = false
  }
}

const showAddColumnDialog = () => {
  isEditColumn.value = false
  columnForm.value = {
    modelId: currentModel.value.id,
    columnName: '',
    displayName: '',
    description: '',
    dataType: '',
    columnType: 'dimension',
    isNullable: true
  }
  columnDialogVisible.value = true
}

const editColumn = (row) => {
  isEditColumn.value = true
  columnForm.value = { ...row }
  columnDialogVisible.value = true
}

const submitColumnForm = async () => {
  if (!columnFormRef.value) return
  await columnFormRef.value.validate(async (valid) => {
    if (!valid) return

    try {
      if (isEditColumn.value) {
        await columnAPI.update(columnForm.value.id, columnForm.value)
        ElMessage.success('更新成功')
      } else {
        await columnAPI.create(columnForm.value)
        ElMessage.success('创建成功')
      }
      columnDialogVisible.value = false
      loadColumns(currentModel.value.id)
    } catch (error) {
      ElMessage.error(isEditColumn.value ? '更新失败' : '创建失败')
    }
  })
}

const deleteColumn = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除该字段吗？', '提示', {
      type: 'warning'
    })
    await columnAPI.delete(row.id)
    ElMessage.success('删除成功')
    loadColumns(currentModel.value.id)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}
</script>

<style scoped>
.models-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-bar {
  margin-bottom: 20px;
  display: flex;
  gap: 10px;
}

.columns-header {
  margin-bottom: 15px;
}
</style>
