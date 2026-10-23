<template>
  <div>
    <!-- ======================== 学生管理 ======================== -->
    <PageHeader v-if="currentTab === 'students'" title="学生管理">
      <template #sub>全班 {{ studentList.length }} 人</template>
      <template #actions>
        <el-button @click="loadStudents" :loading="loading">刷新</el-button>
        <el-button type="primary" @click="openAddStudent">添加学生</el-button>
      </template>
    </PageHeader>
    <div v-if="currentTab === 'students'" class="table-wrapper">
      <el-table :data="studentList" v-loading="loading">
        <el-table-column type="index" label="#" width="56" align="right" class-name="roster-idx" />
        <el-table-column prop="realName" label="姓名" min-width="140">
          <template #default="scope">
            <span class="cell-strong">{{ scope.row.realName || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="username" label="账号" min-width="160">
          <template #default="scope">
            <span class="cell-muted">@{{ scope.row.username }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" align="right">
          <template #default="scope">
            <el-button size="small" type="primary" @click="openHomework(scope.row)">布置作业</el-button>
            <el-dropdown trigger="click" style="margin-left:8px" @command="(cmd) => onStudentCmd(cmd, scope.row)">
              <el-button size="small" text class="row-more-btn">
                更多<el-icon class="row-more-icon"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">编辑信息</el-dropdown-item>
                  <el-dropdown-item command="sheet">AI 答题卡</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState text="还没有学生。添加后就能给 TA 布置作业。">
            <el-button type="primary" @click="openAddStudent">添加学生</el-button>
          </EmptyState>
        </template>
      </el-table>
    </div>

    <!-- ======================== 作业管理 ======================== -->
    <div v-if="currentTab === 'homework'">
      <PageHeader title="作业管理" sub="选择学生，查看并批改 TA 的作业">
        <template #actions>
          <el-select
            v-model="selectedStudentId"
            placeholder="请选择学生"
            clearable
            style="width:260px"
            @change="onStudentChange"
            @clear="onStudentChange"
          >
            <el-option
              v-for="s in studentList"
              :key="s.id"
              :label="s.realName + ' (@' + s.username + ')'"
              :value="s.id"
            />
          </el-select>
          <el-button type="primary" @click="queryStudentHomework" :loading="loading" :disabled="!selectedStudentId">
            查看作业
          </el-button>
        </template>
      </PageHeader>

      <!-- 未选择学生 / 未查询 -->
      <EmptyState v-if="!queriedStudent" text="先在上面选一个学生。" />

      <!-- 查询结果区 -->
      <template v-if="queriedStudent">
        <!-- 学生范围 -->
        <p class="scope-line">
          <strong>{{ queriedStudent.realName }}</strong>
          <span v-if="queriedStudent.username" class="scope-user">@{{ queriedStudent.username }}</span>
          <span class="scope-hint">共 {{ filteredHomeworks.length }} 份作业</span>
        </p>

        <!-- 作业状态概览 — 点击筛选 -->
        <div v-if="!loading && filteredHomeworks.length" class="hw-stats-bar">
          <span
            class="hw-stat-item hw-stat-todo"
            :class="{ 'hw-stat-active': statusFilter === '已提交' }"
            @click="statusFilter = statusFilter === '已提交' ? null : '已提交'"
          >
            待批改 <strong>{{ hwStatusCounts.submitted }}</strong>
          </span>
          <span
            class="hw-stat-item hw-stat-pending"
            :class="{ 'hw-stat-active': statusFilter === '未提交' }"
            @click="statusFilter = statusFilter === '未提交' ? null : '未提交'"
          >
            未提交 <strong>{{ hwStatusCounts.pending }}</strong>
          </span>
          <span
            class="hw-stat-item hw-stat-todo"
            :class="{ 'hw-stat-active': statusFilter === '需重做' }"
            @click="statusFilter = statusFilter === '需重做' ? null : '需重做'"
          >
            需重做 <strong>{{ hwStatusCounts.redo }}</strong>
          </span>
          <span
            class="hw-stat-item hw-stat-done"
            :class="{ 'hw-stat-active': statusFilter === '已批改' }"
            @click="statusFilter = statusFilter === '已批改' ? null : '已批改'"
          >
            已批改 <strong>{{ hwStatusCounts.graded }}</strong>
          </span>
          <span v-if="statusFilter" class="hw-filter-tag">
            已筛选：{{ statusFilter }}
            <el-button size="small" text @click="statusFilter = null">清除</el-button>
          </span>
        </div>

        <!-- 学科筛选：按该学生已有的作业学科展示 -->
        <div v-if="!loading && homeworkSubjects.length" class="hw-subject-filter">
          <span class="hw-subject-filter-label">学科：</span>
          <el-button size="small" :type="!subjectFilter ? 'primary' : ''" round @click="subjectFilter = null">全部</el-button>
          <el-button v-for="s in homeworkSubjects" :key="s" size="small" :type="subjectFilter === s ? 'primary' : ''" round @click="subjectFilter = s">{{ s }}</el-button>
        </div>

        <!-- 骨架屏 -->
        <div v-if="loading" class="skeleton-list">
          <div v-for="i in 3" :key="i" class="skeleton-card">
            <div class="sk-line sk-title"></div>
            <div class="sk-line sk-body"></div>
            <div class="sk-line sk-tag"></div>
          </div>
        </div>

        <!-- 作业卡片列表 -->
        <div v-else-if="displayedHomeworks.length" class="hw-card-list">
          <div
            v-for="item in displayedHomeworks"
            :key="item.id"
            class="hw-card"
            :class="'hw-card--' + statusClass(item.status)"
          >
            <div class="hw-card-body">
              <div class="hw-card-top">
                <h4 class="hw-card-title">{{ item.title }}</h4>
                <StatusTag :text="item.status" :tone="statusTone(item.status)" />
              </div>
              <p class="hw-card-content">{{ item.content || '暂无文字内容' }}</p>
              <div class="hw-card-meta">
                <span class="hw-meta-tag">
                  <span class="hw-meta-dot"></span>
                  {{ item.type === 'answer_sheet' ? '答题卡' : (item.type || '作业') }}{{ item.subject ? '，' + item.subject : '' }}
                </span>
                <span class="hw-meta-deadline">
                  {{ item.deadline || '无截止时间' }}
                </span>
              </div>
            </div>
            <div class="hw-card-actions" @click.stop>
              <el-button size="small" round @click="viewHomeworkDetail(item.id)">详情</el-button>
              <el-button v-if="item.type === 'answer_sheet'" size="small" round type="success" @click="openSheetReview(item)">答题卡批改</el-button>
              <el-button v-if="item.type !== 'answer_sheet'" size="small" round type="warning" @click="openEditHomework(item)">编辑</el-button>
              <el-button size="small" type="danger" round @click="deleteHomeworkHandler(item.id)">删除</el-button>
            </div>
          </div>
        </div>

        <!-- 空状态 -->
        <EmptyState v-else-if="!loading" text="这名学生还没有匹配的作业。" />
      </template>
    </div>

    <!-- ======================== 词汇管理 ======================== -->
    <div v-if="currentTab === 'vocabulary'">
      <PageHeader title="词汇管理" sub="词库维护与 AI 智能扩展">
        <template #actions>
          <el-button type="primary" @click="dialogVocab = true">添加词汇</el-button>
          <el-button @click="openAIVocabDialog">AI 智能扩展</el-button>
        </template>
      </PageHeader>
      <div class="vocab-toolbar">
        <el-select v-model="vocabBankFilter" placeholder="全部词库" style="width:150px" clearable @change="searchVocabularies">
          <el-option label="全部词库" value="" />
          <el-option v-for="b in vocabBanksList" :key="b" :label="b" :value="b" />
        </el-select>
        <el-input v-model="vocabKeyword" placeholder="搜索单词或释义..." style="width:220px" clearable @change="searchVocabularies" @clear="searchVocabularies" />
        <el-button @click="searchVocabularies" :loading="loading">刷新</el-button>
      </div>
      <div class="table-wrapper">
      <el-table :data="vocabularies" v-loading="loading" empty-text="暂无词汇">
        <el-table-column prop="word" label="单词">
          <template #default="scope">
            <el-button type="primary" link @click="showWordDetail(scope.row)"><span class="word-cell">{{ scope.row.word }}</span></el-button>
            <el-button size="small" text @click.stop="speak(scope.row.word)">发音</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="phonetic" label="读音" width="130" />
        <el-table-column prop="translation" label="意思" />
        <el-table-column prop="partOfSpeech" label="词性" width="100" />
        <el-table-column prop="bank" label="词库" width="110" />
        <el-table-column prop="extensions" label="延伸词" show-overflow-tooltip />
        <el-table-column label="操作" width="140">
          <template #default="scope">
            <el-button size="small" @click="openEditVocab(scope.row)">编辑</el-button>
            <el-button size="small" type="danger" @click="deleteVocab(scope.row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      </div>
      <el-pagination v-if="vocabTotal>6" v-model:current-page="vocabPage" :total="vocabTotal" :page-size="6" layout="prev,pager,next" style="margin-top:12px;justify-content:center" @current-change="onVocabPageChange" />
    </div>

    <!-- ======================== 学习资料 ======================== -->
    <div v-if="currentTab === 'resources'">
      <PageHeader title="学习资料" sub="学科资料的维护与教师间共享">
        <template #actions>
          <template v-if="!viewingTeacher">
            <el-button type="primary" @click="openAddResource">添加资料</el-button>
          </template>
        </template>
      </PageHeader>
      <div class="vocab-toolbar">
        <el-select v-model="teacherSubjectId" placeholder="全部学科" clearable style="width:130px" @change="loadResources">
          <el-option v-for="s in subjects" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <el-button v-if="!viewingTeacher" plain @click="promptAddSubject">添加学科</el-button>
        <el-button v-if="!viewingTeacher && teacherSubjectId && selectedSubjectName !== '英语'" plain type="danger" @click="confirmDeleteSubject">
          删除学科「{{ selectedSubjectName }}」
        </el-button>
        <el-input v-if="!viewingTeacher" v-model="resourceKeyword" placeholder="按标题搜索..." style="width:200px" clearable @change="loadResources" />
        <!-- 教师间共享资料：搜索其他教师账号 -->
        <template v-if="!viewingTeacher">
          <el-select
            v-model="teacherSelectId"
            filterable remote clearable reserve-keyword
            placeholder="搜索/选择教师账号..."
            :remote-method="remoteSearchTeachers"
            :loading="searchingTeachers"
            style="width:250px"
            @visible-change="onTeacherDropdown"
            @change="onSelectTeacher"
          >
            <el-option
              v-for="t in teacherOptions"
              :key="t.id"
              :label="t.username + (t.realName ? '（' + t.realName + '）' : '')"
              :value="t.id"
            />
          </el-select>
        </template>
        <el-button @click="loadResources" :loading="loading">刷新</el-button>
        <template v-if="viewingTeacher">
          <el-button type="success" plain :loading="copyingAll" @click="copyAllFromTeacher">一键复制全部</el-button>
          <el-tag type="success" closable @close="exitSharedView">正在查看 {{ viewingTeacher.name }} 老师的共享资料</el-tag>
          <el-button size="small" plain @click="exitSharedView">返回我的资料</el-button>
        </template>
      </div>
      <div class="table-wrapper">
      <el-table :data="resources" v-loading="loading" empty-text="暂无学习资料" style="margin-top:12px">
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="type" label="类型" width="100">
          <template #default="scope">
            <el-tag size="small">{{ scope.row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260">
          <template #default="scope">
            <el-button size="small" @click="$router.push('/resource/' + scope.row.id)">详情</el-button>
            <template v-if="viewingTeacher">
              <el-button size="small" type="success" plain @click="copyResourceToMine(scope.row)">复制到我的库</el-button>
            </template>
            <template v-else>
              <el-button size="small" @click="openEditResource(scope.row)">编辑</el-button>
              <el-button size="small" type="danger" @click="deleteResource(scope.row.id)">删除</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      </div>
      <el-pagination v-if="resourceTotal>6" v-model:current-page="resourcePage" :total="resourceTotal" :page-size="6" layout="prev,pager,next" style="margin-top:12px;justify-content:center" @change="loadResources" />
    </div>

    <!-- ======================== 学生档案弹窗 ======================== -->
    <el-dialog v-model="dialogProfile" title="编辑学生基本信息">
      <el-form :model="profile" label-position="top">
        <el-form-item label="年级">
          <el-input v-model="profile.grade" placeholder="如：初二" />
        </el-form-item>
        <el-form-item label="学科">
          <el-input v-model="profile.subject" placeholder="如：英语" />
        </el-form-item>
        <el-form-item label="掌握程度">
          <el-select v-model="profile.level" placeholder="请选择掌握程度" clearable style="width:100%">
            <el-option label="初级" value="初级" />
            <el-option label="中级" value="中级" />
            <el-option label="高级" value="高级" />
          </el-select>
        </el-form-item>
        <el-form-item label="教材">
          <el-input v-model="profile.textbook" placeholder="教材（选填）" />
        </el-form-item>
        <el-form-item label="学习目标">
          <el-input v-model="profile.goals" placeholder="学习目标（选填）" />
        </el-form-item>
        <el-form-item label="薄弱点">
          <el-input v-model="profile.weakPoints" type="textarea" placeholder="薄弱点（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogProfile = false">取消</el-button>
        <el-button type="primary" @click="saveStudentProfile">保存</el-button>
      </template>
    </el-dialog>

    <!-- ======================== AI智能扩展弹窗 ======================== -->
    <el-dialog v-model="dialogAIVocab" title="AI智能扩展词汇" width="600px" :close-on-click-modal="false">
      <!-- 输入区 -->
      <div v-if="!aiResult" style="text-align:center;padding:20px">
        <el-alert title="输入一个英文单词，AI 自动分析释义并推荐延伸词" type="info" :closable="false" style="margin-bottom:16px" />
        <div style="display:flex;gap:8px;max-width:400px;margin:0 auto">
          <el-input v-model="aiWordInput" placeholder="输入英文单词，如 happy" size="large" @keyup.enter="onAIExpand" />
          <el-button type="primary" size="large" @click="onAIExpand" :loading="aiProcessing">
            {{ aiProcessing ? 'AI分析中...' : '分析' }}
          </el-button>
        </div>
      </div>
      <!-- 结果区 — 主词信息 + 延伸词勾选 -->
      <div v-if="aiResult">
        <!-- 主词信息（可编辑） -->
        <el-descriptions :column="3" border size="small" style="margin-bottom:16px">
          <el-descriptions-item label="单词">
            <strong style="font-size:18px;color:var(--accent)">{{ aiResult.word }}</strong>
            <span style="cursor:pointer;opacity:.55;margin-left:6px" title="播放发音" @click="speak(aiResult.word)">发音</span>
          </el-descriptions-item>
          <el-descriptions-item label="释义">
            <el-input v-model="aiResult.translation" size="small" style="width:140px" />
          </el-descriptions-item>
          <el-descriptions-item label="词性">
            <el-input v-model="aiResult.partOfSpeech" size="small" style="width:100px" />
          </el-descriptions-item>
        </el-descriptions>

        <!-- 延伸词（可勾选/删除/添加） -->
        <div style="margin-bottom:12px">
          <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px">
            <h4 style="margin:0;color:var(--color-text-regular)">延伸词（勾选的将存入 extensions 字段）</h4>
            <el-button size="small" type="primary" text @click="addCustomExtension">+ 自定义延伸词</el-button>
          </div>
          <div style="max-height:220px;overflow-y:auto;border:1px solid var(--border);border-radius:6px;padding:8px">
            <div v-for="(ext, i) in aiResult.extensions" :key="i"
              style="display:flex;align-items:center;gap:8px;padding:6px 8px;border-radius:4px;margin-bottom:4px"
              :style="{background: ext.selected ? 'var(--accent-soft)' : '#f5f5f5'}">
              <el-checkbox v-model="ext.selected" />
              <el-input v-model="ext.word" size="small" style="width:130px" placeholder="单词" />
              <span style="color:var(--muted)"></span>
              <el-input v-model="ext.translation" size="small" style="flex:1" placeholder="释义" />
              <el-button size="small" type="danger" circle text @click="aiResult.extensions.splice(i,1)">×</el-button>
            </div>
            <el-empty v-if="!aiResult.extensions.length" description="无延伸词" :image-size="40" />
          </div>
          <p style="color:var(--muted);font-size:12px;margin-top:4px">
            已选 {{ aiResult.extensions.filter(e => e.selected).length }} / {{ aiResult.extensions.length }} 个延伸词
          </p>
        </div>

        <!-- 操作按钮 -->
        <div style="display:flex;gap:8px">
          <el-button @click="aiResult=null;aiWordInput=''" style="flex:1">取消，重新输入</el-button>
          <el-button type="primary" @click="saveAIResult" :loading="aiSaving" style="flex:1">
            保存到词库
          </el-button>
        </div>
      </div>
    </el-dialog>

    <!-- ======================== 布置/编辑作业弹窗 ======================== -->
    <el-dialog v-model="dialogHomework" :title="editingHomeworkId ? '编辑作业' : '布置作业'" width="500px">
      <el-form :model="homeworkForm" label-position="top">
        <el-input v-model="homeworkForm.title" placeholder="作业标题" />
        <el-select v-model="homeworkForm.subject" placeholder="作业分类（学科）" style="width:100%;margin-top:12px">
          <el-option v-for="s in subjects" :key="s.id" :label="s.name" :value="s.name" />
        </el-select>
        <el-input v-model="homeworkForm.content" type="textarea" :rows="6" placeholder="作业内容" style="margin-top:12px" />
        <el-date-picker
          v-model="homeworkForm.deadline"
          type="datetime"
          format="YYYY-MM-DD HH:mm"
          value-format="YYYY-MM-DDTHH:mm:ss"
          placeholder="选择截止日期和时间"
          :default-time="new Date(2000, 1, 1, 23, 59, 0)"
          :teleported="false"
          style="width:100%;margin-top:12px"
        />
        <!-- 上传图片附件 -->
        <div style="margin-top:12px">
          <label style="font-size:14px;color:var(--color-text-regular);margin-bottom:4px;display:block">附件图片（可选，可多张）</label>
          <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
            <input
              ref="cameraInput1"
              type="file"
              accept="image/*"
              capture="environment"
              style="display:none"
              @change="onCameraCapture($event, 'homework')"
            />
            <el-button plain type="success" @click="$refs.cameraInput1.click()">拍照</el-button>
            <el-upload :http-request="uploadHomeworkAttachment" :show-file-list="false" accept="image/*">
              <el-button plain>添加图片</el-button>
            </el-upload>
            <span style="color:var(--muted);font-size:12px">可多次点击添加多张，拍照自动压缩</span>
          </div>
          <div v-if="homeworkAttachmentUrls.length" style="display:flex;flex-wrap:wrap;gap:8px;margin-top:8px">
            <div v-for="(url, i) in homeworkAttachmentUrls" :key="i"
              style="position:relative;width:80px;height:80px;border-radius:6px;overflow:hidden;border:1px solid var(--border)">
              <el-image :src="fileUrl(url)" style="width:100%;height:100%" fit="cover" :preview-src-list="homeworkAttachmentUrls.map(fileUrl)" :initial-index="i" />
              <el-button size="small" circle type="danger"
                style="position:absolute;top:2px;right:2px;width:18px;height:18px"
                @click="homeworkAttachmentUrls.splice(i,1)">×</el-button>
            </div>
          </div>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogHomework = false; editingHomeworkId = null">取消</el-button>
        <el-button type="primary" @click="addStudentHomework">{{ editingHomeworkId ? '保存修改' : '提交' }}</el-button>
      </template>
    </el-dialog>

    <!-- ======================== AI 答题卡出卷向导 ======================== -->
    <el-dialog v-model="dialogSheet" title="AI 答题卡出卷" width="800px" class="sheet-dialog" :close-on-click-modal="false">
      <el-steps :active="sheetStep" simple style="margin-bottom:16px">
        <el-step title="输入题目" />
        <el-step title="核验答案" />
        <el-step title="发布" />
      </el-steps>

      <!-- ---------- 第 1 步：粘贴题目文本 ---------- -->
      <div v-if="sheetStep === 0">
        <div v-if="sheetAppending" class="sheet-append-tip">
          追加模式：解析结果会接在已有 {{ sheetSections.length }} 个大题后面，不会覆盖前面的题。
        </div>
        <el-input
          v-model="sheetRawText"
          type="textarea"
          :rows="12"
          placeholder="选好学科后一次粘一个大题（阅读/材料题请连文章一起粘），不带答案也行，AI 会自己解。英语例：&#10;二、完形填空&#10;Tom is a middle school student. He __1__ up at six every morning ...&#10;1. A. get  B. gets  C. getting&#10;数学例：三、解答题&#10;解方程：2x + 6 = 20"
        />
        <!-- 图片识别出卷：拍照/截图上传，多模态模型转成文字后走与上面相同的出卷流程 -->
        <div style="display:flex;align-items:center;gap:10px;margin-top:10px;flex-wrap:wrap">
          <el-upload v-if="!sheetImagePreview" accept="image/*" :show-file-list="false"
            :auto-upload="false" :on-change="onSheetImageChange">
            <el-button plain>上传题目图片（拍照/截图）</el-button>
          </el-upload>
          <template v-else>
            <div style="position:relative">
              <el-image :src="sheetImagePreview" style="width:96px;height:96px;border-radius:6px;border:1px solid var(--border)"
                fit="cover" :preview-src-list="[sheetImagePreview]" />
              <el-button size="small" circle type="danger"
                style="position:absolute;top:-8px;right:-8px;width:20px;height:20px" @click="clearSheetImage">×</el-button>
            </div>
            <span style="font-size:12px;color:var(--warning)">已选图片，解析时将用「图片识别」而不是上面的文本</span>
          </template>
        </div>
        <div style="display:flex;align-items:center;gap:12px;margin-top:12px;flex-wrap:wrap">
          <span style="font-size:14px;color:var(--color-text-regular)">学科</span>
          <el-select v-model="sheetSubject" style="width:110px">
            <el-option v-for="s in SHEET_SUBJECTS" :key="s" :label="s" :value="s" />
          </el-select>
          <span style="font-size:14px;color:var(--color-text-regular)">这段是</span>
          <!-- 题型提示让老师自己写：语文的“文言文阅读”、数学的“计算题”没法穷举成下拉 -->
          <el-input v-model="sheetSectionHint" clearable style="width:250px"
            placeholder="如：完形填空 / 文言文阅读 / 解答题，留空让 AI 判断" />
          <span style="font-size:14px;color:var(--color-text-regular)">解析后按满分 100 分自动均分每题分值</span>
          <el-button type="primary" :loading="sheetParsing" @click="doParseQuestions">{{ sheetImageFile ? 'AI 图片识别出卷' : 'AI 解析出卷' }}</el-button>
        </div>
        <p style="font-size:12px;color:var(--muted);margin-top:8px">
          <template v-if="sheetParsing">AI 正在按「{{ sheetSubject }}」解析，约需 10~30 秒（题量大时更久），请勿关闭弹窗…</template>
          <template v-else>选好学科、写明题型比让 AI 猜准得多；文本解析约需 10~30 秒，图片识别会再久一点，请耐心等待。</template>
        </p>
      </div>

      <!-- ---------- 第 2 步：按大题核验 ---------- -->
      <div v-if="sheetStep === 1">
        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:10px;flex-wrap:wrap;gap:8px">
          <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap">
            <span style="font-size:14px;color:var(--color-text-regular)">
              共 <strong>{{ sheetSections.length }}</strong> 个大题、<strong>{{ sheetQuestionCount }}</strong> 小题，满分 <strong>{{ sheetFullScore }}</strong> 分
            </span>
            <!-- 手动加减题后总分会偏离 100，一键重新均分 -->
            <el-button size="small" text type="primary" @click="redistributeSheetScores">重新均分（满分 100）</el-button>
          </div>
          <span style="font-size:12px;color:var(--warning)">AI 给的答案可能有误，发布前请逐题确认</span>
        </div>

        <div v-for="(sec, si) in sheetSections" :key="si" class="sheet-sec-card">
          <div class="sheet-sec-head">
            <span class="sheet-sec-seq">{{ si + 1 }}</span>
            <el-select v-model="sec.sectionType" size="small" style="width:120px" @change="onSectionTypeChange(sec)">
              <el-option v-for="t in SECTION_TYPES" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
            <el-input v-model="sec.title" size="small" placeholder="大题标题" style="flex:1;min-width:130px" />
            <el-button size="small" text type="danger" @click="sheetSections.splice(si, 1)">删除大题</el-button>
          </div>
          <el-input v-model="sec.instruction" size="small" placeholder="答题要求（可选）" />

          <!-- 材料原文：完形/阅读/对话必须保留，学生作答时要看（解答题/单句题没有共享材料） -->
          <div v-if="!['standalone', 'solution'].includes(sec.sectionType)" class="sheet-sec-block">
            <div class="sheet-sec-label">
              材料原文
              <span class="sheet-sec-tip">文章里的空写成 _1_ _2_，编号对应下面的小题号</span>
            </div>
            <el-input v-model="sec.passage" type="textarea" :rows="6"
              placeholder="完形填空/阅读的文章原文、对话原文或写作要求" />
          </div>

          <!-- 选词填空的备选词库 -->
          <div v-if="sec.sectionType === 'word_bank'" class="sheet-sec-block">
            <div class="sheet-sec-label">备选词库<span class="sheet-sec-tip">多个词用 / 分隔</span></div>
            <el-input v-model="sec.wordBankText" placeholder="如：make / happy / carefully" />
          </div>

          <!-- 整个大题共用一个选项框（补全对话七选五） -->
          <div v-if="sec.sectionType === 'dialogue'" class="sheet-sec-block">
            <div class="sheet-sec-label">
              共用选项框
              <span class="sheet-sec-tip">所有小题从这里选答案，没有就留空</span>
              <el-button size="small" text type="primary" @click="addSharedOption(sec)">+ 加一项</el-button>
            </div>
            <div v-for="(o, oi) in sec.sharedOptions" :key="oi" class="sheet-q-option">
              <span class="sheet-q-okey">{{ o.key }}</span>
              <el-input v-model="o.text" size="small" placeholder="选项内容" />
              <el-button size="small" text type="danger" @click="sec.sharedOptions.splice(oi, 1)">×</el-button>
            </div>
          </div>

          <div v-for="(q, i) in sec.questions" :key="i" class="sheet-q-card">
            <div class="sheet-q-head">
              <span class="sheet-q-seq">第 {{ globalSeq(si, i) }} 题</span>
              <el-select v-model="q.questionType" size="small" style="width:110px">
                <el-option label="单选" value="single" />
                <el-option label="多选" value="multi" />
                <el-option label="判断" value="judge" />
                <el-option label="填空" value="blank" />
                <el-option label="简答/写作" value="short" />
              </el-select>
              <el-input v-model.number="q.score" type="number" size="small" style="width:80px" />
              <span style="font-size:12px;color:var(--muted)">分</span>
              <el-button size="small" text type="danger" @click="sec.questions.splice(i, 1)">删除本题</el-button>
            </div>
            <el-input v-model="q.content" type="textarea" :rows="2" placeholder="题干" />
            <!-- 选项（选择题/判断题） -->
            <div v-if="q.options && q.options.length" class="sheet-q-options">
              <div v-for="(o, oi) in q.options" :key="oi" class="sheet-q-option">
                <span class="sheet-q-okey">{{ o.key }}</span>
                <el-input v-model="o.text" size="small" placeholder="选项内容" />
              </div>
            </div>
            <div class="sheet-q-answer">
              <span class="sheet-q-label">标准答案</span>
              <el-input v-model="q.standardAnswer" size="small"
                :placeholder="q.questionType === 'blank' ? '多种写法用竖线分隔，如 is|is not' : '如 B 或 AC'" />
            </div>
            <el-input v-model="q.analysis" type="textarea" :rows="2" placeholder="解析（学生提交后可见）" style="margin-top:6px" />
            <el-input v-model="q.knowledgePoint" size="small" placeholder="知识点标签（可选）" style="margin-top:6px" />
          </div>
          <el-button size="small" plain @click="addQuestion(sec)">加一道小题</el-button>
        </div>

        <el-empty v-if="!sheetSections.length" description="没有题目，请返回上一步重新解析" />
        <el-button v-if="sheetSections.length" plain type="primary" style="width:100%;margin-top:8px"
          @click="startAppendSection">再粘一段（追加大题）</el-button>
      </div>

      <!-- ---------- 第 3 步：发布 ---------- -->
      <div v-if="sheetStep === 2">
        <el-form label-position="top">
          <el-input v-model="sheetForm.title" placeholder="答题卡标题，如：第三单元语法小测" />
          <el-input v-model="sheetForm.content" type="textarea" :rows="3" placeholder="作答说明（可选）" style="margin-top:12px" />
          <el-date-picker
            v-model="sheetForm.deadline"
            type="datetime"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="选择截止日期和时间"
            :default-time="new Date(2000, 1, 1, 23, 59, 0)"
            :teleported="false"
            style="width:100%;margin-top:12px"
          />
        </el-form>
        <div class="sheet-summary">
          将给 <strong>{{ sheetStudentName }}</strong> 布置<strong>{{ sheetSubject }}</strong>答题卡：<strong>{{ sheetSections.length }}</strong> 个大题、<strong>{{ sheetQuestionCount }}</strong> 小题，
          满分 <strong>{{ sheetFullScore }}</strong> 分。学生提交后系统自动判分，主观题由 AI 按学科评分，你可以再复核调分。
        </div>
      </div>

      <template #footer>
        <el-button v-if="sheetStep > 0" @click="sheetStep--">上一步</el-button>
        <el-button @click="dialogSheet = false">取消</el-button>
        <el-button v-if="sheetStep === 1" type="primary" :disabled="!sheetQuestionCount" @click="sheetStep = 2">下一步</el-button>
        <el-button v-if="sheetStep === 2" type="primary" :loading="sheetPublishing" @click="doPublishSheet">发布答题卡</el-button>
      </template>
    </el-dialog>

    <!-- ======================== 答题卡批改复核弹窗 ======================== -->
    <el-dialog v-model="dialogSheetReview" title="答题卡批改" width="760px">
      <div v-loading="sheetReviewLoading">
        <template v-if="sheetResult">
          <div class="sheet-score-bar">
            <span>得分 <strong>{{ sheetResult.gotScore }}</strong> / {{ sheetResult.fullScore }}</span>
            <span>全对 <strong>{{ sheetResult.rightCount }}</strong> / {{ sheetResult.total }} 题</span>
            <span v-if="sheetResult.review">百分制 <strong>{{ sheetResult.review.score }}</strong></span>
          </div>
          <div v-for="(g, gi) in reviewGroups" :key="gi" class="sheet-sec-card">
            <div v-if="g.sectionType !== 'standalone'" class="sheet-sec-head">
              <span class="sheet-sec-seq">{{ gi + 1 }}</span>
              <strong style="font-size:14px">{{ g.title || sectionLabel(g.sectionType) }}</strong>
              <el-tag size="small" type="primary">{{ sectionLabel(g.sectionType) }}</el-tag>
            </div>
            <!-- 判主观题时要对着原文看，默认收起免得刷很久 -->
            <div v-if="g.passage" class="sheet-sec-block">
              <div class="sheet-sec-label" style="cursor:pointer" @click="reviewPassageOpen[gi] = !reviewPassageOpen[gi]">
                材料原文
                <span class="sheet-sec-tip">{{ reviewPassageOpen[gi] ? '收起 ▲' : '展开 ▼' }}</span>
              </div>
              <div v-show="reviewPassageOpen[gi]" class="sheet-sec-passage-text">{{ g.passage }}</div>
            </div>
            <div v-if="g.wordBank && g.wordBank.length" class="sheet-sec-block">
              <div class="sheet-sec-label">备选词</div>
              <el-tag v-for="(w, wi) in g.wordBank" :key="wi" size="small" round style="margin-right:6px">{{ w }}</el-tag>
            </div>

            <div v-for="it in g.items" :key="it.questionId" class="sheet-r-card"
              :class="it.correct === 1 ? 'sheet-r-right' : (it.correct === 0 ? 'sheet-r-wrong' : 'sheet-r-pending')">
              <div class="sheet-r-head">
                <span>第 {{ it.seq }} 题・{{ typeLabel(it.questionType) }}</span>
                <span class="sheet-r-flag">
                  {{ it.correct === 1 ? '全对' : (it.correct === 0 ? '有误' : '待判') }}
                  <el-tag v-if="it.teacherFixed === 1" size="small" type="warning">已复核</el-tag>
                </span>
              </div>
              <div class="sheet-r-content">{{ it.content }}</div>
              <div class="sheet-r-row"><span class="sheet-r-label">学生答案</span>{{ it.studentAnswer || '（未作答）' }}</div>
              <div class="sheet-r-row"><span class="sheet-r-label">标准答案</span>{{ it.standardAnswer }}</div>
              <div v-if="it.aiComment" class="sheet-r-row"><span class="sheet-r-label">AI 点评</span>{{ it.aiComment }}</div>
              <div class="sheet-r-fix">
                <span class="sheet-r-label">调分</span>
                <el-input v-model.number="it.gotScore" type="number" size="small" style="width:80px" />
                <span style="font-size:12px;color:var(--muted)">/ {{ it.score }} 分</span>
                <el-switch v-model="it.correctBool" size="small" active-text="判对" inactive-text="判错" />
              </div>
            </div>
          </div>
          <el-input v-model="sheetFixComment" type="textarea" :rows="2" placeholder="整体评语（留空则自动生成）" style="margin-top:12px" />
        </template>
        <el-empty v-else-if="!sheetReviewLoading" description="学生还没有提交这张答题卡" />
      </div>
      <template #footer>
        <el-button @click="dialogSheetReview = false">关闭</el-button>
        <el-button v-if="sheetResult" type="primary" :loading="sheetFixing" @click="doFixSheet">保存复核</el-button>
      </template>
    </el-dialog>

    <!-- ======================== 作业详情弹窗（含提交与批改） ======================== -->
    <el-dialog v-model="dialogHwDetail" title="作业详情" width="720px" class="submit-dialog">
      <div v-if="hwDetail" v-loading="hwDetailLoading">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="作业标题" :span="2">
            <strong>{{ hwDetail.title }}</strong>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTag(hwDetail.status)" size="small">{{ hwDetail.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="截止时间">
            {{ hwDetail.deadline ? hwDetail.deadline.replace('T', ' ').slice(0, 16) : '无截止时间' }}
          </el-descriptions-item>
          <el-descriptions-item label="作业内容" :span="2">
            <div style="white-space:pre-wrap;line-height:1.8;font-size:14px;max-height:200px;overflow-y:auto">
              {{ hwDetail.content || '无文字内容' }}
            </div>
          </el-descriptions-item>
        </el-descriptions>
        <!-- 附件图片 -->
        <div v-if="hwDetailImages.length" style="margin-top:16px">
          <h4 style="margin:0 0 8px 0;color:var(--color-text-regular)">附件图片</h4>
          <div style="display:flex;flex-wrap:wrap;gap:8px">
            <el-image
              v-for="(url, i) in hwDetailImages"
              :key="i"
              :src="fileUrl(url)"
              style="width:140px;height:140px;border-radius:8px;border:1px solid var(--border);cursor:pointer"
              fit="cover"
              :preview-src-list="hwDetailImages.map(fileUrl)"
              :initial-index="i"
            />
          </div>
        </div>

        <!-- 学生提交与批改：看完作业往下滚就是提交内容，提交下面直接是批改表单，不用再开别的弹窗 -->
        <div v-if="hwDetail.type !== 'answer_sheet'" style="margin-top:16px">
          <h4 style="margin:0 0 8px 0;color:var(--accent)">学生提交与批改</h4>
          <div v-for="sub in hwDetailSubmits" :key="sub.id" class="submit-card">
            <p style="margin:0 0 8px 0;font-size:12px;color:var(--muted)">
              提交时间：{{ (sub.submitTime || '').replace('T', ' ').slice(0, 16) || '-' }}
            </p>
            <!-- 学生回答 -->
            <div v-if="sub.textAnswer" class="submit-answer">{{ sub.textAnswer }}</div>
            <!-- 图片预览区 -->
            <div v-if="sub.fileUrl" class="submit-images">
              <!-- submit-card 的 backdrop-filter 会把就地渲染的 fixed 预览层困在卡片里裁掉，
                   preview-teleported 把预览层挂到 body 上才能全屏，与布置附件图片的行为一致 -->
              <el-image
                v-for="(url, i) in sub.fileUrl.split(',').filter(Boolean)"
                :key="i"
                :src="fileUrl(url)"
                class="submit-image-item"
                fit="cover"
                :preview-src-list="sub.fileUrl.split(',').filter(Boolean).map(fileUrl)"
                :initial-index="i"
                preview-teleported
                hide-on-click-modal
              />
            </div>
            <p v-if="!sub.textAnswer && !sub.fileUrl" style="margin:0;color:var(--muted);font-size:13px">（无文字与图片内容）</p>
            <!-- 批改表单：分数 / 评语 / 语音 / 打回重做 -->
            <div class="review-form" v-if="reviewForms[sub.id]">
              <div class="review-form-row">
                <el-input v-model="reviewForms[sub.id].score" type="number" placeholder="分数" class="review-score" />
                <el-input v-model="reviewForms[sub.id].comment" type="textarea" :rows="2" placeholder="评语" class="review-comment" />
              </div>
              <!-- 语音评语：口述比打字快，学生还能听到发音示范和语气 -->
              <div class="review-voice">
                <span class="review-voice-label">语音评语</span>
                <!-- 正在给这条提交录音 -->
                <template v-if="voiceRecording && voiceTarget === sub.id">
                  <el-button type="danger" size="small" @click="stopVoice">停止录音（{{ formatDuration(voiceSeconds) }}）</el-button>
                  <span class="review-voice-tip">最长 2 分钟，到时自动停止</span>
                </template>
                <!-- 刚录好、尚未随批改提交 -->
                <template v-else-if="voiceTarget === sub.id && voicePreviewUrl">
                  <audio :src="voicePreviewUrl" controls class="review-voice-audio" />
                  <el-button size="small" text type="danger" @click="dropVoice(sub.id)">删除重录</el-button>
                </template>
                <!-- 之前已保存过语音评语 -->
                <template v-else-if="reviewForms[sub.id].voiceUrl">
                  <audio :src="fileUrl(reviewForms[sub.id].voiceUrl)" controls class="review-voice-audio" />
                  <el-button size="small" text @click="startVoice(sub.id)">重新录制</el-button>
                  <el-button size="small" text type="danger" @click="dropVoice(sub.id)">删除</el-button>
                </template>
                <template v-else>
                  <el-button size="small" :disabled="!voiceSupported" @click="startVoice(sub.id)">开始录音</el-button>
                  <span v-if="!voiceSupported" class="review-voice-tip">当前环境不支持录音（需用 HTTPS 访问）</span>
                </template>
              </div>
              <div class="review-form-actions">
                <div class="review-toggle">
                  <span class="review-toggle-label">打回重做</span>
                  <el-switch v-model="reviewForms[sub.id].needRedo" active-color="var(--negative)" />
                </div>
                <el-button type="primary" :loading="reviewSaving === sub.id" @click="doReview(sub.id)">提交批改</el-button>
              </div>
            </div>
          </div>
          <el-empty v-if="!hwDetailSubmits.length" description="学生还没提交，提交后可在这里直接批改" :image-size="60" />
        </div>
        <!-- 答题卡作业的提交由系统逐题判分，引导去专属复核入口 -->
        <div v-else style="margin-top:16px">
          <div v-if="hwDetail.status !== '未提交'" style="padding:14px;background:var(--raised);border-left:4px solid var(--accent);border-radius:6px">
            <p style="margin:0;font-size:14px;color:var(--color-text-regular);line-height:1.7">
              这是答题卡作业，学生提交后系统已自动判分。点击作业卡片上的「答题卡批改」可查看逐题对错、AI 点评并复核调分。
            </p>
          </div>
          <el-empty v-else description="学生还没提交，答题卡交卷后自动判分" :image-size="60" />
        </div>
      </div>
      <el-empty v-else-if="!hwDetailLoading" description="作业不存在" />
    </el-dialog>

    <!-- ======================== 添加词汇弹窗 ======================== -->
    <el-dialog v-model="dialogVocab" title="添加词汇" width="420px">
      <el-form label-position="top">
        <el-input v-model="vocabForm.word" placeholder="单词（必填）" />
        <el-input v-model="vocabForm.phonetic" placeholder="读音/音标（如 /ˈæpl/）" style="margin-top:12px" />
        <el-input v-model="vocabForm.translation" placeholder="意思（必填）" style="margin-top:12px" />
        <el-input v-model="vocabForm.partOfSpeech" placeholder="词性（如：noun/verb/adj）" style="margin-top:12px" />
        <el-select v-model="vocabForm.bank" placeholder="所属词库" style="margin-top:12px;width:100%">
          <el-option v-for="b in vocabBanksList" :key="b" :label="b" :value="b" />
        </el-select>
        <div style="margin-top:12px">
          <label style="font-size:14px;color:var(--color-text-regular);margin-bottom:4px;display:block">延伸词</label>
          <div style="display:flex;flex-wrap:wrap;gap:4px;padding:4px;border:1px solid var(--border);border-radius:4px;min-height:36px;align-items:center">
            <el-tag v-for="(t, i) in vocabTags" :key="i" closable @close="vocabTags.splice(i,1)" size="small">{{ t }}</el-tag>
            <input v-model="vocabTagInput" placeholder="输入后回车" style="border:none;outline:none;flex:1;min-width:80px;font-size:14px"
              @keydown.enter="onVocabTagEnter" @keydown.backspace="onVocabTagBackspace" />
          </div>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVocab = false">取消</el-button>
        <el-button type="primary" @click="addVocabulary">添加</el-button>
      </template>
    </el-dialog>

    <!-- ======================== 添加资料弹窗 ======================== -->
    <el-dialog v-model="dialogResource" title="添加学习资料" width="500px">
      <el-form label-position="top">
        <el-form-item label="标题" required>
          <el-input v-model="resourceForm.title" placeholder="资料标题" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="resourceForm.type" filterable allow-create default-first-option placeholder="选择已有类型，或直接输入新类型" style="width: 100%">
            <el-option v-for="t in resourceTypeOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="学科">
          <el-select v-model="resourceForm.subjectId" style="width: 100%" @change="onAddResourceSubjectChange">
            <el-option v-for="s in subjects" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="文字内容">
          <el-input v-model="resourceForm.content" type="textarea" :rows="6" placeholder="输入文字内容" />
        </el-form-item>
        <el-form-item label="上传图片/Word文档">
          <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
            <input
              ref="cameraInput2"
              type="file"
              accept="image/*"
              capture="environment"
              style="display:none"
              @change="onCameraCapture($event, 'resource')"
            />
            <el-button type="success" @click="$refs.cameraInput2.click()">拍照</el-button>
            <el-upload :http-request="uploadResourceFile" :show-file-list="false" accept="image/*,.doc,.docx,.pdf,.txt,.rtf,.ppt,.pptx,.xls,.xlsx,.csv">
              <el-button type="primary" plain>添加文件</el-button>
            </el-upload>
            <span style="color:var(--muted);font-size:12px">支持图片、Word/PDF/TXT/PPT/Excel 等文档，可多次添加</span>
          </div>
          <div v-if="resourceFileUrls.length" style="display:flex;flex-wrap:wrap;gap:8px;margin-top:8px">
            <template v-for="(url, i) in resourceFileUrls" :key="i">
              <!-- Word 文档：文件条展示 -->
              <div v-if="isDocFile(url)"
                style="position:relative;display:flex;align-items:center;gap:6px;padding:8px 30px 8px 10px;border:1px solid var(--border);border-radius:6px;background:#f8fafc;max-width:230px">
                <span></span>
                <span style="font-size:12px;color:var(--text);overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ fileNameFromUrl(url) }}</span>
                <el-button size="small" circle type="danger"
                  style="position:absolute;top:50%;right:4px;transform:translateY(-50%);width:18px;height:18px"
                  @click="resourceFileUrls.splice(i,1)">×</el-button>
              </div>
              <!-- 图片：缩略图展示 -->
              <div v-else style="position:relative;width:80px;height:80px;border-radius:6px;overflow:hidden;border:1px solid var(--border)">
                <el-image :src="fileUrl(url)" style="width:100%;height:100%" fit="cover" :preview-src-list="resourceImageUrls.map(fileUrl)" :initial-index="resourceImageUrls.indexOf(url)" preview-teleported hide-on-click-modal />
                <el-button size="small" circle type="danger"
                  style="position:absolute;top:2px;right:2px;width:18px;height:18px"
                  @click="resourceFileUrls.splice(i,1)">×</el-button>
              </div>
            </template>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogResource = false">取消</el-button>
        <el-button type="primary" @click="addResource" :loading="uploading">添加</el-button>
      </template>
    </el-dialog>

    <!-- ======================== 编辑词汇弹窗 ======================== -->
    <el-dialog v-model="dialogEditVocab" title="编辑词汇" width="420px">
      <el-form label-position="top">
        <el-input v-model="editVocabForm.word" placeholder="单词" />
        <el-input v-model="editVocabForm.phonetic" placeholder="读音/音标（如 /ˈæpl/）" style="margin-top:12px" />
        <el-input v-model="editVocabForm.translation" placeholder="意思" style="margin-top:12px" />
        <el-input v-model="editVocabForm.partOfSpeech" placeholder="词性" style="margin-top:12px" />
        <el-select v-model="editVocabForm.bank" placeholder="所属词库" style="margin-top:12px;width:100%">
          <el-option v-for="b in vocabBanksList" :key="b" :label="b" :value="b" />
        </el-select>
        <div style="margin-top:12px">
          <label style="font-size:14px;color:var(--color-text-regular);margin-bottom:4px;display:block">延伸词</label>
          <div style="display:flex;flex-wrap:wrap;gap:4px;padding:4px;border:1px solid var(--border);border-radius:4px;min-height:36px;align-items:center">
            <el-tag v-for="(t, i) in editVocabTags" :key="i" closable @close="editVocabTags.splice(i,1)" size="small">{{ t }}</el-tag>
            <input v-model="editVocabTagInput" placeholder="输入后回车" style="border:none;outline:none;flex:1;min-width:80px;font-size:14px"
              @keydown.enter="onEditVocabTagEnter" @keydown.backspace="onEditVocabTagBackspace" />
          </div>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogEditVocab = false">取消</el-button>
        <el-button type="primary" @click="doEditVocab">保存</el-button>
      </template>
    </el-dialog>

    <!-- ======================== 编辑资料弹窗 ======================== -->
    <el-dialog v-model="dialogEditResource" title="编辑学习资料" width="500px">
      <el-form label-position="top">
        <el-form-item label="标题" required>
          <el-input v-model="editResourceForm.title" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="editResourceForm.type" filterable allow-create default-first-option placeholder="选择已有类型，或直接输入新类型" style="width:100%">
            <el-option v-for="t in editResourceTypeOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="学科">
          <el-select v-model="editResourceForm.subjectId" style="width:100%" @change="onEditResourceSubjectChange">
            <el-option v-for="s in subjects" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="文字内容">
          <el-input v-model="editResourceForm.content" type="textarea" :rows="6" />
        </el-form-item>
        <el-form-item label="替换图片/Word文档（可选）">
          <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
            <input
              ref="cameraInput3"
              type="file"
              accept="image/*"
              capture="environment"
              style="display:none"
              @change="onCameraCapture($event, 'editResource')"
            />
            <el-button type="success" @click="$refs.cameraInput3.click()">拍照</el-button>
            <el-upload :http-request="uploadEditResourceFile" :show-file-list="false" accept="image/*,.doc,.docx,.pdf,.txt,.rtf,.ppt,.pptx,.xls,.xlsx,.csv">
              <el-button plain>添加文件</el-button>
            </el-upload>
            <span style="color:var(--muted);font-size:12px">支持图片、Word/PDF/TXT/PPT/Excel 等文档，可多次添加</span>
          </div>
          <div v-if="editResourceFileUrls.length" style="display:flex;flex-wrap:wrap;gap:8px;margin-top:8px">
            <template v-for="(url, i) in editResourceFileUrls" :key="i">
              <!-- Word 文档：文件条展示 -->
              <div v-if="isDocFile(url)"
                style="position:relative;display:flex;align-items:center;gap:6px;padding:8px 30px 8px 10px;border:1px solid var(--border);border-radius:6px;background:#f8fafc;max-width:230px">
                <span></span>
                <span style="font-size:12px;color:var(--text);overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ fileNameFromUrl(url) }}</span>
                <el-button size="small" circle type="danger"
                  style="position:absolute;top:50%;right:4px;transform:translateY(-50%);width:18px;height:18px"
                  @click="editResourceFileUrls.splice(i,1)">×</el-button>
              </div>
              <!-- 图片：缩略图展示 -->
              <div v-else style="position:relative;width:80px;height:80px;border-radius:6px;overflow:hidden;border:1px solid var(--border)">
                <el-image :src="fileUrl(url)" style="width:100%;height:100%" fit="cover" :preview-src-list="editResourceImageUrls.map(fileUrl)" :initial-index="editResourceImageUrls.indexOf(url)" preview-teleported hide-on-click-modal />
                <el-button size="small" circle type="danger"
                  style="position:absolute;top:2px;right:2px;width:18px;height:18px"
                  @click="editResourceFileUrls.splice(i,1)">×</el-button>
              </div>
            </template>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogEditResource = false">取消</el-button>
        <el-button type="primary" @click="doEditResource" :loading="uploading">保存</el-button>
      </template>
    </el-dialog>

    <!-- ======================== 单词详情弹窗 ======================== -->
    <el-dialog v-model="dialogWordDetail" :title="'' + (detailWord?.word || '')" width="450px">
      <el-descriptions v-if="detailWord" :column="1" border>
        <el-descriptions-item label="单词">
          <strong>{{ detailWord.word }}</strong>
          <span style="cursor:pointer;opacity:.55;margin-left:6px" title="播放发音" @click="speak(detailWord.word)">发音</span>
        </el-descriptions-item>
        <el-descriptions-item label="意思">{{ detailWord.translation }}</el-descriptions-item>
        <el-descriptions-item label="读音">{{ detailWord.phonetic || '-' }}</el-descriptions-item>
        <el-descriptions-item label="词性">{{ detailWord.partOfSpeech || '-' }}</el-descriptions-item>
        <el-descriptions-item label="延伸词">
          <el-tag v-for="(ext, i) in (detailWord.extensions || '').split(',').filter(Boolean)" :key="i" size="small" style="margin:2px">{{ ext }}</el-tag>
          <span v-if="!detailWord.extensions">-</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- ======================== 添加学生弹窗 ======================== -->
    <el-dialog v-model="dialogAddStudent" title="添加学生" width="500px">
      <el-alert title="通过真实姓名搜索学生并绑定到自己名下" type="info" :closable="false" style="margin-bottom:16px" />
      <div style="display:flex;gap:8px">
        <el-input v-model="searchName" placeholder="输入姓名筛选（空=全部未绑定学生）" @keyup.enter="loadAvailableStudents" clearable @clear="loadAvailableStudents" style="flex:1" />
        <el-button type="primary" @click="loadAvailableStudents" :loading="searching">搜索</el-button>
      </div>

      <el-table :data="searchResults" v-loading="searching" style="margin-top:12px" empty-text="未找到匹配的学生，或已全部绑定">
        <el-table-column prop="realName" label="姓名" />
        <el-table-column prop="username" label="账号" />
        <el-table-column prop="phone" label="手机号" />
        <el-table-column label="状态" width="100">
          <template #default="scope">
            <el-tag v-if="scope.row.teacherId === userStore.user?.id" type="success" size="small">已绑定</el-tag>
            <el-tag v-else-if="scope.row.teacherId" type="warning" size="small">已绑定他人</el-tag>
            <el-tag v-else type="info" size="small">未绑定</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="scope">
            <el-button
              v-if="!scope.row.teacherId"
              size="small"
              type="primary"
              @click="doBind(scope.row.id)"
              :loading="bindingId === scope.row.id"
            >绑定</el-button>
            <span v-else-if="scope.row.teacherId === userStore.user?.id" style="color:var(--positive);font-size:12px">我的学生</span>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="dialogAddStudent = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ArrowDown } from '@element-plus/icons-vue';
import PageHeader from '../components/ui/PageHeader.vue';
import StatusTag from '../components/ui/StatusTag.vue';
import EmptyState from '../components/ui/EmptyState.vue';
import {
  addHomework, updateHomework, homeworkList, getHomework, review, reviewResult, getSubmitReview, saveProfile, studentProfile, students, submits,
  addVocab, vocabList, vocabSearch, updateVocab, deleteVocab as deleteVocabApi, vocabBanks,
  addResource as addResourceApi, updateResource, resourceList, resourceTypes, deleteResource as deleteResourceApi, copyResource, copyAllResources,
  searchStudents, bindStudent, upload, uploadVoice, aiExpandVocabulary, searchTeachers,
  subjectList, addSubjectApi, deleteSubjectApi,
  deleteHomework,
  aiParseQuestions, aiParseImageQuestions, assignAnswerSheet, answerSheetResult, fixAnswerSheet
} from '../api';
import { useVoiceRecorder } from '../composables/useVoiceRecorder';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useUserStore } from '../stores/user';
import { useVocabStore } from '../stores/vocab';
import { useResourceStore } from '../stores/resource';
import { isDocFile, isImageUrl, fileNameFromUrl } from '../utils/docFile';
import { compressImage } from '../utils/compress';
import { speakWord } from '../utils/speech';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const vocabStore = useVocabStore();
const resourceStore = useResourceStore();
const currentTab = computed(() => route.meta.tab || 'students');
const apiBase = import.meta.env.VITE_API_BASE_URL || '';
const fileUrl = (url) => (url && url.startsWith('http')) ? url : (apiBase + url);
const loading = ref(false);

// --- 学生管理 ---
const studentList = ref([]);

// --- 作业 ---
const homeworks = ref([]);
const selectedStudentId = ref(null);
const filteredHomeworks = ref([]);
const queriedStudent = ref(null);
const statusFilter = ref(null);  // null=全部, '未提交', '已提交', '已批改', '需重做'
const subjectFilter = ref(null); // null=全部学科；否则只看该学科
const SUBJECT_ORDER = ['语文', '数学', '英语', '政治', '历史', '地理', '物理', '化学', '生物'];
// 当前学生已有的作业学科（用于学科筛选）
const homeworkSubjects = computed(() => {
  const set = new Set(filteredHomeworks.value.map(h => h.subject || '英语'));
  return [...set].sort((a, b) => {
    const ia = SUBJECT_ORDER.indexOf(a); const ib = SUBJECT_ORDER.indexOf(b);
    return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib);
  });
});
// 先按学科筛选，再在结果上做状态筛选
const subjectScopedHomeworks = computed(() => {
  if (!subjectFilter.value) return filteredHomeworks.value;
  return filteredHomeworks.value.filter(h => (h.subject || '英语') === subjectFilter.value);
});
const hwStatusCounts = computed(() => {
  const items = subjectScopedHomeworks.value;
  return {
    pending: items.filter(h => h.status === '未提交').length,
    submitted: items.filter(h => h.status === '已提交').length,
    graded: items.filter(h => h.status === '已批改').length,
    redo: items.filter(h => h.status === '需重做').length,
  };
});
const displayedHomeworks = computed(() => {
  if (!statusFilter.value) return subjectScopedHomeworks.value;
  return subjectScopedHomeworks.value.filter(h => h.status === statusFilter.value);
});
const homeworkAttachmentUrls = ref([]);
const dialogHwDetail = ref(false);
const hwDetail = ref(null);
const hwDetailImages = ref([]);
const hwDetailLoading = ref(false);
// 详情弹窗里的提交列表：看完作业往下滚就是学生提交，提交下面直接是批改表单
const hwDetailSubmits = ref([]);

// --- 答题卡（AI 出卷 + 自动判分）---
/** 大题题型：只影响怎么展示（文章/词库/共用选项），判分仍看小题的 questionType */
const SECTION_TYPES = [
  { value: 'cloze', label: '完形填空' },
  { value: 'reading', label: '阅读理解' },
  { value: 'word_bank', label: '选词填空' },
  { value: 'blank_fill', label: '盲填' },
  { value: 'dialogue', label: '补全对话' },
  { value: 'solution', label: '解答题' },
  { value: 'writing', label: '书面表达' },
  { value: 'standalone', label: '单句题' }
];
/** 出卷学科（暂定九门），AI 按学科切换出卷/评分口径 */
const SHEET_SUBJECTS = ['语文', '数学', '英语', '政治', '历史', '地理', '物理', '化学', '生物'];
const dialogSheet = ref(false);
const sheetStep = ref(0);              // 0=粘贴题目 1=核验 2=发布
const sheetSubject = ref('英语');      // 这张卷子的学科，AI 按学科切换出卷/评分口径
const sheetRawText = ref('');
const sheetImageFile = ref(null);      // 选了题目图片时优先走图片识别，后续流程与文本解析一致
const sheetImagePreview = ref('');     // 图片本地预览地址
const sheetSectionHint = ref('auto');  // 老师预先告知题型，比让 AI 猜准
// 换学科后原题型提示可能不再适用（如英语的“完形填空”），统一清空让老师重填
watch(sheetSubject, () => { sheetSectionHint.value = ''; });
const sheetParsing = ref(false);
const sheetPublishing = ref(false);
const sheetSections = ref([]);
const sheetAppending = ref(false);     // true=本次解析结果追加而不是覆盖
const sheetForm = reactive({ studentId: null, title: '', content: '', deadline: '' });
const sheetStudentName = ref('');
const sheetQuestionCount = computed(() =>
  sheetSections.value.reduce((sum, s) => sum + (s.questions ? s.questions.length : 0), 0));
const sheetFullScore = computed(() =>
  sheetSections.value.reduce((sum, s) =>
    sum + (s.questions || []).reduce((a, q) => a + (Number(q.score) || 0), 0), 0));
// 批改复核
const dialogSheetReview = ref(false);
const sheetReviewLoading = ref(false);
const sheetResult = ref(null);
const sheetFixComment = ref('');
const sheetFixing = ref(false);
// 复核时材料默认收起，要对着文章看再展开
const reviewPassageOpen = reactive({});
/** 复核列表按大题分组，材料从后端返回的 sections 里取 */
const reviewGroups = computed(() => {
  const d = sheetResult.value;
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
const vocabBanksList = ref(['学生词库', '四级词库', '六级词库']);
const vocabBankFilter = ref('');

// --- 资料 ---
const resources = ref([]);
const resourceKeyword = ref('');
const resourcePage = ref(1);
const resourceTotal = ref(0);
const uploading = ref(false);
// 教师间共享资料：viewingTeacher=null 表示看自己的资料
const viewingTeacher = ref(null);
const teacherSearchKeyword = ref('');
const teacherSearchResults = ref([]);
const searchingTeachers = ref(false);
const copyingAll = ref(false);
// 教师账号下拉：teacherSelectId 为选中的教师用户ID，teacherOptions 为下拉候选（全部/搜索结果）
const teacherSelectId = ref(null);
const teacherOptions = ref([]);
// 学科
const subjects = ref([]);
const teacherSubjectId = ref(null);
const selectedSubjectName = computed(() => {
  const s = subjects.value.find(s => s.id === teacherSubjectId.value);
  return s ? s.name : '';
});

// --- 弹窗 ---
const dialogProfile = ref(false);
const dialogHomework = ref(false);
const editingHomeworkId = ref(null);
const dialogVocab = ref(false);
const dialogResource = ref(false);
const dialogAddStudent = ref(false);
const searchName = ref('');
const searchResults = ref([]);
const searching = ref(false);
const bindingId = ref(null);

const profile = reactive({ studentId: null, grade: '', subject: '', textbook: '', level: '', goals: '', weakPoints: '' });
const homeworkForm = reactive({ studentId: null, title: '', content: '', subject: '英语', deadline: '' });
// 逐提交的评语表单 { submitId: { score, comment, voiceUrl, needRedo } }
const reviewForms = reactive({});
const reviewSaving = ref(null);   // 正在提交批改的 submitId
// 语音评语：全局只有一个录音器，voiceTarget 记录当前录音归属哪条提交
const {
  supported: voiceSupported, recording: voiceRecording, seconds: voiceSeconds,
  blob: voiceBlob, previewUrl: voicePreviewUrl, error: voiceError,
  start: voiceStart, stop: voiceStop, reset: voiceReset, formatDuration
} = useVoiceRecorder({ maxSeconds: 120 });
const voiceTarget = ref(null);
const vocabForm = reactive({ word: '', phonetic: '', translation: '', partOfSpeech: '', bank: '学生词库' });
const vocabTags = ref([]);
const vocabTagInput = ref('');
const resourceForm = reactive({ title: '', type: '', subjectId: null, content: '' });
// 资料类型候选：按所选学科动态加载该学科已用过的类型（支持手动输入新类型）
const resourceTypeOptions = ref([]);
const editResourceTypeOptions = ref([]);
const resourceFileUrls = ref([]);
// 预览大图列表只包含图片（Word 文档以文件条展示，不参与图片预览）
const resourceImageUrls = computed(() => resourceFileUrls.value.filter(u => isImageUrl(u)));

// Edit state
const dialogEditVocab = ref(false);
const dialogEditResource = ref(false);
const editVocabForm = reactive({ id: null, word: '', phonetic: '', translation: '', partOfSpeech: '', bank: '学生词库' });
const editVocabTags = ref([]);
const editVocabTagInput = ref('');
const editResourceForm = reactive({ id: null, title: '', type: '', subjectId: null, content: '' });
const editResourceFileUrls = ref([]);
const editResourceImageUrls = computed(() => editResourceFileUrls.value.filter(u => isImageUrl(u)));

// Word detail
const dialogWordDetail = ref(false);
const detailWord = ref(null);

/** 播放英文发音（浏览器 speechSynthesis） */
function speak(text) {
  speakWord(text);
}


// Tag helpers（兼容中文输入法 IME：Enter 键提交 IME 组合时不触发，避免吞掉汉字或插入拼音）
function onVocabTagEnter(e) {
  if (e.isComposing) return;          // IME 组合中不拦截，让输入法正常提交
  e.preventDefault();
  addVocabTag();
}
function onVocabTagBackspace(e) {
  if (e.isComposing) return;
  if (!vocabTagInput.value) vocabTags.value.pop();
}
function addVocabTag() { const v = vocabTagInput.value.trim(); if (v) { vocabTags.value.push(v); vocabTagInput.value = ''; } }

function onEditVocabTagEnter(e) {
  if (e.isComposing) return;
  e.preventDefault();
  addEditVocabTag();
}
function onEditVocabTagBackspace(e) {
  if (e.isComposing) return;
  if (!editVocabTagInput.value) editVocabTags.value.pop();
}
function addEditVocabTag() { const v = editVocabTagInput.value.trim(); if (v) { editVocabTags.value.push(v); editVocabTagInput.value = ''; } }

function statusTag(status) {
  const map = { '未提交': 'info', '已提交': 'warning', '已批改': 'success', '需重做': 'danger' };
  return map[status] || 'info';
}
function statusClass(s) {
  const m = { '未提交': 'pending', '已提交': 'submitted', '已批改': 'graded', '需重做': 'redo' };
  return m[s] || 'pending';
}
// 红笔 = 要老师动手；板绿 = 已完成；其余中性
function statusTone(s) {
  if (s === '已批改') return 'board';
  if (s === '已提交' || s === '需重做') return 'red';
  return 'muted';
}

// --- 学生 ---
async function loadStudents() { loading.value = true; try { studentList.value = await students(); } finally { loading.value = false; } }
function onStudentCmd(cmd, row) {
  if (cmd === 'profile') openProfile(row);
  else if (cmd === 'sheet') openSheetWizard(row);
}
async function openProfile(row) {
  profile.studentId = row.id;
  profile.grade = ''; profile.subject = ''; profile.textbook = '';
  profile.level = ''; profile.goals = ''; profile.weakPoints = '';
  dialogProfile.value = true;
  // 载入该学生已有档案（没有则留空，保存时后端自动新建）
  try {
    const p = await studentProfile(row.id);
    if (p) {
      profile.grade = p.grade || '';
      profile.subject = p.subject || '';
      profile.textbook = p.textbook || '';
      profile.level = p.level || '';
      profile.goals = p.goals || '';
      profile.weakPoints = p.weakPoints || '';
    }
  } catch (e) { /* 无档案，留空即可 */ }
}
async function saveStudentProfile() { await saveProfile({ ...profile }); ElMessage.success('保存成功'); dialogProfile.value = false; }

// --- 作业 ---
async function loadHomeworks() { loading.value = true; try { homeworks.value = await homeworkList(); } finally { loading.value = false; } }
function onStudentChange() {
  // 切换学生时清空之前的结果和筛选
  statusFilter.value = null;
  subjectFilter.value = null;
  if (!selectedStudentId.value) {
    filteredHomeworks.value = [];
    queriedStudent.value = null;
  }
}
async function queryStudentHomework() {
  if (!selectedStudentId.value) { ElMessage.warning('请先选择学生'); return; }
  statusFilter.value = null;
  subjectFilter.value = null;
  loading.value = true;
  try {
    filteredHomeworks.value = await homeworkList(selectedStudentId.value);
    // 从已加载的学生列表中找到该学生信息用于统计
    const s = studentList.value.find(s => s.id === selectedStudentId.value);
    queriedStudent.value = s || { id: selectedStudentId.value, realName: '未知学生', username: '' };
  } finally { loading.value = false; }
}
function openHomework(row) { editingHomeworkId.value = null; homeworkForm.studentId = row.id; homeworkForm.title = ''; homeworkForm.content = ''; homeworkForm.subject = '英语'; homeworkForm.deadline = ''; homeworkAttachmentUrls.value = []; dialogHomework.value = true; }
function openEditHomework(row) {
  editingHomeworkId.value = row.id;
  homeworkForm.studentId = row.studentId;
  homeworkForm.title = row.title || '';
  homeworkForm.content = row.content || '';
  homeworkForm.subject = row.subject || '英语';
  homeworkForm.deadline = row.deadline || '';
  homeworkAttachmentUrls.value = row.attachmentUrl ? row.attachmentUrl.split(',').filter(Boolean) : [];
  dialogHomework.value = true;
}
async function uploadHomeworkAttachment(option) {
  try { const r = await upload(option.file); homeworkAttachmentUrls.value.push(r.fileUrl); option.onSuccess(r); }
  catch (err) { option.onError(err); }
}

// 拍照上传（三个场景共用：布置作业 / 添加资料 / 编辑资料）
async function onCameraCapture(event, target) {
  const file = event.target.files[0];
  if (!file) return;
  try {
    const r = await upload(file); // compressImage 已在 upload() 内部自动调用
    if (target === 'homework') {
      homeworkAttachmentUrls.value.push(r.fileUrl);
    } else if (target === 'resource') {
      resourceFileUrls.value.push(r.fileUrl);
    } else if (target === 'editResource') {
      editResourceFileUrls.value.push(r.fileUrl);
    }
    ElMessage.success('拍照上传成功');
  } catch (err) {
    // 错误已在 request.js 拦截器中通过 ElMessage.error 展示
  } finally {
    event.target.value = ''; // 清空以便下次触发 change
  }
}
async function addStudentHomework() {
  const payload = { ...homeworkForm, attachmentUrl: homeworkAttachmentUrls.value.join(',') };
  if (editingHomeworkId.value) {
    await updateHomework(editingHomeworkId.value, payload);
    ElMessage.success('修改成功');
  } else {
    await addHomework(payload);
    ElMessage.success('布置成功');
  }
  dialogHomework.value = false;
  editingHomeworkId.value = null;
  // 如果当前正在查看该学生的作业，自动刷新
  if (selectedStudentId.value === homeworkForm.studentId) {
    await queryStudentHomework();
  }
}
// --- 语音评语 ---
async function startVoice(submitId) {
  if (voiceRecording.value) { ElMessage.warning('正在录音中，请先停止'); return; }
  voiceReset();
  voiceTarget.value = submitId;
  const ok = await voiceStart();
  if (!ok) {
    voiceTarget.value = null;
    ElMessage.error(voiceError.value || '录音启动失败');
  }
}
function stopVoice() { voiceStop(); }
/** 删除语音：同时清掉本地未提交的录音和已保存的 voiceUrl（提交批改后生效） */
function dropVoice(submitId) {
  if (voiceTarget.value === submitId) {
    voiceReset();
    voiceTarget.value = null;
  }
  if (reviewForms[submitId]) reviewForms[submitId].voiceUrl = '';
}

async function doReview(submitId) {
  const f = reviewForms[submitId];
  if (!f) return;
  if (voiceRecording.value && voiceTarget.value === submitId) { ElMessage.warning('请先停止录音再提交'); return; }
  reviewSaving.value = submitId;
  try {
    if (voiceTarget.value === submitId) {
      // stop() 后 blob 由 MediaRecorder 异步回调填充，这里最多等 1 秒
      for (let i = 0; i < 10 && !voiceBlob.value; i++) await new Promise(r => setTimeout(r, 100));
      if (voiceBlob.value) {
        const r = await uploadVoice(voiceBlob.value);
        f.voiceUrl = r.fileUrl;
        voiceReset();
        voiceTarget.value = null;
      }
    }
    await review({
      submitId,
      score: f.score,
      comment: f.comment,
      voiceUrl: f.voiceUrl || null,
      needRedo: f.needRedo ? 'true' : 'false'
    });
  } finally {
    reviewSaving.value = null;
  }
  ElMessage.success(f.needRedo ? '已打回重做' : '批改完成');
  if (selectedStudentId.value) await queryStudentHomework();
  // 详情弹窗还开着就原地刷新状态与表单，老师可以连续批改不用重开
  if (dialogHwDetail.value && hwDetail.value) await loadHwDetailData(hwDetail.value.id);
}
async function deleteHomeworkHandler(id) {
  await ElMessageBox.confirm('确定删除该作业吗？相关的提交和批改记录也将被删除。', '提示', { type: 'warning' });
  await deleteHomework(id);
  ElMessage.success('已删除');
  if (selectedStudentId.value) await queryStudentHomework();
}
/** 打开详情弹窗（提交与批改已合并进来，录音状态随弹窗重置） */
async function viewHomeworkDetail(id) {
  dialogHwDetail.value = true;
  // 换作业时丢弃上一次未提交的录音，避免误传到别的提交名下
  voiceReset();
  voiceTarget.value = null;
  await loadHwDetailData(id);
}

/** 拉取作业详情 + 全部提交 + 各提交已有批改（批改后也调它原地刷新） */
async function loadHwDetailData(id) {
  hwDetail.value = null;
  hwDetailImages.value = [];
  hwDetailSubmits.value = [];
  hwDetailLoading.value = true;
  try {
    const d = await getHomework(id);
    hwDetail.value = d;
    if (d.attachmentUrl) {
      hwDetailImages.value = d.attachmentUrl.split(',').filter(Boolean);
    }
    if (d.status !== '未提交') {
      const subs = await submits(id).catch(() => []);
      hwDetailSubmits.value = subs;
      // 每条提交一套独立表单，已批改过的预填分数/评语/语音，老师可以改完重提
      for (const sub of subs) {
        const existing = await getSubmitReview(sub.id).catch(() => null);
        reviewForms[sub.id] = reactive({
          score: existing ? existing.score : 80,
          comment: existing ? existing.comment : '',
          voiceUrl: existing ? (existing.voiceUrl || '') : '',
          needRedo: existing ? (existing.needRedo === 'true') : false
        });
      }
    }
  } finally {
    hwDetailLoading.value = false;
  }
}

// --- 答题卡 ---
function typeLabel(t) {
  const m = { single: '单选', multi: '多选', judge: '判断', blank: '填空', short: '简答' };
  return m[t] || t || '题目';
}

function sectionLabel(t) {
  const s = SECTION_TYPES.find(x => x.value === t);
  return s ? s.label : '题目';
}

/** 小题在整张卡里的连续题号，跟文章里的 _N_ 对得上 */
function globalSeq(si, qi) {
  let n = 0;
  for (let i = 0; i < si; i++) n += (sheetSections.value[i].questions || []).length;
  return n + qi + 1;
}

/** 词库在界面上是一行文本，落库前再拆回数组 */
function splitWordBank(text) {
  if (!text) return null;
  const arr = String(text).split(/[\/／,，、\n]/).map(s => s.trim()).filter(Boolean);
  return arr.length ? arr : null;
}

/** AI 返回的大题补齐前端编辑需要的字段，免得模板里到处判空 */
function normalizeSections(list) {
  return list.map(s => ({
    ...s,
    sectionType: s.sectionType || 'standalone',
    title: s.title || sectionLabel(s.sectionType),
    passage: s.passage || '',
    instruction: s.instruction || '',
    wordBankText: (s.wordBank || []).join(' / '),
    sharedOptions: s.sharedOptions || [],
    questions: (s.questions || []).map(q => ({ ...q, options: q.options || [] }))
  }));
}

function onSectionTypeChange(sec) {
  // 改成单句题/解答题就没有共享材料了，清掉避免误传
  if (['standalone', 'solution'].includes(sec.sectionType)) sec.passage = '';
}

function addSharedOption(sec) {
  if (!sec.sharedOptions) sec.sharedOptions = [];
  sec.sharedOptions.push({ key: String.fromCharCode(65 + sec.sharedOptions.length), text: '' });
}

/** 按满分 100 自动均分所有小题分值：整除不尽时排前面的题多 1 分，总和恰为 100 */
function redistributeSheetScores() {
  const all = [];
  sheetSections.value.forEach(s => (s.questions || []).forEach(q => all.push(q)));
  const n = all.length;
  if (!n) return;
  const base = Math.floor(100 / n);
  const rem = 100 % n;
  all.forEach((q, i) => { q.score = base + (i < rem ? 1 : 0); });
}

function addQuestion(sec) {
  sec.questions.push({
    // 作文/解答题要成段作答或写过程，默认简答；其他大题默认填空
    questionType: ['writing', 'solution'].includes(sec.sectionType) ? 'short' : 'blank',
    content: '',
    options: [],
    standardAnswer: '',
    analysis: '',
    // 临时占位分，加完题后可点「重新均分」恢复满分 100
    score: 5,
    knowledgePoint: ''
  });
}

/** 回到第一步再粘一段，一张卷子通常有好几个大题，分次解析比一次喂整卷准 */
function startAppendSection() {
  sheetAppending.value = true;
  sheetRawText.value = '';
  sheetSectionHint.value = '';
  clearSheetImage();
  sheetStep.value = 0;
}

function openSheetWizard(row) {
  sheetStep.value = 0;
  sheetRawText.value = '';
  sheetSections.value = [];
  sheetSubject.value = '英语';
  sheetSectionHint.value = '';
  sheetAppending.value = false;
  clearSheetImage();
  sheetForm.studentId = row.id;
  sheetForm.title = '';
  sheetForm.content = '';
  sheetForm.deadline = '';
  sheetStudentName.value = row.realName || row.username || '该学生';
  dialogSheet.value = true;
}

async function doParseQuestions() {
  if (!sheetImageFile.value && !sheetRawText.value.trim()) { ElMessage.warning('请先粘贴题目或上传题目图片'); return; }
  sheetParsing.value = true;
  try {
    // 选了图片就走图片识别：后端先把图片转成文字，再进入与文本解析完全相同的出卷流程；否则解析粘贴的文本
    const list = sheetImageFile.value
      ? await aiParseImageQuestions(await buildSheetImageForm())
      : await aiParseQuestions({
          rawText: sheetRawText.value,
          // 分值由前端解析完后统一均分（满分 100），AI 侧的 defaultScore 仅作占位兼容
          defaultScore: 5,
          // 自由文本题型提示：填了就交给 AI 当强提示，留空等于自动识别
          sectionHint: sheetSectionHint.value.trim(),
          subject: sheetSubject.value.trim()
        });
    if (!list || !list.length) { ElMessage.warning('AI 没能识别出题目，换个格式或换张更清晰的图片再试试'); return; }
    const parsed = normalizeSections(list);
    sheetSections.value = sheetAppending.value ? sheetSections.value.concat(parsed) : parsed;
    sheetAppending.value = false;
    // 追加模式下对整卷重新均分，保证总分恒为 100
    redistributeSheetScores();
    sheetStep.value = 1;
    clearSheetImage();  // 解析成功就释放图片，「再粘一段」回到文本方式也干净
    const qs = parsed.reduce((sum, s) => sum + s.questions.length, 0);
    ElMessage.success(`解析出 ${parsed.length} 个大题、${qs} 小题，分值已按满分 100 均分，请核验材料与答案`);
  } finally {
    sheetParsing.value = false;
  }
}

/** 题目图片先客户端压缩（手机拍照动辄几 MB），再连同题型提示组 FormData */
async function buildSheetImageForm() {
  const compressed = await compressImage(sheetImageFile.value);
  const formData = new FormData();
  formData.append('file', compressed);
  formData.append('defaultScore', '5');
  formData.append('sectionHint', sheetSectionHint.value.trim());
  formData.append('subject', sheetSubject.value.trim());
  return formData;
}

/** 选择题目图片：先本地预览，点解析时再上传；换图时释放旧预览地址 */
function onSheetImageChange(uploadFile) {
  const file = uploadFile?.raw;
  if (!file || !file.type.startsWith('image/')) { ElMessage.warning('请选择图片文件（jpg/png 等）'); return; }
  clearSheetImage();
  sheetImageFile.value = file;
  sheetImagePreview.value = URL.createObjectURL(file);
}

function clearSheetImage() {
  if (sheetImagePreview.value) URL.revokeObjectURL(sheetImagePreview.value);
  sheetImageFile.value = null;
  sheetImagePreview.value = '';
}

async function doPublishSheet() {
  if (!sheetForm.title.trim()) { ElMessage.warning('请填写答题卡标题'); return; }
  for (const sec of sheetSections.value) {
    if (sec.questions.some(q => !q.content || !String(q.content).trim())) {
      ElMessage.warning('有题目的题干为空，请返回核验'); return;
    }
    // 完形/阅读这类题丢了文章学生根本无法作答，卡在发布前提醒（解答题/单句题没有共享材料）
    if (!['standalone', 'solution'].includes(sec.sectionType) && !String(sec.passage || '').trim()) {
      ElMessage.warning(`【${sec.title || sectionLabel(sec.sectionType)}】缺材料原文，学生会看不到文章`); return;
    }
  }
  sheetPublishing.value = true;
  try {
    let seq = 1;
    await assignAnswerSheet({
      studentId: sheetForm.studentId,
      title: sheetForm.title,
      content: sheetForm.content,
      subject: sheetSubject.value.trim() || null,
      deadline: sheetForm.deadline || null,
      sections: sheetSections.value.map((sec, si) => ({
        seq: si + 1,
        sectionType: sec.sectionType,
        title: sec.title,
        instruction: sec.instruction || null,
        passage: ['standalone', 'solution'].includes(sec.sectionType) ? null : (sec.passage || null),

        wordBank: sec.sectionType === 'word_bank' ? splitWordBank(sec.wordBankText) : null,
        sharedOptions: (sec.sharedOptions && sec.sharedOptions.length) ? sec.sharedOptions : null,
        questions: sec.questions.map(q => ({
          seq: seq++,
          questionType: q.questionType,
          content: q.content,
          // 空选项数组传 null，避免数据库里存下无意义的 []
          options: (q.options && q.options.length) ? q.options : null,
          standardAnswer: q.standardAnswer,
          analysis: q.analysis,
          score: Number(q.score) || 5,
          knowledgePoint: q.knowledgePoint
        }))
      }))
    });
    ElMessage.success('答题卡已发布，学生端可以作答了');
    dialogSheet.value = false;
    if (selectedStudentId.value === sheetForm.studentId) await queryStudentHomework();
  } finally {
    sheetPublishing.value = false;
  }
}

async function openSheetReview(item) {
  dialogSheetReview.value = true;
  sheetResult.value = null;
  sheetFixComment.value = '';
  Object.keys(reviewPassageOpen).forEach(k => delete reviewPassageOpen[k]);
  sheetReviewLoading.value = true;
  try {
    const subs = await submits(item.id).catch(() => []);
    if (!subs.length) return;
    const r = await answerSheetResult(subs[0].id);
    // correctBool 给开关用；correct 可能是 null（AI 未判），此时默认判错待老师确认
    (r.items || []).forEach(it => { it.correctBool = it.correct === 1; });
    sheetResult.value = r;
    sheetFixComment.value = r.review ? (r.review.comment || '') : '';
  } finally {
    sheetReviewLoading.value = false;
  }
}

async function doFixSheet() {
  if (!sheetResult.value) return;
  sheetFixing.value = true;
  try {
    await fixAnswerSheet({
      submitId: sheetResult.value.submitId,
      comment: sheetFixComment.value,
      items: sheetResult.value.items.map(it => ({
        questionId: it.questionId,
        correct: it.correctBool ? 1 : 0,
        gotScore: Number(it.gotScore) || 0
      }))
    });
    ElMessage.success('复核已保存，分数已重新汇总');
    dialogSheetReview.value = false;
    if (selectedStudentId.value) await queryStudentHomework();
  } finally {
    sheetFixing.value = false;
  }
}

// --- 词汇 ---
async function loadVocabBanks() {
  // 词库列表很少变化：命中缓存直接复用，不再请求
  if (Array.isArray(vocabStore.banks) && vocabStore.banks.length) {
    vocabBanksList.value = vocabStore.banks;
    return;
  }
  try {
    const list = await vocabBanks();
    if (Array.isArray(list) && list.length) {
      vocabBanksList.value = list;
      vocabStore.setBanks(list);
    }
  } catch (e) { /* 词库接口失败时沿用默认三个词库 */ }
}
/**
 * 加载词汇列表。
 * force=false（默认）：先看缓存，条件一致就直接复用，避免"每次返回都重新加载"；
 * force=true：忽略缓存强制重拉（刷新按钮 / 增删改后 / 搜索）。
 */
async function loadVocabularies(force = false) {
  const key = `${vocabPage.value}|${vocabKeyword.value || ''}|${vocabBankFilter.value || ''}`;
  if (!force && vocabStore.cacheKey === key && vocabStore.listData) {
    vocabularies.value = vocabStore.listData.records || [];
    vocabTotal.value = vocabStore.listData.total || 0;
    return;
  }
  loading.value = true;
  try {
    const params = { page: vocabPage.value, size: 6 };
    // 选择词库时按词库过滤；不选则展示全部词库
    if (vocabBankFilter.value) params.bank = vocabBankFilter.value;
    // 有关键词时走搜索接口（ES 优先，不可用时自动降级 MySQL LIKE），避免搜不到已存在的单词
    const r = vocabKeyword.value
      ? await vocabSearch({ ...params, keyword: vocabKeyword.value })
      : await vocabList(params);
    const data = { records: r.records || [], total: r.total || 0 };
    vocabularies.value = data.records;
    vocabTotal.value = data.total;
    vocabStore.setList(key, data);
  } finally { loading.value = false; }
}
/** 分页切换：条件变化会自然绕过缓存重新拉取 */
function onVocabPageChange() {
  loadVocabularies();
}
async function searchVocabularies() {
  vocabPage.value = 1;
  await loadVocabularies(true);
}
/** 查看单词详情（教师端词汇表单词名点击） */
function showWordDetail(word) {
  detailWord.value = word;
  dialogWordDetail.value = true;
}
/** 获取完整的延伸词列表：已添加的 tag + 输入框中尚未按回车的文本 */
function getVocabExtensions(tags, pendingInput) {
  const all = [...tags];
  const pending = (pendingInput || '').trim();
  if (pending) all.push(pending);  // 用户输入了但没按回车，也一并保存
  return all.join(',');
}

async function addVocabulary() {
  await addVocab({ ...vocabForm, extensions: getVocabExtensions(vocabTags.value, vocabTagInput.value) });
  ElMessage.success('添加成功');
  dialogVocab.value = false;
  // 清空表单，方便下一次添加
  vocabForm.word = '';
  vocabForm.phonetic = '';
  vocabForm.translation = '';
  vocabForm.partOfSpeech = '';
  vocabForm.bank = '学生词库';
  vocabTags.value = [];
  vocabTagInput.value = '';
  vocabStore.invalidate();
  await loadVocabularies(true);
}
async function deleteVocab(id) {
  await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' });
  await deleteVocabApi(id);
  ElMessage.success('已删除');
  vocabStore.invalidate();
  await loadVocabularies(true);
}

// --- 资料 ---
async function loadSubjects() {
  try {
    subjects.value = await subjectList() || [];
  } catch (e) { /* handled */ }
}
// 类型与学科绑定：只取该学科下已用过的类型作为下拉建议
async function loadResourceTypes() {
  try {
    const list = await resourceTypes(resourceForm.subjectId);
    resourceTypeOptions.value = Array.isArray(list) ? list : [];
  } catch (e) { /* 忽略 */ }
}
async function loadEditResourceTypes() {
  try {
    const list = await resourceTypes(editResourceForm.subjectId);
    editResourceTypeOptions.value = Array.isArray(list) ? list : [];
  } catch (e) { /* 忽略 */ }
}
async function promptAddSubject() {
  try {
    const { value } = await ElMessageBox.prompt('请输入学科名称（如：数学、物理）', '添加学科', {
      confirmButtonText: '添加',
      cancelButtonText: '取消',
      inputValidator: (v) => (v && v.trim()) ? true : '学科名称不能为空'
    });
    await addSubjectApi(value.trim());
    ElMessage.success(`学科「${value.trim()}」已创建`);
    await loadSubjects();
    // 自动选中新学科，切换到新的资料库
    const s = subjects.value.find(s => s.name === value.trim());
    if (s) teacherSubjectId.value = s.id;
    await loadResources();
  } catch (e) { /* 取消或错误，拦截器已提示 */ }
}
async function confirmDeleteSubject() {
  const name = selectedSubjectName.value;
  try {
    await ElMessageBox.confirm(`删除学科「${name}」后，该学科下的学习资料将自动归入默认"英语"学科，确定删除？`,
      '删除学科', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' });
    await deleteSubjectApi(teacherSubjectId.value);
    ElMessage.success(`学科「${name}」已删除，资料已归入英语`);
    teacherSubjectId.value = null;
    await loadSubjects();
    await loadResources();
  } catch (e) { /* 取消或错误 */ }
}
async function loadResources() {
  // 从资料详情返回时命中缓存，直接复用列表与筛选/分页状态，不再重新加载
  const key = `${resourcePage.value}|${resourceKeyword.value || ''}|${teacherSubjectId.value || ''}|${viewingTeacher.value ? viewingTeacher.value.id : ''}`;
  if (resourceStore.teacher && resourceStore.teacher.key === key) {
    resources.value = resourceStore.teacher.records;
    resourceTotal.value = resourceStore.teacher.total;
    return;
  }
  loading.value = true;
  try {
    const params = { page: resourcePage.value, size: 6 };
    if (resourceKeyword.value) params.keyword = resourceKeyword.value;
    if (teacherSubjectId.value) params.subjectId = teacherSubjectId.value;
    // 教师间共享：带 teacherId 则查看该教师发布的资料
    if (viewingTeacher.value) params.teacherId = viewingTeacher.value.id;
    const r = await resourceList(params);
    resources.value = r.records || [];
    resourceTotal.value = r.total || 0;
    resourceStore.setTeacher({ key, records: resources.value, total: resourceTotal.value });
  } finally { loading.value = false; }
}

// --- 教师间共享资料 ---
/** 下拉展开时若还没候选项，拉取全部教师账号（后端排除自己、LIMIT 50） */
async function onTeacherDropdown(visible) {
  if (visible && !teacherOptions.value.length) await remoteSearchTeachers('');
}
/** 远程搜索：按账号/姓名过滤；空关键字=列出全部教师账号 */
async function remoteSearchTeachers(query) {
  searchingTeachers.value = true;
  try {
    teacherOptions.value = await searchTeachers((query || '').trim());
  } catch (e) { teacherOptions.value = []; }
  finally { searchingTeachers.value = false; }
}
/** 选中某教师 直接进入其共享资料视图 */
function onSelectTeacher(teacherId) {
  if (!teacherId) return;
  const t = teacherOptions.value.find(x => x.id === teacherId);
  if (t) viewTeacherResources(t);
}
function viewTeacherResources(teacher) {
  viewingTeacher.value = { id: teacher.id, name: teacher.realName || teacher.username };
  teacherSelectId.value = null;
  teacherOptions.value = [];
  resourcePage.value = 1;
  loadResources();
}
function exitSharedView() {
  viewingTeacher.value = null;
  resourcePage.value = 1;
  resourceStore.invalidateTeacher();
  loadResources();
}
async function copyResourceToMine(row) {
  await ElMessageBox.confirm(`确定将「${row.title}」复制到自己的资料库？`, '复制资料', { type: 'warning' });
  await copyResource(row.id);
  ElMessage.success('已复制到自己的资料库');
  // 切回自己的资料，方便立即看到/编辑复制后的资料
  exitSharedView();
}
async function copyAllFromTeacher() {
  await ElMessageBox.confirm(`确定将该教师的全部学习资料复制到自己的资料库？（已复制过的会自动跳过）`, '一键复制全部', { type: 'warning' });
  copyingAll.value = true;
  try {
    const n = await copyAllResources(viewingTeacher.value.id);
    ElMessage.success(`已复制 ${n} 条资料到自己的资料库`);
    exitSharedView();
  } finally { copyingAll.value = false; }
}
async function uploadResourceFile(option) {
  try { const r = await upload(option.file); resourceFileUrls.value.push(r.fileUrl); option.onSuccess(r); }
  catch (err) { option.onError(err); }
}
function openAddResource() {
  // 打开添加弹窗时，学科默认跟随当前筛选的学科（未筛选时为空 后端默认英语）
  resourceForm.subjectId = teacherSubjectId.value;
  resourceForm.type = '';
  loadResourceTypes();
  dialogResource.value = true;
}
/** 添加资料弹窗：切换学科后，类型候选随之切换到该学科 */
function onAddResourceSubjectChange() {
  resourceForm.type = '';
  loadResourceTypes();
}
async function addResource() {
  if (!resourceForm.title) { ElMessage.warning('请输入标题'); return; }
  uploading.value = true;
  try {
    const fd = new FormData();
    fd.append('title', resourceForm.title);
    fd.append('type', resourceForm.type);
    fd.append('content', resourceForm.content || '');
    if (resourceForm.subjectId) fd.append('subjectId', resourceForm.subjectId);
    if (resourceFileUrls.value.length) fd.append('fileUrl', resourceFileUrls.value.join(','));
    await addResourceApi(fd);
    ElMessage.success('添加成功');
    dialogResource.value = false;
    // 清空表单，方便下一次添加
    resourceForm.title = '';
    resourceForm.type = '';
    resourceForm.content = '';
    resourceForm.subjectId = teacherSubjectId.value;
    resourceFileUrls.value = [];
    resourceStore.invalidateTeacher();
    await loadResources();
  } finally { uploading.value = false; }
}
async function deleteResource(id) {
  await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' });
  await deleteResourceApi(id);
  ElMessage.success('已删除');
  resourceStore.invalidateTeacher();
  await loadResources();
}

// --- 编辑词汇 ---
function openEditVocab(row) {
  editVocabForm.id = row.id;
  editVocabForm.word = row.word;
  editVocabForm.phonetic = row.phonetic || '';
  editVocabForm.translation = row.translation;
  editVocabForm.partOfSpeech = row.partOfSpeech || '';
  editVocabForm.bank = row.bank || '学生词库';
  editVocabTags.value = row.extensions ? row.extensions.split(',').filter(Boolean) : [];
  editVocabTagInput.value = '';
  dialogEditVocab.value = true;
}
async function doEditVocab() {
  await updateVocab(editVocabForm.id, { ...editVocabForm, extensions: getVocabExtensions(editVocabTags.value, editVocabTagInput.value) });
  ElMessage.success('修改成功');
  dialogEditVocab.value = false;
  editVocabTags.value = [];
  editVocabTagInput.value = '';
  vocabStore.invalidate();
  await loadVocabularies(true);
}

// --- 编辑资料 ---
function openEditResource(row) {
  editResourceForm.id = row.id;
  editResourceForm.title = row.title;
  editResourceForm.type = row.type;
  editResourceForm.subjectId = row.subjectId || null;
  editResourceForm.content = row.content || '';
  editResourceFileUrls.value = row.fileUrl ? row.fileUrl.split(',').filter(Boolean) : [];
  loadEditResourceTypes();
  dialogEditResource.value = true;
}
/** 编辑资料弹窗：切换学科后，类型候选随之切换到该学科 */
function onEditResourceSubjectChange() {
  editResourceForm.type = '';
  loadEditResourceTypes();
}
async function uploadEditResourceFile(option) {
  try { const r = await upload(option.file); editResourceFileUrls.value.push(r.fileUrl); option.onSuccess(r); }
  catch (err) { option.onError(err); }
}
async function doEditResource() {
  uploading.value = true;
  try {
    const fd = new FormData();
    fd.append('title', editResourceForm.title);
    fd.append('type', editResourceForm.type);
    fd.append('content', editResourceForm.content || '');
    if (editResourceForm.subjectId) fd.append('subjectId', editResourceForm.subjectId);
    if (editResourceFileUrls.value.length) fd.append('fileUrl', editResourceFileUrls.value.join(','));
    await updateResource(editResourceForm.id, fd);
    ElMessage.success('修改成功');
    dialogEditResource.value = false;
    editResourceFileUrls.value = [];
    resourceStore.invalidateTeacher();
    await loadResources();
  } finally { uploading.value = false; }
}

// --- 添加学生（搜索+绑定）---
async function openAddStudent() {
  searchName.value = '';
  dialogAddStudent.value = true;
  await loadAvailableStudents();
}
async function loadAvailableStudents() {
  searching.value = true;
  try {
    const name = searchName.value?.trim() || undefined;
    searchResults.value = await searchStudents(name);
  } catch (e) { /* handled */ }
  finally { searching.value = false; }
}
async function doBind(studentId) {
  bindingId.value = studentId;
  try {
    await bindStudent(studentId);
    ElMessage.success('绑定成功');
    await loadStudents();
    await loadAvailableStudents();
  } catch (e) { /* handled */ }
  finally { bindingId.value = null; }
}

// ========== AI 智能扩展词汇 ==========
const dialogAIVocab = ref(false);
const aiProcessing = ref(false);
const aiSaving = ref(false);
const aiWordInput = ref('');
const aiResult = ref(null);

function openAIVocabDialog() {
  aiResult.value = null;
  aiWordInput.value = '';
  dialogAIVocab.value = true;
}

async function onAIExpand() {
  const word = aiWordInput.value.trim();
  if (!word) { ElMessage.warning('请输入一个英文单词'); return; }
  aiProcessing.value = true;
  try {
    const result = await aiExpandVocabulary(word);
    aiResult.value = result;
    ElMessage.success(`AI分析完成！主词: ${result.word}，建议 ${result.extensions.length} 个延伸词`);
  } catch (err) {
    ElMessage.error('AI分析失败：请确认DeepSeek API Key已配置，且网络正常');
  } finally {
    aiProcessing.value = false;
  }
}

function addCustomExtension() {
  if (!aiResult.value) return;
  aiResult.value.extensions.push({ word: '', translation: '', selected: true });
}

async function saveAIResult() {
  const r = aiResult.value;
  if (!r || !r.word) return;
  // 只保留勾选的延伸词，以「单词:释义」格式拼接为逗号分隔字符串（无释义时仅存单词）
  const selectedExts = r.extensions.filter(e => e.selected && e.word.trim());
  const extensionsStr = selectedExts
    .map(e => e.translation.trim() ? `${e.word.trim()}:${e.translation.trim()}` : e.word.trim())
    .join(',');
  aiSaving.value = true;
  try {
    await addVocab({
      word: r.word.trim(),
      translation: r.translation.trim(),
      partOfSpeech: r.partOfSpeech.trim(),
      extensions: extensionsStr
    });
    ElMessage.success(`"${r.word}" 已添加到词库，含 ${selectedExts.length} 个延伸词`);
    dialogAIVocab.value = false;
    aiResult.value = null;
    aiWordInput.value = '';
    vocabStore.invalidate();
    await loadVocabularies(true);
  } catch (err) {
    ElMessage.error('保存失败，该单词可能已存在');
  } finally {
    aiSaving.value = false;
  }
}

function loadByTab() {
  if (currentTab.value === 'students') loadStudents();
  if (currentTab.value === 'homework') { loadStudents(); loadSubjects(); } // 学生下拉 + 学科下拉
  if (currentTab.value === 'vocabulary') { loadVocabBanks(); loadVocabularies(); }
  if (currentTab.value === 'resources') { loadSubjects(); loadResources(); }
}
onMounted(() => {
  loadByTab();
  handleAIAction(route.query);
});
watch(currentTab, loadByTab);

// ========== 响应 AI 助手操作指令 ==========
watch(() => route.query, (query) => {
  if (query.aiStudent || query.aiDialog || query.aiSearch || query.aiOpenProfile) {
    handleAIAction(query);
  }
});

async function handleAIAction(query) {
  if (!query) return;

  // AI 要求自动选择学生（作业管理 / 学生管理）
  if (query.aiStudent) {
    const name = query.aiStudent;
    // 确保学生列表已加载
    if (!studentList.value.length) await loadStudents();

    const student = studentList.value.find(s =>
      s.realName && (s.realName === name || s.realName.includes(name))
    );
    if (student) {
      if (query.aiOpenProfile === 'true') {
        // 打开学生档案
        openProfile(student);
      } else {
        // 默认：选择该学生并查询作业
        selectedStudentId.value = student.id;
        await queryStudentHomework();
      }
    } else {
      ElMessage.warning(`未找到学生："${name}"，请检查姓名是否正确`);
    }
  }

  // AI 要求打开对话框
  if (query.aiDialog) {
    // 读取 AI 生成的预填内容
    let prefill = null;
    try { prefill = JSON.parse(sessionStorage.getItem('aiPrefill') || 'null'); } catch(e) {}
    sessionStorage.removeItem('aiPrefill');

    switch (query.aiDialog) {
      case 'aiExpand':
        openAIVocabDialog();
        if (query.aiPrefill) {
          aiWordInput.value = query.aiPrefill;
          await nextTick();
          await onAIExpand();
        }
        break;
      case 'vocabulary':
        dialogVocab.value = true;
        if (query.aiPrefill) vocabForm.word = query.aiPrefill;
        break;
      case 'addStudent':
        dialogAddStudent.value = true;
        await loadAvailableStudents();
        break;
      case 'homework':
        if (query.aiStudent && studentList.value.length) {
          const s = studentList.value.find(st =>
            st.realName && (st.realName === query.aiStudent || st.realName.includes(query.aiStudent))
          );
          if (s) {
            openHomework(s);
            // 预填 AI 生成的作业内容
            if (prefill) {
              homeworkForm.title = prefill.title || '';
              homeworkForm.content = prefill.content || '';
            }
          }
        }
        break;
      case 'resource':
        dialogResource.value = true;
        // 预填 AI 生成的资料内容
        if (prefill) {
          resourceForm.title = prefill.title || '';
          resourceForm.type = prefill.type || '';
          resourceForm.content = prefill.content || '';
        }
        break;
    }
  }

  // AI 要求搜索
  if (query.aiSearch) {
    vocabKeyword.value = query.aiSearch;
    await searchVocabularies();
  }

  // 清除 query 参数，防止重复执行
  const cleanQuery = { ...route.query };
  delete cleanQuery.aiStudent;
  delete cleanQuery.aiDialog;
  delete cleanQuery.aiPrefill;
  delete cleanQuery.aiSearch;
  delete cleanQuery.aiOpenProfile;
  router.replace({ query: cleanQuery });
}

</script>

<style scoped>
/* 标题行 */
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
  margin-bottom: 18px;
}
.page-head h1 {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: 600;
  color: var(--text);
}
.page-sub {
  margin: 0;
  font-size: 13px;
  color: var(--muted);
}
.page-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.vocab-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.cell-strong { font-weight: 500; color: var(--text); }
.cell-muted { color: var(--muted); font-size: var(--fs-sm); }
.row-more-btn { color: var(--muted); }
.row-more-btn:hover { color: var(--accent); }
.row-more-icon { margin-left: 2px; font-size: 12px; }
.scope-line {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 0 0 10px;
  font-size: 14px;
  color: var(--text);
}
.scope-user { font-size: 13px; color: var(--muted); }
.scope-hint { font-size: 13px; color: var(--muted); }

/* 表格横向滚动容器 */
.table-wrapper {
  width: 100%;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

/* ========== 提交记录卡片 ========== */
.submit-card {
  margin-bottom: 20px;
  padding: 16px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--surface);
}
.submit-answer {
  margin-bottom: 12px;
  white-space: pre-wrap;
  font-size: 14px;
  line-height: 1.7;
}
.submit-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.submit-image-item {
  width: 120px;
  height: 120px;
  border-radius: 6px;
  border: 1px solid var(--border);
  cursor: pointer;
}

/* ========== 评语表单 ========== */
.review-form {
  margin-top: 12px;
  padding: 12px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 6px;
}
.review-form-row {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}
.review-score {
  width: 80px;
  flex-shrink: 0;
}
.review-comment {
  flex: 1;
  min-width: 0;
}
.review-form-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  gap: 12px;
}
.review-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
}
.review-toggle-label {
  font-size: 13px;
  color: var(--color-text-regular);
}
.review-voice {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--border);
}
.review-voice-label {
  font-size: 13px;
  color: var(--color-text-regular);
}
.review-voice-audio {
  height: 32px;
  max-width: 220px;
}
.review-voice-tip {
  font-size: 12px;
  color: var(--muted);
}

/* ═══════════════ 作业筛选栏 ═══════════════ */
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
  transition: border-color 180ms ease, background 180ms ease, color 180ms ease;
  user-select: none;
}
.hw-stat-item:hover { border-color: #c7cdd6; }
.hw-stat-item strong {
  font-size: 16px;
  margin-left: 2px;
  color: var(--text);
  font-variant-numeric: tabular-nums;
}
.hw-stat-pending.hw-stat-active {
  border-color: var(--text);
  background: var(--raised);
  color: var(--text);
}
/* 红笔：待批改 / 需重做 */
.hw-stat-todo { color: var(--red); }
.hw-stat-todo strong { color: var(--red); }
.hw-stat-todo.hw-stat-active { border-color: var(--red); background: var(--red-soft); color: var(--red); }
/* 板绿：已批改 */
.hw-stat-done { color: var(--board); }
.hw-stat-done strong { color: var(--board); }
.hw-stat-done.hw-stat-active { border-color: var(--board); background: var(--board-soft); color: var(--board); }
/* 花名册序号 */
.roster-idx { color: var(--muted); font-variant-numeric: tabular-nums; }
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

/* ═══════════════ 骨架屏 ═══════════════ */
.skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.skeleton-card {
  padding: 20px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
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

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* ═══════════════ 作业卡片 ═══════════════ */
.hw-card-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.hw-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  border-left: 3px solid var(--border);
  cursor: pointer;
  transition: border-color 180ms ease;
  overflow: hidden;
}
.hw-card:hover { border-color: #c7cdd6; border-left-color: var(--muted); }
.hw-card--pending { border-left-color: var(--border); }
.hw-card--submitted { border-left-color: var(--red); }
.hw-card--graded { border-left-color: var(--board); }
.hw-card--redo { border-left-color: var(--red); }

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
  color: var(--text);
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
/* 红笔只上"要老师动手"的状态：待批改 / 需重做；已批改用板绿 */
.hw-status--pending { background: var(--raised); color: var(--muted); }
.hw-status--submitted { background: var(--red-soft); color: var(--red); }
.hw-status--graded { background: var(--board-soft); color: var(--board); }
.hw-status--redo { background: var(--red-soft); color: var(--red); }

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
  color: var(--muted);
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
  background: var(--muted);
}
.hw-meta-deadline { display: flex; align-items: center; gap: 4px; }

.hw-card-actions {
  display: flex;
  gap: 8px;
  padding: 8px 20px 14px;
  border-top: 1px solid var(--rule);
  background: var(--raised);
}

/* ═══════════════ 答题卡 ═══════════════ */
/* --- 出卷向导：大题卡片 --- */
.sheet-append-tip {
  padding: 10px 12px;
  margin-bottom: 10px;
  border-radius: 8px;
  background: var(--accent-soft);
  font-size: 13px;
  color: var(--accent);
}
.sheet-sec-card {
  padding: 12px 14px;
  margin-bottom: 16px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--surface);
}
.sheet-sec-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.sheet-sec-seq {
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
.sheet-sec-block {
  margin-top: 10px;
}
.sheet-sec-label {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-regular);
}
.sheet-sec-tip {
  font-weight: 400;
  font-size: 12px;
  color: var(--muted);
}
.sheet-sec-passage-text {
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--raised);
  font-size: 14px;
  line-height: 1.8;
  color: var(--text);
  white-space: pre-wrap;
}

