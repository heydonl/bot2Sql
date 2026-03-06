import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('../views/Dashboard.vue'),
    meta: { title: '仪表盘' }
  },
  {
    path: '/datasources',
    name: 'DataSources',
    component: () => import('../views/DataSources.vue'),
    meta: { title: '数据源管理' }
  },
  {
    path: '/models',
    name: 'Models',
    component: () => import('../views/Models.vue'),
    meta: { title: '表模型管理' }
  },
  {
    path: '/relationships',
    name: 'Relationships',
    component: () => import('../views/Relationships.vue'),
    meta: { title: '表关系配置' }
  },
  {
    path: '/llm-config',
    name: 'LLMConfig',
    component: () => import('../views/LLMConfig.vue'),
    meta: { title: '大模型配置' }
  },
  {
    path: '/prompts',
    name: 'Prompts',
    component: () => import('../views/Prompts.vue'),
    meta: { title: '提示词管理' }
  },
  {
    path: '/glossary',
    name: 'Glossary',
    component: () => import('../views/Glossary.vue'),
    meta: { title: '术语库' }
  },
  {
    path: '/query',
    name: 'Query',
    component: () => import('../views/Query.vue'),
    meta: { title: '查询测试' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - SQL2Bot` : 'SQL2Bot'
  next()
})

export default router
