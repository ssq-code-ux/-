/**
 * 离线词库 — 基于 IndexedDB 的本地词汇缓存
 *
 * PWA 离线能力核心：在线时把词汇写入本地，断网后「记忆卡片」等功能仍可正常学习。
 * IndexedDB 容量远大于 localStorage（几十 MB 起步），适合存整个词库。
 */

const DB_NAME = 'engtutor-offline';
const DB_VERSION = 1;
const STORE_VOCAB = 'vocabulary';
const STORE_META = 'meta';

let dbPromise = null;

/** 打开（或创建）数据库，单例复用连接 */
function openDB() {
  if (dbPromise) return dbPromise;
  dbPromise = new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      reject(new Error('当前环境不支持 IndexedDB'));
      return;
    }
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE_VOCAB)) {
        db.createObjectStore(STORE_VOCAB, { keyPath: 'id' });
      }
      if (!db.objectStoreNames.contains(STORE_META)) {
        db.createObjectStore(STORE_META, { keyPath: 'key' });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => {
      dbPromise = null;
      reject(req.error);
    };
  });
  return dbPromise;
}

/** 事务转 Promise */
function txDone(tx) {
  return new Promise((resolve, reject) => {
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error);
  });
}

/**
 * 全量覆盖保存词汇（在线拉取成功后调用）
 * @param {Array} words 词汇数组（需含 id 字段）
 */
export async function saveVocabWords(words) {
  if (!Array.isArray(words) || !words.length) return;
  const db = await openDB();
  const tx = db.transaction([STORE_VOCAB, STORE_META], 'readwrite');
  const store = tx.objectStore(STORE_VOCAB);
  store.clear();
  words.forEach((w) => {
    if (w && w.id != null) store.put(JSON.parse(JSON.stringify(w)));
  });
  tx.objectStore(STORE_META).put({ key: 'vocabCachedAt', value: Date.now(), count: words.length });
  await txDone(tx);
}

/**
 * 读取本地缓存的全部词汇（离线兜底）
 * @returns {Promise<Array>} 词汇数组，无缓存时返回空数组
 */
export async function getVocabWords() {
  try {
    const db = await openDB();
    const tx = db.transaction(STORE_VOCAB, 'readonly');
    const req = tx.objectStore(STORE_VOCAB).getAll();
    return await new Promise((resolve, reject) => {
      req.onsuccess = () => resolve(req.result || []);
      req.onerror = () => reject(req.error);
    });
  } catch (e) {
    return [];
  }
}

/** 词库缓存信息（缓存时间/数量），用于界面提示 */
export async function getVocabCacheInfo() {
  try {
    const db = await openDB();
    const tx = db.transaction(STORE_META, 'readonly');
    const req = tx.objectStore(STORE_META).get('vocabCachedAt');
    return await new Promise((resolve) => {
      req.onsuccess = () => resolve(req.result || null);
      req.onerror = () => resolve(null);
    });
  } catch (e) {
    return null;
  }
}
