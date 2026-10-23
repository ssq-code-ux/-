<template>
  <div>
    <!-- ═══════════════ Tab 标题区 ═══════════════ -->
    <header class="page-header">
      <h1 class="page-title">{{ tabTitle }}</h1>
      <p class="page-subtitle">{{ tabSubtitle }}</p>
    </header>

    <!-- ═══════════════ 我的作业 ═══════════════ -->
    <div v-if="currentTab === 'homework'">
      <!-- 过期提醒 — 脉冲动画 -->
      <transition-group name="alert-list" tag="div">
        <div
          v-if="overdueItems.length"
          key="overdue"
          class="overdue-alert"
        >
          <div class="overdue-alert-inner">
            <span class="overdue-icon"></span>
            <div>
              <strong>你有 {{ overdueItems.length }} 份作业已过截止时间未提交！</strong>
              <div class="overdue-list">
                <span v-for="h in overdueItems" :key="h.id" class="overdue-tag">
                  {{ h.title }} — {{ h.deadline }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </transition-group>

      <!-- 作业状态概览 — 点击筛选 -->
      <div v-if="!loading && homeworkItems.length" class="hw-stats-bar">
        <span
          class="hw-stat-item hw-stat-pending"
          :class="{ 'hw-stat-active': statusFilter === '未提交' }"
          @click="statusFilter = statusFilter === '未提交' ? null : '未提交'"
        >
          未提交 <strong>{{ hwStatusCounts.pending }}</strong>
        </span>
        <span
          class="hw-stat-item hw-stat-redo"
          :class="{ 'hw-stat-active': statusFilter === '需重做' }"
          @click="statusFilter = statusFilter === '需重做' ? null : '需重做'"
        >
          需重做 <strong>{{ hwStatusCounts.redo }}</strong>
        </span>
        <span v-if="statusFilter" class="hw-filter-tag">
          已筛选：{{ statusFilter }}
          <el-button size="small" text @click="statusFilter = null">× 清除</el-button>
        </span>
      </div>

      <!-- 学科筛选 -->
      <div v-if="!loading && homeworkSubjects.length" class="hw-subject-filter">
        <span class="hw-subject-filter-label">学科：</span>
        <el-button size="small" :type="!subjectFilter ? 'primary' : ''" round @click="subjectFilter = null">全部</el-button>
        <el-button v-for="s in homeworkSubjects" :key="s" size="small" :type="subjectFilter === s ? 'primary' : ''" round @click="subjectFilter = s">{{ s }}</el-button>
      </div>

      <!-- 操作栏 -->
      <div class="toolbar">
        <el-button type="primary" @click="loadHomework" :loading="loading" round>
          刷新作业
        </el-button>
        <span class="toolbar-hint" v-if="!loading && homeworkItems.length">
          共 {{ displayedHomeworks.length }} 份作业
          <template v-if="statusFilter">（{{ statusFilter }}）</template>
        </span>
      </div>

      <!-- 骨架屏 -->
      <div v-if="loading" class="skeleton-list">
        <div v-for="i in 3" :key="i" class="skeleton-card">
          <div class="sk-line sk-title"></div>
          <div class="sk-line sk-body"></div>
          <div class="sk-line sk-tag"></div>
        </div>
      </div>

      <!-- 作业卡片列表（按学科分组） -->
      <div v-else-if="displayedHomeworks.length" class="hw-card-list">
        <template v-for="grp in homeworksBySubject" :key="grp.subject">
        <div class="hw-subject-head">
          <span class="hw-subject-name">{{ grp.subject }}</span>
          <span class="hw-subject-count">{{ grp.items.length }} 份</span>
        </div>
        <div
          v-for="item in grp.items"
          :key="item.id"
          class="hw-card"
          :class="'hw-card--' + statusClass(item.status)"
          @click="viewDetail(item.id)"
        >
          <div class="hw-card-body">
            <div class="hw-card-top">
              <h4 class="hw-card-title">{{ item.title }}</h4>
              <span class="hw-status" :class="'hw-status--' + statusClass(item.status)">
                {{ item.status }}
              </span>
            </div>
            <p class="hw-card-content">{{ item.content || '暂无文字内容' }}</p>
            <div class="hw-card-meta">
              <span class="hw-meta-tag">
                <span class="hw-meta-dot"></span>
                {{ item.type === 'answer_sheet' ? '答题卡' : (item.type || '作业') }}{{ item.subject ? ' · ' + item.subject : '' }}
              </span>
              <span class="hw-meta-deadline" :class="{ 'hw-deadline-over': isOverdue(item) }">
                {{ item.deadline || '无截止时间' }}
              </span>
            </div>
          </div>
          <div class="hw-card-actions" @click.stop>
            <el-button size="small" round @click="viewDetail(item.id)">详情</el-button>
            <!-- 答题卡在专门的作答页上做题，提交后系统立即判分 -->
            <el-button
              v-if="item.type === 'answer_sheet'"
              size="small"
              type="primary"
              round
              @click="openPaper(item)"
              :disabled="item.status !== '未提交' && item.status !== '需重做'"
            >
              开始作答
            </el-button>
            <el-button
              v-else
              size="small"
              type="primary"
              round
              @click="openSubmit(item)"
              :disabled="item.status !== '未提交' && item.status !== '需重做'"
            >
              提交
            </el-button>
            <el-button
              size="small"
              round
              @click="item.type === 'answer_sheet' ? openSheetResult(item) : seeReview(item.id)"
              :disabled="item.status === '未提交'"
            >
              批改结果
            </el-button>
          </div>
        </div>
        </template>
      </div>

      <!-- 空状态 -->
      <el-empty v-else description="暂无作业，去学习吧 ">
      </el-empty>
    </div>

    <!-- ═══════════════ 词汇学习 ═══════════════ -->
    <div v-if="currentTab === 'vocabulary'">
      <!-- 视图切换 -->
      <div class="vocab-view-tabs">
        <el-button
          :type="vocabViewMode === 'list' ? 'primary' : ''"
          round
          size="large"
          @click="vocabViewMode = 'list'"
        >
          词汇列表
        </el-button>
        <el-button
          :type="vocabViewMode === 'error' ? 'primary' : ''"
          round
          size="large"
          @click="switchBook('error')"
        >
          错题本
          <span v-if="bookCounts.error > 0" class="error-badge-inline">{{ bookCounts.error }}</span>
        </el-button>
        <el-button
          :type="vocabViewMode === 'review' ? 'primary' : ''"
          round
          size="large"
          @click="switchBook('review')"
        >
          复习本
          <span v-if="bookCounts.review > 0" class="error-badge-inline">{{ bookCounts.review }}</span>
        </el-button>
        <el-button
          :type="vocabViewMode === 'mastered' ? 'primary' : ''"
          round
          size="large"
          @click="switchBook('mastered')"
        >
          已掌握本
          <span v-if="bookCounts.mastered > 0" class="error-badge-inline">{{ bookCounts.mastered }}</span>
        </el-button>
      </div>

      <!-- ══════ 词汇列表视图 ══════ -->
      <template v-if="vocabViewMode === 'list'">
      <!-- 搜索 & 操作栏 -->
      <div class="vocab-toolbar">
        <el-input
          v-model="vocabKeyword"
          placeholder="搜索单词或释义..."
          clearable
          @change="searchVocabularies"
          @clear="searchVocabularies"
          class="vocab-search"
          size="large"
        >
          <template #prefix></template>
        </el-input>
        <el-button type="primary" size="large" round @click="openQuizDialog" :loading="loading" class="quiz-btn">
          看词选意
        </el-button>
        <el-button size="large" round @click="openDictation" class="quiz-btn dict-btn">
          听写模式
        </el-button>
      </div>

      <!-- 骨架屏 -->
      <div v-if="loading" class="vocab-grid">
        <div v-for="i in 6" :key="i" class="skeleton-vocab-card">
          <div class="sk-line sk-v-title"></div>
          <div class="sk-line sk-v-body"></div>
          <div class="sk-line sk-v-tag"></div>
        </div>
      </div>

      <!-- 词汇卡片网格 -->
      <div v-else-if="vocabularies.length" class="vocab-grid">
        <div
          v-for="v in vocabularies"
          :key="v.id"
          class="vocab-card"
          @click="showWordDetail(v)"
        >
          <div class="vocab-card-inner">
            <h3 class="vocab-word">
              {{ v.word }}
              <span class="speak-btn" title="播放发音" @click.stop="speak(v.word)">发音</span>
            </h3>
            <p class="vocab-trans">{{ v.translation }}</p>
            <div class="vocab-footer">
              <span class="vocab-pos">{{ v.partOfSpeech || 'word' }}</span>
              <span v-if="v.extensions" class="vocab-ext-count">
                +{{ v.extensions.split(',').filter(Boolean).length }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <EmptyState v-else text="还没有词汇。等老师添加后就能开始背了。" />

      <div v-if="vocabTotal > 6" class="pagination-wrap">
        <el-pagination
          v-model:current-page="vocabPage"
          :total="vocabTotal"
          :page-size="6"
          layout="prev, pager, next"
          background
          @change="loadVocabularies"
        />
      </div>
      </template>

      <!-- ══════ 错题三本视图 ══════ -->
      <template v-if="vocabViewMode !== 'list'">
        <div class="error-toolbar">
          <div class="error-stats">
            <span class="error-stat-item">错题本 <strong>{{ bookCounts.error }}</strong></span>
            <span class="error-stat-item">复习本 <strong>{{ bookCounts.review }}</strong></span>
            <span class="error-stat-item">已掌握 <strong>{{ bookCounts.mastered }}</strong></span>
          </div>
          <el-button
            v-if="vocabViewMode === 'error'"
            type="primary"
            size="large"
            round
            @click="startErrorReview"
            :disabled="!bookCounts.error"
            class="review-btn"
          >
            复习错题
          </el-button>
        </div>

        <!-- 已掌握本：与错题本/复习本一样分页展示（单词 + 状态「已掌握」 + 忘了） -->
        <div v-if="vocabViewMode === 'mastered'">
          <div v-if="quizLoading" class="vocab-grid">
            <div v-for="i in 3" :key="i" class="skeleton-vocab-card">
              <div class="sk-line sk-v-title"></div>
              <div class="sk-line sk-v-body"></div>
            </div>
          </div>

          <div v-else-if="errorRecords.length" class="error-list">
            <div v-for="rec in errorRecords" :key="rec.id" class="error-card">
              <div class="error-card-body">
                <div class="error-card-top">
                  <h4 class="error-word" title="点击查看详细信息" @click="showWordDetail(rec)">
                    {{ rec.word }}
                    <span class="speak-btn" title="播放发音" @click.stop="speak(rec.word)">发音</span>
                  </h4>
                  <span v-if="rec.partOfSpeech" class="error-pos-tag">{{ rec.partOfSpeech }}</span>
                  <span class="error-type-tag tag-mastered">已掌握</span>
                </div>
                <div class="error-detail">
                  <p><span class="error-label">释义：</span>{{ rec.translation }}</p>
                  <div v-if="rec.extensions" class="error-extensions">
                    <span class="error-label">延伸词：</span>
                    <el-tag
                      v-for="(ext, i) in rec.extensions.split(',').filter(Boolean)"
                      :key="i"
                      size="small"
                      round
                      class="ext-tag"
                    >{{ ext }}</el-tag>
                  </div>
                  <p v-if="rec.phonetic" class="error-meta">音标：{{ rec.phonetic }}</p>
                </div>
              </div>
              <div class="error-card-actions">
                <el-button size="small" type="warning" round @click="doForget(rec.id)">
                  忘了
                </el-button>
                <el-button size="small" round @click="doDeleteError(rec.id)">
                  删除
                </el-button>
              </div>
            </div>
          </div>

          <el-empty v-else description="暂无已掌握的单词" />

          <div v-if="errorTotal > 20" class="pagination-wrap">
            <el-pagination
              v-model:current-page="errorPage"
              :total="errorTotal"
              :page-size="20"
              layout="prev, pager, next"
              background
              @current-change="loadErrorRecords()"
            />
          </div>
        </div>

        <template v-else>
          <div v-if="quizLoading" class="vocab-grid">
            <div v-for="i in 4" :key="i" class="skeleton-vocab-card">
              <div class="sk-line sk-v-title"></div>
              <div class="sk-line sk-v-body"></div>
            </div>
          </div>

          <div v-else-if="errorRecords.length" class="error-list">
            <div v-for="rec in errorRecords" :key="rec.id" class="error-card">
              <div class="error-card-body">
                <div class="error-card-top">
                  <h4 class="error-word" title="点击查看详细信息" @click="showWordDetail(rec)">
                    {{ rec.word }}
                    <span class="speak-btn" title="播放发音" @click.stop="speak(rec.word)">发音</span>
                  </h4>
                  <span v-if="rec.partOfSpeech" class="error-pos-tag">{{ rec.partOfSpeech }}</span>
                  <span class="error-type-tag" :class="rec.errorType === 'spelling' ? 'tag-spelling' : (rec.errorType === 'unfamiliar' ? 'tag-unfamiliar' : 'tag-meaning')">
                    {{ rec.errorType === 'spelling' ? '拼写错误' : (rec.errorType === 'unfamiliar' ? '不熟' : '选错意思') }}
                  </span>
                </div>
                <div class="error-detail">
                  <p><span class="error-label">释义：</span>{{ rec.translation }}</p>
                  <div v-if="rec.extensions" class="error-extensions">
                    <span class="error-label">延伸词：</span>
                    <el-tag
                      v-for="(ext, i) in rec.extensions.split(',').filter(Boolean)"
                      :key="i"
                      size="small"
                      round
                      class="ext-tag"
                    >{{ ext }}</el-tag>
                  </div>
                  <p><span class="error-label">你的答案：</span><span class="error-wrong">{{ rec.wrongAnswer || '未作答' }}</span></p>
                  <p v-if="rec.correctAnswer"><span class="error-label">正确答案：</span><span class="error-correct">{{ rec.correctAnswer }}</span></p>
                  <p class="error-meta">
                    错误 {{ rec.errorCount }} 次 · {{ rec.createTime?.slice(0, 10) || '' }}
                  </p>
                </div>
              </div>
              <div class="error-card-actions">
                <el-button
                  v-if="vocabViewMode === 'error'"
                  size="small"
                  type="success"
                  round
                  @click="doMarkMastered(rec.id)"
                >
                  已掌握
                </el-button>
                <el-button
                  v-else
                  size="small"
                  type="success"
                  round
                  @click="doReviewMastered(rec.id)"
                >
                  再次掌握
                </el-button>
                <el-button
                  size="small"
                  round
                  @click="doDeleteError(rec.id)"
                >
                  删除
                </el-button>
              </div>
            </div>
          </div>

          <el-empty v-else :description="vocabViewMode === 'error' ? '暂无错题记录 ' : '暂无待复习单词 '" />

          <div v-if="errorTotal > 20" class="pagination-wrap">
            <el-pagination
              v-model:current-page="errorPage"
              :total="errorTotal"
              :page-size="20"
              layout="prev, pager, next"
              background
              @current-change="loadErrorRecords()"
            />
          </div>
        </template>
      </template>

      <!-- ══════ 测验弹窗（两阶段） ══════ -->
      <el-dialog v-model="dialogQuiz" :title="quizDialogTitle" width="650px" :close-on-click-modal="false" class="quiz-dialog">
        <!-- ═══ 数量选择 ═══ -->
        <template v-if="quizPhase === 'countSelect'">
          <div class="count-select-area">
            <p class="count-select-title">本次要背诵/检测多少个单词？（可自定义 1~200）</p>
            <div class="count-slider-wrap">
              <el-slider
                v-model="quizWordCount"
                :min="1"
                :max="200"
                :step="1"
                show-input
                class="count-slider"
              />
            </div>
            <el-button type="primary" size="large" round @click="startPhase1" :loading="quizLoading" class="start-btn">
              开始测验
            </el-button>
          </div>
        </template>

        <!-- ═══ 阶段一：看词选义 ═══ -->
        <template v-if="quizPhase === 'phase1'">
          <div class="quiz-phase-header">
            <span class="phase-badge phase1-badge">阶段一：看词选义</span>
            <span class="phase-progress">{{ quizCurrentIndex + 1 }} / {{ quizWords.length }}</span>
          </div>
          <div class="quiz-progress-bar">
            <div class="quiz-progress-fill" :style="{ width: (quizCurrentIndex / quizWords.length * 100) + '%' }"></div>
          </div>

          <div v-if="quizWords[quizCurrentIndex]" class="phase1-card">
            <div class="phase1-word-display">
              <span class="phase1-word">
                {{ quizWords[quizCurrentIndex].word }}
                <span class="speak-btn" title="播放发音" @click="speak(quizWords[quizCurrentIndex].word)">发音</span>
              </span>
              <span v-if="quizWords[quizCurrentIndex].partOfSpeech" class="phase1-pos">
                {{ quizWords[quizCurrentIndex].partOfSpeech }}
              </span>
            </div>
            <p class="phase1-question">请选择以上单词的中文意思：</p>
            <el-radio-group v-model="quizCurrentAnswer" class="phase1-options" :disabled="!!quizFeedback?.showDetails || !!quizFeedback?.finalAttempt">
              <el-radio
                v-for="opt in quizWords[quizCurrentIndex].options"
                :key="opt"
                :value="opt"
                class="phase1-option"
                :class="{
                  'option-correct': (quizFeedback?.showDetails || quizFeedback?.finalAttempt) && opt === quizWords[quizCurrentIndex].translation,
                  'option-wrong': quizFeedback && !quizFeedback.correct && !quizFeedback.showDetails && !quizFeedback.finalAttempt && quizCurrentAnswer === opt && opt !== quizWords[quizCurrentIndex].translation
                }"
              >
                {{ opt }}
              </el-radio>
            </el-radio-group>

            <!-- 反馈区 -->
            <transition name="feedback-fade">
              <div v-if="quizFeedback" class="phase1-feedback" :class="quizFeedback.correct ? 'feedback-ok' : 'feedback-bad'">
                <p class="feedback-msg">{{ quizFeedback.message }}</p>
                <div v-if="quizFeedback.correct && quizFeedback.extensions" class="feedback-extensions">
                  <span class="ext-label">延伸词：</span>
                  <el-tag
                    v-for="(ext, i) in quizFeedback.extensions.split(',').filter(Boolean)"
                    :key="i"
                    size="small"
                    round
                    class="ext-tag"
                  >{{ ext }}</el-tag>
                </div>
              </div>
            </transition>

            <!-- 选项详细信息面板（选对后或最终失败后展示） -->
            <transition name="feedback-fade">
              <div v-if="quizFeedback?.showDetails || quizFeedback?.finalAttempt" class="option-details-panel">
                <h4 class="option-details-title">全部选项解析</h4>
                <div
                  v-for="(detail, i) in quizWords[quizCurrentIndex].optionDetails"
                  :key="i"
                  class="option-detail-item"
                  :class="{ 'option-detail-correct': detail.optionText === quizWords[quizCurrentIndex].translation }"
                >
                  <div class="option-detail-head">
                    <span class="option-detail-num">{{ ['A','B','C','D'][i] }}</span>
                    <span class="option-detail-text">{{ detail.optionText }}</span>
                    <span class="option-detail-arrow">←</span>
                    <span class="option-detail-word">{{ detail.sourceWord }}</span>
                    <span v-if="detail.partOfSpeech && detail.partOfSpeech !== '—'" class="option-detail-pos">{{ detail.partOfSpeech }}</span>
                  </div>
                  <div v-if="detail.extensions" class="option-detail-ext">
                    {{ detail.extensions.split(',').filter(Boolean).join('、') }}
                  </div>
                </div>
              </div>
            </transition>
          </div>
        </template>

        <!-- ═══ 阶段二：看义写词 ═══ -->
        <template v-if="quizPhase === 'phase2'">
          <div class="quiz-phase-header">
            <span class="phase-badge phase2-badge">阶段二：看义写词</span>
            <span class="phase-progress">{{ quizPhase2Index + 1 }} / {{ quizPhase2Words.length }}</span>
          </div>
          <div class="quiz-progress-bar">
            <div class="quiz-progress-fill phase2-fill" :style="{ width: (quizPhase2Index / quizPhase2Words.length * 100) + '%' }"></div>
          </div>

          <div v-if="quizPhase2Words[quizPhase2Index]" class="phase2-card">
            <div class="phase2-meaning-display">
              <p class="phase2-label">请根据意思写出对应的英文单词：</p>
              <h3 class="phase2-meaning">{{ quizPhase2Words[quizPhase2Index].translation }}</h3>
              <span v-if="quizPhase2Words[quizPhase2Index].partOfSpeech" class="phase2-pos-hint">
                词性提示：{{ quizPhase2Words[quizPhase2Index].partOfSpeech }}
              </span>
            </div>
            <el-input
              v-model="quizPhase2Input"
              placeholder="请输入英文单词..."
              size="large"
              class="phase2-input"
              :disabled="!!quizPhase2Feedback?.showDetails || !!quizPhase2Feedback?.finalAttempt"
              @keyup.enter="(!quizPhase2Feedback?.showDetails && !quizPhase2Feedback?.finalAttempt) && confirmPhase2Answer()"
            />

            <!-- 反馈区 -->
            <transition name="feedback-fade">
              <div v-if="quizPhase2Feedback" class="phase2-feedback" :class="quizPhase2Feedback.correct ? 'feedback-ok' : 'feedback-bad'">
                <p class="feedback-msg">{{ quizPhase2Feedback.message }}</p>
                <div v-if="quizPhase2Feedback.correct && quizPhase2Feedback.extensions" class="feedback-extensions">
                  <span class="ext-label">延伸词：</span>
                  <el-tag
                    v-for="(ext, i) in quizPhase2Feedback.extensions.split(',').filter(Boolean)"
                    :key="i"
                    size="small"
                    round
                    class="ext-tag"
                  >{{ ext }}</el-tag>
                </div>
              </div>
            </transition>
          </div>
        </template>

        <!-- ═══ 测验报告 ═══ -->
        <template v-if="quizPhase === 'report'">
          <div class="quiz-result">
            <div
              class="score-ring"
              :style="{ '--score-percent': (quizPhase2Correct / quizPhase2Words.length * 100) + '%' }"
            >
              <div class="score-inner">
                <span class="score-num">{{ quizPhase2Correct }}</span>
                <span class="score-total">/ {{ quizPhase2Words.length }}</span>
              </div>
            </div>
            <p class="score-msg">
              {{ quizPhase2Correct === quizPhase2Words.length ? '全部正确！太棒了！' : quizPhase2Correct >= quizPhase2Words.length / 2 ? '继续加油！' : '需要多加练习哦~' }}
            </p>
          </div>

          <!-- 阶段一错题 -->
          <div v-if="quizPhase1Errors.length" class="report-section">
            <el-divider>阶段一错题（看词选义）</el-divider>
            <div v-for="(e, i) in quizPhase1Errors" :key="'e1'+i" class="quiz-report-item report-wrong">
              <div class="report-head">
                <span class="report-num">{{ i + 1 }}. {{ e.word }}</span>
                <span class="report-badge badge-no">选错</span>
              </div>
              <p class="report-your">你的选择：<strong>{{ e.wrongAnswer }}</strong></p>
              <p class="report-correct-ans">正确答案：<strong>{{ e.correctAnswer }}</strong></p>
            </div>
          </div>

          <!-- 阶段二错题 -->
          <div v-if="quizPhase2Errors.length" class="report-section">
            <el-divider>阶段二错题（看义写词）</el-divider>
            <div v-for="(e, i) in quizPhase2Errors" :key="'e2'+i" class="quiz-report-item report-wrong">
              <div class="report-head">
                <span class="report-num">{{ i + 1 }}. {{ e.translation }}</span>
                <span class="report-badge badge-no">拼错</span>
              </div>
              <p class="report-your">你写的：<strong>{{ e.wrongAnswer }}</strong></p>
              <p class="report-correct-ans">正确答案：<strong>{{ e.correctAnswer }}</strong></p>
            </div>
          </div>

          <!-- 阶段一正确（展示延伸词） -->
          <div v-if="quizWords.length" class="report-section">
            <el-divider>阶段一通过的单词</el-divider>
            <div class="report-passed-grid">
              <div v-for="w in quizWords" :key="'pw'+w.id" class="report-passed-item">
                <span class="passed-word">{{ w.word }}</span>
                <span class="passed-trans">{{ w.translation }}</span>
                <span v-if="w.extensions" class="passed-ext">
                  {{ w.extensions.split(',').filter(Boolean).join('、') }}
                </span>
              </div>
            </div>
          </div>
        </template>

        <template #footer>
          <!-- 阶段一按钮 -->
          <template v-if="quizPhase === 'phase1'">
            <el-button round @click="dialogQuiz = false">关闭</el-button>
            <el-button
              v-if="quizFeedback?.correct && quizWords[quizCurrentIndex]"
              type="warning"
              round
              :disabled="quizFlagged.includes(quizWords[quizCurrentIndex].id)"
              @click="flagQuizUnfamiliar(quizWords[quizCurrentIndex].id)"
            >
              {{ quizFlagged.includes(quizWords[quizCurrentIndex].id) ? '已加入错题本' : '不熟，加入错题本' }}
            </el-button>
            <el-button
              v-if="!quizFeedback?.showDetails && !quizFeedback?.finalAttempt"
              type="primary"
              round
              @click="confirmPhase1Answer"
              :disabled="!quizCurrentAnswer"
            >
              确认
            </el-button>
            <el-button
              v-else
              type="success"
              round
              @click="nextPhase1Word"
            >
              {{ quizCurrentIndex + 1 >= quizWords.length ? '进入阶段二 →' : '下一题 →' }}
            </el-button>
          </template>
          <!-- 阶段二按钮 -->
          <template v-if="quizPhase === 'phase2'">
            <el-button round @click="dialogQuiz = false">关闭</el-button>
            <el-button
              v-if="!quizPhase2Feedback?.showDetails && !quizPhase2Feedback?.finalAttempt"
              type="primary"
              round
              @click="confirmPhase2Answer"
              :disabled="!quizPhase2Input.trim()"
            >
              确认
            </el-button>
            <el-button
              v-else
              type="success"
              round
              @click="nextPhase2Word"
            >
              {{ quizPhase2Index + 1 >= quizPhase2Words.length ? '查看报告 →' : '下一题 →' }}
            </el-button>
          </template>
          <!-- 报告按钮 -->
          <template v-if="quizPhase === 'report'">
            <el-button round @click="dialogQuiz = false">关闭</el-button>
            <el-button type="primary" round @click="resetQuiz">再来一次 </el-button>
            <el-button
              v-if="quizPhase1Errors.length || quizPhase2Errors.length"
              type="warning"
              round
              @click="dialogQuiz = false; switchBook('error')"
            >
              去错题库复习 </el-button>
          </template>
        </template>
      </el-dialog>

      <!-- ══════ 听写模式弹窗（听音写词） ══════ -->
      <el-dialog v-model="dialogDictation" :title="dictTitle" width="560px" :close-on-click-modal="false" class="quiz-dialog">
        <!-- 范围与数量选择 -->
        <template v-if="dictPhase === 'setup'">
          <div class="count-select-area">
            <p class="count-select-title">选择听写范围</p>
            <div class="dict-scope-row">
              <div class="dict-scope-item" :class="{ active: dictScope === 'all' }" @click="dictScope = 'all'">
                <span class="dict-scope-icon"></span>
                <span class="dict-scope-name">全词库随机</span>
                <span class="dict-scope-desc">从所有单词中随机抽取</span>
              </div>
              <div class="dict-scope-item" :class="{ active: dictScope === 'errors' }" @click="dictScope = 'errors'">
                <span class="dict-scope-icon"></span>
                <span class="dict-scope-name">错题本单词</span>
                <span class="dict-scope-desc">优先巩固没掌握的词</span>
              </div>
            </div>
            <div class="count-slider-wrap">
              <el-slider
                v-model="dictCount"
                :min="5"
                :max="30"
                :step="5"
                show-stops
                show-input
                :marks="{5:'5',10:'10',15:'15',20:'20',25:'25',30:'30'}"
                class="count-slider"
              />
            </div>
            <p class="dict-tip">系统朗读单词，听音拼写；每词一次作答机会，拼错自动记入错题本</p>
            <el-button type="primary" size="large" round @click="startDictation" :loading="dictLoading" class="start-btn">
              开始听写
            </el-button>
          </div>
        </template>

        <!-- 听写进行中 -->
        <template v-if="dictPhase === 'listen'">
          <div class="quiz-phase-header">
            <span class="phase-title">听音写词</span>
            <span class="phase-progress">{{ dictIndex + 1 }} / {{ dictWords.length }}</span>
          </div>
          <div class="quiz-progress-bar">
            <div class="quiz-progress-fill dict-fill" :style="{ width: (dictIndex / dictWords.length * 100) + '%' }"></div>
          </div>
          <div v-if="dictWords[dictIndex]" class="dict-card">
            <button class="dict-speak-btn" type="button" title="再听一遍" @click="replayDictWord">发音</button>
            <p class="dict-hint-line">点击可重听 · 每词一次作答机会</p>
            <div v-if="dictHintVisible" class="dict-hint">
              <span v-if="dictWords[dictIndex].partOfSpeech" class="dict-pos">{{ dictWords[dictIndex].partOfSpeech }}</span>
              <span class="dict-trans">{{ dictWords[dictIndex].translation }}</span>
            </div>
            <el-button v-else link type="primary" size="small" class="dict-hint-btn" @click="dictHintVisible = true">
              看提示（词性 + 释义）
            </el-button>
            <el-input
              v-model="dictInput"
              size="large"
              placeholder="输入你听到的单词"
              class="dict-input"
              :disabled="!!dictFeedback"
              @keyup.enter="!dictFeedback && confirmDictAnswer()"
            />
            <div v-if="dictFeedback" class="phase1-feedback" :class="dictFeedback.correct ? 'feedback-ok' : 'feedback-bad'">
              <p class="feedback-msg">{{ dictFeedback.message }}</p>
              <p v-if="!dictFeedback.correct" class="dict-feedback-extra">释义：{{ dictWords[dictIndex].translation }}</p>
            </div>
          </div>
        </template>

        <!-- 听写报告 -->
        <template v-if="dictPhase === 'report'">
          <div class="quiz-result">
            <div class="score-ring" :style="{ '--score-percent': (dictCorrect / dictWords.length * 100) + '%' }">
              <div class="score-inner">
                <span class="score-num">{{ dictCorrect }}</span>
                <span class="score-total">/ {{ dictWords.length }}</span>
              </div>
            </div>
            <p class="score-msg">
              {{ dictCorrect === dictWords.length ? '全部听写正确，太棒了！' : dictCorrect >= dictWords.length / 2 ? '听写完成，继续加油！' : '需要多听多练哦~' }}
            </p>
          </div>
          <div v-if="dictErrors.length" class="report-section">
            <el-divider>听错的单词（已自动加入错题本）</el-divider>
            <div v-for="(e, i) in dictErrors" :key="'de'+i" class="quiz-report-item report-wrong">
              <div class="report-head">
                <span class="report-num">{{ i + 1 }}. {{ e.word }}
                  <span class="speak-btn" title="播放发音" @click="speak(e.word)">发音</span>
                </span>
                <span class="report-badge badge-no">听错</span>
              </div>
              <p class="report-your">你写的：<strong>{{ e.wrongAnswer }}</strong></p>
              <p class="dict-report-trans">释义：{{ e.translation }}</p>
            </div>
          </div>
          <div v-else class="report-section">
            <el-divider>本轮全部听写正确</el-divider>
          </div>
        </template>

        <template #footer>
          <template v-if="dictPhase === 'setup'">
            <el-button round @click="dialogDictation = false">关闭</el-button>
          </template>
          <template v-if="dictPhase === 'listen'">
            <el-button round @click="dialogDictation = false">关闭</el-button>
            <el-button
              v-if="!dictFeedback"
              type="primary"
              round
              @click="confirmDictAnswer"
              :disabled="!dictInput.trim()"
            >
              提交
            </el-button>
            <el-button v-else type="success" round @click="nextDictWord">
              {{ dictIndex + 1 >= dictWords.length ? '查看报告 →' : '下一个 →' }}
            </el-button>
          </template>
          <template v-if="dictPhase === 'report'">
            <el-button round @click="dialogDictation = false">完成</el-button>
            <el-button type="primary" round @click="openDictation">再听一轮 </el-button>
            <el-button v-if="dictErrors.length" type="warning" round @click="retryDictErrors">
              只练错词 </el-button>
          </template>
        </template>
      </el-dialog>

      <!-- ══════ 错题复习弹窗 ══════ -->
      <el-dialog v-model="dialogErrorReview" title="错题复习" width="600px" :close-on-click-modal="false" class="quiz-dialog">
        <!-- 数量选择 -->
        <template v-if="errorReviewPhase === 'countSelect'">
          <div class="count-select-area">
            <p class="count-select-title">复习多少个错题？</p>
            <p class="count-select-hint">当前有 {{ errorUnreviewedCount }} 个待复习错题</p>
            <div class="count-slider-wrap">
              <el-slider
                v-model="errorReviewCount"
                :min="Math.min(5, errorUnreviewedCount || 5)"
                :max="Math.max(5, errorUnreviewedCount || 10)"
                :step="5"
                show-input
                class="count-slider"
              />
            </div>
            <el-button type="primary" size="large" round @click="beginErrorReview" :loading="quizLoading" class="start-btn">
              开始复习
            </el-button>
          </div>
        </template>

        <!-- 复习中 -->
        <template v-if="errorReviewPhase === 'reviewing'">
          <div class="quiz-phase-header">
            <span class="phase-badge phase1-badge">错题复习</span>
            <span class="phase-progress">{{ errorReviewCurrent + 1 }} / {{ errorReviewWords.length }}</span>
          </div>
          <div class="quiz-progress-bar">
            <div class="quiz-progress-fill" :style="{ width: (errorReviewCurrent / errorReviewWords.length * 100) + '%' }"></div>
          </div>

          <div v-if="errorReviewWords[errorReviewCurrent]" class="phase1-card">
            <div class="phase1-word-display">
              <span class="phase1-word">{{ errorReviewWords[errorReviewCurrent].word }}</span>
              <span v-if="errorReviewWords[errorReviewCurrent].partOfSpeech" class="phase1-pos">
                {{ errorReviewWords[errorReviewCurrent].partOfSpeech }}
              </span>
            </div>
            <p class="phase1-question">请选择以上单词的中文意思：</p>
            <el-radio-group v-model="errorReviewAnswer" class="phase1-options" :disabled="!!errorReviewFeedback?.showDetails || !!errorReviewFeedback?.finalAttempt">
              <el-radio
                v-for="opt in errorReviewWords[errorReviewCurrent].options"
                :key="opt"
                :value="opt"
                class="phase1-option"
              >
                {{ opt }}
              </el-radio>
            </el-radio-group>

            <transition name="feedback-fade">
              <div v-if="errorReviewFeedback" class="phase1-feedback" :class="errorReviewFeedback.correct ? 'feedback-ok' : 'feedback-bad'">
                <p class="feedback-msg">{{ errorReviewFeedback.message }}</p>
              </div>
            </transition>

            <!-- 选项详细信息面板（选对后或最终失败后展示） -->
            <transition name="feedback-fade">
              <div v-if="errorReviewFeedback?.showDetails || errorReviewFeedback?.finalAttempt" class="option-details-panel">
                <h4 class="option-details-title">全部选项解析</h4>
                <div
                  v-for="(detail, i) in errorReviewWords[errorReviewCurrent].optionDetails"
                  :key="i"
                  class="option-detail-item"
                  :class="{ 'option-detail-correct': detail.optionText === errorReviewWords[errorReviewCurrent].translation }"
                >
                  <div class="option-detail-head">
                    <span class="option-detail-num">{{ ['A','B','C','D'][i] }}</span>
                    <span class="option-detail-text">{{ detail.optionText }}</span>
                    <span class="option-detail-arrow">←</span>
                    <span class="option-detail-word">{{ detail.sourceWord }}</span>
                    <span v-if="detail.partOfSpeech && detail.partOfSpeech !== '—'" class="option-detail-pos">{{ detail.partOfSpeech }}</span>
                  </div>
                  <div v-if="detail.extensions" class="option-detail-ext">
                    {{ detail.extensions.split(',').filter(Boolean).join('、') }}
                  </div>
                </div>
              </div>
            </transition>
          </div>
        </template>

        <!-- 复习完成 -->
        <template v-if="errorReviewPhase === 'done'">
          <div class="quiz-result">
            <div
              class="score-ring"
              :style="{ '--score-percent': (errorReviewCorrect / errorReviewWords.length * 100) + '%' }"
            >
              <div class="score-inner">
                <span class="score-num">{{ errorReviewCorrect }}</span>
                <span class="score-total">/ {{ errorReviewWords.length }}</span>
              </div>
            </div>
            <p class="score-msg">
              {{ errorReviewCorrect === errorReviewWords.length ? '全部正确！' : '继续加油，错题已重新记录！' }}
            </p>
            <div v-if="errorReviewErrors.length" class="report-section">
              <el-divider>仍需要复习的单词</el-divider>
              <div v-for="(e, i) in errorReviewErrors" :key="'re'+i" class="quiz-report-item report-wrong">
                <span class="report-num">{{ i + 1 }}. {{ e.word }}</span>
                <span class="report-badge badge-no"></span>
                <p class="report-correct-ans">正确答案：<strong>{{ e.correctAnswer }}</strong></p>
              </div>
            </div>
          </div>
        </template>

        <template #footer>
          <template v-if="errorReviewPhase === 'reviewing'">
            <el-button round @click="dialogErrorReview = false">关闭</el-button>
            <el-button
              v-if="!errorReviewFeedback?.showDetails && !errorReviewFeedback?.finalAttempt"
              type="primary"
              round
              @click="confirmErrorReview"
              :disabled="!errorReviewAnswer"
            >
              确认
            </el-button>
            <el-button
              v-else
              type="success"
              round
              @click="nextErrorReviewWord"
            >
              {{ errorReviewCurrent + 1 >= errorReviewWords.length ? '查看结果 →' : '下一题 →' }}
            </el-button>
          </template>
          <template v-if="errorReviewPhase === 'done'">
            <el-button round @click="dialogErrorReview = false; loadErrorRecords()">关闭</el-button>
            <el-button type="primary" round @click="startErrorReview">再来一次 </el-button>
          </template>
        </template>
      </el-dialog>
    </div>

    <!-- ═══════════════ 学习资料 ═══════════════ -->
    <div v-if="currentTab === 'resources'">
      <div class="resource-toolbar">
        <el-input
          v-model="resourceKeyword"
          placeholder="搜索标题..."
          clearable
          @change="loadResources"
          class="resource-search"
        >
          <template #prefix></template>
        </el-input>
        <el-select v-model="resourceSubjectId" placeholder="学科" @change="onResourceSubjectChange" class="resource-type-select">
          <el-option v-for="s in subjects" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <el-select v-model="resourceType" placeholder="全部类型" @change="loadResources" class="resource-type-select">
          <el-option label="全部" value="" />
          <el-option v-for="t in resourceTypeOptions" :key="t" :label="t" :value="t" />
        </el-select>
      </div>

      <!-- 骨架屏 -->
      <div v-if="loading" class="resource-list">
        <div v-for="i in 3" :key="i" class="skeleton-res-card">
          <div class="sk-line sk-r-title"></div>
          <div class="sk-line sk-r-body"></div>
        </div>
      </div>

      <!-- 资料卡片 -->
      <div v-else-if="resourceItems.length" class="resource-list">
        <div
          v-for="item in resourceItems"
          :key="item.id"
          class="res-card"
          :class="'res-card--' + (item.type || 'default')"
          @click="$router.push('/resource/' + item.id)"
        >
          <div class="res-card-accent"></div>
          <div class="res-card-body">
            <h4 class="res-card-title">{{ item.title }}</h4>
            <span class="res-type-tag">{{ typeLabel(item.type) }}</span>
            <p v-if="item.content" class="res-card-preview">
              {{ item.content.slice(0, 80) }}{{ item.content.length > 80 ? '...' : '' }}
            </p>
            <span v-else class="res-card-hint">点击查看详情 →</span>
          </div>
        </div>
      </div>

      <EmptyState v-else text="还没有学习资料。" />

      <div v-if="resourceTotal > 6" class="pagination-wrap">
        <el-pagination
          v-model:current-page="resourcePage"
          :total="resourceTotal"
          :page-size="6"
          layout="prev, pager, next"
          background
          @change="loadResources"
        />
      </div>
    </div>

    <!-- ═══════════════ 提交作业弹窗 ═══════════════ -->
    <el-dialog v-model="dialogSubmit" title="提交作业" width="550px" class="submit-dialog">
      <el-input
        v-model="submitAnswer"
        type="textarea"
        :rows="5"
        placeholder="请输入文本答案（可选）"
      />
      <div class="upload-bar">
        <input
          ref="cameraInput"
          type="file"
          accept="image/*"
          capture="environment"
          style="display:none"
          @change="onCameraCapture"
        />
        <el-button type="success" round @click="$refs.cameraInput.click()" class="upload-btn upload-btn--camera">
          拍照
        </el-button>
        <el-upload
          :http-request="uploadFile"
          :show-file-list="false"
          accept="image/*"
          class="upload-inline"
        >
          <el-button type="primary" round plain class="upload-btn">添加图片</el-button>
        </el-upload>
        <span class="upload-hint">可多次点击添加多张，拍照自动压缩</span>
      </div>

      <!-- 图片预览网格 -->
      <div v-if="uploadedUrls.length" class="preview-grid">
        <div
          v-for="(url, i) in uploadedUrls"
          :key="i"
          class="preview-item"
        >
          <el-image
            :src="fileUrl(url)"
            fit="cover"
            :preview-src-list="uploadedUrls.map(fileUrl)"
            :initial-index="i"
            class="preview-img"
          />
          <button class="preview-del" @click="uploadedUrls.splice(i,1)" title="删除">×</button>
        </div>
      </div>

      <template #footer>
        <el-button round @click="dialogSubmit = false">取消</el-button>
        <el-button type="primary" round @click="doSubmit" :loading="submitting">确认提交</el-button>
      </template>
    </el-dialog>

    <!-- ═══════════════ 批改结果弹窗 ═══════════════ -->
    <el-dialog v-model="dialogReview" title="批改结果" width="550px" class="review-dialog">
      <div v-if="reviewData" v-loading="reviewLoading">
        <!-- 分数环 -->
        <div class="review-score-section">
          <div
            class="review-score-ring"
            :style="{ '--review-percent': (reviewData.review.score / 100 * 100) + '%' }"
          >
            <div class="review-score-inner">
              <span class="review-score-num" :class="scoreColor(reviewData.review.score)">
                {{ reviewData.review.score }}
              </span>
              <span class="review-score-unit">分</span>
            </div>
          </div>
          <el-tag v-if="reviewData.review.needRedo === 'true'" type="danger" round size="large">
            需重做
          </el-tag>
        </div>

        <!-- 作答内容 -->
        <div v-if="reviewData.submit" class="review-answer-box">
          <h5>我的作答</h5>
          <div v-if="reviewData.submit.textAnswer" class="review-text-answer">
            {{ reviewData.submit.textAnswer }}
          </div>
          <div v-if="submitImages.length" class="review-images">
            <el-image
              v-for="(url, i) in submitImages"
              :key="i"
              :src="fileUrl(url)"
              fit="cover"
              :preview-src-list="submitImages.map(fileUrl)"
              :initial-index="i"
              class="review-img-thumb"
            />
          </div>
          <span v-if="!reviewData.submit.textAnswer && !submitImages.length" class="review-no-content">
            仅提交图片，无文字
          </span>
        </div>

        <el-divider />

        <!-- 老师评语 -->
        <div class="review-comment-box">
          <h5>老师评语</h5>
          <p>{{ reviewData.review.comment || '暂无评语' }}</p>
          <!-- 语音评语：听老师的语气和发音示范 -->
          <div v-if="reviewData.review.voiceUrl" class="review-voice-play">
            <span class="review-voice-tip">老师语音评语</span>
            <audio :src="fileUrl(reviewData.review.voiceUrl)" controls class="review-voice-audio" />
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无批改结果" />
    </el-dialog>

    <!-- ═══════════════ 作业详情弹窗 ═══════════════ -->
    <el-dialog v-model="dialogDetail" title="作业详情" width="600px" class="detail-dialog">
      <div v-if="homeworkDetail" v-loading="detailLoading">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="作业标题" :span="2">
            <strong>{{ homeworkDetail.title }}</strong>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <span class="hw-status" :class="'hw-status--' + statusClass(homeworkDetail.status)">
              {{ homeworkDetail.status }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="截止时间">
            {{ homeworkDetail.deadline || '无截止时间' }}
          </el-descriptions-item>
          <el-descriptions-item label="作业内容" :span="2">
            <div class="detail-content-text">
              {{ homeworkDetail.content || '无文字内容' }}
            </div>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 附件图片 -->
        <div v-if="detailImages.length" style="margin-top:16px">
          <h4 style="margin:0 0 8px 0;color:var(--color-text-regular)">附件图片</h4>
          <div style="display:flex;flex-wrap:wrap;gap:8px">
            <el-image
              v-for="(url, i) in detailImages"
              :key="i"
              :src="fileUrl(url)"
              style="width:140px;height:140px;border-radius:8px;border:1px solid var(--border);cursor:pointer"
              fit="cover"
              :preview-src-list="detailImages.map(fileUrl)"
              :initial-index="i"
            />
          </div>
        </div>
      </div>
      <el-empty v-else-if="!detailLoading" description="作业不存在" />
    </el-dialog>

    <!-- ═══════════════ 答题卡作答弹窗 ═══════════════ -->
    <el-dialog
      v-model="dialogPaper"
      :title="'' + (paperHomework?.title || '答题卡')"
      width="720px"
      class="paper-dialog"
      :close-on-click-modal="false"
    >
      <div v-loading="paperLoading">
        <div v-if="paperQuestionCount" class="paper-bar">
          <span v-if="paperHomework?.subject">{{ paperHomework.subject }}</span>
          <span>共 <strong>{{ paperQuestionCount }}</strong> 题，满分 <strong>{{ paperFullScore }}</strong> 分</span>
          <span>已答 <strong>{{ paperAnsweredCount }}</strong> 题</span>
        </div>
        <div v-if="paperHomework?.content" class="paper-tip">{{ paperHomework.content }}</div>

        <div v-for="(sec, si) in paperSections" :key="si" class="paper-sec">
          <div v-if="sec.sectionType !== 'standalone'" class="paper-sec-head">
            <span class="paper-sec-title">{{ sec.title || sectionTypeLabel(sec.sectionType) }}</span>
            <span class="paper-sec-badge">{{ sectionTypeLabel(sec.sectionType) }}</span>
          </div>
          <div v-if="sec.instruction" class="paper-sec-inst">{{ sec.instruction }}</div>

          <!-- 材料原文：默认展开，做题时可以折起来腾出屏幕 -->
          <div v-if="sec.passage" class="paper-passage">
            <div class="paper-passage-bar" @click="passageOpen[si] = !passageOpen[si]">
              <span>材料原文</span>
              <span class="paper-passage-toggle">{{ passageOpen[si] ? '收起 ▲' : '展开 ▼' }}</span>
            </div>
            <div v-show="passageOpen[si]" class="paper-passage-text">{{ sec.passage }}</div>
          </div>

          <!-- 选词填空的备选词库 -->
          <div v-if="sec.wordBank && sec.wordBank.length" class="paper-bank">
            <span class="paper-bank-label">备选词</span>
            <el-tag v-for="(w, wi) in sec.wordBank" :key="wi" size="small" round class="paper-bank-tag">{{ w }}</el-tag>
          </div>

          <!-- 整个大题共用的选项框（补全对话七选五） -->
          <div v-if="sec.sharedOptions && sec.sharedOptions.length" class="paper-shared">
            <div class="paper-bank-label">选项</div>
            <div v-for="o in sec.sharedOptions" :key="o.key" class="paper-shared-opt">
              <strong>{{ o.key }}.</strong> {{ o.text }}
            </div>
          </div>

          <div v-for="q in sec.questions" :key="q.id" class="paper-q">
            <div class="paper-q-head">
              <span class="paper-q-seq">{{ q.seq }}</span>
              <span class="paper-q-type">{{ questionTypeLabel(q.questionType) }}</span>
              <span class="paper-q-score">{{ q.score }} 分</span>
            </div>
            <div class="paper-q-content">{{ q.content }}</div>

            <!-- 单选、判断；补全对话这种没自带选项的，用大题共用选项框 -->
            <el-radio-group
              v-if="q.questionType === 'single' || q.questionType === 'judge'"
              v-model="paperAnswers[q.id]"
              class="paper-opts"
              :class="{ 'paper-opts-inline': isFillSection(sec.sectionType) }"
            >
              <el-radio v-for="o in optionsOf(sec, q)" :key="o.key" :value="o.key" class="paper-opt">
                {{ o.key }}. {{ o.text }}
              </el-radio>
            </el-radio-group>

            <!-- 多选：勾多个，交卷时拼成 "AC" 这样的字符串 -->
            <el-checkbox-group
              v-else-if="q.questionType === 'multi'"
              v-model="paperMulti[q.id]"
              class="paper-opts"
            >
              <el-checkbox v-for="o in optionsOf(sec, q)" :key="o.key" :value="o.key" class="paper-opt">
                {{ o.key }}. {{ o.text }}
              </el-checkbox>
            </el-checkbox-group>

            <!-- 填空：文章里的空答案短，输入框不必占整行 -->
            <el-input
              v-else-if="q.questionType === 'blank'"
              v-model="paperAnswers[q.id]"
              :style="isFillSection(sec.sectionType) ? 'max-width:260px' : ''"
              placeholder="在这里填写答案"
            />

            <!-- 简答、写作 -->
            <el-input
              v-else
              v-model="paperAnswers[q.id]"
              type="textarea"
              :rows="['writing', 'solution'].includes(sec.sectionType) ? 8 : 4"
              placeholder="在这里作答"
            />
          </div>
        </div>

        <el-empty v-if="!paperLoading && !paperQuestionCount" description="这张答题卡还没有题目" />
      </div>
      <template #footer>
        <el-button round @click="dialogPaper = false">稍后再做</el-button>
        <el-button
          type="primary"
          round
          :loading="paperSubmitting"
          :disabled="!paperQuestionCount"
          @click="doSubmitPaper"
        >
          交卷
        </el-button>
      </template>
    </el-dialog>

    <!-- ═══════════════ 答题卡结果弹窗 ═══════════════ -->
    <el-dialog v-model="dialogSheetResult" title="答题卡批改结果" width="720px" class="paper-dialog">
      <div v-loading="sheetResultLoading">
        <template v-if="sheetResultData">
          <div class="paper-result-bar">
            <div class="paper-result-score">
              <span class="paper-result-num" :class="scoreColor(sheetResultData.review ? sheetResultData.review.score : 0)">
                {{ sheetResultData.gotScore }}
              </span>
              <span class="paper-result-unit">/ {{ sheetResultData.fullScore }} 分</span>
            </div>
            <div class="paper-result-meta">
              <span>全对 <strong>{{ sheetResultData.rightCount }}</strong> / {{ sheetResultData.total }} 题</span>
              <span v-if="sheetResultData.review">换算百分制 <strong>{{ sheetResultData.review.score }}</strong> 分</span>
            </div>
          </div>
          <div v-if="sheetResultData.review && sheetResultData.review.comment" class="paper-tip">
            {{ sheetResultData.review.comment }}
          </div>

          <div v-for="(g, gi) in resultGroups" :key="gi" class="paper-sec">
            <div v-if="g.sectionType !== 'standalone'" class="paper-sec-head">
              <span class="paper-sec-title">{{ g.title || sectionTypeLabel(g.sectionType) }}</span>
              <span class="paper-sec-badge">{{ sectionTypeLabel(g.sectionType) }}</span>
            </div>
            <!-- 看错题时能回去重读原文，默认收起不占屏幕 -->
            <div v-if="g.passage" class="paper-passage">
              <div class="paper-passage-bar" @click="resultPassageOpen[gi] = !resultPassageOpen[gi]">
                <span>材料原文</span>
                <span class="paper-passage-toggle">{{ resultPassageOpen[gi] ? '收起 ▲' : '展开 ▼' }}</span>
              </div>
              <div v-show="resultPassageOpen[gi]" class="paper-passage-text">{{ g.passage }}</div>
            </div>
            <div v-if="g.wordBank && g.wordBank.length" class="paper-bank">
              <span class="paper-bank-label">备选词</span>
              <el-tag v-for="(w, wi) in g.wordBank" :key="wi" size="small" round class="paper-bank-tag">{{ w }}</el-tag>
            </div>

            <div
              v-for="it in g.items"
              :key="it.questionId"
              class="paper-r-card"
              :class="it.correct === 1 ? 'paper-r-right' : (it.correct === 0 ? 'paper-r-wrong' : 'paper-r-pending')"
            >
              <div class="paper-q-head">
                <span class="paper-q-seq">{{ it.seq }}</span>
                <span class="paper-q-type">{{ questionTypeLabel(it.questionType) }}</span>
                <span class="paper-r-flag">
                  {{ it.correct === 1 ? '全对' : (it.correct === 0 ? '错了' : '待老师批改') }}
                  {{ it.gotScore }} / {{ it.score }} 分
                </span>
              </div>
              <div class="paper-q-content">{{ it.content }}</div>
              <div class="paper-r-row">
                <span class="paper-r-label">我的答案</span>
                <span :class="it.correct === 0 ? 'paper-r-mine-wrong' : ''">{{ it.studentAnswer || '（未作答）' }}</span>
              </div>
              <div class="paper-r-row">
                <span class="paper-r-label">正确答案</span>
                <strong>{{ it.standardAnswer }}</strong>
              </div>
              <div v-if="it.analysis" class="paper-r-row">
                <span class="paper-r-label">解析</span>{{ it.analysis }}
              </div>
              <div v-if="it.aiComment" class="paper-r-row">
                <span class="paper-r-label">点评</span>{{ it.aiComment }}
              </div>
              <div v-if="it.knowledgePoint" class="paper-r-row">
                <span class="paper-r-label">知识点</span>
                <el-tag size="small" round>{{ it.knowledgePoint }}</el-tag>
              </div>
            </div>
          </div>
        </template>
        <el-empty v-else-if="!sheetResultLoading" description="还没有批改结果" />
      </div>
      <template #footer>
        <el-button type="primary" round @click="dialogSheetResult = false">知道了</el-button>
      </template>
    </el-dialog>

    <!-- ═══════════════ 单词详情弹窗 ═══════════════ -->
    <el-dialog v-model="dialogWordDetail" :title="'' + (detailWord?.word || '')" width="420px" class="word-detail-dialog">
      <el-descriptions v-if="detailWord" :column="1" border>
        <el-descriptions-item label="单词">
          <strong>{{ detailWord.word }}</strong>
          <span class="speak-btn" title="播放发音" @click="speak(detailWord.word)">发音</span>
        </el-descriptions-item>
        <el-descriptions-item label="读音">{{ detailWord.phonetic || '-' }}</el-descriptions-item>
        <el-descriptions-item label="意思">{{ detailWord.translation }}</el-descriptions-item>
        <el-descriptions-item label="词性">{{ detailWord.partOfSpeech || '-' }}</el-descriptions-item>
        <el-descriptions-item label="延伸词">
          <el-tag
            v-for="(ext, i) in (detailWord.extensions || '').split(',').filter(Boolean)"
            :key="i"
            size="small"
            round
            class="ext-tag"
          >{{ ext }}</el-tag>
          <span v-if="!detailWord.extensions">-</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import EmptyState from '../components/ui/EmptyState.vue';
import { useRoute, useRouter } from 'vue-router';
import {
  homeworkList, getHomework, reviewResult, submitHomework, upload,
  vocabList as vocabListApi, vocabSearch, randomVocab,
  resourceList as resourceListApi, resourceTypes,
  subjectList,
  overdueHomework,
  startQuiz, getSpellWords, checkSpelling, recordQuizError,
  getQuizErrors, reviewErrors, markErrorMastered, reviewMastered, forgetError, deleteQuizError, errorStats, markWordMastered,
  getTodayReview, submitReviewResult,
  answerSheetPaper, submitAnswerSheet, answerSheetResult
} from '../api';
import { ElMessage, ElMessageBox } from 'element-plus';
import { speakWord } from '../utils/speech';
import { useResourceStore } from '../stores/resource';
const route = useRoute();
const resourceStore = useResourceStore();
const router = useRouter();
const currentTab = computed(() => route.meta.tab || 'homework');
const apiBase = import.meta.env.VITE_API_BASE_URL || '';
const fileUrl = (url) => (url && url.startsWith('http')) ? url : (apiBase + url);
const loading = ref(false);

// --- 作业 ---
const homeworkItems = ref([]);
const overdueItems = ref([]);
const statusFilter = ref(null);  // null=全部, '未提交', '需重做'
const subjectFilter = ref(null); // null=全部学科
const SUBJECT_ORDER = ['语文', '数学', '英语', '政治', '历史', '地理', '物理', '化学', '生物'];
// 我现有的作业学科（用于学科筛选）
const homeworkSubjects = computed(() => {
  const set = new Set(homeworkItems.value.map(h => h.subject || '英语'));
  return [...set].sort((a, b) => {
    const ia = SUBJECT_ORDER.indexOf(a); const ib = SUBJECT_ORDER.indexOf(b);
    return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib);
  });
});
const subjectScopedHomeworks = computed(() => {
  if (!subjectFilter.value) return homeworkItems.value;
  return homeworkItems.value.filter(h => (h.subject || '英语') === subjectFilter.value);
});
const hwStatusCounts = computed(() => {
  const items = subjectScopedHomeworks.value;
  return {
    pending: items.filter(h => h.status === '未提交').length,
    redo: items.filter(h => h.status === '需重做').length,
  };
});
const displayedHomeworks = computed(() => {
  if (!statusFilter.value) return subjectScopedHomeworks.value;
  return subjectScopedHomeworks.value.filter(h => h.status === statusFilter.value);
});
// 按学科分组展示（标准 9 科优先）
const homeworksBySubject = computed(() => {
  const groups = new Map();
  for (const h of displayedHomeworks.value) {
    const s = h.subject || '英语';
    if (!groups.has(s)) groups.set(s, []);
    groups.get(s).push(h);
  }
  return [...groups.entries()]
    .sort((a, b) => {
      const ia = SUBJECT_ORDER.indexOf(a[0]); const ib = SUBJECT_ORDER.indexOf(b[0]);
      return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib);
    })
    .map(([subject, items]) => ({ subject, items }));
});
const dialogSubmit = ref(false);
const dialogReview = ref(false);
const dialogDetail = ref(false);
const currentHomework = ref(null);
const homeworkDetail = ref(null);
const detailImages = ref([]);
const submitAnswer = ref('');
const uploadedUrls = ref([]);
const reviewData = ref(null);
const reviewLoading = ref(false);
const submitImages = ref([]);
const submitting = ref(false);
const detailLoading = ref(false);

