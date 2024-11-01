module.exports = {
  babel: {
    presets: [],
    plugins: [],
    env: {
      production: {
        plugins: ["transform-remove-console"]
      }
    }
  }
};
