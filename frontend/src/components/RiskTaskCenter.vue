<template>
  <section class="risk-center" aria-label="AI 风险检测任务中心">
    <header class="risk-toolbar">
      <div><h2>AI 风险检测任务中心</h2><p>红标告警自动建单 · 现场证据留存 · 人工核验归档</p></div>
      <button class="risk-button" :disabled="loading" @click="loadTasks">{{ loading ? '刷新中…' : '刷新记录' }}</button>
    </header>
    <nav class="risk-filters" aria-label="任务状态筛选">
      <button v-for="tab in tabs" :key="tab.value" :class="['risk-filter', { selected: filter === tab.value }]"
              :aria-pressed="filter === tab.value" @click="setFilter(tab.value)">
        {{ tab.label }} <strong>{{ counts[tab.count] }}</strong>
      </button>
    </nav>
    <div v-if="error" class="risk-message error" role="alert">{{ error }} <button @click="loadTasks">重试</button></div>
    <div v-if="notice" class="risk-message success" role="status">{{ notice }}</div>
    <div class="risk-grid" :aria-busy="loading">
      <article v-for="task in tasks" :key="task.id" class="risk-card">
        <header class="risk-card-heading">
          <div><small>风险任务 #{{ task.id }}</small><h3>{{ task.eventName }}</h3></div>
          <span :class="['risk-status', task.status.toLowerCase()]">{{ statusText(task.status) }}</span>
        </header>
        <dl class="risk-info">
          <div><dt>告警时间</dt><dd>{{ task.occurredAt }}</dd></div>
          <div><dt>监控设备</dt><dd>{{ task.sourceDeviceName }} <small>{{ task.sourceDeviceCode }}</small></dd></div>
          <div><dt>发生地点</dt><dd>{{ task.locationDesc || '尚未配置地点名称' }}</dd></div>
          <div><dt>点位坐标</dt><dd>{{ locationText(task.location) }}</dd></div>
          <div><dt>设备信息</dt><dd>{{ task.sourceDeviceType || '摄像头' }} · {{ task.deviceIp || 'IP 未配置' }}</dd></div>
          <div><dt>检测模型</dt><dd>{{ task.selectedModel || '未记录模型' }} · 置信度 {{ confidenceText(task.confidence) }}</dd></div>
        </dl>
        <div class="risk-evidence-heading"><strong>现场带框截图</strong><small>保留告警时的真实检测框</small></div>
        <a v-if="task.snapshotUrl && !brokenImages[task.id]" :href="mediaUrl(task.snapshotUrl)" target="_blank" rel="noopener" class="risk-snapshot" title="查看原始截图">
          <img :src="mediaUrl(task.snapshotUrl)" :alt="`${task.sourceDeviceName} 告警带框截图`" loading="lazy" @error="brokenImages[task.id] = true">
        </a>
        <div v-else class="risk-media-empty">{{ task.snapshotUrl ? '截图暂不可用，请检查证据服务' : '告警时未取得截图' }}</div>
        <div class="risk-evidence-heading"><strong>告警视频</strong><small>{{ task.mediaStatus === 'READY' ? '支持回放与下载' : '' }}</small></div>
        <video v-if="task.mediaStatus === 'READY' && task.videoUrl" class="risk-video" controls preload="none"
               :src="mediaUrl(task.videoUrl)" :poster="task.snapshotUrl ? mediaUrl(task.snapshotUrl) : undefined"
               @error="brokenVideos[task.id] = true"></video>
        <div v-else class="risk-media-empty compact" :class="{ recording: task.mediaStatus === 'RECORDING' }">
          {{ task.mediaStatus === 'RECORDING' ? '正在留存告警视频，完成后自动提供回放…' : '视频留存失败，截图和任务记录仍会保留' }}
        </div>
        <p v-if="brokenVideos[task.id]" class="risk-inline-error">视频暂不可播放，请检查证据服务或下载后查看。</p>
        <p v-if="task.details" class="risk-details">{{ task.details }}</p>
        <div v-if="task.handledAt" class="risk-handled">
          <strong>{{ task.handledBy }}</strong> 于 {{ task.handledAt }} 标注为{{ statusText(task.status) }}
          <p v-if="task.note">备注：{{ task.note }}</p>
        </div>
        <footer class="risk-actions">
          <button class="risk-button" @click="openHistory(task)">处理历史</button>
          <div>
            <button class="risk-button processed" :disabled="task.status === 'PROCESSED'" @click="openAnnotation(task, 'PROCESSED')">标为已处理</button>
            <button class="risk-button recognition_error" :disabled="task.status === 'RECOGNITION_ERROR'" @click="openAnnotation(task, 'RECOGNITION_ERROR')">识别错误</button>
          </div>
        </footer>
      </article>
      <div v-if="!tasks.length && !error" class="risk-empty">
        <strong>{{ loading ? '正在加载任务…' : '暂无该状态的风险任务' }}</strong>
        <span>安全动态监测触发红标告警后，任务会自动保存在这里。</span>
      </div>
    </div>
    <footer class="risk-pagination">
      <span>共 {{ total }} 条 · 第 {{ page }} / {{ pages }} 页</span>
      <div><button class="risk-button" :disabled="page <= 1 || loading" @click="changePage(-1)">上一页</button>
      <button class="risk-button" :disabled="page >= pages || loading" @click="changePage(1)">下一页</button></div>
    </footer>

    <div v-if="annotation" class="risk-modal-backdrop" @click.self="!saving && (annotation = null)">
      <section class="risk-dialog" role="dialog" aria-modal="true" aria-labelledby="risk-annotation-title">
        <h3 id="risk-annotation-title">任务 #{{ annotation.task.id }} · 标为{{ statusText(annotation.status) }}</h3>
        <p>原始告警、截图、视频和本次处理记录都会保留。</p>
        <label for="risk-note">处理备注（选填）</label>
        <textarea id="risk-note" v-model="note" rows="4" maxlength="1000" placeholder="填写核验情况、处理结果或误识别原因"></textarea>
        <p v-if="annotationError" class="risk-inline-error" role="alert">{{ annotationError }}</p>
        <footer><button class="risk-button" :disabled="saving" @click="annotation = null">取消</button>
          <button class="risk-button primary" :disabled="saving" @click="saveAnnotation">{{ saving ? '保存中…' : '确认保存' }}</button></footer>
      </section>
    </div>
    <div v-if="historyTask" class="risk-modal-backdrop" @click.self="historyTask = null">
      <section class="risk-dialog" role="dialog" aria-modal="true" aria-labelledby="risk-history-title">
        <h3 id="risk-history-title">任务 #{{ historyTask.id }} · 处理历史</h3>
        <p v-if="historyLoading">正在加载历史…</p>
        <p v-else-if="historyError" class="risk-inline-error" role="alert">{{ historyError }}</p>
        <ol v-else class="risk-history">
          <li v-for="entry in history" :key="entry.id"><strong>{{ statusText(entry.toStatus) }}</strong>
            <small>{{ entry.createdAt }} · {{ entry.operatorName }}</small>
            <p>{{ entry.note || '无备注' }}</p>
          </li>
        </ol>
        <footer><button class="risk-button" @click="historyTask = null">关闭</button></footer>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import request from '../utils/request';