// --- 答题卡 ---
const dialogPaper = ref(false);
const paperLoading = ref(false);
const paperSubmitting = ref(false);
const paperHomework = ref(null);
const paperSections = ref([]);
// 折叠状态按大题下标记，默认展开文章
const passageOpen = reactive({});
// 单选/判断/填空/简答存字符串，多选单独用数组接 el-checkbox-group
const paperAnswers = reactive({});
const paperMulti = reactive({});
// 拉平后的小题：统分、统已答、交卷都不关心分层
const paperQuestions = computed(() =>
  paperSections.value.flatMap(s => s.questions || []));
const paperQuestionCount = computed(() => paperQuestions.value.length);
const paperFullScore = computed(() =>
  paperQuestions.value.reduce((sum, q) => sum + (Number(q.score) || 0), 0));
const paperAnsweredCount = computed(() =>
  paperQuestions.value.filter(q => {
    if (q.questionType === 'multi') return (paperMulti[q.id] || []).length > 0;
    return !!(paperAnswers[q.id] && String(paperAnswers[q.id]).trim());
  }).length);
const dialogSheetResult = ref(false);
const sheetResultLoading = ref(false);
const sheetResultData = ref(null);
// 结果页的原文默认收起，看的人主要关心对错
const resultPassageOpen = reactive({});
/** 逐题结果按大题分组，材料从后端返回的 sections 里取 */
const resultGroups = computed(() => {
  const d = sheetResultData.value;
  if (!d || !d.items) return [];
  const metaBySeq = {};
  (d.sections || []).forEach(s => { metaBySeq[s.seq] = s; });
  const bySeq = {};
  const groups = [];
  d.items.forEach(it => {
    const key = it.sectionSeq == null ? 0 : it.sectionSeq;
    if (!bySeq[key]) {
      const meta = metaBySeq[key] || {};
      bySeq[key] = {
        sectionType: it.sectionType || meta.sectionType || 'standalone',
        title: it.sectionTitle || meta.title || '',
        passage: meta.passage || '',
        wordBank: meta.wordBank || [],
        items: []
      };
      groups.push(bySeq[key]);
    }
    bySeq[key].items.push(it);
  });
  return groups;
});

