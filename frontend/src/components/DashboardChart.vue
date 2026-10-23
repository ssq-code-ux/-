<template>
  <div>
    <el-row :gutter="16">
      <!-- 提交趋势图 -->
      <el-col :xs="24" :md="12" style="margin-bottom: 16px">
        <el-card>
          <h4>近7天提交趋势</h4>
          <div ref="submitChartRef" style="height: 280px"></div>
          <el-empty v-if="!hasSubmitData" description="暂无提交数据" :image-size="60" />
        </el-card>
      </el-col>

      <!-- 分数趋势图 -->
      <el-col :xs="24" :md="12" style="margin-bottom: 16px">
        <el-card>
          <h4>近期作业得分</h4>
          <div ref="scoreChartRef" style="height: 280px"></div>
          <el-empty v-if="!hasScoreData" description="暂无评分数据" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onBeforeUnmount, nextTick, computed } from 'vue';
// 按需引入 echarts（只用本组件用到的柱状图/折线图），替代整包 1MB 引入
import * as echarts from 'echarts/core';
import { BarChart, LineChart } from 'echarts/charts';
import { GridComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

echarts.use([BarChart, LineChart, GridComponent, TooltipComponent, CanvasRenderer]);

const props = defineProps({
  submitTrend: { type: Array, default: () => [] },
  scoreTrend: { type: Array, default: () => [] }
});

const submitChartRef = ref(null);
const scoreChartRef = ref(null);
let submitChart = null;
let scoreChart = null;

const hasSubmitData = computed(() => props.submitTrend?.some(d => d.count > 0));
const hasScoreData = computed(() => props.scoreTrend?.length > 0);

function renderCharts() {
  nextTick(() => {
    // 提交趋势图
    if (submitChartRef.value && hasSubmitData.value) {
      if (submitChart) submitChart.dispose();
      submitChart = echarts.init(submitChartRef.value);
      submitChart.setOption({
        tooltip: { trigger: 'axis' },
        xAxis: {
          type: 'category',
          data: props.submitTrend.map(d => d.date?.slice(5) || ''),
          axisLabel: { rotate: 45 }
        },
        yAxis: { type: 'value', minInterval: 1, name: '提交数' },
        series: [{
          type: 'bar',
          data: props.submitTrend.map(d => d.count || 0),
          itemStyle: {
            // 渐变用对象字面量（echarts 官方写法），免去引入 graphic.LinearGradient
            color: {
              type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [
                { offset: 0, color: 'var(--accent)' },
                { offset: 1, color: '#79bbff' }
              ]
            }
          },
          barWidth: '60%'
        }]
      });
    }

    // 分数趋势图
    if (scoreChartRef.value && hasScoreData.value) {
      if (scoreChart) scoreChart.dispose();
      scoreChart = echarts.init(scoreChartRef.value);
      scoreChart.setOption({
        tooltip: { trigger: 'axis' },
        xAxis: {
          type: 'category',
          data: props.scoreTrend.map(d => d.title?.length > 8 ? d.title.slice(0, 8) + '...' : (d.title || '')),
          axisLabel: { rotate: 30 }
        },
        yAxis: { type: 'value', min: 0, max: 100, name: '分数' },
        series: [{
          type: 'line',
          data: props.scoreTrend.map(d => d.score || 0),
          smooth: true,
          lineStyle: { color: 'var(--positive)', width: 3 },
          itemStyle: { color: 'var(--positive)' },
          areaStyle: {
            color: {
              type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(103,194,58,0.3)' },
                { offset: 1, color: 'rgba(103,194,58,0.05)' }
              ]
            }
          }
        }]
      });
    }
  });
}

watch(() => [props.submitTrend, props.scoreTrend], renderCharts, { deep: true });
onMounted(renderCharts);

// 窗口大小变化时自动重绘图表
function handleResize() {
  submitChart?.resize();
  scoreChart?.resize();
}
onMounted(() => window.addEventListener('resize', handleResize));
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
  submitChart?.dispose();
  scoreChart?.dispose();
});
</script>
