<template>
  <div>
    <el-card>
      <template #header><span>审计日志</span></template>
      <el-table :data="logs" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="action" label="操作" min-width="200" show-overflow-tooltip />
        <el-table-column prop="detail" label="详情" min-width="200" show-overflow-tooltip />
        <el-table-column prop="ipAddress" label="IP" width="120" />
        <el-table-column prop="actionTime" label="时间" width="180" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '@/api/request'

const logs = ref([])

onMounted(async () => {
  try {
    const res = await api.get('/audit/logs')
    if (res.success) logs.value = res.data
  } catch (e) {}
})
</script>