// --- 词汇 ---
const vocabularies = ref([]);
const vocabPage = ref(1);
const vocabTotal = ref(0);
const vocabKeyword = ref('');
const vocabViewMode = ref('list'); // 'list' | 'error' | 'review' | 'mastered'
const bookCounts = reactive({ error: 0, review: 0, mastered: 0 });

// --- 测验（两阶段） ---
const dialogQuiz = ref(false);
// 阶段: 'countSelect' | 'phase1' | 'phase2' | 'report'
const quizPhase = ref('countSelect');
const quizWordCount = ref(10);
const quizWords = ref([]);          // 阶段一的单词（QuizWordDTO[]）
const quizCurrentIndex = ref(0);    // 当前题目索引
const quizCurrentAnswer = ref('');  // 当前选择的答案
const quizFeedback = ref(null);     // { correct, message, extensions }
const quizPhase1Errors = ref([]);   // 阶段一错题 {wordId, word, wrongAnswer, correctAnswer}
const quizPhase2Words = ref([]);    // 阶段二的单词（SpellWordDTO[]）
const quizPhase2Index = ref(0);
const quizPhase2Input = ref('');
const quizPhase2Feedback = ref(null);
const quizPhase2Errors = ref([]);   // 阶段二错题
const quizPhase2Correct = ref(0);
const quizP1Passed = ref([]);   // 阶段一答对过的单词 id
const quizP2Passed = ref([]);   // 阶段二拼写通过过的单词 id
const quizFlagged = ref([]);    // 阶段一答对但被标记"不熟"的单词 id
const quizMastered = ref(0);    // 两阶段都通过 → 已掌握本的数量
// 每题的尝试次数追踪（用于给予2次重试机会）
const quizAttemptCount = ref(0);     // 阶段一当前题已尝试次数
const quizPhase2AttemptCount = ref(0); // 阶段二当前题已尝试次数
const quizLoading = ref(false);

