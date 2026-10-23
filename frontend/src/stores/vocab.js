import { defineStore } from 'pinia';
import { ref } from 'vue';

/**
 * 词汇管理缓存
 *
 * 目的：切换标签 / 重新进入「词汇管理」时复用上次数据，避免每次都重新请求，
 * 解决"每次返回词汇管理都会重新加载单词"的问题。
 * 失效时机：新增 / 编辑 / 删除词汇后调用 invalidate()，或点「刷新」强制重拉。
 */
export const useVocabStore = defineStore('vocab', () => {
  /** 词库列表（很少变化，缓存一次即可） */
  const banks = ref(null);
  /** 列表缓存对应的查询条件：`${page}|${keyword}|${bank}` */
  const cacheKey = ref(null);
  /** 列表缓存数据 { records, total } */
  const listData = ref(null);

  function setBanks(value) {
    banks.value = value;
  }

  function setList(key, data) {
    cacheKey.value = key;
    listData.value = data;
  }

  /** 增删改后失效，下次进入强制重拉 */
  function invalidate() {
    cacheKey.value = null;
    listData.value = null;
  }

  return { banks, cacheKey, listData, setBanks, setList, invalidate };
});