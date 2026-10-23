<template>
  <div class="flashcards">
    <!-- ═══════════════ 顶部工具栏 ═══════════════ -->
    <div class="fc-toolbar">
      <div class="fc-toolbar-left">
        <el-radio-group v-model="studyMode" size="small" @change="restart">
          <el-radio-button value="all">全部单词</el-radio-button>
          <el-radio-button value="unknown">只看不熟的</el-radio-button>
        </el-radio-group>
      </div>
      <div class="fc-toolbar-right">
        <span v-if="offlineMode" class="offline-badge">离线模式（本地词库）</span>
        <span class="fc-known-count">当前词库共 {{ allWords.length }} 个单词</span>
      </div>
    </div>

    <!-- 背诵进度 -->
    <div class="fc-progress-bar2">
      <el-progress :percentage="progressPct" :stroke-width="14" :text-inside="true" style="flex: 1" />
      <span class="fc-known-count" style="white-space: nowrap">已掌握 {{ masteredCount }}/{{ allWords.length }}</span>
      <el-button size="small" @click="resetProgress">清零进度</el-button>
    </div>

    <!-- ═══════════════ 加载中 ═══════════════ -->
    <div v-if="loading" class="fc-loading">
      <div class="skeleton" style="width: 100%; max-width: 480px; height: 300px; border-radius: 20px"></div>
    </div>

    <!-- ═══════════════ 无单词 ═══════════════ -->
    <el-empty
      v-else-if="!deck.length"
      :description="studyMode === 'unknown' ? '太棒了，所有单词都已掌握！' : '暂无词汇，请等待老师添加'"
    >
      <el-button v-if="studyMode === 'unknown'" round type="primary" @click="studyMode = 'all'; restart()">
        复习全部单词
      </el-button>
    </el-empty>

    <!-- ═══════════════ 学习完成 ═══════════════ -->
    <div v-else-if="finished" class="fc-result">
      <h3>本轮学习完成</h3>
      <div class="fc-result-stats">
        <div class="fc-result-item">
          <div class="fc-result-num" style="color: var(--color-success)">{{ sessionKnown }}</div>
          <div class="fc-result-label">认识</div>
        </div>
        <div class="fc-result-item">
          <div class="fc-result-num" style="color: var(--color-danger)">{{ deck.length - sessionKnown }}</div>
          <div class="fc-result-label">不熟</div>
        </div>
      </div>
      <div class="fc-result-actions">
        <el-button type="primary" round size="large" @click="restart">再来一轮</el-button>
        <el-button
          v-if="deck.length - sessionKnown > 0"
          round
          size="large"
          @click="studyMode = 'unknown'; restart()"
        >
          只练不熟的
        </el-button>
      </div>
    </div>

    <!-- ═══════════════ 卡片学习区 ═══════════════ -->
    <div v-else class="fc-stage">
      <!-- 进度条 -->
      <div class="fc-progress">
        <div class="fc-progress-bar">
          <div class="fc-progress-inner" :style="{ width: roundPct + '%' }"></div>
        </div>
        <span class="fc-progress-text">{{ currentIndex + 1 }} / {{ deck.length }}</span>
      </div>

      <!-- 翻转卡片 -->
      <div class="fc-card-wrap" @click="onCardClick">
        <div class="fc-card-count">已掌握：{{ masteredCount }}/{{ allWords.length }}</div>
        <div class="fc-card" :class="{ flipped }">
          <!-- 正面：仅单词 + 发音按钮 + 提示 -->
          <div class="fc-face fc-front">
            <div class="fc-word">{{ currentWord.word }}</div>
            <el-button size="small" plain class="fc-speak" @click.stop="speak">发音</el-button>
            <div class="fc-hint">点击卡片查看释义</div>
          </div>
          <!-- 背面：单词 + 词性 + 读音 + 翻译 + 延伸词 -->
          <div class="fc-face fc-back">
            <div class="fc-word-small">
              {{ currentWord.word }}
              <button class="fc-speak-back" title="播放读音" @click.stop="speak">发音</button>
            </div>
            <div class="fc-back-meta">
              <span class="fc-pos-badge" v-if="currentWord.partOfSpeech">{{ currentWord.partOfSpeech }}</span>
              <span class="fc-phonetic" v-if="currentWord.phonetic">{{ currentWord.phonetic }}</span>
            </div>
            <div class="fc-translation">{{ currentWord.translation }}</div>
            <div class="fc-extensions" v-if="currentWord.extensions">{{ currentWord.extensions }}</div>
            <div class="fc-hint">点击卡片返回</div>
          </div>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div v-if="!answered" class="fc-actions">
        <button class="fc-btn fc-btn-unknown" @click="answer(false)">不熟</button>
        <button class="fc-btn fc-btn-known" @click="answer(true)">认识</button>
      </div>
      <div class="fc-actions">
        <button class="fc-btn fc-btn-nav" :disabled="currentIndex <= 0" @click="prevCard">← 上一个</button>
        <button class="fc-btn fc-btn-nav" :disabled="currentIndex >= deck.length - 1" @click="nextCard">下一个 →</button>
      </div>
      <div class="fc-tip">快捷键：空格翻面，← 不熟，→ 认识</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue';
