<template>
  <div class="kpi-card">
    <div v-if="$slots.icon" class="kpi-icon" :class="'tone-' + tone">
      <slot name="icon" />
    </div>
    <div class="kpi-body">
      <span class="kpi-label">{{ label }}</span>
      <span class="kpi-value">
        {{ value }}<small v-if="unit">{{ unit }}</small>
      </span>
      <span v-if="note" class="kpi-note">{{ note }}</span>
    </div>
  </div>
</template>

<script setup>
/* 白卡 + 彩色圆角图标块 + 大号数字。参考「智慧校园」风格。 */
defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], default: '' },
  unit: { type: String, default: '' },
  note: { type: String, default: '' },
  tone: { type: String, default: 'blue' } // blue | green | amber | red
});
</script>

<style scoped>
.kpi-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  transition: box-shadow var(--t-hover) ease, transform var(--t-press) ease;
}
.kpi-card:hover { box-shadow: var(--shadow-lg); }
.kpi-icon {
  width: 46px;
  height: 46px;
  border-radius: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 23px;
  flex: none;
}
.tone-blue { background: var(--accent-soft); color: var(--accent); }
.tone-green { background: var(--board-soft); color: var(--board); }
.tone-amber { background: var(--amber-soft); color: var(--amber); }
.tone-red { background: var(--red-soft); color: var(--red); }
.kpi-body { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.kpi-label { font-size: var(--fs-sm); color: var(--muted); }
.kpi-value {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--text);
  font-variant-numeric: tabular-nums;
}
.kpi-value small { font-size: var(--fs-sm); font-weight: 500; color: var(--muted); margin-left: 3px; }
.kpi-note { font-size: var(--fs-xs); color: var(--muted); }
</style>