import { defineStore } from 'pinia';
import { ref } from 'vue';

/**
 * 学习资料列表缓存
 *
 * 目的：从资料详情页（/resource/:id）返回列表时，直接复用上次的列表与筛选/分页状态，
 * 避免"看完一个资料回来就得重新搜索/重新加载"。教师端看其他老师共享资料同理。
 *
 * 失效时机：新增/编辑/删除/复制资料、切换"查看某老师共享资料"时调用 invalidate。
 * 筛选/分页变化时 cacheKey 改变，自然绕过缓存重新拉取并覆盖缓存。
 */
export const useResourceStore = defineStore('resource', () => {
  // 教师端：{ key, records, total }
  const teacher = ref(null);
  // 学生端：{ key, records, total }
  const student = ref(null);

  function setTeacher(v) { teacher.value = v; }
  function setStudent(v) { student.value = v; }
  function invalidateTeacher() { teacher.value = null; }
  function invalidateStudent() { student.value = null; }

  return { teacher, student, setTeacher, setStudent, invalidateTeacher, invalidateStudent };
});