const tasks = ref<any[]>([]);
const filter = ref('ALL');
const page = ref(1);
const total = ref(0);
const pageSize = 12;
const counts = ref<Record<string, number>>({ total: 0, pending: 0, processed: 0, error: 0 });
const pages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)));
const loading = ref(false);
const error = ref('');
const notice = ref('');
const brokenImages = ref<Record<number, boolean>>({});
const brokenVideos = ref<Record<number, boolean>>({});
const annotation = ref<{ task: any; status: string } | null>(null);
const note = ref('');
const saving = ref(false);
const annotationError = ref('');
const historyTask = ref<any>(null);
const history = ref<any[]>([]);
const historyLoading = ref(false);
const historyError = ref('');
let timer: ReturnType<typeof setInterval>;
let requestId = 0;
const tabs = [
  { value: 'ALL', label: '全部任务', count: 'total' },
  { value: 'PENDING', label: '未处理', count: 'pending' },
  { value: 'PROCESSED', label: '已处理', count: 'processed' },
  { value: 'RECOGNITION_ERROR', label: '识别错误', count: 'error' },
];
const statusText = (value: string) => ({ PENDING: '未处理', PROCESSED: '已处理', RECOGNITION_ERROR: '识别错误' }[value] || value);
const confidenceText = (value: number | null) => value == null ? '未记录' : `${(value <= 1 ? value * 100 : value).toFixed(1)}%`;
const locationText = (value: string | null) => {
  if (!value) return '尚未配置点位坐标';
  const match = value.match(/POINT\s*\(\s*([-\d.]+)\s+([-\d.]+)\s*\)/i);
  return match ? `经度 ${Number(match[1]).toFixed(6)}，纬度 ${Number(match[2]).toFixed(6)}` : value;
};
const mediaUrl = (path: string) => {
  if (!/^\/static\/risk-evidence\/[a-f0-9]{32}\.(jpg|mp4)$/.test(path)) return '';
  const base = new URL(window.location.origin);
  base.port = '8000';
  return new URL(path, import.meta.env.VITE_DISTRIBUTION_URL || base.origin).href;
};
const message = (e: any) => e?.response?.data?.message || e?.message || '请求失败，请检查服务连接';