// --- 听写模式（听音拼写） ---
const dialogDictation = ref(false);
const dictPhase = ref('setup');      // 'setup' | 'listen' | 'report'
const dictScope = ref('all');        // 'all' 全词库 | 'errors' 错题本
const dictCount = ref(10);
const dictWords = ref([]);           // 本轮听写的单词
const dictIndex = ref(0);
const dictInput = ref('');
const dictFeedback = ref(null);      // { correct, message }
const dictHintVisible = ref(false);  // 是否已看提示
const dictCorrect = ref(0);
const dictErrors = ref([]);          // 听错的词 {wordId, word, translation, wrongAnswer, correctAnswer}
const dictLoading = ref(false);

// --- 错题库 ---
const errorRecords = ref([]);
const errorTotal = ref(0);
const errorPage = ref(1);
const errorUnreviewedCount = ref(0);
const dialogErrorReview = ref(false);
const errorReviewWords = ref([]);
const errorReviewPhase = ref('countSelect'); // 'countSelect' | 'reviewing' | 'done'
const errorReviewCount = ref(10);
const errorReviewCurrent = ref(0);
const errorReviewAnswer = ref('');
const errorReviewFeedback = ref(null);
const errorReviewCorrect = ref(0);
const errorReviewErrors = ref([]);
const errorReviewAttemptCount = ref(0);  // 错题复习每题尝试次数

// --- 今日复习（艾宾浩斯记忆曲线） ---
const dueReviewCount = ref(0);          // 今日期需复习数（入口徽标）
const dialogTodayReview = ref(false);
const trLoading = ref(false);
const trCards = ref([]);                // 到期待复习的错词记录
const trIndex = ref(0);
const trRevealed = ref(false);          // 当前卡片是否已翻开释义
const trRemembered = ref(0);            // 本轮记住的个数

// Word detail
const dialogWordDetail = ref(false);
const detailWord = ref(null);
function showWordDetail(word) { detailWord.value = word; dialogWordDetail.value = true; }

// --- 资料 ---
const resourceItems = ref([]);
const resourceKeyword = ref('');
const resourceType = ref('');
const resourceTypeOptions = ref([]);
const resourcePage = ref(1);
const resourceTotal = ref(0);
// 学科筛选（默认选中“英语”）
const subjects = ref([]);
const resourceSubjectId = ref(null);

