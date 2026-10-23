<template>
  <div v-loading="loading">
    <el-button @click="$router.back()" style="margin-bottom:16px">← 返回</el-button>

    <el-card v-if="resource">
      <template #header>
        <h1 style="margin:0">{{ resource.title }}</h1>
        <el-tag style="margin-top:8px">{{ typeLabel(resource.type) }}</el-tag>
        <el-tag v-if="resource.teacherName" type="info" style="margin-top:8px;margin-left:8px">
          发布教师：{{ resource.teacherName }}
        </el-tag>
      </template>

      <div v-if="resource.content" style="white-space:pre-wrap;font-size:15px;line-height:1.8;margin-bottom:20px">
        {{ resource.content }}
      </div>

      <!-- 图片展示（支持点击放大） -->
      <div v-if="images.length" class="res-images">
        <el-image
          v-for="(url, i) in images"
          :key="i"
          :src="fileUrl(url)"
          class="res-img"
          fit="contain"
          :preview-src-list="images.map(fileUrl)"
          :initial-index="i"
          preview-teleported
          hide-on-click-modal
        />
      </div>

      <!-- Word 文档列表：在线预览 + 下载 -->
      <div v-if="wordDocs.length" style="margin-top:16px">
        <h4 style="margin:0 0 8px 0">文档</h4>
        <div
          v-for="doc in wordDocs"
          :key="doc.index"
          class="doc-row"
        >
          <span class="doc-icon"></span>
          <span class="doc-name">{{ fileNameFromUrl(doc.url) }}</span>
          <el-button size="small" type="primary" plain :disabled="!isDocx(doc.url)" @click="openDocPreview(doc)">
            在线预览
          </el-button>
          <el-button size="small" type="success" plain @click="onDownload(doc)">下载</el-button>
        </div>
        <p v-if="wordDocs.some(d => !isDocx(d.url))" style="color:var(--muted);font-size:12px;margin:4px 0 0">
          提示：仅 .docx 支持在线预览，其余（.doc / .pdf / .txt 等）请下载查看
        </p>
      </div>

      <el-empty v-if="!resource.content && !images.length && !wordDocs.length" description="暂无内容" />
    </el-card>

    <el-empty v-else-if="!loading" description="资料不存在" />

    <!-- Word 文档在线预览弹窗（全屏） -->
    <el-dialog v-model="docPreviewVisible" :title="'' + docPreviewName" fullscreen destroy-on-close>
      <div ref="docContainer" class="doc-preview-container" v-loading="docPreviewLoading" />
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { getResource, resourceFileBlob } from '../api';
import { isImageUrl, isDocx, fileNameFromUrl, renderDocxBuffer } from '../utils/docFile';
import { ElMessage } from 'element-plus';

const route = useRoute();
const apiBase = import.meta.env.VITE_API_BASE_URL || '';
const fileUrl = (url) => (url && url.startsWith('http')) ? url : (apiBase + url);
const resource = ref(null);
const loading = ref(false);

// fileUrl 为逗号分隔的多文件：拆成图片 和 Word 文档两组
const allFiles = computed(() => {
  if (!resource.value?.fileUrl) return [];
  return resource.value.fileUrl.split(',').filter(Boolean);
});
const images = computed(() => allFiles.value.filter(u => isImageUrl(u)));
// 非图片文件（Word/PDF/TXT/PPT/Excel 等）带上在 fileUrl 中的下标，供后端代理接口定位文件
const wordDocs = computed(() =>
  allFiles.value.map((url, index) => ({ url, index })).filter(d => !isImageUrl(d.url)));

function typeLabel(t) {
  const m = { grammar: '语法', reading: '阅读', listening: '听力', writing: '写作' };
  return m[t] || t;
}