async function loadTasks() {
  const id = ++requestId;
  loading.value = true;
  try {
    const response: any = await request.get('/api/v1/risk-tasks', { params: {
      pageNum: page.value, pageSize, ...(filter.value === 'ALL' ? {} : { status: filter.value }),
    } });
    if (id !== requestId) return;
    if (response.code !== 200) throw new Error(response.message || '加载任务失败');
    tasks.value = response.data.records;
    counts.value = response.data.counts;
    total.value = response.data.total;
    error.value = '';
    if (page.value > pages.value) { page.value = pages.value; void loadTasks(); }
  } catch (e) {
    if (id === requestId) error.value = message(e);
  } finally {
    if (id === requestId) loading.value = false;
  }
}
function setFilter(value: string) {
  filter.value = value;
  page.value = 1;
  tasks.value = [];
  void loadTasks();
}
function changePage(delta: number) { page.value += delta; void loadTasks(); }
function openAnnotation(task: any, status: string) {
  annotation.value = { task: { ...task }, status };
  note.value = '';
  annotationError.value = '';
}
async function saveAnnotation() {
  if (!annotation.value || saving.value) return;
  saving.value = true;
  try {
    const { task, status } = annotation.value;
    const response: any = await request.post(`/api/v1/risk-tasks/${task.id}/status`, {
      status, expectedStatus: task.status, note: note.value.trim(),
    });
    if (response.code !== 200 || response.data !== true) throw new Error(response.message || '保存未成功');
    notice.value = `任务 #${task.id} 已标为${statusText(status)}，处理历史已保存。`;
    annotation.value = null;
    await loadTasks();
  } catch (e) {
    annotationError.value = message(e);
  } finally { saving.value = false; }
}
async function openHistory(task: any) {
  historyTask.value = task;
  historyLoading.value = true;
  historyError.value = '';
  history.value = [];
  try {
    const response: any = await request.get(`/api/v1/risk-tasks/${task.id}/history`);
    if (response.code !== 200) throw new Error(response.message || '读取历史失败');
    if (historyTask.value?.id === task.id) history.value = response.data;
  } catch (e) { historyError.value = message(e); }
  finally { historyLoading.value = false; }
}
onMounted(() => { void loadTasks(); timer = setInterval(() => { if (!loading.value) void loadTasks(); }, 3000); });
onUnmounted(() => { clearInterval(timer); ++requestId; });
</script>