// ======== Tab 页头信息 ========
const tabIcon = computed(() => {
  const m = { homework: '', vocabulary: '', resources: '' };
  return m[currentTab.value] || '';
});
const tabTitle = computed(() => {
  const m = { homework: '我的作业', vocabulary: '词汇学习', resources: '学习资料' };
  return m[currentTab.value] || '';
});
const tabSubtitle = computed(() => {
  const m = { homework: '查看并提交老师布置的作业', vocabulary: '背单词、做测验、复习错题，积累词汇量', resources: '浏览老师分享的学习资料' };
  return m[currentTab.value] || '';
});
const quizDialogTitle = computed(() => {
  const m = {
    countSelect: '看词选意',
    phase1: '阶段一：看词选义',
    phase2: '阶段二：看义写词',
    report: '测验报告'
  };
  return m[quizPhase.value] || '看词选意';
});

// ======== Helpers ========
function statusTag(s) {
  const m = { '未提交': 'info', '已提交': 'warning', '已批改': 'success', '需重做': 'danger' };
  return m[s] || 'info';
}
function statusClass(s) {
  const m = { '未提交': 'pending', '已提交': 'submitted', '已批改': 'graded', '需重做': 'redo' };
  return m[s] || 'pending';
}
function isOverdue(h) {
  if (!h.deadline) return false;
  return h.status === '未提交' || h.status === '需重做';
}
function scoreColor(score) {
  if (score >= 80) return 'score-high';
  if (score >= 60) return 'score-mid';
  return 'score-low';
}
function typeTag(t) {
  const m = { grammar: 'primary', reading: 'success', listening: 'warning', writing: 'danger' };
  return m[t] || 'info';
}
function typeLabel(t) {
  const m = { grammar: '语法', reading: '阅读', listening: '听力', writing: '写作' };
  return m[t] || t;
}

// ======== 作业 ========
async function loadHomework() {
  loading.value = true;
  try {
    homeworkItems.value = await homeworkList();
    overdueItems.value = await overdueHomework().catch(() => []);
  } finally { loading.value = false; }
}
function openSubmit(row) { currentHomework.value = row; submitAnswer.value = ''; uploadedUrls.value = []; dialogSubmit.value = true; }

// el-upload 自定义上传（相册选择）
async function uploadFile(option) {
  try {
    const r = await upload(option.file);
    uploadedUrls.value.push(r.fileUrl);
    option.onSuccess(r);
  } catch (err) {
    option.onError(err);
  }
}

// 拍照上传处理
const cameraInput = ref(null);
async function onCameraCapture(event) {
  const file = event.target.files[0];
  if (!file) return;
  try {
    const r = await upload(file);
    uploadedUrls.value.push(r.fileUrl);
    ElMessage.success('拍照上传成功');
  } catch (err) {
    // 错误已在 request.js 拦截器中通过 ElMessage.error 展示
  } finally {
    event.target.value = '';
  }
}
async function doSubmit() {
  submitting.value = true;
  try {
    await submitHomework({ homeworkId: currentHomework.value.id, textAnswer: submitAnswer.value, fileUrl: uploadedUrls.value.join(',') });
    ElMessage.success('提交成功');
    dialogSubmit.value = false;
    await loadHomework();
  } finally { submitting.value = false; }
}

// ======== 答题卡 ========
function questionTypeLabel(t) {
  const m = { single: '单选', multi: '多选', judge: '判断', blank: '填空', short: '简答' };
  return m[t] || '题目';
}

function sectionTypeLabel(t) {
  const m = {
    cloze: '完形填空', reading: '阅读理解', word_bank: '选词填空',
    blank_fill: '盲填', dialogue: '补全对话', solution: '解答题',
    writing: '书面表达', standalone: '单句题'
  };
  return m[t] || '题目';
}


/** 只是文章里一个空的题型，选项和输入框横着摆更紧凑 */
function isFillSection(t) {
  return t === 'cloze' || t === 'word_bank' || t === 'blank_fill';
}

/** 小题自带选项就用自己的，否则回退到大题共用选项框（七选五） */
function optionsOf(sec, q) {
  if (q.options && q.options.length) return q.options;
  return sec.sharedOptions || [];
}

async function openPaper(item) {
  paperHomework.value = item;
  paperSections.value = [];
  // 换卷时清掉上一张的作答，避免答案串到新题上
  Object.keys(paperAnswers).forEach(k => delete paperAnswers[k]);
  Object.keys(paperMulti).forEach(k => delete paperMulti[k]);
  Object.keys(passageOpen).forEach(k => delete passageOpen[k]);
  dialogPaper.value = true;
  paperLoading.value = true;
  try {
    const list = await answerSheetPaper(item.id) || [];
    paperSections.value = list;
    list.forEach((sec, si) => {
      passageOpen[si] = true;
      (sec.questions || []).forEach(q => {
        if (q.questionType === 'multi') paperMulti[q.id] = [];
        else paperAnswers[q.id] = '';
      });
    });
  } finally {
    paperLoading.value = false;
  }
}

async function doSubmitPaper() {
  const answers = paperQuestions.value.map(q => ({
    questionId: q.id,
    // 多选拼成 "AC"，后端判分时忽略选项顺序
    answer: q.questionType === 'multi'
      ? [...(paperMulti[q.id] || [])].sort().join('')
      : (paperAnswers[q.id] || '')
  }));
  const unanswered = answers.filter(a => !String(a.answer).trim()).length;
  if (unanswered) {
    try {
      await ElMessageBox.confirm(`还有 ${unanswered} 题没作答，未作答的题会计 0 分，确定交卷？`,
        '提示', { type: 'warning', confirmButtonText: '确定交卷', cancelButtonText: '再检查下' });
    } catch (e) { return; }
  }

  paperSubmitting.value = true;
  try {
    const r = await submitAnswerSheet({ homeworkId: paperHomework.value.id, answers });
    dialogPaper.value = false;
    ElMessage.success(`交卷成功，得分 ${r.gotScore}/${r.fullScore}，全对 ${r.rightCount}/${r.total} 题`);
    await loadHomework();
    // 判分是同步完成的，直接展开逐题对错与解析
    await showSheetResult(r.submitId);
  } finally {
    paperSubmitting.value = false;
  }
}

/** 从作业卡片进入结果页：先拿到提交记录 id，再拉逐题结果 */
async function openSheetResult(item) {
  dialogSheetResult.value = true;
  sheetResultData.value = null;
  sheetResultLoading.value = true;
  try {
    const d = await reviewResult(item.id).catch(() => null);
    if (!d || !d.submit) return;
    sheetResultData.value = await answerSheetResult(d.submit.id);
  } finally {
    sheetResultLoading.value = false;
  }
}

async function showSheetResult(submitId) {
  dialogSheetResult.value = true;
  sheetResultData.value = null;
  sheetResultLoading.value = true;
  try {
    sheetResultData.value = await answerSheetResult(submitId);
  } finally {
    sheetResultLoading.value = false;
  }
}
async function seeReview(id) {
  reviewLoading.value = true;
  reviewData.value = null;
  submitImages.value = [];
  try {
    const data = await reviewResult(id);
    reviewData.value = data;
    if (data.submit && data.submit.fileUrl) {
      submitImages.value = data.submit.fileUrl.split(',').filter(Boolean);
    }
  } finally { reviewLoading.value = false; }
  dialogReview.value = true;
}
async function viewDetail(id) {
  dialogDetail.value = true;
  homeworkDetail.value = null;
  detailImages.value = [];
  detailLoading.value = true;
  try {
    const d = await getHomework(id);
    homeworkDetail.value = d;
    if (d.attachmentUrl) {
      detailImages.value = d.attachmentUrl.split(',').filter(Boolean);
    }
  } finally {
    detailLoading.value = false;
  }
}

// ======== 词汇 ========
async function loadVocabularies() {
  loading.value = true;
  try {
    const params = { page: vocabPage.value, size: 6 };
    let r;
    if (vocabKeyword.value) {
      params.keyword = vocabKeyword.value;
      r = await vocabSearch(params);
    } else {
      r = await vocabListApi(params);
    }
    vocabularies.value = r.records || [];
    vocabTotal.value = r.total || 0;
  } finally { loading.value = false; }
}
function searchVocabularies() {
  vocabPage.value = 1;
  loadVocabularies();
}
// ======== 测验 — 两阶段流程 ========

/** 打开测验对话框 — 显示数量选择 */
function openQuizDialog() {
  quizPhase.value = 'countSelect';
  quizWordCount.value = 10;
  quizWords.value = [];
  quizCurrentIndex.value = 0;
  quizCurrentAnswer.value = '';
  quizFeedback.value = null;
  quizPhase1Errors.value = [];
  quizPhase2Words.value = [];
  quizPhase2Index.value = 0;
  quizPhase2Input.value = '';
  quizPhase2Feedback.value = null;
  quizPhase2Errors.value = [];
  quizPhase2Correct.value = 0;
  quizAttemptCount.value = 0;
  quizPhase2AttemptCount.value = 0;
  quizP1Passed.value = [];
  quizP2Passed.value = [];
  quizFlagged.value = [];
  quizMastered.value = 0;
  dialogQuiz.value = true;
}

/** 从数量选择开始阶段一 */
async function startPhase1() {
  quizLoading.value = true;
  try {
    const data = await startQuiz(quizWordCount.value);
    quizWords.value = data.words || [];
    if (!quizWords.value.length) {
      ElMessage.warning('词汇不足，请联系老师添加更多单词');
      dialogQuiz.value = false;
      return;
    }
    // 如果词汇不够请求数量，调整
    quizWordCount.value = quizWords.value.length;
    quizPhase.value = 'phase1';
    quizCurrentIndex.value = 0;
    quizCurrentAnswer.value = '';
    quizFeedback.value = null;
    quizAttemptCount.value = 0;
    quizPhase1Errors.value = [];
  } catch (e) { /* error handled in interceptor */ }
  finally { quizLoading.value = false; }
}

/** 阶段一：确认当前题答案 */
async function confirmPhase1Answer() {
  if (!quizCurrentAnswer.value) {
    ElMessage.warning('请先选择一个答案');
    return;
  }
  const word = quizWords.value[quizCurrentIndex.value];
  const isCorrect = quizCurrentAnswer.value === word.translation;
  quizAttemptCount.value++;

  if (isCorrect) {
    // 正确 → 记录通过；显示延伸词 + 所有选项信息，等待学生手动点击"下一题"
    if (!quizP1Passed.value.includes(word.id)) quizP1Passed.value = [...quizP1Passed.value, word.id];
    quizFeedback.value = {
      correct: true,
      message: '回答正确！',
      extensions: word.extensions,
      showDetails: true  // 标记需要展示选项详情
    };
    // 不再自动跳转 — 学生需手动点"下一题"
  } else {
    // 错误
    // 第一次错误就立即记录到错题库
    const alreadyRecorded = quizPhase1Errors.value.find(
      e => e.wordId === word.id && e.errorType === 'meaning'
    );
    if (!alreadyRecorded) {
      quizPhase1Errors.value.push({
        wordId: word.id,
        word: word.word,
        wrongAnswer: quizCurrentAnswer.value,
        correctAnswer: word.translation
      });
      // 调用后端记录错误
      recordQuizError(word.id, quizCurrentAnswer.value, 'meaning').catch(() => {});
    }

    if (quizAttemptCount.value >= 3) {
      // 三次都错了 → 显示正确答案，需要手动点"下一题"
      quizFeedback.value = {
        correct: false,
        message: '很遗憾，正确答案是：' + word.translation,
        extensions: null,
        showDetails: true, // 需要展示选项详情（即使答错也展示以帮助学习）
        finalAttempt: true
      };
    } else {
      // 还有重试机会 → 提示错误并清除选择，让学生重试
      const remaining = 3 - quizAttemptCount.value;
      quizFeedback.value = {
        correct: false,
        message: '选错了！请重新选择（还剩 ' + remaining + ' 次机会）',
        extensions: null,
        showDetails: false
      };
      quizCurrentAnswer.value = '';
      setTimeout(() => { quizFeedback.value = null; }, 2000);
    }
  }
}

/** 阶段一：进入下一题（或完成阶段一） */
function nextPhase1Word() {
  quizFeedback.value = null;
  quizCurrentAnswer.value = '';
  quizAttemptCount.value = 0;  // 重置尝试次数
  if (quizCurrentIndex.value + 1 >= quizWords.value.length) {
    // 阶段一完成 → 进入阶段二
    startPhase2();
  } else {
    quizCurrentIndex.value++;
  }
}

/** 开始阶段二：看意思写单词 */
async function startPhase2() {
  quizLoading.value = true;
  try {
    const wordIds = quizWords.value.map(w => w.id);
    const data = await getSpellWords(wordIds);
    quizPhase2Words.value = data.words || [];
    quizPhase.value = 'phase2';
    quizPhase2Index.value = 0;
    quizPhase2Input.value = '';
    quizPhase2Feedback.value = null;
    quizPhase2AttemptCount.value = 0;
    quizPhase2Errors.value = [];
    quizPhase2Correct.value = 0;
  } catch (e) { /* error handled in interceptor */ }
  finally { quizLoading.value = false; }
}

/** 阶段二：确认拼写 */
async function confirmPhase2Answer() {
  const input = quizPhase2Input.value.trim();
  if (!input) {
    ElMessage.warning('请输入单词');
    return;
  }
  const word = quizPhase2Words.value[quizPhase2Index.value];
  quizPhase2AttemptCount.value++;

  try {
    const result = await checkSpelling(word.id, input);

    if (result.correct) {
      // 正确 → 记录通过，显示延伸词，等待学生手动点击"下一题"
      quizPhase2Correct.value++;
      if (!quizP2Passed.value.includes(word.id)) quizP2Passed.value = [...quizP2Passed.value, word.id];
      quizPhase2Feedback.value = {
        correct: true,
        message: '拼写正确！',
        extensions: result.extensions,
        showDetails: true
      };
      // 不再自动跳转
    } else {
      // 错误 → 判断是否还有重试机会
      if (quizPhase2AttemptCount.value >= 3) {
        // 三次都错了 → 显示正确答案
        quizPhase2Feedback.value = {
          correct: false,
          message: '很遗憾，正确答案是：' + result.correctAnswer + '\n' + (result.errorHint || ''),
          extensions: null,
          showDetails: true,
          finalAttempt: true
        };
        // 追踪错题（后端已自动记录，此处仅追踪前端展示）
        quizPhase2Errors.value.push({
          wordId: word.id,
          translation: word.translation,
          wrongAnswer: input,
          correctAnswer: result.correctAnswer
        });
      } else {
        // 还有重试机会 → 显示提示，让学生重试
        const remaining = 3 - quizPhase2AttemptCount.value;
        quizPhase2Feedback.value = {
          correct: false,
          message: '拼写错误！' + (result.errorHint || '') + '\n请重新输入（还剩 ' + remaining + ' 次机会）',
          extensions: null,
          showDetails: false
        };
        quizPhase2Input.value = '';
        setTimeout(() => { quizPhase2Feedback.value = null; }, 2500);
      }
    }
  } catch (e) { /* error handled in interceptor */ }
}

/** 阶段二：进入下一题（或完成） */
function nextPhase2Word() {
  quizPhase2Feedback.value = null;
  quizPhase2Input.value = '';
  quizPhase2AttemptCount.value = 0;  // 重置尝试次数
  if (quizPhase2Index.value + 1 >= quizPhase2Words.value.length) {
    // 全部完成 → 结算已掌握，再显示报告
    finalizeQuizMastery();
    quizPhase.value = 'report';
  } else {
    quizPhase2Index.value++;
  }
}

/** 阶段一答对后手动标记"不熟" → 加入错题本 */
function flagQuizUnfamiliar(id) {
  if (quizFlagged.value.includes(id)) return;
  quizFlagged.value = [...quizFlagged.value, id];
  recordQuizError(id, '', 'unfamiliar').catch(() => {});
  ElMessage.info('已加入错题本');
}

/** 阶段一答对 + 阶段二拼写通过（且未被标记不熟）→ 归入已掌握本 */
function finalizeQuizMastery() {
  const ids = quizWords.value.map(w => w.id);
  const masters = ids.filter(id => quizP1Passed.value.includes(id) && quizP2Passed.value.includes(id) && !quizFlagged.value.includes(id));
  quizMastered.value = masters.length;
  masters.forEach(id => markWordMastered(id).catch(() => {}));
}

