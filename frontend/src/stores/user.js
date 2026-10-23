import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { logoutApi } from '../api';

export const useUserStore = defineStore('user', () => {
  const user = ref(JSON.parse(localStorage.getItem('user') || 'null'));
  const token = computed(() => user.value?.token);
  const role = computed(() => user.value?.role);
  const isLoggedIn = computed(() => !!token.value);

  function login(userData) {
    user.value = userData;
    localStorage.setItem('user', JSON.stringify(userData));
  }

  function logout() {
    // 通知后端把 Token 拉入黑名单，失败不阻塞退出流程
    logoutApi(user.value?.refreshToken).catch(() => {});
    user.value = null;
    localStorage.removeItem('user');
    window.location.href = '/login';
  }

  return { user, token, role, isLoggedIn, login, logout };
});