<style scoped>
.risk-center { height: 100%; min-width: 0; display: flex; flex-direction: column; padding: 20px; gap: 16px; box-sizing: border-box; color: var(--text-white); }
.risk-toolbar, .risk-card-heading, .risk-actions, .risk-pagination, .risk-evidence-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
h2, h3, p { margin: 0; } h2 { font-size: 19px; } .risk-toolbar p { color: var(--text-muted); font-size: 12px; margin-top: 6px; }
.risk-filters { display: flex; flex-wrap: wrap; gap: 8px; }
button { cursor: pointer; font: inherit; } button:disabled { opacity: .5; cursor: default; }
.risk-button, .risk-filter { background: var(--bg-card); border: 1px solid var(--border-color); color: var(--text-white); border-radius: 6px; padding: 8px 12px; font-size: 12px; }
.risk-filter.selected { color: var(--primary); border-color: var(--primary); background: rgba(28, 99, 134, .08); }
.risk-filter strong { margin-left: 8px; font-variant-numeric: tabular-nums; }
.risk-grid { flex: 1; min-height: 0; overflow-y: auto; display: grid; grid-template-columns: repeat(auto-fill, minmax(min(100%, 360px), 1fr)); gap: 16px; align-content: start; padding: 2px; }
.risk-card { background: var(--bg-card); border: 1px solid var(--border-color); border-radius: 10px; padding: 16px; display: flex; flex-direction: column; gap: 12px; min-width: 0; }
.risk-card-heading { align-items: flex-start; } .risk-card-heading small { color: var(--text-muted); font-size: 11px; } h3 { font-size: 14px; line-height: 1.6; margin-top: 4px; }
.risk-status { flex-shrink: 0; border-radius: 5px; padding: 4px 8px; font-size: 11px; font-weight: 600; }
.pending { color: #b45309; background: #fef3c7; } .processed { color: #047857; background: #d1fae5; } .recognition_error { color: #b91c1c; background: #fee2e2; }
.risk-info { padding: 12px; margin: 0; background: var(--bg-input); border-radius: 6px; display: grid; gap: 8px; font-size: 12px; }
.risk-info > div { display: flex; align-items: baseline; gap: 14px; } dt { color: var(--text-muted); flex-shrink: 0; } dd { margin: 0; overflow-wrap: anywhere; } dd small { color: var(--text-muted); display: inline-block; margin-left: 6px; }
.risk-evidence-heading { font-size: 12px; } .risk-evidence-heading small { font-size: 10px; color: var(--text-muted); }
.risk-snapshot { display: block; background: #101b2b; border-radius: 6px; overflow: hidden; }
.risk-snapshot img { display: block; width: 100%; height: 180px; object-fit: contain; }
.risk-video { width: 100%; height: 180px; background: #101b2b; border-radius: 6px; }
.risk-media-empty { height: 180px; display: flex; align-items: center; justify-content: center; color: var(--text-muted); background: var(--bg-input); font-size: 12px; padding: 12px; box-sizing: border-box; border-radius: 6px; }
.risk-media-empty.compact { height: auto; min-height: 60px; } .recording { color: var(--primary); }
.risk-details, .risk-handled { font-size: 12px; color: var(--text-muted); line-height: 1.7; overflow-wrap: anywhere; } .risk-handled { border-top: 1px solid var(--border-color); padding-top: 10px; }
.risk-actions { margin-top: auto; border-top: 1px solid var(--border-color); padding-top: 12px; } .risk-actions > div, .risk-pagination > div, .risk-dialog footer { display: flex; gap: 8px; }
.risk-empty { grid-column: 1 / -1; padding: 64px 20px; text-align: center; color: var(--text-muted); display: flex; flex-direction: column; gap: 12px; background: var(--bg-card); border: 1px dashed var(--border-color); border-radius: 8px; font-size: 13px; }
.risk-pagination { font-size: 12px; color: var(--text-muted); }
.risk-message { border-radius: 6px; padding: 10px 12px; font-size: 12px; } .error, .risk-inline-error { color: #b91c1c; } .error { background: #fee2e2; } .success { color: #047857; background: #d1fae5; } .risk-message button { border: 0; background: transparent; text-decoration: underline; color: inherit; }
.risk-inline-error { font-size: 12px; line-height: 1.6; }
.risk-modal-backdrop { position: fixed; inset: 0; background: rgba(15, 23, 42, .55); z-index: 2000; display: flex; align-items: center; justify-content: center; padding: 20px; }
.risk-dialog { width: 480px; max-width: 100%; max-height: 85vh; overflow-y: auto; background: var(--bg-card); padding: 24px; border: 1px solid var(--border-color); border-radius: 12px; display: flex; flex-direction: column; gap: 16px; }
.risk-dialog > p, .risk-dialog label { font-size: 12px; color: var(--text-muted); line-height: 1.7; }
.risk-dialog textarea { width: 100%; resize: vertical; box-sizing: border-box; border-radius: 6px; border: 1px solid var(--border-color); background: var(--bg-input); color: var(--text-white); padding: 10px; font: inherit; font-size: 13px; }
.risk-dialog footer { justify-content: flex-end; } .primary { background: var(--primary); color: white; border-color: var(--primary); }
.risk-history { padding-left: 18px; display: flex; flex-direction: column; gap: 20px; font-size: 13px; } .risk-history small { display: block; margin: 5px 0; color: var(--text-muted); } .risk-history p { overflow-wrap: anywhere; line-height: 1.7; }
@media (max-width: 700px) { .risk-center { padding: 12px; } .risk-toolbar { align-items: flex-start; } .risk-toolbar p { max-width: 220px; } .risk-filters { gap: 6px; } .risk-filter { padding: 7px 9px; } .risk-actions { flex-wrap: wrap; } }
</style>
