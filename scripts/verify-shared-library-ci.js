const fs = require('fs');
const path = require('path');

const workflowPath = path.join(__dirname, '..', '.github', 'workflows', 'ci.yml');
const workflow = fs.readFileSync(workflowPath, 'utf8');

const requiredSnippets = [
  'shared-libraries:',
  'name: Shared Libraries (Java 21 / Maven)',
  'mvn -B -U verify --no-transfer-progress -pl libs/common,libs/security,libs/rules-engine -am',
];

const missing = requiredSnippets.filter((snippet) => !workflow.includes(snippet));

if (missing.length > 0) {
  console.error(`Missing shared-library CI configuration:\n${missing.join('\n')}`);
  process.exit(1);
}
