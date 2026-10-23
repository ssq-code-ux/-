<template>
  <div class="admin-page">
    <header class="page-head">
      <h1>{{ currentTabLabel }}</h1>
    </header>

    <!-- ═══════════ Tab 导航 ═══════════ -->
    <div class="admin-tabs">
      <div
        v-for="t in tabs"
        :key="t.key"
        class="admin-tab"
        :class="{ active: currentTab === t.key }"
        @click="$router.push(`/admin/${t.key}`)"
      >
        <span>{{ t.label }}</span>
        <span v-if="tabBadge(t.key)" class="tab-badge">{{ tabBadge(t.key) }}</span>
      </div>
    </div>

    <!-- ═══════════ Tab 1：用户管理 ═══════════ -->
    <div v-if="currentTab === 'users'" class="card">
      <div class="filter-bar">
        <el-input
          v-model="userQuery.keyword"
          placeholder="搜索账号 / 姓名 / 手机号"
          clearable
          style="width: 220px"
          @keyup.enter="loadUsers(1)"
          @clear="loadUsers(1)"
        />
        <el-select v-model="userQuery.role" placeholder="全部角色" clearable style="width: 120px" @change="loadUsers(1)">
          <el-option label="老师" value="teacher" />
          <el-option label="学生" value="student" />
          <el-option label="家长" value="parent" />
          <el-option label="管理员" value="admin" />
        </el-select>
        <el-select v-model="userQuery.status" placeholder="全部状态" clearable style="width: 120px" @change="loadUsers(1)">
          <el-option label="正常" :value="1" />
          <el-option label="已封禁" :value="0" />
        </el-select>
        <el-button type="primary" @click="loadUsers(1)">查询</el-button>
      </div>

      <el-table :data="userList" v-loading="userLoading" empty-text="暂无用户">
        <el-table-column prop="username" label="账号" min-width="110" />
        <el-table-column prop="realName" label="姓名" min-width="90" />
        <el-table-column label="角色" width="80">
          <template #default="{ row }">
            <el-tag :type="roleTag(row.role)" size="small">{{ roleText(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'danger' : 'success'" size="small">
              {{ row.status === 0 ? '已封禁' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="手机号" min-width="118">
          <template #default="{ row }">
            <!-- 需求 4：点手机号直接拉起系统拨号盘 -->
            <a v-if="row.phone" class="tel-link" :href="'tel:' + row.phone">{{ row.phone }}</a>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="邀请码 / 已邀请" min-width="136">
          <template #default="{ row }">
            <div v-if="row.inviteCode" class="invite-cell">
              <b class="invite-code-mini">{{ row.inviteCode }}</b>
              <el-link v-if="row.invitedCount > 0" type="primary" :underline="false" @click="openInvites(row)">
                已邀请 {{ row.invitedCount }} 人
              </el-link>
              <span v-else class="muted">未邀请</span>
            </div>
            <span v-else class="muted">—</span>
            <div v-if="row.inviterName" class="muted invite-from">由 {{ row.inviterName }} 邀请</div>
          </template>
        </el-table-column>
        <el-table-column label="所属老师 / 家长" min-width="130">
          <template #default="{ row }">
            <div v-if="row.role === 'student'">
              <div v-if="row.teacherName">{{ row.teacherName }}</div>
              <div v-if="row.parentName">{{ row.parentName }}</div>
              <span v-if="!row.teacherName && !row.parentName" class="muted">未绑定</span>
            </div>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="onResetPassword(row)">重置密码</el-button>
            <el-button v-if="row.role !== 'admin'" link :type="row.status === 0 ? 'success' : 'danger'" size="small" @click="onToggleStatus(row)">
              {{ row.status === 0 ? '解封' : '封禁' }}
            </el-button>
            <el-dropdown v-if="row.role !== 'admin'" trigger="click" @command="(cmd) => onUserCommand(cmd, row)">
              <el-button link type="primary" size="small">更多 ▾</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="role">调整角色</el-dropdown-item>
                  <el-dropdown-item command="password">修改密码</el-dropdown-item>
                  <el-dropdown-item v-if="row.parentId" command="unbindParent">解除家长绑定</el-dropdown-item>
                  <el-dropdown-item v-if="row.teacherId" command="unbindTeacher">解除老师绑定</el-dropdown-item>
                  <el-dropdown-item command="delete" divided style="color:var(--negative)">删除用户</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="userQuery.page"
        :page-size="userQuery.size"
        :total="userTotal"
        layout="total, prev, pager, next"
        class="pager"
        @current-change="loadUsers()"
      />
    </div>

    <!-- ═══════════ Tab 2：数据看板 ═══════════ -->
    <div v-else-if="currentTab === 'dashboard'" v-loading="dashLoading">
      <div class="kpi-grid">
        <KpiCard
          label="注册用户"
          :value="dash.totalUsers ?? '-'"
          :note="`老师 ${dash.roleCount?.teacher ?? 0}　学生 ${dash.roleCount?.student ?? 0}　家长 ${dash.roleCount?.parent ?? 0}`"
          tone="blue"
        >
          <template #icon><el-icon><User /></el-icon></template>
        </KpiCard>
        <KpiCard
          label="封禁中账号"
          :value="dash.bannedUsers ?? 0"
          :note="`站内通知 ${dash.totalNotifications ?? 0} 条`"
          :tone="dash.bannedUsers > 0 ? 'red' : 'green'"
        >
          <template #icon><el-icon><Warning /></el-icon></template>
        </KpiCard>
      </div>

      <div class="card">
        <div class="card-title">
          家教业务概览
          <el-button link type="primary" size="small" @click="loadTodo">刷新</el-button>
        </div>
        <div class="kpi-grid">
          <KpiCard
            label="待发布家长需求"
            :value="todo.pendingDemands ?? 0"
            :note="`已发布 ${todo.publishedDemands ?? 0} 条`"
            :tone="todo.pendingDemands > 0 ? 'amber' : 'blue'"
          >
            <template #icon><el-icon><Promotion /></el-icon></template>
          </KpiCard>
          <KpiCard
            label="删除申请待确认"
            :value="todo.deleteRequests ?? 0"
            note="家长提交，待管理端处理"
            :tone="todo.deleteRequests > 0 ? 'amber' : 'blue'"
          >
            <template #icon><el-icon><Warning /></el-icon></template>
          </KpiCard>
          <KpiCard
            label="待审核教师投递"
            :value="todo.pendingApplications ?? 0"
            :note="`开放中家教单 ${todo.openJobs ?? 0} 个`"
            :tone="todo.pendingApplications > 0 ? 'amber' : 'blue'"
          >
            <template #icon><el-icon><Document /></el-icon></template>
          </KpiCard>
          <KpiCard
            label="试课中订单"
            :value="todo.trialInProgress ?? 0"
            :note="`已完成 ${todo.trialSuccess ?? 0} 个`"
            tone="blue"
          >
            <template #icon><el-icon><Clock /></el-icon></template>
          </KpiCard>
          <KpiCard
            label="试课失败待退费"
            :value="todo.trialFailed ?? 0"
            :note="`已下架家教单 ${todo.closedJobs ?? 0} 个`"
            :tone="todo.trialFailed > 0 ? 'red' : 'blue'"
          >
            <template #icon><el-icon><Warning /></el-icon></template>
          </KpiCard>
          <KpiCard
            label="缴费收入（未退费）"
            :value="`¥${todo.totalIncome ?? 0}`"
            :note="`${todo.paidTeachers ?? 0} 位老师已缴费，需缴费单 ${todo.payToApplyJobs ?? 0} 个`"
            tone="green"
          >
            <template #icon><el-icon><CreditCard /></el-icon></template>
          </KpiCard>
        </div>
      </div>

      <div class="card">
        <div class="card-title">最近 7 天注册趋势</div>
        <div class="trend-chart">
          <div v-for="p in dash.registerTrend || []" :key="p.date" class="trend-col">
            <div class="trend-count">{{ p.count }}</div>
            <div class="trend-bar" :style="{ height: barHeight(p.count) + 'px' }" />
            <div class="trend-date">{{ p.date }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 单体版没有服务注册中心，原「系统监控」Tab（微服务健康状态 + 催交提醒记录）整段移除 -->

    <!-- ═══════════ Tab 3：家教信息 ═══════════ -->
    <div v-else-if="isJobsTab" class="card">
      <!-- 待办概览：点任意一项就把下面的列表筛成对应的单子，再点一次取消筛选 -->
      <div class="todo-bar">
        <div class="todo-item clickable" :class="{ hot: todo.pendingApplications > 0, on: effectiveView === 'pendingApply' }"
          @click="toggleView('pendingApply')">
          <span class="todo-num">{{ todo.pendingApplications || 0 }}</span>
          <span class="todo-label">待审核投递</span>
        </div>
        <div class="todo-item clickable" :class="{ hot: todo.trialFailed > 0, on: effectiveView === 'refund' }"
          @click="toggleView('refund')">
          <span class="todo-num">{{ todo.trialFailed || 0 }}</span>
          <span class="todo-label">试课失败待退费</span>
        </div>
        <div class="todo-item clickable" :class="{ warn: todo.trialInProgress > 0, on: effectiveView === 'trial' }"
          @click="toggleView('trial')">
          <span class="todo-num">{{ todo.trialInProgress || 0 }}</span>
          <span class="todo-label">试课中</span>
        </div>
        <div class="todo-item clickable" :class="{ on: effectiveView === 'done' }" @click="toggleView('done')">
          <span class="todo-num">{{ todo.trialSuccess || 0 }}</span>
          <span class="todo-label">已完成订单</span>
        </div>
        <div class="todo-item clickable" :class="{ on: effectiveView === 'open' }" @click="toggleView('open')">
          <span class="todo-num">{{ todo.openJobs || 0 }}</span>
          <span class="todo-label">开放中家教单</span>
        </div>
        <div class="todo-item clickable" :class="{ on: !effectiveView }" @click="clearView">
          <span class="todo-num">全部</span>
          <span class="todo-label">清除筛选</span>
        </div>
        <div class="todo-item">
          <span class="todo-num">¥{{ todo.totalIncome || 0 }}</span>
          <span class="todo-label">缴费收入（{{ todo.paidTeachers || 0 }} 人）</span>
        </div>
      </div>

      <div class="filter-bar">
        <el-input
          v-model="jobQuery.keyword"
          placeholder="搜索科目 / 城市 / 编号"
          clearable
          style="width: 200px"
          @keyup.enter="loadJobs(1)"
          @clear="loadJobs(1)"
        />
        <!-- 状态查询三态：试课中不是 tutor_job.status，后端按已通过投递的试课阶段筛 -->
        <el-select v-model="jobQuery.view" placeholder="全部状态" clearable style="width: 120px" @change="onViewSelect">
          <el-option label="开放中" value="open" />
          <el-option label="已关闭" value="closed" />
          <el-option label="试课中" value="trial" />
        </el-select>
        <el-button type="primary" @click="loadJobs(1)">查询</el-button>
        <el-button type="success" @click="openJobForm()">{{ isPartnerTab ? '发布合作单' : '发布家教单' }}</el-button>
        <!-- 需求 7：粘一堆原始信息→自动拆分→逐条校对→批量发布（本版界面不放 AI 图标） -->
        <el-button type="warning" plain @click="openBatch">批量发布</el-button>
        <!-- 需求 8：每个科目一个用例模板 -->
        <el-button plain @click="$router.push('/admin/templates')">科目模板</el-button>
      </div>

      <el-table :data="jobList" v-loading="jobLoading" :empty-text="isPartnerTab ? '暂无同行合作单，点击右上角发布' : '暂无家教单，点击右上角发布'">
        <el-table-column prop="jobNo" label="编号" width="80" />
        <el-table-column prop="subject" label="科目" width="80" />
        <el-table-column label="地点" min-width="120">
          <template #default="{ row }">{{ row.province || '-' }}{{ row.city ? ' - ' + row.city : '' }}</template>
        </el-table-column>
        <el-table-column prop="grade" label="年级" width="70" />
        <el-table-column label="时薪" width="90">
          <template #default="{ row }">{{ row.salary ? row.salary + '元/h' : '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="132">
          <template #default="{ row }">
            <el-tag :type="jobStatusTag(row)" size="small">{{ jobStatusText(row) }}</el-tag>
            <!-- 试课失败后单子已重开、投递已清零，退费改为到「退费工单」Tab 办理 -->
            <el-tooltip v-if="row.refundPending" content="有老师反馈试课失败，退费尚未办理；点这里去「退费工单」办理" placement="top">
              <el-tag type="danger" size="small" effect="dark" class="refund-tag" @click="gotoRefunds(row.jobNo)">
                待退费
              </el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="缴费" width="88">
          <template #default="{ row }">
            <el-tag v-if="row.payToApply === 1" type="danger" size="small" effect="plain">需缴费</el-tag>
            <span v-else class="muted">免费</span>
          </template>
        </el-table-column>
        <el-table-column label="投递数" width="75">
          <template #default="{ row }">
            <el-badge :value="row.applyCount || 0" type="primary" class="job-apply-badge" />
          </template>
        </el-table-column>
        <el-table-column label="发布时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="292" fixed="right">
          <template #default="{ row }">
            <el-button link type="info" size="small" @click="openJobDetail(row)">详情</el-button>
            <el-button link type="primary" size="small" @click="openApplications(row)">投递（{{ row.applyCount || 0 }}）</el-button>
            <el-button v-if="row.status === 1" link type="primary" size="small" @click="openJobForm(row)">编辑</el-button>
            <el-button v-if="row.status === 1" link type="danger" size="small" @click="onCloseJob(row)">下架</el-button>
            <el-button v-else link type="success" size="small" @click="onReopenJob(row)">重开</el-button>
            <el-button link type="danger" size="small" @click="onDeleteJob(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="jobQuery.page"
        :page-size="jobQuery.size"
        :total="jobTotal"
        layout="total, prev, pager, next"
        class="pager"
        @current-change="loadJobs()"
      />
    </div>

    <!-- ═══════════ Tab：家长需求 ═══════════ -->
    <div v-else-if="currentTab === 'demands'" class="card">
      <!-- 待办概览：点击可直接筛选 -->
      <div class="todo-bar">
        <div class="todo-item clickable" :class="{ hot: todo.deleteRequests > 0, on: demandQuery.deleteRequested === 1 }"
          @click="filterDemandsDelete()">
          <span class="todo-num">{{ todo.deleteRequests || 0 }}</span>
          <span class="todo-label">删除申请待确认</span>
        </div>
        <div class="todo-item clickable" :class="{ warn: todo.pendingDemands > 0, on: demandQuery.status === 0 }"
          @click="filterDemands(0)">
          <span class="todo-num">{{ todo.pendingDemands || 0 }}</span>
          <span class="todo-label">待发布需求</span>
        </div>
        <div class="todo-item clickable" :class="{ on: demandQuery.status === 1 }" @click="filterDemands(1)">
          <span class="todo-num">{{ todo.publishedDemands || 0 }}</span>
          <span class="todo-label">已发布需求</span>
        </div>
        <div class="todo-item clickable" :class="{ on: demandQuery.status === -1 && !demandQuery.deleteRequested }"
          @click="filterDemands(-1)">
          <span class="todo-num">全部</span>
          <span class="todo-label">清除筛选</span>
        </div>
      </div>

      <div class="filter-bar">
        <el-input
          v-model="demandQuery.keyword"
          placeholder="搜订单号 / 家长称呼 / 电话 / 微信"
          clearable
          style="width: 240px"
          @keyup.enter="loadDemands(1)"
          @clear="loadDemands(1)"
        />
        <el-select v-model="demandQuery.status" placeholder="全部状态" clearable style="width: 140px" @change="loadDemands(1)">
          <el-option label="待发布" :value="0" />
          <el-option label="已发布" :value="1" />
        </el-select>
        <el-checkbox v-model="onlyDeleteReq" @change="loadDemands(1)">只看删除申请</el-checkbox>
        <el-button type="primary" @click="loadDemands(1)">查询</el-button>
        <span class="filter-tip">需求编号 = 发布后的订单号，两边可直接对号</span>
      </div>

      <el-table :data="demandList" v-loading="demandLoading" empty-text="暂无家长需求" :row-class-name="demandRowClass">
        <el-table-column label="需求编号" width="118">
          <template #default="{ row }">
            <el-tooltip :content="row.jobNo ? `已发布，与家教单订单号一致：${row.jobNo}` : '尚未发布；发布为家教单后订单号将沿用此编号'"
              placement="top">
              <b class="demand-no">{{ row.demandNo || '—' }}</b>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="家长" min-width="140">
          <template #default="{ row }">
            <div>{{ row.contactName || '—' }}</div>
            <!-- 需求 3：家长只必填手机号，其余可能为空 -->
            <div class="muted">
              <a v-if="row.phone" class="tel-link" :href="'tel:' + row.phone">{{ row.phone }}</a>
              <span v-else>未留号码</span>
              <span v-if="row.wechat"> · {{ row.wechat }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="学科" width="76">
          <template #default="{ row }">{{ row.subject || '待确认' }}</template>
        </el-table-column>
        <el-table-column label="年级" width="66">
          <template #default="{ row }">{{ row.grade || '—' }}</template>
        </el-table-column>
        <el-table-column label="时薪" width="86">
          <template #default="{ row }">{{ row.salary ? row.salary + '元/h' : '—' }}</template>
        </el-table-column>
        <el-table-column label="学生情况 / 要求" min-width="200">
          <template #default="{ row }">
            <div v-if="row.studentLevel">{{ row.studentLevel }}</div>
            <div v-if="row.studentGender" class="muted">{{ row.studentGender }}<span v-if="row.expectGender"> · 期望{{ row.expectGender }}老师</span></div>
            <div v-if="row.otherRequire" class="muted">{{ row.otherRequire }}</div>
          </template>
        </el-table-column>
        <el-table-column label="关联家教单" min-width="126">
          <template #default="{ row }">
            <template v-if="row.jobNo">
              <div class="demand-no">{{ row.jobNo }}</div>
              <div class="muted">{{ row.jobStatus || '已关闭' }} · {{ row.applyCount || 0 }} 份投递</div>
            </template>
            <span v-else class="muted">尚未发布</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="112">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'warning'" size="small">{{ row.status === 1 ? '已发布' : '待发布' }}</el-tag>
            <el-tag v-if="row.deleteRequested === 1" type="danger" size="small" effect="dark" style="margin-left:4px">申请删除</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="146">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="196" fixed="right">
          <template #default="{ row }">
            <el-button link type="info" size="small" @click="openDemandDetail(row)">详情</el-button>
            <el-button v-if="row.status === 0" link type="success" size="small" @click="openDemandPublish(row)">发布家教单</el-button>
            <el-button v-if="row.jobNo" link type="primary" size="small" @click="gotoJob(row)">看单</el-button>
            <el-button link type="danger" size="small" @click="onConfirmDeleteDemand(row)">
              {{ row.deleteRequested === 1 ? '确认删除' : '删除' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="demandQuery.page"
        :page-size="demandQuery.size"
        :total="demandTotal"
        layout="total, prev, pager, next"
        class="pager"
        @current-change="loadDemands()"
      />
    </div>

    <!-- ═══════════ Tab：退费工单（需求 1/2） ═══════════ -->
    <div v-else-if="currentTab === 'refunds'" class="card">
      <div class="card-title">
        退费工单
        <span class="card-sub">老师反馈试课失败后，订单已自动重开、投递已清空，退费统一在这里办理</span>
      </div>
      <div class="filter-bar">
        <el-input v-model="refundQuery.keyword" placeholder="搜订单号 / 老师姓名 / 电话" clearable style="width: 240px"
          @keyup.enter="loadRefunds(1)" @clear="loadRefunds(1)" />
        <el-select v-model="refundQuery.status" placeholder="全部状态" clearable style="width: 130px" @change="loadRefunds(1)">
          <el-option label="待退费" :value="0" />
          <el-option label="已办结" :value="1" />
        </el-select>
        <el-button type="primary" @click="loadRefunds(1)">查询</el-button>
        <el-button plain @click="filterRefunds(0)">只看待退费（{{ refundPending }}）</el-button>
      </div>
      <el-table :data="refundList" v-loading="refundLoading" empty-text="暂无退费工单" :row-class-name="refundRowClass">
        <el-table-column prop="jobNo" label="订单号" width="86" />
        <el-table-column label="老师" min-width="146">
          <template #default="{ row }">
            <div>{{ row.teacherName || '—' }}</div>
            <div class="muted">
              <a v-if="row.teacherPhone" class="tel-link" :href="'tel:' + row.teacherPhone">{{ row.teacherPhone }}</a>
              <span v-else>—</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="应退金额" width="98">
          <template #default="{ row }">
            <b v-if="Number(row.amount) > 0" class="amount-danger">{{ row.amount }} 元</b>
            <span v-else class="muted">免费单</span>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="退费原因" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="88">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small" :effect="row.status === 1 ? 'plain' : 'dark'">
              {{ row.status === 1 ? '已办结' : '待退费' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="登记时间" width="146">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="办结时间" width="146">
          <template #default="{ row }">{{ fmtTime(row.refundTime) || '—' }}</template>
        </el-table-column>
        <el-table-column prop="note" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 1" link type="danger" size="small" @click="onSettleRefund(row)">
              {{ Number(row.amount) > 0 ? '办理退费' : '标记已处理' }}
            </el-button>
            <el-button v-if="row.jobNo" link type="info" size="small" @click="gotoJobByNo(row.jobNo)">看单</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="refundQuery.page"
        :page-size="refundQuery.size"
        :total="refundTotal"
        layout="total, prev, pager, next"
        class="pager"
        @current-change="loadRefunds()"
      />
    </div>

    <!-- ═══════════ Tab：缴费流水（需求 2：缴费与退费都有时间记录） ═══════════ -->
    <div v-else-if="currentTab === 'payments'" class="card">
      <div class="card-title">
        缴费 / 退费流水
        <span class="card-sub">只追加不修改，删单也不会丢；累计缴费 ¥{{ payLogSum.paidTotal || 0 }} · 累计退费 ¥{{ payLogSum.refundTotal || 0 }}</span>
      </div>
      <div class="filter-bar">
        <el-input v-model="payLogQuery.jobId" placeholder="按订单 ID 过滤" clearable style="width: 160px"
          @keyup.enter="loadPayLogs(1)" @clear="loadPayLogs(1)" />
        <el-select v-model="payLogQuery.type" placeholder="全部类型" clearable style="width: 130px" @change="loadPayLogs(1)">
          <el-option label="缴费" :value="1" />
          <el-option label="退费" :value="2" />
        </el-select>
        <el-button type="primary" @click="loadPayLogs(1)">查询</el-button>
      </div>
      <el-table :data="payLogList" v-loading="payLogLoading" empty-text="暂无流水">
        <el-table-column label="类型" width="76">
          <template #default="{ row }">
            <el-tag :type="row.type === 1 ? 'success' : 'danger'" size="small">{{ row.type === 1 ? '缴费' : '退费' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="100">
          <template #default="{ row }">
            <b :class="row.type === 1 ? 'amount-ok' : 'amount-danger'">{{ row.type === 1 ? '+' : '-' }}{{ row.amount }} 元</b>
          </template>
        </el-table-column>
        <el-table-column prop="jobNo" label="订单号" width="86" />
        <el-table-column prop="teacherName" label="老师" min-width="100" />
        <el-table-column prop="operatorName" label="办理人" min-width="100" />
        <el-table-column prop="note" label="备注" min-width="150" show-overflow-tooltip />
        <el-table-column label="发生时间" width="146">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="payLogQuery.page"
        :page-size="payLogQuery.size"
        :total="payLogTotal"
        layout="total, prev, pager, next"
        class="pager"
        @current-change="loadPayLogs()"
      />
    </div>

    <!-- ═══════════ Tab：科目用例模板（需求 8） ═══════════ -->
    <div v-else-if="currentTab === 'templates'" class="card">
      <div class="card-title">
        科目用例模板
        <el-button type="success" size="small" @click="openTplForm()">新增模板</el-button>
      </div>
      <div class="muted" style="font-size:12px;margin-bottom:10px">
        同一科目 + 同一单子类型只能有一个模板；发布家教单时可直接套用，再改细节即可。
      </div>
      <el-table :data="templateList" v-loading="templateLoading" empty-text="暂无模板">
        <el-table-column prop="subject" label="科目" width="86" />
        <el-table-column prop="name" label="模板名称" min-width="150" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="row.jobType === 2 ? 'warning' : 'primary'" size="small" effect="plain">
              {{ row.jobType === 2 ? '同行合作单' : '家教单' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="默认内容" min-width="260">
          <template #default="{ row }">
            <div class="muted">{{ [row.grade, row.province, row.city, row.frequency, row.salary ? row.salary + '元/h' : ''].filter(Boolean).join(' · ') || '—' }}</div>
            <div v-if="row.situation" class="muted">{{ row.situation }}</div>
            <div v-if="row.requirement" class="muted">{{ row.requirement }}</div>
          </template>
        </el-table-column>
        <el-table-column label="缴费解锁" width="92">
          <template #default="{ row }">
            <el-tag :type="row.payToApply === 1 ? 'danger' : 'info'" size="small" effect="plain">
              {{ row.payToApply === 1 ? '需缴费' : '免费' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="146">
          <template #default="{ row }">{{ fmtTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="176" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openTplForm(row)">编辑</el-button>
            <el-button link type="success" size="small" @click="useTplNow(row)">用此模板发布</el-button>
            <el-button link type="danger" size="small" @click="onDeleteTpl(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- ═══════════ Tab：接单配置（须知/联系方式/收款码） ═══════════ -->
    <div v-else-if="currentTab === 'config'" class="card" v-loading="configLoading">
      <div class="card-title">接单配置（教师端展示的接单须知、联系方式与收款码）</div>
      <el-form label-width="120px" label-position="left" style="max-width: 720px">
        <el-form-item label="接单须知（文字）">
          <el-input v-model="tutorConfig.noticeText" type="textarea" :rows="6" maxlength="2000" show-word-limit
            placeholder="填写教师接单须知，如：接单流程、缴费规则、退费说明等" />
        </el-form-item>
        <el-form-item label="接单须知图片">
          <div class="cfg-imgs">
            <div v-for="(img, i) in tutorConfig.noticeImgs" :key="i" class="cfg-img-box">
              <img :src="img" @click="previewImg(img)" />
              <span class="cfg-img-del" @click="tutorConfig.noticeImgs.splice(i, 1)">×</span>
            </div>
            <el-upload :show-file-list="false" :http-request="uploadNoticeImg" accept="image/*">
              <div class="cfg-img-add">＋</div>
            </el-upload>
          </div>
        </el-form-item>
        <el-form-item label="管理员电话">
          <el-input v-model="tutorConfig.phone" placeholder="教师缴费沟通 / 家长通过简历后展示" maxlength="20" style="width: 260px" />
        </el-form-item>
        <el-form-item label="管理员微信">
          <el-input v-model="tutorConfig.wechat" placeholder="同上，微信号" maxlength="50" style="width: 260px" />
        </el-form-item>
        <el-form-item label="收款码图片">
          <div v-if="tutorConfig.payQrUrl" class="cfg-img-box">
            <img :src="tutorConfig.payQrUrl" @click="previewImg(tutorConfig.payQrUrl)" />
            <span class="cfg-img-del" @click="tutorConfig.payQrUrl = ''">×</span>
          </div>
          <el-upload v-else :show-file-list="false" :http-request="uploadPayQr" accept="image/*">
            <el-button size="small">上传收款码</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="configSaving" @click="saveConfig">保存配置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- ═══════════ 发布家长需求为家教单弹窗 ═══════════ -->
    <el-dialog v-model="demandPublishVisible" title="发布为家教单（教师端可见）" :width="isMobile ? '94%' : '640px'">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
        <template #title>
          家长：{{ demandPublishForm.contactName || '—' }}
          <a v-if="demandPublishForm.phone" class="tel-link" :href="'tel:' + demandPublishForm.phone">{{ demandPublishForm.phone }}</a>
          <span v-if="demandPublishForm.wechat"> · {{ demandPublishForm.wechat }}</span>
          （家长信息不会展示给教师端）
        </template>
      </el-alert>
      <el-form :model="demandPublishForm" label-width="90px" label-position="left">
        <el-form-item label="科目" required>
          <el-input v-model="demandPublishForm.subject" maxlength="50" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="省份"><el-input v-model="demandPublishForm.province" placeholder="如：河南" maxlength="50" /></el-form-item>
          <el-form-item label="城市"><el-input v-model="demandPublishForm.city" placeholder="如：郑州" maxlength="50" /></el-form-item>
        </div>
        <el-form-item label="详细地址"><el-input v-model="demandPublishForm.address" maxlength="200" /></el-form-item>
        <div class="form-row">
          <el-form-item label="年级"><el-input v-model="demandPublishForm.grade" maxlength="50" /></el-form-item>
          <el-form-item label="时薪(元/h)"><el-input v-model="demandPublishForm.salary" type="number" /></el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="学生性别">
            <el-select v-model="demandPublishForm.studentGender" placeholder="不限" clearable style="width: 100%">
              <el-option label="男孩" value="男孩" /><el-option label="女孩" value="女孩" />
            </el-select>
          </el-form-item>
          <el-form-item label="要求老师">
            <el-select v-model="demandPublishForm.expectGender" placeholder="不限" clearable style="width: 100%">
              <el-option label="男老师" value="男" /><el-option label="女老师" value="女" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="上课频次"><el-input v-model="demandPublishForm.frequency" placeholder="如：每周1次，每次2小时" maxlength="100" /></el-form-item>
        <el-form-item label="学生情况"><el-input v-model="demandPublishForm.situation" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item label="要求"><el-input v-model="demandPublishForm.requirement" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item label="缴费解锁">
          <el-switch v-model="demandPublishForm.payToApply" active-text="需缴费后才能投递" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="demandPublishVisible = false">取消</el-button>
        <el-button type="primary" :loading="demandPublishing" @click="submitDemandPublish">发布</el-button>
      </template>
    </el-dialog>

    <!-- ═══════════ 家教单详情弹窗（完整信息 + 家长联系方式） ═══════════ -->
    <el-dialog v-model="jobDetailVisible" :title="`家教单详情（${jobDetail.jobNo || ''}）`" :width="isMobile ? '94%' : '640px'">
      <div v-loading="jobDetailLoading">
        <div class="jd-grid">
          <div class="jd-cell"><span>科目</span>{{ jobDetail.subject || '—' }}</div>
          <div class="jd-cell"><span>年级</span>{{ jobDetail.grade || '—' }}</div>
          <div class="jd-cell"><span>时薪</span>{{ jobDetail.salary ? jobDetail.salary + ' 元/h' : '—' }}</div>
          <div class="jd-cell"><span>订单阶段</span>
            <el-tag :type="jobStatusTag(jobDetail)" size="small">{{ jobStatusText(jobDetail) }}</el-tag>
            <el-tag v-if="jobDetail.refundPending" type="danger" size="small" effect="dark" class="refund-tag">试课失败待退费</el-tag>
          </div>
          <div class="jd-cell"><span>投递数</span>{{ jobDetail.applyCount || 0 }}</div>
          <div class="jd-cell"><span>缴费解锁</span>{{ jobDetail.payToApply === 1 ? '需缴费后投递' : '免费投递' }}</div>
          <div class="jd-cell"><span>学生性别</span>{{ jobDetail.studentGender || '不限' }}</div>
          <div class="jd-cell"><span>要求老师</span>{{ jobDetail.expectGender ? jobDetail.expectGender + '老师' : '不限' }}</div>
          <div class="jd-cell jd-wide"><span>上课频次</span>{{ jobDetail.frequency || '—' }}</div>
          <div class="jd-cell jd-wide"><span>地点</span>{{ (jobDetail.province || '') + (jobDetail.city ? ' - ' + jobDetail.city : '') + (jobDetail.address ? ' ' + jobDetail.address : '') || '—' }}</div>
          <div class="jd-cell jd-wide"><span>学生情况</span>{{ jobDetail.situation || '—' }}</div>
          <div class="jd-cell jd-wide"><span>要求</span>{{ jobDetail.requirement || '—' }}</div>
          <div class="jd-cell"><span>发布时间</span>{{ fmtTime(jobDetail.createTime) || '—' }}</div>
          <div class="jd-cell"><span>关闭时间</span>{{ fmtTime(jobDetail.closeTime) || '—' }}</div>
          <div v-if="jobDetail.approvedTeacherName" class="jd-cell jd-wide">
            <span>已通过老师</span>{{ jobDetail.approvedTeacherName }} ·
            <a v-if="jobDetail.approvedTeacherPhone" class="tel-link" :href="'tel:' + jobDetail.approvedTeacherPhone">{{ jobDetail.approvedTeacherPhone }}</a>
            <span v-else class="muted">—</span>
          </div>
        </div>
        <!-- 家长联系方式 -->
        <div v-if="jobDetail.parentContact" class="admin-parent-contact">
          <div class="apc-title">家长联系方式</div>
          <div class="apc-line">
            {{ jobDetail.parentContact.contactName || '—' }} ·
            <a v-if="jobDetail.parentContact.phone" class="tel-link" :href="'tel:' + jobDetail.parentContact.phone">{{ jobDetail.parentContact.phone }}</a>
            <span v-if="jobDetail.parentContact.wechat"> · 微信：{{ jobDetail.parentContact.wechat }}</span>
          </div>
          <div v-if="jobDetail.parentContact.grade || jobDetail.parentContact.studentLevel" class="apc-line muted">
            <span v-if="jobDetail.parentContact.grade">{{ jobDetail.parentContact.grade }}</span>
            <span v-if="jobDetail.parentContact.subject"> · {{ jobDetail.parentContact.subject }}</span>
            <span v-if="jobDetail.parentContact.studentLevel"> · 学生情况：{{ jobDetail.parentContact.studentLevel }}</span>
          </div>
        </div>
        <div v-else class="muted" style="margin-top:10px">该单为管理员手工发布，无关联家长</div>
      </div>
      <template #footer>
        <el-button @click="jobDetailVisible = false">关闭</el-button>
        <el-button type="primary" @click="jobDetailVisible = false; openApplications(jobDetail)">查看投递</el-button>
      </template>
    </el-dialog>

    <!-- ═══════════ 家长需求详情弹窗（家长填写的完整信息 + 关联家教单） ═══════════ -->
    <el-dialog v-model="demandDetailVisible" :title="`家长需求详情（${demandDetail.demandNo || ''}）`"
      :width="isMobile ? '94%' : '640px'">
      <div v-loading="demandDetailLoading">
        <div class="admin-parent-contact">
          <div class="apc-title">家长联系方式</div>
          <div class="apc-line">
            {{ demandDetail.contactName || '—' }} ·
            <a v-if="demandDetail.phone" class="tel-link" :href="'tel:' + demandDetail.phone">{{ demandDetail.phone }}</a>
            <span v-else class="muted">未留号码</span>
            <span v-if="demandDetail.wechat"> · 微信：{{ demandDetail.wechat }}</span>
          </div>
        </div>
        <div class="jd-grid">
          <div class="jd-cell"><span>需求编号</span><b class="demand-no">{{ demandDetail.demandNo || '—' }}</b></div>
          <div class="jd-cell"><span>状态</span>
            <el-tag :type="demandDetail.status === 1 ? 'success' : 'warning'" size="small">
              {{ demandDetail.status === 1 ? '已发布' : '待发布' }}
            </el-tag>
            <el-tag v-if="demandDetail.deleteRequested === 1" type="danger" size="small" effect="dark"
              style="margin-left:4px">申请删除</el-tag>
          </div>
          <div class="jd-cell"><span>学科</span>{{ demandDetail.subject || '—' }}</div>
          <div class="jd-cell"><span>年级</span>{{ demandDetail.grade || '—' }}</div>
          <div class="jd-cell"><span>期望时薪</span>{{ demandDetail.salary ? demandDetail.salary + ' 元/h' : '面议' }}</div>
          <div class="jd-cell"><span>学生性别</span>{{ demandDetail.studentGender || '不限' }}</div>
          <div class="jd-cell"><span>期望老师</span>{{ demandDetail.expectGender ? demandDetail.expectGender + '老师' : '不限' }}</div>
          <div class="jd-cell"><span>提交时间</span>{{ fmtTime(demandDetail.createTime) || '—' }}</div>
          <div class="jd-cell jd-wide"><span>学生情况</span>{{ demandDetail.studentLevel || '—' }}</div>
          <div class="jd-cell jd-wide"><span>其他要求</span>{{ demandDetail.otherRequire || '—' }}</div>
          <div class="jd-cell jd-wide"><span>发布时间</span>{{ fmtTime(demandDetail.publishTime) || '尚未发布' }}</div>
        </div>
        <!-- 关联家教单 -->
        <div v-if="demandDetail.job" class="dd-job">
          <div class="apc-title">关联家教单</div>
          <div class="apc-line">
            <b class="demand-no">{{ demandDetail.job.jobNo }}</b> · {{ demandDetail.job.subject || '—' }}
            · {{ demandDetail.job.status === 1 ? '开放中' : '已关闭' }}
          </div>
          <div class="apc-line muted">
            {{ (demandDetail.job.province || '') + (demandDetail.job.city ? ' - ' + demandDetail.job.city : '') || '地点未填' }}
            <span v-if="demandDetail.job.salary"> · {{ demandDetail.job.salary }} 元/h</span>
          </div>
        </div>
        <div v-else class="muted" style="margin-top:10px">该需求尚未发布为家教单</div>
      </div>
      <template #footer>
        <el-button @click="demandDetailVisible = false">关闭</el-button>
        <el-button v-if="demandDetail.job" @click="demandDetailVisible = false; gotoJob(demandDetail)">看关联家教单</el-button>
        <el-button v-if="demandDetail.status === 0" type="success" @click="demandDetailVisible = false; openDemandPublish(demandDetail)">
          发布家教单
        </el-button>
        <el-button type="danger" @click="demandDetailVisible = false; onConfirmDeleteDemand(demandDetail)">
          {{ demandDetail.deleteRequested === 1 ? '确认删除' : '删除' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- ═══════════ 图片预览 ═══════════ -->
    <el-dialog v-model="imgPreviewVisible" width="fit-content" class="img-preview-dialog">
      <img :src="imgPreviewUrl" style="max-width: 82vw; max-height: 74vh; display: block" />
    </el-dialog>

    <!-- ═══════════ 发布/编辑家教单弹窗 ═══════════ -->
    <el-dialog v-model="jobFormVisible"
      :title="(jobForm.id ? '编辑' : '发布') + (jobForm.jobType === 2 ? '同行合作单 ' : '家教单 ') + (jobForm.jobNo || '')"
      :width="isMobile ? '94%' : '640px'">
      <el-form :model="jobForm" label-width="90px" label-position="left">
        <!-- 需求 8：新建时可先套一个科目模板，再改细节 -->
        <el-form-item v-if="!jobForm.id" label="套用模板">
          <el-select v-model="jobTemplateId" placeholder="不用模板，直接填写" clearable style="width: 100%" @change="applyTemplate">
            <el-option v-for="t in templateOptions" :key="t.id"
              :label="`${t.subject} · ${t.name}${t.jobType === 2 ? '（合作单）' : ''}`" :value="t.id" />
          </el-select>
        </el-form-item>
        <!-- 需求 6：同行合作单与家教单分开管理，发布时就定好类型 -->
        <el-form-item label="单子类型">
          <el-radio-group v-model="jobForm.jobType">
            <el-radio :value="1">家教单</el-radio>
            <el-radio :value="2">同行合作单</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="科目" required>
          <el-input v-model="jobForm.subject" placeholder="如：数学 / 英语" maxlength="50" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="省份">
            <el-input v-model="jobForm.province" placeholder="如：江苏" maxlength="50" />
          </el-form-item>
          <el-form-item label="城市">
            <el-input v-model="jobForm.city" placeholder="如：南通" maxlength="50" />
          </el-form-item>
        </div>
        <el-form-item label="详细地址">
          <el-input v-model="jobForm.address" placeholder="如：南通海门区、秀山新村" maxlength="200" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="年级">
            <el-input v-model="jobForm.grade" placeholder="如：初三" maxlength="50" />
          </el-form-item>
          <el-form-item label="时薪(元/h)">
            <el-input v-model="jobForm.salary" type="number" placeholder="如：150" />
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="学生性别">
            <el-select v-model="jobForm.studentGender" placeholder="不限" clearable style="width: 100%">
              <el-option label="男孩" value="男孩" />
              <el-option label="女孩" value="女孩" />
            </el-select>
          </el-form-item>
          <el-form-item label="要求老师">
            <el-select v-model="jobForm.expectGender" placeholder="不限" clearable style="width: 100%">
              <el-option label="男老师" value="男" />
              <el-option label="女老师" value="女" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="上课频次">
          <el-input v-model="jobForm.frequency" placeholder="如：每周1次，每次2小时，周中晚上或双休日" maxlength="100" />
        </el-form-item>
        <el-form-item label="学生情况">
          <el-input v-model="jobForm.situation" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="如：补基础，提优" />
        </el-form-item>
        <el-form-item label="要求">
          <el-input v-model="jobForm.requirement" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="如：教学经验丰富，善于引导" />
        </el-form-item>
        <el-form-item label="缴费解锁">
          <el-switch v-model="jobForm.payToApply" active-text="需缴费后才能投递（教师需先与管理员沟通缴费，由管理员标记已缴费后解锁）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="jobFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="jobFormSubmitting" @click="submitJobForm">
          {{ jobForm.id ? '保存' : '发布' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- ═══════════ 投递列表/审核弹窗（含教师简历与缴费管理） ═══════════ -->
    <el-dialog v-model="appsVisible" :title="`投递列表（${appsJob?.jobNo || ''} · ${appsJob?.subject || ''}）`" :width="isMobile ? '94%' : '760px'" top="4vh">
      <el-alert
        v-if="appsJob?.status === 0 && appsJob?.approvedTeacherId"
        type="success" :closable="false" show-icon style="margin-bottom: 12px"
        :title="`该单已关闭：已通过一位老师的投递（老师ID ${appsJob.approvedTeacherId}），不再接受投递`"
      />
      <!-- 需求 1：试课失败后单子会重开、投递全部清零，退费改为在「退费工单」Tab 办理；
           这里保留历史数据的试课失败/已退费标识，点退费仍会同步结掉对应工单 -->
      <el-alert
        v-if="settledCount > 0"
        type="warning" :closable="false" show-icon style="margin-bottom: 12px"
        :title="`列表含 ${settledCount} 条试课失败 / 已退费的历史记录；待退费的请到「退费工单」Tab 统一办理`"
      />
      <!-- 该单对应家长的联系方式（简历通过的老师需要对接时使用） -->
      <div v-if="appParentContact" class="admin-parent-contact">
        <div class="apc-title">该单家长联系方式</div>
        <div class="apc-line">
          {{ appParentContact.contactName || '—' }} ·
          <a v-if="appParentContact.phone" class="tel-link" :href="'tel:' + appParentContact.phone">{{ appParentContact.phone }}</a>
          <span v-if="appParentContact.wechat"> · 微信：{{ appParentContact.wechat }}</span>
        </div>
        <div v-if="appParentContact.grade || appParentContact.studentLevel" class="apc-line muted">
          <span v-if="appParentContact.grade">{{ appParentContact.grade }}</span>
          <span v-if="appParentContact.subject"> · {{ appParentContact.subject }}</span>
          <span v-if="appParentContact.studentLevel"> · 学生情况：{{ appParentContact.studentLevel }}</span>
        </div>
      </div>
      <div v-loading="appLoading" class="apps-body">
        <div v-if="!appLoading && !appList.length" class="muted" style="text-align:center;padding:24px 0">暂无投递</div>

        <div v-for="row in appList" :key="row.id" class="admin-app-card">
          <div class="app-head">
            <img v-if="row.profile?.photoUrl" :src="row.profile.photoUrl" class="app-photo" @click="previewImg(row.profile.photoUrl)" />
            <div v-else class="app-photo app-photo-empty">照片</div>
            <div class="app-head-info">
              <div class="app-name">
                <b>{{ row.profile?.name || row.teacherName || '老师' }}</b>
                <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : 'warning'" size="small">
                  {{ ['待处理', '已通过', '未通过'][row.status] }}
                </el-tag>
                <el-tag v-if="row.status === 1 && row.trialStatus != null" :type="TRIAL_TAG[row.trialStatus]" size="small" effect="plain">
                  {{ TRIAL_TEXT[row.trialStatus] }}
                </el-tag>
                <el-tag v-if="Number(row.paidAmount) > 0" type="danger" size="small" effect="dark">已缴 {{ row.paidAmount }}元</el-tag>
              </div>
              <div class="app-brief">
                <a v-if="row.teacherPhone" class="tel-link" :href="'tel:' + row.teacherPhone">{{ row.teacherPhone }}</a>
                <span v-else>—</span>
                <span v-if="row.profile?.gender"> · {{ row.profile.gender }}</span>
                <span v-if="row.profile?.age"> · {{ row.profile.age }}岁</span>
                <span v-if="row.profile?.school"> · {{ row.profile.school }}</span>
                <span v-if="row.profile?.major"> · {{ row.profile.major }}</span>
                <span v-if="row.profile?.gradeYear"> · {{ row.profile.gradeYear }}</span>
              </div>
              <div class="app-brief" v-if="row.profile?.teachYears || row.profile?.hometown">
                <span v-if="row.profile?.teachYears">教龄 {{ row.profile.teachYears }}</span>
                <span v-if="row.profile?.hometown"> · 籍贯 {{ row.profile.hometown }}</span>
              </div>
            </div>
          </div>

          <div class="app-grid">
            <div v-if="row.profile?.subjects" class="app-cell"><span>接单科目</span>{{ row.profile.subjects }}</div>
            <div v-if="row.profile?.availableTime" class="app-cell"><span>可上课时间</span>{{ row.profile.availableTime }}</div>
            <div v-if="row.profile?.longTerm" class="app-cell"><span>长期授课</span>{{ row.profile.longTerm }}</div>
            <div v-if="row.profile?.textbookVer" class="app-cell"><span>可教教材版本</span>{{ row.profile.textbookVer }}</div>
            <div v-if="row.profile?.teachScope" class="app-cell"><span>可辅导年级及科目</span>{{ row.profile.teachScope }}</div>
            <div v-if="row.profile?.hasMaterial" class="app-cell"><span>备有辅导教材</span>{{ row.profile.hasMaterial }}</div>
          </div>
          <div v-if="row.profile?.teachAdvantage" class="app-block"><span>辅导优势：</span>{{ row.profile.teachAdvantage }}</div>
          <div v-if="row.profile?.otherAdvantage" class="app-block"><span>其他优势：</span>{{ row.profile.otherAdvantage }}</div>
          <div v-if="row.profile?.awardImgs" class="app-block">
            <span>奖项证明：</span>
            <div class="award-thumbs">
              <img v-for="(img, i) in String(row.profile.awardImgs).split(',').filter(Boolean)" :key="i"
                :src="img" class="award-thumb" @click="previewImg(img)" />
            </div>
          </div>
          <div v-if="row.message" class="app-msg">附言：{{ row.message }}</div>
          <div class="app-time">{{ fmtTime(row.createTime) }} 投递</div>

          <div class="app-ops">
            <!-- 试课失败待退费：这里是管理端办理退费的入口（待处理分支不会命中已通过的记录） -->
            <template v-if="row.trialStatus === 2">
              <span class="trial-refund-tip">该老师反馈试课失败，单子已重新开放，请尽快办理退费</span>
              <el-button v-if="Number(row.paidAmount) > 0" size="small" type="danger" @click="onRefund(row)">
                办理退费（{{ row.paidAmount }}元）
              </el-button>
              <!-- 免费单没有退款金额，但仍需把「试课失败待退费」结算掉，否则待退费计数清不掉 -->
              <el-button v-else size="small" type="warning" plain @click="onRefund(row)">
                标记已处理（该单无需缴费）
              </el-button>
            </template>
            <template v-else-if="row.trialStatus === 3">
              <span class="muted">试课退费已完成，老师已收到退费通知</span>
            </template>
            <template v-else-if="row.status === 0 && appsJob?.status === 1">
              <el-button size="small" @click="onMarkPaid(row)">标记缴费</el-button>
              <el-button v-if="Number(row.paidAmount) > 0" size="small" type="warning" plain @click="onRefund(row)">退费</el-button>
              <el-button size="small" @click="onReview(row, false)">不通过</el-button>
              <el-button size="small" type="primary" @click="onReview(row, true)">通过</el-button>
            </template>
            <template v-else-if="row.status === 0">
              <el-button size="small" @click="onMarkPaid(row)">标记缴费</el-button>
              <el-button v-if="Number(row.paidAmount) > 0" size="small" type="warning" plain @click="onRefund(row)">退费</el-button>
              <span class="muted">单子已关闭，待处理状态仅可管理缴费</span>
            </template>
          </div>
        </div>
      </div>
      <div v-if="appsJob?.status === 0 && !appsJob?.approvedTeacherId" class="muted" style="margin-top: 8px">
        该单已被手动下架，重新上架后可继续审核投递
      </div>

      <!-- 缴费解锁管理：教师需先缴费才能投递的单，管理员在这里主动开通（不依赖投递记录） -->
      <div class="pay-section">
        <div class="pay-section-title">
          <b>缴费解锁管理</b>
          <el-button size="small" type="primary" plain @click="openPayGrant">开通缴费解锁</el-button>
        </div>
        <div class="muted" style="font-size: 12px; margin-bottom: 8px">
          需缴费的单：教师联系管理员并扫收款码缴费后，在这里选中老师标记金额，即可开通该老师对本单的投递资格
        </div>
        <el-table v-if="payList.length" :data="payList" size="small">
          <el-table-column prop="teacherName" label="老师" min-width="100" />
          <el-table-column prop="teacherId" label="ID" width="70" />
          <el-table-column label="已缴金额" width="100">
            <template #default="{ row }">
              <span v-if="Number(row.amount) > 0" style="color:var(--negative);font-weight:600">{{ row.amount }} 元</span>
              <span v-else class="muted">已退清</span>
            </template>
          </el-table-column>
          <el-table-column label="缴费时间" width="145">
            <template #default="{ row }">{{ fmtTime(row.paidTime) || '—' }}</template>
          </el-table-column>
          <el-table-column label="退费时间" width="145">
            <template #default="{ row }">{{ fmtTime(row.refundTime) || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button v-if="Number(row.amount) > 0" link type="warning" size="small" @click="onRefundPayRow(row)">退费</el-button>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
        </el-table>
        <div v-else class="muted">本单暂无缴费记录</div>
      </div>
    </el-dialog>

    <!-- ═══════════ 开通缴费解锁弹窗（搜老师 → 填金额 → 标记已缴） ═══════════ -->
    <el-dialog v-model="payGrantVisible" title="开通缴费解锁" :width="isMobile ? '92%' : '520px'">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
        :title="`家教单 ${appsJob?.jobNo || ''} · ${appsJob?.subject || ''}：开通后该老师即可投递本单`" />
      <el-form label-width="90px" label-position="left">
        <el-form-item label="搜索老师" required>
          <div style="display:flex;gap:8px;width:100%">
            <el-input v-model="payGrantKeyword" placeholder="输入账号 / 姓名 / 手机号" style="flex:1"
              @keyup.enter="searchPayTeachers" clearable @clear="payGrantOptions = []" />
            <el-button @click="searchPayTeachers">搜索</el-button>
          </div>
        </el-form-item>
      </el-form>
      <div v-if="payGrantSearching" style="text-align:center;padding:12px 0;color:var(--muted)">搜索中…</div>
      <div v-else-if="payGrantOptions.length" class="pay-teacher-list">
        <div
          v-for="t in payGrantOptions"
          :key="t.id"
          class="pay-teacher-item"
          :class="{ active: payGrantTeacherId === t.id }"
          @click="payGrantTeacherId = t.id"
        >
          <span class="pt-name">{{ t.realName || t.username }}</span>
          <span class="muted">{{ t.username }} · <a v-if="t.phone" class="tel-link" :href="'tel:' + t.phone">{{ t.phone }}</a><span v-else>—</span></span>
        </div>
      </div>
      <el-form v-if="payGrantTeacherId" label-width="90px" label-position="left" style="margin-top: 10px">
        <el-form-item label="实缴金额" required>
          <el-input v-model="payGrantAmount" placeholder="元，如：50 或 49.9" style="width: 220px">
            <template #append>元</template>
          </el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="payGrantVisible = false">取消</el-button>
        <el-button type="primary" :loading="payGrantSubmitting" @click="submitPayGrant">确认已缴费并开通</el-button>
      </template>
    </el-dialog>

    <!-- ═══════════ 需求 5：某位老师邀请来的账号名单 ═══════════ -->
    <el-dialog v-model="invitesVisible" :title="`${invitesOwner} 邀请的账号`" :width="isMobile ? '94%' : '620px'">
      <div v-loading="invitesLoading">
        <div v-if="invitesCode" class="invite-code-line">
          邀请码：<b class="invite-code-mini">{{ invitesCode }}</b>
          · 已邀请 <b>{{ invitesList.length }}</b> 人
        </div>
        <el-table v-if="invitesList.length" :data="invitesList" size="small">
          <el-table-column prop="username" label="账号" min-width="100" />
          <el-table-column label="姓名" min-width="86">
            <template #default="{ row }">{{ row.realName || '—' }}</template>
          </el-table-column>
          <el-table-column label="角色" width="76">
            <template #default="{ row }">
              <el-tag :type="roleTag(row.role)" size="small">{{ roleText(row.role) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="手机号" min-width="118">
            <template #default="{ row }">
              <a v-if="row.phone" class="tel-link" :href="'tel:' + row.phone">{{ row.phone }}</a>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="注册时间" width="140">
            <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="这位老师还没有邀请到任何人" :image-size="70" />
      </div>
      <template #footer>
        <el-button @click="invitesVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- ═══════════ 需求 7：批量发布（粘贴 → 拆分 → 校对 → 发布） ═══════════ -->
    <el-dialog v-model="batchVisible" title="批量发布家教单" :width="isMobile ? '96%' : '1060px'" top="4vh"
      :close-on-click-modal="false">
      <el-steps :active="batchStep" align-center finish-status="success" class="batch-steps">
        <el-step title="粘贴原始信息" />
        <el-step title="拆分并校对" />
        <el-step title="发布结果" />
      </el-steps>

      <!-- 第一步：粘文本 -->
      <div v-show="batchStep === 0">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px"
          title="把群里 / 朋友圈的多条家教信息整段粘进来，AI 会自动拆成一条条并归类字段；单次最多 50 条" />
        <el-form label-width="80px" label-position="left">
          <el-form-item label="单子类型">
            <el-radio-group v-model="batchJobType">
              <el-radio :value="1">家教单</el-radio>
              <el-radio :value="2">同行合作单</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
        <el-input v-model="batchText" type="textarea" :rows="12" maxlength="20000" show-word-limit resize="none"
          placeholder="示例：&#10;1、南通海门初二数学，每周两次每次2小时，150/小时，需女老师&#10;2、郑州金水区高三英语冲刺，每周3次，200/小时，有一轮复习经验优先" />
      </div>

      <!-- 第二步：逐条校对（可直接改） -->
      <div v-show="batchStep === 1">
        <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 10px"
          :title="`AI 拆出 ${batchRows.length} 条，请逐条核对科目 / 地区 / 时薪；不对的直接在表里改，改完再发布`" />
        <el-table :data="batchRows" size="small" border max-height="48vh">
          <el-table-column type="index" label="#" width="44" fixed />
          <el-table-column label="科目" width="96" fixed>
            <template #default="{ row }"><el-input v-model="row.subject" size="small" /></template>
          </el-table-column>
          <el-table-column label="年级" width="84">
            <template #default="{ row }"><el-input v-model="row.grade" size="small" /></template>
          </el-table-column>
          <el-table-column label="省份" width="84">
            <template #default="{ row }"><el-input v-model="row.province" size="small" /></template>
          </el-table-column>
          <el-table-column label="城市" width="84">
            <template #default="{ row }"><el-input v-model="row.city" size="small" /></template>
          </el-table-column>
          <el-table-column label="详细地址" width="150">
            <template #default="{ row }"><el-input v-model="row.address" size="small" /></template>
          </el-table-column>
          <el-table-column label="上课频次" width="130">
            <template #default="{ row }"><el-input v-model="row.frequency" size="small" /></template>
          </el-table-column>
          <el-table-column label="时薪" width="76">
            <template #default="{ row }"><el-input v-model="row.salary" size="small" type="number" /></template>
          </el-table-column>
          <el-table-column label="学生性别" width="84">
            <template #default="{ row }"><el-input v-model="row.studentGender" size="small" /></template>
          </el-table-column>
          <el-table-column label="要求老师" width="84">
            <template #default="{ row }"><el-input v-model="row.expectGender" size="small" /></template>
          </el-table-column>
          <el-table-column label="学生情况" width="180">
            <template #default="{ row }"><el-input v-model="row.situation" size="small" /></template>
          </el-table-column>
          <el-table-column label="对老师要求" width="180">
            <template #default="{ row }"><el-input v-model="row.requirement" size="small" /></template>
          </el-table-column>
          <el-table-column label="缴费解锁" width="80">
            <template #default="{ row }">
              <el-switch v-model="row.payToApply" :active-value="1" :inactive-value="0" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="64" fixed="right">
            <template #default="{ $index }">
              <el-button link type="danger" size="small" @click="batchRows.splice($index, 1)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="batch-bar">
          <el-button size="small" @click="addBatchRow">手动加一行</el-button>
          <span class="muted">共 {{ batchRows.length }} 条待发（单次上限 50）</span>
        </div>
      </div>

      <!-- 第三步：结果 -->
      <div v-show="batchStep === 2">
        <el-result :icon="batchResult.fail ? 'warning' : 'success'"
          :title="`成功发布 ${batchResult.success || 0} 条${batchResult.fail ? '，失败 ' + batchResult.fail + ' 条' : ''}`"
          sub-title="发布成功的单子教师端立即可见">
          <template #extra>
            <div v-if="(batchResult.jobs || []).length" class="batch-done">
              <el-tag v-for="j in batchResult.jobs" :key="j.id" type="success" effect="plain" size="small">
                {{ j.jobNo }} · {{ j.subject }}
              </el-tag>
            </div>
            <el-table v-if="(batchResult.errors || []).length" :data="batchResult.errors" size="small" border style="margin-top:10px">
              <el-table-column label="第几条" width="80">
                <template #default="{ row }">{{ (row.index ?? 0) + 1 }}</template>
              </el-table-column>
              <el-table-column prop="subject" label="科目" width="100" />
              <el-table-column prop="city" label="城市" width="100" />
              <el-table-column prop="error" label="失败原因" min-width="220" show-overflow-tooltip />
            </el-table>
          </template>
        </el-result>
      </div>

      <template #footer>
        <el-button @click="batchVisible = false">关闭</el-button>
        <el-button v-if="batchStep === 1" @click="batchStep = 0">上一步</el-button>
        <el-button v-if="batchStep === 0" type="primary" :loading="batchParsing" @click="doParseBatch">
          {{ batchParsing ? '解析中…' : '开始拆分' }}
        </el-button>
        <el-button v-else-if="batchStep === 1" type="success" :loading="batchPublishing" @click="doBatchPublish">
          确认无误，批量发布
        </el-button>
        <el-button v-else type="primary" @click="finishBatch">完成</el-button>
      </template>
    </el-dialog>

    <!-- ═══════════ 需求 8：科目用例模板编辑 ═══════════ -->
    <el-dialog v-model="tplFormVisible" :title="tplForm.id ? '编辑科目模板' : '新增科目模板'"
      :width="isMobile ? '94%' : '640px'">
      <el-form :model="tplForm" label-width="90px" label-position="left">
        <div class="form-row">
          <el-form-item label="科目" required>
            <el-input v-model="tplForm.subject" placeholder="如：数学" maxlength="50" />
          </el-form-item>
          <el-form-item label="模板名称" required>
            <el-input v-model="tplForm.name" placeholder="如：初中数学·基础补差" maxlength="100" />
          </el-form-item>
        </div>
        <el-form-item label="适用类型">
          <el-radio-group v-model="tplForm.jobType">
            <el-radio :value="1">家教单</el-radio>
            <el-radio :value="2">同行合作单</el-radio>
          </el-radio-group>
        </el-form-item>
        <div class="form-row">
          <el-form-item label="省份"><el-input v-model="tplForm.province" maxlength="50" /></el-form-item>
          <el-form-item label="城市"><el-input v-model="tplForm.city" maxlength="50" /></el-form-item>
        </div>
        <el-form-item label="详细地址"><el-input v-model="tplForm.address" maxlength="200" /></el-form-item>
        <div class="form-row">
          <el-form-item label="年级"><el-input v-model="tplForm.grade" maxlength="50" /></el-form-item>
          <el-form-item label="时薪(元/h)"><el-input v-model="tplForm.salary" type="number" /></el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="学生性别">
            <el-select v-model="tplForm.studentGender" placeholder="不限" clearable style="width: 100%">
              <el-option label="男孩" value="男孩" /><el-option label="女孩" value="女孩" />
            </el-select>
          </el-form-item>
          <el-form-item label="要求老师">
            <el-select v-model="tplForm.expectGender" placeholder="不限" clearable style="width: 100%">
              <el-option label="男老师" value="男" /><el-option label="女老师" value="女" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="上课频次"><el-input v-model="tplForm.frequency" placeholder="如：每周1次，每次2小时" maxlength="100" /></el-form-item>
        <el-form-item label="学生情况"><el-input v-model="tplForm.situation" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item label="要求"><el-input v-model="tplForm.requirement" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item label="缴费解锁">
          <el-switch v-model="tplForm.payToApply" :active-value="1" :inactive-value="0" active-text="需缴费后才能投递" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tplFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="tplSaving" @click="submitTplForm">保存模板</el-button>
      </template>
    </el-dialog>

    <!-- ═══════════ Tab：App 版本 / wgt 热更新（客户端启动自动检查并静默更新） ═══════════ -->
    <div v-if="currentTab === 'appversion'">
      <div class="card">
        <div class="card-title">发布 App 新版本（wgt 热更新）</div>
        <div class="muted" style="line-height:1.8;margin-bottom:16px;padding:12px 14px;background:rgba(64,158,255,0.08);border-radius:8px">
          <b>发版流程：</b>HBuilderX 打开 app-android →「发行 → 原生App-制作移动App资源升级包(wgt)」→ 下方选择该 .wgt 上传 →
          填版本号（code 必须比线上大）→ 点发布。用户下次打开 App 会<b>自动静默更新，无需重装、无需再微信发安装包</b>。<br>
          <b>注意：</b>wgt 只能更新前端页面/JS/CSS；若改了原生权限、图标、模块（manifest 的 app-plus 段），必须重新云打包 APK，并在下方「APK 安装包」上传该 APK——发布后官网登录页会自动出现「下载安卓 App」按钮，把网址发给别人即可，不用再微信传文件。
        </div>
        <el-form :model="verForm" label-width="110px" label-position="left">
          <el-form-item label="平台">
            <el-radio-group v-model="verForm.platform">
              <el-radio value="android">Android</el-radio>
              <el-radio value="ios">iOS</el-radio>
            </el-radio-group>
            <span v-if="latestVersion" class="muted" style="margin-left:14px">
              线上最新：{{ latestVersion.versionName || '-' }}（code={{ latestVersion.versionCode }}）
            </span>
          </el-form-item>
          <el-form-item label="wgt 升级包" required>
            <el-upload action="#" :auto-upload="false" :show-file-list="false" accept=".wgt" :on-change="onWgtFileChange">
              <el-button size="small">选择 .wgt 文件</el-button>
            </el-upload>
            <span v-if="verUploading" class="muted" style="margin-left:10px">上传中…</span>
            <span v-else-if="verForm.wgtUrl" style="margin-left:10px;color:var(--positive)">已上传 {{ shortUrl(verForm.wgtUrl) }}</span>
            <span v-else class="muted" style="margin-left:10px">未上传</span>
          </el-form-item>
          <div class="form-row">
            <el-form-item label="版本名">
              <el-input v-model="verForm.versionName" placeholder="如 1.0.1" style="width:170px" />
            </el-form-item>
            <el-form-item label="版本号 code" required>
              <el-input v-model.number="verForm.versionCode" type="number" placeholder="整数，须大于线上" style="width:170px" />
            </el-form-item>
          </div>
          <el-form-item label="强制更新">
            <el-switch v-model="verForm.forceUpdate" :active-value="1" :inactive-value="0" active-text="强制：用户必须更新才能继续使用" />
          </el-form-item>
          <el-form-item label="更新说明">
            <el-input v-model="verForm.changelog" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="本次更新内容，会展示给用户" />
          </el-form-item>
          <el-form-item label="APK 安装包">
            <el-upload action="#" :auto-upload="false" :show-file-list="false" accept=".apk" :on-change="onApkFileChange">
              <el-button size="small">选择 .apk 文件</el-button>
            </el-upload>
            <span v-if="verApkUploading" class="muted" style="margin-left:10px">上传中…（安装包较大，请稍候）</span>
            <span v-else-if="verForm.apkUrl" style="margin-left:10px;color:var(--positive)">已上传 {{ shortUrl(verForm.apkUrl) }}</span>
            <span v-else class="muted" style="margin-left:10px">未上传（选填）</span>
          </el-form-item>
          <el-form-item label="APK 地址">
            <el-input v-model="verForm.apkUrl" placeholder="上传后自动填入；也可手动粘贴 OSS 上的 APK 直链" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="verPublishing" @click="publishVersion">发布此版本</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="card">
        <div class="card-title">版本历史
          <el-button link type="primary" size="small" @click="loadVersions">刷新</el-button>
        </div>
        <el-table :data="versionList" v-loading="verLoading" empty-text="还没有发布过版本">
          <el-table-column prop="versionName" label="版本名" width="100" />
          <el-table-column prop="versionCode" label="code" width="80" />
          <el-table-column prop="platform" label="平台" width="90" />
          <el-table-column label="强制" width="76">
            <template #default="{ row }">
              <el-tag :type="row.forceUpdate === 1 ? 'danger' : 'info'" size="small">{{ row.forceUpdate === 1 ? '强制' : '可选' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="更新包" width="110">
            <template #default="{ row }">
              <el-link v-if="row.wgtUrl" type="primary" :href="row.wgtUrl" target="_blank" :underline="false">wgt</el-link>
              <el-link v-if="row.apkUrl" type="warning" :href="row.apkUrl" target="_blank" :underline="false" style="margin-left:8px">apk</el-link>
              <span v-if="!row.wgtUrl && !row.apkUrl" class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="changelog" label="更新说明" min-width="200" show-overflow-tooltip />
          <el-table-column label="发布时间" width="150">
            <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button link type="danger" size="small" @click="onDeleteVersion(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { User, Warning, Promotion, Document, Clock, CreditCard } from '@element-plus/icons-vue';
import KpiCard from '../components/ui/KpiCard.vue';
import {
  adminUsers, adminResetPassword, adminSetUserStatus, adminUpdateRole, adminUnbind,
  adminUpdatePassword, adminDeleteUser,
  adminDashboard,
  adminTutorJobs, adminPublishTutorJob, adminUpdateTutorJob, adminCloseTutorJob,
  adminReopenTutorJob, adminTutorJobApplications, adminReviewTutorJobApplication,
  adminDeleteTutorJob,
  adminTutorJobDetail,
  adminParentDemands, adminParentDemandDetail, adminPublishParentDemand, adminConfirmDeleteDemand,
  adminTutorTodo,
  adminGetTutorConfig, adminSaveTutorConfig,
  adminMarkPaid, adminRefundPaid, adminJobPayments, upload,
  adminUserInvites, adminParseTutorJobs, adminBatchPublishTutorJobs,
  adminJobTemplates, adminSaveJobTemplate, adminDeleteJobTemplate,
  adminRefundTasks, adminSettleRefundTask, adminPaymentLogs,
  uploadWgt, adminPublishAppVersion, adminAppVersions, adminDeleteAppVersion
} from '../api';

const route = useRoute();
const router = useRouter();
const currentTab = computed(() => route.meta.tab || 'users');
const isMobile = computed(() => window.innerWidth < 768);
const tabs = [
  { key: 'users', icon: '', label: '用户管理' },
  { key: 'jobs', icon: '', label: '家教信息' },
  { key: 'partner', icon: '', label: '同行合作单' },
  { key: 'demands', icon: '', label: '家长需求' },
  { key: 'refunds', icon: '', label: '退费工单' },
  { key: 'payments', icon: '', label: '缴费流水' },
  { key: 'templates', icon: '', label: '科目模板' },
  { key: 'config', icon: '', label: '接单配置' },
  { key: 'appversion', icon: '', label: 'App版本' },
  { key: 'dashboard', icon: '', label: '数据看板' }
];
const currentTabLabel = computed(() => (tabs.find((t) => t.key === currentTab.value) || {}).label || '管理后台');

// 需求 6：同行合作单与家教信息共用一套 UI，只靠 jobType 分开取数
const isPartnerTab = computed(() => currentTab.value === 'partner');
const isJobsTab = computed(() => currentTab.value === 'jobs' || currentTab.value === 'partner');

// 投递试课状态（简历通过后：试课中 → 订单完成 / 试课失败待退费 → 已退费）
const TRIAL_TEXT = { 0: '试课中', 1: '订单已完成', 2: '试课失败待退费', 3: '已退费' };
const TRIAL_TAG = { 0: 'warning', 1: 'success', 2: 'danger', 3: 'info' };

// ==================== 家教业务待办统计（Tab 徽章 + 概览条 + 数据看板共用） ====================
const todo = ref({});

async function loadTodo() {
  try {
    todo.value = await adminTutorTodo();
  } catch (e) { /* 拦截器已提示 */ }
}

/** Tab 右上角红点：只提示“需要管理员动手”的环节 */
function tabBadge(key) {
  const t = todo.value || {};
  let n = 0;
  if (key === 'jobs' || key === 'partner') n = t.pendingApplications || 0;
  else if (key === 'demands') n = (t.pendingDemands || 0) + (t.deleteRequests || 0);
  // 退费搜到独立工单后，待退费红点跟着「退费工单」Tab 走
  else if (key === 'refunds') n = t.trialFailed || 0;
  if (!n) return '';
  return n > 99 ? '99+' : String(n);
}

/** 家教单状态：已成交的单优先展示试课阶段，而不是笼统的“已关闭” */
function jobStatusText(row) {
  if (!row) return '—';
  if (row.status === 1) return '开放中';
  return row.trialStatus != null ? (TRIAL_TEXT[row.trialStatus] || '已关闭') : '已关闭';
}
function jobStatusTag(row) {
  if (!row) return 'info';
  if (row.status === 1) return 'success';
  return row.trialStatus != null ? (TRIAL_TAG[row.trialStatus] || 'info') : 'info';
}

// ==================== 用户管理 ====================
const userQuery = ref({ keyword: '', role: null, status: null, page: 1, size: 10 });
const userList = ref([]);
const userTotal = ref(0);
const userLoading = ref(false);

async function loadUsers(page) {
  if (page) userQuery.value.page = page;
  userLoading.value = true;
  try {
    const data = await adminUsers(userQuery.value);
    userList.value = data.list || [];
    userTotal.value = data.total || 0;
  } catch (e) { /* 拦截器已提示 */ } finally {
    userLoading.value = false;
  }
}

async function onResetPassword(row) {
  try {
    await ElMessageBox.confirm(
      `确定将「${row.realName || row.username}」的密码重置为初始密码吗？`, '重置密码',
      { confirmButtonText: '重置', cancelButtonText: '取消', type: 'warning' });
    const newPwd = await adminResetPassword(row.id);
    ElMessageBox.alert(
      `新密码：${newPwd}（已通过站内通知告知该用户，请其尽快登录并修改）`, '重置成功',
      { confirmButtonText: '知道了' });
    loadUsers();
  } catch (e) { /* 取消或失败 */ }
}

async function onToggleStatus(row) {
  const banning = row.status !== 0;
  try {
    await ElMessageBox.confirm(
      banning
        ? `确定封禁「${row.realName || row.username}」吗？封禁后该账号无法登录（已登录的凭证最长 2 小时后失效）。`
        : `确定解封「${row.realName || row.username}」吗？`,
      banning ? '封禁账号' : '解封账号',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: banning ? 'warning' : 'info' });
    await adminSetUserStatus(row.id, banning ? 0 : 1);
    ElMessage.success(banning ? '已封禁' : '已解封');
    loadUsers();
  } catch (e) { /* 取消或失败 */ }
}

async function onUserCommand(cmd, row) {
  const name = row.realName || row.username;
  try {
    if (cmd === 'role') {
      const { value } = await ElMessageBox.prompt(
        `将「${name}」的角色调整为（teacher / student / parent）：`, '调整角色',
        { confirmButtonText: '确定', cancelButtonText: '取消', inputValue: row.role,
          inputValidator: v => ['teacher', 'student', 'parent'].includes(v) || '只能填 teacher / student / parent' });
      await adminUpdateRole(row.id, value);
      ElMessage.success('角色已调整，该用户重新登录后生效');
      loadUsers();
    } else if (cmd === 'unbindParent') {
      await ElMessageBox.confirm(`确定解除「${name}」与家长的绑定吗？`, '解除绑定', { type: 'warning' });
      await adminUnbind(row.id, 'parent');
      ElMessage.success('已解除家长绑定');
      loadUsers();
    } else if (cmd === 'unbindTeacher') {
      await ElMessageBox.confirm(`确定解除「${name}」与老师的绑定吗？`, '解除绑定', { type: 'warning' });
      await adminUnbind(row.id, 'teacher');
      ElMessage.success('已解除老师绑定');
      loadUsers();
    } else if (cmd === 'password') {
      const { value } = await ElMessageBox.prompt(
        `为「${name}」设置新密码（至少 6 位）：`, '修改密码',
        { confirmButtonText: '确定', cancelButtonText: '取消', inputType: 'password',
          inputPlaceholder: '请输入新密码',
          inputValidator: v => (v && v.length >= 6) || '新密码至少 6 位' });
      await adminUpdatePassword(row.id, value);
      ElMessage.success('密码已修改，已通过站内通知告知该用户');
      loadUsers();
    } else if (cmd === 'delete') {
      await ElMessageBox.confirm(
        `确定删除用户「${name}」吗？此操作不可恢复，将同时清除其绑定关系与登录态！`, '删除用户',
        { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' });
      await adminDeleteUser(row.id);
      ElMessage.success('用户已删除');
      loadUsers();
    }
  } catch (e) { /* 取消或失败 */ }
}

// ==================== 家教信息 ====================
const jobQuery = ref({ keyword: '', view: '', page: 1, size: 10 });
const jobList = ref([]);
const jobTotal = ref(0);
const jobLoading = ref(false);

// 状态筛选有两个入口：状态下拉（开放中/已关闭/试课中）和上面的概览条
// （还多出待审核投递、试课失败待退费、已完成三类）。下拉里没有的三类单独存 todoFilter，
// 两者取其一发给后端；后端 view 优先级高于 status，认不出的值一律忽略。
const SELECT_VIEWS = ['open', 'closed', 'trial'];
const todoFilter = ref('');
const effectiveView = computed(() => todoFilter.value || jobQuery.value.view || '');

function onViewSelect() {
  todoFilter.value = '';
  loadJobs(1);
}

function clearView() {
  jobQuery.value.view = '';
  todoFilter.value = '';
  loadJobs(1);
}

/** 概览条点选：下拉能表达的三态顺手回填到下拉框，其余的只高亮概览项；再点一次取消筛选 */
function toggleView(v) {
  if (effectiveView.value === v) { clearView(); return; }
  if (SELECT_VIEWS.includes(v)) {
    jobQuery.value.view = v;
    todoFilter.value = '';
  } else {
    todoFilter.value = v;
    jobQuery.value.view = '';
  }
  loadJobs(1);
}

async function loadJobs(page) {
  if (page) jobQuery.value.page = page;
  jobLoading.value = true;
  try {
    const params = {
      keyword: jobQuery.value.keyword,
      page: jobQuery.value.page,
      size: jobQuery.value.size,
      jobType: isPartnerTab.value ? 2 : 1,
    };
    if (effectiveView.value) params.view = effectiveView.value;
    const data = await adminTutorJobs(params);
    jobList.value = data.list || [];
    jobTotal.value = data.total || 0;
    loadTodo();
  } catch (e) { /* 拦截器已提示 */ } finally {
    jobLoading.value = false;
  }
}

// 发布/编辑表单（发布时空 id）
const jobFormVisible = ref(false);
const jobFormSubmitting = ref(false);
const jobForm = reactive({});

function openJobForm(row, presetType) {
  Object.keys(jobForm).forEach(k => delete jobForm[k]);
  jobForm.payToApply = true; // 默认收费：教师需缴费解锁后才能投递
  jobForm.jobType = presetType || (isPartnerTab.value ? 2 : 1);
  jobTemplateId.value = null;
  if (row) {
    ['id', 'jobNo', 'subject', 'province', 'city', 'address', 'grade', 'studentGender',
     'expectGender', 'frequency', 'salary', 'situation', 'requirement'].forEach(k => { jobForm[k] = row[k] ?? null; });
    jobForm.payToApply = row.payToApply === 1;
    if (row.jobType) jobForm.jobType = row.jobType;
  } else {
    loadTemplateOptions(); // 需求 8：只有新建时才需要模板下拉
  }
  jobFormVisible.value = true;
}

async function submitJobForm() {
  if (!jobForm.subject?.trim()) { ElMessage.warning('科目不能为空'); return; }
  jobFormSubmitting.value = true;
  try {
    const payload = { ...jobForm, payToApply: jobForm.payToApply ? 1 : 0 };
    if (jobForm.id) {
      await adminUpdateTutorJob(jobForm.id, payload);
      ElMessage.success('已保存');
    } else {
      await adminPublishTutorJob(payload);
      ElMessage.success('发布成功，教师端立即可见');
    }
    jobFormVisible.value = false;
    loadJobs();
  } catch (e) { /* 拦截器已提示 */ } finally {
    jobFormSubmitting.value = false;
  }
}

async function onCloseJob(row) {
  try {
    await ElMessageBox.confirm(
      `确定下架家教单「${row.jobNo}（${row.subject}）」吗？下架后教师端不再展示。`,
      '下架家教单',
      { confirmButtonText: '下架', cancelButtonText: '取消', type: 'warning' });
    await adminCloseTutorJob(row.id);
    ElMessage.success('已下架');
    loadJobs();
  } catch (e) { /* 取消或失败 */ }
}

async function onReopenJob(row) {
  try {
    await ElMessageBox.confirm(
      `确定重新上架家教单「${row.jobNo}（${row.subject}）」吗？旧的通过记录将被清空，单子重新接受投递。`,
      '重新上架',
      { confirmButtonText: '上架', cancelButtonText: '取消', type: 'info' });
    await adminReopenTutorJob(row.id);
    ElMessage.success('已重新上架');
    loadJobs();
  } catch (e) { /* 取消或失败 */ }
}

// 彻底删除家教单（连带投递与缴费记录，不可恢复；关联的家长需求回退为待发布）
async function onDeleteJob(row) {
  const appCount = row.applyCount || 0;
  try {
    await ElMessageBox.confirm(
      `确定删除家教单「${row.jobNo}（${row.subject}）」吗？\n将连带删除 ${appCount} 条投递记录与全部缴费记录，不可恢复。`
      + (row.parentDemandId ? '\n关联的家长需求不会被删，会回退为“待发布”可重新发布。'
        : '\n该单为管理员手工发布，删除后不会保留任何记录。'),
      '删除家教单',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' });
    await adminDeleteTutorJob(row.id);
    ElMessage.success('已删除家教单及其投递、缴费记录');
    loadJobs();
  } catch (e) { /* 取消或失败 */ }
}

// 家教单详情弹窗（完整信息 + 家长联系方式）
const jobDetailVisible = ref(false);
const jobDetailLoading = ref(false);
const jobDetail = ref({});

async function openJobDetail(row) {
  jobDetail.value = { ...row };
  jobDetailVisible.value = true;
  jobDetailLoading.value = true;
  try {
    jobDetail.value = await adminTutorJobDetail(row.id);
  } catch (e) { /* 拦截器已提示 */ } finally {
    jobDetailLoading.value = false;
  }
}

// 投递列表 + 审核
const appsVisible = ref(false);
const appLoading = ref(false);
const appList = ref([]);
const appsJob = ref(null);
const appParentContact = ref(null);
// 已结算（试课失败待退费 / 已退费）的投递数：这类记录不计入“投递数”，弹窗里要单独说明
const settledCount = computed(() =>
  appList.value.filter(r => r.trialStatus === 2 || r.trialStatus === 3).length);

async function openApplications(row) {
  appsJob.value = row;
  appsVisible.value = true;
  appLoading.value = true;
  try {
    const data = await adminTutorJobApplications(row.id, { page: 1, size: 100 });
    appList.value = data.list || [];
    appParentContact.value = data.parentContact || null;
  } catch (e) { /* 拦截器已提示 */ } finally {
    appLoading.value = false;
  }
  loadPayList(row.id);
}

// 本单缴费记录（含未投递但已缴费解锁的老师）
const payList = ref([]);
async function loadPayList(jobId) {
  try {
    const data = await adminJobPayments(jobId);
    payList.value = data.list || [];
  } catch (e) { /* 拦截器已提示 */ }
}

// ==================== 开通缴费解锁（不依赖投递记录） ====================
const payGrantVisible = ref(false);
const payGrantKeyword = ref('');
const payGrantOptions = ref([]);
const payGrantSearching = ref(false);
const payGrantTeacherId = ref(null);
const payGrantAmount = ref('');
const payGrantSubmitting = ref(false);

function openPayGrant() {
  payGrantKeyword.value = '';
  payGrantOptions.value = [];
  payGrantTeacherId.value = null;
  payGrantAmount.value = '';
  payGrantVisible.value = true;
}

async function searchPayTeachers() {
  const kw = payGrantKeyword.value.trim();
  if (!kw) { ElMessage.warning('请输入要搜索的账号 / 姓名 / 手机号'); return; }
  payGrantSearching.value = true;
  try {
    const data = await adminUsers({ keyword: kw, role: 'teacher', page: 1, size: 10 });
    payGrantOptions.value = data.list || [];
    if (!payGrantOptions.value.length) ElMessage.info('未搜到匹配的老师');
  } catch (e) { /* 拦截器已提示 */ } finally {
    payGrantSearching.value = false;
  }
}

async function submitPayGrant() {
  if (!payGrantTeacherId.value) { ElMessage.warning('请先搜索并选中一位老师'); return; }
  if (!/^\d+(\.\d{1,2})?$/.test(payGrantAmount.value.trim())) {
    ElMessage.warning('请输入正确的金额（如：50 或 49.9）');
    return;
  }
  payGrantSubmitting.value = true;
  try {
    await adminMarkPaid(appsJob.value.id, payGrantTeacherId.value, { amount: payGrantAmount.value.trim() });
    ElMessage.success(`已标记缴费 ${payGrantAmount.value} 元，该老师已解锁本单投递（会收到站内通知）`);
    payGrantVisible.value = false;
    loadPayList(appsJob.value.id);
    // 已投递的老师卡片上也要显示最新金额
    const data = await adminTutorJobApplications(appsJob.value.id, { page: 1, size: 100 });
    appList.value = data.list || [];
    loadTodo();
  } catch (e) { /* 拦截器已提示 */ } finally {
    payGrantSubmitting.value = false;
  }
}

// 缴费记录行退费（未投递的老师在这里退）
async function onRefundPayRow(row) {
  const teacher = row.teacherName || `老师#${row.teacherId}`;
  try {
    await ElMessageBox.confirm(
      `确定向「${teacher}」退回已缴的 ${row.amount} 元吗？退费后已缴金额将清零。`,
      '退费并清零',
      { confirmButtonText: '确认退费', cancelButtonText: '取消', type: 'warning' });
    await adminRefundPaid(appsJob.value.id, row.teacherId, { note: '未接单退费' });
    ElMessage.success('已退费，已缴金额已清零');
    loadPayList(appsJob.value.id);
    const data = await adminTutorJobApplications(appsJob.value.id, { page: 1, size: 100 });
    appList.value = data.list || [];
    loadTodo();
  } catch (e) { /* 取消或失败 */ }
}

async function onReview(row, approved) {
  const teacher = row.profile?.name || row.teacherName || `老师#${row.teacherId}`;
  try {
    await ElMessageBox.confirm(
      approved
        ? `确定通过「${teacher}」的投递吗？通过后该家教单将立即关闭，不再接受投递。`
        : `确定将「${teacher}」的投递标记为不通过吗？`,
      approved ? '通过投递' : '不通过',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: approved ? 'success' : 'warning' });
    await adminReviewTutorJobApplication(row.id, approved);
    ElMessage.success(approved ? '已通过，该单已关闭' : '已标记不通过');
    // 刷新投递列表和单子列表（单子状态可能变了）
    const job = appList.value.length || appsJob.value ? appsJob.value : null;
    if (job) {
      const data = await adminTutorJobApplications(job.id, { page: 1, size: 100 });
      appList.value = data.list || [];
      job.status = approved ? 0 : job.status;
      if (approved) job.approvedTeacherId = row.teacherId;
    }
    loadJobs();
  } catch (e) { /* 取消或失败 */ }
}

// 标记教师已缴费（解锁该单投递权）
async function onMarkPaid(row) {
  const teacher = row.profile?.name || row.teacherName || `老师#${row.teacherId}`;
  try {
    const { value } = await ElMessageBox.prompt(
      `确认「${teacher}」已通过收款码缴费。请输入实缴金额（元）：`, '标记缴费',
      { confirmButtonText: '确认缴费', cancelButtonText: '取消',
        inputPattern: /^\d+(\.\d{1,2})?$/, inputErrorMessage: '请输入正确的金额（如：50 或 49.9）' });
    await adminMarkPaid(appsJob.value.id, row.teacherId, { amount: value });
    ElMessage.success(`已标记缴费 ${value} 元，该教师已解锁本单投递`);
    const data = await adminTutorJobApplications(appsJob.value.id, { page: 1, size: 100 });
    appList.value = data.list || [];
    loadTodo();
  } catch (e) { /* 取消或失败 */ }
}

// 退费（金额清零）—— 试课失败待退费的记录走同一个入口，文案与备注区分开
async function onRefund(row) {
  const teacher = row.profile?.name || row.teacherName || `老师#${row.teacherId}`;
  const trialFailed = row.trialStatus === 2;
  const hasMoney = Number(row.paidAmount) > 0;
  try {
    await ElMessageBox.confirm(
      trialFailed
        ? (hasMoney
            ? `「${teacher}」反馈试课失败，确定退回其已缴的 ${row.paidAmount} 元吗？退费后该投递置为“已退费”，老师会收到通知。`
            : `「${teacher}」反馈试课失败，该单无需缴费（没有退款金额）。确认后即把这条记录结算为“已处理”，老师会收到通知，试课待退费提醒随之清除。`)
        : `确定向「${teacher}」退回已缴的 ${row.paidAmount} 元吗？退费后已缴金额将清零（未接单时使用）。`,
      trialFailed ? (hasMoney ? '试课失败退费' : '标记试课失败已处理') : '退费并清零',
      { confirmButtonText: trialFailed && !hasMoney ? '确认已处理' : '确认退费',
        cancelButtonText: '取消', type: 'warning' });
    await adminRefundPaid(appsJob.value.id, row.teacherId, { note: trialFailed ? '试课失败退费' : '未接单退费' });
    ElMessage.success(trialFailed
      ? (hasMoney ? '已完成试课退费，老师会收到退费通知' : '已标记为已处理，老师会收到通知')
      : '已退费，已缴金额已清零');
    const data = await adminTutorJobApplications(appsJob.value.id, { page: 1, size: 100 });
    appList.value = data.list || [];
    loadPayList(appsJob.value.id);
    loadJobs();
  } catch (e) { /* 取消或失败 */ }
}

// ==================== 需求 5：老师邀请名单 ====================
const invitesVisible = ref(false);
const invitesLoading = ref(false);
const invitesOwner = ref('');
const invitesCode = ref('');
const invitesList = ref([]);

async function openInvites(row) {
  invitesOwner.value = row.realName || row.username;
  invitesCode.value = row.inviteCode || '';
  invitesList.value = [];
  invitesVisible.value = true;
  invitesLoading.value = true;
  try {
    invitesList.value = (await adminUserInvites(row.id)) || [];
  } catch (e) { /* 拦截器已提示 */ } finally {
    invitesLoading.value = false;
  }
}

// ==================== 需求 1/2：退费工单 ====================
const refundQuery = ref({ keyword: '', status: null, page: 1, size: 10 });
const refundList = ref([]);
const refundTotal = ref(0);
const refundPending = ref(0);
const refundLoading = ref(false);
const refundLoaded = ref(false);

async function loadRefunds(page) {
  if (page) refundQuery.value.page = page;
  refundLoading.value = true;
  try {
    const data = await adminRefundTasks(refundQuery.value);
    refundList.value = data.list || [];
    refundTotal.value = data.total || 0;
    refundPending.value = data.pending || 0;
    refundLoaded.value = true;
    loadTodo();
  } catch (e) { /* 拦截器已提示 */ } finally {
    refundLoading.value = false;
  }
}

function filterRefunds(status) {
  refundQuery.value.status = refundQuery.value.status === status ? null : status;
  loadRefunds(1);
}

/** 待退费的行标黄提醒 */
function refundRowClass({ row }) {
  return row.status === 0 ? 'row-pending' : '';
}

async function onSettleRefund(row) {
  const teacher = row.teacherName || `老师#${row.teacherId}`;
  const hasMoney = Number(row.amount) > 0;
  try {
    const { value } = await ElMessageBox.prompt(
      hasMoney
        ? `确认已向「${teacher}」退回 ${row.amount} 元？办结后缴费金额清零，退费时间与流水会永久留档，老师会收到通知。`
        : `「${teacher}」这单没有缴费金额，确认后即把工单标记为已处理，待退费提醒随之清除。`,
      hasMoney ? '办理退费' : '标记已处理',
      { confirmButtonText: hasMoney ? '确认已退费' : '确认已处理', cancelButtonText: '取消',
        inputPlaceholder: '备注（选填），如：微信转账已到账', inputValue: '', type: 'warning' });
    await adminSettleRefundTask(row.id, value || (hasMoney ? '管理端办理退费' : '免费单，标记已处理'));
    ElMessage.success(hasMoney ? '退费已办结，流水已记录' : '已标记为已处理');
    loadRefunds();
  } catch (e) { /* 取消或失败 */ }
}

/** 从退费工单跳到对应家教单 */
function gotoJobByNo(jobNo) {
  if (!jobNo) return;
  jobQuery.value.keyword = jobNo;
  jobQuery.value.view = '';
  todoFilter.value = '';
  jobQuery.value.page = 1;
  router.push('/admin/jobs');
  loadJobs(1);
}

/** 从家教单红标跳到退费工单，并按单号过滤 */
function gotoRefunds(jobNo) {
  refundQuery.value.keyword = jobNo || '';
  refundQuery.value.status = null;
  refundQuery.value.page = 1;
  router.push('/admin/refunds');
  loadRefunds(1);
}

// ==================== 需求 2：缴费 / 退费流水 ====================
const payLogQuery = ref({ jobId: '', type: null, page: 1, size: 10 });
const payLogList = ref([]);
const payLogTotal = ref(0);
const payLogSum = ref({});
const payLogLoading = ref(false);
const payLogLoaded = ref(false);

async function loadPayLogs(page) {
  if (page) payLogQuery.value.page = page;
  payLogLoading.value = true;
  try {
    const params = { page: payLogQuery.value.page, size: payLogQuery.value.size };
    if (payLogQuery.value.jobId) params.jobId = payLogQuery.value.jobId;
    if (payLogQuery.value.type) params.type = payLogQuery.value.type;
    const data = await adminPaymentLogs(params);
    payLogList.value = data.list || [];
    payLogTotal.value = data.total || 0;
    payLogSum.value = { paidTotal: data.paidTotal, refundTotal: data.refundTotal };
    payLogLoaded.value = true;
  } catch (e) { /* 拦截器已提示 */ } finally {
    payLogLoading.value = false;
  }
}

// ==================== 需求 8：科目用例模板 ====================
const templateList = ref([]);
const templateLoading = ref(false);
const templateLoaded = ref(false);
const tplFormVisible = ref(false);
const tplSaving = ref(false);
const EMPTY_TPL = {
  id: null, subject: '', name: '', jobType: 1, grade: '', province: '', city: '', address: '',
  studentGender: '', expectGender: '', frequency: '', salary: '', situation: '', requirement: '',
  payToApply: 1,
};
const tplForm = reactive({ ...EMPTY_TPL });

async function loadTemplates() {
  templateLoading.value = true;
  try {
    const data = await adminJobTemplates({});
    templateList.value = data.list || [];
    templateLoaded.value = true;
  } catch (e) { /* 拦截器已提示 */ } finally {
    templateLoading.value = false;
  }
}

function openTplForm(row) {
  Object.assign(tplForm, EMPTY_TPL, row || {});
  if (!row) tplForm.jobType = isPartnerTab.value ? 2 : 1;
  tplFormVisible.value = true;
}

async function submitTplForm() {
  if (!tplForm.subject?.trim()) { ElMessage.warning('科目不能为空'); return; }
  if (!tplForm.name?.trim()) { ElMessage.warning('模板名称不能为空'); return; }
  tplSaving.value = true;
  try {
    await adminSaveJobTemplate({ ...tplForm });
    ElMessage.success('模板已保存');
    tplFormVisible.value = false;
    loadTemplates();
  } catch (e) { /* 拦截器已提示 */ } finally {
    tplSaving.value = false;
  }
}

async function onDeleteTpl(row) {
  try {
    await ElMessageBox.confirm(`确定删除科目「${row.subject}」的模板「${row.name}」吗？`, '删除模板',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' });
    await adminDeleteJobTemplate(row.id);
    ElMessage.success('已删除');
    loadTemplates();
  } catch (e) { /* 取消或失败 */ }
}

/** 直接用这个模板去发布：跳到对应 Tab 并打开预填好的发布弹窗 */
function useTplNow(row) {
  router.push(row.jobType === 2 ? '/admin/partner' : '/admin/jobs');
  openJobForm(null, row.jobType);
  fillFormFromTemplate(row);
}

// 发布弹窗里的模板下拉
const templateOptions = ref([]);
const jobTemplateId = ref(null);
const TPL_FIELDS = ['subject', 'grade', 'province', 'city', 'address', 'studentGender',
  'expectGender', 'frequency', 'salary', 'situation', 'requirement'];

async function loadTemplateOptions() {
  try {
    const data = await adminJobTemplates({});
    templateOptions.value = data.list || [];
  } catch (e) { /* 拦截器已提示 */ }
}

function fillFormFromTemplate(tpl) {
  TPL_FIELDS.forEach(k => { jobForm[k] = tpl[k] ?? null; });
  jobForm.payToApply = tpl.payToApply === 1;
  if (tpl.jobType) jobForm.jobType = tpl.jobType;
}

function applyTemplate(id) {
  if (!id) return;
  const tpl = templateOptions.value.find(t => t.id === id);
  if (!tpl) return;
  fillFormFromTemplate(tpl);
  ElMessage.success(`已套用「${tpl.subject} · ${tpl.name}」模板，可继续修改`);
}

// ==================== 需求 7：AI 批量发布 ====================
const batchVisible = ref(false);
const batchStep = ref(0);
const batchText = ref('');
const batchJobType = ref(1);
const batchRows = ref([]);
const batchParsing = ref(false);
const batchPublishing = ref(false);
const batchResult = ref({});
const MAX_BATCH = 50;

function openBatch() {
  batchStep.value = 0;
  batchText.value = '';
  batchRows.value = [];
  batchResult.value = {};
  batchJobType.value = isPartnerTab.value ? 2 : 1;
  batchVisible.value = true;
}

function emptyBatchRow() {
  return {
    subject: '', grade: '', province: '', city: '', address: '', studentGender: '',
    expectGender: '', frequency: '', salary: '', situation: '', requirement: '',
    payToApply: 1, jobType: batchJobType.value,
  };
}

function addBatchRow() {
  batchRows.value.push(emptyBatchRow());
}

async function doParseBatch() {
  if (!batchText.value.trim()) { ElMessage.warning('请先粘贴需要解析的家教信息'); return; }
  batchParsing.value = true;
  try {
    const list = await adminParseTutorJobs({ text: batchText.value, jobType: batchJobType.value });
    batchRows.value = (list || []).map(j => ({
      ...emptyBatchRow(), ...j,
      jobType: batchJobType.value,
      payToApply: j.payToApply == null ? 1 : Number(j.payToApply),
    }));
    if (!batchRows.value.length) { ElMessage.warning('AI 没识别出内容，请换一种粘贴格式'); return; }
    batchStep.value = 1;
    ElMessage.success(`AI 拆出 ${batchRows.value.length} 条，请逐条核对后再发布`);
  } catch (e) { /* 拦截器已提示 */ } finally {
    batchParsing.value = false;
  }
}

async function doBatchPublish() {
  const rows = batchRows.value.filter(r => r.subject && String(r.subject).trim());
  if (!rows.length) { ElMessage.warning('没有可发布的条目（科目必填）'); return; }
  if (rows.length > MAX_BATCH) { ElMessage.warning(`单次最多发布 ${MAX_BATCH} 条，请分批处理`); return; }
  try {
    await ElMessageBox.confirm(
      `确认发布这 ${rows.length} 条${batchJobType.value === 2 ? '同行合作单' : '家教单'}吗？发布后教师端立即可见。`,
      '批量发布', { confirmButtonText: '确认发布', cancelButtonText: '再检查一下', type: 'warning' });
  } catch (e) { return; }
  batchPublishing.value = true;
  try {
    const payload = rows.map(r => ({ ...r, jobType: batchJobType.value,
      payToApply: Number(r.payToApply) === 0 ? 0 : 1 }));
    batchResult.value = await adminBatchPublishTutorJobs(payload);
    batchStep.value = 2;
    loadTodo();
  } catch (e) { /* 拦截器已提示 */ } finally {
    batchPublishing.value = false;
  }
}

function finishBatch() {
  batchVisible.value = false;
  loadJobs(1);
}

// ==================== 家长需求 ====================
const demandQuery = ref({ keyword: '', status: null, deleteRequested: null, page: 1, size: 10 });
const demandList = ref([]);
const demandTotal = ref(0);
const demandLoading = ref(false);
// “只看删除申请”勾选框：与 demandQuery.deleteRequested 双向同步
const onlyDeleteReq = computed({
  get: () => demandQuery.value.deleteRequested === 1,
  set: v => { demandQuery.value.deleteRequested = v ? 1 : null; }
});

async function loadDemands(page) {
  if (page) demandQuery.value.page = page;
  demandLoading.value = true;
  try {
    const data = await adminParentDemands({ ...demandQuery.value, status: demandQuery.value.status ?? -1 });
    demandList.value = data.records || [];
    demandTotal.value = data.total || 0;
    loadTodo();
  } catch (e) { /* 拦截器已提示 */ } finally {
    demandLoading.value = false;
  }
}

// 概览条点击即筛选（再点一次取消筛选）
function filterDemands(status) {
  const same = demandQuery.value.status === status && !demandQuery.value.deleteRequested;
  demandQuery.value.status = same || status < 0 ? null : status;
  demandQuery.value.deleteRequested = null;
  loadDemands(1);
}
function filterDemandsDelete() {
  const same = demandQuery.value.deleteRequested === 1;
  demandQuery.value.status = null;
  demandQuery.value.deleteRequested = same ? null : 1;
  loadDemands(1);
}

/** 申请删除的行标红置顶提醒，待发布的行标黄 */
function demandRowClass({ row }) {
  if (row.deleteRequested === 1) return 'row-delete-req';
  return row.status === 0 ? 'row-pending' : '';
}

// 需求详情弹窗（家长填写的完整信息 + 关联家教单）
const demandDetailVisible = ref(false);
const demandDetailLoading = ref(false);
const demandDetail = ref({});

async function openDemandDetail(row) {
  demandDetail.value = { ...row };
  demandDetailVisible.value = true;
  demandDetailLoading.value = true;
  try {
    demandDetail.value = await adminParentDemandDetail(row.id);
  } catch (e) { /* 拦截器已提示 */ } finally {
    demandDetailLoading.value = false;
  }
}

/** 从家长需求跳到关联家教单：切到「家教信息」Tab 并按单号搜索
 *  （列表行的单号在 row.jobNo，详情弹窗里在 row.job.jobNo） */
function gotoJob(row) {
  const jobNo = row.jobNo || row.job?.jobNo;
  if (!jobNo) return;
  jobQuery.value.keyword = jobNo;
  jobQuery.value.view = '';
  todoFilter.value = '';
  jobQuery.value.page = 1;
  router.push('/admin/jobs');
  loadJobs(1);
}

// 发布家长需求为家教单（表单预填家长填的内容）
const demandPublishVisible = ref(false);
const demandPublishing = ref(false);
const demandPublishForm = reactive({});
let demandPublishId = null;

function openDemandPublish(row) {
  Object.keys(demandPublishForm).forEach(k => delete demandPublishForm[k]);
  demandPublishId = row.id;
  demandPublishForm.contactName = row.contactName;
  demandPublishForm.phone = row.phone;
  demandPublishForm.wechat = row.wechat;
  demandPublishForm.subject = row.subject;
  demandPublishForm.grade = row.grade;
  demandPublishForm.studentGender = row.studentGender;
  demandPublishForm.expectGender = row.expectGender;
  demandPublishForm.salary = row.salary;
  demandPublishForm.situation = row.studentLevel;
  demandPublishForm.requirement = row.otherRequire;
  demandPublishForm.payToApply = true; // 默认收费：发布后教师需缴费解锁才能投递
  demandPublishVisible.value = true;
}

async function submitDemandPublish() {
  if (!demandPublishForm.subject?.trim()) { ElMessage.warning('科目不能为空'); return; }
  demandPublishing.value = true;
  try {
    const payload = { ...demandPublishForm, payToApply: demandPublishForm.payToApply ? 1 : 0 };
    const res = await adminPublishParentDemand(demandPublishId, payload);
    ElMessage.success(`已发布为家教单（单号 ${res?.jobNo || ''}），教师端立即可见`);
    demandPublishVisible.value = false;
    loadDemands();
  } catch (e) { /* 拦截器已提示 */ } finally {
    demandPublishing.value = false;
  }
}

// 确认删除家长需求单（连带删除关联家教单、投递、缴费记录）
async function onConfirmDeleteDemand(row) {
  const requested = row.deleteRequested === 1;
  try {
    await ElMessageBox.confirm(
      requested
        ? `家长「${row.contactName}」已申请删除该需求，确认后需求单${row.jobId ? '及关联家教单（含投递、缴费记录）' : ''}将被彻底删除，不可恢复。确定删除吗？`
        : `确定删除该家长需求吗？${row.jobId ? '关联家教单（含投递、缴费记录）将一并删除，' : ''}不可恢复。`,
      requested ? '确认家长删除申请' : '删除家长需求',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' });
    await adminConfirmDeleteDemand(row.id);
    ElMessage.success(requested ? '已确认删除，家长需求与关联订单已清除' : '已删除');
    loadDemands();
    loadTodo();
  } catch (e) { /* 取消或失败 */ }
}

// ==================== 接单配置（须知/联系方式/收款码） ====================
const tutorConfig = reactive({ noticeText: '', noticeImgs: [], phone: '', wechat: '', payQrUrl: '' });
const configLoading = ref(false);
const configSaving = ref(false);
const configLoaded = ref(false);

async function loadConfig() {
  configLoading.value = true;
  try {
    const data = await adminGetTutorConfig();
    Object.assign(tutorConfig, {
      noticeText: data.noticeText || '',
      noticeImgs: data.noticeImgs || [],
      phone: data.phone || '',
      wechat: data.wechat || '',
      payQrUrl: data.payQrUrl || '',
    });
    configLoaded.value = true;
  } catch (e) { /* 拦截器已提示 */ } finally {
    configLoading.value = false;
  }
}

async function saveConfig() {
  configSaving.value = true;
  try {
    await adminSaveTutorConfig({
      noticeText: tutorConfig.noticeText,
      noticeImgs: tutorConfig.noticeImgs,
      phone: tutorConfig.phone,
      wechat: tutorConfig.wechat,
      payQrUrl: tutorConfig.payQrUrl,
    });
    ElMessage.success('接单配置已保存，教师端立即生效');
  } catch (e) { /* 拦截器已提示 */ } finally {
    configSaving.value = false;
  }
}

async function uploadNoticeImg(option) {
  try {
    const r = await upload(option.file);
    tutorConfig.noticeImgs.push(r.fileUrl);
  } catch (e) { ElMessage.error('图片上传失败'); }
}

async function uploadPayQr(option) {
  try {
    const r = await upload(option.file);
    tutorConfig.payQrUrl = r.fileUrl;
  } catch (e) { ElMessage.error('收款码上传失败'); }
}

// ==================== 图片预览 ====================
const imgPreviewVisible = ref(false);
const imgPreviewUrl = ref('');
function previewImg(url) {
  if (!url) return;
  imgPreviewUrl.value = url;
  imgPreviewVisible.value = true;
}

// ==================== 数据看板 ====================
const dash = ref({});
const dashLoading = ref(false);

async function loadDashboard() {
  dashLoading.value = true;
  try { dash.value = await adminDashboard(); } catch (e) { /* 拦截器已提示 */ } finally {
    dashLoading.value = false;
  }
}

/** 纯 CSS 柱状图：按 7 天最大值等比放大，最高 96px */
function barHeight(count) {
  const max = Math.max(1, ...(dash.value.registerTrend || []).map(p => p.count));
  return Math.max(4, Math.round((count / max) * 96));
}

// ==================== App 版本 / wgt 热更新 ====================
const verForm = reactive({ platform: 'android', versionCode: null, versionName: '', wgtUrl: '', apkUrl: '', forceUpdate: 0, changelog: '' });
const versionList = ref([]);
const verLoading = ref(false);
const verPublishing = ref(false);
const verUploading = ref(false);
const verApkUploading = ref(false);
const verLoaded = ref(false);
// 当前平台的线上最新版本（用于展示 + 自动预填下一个 versionCode）
const latestVersion = computed(() => versionList.value.find(v => v.platform === verForm.platform) || versionList.value[0] || null);

async function loadVersions() {
  verLoading.value = true;
  try {
    versionList.value = (await adminAppVersions()) || [];
    verLoaded.value = true;
    // 版本号自动预填「线上最新 +1」，省得手算
    const lv = latestVersion.value;
    if (lv && !verForm.versionCode) verForm.versionCode = (lv.versionCode || 0) + 1;
  } catch (e) { /* 拦截器已提示 */ } finally {
    verLoading.value = false;
  }
}

// 选中 .wgt 文件即上传到 OSS（走不压缩的 uploadWgt），拿到 URL 存进表单
async function onWgtFileChange(file) {
  const raw = file && (file.raw || file);
  if (!raw) return;
  if (!/\.wgt$/i.test(raw.name || '')) { ElMessage.warning('请选择 .wgt 升级包文件'); return; }
  verUploading.value = true;
  try {
    const r = await uploadWgt(raw);
    verForm.wgtUrl = r.fileUrl;
    ElMessage.success('wgt 已上传，点「发布此版本」后用户即可自动更新');
  } catch (e) {
    ElMessage.error('wgt 上传失败，请重试');
  } finally {
    verUploading.value = false;
  }
}

// 选中 .apk 即上传到 OSS（复用不压缩、长超时的 uploadWgt 通道），拿到 URL 填进表单，供官网登录页下载入口使用
async function onApkFileChange(file) {
  const raw = file && (file.raw || file);
  if (!raw) return;
  if (!/\.apk$/i.test(raw.name || '')) { ElMessage.warning('请选择 .apk 安装包文件'); return; }
  verApkUploading.value = true;
  try {
    const r = await uploadWgt(raw);
    verForm.apkUrl = r.fileUrl;
    ElMessage.success('APK 已上传，发布后官网登录页会出现下载按钮');
  } catch (e) {
    ElMessage.error('APK 上传失败：文件可能过大或网络不稳，请重试');
  } finally {
    verApkUploading.value = false;
  }
}

async function publishVersion() {
  if (!verForm.versionCode || verForm.versionCode <= 0) { ElMessage.warning('请填写版本号 code（整数，须大于线上最新）'); return; }
  if (!verForm.wgtUrl && !verForm.apkUrl) { ElMessage.warning('请先上传 wgt 升级包，或填写 APK 地址'); return; }
  verPublishing.value = true;
  try {
    await adminPublishAppVersion({ ...verForm });
    ElMessage.success('发布成功！用户下次打开 App 会自动检查并更新');
    Object.assign(verForm, { versionName: '', wgtUrl: '', apkUrl: '', forceUpdate: 0, changelog: '' });
    await loadVersions();
    verForm.versionCode = (latestVersion.value ? latestVersion.value.versionCode : 0) + 1;
  } catch (e) { /* 拦截器已提示 */ } finally {
    verPublishing.value = false;
  }
}

function shortUrl(u) {
  if (!u) return '';
  return u.length > 44 ? u.slice(0, 22) + '…' + u.slice(-18) : u;
}

// 删除一条版本记录：删掉后客户端检查更新会回退到剩下的最大 versionCode，删前明确提醒影响
async function onDeleteVersion(row) {
  const name = row.versionName ? `${row.versionName}（code=${row.versionCode}）` : `code=${row.versionCode}`;
  try {
    await ElMessageBox.confirm(
      `确定删除版本 ${name} 吗？删除后已发布的这条更新不再下发（OSS 上的 wgt/apk 文件不会被删），此操作不可恢复！`,
      '删除版本',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' });
    await adminDeleteAppVersion(row.id);
    ElMessage.success('已删除');
    await loadVersions();
    verForm.versionCode = (latestVersion.value ? latestVersion.value.versionCode : 0) + 1;
  } catch (e) { /* 取消或失败，拦截器已提示 */ }
}

// ==================== 展示工具 ====================
const ROLE_TEXT = { teacher: '老师', student: '学生', parent: '家长', admin: '管理员' };
const ROLE_TAG = { teacher: 'warning', student: 'success', parent: 'primary', admin: 'danger' };
const roleText = r => ROLE_TEXT[r] || r;
const roleTag = r => ROLE_TAG[r] || 'info';

function fmtTime(t) {
  if (!t) return '';
  return String(t).replace('T', ' ').slice(0, 16);
}

// ==================== 初始化 + Tab 切换时按需加载 ====================
watch(currentTab, (tab, prev) => {
  // 家教信息 / 同行合作单共用列表，互切时必须重拉；筛选条件一并清掉，
  // 免得在「家教单」点的待退费筛选被带到「合作单」上，看着像数据少了
  if (tab === 'jobs' || tab === 'partner') {
    if (prev !== tab) clearView();
    else if (!jobList.value.length) loadJobs(1);
  }
  if (tab === 'demands' && !demandList.value.length) loadDemands(1);
  if (tab === 'refunds' && !refundLoaded.value) loadRefunds(1);
  if (tab === 'payments' && !payLogLoaded.value) loadPayLogs(1);
  if (tab === 'templates' && !templateLoaded.value) loadTemplates();
  if (tab === 'config' && !configLoaded.value) loadConfig();
  if (tab === 'dashboard' && !Object.keys(dash.value).length) loadDashboard();
  if (tab === 'appversion' && !verLoaded.value) loadVersions();
}, { immediate: true });

onMounted(() => {
  loadUsers(1);
  loadTodo(); // Tab 徽章与数据看板的家教概览都要用
});
</script>

<style scoped>
.admin-page {
  max-width: 1100px;
  margin: 0 auto;
}

.page-head {
  margin-bottom: 16px;
}
.page-head h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: var(--text);
}

/* ─── Tab 导航 ─── */
.admin-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.admin-tab {
  padding: 8px 18px;
  border-radius: 9999px;
  background: var(--surface);
  border: 1px solid var(--border);
  cursor: pointer;
  font-size: 14px;
  color: var(--color-text-regular);
  transition: color 180ms ease, background-color 180ms ease, border-color 180ms ease, opacity 180ms ease, transform 180ms ease;
  user-select: none;
  position: relative;
}
.admin-tab:hover { border-color: var(--text); }
.admin-tab.active {
  background: var(--text);
  border-color: var(--text);
  color: #fff;
  font-weight: 600;
}

/* Tab 右上角待办徽章 */
.tab-badge {
  position: absolute;
  top: -4px;
  right: -2px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--negative);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  line-height: 18px;
  text-align: center;
}

/* ─── 卡片 ─── */
.card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 18px 20px;
  margin-bottom: 16px;
}
.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #1a2140;
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
/* 卡片标题旁的补充说明 */
.card-sub {
  font-size: 12px;
  font-weight: normal;
  color: var(--muted);
}

/* 需求 4：可点拨号 */
.tel-link {
  color: var(--accent);
  text-decoration: none;
  font-weight: 600;
  white-space: nowrap;
}
.tel-link:hover { text-decoration: underline; }

/* 需求 5：邀请码列 */
.invite-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.invite-code-mini {
  font-family: Consolas, Monaco, monospace;
  letter-spacing: 1px;
  color: var(--accent);
  background: rgba(108, 92, 231, 0.1);
  border-radius: 6px;
  padding: 1px 7px;
  font-size: 12px;
}
.invite-from { font-size: 12px; }
.invite-code-line {
  font-size: 14px;
  color: var(--color-text-regular);
  margin-bottom: 12px;
}

/* 退费 / 流水金额 */
.amount-danger { color: var(--negative); }
.amount-ok { color: var(--positive); }

/* 需求 7：AI 批量发布 */
.batch-steps { margin-bottom: 16px; }
.batch-bar {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.batch-done {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: center;
}

/* ─── 筛选栏 ─── */
.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
  align-items: center;
}
.filter-tip {
  font-size: 12px;
  color: var(--muted);
  margin-left: auto;
}

/* ─── 待办概览条（家教信息 / 家长需求 Tab 顶部） ─── */
.todo-bar {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  padding: 12px 14px;
  margin-bottom: 14px;
  background: var(--raised);
  border: 1px solid var(--border);
  border-radius: 14px;
}
.todo-item {
  flex: 1 1 120px;
  min-width: 110px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 6px 8px;
  border-radius: 10px;
  background: var(--surface);
  transition: color 180ms ease, background-color 180ms ease, border-color 180ms ease, opacity 180ms ease, transform 180ms ease;
}
.todo-item.clickable { cursor: pointer; }
.todo-item.clickable:hover { background: var(--surface); box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08); }
/* 选中态：当前正按该条件筛选 */
.todo-item.on {
  background: var(--accent);
}
.todo-item.on .todo-num,
.todo-item.on .todo-label { color: #fff; }
.todo-num {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--text);
}
.todo-label {
  font-size: 12px;
  color: var(--muted);
  text-align: center;
}
/* 需要马上处理（红）/ 需要关注（黄） */
.todo-item.hot .todo-num { color: var(--negative); }
.todo-item.hot .todo-label { color: var(--negative); }
.todo-item.warn .todo-num { color: var(--warning); }

/* ─── 家长需求表格 ─── */
.demand-no {
  font-family: Consolas, Monaco, monospace;
  letter-spacing: 0.5px;
  color: var(--accent);
}
/* 申请删除 / 待发布的行做背景提醒（表格行由 Element 渲染，需穿透 scoped） */
:deep(.row-delete-req) { --el-table-tr-bg-color: #fdecec; }
:deep(.row-pending) { --el-table-tr-bg-color: #f7efdf; }

/* 需求详情弹窗里的关联家教单 */
.dd-job {
  background: #e7f2ec;
  border: 1px solid #d3ecc6;
  border-radius: 10px;
  padding: 10px 14px;
  margin-top: 12px;
}
.dd-job .apc-title { color: #529b2e; }
.dd-job .apc-line { color: #41613a; }

/* ─── 统计卡片 ─── */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}
.stat-card {
  background: var(--surface);
  border: none;
  border-radius: 0;
  padding: 16px 18px;
}
.stat-card.warn { background: #fdf6f6; }
.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: var(--text);
  line-height: 1.15;
  font-variant-numeric: tabular-nums;
}
.stat-card.warn .stat-value { color: var(--negative); }
.stat-label {
  font-size: 13px;
  color: var(--muted);
  margin-top: 4px;
  font-weight: 500;
}
.stat-sub {
  font-size: 12px;
  color: var(--muted);
  margin-top: 4px;
}

/* ─── 纯 CSS 柱状图 ─── */
.trend-chart {
  display: flex;
  align-items: flex-end;
  gap: 18px;
  padding: 12px 8px 0;
  min-height: 160px;
}
.trend-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.trend-count {
  font-size: 12px;
  color: var(--color-text-regular);
  font-weight: 600;
}
.trend-bar {
  width: 70%;
  max-width: 48px;
  border-radius: 6px 6px 0 0;
  background: var(--accent);
  transition: height 0.4s ease;
}
.trend-date {
  font-size: 12px;
  color: var(--control);
}

/* ─── 分页 ─── */
.pager {
  margin-top: 14px;
  justify-content: flex-end;
}

.muted { color: var(--control); font-size: 13px; }

/* 发布/编辑表单：两列并排 */
.form-row {
  display: flex;
  gap: 12px;
}
.form-row .el-form-item { flex: 1; }
.job-apply-badge :deep(.el-badge__content) { font-size: 12px; }

/* ─── 投递列表（简历卡片） ─── */
.apps-body {
  max-height: 62vh;
  overflow-y: auto;
  padding-right: 4px;
}
.admin-app-card {
  padding: 14px 16px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 12px;
  margin-bottom: 12px;
  background: #fafbfe;
}
.app-head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.app-photo {
  width: 72px;
  height: 92px;
  border-radius: 10px;
  object-fit: cover;
  border: 1px solid rgba(0, 0, 0, 0.08);
  cursor: zoom-in;
  flex-shrink: 0;
}
.app-photo-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--control);
  background: #f2f3f8;
}
.app-head-info { flex: 1; min-width: 0; }
.app-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  flex-wrap: wrap;
}
.app-brief {
  font-size: 13px;
  color: var(--color-text-regular);
  margin-top: 4px;
  line-height: 1.6;
}
.app-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px 12px;
  margin-top: 10px;
}
.app-cell {
  font-size: 13px;
  color: var(--text);
  line-height: 1.6;
}
.award-thumbs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 6px;
}
.award-thumb {
  width: 72px;
  height: 72px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  cursor: zoom-in;
  display: block;
}

.app-cell span,
.app-block span {
  color: var(--muted);
  margin-right: 6px;
}
.app-block {
  font-size: 13px;
  color: var(--text);
  line-height: 1.7;
  margin-top: 8px;
}
.app-msg {
  margin-top: 8px;
  font-size: 12px;
  color: var(--muted);
  background: var(--raised);
  border-radius: 6px;
  padding: 8px 10px;
  line-height: 1.6;
}
.app-time {
  font-size: 12px;
  color: var(--control);
  margin-top: 8px;
}
.app-ops {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: flex-end;
  flex-wrap: wrap;
}
/* 试课失败待退费的提醒（靠左，把退费按钮顶到右边） */
.trial-refund-tip {
  margin-right: auto;
  font-size: 13px;
  color: var(--negative);
  font-weight: 600;
}

/* 单子状态旁的“待退费”红标：可点击直达投递列表办理 */
.refund-tag {
  margin-left: 6px;
  cursor: pointer;
}

/* ─── 缴费解锁管理区块 ─── */
/* ─── 家长联系方式块（投递弹窗 / 订单详情弹窗共用） ─── */
.admin-parent-contact {
  background: #f7efdf;
  border: 1px solid #f5dab1;
  border-radius: 10px;
  padding: 10px 14px;
  margin-bottom: 12px;
}
.apc-title {
  font-weight: 600;
  color: var(--warning);
  font-size: 14px;
  margin-bottom: 4px;
}
.apc-line {
  font-size: 14px;
  color: #5c4827;
  line-height: 1.7;
}

/* ─── 家教单详情弹窗 ─── */
.jd-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 16px;
  margin-bottom: 4px;
}
.jd-cell {
  font-size: 14px;
  color: var(--text);
  line-height: 1.6;
}
.jd-cell > span {
  color: var(--muted);
  margin-right: 8px;
}
.jd-wide {
  grid-column: 1 / -1;
}
@media (max-width: 640px) {
  .jd-grid { grid-template-columns: 1fr; }
}

.pay-section {
  margin-top: 14px;
  border-top: 1px dashed rgba(0, 0, 0, 0.12);
  padding-top: 12px;
}
.pay-section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  color: var(--text);
}

/* 开通弹窗：老师选择列表 */
.pay-teacher-list {
  max-height: 200px;
  overflow-y: auto;
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 10px;
}
.pay-teacher-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  cursor: pointer;
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
  font-size: 13px;
  transition: background 0.15s;
}
.pay-teacher-item:last-child { border-bottom: none; }
.pay-teacher-item:hover { background: var(--raised); }
.pay-teacher-item.active {
  background: var(--accent-soft);
  outline: 1px solid var(--accent);
}
.pt-name { font-weight: 600; color: var(--text); }

/* ─── 接单配置：图片上传 ─── */
.cfg-imgs {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.cfg-img-box {
  position: relative;
  width: 96px;
  height: 96px;
}
.cfg-img-box img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  cursor: zoom-in;
}
.cfg-img-del {
  position: absolute;
  top: -7px;
  right: -7px;
  width: 20px;
  height: 20px;
  line-height: 20px;
  text-align: center;
  border-radius: 50%;
  background: var(--negative);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
}
.cfg-img-add {
  width: 96px;
  height: 96px;
  border-radius: 8px;
  border: 1px dashed rgba(0, 0, 0, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  color: var(--control);
  cursor: pointer;
}
.cfg-img-add:hover { border-color: var(--accent); color: var(--accent); }

@media (max-width: 768px) {
  .form-row { flex-direction: column; gap: 0; }
}

/* ─── 移动端 ─── */
@media (max-width: 768px) {
  .admin-page { padding: 0; }
  .filter-bar .el-input,
  .filter-bar .el-select { width: 100% !important; }
}
/* ─── 移动端增强：简历投递两列信息格窄屏合并单列 ─── */
@media (max-width: 768px) {
  .app-grid { grid-template-columns: 1fr; }
}
</style>
