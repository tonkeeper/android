SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

bun ${SCRIPT_DIR}/translate.js spanish
bun ${SCRIPT_DIR}/translate.js turkish
bun ${SCRIPT_DIR}/translate.js chinese simplified
bun ${SCRIPT_DIR}/translate.js uzbek
bun ${SCRIPT_DIR}/translate.js ukrainian
bun ${SCRIPT_DIR}/translate.js bahasa indonesia