/* --- 出卷向导：逐题核验卡片 --- */
.sheet-q-card {
  padding: 12px 14px;
  margin-top: 10px;
  margin-bottom: 0;
  border: 1px solid var(--border);
  border-left: 3px solid var(--accent);
  border-radius: 10px;
  background: var(--raised);
}
.sheet-q-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.sheet-q-seq {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}
.sheet-q-options {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 8px;
  padding-left: 4px;
}
.sheet-q-option {
  display: flex;
  align-items: center;
  gap: 8px;
}
.sheet-q-okey {
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 12px;
  font-weight: 600;
}
.sheet-q-answer {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}
.sheet-q-label {
  flex-shrink: 0;
  font-size: 13px;
  color: var(--color-text-regular);
}
.sheet-summary {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: 10px;
  background: #e7f2ec;
  font-size: 13px;
  color: #529b2e;
  line-height: 1.7;
}

/* --- 批改复核 --- */
.sheet-score-bar {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
  padding: 12px 16px;
  margin-bottom: 14px;
  border-radius: 10px;
  background: #e7f2ec;
  font-size: 14px;
  color: var(--color-text-regular);
}
.sheet-score-bar strong {
  font-size: 18px;
  color: var(--text);
}
.sheet-r-card {
  padding: 12px 14px;
  margin-bottom: 12px;
  border: 1px solid var(--border);
  border-left: 3px solid var(--border);
  border-radius: 10px;
  background: var(--surface);
}
.sheet-r-right { border-left-color: var(--positive); background: #e7f2ec; }
.sheet-r-wrong { border-left-color: var(--negative); background: #fffafa; }
.sheet-r-pending { border-left-color: var(--warning); background: #fffcf5; }
.sheet-r-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
}
.sheet-r-flag {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.sheet-r-content {
  font-size: 14px;
  color: var(--text);
  line-height: 1.6;
  margin-bottom: 8px;
  white-space: pre-wrap;
}
.sheet-r-row {
  display: flex;
  gap: 8px;
  font-size: 13px;
  color: var(--color-text-regular);
  line-height: 1.7;
}
.sheet-r-label {
  flex-shrink: 0;
  width: 62px;
  color: var(--muted);
}
.sheet-r-fix {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--border);
  flex-wrap: wrap;
}

/* ═══════════════ 空状态插图 ═══════════════ */
.empty-illustration {
  font-size: 64px;
  opacity: 0.6;
}

/* ========== 移动端适配 ========== */
@media (max-width: 768px) {
  /* 弹窗全宽 */
  :deep(.el-dialog) {
    width: 92% !important;
    max-width: 92vw !important;
  }

  /* 表格内部滚动 */
  :deep(.el-table__body-wrapper) {
    overflow-x: auto;
  }

  /* 添加学生弹窗中的搜索栏换行 */
  :deep(.el-dialog) .el-input {
    width: 100% !important;
  }

  /* ===== 移动端评语表单：纵向堆叠 ===== */
  .review-form-row {
    flex-direction: column;
  }
  .review-score {
    width: 100%;
  }
  .review-form-actions {
    flex-direction: column;
    align-items: stretch;
  }
  .review-toggle {
    justify-content: center;
  }
  /* 录音行：手机屏幕窄，播放器占整行 */
  .review-voice {
    align-items: flex-start;
  }
  .review-voice-audio {
    max-width: 100%;
    width: 100%;
  }

  /* 提交卡片内边距缩小 */
  .submit-card {
    padding: 10px;
  }
  .submit-image-item {
    width: 80px;
    height: 80px;
  }

  /* ===== 移动端作业卡片 ===== */
  .hw-card-body { padding: 12px 14px 8px; }
  .hw-card-actions { padding: 8px 14px 10px; gap: 6px; }
  .hw-card-actions .el-button { font-size: 12px; padding: 6px 12px; }
  .hw-card-top { flex-direction: column; align-items: flex-start; gap: 6px; }
  .hw-stats-bar { gap: 6px; }
  .hw-stat-item { padding: 5px 10px; font-size: 12px; }
  .hw-stat-item strong { font-size: 14px; }
}

/* 极小屏 (≤400px) */
@media (max-width: 400px) {
  .hw-card-actions {
    flex-wrap: wrap;
  }
}

</style>