/** 重新开始测验 */
function resetQuiz() {
  dialogQuiz.value = false;
  setTimeout(() => openQuizDialog(), 300);
}

// ======== 听写模式（听音写词） ========
const dictTitle = computed(() => {
  const m = { setup: '单词听写', listen: '听音写词', report: '听写报告' };
  return m[dictPhase.value] || '单词听写';
});

/** 打开听写弹窗 — 显示范围与数量选择 */
function openDictation() {
  dictPhase.value = 'setup';
  dictScope.value = 'all';
  dictCount.value = 10;
  dictWords.value = [];
  dictIndex.value = 0;
  dictInput.value = '';
  dictFeedback.value = null;
  dictHintVisible.value = false;
  dictCorrect.value = 0;
  dictErrors.value = [];
  dialogDictation.value = true;
}

/** 开始听写：按范围取词（全词库随机 / 错题本），自动朗读第一个单词 */
async function startDictation() {
  dictLoading.value = true;
  try {
    const data = dictScope.value === 'errors'
      ? await reviewErrors(dictCount.value)
      : await startQuiz(dictCount.value);
    const words = data.words || [];
    if (!words.length) {
      ElMessage.warning(dictScope.value === 'errors' ? '暂无错题可听写' : '词汇不足，请联系老师添加更多单词');
      return;
    }
    dictWords.value = words;
    dictCount.value = words.length;
    dictPhase.value = 'listen';
    dictIndex.value = 0;
    dictInput.value = '';
    dictFeedback.value = null;
    dictHintVisible.value = false;
    dictCorrect.value = 0;
    dictErrors.value = [];
    speakWord(words[0].word); // 自动朗读第一个单词
  } catch (e) { /* error handled in interceptor */ }
  finally { dictLoading.value = false; }
}

function replayDictWord() {
  const w = dictWords.value[dictIndex.value];
  if (w) speakWord(w.word);
}

/** 提交听写答案：每词一次机会，拼错由后端自动记入错题库 */
async function confirmDictAnswer() {
  const input = dictInput.value.trim();
  if (!input) {
    ElMessage.warning('请输入你听到的单词');
    return;
  }
  const word = dictWords.value[dictIndex.value];
  try {
    const result = await checkSpelling(word.id, input);
    if (result.correct) {
      dictCorrect.value++;
      dictFeedback.value = { correct: true, message: '拼写正确！' };
    } else {
      dictFeedback.value = {
        correct: false,
        message: '正确拼写：' + result.correctAnswer
      };
      dictErrors.value.push({
        wordId: word.id,
        word: result.correctAnswer,
        translation: word.translation,
        wrongAnswer: input,
        correctAnswer: result.correctAnswer
      });
    }
  } catch (e) { /* error handled in interceptor */ }
}

/** 听写：进入下一个单词（或完成看报告），自动朗读 */
function nextDictWord() {
  dictFeedback.value = null;
  dictInput.value = '';
  dictHintVisible.value = false;
  if (dictIndex.value + 1 >= dictWords.value.length) {
    dictPhase.value = 'report';
  } else {
    dictIndex.value++;
    replayDictWord();
  }
}

/** 报告页：把听错的单词再来一轮 */
function retryDictErrors() {
  if (!dictErrors.value.length) return;
  dictWords.value = dictErrors.value.map(e => ({
    id: e.wordId, word: e.correctAnswer, translation: e.translation
  }));
  dictPhase.value = 'listen';
  dictIndex.value = 0;
  dictInput.value = '';
  dictFeedback.value = null;
  dictHintVisible.value = false;
  dictCorrect.value = 0;
  dictErrors.value = [];
  replayDictWord();
}

// ======== 错题库 ========

/** 切换到某个本子视图：error | review | mastered */
async function switchBook(book) {
  vocabViewMode.value = book;
  errorPage.value = 1;
  await loadErrorRecords();
}

/** 加载当前本子的单词列表 */
async function loadErrorRecords() {
  if (vocabViewMode.value === 'list') return;
  quizLoading.value = true;
  try {
    const data = await getQuizErrors(errorPage.value, 20, undefined, vocabViewMode.value);
    errorRecords.value = data.records || [];
    errorTotal.value = data.total || 0;
    bookCounts.error = data.errorCount || 0;
    bookCounts.review = data.reviewCount || 0;
    bookCounts.mastered = data.masteredCount || 0;
    errorUnreviewedCount.value = data.errorCount || 0;
  } catch (e) { /* ignore */ }
  finally { quizLoading.value = false; }
}

/** 开始错题复习 */
async function startErrorReview() {
  errorReviewPhase.value = 'countSelect';
  errorReviewCount.value = Math.min(10, errorUnreviewedCount.value || 10);
  dialogErrorReview.value = true;
}

/** 确认错题复习数量并开始 */
async function beginErrorReview() {
  quizLoading.value = true;
  try {
    const data = await reviewErrors(errorReviewCount.value);
    errorReviewWords.value = data.words || [];
    if (!errorReviewWords.value.length) {
      ElMessage.success('暂无错题需要复习 ');
      dialogErrorReview.value = false;
      return;
    }
    errorReviewPhase.value = 'reviewing';
    errorReviewCurrent.value = 0;
    errorReviewAnswer.value = '';
    errorReviewFeedback.value = null;
    errorReviewAttemptCount.value = 0;
    errorReviewCorrect.value = 0;
    errorReviewErrors.value = [];
  } catch (e) { /* ignore */ }
  finally { quizLoading.value = false; }
}

/** 错题复习：确认答案 */
async function confirmErrorReview() {
  if (!errorReviewAnswer.value) {
    ElMessage.warning('请先选择一个答案');
    return;
  }
  const word = errorReviewWords.value[errorReviewCurrent.value];
  const isCorrect = errorReviewAnswer.value === word.translation;
  errorReviewAttemptCount.value++;

  if (isCorrect) {
    errorReviewCorrect.value++;
    errorReviewFeedback.value = {
      correct: true,
      message: '回答正确！',
      extensions: word.extensions,
      showDetails: true
    };
    // 不再自动跳转，学生手动点击"下一题"
  } else {
    // 错误 → 记录错误并判断是否还有重试机会
    if (errorReviewAttemptCount.value >= 3) {
      // 三次都错了
      errorReviewFeedback.value = {
        correct: false,
        message: '很遗憾，正确答案是：' + word.translation,
        showDetails: true,
        finalAttempt: true
      };
      errorReviewErrors.value.push({
        wordId: word.id,
        word: word.word,
        wrongAnswer: errorReviewAnswer.value,
        correctAnswer: word.translation
      });
      recordQuizError(word.id, errorReviewAnswer.value, 'meaning').catch(() => {});
    } else {
      const remaining = 3 - errorReviewAttemptCount.value;
      errorReviewFeedback.value = {
        correct: false,
        message: '选错了！请重新选择（还剩 ' + remaining + ' 次机会）',
        showDetails: false
      };
      errorReviewErrors.value.push({
        wordId: word.id,
        word: word.word,
        wrongAnswer: errorReviewAnswer.value,
        correctAnswer: word.translation
      });
      recordQuizError(word.id, errorReviewAnswer.value, 'meaning').catch(() => {});
      errorReviewAnswer.value = '';
      setTimeout(() => { errorReviewFeedback.value = null; }, 2000);
    }
  }
}

function nextErrorReviewWord() {
  errorReviewFeedback.value = null;
  errorReviewAnswer.value = '';
  errorReviewAttemptCount.value = 0;  // 重置尝试次数
  if (errorReviewCurrent.value + 1 >= errorReviewWords.value.length) {
    errorReviewPhase.value = 'done';
  } else {
    errorReviewCurrent.value++;
  }
}

/** 错题本「已掌握」：移入复习本 */
async function doMarkMastered(id) {
  try {
    await markErrorMastered(id);
    ElMessage.success('已移入复习本');
    await loadErrorRecords();
  } catch (e) { /* ignore */ }
}

/** 复习本「再次掌握」：移入已掌握本 */
async function doReviewMastered(id) {
  try {
    await reviewMastered(id);
    ElMessage.success('已移入已掌握本');
    await loadErrorRecords();
  } catch (e) { /* ignore */ }
}

/** 已掌握本「忘了」：退回错题本 */
async function doForget(id) {
  try {
    await forgetError(id);
    ElMessage.info('已退回错题本');
    await loadErrorRecords();
  } catch (e) { /* ignore */ }
}

/** 删除错题 */
async function doDeleteError(id) {
  try {
    await deleteQuizError(id);
    ElMessage.success('已删除');
    await loadErrorRecords();
  } catch (e) { /* ignore */ }
}

async function loadErrorStats() {
  try {
    const data = await errorStats();
    bookCounts.error = data.errorCount || 0;
    bookCounts.review = data.reviewCount || 0;
    bookCounts.mastered = data.masteredCount || 0;
    errorUnreviewedCount.value = data.errorCount || 0;
  } catch (e) { /* ignore */ }
}

// ======== 一键发音 ========

/** 播放英文发音（浏览器 speechSynthesis） */
function speak(text) {
  speakWord(text);
}

// ======== 今日复习（艾宾浩斯记忆曲线） ========

/** 打开今日复习弹窗，加载到期单词 */
async function openTodayReview() {
  dialogTodayReview.value = true;
  trLoading.value = true;
  trCards.value = [];
  trIndex.value = 0;
  trRevealed.value = false;
  trRemembered.value = 0;
  try {
    const data = await getTodayReview(20);
    trCards.value = data.records || [];
  } catch (e) { /* ignore */ }
  finally { trLoading.value = false; }
}

/** 复习提交：记住 → 推进下一阶段；没记住 → 重置回第一阶段明天再复习 */
async function answerTodayReview(correct) {
  const card = trCards.value[trIndex.value];
  if (!card) return;
  try {
    await submitReviewResult(card.id, correct);
  } catch (e) { /* ignore */ }
  if (correct) trRemembered.value++;
  trRevealed.value = false;
  trIndex.value++;
}

// ======== 资料 ========
async function loadSubjects() {
  try {
    subjects.value = await subjectList() || [];
    // 仅在首次（还没选过学科）时默认选中英语，避免从详情返回时把用户选的学科重置掉
    if (!resourceSubjectId.value) {
      const eng = subjects.value.find(s => s.name === '英语');
      resourceSubjectId.value = eng ? eng.id : (subjects.value[0] ? subjects.value[0].id : null);
    }
  } catch (e) { /* handled */ }
}
// 资料类型与学科绑定：只展示当前学科下已用过的类型
async function loadResourceTypeOptions() {
  try {
    const list = await resourceTypes(resourceSubjectId.value);
    resourceTypeOptions.value = Array.isArray(list) ? list : [];
  } catch (e) { resourceTypeOptions.value = []; }
}
function onResourceSubjectChange() {
  resourceType.value = '';
  loadResourceTypeOptions();
  loadResources();
}
async function loadResources() {
  // 从资料详情返回时命中缓存，直接复用列表与筛选/分页状态，不再重新加载
  const key = `${resourcePage.value}|${resourceKeyword.value || ''}|${resourceType.value || ''}|${resourceSubjectId.value || ''}`;
  if (resourceStore.student && resourceStore.student.key === key) {
    resourceItems.value = resourceStore.student.records;
    resourceTotal.value = resourceStore.student.total;
    return;
  }
  loading.value = true;
  try {
    const params = { type: resourceType.value || undefined, page: resourcePage.value, size: 6 };
    if (resourceKeyword.value) params.keyword = resourceKeyword.value;
    if (resourceSubjectId.value) params.subjectId = resourceSubjectId.value;
    const r = await resourceListApi(params);
    resourceItems.value = r.records || [];
    resourceTotal.value = r.total || 0;
    resourceStore.setStudent({ key, records: resourceItems.value, total: resourceTotal.value });
  } finally { loading.value = false; }
}
// ======== 打卡相关逻辑已移除 ========

// ======== Init ========
async function loadByTab() {
  if (currentTab.value === 'homework') loadHomework();
  if (currentTab.value === 'vocabulary') { loadVocabularies(); loadErrorStats(); }
  if (currentTab.value === 'resources') {
    await loadSubjects();
    await loadResourceTypeOptions();
    await loadResources();
  }
}
onMounted(() => {
  loadByTab();
  handleAIAction(route.query);
});
watch(currentTab, loadByTab);

// ========== 响应 AI 助手操作指令 ==========
watch(() => route.query, (query) => { handleAIAction(query); });

async function handleAIAction(query) {
  if (!query || (!query.aiSearch && !query.aiDialog)) return;

  // AI 要求搜索（词汇学习 / 学习资料）
  if (query.aiSearch) {
    if (currentTab.value === 'vocabulary') {
      vocabKeyword.value = query.aiSearch;
      searchVocabularies();
    } else if (currentTab.value === 'resources') {
      resourceKeyword.value = query.aiSearch;
      resourcePage.value = 1;
      if (!subjects.value.length) await loadSubjects();
      await loadResources();
    }
  }

  // AI 要求打开词汇测验 / 听写模式
  if (query.aiDialog === 'quiz' && currentTab.value === 'vocabulary') {
    openQuizDialog();
  }
  if (query.aiDialog === 'dictation' && currentTab.value === 'vocabulary') {
    openDictation();
  }

  // 清除 query 参数，防止刷新页面重复执行
  const cleanQuery = { ...route.query };
  delete cleanQuery.aiSearch;
  delete cleanQuery.aiDialog;
  delete cleanQuery.aiStudent;
  delete cleanQuery.aiPrefill;
  delete cleanQuery.aiOpenProfile;
  router.replace({ query: cleanQuery });
}
</script>

<style scoped>
/* ============================================================
   学生端 — 视觉升级样式
   使用 :root 中定义的 CSS 变量，保持与全局设计令牌一致
   ============================================================ */

/* ── 页头 ── */
.page-header {
  margin-bottom: 20px;
}
.page-title {
  margin: 0 0 4px 0;
  font-size: 22px;
  font-weight: 600;
  color: var(--text);
}
.page-subtitle {
  margin: 0;
  font-size: 14px;
  color: var(--muted);
}

/* ── 工具栏 ── */
/* ── 作业状态概览条 ── */
.hw-stats-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.hw-stat-item {
  padding: 6px 14px;
  border-radius: var(--radius-full);
  font-size: 13px;
  background: var(--surface);
  border: 1px solid var(--border);
  color: var(--muted);
  cursor: pointer;
  transition: color 180ms ease, background-color 180ms ease, border-color 180ms ease;
  user-select: none;
}
.hw-stat-item:hover {
  border-color: #c7cdd6;
}
.hw-stat-item strong {
  font-size: 16px;
  margin-left: 2px;
  color: var(--text);
  font-variant-numeric: tabular-nums;
}
.hw-stat-pending.hw-stat-active,
.hw-stat-redo.hw-stat-active {
  background: var(--raised);
  border-color: var(--text);
  color: var(--text);
}
.hw-stat-redo { color: var(--negative); }
.hw-stat-redo strong { color: var(--negative); }
.hw-filter-tag {
  display: flex; align-items: center; gap: 4px;
  padding: 4px 10px; border-radius: var(--radius-full);
  font-size: 12px; color: var(--accent);
  background: var(--accent-soft);
}

.hw-subject-filter {
  display: flex; align-items: center; gap: 6px; flex-wrap: wrap;
  margin: 8px 0 4px;
}
.hw-subject-filter-label { font-size: 13px; color: var(--color-text-secondary); }

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.toolbar-hint {
  font-size: 13px;
  color: var(--color-text-secondary);
}

/* ── 空状态插图 ── */
.empty-illustration {
  font-size: 64px;
  opacity: 0.6;
}

