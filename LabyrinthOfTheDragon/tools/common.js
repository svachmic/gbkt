const fs = require('fs');
const fsPromise = require('fs/promises');
const path = require('path');

async function updateManifest(outputPath, data) {
  const outputFileName = path.basename(outputPath);
  const outputBaseDir = path.dirname(outputPath);
  const manifestPath = path.join(outputBaseDir, 'manifest.json');
  let manifest = {};

  try {
    await fsPromise.access(manifestPath, fs.constants.R_OK);
    manifest = JSON.parse(await fsPromise.readFile(manifestPath));
  } catch (err) {
    manifest = { files: {} };
  }

  try {
    manifest.files[outputFileName] = data;
    await fsPromise.writeFile(manifestPath, JSON.stringify(manifest));
  } catch (err) {
    console.error('Unable to save manifest file.');
    console.error(err);
    process.exit(1);
  }
}

module.exports = {
  updateManifest
}
