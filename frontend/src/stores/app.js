import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useAppStore = defineStore('app', () => {
  const loading = ref(false);
  const sidebarCollapsed = ref(false);

  function setLoading(val) {
    loading.value = val;
  }

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value;
  }

  return { loading, sidebarCollapsed, setLoading, toggleSidebar };
});
