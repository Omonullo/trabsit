import resolve from "rollup-plugin-node-resolve";
import commonjs from "rollup-plugin-commonjs";
import livereload from "rollup-plugin-livereload";
import json from "rollup-plugin-json";
import replace from "rollup-plugin-replace";
import { terser } from "rollup-plugin-terser";
import vue from "rollup-plugin-vue";
const production = !process.env.ROLLUP_WATCH;

export default {
  input: "resources/app/vue/report/main.js",
  output: {
    sourcemap: true,
    format: "esm",
    name: "report",
    file: "target/rollup/public/js/report.js",
  },

  plugins: [
    json(),
    vue({
      sourceRoot: "app/vue/report",
      template: {
        isProduction: !production,
      },
    }),

    commonjs(),

    resolve({
      browser: true,
    }),

    !production && livereload("target/rollup/public"),

    production && terser(),
    replace({
      "process.env.NODE_ENV": production
        ? JSON.stringify("production")
        : JSON.stringify("development"),
      "process.env.VUE_ENV": JSON.stringify("browser"),
    }),
  ],
  watch: {
    clearScreen: false,
  },
};
