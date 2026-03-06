import request from './request'

// 数据源 API
export const datasourceAPI = {
  // 获取所有数据源
  list() {
    return request.get('/datasources')
  },
  // 获取单个数据源
  get(id) {
    return request.get(`/datasources/${id}`)
  },
  // 创建数据源
  create(data) {
    return request.post('/datasources', data)
  },
  // 更新数据源
  update(id, data) {
    return request.put(`/datasources/${id}`, data)
  },
  // 删除数据源
  delete(id) {
    return request.delete(`/datasources/${id}`)
  },
  // 测试连接
  testConnection(data) {
    return request.post('/datasources/test', data)
  },
  // 发现表结构
  discoverTables(id) {
    return request.post(`/datasources/${id}/discover`)
  }
}

// 表模型 API
export const modelAPI = {
  list() {
    return request.get('/models')
  },
  listByDatasource(datasourceId) {
    return request.get(`/models/datasource/${datasourceId}`)
  },
  listVisible() {
    return request.get('/models/visible')
  },
  get(id) {
    return request.get(`/models/${id}`)
  },
  create(data) {
    return request.post('/models', data)
  },
  update(id, data) {
    return request.put(`/models/${id}`, data)
  },
  delete(id) {
    return request.delete(`/models/${id}`)
  }
}

// 字段定义 API
export const columnAPI = {
  listByModel(modelId) {
    return request.get(`/columns/model/${modelId}`)
  },
  get(id) {
    return request.get(`/columns/${id}`)
  },
  create(data) {
    return request.post('/columns', data)
  },
  update(id, data) {
    return request.put(`/columns/${id}`, data)
  },
  delete(id) {
    return request.delete(`/columns/${id}`)
  }
}

// 表关系 API
export const relationshipAPI = {
  list() {
    return request.get('/relationships')
  },
  create(data) {
    return request.post('/relationships', data)
  },
  update(id, data) {
    return request.put(`/relationships/${id}`, data)
  },
  delete(id) {
    return request.delete(`/relationships/${id}`)
  }
}

// 查询 API
export const queryAPI = {
  execute(data) {
    return request.post('/query', data)
  },
  getConversations(userId) {
    return request.get(`/conversations/user/${userId}`)
  },
  getMessages(conversationId) {
    return request.get(`/conversations/${conversationId}/messages`)
  }
}

// 向量索引 API
export const vectorIndexAPI = {
  rebuildAll() {
    return request.post('/vector-index/rebuild-all')
  },
  indexModel(modelId) {
    return request.post(`/vector-index/index-model/${modelId}`)
  },
  deleteModel(modelId) {
    return request.delete(`/vector-index/model/${modelId}`)
  }
}
