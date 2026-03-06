<template>
  <el-container class="app-container">
    <el-aside width="200px" class="sidebar">
      <div class="logo">
        <h2>SQL2Bot</h2>
        <p>管理后台</p>
      </div>
      <el-menu
        :default-active="$route.path"
        router
        class="sidebar-menu"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>

        <el-sub-menu index="data">
          <template #title>
            <el-icon><Database /></el-icon>
            <span>数据管理</span>
          </template>
          <el-menu-item index="/datasources">数据源</el-menu-item>
          <el-menu-item index="/models">表模型</el-menu-item>
          <el-menu-item index="/relationships">表关系</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="config">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>配置管理</span>
          </template>
          <el-menu-item index="/llm-config">大模型配置</el-menu-item>
          <el-menu-item index="/prompts">提示词管理</el-menu-item>
          <el-menu-item index="/glossary">术语库</el-menu-item>
        </el-sub-menu>

        <el-menu-item index="/query">
          <el-icon><Search /></el-icon>
          <span>查询测试</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-content">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ $route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { DataAnalysis, Database, Setting, Search } from '@element-plus/icons-vue'
</script>

<style scoped>
.app-container {
  height: 100vh;
}

.sidebar {
  background: #001529;
  color: #fff;
}

.logo {
  padding: 20px;
  text-align: center;
  border-bottom: 1px solid #ffffff1a;
}

.logo h2 {
  margin: 0;
  color: #fff;
  font-size: 20px;
}

.logo p {
  margin: 5px 0 0;
  color: #ffffff80;
  font-size: 12px;
}

.sidebar-menu {
  border: none;
  background: #001529;
}

:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  color: #ffffffa6;
}

:deep(.el-menu-item:hover),
:deep(.el-sub-menu__title:hover) {
  background: #ffffff1a !important;
  color: #fff;
}

:deep(.el-menu-item.is-active) {
  background: #1890ff !important;
  color: #fff;
}

.header {
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  padding: 0 20px;
}

.header-content {
  width: 100%;
}

.main-content {
  background: #f0f2f5;
  padding: 20px;
}
</style>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

#app {
  height: 100vh;
}
</style>