/* ═══════════════ 骨架屏 ───────────── */
.skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.skeleton-card {
  padding: 20px;
  background: var(--color-bg-card);
  border-radius: var(--radius-lg);
}
.skeleton-vocab-card {
  padding: 20px;
  background: var(--color-bg-card);
  border-radius: var(--radius-lg);
}
.skeleton-res-card {
  padding: 20px 20px 20px 24px;
  background: var(--color-bg-card);
  border-radius: var(--radius-lg);
  border-left: 4px solid var(--skeleton-base);
}
.sk-line {
  height: 14px;
  border-radius: var(--radius-sm);
  background: var(--skeleton-base);
  margin-bottom: 12px;
}
.sk-line:last-child { margin-bottom: 0; }
.sk-title { width: 55%; height: 18px; }
.sk-body { width: 85%; }
.sk-tag { width: 30%; height: 24px; border-radius: var(--radius-full); }
.sk-v-title { width: 45%; height: 20px; }
.sk-v-body { width: 70%; }
.sk-v-tag { width: 35%; height: 22px; border-radius: var(--radius-full); }
.sk-r-title { width: 50%; height: 18px; }
.sk-r-body { width: 75%; }

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* ═══════════════ 答题卡 ═══════════════ */
.paper-bar {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding: 10px 14px;
  margin-bottom: 12px;
  border-radius: var(--radius-md, 8px);
  background: var(--accent-soft);
  font-size: 13px;
  color: var(--color-text-regular);
}
.paper-tip {
  padding: 10px 14px;
  margin-bottom: 12px;
  border-radius: var(--radius-md, 8px);
  background: #f7efdf;
  font-size: 13px;
  color: var(--warning);
  line-height: 1.7;
  white-space: pre-wrap;
}
/* --- 大题（完形/阅读/选词填空……）--- */
.paper-sec {
  padding: 12px;
  margin-bottom: 18px;
  border: 1px solid var(--color-border, var(--border));
  border-radius: var(--radius-lg, 12px);
  background: var(--raised);
}
.paper-sec-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.paper-sec-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
}
.paper-sec-badge {
  padding: 2px 10px;
  border-radius: var(--radius-full, 9999px);
  background: var(--accent);
  color: #fff;
  font-size: 12px;
}
.paper-sec-inst {
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.7;
}
/* 文章默认展开，手机上可以收起来专心填空 */
.paper-passage {
  margin-bottom: 10px;
  border: 1px solid var(--accent-soft);
  border-radius: var(--radius-md, 8px);
  background: var(--surface);
  overflow: hidden;
}
.paper-passage-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--accent-soft);
  font-size: 13px;
  font-weight: 600;
  color: var(--accent);
  cursor: pointer;
  user-select: none;
}
.paper-passage-toggle {
  font-weight: 400;
  font-size: 12px;
}
.paper-passage-text {
  padding: 12px;
  font-size: 15px;
  line-height: 1.9;
  color: var(--color-text-primary);
  white-space: pre-wrap;
}
.paper-bank {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
  padding: 10px 12px;
  border-radius: var(--radius-md, 8px);
  background: #e7f2ec;
}
.paper-bank-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-regular);
}
.paper-bank-tag {
  font-size: 13px;
}
.paper-shared {
  margin-bottom: 10px;
  padding: 10px 12px;
  border-radius: var(--radius-md, 8px);
  background: var(--raised);
}
.paper-shared-opt {
  font-size: 14px;
  line-height: 1.9;
  color: var(--color-text-primary);
}

.paper-q {
  padding: 14px 16px;
  margin-bottom: 14px;
  border: 1px solid var(--color-border, var(--border));
  border-radius: var(--radius-lg, 12px);
  background: var(--color-bg-card, #fff);
}
.paper-q-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.paper-q-seq {
  width: 24px;
  height: 24px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--accent);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
}
.paper-q-type {
  padding: 2px 10px;
  border-radius: var(--radius-full, 9999px);
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 12px;
}
.paper-q-score {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.paper-q-content {
  font-size: 15px;
  color: var(--color-text-primary);
  line-height: 1.7;
  margin-bottom: 10px;
  white-space: pre-wrap;
}
/* 选项竖着排，手机上才点得准 */
.paper-opts {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}
.paper-opt {
  height: auto;
  padding: 6px 0;
  white-space: normal;
  line-height: 1.6;
}
/* 完形填空的选项只有一两个词，横着摆一屏能多看几道题 */
.paper-opts-inline {
  flex-direction: row;
  flex-wrap: wrap;
  gap: 14px;
}

/* --- 结果页 --- */
.paper-result-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding: 16px 18px;
  margin-bottom: 14px;
  border-radius: var(--radius-lg, 12px);
  background: #e7f2ec;
}
.paper-result-score {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.paper-result-num {
  font-size: 34px;
  font-weight: 700;
}
.paper-result-unit {
  font-size: 14px;
  color: var(--color-text-secondary);
}
.paper-result-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: var(--color-text-regular);
}
.paper-r-card {
  padding: 14px 16px;
  margin-bottom: 12px;
  border: 1px solid var(--color-border, var(--border));
  border-left: 4px solid var(--color-border, var(--border));
  border-radius: var(--radius-lg, 12px);
  background: var(--color-bg-card, #fff);
}
.paper-r-right { border-left-color: var(--positive); background: #e7f2ec; }
.paper-r-wrong { border-left-color: var(--negative); background: #fffafa; }
.paper-r-pending { border-left-color: var(--warning); background: #fffcf5; }
.paper-r-flag {
  margin-left: auto;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-regular);
}
.paper-r-row {
  display: flex;
  gap: 8px;
  font-size: 13px;
  color: var(--color-text-regular);
  line-height: 1.8;
}
.paper-r-label {
  flex-shrink: 0;
  width: 60px;
  color: var(--color-text-secondary);
}
.paper-r-mine-wrong {
  color: var(--negative);
  text-decoration: line-through;
}

/* ═══════════════ 过期提醒 ═══════════════ */
.overdue-alert {
  margin-bottom: 16px;
  border-radius: var(--radius-lg);
  background: #fdecec;
  border: 1px solid #f3c9c9;
}
.overdue-alert-inner {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
}
.overdue-icon {
  font-size: 28px;
  flex-shrink: 0;
}
.overdue-list {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.overdue-tag {
  display: inline-block;
  padding: 2px 10px;
  background: rgba(245, 108, 108, 0.12);
  border-radius: var(--radius-full);
  font-size: 12px;
  color: var(--color-danger);
}

@keyframes overdue-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(245, 108, 108, 0.25); }
  50% { box-shadow: 0 0 0 8px rgba(245, 108, 108, 0); }
}

/* alert-list 过渡 */
.alert-list-enter-active,
.alert-list-leave-active {
  transition: color 180ms ease, background-color 180ms ease, border-color 180ms ease, opacity 180ms ease, transform 180ms ease;
}
.alert-list-enter-from,
.alert-list-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

/* ═══════════════ 作业卡片 ═══════════════ */
.hw-card-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.hw-subject-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  padding: 4px 2px;
}
.hw-subject-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text-primary);
  padding-left: 8px;
  border-left: 3px solid var(--color-primary);
}
.hw-subject-count {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.hw-card {
  background: var(--color-bg-card);
  border: 1px solid var(--border);
  border-left: 3px solid var(--border);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: border-color 180ms ease;
  overflow: hidden;
}
.hw-card:hover { border-color: #c7cdd6; border-left-color: var(--muted); }
.hw-card--pending { border-left-color: var(--muted); }
.hw-card--submitted { border-left-color: var(--warning); }
.hw-card--graded { border-left-color: var(--positive); }
.hw-card--redo { border-left-color: var(--negative); }

.hw-card-body {
  padding: 16px 20px 12px;
}
.hw-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}
.hw-card-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  flex: 1;
}
.hw-status {
  display: inline-block;
  padding: 3px 12px;
  border-radius: var(--radius-full);
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}
.hw-status--pending { background: var(--raised); color: var(--muted); }
.hw-status--submitted { background: #f7efdf; color: var(--warning); }
.hw-status--graded { background: #e7f2ec; color: var(--positive); }
.hw-status--redo { background: #fdecec; color: var(--negative); }

.hw-card-content {
  margin: 0 0 10px 0;
  font-size: 14px;
  color: var(--color-text-regular);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.hw-card-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.hw-meta-tag {
  display: flex;
  align-items: center;
  gap: 4px;
}
.hw-meta-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-primary);
}
.hw-meta-deadline { display: flex; align-items: center; gap: 4px; }
.hw-deadline-over { color: var(--color-danger); font-weight: 600; }

.hw-card-actions {
  display: flex;
  gap: 8px;
  padding: 8px 20px 14px;
  border-top: 1px solid var(--color-border-light);
  background: var(--color-bg-hover);
}

/* ═══════════════ 词汇 ═══════════════ */
.vocab-toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}
.vocab-search {
  flex: 1;
  min-width: 200px;
}
.vocab-search :deep(.el-input__wrapper) {
  border-radius: var(--radius-full);
}
.quiz-btn {
  background: var(--accent);
  border: none;
  color: #fff;
  font-weight: 600;
}
.quiz-btn:hover {
  opacity: 0.9;
  color: #fff;
}

.vocab-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 14px;
}
.vocab-card {
  background: var(--color-bg-card);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: transform var(--transition-normal), box-shadow var(--transition-normal);
  overflow: hidden;
  perspective: 800px;
}
.vocab-card:hover {
  transform: translateY(-4px) perspective(800px) rotateY(-1deg);
}
.vocab-card-inner {
  padding: 20px;
}
.vocab-word {
  margin: 0 0 6px 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--text);
}
.vocab-trans {
  margin: 0 0 14px 0;
  font-size: 14px;
  color: var(--color-text-regular);
  line-height: 1.5;
}
.vocab-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.vocab-pos {
  padding: 3px 10px;
  background: var(--color-bg-hover);
  border-radius: var(--radius-full);
  font-size: 12px;
  color: var(--color-text-secondary);
}
.vocab-ext-count {
  font-size: 11px;
  color: var(--color-primary);
  font-weight: 600;
}

