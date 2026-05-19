const fs = require('fs');
const file = 'frontend/libs/crm/dashboard/src/lib/dashboard.component.ts';
let content = fs.readFileSync(file, 'utf8');

content = content.replace(
  'totalDeals = computed(() => this.pipeline().reduce((sum, item) => sum + item.count, 0));',
  `// ⚡ Bolt: Use computed signals to cache expensive array reductions.
  // This prevents recalculating totals on every change detection cycle.
  totalDeals = computed(() => this.pipeline().reduce((sum, item) => sum + item.count, 0));`
);

content = content.replace(
  'private pipelineMap = computed(() => {',
  `// ⚡ Bolt: Create an O(1) lookup map for pipeline stages to avoid O(N) .find()
  // operations inside the template's @for loop during change detection.
  private pipelineMap = computed(() => {`
);

content = content.replace(
  'private maxPipelineValue = computed(() => Math.max(...this.pipeline().map((item) => item.totalAmount), 1));',
  `// ⚡ Bolt: Cache the max pipeline value instead of recalculating Math.max()
  // repeatedly in the barWidth() method for every stage in the template.
  private maxPipelineValue = computed(() => Math.max(...this.pipeline().map((item) => item.totalAmount), 1));`
);

fs.writeFileSync(file, content);
