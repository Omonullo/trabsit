import Vue from "vue";
import Vuex from "vuex";
import details from "./modules/details";
import offenses from "./modules/offenses";
import reward from "./modules/reward";
import axios from "axios";
import createVideo from "./modules/video";

const isObject = (obj) => {
  return obj === Object(obj);
};

const prettyError = (error) => {
  return error && error.filter((e) => e).join(", ");
};

function coerceOffenseErrors(errors) {
  let offenseErrors = {};
  for (let key in errors.offenses) {
    if (errors.offenses.hasOwnProperty(key)) {
      const o = errors.offenses[key];
      offenseErrors[key] = {
        vehicleId: prettyError(o.vehicle_id),
        typeId: prettyError(o.type_id),
        testimony: prettyError(o.testimony),
        citizenArticleId: prettyError(o.citizenArticleId),
      };
    }
  }
  return offenseErrors;
}

function coerceRewardErrors(params) {
  if (isObject(params)) {
    return {
      type: "",
      phone: prettyError(params && params.phone),
      card: prettyError(params && params.card),
      fund: prettyError(params && params.fund),
      bank: prettyError(params && params.bank),
    };
  } else {
    return {
      type: prettyError(params),
      phone: "",
      card: "",
      fund: "",
      bank: "",
    };
  }
}

function coerceDetailsErrors({
  incident_time,
  incident_date,
  area_id,
  address,
  lat,
  lng,
  district_id,
}) {
  return {
    time: prettyError(incident_time),
    date: prettyError(incident_date),
    area: prettyError(area_id),
    address: prettyError(address),
    district: prettyError(district_id),
    coords: prettyError([...(lat || []), ...(lng || [])]),
  };
}

const getters = {
  errorCount(state, getters) {
    return Object.keys(state).reduce(
      (acc, name) => acc + (getters[name + "/errorCount"] || 0),
      0
    );
  },
};

const actions = {
  updateErrors({ commit }, errors) {
    commit("offenses/updateErrors", coerceOffenseErrors(errors));
    commit("reward/updateErrors", coerceRewardErrors(errors.reward_params));

    const detailsErrors = coerceDetailsErrors(errors);

    if (detailsErrors.date) {
      commit("details/refreshNow");
      commit("details/updateDate");
    }
    commit("details/updateErrors", detailsErrors);

    if (errors.video_id) {
      commit("video/reset");
      commit("video/updateError", prettyError(errors.video_id));
    }
    if (errors.extra_video_id) {
      commit("extraVideo/reset");
      commit(
        "extraVideo/updateError",
        prettyError([
          ...(errors.extra_video_id || []),
          ...(errors.extra_video_type || []),
        ])
      );
    }
  },

  showNotification(_, { text, type }) {
    $.notify(
      {
        message: text,
      },
      {
        animate: {
          enter: "animated fadeInUp",
          exit: "animated fadeOut",
        },
        type: type || "success",
        spacing: 10,
        offset: {
          y: 20,
        },
        allow_dismiss: false,
        newest_on_top: false,
        placement: {
          from: "bottom",
          align: "center",
        },
        delay: 500,
        duration: 3000,
        onShow: function () {
          this.css({ width: "20rem" });
        },
      }
    );
  },

  async validate({ dispatch, state }) {
    Object.keys(state).forEach((k) => {
      if (state[k].list) dispatch(k + "/validateList");
      else dispatch(k + "/validate");
    });
  },

  async sendReport({ dispatch, state, commit, getters }) {
    const { offenses, video, reward, details, extraVideo } = state;
    let result;
    await dispatch("validate");

    if (video.status === "uploading" || extraVideo.status === "uploading") {
      dispatch("showNotification", {
        text: t("Дождитесь окончания отправки видео"),
        type: "warning",
      });
      return;
    }

    if (getters["errorCount"] > 0) {
      dispatch("showNotification", {
        text: t("Исправьте ошибки"),
        type: "danger",
      });
      return;
    }

    const withExtraVideo =
      state.withExtraVideo && extraVideo.status !== "initial";

    let data = {
      offenses: offenses.list
        .map((o) => ({
          vehicle_id: o.vehicleId,
          citizen_article_id: o.citizenArticleId,
          type_id: o.typeId,
          testimony: o.testimony,
        }))
        .reduce((acc, v, i) => ({ ...acc, [i]: v }), {}),
      video_id: video.id,
      lat: details.coords[0],
      lng: details.coords[1],
      address: details.address,
      incident_date: details.date && details.date.format("DD.MM.YYYY"),
      incident_time: details.time,
      area_id: details.area && details.area.id,
      district_id: details.district && details.district.id,
      with_extra_video: withExtraVideo,
    };

    if (withExtraVideo) {
      data.extra_video_id = extraVideo.id;
      data.extra_video_type = extraVideo.type;
    }
    if (reward.type === "phone") data.reward_params = { phone: reward.phone };
    else if (reward.type === "fund") data.reward_params = { fund: reward.fund };
    else if (reward.type === "card") {
      data.reward_params = { card: true };
    } else if (reward.type === "bank")
      data.reward_params = { bank: reward.bank };
    else if (reward.type === "no-reward")
      data.reward_params = { "no-reward": true };

    try {
      result = await axios.post("/api/citizen/reports", data);
    } catch (e) {
      result = e.response;
    }
    if (result && result.status) {
      // if not network error
      if (result.status === 200) {
        window.open(result.data.redirect, "_self");
      } else if (result.status === 400) {
        commit("reset");
        dispatch("updateErrors", result.data.errors);
        dispatch("showNotification", {
          text: t("Исправьте ошибки"),
          type: "danger",
        });
      }
    } else {
      dispatch("showNotification", {
        text: t("Сервис недоступен, проверьте соединение"),
        type: "danger",
      });
    }
  },
};

Vue.use(Vuex);
export { prettyError };
export default new Vuex.Store({
  state: {
    withExtraVideo: false,
  },

  mutations: {
    updateWithExtraVideo(state, flag) {
      state.withExtraVideo = flag;
    },
  },
  getters,
  actions,
  modules: {
    reward,
    details,
    extraVideo: createVideo(),
    video: createVideo(),
    offenses,
  },
});
