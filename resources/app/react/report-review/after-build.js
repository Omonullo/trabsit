require("dotenv").config();
const path = require("path");
const fs = require("fs");
const mkdirp = require("mkdirp");
const fse = require("fs-extra");

function writeFile(_path, contents) {
  mkdirp.sync(path.dirname(_path));
  fs.writeFileSync(_path, contents);
}

async function main() {
  const jsFolder = path.join(__dirname, "build", "static", "js");
  const outputJs = path.join(
    __dirname,
    "..",
    "..",
    "..",
    "..",
    "resources",
    "public",
    "js",
    "report-review.js"
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

  const cssFolder = path.join(__dirname, "build", "static", "css");

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
    "report-review.css"
  );
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

  const mediaFolder = path.join(__dirname, "build", "static", "media");

  const outputMedia = path.join(
    __dirname,
    "..",
    "..",
    "..",
    "..",
    "target",
    "react",
    "public",
    "media"
  );
  fse.copySync(mediaFolder, outputMedia);
}

(async () => {
  await main();
})();
