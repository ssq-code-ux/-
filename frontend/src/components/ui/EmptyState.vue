<template>
  <div class="ui-empty">
    <p class="ui-empty__text">{{ text }}</p>
    <div v-if="$slots.default" class="ui-empty__action">
      <slot />
    </div>
  </div>
</template>

<script setup>
/* 空态是"邀请动作"，不是情绪：说明情况 + 给一个下一步。 */
defineProps({
  text: { type: String, required: true }
});
</script>

<style scoped>
.ui-empty {
  padding: 40px 16px 44px;
  text-align: center;
}
.ui-empty__text {
  margin: 0 0 14px;
  font-size: var(--fs-base);
  color: var(--muted);
}
.ui-empty__action {
  display: flex;
  justify-content: center;
  gap: 10px;
}
</style>