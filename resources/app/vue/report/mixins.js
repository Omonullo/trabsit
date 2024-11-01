import { prettyError } from "./store";

export default {
  filters: {
    t: function (value) {
      return t(value);
    },
    tget: function (value, prop) {
      return value[`${prop}_${window.locale}`];
    },
    moment: window.moment,

    localDateFormat(date) {
      return t("%s года", date.format(`dddd, D MMMM YYYY`));
    },

    humanFileSize(bytes) {
      if (bytes) {
        let thresh = 1024;
        if (Math.abs(bytes) < thresh) {
          return bytes + " Bytes";
        }
        const units = [
          "KBytes",
          "MBytes",
          "GBytes",
          "TBytes",
          "PBytes",
          "EBytes",
          "ZBytes",
          "YBytes",
        ];
        let u = -1;
        do {
          bytes /= thresh;
          ++u;
        } while (Math.abs(bytes) >= thresh && u < units.length - 1);
        return bytes.toFixed(1) + " " + units[u];
      } else {
        return 0 + " Bytes";
      }
    },

    humanRemainingTime(seconds) {
      const mins = Math.round(seconds / 60);
      const hours = Math.round(seconds / 3600);
      if (isFinite(seconds)) {
        if (seconds < 60) {
          return `${seconds} ${t("сек.")}`;
        } else if (mins < 60) {
          return `${Math.round(seconds / 60)} ${"мин."}`;
        } else {
          return `${hours} ${"час."}`;
        }
      } else return t("Неопределенно");
    },

    prettyError,

    lowerFirstLetter(str) {
      return str.charAt(0).toLowerCase() + str.slice(1);
    },
  },
  methods: {
    debug: console.log,
    t: t,
  },
};
