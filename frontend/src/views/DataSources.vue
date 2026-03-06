<template>
  <div class="datasources-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>数据源管理</span>
          <el-button type="primary" @click="showAddDialog">
            <el-icon><Plus /></el-icon>
            添加数据源
          </el-button>
        </div>
      </template>

      <el-table :data="datasources" v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="type" label="类型">
          <template #default="{ row }">
            <el-tag>{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="host" label="主机" />
        <el-table-column prop="port" label="端口" width="80" />
        <el-table-column prop="databaseName" label="数据库" />
        <el-table-column label="操作" width="300">
          <template #default="{ row }">
            <el-button size="small" @click="discoverTables(row)">
              <el-icon><Search /></el-icon>
              发现表
            </el-button>
            <el-button size="small" type="primary" @click="editDatasource(row)">
              编辑
            </el-button>
            <el-button size="small" type="danger" @click="deleteDatasource(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 添加/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入数据源名称" />
        </el-form-item>

        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" placeholder="请选择数据库类型">
            <el-option label="MySQL" value="mysql" />
            <el-option label="PostgreSQL" value="postgresql" />
          </el-select>
        </el-form-item>

        <el-form-item label="主机" prop="host">
          <el-input v-model="form.host" placeholder="localhost" />
        </el-form-item>

        <el-form-item label="端口" prop="port">
          <el-input-number v-model="form.port" :min="1" :max="65535" />
        </el-form-item>

        <el-form-item label="数据库名" prop="databaseName">
          <el-input v-model="form.databaseName" placeholder="请输入数据库名" />
        </el-form-item>

        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="testConnection" :loading="testing">
          测试连接
        </el-button>
        <el-button type="success" @click="submitForm" :loading="submitting">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { datasourceAPI } from '../api'

const datasources = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('添加数据源')
const formRef = ref(null)
const testing = ref(false)
const submitting = ref(false)

const form = ref({
  name: '',
  type: 'mysql',
  host: 'localhost',
  port: 3306,
  databaseName: '',
  username: '',
  password: ''
})

const rules = {
  name: [{ required: true, message: '请输入数据源名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择数据库类型', trigger: 'change' }],
  host: [{ required: true, message: '请输入主机地址', trigger: 'blur' }],
  port: [{ required: true, message: '请输入端口', trigger: 'blur' }],
  databaseName: [{ required: true, message: '请输入数据库名', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const loadDatasources = async () => {
  loading.value = true
  try {
    const res = await datasourceAPI.list()
    datasources.value = res.data || []
  } catch (error) {
    ElMessage.error('加载数据源失败')
  } finally {
    loading.value = false
  }
}

const showAddDialog = () => {
  dialogTitle.value = '添加数据源'
  form.value = {
    name: '',
    type: 'mysql',
    host: 'localhost',
    port: 3306,
    databaseName: '',
    username: '',
    password: ''
  }
  dialogVisible.value = true
}

const editDatasource = (row) => {
  dialogTitle.value = '编辑数据源'
  form.value = { ...row }
  dialogVisible.value = true
}

const testConnection = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  testing.value = true
  try {
    await datasourceAPI.testConnection(form.value)
    ElMessage.success('连接测试成功')
  } catch (error) {
    ElMessage.error('连接测试失败')
  } finally {
    testing.value = false
  }
}

const submitForm = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (form.value.id) {
      await datasourceAPI.update(form.value.id, form.value)
      ElMessage.success('更新成功')
    } else {
      await datasourceAPI.create(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadDatasources()
  } catch (error) {
    ElMessage.error('保存失败')
  } finally {
    submitting.value = false
  }
}

const deleteDatasource = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除这个数据源吗？', '提示', {
      type: 'warning'
    })
    await datasourceAPI.delete(row.id)
    ElMessage.success('删除成功')
    loadDatasources()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const discoverTables = async (row) => {
  try {
    await datasourceAPI.discoverTables(row.id)
    ElMessage.success('表结构发现成功')
  } catch (error) {
    ElMessage.error('表结构发现失败')
  }
}

onMounted(() => {
  loadDatasources()
})
</script>

<style scoped>
.datasources-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
