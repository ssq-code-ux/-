<template>
  <section class="ui-section" :class="{ 'is-ruled': ruled }">
    <header v-if="title || $slots.actions" class="ui-section__head">
      <h2 class="ui-section__title">{{ title }}</h2>
      <div v-if="$slots.actions" class="ui-section__actions">
        <slot name="actions" />
      </div>
    </header>
    <slot />
  </section>
</template>

<script setup>
/* 用发丝线 + 留白分区，而不是给每块套一张圆角白卡。 */
defineProps({
  title: { type: String, default: '' },
  ruled: { type: Boolean, default: true }
});
</script>

<style scoped>
.ui-section { margin-bottom: 28px; }
.ui-section.is-ruled {
  padding-top: 18px;
  border-top: 1px solid var(--border);
}
.ui-section__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.ui-section__title {
  margin: 0;
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text);
}
.ui-section__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>