import { vocabList, recordQuizError, markWordMastered, resetMastered, errorStats } from '../api';
import { saveVocabWords, getVocabWords } from '../utils/offlineVocab';
import { speakWord } from '../utils/speech';
import { useUserStore } from '../stores/user';
import { ElMessage, ElMessageBox } from 'element-plus';

const userStore = useUserStore();

const loading = ref(true);
const offlineMode = ref(false);
const allWords = ref([]);
const deck = ref([]);
const currentIndex = ref(0);
const flipped = ref(false);
const finished = ref(false);
const sessionKnown = ref(0);
const studyMode = ref('all'); // all | unknown
const answered = ref(false); // 已作答后翻卡展示释义，等待「下一个」

// 已掌握单词 id 集合 — 按用户隔离，存 localStorage
const storageKey = computed(() => `flashcards-known-${userStore.user?.id || 'anon'}`);
const knownIds = ref(new Set(JSON.parse(localStorage.getItem(storageKey.value) || '[]')));

const currentWord = computed(() => deck.value[currentIndex.value] || {});
const roundPct = computed(() =>
  deck.value.length ? Math.round(((currentIndex.value + 1) / deck.value.length) * 100) : 0
);
// 进度以后端「已掌握本」的数量为准
const masteredCount = ref(0);
const progressPct = computed(() =>
  allWords.value.length ? Math.round((masteredCount.value / allWords.value.length) * 100) : 0
);

function saveKnown() {
  localStorage.setItem(storageKey.value, JSON.stringify([...knownIds.value]));
}