/** 下载：走后端代理拿 Blob 后触发保存（同源无跨域问题，文件名用资料标题） */
async function onDownload(doc) {
  try {
    const resp = await resourceFileBlob(resource.value.id, doc.index, 'download');
    const ext = doc.url.includes('.') ? doc.url.substring(doc.url.lastIndexOf('.')) : '';
    const objectUrl = URL.createObjectURL(resp.data);
    const a = document.createElement('a');
    a.href = objectUrl;
    a.download = (resource.value.title || fileNameFromUrl(doc.url)) + ext;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(objectUrl);
  } catch (e) { /* 拦截器已提示 */ }
}

// --- Word 在线预览 ---
const docPreviewVisible = ref(false);
const docPreviewLoading = ref(false);
const docPreviewName = ref('');
const docContainer = ref(null);

/** Word 页面原始宽度（A4 约 794px），首次渲染后缓存，避免 zoom 后读到放大值 */
let docPageBaseWidth = 0;

/** 按容器宽度自适应放大 Word 页面：A4 原宽在宽屏上只占一半，放大铺满整屏 */
function fitDocZoom() {
  const container = docContainer.value;
  if (!container) return;
  if (!docPageBaseWidth) {
    const page = container.querySelector('.docx-wrapper > section.docx');
    if (!page) return;
    docPageBaseWidth = page.offsetWidth || 1;
  }
  const avail = container.clientWidth - 24; // 减去容器左右 padding
  const z = avail > docPageBaseWidth ? avail / docPageBaseWidth : 1;
  container.style.setProperty('--doc-zoom', String(z));
}

const onDocResize = () => fitDocZoom();
// 弹窗打开期间监听窗口尺寸变化，实时重新适配
watch(docPreviewVisible, (v) => {
  if (v) window.addEventListener('resize', onDocResize);
  else window.removeEventListener('resize', onDocResize);
});
onBeforeUnmount(() => window.removeEventListener('resize', onDocResize));

async function openDocPreview(doc) {
  docPreviewName.value = fileNameFromUrl(doc.url);
  docPreviewVisible.value = true;
  docPreviewLoading.value = true;
  docPageBaseWidth = 0; // 新文档重新测量原始宽度
  await nextTick(); // 等弹窗内容挂载完成再渲染
  try {
    // 走后端代理接口拿文件流（同源），规避浏览器直连 OSS 的跨域限制
    const resp = await resourceFileBlob(resource.value.id, doc.index, 'preview');
    const buffer = await resp.data.arrayBuffer();
    await renderDocxBuffer(buffer, docContainer.value);
    fitDocZoom(); // 渲染完成后按容器宽度放大铺满
  } catch (e) {
    ElMessage.error('在线预览失败：请确认文件服务已启动且文件未被删除');
  } finally {
    docPreviewLoading.value = false;
  }
}

onMounted(async () => {
  loading.value = true;
  try { resource.value = await getResource(route.params.id); }
  finally { loading.value = false; }
});
</script>

<style scoped>
.doc-preview-container {
  /* 全屏弹窗：预留标题栏与内边距，内容区占满剩余高度 */
  height: calc(100vh - 110px);
  overflow: auto;
  background: var(--raised);
  padding: 12px;
  min-height: 200px;
}
.doc-preview-container :deep(.docx-wrapper) {
  background: transparent;
  padding: 0;
  /* 按容器宽度自适应放大（fitDocZoom 动态写入），铺满全屏弹窗 */
  zoom: var(--doc-zoom, 1);
}
.doc-preview-container :deep(.docx-wrapper > section.docx) {
  margin: 0 auto 12px;
}
/* ─── 资料图片 / Word 文档行（窄屏自适应） ─── */
.res-images {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.res-img {
  max-width: 280px;
  max-height: 280px;
  border-radius: 8px;
  border: 1px solid var(--border);
  cursor: pointer;
}
.doc-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
  margin-bottom: 8px;
}
.doc-icon {
  font-size: 24px;
  flex-shrink: 0;
}
.doc-name {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  color: var(--text);
  word-break: break-all;
}

@media (max-width: 768px) {
  .res-img { max-width: 100%; }
  .doc-row { flex-wrap: wrap; }
  .doc-row .el-button { flex-shrink: 0; }
}
</style>