/* 测验弹窗 */
.quiz-progress-bar {
  height: 6px;
  background: var(--color-border-light);
  border-radius: 3px;
  margin-bottom: 8px;
  overflow: hidden;
}
.quiz-progress-fill {
  height: 100%;
  background: var(--gradient-primary);
  border-radius: 3px;
  transition: width var(--transition-normal);
}
.quiz-hint {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin: 0 0 16px 0;
}
.quiz-item {
  display: flex;
  gap: 14px;
  margin-bottom: 12px;
  padding: 14px;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-light);
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
}
.quiz-item--answered {
  border-color: var(--color-primary-light);
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.1);
}
.quiz-num {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--gradient-primary);
  color: #fff;
  font-weight: 700;
  font-size: 14px;
  flex-shrink: 0;
}
.quiz-body {
  flex: 1;
  min-width: 0;
}
.quiz-question {
  margin: 0 0 10px 0;
  font-size: 15px;
  color: var(--color-text-primary);
}
.quiz-options {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.quiz-option {
  padding: 8px 12px !important;
  border-radius: var(--radius-md);
  transition: background var(--transition-fast);
  margin-right: 0 !important;
}
.quiz-option:hover {
  background: var(--color-bg-hover);
}

/* 测验结果 */
.quiz-result {
  text-align: center;
  padding: 10px 0 20px;
}
.score-ring {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  margin: 0 auto 16px;
  background: var(--raised);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}
.score-inner {
  width: 90px;
  height: 90px;
  border-radius: 50%;
  background: var(--surface);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.score-num {
  font-size: 32px;
  font-weight: 800;
  color: var(--color-success);
  line-height: 1;
}
.score-total {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.score-msg {
  font-size: 15px;
  color: var(--color-text-regular);
}
.quiz-report-item {
  padding: 12px;
  border-radius: var(--radius-md);
  margin-bottom: 8px;
}
.report-correct { background: #e7f2ec; border-left: 3px solid var(--color-success); }
.report-wrong { background: #fdecec; border-left: 3px solid var(--color-danger); }
.report-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.report-num { font-weight: 600; }
.report-badge { font-size: 12px; padding: 2px 8px; border-radius: var(--radius-full); }
.badge-ok { background: #e7f2ec; color: var(--positive); }
.badge-no { background: #fdecec; color: var(--negative); }
.report-your { margin: 4px 0; font-size: 13px; color: var(--color-text-regular); }
.report-correct-ans { margin: 4px 0; font-size: 13px; color: var(--color-success); }

/* ═══════════════ 学习资料 ═══════════════ */
.resource-toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}
.resource-search {
  flex: 1;
  min-width: 180px;
}
.resource-search :deep(.el-input__wrapper) {
  border-radius: var(--radius-full);
}
.resource-type-select {
  width: 140px;
}
.resource-type-select :deep(.el-input__wrapper) {
  border-radius: var(--radius-full);
}

.resource-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.res-card {
  display: flex;
  background: var(--color-bg-card);
  border-radius: var(--radius-lg);
  cursor: pointer;
  overflow: hidden;
  transition: transform var(--transition-normal), box-shadow var(--transition-normal);
}
.res-card:hover {
  transform: translateY(-2px);
}
.res-card-accent {
  width: 4px;
  flex-shrink: 0;
  background: var(--color-primary);
}
.res-card--grammar .res-card-accent { background: var(--accent); }
.res-card--reading .res-card-accent { background: var(--positive); }
.res-card--listening .res-card-accent { background: var(--warning); }
.res-card--writing .res-card-accent { background: var(--negative); }
.res-card--default .res-card-accent { background: var(--color-primary); }

.res-card-body {
  flex: 1;
  padding: 16px 20px;
  min-width: 0;
}
.res-card-title {
  margin: 0 0 8px 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
}
.res-type-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: var(--radius-full);
  font-size: 11px;
  font-weight: 600;
  background: var(--color-bg-hover);
  color: var(--color-text-secondary);
  margin-bottom: 8px;
}
.res-card-preview {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.5;
}
.res-card-hint {
  font-size: 12px;
  color: var(--color-primary);
}

/* ═══════════════ 上传 & 图片预览 ═══════════════ */
.upload-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
  flex-wrap: wrap;
}
.upload-inline {
  display: inline-block;
}
.upload-btn--camera {
  background: var(--gradient-success);
  border: none;
  color: #fff;
}
.upload-btn--camera:hover { opacity: 0.9; color: #fff; }
.upload-hint {
  font-size: 12px;
  color: var(--color-text-secondary);
  width: 100%;
  margin-top: 4px;
}

/* 图片预览网格 */
.preview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
  gap: 8px;
  margin-top: 14px;
}
.preview-item {
  position: relative;
  aspect-ratio: 1;
  border-radius: var(--radius-md);
  overflow: hidden;
  border: 1px solid var(--color-border-light);
}
.preview-img {
  width: 100%;
  height: 100%;
}
.preview-del {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: none;
  background: rgba(245, 108, 108, 0.9);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity var(--transition-fast), transform var(--transition-fast);
}
.preview-item:hover .preview-del {
  opacity: 1;
}
.preview-del:hover {
  transform: scale(1.15);
}

/* ═══════════════ 批改结果弹窗 ═══════════════ */
.review-score-section {
  text-align: center;
  margin-bottom: 20px;
}
.review-score-ring {
  width: 110px;
  height: 110px;
  border-radius: 50%;
  margin: 0 auto 12px;
  background: var(--raised);
  display: flex;
  align-items: center;
  justify-content: center;
}
.review-score-inner {
  width: 84px;
  height: 84px;
  border-radius: 50%;
  background: var(--surface);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.review-score-num {
  font-size: 32px;
  font-weight: 800;
  line-height: 1;
}
.score-high { color: var(--color-success); }
.score-mid { color: var(--color-warning); }
.score-low { color: var(--color-danger); }
.review-score-unit {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.review-answer-box {
  padding: 14px;
  background: var(--color-bg-hover);
  border-radius: var(--radius-md);
  margin-bottom: 8px;
}
.review-answer-box h5 {
  margin: 0 0 8px 0;
  font-size: 14px;
  color: var(--color-text-primary);
}
.review-text-answer {
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  color: var(--color-text-regular);
}
.review-images {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.review-img-thumb {
  width: 90px;
  height: 90px;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-light);
  cursor: pointer;
  object-fit: cover;
}
.review-no-content {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.review-comment-box {
  padding: 14px;
  background: #e7f2ec;
  border-left: 4px solid var(--color-success);
  border-radius: var(--radius-md);
}
.review-comment-box h5 {
  margin: 0 0 8px 0;
  font-size: 14px;
  color: var(--color-success);
}
.review-comment-box p {
  margin: 0;
  font-size: 15px;
  line-height: 1.7;
  white-space: pre-wrap;
  color: var(--color-text-regular);
}
.review-voice-play {
  margin-top: 10px;
}
.review-voice-tip {
  display: block;
  margin-bottom: 4px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.review-voice-audio {
  width: 100%;
  max-width: 320px;
  height: 34px;
}

/* ═══════════════ 作业详情弹窗 ═══════════════ */
.detail-content-text {
  white-space: pre-wrap;
  line-height: 1.8;
  font-size: 14px;
  max-height: 200px;
  overflow-y: auto;
  color: var(--color-text-regular);
}
.detail-images {
  margin-top: 16px;
}
.detail-images h4 {
  margin: 0 0 8px 0;
  font-size: 14px;
  color: var(--color-text-regular);
}
.detail-img-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.detail-img-thumb {
  width: 130px;
  height: 130px;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-light);
  cursor: pointer;
  object-fit: cover;
  transition: transform var(--transition-fast);
}
.detail-img-thumb:hover {
  transform: scale(1.05);
}

/* ═══════════════ 延伸词标签 ═══════════════ */
.ext-tag {
  margin: 2px;
  background: var(--raised);
  border: none;
  color: var(--color-primary-dark);
}

/* ═══════════════ 分页 ═══════════════ */
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

/* ═══════════════ 通用空状态 ═══════════════ */
:deep(.el-empty__description) {
  color: var(--color-text-secondary);
}

/* ═══════════════ 移动端适配 (≤768px) ═══════════════ */
@media (max-width: 768px) {
  .page-title {
    font-size: 20px;
  }
  .page-subtitle {
    padding-left: 0;
    font-size: 13px;
  }

  /* 弹窗全宽 */
  :deep(.el-dialog) {
    width: 92% !important;
    max-width: 92vw !important;
  }

  /* 作业卡片 */
  .hw-card-body { padding: 12px 14px 8px; }
  .hw-card-actions { padding: 8px 14px 10px; gap: 6px; }
  .hw-card-actions .el-button { font-size: 12px; padding: 6px 12px; }
  .hw-card-top { flex-direction: column; align-items: flex-start; gap: 6px; }

  /* 词汇网格 — 两列 */
  .vocab-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 10px;
  }
  .vocab-card-inner { padding: 14px; }
  .vocab-word { font-size: 17px; }
  .vocab-toolbar { flex-direction: column; }

  /* 资料列表 */
  .resource-toolbar { flex-direction: column; }
  .resource-type-select { width: 100%; }

  /* 图片预览 */
  .preview-grid {
    grid-template-columns: repeat(auto-fill, minmax(80px, 1fr));
  }
  .detail-img-thumb { width: 100px; height: 100px; }
  .review-img-thumb { width: 70px; height: 70px; }
}

/* 极小屏 (≤400px) */
@media (max-width: 400px) {
  .vocab-grid {
    grid-template-columns: 1fr;
  }
  .hw-card-actions {
    flex-wrap: wrap;
  }
}

/* ═══════════════ 听写模式 ═══════════════ */
.dict-btn {
  background: var(--accent);
  border: none;
  color: #fff;
}
.dict-btn:hover,
.dict-btn:focus {
  opacity: 0.92;
  color: #fff;
}
.dict-scope-row {
  display: flex;
  gap: 12px;
  margin-bottom: 8px;
}
.dict-scope-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 16px 10px 12px;
  border: 2px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: color 180ms ease, background-color 180ms ease, border-color 180ms ease, opacity 180ms ease, transform 180ms ease;
  text-align: center;
}
.dict-scope-item:hover {
  transform: translateY(-2px);
}
.dict-scope-item.active {
  border-color: var(--color-primary);
  background: rgba(102, 126, 234, 0.06);
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.12);
}
.dict-scope-icon {
  font-size: 26px;
  line-height: 1;
}
.dict-scope-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
}
.dict-scope-desc {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.dict-tip {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 0 0 16px;
}
.dict-card {
  text-align: center;
  padding: 8px 0 0;
}
.dict-speak-btn {
  width: 84px;
  height: 84px;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  font-size: 34px;
  color: #fff;
  background: var(--accent);
  transition: transform 0.15s ease;
}
.dict-speak-btn:hover {
  transform: scale(1.05);
}
.dict-speak-btn:active {
  transform: scale(0.94);
}
.dict-hint-line {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin: 10px 0 14px;
}
.dict-hint {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
  margin: 0 auto 14px;
  padding: 8px 14px;
  width: fit-content;
  background: var(--color-bg-hover);
  border-radius: 20px;
}
.dict-pos {
  font-size: 12px;
  color: var(--color-primary-dark);
  background: var(--accent-soft);
  padding: 2px 8px;
  border-radius: 10px;
}
.dict-trans {
  font-size: 14px;
  color: var(--color-text-regular);
}
.dict-hint-btn {
  margin-bottom: 14px;
}
.dict-input {
  max-width: 340px;
  margin: 0 auto;
}
.dict-input :deep(.el-input__wrapper) {
  border-radius: 24px;
}
.dict-input :deep(.el-input__inner) {
  text-align: center;
  font-size: 18px;
  letter-spacing: 1px;
}
.dict-feedback-extra {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 4px 0 0;
}
.dict-report-trans {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 2px 0 0;
}
.dict-fill {
  background: var(--accent);
}

/* ═══════════════ 词汇视图切换 ═══════════════ */
.vocab-view-tabs {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}
.error-badge-inline {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  margin-left: 6px;
  border-radius: 10px;
  background: var(--color-danger);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
}

/* ═══════════════ 测验 — 数量选择 ═══════════════ */
.count-select-area {
  text-align: center;
  padding: 20px 0;
}
.count-select-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0 0 8px 0;
}
.count-select-hint {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 0 0 20px 0;
}
.count-slider-wrap {
  max-width: 400px;
  margin: 0 auto 24px;
  padding: 0 20px;
}
.count-slider {
  width: 100%;
}
.start-btn {
  min-width: 180px;
  font-size: 16px;
  font-weight: 600;
  background: var(--gradient-primary);
  border: none;
  color: #fff;
}
.start-btn:hover { opacity: 0.9; color: #fff; }

/* ═══════════════ 测验阶段头 ═══════════════ */
.quiz-phase-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.phase-badge {
  display: inline-block;
  padding: 4px 14px;
  border-radius: var(--radius-full);
  font-size: 13px;
  font-weight: 700;
  color: #fff;
}
.phase1-badge { background: var(--gradient-primary); }
.phase2-badge { background: #e7f2ec; }
.phase-progress {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-secondary);
}
.phase2-fill {
  background: #e7f2ec;
  border-radius: var(--radius-lg);
  margin-bottom: 16px;
}
.phase1-word {
  font-size: 36px;
  font-weight: 800;
  color: var(--color-primary-dark);
  display: block;
}
.phase1-pos {
  display: inline-block;
  margin-top: 6px;
  padding: 2px 12px;
  border-radius: var(--radius-full);
  background: rgba(64, 158, 255, 0.15);
  font-size: 13px;
  color: var(--color-primary);
}
.phase1-question {
  font-size: 15px;
  color: var(--color-text-regular);
  margin: 0 0 12px 0;
}
.phase1-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.phase1-option {
  padding: 12px 16px !important;
  border-radius: var(--radius-md) !important;
  border: 1px solid var(--color-border-light) !important;
  margin-right: 0 !important;
  transition: color 180ms ease, background-color 180ms ease, border-color 180ms ease, opacity 180ms ease, transform 180ms ease;
  width: 100%;
}
.phase1-option:hover {
  border-color: var(--color-primary) !important;
  background: var(--accent-soft);
}
.option-correct {
  border-color: var(--color-success) !important;
  background: #e7f2ec !important;
}
.option-wrong {
  border-color: var(--color-danger) !important;
  background: #fdecec !important;
}

/* 反馈区 */
.phase1-feedback, .phase2-feedback {
  margin-top: 16px;
  padding: 14px;
  border-radius: var(--radius-md);
}
.feedback-ok {
  background: #e7f2ec;
  border-left: 4px solid var(--color-success);
}
.feedback-bad {
  background: #fdecec;
  border-left: 4px solid var(--color-danger);
}
.feedback-msg {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-line;
  color: var(--color-text-primary);
}
.feedback-extensions {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}
.ext-label {
  font-size: 13px;
  color: var(--color-text-secondary);
}

/* 反馈动画 */
.feedback-fade-enter-active { transition: color 180ms ease, background-color 180ms ease, border-color 180ms ease, opacity 180ms ease, transform 180ms ease; }
.feedback-fade-leave-active { transition: color 180ms ease, background-color 180ms ease, border-color 180ms ease, opacity 180ms ease, transform 180ms ease; }
.feedback-fade-enter-from { opacity: 0; transform: translateY(-6px); }
.feedback-fade-leave-to { opacity: 0; }

/* ═══════════════ 阶段二：看义写词 ═══════════════ */
.phase2-card {
  margin-top: 16px;
  text-align: center;
}
.phase2-meaning-display {
  padding: 24px;
  background: #e7f2ec;
  border-radius: var(--radius-lg);
  margin-bottom: 20px;
}
.phase2-label {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin: 0 0 8px 0;
}
.phase2-meaning {
  font-size: 32px;
  font-weight: 800;
  color: var(--color-text-primary);
  margin: 0;
}
.phase2-pos-hint {
  display: inline-block;
  margin-top: 8px;
  padding: 2px 12px;
  border-radius: var(--radius-full);
  background: rgba(103, 194, 58, 0.15);
  font-size: 13px;
  color: var(--color-success);
}
.phase2-input {
  max-width: 360px;
}
.phase2-input :deep(.el-input__wrapper) {
  border-radius: var(--radius-full);
}

/* ═══════════════ 测验报告 ═══════════════ */
.report-section {
  margin-top: 12px;
}
.report-passed-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 10px;
  margin-top: 12px;
}
.report-passed-item {
  padding: 12px;
  background: #e7f2ec;
  border-radius: var(--radius-md);
  text-align: center;
  border-left: 3px solid var(--color-success);
}
.passed-word {
  display: block;
  font-size: 16px;
  font-weight: 700;
  color: var(--color-success);
}
.passed-trans {
  display: block;
  font-size: 13px;
  color: var(--color-text-regular);
  margin-top: 2px;
}
.passed-ext {
  display: block;
  font-size: 11px;
  color: var(--color-text-secondary);
  margin-top: 4px;
}

/* ═══════════════ 错题库 ═══════════════ */
.error-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}
.error-stats {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}
.error-stat-item {
  font-size: 14px;
  color: var(--color-text-regular);
}
.error-stat-pending {
  color: var(--color-danger);
}
.error-stat-clear {
  color: var(--color-success);
}
.review-btn {
  background: var(--gradient-success);
  border: none;
  color: #fff;
  font-weight: 600;
}
.review-btn:hover { opacity: 0.9; color: #fff; }

.error-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.error-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  background: var(--color-bg-card);
  border-radius: var(--radius-lg);
  border-left: 4px solid var(--color-danger);
  transition: color 180ms ease, background-color 180ms ease, border-color 180ms ease, opacity 180ms ease, transform 180ms ease;
}
.error-card--mastered {
  border-left-color: var(--color-success);
  opacity: 0.75;
}
.tag-unfamiliar {
  background: rgba(144, 147, 153, 0.12);
  color: var(--color-text-secondary);
}
.mastered-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 4px 0 8px;
}
.mastered-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 9999px;
  background: var(--color-bg-hover);
  font-size: 14px;
  color: var(--color-text-primary);
}
.chip-mastered {
  font-size: 11px;
  color: var(--color-success);
  background: rgba(103, 194, 58, 0.12);
  border-radius: 9999px;
  padding: 1px 8px;
}
.chip-forget {
  font-size: 12px;
  color: var(--color-danger);
  cursor: pointer;
  padding-left: 6px;
  border-left: 1px solid var(--color-border-light);
}
.error-card-body {
  flex: 1;
  min-width: 0;
}
.error-card-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.error-word {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text-primary);
  cursor: pointer;
}
.error-word:hover {
  color: var(--color-primary);
}
.error-type-tag {
  padding: 2px 10px;
  border-radius: var(--radius-full);
  font-size: 11px;
  font-weight: 600;
}
.tag-meaning { background: #f7efdf; color: var(--warning); }
.tag-spelling { background: var(--accent-soft); color: var(--accent); }
.tag-mastered { background: #e7f7ee; color: var(--positive); }
.error-mastered-tag {
  font-size: 11px;
  color: var(--color-success);
}
.error-detail {
  font-size: 13px;
  color: var(--color-text-regular);
  line-height: 1.6;
}
.error-detail p { margin: 2px 0; }
.error-label { color: var(--color-text-secondary); }
.error-wrong { color: var(--color-danger); font-weight: 600; }
.error-correct { color: var(--color-success); font-weight: 600; }
.error-meta {
  font-size: 11px;
  color: var(--color-text-secondary);
  margin-top: 6px;
}
.error-card-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex-shrink: 0;
}

/* ═══════════════ 选项详情面板（选对后展示所有选项的来源单词） ═══════════════ */
.option-details-panel {
  margin-top: 20px;
  padding: 16px;
  background: var(--raised);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border-light);
}
.option-details-title {
  margin: 0 0 12px 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text-primary);
}
.option-detail-item {
  padding: 10px 12px;
  margin-bottom: 8px;
  background: var(--surface);
  border-radius: var(--radius-md);
  border-left: 3px solid var(--color-border);
  transition: border-color var(--transition-fast);
}
.option-detail-item:last-child { margin-bottom: 0; }
.option-detail-correct {
  border-left-color: var(--color-success);
  background: #e7f2ec;
}
.option-detail-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.option-detail-num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--color-bg-hover);
  font-size: 12px;
  font-weight: 700;
  color: var(--color-text-secondary);
  flex-shrink: 0;
}
.option-detail-correct .option-detail-num {
  background: var(--color-success);
  color: #fff;
}
.option-detail-text {
  font-size: 14px;
  color: var(--color-text-regular);
  font-weight: 500;
}
.option-detail-arrow {
  color: var(--color-text-secondary);
  font-size: 12px;
}
.option-detail-word {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-primary-dark);
}
.option-detail-pos {
  display: inline-block;
  padding: 1px 8px;
  border-radius: var(--radius-full);
  background: rgba(64, 158, 255, 0.1);
  font-size: 11px;
  color: var(--color-primary);
}
.option-detail-ext {
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-text-secondary);
  padding-left: 32px;
}

/* 错题库 — 词性标签 */
.error-pos-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: var(--radius-full);
  background: rgba(64, 158, 255, 0.1);
  font-size: 11px;
  font-weight: 500;
  color: var(--color-primary);
}
.error-extensions {
  margin: 4px 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

/* ═══════════════ 移动端适配补充 ═══════════════ */
@media (max-width: 768px) {
  .vocab-view-tabs {
    flex-direction: row;
    gap: 8px;
  }
  .vocab-view-tabs .el-button {
    font-size: 13px;
    padding: 8px 16px;
  }
  .phase1-word { font-size: 28px; }
  .phase2-meaning { font-size: 24px; }
  .count-slider-wrap { padding: 0 10px; }
  .error-card {
    flex-direction: column;
  }
  .error-card-actions {
    flex-direction: row;
    width: 100%;
  }
  .report-passed-grid {
    grid-template-columns: 1fr;
  }
  .error-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }
}

/* ═══════════════ 一键发音按钮 ═══════════════ */
.speak-btn {
  cursor: pointer;
  opacity: 0.55;
  font-size: 0.75em;
  margin-left: 6px;
  transition: opacity .2s, transform .2s;
  user-select: none;
}
.speak-btn:hover {
  opacity: 1;
  transform: scale(1.15);
}

/* ═══════════════ 今日复习弹窗 ═══════════════ */
.tr-loading {
  text-align: center;
  padding: 40px 0;
  color: #999;
}
.tr-progress {
  display: flex;
  justify-content: space-between;
  color: var(--muted);
  font-size: 13px;
  margin-bottom: 14px;
}
.tr-card {
  text-align: center;
  padding: 34px 20px;
  background: #f7f8fc;
  border-radius: 16px;
}
.tr-word {
  font-size: 32px;
  font-weight: 700;
  color: var(--color-primary, #2b3a67);
}
.tr-trans {
  font-size: 18px;
  margin-top: 16px;
  color: #333;
}
.tr-pos {
  color: var(--muted);
  font-size: 13px;
  margin-top: 4px;
}
.tr-ext {
  margin-top: 12px;
  font-size: 13px;
  color: var(--color-text-regular);
}
.tr-reveal-btn {
  margin-top: 22px;
}
.tr-actions {
  display: flex;
  justify-content: center;
  gap: 24px;
  margin-top: 22px;
}
.tr-done {
  text-align: center;
  padding: 24px 0 8px;
}
.tr-done-emoji {
  font-size: 44px;
  margin-bottom: 10px;
}
.tr-done-hint {
  color: var(--muted);
  font-size: 13px;
  margin: 10px 0 20px;
}
</style>
