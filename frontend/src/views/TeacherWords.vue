<template>
  <div class="teacher-words">
    <!-- ═══════════════ 顶部：词库 + 模式 ═══════════════ -->
    <div class="tw-header">
      <h1>背单词</h1>
      <p class="tw-sub">选择词库后，可进行记忆卡片、看词选意与错题三本复习</p>
    </div>

    <div class="tw-toolbar">
      <div class="tw-bank">
        <span class="tw-label">词库</span>
        <el-select v-model="bank" style="width: 160px" @change="onBankChange">
          <el-option v-for="b in banks" :key="b" :label="b" :value="b" />
        </el-select>
      </div>
      <el-radio-group v-model="mode" @change="onModeChange">
        <el-radio-button value="cards">记忆卡片</el-radio-button>
        <el-radio-button value="quiz">看词选意</el-radio-button>
        <el-radio-button value="errors">错题本</el-radio-button>
      </el-radio-group>
    </div>

    <!-- ═══════════════ 记忆卡片 ═══════════════ -->
    <div v-if="mode === 'cards'" class="tw-panel">
      <div class="tw-row-between">
        <el-radio-group v-model="studyMode" size="small" @change="restartCards">
          <el-radio-button value="all">全部单词</el-radio-button>
          <el-radio-button value="unknown">只看不熟的</el-radio-button>
        </el-radio-group>
        <span class="tw-muted">当前词库共 {{ allWords.length }} 个单词</span>
      </div>

      <!-- 背诵进度 -->
      <div class="tw-progress-bar2">
        <el-progress :percentage="progressPct" :stroke-width="16" :text-inside="true" style="flex: 1" />
        <span class="tw-muted" style="white-space: nowrap">已掌握 {{ masteredCount }}/{{ allWords.length }}</span>
        <el-button size="small" @click="resetProgress">清零进度</el-button>
      </div>

      <div v-if="cardsLoading" class="tw-loading"><el-skeleton :rows="5" animated /></div>

      <el-empty
        v-else-if="!deck.length"
        :description="studyMode === 'unknown' ? '太棒了，该词库的单词都已掌握！' : '该词库暂无词汇'"
      >
        <el-button v-if="studyMode === 'unknown'" round type="primary" @click="studyMode = 'all'; restartCards()">复习全部单词</el-button>
      </el-empty>

      <div v-else-if="finished" class="tw-result">
        <h3>本轮学习完成！</h3>
        <div class="tw-result-stats">
          <div><div class="tw-result-num" style="color: var(--color-success)">{{ sessionKnown }}</div><div class="tw-muted">认识</div></div>
          <div><div class="tw-result-num" style="color: var(--color-danger)">{{ deck.length - sessionKnown }}</div><div class="tw-muted">不熟（已进错题本）</div></div>
        </div>
        <div class="tw-actions">
          <el-button type="primary" round @click="restartCards">再来一轮</el-button>
          <el-button v-if="deck.length - sessionKnown > 0" round @click="studyMode = 'unknown'; restartCards()">只练不熟的</el-button>
        </div>
      </div>

      <div v-else class="tw-stage">
        <div class="tw-progress">
          <div class="tw-progress-bar"><div class="tw-progress-inner" :style="{ width: cardsProgress + '%' }"></div></div>
          <span class="tw-muted">{{ currentIndex + 1 }} / {{ deck.length }}</span>
        </div>

        <div class="tw-card-wrap" @click="onCardClick">
          <div class="tw-card-count">已掌握：{{ masteredCount }}/{{ allWords.length }}</div>
          <div class="tw-card" :class="{ flipped }">
            <!-- 正面：仅单词 + 发音按钮 + 提示 -->
            <div class="tw-face tw-front">
              <div class="tw-word">{{ currentWord.word }}</div>
              <el-button size="small" plain class="tw-speak" @click.stop="speakCard">发音</el-button>
              <div class="tw-hint">点击卡片查看释义</div>
            </div>
            <!-- 背面：单词 + 词性 + 读音 + 翻译 + 延伸词 -->
            <div class="tw-face tw-back">
              <div class="tw-word-small">
                {{ currentWord.word }}
                <button class="tw-speak-back" title="播放读音" @click.stop="speakCard">发音</button>
              </div>
              <div class="tw-back-meta">
                <span class="tw-pos-badge" v-if="currentWord.partOfSpeech">{{ currentWord.partOfSpeech }}</span>
                <span class="tw-phonetic" v-if="currentWord.phonetic">{{ currentWord.phonetic }}</span>
              </div>
              <div class="tw-translation">{{ currentWord.translation }}</div>
              <div class="tw-extensions" v-if="currentWord.extensions">{{ currentWord.extensions }}</div>
              <div class="tw-hint">点击卡片返回</div>
            </div>
          </div>
        </div>

        <div v-if="!answered" class="tw-actions">
          <el-button class="tw-btn-unknown" round @click="answerCard(false)">不熟</el-button>
          <el-button class="tw-btn-known" round @click="answerCard(true)">认识</el-button>
        </div>
        <div class="tw-actions">
          <el-button round :disabled="currentIndex <= 0" @click="prevCard">上一个</el-button>
          <el-button round type="primary" :disabled="currentIndex >= deck.length - 1" @click="nextCard">下一个</el-button>
        </div>
        <div class="tw-tip">点「认识 / 不熟」会翻转看释义，再点「下一个」继续； 切换上一个/下一个</div>
      </div>
    </div>

    <!-- ═══════════════ 看词选意 ═══════════════ -->
    <div v-else-if="mode === 'quiz'" class="tw-panel">
      <div v-if="quizStage === 'idle'" class="tw-quiz-setup">
        <p class="tw-muted">自定义本轮要背诵/检测的单词数量（词库：{{ bank }}）</p>
        <div class="tw-counts">
          <el-button v-for="n in [5, 10, 15, 20, 30, 50]" :key="n" round
            :type="quizCount === n ? 'primary' : ''" @click="quizCount = n">{{ n }} 词</el-button>
        </div>
        <div class="tw-custom-count">
          <span class="tw-muted">自定义：</span>
          <el-input-number v-model="quizCount" :min="1" :max="200" size="large" />
          <span class="tw-muted">个单词</span>
        </div>
        <el-button type="primary" size="large" round :loading="quizLoading" @click="startQuizFlow" style="margin-top: 18px">开始</el-button>
      </div>

      <div v-else-if="quizStage === 'phase1' && currentQuiz" class="tw-quiz">
        <div class="tw-quiz-top">
          <el-tag type="warning">阶段一，看词选义</el-tag>
          <span class="tw-muted">{{ p1Index + 1 }} / {{ words.length }}</span>
        </div>
        <div class="tw-quiz-word">
          <span>{{ currentQuiz.word }}</span>
          <el-button size="small" plain @click="speakText(currentQuiz.word)">发音</el-button>
          <span class="tw-pos-badge" v-if="currentQuiz.partOfSpeech">{{ currentQuiz.partOfSpeech }}</span>
        </div>
        <div class="tw-ask">请选择以上单词的中文意思：</div>
        <div class="tw-options">
          <button v-for="(opt, i) in currentQuiz.options" :key="opt" class="tw-option"
            :class="p1OptionClass(opt)" :disabled="p1Locked" @click="pickP1(opt)">
            <span class="tw-option-key">{{ String.fromCharCode(65 + i) }}</span>
            <span>{{ opt }}</span>
          </button>
        </div>

        <div v-if="p1Feedback" class="tw-feedback" :class="p1Feedback.correct ? 'ok' : 'bad'">
          <div>{{ p1Feedback.message }}</div>
          <div v-if="p1Feedback.correct && p1Feedback.extensions" class="tw-ext-row">
            <span v-for="(e, i) in splitExt(p1Feedback.extensions)" :key="i" class="tw-ext-tag">{{ e }}</span>
          </div>
        </div>

        <div v-if="p1Feedback && p1Feedback.showDetails" class="tw-detail-panel">
          <div class="tw-detail-title">全部选项解析</div>
          <div v-for="(d, i) in currentQuiz.optionDetails" :key="i" class="tw-detail-item" :class="{ on: d.optionText === currentQuiz.translation }">
            <span class="tw-detail-num">{{ ['A', 'B', 'C', 'D'][i] }}</span>
            <span class="tw-detail-text">{{ d.optionText }}</span>
            <span class="tw-detail-arrow"></span>
            <span class="tw-detail-word">{{ d.sourceWord }}</span>
            <span v-if="d.partOfSpeech && d.partOfSpeech !== '—'" class="tw-detail-pos">{{ d.partOfSpeech }}</span>
          </div>
        </div>

        <div v-if="p1Feedback && p1Feedback.correct" class="tw-actions">
          <el-button type="warning" round :disabled="flaggedIds.includes(currentQuiz.id)" @click="flagUnfamiliarWord(currentQuiz.id)">
            {{ flaggedIds.includes(currentQuiz.id) ? '已加入错题本' : '不熟，加入错题本' }}
          </el-button>
        </div>
        <div class="tw-actions">
          <el-button v-if="!p1Feedback || !p1Feedback.showDetails" type="primary" round :disabled="!p1Answer" @click="confirmP1">确认</el-button>
          <el-button v-else type="primary" round @click="nextP1">{{ p1Index + 1 >= words.length ? '进入阶段二' : '下一题' }}</el-button>
        </div>
      </div>

      <div v-else-if="quizStage === 'phase2' && currentSpell" class="tw-quiz">
        <div class="tw-quiz-top">
          <el-tag type="success">阶段二，看义写词</el-tag>
          <span class="tw-muted">{{ p2Index + 1 }} / {{ p2Words.length }}</span>
        </div>
        <div class="tw-ask">请根据意思写出对应的英文单词：</div>
        <div class="tw-quiz-word">{{ currentSpell.translation }}</div>
        <span class="tw-pos-hint" v-if="currentSpell.partOfSpeech">词性提示：{{ currentSpell.partOfSpeech }}</span>
        <el-input v-model="p2Input" size="large" placeholder="请输入英文单词" class="tw-spell-input"
          @keyup.enter="confirmP2" :disabled="p2Locked" />
        <div v-if="p2Feedback" class="tw-feedback" :class="p2Feedback.correct ? 'ok' : 'bad'">
          <div style="white-space: pre-wrap">{{ p2Feedback.message }}</div>
          <div v-if="p2Feedback.correct && p2Feedback.extensions" class="tw-ext-row">
            <span v-for="(e, i) in splitExt(p2Feedback.extensions)" :key="i" class="tw-ext-tag">{{ e }}</span>
          </div>
        </div>
        <div class="tw-actions">
          <el-button v-if="!p2Feedback || !p2Feedback.showDetails" type="primary" round :disabled="!p2Input.trim()" @click="confirmP2">确认</el-button>
          <el-button v-else type="primary" round @click="nextP2">{{ p2Index + 1 >= p2Words.length ? '查看报告' : '下一题' }}</el-button>
        </div>
      </div>

      <div v-else-if="quizStage === 'report'" class="tw-report">
        <div class="tw-result">
          <h3>本轮测验完成</h3>
          <div class="tw-result-stats">
            <div><div class="tw-result-num" style="color: var(--color-success)">{{ quizMastered }}</div><div class="tw-muted">已掌握（两阶段通过）</div></div>
            <div><div class="tw-result-num" style="color: var(--color-success)">{{ p2Correct }}</div><div class="tw-muted">拼写正确</div></div>
            <div><div class="tw-result-num" style="color: var(--color-danger)">{{ p1Errors.length }}</div><div class="tw-muted">选义错题</div></div>
            <div><div class="tw-result-num" style="color: var(--color-danger)">{{ p2Errors.length }}</div><div class="tw-muted">拼写错题</div></div>
          </div>
          <div class="tw-actions">
            <el-button type="primary" round @click="startQuizFlow">再来一轮</el-button>
            <el-button round @click="mode = 'errors'; onModeChange()">去错题本复习</el-button>
          </div>
        </div>

        <div v-if="p1Errors.length" class="tw-panel" style="margin-top: 16px">
          <div class="tw-detail-title">阶段一错题（看词选义）</div>
          <div v-for="(e, i) in p1Errors" :key="'e1' + i" class="tw-report-item">
            <div class="tw-report-head"><b>{{ i + 1 }}. {{ e.word }}</b><el-tag size="small" type="danger">选错</el-tag></div>
            <div class="tw-report-line">你的选择：<span class="tw-bad">{{ e.wrongAnswer }}</span></div>
            <div class="tw-report-line">正确答案：<span class="tw-ok">{{ e.correctAnswer }}</span></div>
          </div>
        </div>

        <div v-if="p2Errors.length" class="tw-panel" style="margin-top: 16px">
          <div class="tw-detail-title">阶段二错题（看义写词）</div>
          <div v-for="(e, i) in p2Errors" :key="'e2' + i" class="tw-report-item">
            <div class="tw-report-head"><b>{{ i + 1 }}. {{ e.translation }}</b><el-tag size="small" type="danger">拼错</el-tag></div>
            <div class="tw-report-line">你写的：<span class="tw-bad">{{ e.wrongAnswer }}</span></div>
            <div class="tw-report-line">正确答案：<span class="tw-ok">{{ e.correctAnswer }}</span></div>
          </div>
        </div>
      </div>

      <div v-else class="tw-loading"><el-skeleton :rows="4" animated /></div>
    </div>

    <!-- ═══════════════ 错题三本 ═══════════════ -->
    <div v-else class="tw-panel">
      <div v-if="errReviewStage === 'list'">
        <div class="tw-row-between">
          <el-radio-group v-model="bookTab" @change="onBookChange">
            <el-radio-button value="error">错题本 {{ bookCounts.error }}</el-radio-button>
            <el-radio-button value="review">复习本 {{ bookCounts.review }}</el-radio-button>
            <el-radio-button value="mastered">已掌握本 {{ bookCounts.mastered }}</el-radio-button>
          </el-radio-group>
          <el-button v-if="bookTab === 'error'" type="primary" :disabled="!bookCounts.error" @click="beginErrReview">开始错题复习</el-button>
        </div>

        <!-- 已掌握本：单词 + 状态 + 忘了（与错题本一样分页展示） -->
        <el-table v-if="bookTab === 'mastered'" :data="errors" v-loading="errLoading" empty-text="暂无已掌握的单词" style="margin-top: 12px">
          <el-table-column prop="word" label="单词" width="150">
            <template #default="{ row }">
              <el-button type="primary" link title="查看详细信息" @click="showDetail(row)">{{ row.word }}</el-button>
              <el-button type="primary" link @click="speakText(row.word)"></el-button>
            </template>
          </el-table-column>
          <el-table-column prop="translation" label="意思" show-overflow-tooltip />
          <el-table-column label="状态" width="90">
            <template #default><el-tag size="small" type="success">已掌握</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button size="small" type="warning" @click="doForget(row.id)">忘了</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 错题本 / 复习本 -->
        <el-table v-else :data="errors" v-loading="errLoading" :empty-text="bookTab === 'error' ? '暂无错题，继续保持！' : '暂无待复习单词'" style="margin-top: 12px">
          <el-table-column prop="word" label="单词" width="150">
            <template #default="{ row }">
              <el-button type="primary" link title="查看详细信息" @click="showDetail(row)">{{ row.word }}</el-button>
              <el-button type="primary" link @click="speakText(row.word)"></el-button>
            </template>
          </el-table-column>
          <el-table-column prop="translation" label="意思" show-overflow-tooltip />
          <el-table-column label="错误类型" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.errorType === 'spelling' ? 'warning' : (row.errorType === 'unfamiliar' ? 'info' : 'danger')">
                {{ row.errorType === 'spelling' ? '拼写' : (row.errorType === 'unfamiliar' ? '不熟' : '选义') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="errorCount" label="错误次数" width="90" />
          <el-table-column label="操作" width="190">
            <template #default="{ row }">
              <el-button v-if="bookTab === 'error'" size="small" type="success" @click="doMastered(row.id)">已掌握</el-button>
              <el-button v-else size="small" type="success" @click="doReviewMastered(row.id)">再次掌握</el-button>
              <el-button size="small" type="danger" @click="doDelete(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination v-if="errTotal > errSize" v-model:current-page="errPage" :total="errTotal"
          :page-size="errSize" layout="prev,pager,next" style="margin-top: 12px; justify-content: center" @current-change="loadBooks()" />
      </div>

      <!-- 错题复习进行中（3 次机会，与学生端一致） -->
      <div v-else class="tw-quiz">
        <div class="tw-quiz-top">
          <el-tag type="danger">错题复习</el-tag>
          <span class="tw-muted">{{ errReviewIndex + 1 }} / {{ errReviewWords.length }}</span>
        </div>
        <template v-if="currentErrWord">
          <div class="tw-quiz-word">
            <span>{{ currentErrWord.word }}</span>
            <el-button size="small" plain @click="speakText(currentErrWord.word)">发音</el-button>
            <span class="tw-pos-badge" v-if="currentErrWord.partOfSpeech">{{ currentErrWord.partOfSpeech }}</span>
          </div>
          <div class="tw-ask">请选择以上单词的中文意思：</div>
          <div class="tw-options">
            <button v-for="(opt, i) in currentErrWord.options" :key="opt" class="tw-option"
              :class="errOptionClass(opt)" :disabled="errLocked" @click="pickErr(opt)">
              <span class="tw-option-key">{{ String.fromCharCode(65 + i) }}</span>
              <span>{{ opt }}</span>
            </button>
          </div>
          <div v-if="errFeedback" class="tw-feedback" :class="errFeedback.correct ? 'ok' : 'bad'">{{ errFeedback.message }}</div>
          <div class="tw-actions">
            <el-button v-if="!errFeedback || !errFeedback.showDetails" type="primary" round :disabled="!errAnswer" @click="confirmErr">确认</el-button>
            <el-button v-else type="primary" round @click="nextErr">{{ errReviewIndex + 1 >= errReviewWords.length ? '查看结果' : '下一题' }}</el-button>
          </div>
        </template>
        <div v-else class="tw-result">
          <h3>错题复习完成</h3>
          <div class="tw-actions">
            <el-button type="primary" round @click="errReviewStage = 'list'; loadBooks()">返回错题本</el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 单词详细信息弹窗（错题本/复习本/已掌握本点击单词卡片） -->
    <el-dialog v-model="dialogDetail" :title="'' + (detailRow?.word || '')" width="460px">
      <el-descriptions v-if="detailRow" :column="1" border>
        <el-descriptions-item label="单词">
          <strong>{{ detailRow.word }}</strong>
          <el-button type="primary" link @click="speakText(detailRow.word)"></el-button>
        </el-descriptions-item>
        <el-descriptions-item label="读音">{{ detailRow.phonetic || '-' }}</el-descriptions-item>
        <el-descriptions-item label="意思">{{ detailRow.translation || '-' }}</el-descriptions-item>
        <el-descriptions-item label="词性">{{ detailRow.partOfSpeech || '-' }}</el-descriptions-item>
        <el-descriptions-item label="延伸词">
          <el-tag v-for="(ext, i) in (detailRow.extensions || '').split(',').filter(Boolean)" :key="i" size="small" round style="margin: 2px 4px 2px 0">{{ ext }}</el-tag>
          <span v-if="!detailRow.extensions">-</span>
        </el-descriptions-item>
        <el-descriptions-item label="错误类型">{{ errorTypeText(detailRow.errorType) }}</el-descriptions-item>
        <el-descriptions-item label="你的作答">{{ detailRow.wrongAnswer || '-' }}</el-descriptions-item>
        <el-descriptions-item label="正确答案">{{ detailRow.correctAnswer || '-' }}</el-descriptions-item>
        <el-descriptions-item label="错误次数">{{ detailRow.errorCount || 1 }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue';
import {
  vocabBanks, vocabList, startQuiz, getSpellWords, checkSpelling, recordQuizError,
  getQuizErrors, reviewErrors, markErrorMastered, reviewMastered, forgetError, deleteQuizError, errorStats,
  markWordMastered, resetMastered
} from '../api';
import { speakWord } from '../utils/speech';
import { useUserStore } from '../stores/user';
import { ElMessage, ElMessageBox } from 'element-plus';

const userStore = useUserStore();

const banks = ref(['学生词库', '四级词库', '六级词库']);
const bank = ref('学生词库');
const mode = ref('cards');

// ═══════════ 记忆卡片 ═══════════
const cardsLoading = ref(false);
const allWords = ref([]);
const deck = ref([]);
const currentIndex = ref(0);
const flipped = ref(false);
const finished = ref(false);
const sessionKnown = ref(0);
const studyMode = ref('all');
const answered = ref(false);
// 后端「已掌握本」中该词库的单词数（记忆卡片进度以它为准）
const masteredCount = ref(0);

const progressPct = computed(() =>
  allWords.value.length ? Math.round((masteredCount.value / allWords.value.length) * 100) : 0
);

const knownKey = computed(() => `teacher-cards-known-${userStore.user?.id || 'anon'}-${bank.value}`);
const knownIds = ref(new Set());

const currentWord = computed(() => deck.value[currentIndex.value] || {});
const cardsProgress = computed(() =>
  deck.value.length ? Math.round(((currentIndex.value + 1) / deck.value.length) * 100) : 0
);

function shuffle(arr) {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}
function saveKnown() { localStorage.setItem(knownKey.value, JSON.stringify([...knownIds.value])); }
function buildDeck() {
  let pool = allWords.value;
  if (studyMode.value === 'unknown') pool = pool.filter((w) => !knownIds.value.has(w.id));
  deck.value = shuffle(pool).slice(0, 50);
}
function restartCards() {
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
function answerCard(known) {
  const w = currentWord.value;
  if (!w.id || answered.value) return;
  if (known) {
    sessionKnown.value++;
    knownIds.value.add(w.id);
    // 认识 计入后端「已掌握本」，记忆卡片进度随之增加
    masteredCount.value++;
    markWordMastered(w.id).catch(() => {});
  } else {
    knownIds.value.delete(w.id);
    // 不熟 退回错题本（状态=不熟），进度相应减少
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
function speakCard() { if (currentWord.value.word) speakWord(currentWord.value.word); }
function speakText(text) { if (text) speakWord(text); }

// 每张卡片显示时自动朗读一次
watch(() => currentWord.value.word, (w) => {
  if (w && !finished.value && mode.value === 'cards') speakWord(w);
});
// 翻到背面时自动播报一次读音
watch(flipped, (f) => {
  if (f && currentWord.value.word && mode.value === 'cards') speakWord(currentWord.value.word);
});

/** 分页拉取该词库全部单词（接口单页上限 1000，避免"总词数只显示 1000"） */
async function loadAllWords(bankName) {
  const size = 1000;
  let page = 1, all = [], total = 0;
  while (page <= 100) {
    const r = await vocabList({ page, size, bank: bankName });
    const rows = r?.records || [];
    all = all.concat(rows);
    total = r?.total || 0;
    if (!rows.length || all.length >= total) break;
    page++;
  }
  return { words: all, total };
}

async function loadCards() {
  cardsLoading.value = true;
  try {
    const { words } = await loadAllWords(bank.value);
    allWords.value = words;
    knownIds.value = new Set(JSON.parse(localStorage.getItem(knownKey.value) || '[]'));
    // 进度以「已掌握本」为准：拉取该词库已掌握数量
    try {
      const s = await errorStats(bank.value);
      masteredCount.value = (s && s.masteredCount) || 0;
    } catch (e) { /* 忽略 */ }
  } catch (e) {
    allWords.value = [];
    knownIds.value = new Set();
  }
  restartCards();
  cardsLoading.value = false;
}

/** 清零该词库的背诵进度：已掌握单词全部退回错题本（状态=不熟） */
async function resetProgress() {
  try {
    await ElMessageBox.confirm('确定清零该词库的背诵进度吗？所有单词将重新归入"不认识"。', '清零进度', { type: 'warning' });
  } catch { return; }
  try { await resetMastered(bank.value); } catch (e) { /* 忽略 */ }
  knownIds.value = new Set();
  masteredCount.value = 0;
  saveKnown();
  restartCards();
  ElMessage.success('进度已清零');
}

// ═══════════ 随机测验（与学生端一致：3 次机会 + 选项解析 + 两阶段） ═══════════
const quizStage = ref('idle'); // idle | phase1 | phase2 | report
const quizCount = ref(10);
const quizLoading = ref(false);
const words = ref([]);
const p1Index = ref(0);
const p1Answer = ref('');
const p1Feedback = ref(null);
const p1Attempt = ref(0);
const p1Errors = ref([]);
const p2Words = ref([]);
const p2Index = ref(0);
const p2Input = ref('');
const p2Feedback = ref(null);
const p2Attempt = ref(0);
const p2Errors = ref([]);
const p2Correct = ref(0);
const p1Passed = ref([]);      // 阶段一答对过的单词 id
const p2Passed = ref([]);      // 阶段二拼写通过过的单词 id
const flaggedIds = ref([]);    // 阶段一答对但被手动标记"不熟"的单词 id
const quizMastered = ref(0);   // 两阶段都通过 归入已掌握本的数量

const currentQuiz = computed(() => words.value[p1Index.value] || null);
const currentSpell = computed(() => p2Words.value[p2Index.value] || null);
const p1Locked = computed(() => !!(p1Feedback.value && p1Feedback.value.showDetails));
const p2Locked = computed(() => !!(p2Feedback.value && p2Feedback.value.showDetails));

async function startQuizFlow() {
  quizLoading.value = true;
  try {
    const res = await startQuiz(quizCount.value, bank.value);
    const list = res?.words || [];
    if (!list.length) { ElMessage.warning('该词库词汇不足，请先添加单词'); return; }
    words.value = list;
    quizCount.value = list.length;
    p1Index.value = 0; p1Answer.value = ''; p1Feedback.value = null; p1Attempt.value = 0; p1Errors.value = [];
    p2Words.value = []; p2Index.value = 0; p2Input.value = ''; p2Feedback.value = null; p2Attempt.value = 0; p2Errors.value = []; p2Correct.value = 0;
    p1Passed.value = []; p2Passed.value = []; flaggedIds.value = []; quizMastered.value = 0;
    quizStage.value = 'phase1';
  } catch (e) { /* handled */ } finally {
    quizLoading.value = false;
  }
}
function p1OptionClass(opt) {
  const fb = p1Feedback.value;
  if (!fb) return p1Answer.value === opt ? 'selected' : '';
  const correct = currentQuiz.value.translation;
  if (fb.showDetails || fb.finalAttempt) {
    if (opt === correct) return 'correct';
    if (opt === p1Answer.value && opt !== correct) return 'wrong';
    return '';
  }
  return p1Answer.value === opt ? 'selected' : '';
}
function pickP1(opt) { if (!p1Locked.value) p1Answer.value = opt; }
function confirmP1() {
  if (!p1Answer.value) { ElMessage.warning('请先选择一个答案'); return; }
  const word = currentQuiz.value;
  const isCorrect = p1Answer.value === word.translation;
  p1Attempt.value++;
  if (isCorrect) {
    if (!p1Passed.value.includes(word.id)) p1Passed.value = [...p1Passed.value, word.id];
    p1Feedback.value = { correct: true, message: '回答正确！', extensions: word.extensions, showDetails: true };
  } else {
    if (!p1Errors.value.find((e) => e.wordId === word.id)) {
      p1Errors.value.push({ wordId: word.id, word: word.word, wrongAnswer: p1Answer.value, correctAnswer: word.translation });
      recordQuizError(word.id, p1Answer.value, 'meaning').catch(() => {});
    }
    if (p1Attempt.value >= 3) {
      p1Feedback.value = { correct: false, message: '很遗憾，正确答案是：' + word.translation, showDetails: true, finalAttempt: true };
    } else {
      const remain = 3 - p1Attempt.value;
      p1Feedback.value = { correct: false, message: `选错了！请重新选择（还剩 ${remain} 次机会）`, showDetails: false };
      p1Answer.value = '';
      setTimeout(() => { p1Feedback.value = null; }, 2000);
    }
  }
}
function nextP1() {
  p1Feedback.value = null; p1Answer.value = ''; p1Attempt.value = 0;
  if (p1Index.value + 1 >= words.value.length) startPhase2();
  else p1Index.value++;
}
async function startPhase2() {
  quizLoading.value = true;
  try {
    const ids = words.value.map((w) => w.id);
    const res = await getSpellWords(ids);
    p2Words.value = res?.words || [];
    p2Index.value = 0; p2Input.value = ''; p2Feedback.value = null; p2Attempt.value = 0; p2Errors.value = []; p2Correct.value = 0;
    quizStage.value = 'phase2';
  } catch (e) { /* handled */ } finally {
    quizLoading.value = false;
  }
}
async function confirmP2() {
  const input = p2Input.value.trim();
  if (!input) { ElMessage.warning('请输入单词'); return; }
  const word = currentSpell.value;
  p2Attempt.value++;
  try {
    const res = await checkSpelling(word.id, input);
    if (res.correct) {
      p2Correct.value++;
      if (!p2Passed.value.includes(word.id)) p2Passed.value = [...p2Passed.value, word.id];
      p2Feedback.value = { correct: true, message: '拼写正确！', extensions: res.extensions, showDetails: true };
    } else if (p2Attempt.value >= 3) {
      p2Feedback.value = { correct: false, message: '很遗憾，正确答案是：' + res.correctAnswer + (res.errorHint ? '\n' + res.errorHint : ''), showDetails: true, finalAttempt: true };
      p2Errors.value.push({ wordId: word.id, translation: word.translation, wrongAnswer: input, correctAnswer: res.correctAnswer });
    } else {
      const remain = 3 - p2Attempt.value;
      p2Feedback.value = { correct: false, message: '拼写错误！' + (res.errorHint ? '' + res.errorHint + '\n' : '') + `请重新输入（还剩 ${remain} 次机会）`, showDetails: false };
      p2Input.value = '';
      setTimeout(() => { p2Feedback.value = null; }, 2500);
    }
  } catch (e) { /* handled */ }
}
function nextP2() {
  p2Feedback.value = null; p2Input.value = ''; p2Attempt.value = 0;
  if (p2Index.value + 1 >= p2Words.value.length) { finalizeQuizMastery(); quizStage.value = 'report'; }
  else p2Index.value++;
}
/** 阶段一"不熟，加入错题本" */
function flagUnfamiliarWord(id) {
  if (flaggedIds.value.includes(id)) return;
  flaggedIds.value = [...flaggedIds.value, id];
  recordQuizError(id, '', 'unfamiliar').catch(() => {});
  ElMessage.info('已加入错题本');
}
/** 阶段一答对 + 阶段二拼写通过（且未被标记不熟） 归入已掌握本 */
function finalizeQuizMastery() {
  const wordIds = words.value.map((w) => w.id);
  const masters = wordIds.filter((id) => p1Passed.value.includes(id) && p2Passed.value.includes(id) && !flaggedIds.value.includes(id));
  quizMastered.value = masters.length;
  // 本会话新掌握 同步到记忆卡片进度
  if (masters.length) masteredCount.value += masters.length;
  masters.forEach((id) => markWordMastered(id).catch(() => {}));
}
function splitExt(s) { return s ? String(s).split(',').map((x) => x.trim()).filter(Boolean) : []; }

// ═══════════ 错题三本 ═══════════
const bookTab = ref('error');
const bookCounts = reactive({ error: 0, review: 0, mastered: 0 });
const errLoading = ref(false);
const errors = ref([]);
const errTotal = ref(0);
const errPage = ref(1);
const errSize = 20;

const errReviewStage = ref('list'); // list | review
const errReviewWords = ref([]);
const errReviewIndex = ref(0);
const errAnswer = ref('');
const errFeedback = ref(null);
const errAttempt = ref(0);
const errCorrect = ref(0);

const currentErrWord = computed(() => errReviewWords.value[errReviewIndex.value] || null);
const errLocked = computed(() => !!(errFeedback.value && errFeedback.value.showDetails));

// 单词详细信息弹窗
const dialogDetail = ref(false);
const detailRow = ref(null);
function showDetail(row) { detailRow.value = row; dialogDetail.value = true; }
function errorTypeText(t) { return t === 'spelling' ? '拼写错误' : (t === 'unfamiliar' ? '不熟' : '选错意思'); }

async function loadBooks() {
  errLoading.value = true;
  try {
    const res = await getQuizErrors(errPage.value, errSize, bank.value, bookTab.value);
    errors.value = res?.records || [];
    errTotal.value = res?.total || 0;
    bookCounts.error = res?.errorCount || 0;
    bookCounts.review = res?.reviewCount || 0;
    bookCounts.mastered = res?.masteredCount || 0;
  } catch (e) {
    errors.value = [];
  } finally {
    errLoading.value = false;
  }
}
function onBookChange() { errPage.value = 1; loadBooks(); }
async function doMastered(id) { await markErrorMastered(id); ElMessage.success('已移入复习本'); await loadBooks(); }
async function doReviewMastered(id) {
  await reviewMastered(id);
  ElMessage.success('已移入已掌握本');
  // 复习本 已掌握本：记忆卡片进度相应增加
  masteredCount.value++;
  await loadBooks();
}
async function doForget(id) {
  await forgetError(id);
  ElMessage.info('已退回错题本');
  // 已掌握本 错题本（状态=不熟）：记忆卡片进度相应减少
  masteredCount.value = Math.max(0, masteredCount.value - 1);
  await loadBooks();
}
async function doDelete(id) {
  try { await ElMessageBox.confirm('确定删除该单词记录？', '提示', { type: 'warning' }); } catch { return; }
  await deleteQuizError(id);
  ElMessage.success('已删除');
  await loadBooks();
}

async function beginErrReview() {
  try {
    const res = await reviewErrors(10, bank.value);
    const list = res?.words || [];
    if (!list.length) { ElMessage.info('该词库暂无错题'); return; }
    errReviewWords.value = list;
    errReviewIndex.value = 0; errAnswer.value = ''; errFeedback.value = null; errAttempt.value = 0; errCorrect.value = 0;
    errReviewStage.value = 'review';
  } catch (e) { /* handled */ }
}
function errOptionClass(opt) {
  const fb = errFeedback.value;
  if (!fb) return errAnswer.value === opt ? 'selected' : '';
  const correct = currentErrWord.value.translation;
  if (fb.showDetails || fb.finalAttempt) {
    if (opt === correct) return 'correct';
    if (opt === errAnswer.value && opt !== correct) return 'wrong';
    return '';
  }
  return errAnswer.value === opt ? 'selected' : '';
}
function pickErr(opt) { if (!errLocked.value) errAnswer.value = opt; }
function confirmErr() {
  if (!errAnswer.value) { ElMessage.warning('请先选择一个答案'); return; }
  const word = currentErrWord.value;
  const isCorrect = errAnswer.value === word.translation;
  errAttempt.value++;
  if (isCorrect) {
    errCorrect.value++;
    errFeedback.value = { correct: true, message: '回答正确！', showDetails: true };
  } else {
    recordQuizError(word.id, errAnswer.value, 'meaning').catch(() => {});
    if (errAttempt.value >= 3) {
      errFeedback.value = { correct: false, message: '很遗憾，正确答案是：' + word.translation, showDetails: true, finalAttempt: true };
    } else {
      errFeedback.value = { correct: false, message: `选错了！请重新选择（还剩 ${3 - errAttempt.value} 次机会）`, showDetails: false };
      errAnswer.value = '';
      setTimeout(() => { errFeedback.value = null; }, 2000);
    }
  }
}
function nextErr() {
  errFeedback.value = null; errAnswer.value = ''; errAttempt.value = 0;
  if (errReviewIndex.value + 1 >= errReviewWords.value.length) errReviewIndex.value++;
  else errReviewIndex.value++;
}

// ═══════════ 切换 ═══════════
function onBankChange() {
  quizStage.value = 'idle';
  errReviewStage.value = 'list';
  errPage.value = 1;
  if (mode.value === 'cards') loadCards();
  else if (mode.value === 'errors') loadBooks();
}
function onModeChange() {
  if (mode.value === 'cards') loadCards();
  else if (mode.value === 'quiz') quizStage.value = 'idle';
  else loadBooks();
}

function onKeydown(e) {
  if (mode.value !== 'cards' || cardsLoading.value || finished.value || !deck.value.length) return;
  if (answered.value) {
    if (e.code === 'ArrowRight' || e.code === 'Enter' || e.code === 'Space') { e.preventDefault(); nextCard(); }
    else if (e.code === 'ArrowLeft') { e.preventDefault(); prevCard(); }
    return;
  }
  if (e.code === 'Space') { e.preventDefault(); flipped.value = !flipped.value; }
  else if (e.code === 'ArrowRight') answerCard(true);
  else if (e.code === 'ArrowLeft') answerCard(false);
}

onMounted(async () => {
  try {
    const list = await vocabBanks();
    if (Array.isArray(list) && list.length) banks.value = list;
  } catch (e) { /* 默认三个词库 */ }
  if (!banks.value.includes(bank.value)) bank.value = banks.value[0];
  loadCards();
  window.addEventListener('keydown', onKeydown);
});
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown));
</script>

<style scoped>
.teacher-words { max-width: 900px; margin: 0 auto; }
.tw-header h1 { margin: 0 0 4px; font-size: 22px; font-weight: 600; color: var(--text); }
.tw-sub { margin: 0 0 18px; color: var(--color-text-secondary); font-size: 13px; }
.tw-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 18px; }
.tw-bank { display: flex; align-items: center; gap: 8px; }
.tw-label { font-size: 14px; color: var(--color-text-secondary); }
.tw-panel { background: var(--color-bg-card); border-radius: 16px; padding: 20px; box-shadow: var(--shadow-sm); }
.tw-row-between { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.tw-progress-bar2 { display: flex; align-items: center; gap: 12px; margin-top: 14px; }
.tw-muted { font-size: 13px; color: var(--color-text-secondary); }
.tw-loading { padding: 20px 0; }
.tw-tip { text-align: center; margin-top: 14px; font-size: 12px; color: var(--color-text-secondary); }

/* 卡片 */
.tw-stage { max-width: 620px; margin: 0 auto; }
.tw-progress { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.tw-progress-bar { flex: 1; height: 8px; border-radius: 9999px; background: var(--color-bg-hover); overflow: hidden; }
.tw-progress-inner { height: 100%; border-radius: 9999px; background: var(--gradient-primary); transition: width .3s ease; }
.tw-card-wrap { perspective: 1200px; cursor: pointer; position: relative; }
.tw-card-count {
  position: absolute; top: 12px; right: 14px; z-index: 5;
  font-size: 13px; font-weight: 600; color: var(--text);
  background: var(--surface); border: 1px solid var(--border);
  border-radius: 9999px; padding: 3px 12px;
}
.tw-card { position: relative; width: 100%; height: 300px; transform-style: preserve-3d; transition: transform .55s cubic-bezier(.4,0,.2,1); }
.tw-card.flipped { transform: rotateY(180deg); }
.tw-face { position: absolute; inset: 0; backface-visibility: hidden; -webkit-backface-visibility: hidden; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 14px; border-radius: var(--radius-lg); padding: 24px; border: 1px solid var(--border); overflow: hidden; }
.tw-face > * { position: relative; z-index: 2; }
.tw-front { background: var(--accent-soft); color: var(--text); }
.tw-back { background: var(--gradient-homework); color: #fff; transform: rotateY(180deg); }
.tw-word { font-size: 42px; font-weight: 800; letter-spacing: 1px; text-align: center; word-break: break-word; }
.tw-word-small { font-size: 20px; font-weight: 600; opacity: .85; }
.tw-speak-back {
  border: none; cursor: pointer; margin-left: 8px;
  background: rgba(255, 255, 255, 0.25); color: #fff;
  border-radius: 9999px; font-size: 15px; padding: 3px 12px;
}
.tw-speak-back:active { transform: scale(0.94); }
.tw-back-meta { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; justify-content: center; }
.tw-pos-badge { font-size: 13px; padding: 2px 12px; border-radius: 9999px; background: rgba(255,255,255,.25); }
.tw-front .tw-pos-badge { background: rgba(255,255,255,.5); }
.tw-phonetic { font-size: 16px; opacity: .9; letter-spacing: .5px; }
.tw-translation { font-size: 26px; font-weight: 700; text-align: center; }
.tw-extensions { font-size: 14px; opacity: .85; text-align: center; max-height: 80px; overflow-y: auto; }
.tw-speak { font-size: 13px; }
.tw-hint { position: absolute; bottom: 14px; font-size: 12px; opacity: .6; }
.tw-actions { display: flex; gap: 14px; justify-content: center; margin-top: 22px; flex-wrap: wrap; }
.tw-btn-known { background: var(--positive); color: #fff; border: none; }
.tw-btn-unknown { background: var(--negative); color: #fff; border: none; }

/* 测验 */
.tw-quiz { max-width: 560px; margin: 0 auto; text-align: center; }
.tw-quiz-setup { text-align: center; }
.tw-counts { display: flex; gap: 10px; justify-content: center; margin: 16px 0 14px; flex-wrap: wrap; }
.tw-custom-count { display: flex; align-items: center; justify-content: center; gap: 10px; }
.tw-quiz-top { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.tw-quiz-word { font-size: 32px; font-weight: 800; margin-bottom: 8px; display: flex; align-items: center; justify-content: center; gap: 10px; flex-wrap: wrap; }
.tw-ask { margin: 16px 0; font-size: 15px; color: var(--color-text-secondary); }
.tw-options { display: flex; flex-direction: column; gap: 10px; margin-top: 12px; }
.tw-option { display: flex; align-items: center; gap: 12px; padding: 14px 16px; font-size: 16px; border: 1px solid var(--color-border, var(--border)); border-radius: 12px; background: transparent; cursor: pointer; text-align: left; transition: color 180ms ease, background-color 180ms ease, border-color 180ms ease, opacity 180ms ease, transform 180ms ease; }
.tw-option:hover:not(:disabled) { border-color: var(--color-primary, var(--accent)); background: var(--color-bg-hover); }
.tw-option:disabled { cursor: default; }
.tw-option.selected { border-color: var(--color-primary, var(--accent)); background: var(--color-bg-hover); }
.tw-option.correct { border-color: var(--color-success, var(--positive)); background: rgba(103,194,58,.12); }
.tw-option.wrong { border-color: var(--color-danger, var(--negative)); background: rgba(245,108,108,.12); }
.tw-option-key { width: 26px; height: 26px; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center; background: var(--color-bg-hover); font-size: 13px; font-weight: 700; flex-shrink: 0; }
.tw-feedback { margin: 16px 0; font-size: 15px; font-weight: 600; }
.tw-feedback.ok { color: var(--color-success, var(--positive)); }
.tw-feedback.bad { color: var(--color-danger, var(--negative)); }
.tw-ext-row { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; margin-top: 8px; }
.tw-ext-tag { font-size: 12px; color: var(--accent); background: rgba(13,148,136,.12); border-radius: 9999px; padding: 3px 12px; font-weight: 400; }
.tw-detail-panel { margin-top: 16px; padding: 16px; border-radius: 12px; background: var(--color-bg-hover); text-align: left; }
.tw-detail-title { font-size: 14px; font-weight: 700; color: var(--color-text-primary); margin-bottom: 10px; }
.tw-detail-item { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; padding: 8px 0; border-bottom: 1px dashed var(--color-border-light, var(--border)); font-size: 14px; }
.tw-detail-item:last-child { border-bottom: none; }
.tw-detail-item.on .tw-detail-text { color: var(--color-success, var(--positive)); font-weight: 700; }
.tw-detail-num { width: 22px; height: 22px; border-radius: 50%; background: var(--color-primary, var(--accent)); color: #fff; font-size: 12px; display: inline-flex; align-items: center; justify-content: center; }
.tw-detail-arrow { color: #b0b4bb; }
.tw-detail-word { color: var(--color-primary, var(--accent)); font-weight: 600; }
.tw-detail-pos { font-size: 12px; color: var(--color-text-secondary); background: var(--color-bg-card); border-radius: 4px; padding: 1px 8px; }
.tw-spell-input { max-width: 340px; margin: 16px auto; display: block; }
.tw-pos-hint { display: block; font-size: 13px; color: var(--color-text-secondary); }

/* 结果 / 报告 */
.tw-result { text-align: center; padding: 26px 20px; }
.tw-result-emoji { font-size: 54px; margin-bottom: 8px; }
.tw-result-stats { display: flex; justify-content: center; gap: 36px; margin: 18px 0 6px; flex-wrap: wrap; }
.tw-result-num { font-size: 30px; font-weight: 800; }
.tw-report-item { padding: 10px 0; border-bottom: 1px solid var(--color-border-light, var(--border)); }
.tw-report-item:last-child { border-bottom: none; }
.tw-report-head { display: flex; align-items: center; justify-content: space-between; }
.tw-report-line { margin-top: 4px; font-size: 13px; color: var(--color-text-secondary); }
.tw-bad { color: var(--color-danger, var(--negative)); }
.tw-ok { color: var(--color-success, var(--positive)); font-weight: 600; }

/* 已掌握本 */
.tw-mastered-wrap { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 16px; }
.tw-mastered-chip { display: inline-flex; align-items: center; gap: 8px; padding: 8px 14px; border-radius: 9999px; background: var(--color-bg-hover); font-size: 14px; }
.tw-chip-action { font-size: 12px; color: var(--color-danger, var(--negative)); cursor: pointer; padding-left: 4px; border-left: 1px solid var(--color-border-light, var(--border)); }

@media (max-width: 768px) {
  .tw-card { height: 260px; }
  .tw-word { font-size: 32px; }
  .tw-translation { font-size: 22px; }
  .tw-tip { display: none; }
}
</style>