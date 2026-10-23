<template>
  <header class="ui-page-head">
    <div class="ui-page-head__text">
      <h1>{{ title }}</h1>
      <p v-if="sub || $slots.sub">
        <slot name="sub">{{ sub }}</slot>
      </p>
    </div>
    <div v-if="$slots.actions" class="ui-page-head__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  sub: { type: String, default: '' }
});
</script>

<style scoped>
.ui-page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
  margin-bottom: 18px;
}
.ui-page-head h1 {
  margin: 0 0 4px;
  font-size: var(--fs-2xl);
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--text);
}
.ui-page-head p {
  margin: 0;
  font-size: var(--fs-sm);
  color: var(--muted);
}
.ui-page-head__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
</style>