function shuffle(arr) {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

function buildDeck() {
  let pool = allWords.value;
  if (studyMode.value === 'unknown') {
    pool = pool.filter((w) => !knownIds.value.has(w.id));
  }
  deck.value = shuffle(pool).slice(0, 50); // 每轮最多 50 词，避免疲劳
}

function restart() {
  currentIndex.value = 0;
  flipped.value = false;
  finished.value = false;
  sessionKnown.value = 0;
  answered.value = false;
  buildDeck();
}

function resetPerCard() {
  flipped.value = false;
  answered.value = false;
}

function answer(known) {
  const w = currentWord.value;
  if (!w.id || answered.value) return;
  if (known) {
    sessionKnown.value++;
    knownIds.value.add(w.id);
    // 认识 → 计入后端「已掌握本」，进度随之增加
    masteredCount.value++;
    markWordMastered(w.id).catch(() => {});
  } else {
    knownIds.value.delete(w.id);
    // 不熟 → 退回错题本（状态=不熟），进度相应减少
    masteredCount.value = Math.max(0, masteredCount.value - 1);
    recordQuizError(w.id, '', 'unfamiliar').catch(() => {});
  }
  saveKnown();
  // 认识/不熟都自动翻到背面看详细信息，等用户点「下一个」
  flipped.value = true;
  answered.value = true;
}

function prevCard() {
  if (currentIndex.value <= 0) return;
  currentIndex.value--;
  resetPerCard();
}

function nextCard() {
  if (currentIndex.value >= deck.value.length - 1) { finished.value = true; return; }
  currentIndex.value++;
  resetPerCard();
}

function onCardClick() {
  // 无论是否已作答，点击卡片都能翻转（点「不熟」后也能翻回去看）
  flipped.value = !flipped.value;
}

/** 清零该词库背诵进度：所有单词重新归入"不认识" */
async function resetProgress() {
  try {
    await ElMessageBox.confirm('确定清零该词库的背诵进度吗？所有单词将重新归入"不认识"。', '清零进度', { type: 'warning' });
  } catch { return; }
  try { await resetMastered(); } catch (e) { /* 忽略 */ }
  knownIds.value = new Set();
  masteredCount.value = 0;
  saveKnown();
  restart();
  ElMessage.success('进度已清零');
}

function speak() {
  if (currentWord.value.word) speakWord(currentWord.value.word);
}

// 每张卡片显示时自动朗读一次单词
watch(() => currentWord.value.word, (w) => {
  if (w && !finished.value) speakWord(w);
});
// 翻到背面时自动播报一次读音（翻到背面即可听到）
watch(flipped, (f) => {
  if (f && currentWord.value.word) speakWord(currentWord.value.word);
});

// 键盘快捷键（桌面端体验）
function onKeydown(e) {
  if (loading.value || finished.value || !deck.value.length) return;
  if (answered.value) {
    if (e.code === 'ArrowRight' || e.code === 'Enter' || e.code === 'Space') { e.preventDefault(); nextCard(); }
    else if (e.code === 'ArrowLeft') { e.preventDefault(); prevCard(); }
    return;
  }
  if (e.code === 'Space') { e.preventDefault(); flipped.value = !flipped.value; }
  else if (e.code === 'ArrowRight') answer(true);
  else if (e.code === 'ArrowLeft') answer(false);
}

async function loadWords() {
  loading.value = true;
  try {
    // 在线：分页拉取全量词库（接口单页上限 1000，避免只拿到 1000 个）
    const size = 1000;
    let page = 1, all = [], total = 0;
    while (page <= 100) {
      const res = await vocabList({ page, size });
      const rows = res?.records || [];
      all = all.concat(rows);
      total = res?.total || 0;
      if (!rows.length || all.length >= total) break;
      page++;
    }
    allWords.value = all;
    if (allWords.value.length) saveVocabWords(allWords.value).catch(() => {});
    offlineMode.value = false;
    knownIds.value = new Set(JSON.parse(localStorage.getItem(storageKey.value) || '[]'));
    // 进度以「已掌握本」为准
    try {
      const s = await errorStats();
      masteredCount.value = (s && s.masteredCount) || 0;
    } catch (e) { /* 忽略 */ }
  } catch (e) {
    // 离线/接口失败：读 IndexedDB 本地词库兜底
    allWords.value = await getVocabWords();
    offlineMode.value = allWords.value.length > 0;
  }
  restart();
  loading.value = false;
}

onMounted(() => {
  loadWords();
  window.addEventListener('keydown', onKeydown);
});
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown));
</script>

<style scoped>
.flashcards {
  max-width: 720px;
  margin: 0 auto;
}

