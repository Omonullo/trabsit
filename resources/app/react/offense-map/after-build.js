require("dotenv").config();
const path = require("path");
const fs = require("fs");
const mkdirp = require('mkdirp');

function writeFile(_path, contents) {
  mkdirp.sync(path.dirname(_path));
  fs.writeFileSync(_path, contents)
}

async function main() {
  const jsFolder = path.join(__dirname, "build", "static", "js");
  const cssFolder = path.join(__dirname, "build", "static", "css");
  const outputJs = path.join(
    __dirname,
    "..",
    "..",
    "..",
    "..",
    "target",
    "react",
    "public",
    "js",
    "offense-map.js"
  );
  const outputCss = path.join(
    __dirname,
    "..",
    "..",
    "..",
    "..",
    "target",
    "react",
    "public",
    "css",
    "offense-map.css"
  );
  let jsContent = "";
  fs.readdirSync(jsFolder).forEach((file) => {
    if (file.match(/^.*\.(js)$/)) {
      jsContent =
        jsContent +
        "\n" +
        fs.readFileSync(path.join(jsFolder, file)).toString();
    }
  });
  writeFile(outputJs, jsContent);

  let cssContent = "";
  fs.readdirSync(cssFolder).forEach((file) => {
    if (file.match(/^.*\.(css)$/)) {
      cssContent =
        cssContent +
        "\n" +
        fs.readFileSync(path.join(cssFolder, file)).toString();
    }
  });
  writeFile(outputCss, cssContent);
}

(async () => {
  await main();
})();
