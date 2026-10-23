<template>
  <span class="ui-status" :class="'is-' + tone">{{ text }}</span>
</template>

<script setup>
/* 红笔只用于"要动手"的状态（待批改 / 需重做 / 逾期）；板绿用于已完成。 */
defineProps({
  text: { type: String, required: true },
  tone: { type: String, default: 'muted' } // muted | red | board
});
</script>

<style scoped>
.ui-status {
  display: inline-block;
  padding: 2px 9px;
  border-radius: var(--radius-sm);
  font-size: var(--fs-xs);
  font-weight: 500;
  white-space: nowrap;
}
.is-muted { background: var(--raised); color: var(--muted); }
.is-red { background: var(--red-soft); color: var(--red); }
.is-board { background: var(--board-soft); color: var(--board); }
</style>