/* ═══ 工具栏 ═══ */
.fc-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 18px;
}
.fc-toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.offline-badge {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 9999px;
  background: rgba(230, 162, 60, 0.12);
  color: var(--color-warning);
}
.fc-known-count {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.fc-progress-bar2 { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.fc-btn-nav {
  flex: 1;
  max-width: 200px;
  padding: 12px 0;
  font-size: 15px;
  font-weight: 600;
  border: none;
  border-radius: 9999px;
  cursor: pointer;
  background: #eef0f4;
  color: #4b5563;
}
.fc-btn-nav:disabled { opacity: 0.5; cursor: not-allowed; }

.fc-loading { display: flex; justify-content: center; padding: 30px 0; }

/* ═══ 进度 ═══ */
.fc-progress {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}
.fc-progress-bar {
  flex: 1;
  height: 8px;
  border-radius: 9999px;
  background: var(--color-bg-hover);
  overflow: hidden;
}
.fc-progress-inner {
  height: 100%;
  border-radius: 9999px;
  background: var(--gradient-primary);
  transition: width 0.3s ease;
}
.fc-progress-text {
  font-size: 13px;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

/* ═══ 翻转卡片（CSS 3D） ═══ */
.fc-card-wrap {
  perspective: 1200px;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  position: relative;
}
.fc-card-count {
  position: absolute;
  top: 12px;
  right: 14px;
  z-index: 5;
  font-size: 13px;
  font-weight: 600;
  color: #1a4034;
  background: rgba(255, 255, 255, 0.55);
  border-radius: 9999px;
  padding: 3px 12px;
}
.fc-card {
  position: relative;
  width: 100%;
  height: 320px;
  transform-style: preserve-3d;
  transition: transform 0.55s cubic-bezier(0.4, 0, 0.2, 1);
}
.fc-card.flipped { transform: rotateY(180deg); }
.fc-face {
  position: absolute;
  inset: 0;
  backface-visibility: hidden;
  -webkit-backface-visibility: hidden;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  border-radius: 22px;
  padding: 24px;
}
/* 卡片正面 / 背面的装饰图案（微弱网点 + 高光） */
.fc-front,
.fc-back {
  overflow: hidden;
}
.fc-front::before,
.fc-back::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
  
  background-size: 20px 20px;
  opacity: 0.6;
}
.fc-front::after,
.fc-back::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 1;
  
  pointer-events: none;
}
.fc-face > * { position: relative; z-index: 2; }
.fc-front {
  background: var(--accent-soft);
  color: var(--text);
}
.fc-back {
  background: var(--gradient-homework);
  color: #fff;
  transform: rotateY(180deg);
}
.fc-pos {
  font-size: 13px;
  padding: 2px 12px;
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.4);
}
.fc-word {
  font-size: 44px;
  font-weight: 800;
  letter-spacing: 1px;
  text-align: center;
  word-break: break-word;
}
.fc-word-small { font-size: 20px; font-weight: 600; opacity: 0.85; }
.fc-speak-back {
  border: none; cursor: pointer; margin-left: 8px;
  background: rgba(255, 255, 255, 0.25); color: #fff;
  border-radius: 9999px; font-size: 15px; padding: 3px 12px;
}
.fc-speak-back:active { transform: scale(0.94); }
.fc-back-meta { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; justify-content: center; }
.fc-pos-badge { font-size: 13px; padding: 2px 12px; border-radius: 9999px; background: rgba(255, 255, 255, 0.25); }
.fc-phonetic { font-size: 16px; opacity: 0.9; letter-spacing: 0.5px; }
.fc-translation {
  font-size: 26px;
  font-weight: 700;
  text-align: center;
  line-height: 1.4;
}
.fc-extensions {
  font-size: 14px;
  opacity: 0.85;
  text-align: center;
  max-height: 80px;
  overflow-y: auto;
  line-height: 1.6;
}
.fc-speak { font-size: 13px; }
.fc-hint {
  position: absolute;
  bottom: 14px;
  font-size: 12px;
  opacity: 0.6;
}

/* ═══ 操作按钮 ═══ */
.fc-actions {
  display: flex;
  gap: 14px;
  justify-content: center;
  margin-top: 22px;
}
.fc-btn {
  flex: 1;
  max-width: 200px;
  padding: 14px 0;
  font-size: 16px;
  font-weight: 600;
  border: none;
  border-radius: 9999px;
  cursor: pointer;
  transition: background-color 180ms ease, transform 140ms ease;
  color: #fff;
}
.fc-btn:active { transform: scale(0.98); }
.fc-btn-known { background: var(--positive); }
.fc-btn-next { background: var(--accent); }
.fc-btn-unknown { background: var(--negative); }
.fc-tip {
  text-align: center;
  margin-top: 14px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

/* ═══ 结果页 ═══ */
.fc-result {
  text-align: center;
  padding: 40px 20px;
  border-radius: var(--radius-lg);
  background: var(--surface);
  border: 1px solid var(--border);
}
.fc-result-emoji { font-size: 60px; margin-bottom: 8px; }
.fc-result h3 { margin: 0 0 20px; color: var(--color-text-primary); }
.fc-result-stats {
  display: flex;
  justify-content: center;
  gap: 50px;
  margin-bottom: 26px;
}
.fc-result-num { font-size: 36px; font-weight: 800; }
.fc-result-label { font-size: 13px; color: var(--color-text-secondary); }
.fc-result-actions { display: flex; gap: 10px; justify-content: center; flex-wrap: wrap; }

/* ═══ 移动端 ═══ */
@media (max-width: 768px) {
  .fc-card { height: 280px; }
  .fc-word { font-size: 34px; }
  .fc-translation { font-size: 22px; }
  .fc-tip { display: none; }
}
</style>
